package com.vladik.godbox;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Модельки боссов, собранные из блоков-дисплеев (block_display).
 * Сам моб становится невидимым, а вокруг него каждый тик двигается модель.
 * Модель смотрит вперёд по +Z и поворачивается вместе с телом босса.
 * group: 0 - тело, 1/2 - ноги (шагают по очереди), 3/4 - крылья (машут).
 */
public final class BossModels {
    private record Part(String block, float x, float y, float z, float sx, float sy, float sz, int group, boolean glow) {}

    private static final class Model {
        final List<Entity> ents = new ArrayList<>();
        final List<Part> parts = new ArrayList<>();
        final float amp;
        float phase;
        int t;

        Model(float amp) {
            this.amp = amp;
        }
    }

    private static final Map<MobEntity, Model> MODELS = new HashMap<>();
    private static int clock;

    private BossModels() {}

    private static Part P(String b, double x, double y, double z, double sx, double sy, double sz) {
        return new Part(b, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, 0, false);
    }

    private static Part G(int g, String b, double x, double y, double z, double sx, double sy, double sz) {
        return new Part(b, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, g, false);
    }

    private static Part L(String b, double x, double y, double z, double sx, double sy, double sz) {
        return new Part(b, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz, 0, true);
    }

    // ---------------- Крабзилла ----------------
    private static List<Part> crab() {
        List<Part> l = new ArrayList<>();
        l.add(P("red_concrete", -3, 2, -2.5, 6, 2.5, 5));
        l.add(P("orange_concrete", -2.6, 4.5, -2.1, 5.2, 0.7, 4.2));
        l.add(P("red_terracotta", -1.5, 5.2, -1.2, 3, 0.25, 2.4));
        l.add(P("white_terracotta", -2.8, 1.7, -2.3, 5.6, 0.3, 4.6));
        l.add(P("black_concrete", -0.8, 2.6, 2.5, 1.6, 0.5, 0.1));
        // глаза на стебельках
        l.add(P("red_concrete", -1.6, 4.5, 1.8, 0.45, 1.6, 0.45));
        l.add(P("red_concrete", 1.15, 4.5, 1.8, 0.45, 1.6, 0.45));
        l.add(P("white_concrete", -1.75, 6.0, 1.65, 0.75, 0.75, 0.75));
        l.add(P("white_concrete", 1.0, 6.0, 1.65, 0.75, 0.75, 0.75));
        l.add(P("black_concrete", -1.6, 6.15, 2.4, 0.45, 0.45, 0.05));
        l.add(P("black_concrete", 1.15, 6.15, 2.4, 0.45, 0.45, 0.05));
        // руки и клешни
        l.add(P("red_terracotta", -4.2, 3, 1.2, 1.2, 1, 3.2));
        l.add(P("red_terracotta", 3.0, 3, 1.2, 1.2, 1, 3.2));
        l.add(P("red_concrete", -5.4, 2.4, 4, 2.4, 2.2, 2.6));
        l.add(P("red_concrete", 3.0, 2.4, 4, 2.4, 2.2, 2.6));
        l.add(P("orange_terracotta", -5.4, 3.3, 6.6, 1, 1.3, 1.4));
        l.add(P("orange_terracotta", -4.0, 2.4, 6.6, 1, 0.8, 1.4));
        l.add(P("orange_terracotta", 4.4, 3.3, 6.6, 1, 1.3, 1.4));
        l.add(P("orange_terracotta", 3.0, 2.4, 6.6, 1, 0.8, 1.4));
        // по 3 ноги с каждой стороны
        double[] zs = {-2, -0.4, 1.2};
        for (int k = 0; k < 3; k++) {
            int g = k % 2 == 0 ? 1 : 2;
            int g2 = g == 1 ? 2 : 1;
            double z = zs[k];
            l.add(G(g, "red_terracotta", -4.6, 2.2, z, 1.6, 0.55, 0.55));
            l.add(G(g, "red_terracotta", -4.9, 0, z, 0.55, 2.4, 0.55));
            l.add(G(g2, "red_terracotta", 3.0, 2.2, z, 1.6, 0.55, 0.55));
            l.add(G(g2, "red_terracotta", 4.35, 0, z, 0.55, 2.4, 0.55));
        }
        return l;
    }

    // ---------------- Мехагигант ----------------
    private static List<Part> robot() {
        List<Part> l = new ArrayList<>();
        l.add(G(1, "iron_block", -2.0, 0.6, -0.8, 1.6, 4, 1.6));
        l.add(G(1, "gray_concrete", -2.1, 0, -1.0, 1.8, 0.6, 2.4));
        l.add(G(2, "iron_block", 0.4, 0.6, -0.8, 1.6, 4, 1.6));
        l.add(G(2, "gray_concrete", 0.3, 0, -1.0, 1.8, 0.6, 2.4));
        l.add(P("gray_concrete", -2.3, 4.5, -1, 4.6, 1, 2));
        l.add(P("iron_block", -2.8, 5.5, -1.5, 5.6, 3.5, 3));
        l.add(P("light_gray_concrete", -2.2, 6, 1.5, 4.4, 2.5, 0.15));
        l.add(L("sea_lantern", -0.6, 6.6, 1.6, 1.2, 1.2, 0.2));
        l.add(P("gray_concrete", -1.5, 6, -2.1, 3, 2.5, 0.6));
        l.add(P("light_gray_concrete", -4.1, 7.7, -1.1, 1.4, 1.3, 2.2));
        l.add(P("light_gray_concrete", 2.7, 7.7, -1.1, 1.4, 1.3, 2.2));
        // руки-пушки качаются в противофазе с ногами
        l.add(G(2, "iron_block", -4.0, 4.2, -0.7, 1.2, 3.5, 1.4));
        l.add(G(1, "iron_block", 2.8, 4.2, -0.7, 1.2, 3.5, 1.4));
        l.add(G(2, "gray_concrete", -4.15, 3.0, -0.8, 1.5, 1.2, 2.2));
        l.add(G(1, "gray_concrete", 2.65, 3.0, -0.8, 1.5, 1.2, 2.2));
        // голова
        l.add(P("gray_concrete", -0.5, 9, -0.5, 1, 0.4, 1));
        l.add(P("iron_block", -1.2, 9.4, -1.2, 2.4, 1.8, 2.4));
        l.add(L("sea_lantern", -0.95, 10.1, 1.2, 1.9, 0.45, 0.1));
        l.add(P("lightning_rod", -0.15, 11.2, -0.15, 0.3, 1.0, 0.3));
        l.add(L("redstone_block", -0.2, 12.1, -0.2, 0.4, 0.4, 0.4));
        return l;
    }

    // ---------------- Дракон ----------------
    private static List<Part> dragon() {
        List<Part> l = new ArrayList<>();
        l.add(P("red_nether_bricks", -1.5, 0, -4, 3, 2.5, 7));
        l.add(P("yellow_terracotta", -1.2, -0.15, -3.5, 2.4, 0.3, 6));
        for (double z : new double[]{-3, -1, 1}) l.add(P("bone_block", -0.2, 2.5, z, 0.4, 0.6, 0.6));
        l.add(P("red_nether_bricks", -0.8, 1.2, 3, 1.6, 1.6, 3));
        l.add(P("red_nether_bricks", -1.2, 1.6, 5.8, 2.4, 1.8, 3));
        l.add(P("nether_bricks", -0.9, 1.6, 8.8, 1.8, 1.0, 1.6));
        l.add(P("nether_bricks", -0.8, 1.2, 6.5, 1.6, 0.4, 3.5));
        l.add(L("shroomlight", -1.3, 2.8, 7.6, 0.3, 0.4, 0.6));
        l.add(L("shroomlight", 1.0, 2.8, 7.6, 0.3, 0.4, 0.6));
        l.add(P("bone_block", -1.0, 3.4, 6.2, 0.3, 1.1, 0.3));
        l.add(P("bone_block", 0.7, 3.4, 6.2, 0.3, 1.1, 0.3));
        // хвост
        l.add(P("red_nether_bricks", -0.7, 0.6, -8, 1.4, 1.2, 4));
        l.add(P("red_nether_bricks", -0.4, 0.8, -11, 0.8, 0.8, 3));
        l.add(P("bone_block", -0.5, 0.75, -12, 1, 0.9, 1));
        // крылья: внутренняя часть (3) и кончики (4)
        l.add(G(3, "orange_terracotta", -5.5, 1.8, -2.5, 4, 0.25, 5));
        l.add(G(3, "red_nether_bricks", -5.5, 2.05, 1.9, 4, 0.4, 0.5));
        l.add(G(4, "orange_terracotta", -9.5, 1.8, -2, 4, 0.25, 4));
        l.add(G(4, "red_nether_bricks", -9.5, 2.05, 1.9, 4, 0.4, 0.5));
        l.add(G(3, "orange_terracotta", 1.5, 1.8, -2.5, 4, 0.25, 5));
        l.add(G(3, "red_nether_bricks", 1.5, 2.05, 1.9, 4, 0.4, 0.5));
        l.add(G(4, "orange_terracotta", 5.5, 1.8, -2, 4, 0.25, 4));
        l.add(G(4, "red_nether_bricks", 5.5, 2.05, 1.9, 4, 0.4, 0.5));
        // лапы
        l.add(P("nether_bricks", -1.4, -1, -2.5, 0.7, 1, 0.7));
        l.add(P("nether_bricks", 0.7, -1, -2.5, 0.7, 1, 0.7));
        l.add(P("nether_bricks", -1.4, -0.8, 1.5, 0.6, 0.8, 0.6));
        l.add(P("nether_bricks", 0.8, -0.8, 1.5, 0.6, 0.8, 0.6));
        return l;
    }

    private static String kindOf(MobEntity m) {
        for (String k : new String[]{"crab", "robot", "dragon"}) if (m.getCommandTags().contains("godbox_kind_" + k)) return k;
        if (m.getType() == EntityType.RAVAGER) return "crab";
        if (m.getType() == EntityType.IRON_GOLEM) return "robot";
        if (m.getType() == EntityType.PHANTOM) return "dragon";
        return null;
    }

    private static Entity make(ServerWorld w, Part p) {
        String s = String.format(Locale.ROOT,
                "{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:%s\"},teleport_duration:2,width:24.0f,height:16.0f,view_range:4.0f,"
                        + "transformation:{left_rotation:[0.0f,0.0f,0.0f,1.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f],"
                        + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}%s}",
                p.block(), p.x(), p.y(), p.z(), p.sx(), p.sy(), p.sz(),
                p.glow() ? ",brightness:{sky:15,block:15}" : "");
        try {
            NbtCompound nbt = StringNbtReader.parse(s);
            return EntityType.getEntityFromNbt(nbt, w).orElse(null);
        } catch (Exception ex) {
            GodBox.LOG.warn("GodBox: boss part failed: " + s, ex);
            return null;
        }
    }

    public static void attach(MobEntity m, String kind, ServerWorld w) {
        if (MODELS.containsKey(m) || Models.has(m)) return;
        if (Models.custom() && ModelParts.has(kind)) {
            // новая модель из своих текстур; старая из блоков остаётся запасной (models.custom=false)
            m.addCommandTag("godbox_kind_" + kind);
            if (Models.attach(m, kind)) return;
        }
        m.removeCommandTag(Models.MODEL_TAG + kind);
        List<Part> parts = switch (kind) {
            case "crab" -> crab();
            case "robot" -> robot();
            default -> dragon();
        };
        m.addCommandTag("godbox_kind_" + kind);
        m.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));
        Model md = new Model(kind.equals("robot") ? 0.8f : 0.5f);
        for (Part p : parts) {
            Entity e = make(w, p);
            if (e == null) continue;
            e.refreshPositionAndAngles(m.getX(), m.getY(), m.getZ(), m.getBodyYaw(), 0f);
            e.addCommandTag("godbox_part");
            if (!w.spawnEntity(e)) continue;
            md.ents.add(e);
            md.parts.add(p);
        }
        MODELS.put(m, md);
    }

    /** Поза атаки для новых моделей (у старых её нет). */
    public static void strike(MobEntity m) {
        Models.trigger(m);
    }

    public static void tick(MinecraftServer server) {
        Models.tick(server);
        Iterator<Map.Entry<MobEntity, Model>> it = MODELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MobEntity, Model> en = it.next();
            MobEntity m = en.getKey();
            Model md = en.getValue();
            if (m.isRemoved() || !m.isAlive()) {
                if (m.getWorld() instanceof ServerWorld w) {
                    Fx.particles(w, ParticleTypes.EXPLOSION, m.getX(), m.getY() + 2, m.getZ(), 12, 2, 2, 2, 0);
                }
                for (Entity e : md.ents) e.discard();
                it.remove();
                continue;
            }
            md.t++;
            double vx = m.getX() - m.prevX, vz = m.getZ() - m.prevZ;
            boolean moving = vx * vx + vz * vz > 0.0004;
            if (moving) md.phase += 0.35f;
            float yaw = m.getBodyYaw();
            double flap = Math.sin(md.t * 0.25);
            for (int i = 0; i < md.ents.size(); i++) {
                Entity e = md.ents.get(i);
                if (e.isRemoved()) continue;
                Part p = md.parts.get(i);
                double dy = switch (p.group()) {
                    case 1 -> moving ? Math.max(0, Math.sin(md.phase)) * md.amp : 0;
                    case 2 -> moving ? Math.max(0, -Math.sin(md.phase)) * md.amp : 0;
                    case 3 -> flap * 0.45;
                    case 4 -> flap * 1.4;
                    default -> 0;
                };
                e.refreshPositionAndAngles(m.getX(), m.getY() + dy, m.getZ(), yaw, 0f);
            }
        }

        // раз в 5 секунд: убрать бесхозные детали и вернуть модели боссам после перезахода
        if (++clock % 100 != 0) return;
        Set<Entity> live = new HashSet<>();
        for (Model md : MODELS.values()) live.addAll(md.ents);
        for (ServerWorld w : server.getWorlds()) {
            List<Entity> orphans = new ArrayList<>();
            List<MobEntity> bare = new ArrayList<>();
            List<LivingEntity> tagged = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (e.getCommandTags().contains("godbox_part")) {
                    if (!live.contains(e) && !Models.owns(e)) orphans.add(e);
                } else if (e instanceof MobEntity mob && mob.getCommandTags().contains("godbox_boss") && mob.isAlive() && !MODELS.containsKey(mob) && !Models.has(mob)) {
                    bare.add(mob);
                } else if (e instanceof LivingEntity le && le.isAlive() && !Models.has(le) && Models.tagged(le)) {
                    tagged.add(le);
                }
            }
            for (Entity e : orphans) e.discard();
            for (LivingEntity le : tagged) Models.reattach(le);
            for (MobEntity mob : bare) {
                String k = kindOf(mob);
                if (k != null) attach(mob, k, w);
            }
        }
    }

    public static void clear() {
        for (Model md : MODELS.values()) for (Entity e : md.ents) e.discard();
        MODELS.clear();
        Models.clear();
        clock = 0;
    }
}
