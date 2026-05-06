package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Per-Pokemon payload shown in the battle transition preview.
 * This entry includes species identity plus lightweight details used by the
 * hover tooltip (ability, held item, types, moves, nature, level).
 */
public record ArenaTransitionPokemonEntryPayload(
    String speciesKey,
    String speciesName,
    String abilityName,
    String heldItemName,
    java.util.List<String> typeNames,
    java.util.List<String> moveNames,
    String natureName,
    int level
) {
    public static final PacketCodec<
        ByteBuf,
        ArenaTransitionPokemonEntryPayload
    > CODEC = PacketCodec.of(
        (entry, buf) -> {
            PacketCodecs.STRING.encode(buf, entry.speciesKey());
            PacketCodecs.STRING.encode(buf, entry.speciesName());
            PacketCodecs.STRING.encode(buf, entry.abilityName());
            PacketCodecs.STRING.encode(buf, entry.heldItemName());
            PacketCodecs.STRING
                .collect(PacketCodecs.toList())
                .encode(buf, entry.typeNames());
            PacketCodecs.STRING
                .collect(PacketCodecs.toList())
                .encode(buf, entry.moveNames());
            PacketCodecs.STRING.encode(buf, entry.natureName());
            buf.writeInt(entry.level());
        },
        buf ->
            new ArenaTransitionPokemonEntryPayload(
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.collect(PacketCodecs.toList()).decode(buf),
                PacketCodecs.STRING.collect(PacketCodecs.toList()).decode(buf),
                PacketCodecs.STRING.decode(buf),
                buf.readInt()
            )
    );

    public ArenaTransitionPokemonEntryPayload(String speciesKey, String speciesName) {
        this(
            speciesKey,
            speciesName,
            "",
            "",
            java.util.List.of(),
            java.util.List.of(),
            "",
            0
        );
    }
}
