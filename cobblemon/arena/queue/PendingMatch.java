package cobblemon.arena.queue;

import cobblemon.arena.ladder.ArenaLadder;
import java.util.UUID;

public final class PendingMatch {
   private final UUID player1UUID;
   private final UUID player2UUID;
   private final ArenaLadder ladder;
   private final long readyAtMs;
   private final int player1PartySignature;
   private final int player2PartySignature;
   private int lastCountdownBroadcast = Integer.MIN_VALUE;

   public PendingMatch(UUID player1UUID, UUID player2UUID, ArenaLadder ladder, long readyAtMs, int player1PartySignature, int player2PartySignature) {
      this.player1UUID = player1UUID;
      this.player2UUID = player2UUID;
      this.ladder = ladder;
      this.readyAtMs = readyAtMs;
      this.player1PartySignature = player1PartySignature;
      this.player2PartySignature = player2PartySignature;
   }

   public UUID getPlayer1UUID() {
      return this.player1UUID;
   }

   public UUID getPlayer2UUID() {
      return this.player2UUID;
   }

   public ArenaLadder getLadder() {
      return this.ladder;
   }

   public boolean involves(UUID playerUUID) {
      return this.player1UUID.equals(playerUUID) || this.player2UUID.equals(playerUUID);
   }

   public UUID getOpponent(UUID playerUUID) {
      if (this.player1UUID.equals(playerUUID)) {
         return this.player2UUID;
      } else {
         return this.player2UUID.equals(playerUUID) ? this.player1UUID : null;
      }
   }

   public long getReadyAtMs() {
      return this.readyAtMs;
   }

   public int getExpectedPartySignature(UUID playerUUID) {
      if (this.player1UUID.equals(playerUUID)) {
         return this.player1PartySignature;
      } else {
         return this.player2UUID.equals(playerUUID) ? this.player2PartySignature : Integer.MIN_VALUE;
      }
   }

   public int getRemainingSeconds(long nowMs) {
      long remainingMs = Math.max(0L, this.readyAtMs - nowMs);
      return remainingMs == 0L ? 0 : (int)Math.ceil(remainingMs / 1000.0);
   }

   public int getLastCountdownBroadcast() {
      return this.lastCountdownBroadcast;
   }

   public void setLastCountdownBroadcast(int lastCountdownBroadcast) {
      this.lastCountdownBroadcast = lastCountdownBroadcast;
   }
}
