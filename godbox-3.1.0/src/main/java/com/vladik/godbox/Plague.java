package com.vladik.godbox;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** v3.1: эпидемии королевств — болезнь расходится по дорогам, с караванами, кораблями и войсками. */
public final class Plague {
    public static boolean on = true;

    static final class St {
        double level, immunity;
        boolean quarantine, forced;
        int qTimer, qCost, age, dead;
        String strain = "";
    }

    static final Map<Kingdoms.K, St> STATE = new EnumMap<>(Kingdoms.K.class);
    /** Заражённые караваны и суда в пути: "FROM>TO" -> уровень болезни при отправке. */
    private static final Map<String, Double> CARRIERS = new HashMap<>();
    private static final Random RNG = new Random();
    private static final String[][] ADJ = {
        {"Красный", "Красная", "Красное"}, {"Чёрный", "Чёрная", "Чёрное"}, {"Серый", "Серая", "Серое"},
        {"Болотный", "Болотная", "Болотное"}, {"Багровый", "Багровая", "Багровое"}, {"Ледяной", "Ледяная", "Ледяное"},
        {"Пепельный", "Пепельная", "Пепельное"}, {"Гнилой", "Гнилая", "Гнилое"}, {"Бледный", "Бледная", "Бледное"},
        {"Жёлтый", "Жёлтая", "Жёлтое"}, {"Тихий", "Тихая", "Тихое"}, {"Ночной", "Ночная", "Ночное"},
        {"Костяной", "Костяная", "Костяное"}, {"Южный", "Южная", "Южное"}};
    // существительное и его род: 0 м, 1 ж, 2 ср
    private static final String[] NOUN = {"мор", "лихорадка", "чума", "хворь", "поветрие", "язва", "горячка", "недуг"};
    private static final int[] GENDER = {0, 1, 1, 1, 2, 1, 1, 0};
    private static MinecraftServer server;
    private static int t;

    private Plague() {}

    public static void init() {
        SaveExt.register("plague", Plague::save, Plague::load, Plague::reset);
    }

    private static List<String> save() {
        List<String> o = new ArrayList<>();
        o.add("law|" + on);
        for (Map.Entry<Kingdoms.K, St> e : STATE.entrySet()) {
            St st = e.getValue();
            o.add(e.getKey().name() + "|" + (int) Math.round(st.level * 10) + "|" + (int) Math.round(st.immunity * 10) + "|" + st.quarantine
                + "|" + st.forced + "|" + st.qTimer + "|" + st.age + "|" + st.dead + "|" + st.strain.replace('|', ' ').replace('\n', ' '));
        }
        return o;
    }

    private static void load(String[] a) {
        try {
            if (a.length >= 2 && a[0].equals("law")) { on = Boolean.parseBoolean(a[1]); return; }
            if (a.length < 9) return;
            Kingdoms.K k = Kingdoms.K.valueOf(a[0]);
            St st = new St();
            st.level = clamp(Integer.parseInt(a[1]) / 10.0);
            st.immunity = clamp(Integer.parseInt(a[2]) / 10.0);
            st.quarantine = Boolean.parseBoolean(a[3]);
            st.forced = Boolean.parseBoolean(a[4]);
            st.qTimer = Integer.parseInt(a[5]);
            st.age = Integer.parseInt(a[6]);
            st.dead = Integer.parseInt(a[7]);
            st.strain = a[8];
            STATE.put(k, st);
        } catch (IllegalArgumentException ignored) {
            // неизвестное королевство или битая строка — пропускаем
        }
    }

    private static void reset() {
        STATE.clear();
        CARRIERS.clear();
        on = Config.b("plague.enabled", true);
        t = 0;
    }

    public static void clear() {
        STATE.clear();
        CARRIERS.clear();
        t = 0;
        server = null;
    }

    // ---------------- доступ ----------------
    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    private static St st(Kingdoms.K k) { return STATE.computeIfAbsent(k, x -> new St()); }

    private static boolean ok(Kingdoms.K k) { return k != null && k.alive && k.world != null && k.capital != null; }

    /** Уровень болезни 0..100. */
    public static int level(Kingdoms.K k) {
        St st = k == null ? null : STATE.get(k);
        return st == null ? 0 : (int) Math.ceil(st.level);
    }

    public static boolean infected(Kingdoms.K k) { return level(k) > 0; }

    public static boolean quarantined(Kingdoms.K k) {
        St st = k == null ? null : STATE.get(k);
        return st != null && st.quarantine;
    }

    /** Карантин: караваны и корабли между a и b не ходят. */
    public static boolean blocked(Kingdoms.K a, Kingdoms.K b) {
        return on && (quarantined(a) || quarantined(b));
    }

    /** Одна строка о здоровье королевства. */
    public static String status(Kingdoms.K k) {
        St st = k == null ? null : STATE.get(k);
        if (st == null) return "здорово";
        if (st.level > 0) return st.strain + ": " + level(k) + "%" + (st.quarantine ? " (карантин)" : "");
        if (st.quarantine) return "здорово (карантин)";
        return st.immunity >= 10 ? "здорово (иммунитет " + (int) st.immunity + "%)" : "здорово";
    }

    /** То же с цветом — для сводки королевства. */
    static String line(Kingdoms.K k) {
        St st = STATE.get(k);
        String c = st == null || (st.level <= 0 && !st.quarantine) ? "\u00a7a" : st.level >= 50 ? "\u00a74" : st.level > 0 ? "\u00a7c" : "\u00a7e";
        return c + status(k);
    }

    static String strain() {
        int n = RNG.nextInt(NOUN.length);
        return ADJ[RNG.nextInt(ADJ.length)][GENDER[n]] + " " + NOUN[n];
    }

    private static String came(String strain) {
        return strain.endsWith("а") || strain.endsWith("ь") ? "пришла" : strain.endsWith("е") ? "пришло" : "пришёл";
    }

    private static void announce(String msg) {
        Chronicle.log("\u00a72" + msg);
        if (server == null) return;
        Text tx = Text.literal("[Летопись] ").formatted(Formatting.GOLD).append(Text.literal("☣ " + msg).formatted(Formatting.DARK_GREEN));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(tx, false);
    }

    private static List<VillagerEntity> villagers(Kingdoms.K k) {
        List<VillagerEntity> l = new ArrayList<>();
        for (MobEntity m : k.members) if (m instanceof VillagerEntity v && v.isAlive()) l.add(v);
        return l;
    }

    // ---------------- заражение ----------------
    /** Начать эпидемию. how — как она появилась (null — без записи в летопись). */
    static boolean outbreak(Kingdoms.K k, String strain, double level, String how) {
        if (!ok(k)) return false;
        if (k.world.getServer() != null) server = k.world.getServer();
        St st = st(k);
        boolean fresh = st.level <= 0;
        if (fresh) {
            st.strain = strain == null || strain.isBlank() ? strain() : strain;
            st.age = 0;
            st.dead = 0;
        }
        st.level = clamp(Math.max(st.level, level));
        if (fresh) {
            BlockPos p = k.capital;
            Fx.particles(k.world, ParticleTypes.SNEEZE, p.getX() + 0.5, p.getY() + 2, p.getZ() + 0.5, 60, 4, 1.5, 4, 0.02);
            if (how != null) announce(how);
        }
        return fresh;
    }

    /**
     * Болезнь переходит из from в to с вероятностью chance (0..1) при полной силе эпидемии.
     * via — как она пришла: «с караваном из», «по дороге из», «с торговым судном из»...
     */
    public static boolean carry(Kingdoms.K from, Kingdoms.K to, double chance, String via) {
        return carry(from, to, chance, via, level(from), from == null || !STATE.containsKey(from) ? null : STATE.get(from).strain);
    }

    private static boolean carry(Kingdoms.K from, Kingdoms.K to, double chance, String via, double src, String strain) {
        if (!on || from == null || from == to || !ok(to) || src <= 0 || strain == null || strain.isEmpty()) return false;
        St ts = st(to);
        if (ts.level > 0) return false;
        double p = chance * Math.min(1.0, src / Config.d("plague.fullSpreadLevel", 50.0)) * (1.0 - ts.immunity / 100.0);
        if (quarantined(from) || ts.quarantine) p *= Config.d("plague.quarantineLeak", 0.1);
        if (RNG.nextDouble() >= p) return false;
        return outbreak(to, strain, Config.d("plague.startLevel", 10.0),
            "«" + strain + "» " + came(strain) + " в " + to.title + " " + via + " «" + from.title + "»");
    }

    /** Караван или судно вышло из a в b: запоминаем, везёт ли оно болезнь. */
    static void depart(Kingdoms.K a, Kingdoms.K b) {
        if (!on || a == null || b == null) return;
        String key = a.name() + ">" + b.name();
        if (level(a) > 0) CARRIERS.put(key, (double) level(a)); else CARRIERS.remove(key);
    }

    /** Караван или судно из a дошло до b. */
    static void arrive(Kingdoms.K a, Kingdoms.K b, double chance, String via) {
        if (a == null || b == null) return;
        Double lv = CARRIERS.remove(a.name() + ">" + b.name());
        St fs = STATE.get(a);
        if (fs == null || fs.strain.isEmpty()) return;
        double src = Math.max(lv == null ? 0 : lv, fs.level);
        carry(a, b, chance, via, src, fs.strain);
    }

    /** Королевство d захвачено королевством a: болезнь достаётся победителю. */
    static void conquered(Kingdoms.K a, Kingdoms.K d) {
        if (d == null) return;
        carry(d, a, Config.d("plague.conquestChance", 0.8), "с покорёнными жителями из");
        STATE.remove(d);
    }

    /** Мор из случайных событий и заражённые чумой жители дают начало эпидемии. */
    static void seed(Kingdoms.K k, String how) {
        if (!on || !ok(k)) return;
        St st = st(k);
        if (st.level > 0 || RNG.nextDouble() * 100 < st.immunity) return;
        String s = strain();
        outbreak(k, s, Config.d("plague.startLevel", 10.0) + 5, "В " + k.title + " началась эпидемия: «" + s + "» (" + how + ")");
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        server = s;
        if (s.getTickManager().isFrozen()) return;
        ++t;
        int interval = Math.max(100, Config.i("plague.interval", 600));
        if (!on || t % interval != 0) return;
        if (!Perf.go) { t--; return; }
        for (Kingdoms.K k : Kingdoms.K.values()) if (!k.alive) STATE.remove(k);
        observe();
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!ok(k)) continue;
            St st = st(k);
            if (st.level > 0) progress(s, k, st); else idle(k, st);
            decide(s, k, st);
        }
        spread();
    }

    /** Достаточно жителей с обычной чумой (сила «Чума») — начинается эпидемия. */
    private static void observe() {
        int need = Math.max(1, Config.i("plague.seedCarriers", 4));
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!ok(k) || level(k) > 0) continue;
            Powers2.Epidemic ep = Powers2.PLAGUES.get(k.world);
            if (ep == null || ep.sick.size() < need) continue;
            int n = 0;
            for (MobEntity m : k.members) if (m.isAlive() && ep.sick.containsKey(m.getUuid())) n++;
            if (n >= need) seed(k, "чума среди жителей");
        }
    }

    private static double crowding(Civ.City c, int pop) {
        int houses = c.count(Civ.Kind.HOUSE) + c.count(Civ.Kind.MANOR);
        return Math.max(0.5, Math.min(2.0, pop / (double) Math.max(1, houses * 3)));
    }

    /** Здоровое королевство: иммунитет тает, изредка болезнь возникает сама. */
    private static void idle(Kingdoms.K k, St st) {
        st.immunity = clamp(st.immunity - Config.d("plague.immunityDecay", 0.5));
        ServerWorld w = k.world;
        BlockPos p = k.capital;
        if (!w.shouldTickEntity(p) || !w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return;
        Civ.City c = Civ.city(k);
        int pop = villagers(k).size();
        if (pop < 8) return;
        double ch = Config.d("plague.spontaneous", 0.004) * crowding(c, pop) * (1.0 - st.immunity / 100.0);
        var biome = w.getBiome(p);
        if (biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP)) ch *= 3;
        if (c.hungry || c.food < 5) ch *= 2;
        if (Tech.has(k, Tech.T.MEDICINE)) ch *= 0.5;
        if (RNG.nextDouble() >= ch) return;
        String s = strain();
        outbreak(k, s, Config.d("plague.startLevel", 10.0), "В " + k.title + " вспыхнула болезнь: «" + s + "»");
    }

    private static int healers(Kingdoms.K k) {
        ServerWorld w = k.world;
        if (!w.shouldTickEntity(k.capital)) return 0;
        Box box = new Box(k.capital).expand(64, 40, 64);
        return w.getEntitiesByClass(MobEntity.class, box, m -> m.isAlive()
            && (m.getCommandTags().contains("gbc_white_mage") || m.getCommandTags().contains("gbc_plague_doctor"))).size();
    }

    /** Ход болезни в заражённом королевстве. */
    private static void progress(MinecraftServer s, Kingdoms.K k, St st) {
        ServerWorld w = k.world;
        Civ.City c = Civ.city(k);
        List<VillagerEntity> vill = villagers(k);
        int pop = vill.size();
        st.age++;
        double grow = Config.d("plague.growth", 6.0) * crowding(c, pop) * Math.max(0.4, Math.min(1.5, pop / 20.0)) * (1.0 - st.immunity / 100.0);
        double heal = st.age * Config.d("plague.burnout", 0.5) + c.count(Civ.Kind.TEMPLE) * 2 + c.count(Civ.Kind.WELL) * 0.5
            + (Tech.has(k, Tech.T.MEDICINE) ? 4 : 0) + (Tech.has(k, Tech.T.MAGIC) ? 2 : 0)
            + Math.min(3, healers(k)) * 5 + (st.quarantine ? 3 : 0);
        st.level = clamp(st.level + grow - heal);
        st.immunity = clamp(st.immunity + st.level * 0.04);
        if (st.level <= 0 || pop == 0) {
            st.level = 0;
            st.immunity = clamp(Math.max(st.immunity, Config.d("plague.immunityAfter", 80.0)));
            Chronicle.say(s, k, "пережило болезнь «" + st.strain + "»: умерло " + st.dead + " жителей");
            return;
        }
        int lv = (int) st.level;
        // цена болезни: меньше еды и золота, растёт смута
        c.food = Math.max(0, c.food - Math.max(1, lv / 20));
        Economy.add(k, -(lv / 25));
        Unrest.add(k, lv >= 50 ? 2 : 1);
        if (!w.shouldTickEntity(k.capital)) return;
        // больные жители
        int sick = Math.min(8, Math.max(1, pop * lv / 250));
        for (int i = 0; i < sick && !vill.isEmpty(); i++) {
            VillagerEntity v = vill.get(RNG.nextInt(vill.size()));
            v.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 400, 0));
            v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 400, 0));
            Fx.particles(w, ParticleTypes.SNEEZE, v.getX(), v.getY() + v.getHeight() * 0.8, v.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }
        // умершие: не больше лимита и не ниже порога выживших
        int max = Math.max(0, Config.i("plague.maxDeaths", 2)), floor = Math.max(1, Config.i("plague.minSurvivors", 6));
        int n = lv >= 30 ? Math.min(max, 1 + lv / 45) : (RNG.nextInt(30) < lv ? Math.min(max, 1) : 0);
        MobEntity king = Life.king(k);
        int alive = pop;
        for (int i = 0; i < 10 && n > 0 && alive > floor; i++) {
            VillagerEntity v = vill.get(RNG.nextInt(vill.size()));
            if (v == king || !v.isAlive()) continue;
            Fx.particles(w, ParticleTypes.SNEEZE, v.getX(), v.getY() + 1.5, v.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
            v.kill();
            st.dead++;
            alive--;
            n--;
        }
    }

    /** Правитель сам решает, закрывать ли границы. */
    private static void decide(MinecraftServer s, Kingdoms.K k, St st) {
        if (st.quarantine) {
            // цена закрытых границ: казна и смута
            Economy.add(k, -Math.max(0, Config.i("plague.quarantineGold", 3)));
            if (++st.qCost % 3 == 0) Unrest.add(k, 1);
        }
        if (st.forced) return;
        int trait = Life.kingTrait(k);
        boolean careful = trait == 0 || trait == 3;
        if (st.quarantine) {
            if (--st.qTimer > 0) return;
            if (st.level >= 10 || (trait == 3 && threat(k))) { st.qTimer = 2; return; }
            st.quarantine = false;
            Chronicle.say(s, k, "снимает карантин и открывает границы");
            return;
        }
        int at = Config.i("plague.quarantineAt", 35);
        if (trait == 2) at += 25;
        if (careful) at = Math.max(8, at / 3);
        boolean close = st.level >= at || (trait == 3 && st.level <= 0 && st.immunity < 40 && threat(k));
        if (!close) return;
        st.quarantine = true;
        st.qTimer = Math.max(2, Config.i("plague.quarantineTime", 12));
        Chronicle.say(s, k, st.level > 0 ? "закрывает границы на карантин: «" + st.strain + "»" : "закрывает границы, опасаясь болезни у соседей");
    }

    /** У соседа в том же мире бушует эпидемия. */
    private static boolean threat(Kingdoms.K k) {
        for (Kingdoms.K o : Kingdoms.K.values())
            if (o != k && ok(o) && o.world == k.world && level(o) >= 40) return true;
        return false;
    }

    private static List<BlockPos> nodes(Kingdoms.K k) {
        List<BlockPos> l = new ArrayList<>();
        l.add(k.capital);
        l.addAll(Conquest.towns(k));
        return l;
    }

    /** Есть ли достроенная дорога или тракт между городами a и b. */
    static boolean road(Kingdoms.K a, Kingdoms.K b) {
        if (!ok(a) || !ok(b) || a.world != b.world) return false;
        if (Roads.DONE.isEmpty() && Highways.DONE.isEmpty()) return false;
        List<BlockPos> nb = nodes(b);
        for (BlockPos p : nodes(a)) for (BlockPos q : nb) {
            String key = Roads.key(p, q);
            if (Roads.DONE.contains(key) || Highways.DONE.contains(key)) return true;
        }
        return false;
    }

    /** Войска воюющих королевств сошлись у чьей-то столицы. */
    private static boolean fighting(Kingdoms.K a, Kingdoms.K b) {
        if (Diplomacy.rel(a, b) != Diplomacy.WAR) return false;
        return near(a, b.capital) || near(b, a.capital);
    }

    private static boolean near(Kingdoms.K k, BlockPos p) {
        for (MobEntity m : k.members)
            if (m.isAlive() && !(m instanceof VillagerEntity) && m.squaredDistanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 60 * 60) return true;
        return false;
    }

    /** Болезнь уходит к соседям только по настоящим связям: дороги и войска (караваны и суда — через arrive). */
    private static void spread() {
        double roadCh = Config.d("plague.roadChance", 0.10), warCh = Config.d("plague.warChance", 0.15);
        for (Kingdoms.K a : Kingdoms.K.values()) {
            if (!ok(a) || level(a) < 10) continue;
            for (Kingdoms.K b : Kingdoms.K.values()) {
                if (b == a || !ok(b) || b.world != a.world || level(b) > 0) continue;
                if (road(a, b) && carry(a, b, roadCh, "по дороге из")) continue;
                if (fighting(a, b)) carry(a, b, warCh, "с войском из");
            }
        }
    }

    // ---------------- силы бога ----------------
    private static void msg(ServerPlayerEntity god, String s, Formatting f) {
        god.sendMessage(Text.literal(s).formatted(f), true);
    }

    /** Ближайшее королевство: его столица или город рядом с точкой. */
    private static Kingdoms.K nearest(ServerWorld w, BlockPos pos, double r) {
        Kingdoms.K best = null;
        double bd = r * r;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!ok(k) || k.world != w) continue;
            for (BlockPos p : nodes(k)) {
                double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ(), d = dx * dx + dz * dz;
                if (d < bd) { bd = d; best = k; }
            }
        }
        return best;
    }

    /** Сила «Эпидемия». */
    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        if (!on) { msg(god, "Закон «Эпидемии» выключен", Formatting.GRAY); return; }
        Kingdoms.K k = nearest(w, pos, 250);
        if (k == null) { msg(god, "Рядом нет королевств", Formatting.GRAY); return; }
        St st = st(k);
        if (st.level <= 0) {
            String s = strain();
            st.immunity = Math.min(st.immunity, 20);
            outbreak(k, s, Config.d("plague.powerLevel", 25.0), "Бог наслал болезнь на " + k.title + ": «" + s + "»");
        } else {
            st.level = clamp(st.level + 20);
        }
        Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT, 2f, 0.6f);
        msg(god, "☣ " + k.title + " — " + status(k), Formatting.DARK_GREEN);
    }

    /** Сила «Карантин»: закрыть или открыть границы ближайшего королевства. */
    public static void quarantine(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        if (!on) { msg(god, "Закон «Эпидемии» выключен", Formatting.GRAY); return; }
        Kingdoms.K k = nearest(w, pos, 250);
        if (k == null) { msg(god, "Рядом нет королевств", Formatting.GRAY); return; }
        St st = st(k);
        boolean v = !st.quarantine;
        st.quarantine = v;
        st.forced = v;
        st.qTimer = v ? Math.max(2, Config.i("plague.quarantineTime", 12)) : 0;
        Chronicle.say(w.getServer(), k, v ? "закрывает границы на карантин по воле бога" : "снимает карантин по воле бога");
        msg(god, "Карантин — " + k.title + ": " + (v ? "границы закрыты, торговля стоит" : "границы открыты"), v ? Formatting.YELLOW : Formatting.GREEN);
    }

    /** Сила «Исцеление» рядом с королевством ослабляет эпидемию. */
    static void cure(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush) {
        Kingdoms.K k = nearest(w, pos, 120);
        St st = k == null ? null : STATE.get(k);
        if (st == null || st.level <= 0) return;
        st.level = clamp(st.level - Config.d("plague.cureAmount", 20.0) - brush * 2);
        st.immunity = clamp(st.immunity + 5);
        if (st.level <= 0) {
            st.immunity = clamp(Math.max(st.immunity, Config.d("plague.immunityAfter", 80.0)));
            Chronicle.say(w.getServer(), k, "исцелено богом от болезни «" + st.strain + "»: умерло " + st.dead + " жителей");
            if (god != null) msg(god, k.title + ": болезнь побеждена", Formatting.GREEN);
        } else if (god != null) {
            msg(god, k.title + " — " + status(k), Formatting.GREEN);
        }
    }
}
