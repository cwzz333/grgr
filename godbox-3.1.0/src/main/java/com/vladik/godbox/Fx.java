package com.vladik.godbox;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap.Type;

public final class Fx {
    /** 0 = circle, 1 = square, 2 = ring, 3 = spray */
    public static int shape = 0;

    public static boolean inBrush(int dx, int dz, int r) {
        int d = dx * dx + dz * dz;
        return switch (shape) {
            case 1 -> true;
            case 2 -> d <= r * r + r && d >= (r - 1) * (r - 1);
            case 3 -> d <= r * r + r && java.util.concurrent.ThreadLocalRandom.current().nextInt(3) == 0;
            default -> d <= r * r + r;
        };
    }
    private Fx() {
    }

    public static void particles(ServerWorld w, ParticleEffect e, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            w.spawnParticles(p, e, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void sound(ServerWorld w, double x, double y, double z, SoundEvent s, float volume, float pitch) {
        w.playSound(null, x, y, z, s, SoundCategory.MASTER, volume, pitch);
    }

    public static BlockPos surface(ServerWorld w, int x, int z) {
        return w.getTopPosition(Type.MOTION_BLOCKING, new BlockPos(x, 0, z));
    }

    public static List<BlockPos> brush(ServerWorld w, BlockPos c, int r) {
        List<BlockPos> out = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (inBrush(dx, dz, r)) {
                    out.add(surface(w, c.getX() + dx, c.getZ() + dz));
                }
            }
        }

        return out;
    }

    public static List<LivingEntity> living(ServerWorld w, Vec3d c, double r) {
        Box box = new Box(c.x - r, c.y - r - 8.0, c.z - r, c.x + r, c.y + r + 16.0, c.z + r);
        return w.getEntitiesByClass(
            LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !GodManager.isGodEntity(e) && e.squaredDistanceTo(c.x, e.getY(), c.z) <= r * r
        );
    }

    public static boolean breakable(ServerWorld w, BlockPos p, BlockState s, float maxHardness) {
        if (s.isAir()) {
            return false;
        } else {
            float h = s.getHardness(w, p);
            return h >= 0.0F && h <= maxHardness;
        }
    }

    public static void push(Entity e, double x, double y, double z) {
        e.addVelocity(x, y, z);
        e.velocityModified = true;
    }

    public static void fire(ServerWorld w, BlockPos p) {
        if (w.isAir(p)) {
            BlockState fire = AbstractFireBlock.getState(w, p);
            if (fire.canPlaceAt(w, p)) {
                w.setBlockState(p, fire, 3);
            }
        }
    }

    public static void grow(ServerWorld w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (s.getBlock() instanceof Fertilizable f && f.isFertilizable(w, p, s) && f.canGrow(w, w.getRandom(), p, s)) {
            f.grow(w, w.getRandom(), p, s);
        }
    }
}
