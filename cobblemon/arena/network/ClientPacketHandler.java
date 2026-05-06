package cobblemon.arena.network;

import cobblemon.arena.client.ArenaBattleClientState;
import cobblemon.arena.client.ArenaBattleLeadPreviewScreen;
import cobblemon.arena.client.ArenaBattleTransitionOverlay;
import cobblemon.arena.client.ArenaClientState;
import cobblemon.arena.client.ArenaPostMatchClientState;
import cobblemon.arena.client.ArenaRankedConfigClientState;
import cobblemon.arena.client.QueueStatusOverlay;
import cobblemon.arena.network.SyncQuestDataPacket;
import net.minecraft.client.MinecraftClient;

public class ClientPacketHandler {

    private static Runnable screenOpener = null;

    public static void setScreenOpener(Runnable opener) {
        screenOpener = opener;
    }

    public static void handleOpenArenaGui(OpenArenaGuiPacket packet) {
        ArenaClientState.update(packet);
        // Only open the screen if it’s not already showing the Arena GUI AND forceOpen is true.
        // When the server resends this packet (e.g. after a match to refresh
        // stats), the screen stays open and just updates its state data.
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean arenaOpen =
            mc.currentScreen instanceof cobblemon.arena.client.ArenaShellScreen;
        if (!arenaOpen && packet.forceOpen() && screenOpener != null) {
            screenOpener.run();
        }
    }

    public static void handleQueueStatus(QueueStatusPacket packet) {
        QueueStatusOverlay overlay = QueueStatusOverlay.getInstance();
        // Always sync player count (join, cancel, or periodic broadcast).
        ArenaClientState.setPlayersInQueue(packet.playersInQueue());

        if (packet.inQueue()) {
            // Server confirmed — clear any pending rejection signal.
            ArenaClientState.clearQueueRejection();
            overlay.setQueueInfo(
                packet.queueLabel(),
                packet.ladderDisplayName(),
                packet.rulesSummary(),
                packet.queueTimeSeconds(),
                packet.playersInQueue()
            );
            overlay.setVisible(true);
        } else {
            // inQueue=false — only signal "explicit rejection" when the overlay
            // was already visible (player was showing as queued on the client).
            // This distinguishes a real rejection from a routine periodic
            // broadcast that just updates the queue count for non-queued players.
            if (overlay.isVisible()) {
                ArenaClientState.signalQueueRejection();
            }
            overlay.setVisible(false);
        }
    }

    public static void handleMatchFound(MatchFoundPacket packet) {
        QueueStatusOverlay overlay = QueueStatusOverlay.getInstance();
        overlay.setMatchFound(packet.opponentName(), packet.countdownSeconds());
        // Tell ArenaShellScreen to close the queue counter card immediately.
        // The QueueStatusOverlay continues showing "Partida encontrada!" via
        // its own top-right HUD; only the in-screen counter is cleared.
        ArenaClientState.signalMatchFound();
    }

    public static void handleArenaBattleTransition(
        ArenaBattleTransitionPacket packet
    ) {
        // The battle is definitively starting — clear the queue overlay so that
        // if the player reopens /arena after the battle, they don't see the
        // queue counter and aren't auto-placed back in queue.
        QueueStatusOverlay.getInstance().setVisible(false);
        ArenaClientState.clearQueueRejection();

        ArenaBattleClientState.markTransitionStarted(packet.durationTicks());
        ArenaBattleTransitionOverlay.getInstance().start(
            packet.leftPlayerName(),
            packet.leftPlayerUuid(),
            packet.rightPlayerName(),
            packet.rightPlayerUuid(),
            packet.leftTeam(),
            packet.rightTeam(),
            packet.durationTicks(),
            packet.battleTypeId(),
            packet.leftPlayerElo(),
            packet.rightPlayerElo()
        );
        MinecraftClient.getInstance().setScreen(
            new ArenaBattleLeadPreviewScreen()
        );
    }

    public static void handleSyncRankedConfig(SyncRankedConfigPacket packet) {
        ArenaRankedConfigClientState.update(
            packet.configJson(),
            packet.canEdit(),
            packet.canReset(),
            packet.savedCustomLadderNames()
        );
    }

    public static void handlePostMatchResults(PostMatchResultsPacket packet) {
        ArenaPostMatchClientState.queue(packet);
    }

    public static void handleSyncQuestData(SyncQuestDataPacket packet) {
        cobblemon.arena.client.ArenaQuestClientState.update(packet);
    }

    /**
     * Handles the lightweight live-status broadcast (every ~3 s).
     * Updates the four server-wide counters shown in the Status Box.
     */
    public static void handleServerStatus(ArenaServerStatusPacket packet) {
        ArenaClientState.setPlayersOnline(packet.playersOnline());
        ArenaClientState.setPlayersInQueue(packet.playersInQueue());
        ArenaClientState.setActiveBattles(packet.activeBattles());
        ArenaClientState.setAvailableArenas(packet.availableArenas());
        ArenaClientState.setTotalArenas(packet.totalArenas());
    }

    public static void handleRankedSync(ArenaRankedSyncPacket packet) {
        ArenaClientState.applyLiveRankedSnapshots(packet.rankedLadderSnapshots());
    }

    public static void handleActiveBattlesUpdate(ActiveBattlesUpdatePacket packet) {
        ArenaClientState.setActiveBattlesList(packet.activeBattlesList());
    }

    public static void handleSpectateStatus(cobblemon.arena.network.ArenaSpectateStatusPacket packet) {
        ArenaClientState.setSpectating(packet.isSpectating());
    }
}
