package cobblemon.arena.stats;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.battle.ArenaSession;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import cobblemon.arena.network.PostMatchResultsPacket;
import cobblemon.arena.rewards.ArenaRewardService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.networking.NetworkManager;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

public class StatsManager {

    private static final StatsManager INSTANCE = new StatsManager();
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private final Map<UUID, PlayerStats> playerStats =
        new ConcurrentHashMap<>();
    private File statsFile;

    private StatsManager() {}

    public static StatsManager getInstance() {
        return INSTANCE;
    }

    public void initialize(MinecraftServer server) {
        File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        File arenaDir = new File(worldDir, "cobblemon_arena");
        if (!arenaDir.exists()) {
            arenaDir.mkdirs();
        }

        this.statsFile = new File(arenaDir, "player_stats.json");
        this.loadStats();
        this.syncLoadedPlayersToCurrentSeason();
        this.saveStats();
        CobblemonArena.LOGGER.info(
            "Gerenciador de estatisticas inicializado com {} registros de jogadores",
            this.playerStats.size()
        );
    }

    public PlayerStats getOrCreateStats(ServerPlayerEntity player) {
        return this.getOrCreateStats(
            player.getUuid(),
            player.getName().getString()
        );
    }

    public PlayerStats getOrCreateStats(UUID playerUUID, String playerName) {
        PlayerStats stats = this.playerStats.computeIfAbsent(
            playerUUID,
            uuid -> {
                PlayerStats created = new PlayerStats(uuid, playerName);
                this.syncPlayerSeason(created);
                this.saveStatsAsync();
                return created;
            }
        );
        if (!playerName.equals(stats.getPlayerName())) {
            stats.setPlayerName(playerName);
            this.saveStatsAsync();
        }

        this.syncPlayerSeason(stats);
        return stats;
    }

    public PlayerStats getStats(UUID playerUUID) {
        PlayerStats stats = this.playerStats.get(playerUUID);
        if (stats != null) {
            this.syncPlayerSeason(stats);
        }

        return stats;
    }

    public void recordRankedMatch(
        ServerPlayerEntity winner,
        ServerPlayerEntity loser
    ) {
        this.recordRankedMatch(
            winner,
            loser,
            ArenaLadder.defaultRanked(),
            List.of(),
            List.of()
        );
    }

    public void recordRankedMatch(
        ServerPlayerEntity winner,
        ServerPlayerEntity loser,
        ArenaLadder ladder,
        List<ArenaSession.TeamPokemonSnapshot> winnerTeam,
        List<ArenaSession.TeamPokemonSnapshot> loserTeam
    ) {
        PlayerStats winnerStats = this.getOrCreateStats(winner);
        PlayerStats loserStats = this.getOrCreateStats(loser);
        String ladderId = this.normalizeLadderId(
            ladder != null ? ladder.getId() : null
        );
        String ladderName =
            ladder != null
                ? ladder.getDisplayName()
                : ArenaLadder.defaultRanked().getDisplayName();
        int winnerRatingBefore = winnerStats.getRankedRating(ladderId);
        int loserRatingBefore = loserStats.getRankedRating(ladderId);
        int winnerRatingChange = EloCalculator.calculateRatingChange(
            winnerRatingBefore,
            loserRatingBefore,
            true
        );
        int loserRatingChange = EloCalculator.calculateRatingChange(
            loserRatingBefore,
            winnerRatingBefore,
            false
        );
        winnerStats.recordRankedMatch(ladderId, true, winnerRatingChange);
        loserStats.recordRankedMatch(ladderId, false, loserRatingChange);
        winner.sendMessage(
            Text.literal(
                "§a§lVITORIA! §7Rating: §f" +
                    winnerStats.getRankedRating(ladderId) +
                    " §7(" +
                    EloCalculator.formatRatingChange(winnerRatingChange) +
                    "§7) §8[" +
                    ladderName +
                    "]"
            ),
            false
        );
        loser.sendMessage(
            Text.literal(
                "§c§lDERROTA! §7Rating: §f" +
                    loserStats.getRankedRating(ladderId) +
                    " §7(" +
                    EloCalculator.formatRatingChange(loserRatingChange) +
                    "§7) §8[" +
                    ladderName +
                    "]"
            ),
            false
        );
        CobblemonArena.LOGGER.info(
            "Ranked match [{}]: {} ({} → {}) defeated {} ({} → {})",
            new Object[] {
                ladderId,
                winner.getName().getString(),
                winnerRatingBefore,
                winnerStats.getRankedRating(ladderId),
                loser.getName().getString(),
                loserRatingBefore,
                loserStats.getRankedRating(ladderId),
            }
        );
        this.sendPostMatchResults(
            winner,
            new PostMatchResultsPacket(
                true,
                true,
                ladderName,
                loser.getName().getString(),
                winnerRatingBefore,
                winnerStats.getRankedRating(ladderId),
                winnerRatingChange,
                winnerStats.getRankedWins(ladderId),
                winnerStats.getRankedLosses(ladderId),
                winnerStats.getRankedStreak(ladderId),
                this.getPlayerRank(winner.getUuid(), ladderId),
                this.getTotalRankedPlayers(ladderId)
            )
        );
        this.sendPostMatchResults(
            loser,
            new PostMatchResultsPacket(
                true,
                false,
                ladderName,
                winner.getName().getString(),
                loserRatingBefore,
                loserStats.getRankedRating(ladderId),
                loserRatingChange,
                loserStats.getRankedWins(ladderId),
                loserStats.getRankedLosses(ladderId),
                loserStats.getRankedStreak(ladderId),
                this.getPlayerRank(loser.getUuid(), ladderId),
                this.getTotalRankedPlayers(ladderId)
            )
        );
        winnerStats.recordProfileMatch(
            true,
            true,
            ladderName,
            loser.getName().getString(),
            winnerRatingChange,
            winnerStats.getRankedRating(ladderId),
            this.toUsageInputs(winnerTeam)
        );
        loserStats.recordProfileMatch(
            true,
            false,
            ladderName,
            winner.getName().getString(),
            loserRatingChange,
            loserStats.getRankedRating(ladderId),
            this.toUsageInputs(loserTeam)
        );
        ArenaRewardService.evaluateMilestoneRewards(winner, winnerStats);
        ArenaRewardService.evaluateMilestoneRewards(loser, loserStats);
        this.saveStatsAsync();
    }

    public void recordQuickMatch(
        ServerPlayerEntity winner,
        ServerPlayerEntity loser
    ) {
        this.recordQuickMatch(winner, loser, List.of(), List.of());
    }

    public void recordQuickMatch(
        ServerPlayerEntity winner,
        ServerPlayerEntity loser,
        List<ArenaSession.TeamPokemonSnapshot> winnerTeam,
        List<ArenaSession.TeamPokemonSnapshot> loserTeam
    ) {
        PlayerStats winnerStats = this.getOrCreateStats(winner);
        PlayerStats loserStats = this.getOrCreateStats(loser);
        winnerStats.recordQuickMatch(true);
        loserStats.recordQuickMatch(false);
        winner.sendMessage(Text.literal("§a§lVITORIA!"), false);
        loser.sendMessage(Text.literal("§c§lDERROTA!"), false);
        CobblemonArena.LOGGER.info(
            "Partida rapida: {} derrotou {}",
            winner.getName().getString(),
            loser.getName().getString()
        );
        this.sendPostMatchResults(
            winner,
            new PostMatchResultsPacket(
                false,
                true,
                "Partida Rapida",
                loser.getName().getString(),
                0,
                0,
                0,
                winnerStats.getQuickWins(),
                winnerStats.getQuickLosses(),
                0,
                0,
                0
            )
        );
        this.sendPostMatchResults(
            loser,
            new PostMatchResultsPacket(
                false,
                false,
                "Partida Rapida",
                winner.getName().getString(),
                0,
                0,
                0,
                loserStats.getQuickWins(),
                loserStats.getQuickLosses(),
                0,
                0,
                0
            )
        );
        winnerStats.recordProfileMatch(
            false,
            true,
            "Partida Rapida",
            loser.getName().getString(),
            0,
            0,
            this.toUsageInputs(winnerTeam)
        );
        loserStats.recordProfileMatch(
            false,
            false,
            "Partida Rapida",
            winner.getName().getString(),
            0,
            0,
            this.toUsageInputs(loserTeam)
        );
        ArenaRewardService.evaluateMilestoneRewards(winner, winnerStats);
        ArenaRewardService.evaluateMilestoneRewards(loser, loserStats);
        this.saveStatsAsync();
    }

    public List<PlayerStats> getTopPlayers(int limit) {
        return this.getTopPlayers(ArenaLadder.defaultRanked().getId(), limit);
    }

    public List<PlayerStats> getTopPlayers(String ladderId, int limit) {
        String normalizedLadderId = this.normalizeLadderId(ladderId);
        return this.playerStats.values()
            .stream()
            .filter(stats -> stats.getRankedTotalGames(normalizedLadderId) > 0)
            .sorted(
                Comparator.<PlayerStats>comparingInt(stats ->
                    stats.getRankedRating(normalizedLadderId)
                ).reversed()
            )
            .limit(limit)
            .collect(Collectors.toList());
    }

    public int getPlayerRank(UUID playerUUID) {
        return this.getPlayerRank(
            playerUUID,
            ArenaLadder.defaultRanked().getId()
        );
    }

    public int getPlayerRank(UUID playerUUID, String ladderId) {
        String normalizedLadderId = this.normalizeLadderId(ladderId);
        PlayerStats playerStats = this.getStats(playerUUID);
        if (
            playerStats != null &&
            playerStats.getRankedTotalGames(normalizedLadderId) != 0
        ) {
            List<PlayerStats> sortedPlayers = this.playerStats.values()
                .stream()
                .filter(
                    stats -> stats.getRankedTotalGames(normalizedLadderId) > 0
                )
                .sorted(
                    Comparator.<PlayerStats>comparingInt(stats ->
                        stats.getRankedRating(normalizedLadderId)
                    ).reversed()
                )
                .collect(Collectors.toList());

            for (int i = 0; i < sortedPlayers.size(); i++) {
                if (sortedPlayers.get(i).getPlayerUUID().equals(playerUUID)) {
                    return i + 1;
                }
            }

            return 0;
        } else {
            return 0;
        }
    }

    public int getTotalRankedPlayers() {
        return this.getTotalRankedPlayers(ArenaLadder.defaultRanked().getId());
    }

    public int getTotalRankedPlayers(String ladderId) {
        String normalizedLadderId = this.normalizeLadderId(ladderId);
        return (int) this.playerStats.values()
            .stream()
            .filter(stats -> stats.getRankedTotalGames(normalizedLadderId) > 0)
            .count();
    }

    public void resetRankedLadder() {
        this.playerStats.values().forEach(PlayerStats::resetRankedLadder);
        this.saveStats();
        CobblemonArena.LOGGER.info(
            "Estatisticas ranqueadas resetadas para todas as ladders"
        );
    }

    public void resetRankedLadder(String ladderId) {
        String normalizedLadderId = this.normalizeLadderId(ladderId);
        this.playerStats.values().forEach(stats ->
            stats.resetRankedLadder(normalizedLadderId)
        );
        this.saveStats();
        CobblemonArena.LOGGER.info(
            "Estatisticas ranqueadas resetadas para a ladder {}",
            normalizedLadderId
        );
    }

    public ArenaServerConfig.SeasonRolloverResult rolloverRankedSeason(
        String requestedName,
        MinecraftServer server
    ) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        ArenaServerConfig.SeasonRolloverResult result =
            config.rolloverRankedSeason(requestedName);

        for (PlayerStats stats : this.playerStats.values()) {
            stats.beginNewRankedSeason(
                result.getCurrentSeason().getSeasonId(),
                result.getCurrentSeason().getSeasonName(),
                result.getCurrentSeason().getStartedAtMs(),
                1000,
                config.getSeasonSoftResetFactor()
            );
        }

        ArenaRewardService.grantSeasonRolloverRewards(
            server,
            this.playerStats.values(),
            result.getPreviousSeason(),
            config.getActiveRankedLadders()
        );
        this.saveStats();
        return result;
    }

    public String getCurrentSeasonId() {
        return ArenaServerConfig.getInstance().getCurrentSeasonId();
    }

    public String getCurrentSeasonName() {
        return ArenaServerConfig.getInstance().getCurrentSeasonName();
    }

    public long getCurrentSeasonStartedAtMs() {
        return ArenaServerConfig.getInstance().getCurrentSeasonStartedAtMs();
    }

    public List<ArenaMatchHistoryEntryPayload> getRecentMatchHistory(
        UUID playerUUID,
        int limit
    ) {
        PlayerStats stats = this.getStats(playerUUID);
        return stats == null
            ? List.of()
            : stats
                  .getRecentMatches()
                  .stream()
                  .limit(Math.max(0, limit))
                  .map(entry ->
                      new ArenaMatchHistoryEntryPayload(
                          entry.isRanked(),
                          entry.isVictory(),
                          entry.getLadderDisplayName(),
                          entry.getOpponentName(),
                          entry.getRatingDelta(),
                          entry.getRatingAfter(),
                          entry.getPlayedAtMs()
                      )
                  )
                  .toList();
    }

    public List<ArenaPokemonUsageEntryPayload> getTopPokemonUsage(
        UUID playerUUID,
        int limit
    ) {
        PlayerStats stats = this.getStats(playerUUID);
        return stats == null
            ? List.of()
            : stats
                  .getPokemonUsageStats()
                  .stream()
                  .sorted(
                      Comparator.comparingInt(
                          PlayerStats.PokemonUsageStats::getUses
                      )
                          .reversed()
                          .thenComparing(
                              Comparator.comparingInt(
                                  PlayerStats.PokemonUsageStats::getWinRate
                              ).reversed()
                          )
                          .thenComparing(
                              Comparator.comparingInt(
                                  PlayerStats.PokemonUsageStats::getWins
                              ).reversed()
                          )
                          .thenComparingInt(
                              PlayerStats.PokemonUsageStats::getLosses
                          )
                          .thenComparing(
                              PlayerStats.PokemonUsageStats::getSpeciesName
                          )
                  )
                  .limit(Math.max(0, limit))
                  .map(entry ->
                      new ArenaPokemonUsageEntryPayload(
                          entry.getSpeciesName(),
                          entry.getUses(),
                          entry.getWins(),
                          entry.getLosses()
                      )
                  )
                  .toList();
    }

    private void loadStats() {
        if (!this.statsFile.exists()) {
            CobblemonArena.LOGGER.info(
                "Nenhum arquivo de estatisticas encontrado, iniciando do zero"
            );
        } else {
            try (FileReader reader = new FileReader(this.statsFile)) {
                Type type = (
                    new TypeToken<Map<UUID, PlayerStats>>() {}
                ).getType();
                Map<UUID, PlayerStats> loaded = (Map<
                    UUID,
                    PlayerStats
                >) GSON.fromJson(reader, type);
                if (loaded != null) {
                    this.playerStats.putAll(loaded);
                    CobblemonArena.LOGGER.info(
                        "Estatisticas carregadas para {} jogadores",
                        loaded.size()
                    );
                }
            } catch (IOException var6) {
                CobblemonArena.LOGGER.error(
                    "Falha ao carregar estatisticas de jogadores",
                    var6
                );
            }
        }
    }

    public void saveStats() {
        if (this.statsFile != null) {
            try (FileWriter writer = new FileWriter(this.statsFile)) {
                GSON.toJson(this.playerStats, writer);
            } catch (IOException var6) {
                CobblemonArena.LOGGER.error(
                    "Falha ao salvar estatisticas de jogadores",
                    var6
                );
            }
        }
    }

    private void saveStatsAsync() {
        new Thread(
            () -> {
                try {
                    this.saveStats();
                } catch (Exception var2) {
                    CobblemonArena.LOGGER.error(
                        "Falha ao salvar estatisticas de forma assincrona",
                        var2
                    );
                }
            },
            "Arena-Stats-Save"
        )
            .start();
    }

    private void syncLoadedPlayersToCurrentSeason() {
        for (PlayerStats stats : this.playerStats.values()) {
            this.syncPlayerSeason(stats);
        }
    }

    private void syncPlayerSeason(PlayerStats stats) {
        if (stats != null) {
            ArenaServerConfig config = ArenaServerConfig.getInstance();
            stats.syncCurrentSeason(
                config.getCurrentSeasonId(),
                config.getCurrentSeasonName(),
                config.getCurrentSeasonStartedAtMs(),
                config.getSeasonSoftResetFactor()
            );
        }
    }

    private String normalizeLadderId(String ladderId) {
        ArenaLadder ladder = ArenaLadder.byId(ladderId);
        return ladder != null
            ? ladder.getId()
            : ArenaLadder.defaultRanked().getId();
    }

    private void sendPostMatchResults(
        ServerPlayerEntity player,
        PostMatchResultsPacket packet
    ) {
        if (player != null && packet != null) {
            cobblemon.arena.access.ArenaNet.send(player, packet);
            // Resend the full GUI packet so that if the Arena screen is open it
            // refreshes ELO, win/loss, streak etc. in real time without the
            // player having to close and reopen /arena.
            // handleOpenArenaGui() is smart enough NOT to reopen the screen if
            // it’s already showing — it only updates ArenaClientState.
            try {
                cobblemon.arena.access.ArenaAccessService.openMainGui(player);
            } catch (Exception ignored) {
                // Non-critical — stats will refresh next time the player opens /arena.
            }
        }
    }

    private List<PlayerStats.PokemonUsageRecordInput> toUsageInputs(
        List<ArenaSession.TeamPokemonSnapshot> team
    ) {
        if (team != null && !team.isEmpty()) {
            List<PlayerStats.PokemonUsageRecordInput> converted =
                new ArrayList<>(team.size());

            for (ArenaSession.TeamPokemonSnapshot entry : team) {
                if (entry != null) {
                    converted.add(
                        new PlayerStats.PokemonUsageRecordInput(
                            entry.getSpeciesKey(),
                            entry.getSpeciesName()
                        )
                    );
                }
            }

            return converted;
        } else {
            return List.of();
        }
    }
}
