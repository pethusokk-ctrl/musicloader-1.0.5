package com.musicloader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Открывает экран. В новых версиях Minecraft метод переименовывали, поэтому ищем его по имени. */
public final class ScreenUtil {
    private ScreenUtil() {}

    private interface Getter { Object get() throws ReflectiveOperationException; }

    private static Getter screenGetter;
    private static boolean screenSearched = false;

    /** Открыт ли сейчас какой-нибудь экран (меню, инвентарь). Имя поля менялось между версиями. */
    public static boolean hasScreen() {
        return currentScreen() != null;
    }

    /** Текущий открытый экран или null. */
    public static Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (!screenSearched) {
            screenSearched = true;
            screenGetter = findScreenGetter(mc);
        }
        if (screenGetter == null) return null;
        try {
            Object o = screenGetter.get();
            return o instanceof Screen ? (Screen) o : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Getter findScreenGetter(Minecraft mc) {
        try {
            Field f = Minecraft.class.getField("screen");
            return () -> f.get(mc);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method m = Minecraft.class.getMethod("screen");
            return () -> m.invoke(mc);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            try {
                Method m = gui.getClass().getMethod("screen");
                return () -> m.invoke(gui);
            } catch (ReflectiveOperationException ignored) {
            }
            Field f = gui.getClass().getField("screen");
            return () -> f.get(gui);
        } catch (ReflectiveOperationException ignored) {
        }
        MusicLoaderClient.LOGGER.warn("Не нашёл способ узнать текущий экран");
        return null;
    }

    public static void open(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Method m = Minecraft.class.getMethod("setScreenAndShow", Screen.class);
            m.invoke(mc, screen);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method m = Minecraft.class.getMethod("setScreen", Screen.class);
            m.invoke(mc, screen);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field f = Minecraft.class.getField("gui");
            Object gui = f.get(mc);
            Method m = gui.getClass().getMethod("setScreen", Screen.class);
            m.invoke(gui, screen);
        } catch (ReflectiveOperationException e) {
            MusicLoaderClient.LOGGER.error("Не удалось открыть экран", e);
        }
    }
}
