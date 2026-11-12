package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** v1.6: снос построек и руины. */
public final class Demolish {
    static final int FLAGS = Block.NOTIFY_ALL | Block.SKIP_DROPS;

    private Demolish() {}

    /** Естественный блок (земля, камень, растения, деревья) — его не трогаем. */
    static boolean natural(ServerWorld w, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.isOf(Blocks.BEDROCK)) return true;
        if (s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FLOWERS)
            || s.isIn(BlockTags.SAPLINGS) || s.isIn(BlockTags.SAND) || s.isIn(BlockTags.ICE) || s.isIn(BlockTags.SNOW)) return true;
        if (s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SANDSTONE) || s.isOf(Blocks.TERRACOTTA) || s.isOf(Blocks.CACTUS)
            || s.isOf(Blocks.SUGAR_CANE) || s.isOf(Blocks.VINE) || s.isOf(Blocks.DEAD_BUSH) || s.isOf(Blocks.SEAGRASS)
            || s.isOf(Blocks.KELP) || s.isOf(Blocks.KELP_PLANT) || s.isOf(Blocks.SNOW_BLOCK) || Ores.isOre(s)) return true;
        if (s.isReplaceable() && !s.isOf(Blocks.FIRE)) return true;
        if (s.isIn(BlockTags.LOGS)) {
            for (BlockPos q : BlockPos.iterate(p.add(-2, -1, -2), p.add(2, 3, 2))) if (w.getBlockState(q).isIn(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    /** Снести одну постройку под курсором (ruins = оставить заросшие развалины). */
    public static void one(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush, boolean ruins) {
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world != w) continue;
            Civ.City c = Civ.city(k);
            for (Civ.Building b : new ArrayList<>(c.buildings)) {
                if (!b.inside(pos.getX(), pos.getZ(), 1)) continue;
                forget(c, b);
                collapse(w, b.x0 - 1, b.z0 - 1, b.x1 + 1, b.z1 + 1, b.y0, b.y0 + 18, ruins, false);
                god.sendMessage(Text.literal("🏚 " + b.kind.title + " (" + k.title + ") " + (ruins ? "превращена в руины" : "разрушена")).formatted(k.color), true);
                return;
            }
        }
        int r = ruins ? 3 + brush : 5;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int n = collapse(w, pos.getX() - r, pos.getZ() - r, pos.getX() + r, pos.getZ() + r, Math.min(y, pos.getY()) - 2, Math.max(y, pos.getY()) + 20, ruins, true);
        god.sendMessage(Text.literal(n > 0 ? "🏚 Постройки разрушены (" + n + " блоков)" : "Здесь нечего сносить").formatted(Formatting.GRAY), true);
    }

    /** Снести все постройки в радиусе кисти. */
    public static void area(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush) {
        int r = 4 + brush * 3, cnt = 0;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world != w) continue;
            Civ.City c = Civ.city(k);
            for (Civ.Building b : new ArrayList<>(c.buildings)) {
                if (!b.inside(pos.getX(), pos.getZ(), r)) continue;
                forget(c, b);
                cnt++;
            }
        }
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int n = collapse(w, pos.getX() - r, pos.getZ() - r, pos.getX() + r, pos.getZ() + r, Math.min(y, pos.getY()) - 4, Math.max(y, pos.getY()) + 24, false, true);
        god.sendMessage(Text.literal("🏚 Снесено: зданий " + cnt + ", блоков " + n).formatted(Formatting.GRAY), true);
    }

    static void forget(Civ.City c, Civ.Building b) {
        c.buildings.remove(b);
        if (c.project != null && c.project.b == b) c.project = null;
        if (c.quarry == b) c.quarry = null;
    }

    /** Запускает послойное обрушение сверху вниз. @return число блоков */
    static int collapse(ServerWorld w, int x0, int z0, int x1, int z1, int y0, int y1, boolean ruins, boolean onlyManMade) {
        List<List<BlockPos>> layers = new ArrayList<>();
        int total = 0;
        int bottom = onlyManMade ? y0 : y0 + 1;
        for (int y = y1; y >= bottom; y--) {
            List<BlockPos> layer = new ArrayList<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = w.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty() && s.isOf(Blocks.WATER)) continue;
                    if (onlyManMade ? natural(w, p, s) : (s.isIn(BlockTags.LEAVES) || s.isOf(Blocks.BEDROCK))) continue;
                    layer.add(p);
                }
            }
            if (!layer.isEmpty()) {
                layers.add(layer);
                total += layer.size();
            }
        }
        if (total == 0) return 0;
        final double mx = (x0 + x1) / 2.0 + 0.5, mz = (z0 + z1) / 2.0 + 0.5;
        final int my = y0 + 2;
        Fx.sound(w, mx, my, mz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.7f, 0.6f);
        Tasks.add(new Tasks.Task() {
            int i, t;

            @Override
            public boolean tick() {
                if (++t % 2 != 0) return false;
                if (i >= layers.size()) {
                    Fx.particles(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, mx, my, mz, 20, (x1 - x0) / 3.0, 1, (z1 - z0) / 3.0, 0.02);
                    return true;
                }
                Random r = w.getRandom();
                List<BlockPos> layer = layers.get(i++);
                double keep = ruins ? 0.15 + 0.7 * (i / (double) layers.size()) : 0;
                int n = 0;
                for (BlockPos p : layer) {
                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (ruins && r.nextDouble() < keep) {
                        if (s.isOf(Blocks.COBBLESTONE)) w.setBlockState(p, Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
                        else if (s.isOf(Blocks.STONE_BRICKS)) w.setBlockState(p, (r.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).getDefaultState(), FLAGS);
                        else if (s.isIn(BlockTags.PLANKS) && r.nextInt(3) == 0) w.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
                        continue;
                    }
                    if (s.isOf(Blocks.FARMLAND)) {
                        w.setBlockState(p, Blocks.DIRT.getDefaultState(), FLAGS);
                        continue;
                    }
                    if (!ruins && r.nextInt(9) == 0 && s.isFullCube(w, p) && n < 12) {
                        FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, p, s);
                        fb.dropItem = false;
                        n++;
                        continue;
                    }
                    if (r.nextInt(5) == 0) Fx.particles(w, ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
                    w.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
                }
                BlockPos c0 = layer.get(0);
                Fx.sound(w, c0.getX(), c0.getY(), c0.getZ(), SoundEvents.BLOCK_STONE_BREAK, 1f, 0.6f + r.nextFloat() * 0.4f);
                if (ruins && i >= layers.size()) {
                    for (BlockPos p : layer) {
                        BlockPos up = p.up();
                        if (w.getBlockState(up).isAir() && w.getBlockState(p).isSolidBlock(w, p) && r.nextInt(6) == 0)
                            w.setBlockState(up, (r.nextBoolean() ? Blocks.MOSS_CARPET : Blocks.COBWEB).getDefaultState(), FLAGS);
                    }
                }
                return false;
            }
        });
        return total;
    }
}
