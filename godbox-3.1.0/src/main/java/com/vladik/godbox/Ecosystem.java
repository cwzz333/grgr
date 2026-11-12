package com.vladik.godbox;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** v2.4: живая экосистема — хищники охотятся, добыча плодится, дикие стада держатся вместе. */
public final class Ecosystem {
    public static boolean on = true;
    static int hunts;
    static final Map<UUID, Integer> HUNGER = new HashMap<>();
    private static int t;

    private Ecosystem() {}

    static boolean prey(Entity e) {
        return e instanceof SheepEntity || e instanceof RabbitEntity || e instanceof ChickenEntity
                || e instanceof CowEntity || e instanceof PigEntity || e instanceof GoatEntity;
    }

    static boolean predator(Entity e) {
        return e instanceof WolfEntity wf && !wf.isTamed() || e instanceof FoxEntity || e instanceof PolarBearEntity;
    }

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            Entity a = src.getAttacker();
            if (!prey(e) || a == null || !predator(a)) return;
            HUNGER.put(a.getUuid(), 0);
            hunts++;
            if (a instanceof AnimalEntity an) an.heal(6f);
        });
    }

    public static void tick(MinecraftServer s) {
        if (!on || ++t % 100 != 0) return;
        if (!Perf.go && t % 200 != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            ServerWorld w = p.getServerWorld();
            Box box = new Box(p.getBlockPos()).expand(64, 48, 64);
            List<AnimalEntity> all = w.getEntitiesByClass(AnimalEntity.class, box, e -> e.isAlive() && seen.add(e.getUuid()));
            if (!all.isEmpty()) area(w, all);
        }
        if (t % 6000 == 0) HUNGER.keySet().removeIf(id -> HUNGER.get(id) > 60);
    }

    static boolean herd(Entity e) {
        for (String tag : e.getCommandTags()) if (tag.startsWith("gb_herd_")) return true;
        return false;
    }

    static void area(ServerWorld w, List<AnimalEntity> all) {
        Random r = w.getRandom();
        List<AnimalEntity> prey = new ArrayList<>(), pred = new ArrayList<>();
        Map<EntityType<?>, Integer> byType = new HashMap<>();
        Map<EntityType<?>, AnimalEntity> leader = new HashMap<>();
        for (AnimalEntity a : all) {
            if (prey(a)) prey.add(a);
            else if (predator(a)) pred.add(a);
            byType.merge(a.getType(), 1, Integer::sum);
        }
        // хищники: голод → охота, долгий голод → слабость
        for (AnimalEntity h : pred) {
            int hu = HUNGER.merge(h.getUuid(), 1, Integer::sum);
            if (hu >= 6 && h.getTarget() == null && !h.isBaby()) {
                AnimalEntity best = null;
                double bd = 24 * 24;
                for (AnimalEntity q : prey) {
                    if (q.isBaby() && r.nextBoolean()) continue;
                    if (h instanceof FoxEntity && !(q instanceof ChickenEntity || q instanceof RabbitEntity)) continue;
                    double d = q.squaredDistanceTo(h);
                    if (d < bd) { bd = d; best = q; }
                }
                if (best != null) h.setTarget(best);
            }
            if (hu > 40 && hu % 4 == 0) h.damage(w.getDamageSources().starve(), 2f);
            if (hu <= 2 && !h.isBaby() && h.getBreedingAge() == 0 && byType.getOrDefault(h.getType(), 0) < 6 && r.nextInt(8) == 0) baby(w, h);
        }
        // добыча: плодится на траве, при перенаселении — мор
        for (AnimalEntity a : prey) {
            int n = byType.getOrDefault(a.getType(), 0);
            if (!herd(a)) {
                AnimalEntity l = leader.putIfAbsent(a.getType(), a);
                if (l != null && l != a && a.squaredDistanceTo(l) > 100 && a.squaredDistanceTo(l) < 48 * 48 && a.getTarget() == null)
                    a.getNavigation().startMovingTo(l, 1.0);
            }
            if (n < 14 && !a.isBaby() && a.getBreedingAge() == 0 && r.nextInt(10) == 0
                    && w.getBlockState(a.getBlockPos().down()).isOf(Blocks.GRASS_BLOCK)) baby(w, a);
        }
        if (all.size() > 70) {
            for (int i = 0; i < 4; i++) {
                AnimalEntity a = all.get(r.nextInt(all.size()));
                if (!herd(a)) a.damage(w.getDamageSources().starve(), 4f);
            }
        }
        // много добычи и нет хищников — приходит стая
        if (prey.size() > 30 && pred.size() < 2 && r.nextInt(3) == 0) {
            AnimalEntity a = prey.get(r.nextInt(prey.size()));
            BlockPos at = Fx.surface(w, a.getBlockX() + r.nextInt(41) - 20, a.getBlockZ() + r.nextInt(41) - 20);
            pack(w, at, r.nextInt(4) == 0 ? EntityType.FOX : EntityType.WOLF, 3);
        }
    }

    static void baby(ServerWorld w, AnimalEntity a) {
        Entity e = a.getType().create(w);
        if (!(e instanceof AnimalEntity b)) return;
        b.setBaby(true);
        b.refreshPositionAndAngles(a.getX(), a.getY(), a.getZ(), a.getYaw(), 0f);
        for (String tag : a.getCommandTags()) if (tag.startsWith("gb_herd_")) b.addCommandTag(tag);
        w.spawnEntity(b);
        a.setBreedingAge(6000);
        Fx.particles(w, ParticleTypes.HEART, a.getX(), a.getY() + 1, a.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
    }

    static int pack(ServerWorld w, BlockPos at, EntityType<? extends AnimalEntity> type, int n) {
        int made = 0;
        for (int i = 0; i < n; i++) {
            AnimalEntity a = type.create(w);
            if (a == null) continue;
            a.refreshPositionAndAngles(at.getX() + 0.5 + i % 2, at.getY(), at.getZ() + 0.5 + i / 2, w.getRandom().nextFloat() * 360f, 0f);
            w.spawnEntity(a);
            HUNGER.put(a.getUuid(), 10);
            made++;
        }
        Fx.sound(w, at.getX(), at.getY(), at.getZ(), type == EntityType.WOLF ? SoundEvents.ENTITY_WOLF_HOWL : SoundEvents.ENTITY_FOX_SCREECH, 3f, 0.9f);
        return made;
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        int n = pack(w, pos.up(), EntityType.WOLF, 5);
        god.sendMessage(Text.literal("🐺 Стая из " + n + " голодных волков вышла на охоту").formatted(Formatting.GRAY), true);
    }

    public static void clear() {
        HUNGER.clear();
        hunts = 0;
        t = 0;
    }
}
