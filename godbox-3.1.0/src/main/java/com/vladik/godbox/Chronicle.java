package com.vladik.godbox;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Летопись мира: все важные события королевств. */
public final class Chronicle {
    static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static MinecraftServer server;
    /** v3.1: сколько строк записано за всё время (History берёт новые строки для кадров). */
    static long seq;

    private Chronicle() {}

    public static void tick(MinecraftServer s) {
        server = s;
    }

    static long day() {
        if (server == null || server.getOverworld() == null) return 1;
        return server.getOverworld().getTimeOfDay() / 24000L + 1;
    }

    /** Записать строку (можно с цветовыми кодами). */
    public static void log(String s) {
        if (s == null || s.isBlank()) return;
        String line = "\u00a78День " + day() + ": \u00a7r" + s;
        if (!LOG.isEmpty() && LOG.peekLast().equals(line)) return;
        LOG.addLast(line);
        seq++;
        while (LOG.size() > 200) LOG.removeFirst();
    }

    /** Событие королевства: в летопись и в чат. */
    static void say(MinecraftServer s, Kingdoms.K k, String msg) {
        if (s != null) server = s;
        log("\u00a7" + k.color.getCode() + k.title + "\u00a77 " + msg);
        if (server == null) return;
        Text t = Text.literal("[Летопись] ").formatted(Formatting.GOLD)
            .append(Text.literal(k.title).formatted(k.color))
            .append(Text.literal(" " + msg).formatted(Formatting.GRAY));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(t, false);
    }

    static List<String> last(int n) {
        List<String> all = new ArrayList<>(LOG);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    static void show(ServerPlayerEntity god) {
        god.sendMessage(Text.literal("=== Летопись мира ===").formatted(Formatting.GOLD), false);
        List<String> l = last(15);
        if (l.isEmpty()) god.sendMessage(Text.literal("Пока ничего не произошло.").formatted(Formatting.GRAY), false);
        for (String s : l) god.sendMessage(Text.literal(s), false);
    }

    public static void clear() {
        LOG.clear();
    }
}
