package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** Строит новый город по блокам (с анимацией): площадь со знаменем, дома, ферма, в стиле эпохи. */
final class TownBuilder {
    private TownBuilder() {}

    static final class Job implements Tasks.Task {
        final ServerWorld w;
        final List<BlockPos> ps = new ArrayList<>();
        final List<BlockState> ss = new ArrayList<>();
        int i;

        Job(ServerWorld w) { this.w = w; }

        void put(BlockPos p, BlockState s) { ps.add(p); ss.add(s); }

        @Override
        public boolean tick() {
            for (int n = 0; n < 10 && i < ps.size(); n++, i++) {
                BlockPos p = ps.get(i);
                BlockState bs = ss.get(i);
                Block bb = bs.getBlock();
                if (bb instanceof net.minecraft.block.FenceBlock || bb instanceof net.minecraft.block.WallBlock || bb instanceof net.minecraft.block.PaneBlock)
                    bs = Block.postProcessState(bs, w, p);
                w.setBlockState(p, bs, Block.NOTIFY_ALL);
                if (i % 15 == 0) Fx.particles(w, ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
                if (i % 40 == 0) Fx.sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_WOOD_PLACE, 0.6f, 1f);
            }
            return i >= ps.size();
        }
    }

    static int ground(ServerWorld w, int x, int z) {
        BlockPos p = Fx.surface(w, x, z);
        while (p.getY() > w.getBottomY() + 1) {
            BlockState s = w.getBlockState(p.down());
            if (!s.getFluidState().isEmpty()) break;
            if (s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || s.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || s.isReplaceable()) p = p.down();
            else break;
        }
        return p.getY();
    }

    /** Сухая ли земля в точке (не вода/лава и не дно водоёма). */
    static boolean dry(ServerWorld w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
        int y = ground(w, x, z);
        BlockPos g = new BlockPos(x, y - 1, z);
        BlockState s = w.getBlockState(g);
        return s.getFluidState().isEmpty() && w.getFluidState(g.up()).isEmpty() && !s.isAir();
    }

    /** Вся площадка ±h сухая и примерно ровная. */
    static boolean dryArea(ServerWorld w, int x, int z, int h) {
        int y = ground(w, x, z), st = Math.max(2, h / 2);
        for (int dx = -h; dx <= h; dx += st)
            for (int dz = -h; dz <= h; dz += st) {
                if (!dry(w, x + dx, z + dz)) return false;
                if (Math.abs(ground(w, x + dx, z + dz) - y) > 5) return false;
            }
        return true;
    }

    /** Ближайшая сухая площадка под город в радиусе r (или null). */
    static BlockPos dryNear(ServerWorld w, BlockPos c, int r) {
        for (int d = 0; d <= r; d += 2)
            for (int dx = -d; dx <= d; dx += 2)
                for (int dz = -d; dz <= d; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    int x = c.getX() + dx, z = c.getZ() + dz;
                    if (dryArea(w, x, z, 10)) return new BlockPos(x, ground(w, x, z), z);
                }
        return null;
    }

    /** Если точка на воде — сдвинуть на ближайшую сушу. */
    static BlockPos landOr(ServerWorld w, BlockPos p) {
        if (dry(w, p.getX(), p.getZ())) return new BlockPos(p.getX(), ground(w, p.getX(), p.getZ()), p.getZ());
        for (int d = 2; d <= 48; d += 2)
            for (int dx = -d; dx <= d; dx += 2)
                for (int dz = -d; dz <= d; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    int x = p.getX() + dx, z = p.getZ() + dz;
                    if (dryArea(w, x, z, 4)) return new BlockPos(x, ground(w, x, z), z);
                }
        return p;
    }

    private static BlockState st(int era, Civ.Kind kind, Block b) { return Eras.style(era, kind, b.getDefaultState()); }

    static void flatten(Job j, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            for (int yy = y - 3; yy < y; yy++) {
                BlockPos p = new BlockPos(x, yy, z);
                if (!j.w.getBlockState(p).isSolidBlock(j.w, p)) j.put(p, Blocks.DIRT.getDefaultState());
            }
            for (int yy = y; yy <= y + 7; yy++) {
                BlockPos p = new BlockPos(x, yy, z);
                if (!j.w.getBlockState(p).isAir()) j.put(p, Blocks.AIR.getDefaultState());
            }
        }
    }

    // ---------------- v3.0: архитектура зданий ----------------
    static final int COTTAGE = 0, TALL = 1, LONG = 2;
    private static final Civ.Kind H = Civ.Kind.HOUSE;

    static BlockState hedge() {
        return Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true);
    }

    static void clear(Job j, int x0, int z0, int x1, int z1, int yFrom, int yTo) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = yFrom; y <= yTo; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!j.w.getBlockState(p).isAir()) j.put(p, Blocks.AIR.getDefaultState());
        }
    }

    /** Фундамент: каменный цоколь по периметру, дощатый пол внутри. */
    static void floor(Job j, int era, int x0, int z0, int x1, int z1, int y) {
        BlockState fnd = st(era, H, Blocks.STONE_BRICKS), fl = st(era, H, Blocks.SPRUCE_PLANKS);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            j.put(new BlockPos(x, y - 1, z), edge ? fnd : fl);
            if (edge) j.put(new BlockPos(x, y - 2, z), fnd);
        }
    }

    /** Стены этажа: брёвна в углах (и столбы каждые 4 блока у длинных стен), окна, нижний ряд камнем. */
    static void walls(Job j, int era, int x0, int z0, int x1, int z1, int y, int h, boolean door, boolean south,
                      BlockState log, BlockState plank, BlockState low) {
        BlockState glass = st(era, H, Blocks.GLASS);
        int cx = (x0 + x1) / 2, fz = south ? z1 : z0;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean ex = x == x0 || x == x1, ez = z == z0 || z == z1;
            if (!ex && !ez) continue;
            int i = ez ? x - x0 : z - z0, len = ez ? x1 - x0 : z1 - z0;
            for (int dy = 0; dy < h; dy++) {
                BlockPos p = new BlockPos(x, y + dy, z);
                if (ex && ez) j.put(p, log);
                else if (door && x == cx && z == fz && dy < 2) continue;
                else if (len >= 8 && i % 4 == 0) j.put(p, log);
                else if (dy == 1 && i % 4 == 2) j.put(p, glass);
                else j.put(p, dy == 0 && low != null ? low : plank);
            }
        }
    }

    /** Обвязка из балок (перекрытие). (hx,hz) — люк под лестницу. */
    static void ring(Job j, int x0, int z0, int x1, int z1, int y, BlockState log, BlockState plank, int hx, int hz) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean ex = x == x0 || x == x1, ez = z == z0 || z == z1;
            if (x == hx && z == hz) continue;
            BlockState c = ex && ez ? log : ez ? Arch.beam(log, Direction.Axis.X) : ex ? Arch.beam(log, Direction.Axis.Z) : plank;
            j.put(new BlockPos(x, y, z), c);
        }
    }

    /** Фасад: дверь, фонарь у входа, ставни, живая изгородь-«цветочные ящики». */
    static void front(Job j, int era, int x0, int z0, int x1, int z1, int y, boolean south) {
        Direction out = south ? Direction.SOUTH : Direction.NORTH;
        int fz = south ? z1 : z0, oz = south ? z1 + 1 : z0 - 1, cx = (x0 + x1) / 2;
        Arch.door(j::put, new BlockPos(cx, y, fz), out, Blocks.SPRUCE_DOOR);
        Arch.wallTorch(j::put, new BlockPos(cx + 1, y + 1, fz), out);
        for (int x = x0 + 1; x < x1; x++) {
            if (x == cx || x == cx + 1) continue;
            if (Math.abs(x - cx) <= 2 || (x - x0) % 4 == 2) j.put(new BlockPos(x, y, oz), hedge());
        }
        if (era >= 1) {
            for (int x = x0 + 1; x < x1; x++) if (x != cx && (x - x0) % 4 == 2) Arch.shutters(j::put, new BlockPos(x, y + 1, fz), out, Blocks.SPRUCE_TRAPDOOR);
            Arch.shutters(j::put, new BlockPos(x0, y + 1, z0 + 2), Direction.WEST, Blocks.SPRUCE_TRAPDOOR);
            Arch.shutters(j::put, new BlockPos(x1, y + 1, z0 + 2), Direction.EAST, Blocks.SPRUCE_TRAPDOOR);
        }
    }

    /** Совместимость: старый 5×5 дом, дверь на юг. */
    static void house(Job j, int era, int x0, int z0, int y) {
        cottage(j, era, x0, z0, y, true);
    }

    /** Домик 5×5: цоколь, балки, двускатная крыша, труба, огород позади. */
    static void cottage(Job j, int era, int x0, int z0, int y, boolean south) {
        int x1 = x0 + 4, z1 = z0 + 4, cx = x0 + 2, cz = z0 + 2;
        flatten(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y);
        clear(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + 8, y + 10);
        BlockState log = st(era, H, Blocks.OAK_LOG), plank = st(era, H, Blocks.OAK_PLANKS);
        BlockState roof = st(era, H, Blocks.DARK_OAK_PLANKS), low = st(era, H, Blocks.COBBLESTONE);
        floor(j, era, x0, z0, x1, z1, y);
        walls(j, era, x0, z0, x1, z1, y, 3, true, south, log, plank, low);
        ring(j, x0, z0, x1, z1, y + 3, log, plank, Integer.MIN_VALUE, 0);
        Arch.gable(j::put, x0, z0, x1, z1, y + 3, roof, plank, roof);
        int top = y + 3 + Arch.gableHeight(x0, z0, x1, z1);
        int bz = south ? z0 + 1 : z1 - 1;
        Direction out = south ? Direction.SOUTH : Direction.NORTH;
        Arch.chimney(j::put, x1 - 1, bz, y + 4, top, st(era, H, Blocks.BRICKS));
        front(j, era, x0, z0, x1, z1, y, south);
        Arch.bed(j::put, new BlockPos(x0 + 1, y, bz), out.getOpposite(), Blocks.WHITE_BED);
        j.put(new BlockPos(x1 - 1, y, bz), Blocks.CRAFTING_TABLE.getDefaultState());
        j.put(new BlockPos(x1 - 1, y + 1, bz), Arch.pot(x0 + z0));
        j.put(new BlockPos(x1 - 1, y, south ? z1 - 1 : z0 + 1), Blocks.BARREL.getDefaultState());
        j.put(new BlockPos(cx, y + 2, cz), Arch.hanging(Blocks.LANTERN));
    }

    /** Двухэтажный дом 5×6 с лестницей и балконом над входом. */
    static void tall(Job j, int era, int x0, int z0, int y, boolean south) {
        int x1 = x0 + 4, z1 = z0 + 5, cx = x0 + 2, cz = z0 + 2;
        Direction out = south ? Direction.SOUTH : Direction.NORTH;
        int fz = south ? z1 : z0, oz = south ? z1 + 1 : z0 - 1, oz2 = south ? z1 + 2 : z0 - 2;
        flatten(j, x0 - 1, Math.min(z0 - 1, oz2), x1 + 1, Math.max(z1 + 1, oz2), y);
        clear(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + 8, y + 12);
        BlockState log = st(era, H, Blocks.SPRUCE_LOG), plank = st(era, H, Blocks.OAK_PLANKS);
        BlockState roof = st(era, H, Blocks.DARK_OAK_PLANKS), low = st(era, H, Blocks.STONE_BRICKS);
        BlockState upper = st(era, H, Blocks.BIRCH_PLANKS);
        floor(j, era, x0, z0, x1, z1, y);
        walls(j, era, x0, z0, x1, z1, y, 3, true, south, log, plank, low);
        int lx = x0 + 1, lz = south ? z0 + 1 : z1 - 1;
        ring(j, x0, z0, x1, z1, y + 3, log, plank, lx, lz);
        walls(j, era, x0, z0, x1, z1, y + 4, 2, true, south, log, upper, null);
        ring(j, x0, z0, x1, z1, y + 6, log, plank, Integer.MIN_VALUE, 0);
        Arch.gable(j::put, x0, z0, x1, z1, y + 6, roof, upper, roof);
        int top = y + 6 + Arch.gableHeight(x0, z0, x1, z1);
        Arch.chimney(j::put, x1 - 1, lz, y + 7, top, st(era, H, Blocks.BRICKS));
        for (int dy = 0; dy <= 3; dy++)
            j.put(new BlockPos(lx, y + dy, lz), Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, out));
        front(j, era, x0, z0, x1, z1, y, south);
        // балкон: настил из верхних плит, перила, подпорки
        BlockState slab = Arch.slabOr(st(era, H, Blocks.SPRUCE_PLANKS));
        if (slab.contains(net.minecraft.block.SlabBlock.TYPE)) slab = slab.with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP);
        for (int x = x0; x <= x1; x++) {
            j.put(new BlockPos(x, y + 3, oz), slab);
            j.put(new BlockPos(x, y + 3, oz2), slab);
            j.put(new BlockPos(x, y + 4, oz2), Blocks.SPRUCE_FENCE.getDefaultState());
        }
        j.put(new BlockPos(x0, y + 4, oz), Blocks.SPRUCE_FENCE.getDefaultState());
        j.put(new BlockPos(x1, y + 4, oz), Blocks.SPRUCE_FENCE.getDefaultState());
        for (int dy = 0; dy < 3; dy++) {
            j.put(new BlockPos(x0, y + dy, oz2), Blocks.SPRUCE_FENCE.getDefaultState());
            j.put(new BlockPos(x1, y + dy, oz2), Blocks.SPRUCE_FENCE.getDefaultState());
        }
        j.put(new BlockPos(x0 + 1, y + 4, oz2), Blocks.SPRUCE_FENCE.getDefaultState());
        j.put(new BlockPos(x0 + 1, y + 5, oz2), Arch.pot(x0 * 7 + z0));
        Arch.door(j::put, new BlockPos(cx, y + 4, fz), out, Blocks.SPRUCE_DOOR);
        // первый этаж: кухня
        j.put(new BlockPos(x1 - 1, y, lz), Blocks.FURNACE.getDefaultState());
        j.put(new BlockPos(x1 - 1, y, cz + (south ? 1 : -1)), Blocks.CRAFTING_TABLE.getDefaultState());
        j.put(new BlockPos(x1 - 1, y, south ? z1 - 1 : z0 + 1), Blocks.BARREL.getDefaultState());
        j.put(new BlockPos(cx, y + 2, cz + 1), Arch.hanging(Blocks.LANTERN));
        // второй этаж: спальня
        Arch.bed(j::put, new BlockPos(x1 - 1, y + 4, lz), out.getOpposite(), Blocks.RED_BED);
        j.put(new BlockPos(cx, y + 4, lz), Blocks.CHEST.getDefaultState());
        j.put(new BlockPos(x0 + 1, y + 4, south ? z1 - 1 : z0 + 1), Blocks.BOOKSHELF.getDefaultState());
        j.put(new BlockPos(cx, y + 5, cz + 1), Arch.hanging(Blocks.LANTERN));
    }

    /** Длинный дом 9×5: столбы, два окна по фасаду, общий очаг. */
    static void longhouse(Job j, int era, int x0, int z0, int y, boolean south) {
        int x1 = x0 + 8, z1 = z0 + 4, cz = z0 + 2;
        flatten(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y);
        clear(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + 8, y + 10);
        BlockState log = st(era, H, Blocks.DARK_OAK_LOG), plank = st(era, H, Blocks.SPRUCE_PLANKS);
        BlockState roof = st(era, H, Blocks.SPRUCE_PLANKS), low = st(era, H, Blocks.MOSSY_COBBLESTONE);
        floor(j, era, x0, z0, x1, z1, y);
        walls(j, era, x0, z0, x1, z1, y, 3, true, south, log, plank, low);
        ring(j, x0, z0, x1, z1, y + 3, log, plank, Integer.MIN_VALUE, 0);
        Arch.gable(j::put, x0, z0, x1, z1, y + 3, roof, plank, st(era, H, Blocks.DARK_OAK_PLANKS));
        int top = y + 3 + Arch.gableHeight(x0, z0, x1, z1);
        int bz = south ? z0 + 1 : z1 - 1;
        Direction out = south ? Direction.SOUTH : Direction.NORTH;
        Arch.chimney(j::put, x1 - 2, bz, y + 4, top, st(era, H, Blocks.BRICKS));
        front(j, era, x0, z0, x1, z1, y, south);
        Arch.bed(j::put, new BlockPos(x0 + 1, y, bz), out.getOpposite(), Blocks.WHITE_BED);
        Arch.bed(j::put, new BlockPos(x0 + 2, y, bz), out.getOpposite(), Blocks.LIGHT_BLUE_BED);
        j.put(new BlockPos(x1 - 1, y, bz), Blocks.FURNACE.getDefaultState());
        j.put(new BlockPos(x1 - 3, y, bz), Blocks.CRAFTING_TABLE.getDefaultState());
        j.put(new BlockPos(x1 - 1, y, south ? z1 - 1 : z0 + 1), Blocks.CHEST.getDefaultState());
        j.put(new BlockPos(x0 + 4, y, bz), Blocks.BARREL.getDefaultState());
        j.put(new BlockPos(x0 + 4, y + 1, bz), Arch.pot(x0 + z0 * 3));
        j.put(new BlockPos(x0 + 2, y + 2, cz), Arch.hanging(Blocks.LANTERN));
        j.put(new BlockPos(x1 - 2, y + 2, cz), Arch.hanging(Blocks.LANTERN));
    }

    private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.LILY_OF_THE_VALLEY};

    /** Огороженный садик с цветами. */
    static void garden(Job j, int x0, int z0, int x1, int z1, int y, int seed) {
        flatten(j, x0, z0, x1, z1, y);
        int gx = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            j.put(new BlockPos(x, y - 1, z), Blocks.GRASS_BLOCK.getDefaultState());
            if (edge) {
                if (x == gx && (z == z0 || z == z1) && ((seed & 1) == 0 ? z == z0 : z == z1)) continue;
                j.put(new BlockPos(x, y, z), Blocks.OAK_FENCE.getDefaultState());
            } else j.put(new BlockPos(x, y, z), FLOWERS[Math.floorMod(seed * 3 + x * 7 + z * 5, FLOWERS.length)].getDefaultState());
        }
    }

    /** Колодец 3×3 под навесом. */
    static void well(Job j, int era, int cx, int cz, int y) {
        flatten(j, cx - 1, cz - 1, cx + 1, cz + 1, y);
        BlockState ring = st(era, H, Blocks.COBBLESTONE), wall = st(era, H, Blocks.COBBLESTONE_WALL);
        BlockState roof = Arch.slabOr(st(era, H, Blocks.SPRUCE_PLANKS));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = cx + dx, z = cz + dz;
            if (dx == 0 && dz == 0) {
                j.put(new BlockPos(x, y - 3, z), ring);
                j.put(new BlockPos(x, y - 2, z), Blocks.WATER.getDefaultState());
                j.put(new BlockPos(x, y - 1, z), Blocks.WATER.getDefaultState());
            } else {
                j.put(new BlockPos(x, y - 2, z), ring);
                j.put(new BlockPos(x, y - 1, z), ring);
                j.put(new BlockPos(x, y, z), wall);
                if (dx != 0 && dz != 0) {
                    j.put(new BlockPos(x, y + 1, z), Blocks.OAK_FENCE.getDefaultState());
                    j.put(new BlockPos(x, y + 2, z), Blocks.OAK_FENCE.getDefaultState());
                }
            }
            j.put(new BlockPos(x, y + 3, z), roof);
        }
        j.put(new BlockPos(cx, y + 2, cz), Arch.hanging(Blocks.LANTERN));
    }

    /** Рыночный прилавок 3×3 с полосатым навесом цвета королевства. */
    static void stall(Job j, Kingdoms.K k, int x0, int z0, int y, int seed) {
        flatten(j, x0, z0, x0 + 2, z0 + 2, y);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            boolean corner = (x == 0 || x == 2) && (z == 0 || z == 2);
            if (corner) {
                j.put(new BlockPos(x0 + x, y, z0 + z), Blocks.SPRUCE_FENCE.getDefaultState());
                j.put(new BlockPos(x0 + x, y + 1, z0 + z), Blocks.SPRUCE_FENCE.getDefaultState());
            }
            j.put(new BlockPos(x0 + x, y + 2, z0 + z), (x % 2 == 0 ? k.wool : Blocks.WHITE_WOOL).getDefaultState());
        }
        Block[] goods = {Blocks.HAY_BLOCK, Blocks.PUMPKIN, Blocks.MELON, Blocks.BARREL};
        j.put(new BlockPos(x0 + 1, y, z0), goods[Math.floorMod(seed, goods.length)].getDefaultState());
        j.put(new BlockPos(x0 + 1, y, z0 + 2), Blocks.BARREL.getDefaultState());
    }

    /** Фонарный столб. */
    static void lamp(Job j, int era, int x, int z) {
        if (!dry(j.w, x, z)) return;
        int y = ground(j.w, x, z);
        BlockState post = era >= 5 ? st(era, H, Blocks.STONE_BRICK_WALL) : Blocks.OAK_FENCE.getDefaultState();
        for (int dy = 0; dy < 3; dy++) j.put(new BlockPos(x, y + dy, z), post);
        j.put(new BlockPos(x, y + 3, z), era >= 3 ? Blocks.LANTERN.getDefaultState() : Blocks.TORCH.getDefaultState());
    }

    /** Мощёная улица (тропа в ранние эпохи, булыжник/камень позже). */
    static void street(Job j, int era, BlockPos c, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (Math.abs(x - c.getX()) <= 3 && Math.abs(z - c.getZ()) <= 3) continue;
            pave(j, era, c.getY(), x, z);
        }
    }

    static void pave(Job j, int era, int cy, int x, int z) {
        if (!dry(j.w, x, z)) return;
        int gy = ground(j.w, x, z);
        if (Math.abs(gy - cy) > 3) return;
        BlockPos g = new BlockPos(x, gy - 1, z);
        BlockState s;
        if (era >= 5) s = Math.floorMod(x * 3 + z, 5) == 0 ? st(era, H, Blocks.CRACKED_STONE_BRICKS) : st(era, H, Blocks.STONE_BRICKS);
        else if (era >= 3) s = Math.floorMod(x + z * 2, 4) == 0 ? Blocks.GRAVEL.getDefaultState() : st(era, H, Blocks.COBBLESTONE);
        else s = Math.floorMod(x * 5 + z * 3, 6) == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
        j.put(g, s);
        BlockState above = j.w.getBlockState(g.up());
        if (!above.isAir() && above.isReplaceable()) j.put(g.up(), Blocks.AIR.getDefaultState());
    }

    static void farm(Job j, int x0, int z0, int y) {
        flatten(j, x0, z0, x0 + 6, z0 + 6, y);
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            BlockPos f = new BlockPos(x0 + x, y - 1, z0 + z);
            boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
            if (edge) j.put(f, Blocks.OAK_LOG.getDefaultState());
            else if (x == 3 && z == 3) j.put(f, Blocks.WATER.getDefaultState());
            else {
                j.put(f, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
                j.put(f.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, j.w.getRandom().nextInt(8)));
            }
        }
    }

    static void plaza(Job j, Kingdoms.K k, int era, BlockPos c) {
        int y = c.getY();
        flatten(j, c.getX() - 3, c.getZ() - 3, c.getX() + 3, c.getZ() + 3, y);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            j.put(new BlockPos(c.getX() + x, y - 1, c.getZ() + z), (x + z) % 2 == 0 ? st(era, Civ.Kind.HOUSE, Blocks.COBBLESTONE) : Blocks.GRAVEL.getDefaultState());
        for (int dy = 0; dy < 4; dy++) j.put(c.up(dy), Blocks.OAK_FENCE.getDefaultState());
        j.put(c.up(4), k.wool.getDefaultState());
        j.put(c.add(1, 4, 0), k.wool.getDefaultState());
        j.put(c.add(1, 3, 0), k.wool.getDefaultState());
        int[][] cr = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] q : cr) {
            j.put(c.add(q[0], 0, q[1]), Blocks.OAK_FENCE.getDefaultState());
            j.put(c.add(q[0], 1, q[1]), era >= 3 ? Blocks.LANTERN.getDefaultState() : Blocks.TORCH.getDefaultState());
        }
        if (era >= 4) j.put(c.add(-2, 0, 0), Blocks.BELL.getDefaultState());
        // скамьи по краям площади и клумбы
        Block bench = era >= 5 ? Blocks.STONE_BRICK_STAIRS : Blocks.SPRUCE_STAIRS;
        int[][] bs = {{2, -3, 0}, {-2, -3, 0}, {2, 3, 1}, {-2, 3, 1}, {-3, 2, 2}, {-3, -2, 2}, {3, 2, 3}, {3, -2, 3}};
        Direction[] df = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int[] q : bs) j.put(c.add(q[0], 0, q[1]), bench.getDefaultState().with(net.minecraft.block.StairsBlock.FACING, df[q[2]]));
        int[][] fl = {{-1, -3}, {1, -3}, {-1, 3}, {1, 3}};
        for (int[] q : fl) {
            j.put(c.add(q[0], -1, q[1]), Blocks.GRASS_BLOCK.getDefaultState());
            j.put(c.add(q[0], 0, q[1]), (q[0] > 0 ? Blocks.CORNFLOWER : Blocks.POPPY).getDefaultState());
        }
    }

    /**
     * v3.0: планировка города — центральная площадь, главная улица, дома трёх типов фасадом к улице,
     * сады позади домов, колодец, рынок (с бронзового века), фонари и ферма.
     */
    static void town(ServerWorld w, Kingdoms.K k, BlockPos c) {
        Civ.City city = Civ.city(k);
        int era = city.era;
        Job j = new Job(w);
        int cx = c.getX(), cz = c.getZ();
        plaza(j, k, era, c);
        // {смещение x, тип, сторона: 0 = к северу от улицы (дверь на юг), 1 = к югу (дверь на север)}
        int[][] plots = {{-11, COTTAGE, 0}, {5, COTTAGE, 1}, {5, TALL, 0}, {-15, LONG, 1}, {-17, TALL, 0}, {11, COTTAGE, 0}, {11, LONG, 1}};
        int n = Math.min(plots.length, 3 + era / 2);
        for (int i = 0; i < n; i++) {
            int type = plots[i][1];
            boolean north = plots[i][2] == 0, south = north;
            int wd = type == LONG ? 9 : 5, dp = type == TALL ? 6 : 5;
            int x0 = cx + plots[i][0], z0 = north ? cz - 3 - (dp - 1) : cz + 3;
            int x1 = x0 + wd - 1, z1 = z0 + dp - 1;
            int mxp = (x0 + x1) / 2, mzp = (z0 + z1) / 2;
            if (!dry(w, mxp, mzp) || !dry(w, x0, z0) || !dry(w, x1, z1) || !dry(w, x0, z1) || !dry(w, x1, z0)) continue;
            int y = ground(w, mxp, mzp);
            if (Math.abs(y - c.getY()) > 6) continue;
            switch (type) {
                case TALL -> tall(j, era, x0, z0, y, south);
                case LONG -> longhouse(j, era, x0, z0, y, south);
                default -> cottage(j, era, x0, z0, y, south);
            }
            Civ.Building b = new Civ.Building(Civ.Kind.HOUSE, x0, z0, y);
            b.done = true;
            city.buildings.add(b);
            int gz0 = north ? z0 - 4 : z1 + 2;
            if (dry(w, x0, gz0) && dry(w, x1, gz0 + 2) && Math.abs(ground(w, mxp, gz0 + 1) - y) <= 2)
                garden(j, x0, gz0, x1, gz0 + 2, y, i + era);
            pave(j, era, c.getY(), mxp, south ? z1 + 1 : z0 - 1);
        }
        if (dry(w, cx, cz - 5)) well(j, era, cx, cz - 5, ground(w, cx, cz - 5));
        if (era >= 2) {
            if (dry(w, cx - 2, cz + 5)) stall(j, k, cx - 3, cz + 4, ground(w, cx - 2, cz + 5), cx + era);
            if (dry(w, cx + 2, cz + 5)) stall(j, k, cx + 1, cz + 4, ground(w, cx + 2, cz + 5), cz + era + 1);
        }
        street(j, era, c, cx - 20, cz - 1, cx + 20, cz + 1);
        int[][] lamps = {{4, -2}, {-4, 2}, {-20, 0}, {20, 0}, {10, -2}, {-16, 2}, {-6, -2}, {-12, -2}};
        for (int[] q : lamps) lamp(j, era, cx + q[0], cz + q[1]);
        int fx = cx - 3, fz = cz + 9;
        int fy = ground(w, fx + 3, fz + 3);
        boolean fdry = dry(w, fx + 3, fz + 3) && dry(w, fx, fz) && dry(w, fx + 6, fz + 6);
        if (fdry) {
            farm(j, fx, fz, fy);
            Civ.Building f = new Civ.Building(Civ.Kind.FARM, fx, fz, fy);
            f.done = true;
            city.buildings.add(f);
        }
        Tasks.add(j);
    }
}
