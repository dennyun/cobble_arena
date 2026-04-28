package cobblemon.arena.quest;

import java.util.List;

/**
 * Defines the reward for completing a quest.
 * Rewards are executed as server commands.
 */
public final class QuestReward {

    private final String description;    // e.g. "100 coins"
    private final List<String> commands; // e.g. ["eco give {player} 100"]

    public QuestReward(String description, List<String> commands) {
        this.description = description != null ? description : "";
        this.commands    = commands != null ? List.copyOf(commands) : List.of();
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCommands() {
        return commands;
    }

    /** Convenience factory for a reward that grants nothing (useful as a placeholder). */
    public static QuestReward empty() {
        return new QuestReward("", List.of());
    }
}
