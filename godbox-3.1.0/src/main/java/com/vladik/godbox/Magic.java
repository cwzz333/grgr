package com.vladik.godbox;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
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

import java.util.ArrayList;
import java.util.List;

/** Магия: королевские маги (огонь, молнии, лечение) и дикие некроманты. */
public final class Magic {
    public static boolean on = true;
    static final List<MobEntity> NECROS = new ArrayList<>();
    private static int t;

    private Magic() {}

    static boolean enemy(Kingdoms.K k, LivingEntity e) {
        if (!e.isAlive()) return false;
        Kingdoms.K o = Kingdoms.of(e);
        if (o == null) return e instanceof HostileEntity;
        return o != k && Diplomacy.rel(k, o) == Diplomacy.WAR;
    }

    static void cast(ServerWorld w, Kingdoms.K k, MobEntity m) {
        Random r = w.getRandom();
        List<LivingEntity> foes = w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(16), e -> e != m && enemy(k, e));
        if (!foes.isEmpty()) {
            LivingEntity f = foes.get(r.nextInt(foes.size()));
            m.getLookControl().lookAt(f);
            if (r.nextInt(5) == 0 && !(f instanceof VillagerEntity)) {
                LightningEntity l = EntityType.LIGHTNING_BOLT.create(w);
                if (l != null) {
                    l.refreshPositionAfterTeleport(f.getX(), f.getY(), f.getZ());
                    w.spawnEntity(l);
                }
            } else {
                Vec3d dir = new Vec3d(f.getX() - m.getX(), f.getBodyY(0.5) - m.getEyeY(), f.getZ() - m.getZ()).normalize();
                SmallFireballEntity fb = new SmallFireballEntity(w, m, dir);
                fb.setPosition(m.getX() + dir.x, m.getEyeY(), m.getZ() + dir.z);
                w.spawnEntity(fb);
            }
            Fx.sound(w, m.getX(), m.getY(), m.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1f, 1.1f);
            Fx.particles(w, ParticleTypes.WITCH, m.getX(), m.getY() + 2, m.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            return;
        }
        int n = 0;
        for (LivingEntity a : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(8), e -> e.isAlive() && e.getHealth() < e.getMaxHealth())) {
            Kingdoms.K o = Kingdoms.of(a);
            if (o == null || o != k && Diplomacy.rel(k, o) != Diplomacy.ALLY) continue;
            a.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
            Fx.particles(w, ParticleTypes.HEART, a.getX(), a.getY() + 1.5, a.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
            if (++n >= 4) break;
        }
    }

    static MobEntity mage(Kingdoms.K k, BlockPos at) {
        MobEntity m = Kingdoms.spawn(k, EntityType.EVOKER, at);
        if (m == null) return null;
        m.addCommandTag("gb_mage");
        Models.equipHat(m, "mage_hat");
        m.setCustomName(Text.literal("Маг «" + k.title + "»").formatted(k.color));
        m.setCustomNameVisible(true);
        var hp = m.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(40); m.setHealth(40); }
        m.setPersistent();
        return m;
    }

    static MobEntity necro(ServerWorld w, BlockPos at) {
        MobEntity n = EntityType.WITCH.create(w);
        if (n == null) return null;
        n.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        n.addCommandTag("gb_necro");
        n.setCustomName(Text.literal("Некромант").formatted(Formatting.DARK_PURPLE));
        n.setCustomNameVisible(true);
        var hp = n.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(80); n.setHealth(80); }
        n.setPersistent();
        w.spawnEntity(n);
        NECROS.add(n);
        Fx.sound(w, at.getX(), at.getY(), at.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        Fx.particles(w, ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 40, 1, 1, 1, 0.05);
        return n;
    }

    static void necroTick(MobEntity n) {
        if (!(n.getWorld() instanceof ServerWorld w)) return;
        Random r = w.getRandom();
        int undead = w.getEntitiesByClass(MobEntity.class, n.getBoundingBox().expand(24), e -> e.isAlive() && e.getCommandTags().contains("gb_undead")).size();
        if (undead < 6 && r.nextInt(2) == 0) {
            BlockPos g = Fx.surface(w, n.getBlockX() + r.nextInt(9) - 4, n.getBlockZ() + r.nextInt(9) - 4);
            MobEntity u = (r.nextInt(3) == 0 ? EntityType.SKELETON : EntityType.ZOMBIE).create(w);
            if (u != null) {
                u.refreshPositionAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, r.nextFloat() * 360f, 0f);
                u.addCommandTag("gb_undead");
                w.spawnEntity(u);
                Fx.particles(w, ParticleTypes.SOUL_FIRE_FLAME, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.03);
                Fx.sound(w, g.getX(), g.getY(), g.getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.6f);
            }
        }
        for (VillagerEntity v : w.getEntitiesByClass(VillagerEntity.class, n.getBoundingBox().expand(10), VillagerEntity::isAlive))
            if (r.nextInt(8) == 0) {
                Traits.apply(v, "cursed");
                Fx.particles(w, ParticleTypes.SQUID_INK, v.getX(), v.getY() + 1, v.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            }
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 40 != 0) return;
        NECROS.removeIf(n -> !n.isAlive());
        for (MobEntity n : new ArrayList<>(NECROS)) necroTick(n);
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) continue;
            int mages = 0;
            for (MobEntity m : new ArrayList<>(k.members))
                if (m.isAlive() && m.getCommandTags().contains("gb_mage")) { mages++; cast(w, k, m); }
            if (on && t % 1200 == 0 && mages < 2 && k.capital != null && Tech.has(k, Tech.T.MAGIC)) {
                Civ.City c = Civ.city(k);
                if (c.ore >= 10) {
                    c.ore -= 10;
                    if (mage(k, k.capital.up()) != null) Chronicle.say(s, k, "обучило нового мага");
                }
            }
        }
        if (on && t % 6000 == 0 && NECROS.size() < 2) {
            List<Kingdoms.K> ks = new ArrayList<>();
            for (Kingdoms.K k : Kingdoms.K.values()) if (k.alive && k.capital != null && k.world instanceof ServerWorld && Tech.has(k, Tech.T.MAGIC)) ks.add(k);
            if (ks.isEmpty()) return;
            Kingdoms.K k = ks.get(s.getOverworld().getRandom().nextInt(ks.size()));
            ServerWorld w = (ServerWorld) k.world;
            if (w.getRandom().nextInt(3) != 0) return;
            double a = w.getRandom().nextDouble() * Math.PI * 2;
            int x = k.capital.getX() + (int) (Math.cos(a) * 45), z = k.capital.getZ() + (int) (Math.sin(a) * 45);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
            if (necro(w, TownBuilder.landOr(w, Fx.surface(w, x, z))) != null) Chronicle.say(s, k, "видит у своих границ некроманта с армией мертвецов");
        }
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos, String arg) {
        BlockPos g = Fx.surface(w, pos.getX(), pos.getZ());
        if ("necro".equals(arg)) {
            necro(w, g);
            god.sendMessage(Text.literal("Появился некромант").formatted(Formatting.DARK_PURPLE), true);
            return;
        }
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null) { god.sendMessage(Text.literal("Маг служит королевству — сначала оснуйте его").formatted(Formatting.RED), true); return; }
        mage(k, g);
        god.sendMessage(Text.literal("У «" + k.title + "» появился маг").formatted(k.color), true);
    }

    public static void clear() { NECROS.clear(); t = 0; }
}
