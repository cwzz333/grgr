package com.vladik.godbox;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;

/** Долгие эффекты: атомная бомба, метеорит, торнадо, землетрясение, луч света. */
public final class Disasters {
    private Disasters() {}

    // ===================== АТОМНАЯ БОМБА =====================
    public static final class Atomic implements Tasks.Task {
        private final ServerWorld w;
        private final double x, z, gy;
        private int t = 0;

        public Atomic(ServerWorld w, BlockPos c) {
            this.w = w;
            this.x = c.getX() + 0.5;
            this.z = c.getZ() + 0.5;
            this.gy = Fx.surface(w, c.getX(), c.getZ()).getY();
        }

        @Override
        public boolean tick() {
            Random r = w.getRandom();
            if (t == 0) Fx.sound(w, x, gy + 20, z, SoundEvents.ENTITY_WITHER_SPAWN, 8f, 0.5f);
            if (t < 20) {
                double y = gy + 60 - t * 3;
                Fx.particles(w, ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.2, 0.2, 0.2, 0.01);
                Fx.particles(w, ParticleTypes.FLAME, x, y, z, 6, 0.2, 0.2, 0.2, 0.01);
            }
            if (t == 20) {
                Fx.particles(w, ParticleTypes.FLASH, x, gy + 3, z, 6, 3, 3, 3, 0);
                w.createExplosion(null, x, gy, z, 12f, true, World.ExplosionSourceType.TNT);
                Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, x, gy + 2, z, 25, 9, 4, 9, 0);
                Fx.sound(w, x, gy + 10, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 12f, 0.4f);
                for (LivingEntity e : Fx.living(w, new Vec3d(x, gy, z), 40)) {
                    e.setOnFireFor(10);
                    Vec3d d = e.getPos().subtract(x, gy, z);
                    double l = Math.max(1, d.length());
                    Fx.push(e, d.x / l * 2.5, 1.2, d.z / l * 2.5);
                }
            }
            if (t > 20 && t <= 28) {
                double a = (t - 21) * Math.PI / 4;
                double rx = x + Math.cos(a) * 12, rz = z + Math.sin(a) * 12;
                BlockPos s = Fx.surface(w, MathHelper.floor(rx), MathHelper.floor(rz));
                w.createExplosion(null, rx, s.getY(), rz, 7f, true, World.ExplosionSourceType.TNT);
            }
            if (t > 28 && t <= 50) {
                for (int i = 0; i < 5; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 20;
                    Fx.fire(w, Fx.surface(w, MathHelper.floor(x + Math.cos(a) * d), MathHelper.floor(z + Math.sin(a) * d)));
                }
            }
            if (t >= 20) {
                double h = Math.min(40, (t - 20) * 1.5);
                for (int i = 0; i < 12; i++) {
                    Fx.particles(w, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + (r.nextDouble() - 0.5) * 5, gy + r.nextDouble() * h, z + (r.nextDouble() - 0.5) * 5, 1, 0, 0, 0, 0.01);
                }
                for (int i = 0; i < 18; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * (4 + h * 0.35);
                    Fx.particles(w, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * d, gy + h + (r.nextDouble() - 0.3) * 6, z + Math.sin(a) * d, 1, 0, 0, 0, 0.01);
                }
                if (t % 3 == 0) Fx.particles(w, ParticleTypes.LAVA, x, gy + h, z, 10, 6, 2, 6, 0);
            }
            return ++t > 220;
        }
    }

    // ===================== МЕТЕОРИТ =====================
    public static final class Meteor implements Tasks.Task {
        private static final int DURATION = 30;
        private final ServerWorld w;
        private final BlockPos target;
        private final double sx, sy, sz;
        private int t = 0;

        public Meteor(ServerWorld w, BlockPos target) {
            this.w = w;
            this.target = target;
            double a = w.getRandom().nextDouble() * Math.PI * 2;
            this.sx = target.getX() + 0.5 + Math.cos(a) * 30;
            this.sz = target.getZ() + 0.5 + Math.sin(a) * 30;
            this.sy = target.getY() + 80;
        }

        @Override
        public boolean tick() {
            double k = (double) t / DURATION;
            double x = MathHelper.lerp(k, sx, target.getX() + 0.5);
            double y = MathHelper.lerp(k, sy, target.getY() + 1.0);
            double z = MathHelper.lerp(k, sz, target.getZ() + 0.5);
            if (t == 0) Fx.sound(w, target.getX(), target.getY() + 20, target.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, 6f, 0.4f);
            Fx.particles(w, ParticleTypes.FLAME, x, y, z, 30, 0.7, 0.7, 0.7, 0.05);
            Fx.particles(w, ParticleTypes.LARGE_SMOKE, x, y, z, 15, 0.8, 0.8, 0.8, 0.02);
            Fx.particles(w, ParticleTypes.LAVA, x, y, z, 4, 0.5, 0.5, 0.5, 0);
            if (t >= DURATION) {
                double cx = target.getX() + 0.5, cy = target.getY() + 1, cz = target.getZ() + 0.5;
                w.createExplosion(null, cx, cy, cz, 7f, true, World.ExplosionSourceType.TNT);
                Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 6, 3, 2, 3, 0);
                Random r = w.getRandom();
                for (BlockPos p : Fx.brush(w, target, 6)) {
                    BlockPos g = p.down();
                    if (r.nextFloat() < 0.45f && Fx.breakable(w, g, w.getBlockState(g), 50f)) {
                        w.setBlockState(g, r.nextFloat() < 0.7f ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
                    } else if (r.nextFloat() < 0.2f) {
                        Fx.fire(w, p);
                    }
                }
                return true;
            }
            t++;
            return false;
        }
    }

    // ===================== ТОРНАДО =====================
    public static final class Tornado implements Tasks.Task {
        private final ServerWorld w;
        private double x, z, vx, vz;
        private int life = 400;

        public Tornado(ServerWorld w, double x, double z) {
            this.w = w;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean tick() {
            Random r = w.getRandom();
            vx = MathHelper.clamp(vx + (r.nextDouble() - 0.5) * 0.06, -0.25, 0.25);
            vz = MathHelper.clamp(vz + (r.nextDouble() - 0.5) * 0.06, -0.25, 0.25);
            x += vx;
            z += vz;
            int gy = Fx.surface(w, MathHelper.floor(x), MathHelper.floor(z)).getY();

            for (int i = 0; i < 30; i++) {
                double h = r.nextDouble() * 26;
                double a = r.nextDouble() * Math.PI * 2;
                double rad = 0.5 + h * 0.22;
                Fx.particles(w, ParticleTypes.CLOUD, x + Math.cos(a) * rad, gy + h, z + Math.sin(a) * rad, 1, 0, 0, 0, 0.0);
            }

            Box box = new Box(x - 10, gy - 4, z - 10, x + 10, gy + 32, z + 10);
            for (Entity e : w.getOtherEntities(null, box, en -> !GodManager.isGodEntity(en) && !en.isSpectator())) {
                double dx = x - e.getX(), dz = z - e.getZ();
                double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                if (d > 10) continue;
                double tx = -dz / d * 0.45, tz = dx / d * 0.45;
                double up = e.getY() < gy + 24 ? 0.16 + (d < 3 ? 0.12 : 0) : -0.05;
                e.setVelocity(e.getVelocity().multiply(0.75).add(dx / d * 0.16 + tx, up, dz / d * 0.16 + tz));
                e.velocityModified = true;
            }

            if (life % 2 == 0) {
                for (int k = 0; k < 2; k++) {
                    BlockPos top = Fx.surface(w, MathHelper.floor(x + r.nextDouble() * 6 - 3), MathHelper.floor(z + r.nextDouble() * 6 - 3)).down();
                    BlockState s = w.getBlockState(top);
                    if (Fx.breakable(w, top, s, 2.5f) && s.getFluidState().isEmpty() && !s.hasBlockEntity()) {
                        FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, top, s);
                        fb.dropItem = false;
                        fb.setVelocity(r.nextDouble() - 0.5, 0.6 + r.nextDouble() * 0.5, r.nextDouble() - 0.5);
                        fb.velocityModified = true;
                    }
                }
            }
            if (life % 20 == 0) Fx.sound(w, x, gy + 5, z, SoundEvents.ITEM_ELYTRA_FLYING, 5f, 0.6f);
            return --life <= 0;
        }
    }

    // ===================== ЗЕМЛЕТРЯСЕНИЕ =====================
    public static final class Quake implements Tasks.Task {
        private static final int R = 18;
        private final ServerWorld w;
        private final BlockPos c;
        private int life = 140;

        public Quake(ServerWorld w, BlockPos c) {
            this.w = w;
            this.c = c;
        }

        @Override
        public boolean tick() {
            Random r = w.getRandom();
            for (int i = 0; i < 5; i++) {
                BlockPos top = Fx.surface(w, c.getX() + r.nextInt(R * 2 + 1) - R, c.getZ() + r.nextInt(R * 2 + 1) - R).down();
                BlockState s = w.getBlockState(top);
                if (Fx.breakable(w, top, s, 5f) && s.getFluidState().isEmpty() && !s.hasBlockEntity()) {
                    Fx.particles(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, s), top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5, 12, 0.5, 0.2, 0.5, 0.1);
                    FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, top, s);
                    fb.setVelocity((r.nextDouble() - 0.5) * 0.2, 0.25 + r.nextDouble() * 0.35, (r.nextDouble() - 0.5) * 0.2);
                    fb.velocityModified = true;
                }
            }
            if (life % 10 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                int sx = c.getX() + r.nextInt(R * 2 + 1) - R, sz = c.getZ() + r.nextInt(R * 2 + 1) - R;
                for (int l = 0; l < 7; l++) {
                    int bx = sx + (int) Math.round(Math.cos(a) * l), bz = sz + (int) Math.round(Math.sin(a) * l);
                    BlockPos top = Fx.surface(w, bx, bz).down();
                    for (int d = 0; d < 3; d++) {
                        BlockPos q = top.down(d);
                        BlockState s = w.getBlockState(q);
                        if (Fx.breakable(w, q, s, 50f) && s.getFluidState().isEmpty()) w.setBlockState(q, Blocks.AIR.getDefaultState());
                    }
                }
                Fx.sound(w, c.getX(), c.getY() + 5, c.getZ(), SoundEvents.BLOCK_STONE_BREAK, 8f, 0.4f);
            }
            if (life % 3 == 0) {
                for (LivingEntity e : Fx.living(w, Vec3d.ofCenter(c), R)) {
                    if (e.isOnGround()) Fx.push(e, (r.nextDouble() - 0.5) * 0.4, 0.15, (r.nextDouble() - 0.5) * 0.4);
                }
            }
            return --life <= 0;
        }
    }

    // ===================== ЛУЧ СВЕТА =====================
    public static final class Light implements Tasks.Task {
        private final ServerWorld w;
        private final BlockPos c;
        private final int gy;
        private int t = 0;

        public Light(ServerWorld w, BlockPos c) {
            this.w = w;
            this.c = c;
            this.gy = Fx.surface(w, c.getX(), c.getZ()).getY();
        }

        @Override
        public boolean tick() {
            Random r = w.getRandom();
            double x = c.getX() + 0.5, z = c.getZ() + 0.5;
            if (t == 0) {
                Fx.sound(w, x, gy + 10, z, SoundEvents.BLOCK_BEACON_ACTIVATE, 6f, 1.2f);
                Fx.sound(w, x, gy + 10, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 6f, 0.8f);
                Fx.particles(w, ParticleTypes.FLASH, x, gy + 1, z, 2, 0, 0, 0, 0);
            }
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 1.6;
                Fx.particles(w, ParticleTypes.END_ROD, x + Math.cos(a) * d, gy + r.nextDouble() * 60, z + Math.sin(a) * d, 1, 0, 0, 0, 0);
            }
            if (t % 2 == 0) {
                double ring = 2.5 + (t % 20) * 0.25;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12 + t * 0.1;
                    Fx.particles(w, ParticleTypes.GLOW, x + Math.cos(a) * ring, gy + 0.2, z + Math.sin(a) * ring, 1, 0, 0, 0, 0);
                }
            }
            if (t % 10 == 0) {
                for (LivingEntity e : Fx.living(w, new Vec3d(x, gy, z), 7)) {
                    if (e.getType().isIn(EntityTypeTags.UNDEAD)) {
                        e.damage(w.getDamageSources().magic(), 8f);
                        e.setOnFireFor(5);
                    } else {
                        e.heal(6f);
                        for (StatusEffectInstance in : new ArrayList<>(e.getStatusEffects())) {
                            if (!in.getEffectType().value().isBeneficial()) e.removeStatusEffect(in.getEffectType());
                        }
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                        e.extinguish();
                    }
                }
                for (BlockPos p : Fx.brush(w, c, 6)) {
                    if (w.getBlockState(p).isIn(BlockTags.FIRE)) w.setBlockState(p, Blocks.AIR.getDefaultState());
                }
            }
            return ++t > 80;
        }
    }
}
