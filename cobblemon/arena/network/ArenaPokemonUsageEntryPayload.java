package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ArenaPokemonUsageEntryPayload(String speciesName, int uses, int wins, int losses) {
   public static final PacketCodec<ByteBuf, ArenaPokemonUsageEntryPayload> CODEC = PacketCodec.of(
      (entry, buffer) -> {
         PacketCodecs.STRING.encode(buffer, entry.speciesName());
         buffer.writeInt(entry.uses());
         buffer.writeInt(entry.wins());
         buffer.writeInt(entry.losses());
      },
      buffer -> new ArenaPokemonUsageEntryPayload(PacketCodecs.STRING.decode(buffer), buffer.readInt(), buffer.readInt(), buffer.readInt())
   );
}
