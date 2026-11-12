package com.vladik.godbox.client;

import com.vladik.godbox.Power;
import com.vladik.godbox.WorldAges;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** v3.0: отдельные панели «Скорость времени» и «Эпоха мира» сверху по центру экрана бога. */
public final class WorldPanels {
    static final String[] TIME_LABEL = {"||", "x0.5", "x1", "x2", "x5"};
    static final String[] TIME_POWER = {"TIME_PAUSE", "TIME_SLOW", "TIME_NORMAL", "TIME_FAST", "TIME_FASTER"};
    static final float[] TIME_RATE = {0, 10, 20, 40, 100};
    static final String[] AGE_POWER = {"AGE_HOPE", "AGE_SUN", "AGE_WONDERS", "AGE_CHAOS", "AGE_DARK", "AGE_MOON", "AGE_ICE", "AGE_ASH", "AGE_DESPAIR"};
    public static final int[] AGE_RGB = {0x55FF55, 0xFFFF55, 0xFF55FF, 0xFF5555, 0x8A8A8A, 0x6E8BFF, 0x55FFFF, 0xFFAA00, 0xB04CD9};
    static final int BW = 30, BH = 14, ERA_W = 190, AGE_TOTAL = 1200;

    public static boolean popup;

    private WorldPanels() {}

    static int timeX(int width) { return width / 2 - (TIME_LABEL.length * (BW + 2) + 34) / 2; }

    static int active() {
        if (HudState.frozen) return 0;
        int best = 2;
        float bd = Float.MAX_VALUE;
        for (int i = 1; i < TIME_RATE.length; i++) {
            float d = Math.abs(TIME_RATE[i] - HudState.rate);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    public static int bottom() {
        int b = 4 + BH + 4 + 22;
        if (!MortalClient.active()) return b;
        int[] r = mortalRect(MinecraftClient.getInstance().getWindow().getScaledWidth());
        return Math.max(b, r[1] + r[3] + 2);
    }

    /**
     * Панель божьей силы {x, y, w, h}: под панелью эпохи, между карточкой силы и мини-картой;
     * в узком окне — под карточкой, правее вкладок.
     */
    static int[] mortalRect(int width) {
        int h = MortalClient.title.isEmpty() ? 12 : 23;
        // v3.1: карточки силы слева больше нет (нижняя панель) — слева только вкладки
        int card = 34, map = width - 118 - 6;
        int half = Math.min(width / 2 - card, map - width / 2);
        if (half * 2 >= 120) {
            int w = Math.min(ERA_W, half * 2);
            return new int[]{width / 2 - w / 2, 4 + BH + 4 + 22 + 4, w, h};
        }
        int x = 34;
        return new int[]{x, 74, Math.max(80, Math.min(ERA_W, map - x)), h};
    }

    static void renderMortal(DrawContext ctx, TextRenderer tr, int width, int mx, int my) {
        int[] r = mortalRect(width);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        int pool = MortalClient.pool, cap = MortalClient.cap;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xB0101018);
        if (hov) ctx.fill(x, y, x + w, y + h, 0x30FFFFFF);
        float part = Math.max(0f, Math.min(1f, pool / (float) cap));
        int col = part < 0.1f ? 0xFFE04040 : 0xFFE6B422;
        ctx.drawTextWithShadow(tr, "✦", x + 4, y + 2, col);
        String num = w >= 150 ? pool + "/" + cap : String.valueOf(pool);
        int nw = tr.getWidth(num);
        ctx.drawTextWithShadow(tr, num, x + w - nw - 4, y + 2, 0xFFFFFFFF);
        int bx = x + 16, bw = w - 16 - nw - 9;
        if (bw > 8) {
            ctx.fill(bx, y + 3, bx + bw, y + 9, 0xFF202020);
            ctx.fill(bx, y + 3, bx + (int) (bw * part), y + 9, col);
            ctx.fill(bx, y + 3, bx + (int) (bw * part), y + 4, 0x60FFFFFF);
        }
        boolean scen = !MortalClient.title.isEmpty();
        if (scen) {
            int tc = MortalClient.state == 1 ? 0xFF55FF55 : MortalClient.state == 2 ? 0xFFFF5555 : 0xFFFFD700;
            String title = tr.trimToWidth(MortalClient.title, w - 8);
            ctx.drawTextWithShadow(tr, title, x + 4, y + 13, tc);
            int tw = tr.getWidth(title) + 5;
            if (w - 8 - tw > 20) ctx.drawTextWithShadow(tr, tr.trimToWidth(MortalClient.progress, w - 8 - tw), x + 4 + tw, y + 13, 0xFFBBBBBB);
        }
        if (hov && !popup) {
            List<Text> tip = new ArrayList<>();
            tip.add(Text.literal("Божья сила: " + pool + " из " + cap).formatted(Formatting.GOLD));
            tip.add(Text.literal("Приток: +" + MortalClient.perMin + " в минуту — вера и население королевств").formatted(Formatting.GRAY));
            tip.add(Text.literal("Отвеченная молитва и вехи испытаний дают силу сразу").formatted(Formatting.GRAY));
            if (scen) {
                tip.add(Text.literal("Испытание «" + MortalClient.title + "»").formatted(Formatting.YELLOW));
                if (!MortalClient.progress.isEmpty()) tip.add(Text.literal(MortalClient.progress).formatted(Formatting.GRAY));
            } else tip.add(Text.literal("Сила «Испытание» на вкладке «Мир» — выбрать цель").formatted(Formatting.DARK_GRAY));
            ctx.drawTooltip(tr, tip, mx, my + 16);
        }
    }

    public static void render(DrawContext ctx, TextRenderer tr, int width, int height, int mx, int my) {
        int x0 = timeX(width), y = 4;
        int total = TIME_LABEL.length * (BW + 2) + 34;
        ctx.fill(x0 - 3, y - 2, x0 + total + 1, y + BH + 2, 0xB0101018);
        ctx.drawTextWithShadow(tr, "Время", x0 + 1, y + 3, 0xFFB0C4DE);
        int act = active();
        for (int i = 0; i < TIME_LABEL.length; i++) {
            int bx = x0 + 34 + i * (BW + 2);
            boolean hov = mx >= bx && mx < bx + BW && my >= y && my < y + BH;
            int bg = i == act ? (i == 0 ? 0xF0C0392B : 0xF0B8860B) : hov ? 0xD0505060 : 0xC0262630;
            ctx.fill(bx, y, bx + BW, y + BH, bg);
            if (i == act) ctx.fill(bx, y + BH - 2, bx + BW, y + BH, 0xFFFFE680);
            ctx.drawCenteredTextWithShadow(tr, TIME_LABEL[i], bx + BW / 2, y + 3, 0xFFFFFFFF);
        }
        // эпоха
        int ex = width / 2 - ERA_W / 2, ey = y + BH + 4;
        boolean hov = mx >= ex && mx < ex + ERA_W && my >= ey && my < ey + 22;
        ctx.fill(ex - 1, ey - 1, ex + ERA_W + 1, ey + 23, 0xB0101018);
        if (hov) ctx.fill(ex, ey, ex + ERA_W, ey + 22, 0x30FFFFFF);
        int age = Math.max(0, Math.min(AGE_RGB.length - 1, HudState.age));
        int col = AGE_RGB[age];
        String name = HudState.agesOn ? WorldAges.NAMES[age] : "Эпохи выключены";
        ctx.drawCenteredTextWithShadow(tr, name, width / 2, ey + 2, 0xFF000000 | col);
        float prog = HudState.agesOn ? 1f - Math.min(1f, HudState.ageLeft / (float) AGE_TOTAL) : 0f;
        int px = ex + 6, pw = ERA_W - 12, py = ey + 14;
        ctx.fill(px, py, px + pw, py + 4, 0xFF202020);
        ctx.fill(px, py, px + (int) (pw * prog), py + 4, 0xFF000000 | col);
        if (hov) {
            int left = HudState.ageLeft;
            ctx.drawTooltip(tr, List.of(
                    Text.literal(name).formatted(Formatting.GOLD),
                    Text.literal(HudState.agesOn ? "До смены эпохи: " + left / 60 + " мин " + left % 60 + " с" : "Цикл эпох отключён").formatted(Formatting.GRAY),
                    Text.literal("ЛКМ или J — выбрать эпоху").formatted(Formatting.DARK_GRAY)), mx, my + 16);
        }
        if (MortalClient.active()) renderMortal(ctx, tr, width, mx, my);
        if (popup) renderPopup(ctx, tr, width, height, mx, my);
    }

    static int[] popupRect(int width, int height) {
        int w = Math.min(width - 20, 3 * 120 + 16), h = 3 * 34 + 28;
        return new int[]{width / 2 - w / 2, bottom() + 6, w, h};
    }

    static void renderPopup(DrawContext ctx, TextRenderer tr, int width, int height, int mx, int my) {
        int[] r = popupRect(width, height);
        ctx.fill(r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] + r[3] + 2, 0xFFB8860B);
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xF0121218);
        ctx.drawCenteredTextWithShadow(tr, "Выбор эпохи мира", r[0] + r[2] / 2, r[1] + 5, 0xFFFFD700);
        int cw = (r[2] - 16) / 3;
        String hoverDesc = null;
        for (int i = 0; i < AGE_POWER.length; i++) {
            Power p = Power.byName(AGE_POWER[i]);
            int cx = r[0] + 8 + (i % 3) * cw, cy = r[1] + 20 + (i / 3) * 34;
            boolean hov = mx >= cx && mx < cx + cw - 4 && my >= cy && my < cy + 30;
            boolean cur = i == HudState.age;
            ctx.fill(cx, cy, cx + cw - 4, cy + 30, cur ? 0xC0403010 : hov ? 0xC0303040 : 0xC01C1C24);
            ctx.fill(cx, cy, cx + 2, cy + 30, 0xFF000000 | AGE_RGB[i]);
            if (p != null) ctx.drawItem(new ItemStack(p.icon), cx + 5, cy + 7);
            String n = WorldAges.NAMES[i].replace("Эпоха ", "");
            ctx.drawTextWithShadow(tr, n, cx + 24, cy + 6, 0xFF000000 | AGE_RGB[i]);
            if (p != null) ctx.drawTextWithShadow(tr, tr.trimToWidth(p.desc, cw - 30), cx + 24, cy + 17, 0xFF9A9AA8);
            if (hov && p != null) hoverDesc = p.desc;
        }
        if (hoverDesc != null) ctx.drawTooltip(tr, Text.literal(hoverDesc), mx, my);
    }

    /** Возвращает имя силы, которую надо применить, "" если клик поглощён, null если мимо. */
    public static String click(double mx, double my, int width, int height) {
        if (popup) {
            int[] r = popupRect(width, height);
            int cw = (r[2] - 16) / 3;
            for (int i = 0; i < AGE_POWER.length; i++) {
                int cx = r[0] + 8 + (i % 3) * cw, cy = r[1] + 20 + (i / 3) * 34;
                if (mx >= cx && mx < cx + cw - 4 && my >= cy && my < cy + 30) {
                    popup = false;
                    return AGE_POWER[i];
                }
            }
            boolean in = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            if (!in) popup = false;
            return "";
        }
        int x0 = timeX(width), y = 4;
        for (int i = 0; i < TIME_LABEL.length; i++) {
            int bx = x0 + 34 + i * (BW + 2);
            if (mx >= bx && mx < bx + BW && my >= y && my < y + BH) {
                if (i == 0) return TIME_POWER[0];
                if (HudState.frozen) return TIME_POWER[0] + ";" + TIME_POWER[i];
                return TIME_POWER[i];
            }
        }
        int ex = width / 2 - ERA_W / 2, ey = y + BH + 4;
        if (mx >= ex && mx < ex + ERA_W && my >= ey && my < ey + 22) {
            popup = true;
            return "";
        }
        return null;
    }
}
