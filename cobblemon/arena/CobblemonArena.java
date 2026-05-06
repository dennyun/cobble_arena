package cobblemon.arena;

import cobblemon.arena.access.ArenaAccessService;
import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.arena.OfflineRestoreManager;
import cobblemon.arena.arena.SpawnPreventionHandler;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.battle.ArenaSpectatorManager;
import cobblemon.arena.battle.CobblemonBattleHandler;
import cobblemon.arena.battle.MovementLockHandler;
import cobblemon.arena.command.ArenaAdminCommands;
import cobblemon.arena.command.ArenaCommands;
import cobblemon.arena.config.ArenaRankedConfigSync;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.config.BannedPokemonConfig;
import cobblemon.arena.network.ArenaRankedSyncPacket;
import cobblemon.arena.network.ArenaServerStatusPacket;
import cobblemon.arena.network.ServerPacketHandler;
import cobblemon.arena.quest.QuestManager;
import cobblemon.arena.queue.MatchmakingQueue;

import cobblemon.arena.stats.StatsManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonArena {

    public static final String MOD_ID = "cobblemon_arena";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CobblemonArena() {}

    public static void init() {

        ArenaRankedConfigSync.init();

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> {
                ArenaCommands.register(dispatcher);
                ArenaAdminCommands.register(dispatcher);
            }
        );

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                boolean inBattle = ArenaBattleManager.getInstance().getSession(player) != null;
                boolean isSpectating = ArenaSpectatorManager.getInstance().isSpectatingArena(player);
                if (inBattle || isSpectating) {
                    String cmdStr = message.getContent().getString();
                    String cmd = cmdStr.split(" ")[0].toLowerCase(java.util.Locale.ROOT);
                    if (!cmd.equals("sair") && !cmd.equals("arena")) {
                        player.sendMessage(net.minecraft.text.Text.literal("§cVoce nao pode usar comandos durante uma batalha na arena! Use /sair").formatted(net.minecraft.util.Formatting.RED), false);
                        return false;
                    }
                }
            }
            return true;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BannedPokemonConfig.load(server);
            ArenaServerConfig.getInstance().initialize(server);
            StatsManager.getInstance().initialize(server);
            QuestManager.getInstance().initialize(server);
            ArenaManager.getInstance().initialize(World.OVERWORLD, server);
            OfflineRestoreManager.getInstance().initialize(server);
            LOGGER.info("Cobblemon Arena server systems initialized.");
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ArenaBattleManager.getInstance().tick(server);
            ArenaSpectatorManager.getInstance().tick(server);
            MatchmakingQueue.getInstance().tick(server);
            // Broadcast live server stats every 60 ticks (~3 s) so the
            // Arena screen's Status Box always shows real-time data without
            // requiring the player to reopen the GUI.
            if (server.getTicks() % 60 == 0) {
                broadcastLiveStatus(server);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player == null) return;
            ArenaSpectatorManager.getInstance().handleDisconnect(player);

            // Se o jogador desconectou durante uma batalha de arena, registrá-lo como perdedor
            cobblemon.arena.battle.ArenaSession disconnectSession =
                ArenaBattleManager.getInstance().getSession(player);
            if (
                disconnectSession != null &&
                disconnectSession.isActive() &&
                disconnectSession.isQueueMatch() &&
                disconnectSession.getBattleId() != null
            ) {
                ServerPlayerEntity opponent = disconnectSession.getOpponent(
                    player
                );
                try {
                    java.util.List<
                        cobblemon.arena.battle.ArenaSession.TeamPokemonSnapshot
                    > winnerSnap =
                        opponent != null
                            ? disconnectSession.getTeamSnapshot(opponent)
                            : java.util.List.of();
                    java.util.List<
                        cobblemon.arena.battle.ArenaSession.TeamPokemonSnapshot
                    > loserSnap = disconnectSession.getTeamSnapshot(player);
                    if (disconnectSession.isRankedMatch()) {
                        if (opponent != null) {
                            StatsManager.getInstance().recordRankedMatch(
                                opponent,
                                player,
                                disconnectSession.getLadder(),
                                winnerSnap,
                                loserSnap
                            );
                        }
                    } else {
                        if (opponent != null) {
                            String formatId = disconnectSession.getLadder() != null ? disconnectSession.getLadder().getBattleTypeId() : "default";
                            StatsManager.getInstance().recordQuickMatch(
                                formatId,
                                opponent,
                                player,
                                winnerSnap,
                                loserSnap
                            );
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error(
                        "Falha ao registrar derrota por desconexão para {}",
                        player.getName().getString(),
                        e
                    );
                }
            }

            // Força o encerramento do battle Cobblemon se ainda estiver ativo (desconexão forçada)
            if (
                disconnectSession != null &&
                disconnectSession.getBattleId() != null
            ) {
                try {
                    com.cobblemon.mod.common.api.battles.model.PokemonBattle battle =
                        com.cobblemon.mod.common.battles.BattleRegistry.getBattle(
                            disconnectSession.getBattleId()
                        );
                    if (battle != null && !battle.getEnded()) {
                        com.cobblemon.mod.common.battles.BattleRegistry.closeBattle(
                            battle
                        );
                    }
                } catch (Exception ignored) {}
            }

            ArenaBattleManager.getInstance().endArenaForPlayer(player);
            MatchmakingQueueBridge.leaveQueueIfPresent(player);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joined = handler.getPlayer();
            if (joined == null) return;
            OfflineRestoreManager.getInstance().applyIfPending(
                server,
                joined.getUuid()
            );
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ArenaSpectatorManager.getInstance().restoreAll(server);
            StatsManager.getInstance().saveStats();
            OfflineRestoreManager.getInstance().save();
            QuestManager.getInstance().saveProgress();
            ArenaServerConfig.getInstance().save();
        });

        SpawnPreventionHandler.register();
        SpawnPreventionHandler.registerBlockProtection();
        MovementLockHandler.register();
        CobblemonBattleHandler.register();
        ServerPacketHandler.register();

        LOGGER.info("Cobblemon Arena initialized.");
    }

    /**
     * Sends a lightweight {@link ArenaServerStatusPacket} to every connected
     * player via Fabric's {@code ServerPlayNetworking} (safe on any tick; no
     * Architectury codec-null NPE risk).
     */
    private static void broadcastLiveStatus(MinecraftServer server) {
        ArenaServerStatusPacket packet = new ArenaServerStatusPacket(
            server.getCurrentPlayerCount(),
            MatchmakingQueue.getInstance().getTotalPlayersInQueue(),
            ArenaBattleManager.getInstance().getActiveBattleCount(),
            ArenaManager.getInstance().getAvailableArenaCount(),
            ArenaManager.getInstance().getTotalArenaCount()
        );
        for (ServerPlayerEntity pl : server
            .getPlayerManager()
            .getPlayerList()) {
            try {
                ServerPlayNetworking.send(pl, packet);
                ServerPlayNetworking.send(
                    pl,
                    new ArenaRankedSyncPacket(
                        StatsManager.getInstance().getCurrentSeasonName(),
                        StatsManager.getInstance().getCurrentSeasonStartedAtMs(),
                        ArenaAccessService.buildRankedSnapshotsForPlayer(pl)
                    )
                );
            } catch (Exception ignored) {
                // Player may not have fully negotiated the channel yet.
            }
        }
    }

    private static final class MatchmakingQueueBridge {

        private MatchmakingQueueBridge() {}

        private static void leaveQueueIfPresent(ServerPlayerEntity player) {
            try {
                cobblemon.arena.queue.MatchmakingQueue.getInstance().leaveQueue(
                    player,
                    false
                );
            } catch (Exception ignored) {
                // Keep disconnect cleanup resilient even if queue state is inconsistent.
            }
        }
    }
}
