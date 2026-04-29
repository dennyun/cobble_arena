package cobblemon.arena.access;

import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import cobblemon.arena.network.OpenArenaGuiPacket;
import cobblemon.arena.network.RankedLadderSnapshot;
import cobblemon.arena.network.ServerPacketHandler;
import cobblemon.arena.network.SyncQuestDataPacket;
import cobblemon.arena.queue.MatchmakingQueue;
import cobblemon.arena.stats.PlayerStats;
import cobblemon.arena.stats.StatsManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ArenaAccessService {

    private ArenaAccessService() {}

    public static void openMainGui(ServerPlayerEntity player) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        StatsManager statsManager = StatsManager.getInstance();
        PlayerStats stats = statsManager.getStats(player.getUuid());
        int quickWins = stats != null ? stats.getQuickWins() : 0;
        int quickLosses = stats != null ? stats.getQuickLosses() : 0;
        int totalBattles = stats != null ? stats.getTotalBattles() : 0;
        String currentRankedLadderId = config.getRankedLadder().getId();
        List<ArenaMatchHistoryEntryPayload> recentMatchHistory =
            statsManager.getRecentMatchHistory(player.getUuid(), 10);
        List<ArenaPokemonUsageEntryPayload> pokemonUsage =
            statsManager.getTopPokemonUsage(player.getUuid(), 20);
        List<RankedLadderSnapshot> rankedLadderSnapshots =
            buildRankedSnapshotsForPlayer(player);
        ServerPlayNetworking.send(
            player,
            new OpenArenaGuiPacket(
                player.getName().getString(),
                ArenaServerConfig.snapshotToJson(config.toSnapshot()),
                buildPartyPreview(player),
                recentMatchHistory,
                pokemonUsage,
                quickWins,
                quickLosses,
                totalBattles,
                currentRankedLadderId,
                statsManager.getCurrentSeasonName(),
                statsManager.getCurrentSeasonStartedAtMs(),
                rankedLadderSnapshots,
                player.getServer() != null
                    ? player.getServer().getCurrentPlayerCount()
                    : 0,
                MatchmakingQueue.getInstance().getTotalPlayersInQueue(),
                ArenaBattleManager.getInstance().getActiveBattleCount(),
                ArenaManager.getInstance().getAvailableArenaCount(),
                ArenaManager.getInstance().getTotalArenaCount()
            )
        );
        // Also send current quest state
        try {
            ServerPlayNetworking.send(
                player,
                ServerPacketHandler.buildQuestPacket(player)
            );
        } catch (Exception e) {
            cobblemon.arena.CobblemonArena.LOGGER.warn(
                "Failed to send quest data to {}: {}",
                player.getName().getString(),
                e.getMessage()
            );
        }
    }

    private static List<String> buildPartyPreview(ServerPlayerEntity player) {
        List<String> partyPreview = new ArrayList<>();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(
            player
        );

        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) {
                partyPreview.add("");
            } else {
                JsonObject pokemonJson = pokemon.saveToJSON(
                    player.getRegistryManager(),
                    new JsonObject()
                );
                partyPreview.add(pokemonJson.toString());
            }
        }

        return partyPreview;
    }

    private static RankedLadderSnapshot buildRankedLadderSnapshot(
        StatsManager statsManager,
        PlayerStats stats,
        ServerPlayerEntity player,
        ArenaLadder ladder
    ) {
        String ladderId = ladder.getId();
        List<String> leaderboardEntries = statsManager
            .getTopPlayers(ladderId, 10)
            .stream()
            .map(playerStats -> formatLeaderboardEntry(playerStats, ladderId))
            .toList();
        return new RankedLadderSnapshot(
            ladderId,
            stats != null ? stats.getRankedRating(ladderId) : 1000,
            stats != null ? stats.getRankedWins(ladderId) : 0,
            stats != null ? stats.getRankedLosses(ladderId) : 0,
            stats != null ? stats.getRankedStreak(ladderId) : 0,
            statsManager.getPlayerRank(player.getUuid(), ladderId),
            statsManager.getTotalRankedPlayers(ladderId),
            leaderboardEntries
        );
    }

    public static List<RankedLadderSnapshot> buildRankedSnapshotsForPlayer(
        ServerPlayerEntity player
    ) {
        StatsManager statsManager = StatsManager.getInstance();
        PlayerStats stats = statsManager.getStats(player.getUuid());
        return ArenaLadder.getActiveRankedLadders()
            .stream()
            .map(ladder ->
                buildRankedLadderSnapshot(statsManager, stats, player, ladder)
            )
            .toList();
    }

    private static String formatLeaderboardEntry(
        PlayerStats stats,
        String ladderId
    ) {
        return (
            stats.getPlayerName() +
            "  |  " +
            stats.getRankedRating(ladderId) +
            " Elo  |  " +
            stats.getRankedWins(ladderId) +
            "-" +
            stats.getRankedLosses(ladderId) +
            "  |  " +
            stats.getPlayerUUID()
        );
    }
}
