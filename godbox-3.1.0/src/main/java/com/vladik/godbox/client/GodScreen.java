package com.vladik.godbox.client;

import com.vladik.godbox.Category;
import com.vladik.godbox.Power;
import com.vladik.godbox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Экран бога: камера сверху, свободный курсор, панель сил.
 * v3.0: боковая панель вкладок, карточка силы, избранное, отдельные панели времени и эпох,
 * контур кисти в мире, подтверждение опасных сил, справка по I.
 * v3.1: нижняя панель с прокруткой, поле поиска, недавние силы, подсказки, управление стрелками.
 */
public class GodScreen extends Screen {
    private static Category category = Category.BOMBS;
    private static Power selected = Power.BOMB;
    private static int brush = 3;
    private static int shape = 0;
    private static String query = "";
    private static boolean searching;
    private static boolean codex;
    private static boolean help;
    private static int codexScroll;
    private static final String[] SHAPES = {"круг", "квадрат", "кольцо", "спрей"};
    private static final Set<String> DANGER = Set.of("ATOMIC", "CZAR_BOMBA", "TSAR_BOMBA", "ANTIMATTER", "GREY_GOO", "BLACK_HOLE", "WORLD_BANISH", "NUKE");
    private static float pitch = 90f;
    private static BlockPos preview;

    // v3.1: прокрутка сетки — запоминается по вкладкам на время сессии
    private static final Map<Category, Integer> SCROLLS = new EnumMap<>(Category.class);
    private static int searchScroll;
    private static double scrollPx;
    private static Object scrollView;
    private static int cursor;
    private static Object cacheKey;
    private static List<Power> cacheList = List.of();
    private static final int BAR_H = 14, INFO_H = 24, SB = 4, RAIL_TOP = 4;

    private boolean initialized;
    private boolean sentOff;
    private boolean leftDown;
    private int repeatTimer;
    private double targetY;
    private boolean hudWas;
    private double fovEffectWas;
    private BlockHitResult hover;
    private Entity follow;
    private Power armed;
    private long armedAt;
    private String toast = "";
    private long toastUntil;
    private int toastColor = 0xFFFFFFFF;
    private boolean dragBar;
    private double dragOff;

    public GodScreen() {
        super(Text.literal("GodBox"));
    }

    @Override
    protected void init() {
        if (initialized || client == null || client.player == null) return;
        initialized = true;
        hudWas = client.options.hudHidden;
        client.options.hudHidden = true;
        fovEffectWas = client.options.getFovEffectScale().getValue();
        client.options.getFovEffectScale().setValue(0.0);
        targetY = client.player.getY();
        toast("GodBox 3.0 — I: справка, ПКМ по силе: в избранное", 0xFFFFD700);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // без затемнения и размытия — мы смотрим на мир
    }

    // ---------------- данные для контура кисти ----------------
    static BlockPos previewPos() { return preview; }

    static int previewRadius() { return selected != null && selected.category != null ? brush : 0; }

    static int previewShape() { return shape; }

    static int previewColor() { return selected != null && DANGER.contains(selected.name()) ? 0xFF4040 : 0x60E0FF; }

    private void toast(String s, int color) {
        toast = s;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 2600;
    }

    /** v3.1: ответ сервера (строка над хотбаром) — HUD скрыт, поэтому показываем его здесь. */
    void note(String s, int color) {
        if (s != null && !s.isEmpty()) toast(s, color);
    }

    // ---------------- раскладка интерфейса ----------------
    private int slot() { return MathHelper.clamp(width / 28, 20, 26); }

    private int railCell() {
        int n = Category.values().length;
        return MathHelper.clamp((height - RAIL_TOP - 8) / n, 9, 22);
    }

    private int railW() { return railCell() + 6; }

    /** Миникарта ужимается, чтобы не налезать на панель эпохи на узком экране. */
    private int mapSize() { return MathHelper.clamp(Math.min(110, width / 2 - WorldPanels.ERA_W / 2 - 14), 40, 110); }

    private int mapX() { return width - 8 - mapSize(); }

    private static boolean listMode() { return searching && !query.isEmpty(); }

    /** Идёт ввод в поле поиска: буквенные клавиши не должны срабатывать как команды. */
    static boolean typing() { return searching; }

    private static int target() { return listMode() ? searchScroll : SCROLLS.getOrDefault(category, 0); }

    private static void setTarget(int v) {
        if (listMode()) searchScroll = v;
        else SCROLLS.put(category, v);
    }

    /**
     * Нижняя панель: строка выбранной силы, избранное и недавние, заголовок с поиском, сетка сил.
     * Одна и та же раскладка используется для отрисовки и для кликов.
     */
    private final class Dock {
        final int s = slot(), cell = s + 2;
        final boolean rows = listMode();
        final List<Power> list = list();
        final int x0, x1, top, bottom, infoY, stripY, barY, gridY, gridH;
        final int gx0, gridR, gx, cols, cw, slotW, rowsAll, maxScroll, sbX;
        final int helpX, searchX, searchW;
        final List<Power> fav, rec;
        final int favX, recX;

        Dock() {
            int margin = railW() + 8;
            int minW = cell + 2 * Ui.PAD + SB + 2;
            int w = Math.max(minW, Math.min(width - 2 * margin, 28 * cell + 2 * Ui.PAD + SB + 2));
            x0 = (width - w) / 2;
            x1 = x0 + w;
            gx0 = x0 + Ui.PAD;
            gridR = x1 - Ui.PAD - SB - 2;
            sbX = gridR + 2;
            int gridW = gridR - gx0;
            cols = Math.max(1, rows ? gridW / 150 : gridW / cell);
            cw = rows ? gridW / cols : cell;
            slotW = rows ? cw - 2 : s;
            gx = rows ? gx0 : gx0 + (gridW - cols * cell) / 2;
            // сетка не поднимается выше миникарты и панелей времени
            int fixed = s + 49;
            int cap = MathHelper.clamp((height - 4 - fixed - (4 + mapSize() + 8)) / cell, 1, rows ? 5 : 3);
            rowsAll = (list.size() + cols - 1) / cols;
            gridH = MathHelper.clamp(rowsAll, 1, cap) * cell;
            maxScroll = Math.max(0, rowsAll * cell - gridH);
            bottom = height - 4;
            gridY = bottom - Ui.PAD - gridH;
            barY = gridY - BAR_H - 1;
            stripY = barY - (s + 4);
            infoY = stripY - INFO_H;
            top = infoY - 2;
            helpX = x1 - Ui.PAD - 12;
            searchW = MathHelper.clamp(w / 3, 40, 140);
            searchX = helpX - 4 - searchW;
            int capStrip = Math.max(0, (w - 2 * Ui.PAD - 12 - 14) / cell);
            List<Power> f = Favorites.list(), r = Recent.list();
            fav = f.subList(0, Math.min(f.size(), capStrip));
            rec = r.subList(0, Math.min(r.size(), capStrip - fav.size()));
            favX = x0 + Ui.PAD + 12;
            recX = x1 - Ui.PAD - rec.size() * cell;
            int t = MathHelper.clamp(target(), 0, maxScroll);
            if (t != target()) setTarget(t);
            scrollPx = MathHelper.clamp(scrollPx, 0, maxScroll);
        }

        int scroll() { return (int) Math.round(scrollPx); }

        int cellX(int i) { return gx + (i % cols) * cw + 1; }

        int cellY(int i) { return gridY + (i / cols) * cell + 1 - scroll(); }

        int stripSlotY() { return stripY + 2; }

        boolean inside(double mx, double my) { return mx >= x0 && mx < x1 && my >= top && my < bottom; }

        boolean inGrid(double mx, double my) { return mx >= gx0 && mx < gridR && my >= gridY && my < gridY + gridH; }

        boolean inSearch(double mx, double my) { return Ui.in(mx, my, searchX, barY + 1, searchW, 12); }

        boolean inHelp(double mx, double my) { return Ui.in(mx, my, helpX, barY + 1, 12, 12); }

        boolean inBar(double mx, double my) { return maxScroll > 0 && Ui.in(mx, my, sbX - 1, gridY, SB + 2, gridH); }

        int thumbH() { return Math.max(8, gridH * gridH / Math.max(1, rowsAll * cell)); }

        int thumbY() { return maxScroll == 0 ? gridY : gridY + (int) ((gridH - thumbH()) * scrollPx / maxScroll); }

        /** Что под курсором: {вид (0 сетка, 1 избранное, 2 недавние), номер, x, y, ширина} или null. */
        int[] hit(double mx, double my) {
            if (inGrid(mx, my)) {
                for (int i = 0; i < list.size(); i++) {
                    int x = cellX(i), y = cellY(i);
                    if (Ui.in(mx, my, x, y, slotW, s)) return new int[]{0, i, x, y, slotW};
                }
                return null;
            }
            int sy = stripSlotY();
            for (int i = 0; i < fav.size(); i++)
                if (Ui.in(mx, my, favX + i * cell, sy, s, s)) return new int[]{1, i, favX + i * cell, sy, s};
            for (int i = 0; i < rec.size(); i++)
                if (Ui.in(mx, my, recX + i * cell, sy, s, s)) return new int[]{2, i, recX + i * cell, sy, s};
            return null;
        }

        Power power(int[] h) { return h[0] == 0 ? list.get(h[1]) : h[0] == 1 ? fav.get(h[1]) : rec.get(h[1]); }

        /** Курсор на звезде в правом верхнем углу слота. */
        boolean onStar(int[] h, double mx, double my) { return Ui.in(mx, my, h[2] + h[4] - 9, h[3], 9, 9); }

        /** Прокрутить так, чтобы сила i была видна целиком. */
        void reveal(int i) {
            if (i < 0) return;
            int y = (i / cols) * cell, t = target();
            if (y < t) t = y;
            if (y + cell > t + gridH) t = y + cell - gridH;
            setTarget(MathHelper.clamp(t, 0, maxScroll));
        }
    }

    private static boolean danger(Power p) { return DANGER.contains(p.name()); }

    /** Размер и форма кисти влияют на силу (законы, время и эпохи кисть не используют). */
    private static boolean brushApplies(Power p) {
        return p.category != null && p.category != Category.LAWS && !p.name().startsWith("TIME_") && !p.name().startsWith("AGE_");
    }

    private static String hint(Power p) {
        String h = p.category == Category.LAWS ? "ЛКМ — переключить закон"
                : danger(p) ? "ЛКМ дважды — подтвердить"
                : p.continuous ? "Удерживайте ЛКМ — рисовать" : "ЛКМ — применить";
        return brushApplies(p) ? h + " · [ ] кисть · B форма" : h;
    }

    /** Строки подсказки для силы (перенос длинных строк делает {@link #drawTip}). */
    static List<Text> tooltipLines(Power p) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(p.title).formatted(Formatting.GOLD));
        if (p.category != null) lines.add(Text.literal("Вкладка: " + p.category.title).formatted(Formatting.YELLOW));
        lines.add(Text.literal(p.desc).formatted(Formatting.GRAY));
        lines.add(Text.literal((p.continuous ? "Удерживайте ЛКМ" : "Одиночное применение")
                + (danger(p) ? " · опасно: двойной клик" : "")).formatted(danger(p) ? Formatting.RED : Formatting.DARK_AQUA));
        lines.add(Text.literal(brushApplies(p) ? "Кисть: размер " + brush + ", " + SHAPES[shape] : "Кисть не используется").formatted(Formatting.DARK_AQUA));
        if (p.category == Category.LAWS) {
            Boolean on = LawStates.get(p);
            lines.add(on == null ? Text.literal("Состояние станет видно после переключения").formatted(Formatting.DARK_GRAY)
                    : Text.literal(on ? "Сейчас: ВКЛ" : "Сейчас: ВЫКЛ").formatted(on ? Formatting.GREEN : Formatting.RED));
        }
        lines.add(Text.literal(Favorites.has(p) ? "ПКМ — убрать из избранного" : "ПКМ — в избранное").formatted(Formatting.DARK_GRAY));
        return MortalClient.tooltip(p, lines);
    }

    private void drawTip(DrawContext ctx, List<Text> lines, int mx, int my) {
        int maxW = Math.max(60, Math.min(220, width - 40));
        List<OrderedText> out = new ArrayList<>();
        for (Text t : lines) out.addAll(textRenderer.wrapLines(t, maxW));
        ctx.drawTooltip(textRenderer, out, Ui.TIP, mx, my);
    }

    /** Значки поверх иконки рисуются выше предмета, иначе он их перекрывает. */
    private void drawSlot(DrawContext ctx, Power p, int x, int y, int w, boolean hov, boolean cur, String num, boolean starHot) {
        int s = slot();
        boolean danger = danger(p);
        Boolean law = LawStates.get(p);
        ctx.fill(x, y, x + w, y + s, hov ? Ui.CELL_HOVER : p == selected ? Ui.CELL_SEL : law != null ? (law ? Ui.LAW_ON : Ui.LAW_OFF) : Ui.CELL);
        if (law != null) ctx.fill(x, y + s - 2, x + w, y + s, law ? Ui.GREEN : Ui.RED);
        else ctx.fill(x, y + s - 1, x + w, y + s, danger ? Ui.RED : p.continuous ? Ui.AQUA : 0xFF505060);
        if (p == selected) ctx.drawBorder(x - 1, y - 1, w + 2, s + 2, Ui.GOLD);
        else if (cur) ctx.drawBorder(x - 1, y - 1, w + 2, s + 2, Ui.WHITE);
        ctx.drawItem(new ItemStack(p.icon), x + (s - 16) / 2, y + (s - 16) / 2);
        if (!MortalClient.affordable(p)) ctx.fill(x, y, x + s, y + s - 1, 200, 0xB0101018);
        if (w > s && p.category != null) {
            // строка результата поиска: название и вкладка
            int ty = y + (s - 8) / 2;
            String cat = Ui.ellipsis(textRenderer, p.category.title, Math.max(0, w / 3));
            int cwid = textRenderer.getWidth(cat);
            ctx.drawTextWithShadow(textRenderer, cat, x + w - 11 - cwid, ty, Ui.DIM);
            ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(textRenderer, p.title, w - s - 6 - cwid - 13), x + s + 2, ty,
                    p == selected ? Ui.GOLD : Ui.TEXT);
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 200);
        if (num != null) ctx.drawText(textRenderer, num, x + 1, y + 1, 0xFFAAAAAA, true);
        boolean fav = Favorites.has(p);
        if (hov) ctx.drawText(textRenderer, fav ? "★" : "☆", x + w - 7, y + 1, starHot ? Ui.WHITE : fav ? Ui.GOLD : Ui.MUTED, true);
        else if (fav) ctx.drawText(textRenderer, "★", x + w - 7, y + 1, Ui.GOLD, true);
        if (law != null) ctx.drawText(textRenderer, law ? "✔" : "✖", x + w - 8, y + s - 10, law ? Ui.GREEN : Ui.RED, true);
        ctx.getMatrices().pop();
    }

    private boolean overlay() { return help || codex || WorldMap.on || WorldPanels.popup; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        Power hoverPower = null;
        List<Text> tip = null;
        // под открытыми окнами панель не реагирует на курсор
        boolean blocked = overlay() || StatsPanel.inside(mx, my, width, height);
        int hx = blocked ? -9999 : mx, hy = blocked ? -9999 : my;

        // --- боковая панель вкладок ---
        Category[] cs = Category.values();
        int rc = railCell(), rt = RAIL_TOP;
        ctx.fill(2, rt - 2, 4 + rc + 2, rt + cs.length * rc + 2, 0xA0101018);
        for (int i = 0; i < cs.length; i++) {
            Category c = cs[i];
            int y = rt + i * rc;
            boolean sel = c == category && !listMode();
            boolean hov = hx >= 2 && hx < 4 + rc + 2 && hy >= y && hy < y + rc;
            if (sel) {
                ctx.fill(4, y, 4 + rc, y + rc - 1, 0xE0B8860B);
                ctx.fill(2, y, 4, y + rc - 1, Ui.GOLD_LIGHT);
            } else if (hov) ctx.fill(4, y, 4 + rc, y + rc - 1, 0xC0505060);
            if (rc >= 16) ctx.drawItem(new ItemStack(c.icon), 4 + (rc - 16) / 2, y + (rc - 16) / 2);
            else ctx.drawCenteredTextWithShadow(textRenderer, c.title.substring(0, 1), 4 + rc / 2, y + (rc - 8) / 2, Ui.WHITE);
            if (hov) tip = List.of(Text.literal(c.title).formatted(Formatting.GOLD),
                    Text.literal(Power.of(c).size() + " сил").formatted(Formatting.GRAY),
                    Text.literal("Tab / Shift+Tab — листать вкладки").formatted(Formatting.DARK_GRAY));
        }

        MiniMap.render(ctx, client, mapX(), 4, mapSize());

        // --- нижняя панель ---
        Dock d = new Dock();
        int s = d.s, n = d.list.size();
        Object view = d.rows ? "search" : category;
        int tgt = target();
        if (view != scrollView || dragBar) {
            scrollView = view;
            scrollPx = tgt;
        } else {
            scrollPx += (tgt - scrollPx) * Math.min(1.0, delta * 0.5);
            if (Math.abs(tgt - scrollPx) < 0.5) scrollPx = tgt;
        }
        ctx.fill(d.x0, d.top, d.x1, d.bottom, Ui.BG);
        ctx.fill(d.x0, d.top, d.x0 + 2, d.bottom, danger(selected) ? Ui.RED : Ui.GOLD_DARK);
        ctx.fill(d.x0 + 2, d.stripY, d.x1, d.stripY + 1, 0x30FFFFFF);
        ctx.fill(d.x0 + 2, d.gridY - 1, d.x1, d.gridY, 0x30FFFFFF);
        int[] hit = blocked ? null : d.hit(mx, my);

        // выбранная сила: значок, название, кисть, подсказка по управлению
        int ix = d.x0 + Ui.PAD + 2, iy = d.infoY, tx = ix + 24, right = d.x1 - Ui.PAD;
        ctx.fill(ix, iy + 2, ix + 20, iy + 22, Ui.CELL_SOLID);
        ctx.drawItem(new ItemStack(selected.icon), ix + 2, iy + 4);
        boolean ba = brushApplies(selected);
        String br;
        if (right - tx > 260) {
            StringBuilder dots = new StringBuilder();
            for (int i = 1; i <= 10; i++) dots.append(i <= brush ? '●' : '○');
            br = dots + " " + SHAPES[shape];
        } else br = "Кисть " + brush + " · " + SHAPES[shape];
        int bw = textRenderer.getWidth(br);
        ctx.drawTextWithShadow(textRenderer, br, right - bw, iy + 3, ba ? Ui.BLUE : Ui.DIM);
        Boolean law = LawStates.get(selected);
        String st = law == null ? "" : law ? "  ВКЛ" : "  ВЫКЛ";
        int stW = textRenderer.getWidth(st);
        String title = Ui.ellipsis(textRenderer, selected.title, right - bw - 8 - tx - stW);
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, tx, iy + 3, Ui.GOLD);
        if (law != null) ctx.drawTextWithShadow(textRenderer, st, tx + tw, iy + 3, law ? Ui.GREEN : Ui.RED);
        if (hover != null) {
            String tg = hover.getBlockPos().toShortString();
            int gw = textRenderer.getWidth(tg);
            if (tx + tw + stW + 10 + gw <= right - bw - 8) ctx.drawTextWithShadow(textRenderer, tg, right - bw - 8 - gw, iy + 3, Ui.DIM);
        }
        String hint = Ui.ellipsis(textRenderer, hint(selected), Math.max(0, right - tx - 60));
        int hw = textRenderer.getWidth(hint);
        ctx.drawTextWithShadow(textRenderer, hint, right - hw, iy + 13, Ui.AQUA);
        String line2 = follow != null ? "Слежка: " + follow.getName().getString()
                + (follow instanceof LivingEntity le ? " ♥" + (int) le.getHealth() + "/" + (int) le.getMaxHealth() : "") : selected.desc;
        ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(textRenderer, line2, right - hw - 8 - tx), tx, iy + 13, follow != null ? 0xFF80FFFF : Ui.MUTED);
        if (Ui.in(hx, hy, d.x0, d.top, d.x1 - d.x0, d.stripY - d.top)) hoverPower = selected;

        // избранное (слева) и недавние (справа)
        int sy = d.stripSlotY(), gly = sy + (s - 8) / 2;
        if (d.fav.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(textRenderer, "★ ПКМ по силе или ☆ — в избранное",
                    (d.rec.isEmpty() ? d.x1 : d.recX - 12) - d.x0 - 2 * Ui.PAD), d.x0 + Ui.PAD + 2, gly, Ui.DIM);
        } else {
            ctx.drawTextWithShadow(textRenderer, "★", d.x0 + Ui.PAD + 2, gly, Ui.GOLD);
            if (Ui.in(hx, hy, d.x0 + Ui.PAD, sy, 12, s)) tip = List.of(Text.literal("Избранное").formatted(Formatting.GOLD),
                    Text.literal("Shift+1–9 — выбрать, Shift+ПКМ — сдвинуть влево").formatted(Formatting.GRAY));
        }
        for (int i = 0; i < d.fav.size(); i++) {
            boolean hov = hit != null && hit[0] == 1 && hit[1] == i;
            drawSlot(ctx, d.fav.get(i), d.favX + i * d.cell, sy, s, hov, false, i < 9 ? "⇧" + (i + 1) : null, hov && d.onStar(hit, mx, my));
        }
        if (!d.rec.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "↺", d.recX - 10, gly, Ui.BLUE);
            if (Ui.in(hx, hy, d.recX - 12, sy, 12, s)) tip = List.of(Text.literal("Недавние").formatted(Formatting.GOLD),
                    Text.literal("Последние применённые силы").formatted(Formatting.GRAY));
        }
        for (int i = 0; i < d.rec.size(); i++) {
            boolean hov = hit != null && hit[0] == 2 && hit[1] == i;
            drawSlot(ctx, d.rec.get(i), d.recX + i * d.cell, sy, s, hov, false, null, hov && d.onStar(hit, mx, my));
        }

        // заголовок, поле поиска, кнопка справки
        String header = d.rows ? "Поиск: «" + query + "» — " + n : category.title + " — " + n + " сил";
        ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(textRenderer, header, d.searchX - 6 - (d.x0 + Ui.PAD + 2)), d.x0 + Ui.PAD + 2, d.barY + 3, Ui.GOLD_LIGHT);
        boolean sHov = d.inSearch(hx, hy);
        ctx.fill(d.searchX, d.barY + 1, d.searchX + d.searchW, d.barY + 13, sHov || searching ? 0xF0202028 : 0xE0000000);
        ctx.drawBorder(d.searchX, d.barY + 1, d.searchW, 12, searching ? Ui.GOLD : sHov ? Ui.MUTED : 0xFF505060);
        if (searching) {
            String caret = System.currentTimeMillis() / 400 % 2 == 0 ? "_" : "";
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(query, d.searchW - 12, true) + caret, d.searchX + 3, d.barY + 3, 0xFFFFFF55);
        } else {
            ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(textRenderer, "Поиск: Ctrl+F", d.searchW - 6), d.searchX + 3, d.barY + 3, Ui.DIM);
        }
        if (sHov) tip = List.of(Text.literal("Поиск силы").formatted(Formatting.GOLD),
                Text.literal("Ctrl+F или / — искать по названию и описанию во всех вкладках").formatted(Formatting.GRAY),
                Text.literal("Стрелки — выбор, Enter — выбрать, Esc — закрыть").formatted(Formatting.DARK_GRAY));
        boolean qHov = d.inHelp(hx, hy);
        ctx.fill(d.helpX, d.barY + 1, d.helpX + 12, d.barY + 13, qHov ? Ui.CELL_HOVER : Ui.CELL_SOLID);
        ctx.drawBorder(d.helpX, d.barY + 1, 12, 12, qHov ? Ui.GOLD : 0xFF505060);
        ctx.drawCenteredTextWithShadow(textRenderer, "?", d.helpX + 6, d.barY + 3, qHov ? Ui.GOLD : Ui.WHITE);
        if (qHov) tip = List.of(Text.literal("I — справка").formatted(Formatting.GOLD), Text.literal("F1 — тоже справка, H — энциклопедия").formatted(Formatting.GRAY));

        // --- сетка сил (с прокруткой) ---
        if (n == 0) ctx.drawCenteredTextWithShadow(textRenderer, "Ничего не найдено", (d.gx0 + d.gridR) / 2, d.gridY + (d.gridH - 8) / 2, Ui.DIM);
        ctx.enableScissor(d.gx0, d.gridY, d.gridR, d.gridY + d.gridH);
        for (int i = 0; i < n; i++) {
            int x = d.cellX(i), y = d.cellY(i);
            if (y + d.cell <= d.gridY || y - 1 >= d.gridY + d.gridH) continue;
            boolean hov = hit != null && hit[0] == 0 && hit[1] == i;
            drawSlot(ctx, d.list.get(i), x, y, d.slotW, hov, d.rows && i == cursor, !d.rows && i < 9 ? String.valueOf(i + 1) : null, hov && d.onStar(hit, mx, my));
        }
        ctx.disableScissor();
        if (d.maxScroll > 0) {
            boolean bHov = dragBar || d.inBar(hx, hy);
            ctx.fill(d.sbX, d.gridY, d.sbX + SB, d.gridY + d.gridH, 0x60000000);
            ctx.fill(d.sbX, d.thumbY(), d.sbX + SB, d.thumbY() + d.thumbH(), bHov ? Ui.GOLD_LIGHT : Ui.GOLD_DARK);
        }
        if (hit != null) {
            hoverPower = d.power(hit);
            if (d.onStar(hit, mx, my)) {
                tip = List.of(Text.literal(Favorites.has(hoverPower) ? "Убрать из избранного" : "В избранное").formatted(Formatting.GOLD));
                hoverPower = null;
            }
        }

        // --- время, эпоха, статистика, окна (выше значков сил) ---
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        WorldPanels.render(ctx, textRenderer, width, height, mx, my);
        long now = System.currentTimeMillis();
        if (now < toastUntil) {
            String ts = Ui.ellipsis(textRenderer, toast, width - 24);
            int tw2 = textRenderer.getWidth(ts) + 14;
            int ty = WorldPanels.bottom() + 8;
            ctx.fill(width / 2 - tw2 / 2, ty, width / 2 + tw2 / 2, ty + 14, 0xD0101018);
            ctx.fill(width / 2 - tw2 / 2, ty + 13, width / 2 + tw2 / 2, ty + 14, toastColor);
            ctx.drawCenteredTextWithShadow(textRenderer, ts, width / 2, ty + 3, toastColor);
        }
        StatsPanel.render(ctx, textRenderer, width, height);
        if (help) renderHelp(ctx);
        if (WorldMap.on) WorldMap.render(ctx, client, width, height, mx, my);
        if (codex) Codex.render(ctx, textRenderer, width, height, category, codexScroll);
        ctx.getMatrices().pop();

        if (blocked) return;
        if (tip != null) drawTip(ctx, tip, mx, my);
        else if (hoverPower != null) drawTip(ctx, tooltipLines(hoverPower), mx, my);
    }

    private void renderHelp(DrawContext ctx) {
        String[][] rows = {
                {"ЛКМ", "применить силу"}, {"ПКМ по силе", "добавить/убрать из избранного"}, {"WASD / Shift", "летать / быстрее"},
                {"Колесо", "высота камеры"}, {"Ctrl+колесо, [ ]", "размер кисти"}, {"B", "форма кисти"},
                {"Q/E, R/F", "поворот / наклон"}, {"1–9 / Shift+1–9", "сила / избранное"}, {"Tab", "следующая вкладка"},
                {"Ctrl+F или /", "поиск силы"}, {"Ctrl+Z", "отменить своё последнее действие"}, {"P", "пауза времени"},
                {"J", "выбор эпохи"}, {"M / N", "миникарта / карта мира"}, {"H", "энциклопедия"},
                {"K", "статистика"}, {"C", "следить за существом"}, {"Esc / G", "выйти из режима бога"},
                {"Стрелки", "выбор силы в сетке"}, {"Shift+Tab", "предыдущая вкладка"}, {"Enter", "выбрать найденную силу"},
                {"Колесо над сеткой", "прокрутка сил"}, {"☆ на значке силы", "в избранное / убрать"},
                {"Shift+ПКМ по ★", "сдвинуть избранное влево"}, {"F1 или ?", "эта справка"}};
        int kw = 0, dw = 0;
        for (String[] r : rows) {
            kw = Math.max(kw, textRenderer.getWidth(r[0]));
            dw = Math.max(dw, textRenderer.getWidth(r[1]));
        }
        int w = Math.max(300, kw + dw + 34), h = rows.length * 11 + 26;
        // на маленьком экране справка ужимается целиком, а не обрезается
        float k = Math.min(1f, Math.min((width - 8) / (float) (w + 4), (height - 8) / (float) (h + 4)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f, 0);
        ctx.getMatrices().scale(k, k, 1f);
        int x = -w / 2, y = -h / 2;
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, Ui.GOLD_DARK);
        ctx.fill(x, y, x + w, y + h, Ui.BG_SOLID);
        ctx.drawCenteredTextWithShadow(textRenderer, "GodBox 3.0 — управление", 0, y + 6, Ui.GOLD);
        for (int i = 0; i < rows.length; i++) {
            ctx.drawTextWithShadow(textRenderer, rows[i][0], x + 10, y + 22 + i * 11, Ui.BLUE);
            ctx.drawTextWithShadow(textRenderer, rows[i][1], x + kw + 24, y + 22 + i * 11, Ui.TEXT);
        }
        ctx.getMatrices().pop();
    }

    // ---------------- ввод ----------------
    /** Силы вкладки или результаты поиска по всем вкладкам (сначала совпадения в названии). */
    private static List<Power> list() {
        Object key = listMode() ? query : category;
        if (key.equals(cacheKey)) return cacheList;
        List<Power> r;
        if (!listMode()) r = Power.of(category);
        else {
            String q = Ui.norm(query);
            r = new ArrayList<>();
            List<Power> byDesc = new ArrayList<>();
            for (Power p : Power.values()) {
                if (p.category == null) continue;
                if (Ui.norm(p.title).contains(q)) r.add(p);
                else if (Ui.norm(p.desc).contains(q)) byDesc.add(p);
            }
            r.addAll(byDesc);
        }
        cacheKey = key;
        cacheList = r;
        return r;
    }

    private void setQuery(String q) {
        query = q.length() > 30 ? q.substring(0, 30) : q;
        cursor = 0;
        searchScroll = 0;
    }

    private void openSearch() {
        searching = true;
        setQuery("");
        codex = false; WorldMap.on = false; help = false;
    }

    private void closeSearch() {
        searching = false;
        setQuery("");
    }

    private void select(Power p) {
        if (searching && p.category != null) { closeSearch(); category = p.category; }
        if (p.category != null && !searching) category = p.category;
        selected = p;
        leftDown = false;
        armed = null;
        Dock d = new Dock();
        d.reveal(d.list.indexOf(p));
    }

    private void setCategory(Category c) {
        closeSearch();
        category = c;
        List<Power> l = Power.of(c);
        // при возврате на вкладку выбранная на ней сила и прокрутка остаются как были
        if (!l.isEmpty() && (selected == null || selected.category != c)) {
            int row = SCROLLS.getOrDefault(c, 0) / (slot() + 2);
            Dock d = new Dock();
            select(l.get(MathHelper.clamp(row * d.cols, 0, l.size() - 1)));
        }
    }

    private void toggleFav(Power p) {
        Favorites.toggle(p);
        toast(Favorites.has(p) ? "★ " + p.title + " — в избранном" : p.title + " убрана из избранного", 0xFFFFD700);
        Ui.click();
    }

    /** Стрелки: перемещение по сетке (в поиске — по результатам, выбор по Enter). */
    private void moveSel(int dx, int dy) {
        Dock d = new Dock();
        int n = d.list.size();
        if (n == 0) return;
        int i = d.rows ? cursor : d.list.indexOf(selected);
        if (i < 0) i = 0;
        else if (dx != 0) i = MathHelper.clamp(i + dx, 0, n - 1);
        else {
            int j = i + dy * d.cols;
            if (j >= 0 && j < n) i = j;
            else if (dy > 0 && i / d.cols < (n - 1) / d.cols) i = n - 1;
        }
        if (d.rows) {
            cursor = i;
            d.reveal(i);
        } else select(d.list.get(i));
    }

    private void sendNamed(String names) {
        for (String nm : names.split(";")) {
            Power p = Power.byName(nm);
            if (p != null) sendPower(p);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            if (overlay() || StatsPanel.inside(mx, my, width, height)) return true;
            Dock d = new Dock();
            int[] h = d.hit(mx, my);
            if (h != null) {
                Power p = d.power(h);
                if (h[0] == 1 && hasShiftDown()) {
                    Favorites.moveLeft(p);
                    Ui.click();
                } else toggleFav(p);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (help) { help = false; return true; }
        if (WorldMap.on) {
            int[] wm = WorldMap.click(mx, my);
            if (wm != null && ClientPlayNetworking.canSend(Payloads.Use.ID)) {
                follow = null;
                ClientPlayNetworking.send(new Payloads.Use(Power.CAMERA_TP.name(), new BlockPos(wm[0], 64, wm[1]), brush, true));
                WorldMap.on = false;
            }
            return true;
        }
        if (codex) {
            codex = false;
            return true;
        }
        String wp = WorldPanels.click(mx, my, width, height);
        if (wp != null) {
            if (!wp.isEmpty()) sendNamed(wp);
            Ui.click();
            return true;
        }
        if (StatsPanel.inside(mx, my, width, height)) return true;
        Category[] cs = Category.values();
        int rc = railCell(), rt = RAIL_TOP;
        for (int i = 0; i < cs.length; i++) {
            int y = rt + i * rc;
            if (mx >= 2 && mx < 4 + rc + 2 && my >= y && my < y + rc) {
                setCategory(cs[i]);
                Ui.click();
                return true;
            }
        }
        Dock d = new Dock();
        if (d.inside(mx, my)) {
            if (d.inHelp(mx, my)) {
                help = true; codex = false;
                Ui.click();
            } else if (d.inSearch(mx, my)) {
                if (!searching) openSearch();
                Ui.click();
            } else if (d.inBar(mx, my)) {
                int ty = d.thumbY(), th = d.thumbH();
                dragOff = my >= ty && my < ty + th ? my - ty : th / 2.0;
                dragBar = true;
                dragTo(d, my);
            } else {
                int[] h = d.hit(mx, my);
                if (h != null) {
                    Power p = d.power(h);
                    if (d.onStar(h, mx, my)) toggleFav(p);
                    else {
                        select(p);
                        Ui.click();
                    }
                }
            }
            return true;
        }
        // клик мимо панели во время поиска только закрывает поиск, силу не применяет
        if (searching) {
            closeSearch();
            return true;
        }
        int[] mp = MiniMap.click(mx, my, mapX(), 4, mapSize());
        if (mp != null) {
            follow = null;
            if (ClientPlayNetworking.canSend(Payloads.Use.ID))
                ClientPlayNetworking.send(new Payloads.Use(Power.CAMERA_TP.name(), new BlockPos(mp[0], 64, mp[1]), brush, true));
            return true;
        }
        hover = raycast(mx, my);
        if (DANGER.contains(selected.name())) {
            long now = System.currentTimeMillis();
            if (armed != selected || now - armedAt > 3000) {
                armed = selected;
                armedAt = now;
                toast("⚠ " + selected.title + ": нажмите ещё раз для подтверждения", 0xFFFF5555);
                return true;
            }
            armed = null;
        }
        if (selected == Power.POSSESS && follow == null) follow = pickEntity();
        leftDown = true;
        if (hover != null) Recent.push(selected);
        usePower(true);
        repeatTimer = selected.repeat;
        return true;
    }

    private void dragTo(Dock d, double my) {
        int span = d.gridH - d.thumbH();
        int t = span <= 0 ? 0 : (int) Math.round((my - dragOff - d.gridY) * d.maxScroll / span);
        setTarget(MathHelper.clamp(t, 0, d.maxScroll));
        scrollPx = target();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            leftDown = false;
            dragBar = false;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (client == null || client.player == null) return true;
        if (codex) {
            codexScroll = Math.max(0, Math.min(600, codexScroll + (vertical > 0 ? -3 : 3)));
            return true;
        }
        if (WorldMap.on) {
            WorldMap.scroll(mx, my, vertical);
            return true;
        }
        if (hasControlDown()) {
            brush = MathHelper.clamp(brush + (vertical > 0 ? 1 : -1), 1, 10);
            return true;
        }
        if (vertical != 0 && !help && !WorldPanels.popup) {
            // над сеткой колесо листает силы, а не поднимает камеру
            Dock d = new Dock();
            if (d.maxScroll > 0 && (d.inGrid(mx, my) || d.inBar(mx, my))) {
                int t = target() + (vertical > 0 ? -d.cell : d.cell);
                setTarget(MathHelper.clamp(t / d.cell * d.cell, 0, d.maxScroll));
                return true;
            }
        }
        double height = client.player.getY() - groundY();
        targetY -= vertical * Math.max(2, height * 0.15);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (WorldMap.on) {
            WorldMap.drag(dx, dy);
            return true;
        }
        if (dragBar && button == 0) {
            dragTo(new Dock(), my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void sendPower(Power p) {
        if (client == null || client.player == null || !ClientPlayNetworking.canSend(Payloads.Use.ID)) return;
        BlockPos pos = hover != null ? hover.getBlockPos() : client.player.getBlockPos();
        ClientPlayNetworking.send(new Payloads.Use(p.name(), pos, brush + shape * 100, true));
    }

    private void nextCategory(int mods) {
        Category[] cs = Category.values();
        int dir = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? cs.length - 1 : 1;
        setCategory(cs[(category.ordinal() + dir) % cs.length]);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searching) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeSearch();
            else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) setQuery("");
                else if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
            } else if (isPaste(key) && client != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) setQuery(query + clip.replace('\n', ' ').replace('\r', ' ').trim());
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                List<Power> r = list();
                if (listMode() && !r.isEmpty()) select(r.get(MathHelper.clamp(cursor, 0, r.size() - 1)));
                else closeSearch();
            } else if (key == GLFW.GLFW_KEY_LEFT) moveSel(-1, 0);
            else if (key == GLFW.GLFW_KEY_RIGHT) moveSel(1, 0);
            else if (key == GLFW.GLFW_KEY_UP) moveSel(0, -1);
            else if (key == GLFW.GLFW_KEY_DOWN) moveSel(0, 1);
            else if (key == GLFW.GLFW_KEY_TAB) nextCategory(mods);
            else if (key == GLFW.GLFW_KEY_F1) { closeSearch(); help = true; }
            return true;
        }
        if ((key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) || key == GLFW.GLFW_KEY_SLASH) {
            openSearch();
            return true;
        }
        if (WorldMap.on && WorldMap.key(key)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (codex || WorldMap.on || help || WorldPanels.popup)) {
            codex = false; WorldMap.on = false; help = false; WorldPanels.popup = false;
            return true;
        }
        if (GodKeys.is(GodKeys.HELP, key, scan) || key == GLFW.GLFW_KEY_F1) {
            help = !help; codex = false; WorldMap.on = false;
            return true;
        }
        if (GodKeys.is(GodKeys.ERAS, key, scan)) {
            WorldPanels.popup = !WorldPanels.popup;
            return true;
        }
        if (GodKeys.is(GodKeys.CODEX, key, scan)) {
            codex = !codex; codexScroll = 0; WorldMap.on = false; help = false;
            return true;
        }
        if (GodKeys.is(GodKeys.WORLD_MAP, key, scan)) {
            WorldMap.on = !WorldMap.on; codex = false; help = false;
            if (WorldMap.on && client != null && client.player != null) WorldMap.center(client.player.getX(), client.player.getZ());
            return true;
        }
        if (codex && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            codexScroll = Math.max(0, Math.min(600, codexScroll + (key == GLFW.GLFW_KEY_UP ? -3 : 3)));
            return true;
        }
        if (!overlay() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            moveSel(key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0, key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // сила уже выбрана стрелками — Enter лишь показывает её в сетке
            select(selected);
            return true;
        }
        if (key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            sendPower(Power.UNDO);
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) {
            sendPower(Power.TIME_PAUSE);
            return true;
        }
        if (GodKeys.is(GodKeys.STATS, key, scan)) {
            StatsPanel.on = !StatsPanel.on;
            if (StatsPanel.on) ClientPlayNetworking.send(new Payloads.Use(Power.CIV_STATS.name(), BlockPos.ORIGIN, -1, true));
            return true;
        }
        if (GodKeys.is(GodKeys.MAP, key, scan)) {
            MiniMap.on = !MiniMap.on;
            return true;
        }
        if (key == GLFW.GLFW_KEY_C) {
            follow = follow != null ? null : pickEntity();
            return true;
        }
        if (key == GLFW.GLFW_KEY_B) {
            shape = (shape + 1) % SHAPES.length;
            toast("Форма кисти: " + SHAPES[shape], 0xFF9AD0FF);
            return true;
        }
        if (key == GLFW.GLFW_KEY_G) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_BRACKET || key == GLFW.GLFW_KEY_MINUS) {
            brush = Math.max(1, brush - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_BRACKET || key == GLFW.GLFW_KEY_EQUAL) {
            brush = Math.min(10, brush + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            nextCategory(mods);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int i = key - GLFW.GLFW_KEY_1;
            List<Power> l = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? Favorites.list() : list();
            if (i < l.size()) select(l.get(i));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (searching) {
            if (chr == '/' && query.isEmpty()) return true;
            if (chr >= ' ' && query.length() < 30) setQuery(query + chr);
            return true;
        }
        return super.charTyped(chr, mods);
    }

    // ---------------- камера и применение ----------------
    private boolean down(int key) {
        if (searching) return false;
        return GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private double groundY() {
        ClientPlayerEntity p = client.player;
        return client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.getX()), MathHelper.floor(p.getZ()));
    }

    @Override
    public void tick() {
        if (client == null) return;
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null) {
            close();
            return;
        }
        float yaw = p.getYaw();
        if (down(GLFW.GLFW_KEY_Q)) yaw -= 4f;
        if (down(GLFW.GLFW_KEY_E)) yaw += 4f;
        if (down(GLFW.GLFW_KEY_R)) pitch = Math.min(90f, pitch + 2f);
        if (down(GLFW.GLFW_KEY_F)) pitch = Math.max(40f, pitch - 2f);

        double fwd = 0, side = 0;
        if (down(GLFW.GLFW_KEY_W)) fwd += 1;
        if (down(GLFW.GLFW_KEY_S)) fwd -= 1;
        if (down(GLFW.GLFW_KEY_D)) side += 1;
        if (down(GLFW.GLFW_KEY_A)) side -= 1;

        double ground = groundY();
        targetY = MathHelper.clamp(targetY, ground + 6, ground + 220);
        double height = Math.max(6, p.getY() - ground);
        double speed = Math.min(2.5, 0.35 + height * 0.02);
        if (down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT)) speed = Math.min(4.0, speed * 2.5);

        double rad = Math.toRadians(yaw);
        double s = Math.sin(rad), c = Math.cos(rad);
        // forward = (-sin, 0, cos), right = (-cos, 0, -sin)
        double vx = (-s * fwd - c * side) * speed;
        double vz = (c * fwd - s * side) * speed;
        // камера слежения сервера (осмотр существа) двигает игрока сама — две слежки дёргали бы камеру
        if (follow != null && (follow.isRemoved() || !follow.isAlive() || InspectScreen.followName != null)) follow = null;
        if (follow != null && fwd == 0 && side == 0) {
            vx = MathHelper.clamp((follow.getX() - p.getX()) * 0.3, -3, 3);
            vz = MathHelper.clamp((follow.getZ() - p.getZ()) * 0.3, -3, 3);
        }
        double vy = MathHelper.clamp((targetY - p.getY()) * 0.25, -4, 4);
        p.setVelocity(vx, vy, vz);
        p.setYaw(yaw);
        p.setPitch(pitch);

        double mx = client.mouse.getX() * width / client.getWindow().getWidth();
        double my = client.mouse.getY() * this.height / client.getWindow().getHeight();
        hover = raycast(mx, my);
        // контур кисти не рисуем, пока курсор над панелью (и пока не идёт рисование)
        boolean overUi = !leftDown && (new Dock().inside(mx, my)
                || (mx < railW() + 2 && my < RAIL_TOP + Category.values().length * railCell() + 2));
        preview = hover != null && !overUi ? hover.getBlockPos() : null;

        MiniMap.tick(client);
        if (StatsPanel.on && ++StatsPanel.timer % 40 == 0) ClientPlayNetworking.send(new Payloads.Use(Power.CIV_STATS.name(), net.minecraft.util.math.BlockPos.ORIGIN, -1, true));
        if (leftDown && selected.continuous) {
            if (--repeatTimer <= 0) {
                usePower(false);
                repeatTimer = Math.max(1, selected.repeat);
            }
        }
    }

    /** Луч из камеры через точку под курсором. */
    private BlockHitResult raycast(double mx, double my) {
        if (client == null || client.world == null || client.player == null) return null;
        Camera cam = client.gameRenderer.getCamera();
        Vec3d origin = cam.getPos();
        double ndcX = 2.0 * mx / width - 1.0;
        double ndcY = 1.0 - 2.0 * my / height;
        double fov = Math.toRadians(client.options.getFov().getValue());
        double tanH = Math.tan(fov / 2.0);
        double aspect = (double) client.getWindow().getFramebufferWidth() / Math.max(1, client.getWindow().getFramebufferHeight());

        float yaw = cam.getYaw();
        float pt = cam.getPitch();
        Vec3d forward = Vec3d.fromPolar(pt, yaw);
        double yr = Math.toRadians(yaw);
        Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3d up = right.crossProduct(forward);
        Vec3d dir = forward.add(right.multiply(ndcX * tanH * aspect)).add(up.multiply(ndcY * tanH)).normalize();
        Vec3d end = origin.add(dir.multiply(600));
        BlockHitResult hit = client.world.raycast(new RaycastContext(origin, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, client.player));
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private Entity pickEntity() {
        if (client == null || client.world == null || client.player == null) return null;
        Vec3d h = hover != null ? Vec3d.ofCenter(hover.getBlockPos()) : client.player.getPos();
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : client.world.getOtherEntities(client.player, new Box(h, h).expand(8, 12, 8),
                x -> x instanceof LivingEntity && !(x instanceof PlayerEntity) && x.isAlive())) {
            double d = e.squaredDistanceTo(h);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private void usePower(boolean first) {
        if (hover == null || !ClientPlayNetworking.canSend(Payloads.Use.ID)) return;
        ClientPlayNetworking.send(new Payloads.Use(selected.name(), hover.getBlockPos(), brush + shape * 100, first));
    }

    // ---------------- выход ----------------
    @Override
    public void close() {
        sendOff();
        super.close();
    }

    @Override
    public void removed() {
        preview = null;
        // v3.1: поиск и открытые окна не переживают закрытие экрана (вкладка и прокрутка — остаются)
        searching = false;
        query = "";
        cursor = 0;
        help = false;
        codex = false;
        WorldMap.on = false;
        WorldPanels.popup = false;
        if (client != null && initialized) {
            client.options.hudHidden = hudWas;
            client.options.getFovEffectScale().setValue(fovEffectWas);
        }
        sendOff();
    }

    public void closeSilently() {
        sentOff = true;
        if (client != null) client.setScreen(null);
    }

    private void sendOff() {
        if (sentOff) return;
        sentOff = true;
        if (ClientPlayNetworking.canSend(Payloads.Toggle.ID)) ClientPlayNetworking.send(new Payloads.Toggle(false));
    }
}
