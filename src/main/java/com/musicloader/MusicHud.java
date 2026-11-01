package com.musicloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.musicloader.Ui.ACCENT;
import static com.musicloader.Ui.argb;

/** Твой текст в игре: плавно появляется, пока играет песня, и плавно гаснет на паузе. */
public final class MusicHud {
    private static boolean failed = false;
    private static float alpha = 0f;
    private static long last = System.nanoTime();

    private MusicHud() {}

    public static void render(GuiGraphicsExtractor g) {
        if (failed) return;
        try {
            draw(g);
        } catch (Throwable t) {
            failed = true;
            MusicLoaderClient.LOGGER.error("HUD отключён из-за ошибки", t);
        }
    }

    private static void draw(GuiGraphicsExtractor g) {
        long now = System.nanoTime();
        float dt = Math.min(0.25f, (now - last) / 1_000_000_000f);
        last = now;

        float target = MusicPlayer.isPlaying() ? 1f : 0f;
        float step = dt * 0.9f; // около секунды на появление
        alpha = alpha < target ? Math.min(target, alpha + step) : Math.max(target, alpha - step);

        Minecraft mc = Minecraft.getInstance();
        if (ScreenUtil.hasScreen()) return;
        String text = MusicLibrary.cachedText();
        if (text.isEmpty() || alpha < 0.03f) return;

        float e = alpha * alpha * (3f - 2f * alpha);
        Font font = mc.font;
        var song = MusicPlayer.current();
        String songLine = song == null ? "" : "♪ " + song.author() + " - " + song.title();
        if (font.width(songLine) > 220) songLine = Ui.fit(font, songLine, 220);

        int textW = font.width(text);
        int w = Math.max(textW, font.width(songLine)) + 18;
        int h = songLine.isEmpty() ? 18 : 30;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw - w - 8 + (int) ((1f - e) * 16f);
        int y = sh - h - 8;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(0xFFFFFF, 0.10f * e));
        g.fill(x, y, x + w, y + h, argb(0x0E0E14, 0.78f * e));
        g.fill(x, y, x + 2, y + h, argb(ACCENT, 0.95f * e));

        Ui.text(g, font, text, x + 9, y + 5, 0xFFFFFF, e);
        if (!songLine.isEmpty()) {
            Ui.text(g, font, songLine, x + 9, y + 18, 0x9A9AA8, e);
        }
    }
}
