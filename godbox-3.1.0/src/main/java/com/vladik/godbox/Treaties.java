package com.vladik.godbox;

import com.vladik.godbox.Kingdoms.K;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * v3.1: мирные договоры и королевские браки. Война ведёт счёт потерь и захватов, мир заключается на условиях
 * (дань, контрибуция, уступка города, вассалитет, перемирие); браки дают права на трон — унию или войну за наследство.
 */
public final class Treaties {
    public static boolean on = true;
    /** Пока идёт личная уния, Conquest.annex не пишет «захватило». */
    static boolean union;
    static final String CLAIM = "gbclaim:";
    private static final int N = K.values().length;
    private static final String[] PLACES = {"Старом Броду", "Белокамне", "Ольховой Роще", "Трёх Дубах", "Красном Холме",
        "Тихой Заводи", "Вороньем Камне", "Медвежьем Логу", "Соколиной Горе", "Ясном Поле", "Сером Мосту", "Золотом Ключе",
        "Волчьей Пади", "Дальнем Плёсе", "Чёрном Бору", "Светлом Яру"};

    /** Счёт войны пары; A — королевство с меньшим ordinal. */
    static final class War {
        int start, lossA, lossB, townsA, townsB;
    }

    static final class Marriage {
        K a, b;
        int ha, hb, since;
        String ua = "-", ub = "-";

        K other(K k) { return k == a ? b : a; }

        int house(K k) { return k == a ? ha : hb; }

        String ruler(K k) { return k == a ? ua : ub; }
    }

    private static final War[][] WARS = new War[N][N];
    /** Перемирие/пакт о ненападении: до какого тика (симметрично). */
    private static final int[][] TRUCE = new int[N][N];
    /** Дань: [плательщик][получатель] — до какого тика и доля дохода в процентах. */
    private static final int[][] PAY = new int[N][N];
    private static final int[][] PCT = new int[N][N];
    private static final Map<K, Integer> EXH = new EnumMap<>(K.class);
    private static final Map<K, Integer> PREV = new EnumMap<>(K.class);
    private static final List<Marriage> MARRIAGES = new ArrayList<>();
    private static int t;

    static {
        SaveExt.register("treaties", Treaties::save, Treaties::load, Treaties::reset);
    }

    private Treaties() {}

    /** Загружает класс, чтобы расширение сохранения зарегистрировалось до чтения файла. */
    public static void init() {}

    // ---------------- состояние ----------------
    private static War war(K a, K b) {
        return WARS[Math.min(a.ordinal(), b.ordinal())][Math.max(a.ordinal(), b.ordinal())];
    }

    static int exhaustion(K k) {
        return EXH.getOrDefault(k, 0);
    }

    private static void tire(K k, int n) {
        EXH.put(k, MathHelper.clamp(exhaustion(k) + n, 0, 100));
    }

    /** Действует ли перемирие (или пакт о ненападении) между a и b. */
    public static boolean truce(K a, K b) {
        return on && a != null && b != null && a != b && TRUCE[a.ordinal()][b.ordinal()] > t;
    }

    private static void setTruce(K a, K b, int until) {
        TRUCE[a.ordinal()][b.ordinal()] = TRUCE[b.ordinal()][a.ordinal()] = until;
    }

    private static int mins(int ticks) {
        return Math.max(1, ticks / 1200);
    }

    private static boolean ticking(K k) {
        return k.alive && k.world != null && k.capital != null && k.world.shouldTickEntity(k.capital);
    }

    private static int points(War w, boolean sideA, K enemy) {
        return (sideA ? w.lossB : w.lossA) * 2 + (sideA ? w.townsA : w.townsB) * 12 + exhaustion(enemy) / 5;
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        if (s.getTickManager().isFrozen()) return;
        if (!Perf.go) return;
        t++;
        if (!on) return;
        if (t % 20 == 0) sync();
        if (t % 100 == 0) losses();
        if (t % 600 == 0) {
            weariness(s);
            expire(s);
            marriages(s);
        }
    }

    /** Заводит счёт для новых войн и убирает счёт войн, закончившихся без договора. */
    private static void sync() {
        K[] ks = K.values();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                boolean h = ks[i].alive && ks[j].alive && Diplomacy.hostile(ks[i], ks[j]);
                if (h && WARS[i][j] == null) {
                    War w = new War();
                    w.start = t;
                    WARS[i][j] = w;
                    // война, начатая богом или сюзереном, отменяет перемирие и дань
                    TRUCE[i][j] = TRUCE[j][i] = 0;
                    PAY[i][j] = PAY[j][i] = 0;
                } else if (!h && WARS[i][j] != null) {
                    WARS[i][j] = null;
                }
            }
        }
    }

    /** Потери оцениваются по убыли населения воюющего королевства. */
    private static void losses() {
        for (K k : K.values()) {
            if (!ticking(k)) {
                PREV.remove(k);
                continue;
            }
            int n = Vassals.pop(k);
            Integer p = PREV.put(k, n);
            if (p == null || n >= p) continue;
            int d = p - n;
            if (p > 6 && d * 10 > p * 6) continue; // выгрузились чанки, а не погибли люди
            List<War> ws = new ArrayList<>();
            List<Boolean> side = new ArrayList<>();
            for (K o : K.values()) {
                if (o == k || !o.alive) continue;
                War w = war(k, o);
                if (w == null) continue;
                ws.add(w);
                side.add(k.ordinal() < o.ordinal());
            }
            if (ws.isEmpty()) continue;
            for (int i = 0; i < ws.size(); i++) {
                int part = d / ws.size() + (i < d % ws.size() ? 1 : 0);
                if (side.get(i)) ws.get(i).lossA += part;
                else ws.get(i).lossB += part;
            }
            tire(k, d * Config.i("treaties.exhaustionPerLoss", 2));
        }
    }

    /** Хук Conquest.takeTown: a взяло город у d. */
    static void captured(K a, K d) {
        if (!on || a == null || d == null || a == d) return;
        PREV.remove(d);
        War w = war(a, d);
        if (w == null) return;
        if (a.ordinal() < d.ordinal()) w.townsA++;
        else w.townsB++;
        tire(d, 8);
    }

    private static void weariness(MinecraftServer s) {
        for (K k : K.values()) {
            if (!k.alive) {
                EXH.remove(k);
                continue;
            }
            tire(k, Diplomacy.atWar(k) ? Config.i("treaties.exhaustionPerInterval", 2) : -4);
            int e = exhaustion(k);
            if (e >= 40) Unrest.add(k, e >= 70 ? 2 : 1);
        }
        if (!Diplomacy.auto || Kingdoms.eternalPeace) return;
        int limit = Config.i("treaties.exhaustionPeace", 80);
        K[] ks = K.values();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                War w = WARS[i][j];
                if (w == null || t - w.start < 2400 || !Diplomacy.hostile(ks[i], ks[j])) continue;
                if (Math.max(exhaustion(ks[i]), exhaustion(ks[j])) >= limit) conclude(s, ks[i], ks[j]);
            }
        }
    }

    private static void expire(MinecraftServer s) {
        K[] ks = K.values();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (TRUCE[i][j] != 0 && TRUCE[i][j] <= t) TRUCE[i][j] = 0;
                if (PAY[i][j] != 0 && PAY[i][j] <= t) {
                    PAY[i][j] = 0;
                    if (ks[i].alive && ks[j].alive) Chronicle.say(s, ks[i], "выплатило дань по договору — «" + ks[j].title + "» больше ничего не получает");
                }
            }
        }
    }

    // ---------------- мир на условиях ----------------
    /** Хук Diplomacy.think: вместо простого мира — договор. false — закон выключен, действует старое поведение. */
    static boolean peace(MinecraftServer s, K a, K b) {
        if (!on) return false;
        conclude(s, a, b);
        return true;
    }

    /** Хук силы «Мир»: все идущие войны завершаются договорами, затем сила действует как раньше. */
    static void godPeace(MinecraftServer s) {
        godPeaceTerms(s);
    }

    /** То же, но возвращает пары, между которыми только что заключён договор ([i][j], i < j). */
    static boolean[][] godPeaceTerms(MinecraftServer s) {
        boolean[][] done = new boolean[N][N];
        if (!on) return done;
        K[] ks = K.values();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                if (WARS[i][j] != null && ks[i].alive && ks[j].alive && Diplomacy.hostile(ks[i], ks[j])) {
                    conclude(s, ks[i], ks[j]);
                    done[i][j] = true;
                }
            }
        }
        return done;
    }

    private static boolean areaLoaded(ServerWorld w, BlockPos p, int r) {
        for (int dx = -r; dx <= r; dx += r) {
            for (int dz = -r; dz <= r; dz += r) if (!w.isChunkLoaded(p.add(dx, 0, dz))) return false;
        }
        return true;
    }

    /** Город проигравшего, ближайший к столице победителя (только в загруженной области). */
    private static BlockPos cedable(K win, K lose) {
        if (!Conquest.on || lose.world == null || win.capital == null) return null;
        List<BlockPos> towns = Conquest.TOWNS.get(lose);
        if (towns == null) return null;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : towns) {
            double d = p.getSquaredDistance(win.capital);
            if (d < bd && areaLoaded(lose.world, p, 45)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? " и " : ", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static void conclude(MinecraftServer s, K a, K b) {
        K x = a.ordinal() < b.ordinal() ? a : b, y = x == a ? b : a;
        int i = x.ordinal(), j = y.ordinal();
        War w = WARS[i][j];
        if (w == null) w = new War();
        WARS[i][j] = null;
        int sx = points(w, true, y), sy = points(w, false, x);
        int total = sx + sy;
        double ratio = total < 6 ? 0 : Math.abs(sx - sy) / (double) total;
        K win = sx >= sy ? x : y, lose = win == x ? y : x;
        int tier = ratio < Config.d("treaties.whiteMargin", 0.2) ? 0
            : ratio < Config.d("treaties.cessionMargin", 0.45) ? 1
            : ratio < Config.d("treaties.crushMargin", 0.7) ? 2 : 3;
        int trait = Life.kingTrait(win);
        if (tier > 0) {
            if (trait == 0) tier--; // миролюбивый победитель великодушен
            else if (trait == 1 && tier < 3) tier++; // воинственный требует большего
        }
        double mult = 1;
        if (trait == 2 && tier == 2) { // торговец берёт золотом, а не землёй
            tier = 1;
            mult = 1.5;
        }
        Random r = win.world != null ? win.world.getRandom() : Random.create();
        String place = PLACES[r.nextInt(PLACES.length)];
        int truce = Config.i("treaties.truceTicks", 24000);
        Diplomacy.set(x, y, Diplomacy.PEACE);

        if (tier == 0) {
            Diplomacy.OP[i][j] = Diplomacy.OP[j][i] = 0;
            truce /= 2;
            setTruce(x, y, t + truce);
            finish(x, y);
            Diplomacy.broadcast(s, Text.literal("📜 Перемирие в " + place + ": " + x.title + " и " + y.title
                + " расходятся без победителя — белый мир. Перемирие: " + mins(truce) + " мин").formatted(Formatting.WHITE));
            return;
        }

        List<String> parts = new ArrayList<>();
        boolean vassal = false;
        if (tier == 3) {
            if (Vassals.on && !Vassals.LORD.containsKey(lose) && Vassals.LORD.get(win) != lose) vassal = true;
            else tier = 2;
        }
        BlockPos town = tier == 2 ? cedable(win, lose) : null;
        if (tier == 2 && town == null) mult *= 1.5;
        if (!vassal) {
            if (Economy.on) {
                int want = (int) ((Config.i("treaties.goldBase", 40) + ratio * Config.i("treaties.goldScale", 160)) * mult);
                int paid = Math.min(want, Economy.gold(lose));
                if (paid > 0) {
                    Economy.add(lose, -paid);
                    Economy.add(win, paid);
                    parts.add("выплачивает " + paid + " золота");
                }
                int pct = MathHelper.clamp((int) (Config.i("treaties.tributeShare", 25) * mult), 0, 60);
                int len = (int) (Config.i("treaties.tributeTicks", 12000) * mult);
                if (pct > 0 && len > 0) {
                    PAY[lose.ordinal()][win.ordinal()] = t + len;
                    PCT[lose.ordinal()][win.ordinal()] = pct;
                    PAY[win.ordinal()][lose.ordinal()] = 0;
                    parts.add("отдаёт " + pct + "% дохода (" + mins(len) + " мин)");
                }
            } else if (lose.alive && win.alive) {
                Civ.City cl = Civ.city(lose), cw = Civ.city(win);
                double share = Math.min(0.5, (0.2 + ratio * 0.3) * mult);
                int wd = (int) (cl.wood * share), st = (int) (cl.stone * share), fd = (int) (cl.food * share), or = (int) (cl.ore * share);
                cl.wood -= wd; cw.wood += wd;
                cl.stone -= st; cw.stone += st;
                cl.food -= fd; cw.food += fd;
                cl.ore -= or; cw.ore += or;
                if (wd + st + fd + or > 0) parts.add("выплачивает контрибуцию со складов (дерево " + wd + ", камень " + st + ", еда " + fd + ", руда " + or + ")");
            }
            if (town != null) {
                Conquest.takeTown(s, win, lose, town);
                parts.add("уступает город «" + Colonies.name(town) + "»");
            }
            int o = tier == 2 ? -25 : -15;
            Diplomacy.OP[i][j] = Diplomacy.OP[j][i] = o;
        } else {
            Vassals.submit(s, lose, win);
            parts.add("становится вассалом");
        }
        setTruce(x, y, t + truce);
        finish(x, y);
        String body = parts.isEmpty() ? "признаёт поражение" : join(parts);
        Diplomacy.broadcast(s, Text.literal("📜 " + (vassal ? "Капитуляция в " : "Мир в ") + place + ": " + win.title + " побеждает; "
            + lose.title + " " + body + ". Перемирие: " + mins(truce) + " мин").formatted(win.color));
    }

    private static void finish(K x, K y) {
        EXH.put(x, exhaustion(x) / 2);
        EXH.put(y, exhaustion(y) / 2);
        Kingdoms.calm();
    }

    /** Хук Economy: плательщик дани отдаёт долю дохода получателям. */
    static int income(K k, int income) {
        if (!on || income <= 0) return income;
        int i = k.ordinal(), out = income;
        for (K o : K.values()) {
            int j = o.ordinal();
            if (PAY[i][j] <= t || !o.alive) continue;
            int cut = Math.min(out, income * PCT[i][j] / 100);
            if (cut <= 0) continue;
            out -= cut;
            Economy.add(o, cut);
        }
        return out;
    }

    // ---------------- браки ----------------
    private static List<Marriage> marriagesOf(K k) {
        List<Marriage> out = new ArrayList<>();
        for (Marriage m : MARRIAGES) if (m.a == k || m.b == k) out.add(m);
        return out;
    }

    private static boolean married(K a, K b) {
        for (Marriage m : MARRIAGES) if ((m.a == a && m.b == b) || (m.a == b && m.b == a)) return true;
        return false;
    }

    private static boolean living(K k, String uuid) {
        for (MobEntity m : k.members) if (m.isAlive() && m.getUuidAsString().equals(uuid)) return true;
        return false;
    }

    /** Есть ли в королевстве in потомок с правами на дом house королевства other. */
    private static boolean claimant(K in, K other, int house) {
        String want = other.name() + ":" + house;
        for (MobEntity m : in.members) if (m.isAlive() && want.equals(Life.tag(m, CLAIM))) return true;
        return false;
    }

    private static void flourish(K k) {
        ServerWorld w = k.world;
        BlockPos c = k.capital;
        if (w == null || c == null || !w.isChunkLoaded(c)) return;
        Fx.particles(w, ParticleTypes.FIREWORK, c.getX() + 0.5, c.getY() + 7, c.getZ() + 0.5, 90, 4, 3, 4, 0.2);
        Fx.particles(w, ParticleTypes.HEART, c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 20, 3, 1.5, 3, 0.02);
        Fx.sound(w, c.getX(), c.getY(), c.getZ(), SoundEvents.BLOCK_BELL_USE, 4f, 1.2f);
        Fx.sound(w, c.getX(), c.getY(), c.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 3f, 1f);
    }

    /** @return null при успехе, иначе причина отказа */
    private static String marry(MinecraftServer s, K a, K b) {
        MobEntity ka = Kings.king(a), kb = Kings.king(b);
        if (ka == null) return "У королевства «" + a.title + "» нет правителя";
        if (kb == null) return "У королевства «" + b.title + "» нет правителя";
        if (married(a, b)) return a.title + " и " + b.title + " уже связаны браком";
        Dynasty.House ha = Dynasty.house(a), hb = Dynasty.house(b);
        if (ha.id == 0 || hb.id == 0) return "Правящие дома ещё не основаны";
        Marriage m = new Marriage();
        m.a = a;
        m.b = b;
        m.ha = ha.id;
        m.hb = hb.id;
        m.ua = ka.getUuidAsString();
        m.ub = kb.getUuidAsString();
        m.since = t;
        MARRIAGES.add(m);
        if (Diplomacy.hostile(a, b)) {
            Diplomacy.set(a, b, Diplomacy.PEACE);
            WARS[Math.min(a.ordinal(), b.ordinal())][Math.max(a.ordinal(), b.ordinal())] = null;
            Kingdoms.calm();
        }
        Diplomacy.op(a, b, Config.i("treaties.marriageOpinion", 40));
        boolean ally = Diplomacy.allied(a, b);
        if (!ally && Diplomacy.OP[a.ordinal()][b.ordinal()] >= 50) {
            Diplomacy.set(a, b, Diplomacy.ALLY);
            ally = true;
        }
        int pact = Config.i("treaties.truceTicks", 24000) * 2;
        if (!ally) setTruce(a, b, Math.max(TRUCE[a.ordinal()][b.ordinal()], t + pact));
        flourish(a);
        flourish(b);
        Diplomacy.broadcast(s, Text.literal("💍 Королевский брак: дом " + ha.name + " («" + a.title + "») и дом " + hb.name + " («" + b.title
            + "») породнились — " + (ally ? "заключён союз" : "заключён пакт о ненападении на " + mins(pact) + " мин")
            + ", их дети унаследуют права на оба трона").formatted(Formatting.LIGHT_PURPLE));
        return null;
    }

    private static void marriages(MinecraftServer s) {
        for (Marriage m : new ArrayList<>(MARRIAGES)) {
            if (!m.a.alive || !m.b.alive) {
                MARRIAGES.remove(m);
                continue;
            }
            if (Dynasty.house(m.a).id != m.ha || Dynasty.house(m.b).id != m.hb) {
                MARRIAGES.remove(m);
                Chronicle.say(s, m.a, "династия сменилась — брачный союз с «" + m.b.title + "» расторгнут");
                continue;
            }
            if (!ticking(m.a) || !ticking(m.b)) continue;
            if (living(m.a, m.ua) || living(m.b, m.ub) || claimant(m.a, m.b, m.hb) || claimant(m.b, m.a, m.ha)) continue;
            MARRIAGES.remove(m);
            Chronicle.say(s, m.a, "брачный союз с «" + m.b.title + "» угас: супруги умерли, общих наследников нет");
        }
        if (!Diplomacy.auto) return;
        K[] ks = K.values();
        int minOp = Config.i("treaties.marriageMinOpinion", 25), base = Config.i("treaties.marriageChance", 4);
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                K a = ks[i], b = ks[j];
                if (!ticking(a) || !ticking(b) || a.world != b.world || Diplomacy.hostile(a, b)) continue;
                if (Diplomacy.OP[i][j] < minOp || married(a, b) || marriagesOf(a).size() >= 2 || marriagesOf(b).size() >= 2) continue;
                if (Kings.king(a) == null || Kings.king(b) == null) continue;
                int chance = base;
                int ta = Life.kingTrait(a), tb = Life.kingTrait(b);
                if (ta == 0 || ta == 2) chance += 3;
                if (tb == 0 || tb == 2) chance += 3;
                if (ta == 1) chance -= 2;
                if (tb == 1) chance -= 2;
                int ra = Civ.city(a).religion, rb = Civ.city(b).religion;
                if (ra >= 0 && ra == rb) chance += 3;
                int pa = Vassals.pop(a), pb = Vassals.pop(b);
                if (pa * 2 <= pb * 3 && pb * 2 <= pa * 3) chance += 2;
                if (a.world.getRandom().nextInt(100) >= chance) continue;
                if (marry(s, a, b) == null) return; // не больше одной свадьбы за раз
            }
        }
    }

    /** Хук Dynasty.born: королевское дитя получает права и на дом супругов. */
    static void born(K k, VillagerEntity child) {
        if (!on || k == null) return;
        int hk = Dynasty.house(k).id;
        for (Marriage m : MARRIAGES) {
            if ((m.a != k && m.b != k) || m.house(k) != hk) continue;
            K o = m.other(k);
            if (!o.alive || Dynasty.house(o).id != m.house(o)) continue;
            Life.setTag(child, CLAIM, o.name() + ":" + m.house(o));
            if (k.world != null) Civ.tell(k.world, Text.literal("💍 " + k.title + ": " + Dynasty.first(child) + " несёт кровь двух домов — "
                + Dynasty.houseName(k) + " и " + Dynasty.houseName(o)).formatted(Formatting.LIGHT_PURPLE));
            return;
        }
    }

    /**
     * Хук Kings.tick: правитель умер, своих наследников нет.
     * @return true, если трон унаследовал дом супругов и королевство вошло в личную унию
     */
    static boolean succession(MinecraftServer s, K k, ServerWorld w) {
        if (!on) return false;
        int hk = Dynasty.house(k).id;
        List<K> claim = new ArrayList<>();
        List<Marriage> mine = marriagesOf(k);
        for (Marriage m : mine) {
            K p = m.other(k);
            if (!p.alive || p.world != w || m.house(k) != hk || Dynasty.house(p).id != m.house(p)) continue;
            MobEntity pk = Kings.king(p);
            if (pk == null) continue;
            if (pk.getUuidAsString().equals(m.ruler(p)) || claimant(p, k, hk)) claim.add(p);
        }
        if (claim.isEmpty()) return false;
        if (claim.size() >= 2) {
            K c1 = claim.get(0), c2 = claim.get(1);
            Diplomacy.broadcast(s, Text.literal("⚔ Война за наследство: дома " + Dynasty.houseName(c1) + " и " + Dynasty.houseName(c2)
                + " оспаривают опустевший трон «" + k.title + "»").formatted(Formatting.RED, Formatting.BOLD));
            setTruce(c1, c2, 0);
            Diplomacy.declare(s, c1, c2);
            return false;
        }
        K p = claim.get(0);
        MobEntity pk = Kings.king(p);
        int accept = MathHelper.clamp(Config.i("treaties.unionAccept", 65) + Diplomacy.OP[k.ordinal()][p.ordinal()] / 2 - Unrest.unrest(k) / 3, 10, 95);
        if (ticking(k) && w.getRandom().nextInt(100) < accept) {
            String msg = "👑 Личная уния: трон «" + k.title + "» наследует " + Dynasty.first(pk) + " из дома " + Dynasty.houseName(p)
                + " — королевство мирно объединяется с «" + p.title + "»";
            boolean ok;
            union = true;
            try {
                ok = Conquest.annex(s, p, k);
            } finally {
                union = false;
            }
            if (ok) {
                Diplomacy.broadcast(s, Text.literal(msg).formatted(p.color, Formatting.BOLD));
                flourish(p);
                return true;
            }
            return false;
        }
        Diplomacy.broadcast(s, Text.literal("⚔ Война за наследство: знать «" + k.title + "» отвергла права дома " + Dynasty.houseName(p)
            + " — «" + p.title + "» идёт добывать трон силой").formatted(Formatting.RED, Formatting.BOLD));
        for (Marriage m : mine) if (m.other(k) == p) MARRIAGES.remove(m);
        setTruce(k, p, 0);
        Diplomacy.declare(s, p, k);
        return false;
    }

    /** Сила бога: брак правящих домов двух ближайших королевств. */
    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        K a = null, b = null;
        double da = Double.MAX_VALUE, db = Double.MAX_VALUE;
        for (K k : K.values()) {
            if (!k.alive || k.world != w || k.capital == null) continue;
            double d = k.capital.getSquaredDistance(pos);
            if (d < da) {
                b = a;
                db = da;
                a = k;
                da = d;
            } else if (d < db) {
                b = k;
                db = d;
            }
        }
        if (a == null || b == null) {
            god.sendMessage(Text.literal("Для брака нужно два королевства").formatted(Formatting.RED), true);
            return;
        }
        if (!on) {
            god.sendMessage(Text.literal("Закон «Договоры и браки» выключен").formatted(Formatting.YELLOW), true);
            return;
        }
        String err = marry(w.getServer(), a, b);
        if (err != null) god.sendMessage(Text.literal(err).formatted(Formatting.YELLOW), true);
    }

    // ---------------- сведения ----------------
    /** Строки для статистики: войны, перемирия, дань, браки. */
    public static List<String> describe(K k) {
        List<String> out = new ArrayList<>();
        if (!on || !k.alive) return out;
        int i = k.ordinal();
        int e = exhaustion(k);
        if (e > 0) out.add("усталость от войны " + e + "%");
        for (K o : K.values()) {
            if (o == k || !o.alive) continue;
            int j = o.ordinal();
            War w = war(k, o);
            if (w != null) {
                boolean sideA = i < j;
                out.add("война с «" + o.title + "»: счёт " + points(w, sideA, o) + ":" + points(w, !sideA, k));
            }
            if (TRUCE[i][j] > t) out.add("перемирие с «" + o.title + "» (ещё " + mins(TRUCE[i][j] - t) + " мин)");
            if (PAY[i][j] > t) out.add("платит дань «" + o.title + "»: " + PCT[i][j] + "% дохода (ещё " + mins(PAY[i][j] - t) + " мин)");
            if (PAY[j][i] > t) out.add("получает дань от «" + o.title + "»: " + PCT[j][i] + "% дохода (ещё " + mins(PAY[j][i] - t) + " мин)");
        }
        for (Marriage m : MARRIAGES) {
            if (m.a != k && m.b != k) continue;
            K o = m.other(k);
            out.add("брак с домом " + Dynasty.houseName(o) + " («" + o.title + "»)");
        }
        return out;
    }

    /** Строка для окна статистики (пустая, если договоров нет). */
    static String stats(K k) {
        List<String> l = describe(k);
        return l.isEmpty() ? "" : "\u00a77Договоры: \u00a7f" + String.join("\u00a77; \u00a7f", l) + "\n";
    }

    /** Дополнение к сводке силы «Дипломатия». */
    static void info(MutableText out) {
        if (!on) return;
        for (K k : K.values()) {
            List<String> l = describe(k);
            if (!l.isEmpty()) out.append(Text.literal("\n  📜 " + k.title + ": " + String.join("; ", l)).formatted(k.color));
        }
    }

    /** Хук Kingdoms.disbandKingdom: королевства больше нет — договоры и браки с ним недействительны. */
    static void forget(K k) {
        int i = k.ordinal();
        for (int j = 0; j < N; j++) {
            WARS[Math.min(i, j)][Math.max(i, j)] = null;
            TRUCE[i][j] = TRUCE[j][i] = 0;
            PAY[i][j] = PAY[j][i] = 0;
            PCT[i][j] = PCT[j][i] = 0;
        }
        EXH.remove(k);
        PREV.remove(k);
        MARRIAGES.removeIf(m -> m.a == k || m.b == k);
    }

    // ---------------- сохранение ----------------
    static List<String> save() {
        List<String> o = new ArrayList<>();
        o.add("S|" + on + "|" + t);
        K[] ks = K.values();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                War w = i < j ? WARS[i][j] : null;
                if (w != null) o.add("W|" + ks[i].name() + "|" + ks[j].name() + "|" + w.start + "|" + w.lossA + "|" + w.lossB + "|" + w.townsA + "|" + w.townsB);
                if (i < j && TRUCE[i][j] > t) o.add("T|" + ks[i].name() + "|" + ks[j].name() + "|" + TRUCE[i][j]);
                if (PAY[i][j] > t) o.add("P|" + ks[i].name() + "|" + ks[j].name() + "|" + PAY[i][j] + "|" + PCT[i][j]);
            }
        }
        for (Map.Entry<K, Integer> e : EXH.entrySet()) if (e.getValue() > 0) o.add("X|" + e.getKey().name() + "|" + e.getValue());
        for (Marriage m : MARRIAGES) o.add("M|" + m.a.name() + "|" + m.b.name() + "|" + m.ha + "|" + m.hb + "|" + m.ua + "|" + m.ub + "|" + m.since);
        return o;
    }

    static void load(String[] a) {
        try {
            switch (a[0]) {
                case "S" -> {
                    on = Boolean.parseBoolean(a[1]);
                    t = Integer.parseInt(a[2]);
                }
                case "W" -> {
                    K x = K.valueOf(a[1]), y = K.valueOf(a[2]);
                    if (x.ordinal() >= y.ordinal()) return;
                    War w = new War();
                    w.start = Integer.parseInt(a[3]);
                    w.lossA = Integer.parseInt(a[4]);
                    w.lossB = Integer.parseInt(a[5]);
                    w.townsA = Integer.parseInt(a[6]);
                    w.townsB = Integer.parseInt(a[7]);
                    WARS[x.ordinal()][y.ordinal()] = w;
                }
                case "T" -> {
                    K x = K.valueOf(a[1]), y = K.valueOf(a[2]);
                    if (x != y) setTruce(x, y, Integer.parseInt(a[3]));
                }
                case "P" -> {
                    K x = K.valueOf(a[1]), y = K.valueOf(a[2]);
                    if (x == y) return;
                    PAY[x.ordinal()][y.ordinal()] = Integer.parseInt(a[3]);
                    PCT[x.ordinal()][y.ordinal()] = MathHelper.clamp(Integer.parseInt(a[4]), 0, 100);
                }
                case "X" -> EXH.put(K.valueOf(a[1]), MathHelper.clamp(Integer.parseInt(a[2]), 0, 100));
                case "M" -> {
                    Marriage m = new Marriage();
                    m.a = K.valueOf(a[1]);
                    m.b = K.valueOf(a[2]);
                    if (m.a == m.b) return;
                    m.ha = Integer.parseInt(a[3]);
                    m.hb = Integer.parseInt(a[4]);
                    m.ua = a[5];
                    m.ub = a[6];
                    m.since = Integer.parseInt(a[7]);
                    MARRIAGES.add(m);
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void reset() {
        clear();
        on = true;
    }

    public static void clear() {
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                WARS[i][j] = null;
                TRUCE[i][j] = 0;
                PAY[i][j] = 0;
                PCT[i][j] = 0;
            }
        }
        EXH.clear();
        PREV.clear();
        MARRIAGES.clear();
        t = 0;
    }
}
