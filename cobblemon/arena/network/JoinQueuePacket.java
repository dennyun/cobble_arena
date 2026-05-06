package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record JoinQueuePacket(String ladderId, boolean blockLegendaries) implements CustomPayload {
   public static final Id<JoinQueuePacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "join_queue"));
   public static final PacketCodec<ByteBuf, JoinQueuePacket> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, JoinQueuePacket::ladderId,
      PacketCodecs.BOOL, JoinQueuePacket::blockLegendaries,
      JoinQueuePacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
