package cobblemon.arena.client;

import cobblemon.arena.network.PostMatchResultsPacket;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;

/**
 * Minimal queue of post-match results.
 */
public final class ArenaPostMatchClientState {
    private static final Deque<PostMatchResultsPacket> QUEUE = new ArrayDeque<>();

    private ArenaPostMatchClientState() {}

    public static void queue(PostMatchResultsPacket packet) {
        if (packet == null) return;
        QUEUE.addLast(packet);
        // Open screen if none is open (best effort)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) {
            client.setScreen(new ArenaPostMatchScreen());
        }
    }

    static PostMatchResultsPacket poll() {
        return QUEUE.pollFirst();
    }
}

