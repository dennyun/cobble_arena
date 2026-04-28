package cobblemon.arena.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores recent chat messages captured from the global Minecraft chat so they
 * can be rendered inside the Arena screen's chat panel.
 *
 * <p>Messages are captured via the Fabric {@code ClientReceiveMessageEvents.GAME}
 * listener registered in {@link cobblemon.arena.CobblemonArenaClient}.
 * Only non-overlay (non-action-bar) messages are stored.
 *
 * <p>The list is bounded at {@value #MAX_MESSAGES} entries to avoid unbounded
 * memory growth during long sessions.
 */
public final class ArenaChatState {

    private ArenaChatState() {}

    private static final int MAX_MESSAGES = 50;

    /** Oldest-first list of raw message strings. */
    private static final List<String> messages = new ArrayList<>();

    /**
     * Appends a new message.  Called on the client render thread by the
     * Fabric message-receive listener.
     *
     * @param text the plain-text representation of the received message
     */
    public static synchronized void addMessage(String text) {
        if (text == null || text.isBlank()) return;
        messages.add(text);
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    /**
     * Returns an immutable snapshot of the current message list.
     * The list is ordered oldest-first (index 0 = oldest).
     */
    public static synchronized List<String> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /** Clears all stored messages (e.g. on disconnect). */
    public static synchronized void clear() {
        messages.clear();
    }
}
