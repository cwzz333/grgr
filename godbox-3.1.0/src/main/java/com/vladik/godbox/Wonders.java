package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;

/** Чудеса света: одно на весь мир, дают владельцу бонусы, переходят к завоевателю. */
public final class Wonders {
    public static final String[] IDS = {"pyramid", "library", "lighthouse", "colossus", "gardens"};
    public static final String[] NAMES = {"Великая пирамида", "Великая библиотека", "Маяк", "Колосс", "Висячие сады"};
    public static boolean on = true;
    static final Map<String, Kingdoms.K> OWNER = new HashMap<>();
    static final Map<String, BlockPos> AT = new HashMap<>();
    private static int t;

    private Wonders() {}

    public static boolean has(Kingdoms.K k, String id) { return k != null && OWNER.get(id) == k; }

    static int idx(String id) { for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i; return 0; }

    public static String name(String id) { return NAMES[idx(id)]; }

    public static String list(Kingdoms.K k) {
        StringBuilder sb = new StringBuilder();
        for (String id : IDS) if (has(k, id)) sb.append(sb.length() > 0 ? ", " : "").append(name(id));
        return sb.length() == 0 ? "нет" : sb.toString();
    }

    private static BlockState b(Block x) { return x.getDefaultState(); }

    private static void put(TownBuilder.Job j, int x, int y, int z, BlockState s) { j.put(new BlockPos(x, y, z), s); }

    static void pyramid(TownBuilder.Job j, int x, int y, int z) {
        TownBuilder.flatten(j, x - 12, z - 12, x + 12, z + 12, y);
        for (int l = 0; l <= 11; l++) {
            int h = 11 - l;
            for (int dx = -h; dx <= h; dx++)
                for (int dz = -h; dz <= h; dz++) {
                    if (Math.abs(dx) != h && Math.abs(dz) != h) continue;
                    if (l < 2 && dz == h && Math.abs(dx) <= 1) continue;
                    put(j, x + dx, y + l, z + dz, b(l % 3 == 2 ? Blocks.SMOOTH_SANDSTONE : (l % 3 == 1 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE)));
                }
        }
        put(j, x, y + 11, z, b(Blocks.GOLD_BLOCK));
        put(j, x, y, z, b(Blocks.GOLD_BLOCK));
        put(j, x, y + 1, z, b(Blocks.SOUL_LANTERN));
    }

    static void library(TownBuilder.Job j, int x, int y, int z) {
        int x0 = x - 7, x1 = x + 7, z0 = z - 5, z1 = z + 5;
        TownBuilder.flatten(j, x0 - 1, z0 - 1, x1 + 1, z1 + 1, y);
        for (int a = x0; a <= x1; a++)
            for (int c = z0; c <= z1; c++) {
                put(j, a, y - 1, c, b(((a + c) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS));
                boolean edge = a == x0 || a == x1 || c == z0 || c == z1;
                if (edge) for (int dy = 0; dy <= 5; dy++) {
                    boolean pillar = (a - x0) % 4 == 0 && (c == z0 || c == z1) || (c - z0) % 5 == 0 && (a == x0 || a == x1);
                    if (c == z1 && a == x && dy <= 1) continue;
                    BlockState s = pillar ? b(Blocks.QUARTZ_PILLAR) : dy == 5 ? b(Blocks.STONE_BRICKS) : dy >= 3 ? b(Blocks.GLASS) : b(Blocks.BOOKSHELF);
                    put(j, a, y + dy, c, s);
                }
                put(j, a, y + 6, c, b(edge ? Blocks.QUARTZ_BLOCK : Blocks.SMOOTH_STONE));
                if (c == z) put(j, a, y + 7, c, b(Blocks.CHISELED_QUARTZ_BLOCK));
            }
        for (int a = x0 + 2; a <= x1 - 2; a += 3) {
            put(j, a, y, z0 + 2, b(Blocks.BOOKSHELF));
            put(j, a, y + 1, z0 + 2, b(Blocks.BOOKSHELF));
            put(j, a, y, z, b(Blocks.LECTERN));
            put(j, a, y + 5, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
        put(j, x, y, z1 - 2, b(Blocks.ENCHANTING_TABLE));
    }

    static void lighthouse(TownBuilder.Job j, int x, int y, int z) {
        TownBuilder.flatten(j, x - 4, z - 4, x + 4, z + 4, y);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                int r2 = dx * dx + dz * dz;
                for (int dy = 0; dy <= 21; dy++) {
                    if (r2 > 10 || r2 < 5 && dy > 0) continue;
                    if (dz == 3 && dx == 0 && dy <= 1) continue;
                    put(j, x + dx, y + dy, z + dz, b((dy / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE));
                }
                if (r2 <= 13) put(j, x + dx, y + 22, z + dz, b(Blocks.STONE_BRICKS));
                if (r2 >= 9 && r2 <= 13) put(j, x + dx, y + 23, z + dz, b(Blocks.IRON_BARS));
                if (r2 <= 4) {
                    put(j, x + dx, y + 23, z + dz, r2 == 0 ? b(Blocks.SEA_LANTERN) : b(Blocks.GLASS));
                    put(j, x + dx, y + 24, z + dz, r2 == 0 ? b(Blocks.SEA_LANTERN) : b(Blocks.GLASS));
                    put(j, x + dx, y + 25, z + dz, b(Blocks.RED_CONCRETE));
                }
            }
    }

    static void colossus(TownBuilder.Job j, int x, int y, int z) {
        TownBuilder.flatten(j, x - 4, z - 4, x + 4, z + 4, y);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 0; dy <= 2; dy++) put(j, x + dx, y + dy, z + dz, b(dy == 2 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS));
        BlockState cu = b(Blocks.WAXED_COPPER_BLOCK), au = b(Blocks.GOLD_BLOCK);
        for (int dy = 3; dy <= 8; dy++) { put(j, x - 1, y + dy, z, cu); put(j, x + 1, y + dy, z, cu); }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 9; dy <= 14; dy++) put(j, x + dx, y + dy, z + dz, cu);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 15; dy <= 17; dy++) put(j, x + dx, y + dy, z + dz, cu);
        for (int[] q : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, 0}}) put(j, x + q[0], y + 18, z + q[1], au);
        for (int dy = 10; dy <= 14; dy++) put(j, x - 3, y + dy, z, cu);
        for (int dy = 12; dy <= 20; dy++) put(j, x + 3, y + dy, z, cu);
        put(j, x + 3, y + 21, z, au);
        put(j, x + 3, y + 22, z, b(Blocks.GLOWSTONE));
    }

    static void gardens(TownBuilder.Job j, int x, int y, int z) {
        TownBuilder.flatten(j, x - 11, z - 11, x + 11, z + 11, y);
        Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.ALLIUM};
        BlockState leaf = Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int i = 0; i < 3; i++) {
            int h = 10 - 3 * i, yb = y + 3 * i;
            for (int dx = -h; dx <= h; dx++)
                for (int dz = -h; dz <= h; dz++) {
                    boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                    for (int dy = 0; dy <= 2; dy++) put(j, x + dx, yb + dy, z + dz, edge ? b(Blocks.STONE_BRICKS) : dy == 2 ? b(Blocks.GRASS_BLOCK) : b(Blocks.DIRT));
                    boolean inner = i < 2 && Math.abs(dx) <= h - 3 && Math.abs(dz) <= h - 3;
                    if (inner) continue;
                    if (edge) { if (((dx + dz) & 1) == 0) put(j, x + dx, yb + 3, z + dz, leaf); }
                    else if (Math.floorMod(dx * 7 + dz * 13, 3) == 0) put(j, x + dx, yb + 3, z + dz, b(fl[Math.floorMod(dx * 3 + dz, fl.length)]));
                }
        }
        int top = y + 9;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(j, x + dx, top, z + dz, dx == 0 && dz == 0 ? b(Blocks.WATER) : b(Blocks.STONE_BRICK_WALL));
    }

    static void build(ServerWorld w, Kingdoms.K k, String id, BlockPos c) {
        TownBuilder.Job j = new TownBuilder.Job(w);
        int x = c.getX(), y = c.getY(), z = c.getZ();
        switch (id) {
            case "pyramid" -> pyramid(j, x, y, z);
            case "library" -> library(j, x, y, z);
            case "lighthouse" -> lighthouse(j, x, y, z);
            case "colossus" -> colossus(j, x, y, z);
            default -> gardens(j, x, y, z);
        }
        Tasks.add(() -> j.tick() || j.tick() || j.tick());
        OWNER.put(id, k);
        AT.put(id, c);
        Fx.sound(w, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 4f, 1f);
        Chronicle.say(w.getServer(), k, "возводит чудо света: «" + name(id) + "»");
    }

    static BlockPos site(ServerWorld w, Kingdoms.K k, String id) {
        if (id.equals("lighthouse")) {
            BlockPos port = Ships.PORTS.get(k);
            if (port == null) return null;
            BlockPos p = TownBuilder.landOr(w, port);
            return p == port ? null : p;
        }
        Random r = w.getRandom();
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 24 + r.nextInt(30);
            int x = k.capital.getX() + (int) (Math.cos(a) * d), z = k.capital.getZ() + (int) (Math.sin(a) * d);
            if (!TownBuilder.dryArea(w, x, z, 12) || busy(x, z, 14)) continue;
            return new BlockPos(x, TownBuilder.ground(w, x, z), z);
        }
        return null;
    }

    static boolean busy(int x, int z, int h) {
        for (Kingdoms.K o : Kingdoms.K.values())
            for (Civ.Building b : Civ.city(o).buildings)
                if (x + h >= b.x0 && x - h <= b.x1 && z + h >= b.z0 && z - h <= b.z1) return true;
        for (BlockPos p : AT.values()) if (Math.abs(p.getX() - x) < 30 && Math.abs(p.getZ() - z) < 30) return true;
        return false;
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (t % 400 == 0) for (String id : IDS) {
            Kingdoms.K k = OWNER.get(id);
            if (k == null) continue;
            if (!k.alive) {
                BlockPos at = AT.get(id);
                Kingdoms.K n = null;
                double best = 120 * 120;
                for (Kingdoms.K o : Kingdoms.K.values()) {
                    if (!o.alive || o.capital == null) continue;
                    double d = o.capital.getSquaredDistance(at);
                    for (BlockPos tp : Conquest.towns(o)) d = Math.min(d, tp.getSquaredDistance(at));
                    if (d < best) { best = d; n = o; }
                }
                if (n == null) { OWNER.remove(id); continue; }
                OWNER.put(id, n);
                Chronicle.say(s, n, "завладело чудом света «" + name(id) + "»");
                k = n;
            }
            Civ.City c = Civ.city(k);
            switch (id) {
                case "pyramid" -> c.faith += 3;
                case "gardens" -> c.food += 6;
                case "colossus" -> { c.ore += 2; c.wood += 3; }
                case "lighthouse" -> c.food += 4;
                default -> {}
            }
            if (k.world instanceof ServerWorld w) {
                BlockPos at = AT.get(id);
                Fx.particles(w, ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 14, at.getZ() + 0.5, 25, 3, 6, 3, 0.02);
            }
        }
        if (t % 2400 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w) || !Tech.has(k, Tech.T.ARCH)) continue;
            Civ.City c = Civ.city(k);
            if (c.stone < 90 || c.wood < 60 || c.ore < 15) continue;
            for (String id : IDS) {
                if (OWNER.containsKey(id)) continue;
                BlockPos p = site(w, k, id);
                if (p == null) continue;
                c.stone -= 90; c.wood -= 60; c.ore -= 15;
                build(w, k, id, p);
                break;
            }
        }
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos, String id) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null) { god.sendMessage(Text.literal("Чудо строит ближайшее королевство — сначала оснуйте его").formatted(Formatting.RED), true); return; }
        build(w, k, id, TownBuilder.landOr(w, Fx.surface(w, pos.getX(), pos.getZ())));
        god.sendMessage(Text.literal("«" + k.title + "» возводит «" + name(id) + "»").formatted(k.color), true);
    }

    public static void clear() { OWNER.clear(); AT.clear(); t = 0; }
}
