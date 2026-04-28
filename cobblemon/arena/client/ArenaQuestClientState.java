package cobblemon.arena.client;

import cobblemon.arena.network.QuestEntryPayload;
import cobblemon.arena.network.SyncQuestDataPacket;
import java.util.List;

/**
 * Client-side storage of the player's quest state, updated by SyncQuestDataPacket.
 * All fields are static — there is exactly one client-side state per JVM session.
 */
public final class ArenaQuestClientState {

    private static List<QuestEntryPayload> dailyQuests  = List.of();
    private static List<QuestEntryPayload> weeklyQuests = List.of();

    private ArenaQuestClientState() {}

    /**
     * Replaces the locally cached quest lists with the data carried by the packet.
     * Called on the render thread by ClientPacketHandler / the S2C receiver.
     */
    public static void update(SyncQuestDataPacket packet) {
        dailyQuests  = packet.dailyQuests();
        weeklyQuests = packet.weeklyQuests();
    }

    /** Returns the current daily quest snapshots (never null, may be empty). */
    public static List<QuestEntryPayload> getDailyQuests()  { return dailyQuests; }

    /** Returns the current weekly quest snapshots (never null, may be empty). */
    public static List<QuestEntryPayload> getWeeklyQuests() { return weeklyQuests; }

    /** Returns {@code true} if no quests have been synced from the server yet. */
    public static boolean isEmpty() {
        return dailyQuests.isEmpty() && weeklyQuests.isEmpty();
    }

    /**
     * Total number of quests that are completed but whose reward has not yet
     * been claimed.  Used to drive the notification badge on the main menu.
     */
    public static int getClaimableCount() {
        int count = 0;
        for (QuestEntryPayload q : dailyQuests)
            if (q.completed() && !q.claimed()) count++;
        for (QuestEntryPayload q : weeklyQuests)
            if (q.completed() && !q.claimed()) count++;
        return count;
    }
}
