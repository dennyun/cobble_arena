package cobblemon.arena.access;

import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import cobblemon.arena.network.CasualLadderSnapshot;
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
        sendGuiPacket(player, true);
    }

    public static void syncLiveProfile(ServerPlayerEntity player) {
        sendGuiPacket(player, false);
    }

    private static void sendGuiPacket(ServerPlayerEntity player, boolean forceOpen) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        StatsManager statsManager = StatsManager.getInstance();
        PlayerStats stats = statsManager.getStats(player.getUuid());
        int quickWins = stats != null ? stats.getQuickWins() : 0;
        int quickLosses = stats != null ? stats.getQuickLosses() : 0;
        int totalBattles = stats != null ? stats.getTotalBattles() : 0;
        int honorScore = stats != null ? stats.getHonorScore() : 100;
        int totalTurnsPlayed = stats != null ? stats.getTurnAverage() : 0;
        java.util.Map<String, Integer> monotypeWins = stats != null ? stats.getMonotypeWins() : new java.util.LinkedHashMap<>();
        String currentRankedLadderId = config.getRankedLadder().getId();
        List<ArenaMatchHistoryEntryPayload> recentMatchHistory =
            statsManager.getRecentMatchHistory(player.getUuid(), 10);
        List<ArenaPokemonUsageEntryPayload> pokemonUsage =
            statsManager.getTopPokemonUsage(player.getUuid(), 20);
        List<RankedLadderSnapshot> rankedLadderSnapshots =
            buildRankedSnapshotsForPlayer(player);
        List<CasualLadderSnapshot> casualLadderSnapshots = new ArrayList<>();
        if (stats != null) {
            for (java.util.Map.Entry<String, PlayerStats.CasualLadderStats> entry : stats.getCasualLadders().entrySet()) {
                casualLadderSnapshots.add(new CasualLadderSnapshot(
                    entry.getKey(),
                    entry.getValue().getCasualWins(),
                    entry.getValue().getCasualLosses(),
                    entry.getValue().getCasualStreak()
                ));
            }
        }

        List<cobblemon.arena.network.ActiveBattlePayload> activeBattlesList = buildActiveBattlesList(statsManager);



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
                honorScore,
                totalTurnsPlayed,
                monotypeWins,
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
                ArenaManager.getInstance().getTotalArenaCount(),
                forceOpen,
                casualLadderSnapshots,
                activeBattlesList
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

    private static List<cobblemon.arena.network.ActiveBattlePokemonPayload> buildPokemonPayload(
        cobblemon.arena.battle.ArenaSession session,
        com.cobblemon.mod.common.api.battles.model.PokemonBattle battle,
        net.minecraft.server.network.ServerPlayerEntity player
    ) {
        List<cobblemon.arena.network.ActiveBattlePokemonPayload> team = new ArrayList<>();
        List<cobblemon.arena.battle.ArenaSession.TeamPokemonSnapshot> snapshot = session.getTeamSnapshot(player);
        com.cobblemon.mod.common.battles.actor.PlayerBattleActor actor = null;
        if (battle != null) {
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor a : battle.getActors()) {
                if (a instanceof com.cobblemon.mod.common.battles.actor.PlayerBattleActor pa && pa.getUuid().equals(player.getUuid())) {
                    actor = pa;
                    break;
                }
            }
        }
        
        for (int i = 0; i < snapshot.size(); i++) {
            cobblemon.arena.battle.ArenaSession.TeamPokemonSnapshot snap = snapshot.get(i);
            boolean fainted = false;
            String heldItem = snap.getHeldItemName();
            if (actor != null && i < actor.getPokemonList().size()) {
                var bp = actor.getPokemonList().get(i);
                if (bp != null) {
                    fainted = bp.getHealth() <= 0;
                    if (bp.getEffectedPokemon() != null && bp.getEffectedPokemon().heldItem() != net.minecraft.item.ItemStack.EMPTY) {
                        heldItem = net.minecraft.registry.Registries.ITEM.getId(bp.getEffectedPokemon().heldItem().getItem()).toString();
                    }
                }
            }
            team.add(new cobblemon.arena.network.ActiveBattlePokemonPayload(
                snap.getSpeciesKey(), snap.getLevel(), fainted, heldItem
            ));
        }
        return team;
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
            .getTopPlayers(ladderId, 100)
            .stream()
            .map(playerStats -> formatLeaderboardEntry(playerStats, ladderId))
            .toList();
        return new RankedLadderSnapshot(
            ladderId,
            stats != null ? stats.getRankedRating(ladderId) : 0,
            stats != null ? stats.getRankedWins(ladderId) : 0,
            stats != null ? stats.getRankedLosses(ladderId) : 0,
            stats != null ? stats.getRankedStreak(ladderId) : 0,
            stats != null ? stats.getRankedBestStreak(ladderId) : 0,
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
        return ArenaLadder.RANKED_PRESETS
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

    public static List<cobblemon.arena.network.ActiveBattlePayload> buildActiveBattlesList(cobblemon.arena.stats.StatsManager statsManager) {
        List<cobblemon.arena.network.ActiveBattlePayload> activeBattlesList = new java.util.ArrayList<>();
        for (cobblemon.arena.battle.ArenaSession session : cobblemon.arena.battle.ArenaBattleManager.getInstance().getSpectatableSessions()) {
            if (!session.isActive()) continue;
            com.cobblemon.mod.common.api.battles.model.PokemonBattle battle = null;
            if (session.getBattleId() != null) {
                battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattle(session.getBattleId());
                if (battle != null && battle.getEnded()) continue;
            }

            String p1Name = session.getPlayer1().getName().getString();
            String p2Name = session.getPlayer2().getName().getString();
            
            int p1Elo = 0;
            int p2Elo = 0;
            if (session.isRankedMatch() && session.getLadder() != null) {
                String ladderId = session.getLadder().getId();
                cobblemon.arena.stats.PlayerStats p1Stats = statsManager.getStats(session.getPlayer1().getUuid());
                if (p1Stats != null) p1Elo = p1Stats.getRankedRating(ladderId);
                
                cobblemon.arena.stats.PlayerStats p2Stats = statsManager.getStats(session.getPlayer2().getUuid());
                if (p2Stats != null) p2Elo = p2Stats.getRankedRating(ladderId);
            }
            
            List<cobblemon.arena.network.ActiveBattlePokemonPayload> p1Team = buildPokemonPayload(session, battle, session.getPlayer1());
            List<cobblemon.arena.network.ActiveBattlePokemonPayload> p2Team = buildPokemonPayload(session, battle, session.getPlayer2());
            
            int turn = battle != null ? battle.getTurn() : 0;
            int viewers = battle != null ? battle.getSpectators().size() : 0;
            String formatName = session.getLadder() != null ? session.getLadder().getDisplayName() : "Batalha";
            
            long battleStartTimeMs = session.getBattleStartTimeMs() > 0 ? session.getBattleStartTimeMs() : 0;
            
            activeBattlesList.add(new cobblemon.arena.network.ActiveBattlePayload(
                session.getSessionId(), formatName, p1Name, p1Elo, p1Team, p2Name, p2Elo, p2Team, turn, battleStartTimeMs, viewers, session.isRankedMatch()
            ));
        }
        return activeBattlesList;
    }
}
