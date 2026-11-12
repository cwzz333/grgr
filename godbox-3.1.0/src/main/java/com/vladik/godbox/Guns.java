package com.vladik.godbox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Огнестрельное оружие эпохи пороха. Мушкет — арбалет с custom_model_data (модель из tools/models/creatures/guns.py):
 * разбойник-стрелок сохраняет свой ИИ, а его болт заменяется мгновенным выстрелом с дымом.
 * Пушка — модель cannon на невидимой стойке: её выкатывают к месту осады, она стреляет ядром и откатывается.
 */
public final class Guns {
    public static final String CANNON_TAG = "gb_cannon";

    private static final class Cannon {
        final ArmorStandEntity stand;
        final Kingdoms.K k;
        int idle;

        Cannon(ArmorStandEntity stand, Kingdoms.K k) {
            this.stand = stand;
            this.k = k;
        }
    }

    private static final List<Cannon> CANNONS = new ArrayList<>();
    private static int t;

    private Guns() {}

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof PersistentProjectileEntity p) || !(p.getOwner() instanceof MobEntity m)) return;
            if (!isMusket(m.getMainHandStack())) return;
            // болт из мушкета не летит: вместо него выстрел, сам болт убираем на следующем тике
            Vec3d dir = p.getVelocity();
            p.setInvisible(true);
            p.setDamage(0);
            Tasks.delay(1, () -> {
                p.discard();
                if (m.isAlive() && m.getWorld() == world) fire(world, m, m.getTarget(), dir);
            });
        });
    }

    /** Номер детали из набора guns или 0, если свои модели выключены. */
    private static int cmd(String part) {
        ModelParts.Creature def = ModelParts.get("guns");
        if (def == null || !Models.custom()) return 0;
        for (ModelParts.Part p : def.parts()) if (p.name().equals(part)) return p.cmd();
        return 0;
    }

    private static ItemStack tagged(ItemStack stack, String part) {
        int id = cmd(part);
        if (id == 0) return null;
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(id));
        return stack;
    }

    public static boolean isMusket(ItemStack stack) {
        if (!stack.isOf(Items.CROSSBOW)) return false;
        CustomModelDataComponent c = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return c != null && c.value() != 0 && c.value() == cmd("musket");
    }

    // ---------------------------------------------------------------- мушкеты

    /** Выстрел из мушкета: мгновенная пуля по прямой, дым и вспышка у ствола. */
    static void fire(ServerWorld w, MobEntity m, LivingEntity aim, Vec3d fallback) {
        Vec3d eye = new Vec3d(m.getX(), m.getEyeY() - 0.15, m.getZ());
        Vec3d dir;
        if (aim != null && aim.isAlive() && aim.getWorld() == w) {
            double spread = Config.d("guns.musket.spread", 0.04);
            dir = new Vec3d(aim.getX(), aim.getBodyY(0.6), aim.getZ()).subtract(eye).normalize()
                    .add(w.getRandom().nextGaussian() * spread, w.getRandom().nextGaussian() * spread * 0.5, w.getRandom().nextGaussian() * spread);
        } else dir = fallback;
        if (dir.lengthSquared() < 1.0E-6) return;
        dir = dir.normalize();
        Vec3d from = eye.add(dir.multiply(1.1));
        Vec3d to = from.add(dir.multiply(Config.d("guns.musket.range", 40)));
        BlockHitResult bh = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, m));
        if (bh.getType() != HitResult.Type.MISS) to = bh.getPos();
        Kingdoms.K own = Kingdoms.of(m);
        EntityHitResult eh = ProjectileUtil.getEntityCollision(w, m, from, to, new Box(from, to).expand(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && e != m && (own == null || Kingdoms.of(e) != own));
        if (eh != null) to = eh.getPos();

        Fx.sound(w, from.x, from.y, from.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.4f, 1.9f);
        Fx.sound(w, from.x, from.y, from.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.6f, 0.6f);
        Fx.particles(w, ParticleTypes.FLAME, from.x, from.y, from.z, 5, 0.06, 0.06, 0.06, 0.03);
        Fx.particles(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, from.x, from.y, from.z, 3, 0.1, 0.1, 0.1, 0.01);
        Fx.particles(w, ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 4, 0.15, 0.1, 0.15, 0.02);
        double len = to.distanceTo(from);
        for (double d = 1.5; d < len; d += 2.5) {
            Fx.particles(w, ParticleTypes.CRIT, from.x + dir.x * d, from.y + dir.y * d, from.z + dir.z * d, 1, 0, 0, 0, 0);
        }
        if (eh != null && eh.getEntity() instanceof LivingEntity hit) {
            hit.damage(w.getDamageSources().mobProjectile(m, m), (float) Config.d("guns.musket.damage", 8.0));
            Fx.particles(w, ParticleTypes.CRIT, to.x, to.y, to.z, 6, 0.2, 0.3, 0.2, 0.1);
        } else if (bh.getType() != HitResult.Type.MISS) {
            Fx.particles(w, ParticleTypes.SMOKE, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Раз в 5 секунд: стрелки королевств, открывших порох, меняют арбалеты на мушкеты. */
    private static void arm() {
        if (cmd("musket") == 0) return;
        for (Kingdoms.K k : Kingdoms.K.values()) {
            if (!k.alive || !(k.world instanceof ServerWorld) || !Tech.has(k, Tech.T.GUNPOWDER)) continue;
            for (MobEntity m : k.members) {
                if (!(m instanceof PillagerEntity) || !m.isAlive()) continue;
                ItemStack held = m.getMainHandStack();
                if (!held.isOf(Items.CROSSBOW) || isMusket(held)) continue;
                ItemStack musket = tagged(new ItemStack(Items.CROSSBOW), "musket");
                if (musket == null) return;
                m.equipStack(EquipmentSlot.MAINHAND, musket);
                m.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
            }
        }
    }

    // ---------------------------------------------------------------- пушки

    /** Ядро для летящего снаряда (модель ball) или огненный шар, если свои модели выключены. */
    static ItemStack ball() {
        ItemStack s = Models.part("guns", "ball");
        return s != null ? s : new ItemStack(Items.FIRE_CHARGE);
    }

    /**
     * Выстрел осадной пушки рядом с солдатом m: ближайшая пушка королевства поворачивается к цели,
     * а если её нет — выкатывается новая. false — своих моделей нет, стреляют по-старому.
     */
    static boolean cannon(ServerWorld w, Kingdoms.K k, MobEntity m, BlockPos target) {
        if (!Models.custom() || !ModelParts.has("cannon")) return false;
        Cannon c = null;
        double best = 12 * 12;
        int own = 0;
        Cannon oldest = null;
        for (Cannon x : CANNONS) {
            if (x.k != k || x.stand.isRemoved() || x.stand.getWorld() != w) continue;
            own++;
            if (oldest == null || x.idle > oldest.idle) oldest = x;
            double d = x.stand.squaredDistanceTo(m);
            if (d < best) {
                best = d;
                c = x;
            }
        }
        double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
        if (c == null) {
            if (own >= Math.max(1, Config.i("guns.cannon.max", 3)) && oldest != null) remove(oldest);
            double dx = tx - m.getX(), dz = tz - m.getZ(), d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            BlockPos at = Fx.surface(w, MathHelper.floor(m.getX() + dx / d * 2), MathHelper.floor(m.getZ() + dz / d * 2));
            if (Math.abs(at.getY() - m.getBlockY()) > 3) at = m.getBlockPos();
            c = deploy(w, k, at);
            if (c == null) return false;
        }
        ArmorStandEntity s = c.stand;
        float yaw = (float) (MathHelper.atan2(tz - s.getZ(), tx - s.getX()) * 180.0 / Math.PI) - 90f;
        s.refreshPositionAndAngles(s.getX(), s.getY(), s.getZ(), yaw, 0f);
        c.idle = 0;
        double rad = yaw * Math.PI / 180.0;
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double ox = s.getX() + fx * 1.4, oy = s.getY() + 1.15, oz = s.getZ() + fz * 1.4;
        Tech.cannonball(w, m, ox, oy, oz, target, ball());
        Models.trigger(s);
        Fx.particles(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, ox, oy, oz, 6, 0.2, 0.2, 0.2, 0.02);
        return true;
    }

    private static Cannon deploy(ServerWorld w, Kingdoms.K k, BlockPos at) {
        // стойка-носитель не сохраняется в мир: пушки — расходная часть осады
        ArmorStandEntity s = new ArmorStandEntity(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5) {
            @Override
            public boolean shouldSave() {
                return false;
            }
        };
        s.setInvisible(true);
        s.setMarker(true);
        s.setNoGravity(true);
        s.setInvulnerable(true);
        s.setSilent(true);
        s.addCommandTag(CANNON_TAG);
        if (!w.spawnEntity(s)) return null;
        if (!Models.attach(s, "cannon")) {
            s.discard();
            return null;
        }
        Cannon c = new Cannon(s, k);
        CANNONS.add(c);
        Fx.sound(w, s.getX(), s.getY(), s.getZ(), SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
        return c;
    }

    private static void remove(Cannon c) {
        CANNONS.remove(c);
        Models.detach(c.stand);
        c.stand.discard();
    }

    // ---------------------------------------------------------------- тик

    public static void tick(MinecraftServer s) {
        if (++t % 20 != 0) return;
        Iterator<Cannon> it = CANNONS.iterator();
        while (it.hasNext()) {
            Cannon c = it.next();
            c.idle += 20;
            if (!c.stand.isRemoved() && c.k.alive && c.idle <= Config.i("guns.cannon.idle", 1200)) continue;
            Models.detach(c.stand);
            c.stand.discard();
            it.remove();
        }
        if (t % 100 == 0) arm();
    }

    public static void clear() {
        for (Cannon c : CANNONS) {
            Models.detach(c.stand);
            c.stand.discard();
        }
        CANNONS.clear();
        t = 0;
    }
}
