package com.vladik.godbox;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Морские войны: военные флоты, обстрел портов, десант и кракен. */
public final class Navy {
    public static boolean on = true;
    static final List<MobEntity> KRAKENS = new ArrayList<>();
    private static int t;

    private Navy() {}

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 20 == 0) krakens();
        if (t % 12000 == 0 && KRAKENS.isEmpty()) wildKraken(s);
        if (!on || t % 1200 != 0) return;
        for (Kingdoms.K a : Kingdoms.K.values()) {
            if (!a.alive || !(a.world instanceof ServerWorld w) || !Tech.has(a, Tech.T.NAVIGATION) || Ships.PORTS.get(a) == null) continue;
            for (Kingdoms.K b : Kingdoms.K.values()) {
                if (a == b || !b.alive || b.world != a.world || Diplomacy.rel(a, b) != Diplomacy.WAR) continue;
                BlockPos target = Ships.PORTS.get(b);
                if (target == null || Seasons.frozen(a) || w.getRandom().nextInt(2) != 0) continue;
                fleet(w, a, b, target);
                break;
            }
        }
    }

    static void fleet(ServerWorld w, Kingdoms.K a, Kingdoms.K b, BlockPos target) {
        BlockPos moor = Ships.port(w, a);
        if (moor == null) return;
        Random r = w.getRandom();
        List<MobEntity> army = new ArrayList<>();
        for (MobEntity m : a.members)
            if (m.isAlive() && !(m instanceof VillagerEntity) && !m.hasVehicle() && !m.getCommandTags().contains("gb_king") && army.size() < 6) army.add(m);
        Civ.City c = Civ.city(a);
        while (army.size() < 4 && c.wood >= 8) {
            c.wood -= 8;
            MobEntity m = Kingdoms.spawn(a, Kingdoms.soldier(r), moor.up());
            if (m == null) break;
            army.add(m);
        }
        if (army.size() < 2) return;
        List<BoatEntity> boats = new ArrayList<>();
        BoatEntity cur = null;
        int i = 0;
        for (MobEntity m : army) {
            if (cur == null || cur.getPassengerList().size() >= 2) {
                cur = Ships.boat(w, a, moor.add(i % 3 - 1, 0, i / 3), false);
                if (cur == null) break;
                boats.add(cur);
                i++;
            }
            m.stopRiding();
            m.refreshPositionAndAngles(cur.getX(), cur.getY(), cur.getZ(), 0f, 0f);
            m.startRiding(cur, true);
        }
        if (boats.isEmpty()) return;
        Chronicle.say(w.getServer(), a, "отправляет военный флот к берегам «" + b.title + "»");
        int gun = Tech.broadside(a);
        Tasks.add(Tech.sail(a, new Ships.Voyage(w, boats, target, moor.getY(), at -> {
            for (int k = 0; k < 3 + gun * 2; k++) {
                int d = 10 + k * 15;
                Tasks.delay(d, () -> {
                    BlockPos p = Fx.surface(w, target.getX() + r.nextInt(17 + gun * 12) - 8 - gun * 6, target.getZ() + r.nextInt(17 + gun * 12) - 8 - gun * 6);
                    for (BoatEntity bt : boats) if (bt.isAlive()) Fx.particles(w, ParticleTypes.LARGE_SMOKE, bt.getX(), bt.getY() + 1, bt.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                    Fx.sound(w, at.getX(), at.getY(), at.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2f, 0.7f);
                    w.createExplosion(null, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 2f + gun, World.ExplosionSourceType.TNT);
                });
            }
            Tasks.delay(60, () -> {
                BlockPos land = TownBuilder.landOr(w, target);
                for (BoatEntity bt : boats) for (var p : new ArrayList<>(bt.getPassengerList())) {
                    p.stopRiding();
                    BlockPos g = Fx.surface(w, land.getX() + r.nextInt(5) - 2, land.getZ() + r.nextInt(5) - 2);
                    p.refreshPositionAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 0f);
                }
                Chronicle.say(w.getServer(), a, "высадило десант в порту «" + b.title + "»");
                Plague.carry(a, b, 0.5, "с военным флотом из");
                Tasks.delay(200, () -> boats.forEach(bt -> { if (bt.isAlive() && !bt.hasPassengers()) bt.discard(); }));
            });
        })));
    }

    static MobEntity kraken(ServerWorld w, BlockPos at) {
        MobEntity m = EntityType.ELDER_GUARDIAN.create(w);
        if (m == null) return null;
        m.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        m.addCommandTag("gb_kraken");
        m.setCustomName(Text.literal("Кракен").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        m.setCustomNameVisible(true);
        var sc = m.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (sc != null) sc.setBaseValue(3.0);
        var hp = m.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(320); m.setHealth(320); }
        m.setPersistent();
        w.spawnEntity(m);
        Models.attachCustom(m, "kraken");
        KRAKENS.add(m);
        Fx.sound(w, at.getX(), at.getY(), at.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 3f, 0.5f);
        Fx.particles(w, ParticleTypes.BUBBLE_COLUMN_UP, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 80, 3, 2, 3, 0.1);
        return m;
    }

    static void krakens() {
        KRAKENS.removeIf(m -> !m.isAlive());
        for (MobEntity m : new ArrayList<>(KRAKENS)) {
            if (!(m.getWorld() instanceof ServerWorld w)) continue;
            Random r = w.getRandom();
            for (BoatEntity b : w.getEntitiesByClass(BoatEntity.class, m.getBoundingBox().expand(12), BoatEntity::isAlive)) {
                if (r.nextInt(3) != 0) continue;
                for (var p : new ArrayList<>(b.getPassengerList())) p.stopRiding();
                Fx.particles(w, ParticleTypes.SPLASH, b.getX(), b.getY() + 1, b.getZ(), 40, 1, 0.5, 1, 0.2);
                Fx.sound(w, b.getX(), b.getY(), b.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, 2f, 0.6f);
                b.discard();
            }
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(16), e -> e != m && e.isAlive() && e.isTouchingWater() && !e.getCommandTags().contains("gb_kraken") && e.getType() != EntityType.GUARDIAN)) {
                Vec3d d = m.getPos().subtract(e.getPos());
                if (d.lengthSquared() > 0.01) e.addVelocity(d.normalize().multiply(0.35));
                e.velocityModified = true;
                if (d.lengthSquared() < 36) e.damage(w.getDamageSources().mobAttack(m), 6f);
            }
            if (t % 200 == 0) {
                int g = w.getEntitiesByClass(MobEntity.class, m.getBoundingBox().expand(20), e -> e.getType() == EntityType.GUARDIAN).size();
                if (g < 4) {
                    MobEntity gu = EntityType.GUARDIAN.create(w);
                    if (gu != null) {
                        gu.refreshPositionAndAngles(m.getX() + r.nextInt(7) - 3, m.getY(), m.getZ() + r.nextInt(7) - 3, 0f, 0f);
                        w.spawnEntity(gu);
                    }
                }
            }
        }
    }

    static void wildKraken(MinecraftServer s) {
        List<BlockPos> ports = new ArrayList<>(Ships.PORTS.values());
        ServerWorld w = s.getOverworld();
        if (ports.size() < 2 || w.getRandom().nextInt(3) != 0) return;
        BlockPos a = ports.get(0), b = ports.get(1 + w.getRandom().nextInt(ports.size() - 1));
        int x = (a.getX() + b.getX()) / 2, z = (a.getZ() + b.getZ()) / 2;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        BlockPos top = Fx.surface(w, x, z);
        if (w.getFluidState(top.down()).isEmpty()) return;
        if (kraken(w, top.down(2)) != null) Chronicle.log("Из глубин на торговом пути поднялся Кракен!");
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        BlockPos top = Fx.surface(w, pos.getX(), pos.getZ());
        if (w.getFluidState(top.down()).isEmpty()) {
            god.sendMessage(Text.literal("Кракен живёт только в воде").formatted(Formatting.RED), true);
            return;
        }
        kraken(w, top.down(2));
        god.sendMessage(Text.literal("Из глубин поднимается Кракен!").formatted(Formatting.DARK_AQUA), true);
    }

    public static void clear() { KRAKENS.clear(); t = 0; }
}
