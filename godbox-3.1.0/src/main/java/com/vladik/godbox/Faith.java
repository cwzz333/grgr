package com.vladik.godbox;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** Религии, культура, дары и чудеса. */
public final class Faith {
    public static final String[] CULTURES = {"Воинственные", "Торговцы", "Строители", "Земледельцы", "Мудрецы", "Мореходы"};
    public static final int WARLIKE = 0, TRADERS = 1, BUILDERS = 2, FARMERS = 3, SAGES = 4, SEA = 5;
    private static final String[] KINDS = {"Культ", "Вера", "Орден", "Церковь", "Путь", "Братство"};
    private static final String[] GODS = {"Солнца", "Луны", "Звёзд", "Дракона", "Моря", "Огня", "Древа", "Грома", "Камня", "Пустоты", "Ветра", "Зари"};
    static final List<String> RELIGIONS = new ArrayList<>();
    private static int timer;

    private Faith() {}

    static String religion(Civ.City c) {
        return c.religion < 0 || c.religion >= RELIGIONS.size() ? "язычество" : RELIGIONS.get(c.religion);
    }

    static String culture(Civ.City c) {
        return c.culture < 0 ? "—" : CULTURES[c.culture % CULTURES.length];
    }

    static void found(MinecraftServer s, Kingdoms.K k, Civ.City c, Random r) {
        String n = null;
        for (int i = 0; i < 20 && (n == null || RELIGIONS.contains(n)); i++) n = KINDS[r.nextInt(KINDS.length)] + " " + GODS[r.nextInt(GODS.length)];
        if (RELIGIONS.contains(n)) n = n + " " + (RELIGIONS.size() + 1);
        RELIGIONS.add(n);
        c.religion = RELIGIONS.size() - 1;
        c.faith = Math.max(c.faith, 25);
        Chronicle.say(s, k, "основало новую религию «" + n + "»");
        if (k.world != null && k.capital != null) {
            BlockPos p = k.capital;
            Fx.particles(k.world, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 4, p.getZ() + 0.5, 80, 1, 4, 1, 0.05);
            k.world.playSound(null, p, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3f, 0.8f);
        }
    }

    public static void tick(MinecraftServer s) {
        if (++timer % 200 != 50) return;
        Kingdoms.K[] all = Kingdoms.K.values();
        for (Kingdoms.K k : all) {
            if (!k.alive || k.world == null || k.capital == null) continue;
            Civ.City c = Civ.city(k);
            Random r = k.world.getRandom();
            if (c.culture < 0) {
                c.culture = r.nextInt(CULTURES.length);
                Chronicle.say(s, k, "— народ с культурой «" + CULTURES[c.culture] + "»");
            }
            int temples = c.count(Civ.Kind.TEMPLE);
            if (c.religion < 0 && (temples > 0 ? r.nextInt(3) == 0 : r.nextInt(60) == 0)) found(s, k, c, r);
            if (c.religion >= 0) c.faith = Math.min(999, c.faith + 1 + temples * 3 + (c.castle ? 2 : 0) + Races.faith(k));
            switch (c.culture) {
                case BUILDERS -> { c.wood += 1; c.stone += 1; }
                case FARMERS -> c.food += 2;
                case SEA -> c.food += 1;
                case TRADERS -> c.ore += r.nextInt(3) == 0 ? 1 : 0;
                default -> {}
            }
            for (Kingdoms.K o : all) {
                if (o == k || !o.alive || o.world != k.world || o.capital == null) continue;
                if (k.capital.getSquaredDistance(o.capital) > 400.0 * 400.0) continue;
                Civ.City oc = Civ.city(o);
                if (c.religion >= 0 && oc.religion == c.religion) {
                    if (r.nextInt(3) == 0) Diplomacy.op(k, o, 1);
                } else if (c.religion >= 0 && oc.religion >= 0) {
                    if (r.nextInt(4) == 0) Diplomacy.op(k, o, -1);
                }
                if (c.culture == WARLIKE && r.nextInt(4) == 0) Diplomacy.op(k, o, -1);
                if (c.culture == TRADERS && r.nextInt(4) == 0) Diplomacy.op(k, o, 1);
                if (c.religion >= 0 && oc.religion != c.religion && c.faith > oc.faith + 40 && r.nextInt(Tech.convertRoll(k, 10)) == 0) convert(s, o, oc, c.religion);
                Tech.books(s, k, o);
            }
        }
    }

    static void convert(MinecraftServer s, Kingdoms.K o, Civ.City oc, int rel) {
        oc.religion = rel;
        oc.faith = oc.faith / 2 + 10;
        Chronicle.say(s, o, "приняло веру «" + RELIGIONS.get(rel) + "»");
        if (o.world != null && o.capital != null) {
            BlockPos p = o.capital;
            Fx.particles(o.world, ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 2, p.getZ() + 0.5, 150, 3, 2, 3, 1);
        }
    }

    private static Kingdoms.K target(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Diplomacy.nearest(w, pos);
        if (k == null) god.sendMessage(Text.literal("Рядом нет королевства"), true);
        return k;
    }

    // ---------------- силы бога ----------------
    public static void gift(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int type, int brush) {
        Kingdoms.K k = target(god, w, pos);
        if (k == null) return;
        Civ.City c = Civ.city(k);
        int n = 20 + 5 * Math.max(1, brush % 100);
        String what;
        if (type < 0) {
            for (int t = 0; t < 4; t++) Diplomacy.add(c, t, n);
            what = "по " + n + " всех ресурсов";
        } else {
            Diplomacy.add(c, type, n);
            what = n + " (" + Diplomacy.RES[type] + ")";
        }
        BlockPos p = k.capital;
        Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 2, p.getZ() + 0.5, 80, 3, 2, 3, 0.1);
        Fx.particles(w, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 10, p.getZ() + 0.5, 60, 0.3, 6, 0.3, 0.02);
        w.playSound(null, p, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2f, 1.2f);
        Chronicle.say(god.getServer(), k, "получило дар богов: " + what);
    }

    public static void miracle(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = target(god, w, pos);
        if (k == null) return;
        Civ.City c = Civ.city(k);
        for (MobEntity m : k.members) {
            if (!m.isAlive()) continue;
            m.setHealth(m.getMaxHealth());
            Fx.particles(w, ParticleTypes.HEART, m.getX(), m.getY() + 2, m.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
        }
        c.food += 20;
        c.faith = Math.min(999, c.faith + 50);
        if (c.religion < 0) found(god.getServer(), k, c, w.getRandom());
        BlockPos p = k.capital;
        Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, p.getX() + 0.5, p.getY() + 3, p.getZ() + 0.5, 150, 4, 3, 4, 0.5);
        w.playSound(null, p, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 2f, 1f);
        Chronicle.say(god.getServer(), k, "увидело чудо! Вера окрепла, больные исцелены");
    }

    public static void newFaith(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = target(god, w, pos);
        if (k != null) found(god.getServer(), k, Civ.city(k), w.getRandom());
    }

    public static void preach(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = target(god, w, pos);
        if (k == null) return;
        Civ.City c = Civ.city(k);
        if (c.religion < 0) found(god.getServer(), k, c, w.getRandom());
        int n = 0;
        for (Kingdoms.K o : Kingdoms.K.values()) {
            if (o == k || !o.alive || o.world != w) continue;
            Civ.City oc = Civ.city(o);
            if (oc.religion == c.religion) continue;
            convert(god.getServer(), o, oc, c.religion);
            n++;
        }
        if (n == 0) god.sendMessage(Text.literal("Все соседи уже веруют в «" + religion(c) + "»"), true);
    }

    public static void nextCulture(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = target(god, w, pos);
        if (k == null) return;
        Civ.City c = Civ.city(k);
        c.culture = (Math.max(-1, c.culture) + 1) % CULTURES.length;
        Chronicle.say(god.getServer(), k, "сменило культуру: «" + CULTURES[c.culture] + "»");
    }

    public static void clear() {
        RELIGIONS.clear();
        timer = 0;
    }
}
