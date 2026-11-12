package com.vladik.godbox;

import com.vladik.godbox.Kingdoms.K;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Захват столиц и городов, рост территории королевств (как в WorldBox). */
public final class Conquest {
    public static boolean on = true;
    static final Map<K, List<BlockPos>> TOWNS = new EnumMap<>(K.class);
    private static final Map<K, Integer> SIEGE = new EnumMap<>(K.class);
    private static final Map<K, BlockPos> LAST = new EnumMap<>(K.class);
    private static int timer;

    private Conquest() {}

    static List<BlockPos> towns(K k) {
        return TOWNS.computeIfAbsent(k, x -> new ArrayList<>());
    }

    /** Случайный центр поселения: столица или один из захваченных городов. */
    static BlockPos center(K k, Random r) {
        BlockPos p = k.capital;
        List<BlockPos> t = TOWNS.get(k);
        if (t != null && !t.isEmpty() && r.nextInt(t.size() + 1) != 0) p = t.get(r.nextInt(t.size()));
        LAST.put(k, p);
        return p;
    }

    static int cx(K k, Random r) {
        return center(k, r).getX();
    }

    static int cz(K k) {
        BlockPos p = LAST.get(k);
        return (p != null ? p : k.capital).getZ();
    }

    private static boolean soldier(MobEntity m) {
        return !(m instanceof VillagerEntity);
    }

    private static double hd(MobEntity m, BlockPos p) {
        double dx = m.getX() - p.getX() - 0.5, dz = m.getZ() - p.getZ() - 0.5;
        return dx * dx + dz * dz;
    }

    /** Кто осаждает точку p королевства d (null — никто или есть защитники). */
    static K besieger(K d, BlockPos p) {
        for (MobEntity m : d.members) {
            if (!m.isAlive()) continue;
            if (soldier(m) ? hd(m, p) < 20 * 20 : hd(m, p) < 9 * 9) return null;
        }
        K best = null;
        int bn = 1;
        for (K o : K.values()) {
            if (o == d || !o.alive || o.world != d.world || !Diplomacy.hostile(d, o)) continue;
            int n = 0;
            for (MobEntity m : o.members) if (m.isAlive() && soldier(m) && hd(m, p) < 14 * 14) n++;
            if (n > bn) {
                bn = n;
                best = o;
            }
        }
        return best;
    }

    private static void say(MinecraftServer s, Text t) {
        Chronicle.log(t.getString());
        s.getPlayerManager().broadcast(t, false);
    }

    public static void tick(MinecraftServer s) {
        if (++timer % 20 != 0 || !on) return;
        for (K d : K.values()) {
            if (!d.alive || d.world == null || d.capital == null || !d.world.shouldTickEntity(d.capital) || !Diplomacy.atWar(d)) {
                SIEGE.remove(d);
                continue;
            }
            ServerWorld w = d.world;
            K a = besieger(d, d.capital);
            if (a == null) {
                SIEGE.remove(d);
            } else {
                int n = SIEGE.merge(d, 1, Integer::sum);
                Fx.particles(w, ParticleTypes.LARGE_SMOKE, d.capital.getX() + 0.5, d.capital.getY() + 5, d.capital.getZ() + 0.5, 12, 2, 1, 2, 0.02);
                if (n == 1) say(s, Text.literal("🔥 " + a.title + " осаждает столицу: " + d.title + "!").formatted(a.color));
                if (n >= 12) {
                    SIEGE.remove(d);
                    annex(s, a, d);
                    continue;
                }
            }
            List<BlockPos> t = TOWNS.get(d);
            if (t == null) continue;
            for (BlockPos p : new ArrayList<>(t)) {
                K b = besieger(d, p);
                if (b != null && w.getRandom().nextInt(8) == 0) takeTown(s, b, d, p);
            }
        }
    }

    /** Королевство d полностью переходит к a. */
    static boolean annex(MinecraftServer s, K a, K d) {
        if (a == null || d == null || a == d || !a.alive || !d.alive || d.world == null || d.capital == null) return false;
        Tech.trophy(s, a, d);
        ServerWorld w = d.world;
        Civ.City ca = Civ.city(a), cb = Civ.city(d);
        int nb = 0;
        for (Civ.Building b : cb.buildings) {
            if (b == cb.quarry && ca.quarry != null) continue;
            ca.buildings.add(b);
            nb++;
        }
        ca.wood += cb.wood;
        ca.stone += cb.stone;
        ca.food += cb.food;
        ca.ore += cb.ore;
        ca.era = Math.max(ca.era, cb.era);
        if (cb.castle) ca.castle = true;
        List<BlockPos> ta = towns(a);
        ta.add(d.capital);
        List<BlockPos> td = TOWNS.remove(d);
        if (td != null) ta.addAll(td);
        int conv = 0;
        for (MobEntity m : new ArrayList<>(d.members)) {
            if (!m.isAlive()) continue;
            m.removeCommandTag("gbking");
            m.setTarget(null);
            Kingdoms.join(a, m);
            a.members.add(m);
            conv++;
        }
        d.members.clear();
        recolor(w, d.wool, a.wool, d.capital, 48);
        if (td != null) for (BlockPos p : td) recolor(w, d.wool, a.wool, p, 40);
        BlockPos c = d.capital;
        Plague.conquered(a, d);
        Kingdoms.disbandKingdom(d);
        Fx.sound(w, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, SoundEvents.EVENT_RAID_HORN.value(), 6f, 0.8f);
        Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, c.getX() + 0.5, c.getY() + 4, c.getZ() + 0.5, 80, 2, 2, 2, 0.4);
        if (!Treaties.union) say(s, Text.literal("⚔ " + a.title + " захватило " + d.title + "! Перешло построек: " + nb + ", жителей и воинов: " + conv).formatted(a.color, Formatting.BOLD));
        return true;
    }

    /** Отдельный город (бывшая столица) переходит к a. */
    static void takeTown(MinecraftServer s, K a, K d, BlockPos p) {
        List<BlockPos> td = TOWNS.get(d);
        if (td == null || !td.remove(p)) return;
        Civ.City ca = Civ.city(a), cb = Civ.city(d);
        int nb = 0;
        for (Civ.Building b : new ArrayList<>(cb.buildings)) {
            if (b == cb.quarry) continue;
            double dx = b.cx() - p.getX(), dz = b.cz() - p.getZ();
            if (dx * dx + dz * dz > 45 * 45) continue;
            if (cb.project != null && cb.project.b == b) cb.project = null;
            cb.buildings.remove(b);
            ca.buildings.add(b);
            nb++;
        }
        int conv = 0;
        for (MobEntity m : new ArrayList<>(d.members)) {
            if (!m.isAlive() || hd(m, p) > 45 * 45 || m.getCommandTags().contains("gbking")) continue;
            m.setTarget(null);
            Kingdoms.join(a, m);
            d.members.remove(m);
            a.members.add(m);
            conv++;
        }
        towns(a).add(p);
        Plague.carry(d, a, 0.4, "с жителями захваченного города из");
        Treaties.captured(a, d);
        recolor(d.world, d.wool, a.wool, p, 45);
        Fx.sound(d.world, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, SoundEvents.EVENT_RAID_HORN.value(), 4f, 1f);
        say(s, Text.literal("🏴 " + a.title + " захватило город королевства «" + d.title + "» (" + nb + " построек, " + conv + " жителей)").formatted(a.color));
    }

    /** Если королевство пало без жителей — его земли забирает сильнейший враг. */
    static boolean fallTo(MinecraftServer s, K d) {
        if (!on || d.capital == null) return false;
        K best = null;
        double bd = Double.MAX_VALUE;
        for (K o : K.values()) {
            if (o == d || !o.alive || o.world != d.world || o.capital == null || !Diplomacy.hostile(d, o)) continue;
            double dist = o.capital.getSquaredDistance(d.capital);
            for (MobEntity m : o.members) if (m.isAlive() && soldier(m) && hd(m, d.capital) < 64 * 64) dist -= 1e6;
            if (dist < bd) {
                bd = dist;
                best = o;
            }
        }
        return best != null && annex(s, best, d);
    }

    /** Куда идти воинам на войне: ближайшая вражеская столица или город. */
    static BlockPos target(K k, MobEntity m) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (K o : K.values()) {
            if (o == k || !o.alive || o.world != k.world || o.capital == null || !Diplomacy.hostile(k, o)) continue;
            List<BlockPos> all = new ArrayList<>();
            all.add(o.capital);
            List<BlockPos> t = TOWNS.get(o);
            if (t != null) all.addAll(t);
            for (BlockPos p : all) {
                double d = p.getSquaredDistance(m.getBlockPos());
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /** Сила бога: присоединить ближайшее королевство к соседу. */
    public static void force(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        K d = null, a = null;
        double bd = Double.MAX_VALUE;
        for (K k : K.values()) {
            if (!k.alive || k.world != w || k.capital == null) continue;
            double x = k.capital.getSquaredDistance(pos);
            if (x < bd) {
                bd = x;
                d = k;
            }
        }
        if (d == null) {
            god.sendMessage(Text.literal("Рядом нет королевств").formatted(Formatting.GRAY), true);
            return;
        }
        bd = Double.MAX_VALUE;
        for (K k : K.values()) {
            if (k == d || !k.alive || k.world != w || k.capital == null) continue;
            double x = k.capital.getSquaredDistance(d.capital);
            if (x < bd) {
                bd = x;
                a = k;
            }
        }
        if (a == null) {
            god.sendMessage(Text.literal("Нужно второе королевство").formatted(Formatting.RED), true);
            return;
        }
        annex(w.getServer(), a, d);
    }

    /** Перекрасить флаги и шерсть в цвет нового хозяина (постепенно). */
    static void recolor(ServerWorld w, Block from, Block to, BlockPos c, int r) {
        if (w == null || from == to) return;
        BlockState ns = to.getDefaultState();
        Tasks.add(new Tasks.Task() {
            int i = 0;
            final int side = r * 2 + 1;

            @Override
            public boolean tick() {
                for (int n = 0; n < 300 && i < side * side; n++, i++) {
                    int x = c.getX() - r + i % side, z = c.getZ() - r + i / side;
                    for (int y = c.getY() - 4; y <= c.getY() + 24; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (w.getBlockState(p).isOf(from)) w.setBlockState(p, ns);
                    }
                }
                return i >= side * side;
            }
        });
    }

    public static void forget(K k) {
        TOWNS.remove(k);
        SIEGE.remove(k);
        LAST.remove(k);
    }

    public static void clear() {
        TOWNS.clear();
        SIEGE.clear();
        LAST.clear();
        timer = 0;
    }
}
