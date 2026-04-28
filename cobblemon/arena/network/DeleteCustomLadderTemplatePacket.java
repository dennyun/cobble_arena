package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeleteCustomLadderTemplatePacket(String templateName) implements CustomPayload {
   public static final Id<DeleteCustomLadderTemplatePacket> TYPE = new Id<>(
      Identifier.of("cobblemon_arena", "delete_custom_ladder_template")
   );
   public static final PacketCodec<ByteBuf, DeleteCustomLadderTemplatePacket> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, DeleteCustomLadderTemplatePacket::templateName, DeleteCustomLadderTemplatePacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
