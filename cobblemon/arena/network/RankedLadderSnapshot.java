package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record RankedLadderSnapshot(
   String ladderId,
   int rankedRating,
   int rankedWins,
   int rankedLosses,
   int rankedStreak,
   int rankedBestStreak,
   int playerRank,
   int totalRankedPlayers,
   List<String> leaderboardEntries
) {
   public static final PacketCodec<ByteBuf, RankedLadderSnapshot> CODEC = PacketCodec.of(
      (snapshot, buffer) -> {
         PacketCodecs.STRING.encode(buffer, snapshot.ladderId());
         buffer.writeInt(snapshot.rankedRating());
         buffer.writeInt(snapshot.rankedWins());
         buffer.writeInt(snapshot.rankedLosses());
         buffer.writeInt(snapshot.rankedStreak());
         buffer.writeInt(snapshot.rankedBestStreak());
         buffer.writeInt(snapshot.playerRank());
         buffer.writeInt(snapshot.totalRankedPlayers());
         buffer.writeInt(snapshot.leaderboardEntries().size());

         for (String entry : snapshot.leaderboardEntries()) {
            PacketCodecs.STRING.encode(buffer, entry);
         }
      },
      buffer -> new RankedLadderSnapshot(
         PacketCodecs.STRING.decode(buffer),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readInt(),
         decodeEntries(buffer)
      )
   );

   public static RankedLadderSnapshot empty(String ladderId) {
      return new RankedLadderSnapshot(ladderId, 0, 0, 0, 0, 0, 0, 0, List.of());
   }

   private static List<String> decodeEntries(ByteBuf buffer) {
      int entryCount = buffer.readInt();
      List<String> entries = new ArrayList<>(entryCount);

      for (int i = 0; i < entryCount; i++) {
         entries.add(PacketCodecs.STRING.decode(buffer));
      }

      return entries;
   }
}
