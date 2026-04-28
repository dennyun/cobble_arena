package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QueueStatusPacket(boolean inQueue, String queueLabel, String ladderDisplayName, String rulesSummary, int queueTimeSeconds, int playersInQueue)
   implements CustomPayload {
   public static final Id<QueueStatusPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "queue_status"));
   public static final PacketCodec<ByteBuf, QueueStatusPacket> CODEC = PacketCodec.of(
      (packet, buffer) -> {
         buffer.writeBoolean(packet.inQueue());
         PacketCodecs.STRING.encode(buffer, packet.queueLabel());
         PacketCodecs.STRING.encode(buffer, packet.ladderDisplayName());
         PacketCodecs.STRING.encode(buffer, packet.rulesSummary());
         buffer.writeInt(packet.queueTimeSeconds());
         buffer.writeInt(packet.playersInQueue());
      },
      buffer -> new QueueStatusPacket(
         buffer.readBoolean(),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt()
      )
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
