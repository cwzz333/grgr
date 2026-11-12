package com.vladik.godbox;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 10 эпох. Каждая эпоха меняет облик всех зданий королевства. */
public final class Eras {
    public static final String[] NAMES = {"Первобытная эпоха", "Каменный век", "Бронзовый век", "Железный век", "Античность",
            "Раннее Средневековье", "Эпоха замков", "Возрождение", "Имперская эпоха", "Эпоха Просвещения", "Эпоха пара"};
    public static final int CASTLE = 6;
    private static final Map<Block, Block[]> MAP = new HashMap<>();

    private Eras() {}

    private static Block[] of(Block... b) { return b; }

    private static void row(Block[] from, Block... to) { for (Block f : from) MAP.put(f, to); }

    static {
        Block N = null;
        row(of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS), Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.TERRACOTTA, N, Blocks.SMOOTH_SANDSTONE, N, N, Blocks.CALCITE, Blocks.BRICKS, Blocks.BRICKS);
        row(of(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.STRIPPED_OAK_LOG), N, N, N, N, Blocks.QUARTZ_PILLAR, N, Blocks.STONE_BRICKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DARK_OAK_LOG);
        row(of(Blocks.DARK_OAK_PLANKS), Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, Blocks.ORANGE_TERRACOTTA, N, Blocks.RED_TERRACOTTA, N, N, Blocks.DEEPSLATE_TILES, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.SPRUCE_PLANKS);
        row(of(Blocks.SPRUCE_SLAB, Blocks.DARK_OAK_SLAB, Blocks.OAK_SLAB, Blocks.BRICK_SLAB), Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.WAXED_CUT_COPPER_SLAB, N, Blocks.SMOOTH_SANDSTONE_SLAB, N, N, Blocks.DEEPSLATE_TILE_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, Blocks.BRICK_SLAB);
        row(of(Blocks.COBBLESTONE), N, N, N, N, Blocks.SANDSTONE, N, Blocks.STONE_BRICKS, N, Blocks.DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        row(of(Blocks.MOSSY_COBBLESTONE), N, N, N, N, Blocks.CUT_SANDSTONE, N, Blocks.MOSSY_STONE_BRICKS, N, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.STONE_BRICKS);
        row(of(Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE), Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MUD_BRICKS, N, Blocks.CUT_SANDSTONE, N, N, Blocks.QUARTZ_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE);
        row(of(Blocks.CHISELED_STONE_BRICKS), Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.TERRACOTTA, N, Blocks.CHISELED_SANDSTONE, N, N, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CHISELED_DEEPSLATE, Blocks.CHISELED_QUARTZ_BLOCK);
        row(of(Blocks.BRICKS), Blocks.PACKED_MUD, Blocks.MUD_BRICKS, N, N, N, N, N, N, N, N);
        row(of(Blocks.GLASS), Blocks.AIR, Blocks.AIR, N, N, N, N, N, N, Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.GLASS);
        row(of(Blocks.GLASS_PANE), Blocks.AIR, Blocks.AIR, N, N, N, N, N, N, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.GLASS_PANE);
        row(of(Blocks.LANTERN), N, N, N, N, N, N, N, N, N, N);
        row(of(Blocks.TORCH), N, N, N, N, N, N, N, N, N, N);
        row(of(Blocks.OAK_FENCE, Blocks.DARK_OAK_FENCE), N, N, N, N, Blocks.BIRCH_FENCE, N, N, N, Blocks.NETHER_BRICK_FENCE, Blocks.DARK_OAK_FENCE);
        row(of(Blocks.COBBLESTONE_WALL), N, N, N, N, Blocks.SANDSTONE_WALL, N, Blocks.STONE_BRICK_WALL, N, Blocks.DEEPSLATE_BRICK_WALL, Blocks.STONE_BRICK_WALL);
        row(of(Blocks.STONE_BRICK_SLAB), Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.MUD_BRICK_SLAB, N, Blocks.SANDSTONE_SLAB, N, N, Blocks.QUARTZ_SLAB, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.QUARTZ_SLAB);
        row(of(Blocks.SMOOTH_STONE), N, N, N, N, Blocks.SMOOTH_SANDSTONE, N, N, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_QUARTZ);
        row(of(Blocks.QUARTZ_BLOCK), Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE, N, N, N, N, N, N);
        row(of(Blocks.IRON_BARS), Blocks.OAK_FENCE, Blocks.OAK_FENCE, N, N, N, N, N, N, N, N);
        row(of(Blocks.GOLD_BLOCK), Blocks.HAY_BLOCK, Blocks.BONE_BLOCK, Blocks.WAXED_COPPER_BLOCK, Blocks.IRON_BLOCK, N, N, N, N, N, N);
    }

    // Эпоха пара (10): кирпич, базальтовые колонны, медные крыши, чернокаменная кладка, тёплые окна
    static {
        Map<Block, Block> s = new HashMap<>();
        Block[][] pairs = {
            {Blocks.OAK_PLANKS, Blocks.BRICKS}, {Blocks.SPRUCE_PLANKS, Blocks.BRICKS}, {Blocks.BIRCH_PLANKS, Blocks.BRICKS},
            {Blocks.OAK_LOG, Blocks.POLISHED_BASALT}, {Blocks.SPRUCE_LOG, Blocks.POLISHED_BASALT}, {Blocks.BIRCH_LOG, Blocks.POLISHED_BASALT}, {Blocks.STRIPPED_OAK_LOG, Blocks.POLISHED_BASALT},
            {Blocks.DARK_OAK_PLANKS, Blocks.WAXED_WEATHERED_CUT_COPPER},
            {Blocks.OAK_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB}, {Blocks.SPRUCE_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB}, {Blocks.DARK_OAK_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB},
            {Blocks.COBBLESTONE, Blocks.POLISHED_BLACKSTONE_BRICKS}, {Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS},
            {Blocks.STONE_BRICKS, Blocks.POLISHED_BLACKSTONE}, {Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE},
            {Blocks.GLASS, Blocks.ORANGE_STAINED_GLASS}, {Blocks.GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE},
            {Blocks.OAK_FENCE, Blocks.IRON_BARS}, {Blocks.DARK_OAK_FENCE, Blocks.IRON_BARS}, {Blocks.SPRUCE_FENCE, Blocks.IRON_BARS},
            {Blocks.COBBLESTONE_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL}, {Blocks.STONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB},
            {Blocks.SMOOTH_STONE, Blocks.POLISHED_BLACKSTONE}, {Blocks.QUARTZ_BLOCK, Blocks.WAXED_CUT_COPPER}, {Blocks.GOLD_BLOCK, Blocks.WAXED_COPPER_BLOCK}
        };
        for (Block[] p : pairs) s.put(p[0], p[1]);
        for (Map.Entry<Block, Block[]> e : new ArrayList<>(MAP.entrySet())) {
            Block[] a = java.util.Arrays.copyOf(e.getValue(), 11);
            a[10] = s.containsKey(e.getKey()) ? s.get(e.getKey()) : a[8];
            MAP.put(e.getKey(), a);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState copy(BlockState from, Block to) {
        BlockState s = to.getDefaultState();
        for (Property p : from.getProperties()) if (s.contains(p)) s = s.with(p, (Comparable) from.get(p));
        return s;
    }

    static BlockState style(int era, Civ.Kind kind, BlockState s) {
        Block[] to = MAP.get(s.getBlock());
        if (to == null) return s;
        if (kind == Civ.Kind.MILL && (s.isOf(Blocks.OAK_LOG) || s.isOf(Blocks.OAK_PLANKS))) return s;
        Block t = to[Math.max(0, Math.min(to.length - 1, era))];
        return t == null ? s : copy(s, t);
    }

    static void apply(Kingdoms.K k, Civ.Kind kind, List<BlockState> ss) {
        int e = Civ.city(k).era;
        for (int i = 0; i < ss.size(); i++) ss.set(i, Races.style(k, style(e, kind, ss.get(i))));
    }

    /** Украшения, которые появляются только в поздних эпохах. */
    static void decor(Kingdoms.K k, Civ.Building b, Civ.Project p) {
        int e = Civ.city(k).era;
        int y0 = b.y0;
        if (b.kind == Civ.Kind.HOUSE) {
            int top = y0 + 3 + Arch.gableHeight(b.x0, b.z0, b.x1, b.z1);
            if (e >= 4) {
                // фонари под свесами крыши
                BlockState l = Arch.hanging(Blocks.LANTERN);
                p.add(new BlockPos(b.x0 - 1, y0 + 2, b.z0 - 1), l);
                p.add(new BlockPos(b.x1 + 1, y0 + 2, b.z0 - 1), l);
                p.add(new BlockPos(b.x0 - 1, y0 + 2, b.z1 + 1), l);
                p.add(new BlockPos(b.x1 + 1, y0 + 2, b.z1 + 1), l);
            }
            if (e >= 10) {
                p.add(new BlockPos(b.x0 - 1, top + 1, b.cz()), Blocks.LIGHTNING_ROD.getDefaultState());
                p.add(new BlockPos(b.x1 + 1, top + 1, b.cz()), Blocks.LIGHTNING_ROD.getDefaultState());
            }
        } else if (b.kind == Civ.Kind.TOWER && e >= 8) {
            p.add(new BlockPos(b.cx(), y0 + 7, b.cz()), (e == 9 ? Blocks.LANTERN : Blocks.BELL).getDefaultState());
        } else if (b.kind == Civ.Kind.BARRACKS && e >= 6) {
            p.add(new BlockPos(b.x0, y0 + 6, b.z0), Blocks.LANTERN.getDefaultState());
            p.add(new BlockPos(b.x1, y0 + 6, b.z1), Blocks.LANTERN.getDefaultState());
        }
    }

    static int need(Civ.City c) { return c.era + 1; }

    static boolean ready(Civ.City c, int vill, int built, int sage) {
        int n = need(c);
        if (n >= NAMES.length) return false;
        return vill >= Math.min(32, 3 + 3 * n) - sage * 2 && built >= 2 + 3 * n - sage * 2
                && c.wood >= 10 * n && c.stone >= 8 * n && c.ore >= 3 * (n - 1) && c.food >= 4 * n;
    }

    static void pay(Civ.City c) {
        int n = need(c);
        c.wood -= 6 * n;
        c.stone -= 5 * n;
        c.ore -= 2 * (n - 1);
    }

    static Item axe(int e) {
        if (e <= 0) return Items.WOODEN_AXE;
        if (e == 1) return Items.STONE_AXE;
        if (e == 2) return Items.GOLDEN_AXE;
        if (e <= 5) return Items.IRON_AXE;
        if (e <= 7) return Items.DIAMOND_AXE;
        return Items.NETHERITE_AXE;
    }

    private static boolean skip(BlockState s) {
        Block b = s.getBlock();
        return s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || b instanceof BedBlock || b instanceof CropBlock || b instanceof DoorBlock;
    }

    /** Перестраивает готовые здания королевства в стиле текущей эпохи. */
    static void rebuild(Kingdoms.K k, Civ.City c, ServerWorld w) {
        if (w == null) return;
        List<BlockPos> ps = new ArrayList<>();
        List<BlockState> ss = new ArrayList<>();
        for (Civ.Building b : new ArrayList<>(c.buildings)) {
            if (!b.done || b.kind == Civ.Kind.QUARRY || b.kind == Civ.Kind.FARM) continue;
            if (!w.isChunkLoaded(b.cx() >> 4, b.cz() >> 4)) continue;
            List<BlockPos> bp;
            List<BlockState> bs;
            if (b.kind == Civ.Kind.CASTLE) {
                bp = new ArrayList<>();
                bs = new ArrayList<>();
                Ages.plan(k, b, w, bp, bs);
                apply(k, b.kind, bs);
            } else {
                Civ.Project p = Civ.blueprint(k, b, w);
                bp = p.pos;
                bs = p.st;
            }
            for (int i = 0; i < bp.size(); i++) {
                BlockState s = bs.get(i);
                if (skip(s)) continue;
                BlockState cur = w.getBlockState(bp.get(i));
                if (cur.isAir() || cur.getBlock() == s.getBlock() || skip(cur)) continue;
                ps.add(bp.get(i));
                ss.add(s);
            }
        }
        if (ps.isEmpty()) return;
        Chronicle.say(w.getServer(), k, "перестраивает города в стиле эпохи: " + NAMES[Math.min(NAMES.length - 1, c.era)]);
        Tasks.add(new Tasks.Task() {
            int i;

            @Override
            public boolean tick() {
                for (int n = 0; n < 60 && i < ps.size(); n++, i++) {
                    BlockPos p = ps.get(i);
                    w.setBlockState(p, ss.get(i), Block.NOTIFY_ALL);
                    if (i % 15 == 0) Fx.particles(w, ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
                }
                return i >= ps.size();
            }
        });
    }
}
