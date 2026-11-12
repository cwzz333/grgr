package com.vladik.godbox;

import com.vladik.godbox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/** Статистика королевств для окна (K). */
public final class Stats {
    private Stats() {}

    static String c(Kingdoms.K k) {
        return "\u00a7" + k.color.getCode();
    }

    static String block(Kingdoms.K k) {
        Civ.City c = Civ.city(k);
        int vill = 0, kids = 0, war = 0;
        for (MobEntity m : k.members) {
            if (!m.isAlive()) continue;
            if (m instanceof VillagerEntity v) {
                vill++;
                if (v.isBaby()) kids++;
            } else war++;
        }
        int[] j = Civ.counts(k);
        int built = 0;
        for (Civ.Building b : c.buildings) if (b.done && b.kind != Civ.Kind.QUARRY) built++;
        StringBuilder s = new StringBuilder();
        s.append(c(k)).append("\u00a7l").append(k.title).append('\n');
        s.append("\u00a77Раса: \u00a7f").append(Races.describe(k)).append('\n');
        s.append("\u00a77Эпоха: \u00a7f").append(Ages.name(c)).append(c.castle ? " \u00a76(замок)" : "").append('\n');
        s.append("\u00a77").append(Life.describeKing(k)).append('\n');
        s.append("\u00a77Наука: \u00a7b").append(Tech.info(k)).append('\n');
        s.append("\u00a77Жителей: \u00a7f").append(vill).append("\u00a77 (детей ").append(kids).append("), воинов: \u00a7f").append(war).append('\n');
        s.append("\u00a77Лесорубы ").append(j[0]).append(", шахтёры ").append(j[1]).append(", фермеры ").append(j[2])
            .append(", строители ").append(j[3]).append(", рыбаки ").append(j[4]).append('\n');
        s.append("\u00a77Склад: \u00a76дерево ").append(c.wood).append(" \u00a77камень ").append(c.stone)
            .append(" \u00a7aеда ").append(c.food).append(" \u00a7bруда ").append(c.ore).append('\n');
        s.append("\u00a77Построек: \u00a7f").append(built).append("\u00a77 (домов ").append(c.count(Civ.Kind.HOUSE) + c.count(Civ.Kind.MANOR))
            .append(", ферм ").append(c.count(Civ.Kind.FARM)).append(", храмов ").append(c.count(Civ.Kind.TEMPLE)).append(")\n");
        s.append("\u00a77Вера: \u00a7d").append(Faith.religion(c)).append(c.religion >= 0 ? " \u00a77(" + c.faith + ")" : "").append('\n');
        s.append("\u00a77Культура: \u00a7e").append(Faith.culture(c)).append('\n');
        s.append("\u00a77Казна: \u00a76").append(Economy.info(k)).append("\u00a77, \u00a7f").append(Vassals.info(k)).append('\n');
        s.append("\u00a77Смута: \u00a7c").append(Unrest.unrest(k)).append("%\u00a77, героев: \u00a7e").append(Heroes.count(k)).append("\u00a77, трактов: \u00a7f").append(Highways.count(k)).append('\n');
        s.append("\u00a77Вера: \u00a7b").append(Prayers.info(k)).append("\u00a77, стадо: \u00a7a").append(Herds.count(k)).append('\n');
        s.append(Events.statusLine(k));
        s.append("\u00a77Здоровье: ").append(Plague.line(k)).append('\n');
        s.append(Treaties.stats(k));
        StringBuilder rel = new StringBuilder();
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive) continue;
            int r = Diplomacy.rel(k, o);
            String st = r == Diplomacy.WAR ? "\u00a7cвойна" : r == Diplomacy.ALLY ? "\u00a7aсоюз" : "\u00a77мир";
            if (rel.length() > 0) rel.append("\u00a77, ");
            rel.append(c(o)).append(o.title.split(" ")[0]).append(' ').append(st);
        }
        s.append("\u00a77Отношения: ").append(rel.length() == 0 ? "\u00a78соседей нет" : rel.toString());
        return s.toString();
    }

    public static String text() {
        StringBuilder s = new StringBuilder();
        boolean any = false;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world == null) continue;
            if (any) s.append('\u0001');
            s.append(block(k));
            any = true;
        }
        if (!any) s.append("\u00a77Королевств пока нет. Создай их во вкладке «Королевства».");
        s.append('\u0002');
        List<String> l = Chronicle.last(12);
        if (l.isEmpty()) s.append("\u00a78Летопись пока пуста");
        for (int i = l.size() - 1; i >= 0; i--) {
            s.append(l.get(i));
            if (i > 0) s.append('\n');
        }
        String out = s.toString();
        return out.length() > 30000 ? out.substring(0, 30000) : out;
    }

    public static void send(ServerPlayerEntity god, boolean open) {
        if (ServerPlayNetworking.canSend(god, Payloads.Stats.ID)) ServerPlayNetworking.send(god, new Payloads.Stats(open, text()));
    }
}
