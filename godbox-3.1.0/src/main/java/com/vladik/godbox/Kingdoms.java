package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Королевства как в WorldBox: столица, жители, армия, рост и войны. */
public final class Kingdoms {
    public enum K {
        RED("red", "Красное королевство", Formatting.RED, 0xB02E26, Blocks.RED_WOOL, DyeColor.RED),
        BLUE("blue", "Синее королевство", Formatting.BLUE, 0x3C44AA, Blocks.BLUE_WOOL, DyeColor.BLUE),
        GREEN("green", "Зелёное королевство", Formatting.GREEN, 0x5E7C16, Blocks.GREEN_WOOL, DyeColor.GREEN),
        YELLOW("yellow", "Жёлтое королевство", Formatting.YELLOW, 0xFED83D, Blocks.YELLOW_WOOL, DyeColor.YELLOW),
        // v3.1: ещё восемь королевств (rgb — цвет на карте, должен быть уникальным)
        ORANGE("orange", "Оранжевое королевство", Formatting.GOLD, 0xF9801D, Blocks.ORANGE_WOOL, DyeColor.ORANGE),
        PURPLE("purple", "Фиолетовое королевство", Formatting.DARK_PURPLE, 0x8932B8, Blocks.PURPLE_WOOL, DyeColor.PURPLE),
        CYAN("cyan", "Бирюзовое королевство", Formatting.DARK_AQUA, 0x169C9C, Blocks.CYAN_WOOL, DyeColor.CYAN),
        PINK("pink", "Розовое королевство", Formatting.LIGHT_PURPLE, 0xF38BAA, Blocks.PINK_WOOL, DyeColor.PINK),
        WHITE("white", "Белое королевство", Formatting.WHITE, 0xF9FFFE, Blocks.WHITE_WOOL, DyeColor.WHITE),
        BLACK("black", "Чёрное королевство", Formatting.DARK_GRAY, 0x2B2B33, Blocks.BLACK_WOOL, DyeColor.BLACK),
        LIGHT_BLUE("light_blue", "Голубое королевство", Formatting.AQUA, 0x3AB3DA, Blocks.LIGHT_BLUE_WOOL, DyeColor.LIGHT_BLUE),
        BROWN("brown", "Коричневое королевство", Formatting.DARK_RED, 0x835432, Blocks.BROWN_WOOL, DyeColor.BROWN);

        public final String id;
        public final String title;
        public final Formatting color;
        public final int rgb;
        public final Block wool;
        public final DyeColor dye;
        ServerWorld world;
        BlockPos capital;
        boolean alive;
        int grow;
        int fallTimer;
        final List<MobEntity> members = new ArrayList<>();

        K(String id, String title, Formatting color, int rgb, Block wool, DyeColor dye) {
            this.dye = dye;
            this.id = id;
            this.title = title;
            this.color = color;
            this.rgb = rgb;
            this.wool = wool;
        }

        public String team() {
            return "godbox_" + id;
        }

        /** Стоячее знамя, предмет-знамя и кровать цвета королевства. */
        public Block banner() {
            return Registries.BLOCK.get(Identifier.ofVanilla(dye.getName() + "_banner"));
        }

        public Item bannerItem() {
            return Registries.ITEM.get(Identifier.ofVanilla(dye.getName() + "_banner"));
        }

        public Block bed() {
            return Registries.BLOCK.get(Identifier.ofVanilla(dye.getName() + "_bed"));
        }
    }

    public static boolean war;
    public static boolean eternalPeace;
    private static int timer;
    private static final Set<MobEntity> PREPARED = Collections.newSetFromMap(new WeakHashMap<>());

    private Kingdoms() {}

    public static K byId(String id) {
        for (K k : K.values()) if (k.id.equals(id)) return k;
        return null;
    }

    public static K of(Entity e) {
        if (e == null) return null;
        Team t = e.getScoreboardTeam();
        if (t == null || !t.getName().startsWith("godbox_")) return null;
        return byId(t.getName().substring(7));
    }

    private static Team team(MinecraftServer s, K k) {
        ServerScoreboard sb = s.getScoreboard();
        Team t = sb.getTeam(k.team());
        if (t == null) t = sb.addTeam(k.team());
        t.setColor(k.color);
        t.setFriendlyFireAllowed(false);
        t.setDisplayName(Text.literal(k.title));
        return t;
    }

    private static ItemStack dyed(Item item, int rgb) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb, false));
        return st;
    }

    static void join(K k, MobEntity m) {
        MinecraftServer s = m.getServer();
        if (s == null) return;
        s.getScoreboard().addScoreHolderToTeam(m.getNameForScoreboard(), team(s, k));
        m.setGlowing(true);
        m.setPersistent();
        if (!(m instanceof VillagerEntity)) {
            m.targetSelector.clear(g -> true);
            PREPARED.add(m);
            m.equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, k.rgb));
            m.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, k.rgb));
            m.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
            m.setEquipmentDropChance(EquipmentSlot.CHEST, 0f);
            if (k.world != null && m.getRandom().nextInt(6) == 0) m.equipStack(EquipmentSlot.HEAD, Banners.stack(k.world, k));
            Tech.equip(k, m);
            EntityAttributeInstance a = m.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (a != null) a.setBaseValue(96);
        }
        Races.apply(k, m);
    }

    static MobEntity spawn(K k, EntityType<? extends MobEntity> type, BlockPos at) {
        MobEntity m = type.spawn(k.world, at, SpawnReason.TRIGGERED);
        if (m != null) {
            join(k, m);
            k.members.add(m);
            Traits.born(m);
        }
        return m;
    }

    static EntityType<? extends MobEntity> soldier(Random r) {
        return r.nextInt(4) == 0 ? EntityType.PILLAGER : EntityType.VINDICATOR;
    }

    private static BlockPos around(ServerWorld w, BlockPos c, int r) {
        Random rnd = w.getRandom();
        return Fx.surface(w, c.getX() + rnd.nextInt(r * 2 + 1) - r, c.getZ() + rnd.nextInt(r * 2 + 1) - r);
    }

    private static void msg(ServerPlayerEntity god, String s, Formatting f) {
        god.sendMessage(Text.literal(s).formatted(f), true);
    }

    private static void broadcast(MinecraftServer s, Text t) {
        Chronicle.log(t.getString());
        s.getPlayerManager().broadcast(t, false);
    }

    private static boolean loaded(K k) {
        return k.world != null && k.capital != null && k.world.shouldTickEntity(k.capital);
    }

    private static K nearest(ServerWorld w, BlockPos pos) {
        K best = null;
        double bd = Double.MAX_VALUE;
        for (K k : K.values()) {
            if (!k.alive || k.world != w) continue;
            double d = k.capital.getSquaredDistance(pos);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    /** Первое свободное (не живое) королевство или null. */
    public static K free() {
        for (K k : K.values()) if (!k.alive) return k;
        return null;
    }

    /** Сила «Новое королевство»: следующее свободное королевство со случайной расой. */
    public static void foundNew(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush) {
        K near = nearest(w, pos);
        if (near != null && near.capital.getSquaredDistance(pos) < 32 * 32) {
            msg(god, "Слишком близко к столице: " + near.title, Formatting.YELLOW);
            return;
        }
        K k = free();
        if (k == null) {
            msg(god, "Все королевства (" + K.values().length + ") уже основаны — распусти одно из них", Formatting.RED);
            return;
        }
        Races.set(k, Races.pick(w, pos));
        found(god, k.id, w, pos, brush);
        msg(god, k.title + " основано, раса: " + Races.of(k).title.toLowerCase(), k.color);
    }

    private static int aliveCount() {
        int n = 0;
        for (K k : K.values()) if (k.alive) n++;
        return n;
    }

    // ---------------- силы ----------------
    public static void found(ServerPlayerEntity god, String id, ServerWorld w, BlockPos pos, int brush) {
        K k = byId(id);
        if (k == null) return;
        Random r = w.getRandom();
        BlockPos base = TownBuilder.landOr(w, Fx.surface(w, pos.getX(), pos.getZ()));
        if (k.alive && k.world == w) {
            for (int i = 0; i < 2 + brush / 2; i++) spawn(k, EntityType.VILLAGER, around(w, base, 3));
            for (int i = 0; i < 2 + brush / 2; i++) spawn(k, soldier(r), around(w, base, 3));
            msg(god, k.title + ": прибыли поселенцы", k.color);
            return;
        }
        if (k.alive) disbandKingdom(k);
        k.world = w;
        k.capital = base;
        k.alive = true;
        k.grow = 0;
        k.fallTimer = 0;
        k.members.clear();
        team(w.getServer(), k);
        fort(w, base, k);
        Civ.found(k);
        for (int i = 0; i < 5; i++) spawn(k, EntityType.VILLAGER, around(w, base, 2));
        for (int i = 0; i < 6; i++) spawn(k, soldier(r), around(w, base, 3));
        // голем-защитник при основании — только если големы жителей разрешены в настройках
        if (Config.b("villagers.golems", false)) spawn(k, EntityType.IRON_GOLEM, around(w, base, 2));
        Fx.sound(w, base.getX(), base.getY(), base.getZ(), net.minecraft.sound.SoundEvents.EVENT_RAID_HORN.value(), 4f, 1.2f);
        broadcast(w.getServer(), Text.literal("🏰 Основано: " + k.title + " · " + Races.of(k).title.toLowerCase()).formatted(k.color, Formatting.BOLD));
    }

    private static void fort(ServerWorld w, BlockPos base, K k) {
        BlockState brick = Races.style(k, Blocks.STONE_BRICKS.getDefaultState());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                if (!edge) continue;
                if (dz == 4 && Math.abs(dx) <= 1) continue; // ворота
                BlockPos g = Fx.surface(w, base.getX() + dx, base.getZ() + dz);
                int h = ((dx + dz) & 1) == 0 ? 3 : 2;
                for (int y = 0; y < h; y++) w.setBlockState(g.up(y), brick);
            }
        }
        for (int y = 0; y < 5; y++) w.setBlockState(base.up(y), Blocks.OAK_FENCE.getDefaultState());
        w.setBlockState(base.up(5), k.wool.getDefaultState());
        w.setBlockState(base.up(4).east(), k.wool.getDefaultState());
        w.setBlockState(base.up(3).east(), k.wool.getDefaultState());
        w.setBlockState(base.up(4).east(2), k.wool.getDefaultState());
        w.setBlockState(base.up(3).east(2), k.wool.getDefaultState());
    }

    public static void army(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush) {
        K k = nearest(w, pos);
        if (k == null) {
            msg(god, "Сначала оснуй королевство", Formatting.RED);
            return;
        }
        BlockPos at = Fx.surface(w, pos.getX(), pos.getZ());
        int n = 2 + brush;
        for (int i = 0; i < n; i++) spawn(k, soldier(w.getRandom()), around(w, at, 3));
        msg(god, k.title + ": +" + n + " воинов", k.color);
    }

    public static void setWar(ServerPlayerEntity god, boolean on) {
        MinecraftServer s = god.getServer();
        if (on) {
            if (eternalPeace) {
                msg(god, "Действует закон «Вечный мир»", Formatting.YELLOW);
                return;
            }
            if (aliveCount() < 2) {
                msg(god, "Для войны нужно хотя бы 2 королевства", Formatting.RED);
                return;
            }
            war = true;
            Diplomacy.setAll(true);
            broadcast(s, Text.literal("⚔ Началась война королевств!").formatted(Formatting.RED, Formatting.BOLD));
        } else {
            war = false;
            // общий сброс обнуляет мнения — договорные (только что выставленные условиями мира) возвращаем
            boolean[][] treaty = Treaties.godPeaceTerms(s);
            int n = K.values().length;
            int[][] op = new int[n][n];
            for (int i = 0; i < n; i++) op[i] = Diplomacy.OP[i].clone();
            Diplomacy.setAll(false);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (!treaty[i][j]) continue;
                    Diplomacy.OP[i][j] = op[i][j];
                    Diplomacy.OP[j][i] = op[j][i];
                }
            }
            calm();
            broadcast(s, Text.literal("🕊 Королевства заключили мир").formatted(Formatting.WHITE));
        }
    }

    /** Сбросить цели у всех воинов (после мира). */
    public static void calm() {
        for (K k : K.values()) {
            for (MobEntity m : k.members) {
                LivingEntity t = m.getTarget();
                if (t != null && of(t) != null) m.setTarget(null);
            }
        }
    }

    public static void info(ServerPlayerEntity god) {
        if (aliveCount() == 0) {
            msg(god, "Королевств пока нет", Formatting.GRAY);
            return;
        }
        MutableText t = Text.literal(war ? "⚔ Война! " : "🕊 Мир. ").formatted(war ? Formatting.RED : Formatting.WHITE);
        for (K k : K.values()) {
            if (!k.alive) continue;
            int v = 0;
            for (MobEntity m : k.members) if (m instanceof VillagerEntity) v++;
            t.append(Text.literal(k.title + " (" + Races.of(k).title.toLowerCase() + "): " + v + " жит., " + (k.members.size() - v) + " воинов  ").formatted(k.color));
        }
        for (K kk : K.values()) if (kk.alive) t.append(Text.literal("\n" + kk.title + ": " + Civ.summary(kk)).formatted(kk.color));
        god.sendMessage(t, false);
    }

    public static void disband(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        K k = nearest(w, pos);
        if (k == null) {
            msg(god, "Рядом нет королевств", Formatting.GRAY);
            return;
        }
        disbandKingdom(k);
        msg(god, k.title + " распущено", k.color);
    }

    static void disbandKingdom(K k) {
        Civ.remove(k);
        for (MobEntity m : k.members) m.setGlowing(false);
        if (k.world != null) {
            ServerScoreboard sb = k.world.getServer().getScoreboard();
            Team t = sb.getTeam(k.team());
            if (t != null) sb.removeTeam(t);
        }
        k.members.clear();
        k.alive = false;
        Diplomacy.forget(k);
        Treaties.forget(k);
        Conquest.forget(k);
        Races.forget(k);
        if (aliveCount() < 2) war = false;
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer server) {
        if (++timer % 20 != 0) return;
        if (aliveCount() == 0) return;
        for (K k : K.values()) k.members.clear();
        Set<ServerWorld> worlds = new HashSet<>();
        for (K k : K.values()) if (k.alive && k.world != null) worlds.add(k.world);
        for (ServerWorld w : worlds) {
            for (Entity e : w.iterateEntities()) {
                if (!(e instanceof MobEntity m) || !m.isAlive()) continue;
                K k = of(m);
                if (k == null || !k.alive || k.world != w) continue;
                k.members.add(m);
                if (!(m instanceof VillagerEntity) && PREPARED.add(m)) m.targetSelector.clear(g -> true);
            }
        }

        // рост
        for (K k : K.values()) {
            if (!k.alive) continue;
            if (!Civ.economy && ++k.grow >= 25) {
                k.grow = 0;
                if (k.members.size() < 150 && loaded(k)) {
                    Random r = k.world.getRandom();
                    if (r.nextBoolean()) spawn(k, EntityType.VILLAGER, around(k.world, k.capital, 3));
                    else spawn(k, soldier(r), around(k.world, k.capital, 3));
                }
            }
        }

        // ИИ воинов
        for (K k : K.values()) {
            if (!k.alive) continue;
            for (MobEntity m : new ArrayList<>(k.members)) {
                if (m instanceof VillagerEntity) continue;
                LivingEntity t = m.getTarget();
                if (t != null && (!t.isAlive() || of(t) == k || GodManager.isGodEntity(t) || (of(t) != null && !Diplomacy.hostile(k, of(t))))) {
                    m.setTarget(null);
                    t = null;
                }
                LivingEntity att = m.getAttacker();
                if (att != null && att != t && att.isAlive() && of(att) != k && !GodManager.isGodEntity(att) && (of(att) == null || !Diplomacy.allied(k, of(att)))) {
                    m.setTarget(att);
                    t = att;
                }
                if (t == null) {
                    t = Diplomacy.atWar(k) ? nearestEnemy(k, m, 32) : nearestMonster(m, 16);
                    if (t == null && Diplomacy.atWar(k)) t = nearestMonster(m, 16);
                    if (t != null) m.setTarget(t);
                }
                if (t == null) {
                    BlockPos goal = null;
                    if (Diplomacy.atWar(k)) goal = Conquest.target(k, m);
                    if (goal == null && Diplomacy.atWar(k)) goal = nearestEnemyCapital(k, m);
                    else if (k.capital != null && m.getBlockPos().getSquaredDistance(k.capital) > 48 * 48) goal = k.capital;
                    if (goal != null && m.getNavigation().isIdle()) {
                        m.getNavigation().startMovingTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                    }
                }
            }
        }

        // падение королевств
        if (war || Diplomacy.anyWar()) {
            for (K k : K.values()) {
                if (!k.alive) continue;
                if (k.members.isEmpty() && loaded(k)) {
                    if (++k.fallTimer >= 5) {
                        broadcast(server, Text.literal("☠ " + k.title + " пало!").formatted(k.color, Formatting.BOLD));
                        if (!Conquest.fallTo(server, k)) disbandKingdom(k);
                    }
                } else {
                    k.fallTimer = 0;
                }
            }
            if (aliveCount() <= 1) {
                war = false;
                for (K k : K.values()) {
                    if (k.alive) broadcast(server, Text.literal("👑 " + k.title + " победило в войне!").formatted(k.color, Formatting.BOLD));
                }
            }
        }
    }

    private static LivingEntity nearestEnemy(K k, MobEntity m, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (K o : K.values()) {
            if (o == k || !o.alive || o.world != k.world || !Diplomacy.hostile(k, o)) continue;
            for (MobEntity e : o.members) {
                double d = e.squaredDistanceTo(m);
                if (d < bd && e.isAlive()) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static LivingEntity nearestMonster(MobEntity m, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (MobEntity e : m.getWorld().getEntitiesByClass(MobEntity.class, m.getBoundingBox().expand(r),
                e -> e instanceof Monster && e.isAlive() && of(e) == null)) {
            double d = e.squaredDistanceTo(m);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static BlockPos nearestEnemyCapital(K k, MobEntity m) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (K o : K.values()) {
            if (o == k || !o.alive || o.world != k.world || !Diplomacy.hostile(k, o)) continue;
            double d = o.capital.getSquaredDistance(m.getBlockPos());
            if (d < bd) {
                bd = d;
                best = o.capital;
            }
        }
        return best;
    }

    public static void clear() {
        for (K k : K.values()) {
            k.alive = false;
            k.world = null;
            k.capital = null;
            k.members.clear();
        }
        war = false;
        eternalPeace = false;
        timer = 0;
        PREPARED.clear();
        Races.clear();
        Civ.clear();
    }
}
