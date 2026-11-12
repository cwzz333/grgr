package com.vladik.godbox;

import com.vladik.godbox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GodManager {
    private static final Map<UUID, GameMode> PREVIOUS = new HashMap<>();

    private GodManager() {}

    public static boolean isGod(ServerPlayerEntity p) {
        return PREVIOUS.containsKey(p.getUuid());
    }

    public static boolean isGodEntity(Entity e) {
        return e instanceof ServerPlayerEntity sp && isGod(sp);
    }

    public static void setGod(ServerPlayerEntity p, boolean on) {
        ServerWorld w = p.getServerWorld();
        if (on && !isGod(p)) {
            if (!(p.hasPermissionLevel(2) || p.getServer().isHost(p.getGameProfile()))) {
                p.sendMessage(Text.literal("Нужны права оператора, чтобы стать богом").formatted(Formatting.RED), false);
                return;
            }
            PREVIOUS.put(p.getUuid(), p.interactionManager.getGameMode());
            p.changeGameMode(GameMode.SPECTATOR);
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
            p.networkHandler.requestTeleport(p.getX(), top + 45, p.getZ(), p.getYaw(), 90f);
            ServerPlayNetworking.send(p, new Payloads.State(true));
            p.sendMessage(Text.literal("Ты бог. Esc или G — вернуться.").formatted(Formatting.GOLD), true);
        } else if (!on && isGod(p)) {
            GameMode prev = PREVIOUS.remove(p.getUuid());
            p.changeGameMode(prev == null ? GameMode.SURVIVAL : prev);
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
            p.networkHandler.requestTeleport(p.getX(), top + 1, p.getZ(), p.getYaw(), 0f);
            p.fallDistance = 0;
            ServerPlayNetworking.send(p, new Payloads.State(false));
        }
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        if (!isGod(p)) return;
        GameMode prev = PREVIOUS.remove(p.getUuid());
        p.changeGameMode(prev == null ? GameMode.SURVIVAL : prev);
        int top = p.getServerWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
        p.refreshPositionAfterTeleport(p.getX(), top + 1, p.getZ());
        p.fallDistance = 0;
    }

    public static void clear() {
        PREVIOUS.clear();
    }
}
