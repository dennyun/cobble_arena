package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SelectArenaLeadPacket(List<Integer> slotIndexes) implements CustomPayload {
   public static final Id<SelectArenaLeadPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "select_arena_lead"));
   public static final PacketCodec<ByteBuf, SelectArenaLeadPacket> CODEC = PacketCodec.tuple(
      PacketCodecs.INTEGER.collect(PacketCodecs.toList()), SelectArenaLeadPacket::slotIndexes, SelectArenaLeadPacket::new
   );

   public SelectArenaLeadPacket(int slotIndex) {
      this(List.of(slotIndex));
   }

   public SelectArenaLeadPacket {
      slotIndexes = slotIndexes == null ? List.of() : List.copyOf(slotIndexes);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
