package com.vladik.godbox;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Короли королевств: коронация, золотая корона над головой, смерть короля — смута и восстания. */
public final class Kings {
    static final String TAG = "gb_king";
    static final Map<Kingdoms.K, UUID> KING = new EnumMap<>(Kingdoms.K.class);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.82f, 0.1f), 1.0f);
    private static int t;

    private Kings() {}

    static MobEntity king(Kingdoms.K k) {
        for (MobEntity m : k.members) if (m.isAlive() && m.getCommandTags().contains(TAG)) return m;
        return null;
    }

    public static String kingName(Kingdoms.K k) {
        MobEntity m = king(k);
        if (m == null) return "нет";
        return m.hasCustomName() ? m.getCustomName().getString() : "безымянный";
    }

    public static void tick(MinecraftServer s) {
        t++;
        if (t % 10 == 0) for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) continue;
            MobEntity m = king(k);
            if (m != null) Fx.particles(w, GOLD, m.getX(), m.getY() + m.getHeight() + 0.35, m.getZ(), 4, 0.18, 0.04, 0.18, 0.0);
        }
        if (t % 100 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) { KING.remove(k); continue; }
            MobEntity m = king(k);
            if (m != null) { KING.put(k, m.getUuid()); Dynasty.ensure(s, k, w, m); continue; }
            if (KING.containsKey(k)) {
                KING.remove(k);
                MobEntity heir = Dynasty.heir(k);
                if (heir != null) {
                    Dynasty.dispute(s, k, w, heir);
                    crown(s, k, w);
                    continue;
                }
                if (Treaties.succession(s, k, w)) continue;
                Chronicle.say(s, k, "лишилось короля, а наследников нет! В королевстве смута");
                Civ.City c = Civ.city(k);
                c.food = c.food * 2 / 3;
                List<BlockPos> towns = Conquest.TOWNS.get(k);
                if (towns != null && !towns.isEmpty() && w.getRandom().nextInt(3) == 0) {
                    BlockPos p = towns.get(w.getRandom().nextInt(towns.size()));
                    if (w.isChunkLoaded(p)) Rebels.rebel(w, k, p);
                }
            }
            crown(s, k, w);
        }
    }

    static void crown(MinecraftServer s, Kingdoms.K k, ServerWorld w) {
        MobEntity m = Dynasty.pick(k);
        if (m == null) return;
        m.addCommandTag(TAG);
        Traits.apply(m, "tough");
        Traits.apply(m, "regen");
        KING.put(k, m.getUuid());
        Fx.particles(w, GOLD, m.getX(), m.getY() + 2, m.getZ(), 40, 0.6, 0.6, 0.6, 0.0);
        Fx.sound(w, m.getX(), m.getY(), m.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        Dynasty.crowned(s, k, w, m);
    }

    public static void clear() {
        KING.clear();
        t = 0;
    }
}
