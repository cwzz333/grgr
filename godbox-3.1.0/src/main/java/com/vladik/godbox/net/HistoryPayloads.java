package com.vladik.godbox.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** v3.1: пакеты истории мира (повтор изменения границ на карте). */
public final class HistoryPayloads {
    private HistoryPayloads() {}

    /** client -> server: прислать историю мира */
    public record HistoryRequest() implements CustomPayload {
        public static final CustomPayload.Id<HistoryRequest> ID = new CustomPayload.Id<>(Identifier.of("godbox", "history_req"));
        public static final PacketCodec<RegistryByteBuf, HistoryRequest> CODEC = PacketCodec.unit(new HistoryRequest());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client: часть истории (index из total), кадры разделены переводом строки */
    public record HistoryData(int index, int total, String data) implements CustomPayload {
        public static final CustomPayload.Id<HistoryData> ID = new CustomPayload.Id<>(Identifier.of("godbox", "history_data"));
        public static final PacketCodec<RegistryByteBuf, HistoryData> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, HistoryData::index,
                PacketCodecs.VAR_INT, HistoryData::total,
                PacketCodecs.string(262144), HistoryData::data,
                HistoryData::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client: открыть карту мира в режиме истории */
    public record HistoryOpen() implements CustomPayload {
        public static final CustomPayload.Id<HistoryOpen> ID = new CustomPayload.Id<>(Identifier.of("godbox", "history_open"));
        public static final PacketCodec<RegistryByteBuf, HistoryOpen> CODEC = PacketCodec.unit(new HistoryOpen());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
