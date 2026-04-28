package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PostMatchResultsPacket(
   boolean ranked,
   boolean victory,
   String ladderDisplayName,
   String opponentName,
   int ratingBefore,
   int ratingAfter,
   int ratingDelta,
   int wins,
   int losses,
   int streak,
   int rank,
   int totalRankedPlayers
) implements CustomPayload {
   public static final CustomPayload.Id<PostMatchResultsPacket> ID = new CustomPayload.Id<>(
      Identifier.of("cobblemon_arena", "post_match_results")
   );

   public static final PacketCodec<ByteBuf, PostMatchResultsPacket> CODEC = PacketCodec.of(
      (packet, buffer) -> {
         buffer.writeBoolean(packet.ranked());
         buffer.writeBoolean(packet.victory());
         PacketCodecs.STRING.encode(buffer, packet.ladderDisplayName());
         PacketCodecs.STRING.encode(buffer, packet.opponentName());
         buffer.writeInt(packet.ratingBefore());
         buffer.writeInt(packet.ratingAfter());
         buffer.writeInt(packet.ratingDelta());
         buffer.writeInt(packet.wins());
         buffer.writeInt(packet.losses());
         buffer.writeInt(packet.streak());
         buffer.writeInt(packet.rank());
         buffer.writeInt(packet.totalRankedPlayers());
      },
      buffer -> new PostMatchResultsPacket(
         buffer.readBoolean(),
         buffer.readBoolean(),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt()
      )
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
