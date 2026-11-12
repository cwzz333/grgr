package com.vladik.godbox.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** v3.0: контур кисти прямо в мире под курсором (форма и размер кисти). */
public final class BrushPreview {
    private BrushPreview() {}

    static boolean inside(int dx, int dz, int r, int shape) {
        int d2 = dx * dx + dz * dz;
        return switch (shape) {
            case 1 -> Math.abs(dx) <= r && Math.abs(dz) <= r;
            case 2 -> d2 <= r * r + r && d2 >= (r - 1) * (r - 1);
            default -> d2 <= r * r + r;
        };
    }

    public static void render(WorldRenderContext ctx) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!(mc.currentScreen instanceof GodScreen) || mc.world == null) return;
            BlockPos c = GodScreen.previewPos();
            VertexConsumerProvider vcp = ctx.consumers();
            MatrixStack m = ctx.matrixStack();
            if (c == null || vcp == null || m == null) return;
            int r = Math.max(0, GodScreen.previewRadius());
            int shape = GodScreen.previewShape();
            int rgb = GodScreen.previewColor();
            float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cbl = (rgb & 255) / 255f;
            Vec3d cam = ctx.camera().getPos();
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
            m.push();
            m.translate(-cam.x, -cam.y, -cam.z);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!inside(dx, dz, r, shape)) continue;
                    boolean edge = !inside(dx + 1, dz, r, shape) || !inside(dx - 1, dz, r, shape)
                            || !inside(dx, dz + 1, r, shape) || !inside(dx, dz - 1, r, shape);
                    boolean center = dx == 0 && dz == 0;
                    if (!edge && !center) continue;
                    int x = c.getX() + dx, z = c.getZ() + dz;
                    int y = r == 0 ? c.getY() + 1 : mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                    Box b = new Box(x + 0.02, y - 0.98, z + 0.02, x + 0.98, y + 0.04, z + 0.98);
                    if (center) WorldRenderer.drawBox(m, vc, b.expand(0.03), 1f, 0.84f, 0f, 1f);
                    else WorldRenderer.drawBox(m, vc, b, cr, cg, cbl, 0.85f);
                }
            }
            m.pop();
            if (vcp instanceof VertexConsumerProvider.Immediate im) im.draw(RenderLayer.getLines());
        } catch (Exception ignored) {
        }
    }
}
