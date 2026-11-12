package com.vladik.godbox;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * v3.0: реестр игровых систем. Каждая система регистрируется один раз и получает
 * тик сервера и очистку при остановке — больше не нужно вручную дописывать вызовы в GodBox.
 * Порядок регистрации = порядок тиков (как в 2.x), порядок очистки задаётся отдельно.
 */
public final class Systems {
    /** Описание системы: имя (для профилирования/логов), тик и условие включения. */
    public record Sys(String name, Consumer<MinecraftServer> tick, BooleanSupplier enabled) {}

    private static final List<Sys> TICK = new ArrayList<>();
    private static final List<Runnable> CLEAR = new ArrayList<>();
    private static final long[] NANOS = new long[128];
    private static boolean frozen;

    private Systems() {}

    public static void tick(String name, Consumer<MinecraftServer> tick) { tick(name, tick, () -> true); }

    public static void tick(String name, Consumer<MinecraftServer> tick, BooleanSupplier enabled) {
        if (frozen) throw new IllegalStateException("Systems already frozen: " + name);
        TICK.add(new Sys(name, tick, enabled));
    }

    public static void clear(Runnable... rs) { Collections.addAll(CLEAR, rs); }

    /** Запрещает дальнейшую регистрацию (вызывается после onInitialize). */
    public static void freeze() { frozen = true; }

    public static List<Sys> all() { return Collections.unmodifiableList(TICK); }

    /** Среднее время тика системы в микросекундах (экспоненциальное сглаживание). */
    public static long micros(int i) { return i < NANOS.length ? NANOS[i] / 1000 : 0; }

    public static void tickAll(MinecraftServer s) {
        for (int i = 0; i < TICK.size(); i++) {
            Sys y = TICK.get(i);
            if (!y.enabled().getAsBoolean()) continue;
            long t0 = System.nanoTime();
            try {
                y.tick().accept(s);
            } catch (Exception e) {
                GodBox.LOG.error("GodBox system '{}' failed", y.name(), e);
            }
            if (i < NANOS.length) NANOS[i] = (NANOS[i] * 31 + (System.nanoTime() - t0)) / 32;
        }
    }

    public static void clearAll() {
        for (Runnable r : CLEAR) {
            try {
                r.run();
            } catch (Exception e) {
                GodBox.LOG.error("GodBox clear failed", e);
            }
        }
    }

    /** Регистрация всех систем мода в порядке 2.7 (поведение не меняется). */
    static void registerDefaults() {
        tick("perf", Perf::tick);
        tick("tasks", s -> Tasks.tick());
        tick("undo", s -> Undo.tick());
        tick("kingdoms", Kingdoms::tick);
        tick("bosses", s -> Bosses.tick());
        tick("bossModels", BossModels::tick);
        tick("civ", Civ::tick, () -> Perf.go);
        tick("life", Life::tick, () -> Perf.go);
        tick("diplomacy", Diplomacy::tick);
        tick("chronicle", Chronicle::tick);
        tick("ages", Ages::tick);
        tick("faith", Faith::tick);
        tick("events", Events::tick);
        tick("conquest", Conquest::tick);
        tick("save", Save::tick);
        tick("creatures", Creatures::tick);
        tick("worldAges", WorldAges::tick);
        tick("kings", Kings::tick);
        tick("ships", Ships::tick);
        tick("colonies", Colonies::tick);
        tick("tech", Tech::tick, () -> Tech.on);
        tick("wonders", Wonders::tick, () -> Wonders.on);
        tick("magic", Magic::tick);
        tick("navy", Navy::tick);
        tick("roads", Roads::tick);
        tick("siege", Siege::tick);
        tick("economy", Economy::tick);
        tick("vassals", Vassals::tick);
        tick("unrest", Unrest::tick);
        tick("heroes", Heroes::tick);
        tick("highways", Highways::tick);
        tick("storage", Storage::tick);
        tick("decor", Decor::tick);
        tick("prayers", Prayers::tick);
        tick("possess", Possess::tick);
        tick("borders", Borders::tick);
        tick("hud", Hud::tick);
        tick("achievements", Achievements::tick);
        tick("ecosystem", Ecosystem::tick);
        tick("herds", Herds::tick);
        tick("tectonics", Tectonics::tick);
        tick("banners", Banners::tick);
        tick("hand", Hand::tick);
        tick("repair", Repair::tick);
        tick("seasons", Seasons::tick);
        clear(Seasons::clear);
        tick("history", History::tick);
        clear(History::clear);
        tick("inspect", Inspect::tick);
        clear(Inspect::clear);
        tick("plague", Plague::tick);
        clear(Plague::clear);
        tick("treaties", Treaties::tick);
        clear(Treaties::clear);
        Treaties.init();
        tick("mortal", Mortal::tick);
        clear(Mortal::clear);

        clear(Creatures::clear, Kings::clear, WorldAges::clear, Ships::clear,
                Colonies::clear, Tech::clear, Wonders::clear, Magic::clear, Navy::clear, Roads::clear, Siege::clear,
                Economy::clear, Vassals::clear, Unrest::clear, Heroes::clear, Highways::clear, Storage::clear,
                Decor::clear, Prayers::clear, Possess::clear, Achievements::clear, Ecosystem::clear, Herds::clear,
                Tectonics::clear, Banners::clear, Hand::clear, Dynasty::clear, Repair::clear, Perf::clear,
                Tasks::clear, Life::clear, Diplomacy::clear, Ores::clear, Conquest::clear,
                Ages::clear, Faith::clear, Events::clear, Chronicle::clear, Sleep::clear,
                GodManager::clear, Undo::clear, Kingdoms::clear, Bosses::clear, BossModels::clear);
    }
}
