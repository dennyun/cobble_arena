package cobblemon.arena.quest;

import java.util.Objects;

/**
 * Represents a single quest definition (not player progress — that is tracked separately).
 * Quest instances are immutable; per-player progress is stored in {@link PlayerQuestProgress}.
 */
public final class Quest {

    private final String id;           // unique ID, e.g. "daily_win_3"
    private final QuestType type;
    private final String title;
    private final String description;
    private final int targetAmount;    // how many times the player must complete the action
    private final String typeFilter;   // optional: Pokémon type name for USE_TYPE, or format substring
    private final QuestReward reward;
    private final boolean daily;       // true = daily quest, false = weekly quest

    public Quest(
            String id,
            QuestType type,
            String title,
            String description,
            int targetAmount,
            String typeFilter,
            QuestReward reward,
            boolean daily) {
        this.id           = id;
        this.type         = type;
        this.title        = title;
        this.description  = description;
        this.targetAmount = Math.max(1, targetAmount);
        this.typeFilter   = typeFilter;
        this.reward       = reward != null ? reward : QuestReward.empty();
        this.daily        = daily;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public QuestType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    /**
     * Returns the optional filter string.
     * <ul>
     *   <li>For {@link QuestType#USE_TYPE}: the Pokémon type name (e.g. {@code "fire"}).</li>
     *   <li>For {@link QuestType#WIN_RANKED} / {@link QuestType#PLAY_RANKED}: a format-ID
     *       substring to match against (e.g. {@code "doubles"}).  {@code null} or empty
     *       means any format qualifies.</li>
     *   <li>For all other types: unused (may be {@code null}).</li>
     * </ul>
     */
    public String getTypeFilter() {
        return typeFilter;
    }

    public QuestReward getReward() {
        return reward;
    }

    /** @return {@code true} if this is a daily quest, {@code false} if weekly. */
    public boolean isDaily() {
        return daily;
    }

    /** @return {@code true} if this is a weekly quest, {@code false} if daily. */
    public boolean isWeekly() {
        return !daily;
    }

    // ── Object identity (by ID) ───────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quest q)) return false;
        return Objects.equals(id, q.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Quest{id='" + id + "', type=" + type + ", target=" + targetAmount
                + ", daily=" + daily + '}';
    }
}
