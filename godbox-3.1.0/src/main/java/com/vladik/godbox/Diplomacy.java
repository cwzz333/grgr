package com.vladik.godbox;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.TraderLlamaEntity;
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
import java.util.Iterator;
import java.util.List;

/** v1.6: отношения, союзы, войны между парами королевств и торговые караваны. */
public final class Diplomacy {
    public static final int PEACE = 0, WAR = 1, ALLY = 2;
    public static boolean auto = true;
    private static final int N = Kingdoms.K.values().length;
    static final int[][] REL = new int[N][N];
    static final int[][] OP = new int[N][N];
    private static final int[][] TIME = new int[N][N];
    private static final List<Caravan> CARAVANS = new ArrayList<>();
    static final String[] RES = {"дерево", "камень", "еда", "руда"};
    private static int timer;

    static final class Caravan {
        final TraderLlamaEntity e;
        final Kingdoms.K from, to;
        final int type, amount;
        int age, stuck;
        double best = 1e9;

        Caravan(TraderLlamaEntity e, Kingdoms.K from, Kingdoms.K to, int type, int amount) {
            this.e = e;
            this.from = from;
            this.to = to;
            this.type = type;
            this.amount = amount;
        }
    }

    private Diplomacy() {}

    // ---------------- состояние ----------------
    public static int rel(Kingdoms.K a, Kingdoms.K b) {
        return a == b ? ALLY : REL[a.ordinal()][b.ordinal()];
    }

    public static boolean hostile(Kingdoms.K a, Kingdoms.K b) {
        return a != null && b != null && a != b && REL[a.ordinal()][b.ordinal()] == WAR;
    }

    public static boolean allied(Kingdoms.K a, Kingdoms.K b) {
        return a != null && b != null && a != b && REL[a.ordinal()][b.ordinal()] == ALLY;
    }

    public static boolean atWar(Kingdoms.K k) {
        for (Kingdoms.K o : Kingdoms.K.values()) if (o.alive && hostile(k, o)) return true;
        return false;
    }

    public static boolean anyWar() {
        for (Kingdoms.K k : Kingdoms.K.values()) if (k.alive && atWar(k)) return true;
        return false;
    }

    static void set(Kingdoms.K a, Kingdoms.K b, int r) {
        int i = a.ordinal(), j = b.ordinal();
        REL[i][j] = REL[j][i] = r;
        TIME[i][j] = TIME[j][i] = 0;
    }

    static void op(Kingdoms.K a, Kingdoms.K b, int d) {
        int i = a.ordinal(), j = b.ordinal();
        OP[i][j] = OP[j][i] = MathHelper.clamp(OP[i][j] + d, -100, 100);
    }

    public static void setAll(boolean war) {
        for (Kingdoms.K a : Kingdoms.K.values()) {
            for (Kingdoms.K b : Kingdoms.K.values()) {
                if (a.ordinal() >= b.ordinal()) continue;
                set(a, b, war ? WAR : PEACE);
                if (!war) OP[a.ordinal()][b.ordinal()] = OP[b.ordinal()][a.ordinal()] = 0;
            }
        }
    }

    public static void forget(Kingdoms.K k) {
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k) continue;
            set(k, o, PEACE);
            OP[k.ordinal()][o.ordinal()] = OP[o.ordinal()][k.ordinal()] = 0;
        }
    }

    static void broadcast(MinecraftServer s, Text t) {
        Chronicle.log(t.getString());
        if (s == null) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) p.sendMessage(t, false);
    }

    static void declare(MinecraftServer s, Kingdoms.K a, Kingdoms.K b) {
        if (Kingdoms.eternalPeace || hostile(a, b) || Treaties.truce(a, b)) return;
        set(a, b, WAR);
        Kingdoms.war = true;
        broadcast(s, Text.literal("⚔ " + a.title + " объявляет войну: " + b.title + "!").formatted(Formatting.RED, Formatting.BOLD));
        for (Kingdoms.K c : Kingdoms.K.values()) {
            if (!c.alive || c == a || c == b) continue;
            if (allied(c, a) && !allied(c, b) && !hostile(c, b)) {
                set(c, b, WAR);
                broadcast(s, Text.literal("🛡 " + c.title + " вступает в войну на стороне союзника").formatted(c.color));
            } else if (allied(c, b) && !allied(c, a) && !hostile(c, a)) {
                set(c, a, WAR);
                broadcast(s, Text.literal("🛡 " + c.title + " защищает союзника: " + b.title).formatted(c.color));
            }
        }
    }

    static int mood(Kingdoms.K k) {
        return switch (Life.kingTrait(k)) {
            case 0 -> 2;
            case 1 -> -3;
            case 2 -> 1;
            default -> 0;
        };
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        timer++;
        if (timer % 20 == 0) {
            if (Kingdoms.eternalPeace && anyWar()) {
                setAll(false);
                Kingdoms.calm();
            }
            Kingdoms.war = anyWar();
            tickCaravans();
        }
        if (timer % 600 == 0 && auto && !Kingdoms.eternalPeace) think(s);
        if (timer % Seasons.tradePeriod() == 300 && Civ.economy) trade();
    }

    static void think(MinecraftServer s) {
        Kingdoms.K[] ks = Kingdoms.K.values();
        for (int i = 0; i < ks.length; i++) {
            for (int j = i + 1; j < ks.length; j++) {
                Kingdoms.K a = ks[i], b = ks[j];
                if (!a.alive || !b.alive || a.world == null || a.world != b.world || a.capital == null || b.capital == null) continue;
                Random r = a.world.getRandom();
                double dist = Math.sqrt(a.capital.getSquaredDistance(b.capital));
                int delta = r.nextInt(13) - 6 + (dist < 140 ? -3 : 1) + mood(a) + mood(b) + Races.opinion(a, b);
                int pa = a.members.size(), pb = b.members.size();
                if (pa > pb * 2 + 4 || pb > pa * 2 + 4) delta -= 2;
                int rel = REL[i][j];
                TIME[i][j]++;
                TIME[j][i]++;
                if (rel == ALLY) delta += 2;
                op(a, b, delta);
                int o = OP[i][j];
                if (rel == PEACE) {
                    if (o <= -55 - Seasons.warBias() && r.nextInt(3) == 0) {
                        declare(s, a, b);
                    } else if (o >= 55 && r.nextInt(3) == 0) {
                        set(a, b, ALLY);
                        broadcast(s, Text.literal("🤝 " + a.title + " и " + b.title + " заключили союз!").formatted(Formatting.GREEN, Formatting.BOLD));
                    }
                } else if (rel == ALLY) {
                    if (o < 5) {
                        set(a, b, PEACE);
                        broadcast(s, Text.literal("💔 Союз " + a.title + " и " + b.title + " распался").formatted(Formatting.GRAY));
                    }
                } else {
                    int t = TIME[i][j];
                    if (t >= 6 && r.nextInt(100) < 10 + t * 3) {
                        if (Treaties.peace(s, a, b)) continue;
                        set(a, b, PEACE);
                        OP[i][j] = OP[j][i] = -10;
                        Kingdoms.calm();
                        broadcast(s, Text.literal("🕊 " + a.title + " и " + b.title + " подписали мирный договор").formatted(Formatting.WHITE));
                    }
                }
            }
        }
    }

    // ---------------- торговля и караваны ----------------
    static int get(Civ.City c, int t) {
        return switch (t) {
            case 0 -> c.wood;
            case 1 -> c.stone;
            case 2 -> c.food;
            default -> c.ore;
        };
    }

    static void add(Civ.City c, int t, int n) {
        switch (t) {
            case 0 -> c.wood = Math.max(0, c.wood + n);
            case 1 -> c.stone = Math.max(0, c.stone + n);
            case 2 -> c.food = Math.max(0, c.food + n);
            default -> c.ore = Math.max(0, c.ore + n);
        }
    }

    static boolean busy(Kingdoms.K a, Kingdoms.K b) {
        for (Caravan c : CARAVANS) if (c.from == a && c.to == b) return true;
        return false;
    }

    static boolean canTrade(Kingdoms.K a, Kingdoms.K b) {
        return a != b && a.alive && b.alive && a.world != null && a.world == b.world && a.capital != null && b.capital != null
            && !hostile(a, b) && a.capital.getSquaredDistance(b.capital) < 450 * 450
            && a.world.shouldTickEntity(a.capital) && a.world.shouldTickEntity(b.capital);
    }

    /** Выбрать товар: то, чего у a много, а у b мало. -1 — нечем торговать. */
    static int pick(Civ.City ca, Civ.City cb, boolean force) {
        int best = -1, bv = force ? Integer.MIN_VALUE : 0;
        for (int t = 0; t < 4; t++) {
            int have = get(ca, t), min = t == 3 ? 4 : 30;
            if (have < (force ? (t == 3 ? 2 : 8) : min)) continue;
            int v = have - get(cb, t) * (t == 3 ? 1 : 1);
            if (v > bv) {
                bv = v;
                best = t;
            }
        }
        return best;
    }

    static void trade() {
        if (CARAVANS.size() >= 6) return;
        for (Kingdoms.K a : Kingdoms.K.values()) {
            for (Kingdoms.K b : Kingdoms.K.values()) {
                if (!canTrade(a, b) || busy(a, b) || Plague.blocked(a, b)) continue;
                Random r = a.world.getRandom();
                int chance = (allied(a, b) ? 70 : 40) + (Life.kingTrait(a) == 2 ? 25 : 0);
                if (r.nextInt(100) >= chance) continue;
                Civ.City ca = Civ.city(a), cb = Civ.city(b);
                int t = pick(ca, cb, false);
                if (t < 0) continue;
                send(a, b, t);
            }
        }
    }

    static boolean send(Kingdoms.K a, Kingdoms.K b, int t) {
        ServerWorld w = a.world;
        Civ.City ca = Civ.city(a);
        int have = get(ca, t);
        int amount = t == 3 ? Math.max(1, have / 3) : Math.max(4, Math.min(30, have / 3));
        if (have < amount) return false;
        BlockPos at = Civ.ground(w, a.capital.getX() + 3, a.capital.getZ() + 3);
        TraderLlamaEntity e = EntityType.TRADER_LLAMA.create(w);
        if (e == null) return false;
        e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        e.setHasChest(true);
        e.setTame(true);
        e.setPersistent();
        e.setCustomName(Text.literal("Караван → " + b.title + " (" + RES[t] + " " + amount + ")").formatted(a.color));
        e.setCustomNameVisible(true);
        w.spawnEntity(e);
        add(ca, t, -amount);
        CARAVANS.add(new Caravan(e, a, b, t, amount));
        Plague.depart(a, b);
        Fx.sound(w, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_LLAMA_CHEST, 1f, 1f);
        Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1, e.getZ(), 8, 0.5, 0.5, 0.5, 0.02);
        Civ.tell(w, Text.literal("🐪 " + a.title + " отправило караван в " + b.title + ": " + RES[t] + " " + amount).formatted(a.color));
        return true;
    }

    static void deliver(Caravan c) {
        if (!c.to.alive) return;
        Civ.City ct = Civ.city(c.to);
        add(ct, c.type, c.amount);
        if (c.from.alive) add(Civ.city(c.from), 3, 1 + c.amount / 10);
        op(c.from, c.to, 6);
        Tech.caravan(c.from, c.to);
        Plague.arrive(c.from, c.to, 0.6, "с караваном из");
        ServerWorld w = c.to.world;
        if (w != null) {
            TraderLlamaEntity e = c.e;
            Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1, e.getZ(), 16, 0.6, 0.6, 0.6, 0.02);
            Fx.sound(w, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_VILLAGER_YES, 1f, 1f);
            Civ.tell(w, Text.literal("🐪 Караван доставил в " + c.to.title + ": +" + c.amount + " (" + RES[c.type] + ")").formatted(c.to.color));
        }
    }

    static void tickCaravans() {
        Iterator<Caravan> it = CARAVANS.iterator();
        while (it.hasNext()) {
            Caravan c = it.next();
            TraderLlamaEntity e = c.e;
            if (!c.to.alive || c.to.capital == null) {
                e.discard();
                it.remove();
                continue;
            }
            if (!e.isAlive()) {
                if (e.isDead()) {
                    op(c.from, c.to, -6);
                    if (c.from.world != null) Civ.tell(c.from.world, Text.literal("💰 Караван из " + c.from.title + " разграблен!").formatted(Formatting.GOLD));
                } else {
                    deliver(c);
                }
                it.remove();
                continue;
            }
            c.age += 20;
            double dx = c.to.capital.getX() + 0.5 - e.getX(), dz = c.to.capital.getZ() + 0.5 - e.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 6 || c.age > 7200) {
                deliver(c);
                e.discard();
                it.remove();
                continue;
            }
            ServerWorld w = (ServerWorld) e.getWorld();
            double step = Math.min(18, d);
            BlockPos g = Civ.ground(w, MathHelper.floor(e.getX() + dx / d * step), MathHelper.floor(e.getZ() + dz / d * step));
            e.getNavigation().startMovingTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 1.4);
            if (d < c.best - 0.5) {
                c.best = d;
                c.stuck = 0;
            } else if (++c.stuck >= 6) {
                c.stuck = 0;
                c.best = 1e9;
                Fx.particles(w, ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
                e.requestTeleport(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            }
        }
    }

    // ---------------- силы бога ----------------
    static Kingdoms.K nearest(ServerWorld w, BlockPos p) {
        Kingdoms.K best = null;
        double bd = 250.0 * 250.0;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world != w || k.capital == null) continue;
            double d = k.capital.getSquaredDistance(p);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    public static void sway(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int d) {
        Kingdoms.K k = nearest(w, pos);
        if (k == null) {
            god.sendMessage(Text.literal("Рядом нет королевств").formatted(Formatting.GRAY), true);
            return;
        }
        for (Kingdoms.K o : Kingdoms.K.values()) if (o.alive && o != k) op(k, o, d);
        BlockPos c = k.capital;
        Fx.particles(w, d > 0 ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER, c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 40, 4, 2, 4, 0.05);
        god.sendMessage(Text.literal(k.title + (d > 0 ? " теперь дружелюбнее к соседям" : " теперь ненавидит соседей")).formatted(k.color), true);
        if (auto) think(god.getServer());
    }

    public static void forceCaravan(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K a = nearest(w, pos);
        if (a == null) {
            god.sendMessage(Text.literal("Рядом нет королевств").formatted(Formatting.GRAY), true);
            return;
        }
        Kingdoms.K best = null;
        double bd = 1e18;
        for (Kingdoms.K b : Kingdoms.K.values()) {
            if (!canTrade(a, b)) continue;
            double d = a.capital.getSquaredDistance(b.capital);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        if (best == null) {
            god.sendMessage(Text.literal("Нет соседей для торговли (нужно второе королевство без войны)").formatted(Formatting.YELLOW), true);
            return;
        }
        int t = pick(Civ.city(a), Civ.city(best), true);
        if (t < 0 || !send(a, best, t)) {
            god.sendMessage(Text.literal(a.title + ": склад пуст, нечем торговать").formatted(Formatting.YELLOW), true);
        }
    }

    public static void info(ServerPlayerEntity god) {
        MutableText t = Text.literal("📜 Дипломатия (авто: " + (auto ? "вкл" : "выкл") + ", караванов в пути: " + CARAVANS.size() + ")").formatted(Formatting.GOLD);
        Kingdoms.K[] ks = Kingdoms.K.values();
        int alive = 0;
        for (Kingdoms.K k : ks) {
            if (!k.alive) continue;
            alive++;
            t.append(Text.literal("\n" + k.title + " — " + Life.describeKing(k)).formatted(k.color));
        }
        for (int i = 0; i < ks.length; i++) {
            for (int j = i + 1; j < ks.length; j++) {
                if (!ks[i].alive || !ks[j].alive) continue;
                int r = REL[i][j];
                String s = r == WAR ? "⚔ война" : r == ALLY ? "🤝 союз" : "мир";
                Formatting f = r == WAR ? Formatting.RED : r == ALLY ? Formatting.GREEN : Formatting.WHITE;
                t.append(Text.literal("\n  " + ks[i].title + " ↔ " + ks[j].title + ": " + s + " (отношение " + OP[i][j] + ")").formatted(f));
            }
        }
        Treaties.info(t);
        if (alive == 0) t.append(Text.literal("\nКоролевств пока нет").formatted(Formatting.GRAY));
        god.sendMessage(t, false);
    }

    public static void clear() {
        for (Caravan c : CARAVANS) if (c.e.isAlive()) c.e.discard();
        CARAVANS.clear();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                REL[i][j] = PEACE;
                OP[i][j] = 0;
                TIME[i][j] = 0;
            }
        }
        timer = 0;
    }
}
