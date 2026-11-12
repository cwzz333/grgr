package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** v3.1: расы королевств — люди, эльфы, орки и гномы. Раса меняет облик, имена, хозяйство, армию и архитектуру. */
public final class Races {
    public enum Race {
        HUMAN("Люди", "торговцы: больше золота"),
        ELF("Эльфы", "долгожители: лес, еда и вера"),
        ORC("Орки", "воины: большая армия, слабая наука"),
        DWARF("Гномы", "рудокопы: камень и руда, крепкие, растут медленно");

        public final String title;
        public final String desc;

        Race(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    private static final Map<Kingdoms.K, Race> RACE = new EnumMap<>(Kingdoms.K.class);
    private static final Identifier MOD = Identifier.of("godbox", "race");
    private static final Map<Race, Map<Block, Block>> STYLE = new EnumMap<>(Race.class);

    static final String[] ELF_NAMES = {
        "Аэлин", "Таурион", "Лориэн", "Элендил", "Галадор", "Миривэн", "Финрод", "Келеборн", "Амариэль", "Иторил",
        "Сильвана", "Нимлот", "Эларион", "Лютиэн", "Аранэль", "Тинувиэль", "Халдир", "Фаэлар", "Исильмэ", "Риннон"
    };
    static final String[] ORC_NAMES = {
        "Грым", "Углук", "Шаграт", "Борзаг", "Гхаш", "Дурбаг", "Мургаш", "Зогар", "Кражд", "Ургал",
        "Грыжда", "Шарга", "Болдог", "Назгра", "Ражак", "Гортук", "Харгаш", "Лугдуш", "Брогда", "Тракк"
    };
    static final String[] DWARF_NAMES = {
        "Торин", "Балин", "Двалин", "Гимли", "Дурин", "Глоин", "Бофур", "Нарви", "Фундин", "Траин",
        "Борин", "Хильда", "Дагна", "Брунгильда", "Фрерин", "Оин", "Гроин", "Торбьорг", "Кили", "Сигрун"
    };

    static {
        SaveExt.register("race", Races::save, Races::load, RACE::clear);
        Inspect.EXTRA.add((e, out) -> {
            Kingdoms.K k = Kingdoms.of(e);
            if (k != null) out.add("Раса: " + of(k).title);
        });
    }

    private Races() {}

    /** Вызывается из GodBox.onInitialize(), чтобы зарегистрировать сохранение. */
    public static void init() {}

    private static List<String> save() {
        List<String> o = new ArrayList<>();
        for (Map.Entry<Kingdoms.K, Race> e : RACE.entrySet()) o.add(e.getKey().name() + "|" + e.getValue().name());
        return o;
    }

    private static void load(String[] a) {
        try {
            RACE.put(Kingdoms.K.valueOf(a[0]), Race.valueOf(a[1]));
        } catch (Exception ignored) {
        }
    }

    /** Раса королевства; у старых сохранений и у null — люди. */
    public static Race of(Kingdoms.K k) {
        if (k == null) return Race.HUMAN;
        return RACE.getOrDefault(k, Race.HUMAN);
    }

    static void set(Kingdoms.K k, Race r) {
        if (k == null || r == null) return;
        if (r == Race.HUMAN) RACE.remove(k);
        else RACE.put(k, r);
    }

    static void forget(Kingdoms.K k) {
        RACE.remove(k);
    }

    public static void clear() {
        RACE.clear();
    }

    /** Строка для статистики: «Эльфы — долгожители: лес, еда и вера». */
    public static String describe(Kingdoms.K k) {
        Race r = of(k);
        return r.title + " — " + r.desc;
    }

    // ---------------- выбор расы ----------------
    /** Случайная раса; биом в точке основания повышает шансы «родной» расы. */
    static Race pick(ServerWorld w, BlockPos pos) {
        int bonus = Math.max(0, Config.i("races.biomeWeight", 6));
        int[] wt = {3, 2, 2, 2}; // люди, эльфы, орки, гномы
        try {
            RegistryEntry<Biome> b = w.getBiome(pos);
            if (b.isIn(BiomeTags.IS_FOREST) || b.isIn(BiomeTags.IS_JUNGLE) || b.isIn(BiomeTags.IS_TAIGA)) wt[1] += bonus;
            if (b.isIn(BiomeTags.IS_BADLANDS) || b.isIn(BiomeTags.IS_SAVANNA) || b.matchesKey(BiomeKeys.SWAMP)
                || b.matchesKey(BiomeKeys.MANGROVE_SWAMP) || b.matchesKey(BiomeKeys.DESERT)) wt[2] += bonus;
            if (b.isIn(BiomeTags.IS_MOUNTAIN) || b.isIn(BiomeTags.IS_HILL) || b.matchesKey(BiomeKeys.DRIPSTONE_CAVES)
                || b.matchesKey(BiomeKeys.LUSH_CAVES) || b.matchesKey(BiomeKeys.DEEP_DARK)) wt[3] += bonus;
        } catch (Exception ignored) {
        }
        int roll = w.getRandom().nextInt(wt[0] + wt[1] + wt[2] + wt[3]);
        for (int i = 0; i < wt.length; i++) {
            roll -= wt[i];
            if (roll < 0) return Race.values()[i];
        }
        return Race.HUMAN;
    }

    // ---------------- облик и характеристики существа ----------------
    private static void mod(MobEntity m, RegistryEntry<EntityAttribute> attr, double v, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance a = m.getAttributeInstance(attr);
        if (a == null) return;
        a.removeModifier(MOD);
        if (v != 0) a.addPersistentModifier(new EntityAttributeModifier(MOD, v, op));
    }

    /** Применить расу королевства к жителю или воину. Повторный вызов ничего не накапливает. */
    static void apply(Kingdoms.K k, MobEntity m) {
        if (k == null || m == null || !(m instanceof VillagerEntity || m instanceof Monster)) return;
        try {
            Race r = of(k);
            double scale = 1, hp = 0, dmg = 0;
            switch (r) {
                case ELF -> scale = Config.d("races.elf.scale", 1.05);
                case ORC -> {
                    scale = Config.d("races.orc.scale", 1.15);
                    hp = Config.d("races.orc.health", 0.10);
                    dmg = Config.d("races.orc.damage", 0.25);
                }
                case DWARF -> {
                    scale = Config.d("races.dwarf.scale", 0.8);
                    hp = Config.d("races.dwarf.health", 0.30);
                }
                default -> { }
            }
            boolean full = m.getHealth() >= m.getMaxHealth();
            mod(m, EntityAttributes.GENERIC_SCALE, scale - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            mod(m, EntityAttributes.GENERIC_MAX_HEALTH, hp, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            mod(m, EntityAttributes.GENERIC_ATTACK_DAMAGE, dmg, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (full || m.getHealth() > m.getMaxHealth()) m.setHealth(m.getMaxHealth());
            if (m instanceof VillagerEntity v) {
                VillagerType t = switch (r) {
                    case ELF -> VillagerType.JUNGLE;
                    case ORC -> VillagerType.SWAMP;
                    case DWARF -> VillagerType.SNOW;
                    default -> VillagerType.forBiome(v.getWorld().getBiome(v.getBlockPos()));
                };
                if (v.getVillagerData().getType() != t) v.setVillagerData(v.getVillagerData().withType(t));
            }
        } catch (Exception e) {
            GodBox.LOG.warn("GodBox: не удалось применить расу", e);
        }
    }

    static void applyAll(Kingdoms.K k) {
        for (MobEntity m : new ArrayList<>(k.members)) if (m.isAlive()) apply(k, m);
    }

    // ---------------- множители хозяйства ----------------
    private static int pct(Kingdoms.K k, int n, int pct) {
        if (n <= 0 || pct == 100) return n;
        int x = n * Math.max(0, pct);
        int rest = x % 100;
        return x / 100 + (rest > 0 && k.world != null && k.world.getRandom().nextInt(100) < rest ? 1 : 0);
    }

    /** Добыча, принесённая на склад: 0 дерево, 1 камень, 2 еда, 3 руда. */
    static int yield(Kingdoms.K k, int type, int n) {
        return switch (of(k)) {
            case ELF -> type == 0 ? pct(k, n, Config.i("races.elf.wood", 125)) : type == 2 ? pct(k, n, Config.i("races.elf.food", 125)) : n;
            case DWARF -> type == 1 ? pct(k, n, Config.i("races.dwarf.stone", 130)) : type == 3 ? pct(k, n, Config.i("races.dwarf.ore", 140)) : n;
            default -> n;
        };
    }

    /** Очки науки за цикл. */
    static int research(Kingdoms.K k, int gain) {
        return of(k) == Race.ORC ? Math.max(1, gain * Config.i("races.orc.research", 70) / 100) : gain;
    }

    /** Предел армии, который содержат казармы. */
    static int army(Kingdoms.K k, int cap) {
        return of(k) == Race.ORC ? cap * Config.i("races.orc.army", 140) / 100 : cap;
    }

    /** Может ли сейчас родиться ребёнок (гномы растут медленнее). */
    static boolean breeds(Kingdoms.K k, Random r) {
        return of(k) != Race.DWARF || r.nextInt(100) < Config.i("races.dwarf.growth", 65);
    }

    /** На сколько лет позже наступает старость. */
    public static int ageBonus(Kingdoms.K k) {
        return switch (of(k)) {
            case ELF -> Config.i("races.elf.years", 25);
            case DWARF -> Config.i("races.dwarf.years", 10);
            case ORC -> Config.i("races.orc.years", -5);
            default -> 0;
        };
    }

    /** Доход казны. */
    static int gold(Kingdoms.K k, int income) {
        return of(k) == Race.HUMAN ? income * Config.i("races.human.gold", 115) / 100 : income;
    }

    /** Прибавка веры за цикл. */
    static int faith(Kingdoms.K k) {
        return of(k) == Race.ELF ? Config.i("races.elf.faith", 2) : 0;
    }

    /** Постоянная поправка к отношениям двух королевств. */
    static int opinion(Kingdoms.K a, Kingdoms.K b) {
        Race x = of(a), y = of(b);
        if (x == y) return Config.i("races.opinion.same", 1);
        if ((x == Race.ELF && y == Race.ORC) || (x == Race.ORC && y == Race.ELF)) return Config.i("races.opinion.elfOrc", -3);
        if ((x == Race.ELF && y == Race.DWARF) || (x == Race.DWARF && y == Race.ELF)) return Config.i("races.opinion.elfDwarf", -1);
        return 0;
    }

    /** Имя для нового жителя: у людей — общий список. */
    static String name(Kingdoms.K k, Random r, String[] human) {
        String[] a = switch (of(k)) {
            case ELF -> ELF_NAMES;
            case ORC -> ORC_NAMES;
            case DWARF -> DWARF_NAMES;
            default -> human;
        };
        return a[r.nextInt(a.length)];
    }

    // ---------------- архитектура ----------------
    private static void swap(Map<Block, Block> out, String path, String from, String to) {
        if (!path.startsWith(from)) return;
        Identifier id = Identifier.ofVanilla(to + path.substring(from.length()));
        if (!Registries.BLOCK.containsId(id)) return;
        Block b = Registries.BLOCK.get(id);
        if (!b.getDefaultState().hasBlockEntity()) out.put(Registries.BLOCK.get(Identifier.ofVanilla(path)), b);
    }

    private static Map<Block, Block> palette(Race r) {
        Map<Block, Block> out = new HashMap<>();
        String wood = r == Race.ELF ? "jungle_" : r == Race.ORC ? "dark_oak_" : "spruce_";
        String cobble = r == Race.ELF ? "mossy_cobblestone" : r == Race.ORC ? "blackstone" : "cobbled_deepslate";
        String brick = r == Race.ELF ? "mossy_stone_brick" : r == Race.ORC ? "polished_blackstone_brick" : "deepslate_brick";
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!id.getNamespace().equals("minecraft")) continue;
            String p = id.getPath();
            if (Registries.BLOCK.get(id).getDefaultState().hasBlockEntity()) continue;
            for (String src : new String[]{"oak_", "birch_", "spruce_"}) {
                if (wood.equals(src)) continue;
                swap(out, p, src, wood);
                swap(out, p, "stripped_" + src, "stripped_" + wood);
            }
            swap(out, p, "cobblestone", cobble);
            swap(out, p, "stone_brick", brick);
        }
        return out;
    }

    /** Последний шаг палитры здания: замена дерева и камня на материалы расы. */
    static BlockState style(Kingdoms.K k, BlockState s) {
        Race r = of(k);
        if (r == Race.HUMAN || s == null || !Config.b("races.architecture", true)) return s;
        Block to = STYLE.computeIfAbsent(r, Races::palette).get(s.getBlock());
        return to == null ? s : Eras.copy(s, to);
    }

    // ---------------- сила бога ----------------
    /** Силы «Раса: …»: ближайшее королевство меняет расу, все жители преображаются. */
    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos, Race r) {
        Kingdoms.K k = Diplomacy.nearest(w, pos);
        if (k == null || !k.alive) {
            god.sendMessage(Text.literal("Рядом нет королевств").formatted(Formatting.GRAY), true);
            return;
        }
        if (of(k) == r) {
            god.sendMessage(Text.literal(k.title + ": раса уже " + r.title.toLowerCase()).formatted(k.color), true);
            return;
        }
        set(k, r);
        applyAll(k);
        if (k.world != null && Config.b("races.architecture", true)) Eras.rebuild(k, Civ.city(k), k.world);
        Chronicle.say(w.getServer(), k, "— теперь народ расы «" + r.title + "» (" + r.desc + ")");
        god.sendMessage(Text.literal(k.title + ": раса — " + r.title.toLowerCase()).formatted(k.color), true);
    }
}
