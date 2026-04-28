package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveCustomLadderTemplatePacket(String ladderConfigJson) implements CustomPayload {
   public static final Id<SaveCustomLadderTemplatePacket> TYPE = new Id<>(
      Identifier.of("cobblemon_arena", "save_custom_ladder_template")
   );
   public static final PacketCodec<ByteBuf, SaveCustomLadderTemplatePacket> CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, SaveCustomLadderTemplatePacket::ladderConfigJson, SaveCustomLadderTemplatePacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
