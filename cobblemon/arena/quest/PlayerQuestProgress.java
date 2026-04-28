package cobblemon.arena.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks quest progress for a single player.
 *
 * <p>Instances are serialized to / deserialized from JSON by Gson (fields are
 * accessed directly via reflection).  Every mutating accessor therefore
 * includes a lazy-init guard so that Gson-produced instances with {@code null}
 * fields are transparently healed on first use.</p>
 */
public final class PlayerQuestProgress {

    // ── Persistent fields (serialized by Gson) ────────────────────────────────

    /** questId → current progress count for that quest. */
    private Map<String, Integer> progress = new LinkedHashMap<>();

    /** questIds for which the player has already collected the reward. */
    private Set<String> completedAndClaimed = new LinkedHashSet<>();

    /** Epoch-millisecond timestamp of the last daily quest refresh. */
    private long lastDailyRefreshMs = 0L;

    /** Epoch-millisecond timestamp of the last weekly quest refresh. */
    private long lastWeeklyRefreshMs = 0L;

    /** Ordered list of quest IDs that are active for the current daily period. */
    private List<String> activeDailyQuestIds = new ArrayList<>();

    /** Ordered list of quest IDs that are active for the current weekly period. */
    private List<String> activeWeeklyQuestIds = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    /** No-arg constructor required by Gson and direct instantiation. */
    public PlayerQuestProgress() {}

    // ── Lazy-init helpers ─────────────────────────────────────────────────────

    private Map<String, Integer> progress() {
        if (progress == null) progress = new LinkedHashMap<>();
        return progress;
    }

    private Set<String> claimed() {
        if (completedAndClaimed == null) completedAndClaimed = new LinkedHashSet<>();
        return completedAndClaimed;
    }

    private List<String> dailyIds() {
        if (activeDailyQuestIds == null) activeDailyQuestIds = new ArrayList<>();
        return activeDailyQuestIds;
    }

    private List<String> weeklyIds() {
        if (activeWeeklyQuestIds == null) activeWeeklyQuestIds = new ArrayList<>();
        return activeWeeklyQuestIds;
    }

    // ── Progress accessors ────────────────────────────────────────────────────

    /**
     * Returns the current progress count for {@code questId}, or {@code 0} if
     * no progress has been recorded yet.
     */
    public int getProgress(String questId) {
        if (questId == null) return 0;
        return progress().getOrDefault(questId, 0);
    }

    /**
     * Adds {@code amount} (clamped to {@code >= 0}) to the current progress
     * for {@code questId}.
     */
    public void addProgress(String questId, int amount) {
        if (questId == null) return;
        progress().merge(questId, Math.max(0, amount), Integer::sum);
    }

    /**
     * Directly sets the progress for {@code questId} to {@code amount}
     * (clamped to {@code >= 0}).
     *
     * <p>This is used when a counter must be reset, e.g. when a win-streak
     * quest's streak is broken by a loss.</p>
     */
    public void setProgress(String questId, int amount) {
        if (questId == null) return;
        progress().put(questId, Math.max(0, amount));
    }

    /**
     * Returns {@code true} when the player's recorded progress for
     * {@code questId} is greater than or equal to {@code targetAmount}.
     */
    public boolean isCompleted(String questId, int targetAmount) {
        return getProgress(questId) >= targetAmount;
    }

    // ── Claim accessors ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the player has already collected the reward for
     * {@code questId}.
     */
    public boolean isClaimed(String questId) {
        if (questId == null) return false;
        return claimed().contains(questId);
    }

    /**
     * Marks {@code questId} as having its reward collected by this player.
     * Subsequent calls for the same ID are idempotent.
     */
    public void markClaimed(String questId) {
        if (questId != null) {
            claimed().add(questId);
        }
    }

    // ── Refresh-timestamp accessors ───────────────────────────────────────────

    public long getLastDailyRefreshMs() {
        return lastDailyRefreshMs;
    }

    public void setLastDailyRefreshMs(long ms) {
        this.lastDailyRefreshMs = ms;
    }

    public long getLastWeeklyRefreshMs() {
        return lastWeeklyRefreshMs;
    }

    public void setLastWeeklyRefreshMs(long ms) {
        this.lastWeeklyRefreshMs = ms;
    }

    // ── Active quest ID accessors ─────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of the active daily quest IDs.
     * Never {@code null}.
     */
    public List<String> getActiveDailyQuestIds() {
        return List.copyOf(dailyIds());
    }

    /**
     * Returns an unmodifiable view of the active weekly quest IDs.
     * Never {@code null}.
     */
    public List<String> getActiveWeeklyQuestIds() {
        return List.copyOf(weeklyIds());
    }

    /**
     * Replaces the active daily quest IDs and resets any progress / claimed
     * state for the incoming IDs so they start fresh.
     *
     * <p>Old quest IDs that are not present in {@code ids} are left untouched
     * in the maps so that historic data is not inadvertently discarded.</p>
     */
    public void setActiveDailyQuestIds(List<String> ids) {
        this.activeDailyQuestIds = new ArrayList<>(ids);
        for (String id : ids) {
            progress().remove(id);
            claimed().remove(id);
        }
    }

    /**
     * Replaces the active weekly quest IDs and resets any progress / claimed
     * state for the incoming IDs so they start fresh.
     */
    public void setActiveWeeklyQuestIds(List<String> ids) {
        this.activeWeeklyQuestIds = new ArrayList<>(ids);
        for (String id : ids) {
            progress().remove(id);
            claimed().remove(id);
        }
    }
}
