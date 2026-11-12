package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** История изменений блоков для Ctrl+Z. Каждое применение силы — отдельная группа. */
public final class Undo {
    private static final int MAX_BLOCKS = 250000;
    private static final int MAX_GROUPS = 6;
    /** Сколько тиков после применения силы записываются изменения (лава, вода, огонь, задачи). */
    private static final int WINDOW = 2400;

    private static final class Group {
        final ServerWorld w;
        final java.util.UUID owner;
        final LinkedHashMap<BlockPos, BlockState> old = new LinkedHashMap<>();
        int age;

        Group(ServerWorld w, java.util.UUID owner) {
            this.w = w;
            this.owner = owner;
        }
    }

    private static final ArrayDeque<Group> GROUPS = new ArrayDeque<>();
    private static Group current;
    private static boolean restoring;

    private Undo() {}

    public static void begin(ServerWorld w) { begin(w, null); }

    /** v3.0: у каждого бога своя история отмены (MAX_GROUPS групп на игрока). */
    public static void begin(ServerWorld w, java.util.UUID owner) {
        current = new Group(w, owner);
        GROUPS.addLast(current);
        int mine = 0;
        for (java.util.Iterator<Group> it = GROUPS.descendingIterator(); it.hasNext(); ) {
            Group g = it.next();
            if (java.util.Objects.equals(g.owner, owner) && ++mine > MAX_GROUPS) it.remove();
        }
        while (GROUPS.size() > MAX_GROUPS * 4) GROUPS.removeFirst();
    }

    public static boolean active(World w) {
        return current != null && !restoring && current.w == w;
    }

    public static void record(BlockPos p, BlockState old) {
        Group g = current;
        if (g == null || g.old.size() >= MAX_BLOCKS) return;
        g.old.putIfAbsent(p.toImmutable(), old);
    }

    public static void tick() {
        if (current != null && ++current.age > WINDOW) current = null;
    }

    public static void clear() {
        GROUPS.clear();
        current = null;
        restoring = false;
    }

    public static void undo(ServerPlayerEntity god) {
        Group g = null;
        java.util.UUID me = god.getUuid();
        for (java.util.Iterator<Group> it = GROUPS.descendingIterator(); it.hasNext(); ) {
            Group last = it.next();
            if (last.owner != null && !last.owner.equals(me)) continue;
            it.remove();
            if (!last.old.isEmpty()) {
                g = last;
                break;
            }
        }
        if (current != null && (current.owner == null || current.owner.equals(me))) current = null;
        if (g == null) {
            god.sendMessage(Text.literal("Нечего отменять"), true);
            return;
        }
        final ServerWorld w = g.w;
        final List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(g.old.entrySet());
        final int total = list.size();
        Tasks.add(new Tasks.Task() {
            int i = 0;

            @Override
            public boolean tick() {
                restoring = true;
                try {
                    int end = Math.min(list.size(), i + 20000);
                    for (; i < end; i++) {
                        Map.Entry<BlockPos, BlockState> e = list.get(i);
                        w.setBlockState(e.getKey(), e.getValue(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                    }
                } finally {
                    restoring = false;
                }
                return i >= list.size();
            }
        });
        god.sendMessage(Text.literal("Отменено блоков: " + total), true);
    }
}
