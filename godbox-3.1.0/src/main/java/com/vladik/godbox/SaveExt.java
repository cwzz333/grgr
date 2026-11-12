package com.vladik.godbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * v3.1: расширения сохранения. Новая система регистрирует себя один раз:
 * SaveExt.register("tag", () -> строки, поля -> загрузка, очистка).
 * В файле её строки выглядят как "@tag|поле1|поле2"; в load приходит массив без "@tag".
 * Поля не должны содержать символ '|' и переводы строк.
 */
public final class SaveExt {
    /** Версия формата godbox_state.txt: 1 = до 3.0 включительно, 2 = с расширениями "@tag". */
    public static final int FORMAT = 2;

    private record Ext(Supplier<List<String>> save, Consumer<String[]> load, Runnable reset) {}

    private static final Map<String, Ext> EXTS = new LinkedHashMap<>();
    /** Версия формата загруженного файла (0 = файла не было). */
    public static int loadedFormat;

    private SaveExt() {}

    public static void register(String tag, Supplier<List<String>> save, Consumer<String[]> load, Runnable reset) {
        EXTS.put(tag, new Ext(save, load, reset));
    }

    static void saveAll(List<String> out) {
        for (var e : EXTS.entrySet()) {
            try {
                for (String line : e.getValue().save().get()) out.add("@" + e.getKey() + "|" + line);
            } catch (Exception ex) {
                GodBox.LOG.error("GodBox: сохранение '{}' не удалось", e.getKey(), ex);
            }
        }
    }

    /** Сбрасывает состояние расширений перед чтением файла. */
    static void resetAll() {
        for (var e : EXTS.entrySet()) {
            try {
                e.getValue().reset().run();
            } catch (Exception ex) {
                GodBox.LOG.error("GodBox: сброс '{}' не удался", e.getKey(), ex);
            }
        }
    }

    /** a[0] = "@tag"; неизвестные теги пропускаются (файл от более новой версии). */
    static void load(String[] a) {
        Ext x = EXTS.get(a[0].substring(1));
        if (x == null) return;
        String[] rest = new String[a.length - 1];
        System.arraycopy(a, 1, rest, 0, rest.length);
        x.load().accept(rest);
    }
}
