package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Великие тракты: широкие мощёные дороги между дальними поселениями, союзниками и сюзеренами.
 * Бордюры, насыпи и выемки, мосты на опорах с перилами, фонари, аллеи деревьев,
 * верстовые столбы со знамёнами и постоялые дворы. Облик зависит от эпохи королевства.
 */
public final class Highways {
    public static boolean on = true;
    static final Set<String> DONE = new HashSet<>();
    static final List<Way> WAYS = new ArrayList<>();
    static final Map<Kingdoms.K, Integer> BUILT = new EnumMap<>(Kingdoms.K.class);
    static final String[] SIDE = {"Восточный", "Юго-восточный", "Южный", "Юго-западный", "Западный", "Северо-западный", "Северный", "Северо-восточный"};
    static final String[] NOUN = {"тракт", "большак", "королевский путь", "торговый путь", "шлях", "старый путь"};
    private static final int NONE = Integer.MIN_VALUE;
    private static int t;

    private Highways() {}

    static final class Way {
        final Kingdoms.K k;
        final ServerWorld w;
        final String key, name;
        final int n;
        final int[] xs, zs, hy;
        final double[] tx, tz;
        final Map<Long, Boolean> cells = new HashMap<>();
        final List<Integer> miss = new ArrayList<>();
        int i, pass;
        boolean inn;

        Way(Kingdoms.K k, ServerWorld w, String key, String name, List<int[]> pts) {
            this.k = k;
            this.w = w;
            this.key = key;
            this.name = name;
            n = pts.size();
            xs = new int[n];
            zs = new int[n];
            hy = new int[n];
            tx = new double[n];
            tz = new double[n];
            for (int j = 0; j < n; j++) {
                xs[j] = pts.get(j)[0];
                zs[j] = pts.get(j)[1];
                hy[j] = NONE;
            }
            for (int j = 0; j < n; j++) {
                int a = Math.max(0, j - 3), b = Math.min(n - 1, j + 3);
                double dx = xs[b] - xs[a], dz = zs[b] - zs[a], l = Math.sqrt(dx * dx + dz * dz);
                if (l < 1e-6) { dx = 1; dz = 0; l = 1; }
                tx[j] = dx / l;
                tz[j] = dz / l;
            }
        }
    }

    /** Ставит блоки порциями, затем соединяет заборы, стены и стёкла. */
    static final class Place implements Tasks.Task {
        final ServerWorld w;
        final List<BlockPos> ps = new ArrayList<>();
        final List<BlockState> ss = new ArrayList<>();
        int i, fix;

        Place(ServerWorld w) { this.w = w; }

        void put(BlockPos p, BlockState s) { ps.add(p); ss.add(s); }

        void put(BlockPos p, Block b) { put(p, b.getDefaultState()); }

        @Override
        public boolean tick() {
            for (int n = 0; n < 40 && i < ps.size(); n++, i++) {
                BlockPos p = ps.get(i);
                if (!w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
                w.setBlockState(p, ss.get(i), Block.NOTIFY_ALL);
            }
            if (i < ps.size()) return false;
            for (int n = 0; n < 120 && fix < ps.size(); n++, fix++) {
                BlockPos p = ps.get(fix);
                if (!w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
                BlockState s = w.getBlockState(p);
                Block b = s.getBlock();
                if (b instanceof WallBlock || b instanceof FenceBlock || b instanceof PaneBlock)
                    w.setBlockState(p, Block.postProcessState(s, w, p), Block.NOTIFY_LISTENERS);
            }
            return fix >= ps.size();
        }
    }

    // ---------------- материалы по эпохам ----------------
    static BlockState road(int era, boolean edge, int i, Random r) {
        Block b;
        if (era <= 1) b = edge ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH;
        else if (era <= 3) b = edge ? Blocks.COBBLESTONE : (r.nextInt(4) == 0 ? Blocks.COARSE_DIRT : Blocks.GRAVEL);
        else if (era <= 5) b = edge ? Blocks.STONE_BRICKS : (r.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
        else if (era <= 7) b = edge ? Blocks.POLISHED_ANDESITE : (r.nextInt(6) == 0 ? (r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS) : Blocks.STONE_BRICKS);
        else if (era == 8) b = edge ? Blocks.STONE_BRICKS : (i % 6 == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.SMOOTH_STONE);
        else if (era == 9) b = edge ? Blocks.POLISHED_DEEPSLATE : (i % 9 == 0 ? Blocks.AMETHYST_BLOCK : Blocks.DEEPSLATE_TILES);
        else b = edge ? Blocks.POLISHED_BLACKSTONE_BRICKS : (i % 8 == 0 ? Blocks.WAXED_CUT_COPPER : Blocks.BRICKS);
        return b.getDefaultState();
    }

    static BlockState fill(int era) {
        if (era < 4) return Blocks.DIRT.getDefaultState();
        if (era < 9) return Blocks.STONE_BRICKS.getDefaultState();
        if (era == 9) return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    static BlockState bridge(int era, boolean edge) {
        if (era < 4) return (edge ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS).getDefaultState();
        if (era < 8) return (edge ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS).getDefaultState();
        if (era == 8) return (edge ? Blocks.QUARTZ_BRICKS : Blocks.SMOOTH_STONE).getDefaultState();
        if (era == 9) return (edge ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).getDefaultState();
        return (edge ? Blocks.WAXED_CUT_COPPER : Blocks.POLISHED_BLACKSTONE_BRICKS).getDefaultState();
    }

    static BlockState rail(int era) {
        if (era < 4) return Blocks.SPRUCE_FENCE.getDefaultState();
        if (era < 8) return Blocks.STONE_BRICK_WALL.getDefaultState();
        if (era == 8) return Blocks.STONE_BRICK_WALL.getDefaultState();
        if (era == 9) return Blocks.DEEPSLATE_BRICK_WALL.getDefaultState();
        return Blocks.IRON_BARS.getDefaultState();
    }

    static BlockState pillar(int era) {
        if (era < 4) return Blocks.SPRUCE_LOG.getDefaultState();
        if (era < 9) return Blocks.STONE_BRICKS.getDefaultState();
        if (era == 9) return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
    }

    static Block banner(Kingdoms.K k) {
        return k.banner();
    }

    /** Для ускорения жителей на дороге. */
    static boolean paved(BlockState s) {
        return s.isOf(Blocks.POLISHED_ANDESITE) || s.isOf(Blocks.SMOOTH_STONE) || s.isOf(Blocks.CHISELED_STONE_BRICKS) || s.isOf(Blocks.DEEPSLATE_TILES)
                || s.isOf(Blocks.POLISHED_DEEPSLATE) || s.isOf(Blocks.AMETHYST_BLOCK) || s.isOf(Blocks.BRICKS) || s.isOf(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || s.isOf(Blocks.SPRUCE_PLANKS) || s.isOf(Blocks.COARSE_DIRT) || s.isOf(Blocks.WAXED_CUT_COPPER) || s.isOf(Blocks.STRIPPED_SPRUCE_LOG);
    }

    // ---------------- трасса ----------------
    static String name(BlockPos a, BlockPos b) {
        double ang = Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX());
        int sec = Math.floorMod((int) Math.round(ang / (Math.PI / 4)), 8);
        int h = Math.floorMod(a.getX() * 7 + b.getZ() * 13 + b.getX(), NOUN.length);
        return SIDE[sec] + " " + NOUN[h] + " к «" + Colonies.name(b) + "»";
    }

    static Way plan(ServerWorld w, Kingdoms.K k, BlockPos a, BlockPos b, String key) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 30 || len > 2500) return null;
        double px = -dz / len, pz = dx / len;
        int seed = key.hashCode();
        double amp = Math.min(22, len / 10) * ((seed & 1) == 0 ? 1 : -1);
        double waves = 1 + Math.floorMod(seed >> 3, 2);
        int n = (int) (len * 3);
        List<int[]> pts = new ArrayList<>();
        for (int s = 0; s <= n; s++) {
            double f = (double) s / n;
            double off = amp * Math.sin(Math.PI * f * waves) * Math.sin(Math.PI * f);
            int x = (int) Math.round(a.getX() + dx * f + px * off), z = (int) Math.round(a.getZ() + dz * f + pz * off);
            if (!pts.isEmpty()) {
                int[] l = pts.get(pts.size() - 1);
                if (l[0] == x && l[1] == z) continue;
            }
            pts.add(new int[]{x, z});
        }
        if (pts.size() < 10) return null;
        return new Way(k, w, key, name(a, b), pts);
    }

    static int raw(Way wy, int j) {
        if (wy.hy[j] != NONE) return wy.hy[j];
        int x = wy.xs[j], z = wy.zs[j];
        if (!wy.w.isChunkLoaded(x >> 4, z >> 4)) return NONE;
        int g = TownBuilder.ground(wy.w, x, z);
        BlockState s = wy.w.getBlockState(new BlockPos(x, g - 1, z));
        wy.hy[j] = s.getFluidState().isEmpty() ? g - 1 : g;
        return wy.hy[j];
    }

    static int smooth(Way wy, int i) {
        long sum = 0;
        int c = 0;
        for (int d = -6; d <= 6; d++) {
            int j = i + d;
            if (j < 0 || j >= wy.n) continue;
            int h = raw(wy, j);
            if (h == NONE) continue;
            sum += h;
            c++;
        }
        return c == 0 ? NONE : (int) Math.round((double) sum / c);
    }

    static void clearUp(ServerWorld w, Place pl, BlockPos p) {
        BlockState u = w.getBlockState(p);
        if (u.isAir() || !u.getFluidState().isEmpty()) return;
        if (u.isReplaceable() || u.isIn(BlockTags.LEAVES) || u.isIn(BlockTags.LOGS) || u.isOf(Blocks.SNOW) || u.isIn(BlockTags.FLOWERS))
            pl.put(p, Blocks.AIR);
    }

    static List<Civ.Building> allBuildings() {
        List<Civ.Building> bs = new ArrayList<>();
        for (Kingdoms.K o : Kingdoms.K.values()) if (o.alive) bs.addAll(Civ.city(o).buildings);
        return bs;
    }

    static boolean free(Way wy, List<Civ.Building> bs, int x, int z) {
        return wy.w.isChunkLoaded(x >> 4, z >> 4) && !Roads.inBuilding(bs, x, z) && !wy.cells.containsKey(BlockPos.asLong(x, 0, z))
                && TownBuilder.dry(wy.w, x, z);
    }

    static void point(Way wy, int i, int era, Place pl, List<Civ.Building> bs) {
        ServerWorld w = wy.w;
        int x = wy.xs[i], z = wy.zs[i];
        if (Math.max(Math.abs(x - wy.xs[0]), Math.abs(z - wy.zs[0])) < 12) return;
        if (Math.max(Math.abs(x - wy.xs[wy.n - 1]), Math.abs(z - wy.zs[wy.n - 1])) < 12) return;
        Random r = w.getRandom();
        int deck = smooth(wy, i);
        if (deck == NONE) return;
        double px = -wy.tz[i], pz = wy.tx[i];
        boolean water = false;
        Set<Long> seen = new HashSet<>();
        for (int s2 = -5; s2 <= 5; s2++) {
            double o = s2 * 0.5;
            int cx = (int) Math.round(x + px * o), cz = (int) Math.round(z + pz * o);
            long key = BlockPos.asLong(cx, 0, cz);
            if (!seen.add(key)) continue;
            if (!w.isChunkLoaded(cx >> 4, cz >> 4) || Roads.inBuilding(bs, cx, cz)) continue;
            boolean edge = Math.abs(o) > 1.6;
            Boolean was = wy.cells.get(key);
            if (was != null && (was || edge)) continue;
            wy.cells.put(key, !edge);
            int g = TownBuilder.ground(w, cx, cz);
            BlockState s = w.getBlockState(new BlockPos(cx, g - 1, cz));
            if (s.isOf(Blocks.LAVA)) continue;
            if (!s.getFluidState().isEmpty()) {
                water = true;
                int y = Math.max(deck, g);
                pl.put(new BlockPos(cx, y, cz), bridge(era, edge));
                for (int yy = y + 1; yy <= y + 3; yy++) clearUp(w, pl, new BlockPos(cx, yy, cz));
                if (Math.abs(o) >= 2.4) pl.put(new BlockPos(cx, y + 1, cz), rail(era));
                continue;
            }
            int sy = g - 1, y = deck;
            if (y > sy + 8) y = sy + 8;
            if (y < sy - 6) y = sy - 6;
            for (int yy = sy + 1; yy < y; yy++) pl.put(new BlockPos(cx, yy, cz), fill(era));
            for (int yy = y + 1; yy <= sy; yy++) pl.put(new BlockPos(cx, yy, cz), Blocks.AIR);
            pl.put(new BlockPos(cx, y, cz), road(era, edge, i, r));
            int top = Math.max(y, sy);
            for (int yy = top + 1; yy <= top + 5; yy++) clearUp(w, pl, new BlockPos(cx, yy, cz));
        }
        if (water) {
            if (i % 7 == 0) for (int s2 = -4; s2 <= 4; s2 += 4) {
                double o = s2 * 0.5;
                int cx = (int) Math.round(x + px * o), cz = (int) Math.round(z + pz * o);
                if (!w.isChunkLoaded(cx >> 4, cz >> 4)) continue;
                int g = TownBuilder.ground(w, cx, cz), y = Math.max(deck, g);
                for (int yy = y - 1, c = 0; c < 48 && yy > w.getBottomY(); yy--, c++) {
                    BlockPos p = new BlockPos(cx, yy, cz);
                    BlockState st = w.getBlockState(p);
                    if (st.getFluidState().isEmpty() && !st.isAir() && !st.isReplaceable()) break;
                    pl.put(p, pillar(era));
                }
            }
            if (i % 8 == 4) for (int sd = -1; sd <= 1; sd += 2) {
                int cx = (int) Math.round(x + px * 2.5 * sd), cz = (int) Math.round(z + pz * 2.5 * sd);
                if (!w.isChunkLoaded(cx >> 4, cz >> 4)) continue;
                int g = TownBuilder.ground(w, cx, cz), y = Math.max(deck, g);
                pl.put(new BlockPos(cx, y + 2, cz), era == 9 ? Blocks.SOUL_LANTERN : Blocks.LANTERN);
            }
            return;
        }
        if (i % 14 == 0) {
            int sd = (i / 14) % 2 == 0 ? 1 : -1;
            int cx = (int) Math.round(x + px * 3.5 * sd), cz = (int) Math.round(z + pz * 3.5 * sd);
            if (free(wy, bs, cx, cz)) lamp(w, pl, era, cx, cz);
        }
        if (i % 22 == 11) {
            int sd = (i / 22) % 2 == 0 ? -1 : 1;
            int cx = (int) Math.round(x + px * 5.5 * sd), cz = (int) Math.round(z + pz * 5.5 * sd);
            if (free(wy, bs, cx, cz)) tree(w, pl, wy.k, cx, cz);
        }
        if (i % 64 == 32) {
            int sd = (i / 64) % 2 == 0 ? 1 : -1;
            int cx = (int) Math.round(x + px * 3.5 * sd), cz = (int) Math.round(z + pz * 3.5 * sd);
            if (free(wy, bs, cx, cz)) milestone(w, pl, era, wy.k, cx, cz);
        }
        if (!wy.inn && wy.n >= 400 && i >= wy.n / 2) {
            for (int sd = 1; sd >= -1 && !wy.inn; sd -= 2) {
                int cx = (int) Math.round(x + px * 9 * sd), cz = (int) Math.round(z + pz * 9 * sd);
                if (!w.isChunkLoaded(cx >> 4, cz >> 4) || !TownBuilder.dryArea(w, cx, cz, 4)) continue;
                boolean clash = false;
                for (int dx = -4; dx <= 4 && !clash; dx += 2) for (int dz = -4; dz <= 4; dz += 2) if (Roads.inBuilding(bs, cx + dx, cz + dz)) { clash = true; break; }
                if (clash) continue;
                inn(wy, pl, era, cx, cz, -px * sd, -pz * sd);
                wy.inn = true;
            }
            if (i > wy.n / 2 + 120) wy.inn = true;
        }
    }

    static void lamp(ServerWorld w, Place pl, int era, int x, int z) {
        BlockPos b = new BlockPos(x, TownBuilder.ground(w, x, z), z);
        Block post, light;
        int h;
        if (era <= 1) { post = Blocks.OAK_FENCE; light = Blocks.TORCH; h = 1; }
        else if (era <= 3) { post = Blocks.SPRUCE_FENCE; light = Blocks.LANTERN; h = 2; }
        else if (era <= 5) { post = Blocks.COBBLESTONE_WALL; light = Blocks.LANTERN; h = 2; }
        else if (era <= 8) { post = Blocks.STONE_BRICK_WALL; light = Blocks.LANTERN; h = 3; }
        else if (era == 9) { post = Blocks.DEEPSLATE_BRICK_WALL; light = Blocks.SOUL_LANTERN; h = 3; }
        else { post = Blocks.POLISHED_BLACKSTONE_WALL; light = Blocks.LANTERN; h = 3; }
        if (era >= 6) { pl.put(b, era >= 10 ? Blocks.WAXED_CUT_COPPER : Blocks.POLISHED_ANDESITE); b = b.up(); h--; }
        for (int k = 0; k < h; k++) pl.put(b.up(k), post);
        pl.put(b.up(h), light);
        if (era >= 9) pl.put(b.up(h + 1), era == 9 ? Blocks.AMETHYST_CLUSTER : Blocks.LIGHTNING_ROD);
    }

    static void tree(ServerWorld w, Place pl, Kingdoms.K k, int x, int z) {
        int g = TownBuilder.ground(w, x, z);
        if (!w.getBlockState(new BlockPos(x, g - 1, z)).isIn(BlockTags.DIRT)) return;
        for (int h = 0; h < 6; h++) {
            BlockState s = w.getBlockState(new BlockPos(x, g + h, z));
            if (!s.isAir() && !s.isReplaceable()) return;
        }
        Block log, leaf;
        switch (k) {
            case RED -> { log = Blocks.CHERRY_LOG; leaf = Blocks.CHERRY_LEAVES; }
            case BLUE -> { log = Blocks.SPRUCE_LOG; leaf = Blocks.SPRUCE_LEAVES; }
            case GREEN -> { log = Blocks.OAK_LOG; leaf = Blocks.OAK_LEAVES; }
            case ORANGE, BROWN -> { log = Blocks.ACACIA_LOG; leaf = Blocks.ACACIA_LEAVES; }
            case PURPLE, BLACK -> { log = Blocks.DARK_OAK_LOG; leaf = Blocks.DARK_OAK_LEAVES; }
            case CYAN, LIGHT_BLUE -> { log = Blocks.JUNGLE_LOG; leaf = Blocks.JUNGLE_LEAVES; }
            case PINK -> { log = Blocks.CHERRY_LOG; leaf = Blocks.FLOWERING_AZALEA_LEAVES; }
            default -> { log = Blocks.BIRCH_LOG; leaf = Blocks.BIRCH_LEAVES; }
        }
        BlockState lv = leaf.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int h = 0; h < 4; h++) pl.put(new BlockPos(x, g + h, z), log);
        for (int y = g + 2; y <= g + 5; y++) {
            int rad = y <= g + 3 ? 2 : 1;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) == rad && Math.abs(dz) == rad && rad > 1) continue;
                    if (y == g + 5 && Math.abs(dx) + Math.abs(dz) > 1) continue;
                    if (dx == 0 && dz == 0 && y < g + 4) continue;
                    BlockPos p = new BlockPos(x + dx, y, z + dz);
                    if (w.getBlockState(p).isAir()) pl.put(p, lv);
                }
        }
    }

    static void milestone(ServerWorld w, Place pl, int era, Kingdoms.K k, int x, int z) {
        BlockPos b = new BlockPos(x, TownBuilder.ground(w, x, z), z);
        Block base = era < 4 ? Blocks.COBBLESTONE : era < 9 ? Blocks.CHISELED_STONE_BRICKS : era == 9 ? Blocks.CHISELED_DEEPSLATE : Blocks.CHISELED_POLISHED_BLACKSTONE;
        pl.put(b, base);
        pl.put(b.up(), banner(k));
    }

    static void inn(Way wy, Place pl, int era, int cx, int cz, double ux, double uz) {
        ServerWorld w = wy.w;
        int gy = TownBuilder.ground(w, cx, cz);
        int[][] doors = {{0, -3}, {0, 3}, {-3, 0}, {3, 0}};
        int[] door = doors[0];
        double best = -9;
        for (int[] d : doors) {
            double v = d[0] * ux + d[1] * uz;
            if (v > best) { best = v; door = d; }
        }
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx, z = cz + dz;
                for (int yy = gy - 2; yy >= gy - 5; yy--) {
                    BlockPos p = new BlockPos(x, yy, z);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir() || st.isReplaceable()) pl.put(p, st(era, Blocks.COBBLESTONE));
                }
                boolean in = Math.abs(dx) < 3 && Math.abs(dz) < 3;
                pl.put(new BlockPos(x, gy - 1, z), in ? st(era, Blocks.SPRUCE_PLANKS) : st(era, Blocks.COBBLESTONE));
                for (int h = 0; h <= 6; h++) pl.put(new BlockPos(x, gy + h, z), Blocks.AIR.getDefaultState());
                boolean wall = !in, corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                if (wall) for (int h = 0; h < 4; h++) {
                    BlockPos p = new BlockPos(x, gy + h, z);
                    boolean isDoor = dx == door[0] && dz == door[1] && h < 2;
                    if (isDoor) continue;
                    boolean window = !corner && h == 1 && (dx == 0 || dz == 0 || Math.abs(dx) == 1 && Math.abs(dz) == 3 || Math.abs(dz) == 1 && Math.abs(dx) == 3);
                    pl.put(p, corner ? st(era, Blocks.OAK_LOG) : window ? st(era, Blocks.GLASS_PANE) : st(era, Blocks.OAK_PLANKS));
                }
                pl.put(new BlockPos(x, gy + 4, z), st(era, Blocks.DARK_OAK_PLANKS));
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) pl.put(new BlockPos(x, gy + 5, z), st(era, Blocks.DARK_OAK_PLANKS));
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) pl.put(new BlockPos(x, gy + 6, z), st(era, Blocks.DARK_OAK_SLAB));
            }
        pl.put(new BlockPos(cx + 2, gy, cz + 2), Blocks.LANTERN);
        pl.put(new BlockPos(cx - 2, gy, cz - 2), Blocks.BARREL);
        pl.put(new BlockPos(cx - 2, gy, cz + 2), Blocks.CRAFTING_TABLE);
        pl.put(new BlockPos(cx + 2, gy, cz - 2), Blocks.SMOKER);
        pl.put(new BlockPos(cx - 2, gy, cz), Blocks.WHITE_BED);
        pl.put(new BlockPos(cx + 2, gy + 6, cz - 2), Blocks.CAMPFIRE);
        pl.put(new BlockPos(cx + door[0] + (int) Math.signum(door[0]), gy, cz + door[1] + (int) Math.signum(door[1])), Blocks.AIR);
        int hx = cx + door[0] + (door[1] != 0 ? 2 : (int) Math.signum(door[0])), hz = cz + door[1] + (door[0] != 0 ? 2 : (int) Math.signum(door[1]));
        pl.put(new BlockPos(hx, gy, hz), Blocks.HAY_BLOCK);
        pl.put(new BlockPos(hx, gy + 1, hz), Blocks.LANTERN);
        Chronicle.say(w.getServer(), wy.k, "открывает постоялый двор на дороге «" + wy.name + "»");
    }

    private static BlockState st(int era, Block b) { return Eras.style(era, Civ.Kind.HOUSE, b.getDefaultState()); }

    // ---------------- строительство ----------------
    static void step(Way wy, int count) {
        Place pl = new Place(wy.w);
        List<Civ.Building> bs = allBuildings();
        int era = Civ.city(wy.k).era;
        for (int c = 0; c < count && wy.i < wy.n; c++, wy.i++) {
            int x = wy.xs[wy.i], z = wy.zs[wy.i];
            if (!wy.w.isChunkLoaded(x >> 4, z >> 4)) { wy.miss.add(wy.i); continue; }
            point(wy, wy.i, era, pl, bs);
        }
        if (!pl.ps.isEmpty()) Tasks.add(pl);
    }

    static void retry(Way wy) {
        Place pl = new Place(wy.w);
        List<Civ.Building> bs = allBuildings();
        int era = Civ.city(wy.k).era, done = 0;
        for (Iterator<Integer> it = wy.miss.iterator(); it.hasNext() && done < 400; ) {
            int j = it.next();
            if (!wy.w.isChunkLoaded(wy.xs[j] >> 4, wy.zs[j] >> 4)) continue;
            point(wy, j, era, pl, bs);
            it.remove();
            done++;
        }
        if (!pl.ps.isEmpty()) Tasks.add(pl);
    }

    static boolean active(String key) {
        for (Way wy : WAYS) if (wy.key.equals(key)) return true;
        return false;
    }

    static boolean busy(Kingdoms.K k) {
        for (Way wy : WAYS) if (wy.k == k) return true;
        return false;
    }

    static boolean start(ServerWorld w, Kingdoms.K k, BlockPos a, BlockPos b) {
        String key = Roads.key(a, b);
        if (active(key)) return false;
        Way wy = plan(w, k, a, b, key);
        if (wy == null) return false;
        WAYS.add(wy);
        Chronicle.say(w.getServer(), k, "начинает прокладывать «" + wy.name + "» (" + (int) Math.sqrt(a.getSquaredDistance(b)) + " блоков)");
        return true;
    }

    static void finish(MinecraftServer s, Way wy) {
        WAYS.remove(wy);
        DONE.add(wy.key);
        BUILT.merge(wy.k, 1, Integer::sum);
        Chronicle.say(s, wy.k, "завершает «" + wy.name + "»");
    }

    static BlockPos[] choose(Kingdoms.K k) {
        List<BlockPos> nodes = new ArrayList<>();
        nodes.add(k.capital);
        nodes.addAll(Conquest.towns(k));
        BlockPos[] best = null;
        double bd = Double.MAX_VALUE;
        for (int a = 1; a < nodes.size(); a++) {
            BlockPos p = nodes.get(a), q = null;
            double qd = Double.MAX_VALUE;
            for (int b = 0; b < nodes.size(); b++) {
                if (b == a) continue;
                double d = p.getSquaredDistance(nodes.get(b));
                if (d < qd) { qd = d; q = nodes.get(b); }
            }
            for (BlockPos o : new BlockPos[]{q, k.capital}) {
                if (o == null || o.equals(p)) continue;
                double d = p.getSquaredDistance(o);
                if (d < 100 * 100 || d > 2000.0 * 2000.0) continue;
                String key = Roads.key(o, p);
                if (DONE.contains(key) || active(key)) continue;
                if (d < bd) { bd = d; best = new BlockPos[]{o, p}; }
            }
        }
        if (best != null) return best;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.capital == null || o.world != k.world) continue;
            if (Diplomacy.rel(k, o) != Diplomacy.ALLY && Vassals.lord(k) != o) continue;
            double d = k.capital.getSquaredDistance(o.capital);
            if (d < 100 * 100 || d > 2000.0 * 2000.0) continue;
            String key = Roads.key(k.capital, o.capital);
            if (DONE.contains(key) || active(key)) continue;
            return new BlockPos[]{k.capital, o.capital};
        }
        return null;
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 10 == 0) for (Way wy : new ArrayList<>(WAYS)) {
            if (!wy.k.alive) { WAYS.remove(wy); continue; }
            if (wy.i < wy.n) step(wy, 10);
            else if (wy.miss.isEmpty() || wy.pass > 15) finish(s, wy);
            else if (t % 1200 == 0) { wy.pass++; retry(wy); }
        }
        if (!on || t % 3000 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w)) continue;
            Civ.City c = Civ.city(k);
            if (c.era < 2 && !Tech.has(k, Tech.T.WHEEL)) continue;
            if (c.stone < 20 || c.wood < 10 || busy(k)) continue;
            BlockPos[] pair = choose(k);
            if (pair == null) continue;
            if (start(w, k, pair[0], pair[1])) { c.stone -= 20; c.wood -= 10; }
        }
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null || k.capital == null) {
            god.sendMessage(Text.literal("Рядом нет королевства").formatted(Formatting.GRAY), true);
            return;
        }
        BlockPos to = TownBuilder.landOr(w, Fx.surface(w, pos.getX(), pos.getZ()));
        if (k.capital.getSquaredDistance(to) < 30 * 30) {
            god.sendMessage(Text.literal("Слишком близко к столице — укажи точку подальше").formatted(Formatting.GRAY), true);
            return;
        }
        String key = Roads.key(k.capital, to);
        DONE.remove(key);
        if (start(w, k, k.capital, to)) god.sendMessage(Text.literal("🛣 " + k.title + ": тракт прокладывается сюда").formatted(k.color), true);
        else god.sendMessage(Text.literal("Этот тракт уже строится или слишком длинный").formatted(Formatting.GRAY), true);
    }

    static int count(Kingdoms.K k) { return BUILT.getOrDefault(k, 0); }

    public static void clear() {
        DONE.clear();
        WAYS.clear();
        BUILT.clear();
        t = 0;
    }
}
