package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ArenaMatchHistoryEntryPayload(
   boolean ranked,
   boolean victory,
   String ladderDisplayName,
   String opponentName,
   int ratingDelta,
   int ratingAfter,
   long playedAtMs,
   java.util.List<ArenaTransitionPokemonEntryPayload> ownTeam,
   java.util.List<ArenaTransitionPokemonEntryPayload> opponentTeam
) {
   public static final PacketCodec<ByteBuf, ArenaMatchHistoryEntryPayload> CODEC = PacketCodec.of(
      (entry, buffer) -> {
         buffer.writeBoolean(entry.ranked());
         buffer.writeBoolean(entry.victory());
         PacketCodecs.STRING.encode(buffer, entry.ladderDisplayName());
         PacketCodecs.STRING.encode(buffer, entry.opponentName());
         buffer.writeInt(entry.ratingDelta());
         buffer.writeInt(entry.ratingAfter());
         buffer.writeLong(entry.playedAtMs());
         buffer.writeInt(entry.ownTeam().size());

         for (ArenaTransitionPokemonEntryPayload pokemon : entry.ownTeam()) {
            ArenaTransitionPokemonEntryPayload.CODEC.encode(buffer, pokemon);
         }

         buffer.writeInt(entry.opponentTeam().size());

         for (ArenaTransitionPokemonEntryPayload pokemon : entry.opponentTeam()) {
            ArenaTransitionPokemonEntryPayload.CODEC.encode(buffer, pokemon);
         }
      },
      buffer -> new ArenaMatchHistoryEntryPayload(
         buffer.readBoolean(),
         buffer.readBoolean(),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readLong(),
         decodeTeam(buffer),
         decodeTeam(buffer)
      )
   );

   public ArenaMatchHistoryEntryPayload {
      ownTeam = ownTeam == null ? java.util.List.of() : java.util.List.copyOf(ownTeam);
      opponentTeam = opponentTeam == null ? java.util.List.of() : java.util.List.copyOf(opponentTeam);
   }

   private static java.util.List<ArenaTransitionPokemonEntryPayload> decodeTeam(ByteBuf buffer) {
      int count = buffer.readInt();
      java.util.List<ArenaTransitionPokemonEntryPayload> team = new java.util.ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         team.add((ArenaTransitionPokemonEntryPayload)ArenaTransitionPokemonEntryPayload.CODEC.decode(buffer));
      }

      return team;
   }
}
