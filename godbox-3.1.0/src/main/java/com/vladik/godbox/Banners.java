package com.vladik.godbox;

import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Гербы королевств: у каждого своё знамя с узором, оно висит на постройках, над столицей и у знаменосцев. */
public final class Banners {
    /** Номер варианта герба (меняется силой «Новый герб»), сохраняется в Save. */
    public static final int[] SALT = new int[Kingdoms.K.values().length];
    private static int timer;

    private static final Set<Civ.Kind> FLAGGED = EnumSet.of(Civ.Kind.BARRACKS, Civ.Kind.TOWER, Civ.Kind.TEMPLE,
            Civ.Kind.CASTLE, Civ.Kind.MANOR, Civ.Kind.WAREHOUSE);

    private static final List<RegistryKey<BannerPattern>> FIELDS = List.of(
            BannerPatterns.HALF_VERTICAL, BannerPatterns.HALF_HORIZONTAL, BannerPatterns.STRIPE_CENTER,
            BannerPatterns.STRIPE_MIDDLE, BannerPatterns.DIAGONAL_LEFT, BannerPatterns.DIAGONAL_RIGHT,
            BannerPatterns.STRAIGHT_CROSS, BannerPatterns.CROSS, BannerPatterns.SMALL_STRIPES,
            BannerPatterns.TRIANGLE_BOTTOM, BannerPatterns.TRIANGLE_TOP, BannerPatterns.GRADIENT,
            BannerPatterns.STRIPE_TOP, BannerPatterns.STRIPE_BOTTOM);
    private static final List<RegistryKey<BannerPattern>> CHARGES = List.of(
            BannerPatterns.CIRCLE, BannerPatterns.RHOMBUS, BannerPatterns.FLOWER, BannerPatterns.SKULL,
            BannerPatterns.CREEPER, BannerPatterns.GLOBE, BannerPatterns.TRIANGLES_TOP, BannerPatterns.TRIANGLES_BOTTOM);
    private static final List<RegistryKey<BannerPattern>> FRAMES = List.of(
            BannerPatterns.BORDER, BannerPatterns.CURLY_BORDER, BannerPatterns.BRICKS);
    private static final DyeColor[] PALETTE = {DyeColor.WHITE, DyeColor.BLACK, DyeColor.ORANGE, DyeColor.LIGHT_BLUE,
            DyeColor.LIME, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.BROWN, DyeColor.MAGENTA, DyeColor.PINK,
            DyeColor.LIGHT_GRAY, DyeColor.GRAY};

    private Banners() {}

    public static DyeColor dye(Kingdoms.K k) {
        return k.dye;
    }

    static Item item(Kingdoms.K k) {
        return k.bannerItem();
    }

    static Block block(Kingdoms.K k) {
        return k.banner();
    }

    private static DyeColor pick(Random r, DyeColor... not) {
        for (int i = 0; i < 20; i++) {
            DyeColor c = PALETTE[r.nextInt(PALETTE.length)];
            boolean bad = false;
            for (DyeColor n : not) if (n == c) bad = true;
            if (!bad) return c;
        }
        return DyeColor.WHITE;
    }

    private static void add(BannerPatternsComponent.Builder b, Registry<BannerPattern> reg, RegistryKey<BannerPattern> key, DyeColor c) {
        reg.getEntry(key).ifPresent(e -> b.add(e, c));
    }

    /** Узор герба: поле, символ и иногда кайма. Зависит от сида мира, королевства и номера варианта. */
    public static BannerPatternsComponent patterns(ServerWorld w, Kingdoms.K k) {
        Random r = Random.create(w.getSeed() * 31L + k.ordinal() * 7919L + SALT[k.ordinal()] * 104729L + 17L);
        Registry<BannerPattern> reg = w.getRegistryManager().get(RegistryKeys.BANNER_PATTERN);
        DyeColor base = dye(k);
        DyeColor second = pick(r, base);
        DyeColor third = pick(r, base, second);
        BannerPatternsComponent.Builder b = new BannerPatternsComponent.Builder();
        add(b, reg, FIELDS.get(r.nextInt(FIELDS.size())), second);
        add(b, reg, CHARGES.get(r.nextInt(CHARGES.size())), r.nextBoolean() ? third : base);
        if (r.nextInt(3) == 0) add(b, reg, FRAMES.get(r.nextInt(FRAMES.size())), second);
        return b.build();
    }

    /** Знамя королевства как предмет (для знаменосцев и подарков). */
    public static ItemStack stack(ServerWorld w, Kingdoms.K k) {
        ItemStack st = new ItemStack(item(k));
        st.set(DataComponentTypes.BANNER_PATTERNS, patterns(w, k));
        return st;
    }

    private static void paint(ServerWorld w, BlockPos p, ItemStack st) {
        BlockEntity be = w.getBlockEntity(p);
        if (!(be instanceof BannerBlockEntity bbe)) return;
        bbe.readComponents(st);
        bbe.markDirty();
        BlockState s = w.getBlockState(p);
        w.updateListeners(p, s, s, Block.NOTIFY_ALL);
    }

    /** Ставит стоячее знамя королевства. */
    public static boolean place(ServerWorld w, Kingdoms.K k, BlockPos p, int rotation) {
        if (!w.getBlockState(p).isAir()) return false;
        BlockState st = block(k).getDefaultState().with(BannerBlock.ROTATION, Math.floorMod(rotation, 16));
        if (!st.canPlaceAt(w, p)) return false;
        w.setBlockState(p, st, Block.NOTIFY_ALL);
        paint(w, p, stack(w, k));
        return true;
    }

    /** Флаг на крыше готовой постройки. */
    public static void onBuilt(Kingdoms.K k, ServerWorld w, Civ.Building b) {
        if (!FLAGGED.contains(b.kind)) return;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(b.cx(), 0, b.cz()));
        if (top.getY() - b.y0 > 30) return;
        int rot = Math.floorMod((int) Math.round(Math.atan2(k.capital.getX() - b.cx(), b.cz() - k.capital.getZ()) / (Math.PI * 2) * 16), 16);
        place(w, k, top, rot);
    }

    /** Перекрашивает знамёна цвета королевства в радиусе (в чанках). force=false — только пустые, без узора. */
    static int repaint(ServerWorld w, Kingdoms.K k, BlockPos center, int chunks, boolean force) {
        DyeColor color = dye(k);
        ItemStack st = null;
        int n = 0;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(x, z);
                if (ch == null) continue;
                List<BlockPos> found = new ArrayList<>();
                for (Map.Entry<BlockPos, BlockEntity> e : ch.getBlockEntities().entrySet()) {
                    if (!(e.getValue() instanceof BannerBlockEntity bbe)) continue;
                    BlockState s = w.getBlockState(e.getKey());
                    if (!(s.getBlock() instanceof AbstractBannerBlock ab) || ab.getColor() != color) continue;
                    if (!force && !bbe.getPatterns().layers().isEmpty()) continue;
                    found.add(e.getKey().toImmutable());
                }
                for (BlockPos p : found) {
                    if (st == null) st = stack(w, k);
                    paint(w, p, st);
                    n++;
                }
            }
        }
        return n;
    }

    private static void capitalFlag(ServerWorld w, Kingdoms.K k) {
        BlockPos c = k.capital;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, c);
        if (top.getY() - c.getY() > 40) return;
        for (int y = c.getY() - 2; y <= top.getY() + 1; y++) {
            if (w.getBlockState(new BlockPos(c.getX(), y, c.getZ())).getBlock() instanceof AbstractBannerBlock) return;
        }
        place(w, k, top, 0);
    }

    public static void tick(MinecraftServer server) {
        if (++timer % 600 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world == null || k.capital == null) continue;
            if (!k.world.isChunkLoaded(k.capital)) continue;
            capitalFlag(k.world, k);
            repaint(k.world, k, k.capital, 6, false);
            List<BlockPos> towns = Conquest.TOWNS.get(k);
            if (towns != null) for (BlockPos t : new ArrayList<>(towns)) if (k.world.isChunkLoaded(t)) repaint(k.world, k, t, 4, false);
        }
    }

    /** Сила «Новый герб»: ближайшее королевство меняет герб, знамена в городах перекрашиваются. */
    public static void reroll(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Diplomacy.nearest(w, pos);
        if (k == null || !k.alive || k.capital == null) {
            god.sendMessage(Text.literal("Рядом нет королевства"), true);
            return;
        }
        SALT[k.ordinal()]++;
        int n = repaint(k.world, k, k.capital, 6, true);
        List<BlockPos> towns = Conquest.TOWNS.get(k);
        if (towns != null) for (BlockPos t : new ArrayList<>(towns)) n += repaint(k.world, k, t, 4, true);
        for (var m : new ArrayList<>(k.members)) {
            if (m.isAlive() && m.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).getItem() == item(k))
                m.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, stack(k.world, k));
        }
        BlockPos c = k.capital;
        Fx.particles(k.world, ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 40, 3, 2, 3, 0.05);
        Fx.sound(k.world, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, SoundEvents.UI_LOOM_TAKE_RESULT, 2f, 1f);
        god.sendMessage(Text.literal(k.title + " приняло новый герб (перекрашено знамён: " + n + ")").formatted(k.color), false);
        Chronicle.log(k.title + " приняло новый герб");
    }

    public static void clear() {
        timer = 0;
    }
}
