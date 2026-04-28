package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ArenaTransitionPokemonEntryPayload(String speciesKey, String speciesName) {
   public static final PacketCodec<ByteBuf, ArenaTransitionPokemonEntryPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING,
      ArenaTransitionPokemonEntryPayload::speciesKey,
      PacketCodecs.STRING,
      ArenaTransitionPokemonEntryPayload::speciesName,
      ArenaTransitionPokemonEntryPayload::new
   );
}
