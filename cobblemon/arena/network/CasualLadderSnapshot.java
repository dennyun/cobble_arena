package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record CasualLadderSnapshot(
   String formatId,
   int casualWins,
   int casualLosses,
   int casualStreak
) {
   public static final PacketCodec<ByteBuf, CasualLadderSnapshot> CODEC = PacketCodec.of(
      (snapshot, buffer) -> {
         PacketCodecs.STRING.encode(buffer, snapshot.formatId());
         buffer.writeInt(snapshot.casualWins());
         buffer.writeInt(snapshot.casualLosses());
         buffer.writeInt(snapshot.casualStreak());
      },
      buffer -> new CasualLadderSnapshot(
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt()
      )
   );

   public static CasualLadderSnapshot empty(String formatId) {
      return new CasualLadderSnapshot(formatId, 0, 0, 0);
   }
}
