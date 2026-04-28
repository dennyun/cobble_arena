package cobblemon.arena.client;

import com.cobblemon.mod.common.client.CobblemonClient;

public final class ArenaBattleClientState {
   private static int pendingTransitionTicks = 0;
   private static boolean arenaBattleActive = false;

   private ArenaBattleClientState() {
   }

   public static void markTransitionStarted(int durationTicks) {
      pendingTransitionTicks = Math.max(40, durationTicks + 20);
   }

   public static void tick() {
      if (pendingTransitionTicks > 0) {
         pendingTransitionTicks--;
      }

      if (!arenaBattleActive && CobblemonClient.INSTANCE.getBattle() != null) {
         arenaBattleActive = true;
         pendingTransitionTicks = 0;
      } else {
         if (arenaBattleActive && CobblemonClient.INSTANCE.getBattle() == null) {
            arenaBattleActive = false;
         }
      }
   }

   public static boolean isArenaBattleActive() {
      return arenaBattleActive;
   }

   public static boolean hasPendingTransition() {
      return pendingTransitionTicks > 0;
   }
}
