package com.vladik.godbox;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** v2.7: жители чинят постройки после взрывов, пожаров, лавы, потопов, землетрясений и метеоритов. */
public final class Repair {
    static final class Snap {
        final long[] pos;
        final BlockState[] st;
        final int solid;

        Snap(long[] pos, BlockState[] st, int solid) {
            this.pos = pos;
            this.st = st;
            this.solid = solid;
        }
    }

    private static final Map<Civ.Building, Snap> SNAPS = new WeakHashMap<>();
    private static int timer;

    private Repair() {}

    /** Блоки, которые не восстанавливаем (двусоставные, с данными, посевы, жидкости). */
    static boolean skip(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BedBlock || b instanceof DoorBlock || b instanceof TallPlantBlock || b instanceof CropBlock
                || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.isOf(Blocks.FARMLAND) || b instanceof AbstractFireBlock;
    }

    /** Следы катастроф: пустота, огонь, жидкости, застывшая лава, песок, паутина, лёд, пепел. */
    static boolean debris(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.getBlock() instanceof AbstractFireBlock) return true;
        return s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.CRYING_OBSIDIAN) || s.isOf(Blocks.MAGMA_BLOCK) || s.isOf(Blocks.COBWEB)
                || s.isOf(Blocks.BASALT) || s.isOf(Blocks.SMOOTH_BASALT) || s.isOf(Blocks.NETHERRACK) || s.isOf(Blocks.SAND)
                || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.BLACKSTONE)
                || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.POWDER_SNOW) || s.isOf(Blocks.ICE) || s.isOf(Blocks.PACKED_ICE)
                || s.isOf(Blocks.BLUE_ICE) || s.isOf(Blocks.FROSTED_ICE) || s.isOf(Blocks.SOUL_SAND) || s.isOf(Blocks.SOUL_SOIL)
                || s.isOf(Blocks.SLIME_BLOCK) || s.isOf(Blocks.COARSE_DIRT);
    }

    /** Запоминает целую постройку (вызывается после стройки и после ремонта). */
    public static void snapshot(ServerWorld w, Civ.Building b) {
        if (b.kind == Civ.Kind.QUARRY) return;
        int maxY = b.y0;
        for (int x = b.x0; x <= b.x1; x++)
            for (int z = b.z0; z <= b.z1; z++)
                for (int y = b.y0; y <= b.y0 + 26; y++)
                    if (!w.getBlockState(new BlockPos(x, y, z)).isAir()) maxY = Math.max(maxY, y);
        List<Long> ps = new ArrayList<>();
        List<BlockState> ss = new ArrayList<>();
        int solid = 0;
        for (int x = b.x0; x <= b.x1; x++) {
            for (int z = b.z0; z <= b.z1; z++) {
                for (int y = b.y0 - 2; y <= maxY; y++) {
                    BlockPos q = new BlockPos(x, y, z);
                    BlockState s = w.getBlockState(q);
                    if (skip(s)) continue;
                    if (s.isAir() && y < b.y0) continue;
                    ps.add(q.asLong());
                    ss.add(s);
                    if (!s.isAir()) solid++;
                }
            }
        }
        long[] pa = new long[ps.size()];
        for (int i = 0; i < pa.length; i++) pa[i] = ps.get(i);
        SNAPS.put(b, new Snap(pa, ss.toArray(new BlockState[0]), solid));
    }

    static boolean damaged(BlockState want, BlockState cur) {
        if (cur.getBlock() == want.getBlock()) return false;
        if (want.isAir()) return !cur.isAir() && debris(cur);
        return debris(cur);
    }

    static List<Integer> scan(ServerWorld w, Snap sn) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < sn.pos.length; i++) {
            if (damaged(sn.st[i], w.getBlockState(BlockPos.fromLong(sn.pos[i])))) out.add(i);
        }
        return out;
    }

    public static void tick(MinecraftServer server) {
        if (++timer % 200 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world == null || k.capital == null) continue;
            ServerWorld w = k.world;
            Civ.City c = Civ.city(k);
            if (c == null || c.project != null) continue;
            Civ.Building worst = null;
            List<Integer> worstList = null;
            for (Civ.Building b : new ArrayList<>(c.buildings)) {
                if (!b.done || b.kind == Civ.Kind.QUARRY) continue;
                if (!w.isChunkLoaded(new BlockPos(b.x0, b.y0, b.z0)) || !w.isChunkLoaded(new BlockPos(b.x1, b.y0, b.z1))) continue;
                Snap sn = SNAPS.get(b);
                if (sn == null) {
                    snapshot(w, b);
                    continue;
                }
                List<Integer> d = scan(w, sn);
                if (d.size() >= 3 && (worstList == null || d.size() > worstList.size())) {
                    worst = b;
                    worstList = d;
                }
            }
            if (worst != null) start(k, c, w, worst, SNAPS.get(worst), worstList);
        }
    }

    static void start(Kingdoms.K k, Civ.City c, ServerWorld w, Civ.Building b, Snap sn, List<Integer> d) {
        Civ.Project p = new Civ.Project(b);
        p.repair = true;
        List<Integer> clear = new ArrayList<>(), put = new ArrayList<>();
        for (int i : d) {
            if (sn.st[i].isAir()) clear.add(i);
            else put.add(i);
        }
        clear.sort((a, z) -> Integer.compare(BlockPos.unpackLongY(sn.pos[z]), BlockPos.unpackLongY(sn.pos[a])));
        put.sort((a, z) -> Integer.compare(BlockPos.unpackLongY(sn.pos[a]), BlockPos.unpackLongY(sn.pos[z])));
        for (int i : clear) p.add(BlockPos.fromLong(sn.pos[i]), sn.st[i]);
        for (int i : put) p.add(BlockPos.fromLong(sn.pos[i]), sn.st[i]);
        c.project = p;
        boolean ruin = sn.solid > 0 && put.size() * 100 / sn.solid >= 50;
        Civ.tell(w, Text.literal("🛠 " + k.title + (ruin ? ": отстраивают заново — " : ": чинят — ") + b.kind.title
                + " (повреждено блоков: " + d.size() + ")").formatted(k.color));
        if (ruin) Chronicle.log(k.title + " отстраивает разрушенное здание: " + b.kind.title);
    }

    public static void clear() {
        SNAPS.clear();
        timer = 0;
    }
}
