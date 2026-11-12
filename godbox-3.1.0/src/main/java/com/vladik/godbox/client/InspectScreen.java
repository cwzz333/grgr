package com.vladik.godbox.client;

import com.vladik.godbox.net.InspectPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** v3.1: карточка существа — портрет, сведения и родовое древо. */
public class InspectScreen extends Screen {
    /** За кем следит камера (null — слежки нет); приходит с сервера. */
    static String followName;

    private static final int LW = 150, BW = 88, BH = 22, GAP = 5, ROW = BH + 6;
    private static final int LINE = 0xFF8A7A5A, LINE_SELF = 0xFFC8A030;
    private static final String[] BANDS = {"Деды и бабки", "Родители", "Он сам, братья и сёстры", "Дети"};
    private static final String[] RELS = {"Дед или бабка", "Родитель", "Осматриваемый", "Брат или сестра", "Ребёнок"};

    private record Slot(InspectPayloads.Node n, int x, int y) {}

    private InspectPayloads.Profile p;
    private final boolean god;
    private final Deque<UUID> back = new ArrayDeque<>();
    private UUID cameFrom;
    private boolean goingBack;

    private int px, py, pw, ph;
    private int tx, ty, tw, th;
    private int linesTop, linesH;
    private final List<Slot> slots = new ArrayList<>();
    private final List<int[]> wires = new ArrayList<>();
    private final List<int[]> captions = new ArrayList<>();
    private int treeH, treeScroll, linesScroll, linesTotal;
    private ButtonWidget backButton;

    private InspectScreen(InspectPayloads.Profile p, boolean god) {
        super(Text.literal("Осмотр существа"));
        this.p = p;
        this.god = god;
    }

    // ---------------- подключение ----------------
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(InspectPayloads.Profile.ID, (payload, ctx) -> ctx.client().execute(() -> open(payload)));
        ClientPlayNetworking.registerGlobalReceiver(InspectPayloads.FollowState.ID, (payload, ctx) -> ctx.client().execute(() ->
                followName = payload.on() ? payload.name() : null));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> followName = null);
        // на экране бога: плашка слежки и клавиша V — прекратить
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof GodScreen)) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> followBadge(ctx, client.textRenderer, s.width));
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scan, mods) -> {
                if (key == GLFW.GLFW_KEY_V && followName != null && !GodScreen.typing()) stopFollow();
            });
        });
    }

    private static void stopFollow() {
        followName = null;
        if (ClientPlayNetworking.canSend(InspectPayloads.Follow.ID))
            ClientPlayNetworking.send(new InspectPayloads.Follow(new UUID(0L, 0L), false));
    }

    private static void followBadge(DrawContext ctx, TextRenderer tr, int width) {
        if (followName == null) return;
        String s = "📷 Слежка: " + followName + "  ·  V — прекратить";
        int w = tr.getWidth(s) + 14, y = WorldPanels.bottom() + 24; // сразу под строкой подсказки, не доходя до нижней панели
        ctx.fill(width / 2 - w / 2, y, width / 2 + w / 2, y + 14, 0xD0101018);
        ctx.fill(width / 2 - w / 2, y + 13, width / 2 + w / 2, y + 14, 0xFF80FFFF);
        ctx.drawCenteredTextWithShadow(tr, s, width / 2, y + 3, 0xFF80FFFF);
    }

    private static void open(InspectPayloads.Profile p) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return;
        if (c.currentScreen instanceof InspectScreen s) {
            s.set(p);
            return;
        }
        boolean god = c.currentScreen instanceof GodScreen;
        if (!god && c.currentScreen != null) return;
        double mx = c.mouse.getX(), my = c.mouse.getY();
        // экран бога закрываем «тихо», иначе он выключит режим бога; при закрытии карточки вернём его
        if (c.currentScreen instanceof GodScreen gs) gs.closeSilently();
        c.setScreen(new InspectScreen(p, god));
        InputUtil.setCursorParameters(c.getWindow().getHandle(), InputUtil.GLFW_CURSOR_NORMAL, mx, my);
    }

    private void set(InspectPayloads.Profile np) {
        if (!goingBack && cameFrom != null && !cameFrom.equals(np.id())) {
            back.push(cameFrom);
            while (back.size() > 30) back.removeLast();
        }
        goingBack = false;
        cameFrom = null;
        p = np;
        treeScroll = 0;
        linesScroll = 0;
        clearAndInit();
    }

    private void ask(UUID id, boolean isBack) {
        if (!ClientPlayNetworking.canSend(InspectPayloads.Ask.ID)) return;
        goingBack = isBack;
        cameFrom = p.id();
        ClientPlayNetworking.send(new InspectPayloads.Ask(id));
    }

    // ---------------- раскладка ----------------
    @Override
    protected void init() {
        pw = Math.min(width - 12, 640);
        ph = Math.min(height - 12, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tx = px + 6 + LW + 8;
        ty = py + 22;
        tw = px + pw - 6 - tx;
        th = py + ph - 6 - ty;

        int by = py + ph - 24;
        ButtonWidget follow = ButtonWidget.builder(Text.literal(p.following() ? "Не следить" : "Следить"), b -> {
            if (ClientPlayNetworking.canSend(InspectPayloads.Follow.ID))
                ClientPlayNetworking.send(new InspectPayloads.Follow(p.id(), !p.following()));
            close();
        }).dimensions(px + 6, by, 73, 18).build();
        follow.active = p.following() || (p.alive() && p.entityId() >= 0 && god);
        addDrawableChild(follow);
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close()).dimensions(px + 6 + 77, by, 73, 18).build());
        backButton = ButtonWidget.builder(Text.literal("← Назад"), b -> {
            if (!back.isEmpty()) ask(back.pop(), true);
        }).dimensions(px + pw - 6 - 60, py + 4, 60, 15).build();
        backButton.visible = !back.isEmpty();
        addDrawableChild(backButton);

        layoutTree();
        treeScroll = Math.max(0, Math.min(treeScroll, treeH - th));
    }

    private Slot slot(UUID id) {
        for (Slot s : slots) if (s.n.id().equals(id)) return s;
        return null;
    }

    private void wire(int x1, int y1, int x2, int y2, int color) {
        wires.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), color});
    }

    /** Ставит поколение рядами по центру; возвращает ряды. */
    private List<List<Slot>> place(List<InspectPayloads.Node> nodes, int y, int cols) {
        List<List<Slot>> rows = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i += cols) {
            int n = Math.min(cols, nodes.size() - i);
            int x = (tw - (n * BW + (n - 1) * GAP)) / 2;
            List<Slot> row = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                Slot s = new Slot(nodes.get(i + j), x + j * (BW + GAP), y);
                row.add(s);
                slots.add(s);
            }
            rows.add(row);
            y += ROW;
        }
        return rows;
    }

    /** Шина над поколением: от точек сверху (xs на высоте bus) к каждому ящику; нижние ряды — через ствол слева. */
    private void hang(List<Integer> xs, int bus, List<List<Slot>> rows, int color) {
        if (rows.isEmpty() || xs.isEmpty()) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x : xs) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        for (Slot s : rows.get(0)) {
            int cx = s.x + BW / 2;
            lo = Math.min(lo, cx);
            hi = Math.max(hi, cx);
            wire(cx, bus, cx, s.y, color);
        }
        if (rows.size() > 1) lo = 3;
        wire(lo, bus, hi, bus, color);
        for (int i = 1; i < rows.size(); i++) {
            List<Slot> row = rows.get(i);
            int y = row.get(0).y - 3, max = 3;
            for (Slot s : row) {
                int cx = s.x + BW / 2;
                max = Math.max(max, cx);
                wire(cx, y, cx, s.y, color);
            }
            wire(3, y, max, y, color);
            if (i == rows.size() - 1) wire(3, bus, 3, y, color);
        }
    }

    private void layoutTree() {
        slots.clear();
        wires.clear();
        captions.clear();
        treeH = 0;
        if (tw < BW + 12) return;
        List<List<InspectPayloads.Node>> gen = new ArrayList<>();
        for (int i = 0; i < 4; i++) gen.add(new ArrayList<>());
        for (InspectPayloads.Node n : p.tree()) {
            switch (n.rel()) {
                case InspectPayloads.GRAND -> gen.get(0).add(n);
                case InspectPayloads.PARENT -> gen.get(1).add(n);
                case InspectPayloads.SELF -> gen.get(2).add(0, n);
                case InspectPayloads.SIBLING -> gen.get(2).add(n);
                case InspectPayloads.CHILD -> gen.get(3).add(n);
                default -> {}
            }
        }
        int cols = Math.max(1, (tw - 12 + GAP) / (BW + GAP));
        int y = 2;
        int[] bus = new int[4];
        List<List<List<Slot>>> rows = new ArrayList<>();
        for (int g = 0; g < 4; g++) {
            if (gen.get(g).isEmpty()) {
                rows.add(new ArrayList<>());
                continue;
            }
            captions.add(new int[]{g, y});
            y += 11;
            bus[g] = y + 3;
            y += 8;
            List<List<Slot>> r = place(gen.get(g), y, cols);
            rows.add(r);
            y += r.size() * ROW;
        }
        treeH = y;

        // деды -> их дети-родители
        for (List<Slot> row : rows.get(0)) for (Slot s : row) {
            Slot par = slot(s.n.link());
            if (par == null) continue;
            int cx = s.x + BW / 2, pc = par.x + BW / 2;
            wire(cx, s.y + BH, cx, bus[1], LINE);
            wire(cx, bus[1], pc, bus[1], LINE);
            wire(pc, bus[1], pc, par.y, LINE);
        }
        // родители -> он сам и братья
        List<Integer> xs = new ArrayList<>();
        for (List<Slot> row : rows.get(1)) for (Slot s : row) {
            int cx = s.x + BW / 2;
            xs.add(cx);
            wire(cx, s.y + BH, cx, bus[2], LINE);
        }
        hang(xs, bus[2], rows.get(2), LINE);
        // он сам -> дети
        Slot self = slot(p.id());
        if (self != null && !rows.get(3).isEmpty()) {
            int cx = self.x + BW / 2;
            xs = new ArrayList<>();
            if (rows.get(2).size() <= 1) {
                wire(cx, self.y + BH, cx, bus[3], LINE_SELF);
                xs.add(cx);
            } else {
                // под ним ещё ряды братьев — обходим их по правому краю
                int side = tw - 3;
                wire(cx, self.y + BH, cx, self.y + BH + 1, LINE_SELF);
                wire(cx, self.y + BH + 1, side, self.y + BH + 1, LINE_SELF);
                wire(side, self.y + BH + 1, side, bus[3], LINE_SELF);
                xs.add(side);
            }
            hang(xs, bus[3], rows.get(3), LINE_SELF);
        }
    }

    // ---------------- отрисовка ----------------
    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String years(int a) {
        int d = a % 10, h = a % 100;
        if (d == 1 && h != 11) return "год";
        if (d >= 2 && d <= 4 && (h < 12 || h > 14)) return "года";
        return "лет";
    }

    private static String yearText(int y) {
        return y >= 0 ? "год " + y : "за " + (-y) + " " + years(-y) + " до летописи";
    }

    private LivingEntity entity() {
        if (client == null || client.world == null || p.entityId() < 0) return null;
        Entity e = client.world.getEntityById(p.entityId());
        return e instanceof LivingEntity le && le.isAlive() && le.getUuid().equals(p.id()) ? le : null;
    }

    private Slot slotAt(double mx, double my) {
        if (mx < tx || mx >= tx + tw || my < ty || my >= ty + th) return null;
        int x = (int) mx - tx, y = (int) my - ty + treeScroll;
        for (Slot s : slots) if (x >= s.x && x < s.x + BW && y >= s.y && y < s.y + BH) return s;
        return null;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        // лёгкое затемнение без размытия — мир остаётся виден
        ctx.fill(0, 0, width, height, 0x70000000);
        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF6B4E22);
        ctx.fill(px, py, px + pw, py + ph, 0xF0141418);
        renderLeft(ctx, mx, my);
        renderTree(ctx, mx, my);
    }

    private void renderLeft(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        int x = px + 6, y = py + 6;
        int rgb = p.kingdom().isEmpty() ? 0xFFFFFF : p.rgb();
        LivingEntity e = entity();
        ctx.fill(x, y, x + LW, y + 62, 0xC0262630);
        ctx.fill(x, y, x + 2, y + 62, 0xFF000000 | rgb);
        if (e != null) {
            InventoryScreen.drawEntity(ctx, x + 2, y + 1, x + LW, y + 61, 26, 0.0625f, mx, my, e);
        } else {
            String s = p.alive() ? "не на виду" : "†";
            ctx.drawCenteredTextWithShadow(tr, s, x + LW / 2, y + 27, 0xFF8A8A8A);
        }
        y += 66;
        String name = (p.alive() ? "" : "† ") + p.name();
        ctx.drawTextWithShadow(tr, tr.trimToWidth(name, LW), x, y, p.alive() ? 0xFF000000 | brighten(rgb) : 0xFF9A9A9A);
        y += 10;
        ctx.drawTextWithShadow(tr, tr.trimToWidth(p.kingdom().isEmpty() ? "без королевства" : p.kingdom(), LW), x, y, 0xFF9A9A9A);
        y += 13;

        List<OrderedText> out = new ArrayList<>();
        if (e != null) addLine(out, "Вид: " + e.getType().getName().getString());
        for (String s : p.lines()) addLine(out, s);
        linesTop = y;
        linesH = py + ph - 28 - y;
        linesTotal = out.size() * 10;
        linesScroll = Math.max(0, Math.min(linesScroll, linesTotal - linesH));
        if (linesH <= 0) return;
        ctx.enableScissor(x, linesTop, x + LW + 2, linesTop + linesH);
        int ly = linesTop - linesScroll;
        for (OrderedText o : out) {
            if (ly > linesTop - 10 && ly < linesTop + linesH) ctx.drawTextWithShadow(tr, o, x, ly, 0xFFFFFFFF);
            ly += 10;
        }
        ctx.disableScissor();
        bar(ctx, x + LW + 2, linesTop, linesH, linesTotal, linesScroll);
    }

    private void addLine(List<OrderedText> out, String s) {
        int i = s.indexOf(": ");
        MutableText t = i < 0 ? Text.literal(s)
                : Text.literal(s.substring(0, i + 2)).formatted(Formatting.GRAY).append(Text.literal(s.substring(i + 2)).formatted(Formatting.WHITE));
        out.addAll(textRenderer.wrapLines(t, LW - 2));
    }

    /** Тёмные цвета королевств плохо читаются на тёмном фоне — подтягиваем к светлому. */
    private static int brighten(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        r += (255 - r) * 2 / 5;
        g += (255 - g) * 2 / 5;
        b += (255 - b) * 2 / 5;
        return r << 16 | g << 8 | b;
    }

    private void bar(DrawContext ctx, int x, int y, int h, int total, int scroll) {
        if (total <= h || h <= 0) return;
        int len = Math.max(10, h * h / total);
        int by = y + (h - len) * scroll / Math.max(1, total - h);
        ctx.fill(x, y, x + 2, y + h, 0x40FFFFFF);
        ctx.fill(x, by, x + 2, by + len, 0xFFAA8844);
    }

    private void renderTree(DrawContext ctx, int mx, int my) {
        TextRenderer tr = textRenderer;
        ctx.drawTextWithShadow(tr, "Родословная", tx, py + 8, 0xFFFFD700);
        ctx.fill(tx - 5, py + 6, tx - 4, py + ph - 6, 0xFF3A3020);
        if (slots.size() <= 1) {
            String s = p.tree().isEmpty() ? "У этого существа нет родословной" : "Родичи неизвестны";
            int hy = ty + treeH + 6;
            for (OrderedText o : tr.wrapLines(Text.literal(s + " — летопись рода ведётся у жителей королевств с момента рождения."), tw - 8)) {
                ctx.drawTextWithShadow(tr, o, tx + 2, hy, 0xFF8A8A8A);
                hy += 10;
            }
        }
        Slot hov = slotAt(mx, my);
        ctx.enableScissor(tx, ty, tx + tw, ty + th);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(tx, ty - treeScroll, 0);
        for (int[] w : wires) ctx.fill(w[0], w[1], w[2] + 1, w[3] + 1, w[4]);
        for (int[] c : captions) ctx.drawTextWithShadow(tr, BANDS[c[0]], 10, c[1], 0xFF6F6A5C);
        for (Slot s : slots) box(ctx, tr, s, s == hov);
        ctx.getMatrices().pop();
        ctx.disableScissor();
        bar(ctx, tx + tw + 1, ty, th, treeH, treeScroll);
    }

    private void box(DrawContext ctx, TextRenderer tr, Slot s, boolean hov) {
        InspectPayloads.Node n = s.n;
        boolean self = n.rel() == InspectPayloads.SELF;
        int x = s.x, y = s.y;
        int border = self ? 0xFFFFD700 : hov ? 0xFFFFFFFF : n.alive() ? 0xFF5A5A66 : 0xFF3A3A3A;
        ctx.fill(x, y, x + BW, y + BH, border);
        ctx.fill(x + 1, y + 1, x + BW - 1, y + BH - 1, n.alive() ? (hov ? 0xFF34343F : 0xFF262630) : (hov ? 0xFF262626 : 0xFF1A1A1A));
        ctx.fill(x + 1, y + 1, x + 3, y + BH - 1, n.alive() ? 0xFF000000 | n.rgb() : 0xFF555555);
        String name = (n.royal() ? "♔ " : "") + n.name();
        ctx.drawTextWithShadow(tr, tr.trimToWidth(name, BW - 8), x + 5, y + 3, n.alive() ? (n.royal() ? 0xFFFFE680 : 0xFFFFFFFF) : 0xFF8A8A8A);
        int a = Math.max(0, (n.alive() ? p.year() : n.died()) - n.born());
        String sub = (n.alive() ? "" : "† ") + a + " " + years(a);
        ctx.drawTextWithShadow(tr, sub, x + 5, y + 12, n.alive() ? 0xFFAAAAAA : 0xFF6A6A6A);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        Slot s = slotAt(mx, my);
        if (s == null || (backButton != null && backButton.visible && backButton.isMouseOver(mx, my))) return;
        InspectPayloads.Node n = s.n;
        List<Text> tip = new ArrayList<>();
        tip.add(Text.literal((n.royal() ? "♔ " : "") + n.name()).formatted(n.alive() ? Formatting.WHITE : Formatting.GRAY));
        tip.add(Text.literal(RELS[Math.max(0, Math.min(RELS.length - 1, n.rel()))]).formatted(Formatting.GOLD));
        tip.add(Text.literal("Родился: " + yearText(n.born())).formatted(Formatting.GRAY));
        if (n.alive()) {
            int a = Math.max(0, p.year() - n.born());
            tip.add(Text.literal("Жив, " + a + " " + years(a)).formatted(Formatting.GREEN));
        } else {
            int a = Math.max(0, n.died() - n.born());
            tip.add(Text.literal("† Умер: " + yearText(n.died()) + " (в " + a + " " + years(a) + ")").formatted(Formatting.GRAY));
        }
        if (n.royal()) tip.add(Text.literal("Королевская кровь").formatted(Formatting.YELLOW));
        if (n.rel() != InspectPayloads.SELF) tip.add(Text.literal("Клик — открыть карточку").formatted(Formatting.DARK_AQUA));
        ctx.drawTooltip(textRenderer, tip, mx, my);
    }

    // ---------------- ввод ----------------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        Slot s = slotAt(mx, my);
        if (s == null || s.n.rel() == InspectPayloads.SELF) return false;
        ask(s.n.id(), false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int d = vertical > 0 ? -ROW : ROW;
        if (mx >= tx) treeScroll = Math.max(0, Math.min(treeScroll + d, treeH - th));
        else linesScroll = Math.max(0, Math.min(linesScroll + (d > 0 ? 20 : -20), linesTotal - linesH));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && !back.isEmpty()) {
            ask(back.pop(), true);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Возврат на экран бога, если карточку открыли из него. */
    @Override
    public void close() {
        if (client == null) return;
        client.setScreen(god && client.player != null ? new GodScreen() : null);
    }
}
