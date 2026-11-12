package com.vladik.godbox.client;

import com.vladik.godbox.Mortal;
import com.vladik.godbox.Power;
import com.vladik.godbox.net.MortalPayloads;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** v3.1: последнее состояние режима «Смертный бог», присланное сервером, и цены сил для панели. */
public final class MortalClient {
    public static boolean on;
    public static int pool, cap = 1000, state, scale = 100, perMin;
    public static String title = "", progress = "";
    private static long updated;

    private MortalClient() {}

    public static void set(MortalPayloads.MortalHud h) {
        on = h.on();
        pool = h.pool();
        cap = Math.max(1, h.cap());
        title = h.title();
        progress = h.progress();
        state = h.state();
        scale = h.scale();
        perMin = h.perMin();
        updated = System.currentTimeMillis();
    }

    /** Сервер шлёт состояние дважды в секунду, пока режим включён; устаревшее считаем выключенным (другой мир). */
    public static boolean active() {
        return on && System.currentTimeMillis() - updated < 5000;
    }

    public static int costOf(Power p) {
        return (int) Math.ceil(Mortal.cost(p) * scale / 100.0);
    }

    public static boolean affordable(Power p) {
        return !active() || costOf(p) <= pool;
    }

    /** Добавляет к подсказке силы строку с ценой, если режим включён. */
    public static List<Text> tooltip(Power p, List<Text> lines) {
        if (!active()) return lines;
        List<Text> out = new ArrayList<>(lines);
        int c = costOf(p);
        Text line;
        if (c <= 0) line = Text.literal("✦ Бесплатно").formatted(Formatting.GREEN);
        else {
            String s = "✦ Цена: " + c + " божьей силы";
            if (p.continuous) s += " (+" + Math.max(1, (int) Math.ceil(c * Mortal.REPEAT)) + " за повтор)";
            line = c <= pool ? Text.literal(s).formatted(Formatting.GOLD)
                    : Text.literal(s + " — не хватает " + (c - pool)).formatted(Formatting.RED);
        }
        out.add(Math.min(2, out.size()), line);
        return out;
    }
}
