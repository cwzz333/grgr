package com.vladik.godbox;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** v2.7: династии. Королевская кровь передаётся детям, престол наследует старший из рода, спор за трон — мятеж. */
public final class Dynasty {
    static final String ROYAL = "gbroyal:";
    static final String REGNAL = "gbr:";
    static final String PARENT = "gbp:";

    static final class House {
        int id;
        String name = "";
        int reigns;
        final Map<String, Integer> regnal = new HashMap<>();
    }

    public static final Map<Kingdoms.K, House> HOUSES = new EnumMap<>(Kingdoms.K.class);

    private Dynasty() {}

    static House house(Kingdoms.K k) {
        return HOUSES.computeIfAbsent(k, x -> new House());
    }

    public static String houseName(Kingdoms.K k) {
        House h = HOUSES.get(k);
        return h == null || h.name.isEmpty() ? "—" : h.name;
    }

    static String first(MobEntity m) {
        String n = Life.name(m);
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }

    /** Ратибор → Ратиборовичи, Злата → Златичи. */
    static String familyName(String root) {
        if (root.isEmpty()) return "Безымянные";
        char last = root.charAt(root.length() - 1);
        if ("аяоеийь".indexOf(last) >= 0) return root.substring(0, root.length() - 1) + "ичи";
        return root + "овичи";
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n <= 10) return r[n];
        return Integer.toString(n);
    }

    static boolean royal(Kingdoms.K k, MobEntity m) {
        House h = HOUSES.get(k);
        String t = Life.tag(m, ROYAL);
        return h != null && h.id > 0 && t != null && t.equals(Integer.toString(h.id));
    }

    static List<MobEntity> blood(Kingdoms.K k) {
        List<MobEntity> out = new ArrayList<>();
        for (MobEntity m : k.members) if (m.isAlive() && m instanceof VillagerEntity && royal(k, m)) out.add(m);
        out.sort((a, b) -> Integer.compare(Life.age(b), Life.age(a)));
        return out;
    }

    /** Наследник: старший взрослый из рода, иначе ребёнок из рода, иначе null. */
    public static MobEntity heir(Kingdoms.K k) {
        List<MobEntity> b = blood(k);
        for (MobEntity m : b) if (!m.isBaby()) return m;
        return b.isEmpty() ? null : b.get(0);
    }

    /** Кандидат на трон: наследник рода, иначе самый старший взрослый житель. */
    public static MobEntity pick(Kingdoms.K k) {
        MobEntity h = heir(k);
        if (h != null) return h;
        MobEntity best = null;
        int ba = -1;
        for (MobEntity m : k.members) {
            if (!(m instanceof VillagerEntity v) || !v.isAlive() || v.isBaby()) continue;
            int a = Life.age(v);
            if (a > ba) {
                ba = a;
                best = v;
            }
        }
        return best;
    }

    /** Вызывается при коронации. Пишет летопись и даёт порядковый номер. */
    public static void crowned(MinecraftServer s, Kingdoms.K k, ServerWorld w, MobEntity m) {
        House h = house(k);
        boolean inherit = royal(k, m);
        if (!inherit) {
            String old = h.name;
            h.id++;
            h.name = familyName(first(m));
            h.reigns = 0;
            h.regnal.clear();
            Life.setTag(m, ROYAL, Integer.toString(h.id));
            if (!old.isEmpty()) Chronicle.say(s, k, "династия " + old + " пресеклась");
        }
        h.reigns++;
        String root = first(m);
        int n = h.regnal.merge(root, 1, Integer::sum);
        Life.setTag(m, REGNAL, roman(n));
        String who = root + " " + roman(n);
        if (inherit) {
            Chronicle.say(s, k, "престол унаследовал" + (m.isBaby() ? " малолетний " : " ") + who + " из рода " + h.name
                    + " (" + h.reigns + "-й правитель династии)");
        } else {
            Chronicle.say(s, k, "коронован " + who + " — основана династия " + h.name);
        }
    }

    /** У короля из старого сохранения ещё нет рода — заводим. */
    public static void ensure(MinecraftServer s, Kingdoms.K k, ServerWorld w, MobEntity king) {
        if (royal(k, king)) return;
        crowned(s, k, w, king);
    }

    /**
     * Король умер, наследник есть. Если в роду есть ещё взрослый претендент — может вспыхнуть спор за трон.
     * @return true, если начался мятеж претендента
     */
    public static boolean dispute(MinecraftServer s, Kingdoms.K k, ServerWorld w, MobEntity heir) {
        List<MobEntity> b = blood(k);
        MobEntity rival = null;
        for (MobEntity m : b) if (m != heir && !m.isBaby()) { rival = m; break; }
        if (rival == null) return false;
        boolean weak = heir.isBaby() || Life.age(rival) >= Life.age(heir) - 3;
        if (!weak || w.getRandom().nextInt(3) != 0) return false;
        List<BlockPos> towns = Conquest.TOWNS.get(k);
        if (towns == null || towns.isEmpty()) return false;
        BlockPos p = towns.get(w.getRandom().nextInt(towns.size()));
        if (!w.isChunkLoaded(p)) return false;
        Chronicle.say(s, k, "спор за трон! " + first(rival) + " не признаёт " + first(heir) + " и поднимает мятеж");
        Fx.particles(w, ParticleTypes.ANGRY_VILLAGER, rival.getX(), rival.getY() + 2, rival.getZ(), 12, 0.5, 0.5, 0.5, 0.05);
        rival.removeCommandTag(ROYAL + Life.tag(rival, ROYAL));
        Rebels.rebel(w, k, p);
        return true;
    }

    /** Рождение: ребёнку назначается родитель, королевская кровь и черты передаются. */
    public static MobEntity born(Kingdoms.K k, VillagerEntity child) {
        if (k == null) return null;
        List<MobEntity> adults = new ArrayList<>();
        for (MobEntity m : k.members) if (m != child && m.isAlive() && m instanceof VillagerEntity v && !v.isBaby()) adults.add(m);
        if (adults.isEmpty()) return null;
        MobEntity parent = null;
        MobEntity king = Kings.king(k);
        List<MobEntity> blood = blood(k);
        int roll = child.getRandom().nextInt(100);
        if (king != null && roll < 25) parent = king;
        else if (!blood.isEmpty() && roll < 40) {
            List<MobEntity> grown = new ArrayList<>();
            for (MobEntity m : blood) if (!m.isBaby()) grown.add(m);
            if (!grown.isEmpty()) parent = grown.get(child.getRandom().nextInt(grown.size()));
        }
        if (parent == null) {
            double best = Double.MAX_VALUE;
            for (MobEntity m : adults) {
                double d = m.squaredDistanceTo(child);
                if (d < best) {
                    best = d;
                    parent = m;
                }
            }
        }
        if (parent == null) return null;
        Life.setTag(child, PARENT, first(parent));
        String r = Life.tag(parent, ROYAL);
        if (r != null && royal(k, parent)) {
            Life.setTag(child, ROYAL, r);
            Treaties.born(k, child);
            ServerWorld w = k.world;
            if (w != null) {
                Civ.tell(w, Text.literal("👶 " + k.title + ": в роду " + houseName(k) + " родился наследник — "
                        + first(child) + ", дитя " + first(parent)).formatted(k.color));
                Fx.particles(w, ParticleTypes.HEART, child.getX(), child.getY() + 1, child.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
        return parent;
    }

    public static String describe(Kingdoms.K k) {
        House h = HOUSES.get(k);
        if (h == null || h.name.isEmpty()) return "династии нет";
        return "род " + h.name + " (правителей: " + h.reigns + ", кровных родичей: " + blood(k).size() + ")";
    }

    // ---------------- сохранение: $|K|id|name|reigns|имя:n,имя:n ----------------
    static List<String> save() {
        List<String> o = new ArrayList<>();
        for (Map.Entry<Kingdoms.K, House> e : HOUSES.entrySet()) {
            House h = e.getValue();
            if (h.id == 0) continue;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> r : h.regnal.entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(r.getKey()).append(':').append(r.getValue());
            }
            o.add("$|" + e.getKey().name() + "|" + h.id + "|" + h.name.replace("|", "") + "|" + h.reigns + "|" + (sb.length() == 0 ? "-" : sb));
        }
        return o;
    }

    static void load(String[] a) {
        try {
            House h = house(Kingdoms.K.valueOf(a[1]));
            h.id = Integer.parseInt(a[2]);
            h.name = a[3];
            h.reigns = Integer.parseInt(a[4]);
            h.regnal.clear();
            if (a.length > 5 && !a[5].equals("-")) for (String p : a[5].split(",")) {
                int i = p.lastIndexOf(':');
                if (i > 0) h.regnal.put(p.substring(0, i), Integer.parseInt(p.substring(i + 1)));
            }
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        HOUSES.clear();
    }
}
