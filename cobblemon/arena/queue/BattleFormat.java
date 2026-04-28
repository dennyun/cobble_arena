package cobblemon.arena.queue;

public class BattleFormat {
   private final BattleFormat.FormatType type;
   private final BattleFormat.LevelCap levelCap;
   private final boolean allowLegendaries;

   public BattleFormat(BattleFormat.FormatType type, BattleFormat.LevelCap levelCap, boolean allowLegendaries) {
      this.type = type;
      this.levelCap = levelCap;
      this.allowLegendaries = allowLegendaries;
   }

   public BattleFormat.FormatType getType() {
      return this.type;
   }

   public BattleFormat.LevelCap getLevelCap() {
      return this.levelCap;
   }

   public boolean isAllowLegendaries() {
      return this.allowLegendaries;
   }

   public boolean matches(BattleFormat other) {
      return this.type == other.type && this.levelCap == other.levelCap && this.allowLegendaries == other.allowLegendaries;
   }

   @Override
   public String toString() {
      return this.type + " | Lvl " + this.levelCap + " | Legendaries: " + (this.allowLegendaries ? "Yes" : "No");
   }

   public static enum FormatType {
      SINGLES("Singles"),
      DOUBLES("Doubles"),
      TRIPLES("Triples");

      private final String displayName;

      private FormatType(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public static BattleFormat.FormatType fromString(String str) {
         for (BattleFormat.FormatType type : values()) {
            if (type.displayName.equalsIgnoreCase(str)) {
               return type;
            }
         }

         return SINGLES;
      }
   }

   public static enum LevelCap {
      FIFTY("50", 50),
      HUNDRED("100", 100),
      NONE("None", Integer.MAX_VALUE);

      private final String displayName;
      private final int level;

      private LevelCap(String displayName, int level) {
         this.displayName = displayName;
         this.level = level;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public int getLevel() {
         return this.level;
      }

      public static BattleFormat.LevelCap fromString(String str) {
         for (BattleFormat.LevelCap cap : values()) {
            if (cap.displayName.equalsIgnoreCase(str)) {
               return cap;
            }
         }

         return FIFTY;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }
}
