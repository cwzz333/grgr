package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Дороги между городами и союзными столицами: тропа → гравий → булыжник → каменные кирпичи, мосты через воду. */
public final class Roads {
    public static boolean on = true;
    static final Set<String> DONE = new HashSet<>();
    private static int t;

    private Roads() {}

    static String key(BlockPos a, BlockPos b) {
        String sa = a.getX() + "," + a.getZ(), sb = b.getX() + "," + b.getZ();
        return sa.compareTo(sb) < 0 ? sa + ">" + sb : sb + ">" + sa;
    }

    static BlockState mat(int era, Random r) {
        if (era <= 1) return Blocks.DIRT_PATH.getDefaultState();
        if (era <= 3) return (r.nextInt(4) == 0 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).getDefaultState();
        if (era <= 5) return (r.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).getDefaultState();
        return (r.nextInt(6) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).getDefaultState();
    }

    static boolean natural(BlockState s) {
        return s.isIn(BlockTags.DIRT) || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.DIRT_PATH)
                || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.STONE) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.COARSE_DIRT);
    }

    static boolean road(BlockState s) {
        return s.isOf(Blocks.DIRT_PATH) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.MOSSY_COBBLESTONE)
                || s.isOf(Blocks.STONE_BRICKS) || s.isOf(Blocks.CRACKED_STONE_BRICKS) || s.isOf(Blocks.OAK_PLANKS) || Highways.paved(s);
    }

    static boolean inBuilding(List<Civ.Building> bs, int x, int z) {
        for (Civ.Building b : bs) if (x >= b.x0 - 1 && x <= b.x1 + 1 && z >= b.z0 - 1 && z <= b.z1 + 1) return true;
        return false;
    }

    static void build(ServerWorld w, Kingdoms.K k, BlockPos a, BlockPos b) {
        Highways.Place j = new Highways.Place(w);
        int era = Civ.city(k).era;
        Random r = w.getRandom();
        List<Civ.Building> bs = new ArrayList<>(Civ.city(k).buildings);
        int x0 = a.getX(), z0 = a.getZ(), x1 = b.getX(), z1 = b.getZ();
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0), sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1, err = dx - dz;
        boolean xMajor = dx >= dz;
        int x = x0, z = z0;
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int step = 0; step < 500; step++) {
            boolean nearEnd = Math.max(Math.abs(x - x0), Math.abs(z - z0)) < 8 || Math.max(Math.abs(x - x1), Math.abs(z - z1)) < 8;
            if (!nearEnd && w.isChunkLoaded(x >> 4, z >> 4)) {
                for (int o = -1; o <= 1; o++) {
                    int px = xMajor ? x : x + o, pz = xMajor ? z + o : z;
                    if (inBuilding(bs, px, pz)) continue;
                    int y = TownBuilder.ground(w, px, pz);
                    BlockPos g = new BlockPos(px, y - 1, pz);
                    BlockState s = w.getBlockState(g);
                    if (!s.getFluidState().isEmpty()) {
                        // мост: настил по эпохе, края с перилами снаружи дороги, опоры и фонари
                        j.put(g, Highways.bridge(era, false));
                        if (o != 0) {
                            int qx = xMajor ? x : x + 2 * o, qz = xMajor ? z + 2 * o : z;
                            if (!inBuilding(bs, qx, qz)) {
                                BlockPos q = new BlockPos(qx, g.getY(), qz);
                                BlockState qs = w.getBlockState(q);
                                if (!qs.getFluidState().isEmpty() || qs.isAir() || qs.isReplaceable()) {
                                    j.put(q, Highways.bridge(era, true));
                                    j.put(q.up(), Highways.rail(era));
                                    if (step % 8 == 0) j.put(q.up(2), (era >= 4 ? Blocks.LANTERN : Blocks.TORCH).getDefaultState());
                                    if (step % 6 == 0) {
                                        BlockPos d = q.down();
                                        for (int n = 0; n < 40 && d.getY() > w.getBottomY(); n++, d = d.down()) {
                                            BlockState ds = w.getBlockState(d);
                                            if (!(ds.isAir() || !ds.getFluidState().isEmpty() || ds.isReplaceable())) break;
                                            j.put(d, Highways.pillar(era));
                                        }
                                    }
                                }
                            }
                        }
                    } else if (natural(s)) {
                        j.put(g, mat(era, r));
                        BlockState up = w.getBlockState(g.up());
                        if (!up.isAir() && up.isReplaceable()) j.put(g.up(), Blocks.AIR.getDefaultState());
                    }
                }
                if (era >= 2 && step % 16 == 8) {
                    int px = xMajor ? x : x + 2, pz = xMajor ? z + 2 : z;
                    if (!inBuilding(bs, px, pz) && TownBuilder.dry(w, px, pz)) {
                        int y = TownBuilder.ground(w, px, pz);
                        j.put(new BlockPos(px, y, pz), era >= 4 ? Blocks.STONE_BRICK_WALL.getDefaultState() : fence);
                        j.put(new BlockPos(px, y + 1, pz), (era >= 4 ? Blocks.LANTERN : Blocks.TORCH).getDefaultState());
                    }
                }
            }
            if (x == x1 && z == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
        }
        Tasks.add(j);
    }

    static boolean tryRoad(ServerWorld w, Kingdoms.K k, BlockPos a, BlockPos b, int max) {
        if (a.equals(b) || a.getSquaredDistance(b) > (double) max * max || a.getSquaredDistance(b) < 20 * 20) return false;
        String key = key(a, b);
        if (DONE.contains(key)) return false;
        Civ.City c = Civ.city(k);
        if (c.stone < 10) return false;
        c.stone -= 10;
        DONE.add(key);
        build(w, k, a, b);
        Chronicle.say(w.getServer(), k, "прокладывает дорогу");
        return true;
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 40 == 0) speed();
        if (!on || t % 1200 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w) || !Tech.has(k, Tech.T.WHEEL)) continue;
            if (t % 2400 != 0 && !Tech.has(k, Tech.T.ENGINEERING)) continue; // инженеры строят вдвое чаще
            boolean built = false;
            for (BlockPos tp : Conquest.towns(k)) if (tryRoad(w, k, k.capital, tp, Tech.roadReach(k, 110))) { built = true; break; }
            if (built) continue;
            for (Kingdoms.K o : Kingdoms.K.values()) {
                if (o == k || !o.alive || o.capital == null || o.world != k.world || Diplomacy.rel(k, o) != Diplomacy.ALLY) continue;
                if (tryRoad(w, k, k.capital, o.capital, Tech.roadReach(k, 110))) break;
            }
        }
    }

    static void speed() {
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) continue;
            for (MobEntity m : k.members) {
                if (!m.isAlive() || !m.isOnGround()) continue;
                if (road(w.getBlockState(m.getBlockPos().down()))) m.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
            }
        }
    }

    public static void power(net.minecraft.server.network.ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null || k.capital == null) return;
        BlockPos target = Fx.surface(w, pos.getX(), pos.getZ());
        DONE.remove(key(k.capital, target));
        Civ.city(k).stone += 10;
        if (!tryRoad(w, k, k.capital, target, 400)) {
            god.sendMessage(net.minecraft.text.Text.literal("Слишком близко к столице или слишком далеко"), true);
        }
    }

    public static void clear() { DONE.clear(); t = 0; }
}
