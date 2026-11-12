package com.vladik.godbox.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** v3.0: переназначаемые клавиши режима бога (Настройки → Управление → GodBox). */
public final class GodKeys {
    public static KeyBinding MAP, WORLD_MAP, CODEX, STATS, HELP, ERAS;

    private GodKeys() {}

    private static KeyBinding reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.godbox." + id, InputUtil.Type.KEYSYM, key, "category.godbox"));
    }

    public static void init() {
        MAP = reg("minimap", GLFW.GLFW_KEY_M);
        WORLD_MAP = reg("worldmap", GLFW.GLFW_KEY_N);
        CODEX = reg("codex", GLFW.GLFW_KEY_H);
        STATS = reg("stats", GLFW.GLFW_KEY_K);
        HELP = reg("help", GLFW.GLFW_KEY_I);
        ERAS = reg("eras", GLFW.GLFW_KEY_J);
    }

    public static boolean is(KeyBinding k, int key, int scan) {
        return k != null && k.matchesKey(key, scan);
    }
}
