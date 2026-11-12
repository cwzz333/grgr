package com.vladik.godbox;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Герои и легенды: воины с победами получают имя, силу и место в летописи. */
public final class Heroes {
    public static boolean on = true;
    static final Map<UUID, Integer> KILLS = new HashMap<>();
    static final List<String> HALL = new ArrayList<>();
    static final String[] FIRST = {"Ратмир", "Святогор", "Добрыня", "Яромир", "Велимир", "Мстислав", "Ольгерд", "Борислав", "Радогост", "Всеслав",
            "Ингвар", "Тордис", "Хельга", "Леонид", "Аларик", "Беовульф", "Зигфрид", "Ратибор", "Елисей", "Остромир"};
    static final String[] EPI = {"Храбрый", "Железный", "Неустрашимый", "Грозный", "Мудрый", "Быстрый", "Каменный", "Огненный", "Справедливый", "Беспощадный", "Сокол", "Медведь"};
    private static int t;

    private Heroes() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register(Heroes::death);
    }

    static String tag(Entity m) {
        for (String s : m.getCommandTags()) if (s.startsWith("gbhero:")) return s;
        return null;
    }

    static int rank(Entity m) {
        String tg = tag(m);
        return tg == null ? 0 : tg.startsWith("gbhero:2:") ? 2 : 1;
    }

    static String heroName(Entity m) {
        String tg = tag(m);
        return tg == null ? m.getName().getString() : tg.split(":", 3)[2];
    }

    static void death(LivingEntity e, DamageSource src) {
        if (!on) return;
        MinecraftServer s = e.getServer();
        if (s == null) return;
        if (tag(e) != null) {
            Kingdoms.K dk = e instanceof MobEntity dm ? Kingdoms.of(dm) : null;
            String line = (rank(e) == 2 ? "легенда " : "герой ") + heroName(e) + " пал в бою (побед: " + KILLS.getOrDefault(e.getUuid(), 0) + ")";
            HALL.add((dk == null ? "" : dk.title + ": ") + line);
            if (dk != null) Chronicle.say(s, dk, "— " + line); else Chronicle.log(line);
            KILLS.remove(e.getUuid());
        }
        Entity a = src.getAttacker();
        if (!(a instanceof MobEntity m) || !m.isAlive() || m instanceof VillagerEntity) return;
        Kingdoms.K k = Kingdoms.of(m);
        if (k == null) return;
        Kingdoms.K vk = e instanceof MobEntity vm ? Kingdoms.of(vm) : null;
        boolean boss = e.getMaxHealth() >= 80;
        if (!(boss || (vk != null && vk != k) || e instanceof HostileEntity)) return;
        int n = KILLS.merge(m.getUuid(), boss ? 6 : 1, Integer::sum);
        int r = rank(m);
        if (r == 0 && n >= 5) promote(m, k, 1, boss ? "Гроза чудовищ" : null);
        else if (r == 1 && n >= 15) promote(m, k, 2, null);
        if (boss) Chronicle.say(s, k, "— " + heroName(m) + " сразил чудовище «" + e.getName().getString() + "»!");
    }

    static void promote(MobEntity m, Kingdoms.K k, int rank, String epi) {
        Random r = m.getRandom();
        String tg = tag(m), name;
        if (tg != null) {
            name = tg.split(":", 3)[2];
            m.removeCommandTag(tg);
        } else name = FIRST[r.nextInt(FIRST.length)] + " " + (epi != null ? epi : EPI[r.nextInt(EPI.length)]);
        m.addCommandTag("gbhero:" + rank + ":" + name);
        show(m, rank, name);
        Models.equipHat(m, rank == 2 ? "legend_helmet" : "hero_helmet");
        int inf = StatusEffectInstance.INFINITE;
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, inf, rank == 2 ? 4 : 2, false, false));
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, inf, rank - 1, false, false));
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, inf, rank - 1, false, false));
        if (rank == 2) m.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, inf, 0, false, false));
        m.setHealth(m.getMaxHealth());
        m.setPersistent();
        if (m.getWorld() instanceof ServerWorld w) {
            Fx.particles(w, ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + 1, m.getZ(), 50, 0.5, 1, 0.5, 0.4);
            Fx.sound(w, m.getX(), m.getY(), m.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        }
        String line = (rank == 2 ? "— живой легендой стал " : "— прославился воин ") + name + " (побед: " + KILLS.getOrDefault(m.getUuid(), 0) + ")";
        if (m.getServer() != null) Chronicle.say(m.getServer(), k, line);
        HALL.add(k.title + ": " + name + (rank == 2 ? " — легенда" : " — герой"));
        while (HALL.size() > 60) HALL.remove(0);
    }

    static void show(MobEntity m, int rank, String name) {
        m.setCustomName(Text.literal((rank == 2 ? "★★ " : "★ ") + name).formatted(rank == 2 ? Formatting.LIGHT_PURPLE : Formatting.GOLD, Formatting.BOLD));
        m.setCustomNameVisible(true);
    }

    public static void tick(MinecraftServer s) {
        if (++t % 100 != 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive) continue;
            for (MobEntity m : new ArrayList<>(k.members)) {
                if (!m.isAlive() || tag(m) == null) continue;
                int rank = rank(m);
                Text cn = m.getCustomName();
                if (cn == null || !cn.getString().startsWith("★")) show(m, rank, heroName(m));
                if (!(m.getWorld() instanceof ServerWorld w)) continue;
                Fx.particles(w, rank == 2 ? ParticleTypes.END_ROD : ParticleTypes.WAX_ON, m.getX(), m.getY() + 2.2, m.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
                if (rank == 2 && t % 300 == 0)
                    for (MobEntity o : w.getEntitiesByClass(MobEntity.class, new Box(m.getBlockPos()).expand(8), x -> x != m && Kingdoms.of(x) == k))
                        o.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 0, false, true));
            }
        }
        if (KILLS.size() > 3000) KILLS.clear();
    }

    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        MobEntity best = null;
        double bd = Double.MAX_VALUE;
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, new Box(pos).expand(24), x -> x.isAlive() && !(x instanceof VillagerEntity) && Kingdoms.of(x) != null)) {
            double d = m.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ());
            if (d < bd) { bd = d; best = m; }
        }
        if (best == null) { god.sendMessage(Text.literal("Рядом нет воинов королевств").formatted(Formatting.GRAY), true); return; }
        int r = rank(best);
        if (r >= 2) { god.sendMessage(Text.literal(heroName(best) + " уже легенда").formatted(Formatting.LIGHT_PURPLE), true); return; }
        KILLS.merge(best.getUuid(), r == 0 ? 5 : 10, Integer::sum);
        promote(best, Kingdoms.of(best), r + 1, null);
    }

    static int count(Kingdoms.K k) {
        int n = 0;
        for (MobEntity m : k.members) if (m.isAlive() && tag(m) != null) n++;
        return n;
    }

    public static void clear() {
        KILLS.clear();
        HALL.clear();
        t = 0;
    }
}
