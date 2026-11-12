package com.vladik.godbox;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** v2.3: королевства молятся богу; ответ укрепляет веру, молчание рождает смуту. */
public final class Prayers {
    public static boolean on = true;
    static final Map<Kingdoms.K, Integer> FAITH = new EnumMap<>(Kingdoms.K.class);
    static final Map<Kingdoms.K, int[]> WANT = new EnumMap<>(Kingdoms.K.class);
    static final String[] ASK = {
            "пошли нам урожай — люди голодают",
            "защити нас от врагов",
            "пошли дождь на наши поля",
            "исцели наших раненых",
            "дай нам знак, что ты с нами",
            "даруй нам богатство"};
    static int answered;
    private static int t;

    private Prayers() {}

    static int faith(Kingdoms.K k) { return FAITH.getOrDefault(k, 50); }

    static void add(Kingdoms.K k, int n) { FAITH.put(k, Math.max(0, Math.min(100, faith(k) + n))); }

    static void tell(MinecraftServer s, Text msg) {
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) p.sendMessage(msg, false);
    }

    public static void tick(MinecraftServer s) {
        ++t;
        if (!on) return;
        if (t % 20 == 0) {
            for (Map.Entry<Kingdoms.K, int[]> e : new ArrayList<>(WANT.entrySet())) {
                Kingdoms.K k = e.getKey();
                if (!k.alive || k.capital == null) { WANT.remove(k); continue; }
                if (k.world instanceof ServerWorld w && w.isChunkLoaded(k.capital.getX() >> 4, k.capital.getZ() >> 4))
                    Fx.particles(w, ParticleTypes.END_ROD, k.capital.getX() + 0.5, k.capital.getY() + 4, k.capital.getZ() + 0.5, 8, 0.2, 4, 0.2, 0.01);
                if (t >= e.getValue()[1]) {
                    WANT.remove(k);
                    add(k, -12);
                    Mortal.onPrayerIgnored(k);
                    Unrest.add(k, 6);
                    tell(s, Text.literal("🙏 " + k.title + ": бог молчит… вера падает (" + faith(k) + "%)").formatted(Formatting.GRAY));
                    Chronicle.say(s, k, "тщетно молилось богу — вера пошатнулась");
                }
            }
        }
        if (t % 1800 != 0) return;
        List<Kingdoms.K> can = new ArrayList<>();
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || k.capital == null || !(k.world instanceof ServerWorld)) continue;
            int f = faith(k);
            if (f < 20) Unrest.add(k, 3);
            else if (f > 80) { Unrest.add(k, -2); Civ.city(k).faith++; }
            if (f > 55) add(k, -1);
            if (!WANT.containsKey(k)) can.add(k);
        }
        if (can.isEmpty()) return;
        Kingdoms.K k = can.get(s.getOverworld().getRandom().nextInt(can.size()));
        ServerWorld w = (ServerWorld) k.world;
        int type = choose(k, Civ.city(k), w, w.getRandom());
        WANT.put(k, new int[]{type, t + 2400});
        tell(s, Text.literal("🙏 " + k.title + " молится: «" + ASK[type] + "!» — ответь силой «Ответить на молитву» (2 мин)").formatted(k.color));
        Fx.sound(w, k.capital.getX(), k.capital.getY(), k.capital.getZ(), SoundEvents.BLOCK_BELL_USE, 3f, 0.7f);
    }

    static int choose(Kingdoms.K k, Civ.City c, ServerWorld w, Random r) {
        if (c.food < 20) return 0;
        if (Diplomacy.atWar(k)) return 1;
        if (!w.isRaining() && r.nextInt(3) == 0) return 2;
        int hurt = 0;
        for (MobEntity m : k.members) if (m.isAlive() && m.getHealth() < m.getMaxHealth() * 0.6f) hurt++;
        if (hurt >= 3) return 3;
        return r.nextBoolean() ? 4 : 5;
    }

    public static void answer(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Kingdoms.K k = Tech.nearest(w, pos);
        if (k == null) { god.sendMessage(Text.literal("Рядом нет королевства"), true); return; }
        Civ.City c = Civ.city(k);
        BlockPos cp = k.capital != null ? k.capital : pos;
        int[] p = WANT.remove(k);
        if (p == null) {
            add(k, 3);
            Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, cp.getX() + 0.5, cp.getY() + 3, cp.getZ() + 0.5, 60, 3, 2, 3, 0.3);
            god.sendMessage(Text.literal("✨ " + k.title + " чувствует благословение (вера " + faith(k) + "%)").formatted(k.color), true);
            return;
        }
        switch (p[0]) {
            case 0 -> {
                c.food += 60;
                Fx.particles(w, ParticleTypes.HAPPY_VILLAGER, cp.getX() + 0.5, cp.getY() + 2, cp.getZ() + 0.5, 150, 8, 2, 8, 0.1);
            }
            case 1 -> {
                for (MobEntity m : k.members) {
                    if (!m.isAlive()) continue;
                    m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 2400, 1));
                    m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 2400, 1));
                }
            }
            case 2 -> w.setWeather(0, 6000, true, false);
            case 3 -> {
                for (MobEntity m : k.members) {
                    if (!m.isAlive()) continue;
                    m.heal(m.getMaxHealth());
                    m.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1));
                }
            }
            case 4 -> {
                c.faith += 10;
                Fx.particles(w, ParticleTypes.END_ROD, cp.getX() + 0.5, cp.getY() + 10, cp.getZ() + 0.5, 300, 1.5, 12, 1.5, 0.05);
            }
            default -> Economy.add(k, 80);
        }
        add(k, 15);
        Unrest.add(k, -10);
        answered++;
        Mortal.onPrayerAnswered(w.getServer(), k);
        Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, cp.getX() + 0.5, cp.getY() + 3, cp.getZ() + 0.5, 120, 3, 2, 3, 0.4);
        Fx.sound(w, cp.getX(), cp.getY(), cp.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        Chronicle.say(w.getServer(), k, "получило ответ бога на молитву («" + ASK[p[0]] + "»)");
        god.sendMessage(Text.literal("🙏 Молитва исполнена: " + k.title + " — вера " + faith(k) + "%").formatted(k.color), true);
    }

    static String info(Kingdoms.K k) {
        return faith(k) + "%" + (WANT.containsKey(k) ? " (молится!)" : "");
    }

    public static void clear() {
        FAITH.clear();
        WANT.clear();
        answered = 0;
        t = 0;
    }
}
