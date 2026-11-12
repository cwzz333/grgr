package com.vladik.godbox;

import com.vladik.godbox.net.InspectPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** v3.1: осмотр существа — профиль жителя, родословная (реестр людей) и камера слежения. */
public final class Inspect {
    /** Другие системы могут дописать в профиль свои строки «метка: значение». */
    public static final List<BiConsumer<LivingEntity, List<String>>> EXTRA = new ArrayList<>();

    /** Запись о человеке: живёт в реестре и после смерти, чтобы древо помнило предков. */
    public static final class Person {
        public UUID id;
        public String name = "";
        public UUID parentA, parentB;
        public Kingdoms.K kingdom;
        public int bornYear;
        public int diedYear = -1;
        public String note = "";
        public boolean royal;
        // где видели в последний раз (не сохраняется)
        ServerWorld world;
        BlockPos pos;
        int miss;
    }

    static final Map<UUID, Person> PEOPLE = new LinkedHashMap<>();
    /** бог -> существо, за которым следит его камера */
    static final Map<UUID, UUID> FOLLOW = new HashMap<>();
    private static int t;

    private Inspect() {}

    public static void init() {
        SaveExt.register("inspect", Inspect::save, Inspect::load, PEOPLE::clear);
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (!PEOPLE.containsKey(e.getUuid())) return;
            Entity a = src.getAttacker();
            died(e, a == null ? "погиб" : "убит: " + a.getName().getString());
        });
        EXTRA.add(Inspect::fame);
        EXTRA.add(Inspect::beliefs);
    }

    public static void registerNetwork() {
        PayloadTypeRegistry.playS2C().register(InspectPayloads.Profile.ID, InspectPayloads.Profile.CODEC);
        PayloadTypeRegistry.playS2C().register(InspectPayloads.FollowState.ID, InspectPayloads.FollowState.CODEC);
        PayloadTypeRegistry.playC2S().register(InspectPayloads.Ask.ID, InspectPayloads.Ask.CODEC);
        PayloadTypeRegistry.playC2S().register(InspectPayloads.Follow.ID, InspectPayloads.Follow.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(InspectPayloads.Ask.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            player.getServer().execute(() -> {
                try {
                    ask(player, payload.id());
                } catch (Exception e) {
                    GodBox.LOG.error("GodBox inspect failed", e);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(InspectPayloads.Follow.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            player.getServer().execute(() -> follow(player, payload.id(), payload.on()));
        });
    }

    private static void msg(ServerPlayerEntity god, String s, Formatting f) {
        god.sendMessage(Text.literal(s).formatted(f), true);
    }

    // ---------------- годы ----------------
    static int year(Entity e) {
        return (int) (e.getWorld().getTime() / Life.YEAR);
    }

    static String yearText(int y) {
        return y >= 0 ? "год " + y : "за " + (-y) + " " + Life.years(-y) + " до летописи";
    }

    // ---------------- реестр людей ----------------
    private static boolean named(MobEntity m) {
        return Life.tag(m, "gbn:") != null;
    }

    static String displayName(LivingEntity e) {
        if (Heroes.rank(e) > 0) return Heroes.heroName(e);
        if (e instanceof MobEntity m && (named(m) || Kingdoms.of(m) != null)) {
            String rn = Life.tag(m, Dynasty.REGNAL);
            return Life.name(m) + (rn != null ? " " + rn : "");
        }
        return e.getName().getString();
    }

    /** Запись жителя королевства; заводится при первом обращении. Для диких существ — null. */
    static Person person(MobEntity m) {
        Person p = PEOPLE.get(m.getUuid());
        Kingdoms.K k = Kingdoms.of(m);
        if (p == null) {
            if (k == null) return null;
            p = new Person();
            p.id = m.getUuid();
            p.bornYear = year(m) - Life.age(m);
            String par = Life.tag(m, Dynasty.PARENT);
            if (par != null) p.note = "дитя " + par;
            PEOPLE.put(p.id, p);
        }
        refresh(p, m, k);
        return p;
    }

    private static void refresh(Person p, MobEntity m, Kingdoms.K k) {
        p.name = displayName(m).replace("|", "");
        if (k != null) p.kingdom = k;
        p.royal = k != null ? Dynasty.royal(k, m) : Life.tag(m, Dynasty.ROYAL) != null;
        if (p.diedYear >= 0) {
            p.diedYear = -1;
            p.note = "";
        }
        p.world = m.getWorld() instanceof ServerWorld w ? w : null;
        p.pos = m.getBlockPos();
        p.miss = 0;
    }

    /** Вызывается из Life.birth: ребёнок и его родитель попадают в реестр. */
    public static void born(MobEntity child, MobEntity parent) {
        try {
            Person p = person(child);
            if (p == null) return;
            p.bornYear = year(child);
            p.note = "";
            if (parent != null && parent != child && person(parent) != null) p.parentA = parent.getUuid();
        } catch (Exception e) {
            GodBox.LOG.error("GodBox inspect: рождение", e);
        }
    }

    /** Смерть: запись остаётся в реестре. */
    public static void died(LivingEntity e, String note) {
        Person p = PEOPLE.get(e.getUuid());
        if (p == null || p.diedYear >= 0) return;
        dead(p, year(e), note);
    }

    private static void dead(Person p, int year, String note) {
        p.diedYear = Math.max(year, p.bornYear);
        p.note = note == null ? "" : note.replace("|", "").replace("\n", " ");
        p.world = null;
        p.pos = null;
    }

    private static LivingEntity find(MinecraftServer s, UUID id) {
        for (ServerWorld w : s.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) return le;
        }
        return null;
    }

    /** Место, где человека видели в последний раз, загружено с запасом — уйти за край он не мог. */
    private static boolean inside(Person p) {
        if (p.world == null || p.pos == null) return false;
        for (int dx = -32; dx <= 32; dx += 32)
            for (int dz = -32; dz <= 32; dz += 32)
                if (!p.world.shouldTickEntity(p.pos.add(dx, 0, dz))) return false;
        return true;
    }

    // ---------------- тик ----------------
    public static void tick(MinecraftServer s) {
        ++t;
        if (!FOLLOW.isEmpty() && t % Math.max(1, Config.i("inspect.followTicks", 2)) == 0) camera(s);
        if (t % Math.max(40, Config.i("inspect.sweepTicks", 200)) != 0 || !Perf.go) return;
        int now = (int) (s.getOverworld().getTime() / Life.YEAR);
        for (Person p : PEOPLE.values()) {
            if (p.diedYear >= 0) continue;
            LivingEntity e = find(s, p.id);
            if (e instanceof MobEntity m) {
                refresh(p, m, Kingdoms.of(m));
            } else if (inside(p) && ++p.miss >= 3) {
                // место загружено, а существа нет — его убрали без события смерти
                dead(p, now, "сгинул");
            }
        }
        prune();
    }

    /** Сверх лимита убираем сначала давно умерших без живых потомков, потом прочих умерших, потом самых старых. */
    private static void prune() {
        int max = Math.max(100, Config.i("inspect.maxPeople", 4000));
        if (PEOPLE.size() <= max) return;
        Set<UUID> keep = new HashSet<>();
        ArrayDeque<UUID> todo = new ArrayDeque<>();
        for (Person p : PEOPLE.values()) if (p.diedYear < 0) todo.add(p.id);
        while (!todo.isEmpty()) {
            Person p = PEOPLE.get(todo.poll());
            if (p == null) continue;
            if (p.parentA != null && keep.add(p.parentA)) todo.add(p.parentA);
            if (p.parentB != null && keep.add(p.parentB)) todo.add(p.parentB);
        }
        for (int pass = 0; pass < 3 && PEOPLE.size() > max; pass++) {
            Iterator<Person> it = PEOPLE.values().iterator();
            while (it.hasNext() && PEOPLE.size() > max) {
                Person p = it.next();
                if (pass == 2 || (p.diedYear >= 0 && (pass == 1 || !keep.contains(p.id)))) it.remove();
            }
        }
    }

    // ---------------- камера слежения ----------------
    static void follow(ServerPlayerEntity god, UUID id, boolean on) {
        if (!on) {
            stopFollow(god, "📷 Слежка окончена");
            return;
        }
        if (!GodManager.isGod(god)) return;
        Entity e = god.getServerWorld().getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == god) {
            msg(god, "Существо не найдено — следить не за кем", Formatting.GRAY);
            return;
        }
        FOLLOW.put(god.getUuid(), id);
        String name = displayName(le);
        ServerPlayNetworking.send(god, new InspectPayloads.FollowState(true, name));
        msg(god, "📷 Камера следит: " + name, Formatting.AQUA);
    }

    static void stopFollow(ServerPlayerEntity god, String why) {
        if (FOLLOW.remove(god.getUuid()) == null) return;
        ServerPlayNetworking.send(god, new InspectPayloads.FollowState(false, ""));
        if (why != null) msg(god, why, Formatting.GRAY);
    }

    /** Вызывается из Powers3.early: любая другая сила прекращает слежку. */
    public static void onPower(ServerPlayerEntity god, Power power, boolean first) {
        if (!first || FOLLOW.isEmpty() || power == Power.INSPECT || power == Power.CIV_STATS) return;
        stopFollow(god, null);
    }

    private static void camera(MinecraftServer s) {
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(FOLLOW.entrySet())) {
            ServerPlayerEntity god = s.getPlayerManager().getPlayer(en.getKey());
            if (god == null) {
                FOLLOW.remove(en.getKey());
                continue;
            }
            if (!GodManager.isGod(god)) {
                stopFollow(god, null);
                continue;
            }
            Entity e = god.getServerWorld().getEntity(en.getValue());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                stopFollow(god, "📷 Слежка окончена: существо погибло или скрылось из виду");
                continue;
            }
            // камера смотрит вниз под углом: встаём позади цели так, чтобы она была в центре экрана
            double pitch = Math.toRadians(Math.max(35.0, Math.min(90.0, god.getPitch())));
            double h = Math.max(4.0, god.getY() - le.getY());
            double back = Math.min(60.0, h / Math.max(0.05, Math.tan(pitch)));
            if (pitch > Math.toRadians(88.0)) back = 0;
            double yaw = Math.toRadians(god.getYaw());
            double x = le.getX() + Math.sin(yaw) * back;
            double z = le.getZ() - Math.cos(yaw) * back;
            double dx = x - god.getX(), dz = z - god.getZ();
            if (dx * dx + dz * dz > 0.36) god.requestTeleport(x, god.getY(), z);
        }
    }

    // ---------------- сила «Осмотреть существо» ----------------
    public static void power(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos).add(0, 1, 0);
        double r = Math.max(1.0, Config.d("inspect.radius", 5.0));
        LivingEntity best = null, bestCitizen = null;
        double bd = Double.MAX_VALUE, bcd = Double.MAX_VALUE;
        for (LivingEntity e : Fx.living(w, c, r)) {
            if (e == god) continue;
            double d = e.squaredDistanceTo(c);
            if (d < bd) { bd = d; best = e; }
            if (d < bcd && Kingdoms.of(e) != null) { bcd = d; bestCitizen = e; }
        }
        if (bestCitizen != null) best = bestCitizen;
        if (best == null) {
            msg(god, "Здесь некого осматривать", Formatting.GRAY);
            return;
        }
        ServerPlayNetworking.send(god, live(god, best));
    }

    /** Запрос из древа: живое загруженное существо — полный профиль, иначе только запись реестра. */
    static void ask(ServerPlayerEntity god, UUID id) {
        if (!GodManager.isGod(god)) return;
        Entity e = god.getServerWorld().getEntity(id);
        if (e instanceof LivingEntity le && le.isAlive() && e != god) {
            ServerPlayNetworking.send(god, live(god, le));
            return;
        }
        Person p = PEOPLE.get(id);
        if (p != null) ServerPlayNetworking.send(god, record(god, p));
    }

    private static String ageText(int a, boolean baby) {
        return a + " " + Life.years(a) + (baby ? " · ребёнок" : a >= Life.OLD ? " · старик" : " · взрослый");
    }

    private static InspectPayloads.Profile live(ServerPlayerEntity god, LivingEntity e) {
        MobEntity m = e instanceof MobEntity mm ? mm : null;
        Kingdoms.K k = Kingdoms.of(e);
        Person p = m != null ? person(m) : null;
        boolean known = m != null && (k != null || named(m));
        List<String> lines = new ArrayList<>();
        if (known) lines.add("Возраст: " + ageText(Life.age(m), m.isBaby()));
        boolean king = m != null && (m.getCommandTags().contains(Kings.TAG) || (k != null && Life.isKing(k, m)));
        if (m != null && k != null) {
            String job;
            if (m.isBaby()) job = "ребёнок";
            else if (king) job = "правитель";
            else if (m instanceof VillagerEntity v) job = Civ.jobTitle(v).isEmpty() ? "без дела" : Civ.jobTitle(v);
            else job = "воин";
            lines.add("Занятие: " + job);
        }
        lines.add("Здоровье: " + (int) Math.ceil(e.getHealth()) + " / " + (int) Math.ceil(e.getMaxHealth()));
        if (known && m instanceof VillagerEntity) lines.add("Характер: " + Life.TRAITS[Math.floorMod(Life.trait(m), Life.TRAITS.length)]);
        StringBuilder tr = new StringBuilder();
        for (int i = 0; i < Traits.IDS.length; i++) {
            if (!Traits.has(e, Traits.IDS[i])) continue;
            if (tr.length() > 0) tr.append(", ");
            tr.append(Traits.NAMES[i]);
        }
        lines.add("Черты: " + (tr.length() == 0 ? "нет" : tr));
        if (m != null && k != null) {
            boolean royal = Dynasty.royal(k, m);
            if (king) {
                String rn = Life.tag(m, Dynasty.REGNAL);
                lines.add("Титул: правитель" + (rn != null ? ", " + Dynasty.first(m) + " " + rn : ""));
            }
            if (king || royal) lines.add("Династия: " + Dynasty.houseName(k));
            if (royal && !king) lines.add("Королевская кровь: да");
        }
        if (p != null) {
            lines.add("Родился: " + yearText(p.bornYear));
            if (p.parentA == null && p.parentB == null && m != null) {
                String par = Life.tag(m, Dynasty.PARENT);
                if (par != null) lines.add("Родитель: " + par);
            }
        }
        for (BiConsumer<LivingEntity, List<String>> x : new ArrayList<>(EXTRA)) {
            try {
                x.accept(e, lines);
            } catch (Exception ex) {
                GodBox.LOG.error("GodBox inspect: доп. строки", ex);
            }
        }
        UUID f = FOLLOW.get(god.getUuid());
        return new InspectPayloads.Profile(e.getId(), e.getUuid(), displayName(e), k == null ? "" : k.title, k == null ? 0xFFFFFF : k.rgb,
                true, e.getUuid().equals(f), year(e), lines, p == null ? List.of() : tree(p));
    }

    private static InspectPayloads.Profile record(ServerPlayerEntity god, Person p) {
        List<String> lines = new ArrayList<>();
        boolean alive = p.diedYear < 0;
        int now = year(god);
        lines.add("Состояние: " + (alive ? "жив, но сейчас не на виду" : "умер"));
        lines.add("Родился: " + yearText(p.bornYear));
        if (alive) {
            int a = Math.max(0, now - p.bornYear);
            lines.add("Возраст: " + a + " " + Life.years(a));
        } else {
            int a = Math.max(0, p.diedYear - p.bornYear);
            lines.add("Умер: " + yearText(p.diedYear) + " (в " + a + " " + Life.years(a) + ")");
        }
        if (!p.note.isEmpty()) lines.add((alive ? "Заметка: " : "Судьба: ") + p.note);
        if (p.royal) lines.add("Королевская кровь: да");
        Kingdoms.K k = p.kingdom;
        return new InspectPayloads.Profile(-1, p.id, p.name, k == null ? "" : k.title, k == null ? 0xFFFFFF : k.rgb,
                alive, false, now, lines, tree(p));
    }

    // ---------------- доп. строки по умолчанию ----------------
    private static void fame(LivingEntity e, List<String> lines) {
        int rank = Heroes.rank(e);
        Integer kills = Heroes.KILLS.get(e.getUuid());
        if (rank > 0) lines.add("Слава: " + (rank == 2 ? "живая легенда" : "герой"));
        if (kills != null && kills > 0) lines.add("Побед: " + kills);
    }

    private static void beliefs(LivingEntity e, List<String> lines) {
        Kingdoms.K k = Kingdoms.of(e);
        if (k == null || !k.alive) return;
        Civ.City c = Civ.city(k);
        if (c == null) return;
        lines.add("Вера: " + Faith.religion(c));
        if (c.culture >= 0) lines.add("Культура: " + Faith.culture(c));
    }

    // ---------------- древо ----------------
    private static InspectPayloads.Node node(Person p, int rel, UUID link) {
        return new InspectPayloads.Node(p.id, p.name, rel, link, p.diedYear < 0, p.bornYear, p.diedYear,
                p.kingdom == null ? 0xFFFFFF : p.kingdom.rgb, p.royal);
    }

    private static boolean childOf(Person c, UUID parent) {
        return parent != null && (parent.equals(c.parentA) || parent.equals(c.parentB));
    }

    /** Живые вперёд, затем младшие; не больше max. */
    private static List<Person> trim(List<Person> in, int max) {
        in.sort((a, b) -> {
            int c = Boolean.compare(a.diedYear >= 0, b.diedYear >= 0);
            return c != 0 ? c : Integer.compare(b.bornYear, a.bornYear);
        });
        return in.size() > max ? in.subList(0, max) : in;
    }

    private static List<InspectPayloads.Node> tree(Person self) {
        List<InspectPayloads.Node> out = new ArrayList<>();
        List<Person> parents = new ArrayList<>();
        for (UUID u : new UUID[]{self.parentA, self.parentB}) {
            Person p = u == null ? null : PEOPLE.get(u);
            if (p != null && p != self && !parents.contains(p)) parents.add(p);
        }
        for (Person p : parents) {
            for (UUID u : new UUID[]{p.parentA, p.parentB}) {
                Person g = u == null ? null : PEOPLE.get(u);
                if (g != null && g != self && g != p) out.add(node(g, InspectPayloads.GRAND, p.id));
            }
        }
        for (Person p : parents) out.add(node(p, InspectPayloads.PARENT, self.id));
        out.add(node(self, InspectPayloads.SELF, self.id));
        List<Person> sib = new ArrayList<>(), kids = new ArrayList<>();
        for (Person o : PEOPLE.values()) {
            if (o == self) continue;
            if (childOf(o, self.id)) kids.add(o);
            else if (childOf(o, self.parentA) || childOf(o, self.parentB)) sib.add(o);
        }
        for (Person o : trim(sib, Math.max(0, Config.i("inspect.maxSiblings", 10)))) out.add(node(o, InspectPayloads.SIBLING, self.id));
        for (Person o : trim(kids, Math.max(0, Config.i("inspect.maxChildren", 16)))) out.add(node(o, InspectPayloads.CHILD, self.id));
        return out;
    }

    // ---------------- сохранение: id|имя|родительA|родительB|K|родился|умер|кровь|заметка ----------------
    private static String u(UUID id) {
        return id == null ? "-" : id.toString();
    }

    private static UUID u(String s) {
        return s.equals("-") ? null : UUID.fromString(s);
    }

    /** Имена и заметки приходят из тегов существ — в файле не должно быть разделителей. */
    private static String field(String s) {
        return s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    static List<String> save() {
        List<String> o = new ArrayList<>();
        for (Person p : PEOPLE.values()) {
            o.add(p.id + "|" + (p.name.isEmpty() ? "?" : field(p.name)) + "|" + u(p.parentA) + "|" + u(p.parentB) + "|"
                    + (p.kingdom == null ? "-" : p.kingdom.name()) + "|" + p.bornYear + "|" + p.diedYear + "|" + (p.royal ? 1 : 0)
                    + "|" + (p.note.isEmpty() ? "-" : field(p.note)));
        }
        return o;
    }

    static void load(String[] a) {
        try {
            Person p = new Person();
            p.id = UUID.fromString(a[0]);
            p.name = a[1];
            p.parentA = u(a[2]);
            p.parentB = u(a[3]);
            try {
                if (!a[4].equals("-")) p.kingdom = Kingdoms.K.valueOf(a[4]);
            } catch (IllegalArgumentException ignored) {
            }
            p.bornYear = Integer.parseInt(a[5]);
            p.diedYear = Integer.parseInt(a[6]);
            p.royal = a[7].equals("1");
            if (a.length > 8 && !a[8].equals("-")) p.note = a[8];
            PEOPLE.put(p.id, p);
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        PEOPLE.clear();
        FOLLOW.clear();
        t = 0;
    }
}
