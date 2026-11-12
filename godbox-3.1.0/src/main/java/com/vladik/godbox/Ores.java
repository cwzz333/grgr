package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** v1.6: руды как в WorldBox — рисуются кистью по поверхности, шахтёры королевств их добывают. */
public final class Ores {
    private static final Set<BlockPos> SPOTS = new HashSet<>();

    private Ores() {}

    static String mix(Random r) {
        int n = r.nextInt(100);
        return n < 32 ? "coal" : n < 56 ? "iron" : n < 70 ? "copper" : n < 80 ? "gold" : n < 87 ? "redstone"
            : n < 93 ? "lapis" : n < 98 ? "diamond" : "emerald";
    }

    public static Block ore(String kind, boolean deep, Random r) {
        if (kind == null || kind.equals("mix")) kind = mix(r);
        return switch (kind) {
            case "coal" -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
            case "copper" -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
            case "gold" -> deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
            case "redstone" -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
            case "lapis" -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
            case "diamond" -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
            case "emerald" -> deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
            case "debris" -> Blocks.ANCIENT_DEBRIS;
            default -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
        };
    }

    public static boolean isOre(BlockState s) {
        if (s.isOf(Blocks.ANCIENT_DEBRIS)) return true;
        return Registries.BLOCK.getId(s.getBlock()).getPath().endsWith("_ore");
    }

    static boolean ground(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        return s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.DIRT) || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND)
            || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SANDSTONE) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.CLAY)
            || s.isOf(Blocks.TERRACOTTA) || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.MOSSY_COBBLESTONE) || isOre(s);
    }

    static boolean passable(BlockState s) {
        return s.isAir() || s.isReplaceable() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.SAPLINGS);
    }

    public static void paint(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush, String kind, Random r) {
        int n = 0;
        for (BlockPos top : Fx.brush(w, pos, brush)) {
            if (r.nextFloat() > 0.55f) continue;
            BlockPos p = top.down();
            for (int i = 0; i < 10 && passable(w.getBlockState(p)) && !w.getBlockState(p).getFluidState().isEmpty() == false; i++) {
                if (ground(w.getBlockState(p))) break;
                p = p.down();
            }
            if (!ground(w.getBlockState(p))) continue;
            BlockState above = w.getBlockState(p.up());
            if (!above.isAir() && above.isReplaceable() && above.getFluidState().isEmpty()) w.setBlockState(p.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            String k = kind.equals("mix") ? mix(r) : kind;
            int depth = 1 + r.nextInt(3);
            for (int d = 0; d < depth; d++) {
                BlockPos q = p.down(d);
                BlockState s = w.getBlockState(q);
                if (!ground(s)) break;
                boolean deep = s.isOf(Blocks.DEEPSLATE) || q.getY() < 0;
                w.setBlockState(q, ore(k, deep, r).getDefaultState(), Block.NOTIFY_ALL);
                SPOTS.add(q.toImmutable());
            }
            n++;
        }
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        Fx.particles(w, ParticleTypes.WAX_ON, cx, pos.getY() + 1.2, cz, 30 + brush * 10, brush, 0.6, brush, 0.5);
        Fx.sound(w, cx, pos.getY() + 1, cz, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f + r.nextFloat() * 0.4f);
        if (n == 0) god.sendMessage(Text.literal("Руде нужна земля или камень").formatted(Formatting.GRAY), true);
        if (SPOTS.size() > 30000) SPOTS.clear();
    }

    /** Ближайшая открытая руда возле города для шахтёра (или null). */
    static BlockPos prospect(Kingdoms.K k, Civ.City c, ServerWorld w, VillagerEntity v) {
        if (k.capital == null) return null;
        Random r = w.getRandom();
        int rad = Civ.radius(c) + 24;
        for (int i = 0; i < 24; i++) {
            int x = k.capital.getX() + r.nextInt(rad * 2 + 1) - rad, z = k.capital.getZ() + r.nextInt(rad * 2 + 1) - rad;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            for (int d = 1; d <= 3; d++) {
                BlockPos q = top.down(d);
                if (isOre(w.getBlockState(q))) SPOTS.add(q.toImmutable());
            }
        }
        BlockPos best = null;
        double bd = (rad + 40.0) * (rad + 40.0);
        Iterator<BlockPos> it = SPOTS.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            double d = p.getSquaredDistance(k.capital);
            if (d > bd) continue;
            if (!w.isChunkLoaded(p)) continue;
            if (!isOre(w.getBlockState(p))) {
                it.remove();
                continue;
            }
            BlockState up = w.getBlockState(p.up());
            if (!up.isAir() && !up.isReplaceable()) continue;
            if (Civ.claimed(p)) continue;
            bd = d;
            best = p;
        }
        return best;
    }

    public static void clear() {
        SPOTS.clear();
    }
}
