package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Золото, налоги, рынки, наёмники и пиры. В эпоху пара — заводы и дымящие трубы. */
public final class Economy {
    public static boolean on = true;
    static final Map<Kingdoms.K, Integer> GOLD = new EnumMap<>(Kingdoms.K.class);
    static final Map<Kingdoms.K, Integer> TAX = new EnumMap<>(Kingdoms.K.class);
    static final Map<Kingdoms.K, BlockPos> MARKET = new EnumMap<>(Kingdoms.K.class);
    static final String[] TAXN = {"низкие", "обычные", "высокие"};
    private static int t;

    private Economy() {}

    static int gold(Kingdoms.K k) { return GOLD.getOrDefault(k, 0); }

    static void add(Kingdoms.K k, int n) { GOLD.put(k, Math.max(0, gold(k) + n)); }

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 40 == 0) smoke();
        if (t % 600 != 0 || !on) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive) { GOLD.remove(k); TAX.remove(k); MARKET.remove(k); continue; }
            if (k.capital == null || !(k.world instanceof ServerWorld w)) continue;
            Civ.City c = Civ.city(k);
            int vill = 0, war = 0;
            for (MobEntity m : k.members) {
                if (!m.isAlive()) continue;
                if (m instanceof VillagerEntity) vill++; else war++;
            }
            int g = gold(k), tax = TAX.getOrDefault(k, 1);
            int want = g < 20 ? 2 : g > 250 ? 0 : 1;
            if (Unrest.unrest(k) > 55) want = 0;
            if (want != tax) {
                tax = want;
                TAX.put(k, tax);
                Chronicle.say(s, k, "устанавливает " + TAXN[tax] + " налоги");
            }
            int income = vill * (1 + tax) / 2 + c.era / 2 + (MARKET.containsKey(k) ? 4 + Conquest.towns(k).size() * 2 : 0);
            if (Tech.has(k, Tech.T.WRITING)) income += income / 5;
            income = Races.gold(k, income);
            income = Treaties.income(k, income);
            if (c.era >= 10) {
                income += 6;
                c.wood += 4;
                c.stone += 4;
                c.ore += 2;
            }
            g += income - war / 3;
            if (g <= 0) { g = 0; Unrest.add(k, 3); }
            if (tax == 2) Unrest.add(k, 2);
            else if (tax == 0) Unrest.add(k, -2);
            if (MARKET.containsKey(k)) {
                if (c.wood > 90) { c.wood -= 20; g += 5; }
                if (c.stone > 90) { c.stone -= 20; g += 5; }
                if (c.ore > 40) { c.ore -= 10; g += 8; }
                if (c.food < 15 && g >= 10) { c.food += 20; g -= 10; }
                if (c.wood < 10 && g >= 8) { c.wood += 15; g -= 8; }
                if (c.stone < 8 && g >= 8) { c.stone += 12; g -= 8; }
            } else if (c.era >= 1 && g >= 25 && c.wood >= 12 && market(w, k, c)) {
                g -= 25;
                c.wood -= 12;
            }
            Random r = w.getRandom();
            if (Diplomacy.atWar(k) && g >= 60 && war < 30) {
                g -= 40;
                for (int i = 0; i < 3; i++)
                    Kingdoms.spawn(k, Kingdoms.soldier(r), Fx.surface(w, k.capital.getX() + r.nextInt(9) - 4, k.capital.getZ() + r.nextInt(9) - 4));
                Chronicle.say(s, k, "нанимает отряд наёмников за 40 золота");
            } else if (g >= 300) {
                g -= 120;
                c.food += 30;
                Unrest.add(k, -15);
                BlockPos p = k.capital;
                Fx.particles(w, ParticleTypes.FIREWORK, p.getX() + 0.5, p.getY() + 6, p.getZ() + 0.5, 120, 4, 3, 4, 0.2);
                Fx.sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 3f, 1f);
                Chronicle.say(s, k, "устраивает пир на весь мир (−15 к смуте)");
            }
            GOLD.put(k, g);
        }
    }

    /** Дым из труб домов в эпоху пара. */
    static void smoke() {
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) continue;
            Civ.City c = Civ.city(k);
            if (c.era < 10) continue;
            int n = 0;
            for (Civ.Building b : c.buildings) {
                if (!b.done || b.kind != Civ.Kind.HOUSE) continue;
                if (n++ > 10) break;
                if (!w.isChunkLoaded(b.x1 >> 4, b.z1 >> 4)) continue;
                Fx.particles(w, ParticleTypes.LARGE_SMOKE, b.x1 + 0.5, b.y0 + 6.5, b.z1 + 0.5, 3, 0.1, 0.4, 0.1, 0.01);
            }
        }
    }

    /** Рыночная площадь с полосатыми навесами цвета королевства. */
    static boolean market(ServerWorld w, Kingdoms.K k, Civ.City c) {
        Random r = w.getRandom();
        List<Civ.Building> bs = Highways.allBuildings();
        for (int tr = 0; tr < 10; tr++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 12 + r.nextInt(12);
            int x = k.capital.getX() + (int) (Math.cos(a) * d), z = k.capital.getZ() + (int) (Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || !TownBuilder.dryArea(w, x, z, 4)) continue;
            boolean clash = false;
            for (int dx = -4; dx <= 4 && !clash; dx += 2)
                for (int dz = -4; dz <= 4; dz += 2) if (Roads.inBuilding(bs, x + dx, z + dz)) { clash = true; break; }
            if (clash) continue;
            int y = TownBuilder.ground(w, x, z);
            Highways.Place pl = new Highways.Place(w);
            Block floor = Eras.style(c.era, Civ.Kind.HOUSE, Blocks.STONE_BRICKS.getDefaultState()).getBlock();
            Block post = Eras.style(c.era, Civ.Kind.HOUSE, Blocks.OAK_FENCE.getDefaultState()).getBlock();
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    pl.put(new BlockPos(x + dx, y - 1, z + dz), floor);
                    for (int h = 0; h <= 4; h++) pl.put(new BlockPos(x + dx, y + h, z + dz), Blocks.AIR);
                    if (Math.abs(dx) >= 1) pl.put(new BlockPos(x + dx, y + 3, z + dz), (dz & 1) == 0 ? k.wool : Blocks.WHITE_WOOL);
                }
            for (int px : new int[]{-3, -1, 1, 3})
                for (int pz : new int[]{-3, 3})
                    for (int h = 0; h < 3; h++) pl.put(new BlockPos(x + px, y + h, z + pz), post);
            pl.put(new BlockPos(x - 2, y, z - 2), Blocks.BARREL);
            pl.put(new BlockPos(x - 2, y, z + 1), Blocks.HAY_BLOCK);
            pl.put(new BlockPos(x + 2, y, z - 1), Blocks.BARREL);
            pl.put(new BlockPos(x + 2, y, z + 2), Blocks.COMPOSTER);
            pl.put(new BlockPos(x + 2, y, z), Blocks.SMITHING_TABLE);
            pl.put(new BlockPos(x - 2, y, z), Blocks.LOOM);
            pl.put(new BlockPos(x, y, z), Blocks.BELL);
            pl.put(new BlockPos(x, y + 4, z), Blocks.LANTERN);
            Tasks.add(pl);
            MARKET.put(k, new BlockPos(x, y, z));
            Chronicle.say(w.getServer(), k, "открывает рынок — теперь излишки продаются за золото");
            return true;
        }
        return false;
    }

    public static void gift(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null) { god.sendMessage(Text.literal("Рядом нет королевства"), true); return; }
        add(k, 100);
        Fx.particles(w, ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 80, 2, 2, 2, 0.3);
        Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, 2f, 1.2f);
        god.sendMessage(Text.literal("💰 " + k.title + ": +100 золота (казна " + gold(k) + ")").formatted(k.color), true);
    }

    static String info(Kingdoms.K k) {
        return gold(k) + " зол., налоги " + TAXN[TAX.getOrDefault(k, 1)] + (MARKET.containsKey(k) ? ", есть рынок" : "");
    }

    public static void clear() {
        GOLD.clear();
        TAX.clear();
        MARKET.clear();
        t = 0;
    }
}
