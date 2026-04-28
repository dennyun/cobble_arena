package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ResetRankedLadderPacket() implements CustomPayload {
   public static final Id<ResetRankedLadderPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "reset_ranked_ladder"));
   public static final PacketCodec<ByteBuf, ResetRankedLadderPacket> CODEC = PacketCodec.of(
      (buffer, packet) -> {}, buffer -> new ResetRankedLadderPacket()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
