package com.musicloader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicLoaderClient implements ClientModInitializer {
    public static final String MOD_ID = "musicloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static int titleTicks = 0;

    @Override
    public void onInitializeClient() {
        try {
            MusicLibrary.init();
        } catch (Throwable t) {
            LOGGER.error("Не удалось загрузить библиотеку музыки", t);
        }
        try {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MusicPlayer.shutdown());
            Runtime.getRuntime().addShutdownHook(new Thread(MusicPlayer::shutdown, "musicloader-shutdown"));
        } catch (Throwable t) {
            LOGGER.error("Не удалось зарегистрировать остановку музыки", t);
        }
        try {
            HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                    Identifier.fromNamespaceAndPath(MOD_ID, "text"),
                    (graphics, tickCounter) -> MusicHud.render(graphics));
        } catch (Throwable t) {
            LOGGER.error("Не удалось подключить текст в игре", t);
        }
        try {
            // приветствие при самом первом запуске: ждём, пока появится главное меню
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (MusicSettings.welcomeShown) return;
                Screen current = ScreenUtil.currentScreen();
                if (current instanceof TitleScreen) {
                    if (++titleTicks >= 30) {
                        MusicSettings.welcomeShown = true;
                        MusicSettings.save();
                        ScreenUtil.open(new WelcomeScreen(current));
                    }
                } else {
                    titleTicks = 0;
                }
            });
        } catch (Throwable t) {
            LOGGER.error("Не удалось подключить приветствие", t);
        }
        LOGGER.info("Music Loader запущен. Папка: {}", MusicLibrary.DIR);
    }
}
