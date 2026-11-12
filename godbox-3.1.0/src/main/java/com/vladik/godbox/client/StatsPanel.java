package com.vladik.godbox.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Окно статистики королевств и летописи (клавиша K). */
public final class StatsPanel {
    public static String text = "";
    public static boolean on;
    public static int timer;

    private StatsPanel() {}

    /** v3.1: курсор над открытым окном статистики (клики сквозь него не проходят). */
    public static boolean inside(double mx, double my, int width, int height) {
        return on && mx >= 20 && mx < width - 20 && my >= 34 && my < height - 30;
    }

    public static void render(DrawContext ctx, TextRenderer tr, int width, int height) {
        if (!on) return;
        int x0 = 20, y0 = 34, x1 = width - 20, y1 = height - 30;
        ctx.fill(x0, y0, x1, y1, 0xD0101018);
        ctx.drawBorder(x0, y0, x1 - x0, y1 - y0, 0xFFC8A040);
        ctx.drawText(tr, Text.literal("\u00a76\u00a7lСтатистика королевств"), x0 + 8, y0 + 6, 0xFFFFFF, true);
        ctx.drawText(tr, Text.literal("\u00a78K — закрыть"), x1 - 70, y0 + 6, 0xFFFFFF, false);
        String t = text == null ? "" : text;
        int sep = t.indexOf('\u0002');
        String kings = sep >= 0 ? t.substring(0, sep) : t;
        String chron = sep >= 0 ? t.substring(sep + 1) : "";
        String[] blocks = kings.split("\u0001");
        // королевств может быть до 12: по 4 на странице, страницы листаются сами каждые 8 секунд
        int pages = Math.max(1, (blocks.length + 3) / 4);
        int page = (timer / 160) % pages;
        int from = page * 4;
        int cols = Math.max(1, Math.min(blocks.length - from, 4));
        int colW = (x1 - x0 - 16) / cols;
        int chronH = Math.min(110, (y1 - y0) / 3);
        int bottom = y1 - chronH - 6;
        if (pages > 1) {
            String pg = "\u00a77стр. " + (page + 1) + "/" + pages + " \u00a78· королевств: " + blocks.length;
            ctx.drawText(tr, Text.literal(pg), x1 - 78 - tr.getWidth(Text.literal(pg)), y0 + 6, 0xFFFFFF, false);
        }
        for (int n = 0; n < cols; n++) {
            int i = from + n;
            int cx = x0 + 8 + n * colW, cy = y0 + 22;
            for (String line : blocks[i].split("\n")) {
                List<OrderedText> wrapped = tr.wrapLines(Text.literal(line), colW - 8);
                for (OrderedText o : wrapped) {
                    if (cy > bottom - 9) break;
                    ctx.drawText(tr, o, cx, cy, 0xFFFFFF, true);
                    cy += 10;
                }
                cy += 1;
            }
        }
        int cy = bottom + 2;
        ctx.fill(x0 + 6, cy, x1 - 6, cy + 1, 0x80C8A040);
        cy += 4;
        ctx.drawText(tr, Text.literal("\u00a76Летопись мира"), x0 + 8, cy, 0xFFFFFF, true);
        cy += 12;
        for (String line : chron.split("\n")) {
            for (OrderedText o : tr.wrapLines(Text.literal(line), x1 - x0 - 20)) {
                if (cy > y1 - 10) return;
                ctx.drawText(tr, o, x0 + 8, cy, 0xFFFFFF, false);
                cy += 10;
            }
        }
    }
}
