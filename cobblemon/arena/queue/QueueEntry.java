package cobblemon.arena.queue;

import cobblemon.arena.ladder.ArenaLadder;
import java.util.UUID;

/**
 * Represents a player waiting in the matchmaking queue.
 * Supports ELO-based matching with dynamic range expansion.
 */
public final class QueueEntry {
    private static final int INITIAL_RANGE = 100;
    private static final int RANGE_EXPANSION = 50;       // per expansion step
    private static final int EXPANSION_INTERVAL_TICKS = 300; // 15 seconds at 20tps
    private static final int MAX_RANGE = 500;

    private final UUID playerUUID;
    private final String playerName;
    private final ArenaLadder ladder;
    private final int eloRating;
    private final long joinedAtMs;
    private int expansionSteps; // incremented every EXPANSION_INTERVAL_TICKS ticks

    public QueueEntry(UUID playerUUID, String playerName, ArenaLadder ladder, int eloRating) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.ladder = ladder;
        this.eloRating = eloRating;
        this.joinedAtMs = System.currentTimeMillis();
        this.expansionSteps = 0;
    }

    /**
     * Called each server tick to expand the search range over time.
     *
     * @param currentTick  the current server tick counter
     * @param joinedAtTick the server tick at which this player joined the queue
     */
    public void tickExpansion(long currentTick, long joinedAtTick) {
        int ticksInQueue = (int) (currentTick - joinedAtTick);
        int expectedSteps = ticksInQueue / EXPANSION_INTERVAL_TICKS;
        if (expectedSteps > this.expansionSteps) {
            this.expansionSteps = expectedSteps;
        }
    }

    /**
     * Returns the current ELO search range, which grows over time as the player
     * waits in the queue, capped at {@code MAX_RANGE}.
     */
    public int currentRange() {
        return Math.min(MAX_RANGE, INITIAL_RANGE + expansionSteps * RANGE_EXPANSION);
    }

    /**
     * Returns {@code true} if this entry can be matched against {@code other}
     * based on their ELO ratings and current search ranges.
     *
     * <p>A match is considered valid only when the absolute ELO difference is
     * within the <em>smaller</em> of the two players' current ranges, ensuring
     * that both sides genuinely accept the pairing.
     *
     * @param other the candidate opponent entry
     * @return {@code true} if the two entries are within a mutually acceptable range
     */
    public boolean canMatchWith(QueueEntry other) {
        int diff = Math.abs(this.eloRating - other.eloRating);
        return diff <= Math.min(this.currentRange(), other.currentRange());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public ArenaLadder getLadder() {
        return ladder;
    }

    public int getEloRating() {
        return eloRating;
    }

    public long getJoinedAtMs() {
        return joinedAtMs;
    }

    /** Returns {@code true} if this entry belongs to a ranked ladder. */
    public boolean isRanked() {
        return ladder != null && ladder.isRanked();
    }

    /** Returns the number of whole seconds this player has been waiting in the queue. */
    public int getQueueTimeSeconds() {
        return (int) ((System.currentTimeMillis() - joinedAtMs) / 1000L);
    }
}
