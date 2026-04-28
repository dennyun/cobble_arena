package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SpectateArenaBattlePacket() implements CustomPayload {
   public static final Id<SpectateArenaBattlePacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "spectate_arena_battle"));
   public static final PacketCodec<ByteBuf, SpectateArenaBattlePacket> CODEC = PacketCodec.of((buffer, packet) -> {}, buffer -> new SpectateArenaBattlePacket());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
