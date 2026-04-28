package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoadCustomLadderTemplatePacket(int ladderIndex, String templateName) implements CustomPayload {
   public static final Id<LoadCustomLadderTemplatePacket> TYPE = new Id<>(
      Identifier.of("cobblemon_arena", "load_custom_ladder_template")
   );
   public static final PacketCodec<ByteBuf, LoadCustomLadderTemplatePacket> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER,
      LoadCustomLadderTemplatePacket::ladderIndex,
      PacketCodecs.STRING,
      LoadCustomLadderTemplatePacket::templateName,
      LoadCustomLadderTemplatePacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
