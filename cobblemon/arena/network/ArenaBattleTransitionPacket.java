package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ArenaBattleTransitionPacket(
   String leftPlayerName,
   String leftPlayerUuid,
   String rightPlayerName,
   String rightPlayerUuid,
   List<ArenaTransitionPokemonEntryPayload> leftTeam,
   List<ArenaTransitionPokemonEntryPayload> rightTeam,
   int durationTicks
) implements CustomPayload {
   public static final Id<ArenaBattleTransitionPacket> TYPE = new Id<>(Identifier.of("cobblemon_arena", "arena_battle_transition"));
   public static final PacketCodec<ByteBuf, ArenaBattleTransitionPacket> CODEC = PacketCodec.of(
      (packet, buffer) -> {
         PacketCodecs.STRING.encode(buffer, packet.leftPlayerName());
         PacketCodecs.STRING.encode(buffer, packet.leftPlayerUuid());
         PacketCodecs.STRING.encode(buffer, packet.rightPlayerName());
         PacketCodecs.STRING.encode(buffer, packet.rightPlayerUuid());
         buffer.writeInt(packet.leftTeam().size());

         for (ArenaTransitionPokemonEntryPayload entry : packet.leftTeam()) {
            ArenaTransitionPokemonEntryPayload.CODEC.encode(buffer, entry);
         }

         buffer.writeInt(packet.rightTeam().size());

         for (ArenaTransitionPokemonEntryPayload entry : packet.rightTeam()) {
            ArenaTransitionPokemonEntryPayload.CODEC.encode(buffer, entry);
         }

         buffer.writeInt(packet.durationTicks());
      },
      buffer -> new ArenaBattleTransitionPacket(
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         PacketCodecs.STRING.decode(buffer),
         decodeTeam(buffer),
         decodeTeam(buffer),
         buffer.readInt()
      )
   );

   public ArenaBattleTransitionPacket(
      String leftPlayerName,
      String leftPlayerUuid,
      String rightPlayerName,
      String rightPlayerUuid,
      List<ArenaTransitionPokemonEntryPayload> leftTeam,
      List<ArenaTransitionPokemonEntryPayload> rightTeam,
      int durationTicks
   ) {
      leftTeam = leftTeam == null ? List.of() : List.copyOf(leftTeam);
      rightTeam = rightTeam == null ? List.of() : List.copyOf(rightTeam);
      this.leftPlayerName = leftPlayerName;
      this.leftPlayerUuid = leftPlayerUuid;
      this.rightPlayerName = rightPlayerName;
      this.rightPlayerUuid = rightPlayerUuid;
      this.leftTeam = leftTeam;
      this.rightTeam = rightTeam;
      this.durationTicks = durationTicks;
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }

   private static List<ArenaTransitionPokemonEntryPayload> decodeTeam(ByteBuf buffer) {
      int count = buffer.readInt();
      List<ArenaTransitionPokemonEntryPayload> team = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         team.add((ArenaTransitionPokemonEntryPayload)ArenaTransitionPokemonEntryPayload.CODEC.decode(buffer));
      }

      return team;
   }
}
