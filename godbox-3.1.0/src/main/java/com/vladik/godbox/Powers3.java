package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v1.2: боссы, королевства, законы мира, время и новые катастрофы. */
public final class Powers3 {
    private static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.02f, 0.0f, 0.05f), 4.0f);
    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.3f, 1.0f, 0.3f), 2.0f);
    private static final DustParticleEffect GREY = new DustParticleEffect(new Vector3f(0.55f, 0.6f, 0.65f), 3.0f);

    private Powers3() {}

    private static void msg(ServerPlayerEntity god, String s, Formatting f) {
        god.sendMessage(Text.literal(s).formatted(f), true);
    }

    private static void cmd(ServerPlayerEntity god, String s) {
        MinecraftServer srv = god.getServer();
        if (srv == null) return;
        srv.getCommandManager().executeWithPrefix(god.getCommandSource().withLevel(4).withSilent(), s);
    }

    /** Первая позиция над землёй/водой (или null, если чанк не загружен). */
    private static BlockPos top(ServerWorld w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(x, 0, z));
    }

    public static boolean handles(Power p) {
        int o = p.ordinal();
        return (o >= Power.BLACK_HOLE.ordinal() && o < Power.WORLD_DAY.ordinal()) || o > Power.WORLD_BANISH.ordinal();
    }

    public static boolean recordsUndo(Power p) {
        return p.category != null && p.category != Category.WORLD && p.category != Category.LAWS && !p.name().startsWith("GEN_");
    }

    /** Силы, которым не нужен загруженный чанк под курсором. */
    public static boolean early(ServerPlayerEntity god, Power power, BlockPos pos, int brush, boolean first) {
        Inspect.onPower(god, power, first);
        switch (power) {
            case CIV_STATS -> {
                if (first) Stats.send(god, brush != -1);
                return true;
            }
            case GOD_HAND -> {
                Hand.use(god, pos, brush, first);
                return true;
            }
            case UNDO -> {
                if (first) Undo.undo(god);
                return true;
            }
            case CAMERA_TP -> {
                if (first) god.requestTeleport(pos.getX() + 0.5, god.getY(), pos.getZ() + 0.5);
                return true;
            }
            case TIME_PAUSE -> {
                if (first) {
                    ServerTickManager tm = god.getServer().getTickManager();
                    boolean f = !tm.isFrozen();
                    tm.setFrozen(f);
                    msg(god, f ? "⏸ Время остановлено" : "▶ Время идёт", Formatting.AQUA);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void use(ServerPlayerEntity god, Power power, ServerWorld w, BlockPos pos, Vec3d c, int brush, Random r) {
        switch (power) {
            case BLACK_HOLE -> {
                Tasks.add(new BlackHole(w, c.add(0, 8, 0), brush));
                msg(god, "Чёрная дыра открылась!", Formatting.DARK_PURPLE);
            }
            case TSUNAMI -> {
                Tasks.add(new Tsunami(w, pos, god.getYaw(), brush));
                msg(god, "Цунами идёт вперёд по взгляду камеры", Formatting.BLUE);
            }
            case ICE_AGE -> {
                Tasks.add(new IceAge(god, w, pos, brush));
                msg(god, "Наступает ледниковый период...", Formatting.AQUA);
            }
            case ZOMBIE_APOCALYPSE -> {
                Tasks.add(new Zombies(w, pos, brush));
                msg(god, "Зомби-апокалипсис!", Formatting.DARK_GREEN);
            }
            case ALIEN_INVASION -> {
                Tasks.add(new Aliens(w, pos, brush));
                msg(god, "Вторжение пришельцев!", Formatting.GREEN);
            }
            case BOSS_CRAB, BOSS_ROBOT, BOSS_DRAGON -> Bosses.spawn(god, power.arg, w, pos);
            case BOSS_ORDER -> Bosses.command(god, w, pos);
            case BOSS_REMOVE -> Bosses.remove(god);
            case KINGDOM_RED, KINGDOM_BLUE, KINGDOM_GREEN, KINGDOM_YELLOW -> Kingdoms.found(god, power.arg, w, pos, brush);
            case KINGDOM_ARMY -> Kingdoms.army(god, w, pos, brush);
            case KINGDOM_NEW -> Kingdoms.foundNew(god, w, pos, brush);
            case RACE_HUMAN -> Races.power(god, w, pos, Races.Race.HUMAN);
            case RACE_ELF -> Races.power(god, w, pos, Races.Race.ELF);
            case RACE_ORC -> Races.power(god, w, pos, Races.Race.ORC);
            case RACE_DWARF -> Races.power(god, w, pos, Races.Race.DWARF);
            case SEASON_NEXT -> Seasons.power(god);
            case HISTORY -> History.power(god);
            case INSPECT -> Inspect.power(god, w, pos);
            case EPIDEMIC -> Plague.power(god, w, pos);
            case QUARANTINE -> Plague.quarantine(god, w, pos);
            case ROYAL_MARRIAGE -> Treaties.power(god, w, pos);
            case SCENARIO -> Mortal.cycle(god, w, pos);
            case KINGDOM_CREST -> Banners.reroll(god, w, pos);
            case KINGDOM_WAR -> Kingdoms.setWar(god, true);
            case KINGDOM_PEACE -> Kingdoms.setWar(god, false);
            case KINGDOM_INFO -> Kingdoms.info(god);
            case KINGDOM_DISBAND -> Kingdoms.disband(god, w, pos);
            case KINGDOM_FRIEND -> Diplomacy.sway(god, w, pos, 35);
            case KINGDOM_SPITE -> Diplomacy.sway(god, w, pos, -35);
            case KINGDOM_CARAVAN -> Diplomacy.forceCaravan(god, w, pos);
            case KINGDOM_DIPLO -> Diplomacy.info(god);
            case CIV_CHRONICLE -> Chronicle.show(god);
            case GIFT_FOOD -> Faith.gift(god, w, pos, 2, brush);
            case GIFT_WOOD -> Faith.gift(god, w, pos, 0, brush);
            case GIFT_STONE -> Faith.gift(god, w, pos, 1, brush);
            case GIFT_ORE -> Faith.gift(god, w, pos, 3, brush);
            case GIFT_ALL -> Faith.gift(god, w, pos, -1, brush);
            case AGE_UP -> Ages.force(god, w, pos);
            case FAITH_NEW -> Faith.newFaith(god, w, pos);
            case FAITH_PREACH -> Faith.preach(god, w, pos);
            case FAITH_MIRACLE -> Faith.miracle(god, w, pos);
            case CULTURE_NEXT -> Faith.nextCulture(god, w, pos);
            case CR_NECRO, CR_EVIL_MAGE, CR_WHITE_MAGE, CR_DRUID, CR_PLAGUE_DOCTOR, CR_BANDIT, CR_FIRE_ELEMENTAL, CR_CRYSTAL_GOLEM, CR_FIRE_SKULL, CR_JUMPY_SKULL -> Creatures.spawn(god, power, w, pos, brush, r);
            case CIV_INSPIRE -> Rebels.inspire(god, w, pos);
            case AGE_HOPE, AGE_SUN, AGE_WONDERS, AGE_CHAOS, AGE_DARK, AGE_MOON, AGE_ICE, AGE_ASH, AGE_DESPAIR -> WorldAges.set(god.getServer(), Integer.parseInt(power.arg));
            case TRAIT_STRONG, TRAIT_FAST, TRAIT_TOUGH, TRAIT_REGEN, TRAIT_GIANT, TRAIT_TINY, TRAIT_FIREPROOF, TRAIT_IMMORTAL, TRAIT_CURSED, TRAIT_CLEAR -> Traits.brush(god, w, pos, brush, power.arg);
            case CIV_TOWN, CIV_PORT, CIV_SHIP -> Colonies.power(god, w, pos, power.arg);
            case GOLD_GIFT -> Economy.gift(god, w, pos);
            case VASSALIZE -> Vassals.power(god, w, pos);
            case CIVIL_WAR -> Unrest.power(god, w, pos);
            case HERO_BLESS -> Heroes.power(god, w, pos);
            case HIGHWAY_BUILD -> Highways.power(god, w, pos);
            case BEAUTIFY -> Decor.power(god, w, pos);
            case PRAYER_ANSWER -> Prayers.answer(god, w, pos);
            case POSSESS -> Possess.use(god, w, pos);
            case ACHIEVEMENTS -> Achievements.show(god);
            case PREDATORS -> Ecosystem.power(god, w, pos);
            case HERD_TAME -> Herds.power(god, w, pos);
            case LIVE_VOLCANO -> Tectonics.volcano(god, w, pos);
            case RIFT -> Tectonics.rift(god, w, pos);
            case TECH_GIFT -> Tech.gift(god, w, pos);
            case WONDER_PYRAMID, WONDER_LIBRARY, WONDER_LIGHTHOUSE, WONDER_COLOSSUS, WONDER_GARDENS -> Wonders.power(god, w, pos, power.arg);
            case MAGE_SPAWN, NECRO_SPAWN -> Magic.power(god, w, pos, power.arg);
            case KRAKEN -> Navy.power(god, w, pos);
            case ROAD_BUILD -> Roads.power(god, w, pos);
            case WALLS_BUILD -> Siege.power(god, w, pos);
            case WORLD_EVENT -> Events.force(god, w, pos);
            case CIV_ANNEX -> Conquest.force(god, w, pos);
            case DEMOLISH -> Demolish.one(god, w, pos, brush, false);
            case RUINS -> Demolish.one(god, w, pos, brush, true);
            case DEMOLISH_AREA -> Demolish.area(god, w, pos, brush);
            case GEN_CONTINENT, GEN_ISLANDS, GEN_ARCHIPELAGO, GEN_PANGAEA, GEN_MOUNTAINS, GEN_RANDOM -> WorldGen.start(god, w, pos, brush, power.arg);
            case ORE_MIX, ORE_COAL, ORE_IRON, ORE_COPPER, ORE_GOLD, ORE_REDSTONE, ORE_LAPIS, ORE_DIAMOND, ORE_EMERALD, ORE_DEBRIS -> Ores.paint(god, w, pos, brush, power.arg, r);
            case TIME_NORMAL, TIME_FAST, TIME_FASTER, TIME_SLOW -> {
                ServerTickManager tm = god.getServer().getTickManager();
                tm.setFrozen(false);
                tm.setTickRate(Float.parseFloat(power.arg));
                msg(god, "Скорость времени: " + power.title, Formatting.AQUA);
            }
            default -> {
                if (power.category == Category.LAWS) law(god, power);
            }
        }
    }

    // ---------------- законы ----------------
    /** Состояния всех законов одной маской: бит = порядковый номер закона среди сил категории LAWS. */
    static long lawMask(MinecraftServer s) {
        long mask = 0;
        int i = 0;
        for (Power p : Power.values()) {
            if (p.category != Category.LAWS) continue;
            if (i < 64 && lawState(s, p)) mask |= 1L << i;
            i++;
        }
        return mask;
    }

    private static void law(ServerPlayerEntity god, Power power) {
        MinecraftServer s = god.getServer();
        GameRules g = s.getGameRules();
        String a = power.arg == null ? "" : power.arg;
        boolean on;
        if (a.equals("seasons")) {
            Seasons.on = !Seasons.on;
            msg(god, "Закон «" + power.title + "»: " + (Seasons.on ? "включён" : "выключен"), Seasons.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("mortal")) {
            Mortal.toggle(god);
            return;
        }
        if (a.equals("storage") || a.equals("decor")) {
            boolean v25 = a.equals("storage") ? (Storage.on = !Storage.on) : (Decor.on = !Decor.on);
            msg(god, "Закон «" + power.title + "»: " + (v25 ? "включён" : "выключен"), v25 ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("prayers") || a.equals("eco") || a.equals("herds") || a.equals("tectonics") || a.equals("perf")) {
            boolean v23 = switch (a) {
                case "prayers" -> Prayers.on = !Prayers.on;
                case "eco" -> Ecosystem.on = !Ecosystem.on;
                case "herds" -> Herds.on = !Herds.on;
                case "tectonics" -> Tectonics.on = !Tectonics.on;
                default -> Perf.on = !Perf.on;
            };
            msg(god, "Закон «" + power.title + "»: " + (v23 ? "включён" : "выключен"), v23 ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("economy") || a.equals("vassals") || a.equals("rebels") || a.equals("heroes") || a.equals("highways")) {
            boolean v = switch (a) {
                case "economy" -> Economy.on = !Economy.on;
                case "vassals" -> Vassals.on = !Vassals.on;
                case "rebels" -> Unrest.on = !Unrest.on;
                case "heroes" -> Heroes.on = !Heroes.on;
                default -> Highways.on = !Highways.on;
            };
            msg(god, "Закон «" + power.title + "»: " + (v ? "включён" : "выключен"), v ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("tech") || a.equals("wonders") || a.equals("magic") || a.equals("roads") || a.equals("walls") || a.equals("navy")) {
            boolean v = switch (a) {
                case "tech" -> Tech.on = !Tech.on;
                case "wonders" -> Wonders.on = !Wonders.on;
                case "magic" -> Magic.on = !Magic.on;
                case "roads" -> Roads.on = !Roads.on;
                case "walls" -> Siege.on = !Siege.on;
                default -> Navy.on = !Navy.on;
            };
            msg(god, "Закон «" + power.title + "»: " + (v ? "включён" : "выключен"), v ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("plague")) {
            Plague.on = !Plague.on;
            msg(god, "Закон «" + power.title + "»: " + (Plague.on ? "включён" : "выключен"), Plague.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("expand")) {
            Colonies.on = !Colonies.on;
            msg(god, "Закон «Расселение»: " + (Colonies.on ? "включён" : "выключен"), Colonies.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("sea")) {
            Ships.on = !Ships.on;
            msg(god, "Закон «Мореходство»: " + (Ships.on ? "включён" : "выключен"), Ships.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("ages")) {
            WorldAges.on = !WorldAges.on;
            msg(god, "Закон «Смена эпох мира»: " + (WorldAges.on ? "включён (сейчас " + WorldAges.current() + ")" : "выключен — эпоха замерла: " + WorldAges.current()), WorldAges.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("treaties")) {
            Treaties.on = !Treaties.on;
            msg(god, "Закон «" + power.title + "»: " + (Treaties.on ? "включён" : "выключен"), Treaties.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("conquest")) {
            Conquest.on = !Conquest.on;
            msg(god, "Закон «Захват королевств»: " + (Conquest.on ? "включён" : "выключен — павшие королевства просто исчезают"), Conquest.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("events")) {
            Events.on = !Events.on;
            msg(god, "Закон «Случайные события»: " + (Events.on ? "включён" : "выключен"), Events.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("sleep")) {
            Sleep.on = !Sleep.on;
            msg(god, "Закон «Сон в кроватях»: " + (Sleep.on ? "включён — ночью жители спят в кроватях" : "выключен"), Sleep.on ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("diplomacy")) {
            Diplomacy.auto = !Diplomacy.auto;
            msg(god, "Закон «Дипломатия»: " + (Diplomacy.auto ? "включён — королевства сами заключают союзы, торгуют и воюют" : "выключен"), Diplomacy.auto ? Formatting.GREEN : Formatting.GRAY);
            return;
        }
        if (a.equals("peace")) {
            on = !Kingdoms.eternalPeace;
            Kingdoms.eternalPeace = on;
            if (on && Kingdoms.war) {
                Kingdoms.war = false;
                Kingdoms.calm();
            }
        } else if (a.equals("randomTickSpeed")) {
            GameRules.IntRule rule = g.get(GameRules.RANDOM_TICK_SPEED);
            on = rule.get() <= 3;
            rule.set(on ? 30 : 3, s);
        } else {
            GameRules.Key<GameRules.BooleanRule> key = switch (a) {
                case "doFireTick" -> GameRules.DO_FIRE_TICK;
                case "doMobSpawning" -> GameRules.DO_MOB_SPAWNING;
                case "doDaylightCycle" -> GameRules.DO_DAYLIGHT_CYCLE;
                case "doWeatherCycle" -> GameRules.DO_WEATHER_CYCLE;
                case "mobGriefing" -> GameRules.DO_MOB_GRIEFING;
                case "doMobLoot" -> GameRules.DO_MOB_LOOT;
                case "doInsomnia" -> GameRules.DO_INSOMNIA;
                default -> null;
            };
            if (key == null) return;
            GameRules.BooleanRule rule = g.get(key);
            on = !rule.get();
            rule.set(on, s);
        }
        god.sendMessage(Text.literal("Закон «" + power.title + "»: ").formatted(Formatting.GOLD)
                .append(Text.literal(on ? "ВКЛ" : "ВЫКЛ").formatted(on ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)), true);
    }

    public static boolean lawState(MinecraftServer s, Power power) {
        String a = power.arg == null ? "" : power.arg;
        GameRules g = s.getGameRules();
        return switch (a) {
            case "mortal" -> Mortal.on;
            case "peace" -> Kingdoms.eternalPeace;
            case "diplomacy" -> Diplomacy.auto;
            case "events" -> Events.on;
            case "ages" -> WorldAges.on;
            case "expand" -> Colonies.on;
            case "sea" -> Ships.on;
            case "tech" -> Tech.on;
            case "wonders" -> Wonders.on;
            case "magic" -> Magic.on;
            case "roads" -> Roads.on;
            case "walls" -> Siege.on;
            case "navy" -> Navy.on;
            case "economy" -> Economy.on;
            case "vassals" -> Vassals.on;
            case "rebels" -> Unrest.on;
            case "heroes" -> Heroes.on;
            case "highways" -> Highways.on;
            case "prayers" -> Prayers.on;
            case "eco" -> Ecosystem.on;
            case "herds" -> Herds.on;
            case "tectonics" -> Tectonics.on;
            case "perf" -> Perf.on;
            case "storage" -> Storage.on;
            case "decor" -> Decor.on;
            case "seasons" -> Seasons.on;
            case "plague" -> Plague.on;
            case "conquest" -> Conquest.on;
            case "treaties" -> Treaties.on;
            case "sleep" -> Sleep.on;
            case "randomTickSpeed" -> g.getInt(GameRules.RANDOM_TICK_SPEED) > 3;
            case "doFireTick" -> g.getBoolean(GameRules.DO_FIRE_TICK);
            case "doMobSpawning" -> g.getBoolean(GameRules.DO_MOB_SPAWNING);
            case "doDaylightCycle" -> g.getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
            case "doWeatherCycle" -> g.getBoolean(GameRules.DO_WEATHER_CYCLE);
            case "mobGriefing" -> g.getBoolean(GameRules.DO_MOB_GRIEFING);
            case "doMobLoot" -> g.getBoolean(GameRules.DO_MOB_LOOT);
            case "doInsomnia" -> g.getBoolean(GameRules.DO_INSOMNIA);
            default -> false;
        };
    }

    // ---------------- чёрная дыра ----------------
    private static final class BlackHole implements Tasks.Task {
        final ServerWorld w;
        final Vec3d c;
        final int maxR;
        int t;

        BlackHole(ServerWorld w, Vec3d c, int brush) {
            this.w = w;
            this.c = c;
            this.maxR = 6 + brush * 2;
        }

        @Override
        public boolean tick() {
            t++;
            double rad = Math.min(maxR, 2 + t / 15.0);
            Random r = w.getRandom();
            Fx.particles(w, BLACK, c.x, c.y, c.z, 25, rad * 0.25, rad * 0.25, rad * 0.25, 0);
            Fx.particles(w, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, rad, rad, rad, 0.1);
            Fx.particles(w, ParticleTypes.SQUID_INK, c.x, c.y, c.z, 10, rad * 0.4, rad * 0.4, rad * 0.4, 0.02);
            if (t % 30 == 1) Fx.sound(w, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_AMBIENT, 6f, 0.5f);

            double pull = rad * 2.5 + 6;
            for (Entity e : w.getOtherEntities(null, new Box(c, c).expand(pull), e -> !GodManager.isGodEntity(e) && !e.isSpectator())) {
                if (e instanceof PlayerEntity p && p.isCreative()) continue;
                Vec3d d = c.subtract(e.getPos());
                double len = d.length();
                if (len < 1.8) {
                    if (e instanceof LivingEntity le) le.damage(w.getDamageSources().outOfWorld(), 1000f);
                    else e.discard();
                    continue;
                }
                Vec3d v = d.multiply(Math.min(0.6, 0.08 + 2.0 / len) / len);
                e.setVelocity(e.getVelocity().multiply(0.8).add(v));
                e.velocityModified = true;
                e.fallDistance = 0;
            }

            for (int i = 0; i < 25; i++) {
                double x = (r.nextDouble() * 2 - 1) * rad, y = (r.nextDouble() * 2 - 1) * rad, z = (r.nextDouble() * 2 - 1) * rad;
                if (x * x + y * y + z * z > rad * rad) continue;
                BlockPos p = BlockPos.ofFloored(c.x + x, c.y + y, c.z + z);
                BlockState s = w.getBlockState(p);
                if (s.isAir() || s.getHardness(w, p) < 0) continue;
                if (r.nextInt(3) == 0) {
                    Fx.particles(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
                }
                w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }

            if (t >= 400) {
                w.createExplosion(null, c.x, c.y, c.z, 6f, false, World.ExplosionSourceType.TNT);
                Fx.particles(w, ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0, 0, 0, 0);
                return true;
            }
            return false;
        }
    }

    // ---------------- цунами ----------------
    private static final class Tsunami implements Tasks.Task {
        final ServerWorld w;
        final Vec3d start, dir, side;
        final int width, height, length;
        int step, t;
        final ArrayDeque<List<BlockPos>> rows = new ArrayDeque<>();
        final Set<BlockPos> active = new HashSet<>();

        Tsunami(ServerWorld w, BlockPos pos, float yaw, int brush) {
            this.w = w;
            Vec3d d = Vec3d.fromPolar(0, yaw);
            this.dir = new Vec3d(d.x, 0, d.z).normalize();
            this.side = new Vec3d(-dir.z, 0, dir.x);
            this.width = 16 + brush * 4;
            this.height = 4 + brush / 2;
            this.length = 40 + brush * 6;
            this.start = Vec3d.ofCenter(pos).subtract(dir.multiply(12));
        }

        private void drain(List<BlockPos> row) {
            for (BlockPos p : row) {
                active.remove(p);
                if (w.getBlockState(p).isOf(Blocks.WATER)) w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        @Override
        public boolean tick() {
            if (++t % 2 != 0) return false;
            if (step < length) {
                Vec3d f = start.add(dir.multiply(step));
                int h = step < 4 ? height / 2 + 1 : (step > length - 6 ? Math.max(1, height * (length - step) / 6) : height);
                List<BlockPos> row = new ArrayList<>();
                for (int s = -width / 2; s <= width / 2; s++) {
                    Vec3d q = f.add(side.multiply(s));
                    int x = MathHelper.floor(q.x), z = MathHelper.floor(q.z);
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    BlockPos g = w.getTopPosition(Heightmap.Type.OCEAN_FLOOR, new BlockPos(x, 0, z));
                    while (w.getBlockState(g).getFluidState().isIn(FluidTags.WATER) && !active.contains(g) && g.getY() < w.getTopY()) g = g.up();
                    for (int y = 0; y < h; y++) {
                        BlockPos p = g.up(y);
                        if (active.contains(p)) continue;
                        BlockState st = w.getBlockState(p);
                        if (!st.isAir()) {
                            if (!st.getFluidState().isEmpty()) continue;
                            float hard = st.getHardness(w, p);
                            if (!st.isReplaceable() && (hard < 0 || hard > 0.8f)) continue;
                            w.breakBlock(p, false);
                        }
                        w.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                        row.add(p);
                        active.add(p);
                    }
                    if ((s & 1) == 0) Fx.particles(w, ParticleTypes.SPLASH, q.x, g.getY() + h, q.z, 6, 0.5, 0.3, 0.5, 0.2);
                }
                for (LivingEntity e : Fx.living(w, f.add(0, 2, 0), width / 2.0 + 1)) {
                    if (GodManager.isGodEntity(e) || e.isSpectator()) continue;
                    Vec3d rel = e.getPos().subtract(f);
                    double along = rel.dotProduct(dir);
                    if (along < -2 || along > 4) continue;
                    e.setVelocity(e.getVelocity().multiply(0.5).add(dir.multiply(0.9)).add(0, 0.25, 0));
                    e.velocityModified = true;
                }
                if (step % 8 == 0) Fx.sound(w, f.x, f.y + h, f.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 6f, 0.5f);
                rows.add(row);
                step++;
                if (rows.size() > 3) drain(rows.poll());
                return false;
            }
            if (!rows.isEmpty()) drain(rows.poll());
            return rows.isEmpty();
        }
    }

    // ---------------- ледниковый период ----------------
    private static final class IceAge implements Tasks.Task {
        final ServerPlayerEntity god;
        final ServerWorld w;
        final BlockPos c;
        final int R;
        int x;
        int tile;
        int t;
        final List<int[]> tiles = new ArrayList<>();

        IceAge(ServerPlayerEntity god, ServerWorld w, BlockPos c, int brush) {
            this.god = god;
            this.w = w;
            this.c = c;
            this.R = 40 + brush * 8;
            this.x = -R;
            for (int bx = c.getX() - R; bx <= c.getX() + R; bx += 32) {
                for (int bz = c.getZ() - R; bz <= c.getZ() + R; bz += 32) tiles.add(new int[]{bx, bz});
            }
            w.setWeather(0, 6000, true, false);
        }

        @Override
        public boolean tick() {
            t++;
            Random r = w.getRandom();
            for (int k = 0; k < 3 && x <= R; k++, x++) {
                for (int z = -R; z <= R; z++) {
                    if (x * x + z * z > R * R) continue;
                    BlockPos top = top(w, c.getX() + x, c.getZ() + z);
                    if (top == null) continue;
                    BlockPos below = top.down();
                    BlockState s = w.getBlockState(below);
                    if (s.getFluidState().isIn(FluidTags.WATER)) {
                        if (s.getFluidState().isStill()) w.setBlockState(below, (r.nextInt(5) == 0 ? Blocks.PACKED_ICE : Blocks.ICE).getDefaultState(), Block.NOTIFY_LISTENERS);
                        continue;
                    }
                    if (s.getFluidState().isIn(FluidTags.LAVA)) {
                        w.setBlockState(below, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
                        continue;
                    }
                    BlockState at = w.getBlockState(top);
                    if (at.isOf(Blocks.FIRE) || at.isOf(Blocks.SOUL_FIRE)) {
                        w.setBlockState(top, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        at = Blocks.AIR.getDefaultState();
                    }
                    if (at.isOf(Blocks.SNOW)) continue;
                    if (at.isAir() || (at.isReplaceable() && at.getFluidState().isEmpty())) {
                        BlockState snow = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1 + r.nextInt(3));
                        if (snow.canPlaceAt(w, top)) w.setBlockState(top, snow, Block.NOTIFY_LISTENERS);
                    }
                }
            }
            for (int k = 0; k < 2 && tile < tiles.size(); k++, tile++) {
                int[] a = tiles.get(tile);
                cmd(god, "fillbiome " + a[0] + " " + (c.getY() - 10) + " " + a[1] + " " + (a[0] + 31) + " " + (c.getY() + 20) + " " + (a[1] + 31) + " minecraft:snowy_plains");
            }
            if (t % 20 == 0) {
                for (LivingEntity e : Fx.living(w, Vec3d.ofCenter(c), R)) {
                    if (GodManager.isGodEntity(e) || e.isSpectator()) continue;
                    e.setFrozenTicks(e.getMinFreezeDamageTicks() + 30);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                }
                Fx.particles(w, ParticleTypes.SNOWFLAKE, c.getX(), c.getY() + 10, c.getZ(), 200, R / 2.0, 6, R / 2.0, 0.02);
            }
            return x > R && tile >= tiles.size() && t > 400;
        }
    }

    // ---------------- зомби-апокалипсис ----------------
    private static final class Zombies implements Tasks.Task {
        final ServerWorld w;
        final List<MobEntity> horde = new ArrayList<>();
        int t;

        Zombies(ServerWorld w, BlockPos pos, int brush) {
            this.w = w;
            Random r = w.getRandom();
            int n = 8 + brush * 3;
            for (int i = 0; i < n; i++) {
                EntityType<? extends MobEntity> type = switch (r.nextInt(3)) {
                    case 0 -> EntityType.ZOMBIE;
                    case 1 -> EntityType.HUSK;
                    default -> EntityType.ZOMBIE_VILLAGER;
                };
                BlockPos p = top(w, pos.getX() + r.nextInt(13) - 6, pos.getZ() + r.nextInt(13) - 6);
                if (p == null) continue;
                MobEntity z = type.spawn(w, p, SpawnReason.TRIGGERED);
                if (z != null) mark(z);
            }
            double rad = 12 + brush * 2;
            for (VillagerEntity v : w.getEntitiesByClass(VillagerEntity.class, new Box(pos).expand(rad), LivingEntity::isAlive)) infect(v);
            Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 6f, 0.6f);
        }

        void mark(MobEntity z) {
            z.addCommandTag("godbox_zombie");
            z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            z.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
            z.setPersistent();
            horde.add(z);
        }

        boolean infectable(LivingEntity e) {
            return e instanceof MerchantEntity || e instanceof AnimalEntity || e instanceof IllagerEntity || e instanceof WitchEntity;
        }

        void infect(LivingEntity e) {
            if (horde.size() >= 200 || e.getCommandTags().contains("godbox_zombie") || e.getCommandTags().contains("godbox_boss")) return;
            MobEntity res = null;
            if (e instanceof VillagerEntity v) res = v.convertTo(EntityType.ZOMBIE_VILLAGER, false);
            else if (e instanceof PigEntity p) res = p.convertTo(EntityType.ZOMBIFIED_PIGLIN, false);
            else if (e instanceof MobEntity m && infectable(e)) res = m.convertTo(EntityType.ZOMBIE, false);
            if (res != null) {
                mark(res);
                Fx.particles(w, ParticleTypes.ENTITY_EFFECT == null ? ParticleTypes.SMOKE : ParticleTypes.SMOKE, res.getX(), res.getY() + 1, res.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                Fx.sound(w, res.getX(), res.getY(), res.getZ(), SoundEvents.ENTITY_ZOMBIE_INFECT, 1.5f, 1f);
            }
        }

        @Override
        public boolean tick() {
            t++;
            if (t % 20 != 0) return t >= 2400;
            horde.removeIf(z -> z.isRemoved() || !z.isAlive());
            for (MobEntity z : new ArrayList<>(horde)) {
                for (LivingEntity e : Fx.living(w, z.getPos(), 2.5)) {
                    if (e != z && infectable(e)) infect(e);
                }
                LivingEntity tg = z.getTarget();
                if (tg == null || !tg.isAlive()) {
                    LivingEntity best = null;
                    double bd = 24 * 24;
                    for (LivingEntity e : Fx.living(w, z.getPos(), 24)) {
                        boolean ok = infectable(e) && !e.getCommandTags().contains("godbox_zombie");
                        if (e instanceof PlayerEntity p) ok = !GodManager.isGodEntity(p) && !p.isSpectator() && !p.isCreative();
                        if (!ok) continue;
                        double d = e.squaredDistanceTo(z);
                        if (d < bd) {
                            bd = d;
                            best = e;
                        }
                    }
                    if (best != null) z.setTarget(best);
                }
            }
            return t >= 2400;
        }
    }

    // ---------------- пришельцы ----------------
    private static final class Ufo {
        Vec3d pos;
        Vec3d goal;
        int beam;
        int cooldown;
    }

    private static final class Aliens implements Tasks.Task {
        final ServerWorld w;
        final BlockPos c;
        final List<Ufo> ufos = new ArrayList<>();
        int t;

        Aliens(ServerWorld w, BlockPos c, int brush) {
            this.w = w;
            this.c = c;
            Random r = w.getRandom();
            int n = 2 + brush / 3;
            for (int i = 0; i < n; i++) {
                Ufo u = new Ufo();
                u.pos = new Vec3d(c.getX() + r.nextInt(41) - 20, c.getY() + 26, c.getZ() + r.nextInt(41) - 20);
                u.cooldown = 20 + r.nextInt(40);
                ufos.add(u);
            }
            Fx.sound(w, c.getX(), c.getY() + 20, c.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, 8f, 0.5f);
        }

        double ground(double x, double z, double fallback) {
            BlockPos p = top(w, MathHelper.floor(x), MathHelper.floor(z));
            return p == null ? fallback : p.getY();
        }

        @Override
        public boolean tick() {
            t++;
            Random r = w.getRandom();
            for (Ufo u : ufos) {
                if (u.goal == null || u.goal.squaredDistanceTo(u.pos.x, u.goal.y, u.pos.z) < 4) {
                    u.goal = new Vec3d(c.getX() + r.nextInt(81) - 40, 0, c.getZ() + r.nextInt(81) - 40);
                }
                double gy = ground(u.pos.x, u.pos.z, u.pos.y - 26) + 26;
                Vec3d flat = new Vec3d(u.goal.x - u.pos.x, 0, u.goal.z - u.pos.z);
                double len = flat.length();
                Vec3d mv = u.beam > 0 || len < 0.01 ? Vec3d.ZERO : flat.multiply(Math.min(0.5, len) / len);
                u.pos = new Vec3d(u.pos.x + mv.x, u.pos.y + (gy - u.pos.y) * 0.1, u.pos.z + mv.z);

                if (t % 2 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8 + t * 0.15;
                        Fx.particles(w, ParticleTypes.END_ROD, u.pos.x + Math.cos(a) * 3, u.pos.y, u.pos.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
                    }
                    Fx.particles(w, GREY, u.pos.x, u.pos.y, u.pos.z, 12, 1.5, 0.2, 1.5, 0);
                    Fx.particles(w, ParticleTypes.ELECTRIC_SPARK, u.pos.x, u.pos.y - 0.5, u.pos.z, 3, 1, 0.1, 1, 0.05);
                }

                if (u.beam > 0) {
                    u.beam--;
                    double gr = ground(u.pos.x, u.pos.z, u.pos.y - 26);
                    if (t % 2 == 0) {
                        for (double y = gr; y < u.pos.y; y += 1.0) Fx.particles(w, GREEN, u.pos.x, y, u.pos.z, 2, 0.6, 0.3, 0.6, 0);
                    }
                    Box col = new Box(u.pos.x - 2.5, gr - 1, u.pos.z - 2.5, u.pos.x + 2.5, u.pos.y + 1, u.pos.z + 2.5);
                    for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, col, e -> e.isAlive() && !GodManager.isGodEntity(e) && !e.isSpectator())) {
                        if (e instanceof PlayerEntity p && p.isCreative()) continue;
                        Vec3d toC = new Vec3d(u.pos.x - e.getX(), 0, u.pos.z - e.getZ()).multiply(0.1);
                        e.setVelocity(e.getVelocity().multiply(0.5).add(toC).add(0, 0.3, 0));
                        e.velocityModified = true;
                        e.fallDistance = 0;
                        if (!(e instanceof PlayerEntity) && e.getY() > u.pos.y - 3) {
                            Fx.particles(w, ParticleTypes.PORTAL, e.getX(), e.getY(), e.getZ(), 30, 0.5, 0.5, 0.5, 0.3);
                            Fx.sound(w, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 2f, 1.5f);
                            e.discard();
                        }
                    }
                    continue;
                }

                if (--u.cooldown > 0) continue;
                u.cooldown = 40 + r.nextInt(50);
                int action = r.nextInt(3);
                if (action == 0) {
                    u.beam = 70;
                    Fx.sound(w, u.pos.x, u.pos.y, u.pos.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, 5f, 1.6f);
                } else if (action == 1) {
                    Vec3d to = null;
                    List<LivingEntity> near = Fx.living(w, new Vec3d(u.pos.x, u.pos.y - 26, u.pos.z), 30);
                    near.removeIf(e -> GodManager.isGodEntity(e) || e.isSpectator());
                    if (!near.isEmpty()) {
                        LivingEntity e = near.get(r.nextInt(near.size()));
                        to = e.getPos();
                    } else {
                        double x = u.pos.x + r.nextInt(31) - 15, z = u.pos.z + r.nextInt(31) - 15;
                        to = new Vec3d(x, ground(x, z, u.pos.y - 26), z);
                    }
                    Bosses.beam(w, u.pos, to, GREEN);
                    Fx.sound(w, u.pos.x, u.pos.y, u.pos.z, SoundEvents.ENTITY_GUARDIAN_ATTACK, 5f, 1.8f);
                    w.createExplosion(null, to.x, to.y, to.z, 2.5f, true, World.ExplosionSourceType.TNT);
                } else {
                    BlockPos p = top(w, MathHelper.floor(u.pos.x), MathHelper.floor(u.pos.z));
                    if (p != null) {
                        MobEntity alien = EntityType.ENDERMAN.spawn(w, p, SpawnReason.TRIGGERED);
                        if (alien != null) {
                            alien.setCustomName(Text.literal("Пришелец").formatted(Formatting.GREEN));
                            alien.setGlowing(true);
                            Fx.particles(w, GREEN, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 40, 0.5, 1, 0.5, 0);
                        }
                    }
                }
            }
            if (t % 40 == 0) Fx.sound(w, c.getX(), c.getY() + 26, c.getZ(), SoundEvents.BLOCK_BEACON_AMBIENT, 6f, 2f);
            if (t >= 800) {
                for (Ufo u : ufos) Fx.particles(w, ParticleTypes.FLASH, u.pos.x, u.pos.y, u.pos.z, 1, 0, 0, 0, 0);
                Fx.sound(w, c.getX(), c.getY() + 26, c.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 8f, 0.6f);
                return true;
            }
            return false;
        }
    }
}
