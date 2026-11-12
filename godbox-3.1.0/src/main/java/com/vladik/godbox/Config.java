package com.vladik.godbox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * v3.1: настройки мода в config/godbox.properties. Системы читают числа через
 * Config.i/d/b("ключ", значение_по_умолчанию) — ключ сам появляется в файле при следующем сохранении.
 */
public final class Config {
    private static final Map<String, String> VALUES = new TreeMap<>();
    private static final Map<String, String> DEFAULTS = new TreeMap<>();
    private static boolean loaded;
    private static boolean dirty;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("godbox.properties");
    }

    public static synchronized void load() {
        loaded = true;
        VALUES.clear();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq > 0) VALUES.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
            }
        } catch (Exception e) {
            GodBox.LOG.warn("GodBox: не удалось прочитать настройки", e);
        }
    }

    /** Дописывает в файл ключи, которых там ещё нет (со значениями по умолчанию). */
    public static synchronized void flush() {
        if (!dirty) return;
        dirty = false;
        try {
            List<String> o = new ArrayList<>();
            o.add("# Настройки GodBox. Удалите строку, чтобы вернуть значение по умолчанию.");
            Map<String, String> all = new TreeMap<>(DEFAULTS);
            all.putAll(VALUES);
            for (var e : all.entrySet()) o.add(e.getKey() + "=" + e.getValue());
            Files.createDirectories(file().getParent());
            Files.write(file(), o, StandardCharsets.UTF_8);
        } catch (Exception e) {
            GodBox.LOG.warn("GodBox: не удалось записать настройки", e);
        }
    }

    private static synchronized String raw(String key, String def) {
        if (!loaded) load();
        if (!def.equals(DEFAULTS.put(key, def)) && !VALUES.containsKey(key)) dirty = true;
        return VALUES.getOrDefault(key, def);
    }

    public static int i(String key, int def) {
        try {
            return Integer.parseInt(raw(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static double d(String key, double def) {
        try {
            return Double.parseDouble(raw(key, Double.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean b(String key, boolean def) {
        return Boolean.parseBoolean(raw(key, Boolean.toString(def)));
    }
}
