package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestRankedConfigPacket() implements CustomPayload {
   public static final Id<RequestRankedConfigPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "request_ranked_config"));
   public static final PacketCodec<ByteBuf, RequestRankedConfigPacket> CODEC = PacketCodec.of(
      (buffer, packet) -> {}, buffer -> new RequestRankedConfigPacket()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
