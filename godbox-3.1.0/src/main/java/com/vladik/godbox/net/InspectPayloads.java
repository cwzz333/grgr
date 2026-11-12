package com.vladik.godbox.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** v3.1: осмотр существа — профиль, родословная и камера слежения. */
public final class InspectPayloads {
    private InspectPayloads() {}

    /** Родство узла древа относительно осматриваемого. */
    public static final int GRAND = 0, PARENT = 1, SELF = 2, SIBLING = 3, CHILD = 4;

    /** Узел древа: link — к кому тянется линия (для деда — его ребёнок-родитель). died = -1 — жив. */
    public record Node(UUID id, String name, int rel, UUID link, boolean alive, int born, int died, int rgb, boolean royal) {
        static Node read(RegistryByteBuf b) {
            return new Node(b.readUuid(), b.readString(), b.readVarInt(), b.readUuid(), b.readBoolean(),
                    b.readInt(), b.readInt(), b.readInt(), b.readBoolean());
        }

        void write(RegistryByteBuf b) {
            b.writeUuid(id);
            b.writeString(name);
            b.writeVarInt(rel);
            b.writeUuid(link);
            b.writeBoolean(alive);
            b.writeInt(born);
            b.writeInt(died);
            b.writeInt(rgb);
            b.writeBoolean(royal);
        }
    }

    /**
     * server -> client: профиль существа. entityId = -1, если существо мертво или не загружено;
     * kingdom пустой — без королевства; lines — строки «метка: значение»; year — текущий год мира.
     */
    public record Profile(int entityId, UUID id, String name, String kingdom, int rgb, boolean alive, boolean following,
                          int year, List<String> lines, List<Node> tree) implements CustomPayload {
        public static final CustomPayload.Id<Profile> ID = new CustomPayload.Id<>(Identifier.of("godbox", "inspect_profile"));
        public static final PacketCodec<RegistryByteBuf, Profile> CODEC = CustomPayload.codecOf(Profile::write, Profile::read);

        static Profile read(RegistryByteBuf b) {
            int entityId = b.readInt();
            UUID id = b.readUuid();
            String name = b.readString();
            String kingdom = b.readString();
            int rgb = b.readInt();
            boolean alive = b.readBoolean();
            boolean following = b.readBoolean();
            int year = b.readInt();
            int n = Math.min(b.readVarInt(), 256);
            List<String> lines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) lines.add(b.readString());
            n = Math.min(b.readVarInt(), 256);
            List<Node> tree = new ArrayList<>(n);
            for (int i = 0; i < n; i++) tree.add(Node.read(b));
            return new Profile(entityId, id, name, kingdom, rgb, alive, following, year, lines, tree);
        }

        void write(RegistryByteBuf b) {
            b.writeInt(entityId);
            b.writeUuid(id);
            b.writeString(name);
            b.writeString(kingdom);
            b.writeInt(rgb);
            b.writeBoolean(alive);
            b.writeBoolean(following);
            b.writeInt(year);
            b.writeVarInt(lines.size());
            for (String s : lines) b.writeString(s);
            b.writeVarInt(tree.size());
            for (Node n : tree) n.write(b);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** client -> server: показать профиль человека из древа */
    public record Ask(UUID id) implements CustomPayload {
        public static final CustomPayload.Id<Ask> ID = new CustomPayload.Id<>(Identifier.of("godbox", "inspect_ask"));
        public static final PacketCodec<RegistryByteBuf, Ask> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, Ask::id, Ask::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** client -> server: включить/выключить камеру слежения за существом */
    public record Follow(UUID id, boolean on) implements CustomPayload {
        public static final CustomPayload.Id<Follow> ID = new CustomPayload.Id<>(Identifier.of("godbox", "inspect_follow"));
        public static final PacketCodec<RegistryByteBuf, Follow> CODEC = PacketCodec.tuple(
                Uuids.PACKET_CODEC, Follow::id,
                PacketCodecs.BOOL, Follow::on,
                Follow::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** server -> client: за кем сейчас следит камера (on = false — слежка окончена) */
    public record FollowState(boolean on, String name) implements CustomPayload {
        public static final CustomPayload.Id<FollowState> ID = new CustomPayload.Id<>(Identifier.of("godbox", "inspect_follow_state"));
        public static final PacketCodec<RegistryByteBuf, FollowState> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, FollowState::on,
                PacketCodecs.STRING, FollowState::name,
                FollowState::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
