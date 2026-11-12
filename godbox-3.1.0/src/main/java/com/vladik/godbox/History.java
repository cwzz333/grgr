package com.vladik.godbox;

import com.vladik.godbox.net.HistoryPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v3.1: история мира. Раз в history.interval тиков записывает кадр: год, день и для каждого живого
 * королевства — те же города и радиусы, что Borders шлёт на карту, население и флаги (война, вассал, империя).
 * К кадру прикладываются новые строки летописи. Клиент проигрывает кадры на карте мира.
 */
public final class History {
    /** Флаги королевства в кадре. */
    static final int F_WAR = 1, F_VASSAL = 2, F_EMPIRE = 4;
    private static final int NOTES = 4, PER_PACKET = 40, PACKET_CHARS = 100_000;

    /** ks: по строке на королевство "id;rgb;pop;flags;era;title;x,z,r,name;x,z,r,name…" (первый город — столица). */
    private record Frame(long time, long year, long day, List<String> ks, List<String> notes) {}

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final Map<UUID, Integer> ASKED = new HashMap<>();
    private static int t;
    /** Сколько строк летописи уже учтено; -1 = синхронизировать при первом тике. */
    private static long seen = -1;

    private History() {}

    public static void init() {
        SaveExt.register("history", History::save, History::load, History::reset);
    }

    public static void tick(MinecraftServer s) {
        t++;
        if (seen < 0) seen = Chronicle.seq;
        int every = Math.max(100, Config.i("history.interval", 1200));
        if (t % every != 0 || s.getOverworld() == null) return;
        List<String> ks = new ArrayList<>();
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null) continue;
            ks.add(kingdom(k));
        }
        if (ks.isEmpty()) return;
        long fresh = Chronicle.seq - seen;
        seen = Chronicle.seq;
        List<String> notes = new ArrayList<>();
        if (fresh > 0) for (String l : Chronicle.last((int) Math.min(NOTES, fresh))) notes.add(Borders.clean(l).replace('\r', ' '));
        if (!FRAMES.isEmpty() && notes.isEmpty() && FRAMES.get(FRAMES.size() - 1).ks.equals(ks)) return;
        long time = s.getOverworld().getTime();
        FRAMES.add(new Frame(time, time / Life.YEAR + 1, Chronicle.day(), ks, notes));
        thin();
    }

    private static String kingdom(Kingdoms.K k) {
        Civ.City c = Civ.city(k);
        int pop = 0;
        for (MobEntity m : k.members) if (m.isAlive()) pop++;
        int flags = (Diplomacy.atWar(k) ? F_WAR : 0) | (Vassals.lord(k) != null ? F_VASSAL : 0) | (Vassals.count(k) >= 2 ? F_EMPIRE : 0);
        StringBuilder b = new StringBuilder();
        b.append(k.name()).append(';').append(k.rgb).append(';').append(pop).append(';').append(flags).append(';')
                .append(Math.max(0, c.era)).append(';').append(clean(k.title));
        city(b, k, k.capital, Borders.radius(c, true));
        int rt = Borders.radius(c, false);
        for (BlockPos p : new ArrayList<>(Conquest.towns(k))) city(b, k, p, rt);
        return b.toString();
    }

    private static void city(StringBuilder b, Kingdoms.K k, BlockPos p, int r) {
        b.append(';').append(p.getX()).append(',').append(p.getZ()).append(',').append(r).append(',').append(clean(Borders.cityName(k, p)));
    }

    /** Поля кадра не должны содержать разделители. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace(';', ':').replace(',', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Когда кадров слишком много — прореживаем старую половину, чтобы шкала покрывала всю историю. */
    private static void thin() {
        int max = Math.max(20, Config.i("history.maxFrames", 600));
        if (FRAMES.size() <= max) return;
        int half = FRAMES.size() / 2;
        List<Frame> keep = new ArrayList<>(FRAMES.size());
        for (int i = 0; i < FRAMES.size(); i++) if (i >= half || i % 2 == 0) keep.add(FRAMES.get(i));
        FRAMES.clear();
        FRAMES.addAll(keep);
    }

    private static String line(Frame f) {
        StringBuilder b = new StringBuilder();
        b.append(f.time).append('|').append(f.year).append('|').append(f.day).append('|').append(f.ks.size());
        for (String k : f.ks) b.append('|').append(k);
        for (String n : f.notes) b.append('|').append(n);
        return b.toString();
    }

    // ---------------- сохранение ----------------
    private static List<String> save() {
        List<String> out = new ArrayList<>(FRAMES.size());
        for (Frame f : FRAMES) out.add(line(f));
        return out;
    }

    private static void load(String[] a) {
        try {
            if (a.length < 4) return;
            int n = Integer.parseInt(a[3]);
            if (n < 0 || 4 + n > a.length) return;
            List<String> ks = new ArrayList<>();
            for (int i = 0; i < n; i++) ks.add(a[4 + i]);
            List<String> notes = new ArrayList<>();
            for (int i = 4 + n; i < a.length && notes.size() < NOTES; i++) if (!a[i].isEmpty()) notes.add(a[i]);
            FRAMES.add(new Frame(Long.parseLong(a[0]), Long.parseLong(a[1]), Long.parseLong(a[2]), ks, notes));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void reset() {
        FRAMES.clear();
        ASKED.clear();
        seen = -1;
    }

    public static void clear() {
        reset();
    }

    // ---------------- сеть ----------------
    /** Запрос клиента: историю получают только боги, не чаще раза в секунду. */
    public static void request(ServerPlayerEntity p) {
        if (p == null || !GodManager.isGod(p) || !ServerPlayNetworking.canSend(p, HistoryPayloads.HistoryData.ID)) return;
        int tick = p.getServer() == null ? 0 : p.getServer().getTicks();
        Integer last = ASKED.get(p.getUuid());
        if (last != null && tick - last >= 0 && tick - last < 20) return;
        ASKED.put(p.getUuid(), tick);
        List<String> parts = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Frame f : FRAMES) {
            String l = line(f);
            if (n > 0 && (n >= PER_PACKET || b.length() + l.length() > PACKET_CHARS)) {
                parts.add(b.toString());
                b.setLength(0);
                n = 0;
            }
            if (l.length() > PACKET_CHARS) continue;
            if (n > 0) b.append('\n');
            b.append(l);
            n++;
        }
        if (n > 0 || parts.isEmpty()) parts.add(b.toString());
        for (int i = 0; i < parts.size(); i++)
            ServerPlayNetworking.send(p, new HistoryPayloads.HistoryData(i, parts.size(), parts.get(i)));
    }

    /** Сила «История мира»: открыть у бога карту в режиме истории. */
    static void power(ServerPlayerEntity god) {
        if (god == null) return;
        if (ServerPlayNetworking.canSend(god, HistoryPayloads.HistoryOpen.ID))
            ServerPlayNetworking.send(god, new HistoryPayloads.HistoryOpen());
        else
            god.sendMessage(Text.literal("История мира: откройте карту мира и нажмите «История»").formatted(Formatting.GOLD), false);
    }
}
