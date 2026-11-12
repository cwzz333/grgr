package com.vladik.godbox;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** Расселение: королевства сами основывают новые города на свободных землях — пешком или морем. */
public final class Colonies {
    public static boolean on = true;
    private static int t;
    static final String[] NAMES = {"Новгород", "Светлоград", "Белозёрск", "Ольхов", "Каменск", "Ясногорск", "Ветров", "Солнцедар",
            "Златоград", "Сосновка", "Приморск", "Лесогорск", "Северск", "Тихоречье", "Рудня", "Велиград"};

    private Colonies() {}

    public static String name(BlockPos p) { return NAMES[Math.floorMod(p.getX() * 31 + p.getZ() * 17, NAMES.length)]; }

    private static double d2(BlockPos a, int x, int z) { double dx = a.getX() - x, dz = a.getZ() - z; return dx * dx + dz * dz; }

    static boolean free(int x, int z, double d) {
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (!o.alive) continue;
            if (o.capital != null && d2(o.capital, x, z) < d * d) return false;
            List<BlockPos> ts = Conquest.TOWNS.get(o);
            if (ts != null) for (BlockPos p : ts) if (d2(p, x, z) < d * d) return false;
        }
        return true;
    }

    private static boolean land(ServerWorld w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
        BlockPos g = new BlockPos(x, TownBuilder.ground(w, x, z) - 1, z);
        BlockState s = w.getBlockState(g);
        return s.getFluidState().isEmpty() && !s.isIn(BlockTags.LEAVES) && s.isSolidBlock(w, g);
    }

    record Site(BlockPos pos, boolean sea) {}

    static Site site(ServerWorld w, Kingdoms.K k, int min, int max) {
        Random r = w.getRandom();
        List<BlockPos> bases = new ArrayList<>(Conquest.towns(k));
        bases.add(k.capital);
        for (int i = 0; i < 50; i++) {
            BlockPos b = bases.get(r.nextInt(bases.size()));
            double a = r.nextDouble() * Math.PI * 2;
            int dist = min + r.nextInt(max - min + 1);
            int x = b.getX() + (int) (Math.cos(a) * dist), z = b.getZ() + (int) (Math.sin(a) * dist);
            if (!free(x, z, 55) || !land(w, x, z)) continue;
            int y = TownBuilder.ground(w, x, z);
            boolean flat = true;
            for (int[] q : new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}, {0, 12}}) {
                if (!land(w, x + q[0], z + q[1]) || Math.abs(TownBuilder.ground(w, x + q[0], z + q[1]) - y) > 4) { flat = false; break; }
            }
            if (!flat || !TownBuilder.dryArea(w, x, z, 12)) continue;
            int water = 0;
            for (int s = 1; s < 16; s++) {
                int sx = b.getX() + (x - b.getX()) * s / 16, sz = b.getZ() + (z - b.getZ()) * s / 16;
                if (Ships.waterTop(w, sx, sz) != null) water++;
            }
            return new Site(new BlockPos(x, y, z), water >= 3);
        }
        return null;
    }

    static List<MobEntity> settlers(Kingdoms.K k, int n) {
        List<MobEntity> out = new ArrayList<>();
        for (MobEntity m : k.members) {
            if (out.size() >= n) break;
            if (!m.isAlive() || m.hasVehicle() || !(m instanceof VillagerEntity v) || v.isBaby()) continue;
            if (m.getCommandTags().contains(Kings.TAG) || m.getCommandTags().contains("gbking")) continue;
            out.add(m);
        }
        return out;
    }

    /** Поселенцы идут пешком к новому месту (отставшие подтягиваются). */
    static void march(ServerWorld w, Kingdoms.K k, BlockPos site, List<MobEntity> group) {
        Chronicle.say(w.getServer(), k, "отправляет поселенцев осваивать новые земли");
        Tasks.add(new Tasks.Task() {
            int t;

            @Override
            public boolean tick() {
                if (++t % 20 != 0) return false;
                boolean near = false;
                for (MobEntity m : group) {
                    if (!m.isAlive()) continue;
                    if (d2(site, m.getBlockX(), m.getBlockZ()) < 144) near = true;
                    else m.getNavigation().startMovingTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0.9);
                    if (t % 100 == 0) Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 2, m.getZ(), 2, 0.2, 0.2, 0.2, 0);
                }
                if (!near && t < 20 * 90) return false;
                for (MobEntity m : group) {
                    if (!m.isAlive() || d2(site, m.getBlockX(), m.getBlockZ()) < 400) continue;
                    BlockPos g = Fx.surface(w, site.getX() + w.getRandom().nextInt(7) - 3, site.getZ() + w.getRandom().nextInt(7) - 3);
                    m.refreshPositionAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 0f);
                }
                found(w, k, site);
                return true;
            }
        });
    }

    /** Основать город в точке p: постройки + территория + новые жители. */
    static void found(ServerWorld w, Kingdoms.K k, BlockPos p) {
        if (!k.alive) return;
        BlockPos c = TownBuilder.dryNear(w, p, 24);
        if (c == null) { Chronicle.say(w.getServer(), k, "не нашло сухой земли для нового города"); return; }
        TownBuilder.town(w, k, c);
        Conquest.towns(k).add(c);
        Fx.sound(w, c.getX(), c.getY(), c.getZ(), SoundEvents.EVENT_RAID_HORN.value(), 3f, 1.3f);
        Chronicle.say(w.getServer(), k, "основало новый город «" + name(c) + "»");
        Tasks.delay(140, () -> {
            if (!k.alive) return;
            Random r = w.getRandom();
            for (int i = 0; i < 3; i++) {
                BlockPos at = Fx.surface(w, c.getX() + r.nextInt(5) - 2, c.getZ() + r.nextInt(5) - 2);
                Kingdoms.spawn(k, i == 2 ? Kingdoms.soldier(r) : EntityType.VILLAGER, at);
            }
        });
    }

    private static void pay(Civ.City c) { c.wood -= 30; c.food -= 30; c.stone -= 15; }

    public static void tick(MinecraftServer s) {
        if (!on || ++t % 1200 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w)) continue;
            Civ.City c = Civ.city(k);
            int towns = Conquest.towns(k).size(), pop = 0;
            for (MobEntity m : k.members) if (m.isAlive()) pop++;
            if (towns >= 8 || pop < 12 + towns * 6) continue;
            if (c.wood < 30 || c.food < 30 || c.stone < 15 || w.getRandom().nextInt(2) != 0) continue;
            Site st = site(w, k, 70, Tech.seaReach(k, 150));
            if (st == null) continue;
            List<MobEntity> g = settlers(k, 4);
            if (g.size() < 2) continue;
            if (st.sea()) {
                if (!Ships.on || !Ships.colonize(w, k, st.pos(), g)) continue;
            } else march(w, k, st.pos(), g);
            pay(c);
        }
    }

    /** Силы: town — основать город ближайшего королевства, port — порт, ship — корабль колонистов сюда. */
    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos, String arg) {
        Kingdoms.K k = null;
        double best = Double.MAX_VALUE;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (!o.alive || o.capital == null || o.world != w) continue;
            double d = d2(o.capital, pos.getX(), pos.getZ());
            if (d < best) { best = d; k = o; }
        }
        if (k == null) { god.sendMessage(Text.literal("Нет ни одного королевства — сначала оснуйте его").formatted(Formatting.RED), true); return; }
        switch (arg) {
            case "town" -> { found(w, k, pos); god.sendMessage(Text.literal("«" + k.title + "» строит город «" + name(pos) + "»").formatted(k.color), true); }
            case "port" -> {
                BlockPos wt = Ships.waterTop(w, pos.getX(), pos.getZ());
                if (wt == null) { god.sendMessage(Text.literal("Кликните по воде у берега").formatted(Formatting.RED), true); return; }
                net.minecraft.util.math.Direction land = net.minecraft.util.math.Direction.NORTH;
                for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                    BlockPos q = wt.offset(d, 3);
                    if (Ships.waterTop(w, q.getX(), q.getZ()) == null) { land = d; break; }
                }
                Ships.build(w, k, wt, land);
                god.sendMessage(Text.literal("«" + k.title + "» строит порт").formatted(k.color), true);
            }
            default -> {
                if (!land(w, pos.getX(), pos.getZ())) { god.sendMessage(Text.literal("Кликните по суше — туда поплывут колонисты").formatted(Formatting.RED), true); return; }
                List<MobEntity> g = settlers(k, 4);
                if (g.isEmpty() || !Ships.colonize(w, k, pos, g)) { god.sendMessage(Text.literal("У «" + k.title + "» нет порта или свободных жителей (постройте порт силой «Порт»)").formatted(Formatting.RED), true); return; }
                god.sendMessage(Text.literal("Корабли «" + k.title + "» выходят в море").formatted(k.color), true);
            }
        }
    }

    public static void clear() { t = 0; }
}
