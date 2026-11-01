package com.musicloader;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Плеер: WAV через javax.sound, MP3 через JLayer. Музыка играет и после закрытия меню паузы. */
public final class MusicPlayer {
    private static volatile Clip clip;
    private static volatile boolean loading = false;
    private static int index = -1;
    private static boolean userPaused = false;
    private static boolean loop = MusicLibrary.loadLoop();
    private static int token = 0;

    private MusicPlayer() {}

    public static MusicLibrary.Song current() {
        List<MusicLibrary.Song> list = MusicLibrary.songs();
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    public static boolean isPlaying() {
        Clip c = clip;
        return c != null && c.isRunning();
    }

    public static boolean isLoading() { return loading; }

    public static boolean isLoop() { return loop; }

    public static void toggleLoop() {
        loop = !loop;
        MusicLibrary.saveLoop(loop);
    }

    public static void select(int newIndex) {
        List<MusicLibrary.Song> list = MusicLibrary.songs();
        if (list.isEmpty()) return;
        index = Math.floorMod(newIndex, list.size());
        play(list.get(index));
    }

    public static void next() { select(index + 1); }

    public static void previous() { select(index - 1); }

    private static AudioInputStream open(Path file) throws Exception {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            return decodeMp3(file);
        }
        return AudioSystem.getAudioInputStream(file.toFile());
    }

    private static AudioInputStream decodeMp3(Path file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        int freq = 44100;
        int channels = 2;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Bitstream bs = new Bitstream(in);
            Decoder decoder = new Decoder();
            byte[] chunk = new byte[8192];
            Header h;
            while ((h = bs.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bs);
                short[] buf = sb.getBuffer();
                int len = sb.getBufferLength();
                freq = decoder.getOutputFrequency();
                channels = decoder.getOutputChannels();
                int pos = 0;
                for (int i = 0; i < len; i++) {
                    chunk[pos++] = (byte) (buf[i] & 0xFF);
                    chunk[pos++] = (byte) ((buf[i] >> 8) & 0xFF);
                    if (pos >= chunk.length - 1) {
                        out.write(chunk, 0, pos);
                        pos = 0;
                    }
                }
                out.write(chunk, 0, pos);
                bs.closeFrame();
            }
            bs.close();
        }
        byte[] data = out.toByteArray();
        AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, freq, 16, channels,
                channels * 2, freq, false);
        return new AudioInputStream(new ByteArrayInputStream(data), fmt, data.length / (channels * 2L));
    }

    private static void play(MusicLibrary.Song song) {
        stopClip();
        final int my = ++token;
        loading = true;
        Thread t = new Thread(() -> {
            try (AudioInputStream in = open(song.file())) {
                Clip c = AudioSystem.getClip();
                c.open(in);
                if (my != token) {
                    c.close();
                    return;
                }
                c.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP && my == token && clip == c
                            && !userPaused && c.getMicrosecondPosition() >= c.getMicrosecondLength()) {
                        if (loop) {
                            c.setFramePosition(0);
                            c.start();
                        } else {
                            next();
                        }
                    }
                });
                setGain(c);
                userPaused = false;
                clip = c;
                c.start();
            } catch (Throwable e) {
                MusicLoaderClient.LOGGER.error("Не удалось воспроизвести " + song.file(), e);
            } finally {
                if (my == token) loading = false;
            }
        }, "musicloader-decoder");
        t.setDaemon(true);
        t.start();
    }

    public static void togglePause() {
        Clip c = clip;
        if (c == null) {
            if (!loading) select(Math.max(index, 0));
            return;
        }
        if (c.isRunning()) {
            userPaused = true;
            c.stop();
        } else {
            userPaused = false;
            c.start();
        }
    }

    public static void seek(double fraction) {
        Clip c = clip;
        if (c == null) return;
        fraction = Math.max(0, Math.min(0.999, fraction));
        c.setMicrosecondPosition((long) (c.getMicrosecondLength() * fraction));
    }

    public static double progress() {
        Clip c = clip;
        if (c == null || c.getMicrosecondLength() <= 0) return 0;
        return (double) c.getMicrosecondPosition() / c.getMicrosecondLength();
    }

    public static int positionSeconds() {
        Clip c = clip;
        return c == null ? 0 : (int) (c.getMicrosecondPosition() / 1_000_000L);
    }

    public static int lengthSeconds() {
        Clip c = clip;
        return c == null ? 0 : (int) (c.getMicrosecondLength() / 1_000_000L);
    }

    private static void stopClip() {
        Clip old = clip;
        clip = null;
        if (old != null) closeAsync(old);
    }

    /** stop/close у звукового драйвера иногда блокируются - делаем это в фоне, чтобы игра не зависала. */
    private static void closeAsync(Clip c) {
        Thread t = new Thread(() -> {
            try {
                c.stop();
                c.close();
            } catch (Throwable ignored) {
            }
        }, "musicloader-close");
        t.setDaemon(true);
        t.start();
    }

    /** Применяет громкость музыки из настроек к играющей песне. */
    public static void applyVolume() {
        Clip c = clip;
        if (c != null) setGain(c);
    }

    private static void setGain(Clip c) {
        try {
            FloatControl gc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float v = MusicSettings.musicVolume;
            float db = v <= 0.0001f ? gc.getMinimum() : (float) (20.0 * Math.log10(v));
            gc.setValue(Math.max(gc.getMinimum(), Math.min(gc.getMaximum(), db)));
        } catch (Throwable ignored) {
        }
    }

    public static void shutdown() {
        userPaused = true;
        token++;
        stopClip();
    }
}
