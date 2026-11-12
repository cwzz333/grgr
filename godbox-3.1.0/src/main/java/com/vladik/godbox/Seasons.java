package com.vladik.godbox;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** v3.1: времена года и климат. Все множители равны 1, когда закон выключен. */
public final class Seasons {
    public static boolean on = true;
    public static final int SPRING = 0, SUMMER = 1, AUTUMN = 2, WINTER = 3;
    static final String[] NAMES = {"Весна", "Лето", "Осень", "Зима"};
    private static final String[] ICONS = {"🌱", "☀", "🍂", "❄"};
    private static final String[] HINTS = {
        "Наступила весна — посевы растут быстрее, в семьях прибавление",
        "Наступило лето — поля дают полный урожай, но выше риск засух и пожаров",
        "Наступила осень — богатый урожай, караваны ходят чаще",
        "Наступила зима — поля почти не родят, еды уходит больше, армии неохотно идут в поход"
    };
    private static final Formatting[] COLORS = {Formatting.GREEN, Formatting.YELLOW, Formatting.GOLD, Formatting.AQUA};

    static int season, time, year = 1;
    private static int t;
    /** Температура биома столицы (обновляется, пока чанк загружен). */
    private static final Map<Kingdoms.K, Float> TEMP = new EnumMap<>(Kingdoms.K.class);
    private static final Map<Kingdoms.K, Boolean> DRY = new EnumMap<>(Kingdoms.K.class);

    private Seasons() {}

    public static void init() {
        SaveExt.register("seasons",
            () -> List.of((on ? 1 : 0) + "|" + season + "|" + time + "|" + year),
            a -> {
                try {
                    if (a.length < 4) return;
                    on = !a[0].equals("0");
                    season = Math.floorMod(Integer.parseInt(a[1]), 4);
                    time = Math.max(0, Integer.parseInt(a[2]));
                    year = Math.max(1, Integer.parseInt(a[3]));
                } catch (Exception ignored) {
                }
            },
            () -> { on = true; reset(); });
    }

    private static void reset() {
        season = 0;
        time = 0;
        year = 1;
        t = 0;
        TEMP.clear();
        DRY.clear();
    }

    // ---------------- доступ для HUD и других систем ----------------
    /** Длина одного сезона в тиках (следует за скоростью времени сервера). */
    public static int length() { return Math.max(200, Config.i("seasons.length", 6000)); }

    public static int index() { return season; }

    public static int year() { return year; }

    /** Название текущего сезона ("" — закон выключен). */
    public static String name() { return on ? NAMES[season] : ""; }

    /** Доля прошедшего сезона 0..1. */
    public static float progress() { return on ? Math.min(1f, time / (float) length()) : 0f; }

    /** Короткая строка: "Зима, год 3". */
    public static String label() { return on ? NAMES[season] + ", год " + year : ""; }

    public static boolean cold(Kingdoms.K k) { return k != null && TEMP.getOrDefault(k, 0.8f) < 0.2f; }

    public static boolean hot(Kingdoms.K k) {
        return k != null && (TEMP.getOrDefault(k, 0.8f) >= 1.5f || DRY.getOrDefault(k, false));
    }

    /** Климат столицы одним словом. */
    public static String climate(Kingdoms.K k) { return cold(k) ? "холодный" : hot(k) ? "жаркий" : "умеренный"; }

    /** Зима для королевства: в холодном климате она захватывает конец осени и начало весны. */
    public static boolean winter(Kingdoms.K k) {
        if (!on) return false;
        if (season == WINTER) return true;
        if (!cold(k)) return false;
        float p = progress();
        return (season == AUTUMN && p > 0.6f) || (season == SPRING && p < 0.4f);
    }

    /** Суровость текущего сезона для королевства: 1 — обычная, больше — тяжелее. */
    public static double severity(Kingdoms.K k) {
        if (!on) return 1.0;
        double h = Math.max(1.0, Math.min(3.0, Config.d("seasons.harsh", 1.5)));
        if (cold(k) && winter(k)) return h;
        if (hot(k) && season == SUMMER) return h;
        return 1.0;
    }

    /** Множитель урожая с полей. */
    public static double food(Kingdoms.K k) {
        if (!on) return 1.0;
        if (winter(k)) return Math.max(0.1, Math.min(1.0, Config.d("seasons.winter.food", 0.5))) / severity(k);
        if (season == AUTUMN) return Math.max(1.0, Math.min(2.0, Config.d("seasons.autumn.food", 1.25)));
        return 1.0;
    }

    /** Множитель скорости роста посевов. */
    public static double growth() {
        if (!on) return 1.0;
        return season == SPRING ? 1.5 : season == WINTER ? 0.4 : 1.0;
    }

    /** Расход еды с учётом зимы. */
    public static int eat(Kingdoms.K k, int n) {
        if (n <= 0 || !winter(k)) return n;
        return (int) Math.ceil(n * (1.0 + 0.2 * severity(k)));
    }

    /** Раз в сколько циклов экономики возможны рождения (обычно 3). */
    public static int birthPeriod() {
        if (!on) return 3;
        return season == SPRING ? 2 : season == WINTER ? 4 : 3;
    }

    /** Зимой порты в холодном климате скованы льдом: корабли не выходят. */
    public static boolean frozen(Kingdoms.K k) { return on && cold(k) && winter(k); }

    /** На сколько зимой ниже порог объявления войны. */
    public static int warBias() {
        return on && season == WINTER ? Math.max(0, Math.min(40, Config.i("seasons.winter.war", 15))) : 0;
    }

    /** Период отправки караванов: осенью чаще. */
    public static int tradePeriod() { return on && season == AUTUMN ? 600 : 900; }

    /** Вес случайного события e (номера как в Events.fire) в текущем сезоне. */
    public static double weight(Kingdoms.K k, int e) {
        if (!on) return 1.0;
        double sv = severity(k);
        return switch (season) {
            case SPRING -> e == 0 ? 1.3 : 1.0;
            case SUMMER -> e == 1 ? 2.0 * sv : e == 7 ? 2.0 : 1.0;
            case AUTUMN -> e == 0 ? 2.5 : e == 5 ? 1.5 : 1.0;
            default -> e == 0 ? 0.3 : e == 1 ? 0.3 : e == 4 ? 1.5 : e == 7 ? 0.6 : 1.0;
        };
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        if (!on || s.getTickManager().isFrozen()) return;
        t++;
        if (t % 200 == 1) climate();
        if (t % 40 == 0) snow();
        if (++time >= length()) next(s);
    }

    private static void climate() {
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w)) {
                TEMP.remove(k);
                DRY.remove(k);
                continue;
            }
            BlockPos c = k.capital;
            if (!w.isChunkLoaded(c.getX() >> 4, c.getZ() >> 4)) continue;
            try {
                Biome b = w.getBiome(c).value();
                TEMP.put(k, b.getTemperature());
                DRY.put(k, !b.hasPrecipitation());
            } catch (Exception ignored) {
            }
        }
    }

    /** Дешёвый снегопад над столицами зимой (только частицы). */
    private static void snow() {
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld w) || !winter(k) || hot(k)) continue;
            BlockPos c = k.capital;
            if (!w.shouldTickEntity(c) || w.getPlayers().isEmpty()) continue;
            Fx.particles(w, ParticleTypes.SNOWFLAKE, c.getX() + 0.5, c.getY() + 12, c.getZ() + 0.5, cold(k) ? 60 : 30, 16, 4, 16, 0.01);
        }
    }

    static void next(MinecraftServer s) {
        season = (season + 1) % 4;
        time = 0;
        if (season == SPRING) {
            year++;
            Chronicle.log("\u00a7eНаступил " + year + "-й год: зима позади, пришла весна");
        }
        if (s == null) return;
        Text msg = Text.literal(ICONS[season] + " " + HINTS[season] + " (год " + year + ")").formatted(COLORS[season]);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (GodManager.isGod(p)) p.sendMessage(msg, false);
        }
    }

    /** Сила «Сменить сезон». */
    public static void power(ServerPlayerEntity god) {
        if (!on) {
            god.sendMessage(Text.literal("Закон «Времена года» выключен").formatted(Formatting.GRAY), true);
            return;
        }
        next(god.getServer());
        god.sendMessage(Text.literal(ICONS[season] + " " + NAMES[season] + ", год " + year).formatted(COLORS[season]), true);
    }

    public static void clear() {
        reset();
    }
}
