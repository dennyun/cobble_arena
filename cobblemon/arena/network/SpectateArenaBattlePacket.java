package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public record SpectateArenaBattlePacket(Optional<UUID> targetSessionId) implements CustomPayload {
   public static final Id<SpectateArenaBattlePacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "spectate_arena_battle"));
   public static final PacketCodec<ByteBuf, SpectateArenaBattlePacket> CODEC = PacketCodec.of(
      (value, buf) -> {
         if (value.targetSessionId().isPresent()) {
            buf.writeBoolean(true);
            buf.writeLong(value.targetSessionId().get().getMostSignificantBits());
            buf.writeLong(value.targetSessionId().get().getLeastSignificantBits());
         } else {
            buf.writeBoolean(false);
         }
      },
      buf -> {
         if (buf.readBoolean()) {
            return new SpectateArenaBattlePacket(Optional.of(new UUID(buf.readLong(), buf.readLong())));
         } else {
            return new SpectateArenaBattlePacket(Optional.empty());
         }
      }
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
