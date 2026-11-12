package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SnowyBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** v1.6: генерация миров как в WorldBox — континенты, острова, горы, биомы, леса и руды. */
public final class WorldGen implements Tasks.Task {
    static final String[] TYPES = {"continent", "islands", "archipelago", "pangaea", "mountains"};
    static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static int running;

    final ServerPlayerEntity god;
    final ServerWorld w;
    final int cx, cz, R, side, total, sea;
    final String type;
    final long seed;
    final Random rnd;
    final List<long[]> trees = new ArrayList<>();
    int i, ti, ticks, lastPct = -1;

    WorldGen(ServerPlayerEntity god, ServerWorld w, BlockPos c, int brush, String type) {
        this.god = god;
        this.w = w;
        this.cx = c.getX();
        this.cz = c.getZ();
        this.R = 24 + brush * 12;
        this.side = R * 2 + 1;
        this.total = side * side;
        this.sea = w.getSeaLevel();
        this.rnd = Random.create();
        this.seed = rnd.nextLong();
        this.type = type == null || type.equals("random") ? TYPES[rnd.nextInt(TYPES.length)] : type;
    }

    public static void start(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush, String type) {
        if (running >= 2) {
            god.sendMessage(Text.literal("Подожди — мир ещё создаётся").formatted(Formatting.YELLOW), true);
            return;
        }
        WorldGen g = new WorldGen(god, w, pos, Math.max(1, Math.min(10, brush)), type);
        running++;
        Tasks.add(g);
        Fx.sound(w, pos.getX(), pos.getY() + 5, pos.getZ(), SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.5f, 1.2f);
        god.sendMessage(Text.literal("🌍 Сотворение мира: " + title(g.type) + ", радиус " + g.R).formatted(Formatting.AQUA), false);
    }

    static String title(String t) {
        return switch (t) {
            case "islands" -> "Острова";
            case "archipelago" -> "Архипелаг";
            case "pangaea" -> "Пангея";
            case "mountains" -> "Горный край";
            default -> "Континент";
        };
    }

    // ---------------- шум ----------------
    static double hash(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    static double noise(long seed, double x, double z) {
        int x0 = MathHelper.floor(x), z0 = MathHelper.floor(z);
        double fx = x - x0, fz = z - z0;
        double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = hash(seed, x0, z0), b = hash(seed, x0 + 1, z0), c = hash(seed, x0, z0 + 1), d = hash(seed, x0 + 1, z0 + 1);
        return MathHelper.lerp(v, MathHelper.lerp(u, a, b), MathHelper.lerp(u, c, d));
    }

    static double fbm(long seed, double x, double z, int oct) {
        double s = 0, amp = 1, f = 1, norm = 0;
        for (int o = 0; o < oct; o++) {
            s += noise(seed + o * 131L, x * f, z * f) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return s / norm;
    }

    double elevation(int x, int z, double dn) {
        double n = fbm(seed, x / 48.0, z / 48.0, 5);
        return switch (type) {
            case "islands" -> fbm(seed + 3, x / 26.0, z / 26.0, 4) * 1.3 + 0.05 - dn * 0.7;
            case "archipelago" -> fbm(seed + 5, x / 13.0, z / 13.0, 3) * 1.4 - 0.25 - dn * 0.3;
            case "pangaea" -> 0.35 + n * 0.55 - Math.pow(dn, 4) * 1.3;
            case "mountains" -> {
                double rr = 1 - Math.abs(fbm(seed + 7, x / 40.0, z / 40.0, 4));
                yield 0.15 + rr * rr * 1.2 + n * 0.35 - dn * dn * 0.9;
            }
            default -> 0.4 + n * 0.8 - dn * dn * 1.0;
        };
    }

    int height(double e) {
        if (e >= 0) return sea + 1 + (int) (e * (type.equals("mountains") ? 62 : 30));
        return Math.max(w.getBottomY() + 6, sea + (int) Math.floor(e * 26) - 1);
    }

    // ---------------- задача ----------------
    @Override
    public boolean tick() {
        if (god.isRemoved() && i == 0) {
            running--;
            return true;
        }
        ticks++;
        long until = System.nanoTime() + 22_000_000L;
        if (i < total) {
            while (i < total && System.nanoTime() < until) {
                for (int k = 0; k < 16 && i < total; k++, i++) column(cx - R + i % side, cz - R + i / side);
            }
        } else {
            while (ti < trees.size() && System.nanoTime() < until) {
                long[] t = trees.get(ti++);
                tree(new BlockPos((int) t[0], (int) t[1], (int) t[2]), (int) t[3]);
            }
            if (ti >= trees.size()) {
                running--;
                Fx.sound(w, cx, sea + 10, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                god.sendMessage(Text.literal("🌍 Мир создан: " + title(type) + "! Засели его людьми и королевствами").formatted(Formatting.GREEN), true);
                return true;
            }
        }
        int pct = (int) (100L * (i + ti) / Math.max(1, total + trees.size()));
        if (ticks % 10 == 0 && pct != lastPct) {
            lastPct = pct;
            god.sendMessage(Text.literal("🌍 Генерация мира: " + pct + "%").formatted(Formatting.AQUA), true);
        }
        return false;
    }

    void put(BlockPos.Mutable m, BlockState s) {
        if (w.getBlockState(m) != s) w.setBlockState(m, s, FLAGS);
    }

    void column(int x, int z) {
        double dx = x - cx, dz = z - cz;
        double dn = Math.sqrt(dx * dx + dz * dz) / R;
        if (dn > 1) return;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        int oldTop = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        int oldGround = w.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
        int h = height(elevation(x, z, dn));
        if (dn > 0.82) {
            double t = (dn - 0.82) / 0.18;
            t = t * t * (3 - 2 * t);
            h = (int) Math.round(MathHelper.lerp(t, h, oldGround));
        }
        int maxY = w.getTopY() - 2;
        h = Math.min(h, maxY - 12);
        double temp = fbm(seed + 99, x / 90.0, z / 90.0, 3);
        boolean cold = temp < -0.3, hot = temp > 0.3;
        int top = Math.min(maxY, Math.max(oldTop + 1, Math.max(h, sea) + 1));
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = top; y > h; y--) {
            m.set(x, y, z);
            put(m, y > sea ? Blocks.AIR.getDefaultState() : (y == sea && temp < -0.45 ? Blocks.ICE.getDefaultState() : Blocks.WATER.getDefaultState()));
        }
        double hv = hash(seed + 11, x, z);
        boolean under = h < sea;
        BlockState surf, sub;
        if (under) {
            surf = h < sea - 6 ? (hv > 0.4 ? Blocks.GRAVEL.getDefaultState() : hv < -0.75 ? Blocks.CLAY.getDefaultState() : Blocks.SAND.getDefaultState())
                : Blocks.SAND.getDefaultState();
            sub = Blocks.SAND.getDefaultState();
        } else if (h <= sea + 2 && !(h > sea && cold)) {
            surf = sub = Blocks.SAND.getDefaultState();
        } else if (h > sea + 50) {
            surf = Blocks.SNOW_BLOCK.getDefaultState();
            sub = Blocks.STONE.getDefaultState();
        } else if (h > sea + 34) {
            surf = cold || h > sea + 44 ? Blocks.SNOW_BLOCK.getDefaultState() : hv > 0.3 ? Blocks.GRAVEL.getDefaultState() : Blocks.STONE.getDefaultState();
            sub = Blocks.STONE.getDefaultState();
        } else if (hot) {
            surf = Blocks.SAND.getDefaultState();
            sub = Blocks.SANDSTONE.getDefaultState();
        } else if (cold) {
            surf = Blocks.GRASS_BLOCK.getDefaultState().with(SnowyBlock.SNOWY, true);
            sub = Blocks.DIRT.getDefaultState();
        } else {
            surf = Blocks.GRASS_BLOCK.getDefaultState();
            sub = Blocks.DIRT.getDefaultState();
        }
        int bottom = Math.max(w.getBottomY() + 1, Math.min(h - 4, oldGround));
        for (int y = h; y >= bottom; y--) {
            m.set(x, y, z);
            BlockState want;
            if (y == h) want = surf;
            else if (y > h - 4) want = sub;
            else {
                BlockState s = w.getBlockState(m);
                if (!s.isAir() && s.getFluidState().isEmpty() && !s.isIn(BlockTags.LEAVES) && !s.isIn(BlockTags.LOGS)) continue;
                want = Blocks.STONE.getDefaultState();
            }
            put(m, want);
        }
        // ---- украшения ----
        double r2 = (hash(seed + 23, x, z) + 1) / 2;
        m.set(x, h + 1, z);
        if (under) {
            if (h < sea - 1 && r2 < 0.12 && !cold) put(m, Blocks.SEAGRASS.getDefaultState());
            return;
        }
        if (surf.isOf(Blocks.GRASS_BLOCK)) {
            double forest = fbm(seed + 33, x / 30.0, z / 30.0, 2);
            double tc = forest > 0.15 ? 1 / 14.0 : 1 / 110.0;
            if (r2 < tc) {
                trees.add(new long[]{x, h + 1, z, cold ? 1 : forest > 0.4 ? 2 : 0});
            } else if (cold) {
                put(m, Blocks.SNOW.getDefaultState());
            } else if (r2 < 0.3) {
                put(m, Blocks.SHORT_GRASS.getDefaultState());
            } else if (r2 < 0.325) {
                Block[] fl = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM};
                put(m, fl[(int) ((r2 - 0.3) * 1000) % fl.length].getDefaultState());
            }
        } else if (surf.isOf(Blocks.SAND) && hot && h > sea + 2) {
            if (r2 < 0.012) {
                int hh = 1 + (int) (r2 * 250) % 3;
                for (int k = 0; k < hh; k++) {
                    m.set(x, h + 1 + k, z);
                    put(m, Blocks.CACTUS.getDefaultState());
                }
            } else if (r2 < 0.03) {
                put(m, Blocks.DEAD_BUSH.getDefaultState());
            }
        } else if ((surf.isOf(Blocks.STONE) || surf.isOf(Blocks.GRAVEL)) && r2 < 0.025) {
            m.set(x, h, z);
            put(m, Ores.ore("mix", false, rnd).getDefaultState());
        }
    }

    void tree(BlockPos p, int kind) {
        if (!w.isChunkLoaded(p)) return;
        if (!w.getBlockState(p.down()).isIn(BlockTags.DIRT)) return;
        BlockState here = w.getBlockState(p);
        if (!here.isAir() && !here.isReplaceable()) return;
        Block b = kind == 1 ? Blocks.SPRUCE_SAPLING : kind == 2 ? Blocks.BIRCH_SAPLING : Blocks.OAK_SAPLING;
        BlockState st = b.getDefaultState().with(SaplingBlock.STAGE, 1);
        w.setBlockState(p, st, Block.NOTIFY_LISTENERS);
        ((SaplingBlock) b).generate(w, p, st, w.getRandom());
    }
}
