package com.vladik.godbox;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Осады: городские стены с башнями и катапульты, стреляющие по вражеским городам. */
public final class Siege {
    public static boolean on = true;
    static final Map<Kingdoms.K, Integer> WALL = new EnumMap<>(Kingdoms.K.class);
    private static int t;

    private Siege() {}

    static boolean natural(BlockState s) {
        return s.isIn(BlockTags.DIRT) || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.STONE) || s.isOf(Blocks.SNOW_BLOCK)
                || s.isOf(Blocks.ANDESITE) || s.isOf(Blocks.GRANITE) || s.isOf(Blocks.DIORITE) || s.isOf(Blocks.TERRACOTTA);
    }

    static int radius(Kingdoms.K k) {
        int r = 14;
        for (Civ.Building b : Civ.city(k).buildings) {
            int d = (int) Math.ceil(Math.sqrt(Math.pow(b.cx() - k.capital.getX(), 2) + Math.pow(b.cz() - k.capital.getZ(), 2))) + Math.max(b.x1 - b.x0, b.z1 - b.z0) / 2;
            if (d + 6 > r) r = d + 6;
        }
        return Math.min(60, r);
    }

    static void walls(ServerWorld w, Kingdoms.K k, int lvl) {
        TownBuilder.Job j = new TownBuilder.Job(w);
        int R = radius(k), cx = k.capital.getX(), cz = k.capital.getZ();
        int n = (int) Math.ceil(Math.PI * 2 * R * 1.5);
        Set<Long> seen = new HashSet<>();
        List<Civ.Building> bs = new ArrayList<>(Civ.city(k).buildings);
        BlockState body = lvl >= 2 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
        BlockState top = lvl >= 2 ? Blocks.STONE_BRICK_WALL.getDefaultState() : Blocks.SPRUCE_FENCE.getDefaultState();
        int h = lvl >= 2 ? 4 : 3;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double deg = Math.toDegrees(a) % 90;
            if (deg < 360.0 / n * 3 || deg > 90 - 360.0 / n * 3) continue; // ворота по сторонам света
            int x = cx + (int) Math.round(Math.cos(a) * R), z = cz + (int) Math.round(Math.sin(a) * R);
            if (!seen.add(BlockPos.asLong(x, 0, z)) || !w.isChunkLoaded(x >> 4, z >> 4) || Roads.inBuilding(bs, x, z)) continue;
            int y = TownBuilder.ground(w, x, z);
            BlockPos g = new BlockPos(x, y - 1, z);
            BlockState s = w.getBlockState(g);
            if (!s.getFluidState().isEmpty() || Roads.road(s) || !natural(s) && !s.isOf(Blocks.STRIPPED_SPRUCE_LOG) && !s.isOf(Blocks.STONE_BRICKS)) continue;
            for (int dy = 0; dy < h; dy++) j.put(g.up(dy), body);
            if ((x + z & 1) == 0) j.put(g.up(h), top);
        }
        if (lvl >= 2) for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * R), z = cz + (int) Math.round(Math.sin(a) * R);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || !TownBuilder.dry(w, x, z)) continue;
            int y = TownBuilder.ground(w, x, z);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy < 7; dy++) j.put(new BlockPos(x + dx, y + dy, z + dz), Blocks.STONE_BRICKS.getDefaultState());
                if ((dx + dz & 1) == 0) j.put(new BlockPos(x + dx, y + 7, z + dz), Blocks.STONE_BRICK_WALL.getDefaultState());
            }
            j.put(new BlockPos(x, y + 7, z), Blocks.LANTERN.getDefaultState());
        }
        Tasks.add(j);
        WALL.put(k, lvl);
        Chronicle.say(w.getServer(), k, lvl >= 2 ? "возводит каменные стены с башнями" : "обносит столицу частоколом");
    }

    static void shoot(ServerWorld w, MobEntity m, BlockPos target) {
        double dx = target.getX() + 0.5 - m.getX(), dz = target.getZ() + 0.5 - m.getZ(), dy = target.getY() - (m.getEyeY() + 1);
        double d = Math.sqrt(dx * dx + dz * dz);
        double T = Math.max(20, d / 1.1);
        SnowballEntity sb = new SnowballEntity(w, m);
        sb.setItem(new ItemStack(Items.COBBLESTONE));
        sb.setPosition(m.getX(), m.getEyeY() + 1, m.getZ());
        sb.setVelocity(dx / T * 1.25, (dy + 0.5 * 0.03 * T * T) / T * 1.15, dz / T * 1.25);
        w.spawnEntity(sb);
        Fx.sound(w, m.getX(), m.getY(), m.getZ(), SoundEvents.BLOCK_PISTON_EXTEND, 1.5f, 0.5f);
        Vec3d[] last = {sb.getPos()};
        int[] age = {0};
        Tasks.add(() -> {
            age[0]++;
            if (sb.isAlive() && age[0] < 200) {
                last[0] = sb.getPos();
                if (age[0] % 2 == 0) Fx.particles(w, ParticleTypes.SMOKE, last[0].x, last[0].y, last[0].z, 2, 0.05, 0.05, 0.05, 0.0);
                return false;
            }
            if (sb.isAlive()) sb.discard();
            w.createExplosion(null, last[0].x, last[0].y, last[0].z, 2.5f, World.ExplosionSourceType.TNT);
            return true;
        });
    }

    static void catapults(ServerWorld w, Kingdoms.K k) {
        Civ.City c = Civ.city(k);
        int shots = 0;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != k.world || Diplomacy.rel(k, o) != Diplomacy.WAR) continue;
            List<BlockPos> targets = new ArrayList<>(Conquest.towns(o));
            if (o.capital != null) targets.add(o.capital);
            for (MobEntity m : new ArrayList<>(k.members)) {
                if (shots >= 2 || c.stone < 1) return;
                if (!m.isAlive() || m instanceof VillagerEntity) continue;
                for (BlockPos tp : targets) {
                    double d2 = m.getBlockPos().getSquaredDistance(tp);
                    if (d2 > 50 * 50 || d2 < 12 * 12) continue;
                    BlockPos g = Fx.surface(w, tp.getX() + w.getRandom().nextInt(9) - 4, tp.getZ() + w.getRandom().nextInt(9) - 4);
                    if (!Tech.cannon(w, k, m, g)) shoot(w, m, g);
                    c.stone -= 1;
                    shots++;
                    break;
                }
            }
        }
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (!on) return;
        if (t % 100 == 0) for (Kingdoms.K k : Kingdoms.K.values())
            if (k.alive && k.world instanceof ServerWorld w && Tech.has(k, Tech.T.MECHANICS)) catapults(w, k);
        if (t % 3000 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w) || !Tech.has(k, Tech.T.FORT)) continue;
            int want = Civ.city(k).era >= 4 ? 2 : 1;
            if (Tech.bastions(w, k)) continue;
            if (WALL.getOrDefault(k, 0) >= want) continue;
            Civ.City c = Civ.city(k);
            if (c.stone < 40 || c.wood < 20) continue;
            c.stone -= 40; c.wood -= 20;
            walls(w, k, want);
        }
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null || k.capital == null) { god.sendMessage(Text.literal("Нет королевства рядом"), true); return; }
        walls(w, k, Civ.city(k).era >= 4 ? 2 : 1);
        god.sendMessage(Text.literal("«" + k.title + "» строит стены").formatted(k.color), true);
    }

    public static void clear() { WALL.clear(); t = 0; }
}
