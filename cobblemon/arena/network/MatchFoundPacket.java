package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MatchFoundPacket(String opponentName, int countdownSeconds) implements CustomPayload {
   public static final Id<MatchFoundPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "match_found"));
   public static final PacketCodec<ByteBuf, MatchFoundPacket> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, MatchFoundPacket::opponentName, PacketCodecs.INTEGER, MatchFoundPacket::countdownSeconds, MatchFoundPacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
