package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ArenaMatchHistoryEntryPayload(
   boolean ranked, boolean victory, String ladderDisplayName, String opponentName, int ratingDelta, int ratingAfter, long playedAtMs
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
      },
      buffer -> new ArenaMatchHistoryEntryPayload(
         buffer.readBoolean(),
         buffer.readBoolean(),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readLong()
      )
   );
}
