package cobblemon.arena.config;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.ladder.ArenaRankedPreset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;

public final class ArenaServerConfig {
   public static final int MAX_CUSTOM_RANKED_LADDERS = 8;
   public static final int MAX_RANKED_LADDER_SLOTS = 8;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String CONFIG_FILE = "cobblemon_arena_server.json";
   private static final String CUSTOM_LADDER_TEMPLATE_DIR = "cobblemon_arena_custom_ladders";
   private static final float DEFAULT_SEASON_SOFT_RESET_FACTOR = 0.35F;
   private static final ArenaServerConfig INSTANCE = new ArenaServerConfig();
   private Path configFile;
   private Path customLadderTemplateDir;
   private int activeRankedLadderCount = createDefaultRankedLadders().size();
   private int actionTimerSeconds = 30;
   private List<ArenaServerConfig.RankedLadderConfig> rankedLadders = createDefaultRankedLadders();
   private int currentSeasonNumber = 1;
   private String currentSeasonId = "season_1";
   private String currentSeasonName = "Season 1";
   private long currentSeasonStartedAtMs = System.currentTimeMillis();
   private List<ArenaServerConfig.ArchivedSeasonInfo> completedRankedSeasons = new ArrayList<>();
   private ArenaServerConfig.RewardsConfig rewards = createDefaultRewards();

   private ArenaServerConfig() {
   }

   public static ArenaServerConfig getInstance() {
      return INSTANCE;
   }

   public synchronized void initialize(MinecraftServer server) {
      Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cobblemon_arena");

      try {
         Files.createDirectories(configDir);
      } catch (IOException var5) {
         CobblemonArena.LOGGER.error("Falha ao criar diretorio de configuracao da arena {}", configDir, var5);
      }

      this.configFile = configDir.resolve("server_config.json");
      this.customLadderTemplateDir = configDir.resolve("custom_ladders");

      try {
         Files.createDirectories(this.customLadderTemplateDir);
      } catch (IOException var4) {
         CobblemonArena.LOGGER.error("Falha ao criar diretorio de modelos de ladder customizada {}", this.customLadderTemplateDir, var4);
      }

      this.load();
   }

   public synchronized ArenaLadder getRankedLadder() {
      List<ArenaLadder> active = this.getActiveRankedLadders();
      return active.isEmpty() ? ArenaLadder.defaultRanked() : active.get(0);
   }

   public synchronized List<ArenaLadder> getActiveRankedLadders() {
      return buildActiveRankedLadders(this.rankedLadders, this.activeRankedLadderCount);
   }

   public synchronized List<ArenaServerConfig.RankedLadderConfig> getRankedLadderConfigs() {
      return copyRankedLadders(this.rankedLadders);
   }

   public synchronized int getActiveRankedLadderCount() {
      return this.activeRankedLadderCount;
   }

   public synchronized int getActionTimerSeconds() {
      return this.actionTimerSeconds;
   }

   public synchronized String getCurrentSeasonId() {
      return this.currentSeasonId;
   }

   public synchronized String getCurrentSeasonName() {
      return this.currentSeasonName;
   }

   public synchronized int getCurrentSeasonNumber() {
      return this.currentSeasonNumber;
   }

   public synchronized long getCurrentSeasonStartedAtMs() {
      return this.currentSeasonStartedAtMs;
   }

   public synchronized float getSeasonSoftResetFactor() {
      return 0.35F;
   }

   public synchronized List<ArenaServerConfig.ArchivedSeasonInfo> getCompletedRankedSeasons() {
      return copyArchivedSeasons(this.completedRankedSeasons);
   }

   public synchronized ArenaServerConfig.RewardsConfig getRewards() {
      return copyRewards(this.rewards);
   }

   public synchronized ArenaServerConfig.Snapshot toSnapshot() {
      ArenaServerConfig.Snapshot snapshot = new ArenaServerConfig.Snapshot();
      snapshot.setActiveRankedLadderCount(this.activeRankedLadderCount);
      snapshot.setActionTimerSeconds(this.actionTimerSeconds);
      snapshot.setRankedLadders(copyRankedLadders(this.rankedLadders));
      return snapshot;
   }

   public synchronized void setSnapshot(ArenaServerConfig.Snapshot snapshot) {
      ArenaServerConfig.Snapshot normalized = normalizeSnapshot(snapshot);
      this.activeRankedLadderCount = normalized.activeRankedLadderCount;
      this.actionTimerSeconds = normalized.actionTimerSeconds;
      this.rankedLadders = normalized.rankedLadders;
      this.applyActiveLadders();
      this.save();
   }

   private void load() {
      if (this.configFile != null) {
         if (!Files.exists(this.configFile)) {
            this.applyActiveLadders();
            this.save();
         } else {
            try (Reader reader = Files.newBufferedReader(this.configFile)) {
               ArenaServerConfig.StoredConfig loaded = (ArenaServerConfig.StoredConfig)GSON.fromJson(reader, ArenaServerConfig.StoredConfig.class);
               boolean migratedLegacyRankedLadders = false;
               ArenaServerConfig.Snapshot snapshot = new ArenaServerConfig.Snapshot();
               snapshot.activeRankedLadderCount = loaded != null && loaded.activeRankedLadderCount > 0
                  ? loaded.activeRankedLadderCount
                  : createDefaultRankedLadders().size();
               snapshot.actionTimerSeconds = loaded != null ? loaded.actionTimerSeconds : 30;
               if (loaded == null || loaded.rankedLadders == null || loaded.rankedLadders.isEmpty()) {
                  snapshot.rankedLadders = createDefaultRankedLadders();
                  if (loaded != null) {
                     snapshot.rankedLadders.get(0).format = normalizeFormat(loaded.rankedFormat);
                     snapshot.rankedLadders.get(0).level = normalizeLevel(loaded.rankedLevel);
                     snapshot.rankedLadders.get(0).name = buildDefaultName(snapshot.rankedLadders.get(0).format, snapshot.rankedLadders.get(0).level, 1);
                  }
               } else {
                  snapshot.rankedLadders = copyRankedLadders(loaded.rankedLadders);
               }

               ArenaServerConfig.Snapshot normalized = normalizeSnapshot(snapshot);
               this.activeRankedLadderCount = normalized.activeRankedLadderCount;
               this.actionTimerSeconds = normalized.actionTimerSeconds;
               this.rankedLadders = normalized.rankedLadders;
               this.currentSeasonNumber = normalizeSeasonNumber(loaded != null ? loaded.currentSeasonNumber : 1);
               this.currentSeasonId = normalizeSeasonId(loaded != null ? loaded.currentSeasonId : null, this.currentSeasonNumber);
               this.currentSeasonName = normalizeSeasonName(loaded != null ? loaded.currentSeasonName : null, this.currentSeasonNumber);
               this.currentSeasonStartedAtMs = normalizeSeasonStartedAtMs(loaded != null ? loaded.currentSeasonStartedAtMs : 0L);
               this.completedRankedSeasons = copyArchivedSeasons(loaded != null ? loaded.completedRankedSeasons : List.of());
               this.rewards = normalizeRewards(loaded != null ? loaded.rewards : null);
               this.applyActiveLadders();
               if (migratedLegacyRankedLadders) {
                  this.save();
               }
            } catch (Exception var8) {
               CobblemonArena.LOGGER.error("Falha ao carregar configuracao do servidor da Arena de {}", this.configFile, var8);
               ArenaServerConfig.Snapshot fallback = normalizeSnapshot(new ArenaServerConfig.Snapshot());
               this.activeRankedLadderCount = fallback.activeRankedLadderCount;
               this.actionTimerSeconds = fallback.actionTimerSeconds;
               this.rankedLadders = fallback.rankedLadders;
               this.currentSeasonNumber = 1;
               this.currentSeasonId = "season_1";
               this.currentSeasonName = "Season Teste";
               this.currentSeasonStartedAtMs = System.currentTimeMillis();
               this.completedRankedSeasons = new ArrayList<>();
               this.rewards = createDefaultRewards();
               this.applyActiveLadders();
               this.save();
            }
         }
      }
   }

   public synchronized void save() {
      if (this.configFile != null) {
         try (Writer writer = Files.newBufferedWriter(this.configFile)) {
            GSON.toJson(this.toStoredConfig(), writer);
         } catch (IOException var6) {
            CobblemonArena.LOGGER.error("Falha ao salvar configuracao do servidor da Arena em {}", this.configFile, var6);
         }
      }
   }

   public synchronized void saveCustomLadderTemplate(ArenaServerConfig.RankedLadderConfig config) {
      if (this.customLadderTemplateDir != null) {
         ArenaServerConfig.RankedLadderConfig normalized = normalizeSingleLadder(config, 1);
         this.deleteCustomLadderTemplate(normalized.getName());
         Path templateFile = this.templateFileForName(normalized.getName());

         try (Writer writer = Files.newBufferedWriter(templateFile)) {
            GSON.toJson(normalized, writer);
         } catch (IOException var9) {
            CobblemonArena.LOGGER.error("Falha ao salvar modelo de ladder customizada em {}", templateFile, var9);
         }
      }
   }

   public synchronized ArenaServerConfig.RankedLadderConfig loadCustomLadderTemplate(String templateName, int slotIndex) {
      if (this.customLadderTemplateDir == null) {
         return null;
      } else {
         Path templateFile = this.templateFileForName(templateName);
         if (!Files.exists(templateFile)) {
            return null;
         } else {
            try {
               ArenaServerConfig.RankedLadderConfig var6;
               try (Reader reader = Files.newBufferedReader(templateFile)) {
                  ArenaServerConfig.RankedLadderConfig config = (ArenaServerConfig.RankedLadderConfig)GSON.fromJson(
                     reader, ArenaServerConfig.RankedLadderConfig.class
                  );
                  var6 = normalizeSingleLadder(config, slotIndex + 1);
               }

               return var6;
            } catch (Exception var9) {
               CobblemonArena.LOGGER.error("Falha ao carregar modelo de ladder customizada de {}", templateFile, var9);
               return null;
            }
         }
      }
   }

   public synchronized boolean deleteCustomLadderTemplate(String templateName) {
      if (this.customLadderTemplateDir != null && Files.exists(this.customLadderTemplateDir)) {
         String targetName = templateName == null ? "" : templateName.trim();
         Path expectedFile = this.templateFileForName(targetName);
         boolean deletedAny = false;

         try (Stream<Path> stream = Files.list(this.customLadderTemplateDir)) {
            for (Path path : stream.filter(pathx -> pathx.getFileName().toString().endsWith(".json")).toList()) {
               boolean shouldDelete = path.equals(expectedFile);
               if (!shouldDelete) {
                  try (Reader reader = Files.newBufferedReader(path)) {
                     ArenaServerConfig.RankedLadderConfig config = (ArenaServerConfig.RankedLadderConfig)GSON.fromJson(
                        reader, ArenaServerConfig.RankedLadderConfig.class
                     );
                     ArenaServerConfig.RankedLadderConfig normalized = normalizeSingleLadder(config, 1);
                     shouldDelete = normalized.getName().equalsIgnoreCase(targetName);
                  } catch (Exception var16) {
                     CobblemonArena.LOGGER.error("Falha ao inspecionar modelo de ladder customizada {} para exclusao", path, var16);
                  }
               }

               if (shouldDelete) {
                  Files.deleteIfExists(path);
                  deletedAny = true;
               }
            }
         } catch (IOException var18) {
            CobblemonArena.LOGGER
               .error("Failed to delete custom ladder template(s) named {} from {}", new Object[]{templateName, this.customLadderTemplateDir, var18});
         }

         return deletedAny;
      } else {
         return false;
      }
   }

   public synchronized List<String> listSavedCustomLadderTemplateNames() {
      if (this.customLadderTemplateDir != null && Files.exists(this.customLadderTemplateDir)) {
         List<String> names = new ArrayList<>();

         try (Stream<Path> stream = Files.list(this.customLadderTemplateDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString()))
               .forEach(
                  path -> {
                     try (Reader reader = Files.newBufferedReader(path)) {
                        ArenaServerConfig.RankedLadderConfig config = (ArenaServerConfig.RankedLadderConfig)GSON.fromJson(
                           reader, ArenaServerConfig.RankedLadderConfig.class
                        );
                        ArenaServerConfig.RankedLadderConfig normalized = normalizeSingleLadder(config, 1);
                        if (normalized.getPresetKey().equals(ArenaRankedPreset.CUSTOM.getKey())
                           && names.stream().noneMatch(name -> name.equalsIgnoreCase(normalized.getName()))) {
                           names.add(normalized.getName());
                        }
                     } catch (Exception var7x) {
                        CobblemonArena.LOGGER.error("Falha ao inspecionar modelo de ladder customizada {}", path, var7x);
                     }
                  }
               );
         } catch (IOException var7) {
            CobblemonArena.LOGGER.error("Falha ao listar modelos de ladder customizada em {}", this.customLadderTemplateDir, var7);
         }

         return List.copyOf(names);
      } else {
         return List.of();
      }
   }

   public synchronized ArenaServerConfig.SeasonRolloverResult rolloverRankedSeason(String requestedName) {
      long endedAtMs = System.currentTimeMillis();
      ArenaServerConfig.ArchivedSeasonInfo previousSeason = new ArenaServerConfig.ArchivedSeasonInfo(
         this.currentSeasonId, this.currentSeasonName, this.currentSeasonNumber, this.currentSeasonStartedAtMs, endedAtMs
      );
      this.completedRankedSeasons.removeIf(season -> season.getSeasonId().equalsIgnoreCase(previousSeason.getSeasonId()));
      this.completedRankedSeasons.add(previousSeason);
      int nextSeasonNumber = this.currentSeasonNumber + 1;
      this.currentSeasonNumber = normalizeSeasonNumber(nextSeasonNumber);
      this.currentSeasonId = "season_" + this.currentSeasonNumber;
      this.currentSeasonName = normalizeSeasonName(requestedName, this.currentSeasonNumber);
      this.currentSeasonStartedAtMs = endedAtMs;
      this.save();
      return new ArenaServerConfig.SeasonRolloverResult(
         previousSeason,
         new ArenaServerConfig.CurrentSeasonInfo(this.currentSeasonId, this.currentSeasonName, this.currentSeasonNumber, this.currentSeasonStartedAtMs)
      );
   }

   private void applyActiveLadders() {
      ArenaLadder.setActiveRankedLadders(buildActiveRankedLadders(this.rankedLadders, this.activeRankedLadderCount));
   }

   public static String snapshotToJson(ArenaServerConfig.Snapshot snapshot) {
      return GSON.toJson(normalizeSnapshot(snapshot));
   }

   public static ArenaServerConfig.Snapshot copySnapshot(ArenaServerConfig.Snapshot snapshot) {
      return normalizeSnapshot(snapshot);
   }

   public static ArenaServerConfig.Snapshot snapshotFromJson(String json) {
      if (json != null && !json.isBlank()) {
         try {
            ArenaServerConfig.Snapshot snapshot = (ArenaServerConfig.Snapshot)GSON.fromJson(json, ArenaServerConfig.Snapshot.class);
            return normalizeSnapshot(snapshot);
         } catch (Exception var2) {
            return normalizeSnapshot(new ArenaServerConfig.Snapshot());
         }
      } else {
         return normalizeSnapshot(new ArenaServerConfig.Snapshot());
      }
   }

   public static String rankedLadderToJson(ArenaServerConfig.RankedLadderConfig config) {
      return GSON.toJson(normalizeSingleLadder(config, 1));
   }

   public static ArenaServerConfig.RankedLadderConfig rankedLadderFromJson(String json) {
      if (json != null && !json.isBlank()) {
         try {
            ArenaServerConfig.RankedLadderConfig config = (ArenaServerConfig.RankedLadderConfig)GSON.fromJson(json, ArenaServerConfig.RankedLadderConfig.class);
            return normalizeSingleLadder(config, 1);
         } catch (Exception var2) {
            return normalizeSingleLadder(new ArenaServerConfig.RankedLadderConfig(), 1);
         }
      } else {
         return normalizeSingleLadder(new ArenaServerConfig.RankedLadderConfig(), 1);
      }
   }

   private static ArenaServerConfig.Snapshot normalizeSnapshot(ArenaServerConfig.Snapshot snapshot) {
      ArenaServerConfig.Snapshot normalized = new ArenaServerConfig.Snapshot();
      if (snapshot == null) {
         normalized.setRankedLadders(createDefaultRankedLadders());
         return normalized;
      } else {
         normalized.setActiveRankedLadderCount(normalizeActiveCount(snapshot.activeRankedLadderCount));
         normalized.setActionTimerSeconds(normalizeActionTimerSeconds(snapshot.actionTimerSeconds));
         normalized.setRankedLadders(normalizeRankedLadders(snapshot.rankedLadders));
         return normalized;
      }
   }

   public static List<ArenaLadder> activeLaddersFromSnapshot(ArenaServerConfig.Snapshot snapshot) {
      ArenaServerConfig.Snapshot normalized = normalizeSnapshot(snapshot);
      return buildActiveRankedLadders(normalized.rankedLadders, normalized.activeRankedLadderCount);
   }

   private static List<ArenaServerConfig.RankedLadderConfig> normalizeRankedLadders(List<ArenaServerConfig.RankedLadderConfig> ladders) {
      List<ArenaServerConfig.RankedLadderConfig> defaults = createDefaultRankedLadders();
      List<ArenaServerConfig.RankedLadderConfig> normalized = new ArrayList<>();

      for (int i = 0; i < 8; i++) {
         ArenaServerConfig.RankedLadderConfig source;
         if (ladders != null && i < ladders.size()) {
             source = ladders.get(i);
         } else if (i < defaults.size()) {
             source = defaults.get(i);
         } else {
             source = defaultLadder("Singles", "50", i + 1);
         }
         normalized.add(normalizeSingleLadder(source, i + 1));
      }

      return normalized;
   }

   private static List<ArenaLadder> buildActiveRankedLadders(List<ArenaServerConfig.RankedLadderConfig> ladders, int activeCount) {
      List<ArenaServerConfig.RankedLadderConfig> normalized = normalizeRankedLadders(ladders);
      int count = Math.min(normalizeActiveCount(activeCount), normalized.size());
      List<ArenaLadder> built = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         built.add(buildRankedLadder(normalized.get(i), i + 1));
      }

      return built.isEmpty() ? List.of(ArenaLadder.defaultRanked()) : built;
   }

   public static ArenaLadder buildRankedLadder(ArenaServerConfig.RankedLadderConfig config, int slot) {
      ArenaServerConfig.RankedLadderConfig normalized = copyLadder(config);
      normalized.presetKey = normalizePresetKey(normalized.presetKey);
      ArenaRankedPreset preset = ArenaRankedPreset.fromKey(normalized.presetKey);
      return preset.isLockedPreset()
         ? ArenaLadder.rankedPreset(
            "ranked_slot_" + slot,
            preset.getDisplayName(),
            preset.getDescription(),
            preset.getBattleTypeId(),
            preset.getAdjustLevel(),
            preset.allowsRestrictedPokemon(),
            preset.enforcesSpeciesClause(),
            preset.enforcesItemClause(),
            new ArrayList<>(preset.getBannedSpecies()),
            new ArrayList<>(preset.getBannedItems()),
            new ArrayList<>(preset.getBannedAbilities()),
            new ArrayList<>(preset.getBannedMoves()),
            new ArrayList<>(preset.getBannedTiers()),
            preset.getShowdownRules()
         )
         : ArenaLadder.rankedPreset(
            "ranked_slot_" + slot,
            normalized.name,
            null,
            normalized.format,
            "100".equals(normalized.level) ? 100 : 50,
            normalized.allowRestrictedPokemon,
            normalized.enforceSpeciesClause,
            normalized.enforceItemClause,
            normalized.bannedPokemon,
            normalized.bannedItems,
            List.of(),
            normalized.bannedMoves,
            List.of(),
            List.of()
         );
   }

   private static List<ArenaServerConfig.RankedLadderConfig> createDefaultRankedLadders() {
      List<ArenaServerConfig.RankedLadderConfig> defaults = new ArrayList<>();
      defaults.add(defaultPresetLadder(ArenaRankedPreset.SOLO));
      defaults.add(defaultPresetLadder(ArenaRankedPreset.DUPLAS));
      defaults.add(defaultPresetLadder(ArenaRankedPreset.TRIPLAS));
      defaults.add(defaultPresetLadder(ArenaRankedPreset.MONOTYPE));
      return defaults;
   }

   private static ArenaServerConfig.RankedLadderConfig defaultPresetLadder(ArenaRankedPreset preset) {
      ArenaServerConfig.RankedLadderConfig config = new ArenaServerConfig.RankedLadderConfig();
      config.presetKey = preset.getKey();
      config.name = preset.getDisplayName();
      String var2 = preset.getBattleTypeId();

      config.format = switch (var2) {
         case "doubles" -> "Doubles";
         case "triples" -> "Triples";
         default -> "Singles";
      };
      config.level = String.valueOf(preset.getAdjustLevel());
      config.allowRestrictedPokemon = preset.allowsRestrictedPokemon();
      config.enforceSpeciesClause = preset.enforcesSpeciesClause();
      config.enforceItemClause = preset.enforcesItemClause();
      config.bannedPokemon = new ArrayList<>();
      config.bannedItems = new ArrayList<>();
      config.bannedMoves = new ArrayList<>();
      return config;
   }

   private static ArenaServerConfig.RankedLadderConfig defaultLadder(String format, String level, int slot) {
      ArenaServerConfig.RankedLadderConfig config = new ArenaServerConfig.RankedLadderConfig();
      config.name = buildDefaultName(format, level, slot);
      config.format = format;
      config.level = level;
      config.allowRestrictedPokemon = false;
      config.enforceSpeciesClause = true;
      config.enforceItemClause = true;
      config.bannedPokemon = new ArrayList<>();
      config.bannedItems = new ArrayList<>();
      config.bannedMoves = new ArrayList<>();
      return config;
   }

   private static String buildDefaultName(String format, String level, int slot) {
      return "Ranked "
         + normalizeFormat(format).substring(0, 1).toUpperCase(Locale.ROOT)
         + normalizeFormat(format).substring(1)
         + " Lv. "
         + normalizeLevel(level);
   }

   private static int normalizeActiveCount(int count) {
      return Math.max(1, Math.min(8, count));
   }

   private static String normalizePresetKey(String presetKey) {
      return ArenaRankedPreset.fromKey(presetKey).getKey();
   }

   private static String normalizeFormat(String format) {
      String var1 = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "doubles" -> "Doubles";
         case "triples" -> "Triples";
         default -> "Singles";
      };
   }

   private static String normalizeLevel(String level) {
      return switch (level) {
         case "5" -> "5";
         case "100" -> "100";
         default -> "50";
      };
   }

   private static int normalizeActionTimerSeconds(int seconds) {
      return switch (seconds) {
         case 15, 20, 30, 45, 60, 90, 120 -> seconds;
         default -> 30;
      };
   }

   private static int normalizeSeasonNumber(int seasonNumber) {
      return Math.max(1, seasonNumber);
   }

   private static String normalizeSeasonId(String seasonId, int seasonNumber) {
      return seasonId != null && !seasonId.isBlank() ? seasonId.trim().toLowerCase(Locale.ROOT) : "season_" + normalizeSeasonNumber(seasonNumber);
   }

   private static String normalizeSeasonName(String seasonName, int seasonNumber) {
      if (seasonName != null && !seasonName.isBlank()) return seasonName.trim();
      int normalized = normalizeSeasonNumber(seasonNumber);
      return normalized == 1 ? "Season Teste" : "Season " + normalized;
   }

   private static long normalizeSeasonStartedAtMs(long startedAtMs) {
      return startedAtMs > 0L ? startedAtMs : System.currentTimeMillis();
   }

   private static String normalizeName(String name, int slot, String format, String level) {
      return name != null && !name.isBlank() ? name.trim() : buildDefaultName(format, level, slot);
   }

   private static List<String> normalizeCsvList(List<String> values, boolean species) {
      List<String> normalized = new ArrayList<>();
      if (values == null) {
         return normalized;
      } else {
         for (String value : values) {
            String cleaned = species ? ArenaLadder.normalizeSpeciesKey(value) : ArenaLadder.normalizeItemKey(value);
            if (!cleaned.isBlank()) {
               normalized.add(cleaned);
            }
         }

         return normalized;
      }
   }

   private static List<String> normalizeRuleCsvList(List<String> values) {
      List<String> normalized = new ArrayList<>();
      if (values == null) {
         return normalized;
      } else {
         for (String value : values) {
            String cleaned = ArenaLadder.normalizeRuleKey(value);
            if (!cleaned.isBlank()) {
               normalized.add(cleaned);
            }
         }

         return normalized;
      }
   }

   private static ArenaServerConfig.RankedLadderConfig normalizeSingleLadder(ArenaServerConfig.RankedLadderConfig config, int slot) {
      ArenaServerConfig.RankedLadderConfig normalized = copyLadder(config);
      normalized.presetKey = normalizePresetKey(normalized.presetKey);
      normalized.name = normalizeName(normalized.name, slot, normalized.format, normalized.level);
      normalized.format = normalizeFormat(normalized.format);
      normalized.level = normalizeLevel(normalized.level);
      normalized.bannedPokemon = normalizeCsvList(normalized.bannedPokemon, true);
      normalized.bannedItems = normalizeCsvList(normalized.bannedItems, false);
      normalized.bannedMoves = normalizeRuleCsvList(normalized.bannedMoves);
      return normalized;
   }

   private static ArenaServerConfig.RankedLadderConfig copyLadder(ArenaServerConfig.RankedLadderConfig config) {
      ArenaServerConfig.RankedLadderConfig copy = new ArenaServerConfig.RankedLadderConfig();
      if (config == null) {
         return copy;
      } else {
         copy.presetKey = config.presetKey;
         copy.name = config.name;
         copy.format = config.format;
         copy.level = config.level;
         copy.allowRestrictedPokemon = config.allowRestrictedPokemon;
         copy.enforceSpeciesClause = config.enforceSpeciesClause;
         copy.enforceItemClause = config.enforceItemClause;
         copy.bannedPokemon = config.bannedPokemon == null ? new ArrayList<>() : new ArrayList<>(config.bannedPokemon);
         copy.bannedItems = config.bannedItems == null ? new ArrayList<>() : new ArrayList<>(config.bannedItems);
         copy.bannedMoves = config.bannedMoves == null ? new ArrayList<>() : new ArrayList<>(config.bannedMoves);
         return copy;
      }
   }


   private Path templateFileForName(String templateName) {
      String normalizedName = normalizeTemplateFileName(templateName);
      return this.customLadderTemplateDir.resolve(normalizedName + ".json");
   }

   private static String normalizeTemplateFileName(String templateName) {
      String raw = templateName == null ? "" : templateName.trim().toLowerCase(Locale.ROOT);
      String cleaned = raw.replaceAll("[^a-z0-9_-]+", "_").replaceAll("_+", "_");
      cleaned = cleaned.replaceAll("^_+", "").replaceAll("_+$", "");
      return cleaned.isBlank() ? "custom_ladder" : cleaned;
   }

   private static List<ArenaServerConfig.RankedLadderConfig> copyRankedLadders(List<ArenaServerConfig.RankedLadderConfig> ladders) {
      List<ArenaServerConfig.RankedLadderConfig> copies = new ArrayList<>();
      if (ladders == null) {
         return copies;
      } else {
         for (ArenaServerConfig.RankedLadderConfig ladder : ladders) {
            copies.add(copyLadder(ladder));
         }

         return copies;
      }
   }

   private ArenaServerConfig.StoredConfig toStoredConfig() {
      ArenaServerConfig.StoredConfig stored = new ArenaServerConfig.StoredConfig();
      stored.activeRankedLadderCount = this.activeRankedLadderCount;
      stored.actionTimerSeconds = this.actionTimerSeconds;
      stored.rankedLadders = copyRankedLadders(this.rankedLadders);
      stored.currentSeasonNumber = this.currentSeasonNumber;
      stored.currentSeasonId = this.currentSeasonId;
      stored.currentSeasonName = this.currentSeasonName;
      stored.currentSeasonStartedAtMs = this.currentSeasonStartedAtMs;
      stored.completedRankedSeasons = copyArchivedSeasons(this.completedRankedSeasons);
      stored.rewards = copyRewards(this.rewards);
      return stored;
   }

   private static List<ArenaServerConfig.ArchivedSeasonInfo> copyArchivedSeasons(List<ArenaServerConfig.ArchivedSeasonInfo> seasons) {
      List<ArenaServerConfig.ArchivedSeasonInfo> copies = new ArrayList<>();
      if (seasons == null) {
         return copies;
      } else {
         for (ArenaServerConfig.ArchivedSeasonInfo season : seasons) {
            if (season != null) {
               copies.add(
                  new ArenaServerConfig.ArchivedSeasonInfo(season.seasonId, season.seasonName, season.seasonNumber, season.startedAtMs, season.endedAtMs)
               );
            }
         }

         return copies;
      }
   }

   private static ArenaServerConfig.RewardsConfig createDefaultRewards() {
      ArenaServerConfig.RewardsConfig rewards = new ArenaServerConfig.RewardsConfig();
      rewards.milestoneRewardsEnabled = true;
      rewards.seasonRewardsEnabled = true;
      rewards.milestoneRewards = createDefaultMilestoneRewards();
      rewards.seasonEndRewards = createDefaultSeasonEndRewards();
      return rewards;
   }

   private static List<ArenaServerConfig.MilestoneRewardConfig> createDefaultMilestoneRewards() {
      List<ArenaServerConfig.MilestoneRewardConfig> defaults = new ArrayList<>();
      defaults.add(
         new ArenaServerConfig.MilestoneRewardConfig(
            "ranked_wins_10", "10 Ranked Wins", ArenaServerConfig.RewardStatType.RANKED_WINS.name(), 10, new ArrayList<>()
         )
      );
      defaults.add(
         new ArenaServerConfig.MilestoneRewardConfig(
            "ranked_wins_25", "25 Ranked Wins", ArenaServerConfig.RewardStatType.RANKED_WINS.name(), 25, new ArrayList<>()
         )
      );
      defaults.add(
         new ArenaServerConfig.MilestoneRewardConfig(
            "arena_matches_50", "50 Arena Matches", ArenaServerConfig.RewardStatType.TOTAL_BATTLES.name(), 50, new ArrayList<>()
         )
      );
      return defaults;
   }

   private static ArenaServerConfig.SeasonEndRewardsConfig createDefaultSeasonEndRewards() {
      ArenaServerConfig.SeasonEndRewardsConfig seasonRewards = new ArenaServerConfig.SeasonEndRewardsConfig();
      seasonRewards.placementRewards = new ArrayList<>();
      seasonRewards.placementRewards.add(new ArenaServerConfig.PlacementRewardConfig(1, 5, new ArrayList<>()));
      seasonRewards.placementRewards.add(new ArenaServerConfig.PlacementRewardConfig(3, 10, new ArrayList<>()));
      seasonRewards.placementRewards.add(new ArenaServerConfig.PlacementRewardConfig(10, 20, new ArrayList<>()));
      seasonRewards.participationReward = new ArenaServerConfig.ParticipationRewardConfig(15, new ArrayList<>());
      return seasonRewards;
   }

   private static ArenaServerConfig.RewardsConfig normalizeRewards(ArenaServerConfig.RewardsConfig rewards) {
      ArenaServerConfig.RewardsConfig normalized = new ArenaServerConfig.RewardsConfig();
      ArenaServerConfig.RewardsConfig source = rewards == null ? createDefaultRewards() : rewards;
      normalized.milestoneRewardsEnabled = source.milestoneRewardsEnabled;
      normalized.seasonRewardsEnabled = source.seasonRewardsEnabled;
      normalized.milestoneRewards = new ArrayList<>();

      for (ArenaServerConfig.MilestoneRewardConfig milestone : source.milestoneRewards != null && !source.milestoneRewards.isEmpty()
         ? source.milestoneRewards
         : createDefaultMilestoneRewards()) {
         normalized.milestoneRewards.add(normalizeMilestoneReward(milestone));
      }

      normalized.seasonEndRewards = normalizeSeasonEndRewards(source.seasonEndRewards);
      return normalized;
   }

   private static ArenaServerConfig.RewardsConfig copyRewards(ArenaServerConfig.RewardsConfig rewards) {
      return normalizeRewards(rewards);
   }

   private static ArenaServerConfig.MilestoneRewardConfig normalizeMilestoneReward(ArenaServerConfig.MilestoneRewardConfig milestone) {
      ArenaServerConfig.MilestoneRewardConfig normalized = new ArenaServerConfig.MilestoneRewardConfig();
      if (milestone == null) {
         return normalized;
      } else {
         normalized.id = milestone.id != null && !milestone.id.isBlank() ? milestone.id.trim().toLowerCase(Locale.ROOT) : "arena_milestone";
         normalized.title = milestone.title != null && !milestone.title.isBlank() ? milestone.title.trim() : normalized.id;
         normalized.statType = ArenaServerConfig.RewardStatType.fromName(milestone.statType).name();
         normalized.threshold = Math.max(1, milestone.threshold);
         normalized.commands = copyCommandList(milestone.commands);
         return normalized;
      }
   }

   private static ArenaServerConfig.SeasonEndRewardsConfig normalizeSeasonEndRewards(ArenaServerConfig.SeasonEndRewardsConfig rewards) {
      ArenaServerConfig.SeasonEndRewardsConfig normalized = new ArenaServerConfig.SeasonEndRewardsConfig();
      ArenaServerConfig.SeasonEndRewardsConfig source = rewards == null ? createDefaultSeasonEndRewards() : rewards;
      normalized.placementRewards = new ArrayList<>();

      for (ArenaServerConfig.PlacementRewardConfig reward : source.placementRewards == null ? List.<ArenaServerConfig.PlacementRewardConfig>of() : source.placementRewards) {
         normalized.placementRewards.add(normalizePlacementReward(reward));
      }

      normalized.placementRewards.sort(Comparator.comparingInt(ArenaServerConfig.PlacementRewardConfig::getMaxPlacement));
      normalized.participationReward = normalizeParticipationReward(source.participationReward);
      return normalized;
   }

   private static ArenaServerConfig.PlacementRewardConfig normalizePlacementReward(ArenaServerConfig.PlacementRewardConfig reward) {
      ArenaServerConfig.PlacementRewardConfig normalized = new ArenaServerConfig.PlacementRewardConfig();
      if (reward == null) {
         return normalized;
      } else {
         normalized.maxPlacement = Math.max(1, reward.maxPlacement);
         normalized.minimumGames = Math.max(0, reward.minimumGames);
         normalized.commands = copyCommandList(reward.commands);
         return normalized;
      }
   }

   private static ArenaServerConfig.ParticipationRewardConfig normalizeParticipationReward(ArenaServerConfig.ParticipationRewardConfig reward) {
      ArenaServerConfig.ParticipationRewardConfig normalized = new ArenaServerConfig.ParticipationRewardConfig();
      if (reward == null) {
         return normalized;
      } else {
         normalized.minimumGames = Math.max(1, reward.minimumGames);
         normalized.commands = copyCommandList(reward.commands);
         return normalized;
      }
   }

   private static List<String> copyCommandList(List<String> commands) {
      List<String> copied = new ArrayList<>();
      if (commands == null) {
         return copied;
      } else {
         for (String command : commands) {
            if (command != null && !command.isBlank()) {
               copied.add(command.trim());
            }
         }

         return copied;
      }
   }

   public static final class ArchivedSeasonInfo {
      private String seasonId = "";
      private String seasonName = "";
      private int seasonNumber = 1;
      private long startedAtMs;
      private long endedAtMs;

      public ArchivedSeasonInfo() {
      }

      public ArchivedSeasonInfo(String seasonId, String seasonName, int seasonNumber, long startedAtMs, long endedAtMs) {
         this.seasonId = ArenaServerConfig.normalizeSeasonId(seasonId, seasonNumber);
         this.seasonName = ArenaServerConfig.normalizeSeasonName(seasonName, seasonNumber);
         this.seasonNumber = ArenaServerConfig.normalizeSeasonNumber(seasonNumber);
         this.startedAtMs = startedAtMs;
         this.endedAtMs = endedAtMs;
      }

      public String getSeasonId() {
         return this.seasonId;
      }

      public String getSeasonName() {
         return this.seasonName;
      }

      public int getSeasonNumber() {
         return this.seasonNumber;
      }

      public long getStartedAtMs() {
         return this.startedAtMs;
      }

      public long getEndedAtMs() {
         return this.endedAtMs;
      }
   }

   public static final class CurrentSeasonInfo {
      private final String seasonId;
      private final String seasonName;
      private final int seasonNumber;
      private final long startedAtMs;

      public CurrentSeasonInfo(String seasonId, String seasonName, int seasonNumber, long startedAtMs) {
         this.seasonId = seasonId;
         this.seasonName = seasonName;
         this.seasonNumber = seasonNumber;
         this.startedAtMs = startedAtMs;
      }

      public String getSeasonId() {
         return this.seasonId;
      }

      public String getSeasonName() {
         return this.seasonName;
      }

      public int getSeasonNumber() {
         return this.seasonNumber;
      }

      public long getStartedAtMs() {
         return this.startedAtMs;
      }
   }

   public static final class MilestoneRewardConfig {
      private String id = "arena_milestone";
      private String title = "Arena Milestone";
      private String statType = ArenaServerConfig.RewardStatType.TOTAL_BATTLES.name();
      private int threshold = 1;
      private List<String> commands = new ArrayList<>();

      public MilestoneRewardConfig() {
      }

      public MilestoneRewardConfig(String id, String title, String statType, int threshold, List<String> commands) {
         this.id = id;
         this.title = title;
         this.statType = statType;
         this.threshold = threshold;
         this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
      }

      public String getId() {
         return this.id;
      }

      public String getTitle() {
         return this.title;
      }

      public String getStatType() {
         return this.statType;
      }

      public int getThreshold() {
         return this.threshold;
      }

      public List<String> getCommands() {
         return this.commands;
      }
   }

   public static final class ParticipationRewardConfig {
      private int minimumGames = 15;
      private List<String> commands = new ArrayList<>();

      public ParticipationRewardConfig() {
      }

      public ParticipationRewardConfig(int minimumGames, List<String> commands) {
         this.minimumGames = minimumGames;
         this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
      }

      public int getMinimumGames() {
         return this.minimumGames;
      }

      public List<String> getCommands() {
         return this.commands;
      }
   }

   public static final class PlacementRewardConfig {
      private int maxPlacement = 1;
      private int minimumGames = 0;
      private List<String> commands = new ArrayList<>();

      public PlacementRewardConfig() {
      }

      public PlacementRewardConfig(int maxPlacement, int minimumGames, List<String> commands) {
         this.maxPlacement = maxPlacement;
         this.minimumGames = minimumGames;
         this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
      }

      public int getMaxPlacement() {
         return this.maxPlacement;
      }

      public int getMinimumGames() {
         return this.minimumGames;
      }

      public List<String> getCommands() {
         return this.commands;
      }
   }

   public static final class RankedLadderConfig {
      private String name = "Ranked Singles Lv. 50";
      private String presetKey = ArenaRankedPreset.CUSTOM.getKey();
      private String format = "Singles";
      private String level = "50";
      private boolean allowRestrictedPokemon = false;
      private boolean enforceSpeciesClause = true;
      private boolean enforceItemClause = true;
      private List<String> bannedPokemon = new ArrayList<>();
      private List<String> bannedItems = new ArrayList<>();
      private List<String> bannedMoves = new ArrayList<>();

      public String getName() {
         return this.name;
      }

      public String getPresetKey() {
         return this.presetKey;
      }

      public String getFormat() {
         return this.format;
      }

      public String getLevel() {
         return this.level;
      }

      public boolean isAllowRestrictedPokemon() {
         return this.allowRestrictedPokemon;
      }

      public boolean isEnforceSpeciesClause() {
         return this.enforceSpeciesClause;
      }

      public boolean isEnforceItemClause() {
         return this.enforceItemClause;
      }

      public List<String> getBannedPokemon() {
         return this.bannedPokemon;
      }

      public List<String> getBannedItems() {
         return this.bannedItems;
      }

      public List<String> getBannedMoves() {
         return this.bannedMoves;
      }

      public void setName(String name) {
         this.name = name;
      }

      public void setPresetKey(String presetKey) {
         this.presetKey = presetKey;
      }

      public void setFormat(String format) {
         this.format = format;
      }

      public void setLevel(String level) {
         this.level = level;
      }

      public void setAllowRestrictedPokemon(boolean allowRestrictedPokemon) {
         this.allowRestrictedPokemon = allowRestrictedPokemon;
      }

      public void setEnforceSpeciesClause(boolean enforceSpeciesClause) {
         this.enforceSpeciesClause = enforceSpeciesClause;
      }

      public void setEnforceItemClause(boolean enforceItemClause) {
         this.enforceItemClause = enforceItemClause;
      }

      public void setBannedPokemon(List<String> bannedPokemon) {
         this.bannedPokemon = bannedPokemon;
      }

      public void setBannedItems(List<String> bannedItems) {
         this.bannedItems = bannedItems;
      }

      public void setBannedMoves(List<String> bannedMoves) {
         this.bannedMoves = bannedMoves;
      }
   }

   public static enum RewardStatType {
      TOTAL_BATTLES,
      RANKED_WINS,
      RANKED_MATCHES,
      QUICK_WINS,
      QUICK_MATCHES;

      public static ArenaServerConfig.RewardStatType fromName(String name) {
         if (name != null && !name.isBlank()) {
            try {
               return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
               return TOTAL_BATTLES;
            }
         } else {
            return TOTAL_BATTLES;
         }
      }
   }

   public static final class RewardsConfig {
      private boolean milestoneRewardsEnabled = true;
      private boolean seasonRewardsEnabled = true;
      private List<ArenaServerConfig.MilestoneRewardConfig> milestoneRewards = ArenaServerConfig.createDefaultMilestoneRewards();
      private ArenaServerConfig.SeasonEndRewardsConfig seasonEndRewards = ArenaServerConfig.createDefaultSeasonEndRewards();

      public boolean isMilestoneRewardsEnabled() {
         return this.milestoneRewardsEnabled;
      }

      public boolean isSeasonRewardsEnabled() {
         return this.seasonRewardsEnabled;
      }

      public List<ArenaServerConfig.MilestoneRewardConfig> getMilestoneRewards() {
         return this.milestoneRewards;
      }

      public ArenaServerConfig.SeasonEndRewardsConfig getSeasonEndRewards() {
         return this.seasonEndRewards;
      }
   }

   public static final class SeasonEndRewardsConfig {
      private List<ArenaServerConfig.PlacementRewardConfig> placementRewards = new ArrayList<>();
      private ArenaServerConfig.ParticipationRewardConfig participationReward = new ArenaServerConfig.ParticipationRewardConfig();

      public List<ArenaServerConfig.PlacementRewardConfig> getPlacementRewards() {
         return this.placementRewards;
      }

      public ArenaServerConfig.ParticipationRewardConfig getParticipationReward() {
         return this.participationReward;
      }
   }

   public static final class SeasonRolloverResult {
      private final ArenaServerConfig.ArchivedSeasonInfo previousSeason;
      private final ArenaServerConfig.CurrentSeasonInfo currentSeason;

      public SeasonRolloverResult(ArenaServerConfig.ArchivedSeasonInfo previousSeason, ArenaServerConfig.CurrentSeasonInfo currentSeason) {
         this.previousSeason = previousSeason;
         this.currentSeason = currentSeason;
      }

      public ArenaServerConfig.ArchivedSeasonInfo getPreviousSeason() {
         return this.previousSeason;
      }

      public ArenaServerConfig.CurrentSeasonInfo getCurrentSeason() {
         return this.currentSeason;
      }
   }

   public static final class Snapshot {
      private int activeRankedLadderCount = ArenaServerConfig.createDefaultRankedLadders().size();
      private int actionTimerSeconds = 30;
      private List<ArenaServerConfig.RankedLadderConfig> rankedLadders = ArenaServerConfig.createDefaultRankedLadders();

      public int getActiveRankedLadderCount() {
         return this.activeRankedLadderCount;
      }

      public int getActionTimerSeconds() {
         return this.actionTimerSeconds;
      }

      public List<ArenaServerConfig.RankedLadderConfig> getRankedLadders() {
         return this.rankedLadders;
      }

      public void setActiveRankedLadderCount(int activeRankedLadderCount) {
         this.activeRankedLadderCount = activeRankedLadderCount;
      }

      public void setActionTimerSeconds(int actionTimerSeconds) {
         this.actionTimerSeconds = actionTimerSeconds;
      }

      public void setRankedLadders(List<ArenaServerConfig.RankedLadderConfig> rankedLadders) {
         this.rankedLadders = rankedLadders;
      }
   }

   private static final class StoredConfig {
      private int activeRankedLadderCount = ArenaServerConfig.createDefaultRankedLadders().size();
      private int actionTimerSeconds = 30;
      private List<ArenaServerConfig.RankedLadderConfig> rankedLadders = ArenaServerConfig.createDefaultRankedLadders();
      private int currentSeasonNumber = 1;
      private String currentSeasonId = "season_1";
      private String currentSeasonName = "Season Teste";
      private long currentSeasonStartedAtMs = System.currentTimeMillis();
      private List<ArenaServerConfig.ArchivedSeasonInfo> completedRankedSeasons = new ArrayList<>();
      private ArenaServerConfig.RewardsConfig rewards = ArenaServerConfig.createDefaultRewards();
      private String rankedFormat = "Singles";
      private String rankedLevel = "50";
   }
}
