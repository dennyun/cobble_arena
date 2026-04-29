package cobblemon.arena.stats;

import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaTransitionPokemonEntryPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

public class PlayerStats {

    private static final int DEFAULT_RANKED_RATING = 0;
    private static final int MAX_RECENT_MATCHES = 10;
    private final UUID playerUUID;
    private String playerName;
    private int rankedRating;
    private int rankedWins;
    private int rankedLosses;
    private int rankedStreak;
    private int rankedBestStreak;
    private Map<String, PlayerStats.RankedLadderStats> rankedLadders =
        new LinkedHashMap<>();
    private String currentSeasonId = "";
    private String currentSeasonName = "";
    private long currentSeasonStartedAtMs = 0L;
    private Map<String, PlayerStats.ArchivedSeasonStats> archivedRankedSeasons =
        new LinkedHashMap<>();
    private Set<String> grantedRewardMilestones = new LinkedHashSet<>();
    private int quickWins;
    private int quickLosses;
    private List<PlayerStats.RecentMatchStats> recentMatches =
        new ArrayList<>();
    private Map<String, PlayerStats.PokemonUsageStats> pokemonUsage =
        new LinkedHashMap<>();
    private long lastPlayedTime;
    private int totalBattles;
    private transient boolean legacyRankedMigrated;

    public PlayerStats(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.rankedRating = DEFAULT_RANKED_RATING; // 0 — new players start as Sem Rank
        this.rankedWins = 0;
        this.rankedLosses = 0;
        this.rankedStreak = 0;
        this.rankedBestStreak = 0;
        this.quickWins = 0;
        this.quickLosses = 0;
        this.lastPlayedTime = System.currentTimeMillis();
        this.totalBattles = 0;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getRankedRating() {
        return this.getRankedRating(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedRating(String ladderId) {
        return this.getExistingRankedLadderStats(ladderId).getRankedRating();
    }

    public int getRankedWins() {
        return this.getRankedWins(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedWins(String ladderId) {
        return this.getExistingRankedLadderStats(ladderId).getRankedWins();
    }

    public int getRankedLosses() {
        return this.getRankedLosses(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedLosses(String ladderId) {
        return this.getExistingRankedLadderStats(ladderId).getRankedLosses();
    }

    public int getRankedStreak() {
        return this.getRankedStreak(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedStreak(String ladderId) {
        return this.getExistingRankedLadderStats(ladderId).getRankedStreak();
    }

    public int getRankedBestStreak() {
        return this.getRankedBestStreak(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedBestStreak(String ladderId) {
        return this.getExistingRankedLadderStats(
            ladderId
        ).getRankedBestStreak();
    }

    public int getQuickWins() {
        return this.quickWins;
    }

    public int getQuickLosses() {
        return this.quickLosses;
    }

    public int getLifetimeRankedWins() {
        int total = 0;
        if (this.rankedLadders != null) {
            for (PlayerStats.RankedLadderStats stats : this.rankedLadders.values()) {
                total += stats.getRankedWins();
            }
        }

        if (this.archivedRankedSeasons != null) {
            for (PlayerStats.ArchivedSeasonStats archived : this.archivedRankedSeasons.values()) {
                if (archived != null && archived.rankedLadders != null) {
                    for (PlayerStats.RankedLadderStats stats : archived.rankedLadders.values()) {
                        total += stats.getRankedWins();
                    }
                }
            }
        }

        return total;
    }

    public int getLifetimeRankedMatches() {
        int total = 0;
        if (this.rankedLadders != null) {
            for (PlayerStats.RankedLadderStats stats : this.rankedLadders.values()) {
                total += stats.getTotalGames();
            }
        }

        if (this.archivedRankedSeasons != null) {
            for (PlayerStats.ArchivedSeasonStats archived : this.archivedRankedSeasons.values()) {
                if (archived != null && archived.rankedLadders != null) {
                    for (PlayerStats.RankedLadderStats stats : archived.rankedLadders.values()) {
                        total += stats.getTotalGames();
                    }
                }
            }
        }

        return total;
    }

    public int getQuickMatches() {
        return this.quickWins + this.quickLosses;
    }

    public long getLastPlayedTime() {
        return this.lastPlayedTime;
    }

    public int getTotalBattles() {
        return this.totalBattles;
    }

    public List<PlayerStats.RecentMatchStats> getRecentMatches() {
        return (List<PlayerStats.RecentMatchStats>) (this.recentMatches !=
            null &&
        !this.recentMatches.isEmpty()
            ? new ArrayList<>(
                this.recentMatches.subList(
                        0,
                        Math.min(MAX_RECENT_MATCHES, this.recentMatches.size())
                    )
            )
            : List.of());
    }

    public List<PlayerStats.PokemonUsageStats> getPokemonUsageStats() {
        return (List<PlayerStats.PokemonUsageStats>) (this.pokemonUsage !=
            null &&
        !this.pokemonUsage.isEmpty()
            ? new ArrayList<>(this.pokemonUsage.values())
            : List.of());
    }

    public int getRankedTotalGames() {
        return this.getRankedTotalGames(ArenaLadder.defaultRanked().getId());
    }

    public int getRankedTotalGames(String ladderId) {
        PlayerStats.RankedLadderStats stats = this.getExistingRankedLadderStats(
            ladderId
        );
        return stats.getRankedWins() + stats.getRankedLosses();
    }

    public int getQuickTotalGames() {
        return this.quickWins + this.quickLosses;
    }

    public double getRankedWinRate() {
        return this.getRankedWinRate(ArenaLadder.defaultRanked().getId());
    }

    public double getRankedWinRate(String ladderId) {
        int total = this.getRankedTotalGames(ladderId);
        return total == 0
            ? 0.0
            : ((double) this.getRankedWins(ladderId) / total) * 100.0;
    }

    public double getQuickWinRate() {
        int total = this.getQuickTotalGames();
        return total == 0 ? 0.0 : ((double) this.quickWins / total) * 100.0;
    }

    public void recordRankedMatch(boolean won, int ratingChange) {
        this.recordRankedMatch(
            ArenaLadder.defaultRanked().getId(),
            won,
            ratingChange
        );
    }

    public void recordRankedMatch(
        String ladderId,
        boolean won,
        int ratingChange
    ) {
        PlayerStats.RankedLadderStats stats = this.getOrCreateRankedLadderStats(
            ladderId
        );
        stats.rankedRating += ratingChange;
        this.totalBattles++;
        this.lastPlayedTime = System.currentTimeMillis();
        if (won) {
            stats.rankedWins++;
            stats.rankedStreak++;
            if (stats.rankedStreak > stats.rankedBestStreak) {
                stats.rankedBestStreak = stats.rankedStreak;
            }
        } else {
            stats.rankedLosses++;
            stats.rankedStreak = 0;
        }
    }

    public void recordQuickMatch(boolean won) {
        this.totalBattles++;
        this.lastPlayedTime = System.currentTimeMillis();
        if (won) {
            this.quickWins++;
        } else {
            this.quickLosses++;
        }
    }

    public void recordProfileMatch(
        boolean ranked,
        boolean won,
        String ladderDisplayName,
        String opponentName,
        int ratingDelta,
        int ratingAfter,
        List<PlayerStats.PokemonUsageRecordInput> teamPokemon,
        List<ArenaTransitionPokemonEntryPayload> ownTeam,
        List<ArenaTransitionPokemonEntryPayload> opponentTeam
    ) {
        long playedAt = System.currentTimeMillis();
        this.addRecentMatch(
            new PlayerStats.RecentMatchStats(
                ranked,
                won,
                ladderDisplayName,
                opponentName,
                ratingDelta,
                ratingAfter,
                playedAt,
                ownTeam,
                opponentTeam
            )
        );
        this.updatePokemonUsage(teamPokemon, won);
        this.lastPlayedTime = playedAt;
    }

    public PlayerStats.ArchivedSeasonStats getArchivedSeasonStats(
        String seasonId
    ) {
        if (
            seasonId != null &&
            !seasonId.isBlank() &&
            this.archivedRankedSeasons != null
        ) {
            PlayerStats.ArchivedSeasonStats archived =
                this.archivedRankedSeasons.get(seasonId);
            return archived == null
                ? null
                : new PlayerStats.ArchivedSeasonStats(
                      archived.seasonId,
                      archived.seasonName,
                      archived.startedAtMs,
                      archived.endedAtMs,
                      archived.rankedLadders
                  );
        } else {
            return null;
        }
    }

    public boolean hasGrantedRewardMilestone(String milestoneId) {
        return (
            milestoneId != null &&
            !milestoneId.isBlank() &&
            this.grantedRewardMilestones != null &&
            this.grantedRewardMilestones.contains(milestoneId)
        );
    }

    public void markRewardMilestoneGranted(String milestoneId) {
        if (milestoneId != null && !milestoneId.isBlank()) {
            if (this.grantedRewardMilestones == null) {
                this.grantedRewardMilestones = new LinkedHashSet<>();
            }

            this.grantedRewardMilestones.add(milestoneId);
        }
    }

    public void syncCurrentSeason(
        String seasonId,
        String seasonName,
        long seasonStartedAtMs,
        float carryResetFactor
    ) {
        this.migrateLegacyRankedStats();
        if (this.archivedRankedSeasons == null) {
            this.archivedRankedSeasons = new LinkedHashMap<>();
        }

        if (seasonId != null && !seasonId.isBlank()) {
            if (
                this.currentSeasonId == null || this.currentSeasonId.isBlank()
            ) {
                this.currentSeasonId = seasonId;
                this.currentSeasonName =
                    seasonName != null && !seasonName.isBlank()
                        ? seasonName
                        : seasonId;
                this.currentSeasonStartedAtMs = seasonStartedAtMs;
            } else if (!this.currentSeasonId.equalsIgnoreCase(seasonId)) {
                this.beginNewRankedSeason(
                    seasonId,
                    seasonName,
                    seasonStartedAtMs,
                    1000,
                    carryResetFactor
                );
            } else {
                this.currentSeasonName =
                    seasonName != null && !seasonName.isBlank()
                        ? seasonName
                        : this.currentSeasonName;
                this.currentSeasonStartedAtMs =
                    seasonStartedAtMs > 0L
                        ? seasonStartedAtMs
                        : this.currentSeasonStartedAtMs;
            }
        }
    }

    public void beginNewRankedSeason(
        String seasonId,
        String seasonName,
        long seasonStartedAtMs,
        int baselineRating,
        float carryResetFactor
    ) {
        this.migrateLegacyRankedStats();
        if (this.archivedRankedSeasons == null) {
            this.archivedRankedSeasons = new LinkedHashMap<>();
        }

        if (this.rankedLadders == null) {
            this.rankedLadders = new LinkedHashMap<>();
        }

        if (seasonId != null && !seasonId.isBlank()) {
            if (seasonId.equalsIgnoreCase(this.currentSeasonId)) {
                this.currentSeasonName =
                    seasonName != null && !seasonName.isBlank()
                        ? seasonName
                        : this.currentSeasonName;
                this.currentSeasonStartedAtMs =
                    seasonStartedAtMs > 0L
                        ? seasonStartedAtMs
                        : this.currentSeasonStartedAtMs;
            } else {
                if (
                    this.currentSeasonId != null &&
                    !this.currentSeasonId.isBlank()
                ) {
                    this.archivedRankedSeasons.put(
                        this.currentSeasonId,
                        new PlayerStats.ArchivedSeasonStats(
                            this.currentSeasonId,
                            this.currentSeasonName,
                            this.currentSeasonStartedAtMs,
                            System.currentTimeMillis(),
                            copyRankedLadders(this.rankedLadders)
                        )
                    );
                }

                Map<String, PlayerStats.RankedLadderStats> nextSeasonRatings =
                    new LinkedHashMap<>();

                for (Entry<
                    String,
                    PlayerStats.RankedLadderStats
                > entry : this.rankedLadders.entrySet()) {
                    PlayerStats.RankedLadderStats previous = entry.getValue();
                    if (previous != null) {
                        PlayerStats.RankedLadderStats reset =
                            new PlayerStats.RankedLadderStats();
                        int carriedRating =
                            baselineRating +
                            Math.round(
                                (previous.getRankedRating() - baselineRating) *
                                    carryResetFactor
                            );
                        reset.rankedRating = Math.max(
                            baselineRating,
                            carriedRating
                        );
                        nextSeasonRatings.put(entry.getKey(), reset);
                    }
                }

                this.rankedLadders = nextSeasonRatings;
                this.currentSeasonId = seasonId;
                this.currentSeasonName =
                    seasonName != null && !seasonName.isBlank()
                        ? seasonName
                        : seasonId;
                this.currentSeasonStartedAtMs =
                    seasonStartedAtMs > 0L
                        ? seasonStartedAtMs
                        : System.currentTimeMillis();
            }
        }
    }

    public void resetRankedLadder() {
        this.migrateLegacyRankedStats();
        if (this.rankedLadders != null && !this.rankedLadders.isEmpty()) {
            int rankedGames = this.rankedLadders.values()
                .stream()
                .mapToInt(PlayerStats.RankedLadderStats::getTotalGames)
                .sum();
            this.rankedLadders.clear();
            this.totalBattles = Math.max(0, this.totalBattles - rankedGames);
        }
    }

    public void resetRankedLadder(String ladderId) {
        this.migrateLegacyRankedStats();
        if (this.rankedLadders != null && !this.rankedLadders.isEmpty()) {
            String normalizedLadderId = normalizeLadderId(ladderId);
            PlayerStats.RankedLadderStats removed = this.rankedLadders.remove(
                normalizedLadderId
            );
            if (removed != null) {
                this.totalBattles = Math.max(
                    0,
                    this.totalBattles - removed.getTotalGames()
                );
            }
        }
    }

    public String getRankTitle() {
        return this.getRankTitle(ArenaLadder.defaultRanked().getId());
    }

    public String getRankTitle(String ladderId) {
        return getRankTitleForRating(this.getRankedRating(ladderId));
    }

    public String getRankColor() {
        return this.getRankColor(ArenaLadder.defaultRanked().getId());
    }

    public String getRankColor(String ladderId) {
        return getRankColorForRating(this.getRankedRating(ladderId));
    }

    private PlayerStats.RankedLadderStats getExistingRankedLadderStats(
        String ladderId
    ) {
        this.migrateLegacyRankedStats();
        if (this.rankedLadders == null) {
            this.rankedLadders = new LinkedHashMap<>();
        }

        if (this.recentMatches == null) {
            this.recentMatches = new ArrayList<>();
        }

        if (this.pokemonUsage == null) {
            this.pokemonUsage = new LinkedHashMap<>();
        }

        if (this.grantedRewardMilestones == null) {
            this.grantedRewardMilestones = new LinkedHashSet<>();
        }

        PlayerStats.RankedLadderStats stats = this.rankedLadders.get(
            normalizeLadderId(ladderId)
        );
        return stats != null
            ? stats
            : PlayerStats.RankedLadderStats.defaultStats();
    }

    private PlayerStats.RankedLadderStats getOrCreateRankedLadderStats(
        String ladderId
    ) {
        this.migrateLegacyRankedStats();
        if (this.rankedLadders == null) {
            this.rankedLadders = new LinkedHashMap<>();
        }

        return this.rankedLadders.computeIfAbsent(
            normalizeLadderId(ladderId),
            ignored -> new PlayerStats.RankedLadderStats()
        );
    }

    private void addRecentMatch(PlayerStats.RecentMatchStats match) {
        if (this.recentMatches == null) {
            this.recentMatches = new ArrayList<>();
        }

        this.recentMatches.add(0, match);

        while (this.recentMatches.size() > MAX_RECENT_MATCHES) {
            this.recentMatches.remove(this.recentMatches.size() - 1);
        }
    }

    private void updatePokemonUsage(
        List<PlayerStats.PokemonUsageRecordInput> teamPokemon,
        boolean won
    ) {
        if (teamPokemon != null && !teamPokemon.isEmpty()) {
            if (this.pokemonUsage == null) {
                this.pokemonUsage = new LinkedHashMap<>();
            }

            for (PlayerStats.PokemonUsageRecordInput entry : teamPokemon) {
                if (
                    entry != null &&
                    entry.speciesKey != null &&
                    !entry.speciesKey.isBlank()
                ) {
                    String normalizedKey = entry.speciesKey
                        .trim()
                        .toLowerCase();
                    PlayerStats.PokemonUsageStats usage =
                        this.pokemonUsage.computeIfAbsent(
                            normalizedKey,
                            ignored ->
                                new PlayerStats.PokemonUsageStats(
                                    entry.speciesName,
                                    entry.speciesKey
                                )
                        );
                    if (
                        usage.speciesName == null || usage.speciesName.isBlank()
                    ) {
                        usage.speciesName = entry.speciesName;
                    }
                    if (
                        usage.speciesKey == null || usage.speciesKey.isBlank()
                    ) {
                        usage.speciesKey = entry.speciesKey;
                    }

                    usage.uses++;
                    if (won) {
                        usage.wins++;
                    } else {
                        usage.losses++;
                    }
                }
            }
        }
    }

    private void migrateLegacyRankedStats() {
        if (!this.legacyRankedMigrated) {
            this.legacyRankedMigrated = true;
            if (this.rankedLadders == null) {
                this.rankedLadders = new LinkedHashMap<>();
            }

            if (this.rankedLadders.isEmpty()) {
                if (
                    this.rankedRating != 1000 ||
                    this.rankedWins != 0 ||
                    this.rankedLosses != 0 ||
                    this.rankedStreak != 0 ||
                    this.rankedBestStreak != 0
                ) {
                    PlayerStats.RankedLadderStats migrated =
                        new PlayerStats.RankedLadderStats();
                    migrated.rankedRating = this.rankedRating;
                    migrated.rankedWins = this.rankedWins;
                    migrated.rankedLosses = this.rankedLosses;
                    migrated.rankedStreak = this.rankedStreak;
                    migrated.rankedBestStreak = this.rankedBestStreak;
                    this.rankedLadders.put(
                        ArenaLadder.defaultRanked().getId(),
                        migrated
                    );
                    this.rankedRating = 1000;
                    this.rankedWins = 0;
                    this.rankedLosses = 0;
                    this.rankedStreak = 0;
                    this.rankedBestStreak = 0;
                }
            }
        }
    }

    private static Map<String, PlayerStats.RankedLadderStats> copyRankedLadders(
        Map<String, PlayerStats.RankedLadderStats> source
    ) {
        Map<String, PlayerStats.RankedLadderStats> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        } else {
            for (Entry<
                String,
                PlayerStats.RankedLadderStats
            > entry : source.entrySet()) {
                if (entry.getValue() != null) {
                    PlayerStats.RankedLadderStats copied =
                        new PlayerStats.RankedLadderStats();
                    copied.rankedRating = entry.getValue().rankedRating;
                    copied.rankedWins = entry.getValue().rankedWins;
                    copied.rankedLosses = entry.getValue().rankedLosses;
                    copied.rankedStreak = entry.getValue().rankedStreak;
                    copied.rankedBestStreak = entry.getValue().rankedBestStreak;
                    copy.put(entry.getKey(), copied);
                }
            }

            return copy;
        }
    }

    private static String normalizeLadderId(String ladderId) {
        ArenaLadder ladder = ArenaLadder.byId(ladderId);
        return ladder != null
            ? ladder.getId()
            : ArenaLadder.defaultRanked().getId();
    }

    private static String getRankTitleForRating(int rating) {
        if (rating >= 2400) {
            return "§6§lGrao-Mestre";
        } else if (rating >= 2200) {
            return "§e§lMestre";
        } else if (rating >= 2000) {
            return "§d§lDiamante";
        } else if (rating >= 1800) {
            return "§b§lPlatina";
        } else if (rating >= 1600) {
            return "§a§lOuro";
        } else if (rating >= 1400) {
            return "§f§lPrata";
        } else {
            return rating >= 1000 ? "§7§lBronze" : "§8Sem rank";
        }
    }

    private static String getRankColorForRating(int rating) {
        if (rating >= 2400) {
            return "§6";
        } else if (rating >= 2200) {
            return "§e";
        } else if (rating >= 2000) {
            return "§d";
        } else if (rating >= 1800) {
            return "§b";
        } else if (rating >= 1600) {
            return "§a";
        } else if (rating >= 1400) {
            return "§f";
        } else {
            return rating >= 1000 ? "§7" : "§8";
        }
    }

    public static final class ArchivedSeasonStats {

        private String seasonId = "";
        private String seasonName = "";
        private long startedAtMs;
        private long endedAtMs;
        private Map<String, PlayerStats.RankedLadderStats> rankedLadders =
            new LinkedHashMap<>();

        public ArchivedSeasonStats() {}

        public ArchivedSeasonStats(
            String seasonId,
            String seasonName,
            long startedAtMs,
            long endedAtMs,
            Map<String, PlayerStats.RankedLadderStats> rankedLadders
        ) {
            this.seasonId = seasonId == null ? "" : seasonId;
            this.seasonName = seasonName == null ? "" : seasonName;
            this.startedAtMs = startedAtMs;
            this.endedAtMs = endedAtMs;
            this.rankedLadders = (Map<
                String,
                PlayerStats.RankedLadderStats
            >) (rankedLadders == null
                ? new LinkedHashMap<>()
                : PlayerStats.copyRankedLadders(rankedLadders));
        }

        public String getSeasonId() {
            return this.seasonId;
        }

        public String getSeasonName() {
            return this.seasonName;
        }

        public long getStartedAtMs() {
            return this.startedAtMs;
        }

        public long getEndedAtMs() {
            return this.endedAtMs;
        }

        public Map<String, PlayerStats.RankedLadderStats> getRankedLadders() {
            return PlayerStats.copyRankedLadders(this.rankedLadders);
        }
    }

    public static final class PokemonUsageRecordInput {

        /** Cobblemon resource ID, e.g. {@code "cobblemon:pikachu"}. */
        public final String speciesKey;
        /** Display name, e.g. {@code "Pikachu"}. */
        public final String speciesName;

        public PokemonUsageRecordInput(String speciesKey, String speciesName) {
            this.speciesKey = speciesKey == null ? "" : speciesKey;
            this.speciesName = speciesName == null ? "" : speciesName;
        }
    }

    public static final class PokemonUsageStats {

        private String speciesKey = ""; // e.g. "cobblemon:pikachu"
        private String speciesName = "";
        private int uses;
        private int wins;
        private int losses;

        public PokemonUsageStats() {}

        public PokemonUsageStats(String speciesName) {
            this.speciesName = speciesName != null ? speciesName : "";
        }

        public PokemonUsageStats(String speciesName, String speciesKey) {
            this.speciesName = speciesName != null ? speciesName : "";
            this.speciesKey = speciesKey != null ? speciesKey : "";
        }

        public String getSpeciesKey() {
            return this.speciesKey;
        }

        public String getSpeciesName() {
            return this.speciesName;
        }

        public int getUses() {
            return this.uses;
        }

        public int getWins() {
            return this.wins;
        }

        public int getLosses() {
            return this.losses;
        }

        public int getWinRate() {
            return this.uses <= 0
                ? 0
                : Math.round((this.wins * 100.0F) / this.uses);
        }
    }

    public static final class RankedLadderStats {

        private int rankedRating = 0; // new players start as Sem Rank (< 1000)
        private int rankedWins;
        private int rankedLosses;
        private int rankedStreak;
        private int rankedBestStreak;

        public int getRankedRating() {
            return this.rankedRating;
        }

        public int getRankedWins() {
            return this.rankedWins;
        }

        public int getRankedLosses() {
            return this.rankedLosses;
        }

        public int getRankedStreak() {
            return this.rankedStreak;
        }

        public int getRankedBestStreak() {
            return this.rankedBestStreak;
        }

        public int getTotalGames() {
            return this.rankedWins + this.rankedLosses;
        }

        private static PlayerStats.RankedLadderStats defaultStats() {
            return new PlayerStats.RankedLadderStats();
        }
    }

    public static final class RecentMatchStats {

        private boolean ranked;
        private boolean victory;
        private String ladderDisplayName;
        private String opponentName;
        private int ratingDelta;
        private int ratingAfter;
        private long playedAtMs;
        private List<ArenaTransitionPokemonEntryPayload> ownTeam = List.of();
        private List<ArenaTransitionPokemonEntryPayload> opponentTeam = List.of();

        public RecentMatchStats() {}

        public RecentMatchStats(
            boolean ranked,
            boolean victory,
            String ladderDisplayName,
            String opponentName,
            int ratingDelta,
            int ratingAfter,
            long playedAtMs,
            List<ArenaTransitionPokemonEntryPayload> ownTeam,
            List<ArenaTransitionPokemonEntryPayload> opponentTeam
        ) {
            this.ranked = ranked;
            this.victory = victory;
            this.ladderDisplayName = ladderDisplayName;
            this.opponentName = opponentName;
            this.ratingDelta = ratingDelta;
            this.ratingAfter = ratingAfter;
            this.playedAtMs = playedAtMs;
            this.ownTeam = ownTeam == null ? List.of() : List.copyOf(ownTeam);
            this.opponentTeam = opponentTeam == null
                ? List.of()
                : List.copyOf(opponentTeam);
        }

        public boolean isRanked() {
            return this.ranked;
        }

        public boolean isVictory() {
            return this.victory;
        }

        public String getLadderDisplayName() {
            return this.ladderDisplayName;
        }

        public String getOpponentName() {
            return this.opponentName;
        }

        public int getRatingDelta() {
            return this.ratingDelta;
        }

        public int getRatingAfter() {
            return this.ratingAfter;
        }

        public long getPlayedAtMs() {
            return this.playedAtMs;
        }

        public List<ArenaTransitionPokemonEntryPayload> getOwnTeam() {
            return this.ownTeam;
        }

        public List<ArenaTransitionPokemonEntryPayload> getOpponentTeam() {
            return this.opponentTeam;
        }
    }
}
