package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SelectArenaLeadPacket(int slotIndex) implements CustomPayload {
   public static final Id<SelectArenaLeadPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "select_arena_lead"));
   public static final PacketCodec<ByteBuf, SelectArenaLeadPacket> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER, SelectArenaLeadPacket::slotIndex, SelectArenaLeadPacket::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
