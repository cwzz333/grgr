package com.vladik.godbox;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Физический склад: ресурсы города реально лежат в сундуках и бочках склада.
 * Забрал из сундука — город обеднел, положил — разбогател, сжёг склад — запасы сгорели.
 */
final class Storage {
    private Storage() {}

    static boolean on = true;
    static int timer;
    static boolean dirty;
    private static final Map<Civ.Building, int[]> SHOWN = new WeakHashMap<>();
    private static final Item[] ITEM = {Items.OAK_LOG, Items.COBBLESTONE, Items.BREAD, Items.RAW_IRON};
    static final String[] NAME = {"дерево", "камень", "еда", "руда"};

    static void clear() {
        SHOWN.clear();
        timer = 0;
        dirty = false;
    }

    /** Сколько каждого ресурса город хранит без порчи. */
    static int cap(Civ.City c) {
        return 200 + 400 * c.count(Civ.Kind.WAREHOUSE) + 120 * c.count(Civ.Kind.GRANARY);
    }

    static int cls(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (s.isIn(ItemTags.LOGS) || s.isIn(ItemTags.PLANKS)) return 0;
        if (s.isIn(ItemTags.STONE_CRAFTING_MATERIALS) || s.isOf(Items.STONE) || s.isOf(Items.COBBLESTONE)) return 1;
        if (s.contains(DataComponentTypes.FOOD) || s.isOf(Items.WHEAT)) return 2;
        if (s.isOf(Items.RAW_IRON) || s.isOf(Items.IRON_INGOT) || s.isOf(Items.COAL) || s.isOf(Items.RAW_GOLD) || s.isOf(Items.GOLD_INGOT)
            || s.isOf(Items.RAW_COPPER) || s.isOf(Items.COPPER_INGOT) || s.isOf(Items.DIAMOND) || s.isOf(Items.EMERALD)
            || s.isOf(Items.LAPIS_LAZULI) || s.isOf(Items.REDSTONE)) return 3;
        return -1;
    }

    static List<Civ.Building> warehouses(Civ.City c) {
        List<Civ.Building> l = new ArrayList<>();
        for (Civ.Building b : c.buildings) if (b.done && b.kind == Civ.Kind.WAREHOUSE) l.add(b);
        return l;
    }

    static List<Inventory> boxes(ServerWorld w, Civ.Building b) {
        List<Inventory> l = new ArrayList<>();
        for (int y = b.y0; y <= b.y0 + 2; y++)
            for (int x = b.x0; x <= b.x1; x++)
                for (int z = b.z0; z <= b.z1; z++) {
                    BlockEntity be = w.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) l.add((Inventory) be);
                }
        return l;
    }

    static boolean loaded(ServerWorld w, Civ.Building b) {
        return w.isChunkLoaded(b.cx() >> 4, b.cz() >> 4);
    }

    public static void tick(MinecraftServer server) {
        if (!on) return;
        timer++;
        boolean full = timer % 100 == 0;
        if (!full && !(dirty && timer % 20 == 0)) return;
        dirty = false;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.world == null || k.capital == null) continue;
            if (!k.world.shouldTickEntity(k.capital)) continue;
            Civ.City c = Civ.city(k);
            List<Civ.Building> whs = warehouses(c);
            if (!whs.isEmpty()) sync(k, c, k.world, whs);
            if (timer % 1200 == 0 && Civ.economy) spoil(k, c, k.world, whs.size());
        }
    }

    static void sync(Kingdoms.K k, Civ.City c, ServerWorld w, List<Civ.Building> whs) {
        // 1) что изменилось в сундуках с прошлого раза — это сделали игроки или катастрофы
        int lost = 0, added = 0;
        for (Civ.Building b : whs) {
            if (!loaded(w, b)) continue;
            int[] have = new int[4];
            for (Inventory inv : boxes(w, b))
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    int t = cls(s);
                    if (t >= 0) have[t] += s.getCount();
                }
            int[] was = SHOWN.get(b);
            if (was == null) continue;
            int[] d = new int[4];
            for (int t = 0; t < 4; t++) {
                d[t] = have[t] - was[t];
                if (d[t] < 0) lost -= d[t];
                else added += d[t];
            }
            c.wood = Math.max(0, c.wood + d[0]);
            c.stone = Math.max(0, c.stone + d[1]);
            c.food = Math.max(0, c.food + d[2]);
            c.ore = Math.max(0, c.ore + d[3]);
        }
        if (lost >= 24) Civ.tell(w, Text.literal(k.title + ": склад разграблен или разрушен! Потеряно " + lost + " ресурсов").formatted(Formatting.RED));
        if (added >= 24) Civ.tell(w, Text.literal(k.title + ": склад пополнился дарами (+" + added + ")").formatted(Formatting.GREEN));

        // 2) раскладываем запасы города по сундукам поровну
        int n = 0;
        for (Civ.Building b : whs) if (loaded(w, b)) n++;
        if (n == 0) return;
        int[] tot = {c.wood, c.stone, c.food, c.ore};
        for (Civ.Building b : whs) {
            if (!loaded(w, b)) continue;
            List<Inventory> bx = boxes(w, b);
            int[] want = new int[4];
            for (int t = 0; t < 4; t++) want[t] = tot[t] / n;
            int[] wrote = new int[4];
            List<int[]> free = new ArrayList<>();
            for (int bi = 0; bi < bx.size(); bi++) {
                Inventory inv = bx.get(bi);
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isEmpty() || cls(s) >= 0) {
                        if (!s.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
                        free.add(new int[]{bi, i});
                    }
                }
            }
            int fi = 0;
            boolean any = true;
            while (any && fi < free.size()) {
                any = false;
                for (int t = 0; t < 4 && fi < free.size(); t++) {
                    int left = want[t] - wrote[t];
                    if (left <= 0) continue;
                    int put = Math.min(64, left);
                    int[] sl = free.get(fi++);
                    bx.get(sl[0]).setStack(sl[1], new ItemStack(ITEM[t], put));
                    wrote[t] += put;
                    any = true;
                }
            }
            for (Inventory inv : bx) inv.markDirty();
            SHOWN.put(b, wrote);
        }
    }

    /** Без складов лишние запасы гниют, ржавеют и растаскиваются. */
    static void spoil(Kingdoms.K k, Civ.City c, ServerWorld w, int whs) {
        int cap = cap(c);
        int loss = 0;
        if (c.wood > cap) { int l = (c.wood - cap) / 8 + 1; c.wood -= l; loss += l; }
        if (c.stone > cap) { int l = (c.stone - cap) / 10 + 1; c.stone -= l; loss += l; }
        if (c.food > cap) { int l = (c.food - cap) / 5 + 1; c.food -= l; loss += l; }
        if (c.ore > cap) { int l = (c.ore - cap) / 10 + 1; c.ore -= l; loss += l; }
        if (loss >= 30 && whs == 0 && w.getRandom().nextInt(3) == 0)
            Civ.tell(w, Text.literal(k.title + ": запасы портятся без склада (−" + loss + ")").formatted(Formatting.GOLD));
    }

    /** Рабочий принёс добычу к складу — звук, частицы, быстрая пересинхронизация. */
    static void deposit(Kingdoms.K k, Civ.City c, ServerWorld w, VillagerEntity v) {
        for (Civ.Building b : c.buildings) {
            if (!b.done || b.kind != Civ.Kind.WAREHOUSE) continue;
            if (Civ.hdist(v, Civ.front(k, b, w)) > 4.5) continue;
            Fx.sound(w, b.cx() + 0.5, b.y0 + 1, b.cz() + 0.5, w.getRandom().nextBoolean() ? SoundEvents.BLOCK_BARREL_OPEN : SoundEvents.BLOCK_CHEST_OPEN, 0.7f, 1f);
            Fx.particles(w, ParticleTypes.COMPOSTER, v.getX(), v.getY() + 1, v.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            dirty = true;
            return;
        }
    }

    static String info(Civ.City c) {
        return c.count(Civ.Kind.WAREHOUSE) + " (вместимость " + cap(c) + " каждого)";
    }
}
