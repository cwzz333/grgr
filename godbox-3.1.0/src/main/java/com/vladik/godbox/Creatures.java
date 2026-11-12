package com.vladik.godbox;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
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

/** Существа из WorldBox: некромант, маги, друид, чумной доктор, бандиты, элементали, черепа. */
public final class Creatures {
    private static int timer;
    private static final Block[] PLANTS = {Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.SWEET_BERRY_BUSH};

    private Creatures() {}

    static String name(String t) {
        return switch (t) {
            case "necro" -> "Некромант";
            case "evil_mage" -> "Злой маг";
            case "white_mage" -> "Белый маг";
            case "druid" -> "Друид";
            case "plague_doctor" -> "Чумной доктор";
            case "bandit" -> "Бандит";
            case "fire_elemental" -> "Огненный элементаль";
            case "crystal_golem" -> "Кристальный голем";
            case "fire_skull" -> "Огненный череп";
            default -> "Прыгучий череп";
        };
    }

    static EntityType<? extends MobEntity> type(String t) {
        return switch (t) {
            case "necro" -> EntityType.EVOKER;
            case "evil_mage" -> EntityType.ILLUSIONER;
            case "white_mage", "plague_doctor" -> EntityType.WANDERING_TRADER;
            case "druid" -> EntityType.VILLAGER;
            case "bandit" -> EntityType.PILLAGER;
            case "fire_elemental" -> EntityType.BLAZE;
            case "crystal_golem" -> EntityType.IRON_GOLEM;
            case "fire_skull" -> EntityType.VEX;
            default -> EntityType.MAGMA_CUBE;
        };
    }

    static Formatting color(String t) {
        return switch (t) {
            case "white_mage", "druid", "plague_doctor", "crystal_golem" -> Formatting.GREEN;
            case "bandit" -> Formatting.GOLD;
            default -> Formatting.DARK_RED;
        };
    }

    public static void spawn(ServerPlayerEntity god, Power power, ServerWorld w, BlockPos pos, int brush, Random r) {
        String t = power.arg;
        int n = t.equals("bandit") ? 2 + brush / 2 : 1 + brush / 4;
        for (int i = 0; i < n; i++) {
            BlockPos at = Fx.surface(w, pos.getX() + r.nextInt(5) - 2, pos.getZ() + r.nextInt(5) - 2);
            MobEntity m = make(w, t, at);
            if (m == null) continue;
            Fx.particles(w, ParticleTypes.WITCH, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.05);
        }
        Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 1.0f);
        if (god != null) god.sendMessage(Text.literal("Призвано: " + name(t)).formatted(color(t)), true);
    }

    static MobEntity make(ServerWorld w, String t, BlockPos at) {
        MobEntity m = type(t).create(w);
        if (m == null) return null;
        m.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.getRandom().nextFloat() * 360f, 0f);
        m.initialize(w, w.getLocalDifficulty(at), SpawnReason.EVENT, null);
        m.setCustomName(Text.literal(name(t)).formatted(color(t)));
        m.setCustomNameVisible(true);
        m.setPersistent();
        m.addCommandTag("gbc_" + t);
        if (m instanceof WanderingTraderEntity wt) wt.setDespawnDelay(0);
        double hp = switch (t) {
            case "necro", "evil_mage" -> 60;
            case "crystal_golem" -> 160;
            case "white_mage", "druid", "plague_doctor" -> 40;
            default -> 0;
        };
        EntityAttributeInstance a = m.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp > 0 && a != null) {
            a.setBaseValue(hp);
            m.setHealth((float) hp);
        }
        if (t.equals("fire_skull") || t.equals("fire_elemental")) m.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0));
        if (t.equals("crystal_golem")) m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, -1, 1));
        w.spawnEntity(m);
        dress(m, t);
        return m;
    }

    /** Свой убор гуманоида (ключ существа -> убор из hats.py) или null. */
    private static String hatOf(String t) {
        return switch (t) {
            case "necro" -> "necro_hood";
            case "evil_mage" -> "evil_mage_hat";
            case "white_mage" -> "white_circlet";
            case "plague_doctor" -> "plague_mask";
            case "druid" -> "druid_crown";
            case "bandit" -> "bandit_hood";
            default -> null;
        };
    }

    /** Убор или своя модель существа; повторный вызов ничего не меняет (так одеваются и существа из старых миров). */
    private static void dress(MobEntity m, String t) {
        String hat = hatOf(t);
        if (hat != null) {
            if (!Models.isHat(m.getEquippedStack(EquipmentSlot.HEAD))) Models.equipHat(m, hat);
            return;
        }
        if (!ModelParts.has(t) || Models.has(m) || !Models.custom()) return;
        if (t.equals("fire_skull") && m.getMainHandStack().isOf(Items.IRON_SWORD)) {
            // меч невидимого векса висел бы в воздухе: убираем его, а урон меча (+5) переносим в базовую атаку
            EntityAttributeInstance dmg = m.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() + 5);
            m.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        Models.attachCustom(m, t);
    }

    private static boolean prey(LivingEntity e) {
        return e.isAlive() && !(e instanceof Monster) && !GodManager.isGodEntity(e) && !e.getCommandTags().stream().anyMatch(s -> s.startsWith("gbc_"));
    }

    private static LivingEntity nearest(ServerWorld w, MobEntity m, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(r), Creatures::prey)) {
            double d = e.squaredDistanceTo(m);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static void beam(ServerWorld w, Vec3d a, Vec3d b) {
        for (int i = 0; i <= 12; i++) {
            Vec3d p = a.lerp(b, i / 12.0);
            Fx.particles(w, ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public static void tick(MinecraftServer s) {
        if (++timer % 20 != 0) return;
        int cyc = timer / 20;
        for (ServerWorld w : s.getWorlds()) {
            List<MobEntity> list = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (e instanceof MobEntity m && m.isAlive()) for (String tag : m.getCommandTags()) if (tag.startsWith("gbc_")) { list.add(m); break; }
            }
            for (MobEntity m : list) {
                String t = null;
                for (String tag : m.getCommandTags()) if (tag.startsWith("gbc_")) t = tag.substring(4);
                if (t != null) {
                    if (cyc % 5 == 0) dress(m, t);
                    act(w, m, t, cyc + m.getId());
                }
            }
        }
        Rebels.tick(s, cyc);
    }

    private static void act(ServerWorld w, MobEntity m, String t, int c) {
        Random r = w.getRandom();
        double x = m.getX(), y = m.getY(), z = m.getZ();
        switch (t) {
            case "necro" -> {
                Fx.particles(w, ParticleTypes.SOUL, x, y + 1, z, 4, 0.5, 0.8, 0.5, 0.02);
                if (c % 10 == 0 && w.getEntitiesByClass(AbstractSkeletonEntity.class, m.getBoundingBox().expand(16), e -> true).size() < 8) {
                    for (int i = 0; i < 2; i++) {
                        MobEntity sk = EntityType.SKELETON.create(w);
                        if (sk == null) continue;
                        BlockPos at = Fx.surface(w, (int) x + r.nextInt(7) - 3, (int) z + r.nextInt(7) - 3);
                        sk.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                        sk.initialize(w, w.getLocalDifficulty(at), SpawnReason.EVENT, null);
                        w.spawnEntity(sk);
                        Fx.particles(w, ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.03);
                    }
                    Fx.sound(w, x, y, z, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.5f);
                }
            }
            case "evil_mage" -> {
                if (c % 3 != 0) return;
                LivingEntity e = nearest(w, m, 16);
                if (e == null) return;
                beam(w, m.getEyePos(), e.getEyePos());
                e.damage(w.getDamageSources().magic(), 6f);
                e.setOnFireFor(4);
                Fx.sound(w, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }
            case "white_mage" -> {
                if (c % 2 != 0) return;
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(10), Creatures::prey)) {
                    if (e.getHealth() < e.getMaxHealth()) {
                        e.heal(4f);
                        Fx.particles(w, ParticleTypes.HEART, e.getX(), e.getY() + 1.5, e.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                for (LivingEntity u : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(10), e -> e instanceof ZombieEntity || e instanceof AbstractSkeletonEntity)) {
                    u.damage(w.getDamageSources().magic(), 5f);
                    Fx.particles(w, ParticleTypes.END_ROD, u.getX(), u.getY() + 1, u.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
                }
            }
            case "druid" -> {
                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, x, y + 1, z, 3, 0.5, 0.6, 0.5, 0.0);
                if (c % 3 == 0) {
                    BlockPos p = Fx.surface(w, (int) x + r.nextInt(13) - 6, (int) z + r.nextInt(13) - 6);
                    BlockState below = w.getBlockState(p.down());
                    if (w.getBlockState(p).isAir() && (below.isOf(Blocks.GRASS_BLOCK) || below.isOf(Blocks.DIRT) || below.isOf(Blocks.PODZOL))) {
                        w.setBlockState(p, PLANTS[r.nextInt(PLANTS.length)].getDefaultState(), Block.NOTIFY_ALL);
                    } else if (below.isOf(Blocks.DIRT) || below.isOf(Blocks.SAND) || below.isOf(Blocks.COARSE_DIRT)) {
                        w.setBlockState(p.down(), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
                if (c % 15 == 0 && w.getEntitiesByClass(net.minecraft.entity.passive.AnimalEntity.class, m.getBoundingBox().expand(16), e -> true).size() < 10) {
                    EntityType<? extends MobEntity> at = switch (r.nextInt(5)) { case 0 -> EntityType.SHEEP; case 1 -> EntityType.COW; case 2 -> EntityType.RABBIT; case 3 -> EntityType.FOX; default -> EntityType.WOLF; };
                    MobEntity a = at.create(w);
                    if (a != null) {
                        a.refreshPositionAndAngles(x, y, z, 0f, 0f);
                        w.spawnEntity(a);
                    }
                }
            }
            case "plague_doctor" -> {
                if (c % 2 != 0) return;
                for (ZombieVillagerEntity zv : w.getEntitiesByClass(ZombieVillagerEntity.class, m.getBoundingBox().expand(8), e -> true)) {
                    Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, zv.getX(), zv.getY() + 1, zv.getZ(), 15, 0.4, 0.8, 0.4, 0.0);
                    zv.convertTo(EntityType.VILLAGER, true);
                }
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, m.getBoundingBox().expand(10), Creatures::prey)) {
                    e.removeStatusEffect(StatusEffects.POISON);
                    e.removeStatusEffect(StatusEffects.WITHER);
                    e.removeStatusEffect(StatusEffects.HUNGER);
                    e.removeStatusEffect(StatusEffects.NAUSEA);
                    e.removeStatusEffect(StatusEffects.WEAKNESS);
                }
            }
            case "bandit" -> {
                if (c % 5 != 0) return;
                for (Kingdoms.K k : Kingdoms.K.values()) {
                    if (!k.alive || k.world != w || k.capital == null || k.capital.getSquaredDistance(m.getBlockPos()) > 48 * 48) continue;
                    Civ.City ct = Civ.city(k);
                    ct.food = Math.max(0, ct.food - 4);
                    ct.wood = Math.max(0, ct.wood - 3);
                    ct.ore = Math.max(0, ct.ore - 1);
                    Fx.particles(w, ParticleTypes.SMOKE, x, y + 1.5, z, 6, 0.3, 0.3, 0.3, 0.02);
                    if (r.nextInt(6) == 0) Chronicle.say(w.getServer(), k, "грабят бандиты!");
                }
            }
            case "fire_elemental" -> {
                for (int i = 0; i < 2; i++) {
                    BlockPos p = Fx.surface(w, (int) x + r.nextInt(7) - 3, (int) z + r.nextInt(7) - 3);
                    if (w.getBlockState(p).isAir() && !w.getBlockState(p.down()).isAir()) w.setBlockState(p, AbstractFireBlock.getState(w, p), Block.NOTIFY_ALL);
                }
                Fx.particles(w, ParticleTypes.LAVA, x, y + 1, z, 3, 0.4, 0.5, 0.4, 0.0);
            }
            case "crystal_golem" -> {
                Fx.particles(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.getDefaultState()), x, y + 1.5, z, 8, 0.5, 0.8, 0.5, 0.1);
                if (c % 10 == 0) {
                    BlockPos p = Fx.surface(w, (int) x + r.nextInt(9) - 4, (int) z + r.nextInt(9) - 4);
                    if (w.getBlockState(p).isAir() && w.getBlockState(p.down()).isSolidBlock(w, p.down())) w.setBlockState(p, Blocks.AMETHYST_CLUSTER.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            case "fire_skull" -> {
                LivingEntity e = nearest(w, m, 10);
                Fx.particles(w, ParticleTypes.FLAME, x, y + 0.4, z, 4, 0.2, 0.2, 0.2, 0.01);
                if (e != null && e.squaredDistanceTo(m) < 9) {
                    e.setOnFireFor(3);
                    e.damage(w.getDamageSources().magic(), 2f);
                }
            }
            default -> {
                LivingEntity e = nearest(w, m, 12);
                if (e != null && m.isOnGround()) {
                    Vec3d d = e.getPos().subtract(m.getPos()).normalize();
                    m.setVelocity(d.x * 0.6, 0.7, d.z * 0.6);
                    m.velocityModified = true;
                }
            }
        }
    }

    public static void clear() {
        timer = 0;
    }
}
