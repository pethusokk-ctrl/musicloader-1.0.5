package com.musicloader;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Общие функции рисования для всех экранов мода. */
public final class Ui {
    public static final int ACCENT = 0x7FD400;

    private Ui() {}

    public static int argb(int rgb, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        double rr = r + 0.5;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(rr * rr - (double) dy * dy));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb, float a) {
        if (a < 0.08f) return;
        g.text(font, s, x, y, argb(rgb, a), true);
    }

    public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int rgb, float a) {
        text(g, font, s, cx - font.width(s) / 2, y, rgb, a);
    }

    public static String fit(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    public static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static boolean overCircle(int mx, int my, int cx, int cy, int r) {
        int dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** Позиция мыши по X в координатах интерфейса. */
    public static double mouseGuiX() {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        return mc.mouseHandler.xpos() * win.getGuiScaledWidth() / (double) win.getScreenWidth();
    }

    /** Фон как на логотипе: сверху чёрный, к низу плавно зелёный. Рисует строки от y0 до y1. */
    public static void gradient(GuiGraphicsExtractor g, int x0, int x1, int y0, int y1, int screenH, float alpha) {
        if (alpha <= 0.003f) return;
        for (int y = Math.max(0, y0); y < y1; y++) {
            float t = (y / (float) screenH - 0.45f) / 0.55f;
            t = Math.max(0f, Math.min(1f, t));
            int r = (int) (0x4E * t);
            int gr = (int) (0x79 * t);
            int b = (int) (0x29 * t);
            g.fill(x0, y, x1, y + 1, argb((r << 16) | (gr << 8) | b, alpha));
        }
    }
}
