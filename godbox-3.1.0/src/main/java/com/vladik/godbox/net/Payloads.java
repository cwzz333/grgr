package com.vladik.godbox.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class Payloads {
    private Payloads() {}

    /** client -> server: включить/выключить режим бога */
    public record Toggle(boolean on) implements CustomPayload {
        public static final CustomPayload.Id<Toggle> ID = new CustomPayload.Id<>(Identifier.of("godbox", "toggle"));
        public static final PacketCodec<RegistryByteBuf, Toggle> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, Toggle::on, Toggle::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client: текущее состояние режима бога */
    public record State(boolean on) implements CustomPayload {
        public static final CustomPayload.Id<State> ID = new CustomPayload.Id<>(Identifier.of("godbox", "state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, State::on, State::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client: текст статистики королевств */
    public record Stats(boolean open, String text) implements CustomPayload {
        public static final CustomPayload.Id<Stats> ID = new CustomPayload.Id<>(Identifier.of("godbox", "stats"));
        public static final PacketCodec<RegistryByteBuf, Stats> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, Stats::open,
                PacketCodecs.STRING, Stats::text,
                Stats::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Borders(String data) implements CustomPayload {
        public static final CustomPayload.Id<Borders> ID = new CustomPayload.Id<>(Identifier.of("godbox", "borders"));
        public static final PacketCodec<RegistryByteBuf, Borders> CODEC = PacketCodec.tuple(PacketCodecs.STRING, Borders::data, Borders::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** client -> server: применить силу. v3.0: сила передаётся по стабильному имени, а не по ordinal. */
    public record Use(String power, BlockPos pos, int brush, boolean first) implements CustomPayload {
        public static final CustomPayload.Id<Use> ID = new CustomPayload.Id<>(Identifier.of("godbox", "use3"));
        public static final PacketCodec<RegistryByteBuf, Use> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Use::power,
                BlockPos.PACKET_CODEC, Use::pos,
                PacketCodecs.VAR_INT, Use::brush,
                PacketCodecs.BOOL, Use::first,
                Use::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client (v3.0): скорость времени, пауза, текущая эпоха мира и сколько секунд до смены; v3.1: маска законов. */
    public record Hud(float rate, boolean frozen, int age, int ageLeft, boolean agesOn, long laws) implements CustomPayload {
        public static final CustomPayload.Id<Hud> ID = new CustomPayload.Id<>(Identifier.of("godbox", "hud"));
        public static final PacketCodec<RegistryByteBuf, Hud> CODEC = PacketCodec.tuple(
                PacketCodecs.FLOAT, Hud::rate,
                PacketCodecs.BOOL, Hud::frozen,
                PacketCodecs.VAR_INT, Hud::age,
                PacketCodecs.VAR_INT, Hud::ageLeft,
                PacketCodecs.BOOL, Hud::agesOn,
                PacketCodecs.VAR_LONG, Hud::laws,
                Hud::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
