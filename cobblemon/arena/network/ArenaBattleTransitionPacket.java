package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sent by the server when two players are matched and the lead-selection
 * screen should open.
 *
 * <p>{@code battleTypeId} indicates the format so the client can show how many
 * leads the player must pick (1 for singles, 2 for doubles, 3 for triples).
 *
 * <p><b>Backward-compatibility note:</b> old servers that do not encode
 * {@code battleTypeId} are handled gracefully — the decoder defaults to
 * {@code "singles"} when no more bytes are available in the buffer.
 */
public record ArenaBattleTransitionPacket(
    String leftPlayerName,
    String leftPlayerUuid,
    String rightPlayerName,
    String rightPlayerUuid,
    List<ArenaTransitionPokemonEntryPayload> leftTeam,
    List<ArenaTransitionPokemonEntryPayload> rightTeam,
    int durationTicks,
    String battleTypeId // "singles" | "doubles" | "triples"
) implements CustomPayload {
    public static final Id<ArenaBattleTransitionPacket> TYPE = new Id<>(
        Identifier.of("cobblemon_arena", "arena_battle_transition")
    );

    public static final PacketCodec<
        ByteBuf,
        ArenaBattleTransitionPacket
    > CODEC = PacketCodec.of(
        // ── Encoder ───────────────────────────────────────────────────
        (packet, buf) -> {
            PacketCodecs.STRING.encode(buf, packet.leftPlayerName());
            PacketCodecs.STRING.encode(buf, packet.leftPlayerUuid());
            PacketCodecs.STRING.encode(buf, packet.rightPlayerName());
            PacketCodecs.STRING.encode(buf, packet.rightPlayerUuid());
            buf.writeInt(packet.leftTeam().size());
            for (ArenaTransitionPokemonEntryPayload e : packet.leftTeam()) {
                io.netty.buffer.ByteBuf entryBuf = buf.alloc().buffer();
                try {
                    ArenaTransitionPokemonEntryPayload.CODEC.encode(entryBuf, e);
                    buf.writeInt(entryBuf.readableBytes());
                    buf.writeBytes(
                        entryBuf,
                        entryBuf.readerIndex(),
                        entryBuf.readableBytes()
                    );
                } finally {
                    entryBuf.release();
                }
            }
            buf.writeInt(packet.rightTeam().size());
            for (ArenaTransitionPokemonEntryPayload e : packet.rightTeam()) {
                io.netty.buffer.ByteBuf entryBuf = buf.alloc().buffer();
                try {
                    ArenaTransitionPokemonEntryPayload.CODEC.encode(entryBuf, e);
                    buf.writeInt(entryBuf.readableBytes());
                    buf.writeBytes(
                        entryBuf,
                        entryBuf.readerIndex(),
                        entryBuf.readableBytes()
                    );
                } finally {
                    entryBuf.release();
                }
            }
            buf.writeInt(packet.durationTicks());
            // Always write battleTypeId (new field, v2 format)
            PacketCodecs.STRING.encode(buf, packet.battleTypeId());
        },
        // ── Decoder (backward-compatible with v1 format) ──────────────
        buf -> {
            String leftName = PacketCodecs.STRING.decode(buf);
            String leftUuid = PacketCodecs.STRING.decode(buf);
            String rightName = PacketCodecs.STRING.decode(buf);
            String rightUuid = PacketCodecs.STRING.decode(buf);
            List<ArenaTransitionPokemonEntryPayload> leftTeam = decodeTeam(buf);
            List<ArenaTransitionPokemonEntryPayload> rightTeam = decodeTeam(
                buf
            );
            int durationTicks = buf.readInt();
            // battleTypeId was added in v2.  Old servers don't write it,
            // so check buf.isReadable() before attempting to decode.
            String battleTypeId = buf.isReadable()
                ? PacketCodecs.STRING.decode(buf)
                : "singles";
            return new ArenaBattleTransitionPacket(
                leftName,
                leftUuid,
                rightName,
                rightUuid,
                leftTeam,
                rightTeam,
                durationTicks,
                battleTypeId
            );
        }
    );

    // ── Convenience constructor (defaults to "singles") ────────────────────────
    public ArenaBattleTransitionPacket(
        String leftPlayerName,
        String leftPlayerUuid,
        String rightPlayerName,
        String rightPlayerUuid,
        List<ArenaTransitionPokemonEntryPayload> leftTeam,
        List<ArenaTransitionPokemonEntryPayload> rightTeam,
        int durationTicks
    ) {
        this(
            leftPlayerName,
            leftPlayerUuid,
            rightPlayerName,
            rightPlayerUuid,
            leftTeam != null ? List.copyOf(leftTeam) : List.of(),
            rightTeam != null ? List.copyOf(rightTeam) : List.of(),
            durationTicks,
            "singles"
        );
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }

    private static List<ArenaTransitionPokemonEntryPayload> decodeTeam(
        ByteBuf buf
    ) {
        buf.markReaderIndex();
        try {
            return decodeTeamLengthPrefixed(buf);
        } catch (Exception ignored) {
            // Backward-compatibility path: old servers encoded each entry
            // directly as two strings (speciesKey/speciesName), without
            // entry-length framing and without tooltip fields.
            buf.resetReaderIndex();
            return decodeTeamLegacy(buf);
        }
    }

    private static List<ArenaTransitionPokemonEntryPayload> decodeTeamLengthPrefixed(
        ByteBuf buf
    ) {
        int count = buf.readInt();
        if (count < 0 || count > 24) {
            throw new IllegalArgumentException("Invalid team size: " + count);
        }
        List<ArenaTransitionPokemonEntryPayload> team = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (buf.readableBytes() < 4) {
                throw new IllegalStateException("Missing entry size prefix");
            }
            int entrySize = buf.readInt();
            if (entrySize < 0 || entrySize > buf.readableBytes()) {
                throw new IllegalStateException(
                    "Invalid entry payload size: " + entrySize
                );
            }
            ByteBuf entryBuf = buf.readSlice(entrySize);
            team.add(
                (ArenaTransitionPokemonEntryPayload) ArenaTransitionPokemonEntryPayload.CODEC.decode(
                    entryBuf
                )
            );
        }
        return team;
    }

    private static List<ArenaTransitionPokemonEntryPayload> decodeTeamLegacy(
        ByteBuf buf
    ) {
        int count = buf.readInt();
        if (count < 0 || count > 24) {
            throw new IllegalArgumentException("Invalid legacy team size: " + count);
        }
        List<ArenaTransitionPokemonEntryPayload> team = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String speciesKey = PacketCodecs.STRING.decode(buf);
            String speciesName = PacketCodecs.STRING.decode(buf);
            team.add(new ArenaTransitionPokemonEntryPayload(speciesKey, speciesName));
        }
        return team;
    }
}
