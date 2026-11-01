package com.musicloader;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

import static com.musicloader.Ui.argb;

/** Приветствие при первом запуске: выбор языка -> "Мы рады, что вы с нами!" + логотип + ссылка на Discord. */
public class WelcomeScreen extends Screen {
    public static final String DISCORD = "https://discord.gg/w4GYP92ts";

    private static final String[] CODES = {"ru", "zh", "en", "pt", "de"};
    private static final String[] NAMES = {"Русский", "中文", "English", "Português (Brasil)", "Deutsch"};
    private static final String[] GREET = {
            "Мы рады, что вы с нами!",
            "我们很高兴您能加入我们！",
            "We're glad you're with us!",
            "Estamos felizes por você estar conosco!",
            "Wir freuen uns, dass du dabei bist!"
    };
    private static final String[] JOIN = {
            "Присоединяйтесь к нашему Discord-серверу",
            "欢迎加入我们的 Discord 服务器",
            "Join our Discord server",
            "Entre no nosso servidor do Discord",
            "Tritt unserem Discord-Server bei"
    };

    private static final int LANG = 0, GREET_PHASE = 1, OUT = 2;
    private static final long FADE_IN_MS = 1600;
    private static final long GREET_MS = 10000;
    private static final long FADE_OUT_MS = 4000;

    private static Identifier logoId;
    private static boolean logoFailed;

    private final Screen parent;
    private int phase = LANG;
    private long phaseStart = System.currentTimeMillis();
    private int chosen = 2;
    private boolean closing = false;

    public WelcomeScreen(Screen parent) {
        super(Component.literal("Music Loader"));
        this.parent = parent;
    }

    private static Identifier logo() {
        if (logoId != null || logoFailed) return logoId;
        try (InputStream in = WelcomeScreen.class.getResourceAsStream("/assets/musicloader/logo.png")) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath(MusicLoaderClient.MOD_ID, "welcome_logo");
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "musicloader logo", image));
            logoId = id;
        } catch (Exception e) {
            logoFailed = true;
            MusicLoaderClient.LOGGER.warn("Логотип не загрузился", e);
        }
        return logoId;
    }

    // ---------- раскладка ----------

    private int langBtnW() { return 190; }

    private int langBtnX() { return (this.width - langBtnW()) / 2; }

    private int langBtnY(int i) {
        int total = 5 * 22 + 4 * 6;
        return this.height / 2 - total / 2 + 18 + i * 28;
    }

    private int logoSize() { return Math.min((int) (this.height * 0.34), 120); }

    private int logoY() { return (int) (this.height * 0.10); }

    private int greetY() { return logoY() + logoSize() + 14; }

    private String link() { return DISCORD; }

    private int linkW() { return this.font.width(link()); }

    private int linkX() { return (this.width - linkW()) / 2; }

    private int linkY() { return greetY() + 34; }

    // ---------- виджеты (невидимые кнопки для кликов) ----------

    private void hit(int x, int y, int w, int h, Runnable r) {
        Button b = Button.builder(Component.empty(), btn -> r.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        this.addRenderableWidget(b);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        if (phase == LANG) {
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                hit(langBtnX(), langBtnY(i), langBtnW(), 22, () -> choose(idx));
            }
        } else if (phase == GREET_PHASE) {
            addLinkHit();
        }
    }

    private void addLinkHit() {
        hit(linkX() - 2, linkY() - 2, linkW() + 4, 14, () -> {
            Sfx.click();
            openUrl(link());
        });
    }

    private void choose(int idx) {
        Sfx.click();
        chosen = idx;
        MusicSettings.lang = CODES[idx];
        MusicSettings.save();
        phase = GREET_PHASE;
        phaseStart = System.currentTimeMillis();
        this.clearWidgets();
        addLinkHit();
    }

    private static void openUrl(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
        } catch (Throwable t) {
            MusicLoaderClient.LOGGER.warn("Не удалось открыть ссылку {}", url, t);
        }
    }

    // ---------- рисунок ----------

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Font font = this.font;
        long now = System.currentTimeMillis();
        long dt = now - phaseStart;

        boolean hoverLink = phase == GREET_PHASE
                && Ui.over(mouseX, mouseY, linkX() - 2, linkY() - 2, linkW() + 4, 14);

        float ov;       // общая прозрачность экрана
        float content;  // прозрачность содержимого страницы
        if (phase == LANG) {
            ov = ease(dt / (float) FADE_IN_MS);
            content = ov;
        } else if (phase == GREET_PHASE) {
            ov = 1f;
            content = ease(dt / 1500f);
            if (dt > GREET_MS && !hoverLink) {
                phase = OUT;
                phaseStart = now;
                this.clearWidgets();
            }
        } else {
            float k = dt / (float) FADE_OUT_MS;
            ov = 1f - ease(k);
            content = ov;
            if (k >= 1f && !closing) {
                closing = true;
                this.onClose();
                return;
            }
        }

        // фон как на логотипе: чёрный сверху, зелёный снизу
        Ui.gradient(g, 0, this.width, 0, this.height, this.height, ov);

        if (phase == LANG) {
            drawLanguagePage(g, font, mouseX, mouseY, content);
        } else {
            drawGreetPage(g, font, hoverLink, content, ov);
        }
    }

    private void drawLanguagePage(GuiGraphicsExtractor g, Font font, int mx, int my, float a) {
        Ui.centered(g, font, "Выберите язык", this.width / 2, langBtnY(0) - 30, 0xFFFFFF, a);
        Ui.centered(g, font, Ui.fit(font, "Choose language  ·  选择语言  ·  Escolha o idioma  ·  Sprache wählen",
                this.width - 20), this.width / 2, langBtnY(0) - 18, 0xA0A0B0, a);

        for (int i = 0; i < 5; i++) {
            int x = langBtnX();
            int y = langBtnY(i);
            boolean hov = Ui.over(mx, my, x, y, langBtnW(), 22);
            Ui.box(g, x, y, langBtnW(), 22,
                    argb(hov ? 0x2E3A1A : 0x101014, 0.80f * a),
                    argb(hov ? Ui.ACCENT : 0xFFFFFF, hov ? 0.95f * a : 0.25f * a));
            Ui.centered(g, font, NAMES[i], this.width / 2, y + 7, hov ? 0xFFFFFF : 0xDDDDDD, a);
        }
    }

    private void drawGreetPage(GuiGraphicsExtractor g, Font font, boolean hoverLink, float a, float ov) {
        int size = logoSize();
        int lx = (this.width - size) / 2;
        int ly = logoY();

        Identifier id = logo();
        if (id != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, lx, ly, 0f, 0f, size, size, size, size);
            // "шторка" цвета фона плавно открывает логотип
            Ui.gradient(g, lx, lx + size, ly, ly + size, this.height, 1f - a);
        }

        Ui.centered(g, font, GREET[chosen], this.width / 2, greetY(), 0xFFFFFF, a);
        Ui.centered(g, font, Ui.fit(font, JOIN[chosen], this.width - 20), this.width / 2, greetY() + 16,
                0xB8B8C8, a);

        int lcol = hoverLink ? 0xA9C4FF : 0x7FA6FF;
        Ui.text(g, font, link(), linkX(), linkY(), lcol, a);
        if (hoverLink) {
            g.fill(linkX(), linkY() + 10, linkX() + linkW(), linkY() + 11, argb(lcol, a));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        ScreenUtil.open(parent);
    }
}
