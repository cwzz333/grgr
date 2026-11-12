package com.vladik.godbox;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** Черты существ как в WorldBox: сильный, быстрый, великан, бессмертный и др. */
public final class Traits {
    public static final String[] IDS = {"strong", "fast", "tough", "regen", "giant", "tiny", "fireproof", "immortal", "cursed"};
    public static final String[] NAMES = {"Сильный", "Быстрый", "Крепкий", "Регенерация", "Великан", "Крошка", "Огнеупорный", "Бессмертный", "Проклятый"};

    private Traits() {}

    public static boolean has(Entity e, String t) { return e.getCommandTags().contains("gbt_" + t); }

    public static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return id;
    }

    private static void fx(LivingEntity e, StatusEffect... ignored) {}

    private static void eff(LivingEntity e, RegistryEntry<StatusEffect> s, int amp) {
        e.addStatusEffect(new StatusEffectInstance(s, -1, amp, false, false, true));
    }

    private static EntityAttributeInstance attr(LivingEntity e, RegistryEntry<EntityAttribute> a) {
        return e.getAttributeInstance(a);
    }

    @SuppressWarnings("unchecked")
    private static double base(LivingEntity e, RegistryEntry<EntityAttribute> a) {
        DefaultAttributeContainer d = DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) e.getType());
        return d != null && d.has(a) ? d.getBaseValue(a) : a.value().getDefaultValue();
    }

    private static void scale(LivingEntity e, double s) {
        EntityAttributeInstance i = attr(e, EntityAttributes.GENERIC_SCALE);
        if (i != null) i.setBaseValue(s);
    }

    private static void hp(LivingEntity e, double mult) {
        EntityAttributeInstance i = attr(e, EntityAttributes.GENERIC_MAX_HEALTH);
        if (i == null) return;
        i.setBaseValue(Math.max(i.getBaseValue(), base(e, EntityAttributes.GENERIC_MAX_HEALTH) * mult));
        e.setHealth(e.getMaxHealth());
    }

    public static void apply(LivingEntity e, String t) {
        if (has(e, t)) return;
        e.addCommandTag("gbt_" + t);
        switch (t) {
            case "strong" -> eff(e, StatusEffects.STRENGTH, 1);
            case "fast" -> eff(e, StatusEffects.SPEED, 1);
            case "tough" -> { eff(e, StatusEffects.RESISTANCE, 0); hp(e, 1.5); }
            case "regen" -> eff(e, StatusEffects.REGENERATION, 0);
            case "giant" -> { scale(e, 1.7); hp(e, 2.0); eff(e, StatusEffects.STRENGTH, 0); }
            case "tiny" -> { scale(e, 0.55); eff(e, StatusEffects.SPEED, 0); }
            case "fireproof" -> eff(e, StatusEffects.FIRE_RESISTANCE, 0);
            case "immortal" -> { eff(e, StatusEffects.REGENERATION, 2); eff(e, StatusEffects.RESISTANCE, 2); hp(e, 2.0); }
            case "cursed" -> { eff(e, StatusEffects.WEAKNESS, 0); eff(e, StatusEffects.SLOWNESS, 0); eff(e, StatusEffects.GLOWING, 0); }
            default -> {}
        }
        if (e.getWorld() instanceof ServerWorld w) {
            boolean bad = t.equals("cursed");
            Fx.particles(w, bad ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), 15, 0.4, 0.6, 0.4, 0.02);
        }
    }

    public static void clear(LivingEntity e) {
        List<String> tags = new ArrayList<>(e.getCommandTags());
        boolean any = false;
        for (String s : tags) if (s.startsWith("gbt_")) { e.removeCommandTag(s); any = true; }
        if (!any) return;
        e.clearStatusEffects();
        scale(e, 1.0);
        EntityAttributeInstance i = attr(e, EntityAttributes.GENERIC_MAX_HEALTH);
        if (i != null) {
            i.setBaseValue(base(e, EntityAttributes.GENERIC_MAX_HEALTH));
            if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
        }
        if (e.getWorld() instanceof ServerWorld w) Fx.particles(w, ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
    }

    /** Случайная черта при рождении жителя королевства (шанс 1/4). */
    public static void born(MobEntity m) {
        Random r = m.getRandom();
        if (r.nextInt(4) != 0) return;
        String[] pool = {"strong", "fast", "tough", "regen", "fireproof", "tiny", "giant"};
        String t = pool[r.nextInt(pool.length)];
        if (t.equals("giant") && r.nextInt(3) != 0) t = "tough";
        apply(m, t);
    }

    /** Сила-кисть: дать черту всем существам в радиусе (arg = clear — снять все черты). */
    public static void brush(ServerPlayerEntity god, ServerWorld w, BlockPos pos, int brush, String t) {
        double rad = 2 + brush * 1.5;
        Box box = new Box(pos).expand(rad, 6, rad);
        int n = 0;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof ServerPlayerEntity) && !GodManager.isGodEntity(x))) {
            if (t.equals("clear")) clear(e); else apply(e, t);
            n++;
        }
        Fx.sound(w, pos.getX(), pos.getY(), pos.getZ(), t.equals("cursed") ? SoundEvents.ENTITY_WITHER_AMBIENT : SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        if (god != null) god.sendMessage(Text.literal((t.equals("clear") ? "Черты сняты: " : "Черта «" + name(t) + "»: ") + n + " существ").formatted(Formatting.LIGHT_PURPLE), true);
    }
}
