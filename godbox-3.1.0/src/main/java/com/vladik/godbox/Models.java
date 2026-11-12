package com.vladik.godbox;

import com.vladik.godbox.mixin.DisplayEntityAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Свои модели существ из item_display: каждая деталь — предмет godbox:model с custom_model_data.
 * Описания деталей лежат в ModelParts (генерируется tools/models/gen.py), здесь — сборка и анимация.
 * Модель смотрит вперёд по +Z, +X — левая сторона существа; позы считаются на сервере,
 * клиент сам сглаживает движение (interpolation_duration / teleport_duration).
 * Математика позы повторяет tools/models/lib.py (pose) — менять только вместе.
 */
public final class Models {
    public static final String PART_TAG = "godbox_part";
    /** Метка на носителе: по ней модель возвращается после перезахода. */
    public static final String MODEL_TAG = "godbox_model_";
    public static final String EXTRA_TAG = "godbox_extra_";

    private static final int ANIM_PERIOD = 2;
    private static final int LERP_TICKS = 3;
    private static final double VIEW_DIST = 200;

    private static Item item;

    private static final class Inst {
        final LivingEntity carrier;
        final ModelParts.Creature def;
        final boolean main;
        final DisplayEntity.ItemDisplayEntity[] ents;
        final boolean[] animated;
        final Vector3f[] pos;
        final Quaternionf[] rot;
        final float[] scl;
        final float[] sent;
        int age;
        float walk;
        float moveW;
        int strikeAt = -100000;
        boolean near = true;
        boolean placed;
        double lx, ly, lz;
        float lyaw;

        Inst(LivingEntity carrier, ModelParts.Creature def, boolean main) {
            this.carrier = carrier;
            this.def = def;
            this.main = main;
            int n = def.parts().length;
            ents = new DisplayEntity.ItemDisplayEntity[n];
            animated = new boolean[n];
            pos = new Vector3f[n];
            rot = new Quaternionf[n];
            scl = new float[n];
            sent = new float[n * 8];
            for (int i = 0; i < n; i++) {
                ModelParts.Part p = def.parts()[i];
                animated[i] = p.anims().length > 0 || (p.parent() >= 0 && animated[p.parent()]);
                pos[i] = new Vector3f();
                rot[i] = new Quaternionf();
            }
        }
    }

    private static final Map<LivingEntity, List<Inst>> MODELS = new HashMap<>();
    private static final Set<Entity> PARTS = Collections.newSetFromMap(new IdentityHashMap<>());

    private Models() {}

    /** Регистрирует предмет-носитель моделей. Вызывается из GodBox.onInitialize(). */
    public static void init() {
        item = Registry.register(Registries.ITEM, Identifier.of(GodBox.MOD_ID, "model"), new Item(new Item.Settings()));
        custom();
    }

    /** Настройка models.custom: false — у боссов остаются старые модели из блоков. */
    public static boolean custom() {
        return Config.b("models.custom", true);
    }

    // ---------------------------------------------------------------- API

    /** Полная модель существа: заменяет прежнюю, прячет носителя, если так сказано в описании. */
    public static boolean attach(LivingEntity carrier, String creature) {
        return attach(carrier, creature, true);
    }

    /** Модель для существ без запасной модели из блоков: при models.custom=false моб остаётся ванильным. */
    public static boolean attachCustom(LivingEntity carrier, String creature) {
        return custom() && attach(carrier, creature, true);
    }

    // ---------------------------------------------------------------- головные уборы

    /**
     * Вид головы моба для HeadFeatureRenderer: у жителей и иллагеров голова 8×10×8, у зомби и скелетов 8×8×8,
     * а жителям рендер поднимает предмет ещё на 0.1875. null — у моба нет слоя с предметом на голове.
     */
    private static String headKind(LivingEntity e) {
        if (e instanceof VillagerEntity || e instanceof ZombieVillagerEntity) return "villager";
        if (e instanceof IllagerEntity || e instanceof WanderingTraderEntity) return "tall";
        if (e instanceof ZombieEntity || e instanceof AbstractSkeletonEntity || e instanceof AbstractPiglinEntity) return "biped";
        return null;
    }

    /** Предмет-убор из набора hats (tools/models/creatures/hats.py) под голову этого моба или null. */
    public static ItemStack hat(String name, LivingEntity wearer) {
        ModelParts.Creature def = ModelParts.get("hats");
        String kind = headKind(wearer);
        if (def == null || item == null || kind == null || !custom()) return null;
        String part = name + "_" + kind;
        for (ModelParts.Part p : def.parts()) {
            if (!p.name().equals(part)) continue;
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(p.cmd()));
            return stack;
        }
        return null;
    }

    /** Надевает убор в слот головы (не выпадает). false — убора нет или моб его не покажет. */
    public static boolean equipHat(MobEntity m, String name) {
        ItemStack stack = hat(name, m);
        if (stack == null) return false;
        m.equipStack(EquipmentSlot.HEAD, stack);
        m.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
        return true;
    }

    /** Предмет-деталь по имени набора и детали (для снарядов и оружия) или null. */
    public static ItemStack part(String creature, String name) {
        ModelParts.Creature def = ModelParts.get(creature);
        if (def == null || item == null || !custom()) return null;
        for (ModelParts.Part p : def.parts()) {
            if (!p.name().equals(name)) continue;
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(p.cmd()));
            return stack;
        }
        return null;
    }

    public static boolean isHat(ItemStack stack) {
        return item != null && stack.isOf(item);
    }

    // ----------------------------------------------------------------

    /** Дополнение к видимому мобу (аксессуар): носитель не прячется, можно несколько разных. */
    public static boolean attachExtra(LivingEntity carrier, String creature) {
        return attach(carrier, creature, false);
    }

    private static boolean attach(LivingEntity carrier, String creature, boolean main) {
        ModelParts.Creature def = ModelParts.get(creature);
        if (def == null || item == null || !carrier.isAlive() || !(carrier.getWorld() instanceof ServerWorld w)) return false;
        List<Inst> list = MODELS.computeIfAbsent(carrier, c -> new ArrayList<>());
        for (Iterator<Inst> it = list.iterator(); it.hasNext(); ) {
            Inst old = it.next();
            if (old.def == def && old.main == main) return true;
            if (main && old.main) {
                carrier.removeCommandTag(MODEL_TAG + old.def.name());
                discard(old);
                it.remove();
            }
        }
        Inst in = new Inst(carrier, def, main);
        pose(in, true);
        for (int i = 0; i < in.ents.length; i++) in.ents[i] = spawn(w, in, i);
        follow(in);
        list.add(in);
        carrier.addCommandTag((main ? MODEL_TAG : EXTRA_TAG) + creature);
        if (main && def.hideCarrier()) {
            carrier.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));
        }
        return true;
    }

    /** Снимает все модели с носителя и возвращает ему видимость. */
    public static void detach(LivingEntity carrier) {
        List<Inst> list = MODELS.remove(carrier);
        if (list == null) return;
        for (Inst in : list) {
            discard(in);
            carrier.removeCommandTag((in.main ? MODEL_TAG : EXTRA_TAG) + in.def.name());
            if (in.main && in.def.hideCarrier()) carrier.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
    }

    public static boolean has(LivingEntity carrier) {
        return MODELS.containsKey(carrier);
    }

    /** Деталь принадлежит живой модели (остальные godbox_part — бесхозные). */
    public static boolean owns(Entity part) {
        return PARTS.contains(part);
    }

    /** Поза атаки: детали с анимацией strike делают выпад. */
    public static void trigger(LivingEntity carrier) {
        List<Inst> list = MODELS.get(carrier);
        if (list == null) return;
        for (Inst in : list) in.strikeAt = in.age;
    }

    /** Возвращает модели по меткам носителя (после перезахода или выгрузки чанка). */
    public static void reattach(LivingEntity carrier) {
        if (MODELS.containsKey(carrier) || !carrier.isAlive()) return;
        if (!custom()) {
            // модели выключены в настройках: носитель снова обычный видимый моб
            for (String tag : new ArrayList<>(carrier.getCommandTags())) {
                if (!tag.startsWith(MODEL_TAG) && !tag.startsWith(EXTRA_TAG)) continue;
                carrier.removeCommandTag(tag);
                ModelParts.Creature def = tag.startsWith(MODEL_TAG) ? ModelParts.get(tag.substring(MODEL_TAG.length())) : null;
                if (def != null && def.hideCarrier()) carrier.removeStatusEffect(StatusEffects.INVISIBILITY);
            }
            return;
        }
        for (String tag : new ArrayList<>(carrier.getCommandTags())) {
            if (tag.startsWith(MODEL_TAG)) attach(carrier, tag.substring(MODEL_TAG.length()), true);
            else if (tag.startsWith(EXTRA_TAG)) attach(carrier, tag.substring(EXTRA_TAG.length()), false);
        }
    }

    public static boolean tagged(Entity e) {
        for (String tag : e.getCommandTags()) if (tag.startsWith(MODEL_TAG) || tag.startsWith(EXTRA_TAG)) return true;
        return false;
    }

    public static void clear() {
        for (List<Inst> list : MODELS.values()) for (Inst in : list) discard(in);
        MODELS.clear();
        PARTS.clear();
    }

    // ---------------------------------------------------------------- тик

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<LivingEntity, List<Inst>>> it = MODELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, List<Inst>> en = it.next();
            LivingEntity m = en.getKey();
            if (m.isRemoved() || !m.isAlive()) {
                for (Inst in : en.getValue()) {
                    // взрыв только при смерти, а не при выгрузке чанка
                    if (in.def.deathFx() && m.getHealth() <= 0 && m.getWorld() instanceof ServerWorld w) {
                        Fx.particles(w, ParticleTypes.EXPLOSION, m.getX(), m.getY() + 2, m.getZ(), 12, 2, 2, 2, 0);
                    }
                    discard(in);
                }
                it.remove();
                continue;
            }
            for (Inst in : en.getValue()) tick(in);
        }
    }

    private static void tick(Inst in) {
        LivingEntity m = in.carrier;
        in.age++;
        double vx = m.getX() - m.prevX, vz = m.getZ() - m.prevZ;
        boolean moving = vx * vx + vz * vz > 0.0004;
        in.moveW = Math.max(0f, Math.min(1f, in.moveW + (moving ? 0.2f : -0.15f)));
        if (in.moveW > 0) in.walk += 0.05f;
        // невидимый носитель с огнестойкостью (дракон-фантом днём) иначе показывал бы пламя поверх модели
        if (in.main && in.def.hideCarrier() && m.getFireTicks() > 0 && m.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) m.extinguish();
        follow(in);
        if (in.age % 20 == 1) in.near = playerNear(m);
        if (!in.near) return;
        boolean striking = in.age - in.strikeAt < 40;
        if (in.age % ANIM_PERIOD != 0 && !(striking && in.age == in.strikeAt + 1)) return;
        pose(in, false);
        for (int i = 0; i < in.ents.length; i++) {
            if (in.animated[i] && in.ents[i] != null && !in.ents[i].isRemoved()) send(in, i, false);
        }
    }

    private static boolean playerNear(LivingEntity m) {
        if (!(m.getWorld() instanceof ServerWorld w)) return false;
        for (ServerPlayerEntity p : w.getPlayers()) {
            double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
            if (dx * dx + dz * dz < VIEW_DIST * VIEW_DIST) return true;
        }
        return false;
    }

    /** Детали стоят в точке носителя и поворачиваются вместе с его телом; смещения — в transformation. */
    private static void follow(Inst in) {
        LivingEntity m = in.carrier;
        double x = m.getX(), y = m.getY(), z = m.getZ();
        float yaw = m.getBodyYaw();
        if (in.placed && x == in.lx && y == in.ly && z == in.lz && Math.abs(yaw - in.lyaw) < 0.05f) return;
        in.placed = true;
        in.lx = x;
        in.ly = y;
        in.lz = z;
        in.lyaw = yaw;
        for (Entity e : in.ents) {
            if (e != null && !e.isRemoved()) e.refreshPositionAndAngles(x, y, z, yaw, 0f);
        }
    }

    private static DisplayEntity.ItemDisplayEntity spawn(ServerWorld w, Inst in, int i) {
        ModelParts.Part p = in.def.parts()[i];
        // детали не сохраняются в мир: после перезахода модель собирается заново по меткам носителя
        DisplayEntity.ItemDisplayEntity e = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, w) {
            @Override
            public boolean shouldSave() {
                return false;
            }
        };
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(p.cmd()));
        e.setItemStack(stack);
        e.setTeleportDuration(2);
        e.setInterpolationDuration(LERP_TICKS);
        e.setViewRange(4f);
        e.setDisplayWidth(24f);
        e.setDisplayHeight(16f);
        if (p.bright()) e.setBrightness(new Brightness(15, 15));
        e.addCommandTag(PART_TAG);
        LivingEntity m = in.carrier;
        e.refreshPositionAndAngles(m.getX(), m.getY(), m.getZ(), m.getBodyYaw(), 0f);
        in.ents[i] = e;
        send(in, i, true);
        if (!w.spawnEntity(e)) {
            in.ents[i] = null;
            return null;
        }
        PARTS.add(e);
        return e;
    }

    private static void discard(Inst in) {
        for (Entity e : in.ents) {
            if (e == null) continue;
            PARTS.remove(e);
            e.discard();
        }
    }

    /** Отправляет позу детали, только если она заметно изменилась. */
    private static void send(Inst in, int i, boolean force) {
        Vector3f t = in.pos[i];
        Quaternionf q = in.rot[i];
        float s = in.scl[i];
        float[] o = in.sent;
        int b = i * 8;
        if (!force) {
            float dot = Math.abs(q.x * o[b + 3] + q.y * o[b + 4] + q.z * o[b + 5] + q.w * o[b + 6]);
            if (Math.abs(t.x - o[b]) < 0.002f && Math.abs(t.y - o[b + 1]) < 0.002f && Math.abs(t.z - o[b + 2]) < 0.002f
                    && dot > 0.999995f && Math.abs(s - o[b + 7]) < 0.002f) return;
        }
        o[b] = t.x;
        o[b + 1] = t.y;
        o[b + 2] = t.z;
        o[b + 3] = q.x;
        o[b + 4] = q.y;
        o[b + 5] = q.z;
        o[b + 6] = q.w;
        o[b + 7] = s;
        DisplayEntity.ItemDisplayEntity e = in.ents[i];
        // копии обязательны: трекер сравнивает новое значение со старым через equals
        e.setTransformation(new AffineTransformation(new Vector3f(t), new Quaternionf(q), new Vector3f(s, s, s), new Quaternionf()));
        // без принудительной отправки start_interpolation клиент не начнёт новую интерполяцию
        if (!force) e.getDataTracker().set(DisplayEntityAccessor.godbox$startInterpolation(), 0, true);
    }

    // ---------------------------------------------------------------- поза

    private static void pose(Inst in, boolean all) {
        ModelParts.Creature d = in.def;
        ModelParts.Part[] parts = d.parts();
        float k = d.scale() / 16f;
        float time = in.age / 20f;
        Vector3f off = new Vector3f();
        // масштаб без множителя существа храним отдельно, чтобы наследовать его детям
        for (int i = 0; i < parts.length; i++) {
            if (!all && !in.animated[i]) continue;
            ModelParts.Part p = parts[i];
            int par = p.parent();
            float ps = par >= 0 ? in.scl[par] / d.scale() : 1f;
            Quaternionf rot = in.rot[i];
            if (par >= 0) rot.set(in.rot[par]);
            else rot.identity();
            Quaternionf pr = par >= 0 ? in.rot[par] : null;
            rot.rotateY(rad(p.ry())).rotateX(rad(p.rx())).rotateZ(rad(p.rz()));
            off.set(p.px(), p.py(), p.pz());
            float scl = ps * p.scale();
            for (ModelParts.Anim a : p.anims()) {
                boolean walkClock = a.when() == ModelParts.MOVING;
                float w = a.when() == ModelParts.ALWAYS ? 1f : walkClock ? in.moveW : 1f - in.moveW;
                float t = walkClock ? in.walk : time;
                float v;
                switch (a.type()) {
                    case ModelParts.STRIKE -> {
                        int st = in.age - in.strikeAt;
                        v = st >= 0 ? a.amp() * strikeEnvelope(st / a.speed()) : 0f;
                        turn(rot, a.axis(), v);
                    }
                    case ModelParts.SPIN -> turn(rot, a.axis(), (360f * a.speed() * t) % 360f);
                    default -> {
                        v = w * a.amp() * wave(a.wave(), a.speed() * t + a.phase());
                        if (a.type() == ModelParts.SWING) turn(rot, a.axis(), v);
                        else if (a.type() == ModelParts.BOB) off.setComponent(a.axis(), off.get(a.axis()) + v);
                        else if (a.type() == ModelParts.PULSE) scl *= 1f + v;
                    }
                }
            }
            if (pr != null) pr.transform(off);
            off.mul(ps * k);
            if (par >= 0) off.add(in.pos[par]);
            in.pos[i].set(off);
            in.scl[i] = scl * d.scale();
        }
    }

    private static float rad(float deg) {
        return deg * 0.017453292f;
    }

    private static void turn(Quaternionf q, int axis, float deg) {
        if (deg == 0f) return;
        float r = rad(deg);
        if (axis == 0) q.rotateX(r);
        else if (axis == 1) q.rotateY(r);
        else q.rotateZ(r);
    }

    private static float wave(int wave, float cycles) {
        if (wave == ModelParts.SNAP) {
            float x = cycles - (float) Math.floor(cycles);
            if (x < 0.7f) {
                float t = x / 0.7f;
                return t * t * (3 - 2 * t);
            }
            return Math.max(0f, 1f - (x - 0.7f) / 0.1f);
        }
        float s = (float) Math.sin(2 * Math.PI * cycles);
        return wave == ModelParts.POS ? Math.max(0f, s) : wave == ModelParts.ABS ? Math.abs(s) : s;
    }

    private static float strikeEnvelope(float t) {
        if (t <= 0f || t >= 1f) return 0f;
        if (t < 0.25f) return t / 0.25f;
        float r = 1f - (t - 0.25f) / 0.75f;
        return r * r;
    }
}
