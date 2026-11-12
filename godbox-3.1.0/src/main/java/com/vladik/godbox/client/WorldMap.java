package com.vladik.godbox.client;

import com.vladik.godbox.net.HistoryPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * v3.0: карта мира (N). Рельеф из загруженных чанков, территории королевств, названия городов,
 * столицы с короной, линии войн и союзов, сетка координат, роза ветров, масштаб,
 * зум колесом и перетаскивание мышью.
 */
final class WorldMap {
    static boolean on;

    record Zone(int x, int z, int r, int rgb, String name, boolean cap, String kingdom, int era) {}

    static final List<Zone> ZONES = new ArrayList<>();
    static final List<int[]> WARS = new ArrayList<>();
    static final List<int[]> ALLIES = new ArrayList<>();
    static final List<String> LEGEND = new ArrayList<>();
    static final List<Integer> LEGEND_RGB = new ArrayList<>();

    private static final int TEX = 192;
    private static final Identifier ID = Identifier.of("godbox", "worldmap");
    private static NativeImageBackedTexture tex;
    private static int texRow, texKey, texDone = -1;

    private static double cX, cZ, span = 512;
    private static boolean fitted;
    private static int lx, ly, ls;
    private static double lminX, lminZ, lscale;

    // ---------------- v3.1: история мира (повтор границ) ----------------
    record Frame(long year, long day, List<Zone> zones, List<String> legend, List<Integer> legendRgb, List<String> notes) {}

    static boolean replay;
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final List<Frame> INCOMING = new ArrayList<>();
    private static final int[] BASE = new int[TEX * TEX];
    private static int tintKey;
    private static int fi, speed = 1;
    private static boolean playing, loading, unsupported, scrubbing;
    private static double playAcc;
    private static long lastRender, lastAsk, capSince;
    private static List<String> caps = List.of();
    private static int capFrame = -1;
    /** Панель под картой: кнопка «История», пуск, скорость, шкала (координаты последней отрисовки). */
    private static int barY = -100, hbX0, hbX1, pbX0, pbX1, spX0, spX1, sx0, sx1;

    private WorldMap() {}

    // ---------------- данные с сервера ----------------
    static void set(String d) {
        List<Zone> zs = new ArrayList<>();
        WARS.clear();
        ALLIES.clear();
        LEGEND.clear();
        LEGEND_RGB.clear();
        for (String line : d.split("\n")) {
            String[] a = line.split("\\|", -1);
            try {
                if (a[0].equals("T") && a.length >= 7)
                    zs.add(new Zone(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), a[5],
                            a[6].equals("1"), a.length > 7 ? a[7] : "", a.length > 8 ? Integer.parseInt(a[8]) : 0));
                else if ((a[0].equals("W") || a[0].equals("A")) && a.length >= 5)
                    (a[0].equals("W") ? WARS : ALLIES).add(new int[]{Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])});
                else if (a[0].equals("L") && a.length >= 3) {
                    LEGEND_RGB.add(Integer.parseInt(a[1]));
                    LEGEND.add(a[2]);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (!zs.equals(ZONES)) {
            ZONES.clear();
            ZONES.addAll(zs);
            texKey = 0;
        }
    }

    /** RGB владельца точки или -1. */
    static int own(int x, int z) {
        return own(ZONES, x, z);
    }

    private static int own(List<Zone> zones, int x, int z) {
        int best = -1;
        double bd = 1.0;
        for (Zone zn : zones) {
            double dx = x - zn.x, dz = z - zn.z;
            double q = (dx * dx + dz * dz) / ((double) zn.r * zn.r);
            if (q < bd) { bd = q; best = zn.rgb; }
        }
        return best;
    }

    static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    static int mix(int a, int b, double k) {
        int r = (int) ((a >> 16 & 0xFF) * (1 - k) + (b >> 16 & 0xFF) * k);
        int g = (int) ((a >> 8 & 0xFF) * (1 - k) + (b >> 8 & 0xFF) * k);
        int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Окраска пикселя (формат ABGR) по территории — используется и мини-картой. */
    static int tint(int color, int x, int z) {
        return tint(color, x, z, 1);
    }

    static int tint(int color, int x, int z, int step) {
        return tint(ZONES, color, x, z, step);
    }

    private static int tint(List<Zone> zones, int color, int x, int z, int step) {
        if (zones.isEmpty()) return color;
        int o = own(zones, x, z);
        if (o < 0) return color;
        int c = abgr(o);
        if (own(zones, x - step, z) != o || own(zones, x + step, z) != o || own(zones, x, z - step) != o || own(zones, x, z + step) != o) return c;
        return mix(color, c, 0.32);
    }

    // ---------------- управление видом ----------------
    static void center(double px, double pz) {
        double minX = px - 96, maxX = px + 96, minZ = pz - 96, maxZ = pz + 96;
        for (Zone zn : ZONES) {
            minX = Math.min(minX, zn.x - zn.r);
            maxX = Math.max(maxX, zn.x + zn.r);
            minZ = Math.min(minZ, zn.z - zn.r);
            maxZ = Math.max(maxZ, zn.z + zn.r);
        }
        cX = (minX + maxX) / 2;
        cZ = (minZ + maxZ) / 2;
        span = MathHelper.clamp(Math.max(maxX - minX, maxZ - minZ) * 1.15, 96, 8192);
        fitted = true;
    }

    static void scroll(double mx, double my, double v) {
        if (ls == 0) return;
        // колесо над шкалой истории листает кадры
        if (replay && FRAMES.size() >= 2 && my >= barY - 3) {
            playing = false;
            go(fi + (v > 0 ? -1 : 1));
            return;
        }
        double wx = lminX + (mx - lx) / lscale, wz = lminZ + (my - ly) / lscale;
        boolean in = mx >= lx && my >= ly && mx < lx + ls && my < ly + ls;
        double ns = MathHelper.clamp(span * (v > 0 ? 0.8 : 1.25), 48, 8192);
        if (in) {
            double k = ns / span;
            cX = wx + (cX - wx) * k;
            cZ = wz + (cZ - wz) * k;
        }
        span = ns;
    }

    static void drag(double dx, double dy) {
        if (lscale <= 0 || scrubbing) return;
        cX -= dx / lscale;
        cZ -= dy / lscale;
    }

    // ---------------- рельеф ----------------
    private static void bake(MinecraftClient mc, double minX, double minZ, List<Zone> zones) {
        if (tex == null) {
            tex = new NativeImageBackedTexture(TEX, TEX, true);
            mc.getTextureManager().registerTexture(ID, tex);
        }
        NativeImage img = tex.getImage();
        if (img == null) return;
        int key = (int) (Math.round(minX) * 31 + Math.round(minZ)) * 31 + (int) Math.round(span) + (replay ? 0 : ZONES.hashCode() * 17);
        if (key != texKey) {
            texKey = key;
            texRow = 0;
        }
        double bpp = span / TEX;
        int step = Math.max(1, (int) bpp);
        // v3.1: при смене территорий (кадр истории) рельеф не перерисовываем — только перекрашиваем готовые строки
        int zk = zones.hashCode() * 17 + 1;
        boolean retint = zk != tintKey && texRow > 0;
        tintKey = zk;
        if (retint) {
            for (int row = 0; row < texRow; row++) {
                int z = MathHelper.floor(minZ + (row + 0.5) * bpp);
                for (int i = 0; i < TEX; i++)
                    img.setColor(i, row, tint(zones, BASE[row * TEX + i], MathHelper.floor(minX + (i + 0.5) * bpp), z, step));
            }
        }
        if (texRow >= TEX) {
            if (retint) tex.upload();
            return;
        }
        ClientWorld w = mc.world;
        BlockPos.Mutable m = new BlockPos.Mutable();
        long until = System.nanoTime() + 4_000_000L;
        for (; texRow < TEX && System.nanoTime() < until; texRow++) {
            int z = MathHelper.floor(minZ + (texRow + 0.5) * bpp);
            for (int i = 0; i < TEX; i++) {
                int x = MathHelper.floor(minX + (i + 0.5) * bpp);
                int color;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    boolean chk = (((x >> 6) + (z >> 6)) & 1) == 0;
                    color = chk ? 0xFF2A2218 : 0xFF241D14;
                } else {
                    int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    m.set(x, y, z);
                    BlockState s = w.getBlockState(m);
                    MapColor mcol = s.getMapColor(w, m);
                    int yn = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - step) - 1;
                    MapColor.Brightness b = y > yn ? MapColor.Brightness.HIGH : y < yn ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    if (!s.getFluidState().isEmpty()) b = y > 50 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
                    color = mcol == MapColor.CLEAR ? 0xFF000000 : mcol.getRenderColor(b);
                }
                BASE[texRow * TEX + i] = color;
                img.setColor(i, texRow, tint(zones, color, x, z, step));
            }
        }
        tex.upload();
    }

    // ---------------- отрисовка ----------------
    private static void small(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, float k) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(k, k, 1);
        ctx.drawTextWithShadow(tr, s, -tr.getWidth(s) / 2, 0, color);
        ctx.getMatrices().pop();
    }

    private static void line(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int dash, int x0, int y0, int s) {
        int len = (int) Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (len == 0) return;
        for (int i = 0; i <= len; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) continue;
            int x = x1 + (x2 - x1) * i / len, y = y1 + (y2 - y1) * i / len;
            if (x < x0 || y < y0 || x >= x0 + s || y >= y0 + s) continue;
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    static void render(DrawContext ctx, MinecraftClient mc, int width, int height, int mx, int my) {
        if (mc.player == null || mc.world == null) return;
        if (!fitted) center(mc.player.getX(), mc.player.getZ());
        TextRenderer tr = mc.textRenderer;
        // v3.1: история мира — выбираем кадр; без кадра рисуются живые территории.
        // Локальные ZONES/LEGEND/WARS/ALLIES ниже намеренно заслоняют статические: код отрисовки общий.
        long now = System.currentTimeMillis();
        if (replay && now - lastRender > 1500) exitReplay(); // карту закрывали — открываем её снова живой
        long dt = Math.min(250, Math.max(0, now - lastRender));
        lastRender = now;
        boolean ready = replay && FRAMES.size() >= 2;
        if (ready && scrubbing) {
            if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) go(frameAt(mx));
            else scrubbing = false;
        } else scrubbing = false;
        if (ready && playing) {
            playAcc += dt * 4.0 * speed / 1000.0;
            while (playAcc >= 1) {
                playAcc -= 1;
                if (fi >= FRAMES.size() - 1) { playing = false; playAcc = 0; break; }
                go(fi + 1);
            }
        }
        Frame fr = ready ? FRAMES.get(MathHelper.clamp(fi, 0, FRAMES.size() - 1)) : null;
        final List<Zone> ZONES = fr != null ? fr.zones : WorldMap.ZONES;
        final List<String> LEGEND = fr != null ? fr.legend : WorldMap.LEGEND;
        final List<Integer> LEGEND_RGB = fr != null ? fr.legendRgb : WorldMap.LEGEND_RGB;
        final List<int[]> WARS = fr != null ? List.of() : WorldMap.WARS;
        final List<int[]> ALLIES = fr != null ? List.of() : WorldMap.ALLIES;
        int legendW = width > 560 && !LEGEND.isEmpty() ? 170 : 0;
        int s = Math.min(width - 30 - legendW, height - 70);
        if (s < 80) return;
        int x0 = (width - s - legendW) / 2, y0 = 32;
        double minX = cX - span / 2, minZ = cZ - span / 2;
        double scale = s / span;
        lx = x0; ly = y0; ls = s; lminX = minX; lminZ = minZ; lscale = scale;

        ctx.fill(0, 0, width, height, 0x90000000);
        // рамка «пергамент»
        ctx.fill(x0 - 5, y0 - 5, x0 + s + 5, y0 + s + 5, 0xFF6B4E24);
        ctx.fill(x0 - 3, y0 - 3, x0 + s + 3, y0 + s + 3, 0xFFC9A55C);
        ctx.fill(x0 - 1, y0 - 1, x0 + s + 1, y0 + s + 1, 0xFF3A2A10);
        bake(mc, minX, minZ, ZONES);
        ctx.drawTexture(ID, x0, y0, s, s, 0, 0, TEX, TEX, TEX, TEX);
        if (texRow < TEX) small(ctx, tr, "рисую рельеф… " + texRow * 100 / TEX + "%", x0 + s / 2, y0 + s - 10, 0xFFAAAAAA, 0.75f);

        // сетка координат
        int[] steps = {16, 32, 64, 128, 256, 512, 1024, 2048};
        int gs = steps[steps.length - 1];
        for (int st : steps) if (span / st <= 8) { gs = st; break; }
        for (double gx = Math.ceil(minX / gs) * gs; gx < minX + span; gx += gs) {
            int sx = x0 + (int) ((gx - minX) * scale);
            ctx.fill(sx, y0, sx + 1, y0 + s, 0x26FFFFFF);
            small(ctx, tr, String.valueOf((int) gx), sx, y0 + 2, 0x90FFFFFF, 0.6f);
        }
        for (double gz = Math.ceil(minZ / gs) * gs; gz < minZ + span; gz += gs) {
            int sy = y0 + (int) ((gz - minZ) * scale);
            ctx.fill(x0, sy, x0 + s, sy + 1, 0x26FFFFFF);
            small(ctx, tr, String.valueOf((int) gz), x0 + 12, sy + 2, 0x90FFFFFF, 0.6f);
        }

        // союзы и войны
        for (int[] a : ALLIES)
            line(ctx, x0 + (int) ((a[0] - minX) * scale), y0 + (int) ((a[1] - minZ) * scale), x0 + (int) ((a[2] - minX) * scale), y0 + (int) ((a[3] - minZ) * scale), 0xC060D0FF, 2, x0, y0, s);
        for (int[] a : WARS) {
            int ax = x0 + (int) ((a[0] - minX) * scale), ay = y0 + (int) ((a[1] - minZ) * scale);
            int bx = x0 + (int) ((a[2] - minX) * scale), by = y0 + (int) ((a[3] - minZ) * scale);
            line(ctx, ax, ay, bx, by, 0xFFFF3030, 4, x0, y0, s);
            int mxp = (ax + bx) / 2, myp = (ay + by) / 2;
            if (mxp > x0 && myp > y0 && mxp < x0 + s && myp < y0 + s) {
                ctx.fill(mxp - 6, myp - 5, mxp + 7, myp + 6, 0xD0400000);
                ctx.drawCenteredTextWithShadow(tr, "⚔", mxp, myp - 4, 0xFFFF6060);
            }
        }

        // города
        Zone hov = null;
        double hd = 64;
        boolean names = span <= 1600;
        for (Zone zn : ZONES) {
            int zx = x0 + (int) ((zn.x - minX) * scale), zy = y0 + (int) ((zn.z - minZ) * scale);
            if (zx < x0 - 40 || zy < y0 - 20 || zx > x0 + s + 40 || zy > y0 + s + 20) continue;
            double d = (mx - zx) * (mx - zx) + (my - zy) * (my - zy);
            if (d < hd) { hd = d; hov = zn; }
            if (zx < x0 || zy < y0 || zx >= x0 + s || zy >= y0 + s) continue;
            int col = 0xFF000000 | zn.rgb;
            if (zn.cap) {
                ctx.fill(zx - 4, zy - 4, zx + 5, zy + 5, 0xFF000000);
                ctx.fill(zx - 3, zy - 3, zx + 4, zy + 4, 0xFFFFD700);
                ctx.fill(zx - 2, zy - 2, zx + 3, zy + 3, col);
                String n = "♛ " + zn.name;
                int w = tr.getWidth(n) + 6;
                int ny = Math.max(y0 + 1, zy - 19);
                ctx.fill(zx - w / 2, ny, zx + w / 2, ny + 11, 0xC0101010);
                ctx.fill(zx - w / 2, ny + 10, zx + w / 2, ny + 11, col);
                ctx.drawCenteredTextWithShadow(tr, n, zx, ny + 2, 0xFFFFE680);
                if (!zn.kingdom.isEmpty()) small(ctx, tr, zn.kingdom, zx, zy + 7, col, 0.7f);
            } else {
                ctx.fill(zx - 2, zy - 2, zx + 3, zy + 3, 0xFF000000);
                ctx.fill(zx - 1, zy - 1, zx + 2, zy + 2, col);
                if (names && !zn.name.isEmpty()) {
                    int w = (int) (tr.getWidth(zn.name) * 0.75f) + 4;
                    ctx.fill(zx - w / 2, zy - 11, zx + w / 2, zy - 3, 0x90101010);
                    small(ctx, tr, zn.name, zx, zy - 10, 0xFFEEEEEE, 0.75f);
                }
            }
        }

        // игрок
        int ppx = x0 + (int) ((mc.player.getX() - minX) * scale), ppy = y0 + (int) ((mc.player.getZ() - minZ) * scale);
        if (ppx >= x0 && ppy >= y0 && ppx < x0 + s && ppy < y0 + s) {
            double yr = Math.toRadians(mc.player.getYaw());
            for (int i = 0; i < 7; i++) {
                int ax = ppx + (int) (-Math.sin(yr) * i), az = ppy + (int) (Math.cos(yr) * i);
                ctx.fill(ax - 1, az - 1, ax + 2, az + 2, 0xFFFF4040);
            }
            ctx.fill(ppx - 2, ppy - 2, ppx + 3, ppy + 3, 0xFFFFFFFF);
            small(ctx, tr, "Вы", ppx, ppy + 5, 0xFFFF9090, 0.75f);
        }

        // роза ветров
        int rx = x0 + s - 22, ry = y0 + 22;
        ctx.fill(rx - 15, ry - 15, rx + 16, ry + 16, 0x90101010);
        ctx.fill(rx, ry - 12, rx + 1, ry + 13, 0xFFC9A55C);
        ctx.fill(rx - 12, ry, rx + 13, ry + 1, 0xFFC9A55C);
        ctx.fill(rx - 1, ry - 12, rx + 2, ry - 2, 0xFFFF4040);
        small(ctx, tr, "С", rx + 1, ry - 13 - 1, 0xFFFF6060, 0.6f);
        small(ctx, tr, "Ю", rx + 1, ry + 9, 0xFFFFFFFF, 0.6f);
        small(ctx, tr, "З", rx - 11, ry - 2, 0xFFFFFFFF, 0.6f);
        small(ctx, tr, "В", rx + 12, ry - 2, 0xFFFFFFFF, 0.6f);

        // масштаб
        int[] nice = {10, 25, 50, 100, 250, 500, 1000, 2500};
        int blocks = nice[nice.length - 1];
        for (int nb : nice) if (nb * scale >= 40) { blocks = nb; break; }
        int bw = (int) (blocks * scale);
        int bx0 = x0 + 8, by0 = y0 + s - 14;
        ctx.fill(bx0 - 3, by0 - 10, bx0 + bw + 4, by0 + 6, 0x90101010);
        ctx.fill(bx0, by0, bx0 + bw, by0 + 2, 0xFFFFFFFF);
        ctx.fill(bx0, by0 - 3, bx0 + 1, by0 + 3, 0xFFFFFFFF);
        ctx.fill(bx0 + bw - 1, by0 - 3, bx0 + bw, by0 + 3, 0xFFFFFFFF);
        small(ctx, tr, blocks + " бл.", bx0 + bw / 2, by0 - 9, 0xFFFFFFFF, 0.7f);

        // заголовок
        String title = "Карта мира — колесо: зум, перетаскивание: сдвиг, клик: перелететь, N/Esc: закрыть";
        if (replay) title = "История мира — пробел: пуск/пауза, ←/→ или колесо на шкале: кадры, Esc: назад к карте";
        ctx.drawCenteredTextWithShadow(tr, tr.trimToWidth(title, width - 10), width / 2, y0 - 18, 0xFFFFD700);
        history(ctx, tr, fr, x0, y0, s, mx, my, now);
        if (ZONES.isEmpty() && !replay) ctx.drawCenteredTextWithShadow(tr, "Королевств пока нет — создайте их во вкладке «Королевства»", x0 + s / 2, y0 + s / 2, 0xFFDDDDDD);

        // легенда
        int lgx = legendW > 0 ? x0 + s + 10 : x0 + 4, lgy = legendW > 0 ? y0 : y0 + 40;
        int lgw = legendW > 0 ? legendW - 6 : Math.min(s - 8, 200);
        if (!LEGEND.isEmpty()) {
            int rowH = LEGEND.size() > 6 ? 13 : 24; // v3.1: до 12 королевств — компактные строки
            int shown = LEGEND.size();
            if (legendW == 0) {
                // легенда поверх карты не должна вылезать за неё и закрывать панель истории
                int room = y0 + s - 4 - lgy - 48;
                if (shown * rowH > room) rowH = 13;
                shown = Math.max(0, Math.min(shown, room / rowH));
            }
            int lh = 14 + shown * rowH + 34;
            ctx.fill(lgx - 2, lgy - 2, lgx + lgw + 2, lgy + lh + 2, 0xFF6B4E24);
            ctx.fill(lgx, lgy, lgx + lgw, lgy + lh, 0xE0141210);
            ctx.drawTextWithShadow(tr, "Королевства", lgx + 5, lgy + 4, 0xFFFFD700);
            int yy = lgy + 16;
            for (int i = 0; i < shown; i++) {
                String l = LEGEND.get(i);
                int col = 0xFF000000 | LEGEND_RGB.get(i);
                ctx.fill(lgx + 4, yy, lgx + 7, yy + rowH - 4, col);
                List<net.minecraft.text.OrderedText> ls2 = tr.wrapLines(Text.literal(l), (int) ((lgw - 14) / 0.75f));
                for (int k = 0; k < Math.min(rowH > 13 ? 2 : 1, ls2.size()); k++) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(lgx + 10, yy + k * 9, 0);
                    ctx.getMatrices().scale(0.75f, 0.75f, 1);
                    ctx.drawTextWithShadow(tr, ls2.get(k), 0, 0, k == 0 ? 0xFFFFFFFF : 0xFFAAAAAA);
                    ctx.getMatrices().pop();
                }
                yy += rowH;
            }
            ctx.fill(lgx + 4, yy + 4, lgx + 14, yy + 5, 0xFFFF3030);
            small(ctx, tr, "война", lgx + 34, yy + 1, 0xFFFF8080, 0.7f);
            ctx.fill(lgx + 4, yy + 16, lgx + 14, yy + 17, 0xFF60D0FF);
            small(ctx, tr, "союз", lgx + 32, yy + 13, 0xFF80D8FF, 0.7f);
        }

        // подсказка под курсором
        if (mx >= x0 && my >= y0 && mx < x0 + s && my < y0 + s) {
            int wx = (int) Math.floor(minX + (mx - x0) / scale), wz = (int) Math.floor(minZ + (my - y0) / scale);
            if (hov != null) {
                ctx.drawTooltip(tr, List.of(
                        Text.literal((hov.cap ? "♛ Столица " : "Город ") + hov.name).formatted(Formatting.GOLD),
                        Text.literal(hov.kingdom).withColor(hov.rgb),
                        Text.literal("Эра развития: " + (hov.era + 1) + "   радиус " + hov.r + " бл.").formatted(Formatting.GRAY),
                        Text.literal(hov.x + ", " + hov.z + (replay ? "" : " — клик: перелететь")).formatted(Formatting.DARK_GRAY)), mx, my);
            } else {
                String owner = "ничья земля";
                int o = own(ZONES, wx, wz);
                for (Zone zn : ZONES) if (zn.cap && zn.rgb == o) owner = zn.kingdom.isEmpty() ? zn.name : zn.kingdom;
                String info = wx + ", " + wz + " — " + owner;
                int iw = tr.getWidth(info) + 8;
                ctx.fill(x0 + s / 2 - iw / 2, y0 + s - 30, x0 + s / 2 + iw / 2, y0 + s - 18, 0xC0101010);
                ctx.drawCenteredTextWithShadow(tr, info, x0 + s / 2, y0 + s - 28, 0xFFFFFFFF);
            }
        }
    }

    /** Мировые координаты клика по карте или null. */
    static int[] click(double mx, double my) {
        if (ls > 0 && my >= barY && my < barY + 14) {
            if (mx >= hbX0 && mx < hbX1) {
                if (replay) exitReplay(); else enterReplay();
            } else if (replay && FRAMES.size() >= 2) {
                if (mx >= pbX0 && mx < pbX1) togglePlay();
                else if (mx >= spX0 && mx < spX1) speed = speed >= 4 ? 1 : speed * 2;
                else if (mx >= sx0 - 3 && mx < sx1 + 3 && sx1 > sx0) {
                    scrubbing = true;
                    playing = false;
                    go(frameAt(mx));
                }
            }
            return null;
        }
        if (replay) return null; // в истории клик по карте не переносит камеру
        if (ls == 0 || mx < lx || my < ly || mx >= lx + ls || my >= ly + ls) return null;
        return new int[]{(int) Math.floor(lminX + (mx - lx) / lscale), (int) Math.floor(lminZ + (my - ly) / lscale)};
    }

    // ---------------- v3.1: история мира ----------------
    /** Открыть карту сразу в режиме истории (сила «История мира»). */
    static void openReplay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!on && mc.player != null) center(mc.player.getX(), mc.player.getZ());
        on = true;
        enterReplay();
    }

    private static void enterReplay() {
        replay = true;
        playing = false;
        scrubbing = false;
        fi = 0;
        caps = List.of();
        capFrame = -1;
        long now = System.currentTimeMillis();
        lastRender = now;
        unsupported = !ClientPlayNetworking.canSend(HistoryPayloads.HistoryRequest.ID);
        if (unsupported) return;
        // сервер отвечает не чаще раза в секунду: при быстром повторном входе берём уже полученные кадры
        if (now - lastAsk < 2000) {
            if (!loading) {
                playing = FRAMES.size() >= 2;
                go(0);
            }
            return;
        }
        FRAMES.clear();
        loading = true;
        lastAsk = now;
        ClientPlayNetworking.send(new HistoryPayloads.HistoryRequest());
    }

    private static void exitReplay() {
        replay = false;
        playing = false;
        scrubbing = false;
    }

    /** Клавиши режима истории; true — клавиша обработана. */
    static boolean key(int key) {
        if (!replay) return false;
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> exitReplay();
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> { playing = false; go(fi - 1); }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> { playing = false; go(fi + 1); }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> { playing = false; go(0); }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> { playing = false; go(FRAMES.size() - 1); }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> togglePlay();
            default -> { return false; }
        }
        return true;
    }

    private static void togglePlay() {
        if (FRAMES.size() < 2) return;
        playing = !playing;
        playAcc = 0;
        if (playing && fi >= FRAMES.size() - 1) go(0);
    }

    private static void go(int i) {
        if (FRAMES.isEmpty()) return;
        fi = MathHelper.clamp(i, 0, FRAMES.size() - 1);
        Frame f = FRAMES.get(fi);
        if (!f.notes.isEmpty() && capFrame != fi) {
            caps = f.notes;
            capFrame = fi;
            capSince = System.currentTimeMillis();
        }
    }

    private static int frameAt(double mx) {
        if (sx1 <= sx0 || FRAMES.size() < 2) return 0;
        return (int) Math.round(MathHelper.clamp((mx - sx0) / (double) (sx1 - sx0), 0, 1) * (FRAMES.size() - 1));
    }

    /** Часть истории с сервера: кадры по строке, последняя часть запускает повтор. */
    static void history(int index, int total, String data) {
        if (index == 0) INCOMING.clear();
        for (String line : data.split("\n")) {
            Frame f = parse(line);
            if (f != null) INCOMING.add(f);
        }
        if (index < total - 1) return;
        FRAMES.clear();
        FRAMES.addAll(INCOMING);
        INCOMING.clear();
        loading = false;
        fi = 0;
        caps = List.of();
        capFrame = -1;
        playAcc = 0;
        playing = replay && FRAMES.size() >= 2;
        go(0);
        if (replay && FRAMES.size() >= 2) fitHistory();
    }

    /** "time|year|day|n|королевство…|летопись…", королевство = "id;rgb;pop;flags;era;title;x,z,r,name;…". */
    private static Frame parse(String line) {
        String[] a = line.split("\\|", -1);
        try {
            if (a.length < 4) return null;
            int n = Integer.parseInt(a[3]);
            if (n < 0 || 4 + n > a.length) return null;
            List<Zone> zs = new ArrayList<>();
            List<String> leg = new ArrayList<>();
            List<Integer> legRgb = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String[] k = a[4 + i].split(";", -1);
                if (k.length < 7) continue;
                int rgb = Integer.parseInt(k[1]), pop = Integer.parseInt(k[2]), flags = Integer.parseInt(k[3]), era = Integer.parseInt(k[4]);
                String title = k[5].isEmpty() ? k[0] : k[5];
                int cities = 0;
                for (int j = 6; j < k.length; j++) {
                    String[] c = k[j].split(",", -1);
                    if (c.length < 3) continue;
                    zs.add(new Zone(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Math.max(1, Integer.parseInt(c[2])), rgb, c.length > 3 ? c[3] : "", cities == 0, title, era));
                    cities++;
                }
                if (cities == 0) continue;
                legRgb.add(rgb);
                leg.add(title + ": эра " + (era + 1) + ", жителей " + pop + ", городов " + cities
                        + ((flags & 2) != 0 ? ", вассал" : (flags & 4) != 0 ? ", ИМПЕРИЯ" : "") + ((flags & 1) != 0 ? ", ⚔ война" : ""));
            }
            List<String> notes = new ArrayList<>();
            for (int i = 4 + n; i < a.length; i++) if (!a[i].isBlank()) notes.add(a[i]);
            return new Frame(Long.parseLong(a[1]), Long.parseLong(a[2]), List.copyOf(zs), List.copyOf(leg), List.copyOf(legRgb), List.copyOf(notes));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Расширяет вид так, чтобы поместились территории всех кадров. */
    private static void fitHistory() {
        double minX = cX - span / 2, maxX = cX + span / 2, minZ = cZ - span / 2, maxZ = cZ + span / 2;
        double x1 = minX, x2 = maxX, z1 = minZ, z2 = maxZ;
        for (Frame f : FRAMES)
            for (Zone zn : f.zones) {
                x1 = Math.min(x1, zn.x - zn.r);
                x2 = Math.max(x2, zn.x + zn.r);
                z1 = Math.min(z1, zn.z - zn.r);
                z2 = Math.max(z2, zn.z + zn.r);
            }
        if (x1 == minX && x2 == maxX && z1 == minZ && z2 == maxZ) return;
        cX = (x1 + x2) / 2;
        cZ = (z1 + z2) / 2;
        span = MathHelper.clamp(Math.max(x2 - x1, z2 - z1) * 1.05, 96, 8192);
    }

    private static void button(DrawContext ctx, TextRenderer tr, int x0, int x1, String label, boolean hover, boolean active) {
        ctx.fill(x0, barY, x1, barY + 14, active ? 0xFFFFD700 : hover ? 0xFFC9A55C : 0xFF6B4E24);
        ctx.fill(x0 + 1, barY + 1, x1 - 1, barY + 13, active ? 0xFF4A3A14 : hover ? 0xFF33291A : 0xF0141210);
        ctx.drawCenteredTextWithShadow(tr, label, (x0 + x1) / 2, barY + 3, active || hover ? 0xFFFFE680 : 0xFFDDDDDD);
    }

    /** Панель под картой и надписи режима истории. */
    private static void history(DrawContext ctx, TextRenderer tr, Frame fr, int x0, int y0, int s, int mx, int my, long now) {
        barY = y0 + s + 8;
        // подложка: под картой теперь нижняя панель сил, без фона кнопки и шкала с ней сливаются
        ctx.fill(x0 - 5, y0 + s + 5, x0 + s + 5, barY + 16, 0xF0141210);
        boolean row = my >= barY && my < barY + 14;
        String hl = replay ? "✖ Карта" : "⏳ История";
        hbX0 = x0 - 5;
        hbX1 = hbX0 + tr.getWidth(hl) + 12;
        button(ctx, tr, hbX0, hbX1, hl, row && mx >= hbX0 && mx < hbX1, replay);
        if (!replay) {
            if (row && mx >= hbX0 && mx < hbX1)
                ctx.drawTooltip(tr, List.of(Text.literal("История мира").formatted(Formatting.GOLD),
                        Text.literal("Показать, как менялись границы королевств").formatted(Formatting.GRAY)), mx, my);
            return;
        }
        if (fr == null) {
            String m = unsupported ? "Сервер не поддерживает историю мира" : loading ? "Загружаю историю мира…" : "История ещё не записана…";
            String m2 = unsupported || loading ? "" : "Мир запоминает границы раз в минуту, пока живо хотя бы одно королевство";
            int w = Math.min(s - 8, Math.max(tr.getWidth(m), (int) (tr.getWidth(m2) * 0.75f)) + 16);
            int cy = y0 + s / 2;
            ctx.fill(x0 + s / 2 - w / 2, cy - 14, x0 + s / 2 + w / 2, cy + 16, 0xD0101010);
            ctx.drawCenteredTextWithShadow(tr, m, x0 + s / 2, cy - 9, 0xFFFFE680);
            if (!m2.isEmpty()) small(ctx, tr, tr.trimToWidth(m2, (int) ((s - 16) / 0.75f)), x0 + s / 2, cy + 4, 0xFFCCCCCC, 0.75f);
            return;
        }
        int n = FRAMES.size();
        // пуск/пауза и скорость
        pbX0 = hbX1 + 3;
        pbX1 = pbX0 + 16;
        button(ctx, tr, pbX0, pbX1, playing ? "⏸" : "▶", row && mx >= pbX0 && mx < pbX1, playing);
        spX0 = pbX1 + 3;
        spX1 = spX0 + 22;
        button(ctx, tr, spX0, spX1, "×" + speed, row && mx >= spX0 && mx < spX1, false);
        // шкала времени
        sx0 = spX1 + 9;
        sx1 = x0 + s;
        if (sx1 - sx0 >= 16) {
            ctx.fill(sx0 - 5, barY, sx1 + 5, barY + 14, 0xFF6B4E24);
            ctx.fill(sx0 - 4, barY + 1, sx1 + 4, barY + 13, 0xF0141210);
            int kx = sx0 + (int) ((sx1 - sx0) * (fi / (double) (n - 1)));
            ctx.fill(sx0, barY + 6, sx1, barY + 8, 0xFF4A3A20);
            ctx.fill(sx0, barY + 6, kx, barY + 8, 0xFFC9A55C);
            // отметки кадров с событиями летописи
            if (n <= (sx1 - sx0) / 2)
                for (int i = 0; i < n; i++)
                    if (!FRAMES.get(i).notes.isEmpty()) {
                        int tx = sx0 + (int) ((sx1 - sx0) * (i / (double) (n - 1)));
                        ctx.fill(tx, barY + 3, tx + 1, barY + 5, 0xFFFFD700);
                    }
            ctx.fill(kx - 3, barY + 1, kx + 3, barY + 13, 0xFF000000);
            ctx.fill(kx - 2, barY + 2, kx + 2, barY + 12, scrubbing ? 0xFFFFD700 : 0xFFFFFFFF);
        }
        // дата кадра
        String date = "Год " + fr.year + " · день " + fr.day;
        String sub = "кадр " + (fi + 1) + " из " + n + (playing ? "  ▶ ×" + speed : "  пауза");
        int dw = Math.max(tr.getWidth(date), (int) (tr.getWidth(sub) * 0.7f)) + 12;
        int dx = x0 + 4, dy = y0 + 10;
        ctx.fill(dx, dy, dx + dw, dy + 23, 0xD0101010);
        ctx.fill(dx, dy + 22, dx + dw, dy + 23, 0xFFC9A55C);
        ctx.drawTextWithShadow(tr, date, dx + 6, dy + 3, 0xFFFFE680);
        small(ctx, tr, sub, dx + dw / 2, dy + 14, 0xFFBBBBBB, 0.7f);
        // события летописи: проявляются и гаснут
        long age = now - capSince;
        double al = Math.min(1.0, age / 300.0);
        if (capFrame != fi && age > 3500) al = Math.max(0, 1 - (age - 3500) / 800.0);
        int alpha = (int) (al * 255);
        if (!caps.isEmpty() && alpha >= 16) {
            int cw = Math.min(s - 60, 250);
            List<net.minecraft.text.OrderedText> lines = new ArrayList<>();
            for (String c : caps) {
                List<net.minecraft.text.OrderedText> wl = tr.wrapLines(Text.literal(c), (int) ((cw - 8) / 0.75f));
                for (int k = 0; k < Math.min(2, wl.size()); k++) lines.add(wl.get(k));
            }
            int cy = dy + 27;
            ctx.fill(dx, cy, dx + cw, cy + lines.size() * 8 + 5, (alpha * 3 / 4) << 24 | 0x101010);
            for (int k = 0; k < lines.size(); k++) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(dx + 4, cy + 3 + k * 8, 0);
                ctx.getMatrices().scale(0.75f, 0.75f, 1);
                ctx.drawTextWithShadow(tr, lines.get(k), 0, 0, alpha << 24 | 0xFFFFFF);
                ctx.getMatrices().pop();
            }
        }
        // подсказка над шкалой: какой год под курсором
        if (row && mx >= sx0 - 3 && mx < sx1 + 3 && sx1 - sx0 >= 16) {
            Frame h = FRAMES.get(frameAt(mx));
            ctx.drawTooltip(tr, Text.literal("Год " + h.year + " · день " + h.day), mx, my - 12);
        }
    }
}
