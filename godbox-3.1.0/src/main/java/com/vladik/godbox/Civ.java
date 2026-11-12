package com.vladik.godbox;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Экономика королевств как в WorldBox: профессии, добыча, склад, стройка, рост населения. */
public final class Civ {
    public static boolean economy = true;

    private Civ() {}

    enum Job {
        LUMBER("Лесоруб", VillagerProfession.FLETCHER, Items.IRON_AXE),
        MINER("Шахтёр", VillagerProfession.TOOLSMITH, Items.IRON_PICKAXE),
        FARMER("Фермер", VillagerProfession.FARMER, Items.IRON_HOE),
        BUILDER("Строитель", VillagerProfession.MASON, Items.IRON_SHOVEL),
        FISHER("Рыбак", VillagerProfession.FISHERMAN, Items.FISHING_ROD);

        final String title;
        final VillagerProfession prof;
        final Item tool;

        Job(String title, VillagerProfession prof, Item tool) {
            this.title = title;
            this.prof = prof;
            this.tool = tool;
        }
    }

    enum Kind {
        HOUSE("Дом", 5, 5, 10, 3),
        FARM("Ферма", 7, 7, 6, 0),
        BARRACKS("Казарма", 7, 5, 15, 30),
        TOWER("Сторожевая башня", 3, 3, 4, 25),
        WELL("Колодец", 3, 3, 4, 10),
        MILL("Мельница", 5, 5, 25, 12),
        SMITHY("Кузница", 5, 5, 12, 22),
        GRANARY("Амбар", 5, 5, 22, 6),
        TEMPLE("Храм", 7, 7, 20, 40),
        MANOR("Большой дом", 7, 5, 34, 14),
        QUARRY("Карьер", 5, 5, 0, 0),
        CASTLE("Замок", 15, 15, 0, 0),
        WAREHOUSE("Склад", 7, 5, 18, 10);

        final String title;
        final int w, d, wood, stone;

        Kind(String title, int w, int d, int wood, int stone) {
            this.title = title;
            this.w = w;
            this.d = d;
            this.wood = wood;
            this.stone = stone;
        }
    }

    static final class Building {
        final Kind kind;
        final int x0, z0, x1, z1, y0;
        boolean done;

        Building(Kind kind, int x0, int z0, int y0) {
            this.kind = kind;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x0 + kind.w - 1;
            this.z1 = z0 + kind.d - 1;
            this.y0 = y0;
        }

        int cx() { return (x0 + x1) / 2; }

        int cz() { return (z0 + z1) / 2; }

        BlockPos center() { return new BlockPos(cx(), y0, cz()); }

        boolean inside(int x, int z, int m) {
            return x >= x0 - m && x <= x1 + m && z >= z0 - m && z <= z1 + m;
        }
    }

    static final class Project {
        final Building b;
        final List<BlockPos> pos = new ArrayList<>();
        final List<BlockState> st = new ArrayList<>();
        int i;
        boolean repair;

        Project(Building b) { this.b = b; }

        void add(BlockPos p, BlockState s) {
            pos.add(p);
            st.add(s);
        }
    }

    static final class City {
        int wood = 15, stone = 5, food = 25, ore = 0;
        final List<Building> buildings = new ArrayList<>();
        Project project;
        Building quarry;
        int cycle;
        boolean hungry;
        int era, faith, culture = -1, religion = -1;
        boolean castle;
        boolean water = true;
        BlockPos fireAt;

        int count(Kind k) {
            int n = 0;
            for (Building b : buildings) if (b.kind == k && b.done) n++;
            return n;
        }
    }

    static final class W {
        Job job;
        int state;
        BlockPos target;
        BlockPos walk;
        int work;
        int stuck;
        double best = 1e9;
        int idle;
        BlockPos rest;
        final int[] bag = new int[4]; // дерево, камень, еда, руда

        int bagSum() { return bag[0] + bag[1] + bag[2] + bag[3]; }
    }

    static final int FIND = 0, GO = 1, WORK = 2, RETURN = 3;

    private static final Map<Kingdoms.K, City> CITIES = new EnumMap<>(Kingdoms.K.class);
    private static final Map<VillagerEntity, W> WORKERS = new WeakHashMap<>();
    private static int timer;

    // ---------------- жизненный цикл ----------------
    static City city(Kingdoms.K k) {
        City c = CITIES.get(k);
        if (c == null) {
            c = new City();
            CITIES.put(k, c);
        }
        if (c.quarry == null && k.world != null && k.capital != null && c.cycle % 6 == 0) {
            Building q = site(k, c, Kind.QUARRY, k.world);
            if (q != null) {
                q.done = true;
                c.buildings.add(q);
                c.quarry = q;
            }
        }
        return c;
    }

    public static void found(Kingdoms.K k) {
        CITIES.remove(k);
        city(k);
    }

    public static void remove(Kingdoms.K k) {
        CITIES.remove(k);
        for (MobEntity m : k.members) if (m instanceof VillagerEntity v) WORKERS.remove(v);
    }

    public static void clear() {
        CITIES.clear();
        WORKERS.clear();
        timer = 0;
    }

    public static String summary(Kingdoms.K k) {
        City c = CITIES.get(k);
        if (c == null) return "";
        String s = "склад: дерево " + c.wood + ", камень " + c.stone + ", еда " + c.food + ", руда " + c.ore
            + " | домов " + c.count(Kind.HOUSE) + ", ферм " + c.count(Kind.FARM) + ", казарм " + c.count(Kind.BARRACKS)
            + ", башен " + c.count(Kind.TOWER) + CivPlus.extra(c);
        int[] n = counts(k);
        s += " | лесорубов " + n[0] + ", шахтёров " + n[1] + ", фермеров " + n[2] + ", строителей " + n[3] + ", рыбаков " + n[4];
        if (c.project != null) {
            s += " | строится: " + c.project.b.kind.title + " (" + (c.project.i * 100 / Math.max(1, c.project.pos.size())) + "%)";
        }
        if (c.hungry) s += " | ГОЛОД";
        s += Events.summary(k);
        return s;
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer server) {
        if (!economy) return;
        if (++timer % 5 != 0) return;
        boolean slow = timer % 100 == 0;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world == null || k.capital == null) continue;
            if (!k.world.shouldTickEntity(k.capital)) continue;
            ServerWorld w = k.world;
            City c = city(k);
            growCrops(w, c);
            CivPlus.tickCity(k, c, w, timer);
            for (MobEntity m : new ArrayList<>(k.members)) {
                if (!(m instanceof VillagerEntity v) || !v.isAlive() || v.isBaby()) continue;
                W s = WORKERS.get(v);
                if (s == null) {
                    s = new W();
                    WORKERS.put(v, s);
                }
                if (s.job == null) setJob(k, v, s, pick(k, c));
                step(k, c, w, v, s);
            }
            if (slow) economyTick(k, c, w);
        }
    }

    // ---------------- профессии ----------------
    static int[] counts(Kingdoms.K k) {
        int[] n = new int[Job.values().length];
        for (MobEntity m : k.members) {
            if (m instanceof VillagerEntity v) {
                W s = WORKERS.get(v);
                if (s != null && s.job != null) n[s.job.ordinal()]++;
            }
        }
        return n;
    }

    static Job pick(Kingdoms.K k, City c) {
        int[] n = counts(k);
        int total = n[0] + n[1] + n[2] + n[3];
        if (c.project != null && n[3] < Math.max(1, (total + 1) / 5)) return Job.BUILDER;
        if (c.water && n[4] < Math.max(1, total / 6) && c.food < 60) return Job.FISHER;
        double fw = c.food < 20 ? 1.8 : 1.0;
        double ww = c.wood < 30 ? 1.4 : 1.0;
        double sw = c.stone < 25 ? 1.1 : 0.7;
        if (c.quarry == null) sw = 0.01;
        double a = (n[0] + 0.5) / ww, b = (n[1] + 0.5) / sw, f = (n[2] + 0.5) / fw;
        if (a <= b && a <= f) return Job.LUMBER;
        return b <= f ? Job.MINER : Job.FARMER;
    }

    static String jobTitle(VillagerEntity v) {
        W s = WORKERS.get(v);
        return s == null || s.job == null ? "" : s.job.title;
    }

    static void setJob(Kingdoms.K k, VillagerEntity v, W s, Job j) {
        s.job = j;
        s.state = FIND;
        s.target = null;
        s.idle = 0;
        v.setVillagerData(v.getVillagerData().withProfession(j.prof));
        v.setExperience(1);
        v.setCustomName(Life.label(k, v, j.title));
        v.setCustomNameVisible(false);
        hold(v, new ItemStack(j.tool));
    }

    static void hold(VillagerEntity v, ItemStack st) {
        v.equipStack(EquipmentSlot.MAINHAND, st);
        v.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    // ---------------- поведение рабочего ----------------
    static void step(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        if (CivPlus.night(k, c, w, v, s)) return;
        Building q = c.quarry;
        if (q != null && q.inside(v.getBlockX(), v.getBlockZ(), 0) && v.getY() < q.y0 - 1.5) {
            BlockPos e = front(k, q, w);
            v.requestTeleport(e.getX() + 0.5, e.getY(), e.getZ() + 0.5);
        }
        switch (s.state) {
            case FIND -> find(k, c, w, v, s);
            case GO -> go(k, c, w, v, s);
            case WORK -> work(k, c, w, v, s);
            default -> ret(k, c, w, v, s);
        }
    }

    static void find(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        if (s.job == Job.BUILDER && c.project == null) {
            s.job = null;
            setJob(k, v, s, pick(k, c));
        } else if (s.job != Job.BUILDER && c.project != null && counts(k)[3] == 0) {
            setJob(k, v, s, Job.BUILDER);
        }
        if (s.job == Job.BUILDER && s.bagSum() > 0) {
            s.state = RETURN;
            return;
        }
        BlockPos t = null, to = null;
        switch (s.job) {
            case LUMBER -> {
                t = findTree(k, c, w, v);
                to = t;
            }
            case MINER -> {
                t = Ores.prospect(k, c, w, v);
                if (t != null) {
                    to = t.up();
                } else {
                    t = nextMine(k, c, w);
                    if (t != null) to = front(k, c.quarry, w);
                }
            }
            case FARMER -> {
                t = findFarm(c, w, v);
                if (t == null) t = findForage(k, c, w, v);
                to = t;
            }
            case FISHER -> {
                t = CivPlus.findWater(k, c, w, v);
                to = t == null ? null : CivPlus.shore(w, t);
                if (to == null) t = null;
            }
            case BUILDER -> {
                if (c.project != null) {
                    t = c.project.b.center();
                    to = front(k, c.project.b, w);
                }
            }
        }
        if (t == null) {
            if (s.bagSum() > 0) {
                s.state = RETURN;
                return;
            }
            if (s.idle % 4 == 0) v.setHeadRollingTimeLeft(25);
            if (++s.idle > 12) {
                s.job = null;
                setJob(k, v, s, pick(k, c));
            }
            return;
        }
        s.idle = 0;
        s.target = t;
        s.walk = to;
        s.state = GO;
        s.stuck = 0;
        s.best = 1e9;
    }

    static void go(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        if (s.walk == null || s.target == null) {
            s.state = FIND;
            return;
        }
        double d = hdist(v, s.walk);
        double reach = s.job == Job.LUMBER ? 2.8 : 2.3;
        if (d <= reach && Math.abs(v.getY() - s.walk.getY()) < 4) {
            s.state = WORK;
            s.work = 0;
            return;
        }
        walk(v, s.walk);
        if (d < s.best - 0.4) {
            s.best = d;
            s.stuck = 0;
        } else if (++s.stuck > 50) {
            s.stuck = 0;
            s.best = 1e9;
            if (s.job == Job.BUILDER || s.job == Job.MINER) {
                BlockPos g = ground(w, s.walk.getX(), s.walk.getZ());
                Fx.particles(w, ParticleTypes.CLOUD, v.getX(), v.getY() + 1, v.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                v.requestTeleport(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            } else {
                s.state = FIND;
                s.target = null;
            }
        }
    }

    static void work(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        BlockPos t = s.target;
        if (t == null) {
            s.state = FIND;
            return;
        }
        halt(v);
        v.getLookControl().lookAt(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
        if (s.job == Job.BUILDER) {
            build(k, c, w, v, s);
            return;
        }
        if (s.job == Job.FISHER) {
            CivPlus.fish(k, c, w, v, s);
            return;
        }
        BlockState bs = w.getBlockState(t);
        if (!valid(s.job, w, t, bs)) {
            w.setBlockBreakingInfo(v.getId(), t, -1);
            s.state = FIND;
            s.target = null;
            return;
        }
        s.work++;
        int need = duration(s.job, w, t, bs);
        CivPlus.workFx(v, s.work);
        if (!bs.isAir()) {
            w.setBlockBreakingInfo(v.getId(), t, Math.min(9, s.work * 10 / need));
            Fx.particles(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, bs), t.getX() + 0.5, t.getY() + 0.7, t.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.05);
            if (s.work % 2 == 0) {
                Fx.sound(w, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, bs.getSoundGroup().getHitSound(), 0.8f, 0.8f + w.getRandom().nextFloat() * 0.3f);
            }
        }
        if (s.work < need) return;
        w.setBlockBreakingInfo(v.getId(), t, -1);
        s.work = 0;
        s.target = null;
        switch (s.job) {
            case LUMBER -> {
                s.bag[0] += Math.max(1, chop(w, t));
                s.state = RETURN;
                hold(v, new ItemStack(Items.OAK_LOG));
            }
            case MINER -> {
                mineYield(bs, s);
                w.breakBlock(t, false);
                if (s.bagSum() >= 5) {
                    s.state = RETURN;
                    hold(v, new ItemStack(s.bag[3] > 0 ? Items.RAW_IRON : Items.COBBLESTONE));
                } else {
                    s.state = FIND;
                }
            }
            case FARMER -> {
                farmAct(w, t, bs, s);
                if (c.count(Kind.MILL) > 0 && w.getRandom().nextBoolean()) s.bag[2]++;
                if (s.bag[2] >= 4) {
                    s.state = RETURN;
                    hold(v, new ItemStack(Items.WHEAT));
                } else {
                    s.state = FIND;
                }
            }
            default -> s.state = FIND;
        }
    }

    static void ret(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        BlockPos home = CivPlus.depot(k, c, w, v);
        double d = hdist(v, home);
        if (d <= 3.5) {
            s.bag[2] = Events.harvest(k, s.bag[2]);
            c.wood += Races.yield(k, 0, s.bag[0]);
            c.stone += Races.yield(k, 1, s.bag[1]);
            c.food += Races.yield(k, 2, s.bag[2]);
            c.ore += Races.yield(k, 3, s.bag[3]);
            Storage.deposit(k, c, w, v);
            for (int i = 0; i < 4; i++) s.bag[i] = 0;
            Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.2, v.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
            Fx.sound(w, v.getX(), v.getY(), v.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
            CivPlus.hop(v);
            s.state = FIND;
            s.stuck = 0;
            s.best = 1e9;
            if (w.getRandom().nextInt(4) == 0) {
                s.job = null;
                setJob(k, v, s, pick(k, c));
            } else {
                hold(v, new ItemStack(s.job.tool));
            }
            return;
        }
        walk(v, home);
        if (d < s.best - 0.4) {
            s.best = d;
            s.stuck = 0;
        } else if (++s.stuck > 60) {
            s.stuck = 0;
            s.best = 1e9;
            BlockPos g = ground(w, home.getX(), home.getZ() + 5);
            v.requestTeleport(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        }
    }

    // ---------------- лесоруб ----------------
    static BlockPos findTree(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v) {
        Random r = w.getRandom();
        int R = radius(c);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            int x = Conquest.cx(k, r) + r.nextInt(R * 2 + 1) - R;
            int z = Conquest.cz(k) + r.nextInt(R * 2 + 1) - R;
            if (inCity(x, z, 1)) continue;
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).down();
            if (!w.getBlockState(top).isIn(BlockTags.LOGS)) continue;
            BlockPos b = top;
            int g = 0;
            while (w.getBlockState(b.down()).isIn(BlockTags.LOGS) && g++ < 30) b = b.down();
            if (!hasLeaves(w, top) || claimed(b)) continue;
            double d = b.getSquaredDistance(v.getBlockPos());
            if (d < bd) {
                bd = d;
                best = b.toImmutable();
            }
        }
        if (best == null && r.nextInt(3) == 0) {
            int px = Conquest.cx(k, r) + r.nextInt(R * 2 + 1) - R, pz = Conquest.cz(k) + r.nextInt(R * 2 + 1) - R;
            if (!inCity(px, pz, 2)) {
                BlockPos g = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(px, 0, pz));
                if (w.getBlockState(g.down()).isIn(BlockTags.DIRT) && w.getBlockState(g).isAir()) w.setBlockState(g, Blocks.OAK_SAPLING.getDefaultState());
            }
        }
        return best;
    }

    static boolean hasLeaves(ServerWorld w, BlockPos top) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    if (w.getBlockState(top.add(dx, dy, dz)).isIn(BlockTags.LEAVES)) return true;
        return false;
    }

    static int chop(ServerWorld w, BlockPos base) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        q.add(base);
        seen.add(base);
        while (!q.isEmpty() && logs.size() < 64) {
            BlockPos p = q.poll();
            if (!w.getBlockState(p).isIn(BlockTags.LOGS)) continue;
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.add(dx, dy, dz);
                        if (Math.abs(n.getX() - base.getX()) > 5 || Math.abs(n.getZ() - base.getZ()) > 5) continue;
                        if (seen.add(n)) q.add(n);
                    }
        }
        Block sap = sapling(w.getBlockState(base).getBlock());
        for (int i = logs.size() - 1; i >= 0; i--) w.breakBlock(logs.get(i), false);
        CivPlus.leaves(w, logs);
        if (sap != null && w.getBlockState(base).isAir() && w.getBlockState(base.down()).isIn(BlockTags.DIRT)) {
            w.setBlockState(base, sap.getDefaultState());
        }
        return logs.size();
    }

    static Block sapling(Block log) {
        String path = Registries.BLOCK.getId(log).getPath();
        if (!path.endsWith("_log") || path.startsWith("stripped_")) return null;
        Block s = Registries.BLOCK.get(Identifier.of("minecraft", path.replace("_log", "_sapling")));
        return s == Blocks.AIR ? Blocks.OAK_SAPLING : s;
    }

    // ---------------- шахтёр ----------------
    static BlockPos nextMine(Kingdoms.K k, City c, ServerWorld w) {
        Building q = c.quarry;
        if (q == null) return null;
        int bottom = Math.max(q.y0 - 18, w.getBottomY() + 4);
        for (int y = q.y0 + 2; y >= bottom; y--) {
            for (int x = q.x0; x <= q.x1; x++) {
                for (int z = q.z0; z <= q.z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = w.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    if (s.getHardness(w, p) < 0) continue;
                    if (claimed(p)) continue;
                    return p;
                }
            }
        }
        c.quarry = null;
        Building nq = site(k, c, Kind.QUARRY, w);
        if (nq != null) {
            nq.done = true;
            c.buildings.add(nq);
            c.quarry = nq;
        }
        return null;
    }

    static void mineYield(BlockState s, W st) {
        String p = Registries.BLOCK.getId(s.getBlock()).getPath();
        if (p.equals("ancient_debris")) {
            st.bag[3] += 5;
            return;
        }
        if (p.endsWith("_ore")) {
            st.bag[3] += (p.contains("diamond") || p.contains("emerald") || p.contains("gold")) ? 3 : 1;
            st.bag[1]++;
        } else if (s.isIn(BlockTags.BASE_STONE_OVERWORLD) || p.contains("stone") || p.contains("deepslate")) {
            st.bag[1]++;
        } else if (s.isIn(BlockTags.LOGS)) {
            st.bag[0]++;
        }
    }

    // ---------------- фермер ----------------
    static BlockPos findFarm(City c, ServerWorld w, VillagerEntity v) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (Building b : c.buildings) {
            if (b.kind != Kind.FARM || !b.done) continue;
            for (int x = b.x0; x <= b.x1; x++) {
                for (int z = b.z0; z <= b.z1; z++) {
                    if (x == b.cx() && z == b.cz()) continue;
                    BlockPos p = new BlockPos(x, b.y0, z);
                    BlockState below = w.getBlockState(p.down());
                    if (!below.isOf(Blocks.FARMLAND) && !below.isOf(Blocks.DIRT) && !below.isOf(Blocks.GRASS_BLOCK)) continue;
                    BlockState s = w.getBlockState(p);
                    boolean need = s.isAir() || (s.getBlock() instanceof CropBlock cb && cb.isMature(s));
                    if (!need || claimed(p)) continue;
                    double d = p.getSquaredDistance(v.getBlockPos());
                    if (d < bd) {
                        bd = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    static BlockPos findForage(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v) {
        Random r = w.getRandom();
        int R = radius(c);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < 30; i++) {
            int x = Conquest.cx(k, r) + r.nextInt(R * 2 + 1) - R;
            int z = Conquest.cz(k) + r.nextInt(R * 2 + 1) - R;
            if (inCity(x, z, 0)) continue;
            BlockPos top = w.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, 0, z)).down();
            BlockState s = w.getBlockState(top);
            if (!forage(s) || claimed(top)) continue;
            double d = top.getSquaredDistance(v.getBlockPos());
            if (d < bd) {
                bd = d;
                best = top.toImmutable();
            }
        }
        return best;
    }

    static boolean forage(BlockState s) {
        if (s.isOf(Blocks.SWEET_BERRY_BUSH)) return s.get(SweetBerryBushBlock.AGE) >= 2;
        return s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS) || s.isOf(Blocks.FERN) || s.isIn(BlockTags.SMALL_FLOWERS);
    }

    static void farmAct(ServerWorld w, BlockPos t, BlockState bs, W s) {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        if (bs.getBlock() instanceof CropBlock cb) {
            if (cb.isMature(bs)) {
                s.bag[2] += 2;
                w.syncWorldEvent(2001, t, Block.getRawIdFromState(bs));
                w.setBlockState(t, wheat.withAge(0));
            }
            return;
        }
        if (bs.isAir()) {
            if (!w.getBlockState(t.down()).isOf(Blocks.FARMLAND)) {
                w.setBlockState(t.down(), Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
            }
            w.setBlockState(t, wheat.withAge(0));
            Fx.sound(w, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, SoundEvents.ITEM_CROP_PLANT, 0.8f, 1f);
            return;
        }
        if (bs.isOf(Blocks.SWEET_BERRY_BUSH)) {
            s.bag[2] += 2;
            w.setBlockState(t, bs.with(SweetBerryBushBlock.AGE, 1));
            Fx.sound(w, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.8f, 1f);
            return;
        }
        w.breakBlock(t, false);
        s.bag[2] += 1;
    }

    static void growCrops(ServerWorld w, City c) {
        Random r = w.getRandom();
        for (Building b : c.buildings) {
            if (b.kind != Kind.FARM || !b.done) continue;
            for (int i = 0; i < 3 + 2 * c.count(Kind.WELL); i++) {
                BlockPos p = new BlockPos(b.x0 + r.nextInt(b.kind.w), b.y0, b.z0 + r.nextInt(b.kind.d));
                BlockState s = w.getBlockState(p);
                if (s.getBlock() instanceof CropBlock cb && !cb.isMature(s) && r.nextDouble() * 3 < Seasons.growth()) {
                    w.setBlockState(p, cb.withAge(cb.getAge(s) + 1), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    static boolean valid(Job job, ServerWorld w, BlockPos t, BlockState bs) {
        return switch (job) {
            case LUMBER -> bs.isIn(BlockTags.LOGS);
            case MINER -> !bs.isAir() && bs.getFluidState().isEmpty();
            case FARMER -> {
                if (bs.isAir()) {
                    BlockState below = w.getBlockState(t.down());
                    yield below.isOf(Blocks.FARMLAND) || below.isOf(Blocks.DIRT) || below.isOf(Blocks.GRASS_BLOCK);
                }
                if (bs.getBlock() instanceof CropBlock cb) yield cb.isMature(bs);
                yield forage(bs);
            }
            default -> true;
        };
    }

    static int duration(Job job, ServerWorld w, BlockPos t, BlockState bs) {
        return switch (job) {
            case LUMBER -> 14;
            case MINER -> Math.max(2, Math.min(12, 2 + (int) (bs.getHardness(w, t) * 3)));
            case FARMER -> 4;
            default -> 1;
        };
    }

    // ---------------- строитель ----------------
    static void build(Kingdoms.K k, City c, ServerWorld w, VillagerEntity v, W s) {
        Project p = c.project;
        if (p == null) {
            s.state = FIND;
            return;
        }
        Building b = p.b;
        if (hdist(v, b.center()) > 10) {
            s.state = GO;
            s.target = b.center();
            s.walk = front(k, b, w);
            s.best = 1e9;
            s.stuck = 0;
            return;
        }
        int skipped = 0, fast = 0;
        while (p.i < p.pos.size()) {
            BlockPos q = p.pos.get(p.i);
            BlockState st = p.st.get(p.i);
            p.i++;
            BlockState cur = w.getBlockState(q);
            if (cur.equals(st)) {
                if (++skipped > 16) return;
                continue;
            }
            v.getLookControl().lookAt(q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5);
            if (st.isAir()) {
                boolean soft = cur.isIn(BlockTags.LEAVES) || cur.isReplaceable();
                if (cur.isIn(BlockTags.LOGS)) {
                    c.wood++;
                    hold(v, new ItemStack(Items.IRON_AXE));
                } else if (!soft) hold(v, new ItemStack(Items.IRON_SHOVEL));
                boolean wet = !cur.getFluidState().isEmpty();
                if (wet || cur.getBlock() instanceof net.minecraft.block.AbstractFireBlock) w.setBlockState(q, st, Block.NOTIFY_ALL);
                else w.breakBlock(q, false);
                if ((soft || wet) && ++fast < 6) continue;
            } else {
                boolean bed = st.getBlock() instanceof BedBlock;
                w.setBlockState(q, st, bed ? Block.NOTIFY_LISTENERS : Block.NOTIFY_ALL);
                hold(v, new ItemStack(st.getBlock().asItem()));
                if (w.getRandom().nextInt(3) == 0) CivPlus.bob(v);
                Fx.sound(w, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, st.getSoundGroup().getPlaceSound(), 0.7f, 0.9f + w.getRandom().nextFloat() * 0.2f);
                Fx.particles(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, st), q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.05);
            }
            return;
        }
        finish(k, c, w);
    }

    static void finish(Kingdoms.K k, City c, ServerWorld w) {
        Project p = c.project;
        if (p == null) return;
        c.project = null;
        Building b = p.b;
        b.done = true;
        BlockPos cc = b.center();
        if (p.repair) {
            Repair.snapshot(w, b);
            Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, cc.getX() + 0.5, cc.getY() + 2, cc.getZ() + 0.5, 20, 2, 1.5, 2, 0.05);
            tell(w, Text.literal("🛠 " + k.title + " восстановило: " + b.kind.title).formatted(k.color));
            return;
        }
        Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, cc.getX() + 0.5, cc.getY() + 2, cc.getZ() + 0.5, 30, 2, 1.5, 2, 0.05);
        Fx.sound(w, cc.getX() + 0.5, cc.getY() + 1, cc.getZ() + 0.5, SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1.5f, 1f);
        CivPlus.onFinish(k, c, w, b);
        Banners.onBuilt(k, w, b);
        Repair.snapshot(w, b);
        if (b.kind == Kind.TOWER) {
            Kingdoms.spawn(k, EntityType.PILLAGER, new BlockPos(b.cx(), b.y0 + 7, b.cz()));
        }
        tell(w, Text.literal(k.title + " построило: " + b.kind.title).formatted(k.color));
    }

    // ---------------- экономика города ----------------
    static void economyTick(Kingdoms.K k, City c, ServerWorld w) {
        c.cycle++;
        if (c.project == null) plan(k, c, w);
        int vill = 0, sold = 0;
        for (MobEntity m : k.members) {
            if (m instanceof VillagerEntity) vill++;
            else sold++;
        }
        if (c.cycle % 10 == 0) c.buildings.removeIf(b -> b.done && b.kind == Kind.HOUSE && ruined(w, b));
        if (c.cycle % 5 == 0 && c.wood < 25) c.wood += 2;
        int houses = c.count(Kind.HOUSE);
        int cap = CivPlus.cap(c);
        Random r = w.getRandom();

        if (c.cycle % Events.birthPeriod(k) == 0 && vill < cap && c.food >= 8 && k.members.size() < 260 && Races.breeds(k, r)) {
            c.food -= 8;
            BlockPos at = birthPlace(k, c, w);
            MobEntity m = Kingdoms.spawn(k, EntityType.VILLAGER, at);
            if (m != null) {
                Life.birth(m);
                Fx.particles(w, ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.02);
                Fx.sound(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1f, 1.3f);
            }
        }

        int barracks = c.count(Kind.BARRACKS);
        int towers = c.count(Kind.TOWER);
        int scap = Races.army(k, 2 + barracks * 6 + towers * 2);
        if (c.cycle % 4 == 0 && barracks > 0 && sold < scap && c.food >= 6 && c.wood >= 2 && c.stone >= 3) {
            Building bb = null;
            for (Building b : c.buildings) if (b.kind == Kind.BARRACKS && b.done) bb = b;
            if (bb != null) {
                c.food -= 6;
                c.wood -= 2;
                c.stone -= 3;
                MobEntity m = Kingdoms.spawn(k, Kingdoms.soldier(r), front(k, bb, w));
                CivPlus.arm(c, m, w);
                if (m != null && c.ore >= 2) {
                    c.ore -= 2;
                    m.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    m.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                    m.setEquipmentDropChance(EquipmentSlot.CHEST, 0f);
                    m.setEquipmentDropChance(EquipmentSlot.LEGS, 0f);
                }
            }
        }

        if (c.cycle % 12 == 0) {
            int eat = Seasons.eat(k, (k.members.size() + 4) / (c.count(Kind.GRANARY) > 0 ? 8 : 5));
            c.food -= eat;
            if (c.food < 0) {
                c.food = 0;
                if (!c.hungry) tell(w, Text.literal(k.title + ": голод!").formatted(Formatting.GOLD));
                c.hungry = true;
                for (MobEntity m : k.members) {
                    if (m instanceof VillagerEntity v) {
                        v.damage(w.getDamageSources().starve(), 3f);
                        break;
                    }
                }
            } else {
                c.hungry = false;
            }
        }
    }

    static boolean ruined(ServerWorld w, Building b) {
        if (!w.isChunkLoaded(b.cx() >> 4, b.cz() >> 4)) return false;
        int air = 0;
        int[][] cs = {{b.x0, b.z0}, {b.x1, b.z0}, {b.x0, b.z1}, {b.x1, b.z1}};
        for (int[] q : cs) for (int dy = 0; dy <= 1; dy++) if (w.getBlockState(new BlockPos(q[0], b.y0 + dy, q[1])).isAir()) air++;
        return air >= 6;
    }

    static BlockPos birthPlace(Kingdoms.K k, City c, ServerWorld w) {
        Random r = w.getRandom();
        List<Building> hs = new ArrayList<>();
        for (Building b : c.buildings) if (b.kind == Kind.HOUSE && b.done) hs.add(b);
        if (!hs.isEmpty()) return front(k, hs.get(r.nextInt(hs.size())), w);
        return ground(w, k.capital.getX() + r.nextInt(5) - 2, k.capital.getZ() + 2 + r.nextInt(2));
    }

    static void plan(Kingdoms.K k, City c, ServerWorld w) {
        int vill = 0;
        for (MobEntity m : k.members) if (m instanceof VillagerEntity) vill++;
        int houses = c.count(Kind.HOUSE), farms = c.count(Kind.FARM), bar = c.count(Kind.BARRACKS), tow = c.count(Kind.TOWER);
        Kind want = CivPlus.choose(c, vill, w.getRandom());
        if (want == null) return;
        if (c.wood < want.wood || c.stone < want.stone) return;
        Building b = site(k, c, want, w);
        if (b == null) return;
        c.wood -= want.wood;
        c.stone -= want.stone;
        c.buildings.add(b);
        c.project = blueprint(k, b, w);
        int cleared = Clearing.prepend(c.project, w);
        tell(w, Text.literal(k.title + ": заложено — " + want.title + (cleared > 10 ? " (сначала расчищают площадку)" : "")).formatted(k.color));
    }

    static Building site(Kingdoms.K k, City c, Kind kind, ServerWorld w) {
        Random r = w.getRandom();
        int base = kind == Kind.QUARRY ? 14 : 9;
        int spread = Math.min(150, 6 + c.buildings.size() * 2);
        for (int i = 0; i < 70; i++) {
            BlockPos cap0 = Conquest.center(k, r);
            double a = r.nextDouble() * Math.PI * 2;
            double dist = base + r.nextDouble() * Math.min(spread, 14 + i * 2) + i * 0.3;
            int cx = cap0.getX() + (int) Math.round(Math.cos(a) * dist);
            int cz = cap0.getZ() + (int) Math.round(Math.sin(a) * dist);
            int x0 = cx - kind.w / 2, z0 = cz - kind.d / 2, x1 = x0 + kind.w - 1, z1 = z0 + kind.d - 1;
            if (x1 >= k.capital.getX() - 6 && x0 <= k.capital.getX() + 6 && z1 >= k.capital.getZ() - 6 && z0 <= k.capital.getZ() + 6) continue;
            boolean bad = false;
            for (City oc : CITIES.values()) {
                for (Building o : oc.buildings) {
                    if (x1 >= o.x0 - 2 && x0 <= o.x1 + 2 && z1 >= o.z0 - 2 && z0 <= o.z1 + 2) {
                        bad = true;
                        break;
                    }
                }
                if (bad) break;
            }
            if (bad) continue;
            int[][] pts = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {cx, cz}};
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int[] pt : pts) {
                BlockPos g = soil(w, pt[0], pt[1]);
                BlockState gs = w.getBlockState(g.down());
                if (!gs.getFluidState().isEmpty() || !w.getFluidState(g).isEmpty()) {
                    bad = true;
                    break;
                }
                min = Math.min(min, g.getY());
                max = Math.max(max, g.getY());
            }
            if (bad || max - min > 2) continue;
            return new Building(kind, x0, z0, soil(w, cx, cz).getY());
        }
        return null;
    }

    // ---------------- чертежи ----------------
    static Project blueprint(Kingdoms.K k, Building b, ServerWorld w) {
        Project p = new Project(b);
        int H = switch (b.kind) {
            case HOUSE -> 9;
            case FARM -> 3;
            case BARRACKS -> 8;
            case TOWER -> 10;
            case WELL -> 5;
            case MILL, TEMPLE -> 11;
            case SMITHY, GRANARY -> 7;
            case MANOR -> 10;
            case WAREHOUSE -> 8;
            default -> 0;
        };
        BlockState air = Blocks.AIR.getDefaultState();
        for (int y = b.y0; y < b.y0 + H; y++)
            for (int x = b.x0; x <= b.x1; x++)
                for (int z = b.z0; z <= b.z1; z++) {
                    BlockPos q = new BlockPos(x, y, z);
                    if (!w.getBlockState(q).isAir()) p.add(q, air);
                }
        BlockState fill = Blocks.COBBLESTONE.getDefaultState();
        for (int x = b.x0; x <= b.x1; x++)
            for (int z = b.z0; z <= b.z1; z++) {
                int d = 1;
                while (d < 6 && !solid(w, new BlockPos(x, b.y0 - 1 - d, z))) d++;
                for (int y = b.y0 - d; y <= b.y0 - 2; y++) p.add(new BlockPos(x, y, z), fill);
                p.add(new BlockPos(x, b.y0 - 1, z), floor(b, x, z));
            }

        int dx = k.capital.getX() - b.cx(), dz = k.capital.getZ() - b.cz();
        boolean axisX = Math.abs(dx) >= Math.abs(dz);
        int doorX = axisX ? (dx > 0 ? b.x1 : b.x0) : b.cx();
        int doorZ = axisX ? b.cz() : (dz > 0 ? b.z1 : b.z0);
        int y0 = b.y0;

        switch (b.kind) {
            case HOUSE -> {
                BlockState[] sty = CivPlus.houseStyle(b);
                BlockState log = sty[0];
                BlockState plank = sty[1];
                BlockState base = Blocks.COBBLESTONE.getDefaultState();
                BlockState glass = Blocks.GLASS.getDefaultState();
                int era = city(k).era;
                BlockState roof = Eras.style(era, Kind.HOUSE, sty[2]);
                int cx = b.cx(), cz = b.cz();
                // стены: каменный цоколь, фахверк, окна по центру
                for (int y = y0; y <= y0 + 2; y++)
                    for (int x = b.x0; x <= b.x1; x++)
                        for (int z = b.z0; z <= b.z1; z++) {
                            boolean ex = x == b.x0 || x == b.x1, ez = z == b.z0 || z == b.z1;
                            if (!ex && !ez) continue;
                            if (x == doorX && z == doorZ && y <= y0 + 1) continue;
                            BlockState s;
                            if (ex && ez) s = log;
                            else if (y == y0 + 1 && (x == cx || z == cz)) s = glass;
                            else if (y == y0) s = base;
                            else s = plank;
                            p.add(new BlockPos(x, y, z), s);
                        }
                // потолок с обвязкой из брёвен
                for (int x = b.x0; x <= b.x1; x++)
                    for (int z = b.z0; z <= b.z1; z++) {
                        boolean ex = x == b.x0 || x == b.x1, ez = z == b.z0 || z == b.z1;
                        BlockState s = ez ? Arch.beam(log, Direction.Axis.X) : ex ? Arch.beam(log, Direction.Axis.Z) : plank;
                        p.add(new BlockPos(x, y0 + 3, z), s);
                    }
                // двускатная крыша из ступеней со свесами, конёк с цветом королевства
                Arch.gable(p::add, b.x0, b.z0, b.x1, b.z1, y0 + 3, roof, plank, roof);
                int top = y0 + 3 + Arch.gableHeight(b.x0, b.z0, b.x1, b.z1);
                p.add(new BlockPos(cx, top, cz), k.wool.getDefaultState());
                // печная труба
                Arch.chimney(p::add, b.x1 - 1, b.z1 - 1, y0 + 4, top, Blocks.BRICKS.getDefaultState());
                // дверь, ставни, фонарь у входа
                Direction out = axisX ? (doorX == b.x1 ? Direction.EAST : Direction.WEST) : (doorZ == b.z1 ? Direction.SOUTH : Direction.NORTH);
                Arch.door(p::add, new BlockPos(doorX, y0, doorZ), out, Arch.woodenDoor(plank));
                Block trap = Arch.woodenTrapdoor(plank);
                Object[][] wins = {{new BlockPos(cx, y0 + 1, b.z0), Direction.NORTH}, {new BlockPos(cx, y0 + 1, b.z1), Direction.SOUTH},
                    {new BlockPos(b.x0, y0 + 1, cz), Direction.WEST}, {new BlockPos(b.x1, y0 + 1, cz), Direction.EAST}};
                for (Object[] wv : wins) {
                    BlockPos wp = (BlockPos) wv[0];
                    if (wp.getX() == doorX && wp.getZ() == doorZ) continue;
                    Arch.shutters(p::add, wp, (Direction) wv[1], trap);
                }
                Arch.wallTorch(p::add, new BlockPos(doorX, y0 + 1, doorZ).offset(out.rotateYClockwise()), out);
                Block bed = k.bed();
                BlockPos foot, head;
                Direction face;
                if (axisX) {
                    int bx = doorX == b.x0 ? b.x1 - 1 : b.x0 + 1;
                    foot = new BlockPos(bx, y0, b.z0 + 2);
                    head = new BlockPos(bx, y0, b.z0 + 1);
                    face = Direction.NORTH;
                } else {
                    int bz = doorZ == b.z0 ? b.z1 - 1 : b.z0 + 1;
                    foot = new BlockPos(b.x0 + 2, y0, bz);
                    head = new BlockPos(b.x0 + 1, y0, bz);
                    face = Direction.WEST;
                }
                p.add(foot, bed.getDefaultState().with(BedBlock.FACING, face).with(BedBlock.PART, BedPart.FOOT));
                p.add(head, bed.getDefaultState().with(BedBlock.FACING, face).with(BedBlock.PART, BedPart.HEAD));
                // мебель по свободным углам: верстак с цветком, бочка
                int[][] corners = {{b.x0 + 1, b.z0 + 1}, {b.x1 - 1, b.z0 + 1}, {b.x0 + 1, b.z1 - 1}, {b.x1 - 1, b.z1 - 1}};
                int furn = 0;
                for (int[] cn : corners) {
                    BlockPos cp = new BlockPos(cn[0], y0, cn[1]);
                    if (cp.equals(foot) || cp.equals(head)) continue;
                    if (Math.abs(cn[0] - doorX) + Math.abs(cn[1] - doorZ) <= 1) continue;
                    if (furn == 0) {
                        p.add(cp, Blocks.CRAFTING_TABLE.getDefaultState());
                        p.add(cp.up(), Arch.pot(b.x0 * 7 + b.z0));
                    } else {
                        p.add(cp, Blocks.BARREL.getDefaultState());
                    }
                    if (++furn >= 2) break;
                }
            }
            case FARM -> {
                BlockState wheat = ((CropBlock) Blocks.WHEAT).withAge(0);
                for (int x = b.x0; x <= b.x1; x++)
                    for (int z = b.z0; z <= b.z1; z++) {
                        if (x == b.cx() && z == b.cz()) continue;
                        p.add(new BlockPos(x, y0, z), wheat);
                    }
            }
            case BARRACKS -> {
                BlockState brick = Blocks.STONE_BRICKS.getDefaultState();
                BlockState corner = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
                BlockState bars = Blocks.IRON_BARS.getDefaultState();
                for (int y = y0; y <= y0 + 3; y++)
                    for (int x = b.x0; x <= b.x1; x++)
                        for (int z = b.z0; z <= b.z1; z++) {
                            boolean ex = x == b.x0 || x == b.x1, ez = z == b.z0 || z == b.z1;
                            if (!ex && !ez) continue;
                            if (x == doorX && z == doorZ && y <= y0 + 1) continue;
                            BlockState s;
                            if (ex && ez) s = corner;
                            else if (y == y0 + 2 && ((ez && (x - b.x0) % 2 == 1) || (ex && z == b.cz()))) s = bars;
                            else s = brick;
                            p.add(new BlockPos(x, y, z), s);
                        }
                for (int x = b.x0; x <= b.x1; x++)
                    for (int z = b.z0; z <= b.z1; z++) {
                        p.add(new BlockPos(x, y0 + 4, z), brick);
                        boolean edge = x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1;
                        if (edge && ((x + z) & 1) == 0) p.add(new BlockPos(x, y0 + 5, z), brick);
                    }
                BlockState fence = Blocks.OAK_FENCE.getDefaultState();
                BlockState wool = k.wool.getDefaultState();
                p.add(new BlockPos(b.cx(), y0 + 5, b.cz()), fence);
                p.add(new BlockPos(b.cx(), y0 + 6, b.cz()), fence);
                p.add(new BlockPos(b.cx(), y0 + 7, b.cz()), wool);
                p.add(new BlockPos(b.cx() + 1, y0 + 7, b.cz()), wool);
                p.add(new BlockPos(b.cx() + 1, y0 + 6, b.cz()), wool);
                BlockPos st = new BlockPos(b.x0 + 1, y0, b.z0 + 1);
                if (Math.abs(st.getX() - doorX) + Math.abs(st.getZ() - doorZ) > 1) p.add(st, Blocks.SMITHING_TABLE.getDefaultState());
            }
            case TOWER -> {
                BlockState cob = Blocks.COBBLESTONE.getDefaultState();
                BlockState moss = Blocks.MOSSY_COBBLESTONE.getDefaultState();
                for (int y = y0; y <= y0 + 5; y++)
                    for (int x = b.x0; x <= b.x1; x++)
                        for (int z = b.z0; z <= b.z1; z++)
                            p.add(new BlockPos(x, y, z), Math.floorMod(x + y + z, 5) == 0 ? moss : cob);
                for (int x = b.x0; x <= b.x1; x++)
                    for (int z = b.z0; z <= b.z1; z++) {
                        boolean c0 = x == b.cx() && z == b.cz();
                        p.add(new BlockPos(x, y0 + 6, z), c0 ? k.wool.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
                        if (!c0) p.add(new BlockPos(x, y0 + 7, z), Blocks.COBBLESTONE_WALL.getDefaultState());
                    }
                int[][] cs = {{b.x0, b.z0}, {b.x1, b.z0}, {b.x0, b.z1}, {b.x1, b.z1}};
                for (int[] cn : cs) p.add(new BlockPos(cn[0], y0 + 8, cn[1]), Blocks.LANTERN.getDefaultState());
            }
            default -> {
            }
        }
        CivPlus.extras(k, b, w, p, doorX, doorZ);
        Eras.apply(k, b.kind, p.st);
        Eras.decor(k, b, p);
        return p;
    }

    static BlockState floor(Building b, int x, int z) {
        return switch (b.kind) {
            case HOUSE -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case FARM -> (x == b.cx() && z == b.cz()) ? Blocks.WATER.getDefaultState() : Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7);
            case BARRACKS -> Blocks.STONE_BRICKS.getDefaultState();
            case GRANARY, MANOR, WAREHOUSE -> Blocks.SPRUCE_PLANKS.getDefaultState();
            case TEMPLE -> Blocks.SMOOTH_STONE.getDefaultState();
            default -> Blocks.COBBLESTONE.getDefaultState();
        };
    }

    // ---------------- helpers ----------------
    static boolean solid(ServerWorld w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.isReplaceable();
    }

    static BlockPos ground(ServerWorld w, int x, int z) {
        return w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    static BlockPos front(Kingdoms.K k, Building b, ServerWorld w) {
        int dx = k.capital.getX() - b.cx(), dz = k.capital.getZ() - b.cz();
        int x, z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            x = dx > 0 ? b.x1 + 2 : b.x0 - 2;
            z = b.cz();
        } else {
            z = dz > 0 ? b.z1 + 2 : b.z0 - 2;
            x = b.cx();
        }
        return ground(w, x, z);
    }

    static double hdist(VillagerEntity v, BlockPos p) {
        double ax = v.getX() - (p.getX() + 0.5), az = v.getZ() - (p.getZ() + 0.5);
        return Math.sqrt(ax * ax + az * az);
    }

    static void walk(VillagerEntity v, BlockPos p) {
        v.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(p, 0.75f, 1));
    }

    static void halt(VillagerEntity v) {
        v.getBrain().forget(MemoryModuleType.WALK_TARGET);
        v.getNavigation().stop();
    }

    static int radius(City c) {
        return Math.min(80, 24 + 2 * c.buildings.size());
    }

    /** Земля под деревьями: стволы, листва и грибы не считаются грунтом. */
    static BlockPos soil(ServerWorld w, int x, int z) {
        BlockPos g = ground(w, x, z);
        int bottom = w.getBottomY();
        while (g.getY() > bottom + 1) {
            BlockState s = w.getBlockState(g.down());
            if (!(s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || Clearing.vegetation(s) && !s.isAir())) break;
            g = g.down();
        }
        return g;
    }

    /** Клетка занята другой постройкой (кроме self). */
    static boolean foreign(Building self, int x, int z) {
        for (City c : CITIES.values())
            for (Building b : c.buildings)
                if (b != self && b.inside(x, z, 0)) return true;
        return false;
    }

    static boolean inCity(int x, int z, int m) {
        for (City c : CITIES.values())
            for (Building b : c.buildings)
                if (b.inside(x, z, m)) return true;
        return false;
    }

    static boolean claimed(BlockPos p) {
        for (W o : WORKERS.values()) if (p.equals(o.target)) return true;
        return false;
    }

    static void tell(ServerWorld w, Text t) {
        w.getServer().getPlayerManager().broadcast(t, true);
    }
}
