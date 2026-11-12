package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** v2.4: королевства приручают животных, строят пастбища и кормятся со стад. */
public final class Herds {
    public static boolean on = true;
    static final Map<Kingdoms.K, BlockPos> PEN = new EnumMap<>(Kingdoms.K.class);
    static final Map<Kingdoms.K, Integer> SIZE = new EnumMap<>(Kingdoms.K.class);
    static int tamed;
    private static int t;

    private Herds() {}

    static String tag(Kingdoms.K k) { return "gb_herd_" + k.id; }

    static int count(Kingdoms.K k) { return SIZE.getOrDefault(k, 0); }

    static boolean wild(AnimalEntity a) {
        return Ecosystem.prey(a) && !Ecosystem.herd(a) && !a.isLeashed();
    }

    static void tame(ServerWorld w, Kingdoms.K k, AnimalEntity a) {
        a.addCommandTag(tag(k));
        a.setPersistent();
        tamed++;
        Fx.particles(w, ParticleTypes.HEART, a.getX(), a.getY() + 1, a.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
    }

    public static void tick(MinecraftServer s) {
        if (!on || ++t % 200 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w)) { SIZE.remove(k); continue; }
            BlockPos cap = k.capital;
            if (!w.isChunkLoaded(cap.getX() >> 4, cap.getZ() >> 4)) continue;
            Civ.City c = Civ.city(k);
            Random r = w.getRandom();
            String tg = tag(k);
            List<AnimalEntity> herd = w.getEntitiesByClass(AnimalEntity.class, new Box(cap).expand(64, 32, 64), a -> a.isAlive() && a.getCommandTags().contains(tg));
            SIZE.put(k, herd.size());
            int cap2 = 6 + c.era * 2;
            if (herd.size() < cap2) {
                List<AnimalEntity> wild = w.getEntitiesByClass(AnimalEntity.class, new Box(cap).expand(40, 20, 40), a -> a.isAlive() && wild(a));
                if (!wild.isEmpty()) {
                    AnimalEntity a = wild.get(r.nextInt(wild.size()));
                    tame(w, k, a);
                    herd.add(a);
                    if (tamed % 10 == 1) Chronicle.say(s, k, "приручает диких животных");
                }
            }
            BlockPos pen = PEN.get(k);
            if (pen == null && herd.size() >= 3 && c.wood >= 10 && pasture(w, k, c)) {
                c.wood -= 10;
                pen = PEN.get(k);
            }
            BlockPos home = pen != null ? pen : cap;
            int range = pen != null ? 4 : 20;
            for (AnimalEntity a : herd) {
                if (a.getTarget() == null && a.getBlockPos().getSquaredDistance(home) > range * range)
                    a.getNavigation().startMovingTo(home.getX() + 0.5 + r.nextInt(5) - 2, home.getY(), home.getZ() + 0.5 + r.nextInt(5) - 2, 1.0);
            }
            c.food += herd.size() / 2;
            if (c.food < 5 && herd.size() > 2) {
                AnimalEntity a = herd.remove(r.nextInt(herd.size()));
                if (!a.isBaby()) {
                    a.discard();
                    c.food += 15;
                    Fx.particles(w, ParticleTypes.POOF, a.getX(), a.getY() + 0.5, a.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                }
            }
            if (pen != null && herd.size() >= 2 && herd.size() < cap2 && r.nextInt(4) == 0) {
                AnimalEntity a = herd.get(r.nextInt(herd.size()));
                if (!a.isBaby() && a.getBreedingAge() == 0) Ecosystem.baby(w, a);
            }
        }
    }

    /** Пастбище 9×9 с изгородью, калиткой, корытом и сеном. */
    static boolean pasture(ServerWorld w, Kingdoms.K k, Civ.City c) {
        Random r = w.getRandom();
        List<Civ.Building> bs = Highways.allBuildings();
        for (int tr = 0; tr < 10; tr++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 18 + r.nextInt(14);
            int x = k.capital.getX() + (int) (Math.cos(a) * d), z = k.capital.getZ() + (int) (Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || !TownBuilder.dryArea(w, x, z, 5)) continue;
            boolean clash = false;
            for (int dx = -5; dx <= 5 && !clash; dx += 2)
                for (int dz = -5; dz <= 5; dz += 2) if (Roads.inBuilding(bs, x + dx, z + dz)) { clash = true; break; }
            if (clash) continue;
            int y = TownBuilder.ground(w, x, z);
            Highways.Place pl = new Highways.Place(w);
            Block fence = c.era >= 10 ? Blocks.IRON_BARS : c.era >= 4 ? Blocks.SPRUCE_FENCE : Blocks.OAK_FENCE;
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                    pl.put(new BlockPos(x + dx, y - 1, z + dz), Blocks.GRASS_BLOCK);
                    pl.put(new BlockPos(x + dx, y, z + dz), edge ? fence : Blocks.AIR);
                    pl.put(new BlockPos(x + dx, y + 1, z + dz), Blocks.AIR);
                }
            pl.put(new BlockPos(x, y, z - 4), Blocks.OAK_FENCE_GATE);
            pl.put(new BlockPos(x + 3, y, z + 3), Blocks.HAY_BLOCK);
            pl.put(new BlockPos(x - 3, y, z + 3), Blocks.WATER_CAULDRON);
            pl.put(new BlockPos(x + 4, y + 1, z + 4), Blocks.LANTERN);
            pl.put(new BlockPos(x - 4, y + 1, z - 4), Blocks.LANTERN);
            Tasks.add(pl);
            PEN.put(k, new BlockPos(x, y, z));
            Chronicle.say(w.getServer(), k, "строит пастбище для стада");
            return true;
        }
        return false;
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null) { god.sendMessage(Text.literal("Рядом нет королевства"), true); return; }
        int n = 0;
        for (AnimalEntity a : w.getEntitiesByClass(AnimalEntity.class, new Box(pos).expand(10, 6, 10), a -> a.isAlive() && wild(a))) {
            tame(w, k, a);
            if (k.capital != null) a.getNavigation().startMovingTo(k.capital.getX() + 0.5, k.capital.getY(), k.capital.getZ() + 0.5, 1.1);
            n++;
        }
        Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_VILLAGER_YES, 2f, 1f);
        god.sendMessage(Text.literal("🐑 " + k.title + " приручило " + n + " животных").formatted(k.color), true);
    }

    public static void clear() {
        PEN.clear();
        SIZE.clear();
        tamed = 0;
        t = 0;
    }
}
