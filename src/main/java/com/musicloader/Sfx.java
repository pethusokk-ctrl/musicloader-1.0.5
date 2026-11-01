package com.musicloader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Звуковые эффекты кнопок (генерируются кодом, файлы не нужны). Громкость и вкл/выкл - в настройках. */
public final class Sfx {
    private static final float RATE = 44100f;
    private static final AudioFormat FMT = new AudioFormat(RATE, 16, 1, true, false);

    private static final byte[] HOVER = tone(new double[]{1500}, 26, 0.30);
    private static final byte[] CLICK = tone(new double[]{660, 990}, 90, 0.55);

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "musicloader-sfx");
        t.setDaemon(true);
        return t;
    });

    private static long lastHover = 0;

    private Sfx() {}

    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 60) return;
        lastHover = now;
        play(HOVER);
    }

    public static void click() {
        play(CLICK);
    }

    private static void play(byte[] data) {
        if (!MusicSettings.sfxOn || MusicSettings.sfxVolume <= 0.001f) return;
        final float vol = MusicSettings.sfxVolume;
        POOL.execute(() -> {
            try {
                Clip c = AudioSystem.getClip();
                c.open(FMT, data, 0, data.length);
                try {
                    FloatControl gc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20.0 * Math.log10(vol));
                    gc.setValue(Math.max(gc.getMinimum(), Math.min(gc.getMaximum(), db)));
                } catch (Throwable ignored) {
                }
                c.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) c.close();
                });
                c.start();
            } catch (Throwable ignored) {
            }
        });
    }

    /** Короткий мягкий сигнал: несколько тонов подряд, с плавным затуханием. */
    private static byte[] tone(double[] freqs, int totalMs, double amp) {
        int total = (int) (RATE * totalMs / 1000.0);
        byte[] out = new byte[total * 2];
        int per = total / freqs.length;
        for (int i = 0; i < total; i++) {
            int seg = Math.min(freqs.length - 1, i / per);
            int local = i - seg * per;
            double t = local / (double) RATE;
            double attack = Math.min(1.0, local / (RATE * 0.003));
            double decay = Math.pow(1.0 - local / (double) per, 1.6);
            double s = Math.sin(2 * Math.PI * freqs[seg] * t) * attack * decay * amp;
            short v = (short) (s * 32767);
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}
