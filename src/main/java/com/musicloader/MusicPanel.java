package com.musicloader;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.musicloader.Ui.ACCENT;
import static com.musicloader.Ui.argb;

/** Музыкальная панель на экране паузы. Кнопки - настоящие (невидимые) Button, рисунок - свой. */
public final class MusicPanel {
    private static final Map<Path, Identifier> ICONS = new HashMap<>();
    private static final Map<Path, Boolean> ICON_FAILED = new HashMap<>();

    private MusicPanel() {}

    /** Все размеры панели в одном месте, чтобы кнопки и рисунок совпадали. */
    private record Layout(int sw, int sh, int cw, int x0, int y0, int cx, int r, int cy, int textY,
                          int barX, int barY, int barW, int loopY, int addY, int setY, int y1,
                          int playY, int arrowDx) {
        static Layout of(int sw, int sh) {
            int leftSpace = sw / 2 - 102;
            int cw = Math.max(112, Math.min(170, leftSpace - 16));
            int x0 = 8;
            int y0 = 6;
            int cx = x0 + cw / 2;
            int r = Math.max(16, Math.min(30, Math.min(cw / 2 - 24, (sh - 6 - 214) / 2)));
            int cy = y0 + 88 + r;
            int textY = cy + r + 8;
            int barY = cy + r + 40;
            int barX = x0 + 8;
            int barW = cw - 16;
            int loopY = barY + 16;
            int addY = loopY + 20;
            int setY = addY + 20;
            int y1 = setY + 16 + 8;
            int playY = cy - r - 14;
            int arrowDx = r + 17;
            return new Layout(sw, sh, cw, x0, y0, cx, r, cy, textY, barX, barY, barW, loopY, addY, setY,
                    y1, playY, arrowDx);
        }
    }

    public static void build(Screen screen, Consumer<AbstractWidget> add) {
        Layout L = Layout.of(screen.width, screen.height);

        hit(add, L.cx - 11, L.playY - 11, 22, 22, () -> {
            Sfx.click();
            MusicPlayer.togglePause();
        });
        hit(add, L.cx - L.arrowDx - 10, L.cy - 10, 20, 20, () -> {
            Sfx.click();
            MusicPlayer.previous();
        });
        hit(add, L.cx + L.arrowDx - 10, L.cy - 10, 20, 20, () -> {
            Sfx.click();
            MusicPlayer.next();
        });
        hit(add, L.barX, L.barY - 4, L.barW, 14, () -> {
            Sfx.click();
            MusicPlayer.seek((Ui.mouseGuiX() - L.barX) / (double) L.barW);
        });
        hit(add, L.x0 + 8, L.loopY, L.cw - 16, 16, () -> {
            Sfx.click();
            MusicPlayer.toggleLoop();
        });
        hit(add, L.x0 + 8, L.addY, L.cw - 16, 16, () -> {
            Sfx.click();
            ScreenUtil.open(new TextInputScreen(screen));
        });
        hit(add, L.x0 + 8, L.setY, L.cw - 16, 16, () -> {
            Sfx.click();
            ScreenUtil.open(new SettingsScreen(screen));
        });

        add.accept(new PanelWidget(L));
    }

    /** Настоящая кнопка (ловит клики), но невидимая: внешний вид рисует PanelWidget. */
    private static void hit(Consumer<AbstractWidget> add, int x, int y, int w, int h, Runnable action) {
        Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        add.accept(b);
    }

    private static Identifier iconFor(Path path) {
        if (path == null || ICON_FAILED.containsKey(path)) return null;
        Identifier cached = ICONS.get(path);
        if (cached != null) return cached;
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath(MusicLoaderClient.MOD_ID,
                    "icon/" + Integer.toHexString(path.toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "musicloader icon", image));
            ICONS.put(path, id);
            return id;
        } catch (Exception e) {
            MusicLoaderClient.LOGGER.warn("Иконка не загрузилась: {}", path);
            ICON_FAILED.put(path, true);
            return null;
        }
    }

    private static String time(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    // ---------- рисунок панели ----------

    private static final class PanelWidget extends AbstractWidget {
        private final Layout L;
        private final long start = System.currentTimeMillis();
        private boolean failed = false;
        private int lastHover = 0;

        PanelWidget(Layout L) {
            super(0, 0, 0, 0, Component.empty());
            this.L = L;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            if (failed) return;
            try {
                draw(g, mouseX, mouseY);
            } catch (Throwable t) {
                failed = true;
                MusicLoaderClient.LOGGER.error("Панель музыки отключена из-за ошибки", t);
            }
        }

        private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;

            // плавное появление: выезд слева + проявление
            float t = Math.min(1f, (System.currentTimeMillis() - start) / 420f);
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            int ox = (int) (-(1f - e) * 60f);
            float a = e;

            int cw = L.cw;
            int x0 = L.x0 + ox;
            int y0 = L.y0;
            int cx = L.cx + ox;
            int cy = L.cy;
            int r = L.r;
            int barX = L.barX + ox;
            int barW = L.barW;
            int playY = L.playY;
            int arrowDx = L.arrowDx;

            boolean playing = MusicPlayer.isPlaying();

            // что под мышкой (для звука наведения)
            boolean hoverPlay = Ui.overCircle(mouseX, mouseY, cx, playY, 11);
            boolean hoverL = Ui.overCircle(mouseX, mouseY, cx - arrowDx, cy, 10);
            boolean hoverR = Ui.overCircle(mouseX, mouseY, cx + arrowDx, cy, 10);
            boolean hoverBar = Ui.over(mouseX, mouseY, barX, L.barY - 4, barW, 14);
            boolean hoverLoop = Ui.over(mouseX, mouseY, x0 + 8, L.loopY, cw - 16, 16);
            boolean hoverAdd = Ui.over(mouseX, mouseY, x0 + 8, L.addY, cw - 16, 16);
            boolean hoverSet = Ui.over(mouseX, mouseY, x0 + 8, L.setY, cw - 16, 16);
            int hv = hoverPlay ? 1 : hoverL ? 2 : hoverR ? 3 : hoverBar ? 4 : hoverLoop ? 5
                    : hoverAdd ? 6 : hoverSet ? 7 : 0;
            if (hv != 0 && hv != lastHover && e > 0.9f) Sfx.hover();
            lastHover = hv;

            // карточка
            g.fill(x0 - 1, y0 - 1, x0 + cw + 1, L.y1 + 1, argb(0xFFFFFF, 0.10f * a));
            g.fill(x0, y0, x0 + cw, L.y1, argb(0x0E0E14, 0.88f * a));
            g.fill(x0, y0, x0 + cw, y0 + 2, argb(ACCENT, 0.9f * a));

            // инструкция
            Ui.text(g, font, "Инструкция", x0 + 8, y0 + 7, 0xFFFFFF, a);
            String[] help = {
                    "Положи .wav или .mp3",
                    "в папку musicloader",
                    "или перетащи в окно.",
                    "Иконка: .png с тем же",
                    "именем (не обязательно)"
            };
            for (int i = 0; i < help.length; i++) {
                Ui.text(g, font, Ui.fit(font, help[i], cw - 16), x0 + 8, y0 + 18 + i * 9, 0x9A9AA8, a);
            }

            // кружок
            Ui.circle(g, cx, cy, r, argb(playing ? ACCENT : 0x6A6A78, a));
            Ui.circle(g, cx, cy, r - 2, argb(0x1A1A22, a));

            var song = MusicPlayer.current();
            if (song != null && song.icon() != null && e > 0.6f) {
                Identifier icon = iconFor(song.icon());
                if (icon != null) {
                    int size = (int) (r * 1.4);
                    g.blit(RenderPipelines.GUI_TEXTURED, icon, cx - size / 2, cy - size / 2,
                            0f, 0f, size, size, size, size);
                }
            }

            // кнопка пауза / пуск
            Ui.circle(g, cx, playY, 11, argb(hoverPlay ? 0xA6EF2A : ACCENT, a));
            int ic = argb(0x101010, a);
            if (playing) {
                g.fill(cx - 4, playY - 5, cx - 1, playY + 5, ic);
                g.fill(cx + 1, playY - 5, cx + 4, playY + 5, ic);
            } else {
                for (int dx = 0; dx < 8; dx++) {
                    int hh = 5 - (dx * 5) / 8;
                    g.fill(cx - 3 + dx, playY - hh, cx - 2 + dx, playY + hh, ic);
                }
            }

            // стрелки
            Ui.circle(g, cx - arrowDx, cy, 10, argb(hoverL ? 0x4A4A58 : 0x2A2A34, a));
            Ui.circle(g, cx + arrowDx, cy, 10, argb(hoverR ? 0x4A4A58 : 0x2A2A34, a));
            Ui.centered(g, font, "<", cx - arrowDx, cy - 4, 0xFFFFFF, a);
            Ui.centered(g, font, ">", cx + arrowDx, cy - 4, 0xFFFFFF, a);

            // название, автор, таймкод
            String title = song == null ? "Нет песни" : song.title();
            String author = song == null ? "Выбери стрелками" : song.author();
            if (MusicPlayer.isLoading()) author = "Загрузка...";
            Ui.centered(g, font, Ui.fit(font, title, cw - 12), cx, L.textY, 0xFFFFFF, a);
            Ui.centered(g, font, Ui.fit(font, author, cw - 12), cx, L.textY + 10, 0x9A9AA8, a);
            Ui.centered(g, font, time(MusicPlayer.positionSeconds()) + " / " + time(MusicPlayer.lengthSeconds()),
                    cx, L.textY + 20, playing ? ACCENT : 0x9A9AA8, a);

            // полоса прогресса
            g.fill(barX, L.barY, barX + barW, L.barY + 6, argb(hoverBar ? 0x45454F : 0x34343E, a));
            int fill = (int) (barW * MusicPlayer.progress());
            if (fill > 0) g.fill(barX, L.barY, barX + fill, L.barY + 6, argb(ACCENT, a));
            Ui.circle(g, barX + fill, L.barY + 3, 4, argb(0xFFFFFF, a));

            // кнопки-таблетки
            boolean loop = MusicPlayer.isLoop();
            Ui.box(g, x0 + 8, L.loopY, cw - 16, 16,
                    argb(loop ? 0x2C4A00 : (hoverLoop ? 0x34343E : 0x24242C), a),
                    argb(loop ? ACCENT : 0x4A4A58, a));
            Ui.centered(g, font, loop ? "Повтор: вкл" : "Повтор: выкл", cx, L.loopY + 4,
                    loop ? ACCENT : 0xCCCCCC, a);

            Ui.box(g, x0 + 8, L.addY, cw - 16, 16,
                    argb(hoverAdd ? 0x34343E : 0x24242C, a), argb(hoverAdd ? ACCENT : 0x4A4A58, a));
            Ui.centered(g, font, "Добавить текст", cx, L.addY + 4, 0xCCCCCC, a);

            Ui.box(g, x0 + 8, L.setY, cw - 16, 16,
                    argb(hoverSet ? 0x34343E : 0x24242C, a), argb(hoverSet ? ACCENT : 0x4A4A58, a));
            Ui.centered(g, font, Ui.fit(font, "Настройки музыки", cw - 20), cx, L.setY + 4, 0xCCCCCC, a);

            // подпись создателя в правом верхнем углу
            String credit = "creator:caat_coooll";
            int cwid = font.width(credit) + 14;
            int crx = L.sw - cwid - 8;
            Ui.box(g, crx, 6, cwid, 16, argb(0x0E0E14, 0.85f * a), argb(ACCENT, 0.9f * a));
            Ui.text(g, font, credit, crx + 7, 10, ACCENT, a);

            // свой текст внизу по центру
            String custom = MusicLibrary.cachedText();
            if (!custom.isEmpty()) {
                Ui.text(g, font, custom, (L.sw - font.width(custom)) / 2, L.sh - 14, 0xFFFFFF, a);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}
