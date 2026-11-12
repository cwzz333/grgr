package com.vladik.godbox;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Расчистка площадки перед стройкой: вырубить деревья и кусты, срезать бугры, засыпать ямы. */
final class Clearing {
    private Clearing() {}

    /** Деревья, трава, цветы, снег, кактусы, грибы. */
    static boolean vegetation(BlockState s) {
        return s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.SAPLINGS)
                || s.isReplaceable() || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.BAMBOO)
                || s.isOf(Blocks.SWEET_BERRY_BUSH) || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || s.isOf(Blocks.RED_MUSHROOM_BLOCK)
                || s.isOf(Blocks.MUSHROOM_STEM) || s.isOf(Blocks.VINE) || s.isOf(Blocks.MOSS_CARPET) || s.isOf(Blocks.BEE_NEST);
    }

    /** Природный грунт, который можно срезать при выравнивании. */
    static boolean terrain(BlockState s) {
        return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.SAND)
                || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SANDSTONE)
                || s.isOf(Blocks.RED_SANDSTONE) || s.isOf(Blocks.ICE) || s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.POWDER_SNOW);
    }

    /** Добавляет расчистку в начало проекта. Возвращает число операций. */
    static int prepend(Civ.Project p, ServerWorld w) {
        Civ.Building b = p.b;
        List<BlockPos> cut = new ArrayList<>();
        List<BlockPos> fill = new ArrayList<>();
        int m = 2, maxY = b.y0 + 30;
        for (int x = b.x0 - m; x <= b.x1 + m; x++) {
            for (int z = b.z0 - m; z <= b.z1 + m; z++) {
                if (Civ.foreign(b, x, z)) continue;
                boolean core = x >= b.x0 - 1 && x <= b.x1 + 1 && z >= b.z0 - 1 && z <= b.z1 + 1;
                boolean inner = x >= b.x0 && x <= b.x1 && z >= b.z0 && z <= b.z1;
                int top = Math.min(w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), maxY);
                for (int y = top; y >= b.y0; y--) {
                    BlockPos q = new BlockPos(x, y, z);
                    BlockState s = w.getBlockState(q);
                    if (s.isAir() || !s.getFluidState().isEmpty() || w.getBlockEntity(q) != null) continue;
                    if (vegetation(s) || (core && terrain(s))) cut.add(q);
                }
                if (inner) {
                    for (int y = b.y0 - 3; y <= b.y0 - 1; y++) {
                        BlockPos q = new BlockPos(x, y, z);
                        BlockState s = w.getBlockState(q);
                        if (w.getBlockEntity(q) != null) continue;
                        if (!Civ.solid(w, q) || s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES)) fill.add(q);
                    }
                }
            }
        }
        if (cut.isEmpty() && fill.isEmpty()) return 0;
        cut.sort(Comparator.comparingInt(BlockPos::getY).reversed());
        fill.sort(Comparator.comparingInt(BlockPos::getY));
        List<BlockPos> pos = new ArrayList<>(cut.size() + fill.size() + p.pos.size());
        List<BlockState> st = new ArrayList<>(pos.size());
        BlockState air = Blocks.AIR.getDefaultState(), dirt = Blocks.DIRT.getDefaultState();
        for (BlockPos q : cut) { pos.add(q); st.add(air); }
        for (BlockPos q : fill) { pos.add(q); st.add(dirt); }
        pos.addAll(p.pos);
        st.addAll(p.st);
        p.pos.clear();
        p.st.clear();
        p.pos.addAll(pos);
        p.st.addAll(st);
        return cut.size() + fill.size();
    }
}
