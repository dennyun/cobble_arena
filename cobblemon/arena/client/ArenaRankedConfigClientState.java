package cobblemon.arena.client;

import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import java.util.List;

public final class ArenaRankedConfigClientState {
   private static boolean loaded = false;
   private static boolean canEdit = false;
   private static boolean canReset = false;
   private static List<String> savedCustomLadderNames = List.of();
   private static ArenaServerConfig.Snapshot snapshot = ArenaServerConfig.copySnapshot(new ArenaServerConfig.Snapshot());

   private ArenaRankedConfigClientState() {
   }

   public static void update(String configJson, boolean canEditConfig, boolean canResetLadder, List<String> savedTemplateNames) {
      snapshot = ArenaServerConfig.snapshotFromJson(configJson);
      loaded = true;
      canEdit = canEditConfig;
      canReset = canResetLadder;
      savedCustomLadderNames = savedTemplateNames == null ? List.of() : List.copyOf(savedTemplateNames);
      ArenaLadder.setActiveRankedLadders(ArenaServerConfig.activeLaddersFromSnapshot(snapshot));
      ArenaClientState.ensureValidSelectedRankedLadder();
   }

   public static boolean isLoaded() {
      return loaded;
   }

   public static boolean canEdit() {
      return canEdit;
   }

   public static boolean canReset() {
      return canReset;
   }

   public static ArenaServerConfig.Snapshot getSnapshot() {
      return ArenaServerConfig.copySnapshot(snapshot);
   }

   public static int getActiveRankedLadderCount() {
      return snapshot.getActiveRankedLadderCount();
   }

   public static int getActionTimerSeconds() {
      return snapshot.getActionTimerSeconds();
   }

   public static List<ArenaServerConfig.RankedLadderConfig> getRankedLadders() {
      return getSnapshot().getRankedLadders();
   }

   public static List<String> getSavedCustomLadderNames() {
      return savedCustomLadderNames;
   }
}
