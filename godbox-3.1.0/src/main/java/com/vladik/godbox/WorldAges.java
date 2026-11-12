package com.vladik.godbox;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
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
import java.util.List;

/** Мировые эпохи как в WorldBox: надежда, солнце, чудеса, хаос, тьма, луна, лёд, пепел, отчаяние. */
public final class WorldAges {
    public static boolean on = true;
    static int age;
    static int timer;
    private static int cyc;
    static final int LEN = 24000;
    public static final String[] NAMES = {"Эпоха надежды", "Эпоха солнца", "Эпоха чудес", "Эпоха хаоса", "Эпоха тьмы", "Эпоха луны", "Эпоха льда", "Эпоха пепла", "Эпоха отчаяния"};
    static final String[] DESC = {
        "мир и сытость, жители выздоравливают",
        "вечный день, ясное небо, богатые урожаи",
        "небеса дарят королевствам ресурсы",
        "королевства сходят с ума и объявляют войны",
        "вечная ночь, из тьмы приходит нежить",
        "долгие ночи, стаи волков бродят у городов",
        "снегопад, вода замерзает, земля покрывается снегом",
        "грозы, молнии, пепел и пожары",
        "мор и голод ослабляют королевства"};
    static final Formatting[] COL = {Formatting.GREEN, Formatting.YELLOW, Formatting.LIGHT_PURPLE, Formatting.RED, Formatting.DARK_GRAY, Formatting.BLUE, Formatting.AQUA, Formatting.GOLD, Formatting.DARK_PURPLE};

    private WorldAges() {}

    public static String current() { return NAMES[age]; }

    public static void set(MinecraftServer s, int a) {
        age = Math.max(0, Math.min(NAMES.length - 1, a));
        timer = 0;
        Text t = Text.literal("☀ Наступила " + NAMES[age] + ": " + DESC[age]).formatted(COL[age]);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            p.sendMessage(t, false);
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sound.SoundCategory.MASTER, 0.6f, 0.8f);
        }
        Chronicle.log("Наступила " + NAMES[age]);
        ServerWorld w = s.getOverworld();
        if (age == 1 || age == 0) w.setWeather(12000, 0, false, false);
        if (age == 6) w.setWeather(0, 24000, true, false);
        if (age == 7) w.setWeather(0, 24000, true, true);
    }

    public static void tick(MinecraftServer s) {
        if (on && ++timer >= LEN) {
            Random r = s.getOverworld().getRandom();
            int n = r.nextInt(NAMES.length);
            if (n == age) n = (n + 1) % NAMES.length;
            set(s, n);
        }
        if (s.getTicks() % 40 != 0) return;
        cyc++;
        ServerWorld w = s.getOverworld();
        Random r = w.getRandom();
        long tod = w.getTimeOfDay() % 24000L;
        switch (age) {
            case 0 -> {
                if (cyc % 30 == 0) for (Kingdoms.K k : alive()) {
                    Civ.city(k).food += 3;
                    for (MobEntity m : k.members) if (m.isAlive()) m.heal(2f);
                }
            }
            case 1 -> {
                if (tod > 12000) w.setTimeOfDay(w.getTimeOfDay() + (24000L - tod) + 1000L);
                if (cyc % 30 == 0) for (Kingdoms.K k : alive()) Civ.city(k).food += 2;
            }
            case 2 -> {
                if (cyc % 30 == 0) {
                    List<Kingdoms.K> ks = alive();
                    if (!ks.isEmpty()) {
                        Kingdoms.K k = ks.get(r.nextInt(ks.size()));
                        Civ.City c = Civ.city(k);
                        c.wood += 10; c.stone += 10; c.ore += 4; c.food += 6;
                        BlockPos b = k.capital;
                        if (k.world != null) Fx.particles(k.world, ParticleTypes.TOTEM_OF_UNDYING, b.getX() + 0.5, b.getY() + 3, b.getZ() + 0.5, 60, 3, 2, 3, 0.3);
                        if (r.nextInt(3) == 0) Chronicle.say(s, k, "получает дар небес");
                    }
                }
            }
            case 3 -> {
                if (cyc % 75 == 0 && !Kingdoms.eternalPeace) {
                    List<Kingdoms.K> ks = alive();
                    if (ks.size() >= 2) {
                        Kingdoms.K a = ks.get(r.nextInt(ks.size()));
                        Kingdoms.K b = ks.get(r.nextInt(ks.size()));
                        if (a != b && Diplomacy.rel(a, b) != Diplomacy.WAR) {
                            Diplomacy.set(a, b, Diplomacy.WAR);
                            Kingdoms.war = true;
                            Chronicle.say(s, a, "в безумии хаоса объявляет войну: " + b.title);
                        }
                    }
                }
            }
            case 4, 5 -> {
                if (tod < 13500) w.setTimeOfDay(w.getTimeOfDay() - tod + 14000L);
                if (cyc % 30 == 0) for (Kingdoms.K k : alive()) {
                    if (k.world != w || !w.isChunkLoaded(k.capital)) continue;
                    EntityType<? extends MobEntity> t = age == 4 ? (r.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON) : EntityType.WOLF;
                    for (int i = 0; i < 2; i++) {
                        BlockPos p = Fx.surface(w, k.capital.getX() + r.nextInt(41) - 20, k.capital.getZ() + r.nextInt(41) - 20);
                        if (w.isChunkLoaded(p)) t.spawn(w, p, SpawnReason.EVENT);
                    }
                }
            }
            case 6 -> {
                for (ServerPlayerEntity pl : w.getPlayers()) {
                    for (int i = 0; i < 30; i++) {
                        BlockPos p = Fx.surface(w, pl.getBlockX() + r.nextInt(97) - 48, pl.getBlockZ() + r.nextInt(97) - 48);
                        if (!w.isChunkLoaded(p)) continue;
                        BlockPos d = p.down();
                        if (w.getBlockState(d).isOf(Blocks.WATER)) w.setBlockState(d, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                        else if (w.getBlockState(p).isAir() && w.getBlockState(d).isSolidBlock(w, d)) w.setBlockState(p, Blocks.SNOW.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
            case 7 -> {
                for (ServerPlayerEntity pl : w.getPlayers()) {
                    Fx.particles(w, ParticleTypes.ASH, pl.getX(), pl.getY() - 10, pl.getZ(), 200, 40, 10, 40, 0.0);
                    Fx.particles(w, ParticleTypes.WHITE_ASH, pl.getX(), pl.getY() - 10, pl.getZ(), 120, 40, 10, 40, 0.0);
                    if (r.nextInt(6) == 0) {
                        BlockPos p = Fx.surface(w, pl.getBlockX() + r.nextInt(81) - 40, pl.getBlockZ() + r.nextInt(81) - 40);
                        if (!w.isChunkLoaded(p)) continue;
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                        if (bolt != null) {
                            bolt.refreshPositionAfterTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                            w.spawnEntity(bolt);
                        }
                    }
                }
            }
            default -> {
                if (cyc % 15 == 0) for (Kingdoms.K k : alive()) {
                    Civ.City c = Civ.city(k);
                    c.food = Math.max(0, c.food - 3);
                    for (MobEntity m : k.members) {
                        if (m instanceof VillagerEntity && m.isAlive() && r.nextInt(5) == 0) {
                            m.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 0));
                            m.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 0));
                        }
                    }
                    if (r.nextInt(8) == 0) Chronicle.say(s, k, "страдает от мора");
                }
            }
        }
    }

    static List<Kingdoms.K> alive() {
        List<Kingdoms.K> l = new ArrayList<>();
        for (Kingdoms.K k : Kingdoms.K.values()) if (k.alive && k.world != null && k.capital != null) l.add(k);
        return l;
    }

    public static void clear() {
        age = 0;
        timer = 0;
        cyc = 0;
    }
}
