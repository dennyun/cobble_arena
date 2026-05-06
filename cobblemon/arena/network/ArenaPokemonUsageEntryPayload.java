package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Carries per-species usage statistics for the match-history "Pokémon usados" tab.
 *
 * {@code speciesKey} is the Cobblemon resource identifier (e.g. {@code "cobblemon:pikachu"})
 * used to render the 3-D model on the client.  It may be an empty string for legacy records
 * that pre-date this field.
 */
public record ArenaPokemonUsageEntryPayload(
    String speciesKey,
    String speciesName,
    int uses,
    int wins,
    int losses
) {
    public static final PacketCodec<
        ByteBuf,
        ArenaPokemonUsageEntryPayload
    > CODEC = PacketCodec.of(
        (entry, buf) -> {
            PacketCodecs.STRING.encode(buf, entry.speciesKey());
            PacketCodecs.STRING.encode(buf, entry.speciesName());
            buf.writeInt(entry.uses());
            buf.writeInt(entry.wins());
            buf.writeInt(entry.losses());
        },
        buf ->
            new ArenaPokemonUsageEntryPayload(
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
            )
    );
}
