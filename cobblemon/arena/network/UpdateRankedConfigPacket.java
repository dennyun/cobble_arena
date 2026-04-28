package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdateRankedConfigPacket(String configJson) implements CustomPayload {
   public static final Id<UpdateRankedConfigPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "update_ranked_config"));
   public static final PacketCodec<ByteBuf, UpdateRankedConfigPacket> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, UpdateRankedConfigPacket::configJson, UpdateRankedConfigPacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
