package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CancelQueuePacket() implements CustomPayload {
   public static final Id<CancelQueuePacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "cancel_queue"));
   public static final PacketCodec<ByteBuf, CancelQueuePacket> CODEC = PacketCodec.of((buffer, packet) -> {}, buffer -> new CancelQueuePacket());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
