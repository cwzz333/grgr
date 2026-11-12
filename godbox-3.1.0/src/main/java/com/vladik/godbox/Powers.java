package com.vladik.godbox;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World.ExplosionSourceType;

public final class Powers {
    private Powers() {
    }

    private static void effect(LivingEntity e, RegistryEntry<StatusEffect> type, int ticks, int amp) {
        e.addStatusEffect(new StatusEffectInstance(type, ticks, amp));
    }

    public static void use(ServerPlayerEntity god, Power power, BlockPos pos, int brush, boolean first) {
        if (power != null && GodManager.isGod(god)) {
            if (Powers3.early(god, power, pos, brush, first)) return;
            if (power.continuous || first) {
                ServerWorld w = god.getServerWorld();
                if (w.isChunkLoaded(pos)) {
                    Fx.shape = MathHelper.clamp(brush / 100, 0, 3);
                    brush = MathHelper.clamp(brush % 100, 1, 10);
                    if (first && Powers3.recordsUndo(power)) Undo.begin(w, god.getUuid());
                    Vec3d c = new Vec3d((double)pos.getX() + 0.5, (double)pos.getY() + 1.0, (double)pos.getZ() + 0.5);
                    Random r = w.getRandom();
                    if (power.entity != null) {
                        spawn(w, power.entity, pos, power.continuous ? Math.max(1, brush / 2) : 1, brush,
                                power == Power.SPAWN_TITAN ? "titan" : power == Power.SPAWN_UFO ? "ufo" : null);
                    } else {
                        switch (power) {
                            case BOMB:
                                w.createExplosion(null, c.x, c.y, c.z, 4.0F, false, ExplosionSourceType.TNT);
                                Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1.0, 1.0, 1.0, 0.0);
                                break;
                            case NAPALM:
                                w.createExplosion(null, c.x, c.y, c.z, 3.0F, true, ExplosionSourceType.TNT);

                                for (BlockPos pxxxxxxx : Fx.brush(w, pos, 6)) {
                                    if (r.nextFloat() < 0.6F) {
                                        Fx.fire(w, pxxxxxxx);
                                    }
                                }

                                Fx.particles(w, ParticleTypes.FLAME, c.x, c.y + 1.0, c.z, 250, 4.0, 1.5, 4.0, 0.05);

                                for (LivingEntity exx : Fx.living(w, c, 7.0)) {
                                    exx.setOnFireFor(10.0F);
                                }
                                break;
                            case CLUSTER:
                                for (int i = 0; i < 10; i++) {
                                    double ox = c.x + (r.nextDouble() - 0.5) * 14.0;
                                    double oz = c.z + (r.nextDouble() - 0.5) * 14.0;
                                    Tasks.delay(i * 3 + 1, () -> {
                                        BlockPos s = Fx.surface(w, MathHelper.floor(ox), MathHelper.floor(oz));
                                        w.createExplosion(null, ox, (double)s.getY(), oz, 2.5F, false, ExplosionSourceType.TNT);
                                        Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, ox, (double)s.getY(), oz, 1, 0.0, 0.0, 0.0, 0.0);
                                    });
                                }
                                break;
                            case ATOMIC:
                                Tasks.add(new Disasters.Atomic(w, pos));
                                break;
                            case ANTIMATTER:
                                antimatter(w, pos, c, Math.min(14, 5 + brush));
                                break;
                            case LIGHTNING:
                                LightningEntity l = (LightningEntity)EntityType.LIGHTNING_BOLT.create(w);
                                if (l != null) {
                                    l.refreshPositionAfterTeleport(c.x, c.y - 0.5, c.z);
                                    w.spawnEntity(l);
                                }
                                break;
                            case METEOR:
                                Tasks.add(new Disasters.Meteor(w, pos));
                                break;
                            case TORNADO:
                                Tasks.add(new Disasters.Tornado(w, c.x, c.z));
                                break;
                            case EARTHQUAKE:
                                Tasks.add(new Disasters.Quake(w, pos));
                                break;
                            case FIRE:
                                for (BlockPos pxxxxxxx : Fx.brush(w, pos, brush)) {
                                    Fx.fire(w, pxxxxxxx);
                                }

                                for (LivingEntity exx : Fx.living(w, c, (double)brush + 0.5)) {
                                    exx.setOnFireFor(6.0F);
                                }
                                break;
                            case LAVA:
                                for (BlockPos pxxxxxx : Fx.brush(w, pos, Math.max(0, brush - 1))) {
                                    if (w.getBlockState(pxxxxxx).isReplaceable()) {
                                        w.setBlockState(pxxxxxx, Blocks.LAVA.getDefaultState());
                                    }
                                }
                                break;
                            case ACID:
                                for (BlockPos p : Fx.brush(w, pos, brush)) {
                                    BlockPos t = p.down();
                                    int depth = 1 + r.nextInt(2);

                                    for (int d = 0; d < depth; d++) {
                                        BlockPos q = t.down(d);
                                        if (Fx.breakable(w, q, w.getBlockState(q), 50.0F)) {
                                            w.setBlockState(q, Blocks.AIR.getDefaultState());
                                        }
                                    }

                                    if (r.nextFloat() < 0.3F) {
                                        Fx.particles(
                                            w,
                                            ParticleTypes.ITEM_SLIME,
                                            (double)p.getX() + 0.5,
                                            (double)p.getY(),
                                            (double)p.getZ() + 0.5,
                                            3,
                                            0.3,
                                            0.3,
                                            0.3,
                                            0.05
                                        );
                                    }
                                }

                                Fx.particles(w, ParticleTypes.SNEEZE, c.x, c.y, c.z, 40, (double)brush * 0.5, 0.5, (double)brush * 0.5, 0.02);

                                for (LivingEntity e : Fx.living(w, c, (double)brush + 0.5)) {
                                    e.damage(w.getDamageSources().magic(), 3.0F);
                                    effect(e, StatusEffects.POISON, 100, 1);
                                }

                                if (first) {
                                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.BLOCK_LAVA_EXTINGUISH, 3.0F, 1.5F);
                                }
                                break;
                            case RAIN:
                                for (BlockPos p : Fx.brush(w, pos, brush)) {
                                    if (w.getBlockState(p).isIn(BlockTags.FIRE)) {
                                        w.setBlockState(p, Blocks.AIR.getDefaultState());
                                    }

                                    if (r.nextFloat() < 0.25F) {
                                        Fx.grow(w, p);
                                        Fx.grow(w, p.down());
                                    }

                                    if (r.nextFloat() < 0.15F) {
                                        Fx.particles(
                                            w,
                                            ParticleTypes.FALLING_WATER,
                                            (double)p.getX() + 0.5,
                                            (double)(p.getY() + 8),
                                            (double)p.getZ() + 0.5,
                                            4,
                                            0.5,
                                            2.0,
                                            0.5,
                                            0.0
                                        );
                                    }
                                }

                                Fx.particles(w, ParticleTypes.SPLASH, c.x, c.y, c.z, 40, (double)brush * 0.5, 0.2, (double)brush * 0.5, 0.0);

                                for (LivingEntity e : Fx.living(w, c, (double)brush + 0.5)) {
                                    e.extinguish();
                                }

                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.WEATHER_RAIN, 3.0F, 1.0F);
                                }
                                break;
                            case FREEZE:
                                for (BlockPos pxxxxx : Fx.brush(w, pos, brush)) {
                                    BlockPos t = pxxxxx.down();
                                    FluidState fs = w.getFluidState(t);
                                    if (fs.isIn(FluidTags.WATER) && fs.isStill()) {
                                        w.setBlockState(t, Blocks.ICE.getDefaultState());
                                    } else if (fs.isIn(FluidTags.LAVA) && fs.isStill()) {
                                        w.setBlockState(t, Blocks.OBSIDIAN.getDefaultState());
                                    } else if (w.getBlockState(pxxxxx).isIn(BlockTags.FIRE)) {
                                        w.setBlockState(pxxxxx, Blocks.AIR.getDefaultState());
                                    } else if (w.isAir(pxxxxx) && Blocks.SNOW.getDefaultState().canPlaceAt(w, pxxxxx)) {
                                        w.setBlockState(pxxxxx, Blocks.SNOW.getDefaultState());
                                    }
                                }

                                Fx.particles(w, ParticleTypes.SNOWFLAKE, c.x, c.y + 1.0, c.z, 60, (double)brush * 0.5, 1.0, (double)brush * 0.5, 0.02);

                                for (LivingEntity exx : Fx.living(w, c, (double)brush + 0.5)) {
                                    exx.setFrozenTicks(400);
                                    effect(exx, StatusEffects.SLOWNESS, 200, 3);
                                }
                                break;
                            case DIVINE_LIGHT:
                                Tasks.add(new Disasters.Light(w, pos));
                                break;
                            case BLESSING:
                                for (LivingEntity e : Fx.living(w, c, (double)(3 + brush))) {
                                    effect(e, StatusEffects.REGENERATION, 600, 1);
                                    effect(e, StatusEffects.STRENGTH, 1200, 0);
                                    effect(e, StatusEffects.SPEED, 1200, 0);
                                    e.heal(4.0F);
                                }

                                Fx.particles(
                                    w, ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.0, c.z, 60, 2.0 + (double)brush * 0.5, 1.0, 2.0 + (double)brush * 0.5, 0.0
                                );
                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.ENTITY_PLAYER_LEVELUP, 4.0F, 1.2F);
                                }
                                break;
                            case CURSE:
                                for (LivingEntity e : Fx.living(w, c, (double)(3 + brush))) {
                                    effect(e, StatusEffects.WITHER, 200, 0);
                                    effect(e, StatusEffects.WEAKNESS, 1200, 1);
                                    effect(e, StatusEffects.SLOWNESS, 600, 1);
                                }

                                Fx.particles(w, ParticleTypes.WITCH, c.x, c.y + 1.0, c.z, 80, 2.0 + (double)brush * 0.5, 1.0, 2.0 + (double)brush * 0.5, 0.0);
                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 4.0F, 1.0F);
                                }
                                break;
                            case SHIELD:
                                for (LivingEntity e : Fx.living(w, c, (double)(3 + brush))) {
                                    effect(e, StatusEffects.RESISTANCE, 1200, 3);
                                    effect(e, StatusEffects.ABSORPTION, 1200, 2);
                                    effect(e, StatusEffects.FIRE_RESISTANCE, 1200, 0);
                                }

                                Fx.particles(
                                    w, ParticleTypes.ENCHANTED_HIT, c.x, c.y + 1.0, c.z, 80, 2.0 + (double)brush * 0.5, 1.0, 2.0 + (double)brush * 0.5, 0.1
                                );
                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.BLOCK_ANVIL_USE, 3.0F, 1.4F);
                                }
                                break;
                            case MADNESS:
                                List<LivingEntity> victims = Fx.living(w, c, 16.0);

                                for (LivingEntity e : Fx.living(w, c, (double)(4 + brush))) {
                                    if (e instanceof MobEntity) {
                                        MobEntity mob = (MobEntity)e;
                                        if (victims.size() >= 2) {
                                            LivingEntity target = victims.get(r.nextInt(victims.size()));
                                            if (target != mob) {
                                                mob.setTarget(target);
                                                effect(mob, StatusEffects.STRENGTH, 600, 1);
                                                Fx.particles(
                                                    w, ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 3, 0.3, 0.3, 0.3, 0.0
                                                );
                                            }
                                        }
                                    }
                                }

                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.ENTITY_RAVAGER_ROAR, 4.0F, 1.0F);
                                }
                                break;
                            case FINGER:
                                for (LivingEntity exx : Fx.living(w, c, 1.5 + (double)brush * 0.5)) {
                                    exx.damage(w.getDamageSources().generic(), 3.0F);
                                    Fx.push(exx, (r.nextDouble() - 0.5) * 0.6, 0.8, (r.nextDouble() - 0.5) * 0.6);
                                }

                                Fx.particles(w, ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.3);
                                Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 3.0F, 1.0F);
                                break;
                            case MAGNET:
                                double rad = (double)(5 + brush * 2);
                                Vec3d to = c.add(0.0, 2.0, 0.0);
                                Box box = new Box(c.x - rad, c.y - rad, c.z - rad, c.x + rad, c.y + rad + 10.0, c.z + rad);

                                for (Entity exx : w.getOtherEntities(null, box, en -> !GodManager.isGodEntity(en) && !en.isSpectator())) {
                                    Vec3d dx = to.subtract(exx.getPos());
                                    double len = dx.length();
                                    if (len < 0.7) {
                                        exx.setVelocity(Vec3d.ZERO);
                                    } else {
                                        exx.setVelocity(dx.normalize().multiply(Math.min(1.2, 0.15 + len * 0.08)));
                                    }

                                    exx.velocityModified = true;
                                    exx.fallDistance = 0.0F;
                                }

                                Fx.particles(w, ParticleTypes.PORTAL, to.x, to.y, to.z, 20, 1.0, 1.0, 1.0, 0.5);
                                break;
                            case LIFE_ERASER:
                                for (LivingEntity ex : Fx.living(w, c, (double)(brush + 1))) {
                                    if (!(ex instanceof PlayerEntity)) {
                                        Fx.particles(w, ParticleTypes.SOUL, ex.getX(), ex.getY() + 0.5, ex.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
                                        ex.kill();
                                    }
                                }

                                Fx.particles(w, ParticleTypes.SMOKE, c.x, c.y, c.z, 30, (double)brush * 0.5, 0.5, (double)brush * 0.5, 0.01);
                                break;
                            case RAISE:
                                for (BlockPos pxxxx : Fx.brush(w, pos, brush)) {
                                    BlockPos t = pxxxx.down();
                                    BlockState s = w.getBlockState(t);
                                    if (!s.isAir()
                                        && s.getFluidState().isEmpty()
                                        && s.getHardness(w, t) >= 0.0F
                                        && !s.hasBlockEntity()
                                        && !s.isIn(BlockTags.LEAVES)
                                        && !s.isIn(BlockTags.LOGS)
                                        && s.isFullCube(w, t)) {
                                        w.setBlockState(pxxxx, s);
                                        if (s.isOf(Blocks.GRASS_BLOCK)) {
                                            w.setBlockState(t, Blocks.DIRT.getDefaultState());
                                        }
                                    }
                                }
                                break;
                            case LOWER:
                                for (BlockPos pxxx : Fx.brush(w, pos, brush)) {
                                    BlockPos t = pxxx.down();
                                    BlockState s = w.getBlockState(t);
                                    if (s.getHardness(w, t) >= 0.0F && !s.isAir()) {
                                        w.setBlockState(t, Blocks.AIR.getDefaultState());
                                        BlockState below = w.getBlockState(t.down());
                                        if (below.isOf(Blocks.DIRT) && r.nextFloat() < 0.5F) {
                                            w.setBlockState(t.down(), Blocks.GRASS_BLOCK.getDefaultState());
                                        }
                                    }
                                }
                                break;
                            case WATER:
                                for (BlockPos pxx : Fx.brush(w, pos, Math.max(0, brush - 1))) {
                                    if (w.getBlockState(pxx).isReplaceable()) {
                                        w.setBlockState(pxx, Blocks.WATER.getDefaultState());
                                    }
                                }
                                break;
                            case SPONGE:
                                int radD9 = brush + 2;
                                Mutable m = new Mutable();

                                for (int dx = -radD9; dx <= radD9; dx++) {
                                    for (int dy = -radD9; dy <= radD9; dy++) {
                                        for (int dz = -radD9; dz <= radD9; dz++) {
                                            m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                                            if (!w.getFluidState(m).isEmpty()) {
                                                BlockState s = w.getBlockState(m);
                                                if (s.getBlock() instanceof FluidBlock) {
                                                    w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
                                                } else if (s.contains(Properties.WATERLOGGED)) {
                                                    w.setBlockState(m, (BlockState)s.with(Properties.WATERLOGGED, false));
                                                } else {
                                                    w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
                                                }
                                            }
                                        }
                                    }
                                }

                                Fx.particles(w, ParticleTypes.SPLASH, c.x, c.y, c.z, 60, (double)radD9 * 0.5, 1.0, (double)radD9 * 0.5, 0.0);
                                if (first) {
                                    Fx.sound(w, c.x, c.y + 5.0, c.z, SoundEvents.BLOCK_SPONGE_ABSORB, 3.0F, 1.0F);
                                }
                                break;
                            case GRASS:
                                BlockState[] plants = new BlockState[]{
                                    Blocks.SHORT_GRASS.getDefaultState(),
                                    Blocks.SHORT_GRASS.getDefaultState(),
                                    Blocks.DANDELION.getDefaultState(),
                                    Blocks.POPPY.getDefaultState(),
                                    Blocks.CORNFLOWER.getDefaultState(),
                                    Blocks.OXEYE_DAISY.getDefaultState(),
                                    Blocks.AZURE_BLUET.getDefaultState()
                                };

                                for (BlockPos px : Fx.brush(w, pos, brush)) {
                                    BlockPos t = px.down();
                                    BlockState s = w.getBlockState(t);
                                    if (s.isOf(Blocks.DIRT)
                                        || s.isOf(Blocks.COARSE_DIRT)
                                        || s.isOf(Blocks.ROOTED_DIRT)
                                        || s.isOf(Blocks.SAND)
                                        || s.isOf(Blocks.GRAVEL)) {
                                        w.setBlockState(t, Blocks.GRASS_BLOCK.getDefaultState());
                                    } else if (s.isOf(Blocks.GRASS_BLOCK) && w.isAir(px) && r.nextFloat() < 0.3F) {
                                        BlockState plant = plants[r.nextInt(plants.length)];
                                        if (plant.canPlaceAt(w, px)) {
                                            w.setBlockState(px, plant);
                                        }
                                    }
                                }

                                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 30, (double)brush * 0.5, 0.3, (double)brush * 0.5, 0.0);
                                break;
                            case FOREST:
                                SaplingBlock[] saplings = new SaplingBlock[]{
                                    (SaplingBlock)Blocks.OAK_SAPLING, (SaplingBlock)Blocks.BIRCH_SAPLING, (SaplingBlock)Blocks.SPRUCE_SAPLING
                                };

                                for (BlockPos p : Fx.brush(w, pos, brush)) {
                                    boolean center = p.getX() == pos.getX() && p.getZ() == pos.getZ();
                                    if ((center || !(r.nextFloat() > 0.12F)) && w.getBlockState(p.down()).isIn(BlockTags.DIRT) && w.isAir(p)) {
                                        SaplingBlock sap = saplings[r.nextInt(saplings.length)];
                                        BlockState st = (BlockState)sap.getDefaultState().with(SaplingBlock.STAGE, 1);
                                        w.setBlockState(p, st);
                                        sap.generate(w, p, st, r);
                                    }
                                }

                                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.0, c.z, 30, (double)brush * 0.5, 1.0, (double)brush * 0.5, 0.0);
                                break;
                            default:
                                if (Powers3.handles(power)) Powers3.use(god, power, w, pos, c, brush, r); else Powers2.use(god, power, w, pos, c, brush, r);
                        }
                    }
                }
            }
        }
    }

    /** model: своя модель для призванного этой силой (метка godbox_model_* вернёт её после перезахода) или null. */
    private static void spawn(ServerWorld w, EntityType<?> type, BlockPos pos, int count, int brush, String model) {
        Random r = w.getRandom();

        for (int i = 0; i < count; i++) {
            int x = pos.getX() + (count > 1 ? r.nextInt(brush * 2 + 1) - brush : 0);
            int z = pos.getZ() + (count > 1 ? r.nextInt(brush * 2 + 1) - brush : 0);
            BlockPos s = Fx.surface(w, x, z);
            Entity e = type.spawn(w, s, SpawnReason.TRIGGERED);
            if (e != null) {
                if (model != null && e instanceof LivingEntity le) Models.attachCustom(le, model);
                Fx.particles(w, ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private static void antimatter(ServerWorld w, BlockPos pos, Vec3d c, int rad) {
        Mutable m = new Mutable();

        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rad * rad) {
                        m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        BlockState s = w.getBlockState(m);
                        if (!s.isAir() && s.getHardness(w, m) >= 0.0F) {
                            w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
                        }
                    }
                }
            }
        }

        Vec3d center = Vec3d.ofCenter(pos);

        for (Entity e : w.getOtherEntities(
            null, new Box(pos).expand((double)rad), en -> !(en instanceof PlayerEntity) && en.squaredDistanceTo(center) <= (double)(rad * rad)
        )) {
            e.kill();
        }

        Fx.particles(w, ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 400, (double)rad * 0.5, (double)rad * 0.5, (double)rad * 0.5, 0.2);
        Fx.particles(w, ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        Fx.sound(w, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, 6.0F, 0.5F);
        Fx.sound(w, center.x, center.y, center.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 6.0F, 0.5F);
    }
}
