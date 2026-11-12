package com.vladik.godbox.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

/** v1.6: мини-карта (M) — вид сверху, точки королевств, клик = перелёт. */
final class MiniMap {
    static final int SIZE = 128;
    static boolean on;
    private static NativeImageBackedTexture tex;
    private static final Identifier ID = Identifier.of("godbox", "minimap");
    private static int row, cx, cz, ncx, ncz;
    private static boolean ready;

    private MiniMap() {}

    static void tick(MinecraftClient mc) {
        if (!on || mc.world == null || mc.player == null) return;
        if (tex == null) {
            tex = new NativeImageBackedTexture(SIZE, SIZE, true);
            mc.getTextureManager().registerTexture(ID, tex);
        }
        NativeImage img = tex.getImage();
        if (img == null) return;
        ClientWorld w = mc.world;
        if (row == 0) {
            ncx = MathHelper.floor(mc.player.getX());
            ncz = MathHelper.floor(mc.player.getZ());
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int n = 0; n < 32 && row < SIZE; n++, row++) {
            int z = ncz - SIZE / 2 + row;
            for (int i = 0; i < SIZE; i++) {
                int x = ncx - SIZE / 2 + i;
                int color;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    color = 0xFF151515;
                } else {
                    int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    m.set(x, y, z);
                    BlockState s = w.getBlockState(m);
                    MapColor mcol = s.getMapColor(w, m);
                    int yn = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
                    MapColor.Brightness b = y > yn ? MapColor.Brightness.HIGH : y < yn ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    if (!s.getFluidState().isEmpty()) b = MapColor.Brightness.NORMAL;
                    color = mcol == MapColor.CLEAR ? 0xFF000000 : mcol.getRenderColor(b);
                }
                img.setColor(i, row, WorldMap.tint(color, x, z));
            }
        }
        if (row >= SIZE) {
            row = 0;
            cx = ncx;
            cz = ncz;
            ready = true;
        }
        tex.upload();
    }

    static void render(DrawContext ctx, MinecraftClient mc, int x, int y, int s) {
        if (!on || mc.world == null || mc.player == null) return;
        if (tex == null || !ready) {
            ctx.fill(x, y, x + s, y + s, 0xC0000000);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "карта…", x + s / 2, y + s / 2 - 4, 0xFFAAAAAA);
            return;
        }
        ctx.fill(x - 2, y - 2, x + s + 2, y + s + 2, 0xFF3A2A10);
        ctx.drawTexture(ID, x, y, 0, 0, s, s, s, s);
        double k = s / (double) SIZE;
        double ox = cx - SIZE / 2.0, oz = cz - SIZE / 2.0;
        for (Entity e : mc.world.getEntities()) {
            Team t = e.getScoreboardTeam();
            if (t == null || !t.getName().startsWith("godbox_")) continue;
            double px = (e.getX() - ox) * k, pz = (e.getZ() - oz) * k;
            if (px < 1 || pz < 1 || px >= s - 1 || pz >= s - 1) continue;
            Integer rgb = t.getColor().getColorValue();
            int c = 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb);
            int ix = x + (int) px, iz = y + (int) pz;
            ctx.fill(ix - 1, iz - 1, ix + 1, iz + 1, c);
        }
        double px = (mc.player.getX() - ox) * k, pz = (mc.player.getZ() - oz) * k;
        int ix = x + MathHelper.clamp((int) px, 2, s - 2), iz = y + MathHelper.clamp((int) pz, 2, s - 2);
        ctx.fill(ix - 2, iz - 2, ix + 2, iz + 2, 0xFFFFFFFF);
        double yr = Math.toRadians(mc.player.getYaw());
        int dx = (int) (-Math.sin(yr) * 6), dz = (int) (Math.cos(yr) * 6);
        ctx.fill(ix + dx - 1, iz + dz - 1, ix + dx + 1, iz + dz + 1, 0xFFFF4040);
        ctx.drawBorder(x - 1, y - 1, s + 2, s + 2, 0xFFFFD700);
        ctx.drawTextWithShadow(mc.textRenderer, "N", x + s / 2 - 2, y + 2, 0xFFFFFFFF);
    }

    /** Мировые x,z под курсором или null. */
    static int[] click(double mx, double my, int x, int y, int s) {
        if (!on || !ready || mx < x || my < y || mx >= x + s || my >= y + s) return null;
        double k = SIZE / (double) s;
        return new int[]{(int) Math.floor(cx - SIZE / 2.0 + (mx - x) * k), (int) Math.floor(cz - SIZE / 2.0 + (my - y) * k)};
    }
}
