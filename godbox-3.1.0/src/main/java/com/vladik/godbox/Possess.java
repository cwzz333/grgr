package com.vladik.godbox;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** v2.3: вселение бога в существо — клик по земле ведёт, клик по врагу — атака. */
public final class Possess {
    static final Map<UUID, MobEntity> HOST = new HashMap<>();
    static final Map<UUID, BlockPos> GO = new HashMap<>();
    private static int t;

    private Possess() {}

    private static void msg(ServerPlayerEntity god, String s, Formatting f) {
        god.sendMessage(Text.literal(s).formatted(f), true);
    }

    private static void buff(MobEntity m) {
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 400, 0, false, false));
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 1, false, false));
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1, false, false));
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0, false, false));
    }

    public static void use(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        UUID id = god.getUuid();
        MobEntity m = HOST.get(id);
        Vec3d c = Vec3d.ofCenter(pos).add(0, 1, 0);
        if (m != null && (!m.isAlive() || m.getWorld() != w)) {
            release(god, m, false);
            m = null;
        }
        if (m == null) {
            MobEntity best = null;
            double bd = 36;
            for (LivingEntity e : Fx.living(w, c, 6)) {
                if (!(e instanceof MobEntity mob) || !e.isAlive()) continue;
                double d = e.squaredDistanceTo(c);
                if (d < bd) { bd = d; best = mob; }
            }
            if (best == null) { msg(god, "Здесь нет существа для вселения", Formatting.GRAY); return; }
            HOST.put(id, best);
            GO.remove(id);
            best.setPersistent();
            buff(best);
            Achievements.possessed = true;
            Fx.particles(w, ParticleTypes.SOUL, best.getX(), best.getY() + 1, best.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
            Fx.sound(w, best.getX(), best.getY(), best.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.6f);
            msg(god, "👁 Вы вселились в «" + best.getName().getString() + "». ЛКМ по земле — идти, по врагу — атака, по сосуду — покинуть", Formatting.AQUA);
            return;
        }
        if (m.squaredDistanceTo(c) < 6.25) {
            release(god, m, true);
            return;
        }
        LivingEntity target = null;
        double bd = 9;
        for (LivingEntity e : Fx.living(w, c, 3)) {
            if (e == m || !e.isAlive() || e instanceof PlayerEntity p && GodManager.isGodEntity(p)) continue;
            double d = e.squaredDistanceTo(c);
            if (d < bd) { bd = d; target = e; }
        }
        if (target != null) {
            GO.remove(id);
            m.setTarget(target);
            msg(god, "⚔ Атака: " + target.getName().getString(), Formatting.RED);
        } else {
            BlockPos g = pos.up();
            GO.put(id, g);
            m.setTarget(null);
            m.getNavigation().startMovingTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 1.25);
            Fx.particles(w, ParticleTypes.SOUL_FIRE_FLAME, g.getX() + 0.5, g.getY() + 0.2, g.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.01);
            msg(god, "➜ " + m.getName().getString() + " идёт к " + g.toShortString(), Formatting.AQUA);
        }
    }

    static void release(ServerPlayerEntity god, MobEntity m, boolean say) {
        HOST.remove(god.getUuid());
        GO.remove(god.getUuid());
        if (m.isAlive()) {
            m.removeStatusEffect(StatusEffects.GLOWING);
            m.removeStatusEffect(StatusEffects.SPEED);
            if (m.getWorld() instanceof ServerWorld w)
                Fx.particles(w, ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        }
        if (say) msg(god, "👁 Вы покинули сосуд", Formatting.GRAY);
    }

    public static void tick(MinecraftServer s) {
        if (HOST.isEmpty()) return;
        ++t;
        Iterator<Map.Entry<UUID, MobEntity>> it = HOST.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MobEntity> e = it.next();
            UUID id = e.getKey();
            MobEntity m = e.getValue();
            ServerPlayerEntity god = s.getPlayerManager().getPlayer(id);
            if (god == null || !m.isAlive() || !(m.getWorld() instanceof ServerWorld w)) {
                if (god != null) msg(god, "💀 Сосуд погиб — дух вернулся к богу", Formatting.DARK_RED);
                it.remove();
                GO.remove(id);
                continue;
            }
            if (t % 5 == 0) Fx.particles(w, ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getEyeY() + 0.6, m.getZ(), 1, 0.1, 0.05, 0.1, 0.0);
            if (t % 300 == 0) buff(m);
            BlockPos g = GO.get(id);
            if (g != null && t % 10 == 0) {
                if (m.getBlockPos().getSquaredDistance(g) < 4) GO.remove(id);
                else m.getNavigation().startMovingTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 1.25);
            }
        }
    }

    public static void clear() {
        HOST.clear();
        GO.clear();
        t = 0;
    }
}
