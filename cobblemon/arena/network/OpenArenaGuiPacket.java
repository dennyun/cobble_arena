package cobblemon.arena.network;

import cobblemon.arena.config.ArenaServerConfig;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenArenaGuiPacket(
   String playerName,
   String rankedConfigJson,
   List<String> partyPokemonJson,
   List<ArenaMatchHistoryEntryPayload> recentMatchHistory,
   List<ArenaPokemonUsageEntryPayload> pokemonUsage,
   int quickWins,
   int quickLosses,
   int totalBattles,
   int honorScore,
   int totalTurnsPlayed,
   java.util.Map<String, Integer> monotypeWins,
   String currentRankedLadderId,
   String currentSeasonName,
   long currentSeasonStartedAtMs,
   List<RankedLadderSnapshot> rankedLadderSnapshots,
   int playersOnline,
   int playersInQueue,
   int activeBattles,
   int availableArenas,
   int totalArenas,
   boolean forceOpen,
   List<CasualLadderSnapshot> casualLadderSnapshots,
   List<ActiveBattlePayload> activeBattlesList
) implements CustomPayload {
   public static final CustomPayload.Id<OpenArenaGuiPacket> ID = new CustomPayload.Id<>(Identifier.of("cobblemon_arena", "open_arena_gui"));

   public static final PacketCodec<ByteBuf, OpenArenaGuiPacket> CODEC = PacketCodec.of(
      (packet, buffer) -> {
         PacketCodecs.STRING.encode(buffer, packet.playerName());
         PacketCodecs.STRING.encode(buffer, packet.rankedConfigJson());
         buffer.writeInt(packet.partyPokemonJson().size());

         for (String pokemonJson : packet.partyPokemonJson()) {
            PacketCodecs.STRING.encode(buffer, pokemonJson);
         }

         buffer.writeInt(packet.recentMatchHistory().size());

         for (ArenaMatchHistoryEntryPayload entry : packet.recentMatchHistory()) {
            ArenaMatchHistoryEntryPayload.CODEC.encode(buffer, entry);
         }

         buffer.writeInt(packet.pokemonUsage().size());

         for (ArenaPokemonUsageEntryPayload entry : packet.pokemonUsage()) {
            ArenaPokemonUsageEntryPayload.CODEC.encode(buffer, entry);
         }

         buffer.writeInt(packet.quickWins());
         buffer.writeInt(packet.quickLosses());
         buffer.writeInt(packet.totalBattles());
         buffer.writeInt(packet.honorScore());
         buffer.writeInt(packet.totalTurnsPlayed());
         
         buffer.writeInt(packet.monotypeWins().size());
         for (java.util.Map.Entry<String, Integer> entry : packet.monotypeWins().entrySet()) {
            PacketCodecs.STRING.encode(buffer, entry.getKey());
            buffer.writeInt(entry.getValue());
         }
         
         PacketCodecs.STRING.encode(buffer, packet.currentRankedLadderId());
         PacketCodecs.STRING.encode(buffer, packet.currentSeasonName());
         buffer.writeLong(packet.currentSeasonStartedAtMs());
         buffer.writeInt(packet.rankedLadderSnapshots().size());

         for (RankedLadderSnapshot snapshot : packet.rankedLadderSnapshots()) {
            RankedLadderSnapshot.CODEC.encode(buffer, snapshot);
         }

         buffer.writeInt(packet.playersOnline());
         buffer.writeInt(packet.playersInQueue());
         buffer.writeInt(packet.activeBattles());
         buffer.writeInt(packet.availableArenas());
         buffer.writeInt(packet.totalArenas());
         buffer.writeBoolean(packet.forceOpen());
         
         buffer.writeInt(packet.casualLadderSnapshots().size());
         for (CasualLadderSnapshot snapshot : packet.casualLadderSnapshots()) {
            CasualLadderSnapshot.CODEC.encode(buffer, snapshot);
         }
         
         buffer.writeInt(packet.activeBattlesList().size());
         for (ActiveBattlePayload payload : packet.activeBattlesList()) {
            ActiveBattlePayload.CODEC.encode(buffer, payload);
         }
      },
      buffer -> {
         String p1 = PacketCodecs.STRING.decode(buffer);
         String p2 = PacketCodecs.STRING.decode(buffer);
         List<String> party = decodePartyPokemon(buffer);
         List<ArenaMatchHistoryEntryPayload> history = decodeRecentMatchHistory(buffer);
         List<ArenaPokemonUsageEntryPayload> usage = decodePokemonUsage(buffer);
         int qw = buffer.readInt();
         int ql = buffer.readInt();
         int tb = buffer.readInt();
         int hs = buffer.readInt();
         int ttp = buffer.readInt();
         
         int mapSize = buffer.readInt();
         java.util.Map<String, Integer> mWins = new java.util.LinkedHashMap<>();
         for (int i = 0; i < mapSize; i++) {
             mWins.put(PacketCodecs.STRING.decode(buffer), buffer.readInt());
         }
         
         return new OpenArenaGuiPacket(
            p1, p2, party, history, usage, qw, ql, tb, hs, ttp, mWins,
            PacketCodecs.STRING.decode(buffer),
            PacketCodecs.STRING.decode(buffer),
            buffer.readLong(),
            decodeRankedLadderSnapshots(buffer),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readBoolean(),
            decodeCasualLadderSnapshots(buffer),
            decodeActiveBattles(buffer)
         );
      }
   );

   public OpenArenaGuiPacket(
      String playerName,
      String rankedConfigJson,
      List<String> partyPokemonJson,
      List<ArenaMatchHistoryEntryPayload> recentMatchHistory,
      List<ArenaPokemonUsageEntryPayload> pokemonUsage,
      int quickWins,
      int quickLosses,
      int totalBattles,
      int honorScore,
      int totalTurnsPlayed,
      java.util.Map<String, Integer> monotypeWins,
      String currentRankedLadderId,
      String currentSeasonName,
      long currentSeasonStartedAtMs,
      List<RankedLadderSnapshot> rankedLadderSnapshots,
      int playersOnline,
      int playersInQueue,
      int activeBattles,
      int availableArenas,
      int totalArenas,
      boolean forceOpen,
      List<CasualLadderSnapshot> casualLadderSnapshots,
      List<ActiveBattlePayload> activeBattlesList
   ) {
      rankedConfigJson = rankedConfigJson != null && !rankedConfigJson.isBlank()
         ? rankedConfigJson
         : ArenaServerConfig.snapshotToJson(new ArenaServerConfig.Snapshot());
      this.playerName = playerName;
      this.rankedConfigJson = rankedConfigJson;
      this.partyPokemonJson = partyPokemonJson;
      this.recentMatchHistory = recentMatchHistory;
      this.pokemonUsage = pokemonUsage;
      this.quickWins = quickWins;
      this.quickLosses = quickLosses;
      this.totalBattles = totalBattles;
      this.honorScore = honorScore;
      this.totalTurnsPlayed = totalTurnsPlayed;
      this.monotypeWins = monotypeWins != null ? monotypeWins : java.util.Map.of();
      this.currentRankedLadderId = currentRankedLadderId;
      this.currentSeasonName = currentSeasonName;
      this.currentSeasonStartedAtMs = currentSeasonStartedAtMs;
      this.rankedLadderSnapshots = rankedLadderSnapshots;
      this.playersOnline = playersOnline;
      this.playersInQueue = playersInQueue;
      this.activeBattles = activeBattles;
      this.availableArenas = availableArenas;
      this.totalArenas = totalArenas;
      this.forceOpen = forceOpen;
      this.casualLadderSnapshots = casualLadderSnapshots != null ? casualLadderSnapshots : List.of();
      this.activeBattlesList = activeBattlesList != null ? activeBattlesList : List.of();
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return ID;
   }

   private static List<RankedLadderSnapshot> decodeRankedLadderSnapshots(ByteBuf buffer) {
      int snapshotCount = buffer.readInt();
      List<RankedLadderSnapshot> snapshots = new ArrayList<>(snapshotCount);

      for (int i = 0; i < snapshotCount; i++) {
         snapshots.add((RankedLadderSnapshot)RankedLadderSnapshot.CODEC.decode(buffer));
      }

      return snapshots;
   }

   private static List<CasualLadderSnapshot> decodeCasualLadderSnapshots(ByteBuf buffer) {
      int snapshotCount = buffer.readInt();
      List<CasualLadderSnapshot> snapshots = new ArrayList<>(snapshotCount);

      for (int i = 0; i < snapshotCount; i++) {
         snapshots.add((CasualLadderSnapshot)CasualLadderSnapshot.CODEC.decode(buffer));
      }

      return snapshots;
   }

   private static List<ActiveBattlePayload> decodeActiveBattles(ByteBuf buffer) {
      int count = buffer.readInt();
      List<ActiveBattlePayload> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(ActiveBattlePayload.CODEC.decode(buffer));
      }
      return list;
   }

   private static List<ArenaMatchHistoryEntryPayload> decodeRecentMatchHistory(ByteBuf buffer) {
      int count = buffer.readInt();
      List<ArenaMatchHistoryEntryPayload> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add((ArenaMatchHistoryEntryPayload)ArenaMatchHistoryEntryPayload.CODEC.decode(buffer));
      }

      return entries;
   }

   private static List<ArenaPokemonUsageEntryPayload> decodePokemonUsage(ByteBuf buffer) {
      int count = buffer.readInt();
      List<ArenaPokemonUsageEntryPayload> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add((ArenaPokemonUsageEntryPayload)ArenaPokemonUsageEntryPayload.CODEC.decode(buffer));
      }

      return entries;
   }

   private static List<String> decodePartyPokemon(ByteBuf buffer) {
      int pokemonCount = buffer.readInt();
      List<String> pokemonJson = new ArrayList<>(pokemonCount);

      for (int i = 0; i < pokemonCount; i++) {
         pokemonJson.add(PacketCodecs.STRING.decode(buffer));
      }

      return pokemonJson;
   }
}
