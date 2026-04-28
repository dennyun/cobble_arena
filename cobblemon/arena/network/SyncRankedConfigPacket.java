package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncRankedConfigPacket(String configJson, boolean canEdit, boolean canReset, List<String> savedCustomLadderNames) implements CustomPayload {
   public static final Id<SyncRankedConfigPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "sync_ranked_config"));
   public static final PacketCodec<ByteBuf, SyncRankedConfigPacket> CODEC = PacketCodec.of(
      (packet, buffer) -> {
         PacketCodecs.STRING.encode(buffer, packet.configJson());
         buffer.writeBoolean(packet.canEdit());
         buffer.writeBoolean(packet.canReset());
         buffer.writeInt(packet.savedCustomLadderNames().size());

         for (String name : packet.savedCustomLadderNames()) {
            PacketCodecs.STRING.encode(buffer, name);
         }
      },
      buffer -> new SyncRankedConfigPacket(
         PacketCodecs.STRING.decode(buffer),
         buffer.readBoolean(),
         buffer.readBoolean(),
         decodeSavedNames(buffer)
      )
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }

   private static List<String> decodeSavedNames(ByteBuf buffer) {
      int size = buffer.readInt();
      List<String> names = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         names.add(PacketCodecs.STRING.decode(buffer));
      }

      return names;
   }
}
