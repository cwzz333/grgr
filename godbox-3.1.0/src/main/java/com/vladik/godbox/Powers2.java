package com.vladik.godbox;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.FireworkExplosionComponent.Type;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World.ExplosionSourceType;
import org.joml.Vector3f;

public final class Powers2 {
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.75F, 0.02F, 0.02F), 1.6F);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.1F), 1.2F);
    private static final Block[] FLOWERS = new Block[]{
        Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.ORANGE_TULIP
    };
    private static final Item[] LOOT = new Item[]{
        Items.DIAMOND,
        Items.EMERALD,
        Items.IRON_INGOT,
        Items.BREAD,
        Items.ARROW,
        Items.COOKED_BEEF,
        Items.GOLDEN_CARROT,
        Items.EXPERIENCE_BOTTLE,
        Items.LAPIS_LAZULI,
        Items.REDSTONE,
        Items.ENDER_PEARL,
        Items.SADDLE,
        Items.NAME_TAG,
        Items.GOLDEN_APPLE,
        Items.TOTEM_OF_UNDYING
    };
    private static final Item[] GIFTS = new Item[]{
        Items.COOKIE,
        Items.CAKE,
        Items.PUMPKIN_PIE,
        Items.DIAMOND,
        Items.EMERALD,
        Items.GOLDEN_APPLE,
        Items.FIREWORK_ROCKET,
        Items.SNOWBALL,
        Items.SWEET_BERRIES
    };
    private static final int[] COLORS = new int[]{16724016, 16755200, 16777024, 4259648, 4243711, 4219135, 12599551, 16777215, 16736448};
    static final Map<ServerWorld, Powers2.Epidemic> PLAGUES = new HashMap<>();

    private Powers2() {
    }

    private static void effect(LivingEntity e, RegistryEntry<StatusEffect> type, int ticks, int amp) {
        e.addStatusEffect(new StatusEffectInstance(type, ticks, amp));
    }

    private static void set(ServerWorld w, BlockPos p, BlockState s) {
        w.setBlockState(p, s, 3);
    }

    private static boolean place(ServerWorld w, BlockPos p, BlockState s) {
        if (w.getBlockState(p).isAir() && s.canPlaceAt(w, p)) {
            set(w, p, s);
            return true;
        } else {
            return false;
        }
    }

    private static void tree(ServerWorld w, BlockPos p, Block sapling) {
        BlockState s = sapling.getDefaultState();
        if (place(w, p, s)) {
            if (sapling instanceof SaplingBlock sb) {
                sb.generate(w, p, (BlockState)s.with(SaplingBlock.STAGE, 1), w.getRandom());
            }
        }
    }

    private static boolean natural(BlockState s) {
        return s.isIn(BlockTags.DIRT)
            || s.isIn(BlockTags.SAND)
            || s.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || s.isIn(BlockTags.NYLIUM)
            || s.isOf(Blocks.GRAVEL)
            || s.isOf(Blocks.SNOW_BLOCK)
            || s.isOf(Blocks.NETHERRACK)
            || s.isOf(Blocks.SOUL_SOIL)
            || s.isOf(Blocks.SOUL_SAND)
            || s.isOf(Blocks.AMETHYST_BLOCK)
            || s.isOf(Blocks.CALCITE)
            || s.isOf(Blocks.MAGMA_BLOCK)
            || s.isOf(Blocks.ICE)
            || s.isOf(Blocks.PACKED_ICE)
            || s.isOf(Blocks.SANDSTONE)
            || s.isOf(Blocks.DIRT_PATH)
            || s.isOf(Blocks.CLAY)
            || s.isOf(Blocks.TERRACOTTA)
            || s.isOf(Blocks.SCULK)
            || s.isOf(Blocks.NETHER_WART_BLOCK);
    }

    private static boolean cmd(ServerWorld w, String command) {
        MinecraftServer s = w.getServer();
        ServerCommandSource src = s.getCommandSource().withWorld(w).withLevel(4).withSilent();

        try {
            return s.getCommandManager().getDispatcher().execute(command, src) > 0;
        } catch (Exception var5) {
            System.out.println("[GodBox] command failed: " + command + " (" + var5.getMessage() + ")");
            return false;
        }
    }

    private static void drop(ServerWorld w, double x, double y, double z, ItemStack st) {
        ItemEntity it = new ItemEntity(w, x, y, z, st);
        it.setPickupDelay(10);
        w.spawnEntity(it);
    }

    private static void firework(ServerWorld w, double x, double y, double z, Random r) {
        ItemStack st = new ItemStack(Items.FIREWORK_ROCKET);
        Type[] types = Type.values();
        FireworkExplosionComponent ex = new FireworkExplosionComponent(
            types[r.nextInt(types.length)],
            IntList.of(COLORS[r.nextInt(COLORS.length)], COLORS[r.nextInt(COLORS.length)]),
            IntList.of(COLORS[r.nextInt(COLORS.length)]),
            r.nextBoolean(),
            r.nextBoolean()
        );
        st.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1 + r.nextInt(2), List.of(ex)));
        w.spawnEntity(new FireworkRocketEntity(w, x, y, z, st));
    }

    private static void msg(ServerPlayerEntity god, String text, Formatting f) {
        god.sendMessage(Text.literal(text).formatted(f), true);
    }

    public static void use(ServerPlayerEntity god, Power power, ServerWorld w, BlockPos pos, Vec3d c, int brush, Random r) {
        if (power.category == Category.BIOMES) {
            biome(power, w, pos, brush, r);
        } else if (power.category == Category.BUILD) {
            build(god, power, w, pos);
        } else {
            switch (power) {
                case GRENADE:
                    w.createExplosion(null, c.x, c.y, c.z, 2.0F, false, ExplosionSourceType.TNT);
                    break;
                case TNT:
                    for (int i = 0; i < Math.max(1, brush / 3); i++) {
                        TntEntity tnt = new TntEntity(
                            w, c.x + r.nextGaussian() * (double)brush * 0.4, c.y + 12.0, c.z + r.nextGaussian() * (double)brush * 0.4, null
                        );
                        tnt.setFuse(50 + r.nextInt(20));
                        w.spawnEntity(tnt);
                    }

                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.ENTITY_TNT_PRIMED, 1.0F, 1.0F);
                    break;
                case TIMED_BOMB: {
                    int[] t = new int[]{0};
                    Tasks.add(() -> {
                        t[0]++;
                        if (t[0] % 10 == 0) {
                            float pitch = 0.6F + (float)t[0] / 100.0F;
                            Fx.sound(w, c.x, c.y, c.z, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 2.0F, pitch);
                            Fx.particles(w, ParticleTypes.FLAME, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.02);
                        }

                        Fx.particles(w, ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 1, 0.05, 0.05, 0.05, 0.01);
                        if (t[0] >= 100) {
                            w.createExplosion(null, c.x, c.y, c.z, 6.0F, false, ExplosionSourceType.TNT);
                            return true;
                        } else {
                            return false;
                        }
                    });
                    break;
                }
                case LANDMINE:
                    BlockPos s = Fx.surface(w, pos.getX(), pos.getZ());
                    BlockPos gxxx = s.down();
                    BlockState gsxxxxx = w.getBlockState(gxxx);
                    if (gsxxxxx.isAir() || !gsxxxxx.getFluidState().isEmpty() || gsxxxxx.getHardness(w, gxxx) < 0.0F) {
                        return;
                    }

                    if (!w.getBlockState(s).isAir() && w.getBlockState(s).isReplaceable()) {
                        set(w, s, Blocks.AIR.getDefaultState());
                    }

                    set(w, gxxx, Blocks.TNT.getDefaultState());
                    place(w, s, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.BLOCK_STONE_PLACE, 1.0F, 0.8F);
                    break;
                case WATER_BOMB:
                    w.createExplosion(null, c.x, c.y, c.z, 2.5F, false, ExplosionSourceType.NONE);
                    int rad = 3 + brush / 2;

                    for (int dx = -rad; dx <= rad; dx++) {
                        for (int dyx = -rad; dyx <= 2; dyx++) {
                            for (int dz = -rad; dz <= rad; dz++) {
                                if (dx * dx + dyx * dyx + dz * dz <= rad * rad) {
                                    BlockPos pxx = pos.add(dx, dyx + 1, dz);
                                    BlockState st = w.getBlockState(pxx);
                                    if (st.isIn(BlockTags.FIRE)) {
                                        set(w, pxx, Blocks.AIR.getDefaultState());
                                    } else if (st.isAir() && r.nextFloat() < 0.6F) {
                                        set(w, pxx, Blocks.WATER.getDefaultState());
                                    }
                                }
                            }
                        }
                    }

                    for (LivingEntity exxxx : Fx.living(w, c, (double)(rad + 3))) {
                        exxxx.extinguish();
                    }

                    Fx.particles(w, ParticleTypes.SPLASH, c.x, c.y + 1.0, c.z, 300, (double)rad, 1.5, (double)rad, 0.5);
                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_SPLASH, 3.0F, 0.7F);
                    break;
                case SANTA_BOMB:
                    w.createExplosion(null, c.x, c.y, c.z, 3.0F, false, ExplosionSourceType.TNT);
                    Tasks.delay(3, () -> {
                        for (BlockPos p : Fx.brush(w, pos, 6 + brush / 2)) {
                            if (r.nextFloat() < 0.7F) {
                                place(w, p, Blocks.SNOW.getDefaultState());
                            }
                        }

                        for (int ixx = 0; ixx < 10; ixx++) {
                            ItemStack stx = new ItemStack(GIFTS[r.nextInt(GIFTS.length)]);
                            ItemEntity it = new ItemEntity(w, c.x, c.y + 1.0, c.z, stx);
                            it.setVelocity(r.nextGaussian() * 0.25, 0.5 + (double)r.nextFloat() * 0.3, r.nextGaussian() * 0.25);
                            it.setPickupDelay(20);
                            w.spawnEntity(it);
                        }

                        for (int ix = 0; ix < 4; ix++) {
                            firework(w, c.x + r.nextGaussian() * 2.0, c.y + 1.0, c.z + r.nextGaussian() * 2.0, r);
                        }

                        Fx.particles(w, ParticleTypes.SNOWFLAKE, c.x, c.y + 2.0, c.z, 300, 5.0, 3.0, 5.0, 0.1);
                    });
                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_LEVELUP, 2.0F, 1.5F);
                    break;
                case FIREWORKS:
                    for (int i = 0; i < Math.max(1, brush / 2); i++) {
                        firework(w, c.x + r.nextGaussian() * (double)brush * 0.5, c.y + 0.5, c.z + r.nextGaussian() * (double)brush * 0.5, r);
                    }
                    break;
                case CZAR_BOMBA:
                    czar(w, pos, c, r);
                    break;
                case GREY_GOO:
                    Tasks.add(new Powers2.GreyGoo(w, pos.up(), 2500 + brush * 300));
                    break;
                case METEOR_RAIN:
                    int spread = 8 + brush * 2;

                    for (int i = 0; i < 10; i++) {
                        int x = pos.getX() + r.nextInt(spread * 2 + 1) - spread;
                        int z = pos.getZ() + r.nextInt(spread * 2 + 1) - spread;
                        Tasks.delay(1 + i * 7 + r.nextInt(5), () -> Tasks.add(new Disasters.Meteor(w, Fx.surface(w, x, z).down())));
                    }
                    break;
                case VOLCANO:
                    Tasks.add(new Powers2.Volcano(w, pos.up(), 8 + brush));
                    break;
                case GEYSER:
                    Tasks.add(new Powers2.Geyser(w, c));
                    break;
                case HEATWAVE:
                    for (BlockPos pxx : Fx.brush(w, pos, brush + 2)) {
                        BlockPos gxx = pxx.down();
                        BlockState gsxxxx = w.getBlockState(gxx);
                        BlockState psxx = w.getBlockState(pxx);
                        if (psxx.isOf(Blocks.SNOW)) {
                            set(w, pxx, Blocks.AIR.getDefaultState());
                        }

                        if (gsxxxx.getFluidState().isIn(FluidTags.WATER) && gsxxxx.getBlock() instanceof FluidBlock) {
                            if (r.nextFloat() < 0.5F) {
                                set(w, gxx, Blocks.AIR.getDefaultState());
                            }
                        } else if (gsxxxx.isOf(Blocks.ICE) || gsxxxx.isOf(Blocks.PACKED_ICE)) {
                            set(w, gxx, Blocks.WATER.getDefaultState());
                        } else if (gsxxxx.isOf(Blocks.SNOW_BLOCK)) {
                            set(w, gxx, Blocks.AIR.getDefaultState());
                        } else if (gsxxxx.isOf(Blocks.GRASS_BLOCK) && r.nextFloat() < 0.15F) {
                            set(w, gxx, Blocks.COARSE_DIRT.getDefaultState());
                        } else if (gsxxxx.isIn(BlockTags.LEAVES) && r.nextFloat() < 0.08F) {
                            Fx.fire(w, pxx);
                        }

                        if (psxx.isReplaceable() && psxx.getFluidState().isEmpty() && !psxx.isAir() && r.nextFloat() < 0.3F) {
                            set(w, pxx, Blocks.AIR.getDefaultState());
                        }
                    }

                    for (LivingEntity exxx : Fx.living(w, c, (double)(brush + 3))) {
                        if (r.nextFloat() < 0.3F) {
                            exxx.setOnFireFor(3.0F);
                        }
                    }

                    Fx.particles(w, ParticleTypes.FLAME, c.x, c.y + 1.0, c.z, 15 + brush * 4, (double)brush, 1.0, (double)brush, 0.02);
                    Fx.particles(w, ParticleTypes.WHITE_SMOKE, c.x, c.y + 1.0, c.z, 20 + brush * 4, (double)brush, 1.0, (double)brush, 0.05);
                    break;
                case BLOOD_RAIN:
                    for (LivingEntity exxx : Fx.living(w, c, (double)(brush + 3))) {
                        exxx.heal(3.0F);
                        effect(exxx, StatusEffects.REGENERATION, 100, 0);
                    }

                    Fx.particles(w, BLOOD, c.x, c.y + 8.0, c.z, 60 + brush * 15, (double)brush, 4.0, (double)brush, 0.0);
                    Fx.particles(w, ParticleTypes.FALLING_LAVA, c.x, c.y + 10.0, c.z, 10 + brush * 3, (double)brush, 1.0, (double)brush, 0.0);
                    break;
                case SNOWFALL:
                    for (BlockPos px : Fx.brush(w, pos, brush + 2)) {
                        if (!(r.nextFloat() > 0.4F)) {
                            BlockState psxx = w.getBlockState(px);
                            BlockState gsxxxx = w.getBlockState(px.down());
                            if (gsxxxx.isOf(Blocks.WATER) && r.nextFloat() < 0.3F) {
                                set(w, px.down(), Blocks.ICE.getDefaultState());
                            } else if (psxx.isOf(Blocks.SNOW)) {
                                int l = (Integer)psxx.get(Properties.LAYERS);
                                if (l < 6) {
                                    set(w, px, (BlockState)psxx.with(Properties.LAYERS, l + 1));
                                }
                            } else if (psxx.isReplaceable() && psxx.getFluidState().isEmpty()) {
                                if (!psxx.isAir()) {
                                    set(w, px, Blocks.AIR.getDefaultState());
                                }

                                place(w, px, Blocks.SNOW.getDefaultState());
                            }
                        }
                    }

                    for (LivingEntity exxx : Fx.living(w, c, (double)(brush + 3))) {
                        exxx.setFrozenTicks(Math.min(300, exxx.getFrozenTicks() + 40));
                    }

                    Fx.particles(w, ParticleTypes.SNOWFLAKE, c.x, c.y + 6.0, c.z, 80 + brush * 15, (double)brush, 3.0, (double)brush, 0.02);
                    break;
                case TUMOR:
                    Tasks.add(new Powers2.Creep(w, pos, 8 + brush * 2, false));
                    break;
                case BIOMASS:
                    Tasks.add(new Powers2.Creep(w, pos, 8 + brush * 2, true));
                    break;
                case CURE:
                    Plague.cure(god, w, pos, brush);
                    Powers2.Epidemic ep = PLAGUES.get(w);

                    for (LivingEntity exxx : Fx.living(w, c, (double)(brush + 3))) {
                        if (ep != null) {
                            ep.sick.remove(exxx.getUuid());
                        }

                        if (exxx instanceof ZombieVillagerEntity zv) {
                            zv.convertTo(EntityType.VILLAGER, false);
                        } else {
                            exxx.clearStatusEffects();
                            exxx.extinguish();
                            exxx.setFrozenTicks(0);
                            exxx.setHealth(exxx.getMaxHealth());
                            Fx.particles(w, ParticleTypes.HEART, exxx.getX(), exxx.getY() + (double)exxx.getHeight(), exxx.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                        }
                    }

                    Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.0, c.z, 30 + brush * 5, (double)brush, 1.0, (double)brush, 0.0);
                    break;
                case PLAGUE:
                    Powers2.Epidemic epD1 = plague(w);

                    for (LivingEntity exxx : Fx.living(w, c, (double)(brush + 2))) {
                        epD1.sick.put(exxx.getUuid(), 600);
                    }

                    Fx.particles(w, ParticleTypes.SNEEZE, c.x, c.y + 1.0, c.z, 40 + brush * 6, (double)brush, 1.0, (double)brush, 0.02);
                    break;
                case ZOMBIE_INFECTION:
                    for (LivingEntity exx : Fx.living(w, c, (double)(brush + 2))) {
                        if (exx instanceof MobEntity) {
                            MobEntity mx = (MobEntity)exx;
                            EntityType var117 = mx.getType();
                            MobEntity var128 = null;
                            if (var117 == EntityType.VILLAGER || var117 == EntityType.WANDERING_TRADER) {
                                var128 = mx.convertTo(EntityType.ZOMBIE_VILLAGER, true);
                            } else if (var117 == EntityType.PIG || var117 == EntityType.PIGLIN) {
                                var128 = mx.convertTo(EntityType.ZOMBIFIED_PIGLIN, true);
                            } else if (var117 == EntityType.HORSE || var117 == EntityType.DONKEY) {
                                var128 = mx.convertTo(EntityType.ZOMBIE_HORSE, true);
                            } else if (var117 == EntityType.PILLAGER || var117 == EntityType.VINDICATOR || var117 == EntityType.WITCH) {
                                var128 = mx.convertTo(EntityType.ZOMBIE, true);
                            } else if (var117 == EntityType.HOGLIN) {
                                var128 = mx.convertTo(EntityType.ZOGLIN, true);
                            }

                            if (var128 != null) {
                                Fx.particles(w, ParticleTypes.SQUID_INK, var128.getX(), var128.getY() + 1.0, var128.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                            }
                        } else {
                            effect(exx, StatusEffects.HUNGER, 300, 1);
                            effect(exx, StatusEffects.NAUSEA, 200, 0);
                        }
                    }

                    Fx.particles(w, ParticleTypes.MYCELIUM, c.x, c.y + 1.0, c.z, 40 + brush * 6, (double)brush, 1.0, (double)brush, 0.0);
                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0F, 1.0F);
                    break;
                case MUSH_SPORES:
                    for (BlockPos px : Fx.brush(w, pos, brush + 1)) {
                        BlockState gsxxx = w.getBlockState(px.down());
                        if ((gsxxx.isOf(Blocks.GRASS_BLOCK) || gsxxx.isOf(Blocks.DIRT) || gsxxx.isOf(Blocks.PODZOL)) && r.nextFloat() < 0.4F) {
                            set(w, px.down(), Blocks.MYCELIUM.getDefaultState());
                        }

                        if (r.nextFloat() < 0.06F) {
                            place(w, px, (r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState());
                        }
                    }

                    for (LivingEntity ex : Fx.living(w, c, (double)(brush + 2))) {
                        if (ex.getType() == EntityType.COW && ex instanceof MobEntity mx) {
                            mx.convertTo(EntityType.MOOSHROOM, false);
                        } else {
                            effect(ex, StatusEffects.NAUSEA, 200, 0);
                        }
                    }

                    Fx.particles(w, ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.5, c.z, 60 + brush * 10, (double)brush, 1.5, (double)brush, 0.0);
                    break;
                case RAGE:
                    for (LivingEntity ex : Fx.living(w, c, (double)(brush + 3))) {
                        effect(ex, StatusEffects.STRENGTH, 600, 1);
                        effect(ex, StatusEffects.SPEED, 600, 1);
                        if (ex instanceof MobEntity mx) {
                            LivingEntity best = null;
                            double bd = Double.MAX_VALUE;

                            for (LivingEntity o : Fx.living(w, mx.getPos(), 12.0)) {
                                if (o != mx && o.getType() != mx.getType()) {
                                    double d = o.squaredDistanceTo(mx);
                                    if (d < bd) {
                                        bd = d;
                                        best = o;
                                    }
                                }
                            }

                            if (best != null) {
                                mx.setTarget(best);
                            }
                        }

                        Fx.particles(w, ParticleTypes.ANGRY_VILLAGER, ex.getX(), ex.getY() + (double)ex.getHeight(), ex.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                    }
                    break;
                case FRIENDSHIP:
                    for (LivingEntity ex : Fx.living(w, c, (double)(brush + 3))) {
                        if (ex instanceof MobEntity m) {
                            m.setTarget(null);
                            if (m instanceof Angerable a) {
                                a.stopAnger();
                            }
                        }

                        if (ex instanceof AnimalEntity a && !a.isBaby()) {
                            a.setLoveTicks(600);
                        }

                        ex.setAttacker(null);
                        Fx.particles(w, ParticleTypes.HEART, ex.getX(), ex.getY() + (double)ex.getHeight(), ex.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                    }
                    break;
                case SLEEP:
                    for (LivingEntity ex : Fx.living(w, c, (double)(brush + 3))) {
                        effect(ex, StatusEffects.SLOWNESS, 300, 6);
                        effect(ex, StatusEffects.WEAKNESS, 300, 2);
                        if (ex instanceof MobEntity m) {
                            m.setTarget(null);
                            m.getNavigation().stop();
                        }

                        Fx.particles(w, ParticleTypes.NOTE, ex.getX(), ex.getY() + (double)ex.getHeight() + 0.3, ex.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                    }

                    Fx.particles(w, ParticleTypes.WHITE_ASH, c.x, c.y + 1.0, c.z, 60 + brush * 10, (double)brush, 1.0, (double)brush, 0.0);
                    break;
                case GOLD_RAIN:
                    for (int i = 0; i < 2 + brush / 2; i++) {
                        Item item = r.nextFloat() < 0.2F ? Items.GOLD_INGOT : Items.GOLD_NUGGET;
                        drop(
                            w,
                            c.x + r.nextGaussian() * (double)brush * 0.6,
                            c.y + 10.0 + (double)r.nextInt(4),
                            c.z + r.nextGaussian() * (double)brush * 0.6,
                            new ItemStack(item)
                        );
                    }

                    Fx.particles(w, GOLD, c.x, c.y + 8.0, c.z, 20 + brush * 4, (double)brush, 3.0, (double)brush, 0.0);
                    break;
                case LOOT_RAIN:
                    for (int i = 0; i < 1 + brush / 3; i++) {
                        Item item = LOOT[Math.min(LOOT.length - 1, (int)(Math.pow((double)r.nextFloat(), 1.6) * (double)LOOT.length))];
                        drop(
                            w,
                            c.x + r.nextGaussian() * (double)brush * 0.6,
                            c.y + 10.0 + (double)r.nextInt(4),
                            c.z + r.nextGaussian() * (double)brush * 0.6,
                            new ItemStack(item)
                        );
                    }

                    Fx.particles(w, ParticleTypes.END_ROD, c.x, c.y + 8.0, c.z, 15 + brush * 3, (double)brush, 3.0, (double)brush, 0.02);
                    break;
                case MOUNTAIN:
                    int R = brush + 2;

                    for (BlockPos px : Fx.brush(w, pos, R)) {
                        double dx = (double)(px.getX() - pos.getX());
                        double dz = (double)(px.getZ() - pos.getZ());
                        double d = Math.sqrt(dx * dx + dz * dz);
                        int h = (int)Math.round((1.0 - d / (double)(R + 1)) * 3.0);

                        for (int i = 0; i < h; i++) {
                            BlockPos q = px.up(i);
                            if (!w.getBlockState(q).isReplaceable()) {
                                break;
                            }

                            BlockState sD2 = i == h - 1 && q.getY() > 115
                                ? Blocks.SNOW_BLOCK.getDefaultState()
                                : (r.nextFloat() < 0.15F ? Blocks.ANDESITE.getDefaultState() : Blocks.STONE.getDefaultState());
                            set(w, q, sD2);
                        }
                    }

                    Fx.particles(w, ParticleTypes.CLOUD, c.x, c.y + 1.0, c.z, 15, (double)brush, 1.0, (double)brush, 0.02);
                    break;
                case SAND:
                    for (BlockPos px : Fx.brush(w, pos, brush)) {
                        BlockPos gx = px.down();
                        BlockState gsxx = w.getBlockState(gx);
                        BlockState psx = w.getBlockState(px);
                        if (psx.isReplaceable() && psx.getFluidState().isEmpty() && !psx.isAir()) {
                            set(w, px, Blocks.AIR.getDefaultState());
                        }

                        if (gsxx.getFluidState().isIn(FluidTags.WATER) || natural(gsxx)) {
                            set(w, gx, Blocks.SAND.getDefaultState());
                        }
                    }
                    break;
                case REMOVE_LAVA:
                    int RD3 = brush + 1;

                    for (int dx = -RD3; dx <= RD3; dx++) {
                        for (int dz = -RD3; dz <= RD3; dz++) {
                            for (int dy = -RD3 - 3; dy <= RD3 + 3; dy++) {
                                BlockPos px = pos.add(dx, dy, dz);
                                BlockState st = w.getBlockState(px);
                                if (st.getBlock() instanceof FluidBlock && st.getFluidState().isIn(FluidTags.LAVA)) {
                                    set(w, px, Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }

                    Fx.particles(w, ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0, c.z, 30, (double)RD3, 1.0, (double)RD3, 0.02);
                    Fx.sound(w, c.x, c.y, c.z, SoundEvents.BLOCK_LAVA_EXTINGUISH, 1.0F, 1.0F);
                    break;
                case ROAD:
                    for (BlockPos px : Fx.brush(w, pos, Math.max(0, brush / 3))) {
                        BlockPos g = px.down();
                        BlockState gsx = w.getBlockState(g);
                        BlockState ps = w.getBlockState(px);
                        if (ps.isReplaceable() && ps.getFluidState().isEmpty() && !ps.isAir()) {
                            set(w, px, Blocks.AIR.getDefaultState());
                        }

                        if (gsx.isOf(Blocks.GRASS_BLOCK)
                            || gsx.isOf(Blocks.DIRT)
                            || gsx.isOf(Blocks.COARSE_DIRT)
                            || gsx.isOf(Blocks.PODZOL)
                            || gsx.isOf(Blocks.MYCELIUM)
                            || gsx.isOf(Blocks.ROOTED_DIRT)) {
                            set(w, g, Blocks.DIRT_PATH.getDefaultState());
                        } else if (natural(gsx) && !gsx.isOf(Blocks.DIRT_PATH)) {
                            set(w, g, Blocks.GRAVEL.getDefaultState());
                        }
                    }
                    break;
                case WALL:
                    for (BlockPos px : Fx.brush(w, pos, Math.max(0, (brush - 1) / 3))) {
                        for (int i = 0; i < 4; i++) {
                            BlockPos q = px.up(i);
                            BlockState qs = w.getBlockState(q);
                            if (!qs.isReplaceable()) {
                                break;
                            }

                            set(w, q, (r.nextFloat() < 0.15F ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).getDefaultState());
                        }
                    }
                    break;
                case FRUIT_BUSH:
                    for (BlockPos p : Fx.brush(w, pos, brush)) {
                        if (r.nextFloat() < 0.2F) {
                            place(w, p, (BlockState)Blocks.SWEET_BERRY_BUSH.getDefaultState().with(Properties.AGE_3, 3));
                        }
                    }
                    break;
                case FERTILIZER:
                    for (BlockPos p : Fx.brush(w, pos, brush)) {
                        Fx.grow(w, p);
                        Fx.grow(w, p.down());
                    }

                    for (LivingEntity e : Fx.living(w, c, (double)(brush + 2))) {
                        if (e instanceof PassiveEntity) {
                            PassiveEntity pe = (PassiveEntity)e;
                            if (pe.isBaby()) {
                                pe.setBreedingAge(0);
                            }
                        }
                    }

                    Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.0, c.z, 40 + brush * 8, (double)brush, 1.0, (double)brush, 0.0);
                    break;
                case LIFE_SEED:
                    List<BlockPos> list = Fx.brush(w, pos, brush);

                    for (BlockPos p : list) {
                        BlockState gs = w.getBlockState(p.down());
                        if (gs.isOf(Blocks.DIRT) || gs.isOf(Blocks.COARSE_DIRT) || gs.isOf(Blocks.SAND) && r.nextFloat() < 0.3F) {
                            set(w, p.down(), Blocks.GRASS_BLOCK.getDefaultState());
                        }

                        float f = r.nextFloat();
                        if (f < 0.12F) {
                            place(w, p, FLOWERS[r.nextInt(FLOWERS.length)].getDefaultState());
                        } else if (f < 0.25F) {
                            place(w, p, Blocks.SHORT_GRASS.getDefaultState());
                        }
                    }

                    if (r.nextFloat() < 0.4F && !list.isEmpty()) {
                        EntityType<?>[] animals = new EntityType[]{
                            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT, EntityType.HORSE
                        };
                        BlockPos sD4 = list.get(r.nextInt(list.size()));
                        Entity e = animals[r.nextInt(animals.length)].spawn(w, sD4, SpawnReason.TRIGGERED);
                        if (e != null) {
                            Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
                        }
                    }

                    Fx.particles(w, ParticleTypes.COMPOSTER, c.x, c.y + 1.0, c.z, 20 + brush * 4, (double)brush, 1.0, (double)brush, 0.0);
                    break;
                case WORLD_DAY: {
                    long t = w.getTimeOfDay();
                    w.setTimeOfDay((t / 24000L + 1L) * 24000L + 1000L);
                    msg(god, "☀ Наступило утро", Formatting.YELLOW);
                    break;
                }
                case WORLD_NIGHT: {
                    long t = w.getTimeOfDay();
                    long n = t / 24000L * 24000L + 13500L;
                    if (n <= t) {
                        n += 24000L;
                    }

                    w.setTimeOfDay(n);
                    msg(god, "☾ Наступила ночь", Formatting.BLUE);
                    break;
                }
                case WORLD_CLEAR:
                    w.setWeather(12000, 0, false, false);
                    msg(god, "Ясная погода", Formatting.AQUA);
                    break;
                case WORLD_RAIN:
                    w.setWeather(0, 6000, true, false);
                    msg(god, "Начался ливень", Formatting.AQUA);
                    break;
                case WORLD_STORM:
                    w.setWeather(0, 6000, true, true);
                    msg(god, "Началась гроза", Formatting.DARK_PURPLE);
                    break;
                case WORLD_CLEAR_ITEMS:
                    List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(64.0), ex -> true);

                    for (ItemEntity it : items) {
                        it.discard();
                    }

                    msg(god, "Убрано предметов: " + items.size(), Formatting.GREEN);
                    break;
                case WORLD_BANISH:
                    List<MobEntity> mobs = w.getEntitiesByClass(MobEntity.class, new Box(pos).expand((double)(48 + brush * 4)), mx -> mx instanceof Monster);

                    for (MobEntity m : mobs) {
                        Fx.particles(w, ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                        m.discard();
                    }

                    msg(god, "Изгнано монстров: " + mobs.size(), Formatting.GREEN);
            }
        }
    }

    private static void biome(Power power, ServerWorld w, BlockPos pos, int brush, Random r) {
        int R = 2 + brush;

        for (BlockPos p : Fx.brush(w, pos, R)) {
            BlockPos g = p.down();
            BlockState gs = w.getBlockState(g);
            BlockState ps = w.getBlockState(p);
            boolean water = gs.getFluidState().isIn(FluidTags.WATER);
            if (water || natural(gs)) {
                if (!ps.isAir()
                    && ps.getFluidState().isEmpty()
                    && (
                        ps.isReplaceable()
                            || ps.isIn(BlockTags.FLOWERS)
                            || ps.isIn(BlockTags.SAPLINGS)
                            || ps.isOf(Blocks.DEAD_BUSH)
                            || ps.isOf(Blocks.FIRE)
                            || ps.isOf(Blocks.SOUL_FIRE)
                            || ps.isOf(Blocks.RED_MUSHROOM)
                            || ps.isOf(Blocks.BROWN_MUSHROOM)
                            || ps.isOf(Blocks.AMETHYST_CLUSTER)
                            || ps.isOf(Blocks.WITHER_ROSE)
                            || ps.isOf(Blocks.MOSS_CARPET)
                    )) {
                    set(w, p, Blocks.AIR.getDefaultState());
                }

                float f = r.nextFloat();
                switch (power) {
                    case BIOME_GRASSLAND:
                        if (!water) {
                            set(w, g, Blocks.GRASS_BLOCK.getDefaultState());
                            if (f < 0.25F) {
                                place(w, p, Blocks.SHORT_GRASS.getDefaultState());
                            } else if (f < 0.33F) {
                                place(w, p, FLOWERS[r.nextInt(FLOWERS.length)].getDefaultState());
                            } else if (f < 0.34F) {
                                tree(w, p, Blocks.OAK_SAPLING);
                            }
                        }
                        break;
                    case BIOME_JUNGLE:
                        if (water) {
                            break;
                        }

                        set(w, g, Blocks.GRASS_BLOCK.getDefaultState());
                        if (f < 0.04F) {
                            tree(w, p, Blocks.JUNGLE_SAPLING);
                        } else if (f < 0.07F) {
                            int h = 3 + r.nextInt(5);
                            int i = 0;

                            while (i < h && place(w, p.up(i), Blocks.BAMBOO.getDefaultState())) {
                                i++;
                            }
                        } else if (f < 0.3F) {
                            place(w, p, Blocks.FERN.getDefaultState());
                        } else if (f < 0.4F) {
                            place(w, p, Blocks.SHORT_GRASS.getDefaultState());
                        }
                        break;
                    case BIOME_SAVANNA:
                        if (!water) {
                            set(w, g, Blocks.GRASS_BLOCK.getDefaultState());
                            if (f < 0.025F) {
                                tree(w, p, Blocks.ACACIA_SAPLING);
                            } else if (f < 0.4F) {
                                place(w, p, Blocks.SHORT_GRASS.getDefaultState());
                            }
                        }
                        break;
                    case BIOME_DESERT:
                        if (water) {
                            break;
                        }

                        set(w, g, Blocks.SAND.getDefaultState());
                        if (f < 0.025F) {
                            int h = 1 + r.nextInt(3);
                            int i = 0;

                            while (i < h && place(w, p.up(i), Blocks.CACTUS.getDefaultState())) {
                                i++;
                            }
                        } else if (f < 0.06F) {
                            place(w, p, Blocks.DEAD_BUSH.getDefaultState());
                        }
                        break;
                    case BIOME_SWAMP: {
                        if (water) {
                            if (f < 0.15F) {
                                place(w, p, Blocks.LILY_PAD.getDefaultState());
                            }
                        } else {
                            set(w, g, (f < 0.35F ? Blocks.MUD : Blocks.GRASS_BLOCK).getDefaultState());
                            float d = r.nextFloat();
                            if (d < 0.05F) {
                                place(w, p, Blocks.BLUE_ORCHID.getDefaultState());
                            } else if (d < 0.065F) {
                                tree(w, p, Blocks.OAK_SAPLING);
                            } else if (d < 0.25F) {
                                place(w, p, Blocks.SHORT_GRASS.getDefaultState());
                            }
                        }
                        break;
                    }
                    case BIOME_PERMAFROST:
                        if (water) {
                            if (gs.isOf(Blocks.WATER)) {
                                set(w, g, Blocks.ICE.getDefaultState());
                            }
                        } else {
                            set(w, g, (BlockState)Blocks.GRASS_BLOCK.getDefaultState().with(Properties.SNOWY, true));
                            if (f < 0.03F) {
                                tree(w, p, Blocks.SPRUCE_SAPLING);
                            }

                            place(w, p, Blocks.SNOW.getDefaultState());
                        }
                        break;
                    case BIOME_MUSHROOM:
                        if (!water) {
                            set(w, g, Blocks.MYCELIUM.getDefaultState());
                            if (f < 0.012F) {
                                Block b = r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
                                if (place(w, p, b.getDefaultState()) && b instanceof MushroomPlantBlock mb) {
                                    mb.trySpawningBigMushroom(w, p, b.getDefaultState(), r);
                                }
                            } else if (f < 0.08F) {
                                place(w, p, (r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState());
                            }
                        }
                        break;
                    case BIOME_CANDY:
                        if (!water) {
                            set(w, g, Blocks.GRASS_BLOCK.getDefaultState());
                            if (f < 0.03F) {
                                tree(w, p, Blocks.CHERRY_SAPLING);
                            } else if (f < 0.3F) {
                                place(
                                    w,
                                    p,
                                    (BlockState)((BlockState)Blocks.PINK_PETALS.getDefaultState().with(Properties.FLOWER_AMOUNT, 1 + r.nextInt(4)))
                                        .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.Type.HORIZONTAL.random(r))
                                );
                            }
                        }
                        break;
                    case BIOME_ENCHANTED:
                        if (!water) {
                            set(w, g, Blocks.MOSS_BLOCK.getDefaultState());
                            if (f < 0.02F) {
                                BlockState az = Blocks.AZALEA.getDefaultState();
                                if (place(w, p, az) && az.getBlock() instanceof Fertilizable fb) {
                                    fb.grow(w, r, p, az);
                                }
                            } else if (f < 0.06F) {
                                place(w, p, Blocks.FLOWERING_AZALEA.getDefaultState());
                            } else if (f < 0.16F) {
                                Block[] fl = new Block[]{Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
                                place(w, p, fl[r.nextInt(fl.length)].getDefaultState());
                            } else if (f < 0.3F) {
                                place(w, p, Blocks.MOSS_CARPET.getDefaultState());
                            }
                        }
                        break;
                    case BIOME_CRYSTAL: {
                        if (water) {
                            break;
                        }

                        set(w, g, (f < 0.6F ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK).getDefaultState());
                        float d = r.nextFloat();
                        if (d < 0.07F) {
                            place(w, p, Blocks.AMETHYST_CLUSTER.getDefaultState());
                        } else {
                            if (!(d < 0.085F)) {
                                break;
                            }

                            int h = 2 + r.nextInt(3);
                            int i = 0;

                            while (i < h && place(w, p.up(i), Blocks.AMETHYST_BLOCK.getDefaultState())) {
                                i++;
                            }

                            place(w, p.up(i), Blocks.AMETHYST_CLUSTER.getDefaultState());
                        }
                        break;
                    }
                    case BIOME_INFERNAL:
                        if (!water) {
                            set(w, g, (f < 0.08F ? Blocks.MAGMA_BLOCK : Blocks.NETHERRACK).getDefaultState());
                            if (f >= 0.08F && r.nextFloat() < 0.08F && w.getBlockState(p).isAir()) {
                                set(w, p, AbstractFireBlock.getState(w, p));
                            }
                        }
                        break;
                    case BIOME_CORRUPTED:
                        if (!water) {
                            set(w, g, (f < 0.8F ? Blocks.SOUL_SOIL : Blocks.SOUL_SAND).getDefaultState());
                            float dx = r.nextFloat();
                            if (dx < 0.04F) {
                                place(w, p, Blocks.WITHER_ROSE.getDefaultState());
                            } else if (dx < 0.06F && w.getBlockState(p).isAir()) {
                                set(w, p, AbstractFireBlock.getState(w, p));
                            }
                        }
                }
            }
        }

        if (power.arg != null) {
            cmd(
                w,
                "fillbiome "
                    + (pos.getX() - R - 1)
                    + " "
                    + (pos.getY() - 6)
                    + " "
                    + (pos.getZ() - R - 1)
                    + " "
                    + (pos.getX() + R + 1)
                    + " "
                    + (pos.getY() + 26)
                    + " "
                    + (pos.getZ() + R + 1)
                    + " "
                    + power.arg
            );
        }

        Fx.particles(
            w,
            ParticleTypes.HAPPY_VILLAGER,
            (double)pos.getX() + 0.5,
            (double)pos.getY() + 1.5,
            (double)pos.getZ() + 0.5,
            20 + brush * 4,
            (double)R * 0.6,
            1.0,
            (double)R * 0.6,
            0.0
        );
    }

    private static void build(ServerPlayerEntity god, Power power, ServerWorld w, BlockPos pos) {
        if (power.arg != null) {
            boolean structure = power.arg.startsWith("s:");
            String id = power.arg.substring(2);
            BlockPos at = pos.up();
            String command = (structure ? "place structure " : "place feature ") + id + " " + at.getX() + " " + at.getY() + " " + at.getZ();
            boolean ok = cmd(w, command);
            if (ok) {
                msg(god, "✔ Построено: " + power.title, Formatting.GREEN);
                Fx.particles(w, ParticleTypes.CLOUD, (double)at.getX() + 0.5, (double)(at.getY() + 1), (double)at.getZ() + 0.5, 40, 4.0, 2.0, 4.0, 0.05);
                Fx.sound(w, (double)at.getX(), (double)at.getY(), (double)at.getZ(), SoundEvents.BLOCK_ANVIL_USE, 1.0F, 1.2F);
            } else {
                msg(god, "✘ Здесь не получилось построить: " + power.title, Formatting.RED);
            }
        }
    }

    private static void czar(ServerWorld w, BlockPos pos, Vec3d c, Random r) {
        Tasks.add(new Disasters.Atomic(w, pos));
        Tasks.delay(26, () -> ring(w, c, 14.0, 10, 8.0F));
        Tasks.delay(32, () -> ring(w, c, 26.0, 14, 7.0F));
        Tasks.delay(38, () -> {
            ring(w, c, 38.0, 18, 6.0F);

            for (int i = 0; i < 60; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = 10.0 + r.nextDouble() * 35.0;
                BlockPos s = Fx.surface(w, (int)(c.x + Math.cos(a) * d), (int)(c.z + Math.sin(a) * d));
                Fx.fire(w, s);
            }
        });
    }

    private static void ring(ServerWorld w, Vec3d c, double radius, int n, float power) {
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2) * (double)i / (double)n;
            int x = (int)Math.floor(c.x + Math.cos(a) * radius);
            int z = (int)Math.floor(c.z + Math.sin(a) * radius);
            BlockPos s = Fx.surface(w, x, z);
            w.createExplosion(null, (double)x + 0.5, (double)s.getY(), (double)z + 0.5, power, false, ExplosionSourceType.TNT);
        }
    }

    static Powers2.Epidemic plague(ServerWorld w) {
        Powers2.Epidemic e = PLAGUES.get(w);
        if (e == null || w.getTime() - e.last > 5L || e.sick.isEmpty()) {
            e = new Powers2.Epidemic(w);
            PLAGUES.put(w, e);
            Tasks.add(e);
        }

        return e;
    }

    static final class Creep implements Tasks.Task {
        final ServerWorld w;
        final BlockPos c;
        final int max;
        final boolean sculk;
        int t;

        Creep(ServerWorld w, BlockPos c, int max, boolean sculk) {
            this.w = w;
            this.c = c;
            this.max = max;
            this.sculk = sculk;
        }

        @Override
        public boolean tick() {
            this.t++;
            Random r = this.w.getRandom();
            double rad = Math.min((double)this.max, 1.0 + (double)this.t * 0.05);

            for (int i = 0; i < 14; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = Math.sqrt(r.nextDouble()) * rad;
                BlockPos s = Fx.surface(
                    this.w, (int)Math.floor((double)this.c.getX() + 0.5 + Math.cos(a) * d), (int)Math.floor((double)this.c.getZ() + 0.5 + Math.sin(a) * d)
                );
                BlockPos g = s.down();
                BlockState gs = this.w.getBlockState(g);
                if (!gs.isAir() && gs.getFluidState().isEmpty() && !(gs.getHardness(this.w, g) < 0.0F)) {
                    BlockState ss = this.w.getBlockState(s);
                    if (!ss.isAir() && ss.isReplaceable() && ss.getFluidState().isEmpty()) {
                        this.w.setBlockState(s, Blocks.AIR.getDefaultState(), 3);
                    }

                    BlockState to;
                    if (this.sculk) {
                        to = r.nextFloat() < 0.02F ? Blocks.SCULK_CATALYST.getDefaultState() : Blocks.SCULK.getDefaultState();
                    } else {
                        to = r.nextFloat() < 0.05F ? Blocks.SHROOMLIGHT.getDefaultState() : Blocks.NETHER_WART_BLOCK.getDefaultState();
                    }

                    this.w.setBlockState(g, to, 3);
                }
            }

            Fx.particles(
                this.w,
                this.sculk ? ParticleTypes.SCULK_SOUL : ParticleTypes.CRIMSON_SPORE,
                (double)this.c.getX() + 0.5,
                (double)this.c.getY() + 1.5,
                (double)this.c.getZ() + 0.5,
                6,
                rad * 0.6,
                0.6,
                rad * 0.6,
                0.01
            );
            if (this.t % 70 == 0) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = r.nextDouble() * rad;
                BlockPos s = Fx.surface(this.w, (int)((double)this.c.getX() + Math.cos(a) * d), (int)((double)this.c.getZ() + Math.sin(a) * d));
                (this.sculk ? EntityType.SLIME : EntityType.MAGMA_CUBE).spawn(this.w, s, SpawnReason.TRIGGERED);
            }

            return this.t > 700;
        }
    }

    static final class Epidemic implements Tasks.Task {
        final ServerWorld w;
        final Map<UUID, Integer> sick = new HashMap<>();
        long last;

        Epidemic(ServerWorld w) {
            this.w = w;
            this.last = w.getTime();
        }

        @Override
        public boolean tick() {
            this.last = this.w.getTime();
            if (this.last % 10L != 0L) {
                return this.sick.isEmpty();
            } else {
                Random r = this.w.getRandom();
                List<UUID> add = new ArrayList<>();
                Iterator<Entry<UUID, Integer>> it = this.sick.entrySet().iterator();

                while (it.hasNext()) {
                    Entry<UUID, Integer> en = it.next();
                    Entity e = this.w.getEntity(en.getKey());
                    int left = en.getValue() - 10;
                    if (e instanceof LivingEntity le && le.isAlive() && left > 0) {
                        en.setValue(left);
                        if (!le.hasStatusEffect(StatusEffects.POISON)) {
                            Powers2.effect(le, StatusEffects.POISON, 100, 0);
                        }

                        if (!le.hasStatusEffect(StatusEffects.WEAKNESS)) {
                            Powers2.effect(le, StatusEffects.WEAKNESS, 200, 0);
                        }

                        if (!le.hasStatusEffect(StatusEffects.HUNGER)) {
                            Powers2.effect(le, StatusEffects.HUNGER, 200, 0);
                        }

                        Fx.particles(this.w, ParticleTypes.SNEEZE, le.getX(), le.getY() + (double)le.getHeight() * 0.8, le.getZ(), 2, 0.2, 0.2, 0.2, 0.01);

                        for (LivingEntity o : Fx.living(this.w, le.getPos(), 3.0)) {
                            if (o != le && !this.sick.containsKey(o.getUuid()) && r.nextFloat() < 0.12F) {
                                add.add(o.getUuid());
                            }
                        }
                        continue;
                    }

                    it.remove();
                }

                for (UUID u : add) {
                    this.sick.put(u, 600);
                }

                return this.sick.isEmpty();
            }
        }
    }

    static final class Geyser implements Tasks.Task {
        final ServerWorld w;
        final Vec3d c;
        int t;

        Geyser(ServerWorld w, Vec3d c) {
            this.w = w;
            this.c = c;
        }

        @Override
        public boolean tick() {
            this.t++;
            boolean burst = this.t / 40 % 2 == 1;
            int top = burst ? 14 : 3;

            for (int y = 0; y < top; y += 2) {
                Fx.particles(this.w, ParticleTypes.SPLASH, this.c.x, this.c.y + (double)y, this.c.z, 12, 0.3, 0.8, 0.3, 0.3);
            }

            Fx.particles(this.w, burst ? ParticleTypes.CLOUD : ParticleTypes.BUBBLE_POP, this.c.x, this.c.y + (double)top, this.c.z, 6, 0.6, 0.4, 0.6, 0.05);
            if (burst) {
                for (LivingEntity e : Fx.living(this.w, new Vec3d(this.c.x, this.c.y + 3.0, this.c.z), 4.0)) {
                    Vec3d v = e.getVelocity();
                    e.setVelocity(v.x * 0.5, 1.1, v.z * 0.5);
                    e.velocityModified = true;
                    e.extinguish();
                }

                if (this.t % 10 == 0) {
                    Fx.sound(this.w, this.c.x, this.c.y, this.c.z, SoundEvents.ENTITY_GENERIC_SPLASH, 2.0F, 0.6F);
                }
            }

            return this.t > 240;
        }
    }

    static final class GreyGoo implements Tasks.Task {
        final ServerWorld w;
        final List<BlockPos> front = new ArrayList<>();
        final int max;
        int eaten;
        int t;

        GreyGoo(ServerWorld w, BlockPos start, int max) {
            this.w = w;
            this.max = max;
            this.front.add(start);
        }

        @Override
        public boolean tick() {
            this.t++;
            Random r = this.w.getRandom();

            for (int i = 0; i < 30 && !this.front.isEmpty(); i++) {
                int idx = r.nextInt(this.front.size());
                BlockPos p = this.front.get(idx);
                this.front.set(idx, this.front.get(this.front.size() - 1));
                this.front.remove(this.front.size() - 1);

                for (Direction d : Direction.values()) {
                    if (!(r.nextFloat() < 0.45F)) {
                        BlockPos n = p.offset(d);
                        BlockState st = this.w.getBlockState(n);
                        if (!st.isAir() && !(st.getHardness(this.w, n) < 0.0F) && this.w.isInBuildLimit(n)) {
                            this.w.setBlockState(n, Blocks.AIR.getDefaultState(), 2);
                            this.eaten++;
                            if (this.front.size() < 1500) {
                                this.front.add(n);
                            }

                            if (r.nextFloat() < 0.15F) {
                                Fx.particles(
                                    this.w, ParticleTypes.SMOKE, (double)n.getX() + 0.5, (double)n.getY() + 0.5, (double)n.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01
                                );
                            }
                        }
                    }
                }
            }

            if (this.t % 15 == 0 && !this.front.isEmpty()) {
                BlockPos p = this.front.get(0);
                Fx.sound(this.w, (double)p.getX(), (double)p.getY(), (double)p.getZ(), SoundEvents.BLOCK_SCULK_SPREAD, 1.5F, 0.5F);
            }

            return this.front.isEmpty() || this.eaten >= this.max || this.t > 900;
        }
    }

    static final class Volcano implements Tasks.Task {
        final ServerWorld w;
        final BlockPos base;
        final int h;
        final double rad;
        int t;

        Volcano(ServerWorld w, BlockPos base, int h) {
            this.w = w;
            this.base = base;
            this.h = h;
            this.rad = (double)h * 1.3;
        }

        private void layer(int y) {
            Random r = this.w.getRandom();
            double ry = this.rad * (1.0 - (double)y / (double)this.h) + 0.6;
            int R = (int)Math.ceil(ry);

            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (!((double)d2 > ry * ry)) {
                        BlockPos p = this.base.add(dx, y, dz);
                        BlockState st = this.w.getBlockState(p);
                        if (st.isReplaceable() || st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.FLOWERS)) {
                            BlockState s;
                            if (y >= this.h - 2 && d2 <= 2) {
                                s = Blocks.LAVA.getDefaultState();
                            } else {
                                float f = r.nextFloat();
                                s = f < 0.08F
                                    ? Blocks.MAGMA_BLOCK.getDefaultState()
                                    : (f < 0.3F ? Blocks.BLACKSTONE.getDefaultState() : Blocks.BASALT.getDefaultState());
                            }

                            this.w.setBlockState(p, s, 2);
                        }
                    }
                }
            }

            Fx.particles(
                this.w,
                ParticleTypes.LARGE_SMOKE,
                (double)this.base.getX() + 0.5,
                (double)(this.base.getY() + y),
                (double)this.base.getZ() + 0.5,
                20,
                ry * 0.6,
                0.3,
                ry * 0.6,
                0.02
            );
            Fx.sound(this.w, (double)this.base.getX(), (double)(this.base.getY() + y), (double)this.base.getZ(), SoundEvents.BLOCK_BASALT_PLACE, 2.0F, 0.6F);
        }

        @Override
        public boolean tick() {
            this.t++;
            if (this.t <= this.h * 2) {
                if (this.t % 2 == 0) {
                    this.layer(this.t / 2 - 1);
                }

                return false;
            } else {
                int e = this.t - this.h * 2;
                Random r = this.w.getRandom();
                double x = (double)this.base.getX() + 0.5;
                double y = (double)(this.base.getY() + this.h);
                double z = (double)this.base.getZ() + 0.5;
                Fx.particles(this.w, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 4, 0.8, 0.5, 0.8, 0.04);
                Fx.particles(this.w, ParticleTypes.LAVA, x, y + 0.5, z, 6, 1.0, 0.3, 1.0, 0.0);
                if (e % 4 == 0) {
                    BlockPos at = BlockPos.ofFloored(x, y + 2.0, z);
                    if (this.w.getBlockState(at).isAir()) {
                        FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(this.w, at, Blocks.MAGMA_BLOCK.getDefaultState());
                        fb.setVelocity(r.nextGaussian() * 0.35, 0.7 + (double)r.nextFloat() * 0.6, r.nextGaussian() * 0.35);
                        fb.velocityModified = true;
                        fb.dropItem = false;
                        fb.setHurtEntities(2.0F, 20);
                    }
                }

                if (e % 40 == 0) {
                    Powers2.set(this.w, BlockPos.ofFloored(x, y, z), Blocks.LAVA.getDefaultState());
                    Fx.sound(this.w, x, y, z, (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 4.0F, 0.5F);
                    Fx.particles(this.w, ParticleTypes.EXPLOSION, x, y + 1.0, z, 3, 1.0, 1.0, 1.0, 0.0);
                }

                for (LivingEntity le : Fx.living(this.w, new Vec3d(x, y, z), 2.5)) {
                    le.setOnFireFor(4.0F);
                }

                return e > 320;
            }
        }
    }
}
