package com.vladik.godbox.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** v3.1: пакеты режима «Смертный бог». */
public final class MortalPayloads {
    private MortalPayloads() {}

    /** server -> client: запас божьей силы, испытание и его ход; scale — множитель цен в процентах. */
    public record MortalHud(boolean on, int pool, int cap, String title, String progress, int state, int scale, int perMin) implements CustomPayload {
        public static final CustomPayload.Id<MortalHud> ID = new CustomPayload.Id<>(Identifier.of("godbox", "mortal_hud"));
        public static final PacketCodec<RegistryByteBuf, MortalHud> CODEC = PacketCodec.of(MortalHud::write, MortalHud::new);

        private MortalHud(RegistryByteBuf b) {
            this(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readString(), b.readString(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        private void write(RegistryByteBuf b) {
            b.writeBoolean(on);
            b.writeVarInt(pool);
            b.writeVarInt(cap);
            b.writeString(title);
            b.writeString(progress);
            b.writeVarInt(state);
            b.writeVarInt(scale);
            b.writeVarInt(perMin);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
