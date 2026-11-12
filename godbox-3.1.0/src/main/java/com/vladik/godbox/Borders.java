package com.vladik.godbox;

import com.vladik.godbox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** v2.3: территории королевств для карты мира и мини-карты клиента. */
public final class Borders {
    private static int t;

    private Borders() {}

    static int radius(Civ.City c, boolean cap) {
        return (cap ? 44 : 30) + Math.min(10, Math.max(0, c.era)) * 2;
    }

    public static void tick(MinecraftServer s) {
        if (++t % 40 != 0) return;
        String d = null;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (!GodManager.isGod(p) || !ServerPlayNetworking.canSend(p, Payloads.Borders.ID)) continue;
            if (d == null) d = data();
            ServerPlayNetworking.send(p, new Payloads.Borders(d));
        }
    }

    private static final String[] PRE = {"Бел", "Вер", "Гор", "Дуб", "Зар", "Кам", "Лес", "Мир", "Нов", "Озер", "Рад", "Свет", "Тур", "Яр", "Вол", "Ель", "Сокол", "Злат", "Крас", "Ставр"};
    private static final String[] MID = {"", "о", "а", "и", "е"};
    private static final String[] SUF = {"град", "бург", "поль", "дол", "мир", "ск", "ица", "ов", "гор", "холм", "брод", "стан"};

    /** v3.0: стабильное имя города по королевству и координатам (одно и то же при каждом пакете). */
    static String cityName(Kingdoms.K k, BlockPos p) {
        long h = (k.ordinal() * 73856093L) ^ ((p.getX() >> 3) * 19349663L) ^ ((p.getZ() >> 3) * 83492791L);
        h ^= (h >>> 17);
        h *= 0x9E3779B97F4A7C15L;
        int a = (int) ((h >>> 8) & 0x7FFFFFFF), c = (int) ((h >>> 36) & 0x7FFFFFF);
        String pre = PRE[a % PRE.length], suf = SUF[c % SUF.length];
        String mid = MID[(a / PRE.length) % MID.length];
        if (pre.matches(".*[аеиоуыэюяь]$")) mid = "";
        return pre + mid + suf;
    }

    static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ');
    }

    static String data() {
        StringBuilder b = new StringBuilder();
        java.util.List<Kingdoms.K> alive = new java.util.ArrayList<>();
        for (Kingdoms.K k : Kingdoms.K.values()) if (k.alive && k.capital != null) alive.add(k);
        for (int i = 0; i < alive.size(); i++)
            for (int j = i + 1; j < alive.size(); j++) {
                Kingdoms.K a = alive.get(i), c = alive.get(j);
                int r = Diplomacy.rel(a, c);
                if (r == Diplomacy.WAR || r == Diplomacy.ALLY)
                    b.append(r == Diplomacy.WAR ? "W|" : "A|").append(a.capital.getX()).append('|').append(a.capital.getZ()).append('|')
                            .append(c.capital.getX()).append('|').append(c.capital.getZ()).append('\n');
            }
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null) continue;
            Civ.City c = Civ.city(k);
            int rc = radius(c, true), rt = radius(c, false);
            b.append("T|").append(k.capital.getX()).append('|').append(k.capital.getZ()).append('|').append(rc).append('|').append(k.rgb).append('|').append(clean(cityName(k, k.capital))).append("|1|").append(clean(k.title)).append('|').append(Math.max(0, c.era)).append('\n');
            for (BlockPos p : Conquest.towns(k))
                b.append("T|").append(p.getX()).append('|').append(p.getZ()).append('|').append(rt).append('|').append(k.rgb).append('|')
                        .append(clean(cityName(k, p))).append("|0|").append(clean(k.title)).append('|').append(Math.max(0, c.era)).append('\n');
            int pop = 0;
            for (MobEntity m : k.members) if (m.isAlive()) pop++;
            Kingdoms.K lord = Vassals.lord(k);
            int era = Math.max(0, Math.min(c.era, Eras.NAMES.length - 1));
            String leg = k.title + ": " + Eras.NAMES[era] + ", жителей " + pop + ", городов " + (1 + Conquest.towns(k).size())
                    + ", золото " + Economy.gold(k) + ", вера " + Prayers.faith(k) + "%"
                    + (lord != null ? ", вассал: " + lord.title : Vassals.count(k) >= 2 ? ", ИМПЕРИЯ" : "")
                    + (Diplomacy.atWar(k) ? ", ⚔ война" : "");
            b.append("L|").append(k.rgb).append('|').append(leg.replace('|', '/')).append('\n');
        }
        return b.toString();
    }
}
