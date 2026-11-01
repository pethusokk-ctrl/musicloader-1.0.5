package com.musicloader;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.musicloader.Ui.ACCENT;
import static com.musicloader.Ui.argb;

/** Настройки музыки: громкость песни, звуковые эффекты кнопок (вкл/выкл и громкость). */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private int px, py, pw = 250;
    private int tx, tw;
    private int musicTrackY, sfxToggleY, sfxTrackY, doneY;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Настройки музыки"));
        this.parent = parent;
    }

    private void hit(int x, int y, int w, int h, Runnable r) {
        Button b = Button.builder(Component.empty(), btn -> r.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        this.addRenderableWidget(b);
    }

    @Override
    protected void init() {
        px = (this.width - pw) / 2;
        py = this.height / 2 - 78;
        tx = px + 34;
        tw = pw - 68;
        musicTrackY = py + 44;
        sfxToggleY = py + 70;
        sfxTrackY = py + 112;
        doneY = py + 134;

        // громкость музыки
        hit(px + 12, musicTrackY - 4, 16, 16, () -> changeMusic(MusicSettings.musicVolume - 0.05f));
        hit(px + pw - 28, musicTrackY - 4, 16, 16, () -> changeMusic(MusicSettings.musicVolume + 0.05f));
        hit(tx, musicTrackY - 4, tw, 16, () -> changeMusic((float) ((Ui.mouseGuiX() - tx) / tw)));

        // звуковые эффекты вкл/выкл
        hit(px + 12, sfxToggleY, pw - 24, 18, () -> {
            MusicSettings.sfxOn = !MusicSettings.sfxOn;
            MusicSettings.save();
            Sfx.click();
        });

        // громкость эффектов
        hit(px + 12, sfxTrackY - 4, 16, 16, () -> changeSfx(MusicSettings.sfxVolume - 0.05f));
        hit(px + pw - 28, sfxTrackY - 4, 16, 16, () -> changeSfx(MusicSettings.sfxVolume + 0.05f));
        hit(tx, sfxTrackY - 4, tw, 16, () -> changeSfx((float) ((Ui.mouseGuiX() - tx) / tw)));

        // готово
        hit(px + 12, doneY, pw - 24, 18, () -> {
            Sfx.click();
            this.onClose();
        });
    }

    private void changeMusic(float v) {
        MusicSettings.musicVolume = Math.round(MusicSettings.clamp(v) * 100f) / 100f;
        MusicSettings.save();
        MusicPlayer.applyVolume();
        Sfx.click();
    }

    private void changeSfx(float v) {
        MusicSettings.sfxVolume = Math.round(MusicSettings.clamp(v) * 100f) / 100f;
        MusicSettings.save();
        Sfx.click();
    }

    private void slider(GuiGraphicsExtractor g, Font font, String label, int trackY, float value,
                        int mx, int my) {
        Ui.text(g, font, label, px + 12, trackY - 14, 0xFFFFFF, 1f);
        String pct = Math.round(value * 100) + "%";
        Ui.text(g, font, pct, px + pw - 12 - font.width(pct), trackY - 14, ACCENT, 1f);

        boolean hm = Ui.over(mx, my, px + 12, trackY - 4, 16, 16);
        boolean hp = Ui.over(mx, my, px + pw - 28, trackY - 4, 16, 16);
        Ui.box(g, px + 12, trackY - 4, 16, 16, argb(hm ? 0x4A4A58 : 0x2A2A34, 1f), argb(0x4A4A58, 1f));
        Ui.box(g, px + pw - 28, trackY - 4, 16, 16, argb(hp ? 0x4A4A58 : 0x2A2A34, 1f), argb(0x4A4A58, 1f));
        Ui.centered(g, font, "-", px + 20, trackY, 0xFFFFFF, 1f);
        Ui.centered(g, font, "+", px + pw - 20, trackY, 0xFFFFFF, 1f);

        g.fill(tx, trackY, tx + tw, trackY + 8, argb(0x34343E, 1f));
        int fill = (int) (tw * value);
        if (fill > 0) g.fill(tx, trackY, tx + fill, trackY + 8, argb(ACCENT, 1f));
        Ui.circle(g, tx + fill, trackY + 4, 5, argb(0xFFFFFF, 1f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Font font = this.font;

        // карточка
        g.fill(px - 1, py - 1, px + pw + 1, py + 160 + 1, argb(0xFFFFFF, 0.12f));
        g.fill(px, py, px + pw, py + 160, argb(0x0E0E14, 0.95f));
        g.fill(px, py, px + pw, py + 2, argb(ACCENT, 0.9f));

        Ui.centered(g, font, "Настройки музыки", this.width / 2, py + 10, 0xFFFFFF, 1f);

        slider(g, font, "Громкость песни", musicTrackY, MusicSettings.musicVolume, mouseX, mouseY);

        // тумблер эффектов
        boolean on = MusicSettings.sfxOn;
        boolean hov = Ui.over(mouseX, mouseY, px + 12, sfxToggleY, pw - 24, 18);
        Ui.box(g, px + 12, sfxToggleY, pw - 24, 18,
                argb(on ? 0x2C4A00 : (hov ? 0x34343E : 0x24242C), 1f), argb(on ? ACCENT : 0x4A4A58, 1f));
        Ui.centered(g, font, on ? "Звуковые эффекты кнопок: вкл" : "Звуковые эффекты кнопок: выкл",
                this.width / 2, sfxToggleY + 5, on ? ACCENT : 0xCCCCCC, 1f);

        slider(g, font, "Громкость эффектов", sfxTrackY, MusicSettings.sfxVolume, mouseX, mouseY);

        boolean hd = Ui.over(mouseX, mouseY, px + 12, doneY, pw - 24, 18);
        Ui.box(g, px + 12, doneY, pw - 24, 18, argb(hd ? 0x34343E : 0x24242C, 1f),
                argb(hd ? ACCENT : 0x4A4A58, 1f));
        Ui.centered(g, font, "Готово", this.width / 2, doneY + 5, 0xFFFFFF, 1f);
    }

    @Override
    public void onClose() {
        ScreenUtil.open(parent);
    }
}
