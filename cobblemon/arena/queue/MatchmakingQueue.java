package cobblemon.arena.queue;

import cobblemon.arena.access.ArenaNet;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.MatchFoundPacket;
import cobblemon.arena.network.QueueStatusPacket;
import cobblemon.arena.stats.StatsManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * ELO-aware matchmaking queue with dynamic range expansion.
 *
 * <p>Players are matched within a growing ELO window that starts at ±100 and
 * widens by 50 every 15 seconds (300 ticks), up to a cap of ±500. Matches are
 * attempted on every server tick via {@link #tick(MinecraftServer)}, which must
 * be wired to {@code ServerTickEvents.END_SERVER_TICK} in {@code CobblemonArena}.
 *
 * <p>All mutating operations are {@code synchronized} on {@code this} so the
 * queue remains consistent even when called from different threads (e.g. command
 * executor threads vs. the tick thread).
 */
public final class MatchmakingQueue {

    private static final MatchmakingQueue INSTANCE = new MatchmakingQueue();

    /** How many recent opponents to remember per player to soften rematch rate. */
    private static final int RECENT_MATCH_MEMORY = 5;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    /** Primary queue ordered by arrival time (insertion order preserved). */
    private final Map<UUID, QueueEntry> queue = new LinkedHashMap<>();

    /**
     * Records the server tick at which each player entered the queue.
     * Used by {@link QueueEntry#tickExpansion(long, long)} to compute how long
     * a player has been waiting in tick-time (not wall-clock time).
     */
    private final Map<UUID, Long> joinedAtTick = new HashMap<>();

    /**
     * Tracks the most recent opponents for each player so that we can
     * (softly) avoid scheduling the exact same matchup again immediately.
     * Uses {@link ConcurrentHashMap} because reads from the tick thread and
     * external threads may overlap.
     */
    private final Map<UUID, Deque<UUID>> recentOpponents =
        new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private MatchmakingQueue() {}

    public static MatchmakingQueue getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Public API — kept fully backward-compatible
    // -------------------------------------------------------------------------

    /**
     * Adds {@code player} to the queue for the given {@code ladder}.
     *
     * <p>The player's ELO rating is read from {@link StatsManager} when the
     * ladder is ranked; unranked entries use a fixed baseline of 1 000.
     *
     * @param player the player joining the queue
     * @param ladder the ladder (game-mode) they want to play
     */
    public synchronized void joinQueue(
        ServerPlayerEntity player,
        ArenaLadder ladder,
        boolean blockLegendaries
    ) {
        if (player == null || ladder == null) {
            return;
        }

        if (queue.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§eVocê já está na fila."), false);
            return;
        }

        double rating = 1500.0;
        double rd = 350.0;
        
        if (ladder.isRanked()) {
            cobblemon.arena.stats.PlayerStats.RankedLadderStats stats = StatsManager.getInstance()
                  .getOrCreateStats(player)
                  .getOrCreateRankedLadderStats(ladder.getId());
            rating = stats.getRating();
            rd = stats.getRd();
        }

        QueueEntry entry = new QueueEntry(
            player.getUuid(),
            player.getName().getString(),
            ladder,
            rating,
            rd,
            blockLegendaries
        );
        queue.put(player.getUuid(), entry);

        // joinedAtTick will be resolved to a real tick value on the next tick()
        // call; use the sentinel -1 so tick() knows it hasn't been set yet.
        joinedAtTick.put(player.getUuid(), -1L);

        sendQueueStatus(player, entry);
        player.sendMessage(
            Text.literal("§aEntrou na fila: §f" + ladder.getDisplayName()),
            false
        );
        // Notify other players that the queue count changed
        broadcastQueueCountToOthers(player.getServer(), player.getUuid());
    }

    /**
     * Removes {@code player} from the queue.
     *
     * @param player the player to remove
     * @param notify {@code true} to send a chat confirmation to the player
     */
    public synchronized void leaveQueue(
        ServerPlayerEntity player,
        boolean notify
    ) {
        if (player == null) {
            return;
        }

        QueueEntry removed = queue.remove(player.getUuid());
        joinedAtTick.remove(player.getUuid());

        if (removed != null) {
            ArenaNet.send(
                player,
                new QueueStatusPacket(
                    false,
                    "",
                    "",
                    "",
                    0,
                    getTotalPlayersInQueue()
                )
            );
            broadcastQueueCountToOthers(player.getServer(), player.getUuid());
            if (notify) {
                player.sendMessage(Text.literal("§7Você saiu da fila."), false);
            }
        } else if (notify) {
            player.sendMessage(
                Text.literal("§eVocê não está em nenhuma fila."),
                false
            );
        }
    }

    /**
     * Silently removes {@code player} from the queue because they started an
     * external (non-arena) battle.
     *
     * @param player the player to remove
     * @return {@code true} if the player was in the queue and was removed
     */
    public synchronized boolean cancelParticipationForExternalBattle(
        ServerPlayerEntity player
    ) {
        if (player == null) {
            return false;
        }
        boolean removed = queue.remove(player.getUuid()) != null;
        joinedAtTick.remove(player.getUuid());
        return removed;
    }

    /**
     * Returns {@code true} if {@code player} is currently waiting in the queue.
     *
     * @param player the player to check
     * @return {@code true} if queued
     */
    public boolean isInQueue(ServerPlayerEntity player) {
        return player != null && queue.containsKey(player.getUuid());
    }

    /**
     * Returns {@code true} if the given UUID belongs to a player who is in a
     * pending (pre-battle countdown) match.
     *
     * <p>Pending matches are not implemented in this version; this method always
     * returns {@code false} and exists solely for backward compatibility.
     *
     * @param playerUuid the UUID to check
     * @return {@code false} always
     */
    public boolean isInPendingMatch(UUID playerUuid) {
        return false;
    }

    /**
     * Returns the total number of players currently in the queue across all
     * ladders.
     *
     * @return queue size
     */
    public int getTotalPlayersInQueue() {
        return queue.size();
    }

    /**
     * Re-adds {@code player} to the queue (if not already present) after an
     * arena session was aborted mid-setup due to an external battle conflict.
     *
     * @param player  the player to re-queue
     * @param ladder  the ladder to re-queue them into
     * @param message an optional coloured message to send to the player
     */
    public synchronized void requeueAfterArenaAbort(
        ServerPlayerEntity player,
        ArenaLadder ladder,
        String message
    ) {
        if (player == null || ladder == null) {
            return;
        }
        if (!queue.containsKey(player.getUuid())) {
            joinQueue(player, ladder, false);
        }
        if (message != null && !message.isBlank()) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    /**
     * Removes all players who are queued for a <em>ranked</em> ladder,
     * notifying each online player with the given {@code reason} string.
     *
     * <p>Typically called when ranked configuration changes invalidate the
     * current queue (e.g. season rollover or ladder reset).
     *
     * @param server the running server (used to look up online players)
     * @param reason optional coloured message sent to each evicted player;
     *               ignored when {@code null} or blank
     */
    public synchronized void clearRankedQueues(
        MinecraftServer server,
        String reason
    ) {
        queue
            .values()
            .removeIf(entry -> {
                if (!entry.isRanked()) {
                    return false;
                }
                ServerPlayerEntity player = server
                    .getPlayerManager()
                    .getPlayer(entry.getPlayerUUID());
                if (player != null) {
                    ArenaNet.send(
                        player,
                        new QueueStatusPacket(
                            false,
                            "",
                            "",
                            "",
                            0,
                            getTotalPlayersInQueue()
                        )
                    );
                    if (reason != null && !reason.isBlank()) {
                        player.sendMessage(Text.literal(reason), false);
                    }
                }
                joinedAtTick.remove(entry.getPlayerUUID());
                return true;
            });
    }

    // -------------------------------------------------------------------------
    // Tick — called from CobblemonArena via ServerTickEvents.END_SERVER_TICK
    // -------------------------------------------------------------------------

    /**
     * Main per-tick processing method.
     *
     * <ol>
     *   <li>Seeds {@code joinedAtTick} for any entry that hasn't been given a
     *       real tick value yet (i.e. joined since the last tick).</li>
     *   <li>Calls {@link QueueEntry#tickExpansion(long, long)} on every entry
     *       so ELO ranges grow as players wait.</li>
     *   <li>Iterates the queue in arrival order looking for compatible pairs
     *       (same ladder, within mutual ELO range, not recent opponents).</li>
     *   <li>For each found pair: sends {@link MatchFoundPacket} to both players
     *       and delegates to {@link ArenaBattleManager#startMatch}.</li>
     *   <li>Prunes entries for players who have disconnected.</li>
     * </ol>
     *
     * @param server the running {@link MinecraftServer}
     */
    public synchronized void tick(MinecraftServer server) {
        if (server == null || queue.isEmpty()) {
            return;
        }

        long currentTick = server.getTicks();

        // --- Step 1 & 2: seed join-tick and expand ELO ranges ---
        for (Map.Entry<UUID, QueueEntry> e : queue.entrySet()) {
            Long joinTick = joinedAtTick.get(e.getKey());
            if (joinTick == null || joinTick < 0L) {
                joinedAtTick.put(e.getKey(), currentTick);
                joinTick = currentTick;
            }
            e.getValue().tickExpansion(currentTick, joinTick);
        }

        // --- Step 3: find matches ---
        // Collect candidates in insertion order (fair, FIFO-biased).
        List<QueueEntry> candidates = new ArrayList<>(queue.values());
        List<UUID> matched = new ArrayList<>();
        List<UUID> disconnected = new ArrayList<>();
        
        int availableArenas = cobblemon.arena.arena.ArenaManager.getInstance().getAvailableArenaCount();

        outer: for (int i = 0; i < candidates.size(); i++) {
            if (availableArenas <= 0) {
                break;
            }

            QueueEntry a = candidates.get(i);
            if (matched.contains(a.getPlayerUUID())) {
                continue;
            }

            ServerPlayerEntity playerA = server
                .getPlayerManager()
                .getPlayer(a.getPlayerUUID());
            if (playerA == null) {
                disconnected.add(a.getPlayerUUID());
                continue;
            }

            for (int j = i + 1; j < candidates.size(); j++) {
                QueueEntry b = candidates.get(j);
                if (matched.contains(b.getPlayerUUID())) {
                    continue;
                }

                // Must be on the same ladder
                if (!a.getLadder().getId().equals(b.getLadder().getId())) {
                    continue;
                }

                // ELO range check (mutual)
                if (!a.canMatchWith(b)) {
                    continue;
                }

                // Soft rematch avoidance — skip if they faced each other recently,
                // but only when there are other candidates available to pair with.
                if (
                    isRecentOpponent(a.getPlayerUUID(), b.getPlayerUUID()) &&
                    hasAlternativeCandidate(candidates, matched, a, b)
                ) {
                    continue;
                }

                ServerPlayerEntity playerB = server
                    .getPlayerManager()
                    .getPlayer(b.getPlayerUUID());
                if (playerB == null) {
                    disconnected.add(b.getPlayerUUID());
                    continue;
                }

                // --- Step 4: commit the match ---
                matched.add(a.getPlayerUUID());
                matched.add(b.getPlayerUUID());

                recordRecentOpponent(a.getPlayerUUID(), b.getPlayerUUID());
                recordRecentOpponent(b.getPlayerUUID(), a.getPlayerUUID());

                // Use ArenaNet (Fabric-native) — Architectury's NetworkManager
                // has null codec for our packets when called from the tick thread.
                ArenaNet.send(
                    playerA,
                    new MatchFoundPacket(playerB.getName().getString(), 0)
                );
                ArenaNet.send(
                    playerB,
                    new MatchFoundPacket(playerA.getName().getString(), 0)
                );

                ArenaBattleManager.getInstance().startMatch(
                    playerA,
                    playerB,
                    a.getLadder(),
                    true
                );
                availableArenas--;

                // Move on to find a match for the next unmatched candidate
                continue outer;
            }
        }

        // --- Step 5: remove matched and disconnected players from the queue ---
        for (UUID uuid : matched) {
            queue.remove(uuid);
            joinedAtTick.remove(uuid);
        }
        for (UUID uuid : disconnected) {
            queue.remove(uuid);
            joinedAtTick.remove(uuid);
        }

        // Catch any other disconnections that slipped through the inner loop
        queue
            .entrySet()
            .removeIf(e -> {
                if (server.getPlayerManager().getPlayer(e.getKey()) == null) {
                    joinedAtTick.remove(e.getKey());
                    return true;
                }
                return false;
            });
    }

    // -------------------------------------------------------------------------
    // Queue-count broadcast (used on join / leave)
    // -------------------------------------------------------------------------

    /**
     * Sends a lightweight count-only {@link QueueStatusPacket} (inQueue=false)
     * to every online player who is NOT currently in the queue, so the
     * “Na Fila” counter in the Arena Status Box stays accurate in real time.
     *
     * <p>Uses Fabric’s {@code ServerPlayNetworking.send()} — which has built-in
     * connection-state guards — rather than Architectury’s
     * {@code NetworkManager.sendToPlayer()} to avoid the codec-null NPE that
     * can occur during early tick processing before the player’s Architectury
     * channel is fully negotiated.</p>
     *
     * @param server  the running server (may be {@code null} — call is no-op)
     * @param exclude UUID to skip (the player who triggered the change, since
     *                they already received an accurate packet via
     *                {@link #sendQueueStatus})
     */
    void broadcastQueueCountToOthers(MinecraftServer server, UUID exclude) {
        if (server == null) return;
        int count = getTotalPlayersInQueue();
        QueueStatusPacket countPacket = new QueueStatusPacket(
            false,
            "",
            "",
            "",
            0,
            count
        );
        for (ServerPlayerEntity pl : server
            .getPlayerManager()
            .getPlayerList()) {
            if (pl.getUuid().equals(exclude)) continue;
            if (queue.containsKey(pl.getUuid())) continue; // queued players get their own update
            try {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    pl,
                    countPacket
                );
            } catch (Exception ignored) {
                // Player may not have fully negotiated the channel yet;
                // they will receive the correct count on their next /arena open.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code playerB} appears in {@code playerA}'s
     * recent-opponent history.
     */
    private boolean isRecentOpponent(UUID playerA, UUID playerB) {
        Deque<UUID> recent = recentOpponents.get(playerA);
        return recent != null && recent.contains(playerB);
    }

    /**
     * Checks whether there is at least one other viable candidate that
     * {@code a} could match with (ignoring {@code excluded}) so that the
     * rematch-skip heuristic does not leave {@code a} indefinitely unmatched.
     */
    private boolean hasAlternativeCandidate(
        List<QueueEntry> candidates,
        List<UUID> alreadyMatched,
        QueueEntry a,
        QueueEntry excluded
    ) {
        for (QueueEntry c : candidates) {
            if (c.getPlayerUUID().equals(a.getPlayerUUID())) continue;
            if (c.getPlayerUUID().equals(excluded.getPlayerUUID())) continue;
            if (alreadyMatched.contains(c.getPlayerUUID())) continue;
            if (!a.getLadder().getId().equals(c.getLadder().getId())) continue;
            if (a.canMatchWith(c)) return true;
        }
        return false;
    }

    /**
     * Prepends {@code opponent} to {@code player}'s recent-opponent deque,
     * evicting the oldest entry once the deque exceeds {@link #RECENT_MATCH_MEMORY}.
     */
    private void recordRecentOpponent(UUID player, UUID opponent) {
        Deque<UUID> recent = recentOpponents.computeIfAbsent(player, k ->
            new ArrayDeque<>()
        );
        recent.addFirst(opponent);
        while (recent.size() > RECENT_MATCH_MEMORY) {
            recent.removeLast();
        }
    }

    /**
     * Sends the current queue status to {@code player} immediately after they
     * join the queue so the client HUD can display queue information.
     */
    private void sendQueueStatus(ServerPlayerEntity player, QueueEntry entry) {
        ArenaNet.send(
            player,
            new QueueStatusPacket(
                true,
                entry.getLadder().getQueueLabel(),
                entry.getLadder().getDisplayName(),
                entry.getLadder().getRulesSummary(),
                entry.getQueueTimeSeconds(),
                getTotalPlayersInQueue()
            )
        );
    }
}
