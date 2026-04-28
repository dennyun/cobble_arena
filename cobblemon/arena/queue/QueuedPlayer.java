package cobblemon.arena.queue;

import cobblemon.arena.ladder.ArenaLadder;
import java.util.UUID;

public class QueuedPlayer {
   private final UUID playerUUID;
   private final String playerName;
   private final ArenaLadder ladder;
   private final int rankedRating;
   private final long queueStartTime;

   public QueuedPlayer(UUID playerUUID, String playerName, ArenaLadder ladder, int rankedRating) {
      this.playerUUID = playerUUID;
      this.playerName = playerName;
      this.ladder = ladder;
      this.rankedRating = rankedRating;
      this.queueStartTime = System.currentTimeMillis();
   }

   public UUID getPlayerUUID() {
      return this.playerUUID;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public ArenaLadder getLadder() {
      return this.ladder;
   }

   public boolean isRanked() {
      return this.ladder.isRanked();
   }

   public int getRankedRating() {
      return this.rankedRating;
   }

   public long getQueueStartTime() {
      return this.queueStartTime;
   }

   public int getQueueTimeSeconds() {
      return (int)((System.currentTimeMillis() - this.queueStartTime) / 1000L);
   }
}
