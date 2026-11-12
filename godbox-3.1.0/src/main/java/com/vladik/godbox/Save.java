package com.vladik.godbox;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Сохранение королевств, городов, эпох, дипломатии и летописи между заходами в мир. */
public final class Save {
    private static int timer;

    private Save() {}

    private static Path file(MinecraftServer s) {
        return s.getSavePath(WorldSavePath.ROOT).resolve("godbox_state.txt");
    }

    private static String clean(String s) {
        return s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static int i(String s) { return Integer.parseInt(s.trim()); }

    private static boolean b(String s) { return Boolean.parseBoolean(s.trim()); }

    public static void tick(MinecraftServer s) {
        if (++timer >= 6000) {
            timer = 0;
            save(s);
        }
    }

    public static void save(MinecraftServer s) {
        try {
            List<String> o = new ArrayList<>();
            o.add("godbox|" + SaveExt.FORMAT);
            SaveExt.saveAll(o);
            for (var pe : Ships.PORTS.entrySet()) o.add("P|" + pe.getKey().name() + "|" + pe.getValue().getX() + "|" + pe.getValue().getY() + "|" + pe.getValue().getZ());
            o.add("S|" + Colonies.on + "|" + Ships.on);
        for (var we : Wonders.OWNER.entrySet()) { net.minecraft.util.math.BlockPos wp = Wonders.AT.get(we.getKey()); if (wp != null && we.getValue() != null) o.add("W|" + we.getKey() + "|" + we.getValue().name() + "|" + wp.getX() + "|" + wp.getY() + "|" + wp.getZ()); }
        for (var te : Tech.MASK.entrySet()) o.add("X|" + te.getKey().name() + "|" + te.getValue() + "|" + Tech.PTS.getOrDefault(te.getKey(), 0));
        for (String rd : Roads.DONE) o.add("Y|" + rd);
        for (var se : Siege.WALL.entrySet()) o.add("G|" + se.getKey().name() + "|" + se.getValue());
        o.add("Z|" + Tech.on + "|" + Wonders.on + "|" + Magic.on + "|" + Navy.on + "|" + Roads.on + "|" + Siege.on);
        for (Kingdoms.K k22 : Kingdoms.K.values()) {
            if (Economy.GOLD.containsKey(k22)) o.add("E|" + k22.name() + "|" + Economy.gold(k22) + "|" + Economy.TAX.getOrDefault(k22, 1));
            net.minecraft.util.math.BlockPos mp22 = Economy.MARKET.get(k22);
            if (mp22 != null) o.add("M|" + k22.name() + "|" + mp22.getX() + "|" + mp22.getY() + "|" + mp22.getZ());
            if (Unrest.UNREST.containsKey(k22)) o.add("U|" + k22.name() + "|" + Unrest.unrest(k22));
            if (Highways.BUILT.containsKey(k22)) o.add("N|" + k22.name() + "|" + Highways.count(k22));
        }
        for (java.util.Map.Entry<Kingdoms.K, Kingdoms.K> e22 : Vassals.LORD.entrySet()) o.add("V|" + e22.getKey().name() + "|" + e22.getValue().name());
        for (String h22 : Highways.DONE) o.add("H|" + h22);
        for (String j22 : Heroes.HALL) o.add("J|" + j22.replace('|', '/'));
        o.add("Q|" + Economy.on + "|" + Vassals.on + "|" + Unrest.on + "|" + Heroes.on + "|" + Highways.on);
        for (Kingdoms.K k23 : Kingdoms.K.values()) {
            if (Prayers.FAITH.containsKey(k23)) o.add("%|" + k23.name() + "|" + Prayers.faith(k23));
            net.minecraft.util.math.BlockPos pn23 = Herds.PEN.get(k23);
            if (pn23 != null) o.add("&|" + k23.name() + "|" + pn23.getX() + "|" + pn23.getY() + "|" + pn23.getZ());
        }
        for (Achievements.A a23 : Achievements.GOT) o.add("O|" + a23.name());
        for (Tectonics.V v23 : Tectonics.VOLCANOES) o.add("^|" + v23.top.getX() + "|" + v23.top.getY() + "|" + v23.top.getZ() + "|" + v23.h);
        o.add("!|" + Prayers.on + "|" + Ecosystem.on + "|" + Herds.on + "|" + Tectonics.on + "|" + Perf.on);
        o.add("~|" + Storage.on + "|" + Decor.on);
        for (Kingdoms.K kk : Kingdoms.K.values()) o.add("#|" + kk.name() + "|" + Banners.SALT[kk.ordinal()]);
        o.addAll(Dynasty.save());
            o.add("A|" + WorldAges.age + "|" + WorldAges.timer + "|" + WorldAges.on);
            o.add("F|" + Kingdoms.war + "|" + Kingdoms.eternalPeace + "|" + Conquest.on + "|" + Events.on + "|" + Diplomacy.auto + "|" + Sleep.on);
            for (Kingdoms.K k : Kingdoms.K.values()) {
                if (!k.alive || k.world == null || k.capital == null) continue;
                o.add("K|" + k.name() + "|" + k.world.getRegistryKey().getValue() + "|" + k.capital.getX() + "|" + k.capital.getY() + "|" + k.capital.getZ());
                Civ.City c = Civ.city(k);
                o.add("C|" + k.name() + "|" + c.wood + "|" + c.stone + "|" + c.food + "|" + c.ore + "|" + c.era + "|" + c.faith + "|" + c.culture
                        + "|" + c.religion + "|" + c.castle + "|" + c.water + "|" + c.cycle);
                for (Civ.Building bd : c.buildings) {
                    if (!bd.done) continue;
                    o.add("B|" + k.name() + "|" + bd.kind.name() + "|" + bd.x0 + "|" + bd.z0 + "|" + bd.y0 + "|" + (bd == c.quarry ? 1 : 0));
                }
                List<BlockPos> towns = Conquest.TOWNS.get(k);
                if (towns != null) for (BlockPos t : towns) o.add("T|" + k.name() + "|" + t.getX() + "|" + t.getY() + "|" + t.getZ());
                for (Kingdoms.K j : Kingdoms.K.values()) {
                    if (j == k) continue;
                    o.add("D|" + k.name() + "|" + j.name() + "|" + Diplomacy.REL[k.ordinal()][j.ordinal()] + "|" + Diplomacy.OP[k.ordinal()][j.ordinal()]);
                }
            }
            for (String r : Faith.RELIGIONS) o.add("R|" + clean(r));
            for (String l : Chronicle.LOG) o.add("L|" + clean(l));
            Files.write(file(s), o, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[GodBox] save failed: " + e);
        }
    }

    public static void load(MinecraftServer s) {
        timer = 0;
        SaveExt.loadedFormat = 0;
        SaveExt.resetAll();
        try {
            Path f = file(s);
            if (!Files.exists(f)) return;
            List<String> in = Files.readAllLines(f, StandardCharsets.UTF_8);
            Faith.RELIGIONS.clear();
            Chronicle.LOG.clear();
            Prayers.FAITH.clear(); Herds.PEN.clear(); Achievements.GOT.clear(); Tectonics.VOLCANOES.clear(); java.util.Arrays.fill(Banners.SALT, 0); Dynasty.HOUSES.clear();
            int kingdoms = 0;
            for (String line : in) {
                String[] a = line.split("\\|", -1);
                try {
                    if (a[0].equals("godbox")) {
                        SaveExt.loadedFormat = a.length > 1 ? i(a[1]) : 1;
                        if (SaveExt.loadedFormat > SaveExt.FORMAT) GodBox.LOG.warn("GodBox: сохранение создано более новой версией мода (формат {}), незнакомые данные будут пропущены", SaveExt.loadedFormat);
                        continue;
                    }
                    if (a[0].startsWith("@")) {
                        SaveExt.load(a);
                        continue;
                    }
                    switch (a[0]) {
                        case "P" -> Ships.PORTS.put(Kingdoms.K.valueOf(a[1]), new net.minecraft.util.math.BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])));
                        case "S" -> { Colonies.on = Boolean.parseBoolean(a[1]); Ships.on = Boolean.parseBoolean(a[2]); }
                case "W" -> { Wonders.OWNER.put(a[1], Kingdoms.K.valueOf(a[2])); Wonders.AT.put(a[1], new net.minecraft.util.math.BlockPos(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]))); }
                case "X" -> { Tech.MASK.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[2])); Tech.PTS.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[3])); }
                case "Y" -> Roads.DONE.add(a[1]);
                case "E" -> { Kingdoms.K e22 = Kingdoms.K.valueOf(a[1]); Economy.GOLD.put(e22, Integer.parseInt(a[2])); Economy.TAX.put(e22, Integer.parseInt(a[3])); }
                case "M" -> Economy.MARKET.put(Kingdoms.K.valueOf(a[1]), new net.minecraft.util.math.BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])));
                case "U" -> Unrest.UNREST.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[2]));
                case "N" -> Highways.BUILT.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[2]));
                case "V" -> Vassals.LORD.put(Kingdoms.K.valueOf(a[1]), Kingdoms.K.valueOf(a[2]));
                case "H" -> Highways.DONE.add(a[1]);
                case "J" -> Heroes.HALL.add(a[1]);
                case "~" -> { Storage.on = Boolean.parseBoolean(a[1]); Decor.on = Boolean.parseBoolean(a[2]); }
            case "$" -> Dynasty.load(a);
            case "#" -> { try { Banners.SALT[Kingdoms.K.valueOf(a[1]).ordinal()] = Integer.parseInt(a[2]); } catch (Exception ignored) { } }
                case "%" -> Prayers.FAITH.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[2]));
                case "&" -> Herds.PEN.put(Kingdoms.K.valueOf(a[1]), new net.minecraft.util.math.BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])));
                case "O" -> Achievements.GOT.add(Achievements.A.valueOf(a[1]));
                case "^" -> Tectonics.VOLCANOES.add(new Tectonics.V(new net.minecraft.util.math.BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])), Integer.parseInt(a[4])));
                case "!" -> { Prayers.on = Boolean.parseBoolean(a[1]); Ecosystem.on = Boolean.parseBoolean(a[2]); Herds.on = Boolean.parseBoolean(a[3]); Tectonics.on = Boolean.parseBoolean(a[4]); Perf.on = Boolean.parseBoolean(a[5]); }
                case "Q" -> { Economy.on = Boolean.parseBoolean(a[1]); Vassals.on = Boolean.parseBoolean(a[2]); Unrest.on = Boolean.parseBoolean(a[3]); Heroes.on = Boolean.parseBoolean(a[4]); Highways.on = Boolean.parseBoolean(a[5]); }
                case "G" -> Siege.WALL.put(Kingdoms.K.valueOf(a[1]), Integer.parseInt(a[2]));
                case "Z" -> { Tech.on = Boolean.parseBoolean(a[1]); Wonders.on = Boolean.parseBoolean(a[2]); Magic.on = Boolean.parseBoolean(a[3]); Navy.on = Boolean.parseBoolean(a[4]); Roads.on = Boolean.parseBoolean(a[5]); Siege.on = Boolean.parseBoolean(a[6]); }
                        case "A" -> { WorldAges.age = Math.max(0, Math.min(WorldAges.NAMES.length - 1, Integer.parseInt(a[1]))); WorldAges.timer = Integer.parseInt(a[2]); WorldAges.on = Boolean.parseBoolean(a[3]); }
                        case "F" -> {
                            Kingdoms.war = b(a[1]);
                            Kingdoms.eternalPeace = b(a[2]);
                            Conquest.on = b(a[3]);
                            Events.on = b(a[4]);
                            Diplomacy.auto = b(a[5]);
                            Sleep.on = b(a[6]);
                        }
                        case "K" -> {
                            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
                            ServerWorld w = s.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(a[2])));
                            if (w == null) continue;
                            k.world = w;
                            k.capital = new BlockPos(i(a[3]), i(a[4]), i(a[5]));
                            k.alive = true;
                            k.grow = 0;
                            k.fallTimer = -60;
                            k.members.clear();
                            Civ.city(k);
                            kingdoms++;
                        }
                        case "C" -> {
                            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
                            if (!k.alive) continue;
                            Civ.City c = Civ.city(k);
                            c.wood = i(a[2]);
                            c.stone = i(a[3]);
                            c.food = i(a[4]);
                            c.ore = i(a[5]);
                            c.era = Math.max(0, Math.min(Eras.NAMES.length - 1, i(a[6])));
                            c.faith = i(a[7]);
                            c.culture = i(a[8]);
                            c.religion = i(a[9]);
                            c.castle = b(a[10]);
                            c.water = b(a[11]);
                            c.cycle = i(a[12]);
                            c.buildings.clear();
                            c.quarry = null;
                            c.project = null;
                        }
                        case "B" -> {
                            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
                            if (!k.alive) continue;
                            Civ.City c = Civ.city(k);
                            Civ.Building bd = new Civ.Building(Civ.Kind.valueOf(a[2]), i(a[3]), i(a[4]), i(a[5]));
                            bd.done = true;
                            c.buildings.add(bd);
                            if ("1".equals(a[6])) c.quarry = bd;
                        }
                        case "T" -> {
                            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
                            if (!k.alive) continue;
                            Conquest.TOWNS.computeIfAbsent(k, x -> new ArrayList<>()).add(new BlockPos(i(a[2]), i(a[3]), i(a[4])));
                        }
                        case "D" -> {
                            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
                            Kingdoms.K j = Kingdoms.K.valueOf(a[2]);
                            Diplomacy.REL[k.ordinal()][j.ordinal()] = i(a[3]);
                            Diplomacy.OP[k.ordinal()][j.ordinal()] = i(a[4]);
                        }
                        case "R" -> Faith.RELIGIONS.add(a[1]);
                        case "L" -> Chronicle.LOG.add(a[1]);
                        default -> { }
                    }
                } catch (Exception bad) {
                    System.err.println("[GodBox] bad save line: " + line);
                }
            }
            if (kingdoms > 0) System.out.println("[GodBox] restored kingdoms: " + kingdoms);
        } catch (Exception e) {
            System.err.println("[GodBox] load failed: " + e);
        }
    }
}
