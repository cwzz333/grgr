package com.vladik.godbox;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Случайные события мира: урожай, засуха, набеги, мор, герои... и их последствия (цепочки). */
public final class Events {
    public static boolean on = true;
    private static int timer;

    /** v3.1: затяжные состояния королевства (в порядке важности для показа). */
    public enum Cond {
        FAMINE("Голод", 'c'), DROUGHT("Засуха", 'e'), MOURNING("Траур", '8'), REBUILD("Восстановление после пожара", '6'),
        GOLDEN_AGE("Золотой век", '6'), GOLD_RUSH("Золотая лихорадка", 'e'), BOOM("Бум рождаемости", 'a'),
        FESTIVAL("Праздник", 'd'), ABUNDANCE("Изобилие", 'a'), LODE("Золотая жила", 'e');

        public final String title;
        final char color;

        Cond(String title, char color) {
            this.title = title;
            this.color = color;
        }
    }

    private static final class St {
        int left, stage;
        boolean fresh = true;

        St(int left, int stage) {
            this.left = left;
            this.stage = stage;
        }
    }

    private static final int MAX_LEN = 12;
    private static final Map<Kingdoms.K, EnumMap<Cond, St>> COND = new EnumMap<>(Kingdoms.K.class);
    /** Пауза после голода (в периодах), чтобы он не начинался снова сразу. */
    private static final Map<Kingdoms.K, Integer> COOL = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, UUID> HERO = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, String> HERO_NAME = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, Integer> HERO_AGE = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, MobEntity> HERO_REF = new EnumMap<>(Kingdoms.K.class);

    private Events() {}

    public static void init() {
        SaveExt.register("events", Events::saveLines, Events::loadLine, Events::reset);
    }

    private static List<String> saveLines() {
        List<String> out = new ArrayList<>();
        for (var e : COND.entrySet())
            for (var c : e.getValue().entrySet())
                out.add("C|" + e.getKey().name() + "|" + c.getKey().name() + "|" + c.getValue().left + "|" + c.getValue().stage);
        for (var e : HERO.entrySet()) {
            String n = HERO_NAME.getOrDefault(e.getKey(), "").replace('|', ' ').replace('\n', ' ');
            out.add("H|" + e.getKey().name() + "|" + e.getValue() + "|" + HERO_AGE.getOrDefault(e.getKey(), 0) + "|" + n);
        }
        return out;
    }

    private static void loadLine(String[] a) {
        try {
            if (a.length < 5) return;
            Kingdoms.K k = Kingdoms.K.valueOf(a[1]);
            if (a[0].equals("C")) {
                St st = new St(Math.max(1, Math.min(MAX_LEN, Integer.parseInt(a[3]))), Integer.parseInt(a[4]));
                st.fresh = false;
                COND.computeIfAbsent(k, x -> new EnumMap<>(Cond.class)).put(Cond.valueOf(a[2]), st);
            } else if (a[0].equals("H")) {
                HERO.put(k, UUID.fromString(a[2]));
                HERO_AGE.put(k, Integer.parseInt(a[3]));
                HERO_NAME.put(k, a[4]);
            }
        } catch (Exception ignored) {
        }
    }

    private static void reset() {
        COND.clear();
        COOL.clear();
        HERO.clear();
        HERO_NAME.clear();
        HERO_AGE.clear();
        HERO_REF.clear();
    }

    // ---------------- доступ для интерфейса и других систем ----------------
    public static boolean has(Kingdoms.K k, Cond c) {
        EnumMap<Cond, St> m = COND.get(k);
        return m != null && m.containsKey(c);
    }

    /** Сколько периодов событий ещё продлится состояние (0 — его нет). */
    public static int left(Kingdoms.K k, Cond c) {
        EnumMap<Cond, St> m = COND.get(k);
        St st = m == null ? null : m.get(c);
        return st == null ? 0 : st.left;
    }

    /** Главное состояние королевства или null. */
    public static Cond condition(Kingdoms.K k) {
        EnumMap<Cond, St> m = COND.get(k);
        if (m == null) return null;
        for (Cond c : Cond.values()) if (m.containsKey(c)) return c;
        return null;
    }

    public static List<Cond> conditions(Kingdoms.K k) {
        List<Cond> out = new ArrayList<>();
        EnumMap<Cond, St> m = COND.get(k);
        if (m != null) for (Cond c : Cond.values()) if (m.containsKey(c)) out.add(c);
        return out;
    }

    /** Состояния одной строкой: "Голод, Засуха" ("" — всё спокойно). */
    public static String status(Kingdoms.K k) {
        StringBuilder s = new StringBuilder();
        for (Cond c : conditions(k)) {
            if (s.length() > 0) s.append(", ");
            s.append(c.title);
        }
        return s.toString();
    }

    /** Хвост для Civ.summary. */
    static String summary(Kingdoms.K k) {
        String s = status(k);
        return s.isEmpty() ? "" : " | " + s.toLowerCase();
    }

    /** Строка для окна статистики: сезон, климат и состояния. */
    static String statusLine(Kingdoms.K k) {
        StringBuilder s = new StringBuilder();
        if (Seasons.on) {
            s.append("\u00a77Сезон: \u00a7f").append(Seasons.label()).append("\u00a77, климат ").append(Seasons.climate(k));
            if (Seasons.frozen(k)) s.append(", \u00a7bпорт во льду");
            s.append('\n');
        }
        s.append("\u00a77Состояние: ");
        List<Cond> l = conditions(k);
        if (l.isEmpty()) s.append("\u00a7aспокойно");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) s.append("\u00a77, ");
            s.append('\u00a7').append(l.get(i).color).append(l.get(i).title);
        }
        return s.append('\n').toString();
    }

    /** Сколько еды реально попадёт на склад из n собранных (сезон, засуха, золотой век). */
    static int harvest(Kingdoms.K k, int n) {
        if (n <= 0 || k == null) return n;
        double m = Seasons.food(k);
        if (on && has(k, Cond.DROUGHT)) m *= Math.max(0.1, Math.min(1.0, Config.d("events.drought.yield", 0.5)));
        if (on && has(k, Cond.GOLDEN_AGE)) m *= 1.15;
        if (m == 1.0) return n;
        double v = n * m;
        int whole = (int) Math.floor(v);
        return whole + (Math.random() < v - whole ? 1 : 0);
    }

    /** Раз в сколько циклов экономики возможны рождения. */
    static int birthPeriod(Kingdoms.K k) {
        int p = Seasons.birthPeriod();
        if (!on) return p;
        if (has(k, Cond.FAMINE)) return p * 2;
        return has(k, Cond.BOOM) ? Math.max(1, p - 1) : p;
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        if (!on || ++timer % Math.max(200, Config.i("events.period", 1800)) != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive) {
                drop(k);
                continue;
            }
            if (k.world == null || k.capital == null || !k.world.shouldTickEntity(k.capital)) continue;
            Random r = k.world.getRandom();
            try {
                step(s, k);
            } catch (Exception e) {
                GodBox.LOG.error("GodBox: цепочка событий {} не удалась", k.name(), e);
            }
            if (!k.alive || k.world == null || k.capital == null) continue;
            if (r.nextInt(Math.max(1, Config.i("events.chance", 4))) == 0) fire(s, k, pick(k, r));
        }
    }

    public static void force(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Diplomacy.nearest(w, pos);
        if (k == null) {
            god.sendMessage(Text.literal("Рядом нет королевства"), true);
            return;
        }
        fire(god.getServer(), k, pick(k, w.getRandom()));
    }

    /** Выбор события с учётом сезона и текущих состояний. */
    private static int pick(Kingdoms.K k, Random r) {
        double[] wt = new double[9];
        double sum = 0;
        for (int e = 0; e < wt.length; e++) {
            double x = Seasons.weight(k, e);
            if (e == 1 && (has(k, Cond.DROUGHT) || has(k, Cond.FAMINE))) x = 0;
            if (e == 0 && has(k, Cond.DROUGHT)) x *= 0.3;
            if (e == 7 && has(k, Cond.REBUILD)) x = 0;
            if (e == 6 && HERO.containsKey(k)) x = 0;
            if (e == 5 && has(k, Cond.FAMINE)) x = 0;
            wt[e] = Math.max(0, x);
            sum += wt[e];
        }
        if (sum <= 0) return r.nextInt(9);
        double x = r.nextDouble() * sum;
        for (int e = 0; e < wt.length; e++) {
            x -= wt[e];
            if (x < 0) return e;
        }
        return 8;
    }

    private static void drop(Kingdoms.K k) {
        COND.remove(k);
        COOL.remove(k);
        HERO.remove(k);
        HERO_NAME.remove(k);
        HERO_AGE.remove(k);
        HERO_REF.remove(k);
    }

    private static St start(Kingdoms.K k, Cond c, int len) {
        St st = new St(Math.max(1, Math.min(MAX_LEN, len)), 0);
        COND.computeIfAbsent(k, x -> new EnumMap<>(Cond.class)).put(c, st);
        return st;
    }

    private static void end(Kingdoms.K k, Cond c) {
        EnumMap<Cond, St> m = COND.get(k);
        if (m != null) m.remove(c);
    }

    // ---------------- цепочки ----------------
    private static void step(MinecraftServer s, Kingdoms.K k) {
        ServerWorld w = k.world;
        Civ.City c = Civ.city(k);
        Random r = w.getRandom();
        COOL.computeIfPresent(k, (x, v) -> v <= 1 ? null : v - 1);
        heroStep(s, k, w);
        EnumMap<Cond, St> m = COND.get(k);
        if (m != null) {
            for (Cond cd : Cond.values()) {
                St st = m.get(cd);
                if (st == null) continue;
                if (st.fresh) {
                    st.fresh = false;
                    continue;
                }
                during(s, k, c, w, r, cd, st);
                if (m.get(cd) != st) continue;
                if (--st.left <= 0) {
                    m.remove(cd);
                    expire(s, k, c, r, cd, st);
                }
            }
        }
        // голод начинается сам: из-за засухи или суровой зимы
        if (!has(k, Cond.FAMINE) && !COOL.containsKey(k) && c.hungry && c.food <= 0) {
            boolean drought = has(k, Cond.DROUGHT);
            if (drought || (Seasons.winter(k) && r.nextInt(2) == 0)) {
                start(k, Cond.FAMINE, Config.i("events.famine.max", 6));
                end(k, Cond.BOOM);
                end(k, Cond.ABUNDANCE);
                Chronicle.say(s, k, drought ? "— из-за засухи начался голод: амбары пусты" : "— из-за суровой зимы начался голод: запасы кончились");
            }
        }
    }

    private static void during(MinecraftServer s, Kingdoms.K k, Civ.City c, ServerWorld w, Random r, Cond cd, St st) {
        BlockPos cap = k.capital;
        switch (cd) {
            case DROUGHT -> {
                if (w.isRaining() && !Seasons.hot(k) && r.nextInt(2) == 0) {
                    end(k, cd);
                    Chronicle.say(s, k, "— дожди прекратили засуху");
                    return;
                }
                int n = Math.min(c.food, (int) Math.round(Config.i("events.drought.drain", 4) * Seasons.severity(k)));
                if (n > 0) c.food -= n;
                Fx.particles(w, ParticleTypes.WHITE_ASH, cap.getX() + 0.5, cap.getY() + 3, cap.getZ() + 0.5, 60, 8, 3, 8, 0.02);
            }
            case FAMINE -> {
                if (!c.hungry && c.food >= Config.i("events.famine.end", 15)) {
                    end(k, cd);
                    COOL.put(k, 3);
                    Chronicle.say(s, k, "— голод отступил: амбары снова наполняются");
                    return;
                }
                st.stage++;
                MobEntity king = Life.king(k);
                List<VillagerEntity> vill = villagers(k);
                if (vill.size() > 4) {
                    VillagerEntity v = vill.get(r.nextInt(vill.size()));
                    if (v != king) v.damage(w.getDamageSources().starve(), 4f);
                }
                Unrest.add(k, Math.max(0, Math.min(15, Config.i("events.famine.unrest", 4))));
                if (st.stage == 2) {
                    boolean desperate = Life.kingTrait(k) == 1 || Unrest.unrest(k) >= 50;
                    if (desperate && r.nextInt(100) < Config.i("events.raid.chance", 60) && raid(s, k, c, r)) return;
                }
                if (st.stage >= 2 && st.stage < 2 + Math.max(0, Math.min(6, Config.i("events.refugees.waves", 3)))) {
                    leave(s, k, 1 + r.nextInt(Math.max(1, Math.min(6, Config.i("events.refugees.size", 3)))), true);
                }
            }
            case BOOM -> {
                if (st.stage == 0 && villagers(k).size() >= CivPlus.cap(c)) {
                    st.stage = 1;
                    emigrate(s, k, w);
                }
            }
            case GOLD_RUSH -> {
                if (Economy.on) Economy.add(k, Math.max(0, Math.min(200, Config.i("events.rush.gold", 12))));
                c.ore += 2;
                if (st.stage == 0 && r.nextInt(100) < Config.i("events.rush.bandits", 35)) {
                    st.stage = 1;
                    Chronicle.say(s, k, "— слухи о золоте привлекли разбойников");
                    fire(s, k, 3);
                }
            }
            case REBUILD -> {
                int nw = Math.max(0, Config.i("events.rebuild.wood", 6)), ns = Math.max(0, Config.i("events.rebuild.stone", 3));
                if (c.wood >= nw && c.stone >= ns) {
                    c.wood -= nw;
                    c.stone -= ns;
                } else {
                    c.wood = Math.max(0, c.wood - nw);
                    c.stone = Math.max(0, c.stone - ns);
                    Unrest.add(k, 5);
                    if (st.stage == 0) Chronicle.say(s, k, "— из-за нехватки дерева и камня восстановление после пожара встало: погорельцы ропщут");
                    st.stage = 1;
                }
            }
            case MOURNING -> Unrest.add(k, 2);
            case GOLDEN_AGE -> {
                Unrest.add(k, -3);
                c.faith = Math.min(999, c.faith + 3);
                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, cap.getX() + 0.5, cap.getY() + 2, cap.getZ() + 0.5, 40, 6, 2, 6, 0.1);
            }
            default -> {
            }
        }
    }

    private static void expire(MinecraftServer s, Kingdoms.K k, Civ.City c, Random r, Cond cd, St st) {
        switch (cd) {
            case DROUGHT -> Chronicle.say(s, k, "— засуха отступила, поля снова зеленеют");
            case FAMINE -> {
                COOL.put(k, 3);
                c.food += 6;
                Chronicle.say(s, k, "— голод пошёл на убыль: выжившие делят последние запасы");
            }
            case ABUNDANCE -> {
                if (c.food >= Config.i("events.boom.food", 30) && !has(k, Cond.FAMINE)) {
                    start(k, Cond.BOOM, Config.i("events.boom.length", 3));
                    Chronicle.say(s, k, "— благодаря богатому урожаю начался бум рождаемости");
                }
            }
            case LODE -> {
                start(k, Cond.GOLD_RUSH, Config.i("events.rush.length", 4));
                Chronicle.say(s, k, "— весть о золотой жиле разнеслась: началась золотая лихорадка, казна растёт");
            }
            case GOLD_RUSH -> Chronicle.say(s, k, "— золотая лихорадка утихла: жила истощилась");
            case FESTIVAL -> pilgrimage(s, k, c, r);
            case REBUILD -> Chronicle.say(s, k, st.stage == 0 ? "— город отстроен после пожара" : "— восстановление после пожара сорвано: следы огня остались");
            case GOLDEN_AGE -> Chronicle.say(s, k, "— золотой век подошёл к концу");
            default -> {
            }
        }
    }

    private static List<VillagerEntity> villagers(Kingdoms.K k) {
        List<VillagerEntity> vill = new ArrayList<>();
        for (MobEntity m : k.members) if (m instanceof VillagerEntity v && v.isAlive()) vill.add(v);
        return vill;
    }

    /** Ближайшее живое не враждебное королевство в том же мире. */
    private static Kingdoms.K neighbour(Kingdoms.K k) {
        Kingdoms.K best = null;
        double bd = Double.MAX_VALUE;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != k.world || o.capital == null || Diplomacy.hostile(k, o) || o.members.size() >= 240) continue;
            double d = o.capital.getSquaredDistance(k.capital);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** Часть жителей уходит к соседу: беженцы от голода или переселенцы от тесноты. */
    private static boolean leave(MinecraftServer s, Kingdoms.K k, int n, boolean famine) {
        int keep = Math.max(4, Config.i("events.refugees.keep", 6));
        n = Math.min(n, villagers(k).size() - keep);
        if (n <= 0) return false;
        List<MobEntity> g = Colonies.settlers(k, n);
        if (g.isEmpty()) return false;
        Kingdoms.K o = neighbour(k);
        if (o == null) {
            if (!famine) return false;
            for (MobEntity m : g) {
                k.members.remove(m);
                m.discard();
            }
            Chronicle.say(s, k, "— из-за голода жители (" + g.size() + ") покинули королевство и разбрелись по свету: приюта им никто не дал");
            return true;
        }
        for (MobEntity m : g) {
            k.members.remove(m);
            Kingdoms.join(o, m);
            o.members.add(m);
            m.setTarget(null);
        }
        Plague.carry(k, o, 0.5, "с беженцами из");
        Civ.City oc = Civ.city(o);
        boolean welcome = !oc.hungry && oc.food >= 40;
        Diplomacy.op(k, o, welcome ? 6 : -6);
        Unrest.add(o, Math.min(6, g.size() + (welcome ? 0 : 2)));
        Chronicle.say(s, k, (famine ? "— из-за голода беженцы (" : "— из-за тесноты переселенцы (") + g.size() + ") уходят к соседям: " + o.title);
        walk(s, o, g, welcome);
        return true;
    }

    /** Ушедшие идут пешком в новую столицу; отставших подтягиваем, если там загружено. */
    private static void walk(MinecraftServer s, Kingdoms.K o, List<MobEntity> group, boolean welcome) {
        ServerWorld w = o.world;
        BlockPos to = o.capital;
        Tasks.add(new Tasks.Task() {
            int t;

            @Override
            public boolean tick() {
                if (++t % 20 != 0) return false;
                boolean near = false, any = false;
                for (MobEntity m : group) {
                    if (!m.isAlive()) continue;
                    any = true;
                    double dx = m.getX() - to.getX(), dz = m.getZ() - to.getZ();
                    if (dx * dx + dz * dz < 256) near = true;
                    else m.getNavigation().startMovingTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.9);
                }
                if (any && !near && t < 20 * 90) return false;
                if (!any || !o.alive || o.world != w) return true;
                if (w.shouldTickEntity(to)) {
                    for (MobEntity m : group) {
                        if (!m.isAlive() || m.getWorld() != w) continue;
                        double dx = m.getX() - to.getX(), dz = m.getZ() - to.getZ();
                        if (dx * dx + dz * dz < 576) continue;
                        BlockPos g = Fx.surface(w, to.getX() + w.getRandom().nextInt(9) - 4, to.getZ() + w.getRandom().nextInt(9) - 4);
                        m.refreshPositionAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 0f);
                    }
                }
                Chronicle.say(s, o, welcome ? "приняло пришлых радушно: население растёт" : "приняло пришлых без радости: лишние рты, растёт недовольство");
                return true;
            }
        });
    }

    /** Набег за едой на самого богатого соседа: ссора и, если дипломатия разрешает, война. */
    private static boolean raid(MinecraftServer s, Kingdoms.K k, Civ.City c, Random r) {
        Kingdoms.K t = null;
        int best = 9;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != k.world || o.capital == null || Diplomacy.allied(k, o) || Diplomacy.hostile(k, o)) continue;
            int f = Civ.city(o).food;
            if (f > best) {
                best = f;
                t = o;
            }
        }
        if (t == null) return false;
        Civ.City tc = Civ.city(t);
        int loot = Math.min(tc.food / 4, 20);
        tc.food -= loot;
        c.food += loot;
        Diplomacy.op(k, t, -Math.max(0, Math.min(60, Config.i("events.raid.opinion", 30))));
        Chronicle.say(s, k, "— голод толкнул на набег за едой: " + t.title + " лишается припасов (" + loot + " еды)");
        if (Diplomacy.auto && !Kingdoms.eternalPeace && Diplomacy.rel(k, t) == Diplomacy.PEACE
            && (Diplomacy.OP[k.ordinal()][t.ordinal()] <= -40 || r.nextInt(2) == 0)) {
            Diplomacy.declare(s, k, t);
        }
        return true;
    }

    /** Волна переселенцев после бума: новый город, если колонизация возможна, иначе уход к соседям. */
    private static void emigrate(MinecraftServer s, Kingdoms.K k, ServerWorld w) {
        if (Colonies.on && Perf.go && Conquest.towns(k).size() < 8 && villagers(k).size() >= 10) {
            Colonies.Site st = Colonies.site(w, k, 70, 150);
            List<MobEntity> g = st == null ? List.of() : Colonies.settlers(k, 4);
            if (st != null && g.size() >= 2 && (!st.sea() || (Ships.on && Ships.colonize(w, k, st.pos(), g)))) {
                Chronicle.say(s, k, "— из-за тесноты после бума рождаемости часть жителей уходит основать новый город");
                if (!st.sea()) Colonies.march(w, k, st.pos(), g);
                return;
            }
        }
        leave(s, k, 2 + w.getRandom().nextInt(2), false);
    }

    /** Праздник сближает единоверцев: сразу растёт мнение, позже — паломничество. */
    private static void festival(MinecraftServer s, Kingdoms.K k, Civ.City c) {
        start(k, Cond.FESTIVAL, 2);
        if (c.religion < 0) return;
        int n = 0;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != k.world || Diplomacy.hostile(k, o) || Civ.city(o).religion != c.religion) continue;
            Diplomacy.op(k, o, 8);
            n++;
        }
        if (n > 0) Chronicle.say(s, k, "— праздник сблизил единоверцев: соседи той же веры смотрят теплее");
    }

    private static void pilgrimage(MinecraftServer s, Kingdoms.K k, Civ.City c, Random r) {
        if (c.religion < 0) return;
        Kingdoms.K t = null;
        int best = Integer.MIN_VALUE;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != k.world || o.capital == null || Diplomacy.hostile(k, o) || Civ.city(o).religion != c.religion) continue;
            int op = Diplomacy.OP[k.ordinal()][o.ordinal()];
            if (op > best) {
                best = op;
                t = o;
            }
        }
        if (t == null) return;
        Civ.City tc = Civ.city(t);
        Diplomacy.op(k, t, 10);
        c.faith = Math.min(999, c.faith + 5);
        tc.faith = Math.min(999, tc.faith + 5);
        Chronicle.say(s, k, "— после праздника паломники отправились к единоверцам: " + t.title);
        if (Diplomacy.auto && Diplomacy.rel(k, t) == Diplomacy.PEACE && Diplomacy.OP[k.ordinal()][t.ordinal()] >= 40 && r.nextInt(3) == 0) {
            Diplomacy.set(k, t, Diplomacy.ALLY);
            Diplomacy.broadcast(s, Text.literal("🤝 " + k.title + " и " + t.title + " заключили союз!").formatted(Formatting.GREEN, Formatting.BOLD));
        }
    }

    /** Герой события: пал — траур, одержал победы — золотой век. */
    private static void heroStep(MinecraftServer s, Kingdoms.K k, ServerWorld w) {
        UUID id = HERO.get(k);
        if (id == null) return;
        int age = HERO_AGE.merge(k, 1, Integer::sum);
        String name = HERO_NAME.getOrDefault(k, "");
        MobEntity h = HERO_REF.get(k);
        boolean dead = h != null && (h.isDead() || h.getRemovalReason() == net.minecraft.entity.Entity.RemovalReason.KILLED);
        if (!dead && h != null && h.getRemovalReason() == net.minecraft.entity.Entity.RemovalReason.DISCARDED) {
            forget(k);
            return;
        }
        if (!dead && (h == null || h.isRemoved())) {
            h = w.getEntity(id) instanceof MobEntity m ? m : null;
            if (h == null) {
                HERO_REF.remove(k);
                if (age > 40) forget(k);
                return;
            }
            HERO_REF.put(k, h);
            dead = h.isDead();
        }
        if (dead) {
            forget(k);
            end(k, Cond.GOLDEN_AGE);
            start(k, Cond.MOURNING, Config.i("events.mourning.length", 3));
            Unrest.add(k, 8);
            Chronicle.say(s, k, "— гибель героя " + name + " повергла народ в траур: растёт смута");
            return;
        }
        if (Kingdoms.of(h) != k) {
            forget(k);
            return;
        }
        if (Heroes.KILLS.getOrDefault(id, 0) >= Math.max(1, Config.i("events.hero.kills", 5))) {
            forget(k);
            end(k, Cond.MOURNING);
            start(k, Cond.GOLDEN_AGE, Config.i("events.golden.length", 5));
            Unrest.add(k, -10);
            Chronicle.say(s, k, "— победы героя " + name + " воодушевили народ: начался золотой век");
        } else if (age > 40) forget(k);
    }

    private static void forget(Kingdoms.K k) {
        HERO.remove(k);
        HERO_NAME.remove(k);
        HERO_AGE.remove(k);
        HERO_REF.remove(k);
    }

    static void fire(MinecraftServer s, Kingdoms.K k, int e) {
        ServerWorld w = k.world;
        Civ.City c = Civ.city(k);
        Random r = w.getRandom();
        BlockPos cap = k.capital;
        List<VillagerEntity> vill = new ArrayList<>();
        for (MobEntity m : k.members) if (m instanceof VillagerEntity v && v.isAlive()) vill.add(v);
        switch (e) {
            case 0 -> {
                c.food += 30;
                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, cap.getX() + 0.5, cap.getY() + 2, cap.getZ() + 0.5, 80, 6, 2, 6, 0.1);
                Chronicle.say(s, k, "— урожайный год! Амбары полны (+30 еды)");
                if (!has(k, Cond.FAMINE) && !has(k, Cond.BOOM)) start(k, Cond.ABUNDANCE, 1);
            }
            case 1 -> {
                int n = Math.min(c.food, 20);
                c.food -= n;
                Fx.particles(w, ParticleTypes.WHITE_ASH, cap.getX() + 0.5, cap.getY() + 3, cap.getZ() + 0.5, 120, 8, 3, 8, 0.02);
                Chronicle.say(s, k, "постигла засуха (−" + n + " еды)");
                end(k, Cond.ABUNDANCE);
                start(k, Cond.DROUGHT, (int) Math.round(Config.i("events.drought.length", 4) * Seasons.severity(k)));
            }
            case 2 -> {
                c.ore += 15;
                Chronicle.say(s, k, "— шахтёры нашли золотую жилу (+15 руды)");
                if (!has(k, Cond.GOLD_RUSH)) start(k, Cond.LODE, 1);
            }
            case 3 -> {
                int n = 2 + r.nextInt(3);
                double a = r.nextDouble() * Math.PI * 2;
                BlockPos at = Civ.ground(w, cap.getX() + (int) (Math.cos(a) * 26), cap.getZ() + (int) (Math.sin(a) * 26));
                for (int i = 0; i < n; i++) {
                    VindicatorEntity b = EntityType.VINDICATOR.spawn(w, at.add(r.nextInt(3) - 1, 0, r.nextInt(3) - 1), SpawnReason.EVENT);
                    if (b != null) {
                        b.setCustomName(Text.literal("Разбойник").formatted(Formatting.DARK_GRAY));
                        b.addCommandTag("gbraider");
                    }
                }
                w.playSound(null, at, SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 4f, 1f);
                Chronicle.say(s, k, "атаковано бандой разбойников (" + n + ")!");
            }
            case 4 -> {
                if (vill.size() < 6) {
                    fire(s, k, 0);
                    return;
                }
                int n = 1 + r.nextInt(3);
                MobEntity king = Life.king(k);
                int dead = 0;
                for (int i = 0; i < 10 && dead < n; i++) {
                    VillagerEntity v = vill.get(r.nextInt(vill.size()));
                    if (v == king || !v.isAlive()) continue;
                    Fx.particles(w, ParticleTypes.SNEEZE, v.getX(), v.getY() + 1.5, v.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
                    v.kill();
                    dead++;
                }
                Chronicle.say(s, k, "поражено мором: умерло жителей — " + dead);
                Plague.seed(k, "мор");
            }
            case 5 -> {
                for (VillagerEntity v : vill) v.setHealth(v.getMaxHealth());
                c.faith = Math.min(999, c.faith + 15);
                for (int i = 0; i < 6; i++) {
                    Tasks.delay(i * 8, () -> {
                        double x = cap.getX() + r.nextInt(17) - 8, z = cap.getZ() + r.nextInt(17) - 8;
                        Fx.particles(w, ParticleTypes.FIREWORK, x, cap.getY() + 14 + r.nextInt(6), z, 70, 0.2, 0.2, 0.2, 0.25);
                        w.playSound(null, BlockPos.ofFloored(x, cap.getY() + 14, z), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.AMBIENT, 4f, 1f);
                    });
                }
                Chronicle.say(s, k, "празднует великий праздник!");
                festival(s, k, c);
            }
            case 6 -> {
                MobEntity h = Kingdoms.spawn(k, EntityType.VINDICATOR, Civ.ground(w, cap.getX() + 2, cap.getZ() + 2));
                if (h == null) return;
                EntityAttributeInstance hp = h.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (hp != null) hp.setBaseValue(80);
                h.setHealth(h.getMaxHealth());
                h.setGlowing(true);
                h.addCommandTag("gbhero");
                String n = Life.name(h);
                h.setCustomName(Text.literal("Герой " + n).formatted(Formatting.GOLD));
                h.setCustomNameVisible(true);
                Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, h.getX(), h.getY() + 1, h.getZ(), 60, 0.5, 1, 0.5, 0.4);
                Chronicle.say(s, k, "— родился герой " + n + "!");
                HERO.put(k, h.getUuid());
                HERO_NAME.put(k, n);
                HERO_AGE.put(k, 0);
                HERO_REF.put(k, h);
            }
            case 7 -> {
                List<Civ.Building> bs = new ArrayList<>();
                for (Civ.Building b : c.buildings) if (b.done && b.kind != Civ.Kind.QUARRY && b.kind != Civ.Kind.FARM && b.kind != Civ.Kind.WELL) bs.add(b);
                if (bs.isEmpty() || !w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_FIRE_TICK)) {
                    fire(s, k, 2);
                    return;
                }
                Civ.Building b = bs.get(r.nextInt(bs.size()));
                for (int i = 0; i < 4; i++) {
                    int x = b.x0 + r.nextInt(b.x1 - b.x0 + 1), z = b.z0 + r.nextInt(b.z1 - b.z0 + 1);
                    BlockPos p = new BlockPos(x, w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z), z);
                    if (w.getBlockState(p).isAir()) w.setBlockState(p, Blocks.FIRE.getDefaultState(), 3);
                }
                Chronicle.say(s, k, "— пожар! Горит " + b.kind.title.toLowerCase());
                start(k, Cond.REBUILD, Config.i("events.rebuild.length", 3));
            }
            default -> {
                c.ore += 5;
                c.faith = Math.min(999, c.faith + 20);
                Fx.particles(w, ParticleTypes.ENCHANT, cap.getX() + 0.5, cap.getY() + 2, cap.getZ() + 0.5, 120, 3, 2, 3, 1);
                Chronicle.say(s, k, "— мудрецы сделали великое открытие");
            }
        }
    }

    public static void clear() {
        timer = 0;
        reset();
    }
}
