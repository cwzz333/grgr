package com.vladik.godbox;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** v2.4: живые вулканы, самопроизвольные землетрясения и тектонические разломы. */
public final class Tectonics {
    public static boolean on = true;
    static boolean erupted;
    static final List<V> VOLCANOES = new ArrayList<>();
    private static int t;

    static final class V {
        final BlockPos top;
        final int h;
        int next;

        V(BlockPos top, int h) {
            this.top = top;
            this.h = h;
        }
    }

    private Tectonics() {}

    static void tell(MinecraftServer s, String m) {
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) p.sendMessage(Text.literal(m).formatted(Formatting.GOLD), false);
    }

    public static void volcano(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        if (VOLCANOES.size() >= 8) { god.sendMessage(Text.literal("Уже 8 живых вулканов — больше нельзя"), true); return; }
        int h = 14;
        Tasks.add(new Powers2.Volcano(w, pos.up(), h));
        V v = new V(pos.up(h + 1), h);
        v.next = t + 900;
        VOLCANOES.add(v);
        god.sendMessage(Text.literal("🌋 Живой вулкан пробудился — теперь он сам извергается время от времени").formatted(Formatting.GOLD), true);
    }

    public static void rift(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        double a = w.getRandom().nextDouble() * Math.PI;
        Tasks.add(new Rift(w, pos, Math.cos(a), Math.sin(a)));
        Tasks.add(new Disasters.Quake(w, pos));
        god.sendMessage(Text.literal("🌍 Земля раскалывается!").formatted(Formatting.GOLD), true);
    }

    public static void tick(MinecraftServer s) {
        if (++t % 20 != 0) return;
        ServerWorld w = s.getOverworld();
        Random r = w.getRandom();
        for (V v : VOLCANOES) {
            if (v.next <= 0) v.next = t + 1200 + r.nextInt(2400);
            if (t < v.next) continue;
            v.next = t + 2400 + r.nextInt(4800);
            if (!w.isChunkLoaded(v.top.getX() >> 4, v.top.getZ() >> 4)) continue;
            BlockPos c = Fx.surface(w, v.top.getX(), v.top.getZ());
            Tasks.add(new Eruption(w, c, v.h));
            Tasks.add(new Disasters.Quake(w, c));
            erupted = true;
            tell(s, "🌋 Извержение вулкана у " + c.getX() + ", " + c.getZ() + "!");
            Chronicle.log("Извергся вулкан у " + c.getX() + ", " + c.getZ());
            for (Kingdoms.K k : Kingdoms.K.values())
                if (k.alive && k.capital != null && k.capital.getSquaredDistance(c) < 140 * 140) Unrest.add(k, 4);
        }
        if (!on) return;
        if (t % 6000 == 0 && r.nextBoolean()) {
            List<Kingdoms.K> ks = new ArrayList<>();
            for (Kingdoms.K k : Kingdoms.K.values())
                if (k.alive && k.capital != null && k.world instanceof ServerWorld kw && kw.isChunkLoaded(k.capital.getX() >> 4, k.capital.getZ() >> 4)) ks.add(k);
            if (!ks.isEmpty()) {
                Kingdoms.K k = ks.get(r.nextInt(ks.size()));
                ServerWorld kw = (ServerWorld) k.world;
                BlockPos p = Fx.surface(kw, k.capital.getX() + r.nextInt(81) - 40, k.capital.getZ() + r.nextInt(81) - 40);
                Tasks.add(new Disasters.Quake(kw, p));
                Unrest.add(k, 3);
                Chronicle.say(s, k, "пережило землетрясение");
            }
        }
        if (t % 24000 == 0 && VOLCANOES.size() < 6 && r.nextInt(3) == 0) {
            List<ServerPlayerEntity> ps = w.getPlayers();
            if (ps.isEmpty()) return;
            ServerPlayerEntity p = ps.get(r.nextInt(ps.size()));
            double a = r.nextDouble() * Math.PI * 2;
            int d = 100 + r.nextInt(80);
            int x = p.getBlockX() + (int) (Math.cos(a) * d), z = p.getBlockZ() + (int) (Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
            for (Kingdoms.K k : Kingdoms.K.values())
                if (k.alive && k.capital != null && k.capital.getSquaredDistance(new BlockPos(x, k.capital.getY(), z)) < 90 * 90) return;
            BlockPos g = Fx.surface(w, x, z);
            if (!w.getFluidState(g.down()).isEmpty()) return;
            Tasks.add(new Powers2.Volcano(w, g, 12));
            VOLCANOES.add(new V(g.up(13), 12));
            tell(s, "🌋 Из-под земли поднялся новый вулкан у " + x + ", " + z);
        }
    }

    static final class Eruption implements Tasks.Task {
        final ServerWorld w;
        final BlockPos c;
        final int h;
        int life = 240;

        Eruption(ServerWorld w, BlockPos c, int h) {
            this.w = w;
            this.c = c;
            this.h = h;
        }

        @Override
        public boolean tick() {
            Random r = w.getRandom();
            life--;
            double x = c.getX() + 0.5, y = c.getY() + 1, z = c.getZ() + 0.5;
            Fx.particles(w, ParticleTypes.LAVA, x, y, z, 6, 1, 0.5, 1, 0.5);
            Fx.particles(w, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 2, z, 3, 1.5, 2, 1.5, 0.05);
            Fx.particles(w, ParticleTypes.LARGE_SMOKE, x, y + 4, z, 6, 2, 3, 2, 0.1);
            if (life % 4 == 0) {
                BlockPos at = c.up(3);
                if (w.getBlockState(at).isAir()) {
                    BlockState st = (r.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).getDefaultState();
                    FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, at, st);
                    fb.setVelocity((r.nextDouble() - 0.5) * 1.4, 0.8 + r.nextDouble() * 0.7, (r.nextDouble() - 0.5) * 1.4);
                    fb.velocityModified = true;
                }
            }
            if (life % 20 == 0) {
                Fx.sound(w, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
                for (LivingEntity e : Fx.living(w, Vec3d.ofCenter(c), h)) e.setFireTicks(100);
            }
            if (life == 220 || life == 140) {
                BlockPos lp = c;
                if (w.getBlockState(lp).isAir()) w.setBlockState(lp, Blocks.LAVA.getDefaultState());
            }
            if (life > 0) return false;
            int rad = (int) (h * 1.6);
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++)
                    for (int dy = -h - 3; dy <= 3; dy++) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        if (!w.getFluidState(m).isIn(FluidTags.LAVA)) continue;
                        boolean still = w.getFluidState(m).isStill();
                        w.setBlockState(m, still ? Blocks.MAGMA_BLOCK.getDefaultState() : (r.nextInt(3) == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).getDefaultState());
                    }
            return true;
        }
    }

    static final class Rift implements Tasks.Task {
        final ServerWorld w;
        final BlockPos c;
        final double dx, dz;
        int i = -24;

        Rift(ServerWorld w, BlockPos c, double dx, double dz) {
            this.w = w;
            this.c = c;
            this.dx = dx;
            this.dz = dz;
        }

        @Override
        public boolean tick() {
            for (int n = 0; n < 2 && i <= 24; n++, i++) {
                int depth = 10 - Math.abs(i) / 3;
                int bx = c.getX() + (int) Math.round(dx * i), bz = c.getZ() + (int) Math.round(dz * i);
                for (int s = -1; s <= 1; s++) {
                    int x = bx + (int) Math.round(-dz * s), z = bz + (int) Math.round(dx * s);
                    BlockPos top = Fx.surface(w, x, z);
                    for (int d = 1; d <= depth; d++) {
                        BlockPos p = top.down(d);
                        BlockState st = w.getBlockState(p);
                        if (st.isOf(Blocks.BEDROCK) || !Fx.breakable(w, p, st, 50f)) break;
                        w.setBlockState(p, d >= depth - 1 && s == 0 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
                    }
                    Fx.particles(w, ParticleTypes.LARGE_SMOKE, x + 0.5, top.getY() + 0.5, z + 0.5, 4, 0.4, 0.6, 0.4, 0.05);
                }
                if (i % 6 == 0) Fx.sound(w, bx, c.getY(), bz, SoundEvents.BLOCK_STONE_BREAK, 4f, 0.5f);
            }
            return i > 24;
        }
    }

    public static void clear() {
        VOLCANOES.clear();
        erupted = false;
        t = 0;
    }
}
