package com.vladik.godbox;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Мореходство: порты, корабли колонистов, рыбацкие лодки и торговые суда. */
public final class Ships {
    public static boolean on = true;
    static final Map<Kingdoms.K, BlockPos> PORTS = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, BoatEntity> FISH = new EnumMap<>(Kingdoms.K.class);
    private static int t;

    private Ships() {}

    static boolean sea(ServerWorld w, BlockPos p) {
        return w.getFluidState(p).isIn(FluidTags.WATER) && w.getBlockState(p.up()).isAir();
    }

    /** Верхний блок воды в столбе или null, если там суша. */
    static BlockPos waterTop(ServerWorld w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        BlockPos s = Fx.surface(w, x, z).down();
        return w.getFluidState(s).isIn(FluidTags.WATER) ? s : null;
    }

    static BlockPos port(ServerWorld w, Kingdoms.K k) {
        BlockPos p = PORTS.get(k);
        if (p != null && w.getFluidState(p).isIn(FluidTags.WATER)) return p;
        PORTS.remove(k);
        if (k.capital == null) return null;
        Random r = w.getRandom();
        for (int i = 0; i < 120; i++) {
            BlockPos wt = waterTop(w, k.capital.getX() + r.nextInt(97) - 48, k.capital.getZ() + r.nextInt(97) - 48);
            if (wt == null || !w.getFluidState(wt.down()).isIn(FluidTags.WATER)) continue;
            for (Direction land : Direction.Type.HORIZONTAL) {
                BlockPos l = wt.offset(land, 2);
                if (waterTop(w, l.getX(), l.getZ()) != null) continue;
                BlockPos far = wt.offset(land.getOpposite(), 12);
                if (waterTop(w, far.getX(), far.getZ()) == null) continue;
                return build(w, k, wt, land);
            }
        }
        return null;
    }

    static BlockPos build(ServerWorld w, Kingdoms.K k, BlockPos wt, Direction land) {
        Direction sea = land.getOpposite(), side = sea.rotateYClockwise();
        int era = Civ.city(k).era, y = wt.getY();
        TownBuilder.Job j = new TownBuilder.Job(w);
        for (int d = -1; d < 8; d++) for (int s = -1; s <= 1; s++) {
            BlockPos p = wt.offset(sea, d).offset(side, s);
            p = new BlockPos(p.getX(), y, p.getZ());
            j.put(p, Eras.style(era, Civ.Kind.HOUSE, Blocks.OAK_PLANKS.getDefaultState()));
            if ((d == 2 || d == 6) && s != 0) {
                j.put(p.down(), Blocks.OAK_FENCE.getDefaultState());
                j.put(p.down(2), Blocks.OAK_FENCE.getDefaultState());
                j.put(p.up(), Blocks.OAK_FENCE.getDefaultState());
                j.put(p.up(2), era >= 3 ? Blocks.LANTERN.getDefaultState() : Blocks.TORCH.getDefaultState());
            }
        }
        BlockPos end = wt.offset(sea, 7);
        for (int dy = 1; dy <= 4; dy++) j.put(new BlockPos(end.getX(), y + dy, end.getZ()), Blocks.OAK_FENCE.getDefaultState());
        j.put(new BlockPos(end.getX(), y + 5, end.getZ()), k.wool.getDefaultState());
        j.put(wt.offset(sea, 0).offset(side, 1).up(), Blocks.BARREL.getDefaultState());
        j.put(wt.offset(sea, 0).offset(side, -1).up(), Blocks.CHEST.getDefaultState());
        Tasks.add(j);
        BlockPos moor = wt.offset(sea, 10);
        PORTS.put(k, moor);
        Chronicle.say(w.getServer(), k, "построило порт — начинается эпоха мореходства");
        return moor;
    }

    static BoatEntity boat(ServerWorld w, Kingdoms.K k, BlockPos at, boolean chest) {
        BoatEntity b = chest ? EntityType.CHEST_BOAT.create(w) : EntityType.BOAT.create(w);
        if (b == null) return null;
        int era = Civ.city(k).era;
        b.setVariant(era >= 6 ? BoatEntity.Type.DARK_OAK : era >= 3 ? BoatEntity.Type.SPRUCE : BoatEntity.Type.OAK);
        b.refreshPositionAndAngles(at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 0f, 0f);
        b.addCommandTag("gb_ship");
        w.spawnEntity(b);
        return b;
    }

    /** Плавание группы лодок к цели по воде (обход суши), по прибытии — колбэк. */
    static final class Voyage implements Tasks.Task {
        final ServerWorld w;
        final List<BoatEntity> boats;
        final BlockPos target;
        final Consumer<BlockPos> arrive;
        final int y;
        int t;
        double speed = 0.3;

        Voyage(ServerWorld w, List<BoatEntity> boats, BlockPos target, int y, Consumer<BlockPos> arrive) {
            this.w = w; this.boats = boats; this.target = target; this.y = y; this.arrive = arrive;
        }

        boolean ok(double x, double z) { return sea(w, new BlockPos((int) Math.floor(x), y, (int) Math.floor(z))); }

        void move(BoatEntity b, double nx, double nz, double a) {
            b.refreshPositionAndAngles(nx, y + 0.9, nz, (float) Math.toDegrees(a) - 90f, 0f);
            b.setVelocity(Vec3d.ZERO);
        }

        @Override
        public boolean tick() {
            t++;
            boats.removeIf(b -> !b.isAlive());
            if (boats.isEmpty()) return true;
            BoatEntity lead = boats.get(0);
            double dx = target.getX() + 0.5 - lead.getX(), dz = target.getZ() + 0.5 - lead.getZ();
            if (dx * dx + dz * dz < 9 || t > 20 * 240) { arrive.accept(lead.getBlockPos()); return true; }
            double base = Math.atan2(dz, dx), sp = speed;
            double[] off = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};
            Double dir = null;
            for (double o : off) {
                double a = base + o;
                if (ok(lead.getX() + Math.cos(a) * 2.5, lead.getZ() + Math.sin(a) * 2.5)) { dir = a; break; }
            }
            if (dir == null) { arrive.accept(lead.getBlockPos()); return true; }
            double cx = Math.cos(dir), cz = Math.sin(dir);
            move(lead, lead.getX() + cx * sp, lead.getZ() + cz * sp, dir);
            for (int i = 1; i < boats.size(); i++) {
                BoatEntity b = boats.get(i);
                double tx = lead.getX() - cx * 3 * i + (i % 2 == 0 ? 1.5 : -1.5) * cz, tz = lead.getZ() - cz * 3 * i - (i % 2 == 0 ? 1.5 : -1.5) * cx;
                double ex = tx - b.getX(), ez = tz - b.getZ(), d = Math.sqrt(ex * ex + ez * ez);
                if (d > 0.05) {
                    double st = Math.min(d, sp * 1.3);
                    double nx = b.getX() + ex / d * st, nz = b.getZ() + ez / d * st;
                    if (ok(nx, nz) || d > 12) move(b, nx, nz, Math.atan2(ez, ex));
                }
            }
            if (t % 6 == 0) Fx.particles(w, ParticleTypes.SPLASH, lead.getX() - cx, y + 1, lead.getZ() - cz, 6, 0.3, 0.1, 0.3, 0.05);
            return false;
        }
    }

    /** Колонисты садятся в лодки в порту и плывут основывать город. */
    static boolean colonize(ServerWorld w, Kingdoms.K k, BlockPos target, List<MobEntity> settlers) {
        BlockPos moor = port(w, k);
        if (moor == null || Seasons.frozen(k)) return false;
        List<BoatEntity> boats = new ArrayList<>();
        BoatEntity cur = null;
        int i = 0;
        for (MobEntity m : settlers) {
            if (cur == null || cur.getPassengerList().size() >= 2) {
                cur = boat(w, k, moor.add(i % 3 - 1, 0, i / 3), false);
                if (cur == null) break;
                boats.add(cur);
                i++;
            }
            m.stopRiding();
            m.refreshPositionAndAngles(cur.getX(), cur.getY(), cur.getZ(), 0f, 0f);
            m.startRiding(cur, true);
        }
        BoatEntity cargo = boat(w, k, moor.add(1, 0, 1), true);
        if (cargo != null) boats.add(cargo);
        if (boats.isEmpty()) return false;
        Chronicle.say(w.getServer(), k, "отправляет корабли с колонистами за море");
        Tasks.add(Tech.sail(k, new Voyage(w, boats, target, moor.getY(), at -> {
            for (BoatEntity b : boats) for (var p : new ArrayList<>(b.getPassengerList())) {
                p.stopRiding();
                BlockPos g = Fx.surface(w, target.getX() + w.getRandom().nextInt(7) - 3, target.getZ() + w.getRandom().nextInt(7) - 3);
                p.refreshPositionAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 0f);
                Fx.particles(w, ParticleTypes.POOF, g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, 10, 0.3, 0.5, 0.3, 0.02);
            }
            Chronicle.say(w.getServer(), k, "колонисты высадились на новом берегу");
            Colonies.found(w, k, target);
            Tasks.delay(200, () -> boats.forEach(b -> { if (b.isAlive() && !b.hasPassengers()) b.discard(); }));
        })));
        return true;
    }

    public static void tick(MinecraftServer s) {
        t++;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            BoatEntity b = FISH.get(k);
            BlockPos m = PORTS.get(k);
            if (b == null || m == null || !b.isAlive() || !(b.getWorld() instanceof ServerWorld w)) continue;
            double a = t * 0.012 + k.ordinal() * 1.5;
            double nx = m.getX() + 0.5 + Math.cos(a) * 7, nz = m.getZ() + 0.5 + Math.sin(a) * 7;
            if (sea(w, new BlockPos((int) Math.floor(nx), m.getY(), (int) Math.floor(nz)))) {
                b.refreshPositionAndAngles(nx, m.getY() + 0.9, nz, (float) Math.toDegrees(a), 0f);
                b.setVelocity(Vec3d.ZERO);
            }
        }
        if (!on || t % 1200 != 0) return;
        List<Kingdoms.K> withPort = new ArrayList<>();
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld w)) { PORTS.remove(k); FISH.remove(k); continue; }
            Civ.City c = Civ.city(k);
            BlockPos m = PORTS.get(k);
            if (m == null && Tech.has(k, Tech.T.SAILING) && c.wood >= 25) { if (port(w, k) != null) c.wood -= 20; continue; }
            if (m == null) continue;
            withPort.add(k);
            fisher(w, k, m, c);
        }
        if (withPort.size() >= 2 && t % 2400 == 0) trade(withPort);
    }

    private static void fisher(ServerWorld w, Kingdoms.K k, BlockPos m, Civ.City c) {
        BoatEntity b = FISH.get(k);
        if (b == null || !b.isAlive()) {
            String tag = "gb_fish_" + k.name();
            List<BoatEntity> old = w.getEntitiesByClass(BoatEntity.class, new Box(m).expand(48), x -> x.getCommandTags().contains(tag));
            if (!old.isEmpty()) b = old.get(0);
            else {
                b = boat(w, k, m, false);
                if (b == null) return;
                b.addCommandTag(tag);
                MobEntity f = Kingdoms.spawn(k, EntityType.VILLAGER, m.up());
                if (f != null) f.startRiding(b, true);
            }
            FISH.put(k, b);
        }
        if (b.hasPassengers()) {
            c.food += 4;
            Fx.particles(w, ParticleTypes.FISHING, b.getX(), b.getY() + 0.5, b.getZ(), 12, 1.5, 0.1, 1.5, 0.05);
        }
    }

    private static void trade(List<Kingdoms.K> ks) {
        Kingdoms.K a = ks.get((t / 2400) % ks.size()), b = ks.get((t / 2400 + 1) % ks.size());
        if (a == b || Diplomacy.rel(a, b) == Diplomacy.WAR || Seasons.frozen(a) || Seasons.frozen(b) || !(a.world instanceof ServerWorld w)) return;
        BlockPos from = PORTS.get(a), to = PORTS.get(b);
        if (from == null || to == null) return;
        if (Plague.blocked(a, b)) return;
        BoatEntity ship = boat(w, a, from, true);
        if (ship == null) return;
        Plague.depart(a, b);
        List<BoatEntity> l = new ArrayList<>();
        l.add(ship);
        Tasks.add(Tech.sail(a, new Voyage(w, l, to, from.getY(), at -> {
            Civ.City ca = Civ.city(a), cb = Civ.city(b);
            ca.food += 10; ca.wood += 8; cb.food += 10; cb.ore += 4;
            Chronicle.log("Торговое судно " + a.title + " прибыло в порт " + b.title);
            Plague.arrive(a, b, 0.5, "с торговым судном из");
            Tasks.delay(100, ship::discard);
        })));
    }

    public static void clear() {
        PORTS.clear();
        FISH.clear();
        t = 0;
    }
}
