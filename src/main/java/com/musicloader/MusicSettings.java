package com.musicloader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Настройки мода: громкость, эффекты, повтор, язык приветствия. Хранятся в musicloader/settings.properties. */
public final class MusicSettings {
    private static final Path FILE = MusicLibrary.DIR.resolve("settings.properties");

    public static volatile float musicVolume = 1.0f;
    public static volatile float sfxVolume = 0.6f;
    public static volatile boolean sfxOn = true;
    public static volatile boolean loop = false;
    public static volatile boolean welcomeShown = false;
    public static volatile String lang = "";

    static {
        load();
    }

    private MusicSettings() {}

    private static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(r);
            musicVolume = clamp(parse(p.getProperty("musicVolume"), 1.0f));
            sfxVolume = clamp(parse(p.getProperty("sfxVolume"), 0.6f));
            sfxOn = !"false".equals(p.getProperty("sfxOn"));
            loop = "true".equals(p.getProperty("loop"));
            welcomeShown = "true".equals(p.getProperty("welcomeShown"));
            lang = p.getProperty("lang", "");
        } catch (Exception e) {
            MusicLoaderClient.LOGGER.warn("Не удалось прочитать настройки", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(MusicLibrary.DIR);
            Properties p = new Properties();
            p.setProperty("musicVolume", Float.toString(musicVolume));
            p.setProperty("sfxVolume", Float.toString(sfxVolume));
            p.setProperty("sfxOn", Boolean.toString(sfxOn));
            p.setProperty("loop", Boolean.toString(loop));
            p.setProperty("welcomeShown", Boolean.toString(welcomeShown));
            p.setProperty("lang", lang);
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                p.store(w, "Music Loader settings");
            }
        } catch (IOException e) {
            MusicLoaderClient.LOGGER.warn("Не удалось сохранить настройки", e);
        }
    }

    private static float parse(String s, float def) {
        try {
            return s == null ? def : Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
