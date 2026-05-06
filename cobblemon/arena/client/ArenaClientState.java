package cobblemon.arena.client;

import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import cobblemon.arena.network.CasualLadderSnapshot;
import cobblemon.arena.network.OpenArenaGuiPacket;
import cobblemon.arena.network.RankedLadderSnapshot;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

public final class ArenaClientState {

    private static String playerName = "Player";
    private static int quickWins = 0;
    private static int quickLosses = 0;
    private static int totalBattles = 0;
    private static int playersOnline = 0;
    private static int playersInQueue = 0;
    private static int activeBattles = 0;
    private static int availableArenas = 0;
    private static int totalArenas = 0;
    private static String currentRankedLadderId =
        ArenaLadder.defaultRanked().getId();
    private static String currentSeasonName = "Season Teste";
    private static long currentSeasonStartedAtMs = 0L;
    private static Map<String, RankedLadderSnapshot> rankedLadderSnapshots =
        Map.of();
    private static Map<String, CasualLadderSnapshot> casualLadderSnapshots =
        Map.of();
    private static List<Pokemon> partyPreview = List.of();
    private static List<ArenaMatchHistoryEntryPayload> recentMatchHistory =
        List.of();
    private static List<ArenaPokemonUsageEntryPayload> pokemonUsage = List.of();
    private static List<cobblemon.arena.network.ActiveBattlePayload> activeBattlesList = List.of();
    
    private static int honorScore = 100;
    private static int totalTurnsPlayed = 0;
    private static Map<String, Integer> monotypeWins = Map.of();
    
    private static boolean isSpectating = false;

    private ArenaClientState() {}

    public static void update(OpenArenaGuiPacket packet) {
        playerName = packet.playerName();
        ArenaLadder.setActiveRankedLadders(
            ArenaServerConfig.activeLaddersFromSnapshot(
                ArenaServerConfig.snapshotFromJson(packet.rankedConfigJson())
            )
        );
        partyPreview = decodePartyPreview(packet.partyPokemonJson());
        recentMatchHistory = List.copyOf(packet.recentMatchHistory());
        pokemonUsage = List.copyOf(packet.pokemonUsage());
        quickWins = packet.quickWins();
        quickLosses = packet.quickLosses();
        totalBattles = packet.totalBattles();
        honorScore = packet.honorScore();
        totalTurnsPlayed = packet.totalTurnsPlayed();
        monotypeWins = packet.monotypeWins() != null ? Map.copyOf(packet.monotypeWins()) : Map.of();
        
        currentSeasonName = packet.currentSeasonName();
        currentSeasonStartedAtMs = packet.currentSeasonStartedAtMs();
        rankedLadderSnapshots = buildRankedLadderMap(packet.rankedLadderSnapshots());
        casualLadderSnapshots = buildCasualLadderMap(packet.casualLadderSnapshots());

        playersInQueue = packet.playersInQueue();
        activeBattles = packet.activeBattles();
        activeBattlesList = List.copyOf(packet.activeBattlesList());
        availableArenas = packet.availableArenas();
        totalArenas = packet.totalArenas();
        if (ArenaLadder.byId(currentRankedLadderId) == null) {
            currentRankedLadderId = packet.currentRankedLadderId();
        }

        ensureValidSelectedRankedLadder();
    }

    private static Map<String, RankedLadderSnapshot> buildRankedLadderMap(List<RankedLadderSnapshot> snapshots) {
        Map<String, RankedLadderSnapshot> map = new LinkedHashMap<>();
        for (RankedLadderSnapshot snapshot : snapshots) {
            map.put(snapshot.ladderId(), snapshot);
        }
        return map;
    }

    private static Map<String, CasualLadderSnapshot> buildCasualLadderMap(List<CasualLadderSnapshot> snapshots) {
        Map<String, CasualLadderSnapshot> map = new LinkedHashMap<>();
        for (CasualLadderSnapshot snapshot : snapshots) {
            map.put(snapshot.formatId(), snapshot);
        }
        return map;
    }

    public static String getPlayerName() {
        return playerName;
    }

    /** Returns the RankedLadderSnapshot for a specific ladder ID, or null if not found. */
    public static RankedLadderSnapshot getRankedSnapshotById(String ladderId) {
        if (ladderId == null) return null;
        return rankedLadderSnapshots.get(ladderId);
    }

    public static java.util.Collection<RankedLadderSnapshot> getRankedSnapshots() {
        return rankedLadderSnapshots.values();
    }

    public static java.util.Collection<cobblemon.arena.network.CasualLadderSnapshot> getCasualSnapshots() {
        return casualLadderSnapshots.values();
    }

    public static int getRankedRating() {
        return rankedLadderSnapshots.values().stream()
            .mapToInt(RankedLadderSnapshot::rankedRating)
            .max()
            .orElse(0);
    }

    public static int getRankedWins() {
        return rankedLadderSnapshots.values().stream()
            .mapToInt(RankedLadderSnapshot::rankedWins)
            .sum();
    }

    public static int getRankedLosses() {
        return rankedLadderSnapshots.values().stream()
            .mapToInt(RankedLadderSnapshot::rankedLosses)
            .sum();
    }

    public static int getRankedStreak() {
        return rankedLadderSnapshots.values().stream()
            .mapToInt(RankedLadderSnapshot::rankedStreak)
            .max()
            .orElse(0);
    }

    public static int getQuickWins() {
        return quickWins;
    }

    public static int getQuickLosses() {
        return quickLosses;
    }

    public static int getQuickWins(String formatId) {
        if (casualLadderSnapshots.containsKey(formatId)) {
            return casualLadderSnapshots.get(formatId).casualWins();
        }
        return 0;
    }

    public static int getQuickLosses(String formatId) {
        if (casualLadderSnapshots.containsKey(formatId)) {
            return casualLadderSnapshots.get(formatId).casualLosses();
        }
        return 0;
    }

    public static int getQuickStreak(String formatId) {
        if (casualLadderSnapshots.containsKey(formatId)) {
            return casualLadderSnapshots.get(formatId).casualStreak();
        }
        return 0;
    }

    public static int getTotalBattles() {
        return totalBattles;
    }

    public static int getPlayersOnline() {
        return playersOnline;
    }

    public static int getPlayersInQueue() {
        return playersInQueue;
    }

    /** Called from ClientPacketHandler whenever a QueueStatusPacket arrives. */
    public static void setPlayersInQueue(int count) {
        playersInQueue = Math.max(0, count);
    }

    // ── Queue rejection signal ──────────────────────────────────────────────────────
    // Set by ClientPacketHandler when the server sends inQueue=false WHILE the
    // overlay was still visible (i.e. the server explicitly rejected the join).
    // Consumed (cleared) by ArenaShellScreen.updatePlayButtons() to reset the
    // optimistic counter immediately, without waiting for the 5-second timeout.
    private static volatile boolean serverRejectedQueue = false;

    // Match-found signal — set when MatchFoundPacket arrives so the queue
    // counter in ArenaShellScreen is cleared without triggering a rejection.
    private static volatile boolean matchFoundSignal = false;

    public static void signalMatchFound() {
        matchFoundSignal = true;
    }

    public static boolean consumeMatchFound() {
        if (matchFoundSignal) {
            matchFoundSignal = false;
            return true;
        }
        return false;
    }

    public static void signalQueueRejection() {
        serverRejectedQueue = true;
    }

    public static void clearQueueRejection() {
        serverRejectedQueue = false;
    }

    /** Returns true and clears the flag atomically (consume semantics). */
    public static boolean consumeQueueRejection() {
        if (serverRejectedQueue) {
            serverRejectedQueue = false;
            return true;
        }
        return false;
    }

    public static int getActiveBattles() {
        return activeBattles;
    }

    public static void setPlayersOnline(int count) {
        playersOnline = Math.max(0, count);
    }

    public static List<cobblemon.arena.network.ActiveBattlePayload> getActiveBattlesList() {
        return activeBattlesList;
    }

    public static void setActiveBattlesList(List<cobblemon.arena.network.ActiveBattlePayload> list) {
        activeBattlesList = List.copyOf(list);
    }

    public static boolean isSpectating() {
        return isSpectating;
    }

    public static void setSpectating(boolean spectating) {
        isSpectating = spectating;
    }

    public static void setActiveBattles(int count) {
        activeBattles = Math.max(0, count);
    }

    public static void setAvailableArenas(int count) {
        availableArenas = Math.max(0, count);
    }

    public static void setTotalArenas(int count) {
        totalArenas = Math.max(0, count);
    }

    public static int getAvailableArenas() {
        return availableArenas;
    }

    public static int getTotalArenas() {
        return totalArenas;
    }

    public static String getCurrentSeasonName() {
        return currentSeasonName;
    }

    public static long getCurrentSeasonStartedAtMs() {
        return currentSeasonStartedAtMs;
    }

    public static int getPlayerRank() {
        return currentRankedSnapshot().playerRank();
    }

    public static List<Pokemon> getPartyPreview() {
        return new ArrayList<>(partyPreview);
    }

    public static int getTotalRankedPlayers() {
        return currentRankedSnapshot().totalRankedPlayers();
    }

    public static List<ArenaMatchHistoryEntryPayload> getRecentMatchHistory() {
        return recentMatchHistory;
    }

    public static List<ArenaPokemonUsageEntryPayload> getPokemonUsage() {
        return pokemonUsage;
    }

    public static List<String> getLeaderboardEntries() {
        return currentRankedSnapshot().leaderboardEntries();
    }

    public static int getPlayerRankForFormat(String format) {
        if (format == null || format.isBlank()) return getPlayerRank();
        for (ArenaLadder ladder : getActiveRankedLadders()) {
            if (ladder.getBattleTypeId().equalsIgnoreCase(format)) {
                RankedLadderSnapshot snap = getRankedSnapshotById(ladder.getId());
                if (snap != null) return snap.playerRank();
            }
        }
        for (java.util.Map.Entry<String, RankedLadderSnapshot> e : rankedLadderSnapshots.entrySet()) {
            if (e.getKey().toLowerCase(java.util.Locale.ROOT).contains(format.toLowerCase(java.util.Locale.ROOT))) {
                return e.getValue().playerRank();
            }
        }
        return getPlayerRank();
    }

    /**
     * Returns leaderboard entries for the ladder whose {@code battleTypeId}
     * matches {@code format} (e.g. "singles", "doubles", "triples").
     * Falls back to {@link #getLeaderboardEntries()} if no match is found.
     */
    public static List<String> getLeaderboardEntriesForFormat(String format) {
        if (format == null || format.isBlank()) return getLeaderboardEntries();
        for (ArenaLadder ladder : getActiveRankedLadders()) {
            if (ladder.getBattleTypeId().toLowerCase(java.util.Locale.ROOT).endsWith(format.toLowerCase(java.util.Locale.ROOT))) {
                RankedLadderSnapshot snap = getRankedSnapshotById(
                    ladder.getId()
                );
                if (snap != null) return snap.leaderboardEntries();
            }
        }
        // Fallback removed so it doesn't show other formats if empty.
        for (java.util.Map.Entry<
            String,
            RankedLadderSnapshot
        > e : rankedLadderSnapshots.entrySet()) {
            if (
                e.getKey().contains(format.toLowerCase(java.util.Locale.ROOT))
            ) {
                return e.getValue().leaderboardEntries();
            }
        }
        return List.of();
    }

    public static void applyLiveRankedSnapshots(
        List<RankedLadderSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) return;
        Map<String, RankedLadderSnapshot> merged = new LinkedHashMap<>(
            rankedLadderSnapshots
        );
        for (RankedLadderSnapshot snapshot : snapshots) {
            if (snapshot != null) {
                merged.put(snapshot.ladderId(), snapshot);
            }
        }
        rankedLadderSnapshots = merged;
        ensureValidSelectedRankedLadder();
    }

    public static ArenaLadder getCurrentRankedLadder() {
        ensureValidSelectedRankedLadder();
        ArenaLadder ladder = ArenaLadder.byId(currentRankedLadderId);
        return ladder != null ? ladder : ArenaLadder.defaultRanked();
    }

    public static List<ArenaLadder> getActiveRankedLadders() {
        return new ArrayList<>(ArenaLadder.getActiveRankedLadders());
    }

    public static void setCurrentRankedLadderId(String ladderId) {
        for (ArenaLadder ladder : ArenaLadder.getActiveRankedLadders()) {
            if (ladder.getId().equalsIgnoreCase(ladderId)) {
                currentRankedLadderId = ladder.getId();
                return;
            }
        }

        currentRankedLadderId = ArenaLadder.defaultRanked().getId();
    }

    public static void ensureValidSelectedRankedLadder() {
        setCurrentRankedLadderId(currentRankedLadderId);
    }

    public static String getRankTitle() {
        int rankedRating = getRankedRating();
        if (rankedRating >= 2400) {
            return "Grão-Mestre";
        } else if (rankedRating >= 2200) {
            return "Mestre";
        } else if (rankedRating >= 2000) {
            return "Diamante";
        } else if (rankedRating >= 1800) {
            return "Platina";
        } else if (rankedRating >= 1600) {
            return "Ouro";
        } else if (rankedRating >= 1400) {
            return "Prata";
        } else {
            return rankedRating >= 1000 ? "Bronze" : "Iniciante"; // players start at 0 ELO
        }
    }

    private static RankedLadderSnapshot currentRankedSnapshot() {
        ensureValidSelectedRankedLadder();
        RankedLadderSnapshot snapshot = rankedLadderSnapshots.get(
            currentRankedLadderId
        );
        return snapshot != null
            ? snapshot
            : RankedLadderSnapshot.empty(currentRankedLadderId);
    }

    private static List<Pokemon> decodePartyPreview(
        List<String> partyPokemonJson
    ) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (
            minecraft.world != null &&
            partyPokemonJson != null &&
            !partyPokemonJson.isEmpty()
        ) {
            List<Pokemon> decoded = new ArrayList<>(partyPokemonJson.size());

            for (String pokemonJson : partyPokemonJson) {
                if (pokemonJson != null && !pokemonJson.isBlank()) {
                    try {
                        JsonObject jsonObject = JsonParser.parseString(
                            pokemonJson
                        ).getAsJsonObject();
                        decoded.add(
                            new Pokemon().loadFromJSON(
                                minecraft.world.getRegistryManager(),
                                jsonObject
                            )
                        );
                    } catch (Exception var6) {
                        decoded.add(null);
                    }
                } else {
                    decoded.add(null);
                }
            }

            return decoded;
        } else {
            return List.of();
        }
    }
    // =========================================================================
    // DADOS REAIS DO ARENA PASSPORT
    // =========================================================================

    public static int getHonorRating() {
        return honorScore;
    }

    public static int getTurnAverage() {
        return totalTurnsPlayed;
    }

    public static String getFavoritePokemon() {
        if (pokemonUsage == null || pokemonUsage.isEmpty()) return "Nenhum";
        ArenaPokemonUsageEntryPayload fav = pokemonUsage.get(0);
        for (ArenaPokemonUsageEntryPayload usage : pokemonUsage) {
            if (usage.uses() > fav.uses()) fav = usage;
        }
        return fav.speciesName();
    }

    public static int getMaxWinStreak(String formatId) {
        // Recorde Histórico de Sequência (A ser implementado no backend se necessário, por enquanto mantendo local)
        if (rankedLadderSnapshots != null && rankedLadderSnapshots.containsKey(formatId)) {
            return Math.max(rankedLadderSnapshots.get(formatId).rankedBestStreak(), 0);
        }
        return 0;
    }

    public static String getTopPercentile(String formatId) {
        if (rankedLadderSnapshots == null || !rankedLadderSnapshots.containsKey(formatId)) {
            return "Iniciante";
        }
        RankedLadderSnapshot snapshot = rankedLadderSnapshots.get(formatId);
        List<String> lb = snapshot.leaderboardEntries();
        if (lb == null || lb.isEmpty()) return "Iniciante";
        
        for (int i = 0; i < lb.size(); i++) {
            // "PlayerName  |  1500 Elo  |  5-2  |  UUID"
            if (lb.get(i).contains(playerName) || lb.get(i).toLowerCase().startsWith(playerName.toLowerCase())) {
                return "#" + (i + 1);
            }
        }
        return "N/A";
    }

    public static int getMonotypeWins(String type) {
        if (monotypeWins == null) return 0;
        String normalType = type.trim().toLowerCase(java.util.Locale.ROOT);
        return monotypeWins.getOrDefault(normalType, 0);
    }
}
