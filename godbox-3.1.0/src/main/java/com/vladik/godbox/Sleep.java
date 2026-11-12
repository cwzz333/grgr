package com.vladik.godbox;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Ночной сон жителей в настоящих кроватях. */
public final class Sleep {
    public static boolean on = true;
    private static final Map<VillagerEntity, BlockPos> CLAIM = new WeakHashMap<>();
    private static final Map<VillagerEntity, Integer> TRIES = new WeakHashMap<>();
    private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private Sleep() {}

    static boolean isBed(ServerWorld w, BlockPos p) {
        return p != null && w.getBlockState(p).getBlock() instanceof BedBlock;
    }

    private static boolean claimed(VillagerEntity v, BlockPos p) {
        for (Map.Entry<VillagerEntity, BlockPos> e : CLAIM.entrySet()) {
            if (e.getKey() != v && e.getKey().isAlive() && p.equals(e.getValue())) return true;
        }
        return false;
    }

    /** Найти свободную кровать в одном из домов или поставить новую. */
    static BlockPos find(Kingdoms.K k, List<Civ.Building> homes, ServerWorld w, VillagerEntity v) {
        if (!on || homes.isEmpty()) return null;
        List<Civ.Building> l = new ArrayList<>(homes);
        Collections.shuffle(l);
        for (Civ.Building b : l) {
            BlockPos p = bed(k, b, w, v, false);
            if (p != null) return claim(v, p);
        }
        for (Civ.Building b : l) {
            BlockPos p = bed(k, b, w, v, true);
            if (p != null) return claim(v, p);
        }
        return null;
    }

    static BlockPos claim(VillagerEntity v, BlockPos p) {
        CLAIM.put(v, p);
        TRIES.put(v, 0);
        return p;
    }

    private static boolean air(ServerWorld w, BlockPos p) {
        return w.getBlockState(p).isAir();
    }

    private static boolean solid(ServerWorld w, BlockPos p) {
        return w.getBlockState(p).isSolidBlock(w, p);
    }

    private static boolean nearDoor(ServerWorld w, BlockPos p) {
        for (Direction d : DIRS) {
            if (w.getBlockState(p.offset(d)).getBlock() instanceof DoorBlock) return true;
            if (w.getBlockState(p.offset(d).up()).getBlock() instanceof DoorBlock) return true;
        }
        return w.getBlockState(p).getBlock() instanceof DoorBlock;
    }

    static BlockPos bed(Kingdoms.K k, Civ.Building b, ServerWorld w, VillagerEntity v, boolean place) {
        int beds = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = b.x0; x <= b.x1; x++)
            for (int z = b.z0; z <= b.z1; z++)
                for (int y = b.y0; y <= b.y0 + 3; y++) {
                    m.set(x, y, z);
                    BlockState s = w.getBlockState(m);
                    if (s.getBlock() instanceof BedBlock && s.get(BedBlock.PART) == BedPart.HEAD) {
                        beds++;
                        BlockPos p = m.toImmutable();
                        if (!s.get(BedBlock.OCCUPIED) && !claimed(v, p)) return p;
                    }
                }
        if (!place) return null;
        int max = b.kind == Civ.Kind.MANOR ? 4 : 2;
        if (beds >= max) return null;
        Block bedBlock = k.bed();
        for (int y = b.y0; y <= b.y0 + 2; y++)
            for (int x = b.x0 + 1; x <= b.x1 - 1; x++)
                for (int z = b.z0 + 1; z <= b.z1 - 1; z++) {
                    BlockPos f = new BlockPos(x, y, z);
                    if (!air(w, f) || !air(w, f.up()) || !solid(w, f.down()) || nearDoor(w, f)) continue;
                    for (Direction d : DIRS) {
                        BlockPos h = f.offset(d);
                        if (h.getX() <= b.x0 || h.getX() >= b.x1 || h.getZ() <= b.z0 || h.getZ() >= b.z1) continue;
                        if (!air(w, h) || !air(w, h.up()) || !solid(w, h.down()) || nearDoor(w, h)) continue;
                        BlockState foot = bedBlock.getDefaultState().with(BedBlock.FACING, d).with(BedBlock.PART, BedPart.FOOT);
                        w.setBlockState(f, foot, 3);
                        w.setBlockState(h, foot.with(BedBlock.PART, BedPart.HEAD), 3);
                        return h;
                    }
                }
        return null;
    }

    /** Ночное поведение у кровати. true — житель занят сном. */
    static boolean night(ServerWorld w, VillagerEntity v, BlockPos bed) {
        if (v.isSleeping()) return true;
        if (!on || !isBed(w, bed)) return false;
        BlockState s = w.getBlockState(bed);
        if (s.get(BedBlock.OCCUPIED)) return false;
        double dx = v.getX() - (bed.getX() + 0.5), dz = v.getZ() - (bed.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        int t = TRIES.getOrDefault(v, 0) + 1;
        TRIES.put(v, t);
        if (d <= 1.7 && Math.abs(v.getY() - bed.getY()) < 1.6) {
            Civ.halt(v);
            v.sleep(bed);
            return true;
        }
        if (t > 500) {
            v.requestTeleport(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5);
            v.sleep(bed);
            return true;
        }
        Civ.walk(v, bed);
        return true;
    }

    static void wake(VillagerEntity v) {
        if (v.isSleeping()) v.wakeUp();
        CLAIM.remove(v);
        TRIES.remove(v);
    }

    public static void clear() {
        CLAIM.clear();
        TRIES.clear();
    }
}
