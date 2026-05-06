package cobblemon.arena.client;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class QueueStatusOverlay {

    private static final QueueStatusOverlay INSTANCE = new QueueStatusOverlay();
    private static final long SLIDE_IN_DURATION_MS = 250L;
    private static final long SLIDE_OUT_DURATION_MS = 250L;

    private static final int PANEL_BG = 0xE8180D18;
    private static final int BORDER_COLOR = 0xFF641C20;
    private static final int BORDER_HIGHLIGHT = 0xFFA44446;
    private static final int ACCENT_ORANGE = 0xFFFF8C48;
    private static final int ACCENT_RED = 0xFFDC6646;
    private static final int ACCENT_GREEN = 0xFF44C06C;
    private static final int TEXT_PRIMARY = 0xFFF5F4EB;
    private static final int TEXT_SECONDARY = 0xFFF0DDC8;
    private static final int TEXT_DIM = 0xFF606060;

    private final TextRenderer font;

    private boolean visible = false;
    private String queueLabel = "";
    private String ladderDisplayName = "";
    private String rulesSummary = "";
    private int queueTimeSeconds = 0;
    // Client-local join timestamp used to drive the real-time elapsed counter.
    // Updated each time the player first enters the queue.
    private long queueJoinedAtMs = 0L;

    private boolean matchFound = false;
    private String opponentName = "";
    private int countdownSeconds = 3;

    private long startTime = 0L;
    private boolean isClosing = false;

    private QueueStatusOverlay() {
        this.font = MinecraftClient.getInstance().textRenderer;
        this.queueLabel = Text.translatable(
            "gui.cobblemon_arena.label.quick"
        ).getString();
        this.ladderDisplayName = Text.translatable(
            "gui.cobblemon_arena.queue_overlay.default_ladder_display"
        ).getString();
        this.rulesSummary = Text.translatable(
            "gui.cobblemon_arena.queue_overlay.default_rules_summary"
        ).getString();
    }

    public static QueueStatusOverlay getInstance() {
        return INSTANCE;
    }

    public void render(
        DrawContext graphics,
        int screenWidth,
        int screenHeight
    ) {
        if (!visible && startTime == 0L) return;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - startTime;
        if (isClosing && elapsed > SLIDE_OUT_DURATION_MS) {
            startTime = 0L;
            return;
        }

        float alpha;
        float slideProgress;
        if (isClosing) {
            float t = Math.min(1.0F, elapsed / (float) SLIDE_OUT_DURATION_MS);
            slideProgress = t * t;
            alpha = 1.0F - slideProgress;
        } else {
            float t = Math.min(1.0F, elapsed / (float) SLIDE_IN_DURATION_MS);
            slideProgress = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            alpha = 1.0F;
        }

        String line1 = matchFound
            ? Text.translatable(
                  "gui.cobblemon_arena.queue_overlay.match_found"
              ).getString()
            : (
                isRankedQueue()
                    ? "Procurando partida ranqueada"
                    : "Procurando partida casual"
            );
        String line2 = matchFound
            ? Text.translatable(
                  "gui.cobblemon_arena.queue_overlay.vs_line",
                  opponentName
              ).getString()
            : formatClock(getElapsedSeconds());
        String line3 = matchFound
            ? Text.translatable(
                  "gui.cobblemon_arena.queue_overlay.starting",
                  countdownSeconds
              ).getString()
            : "";

        int paddingX = 12;
        int paddingY = 8;
        int lineHeight = 12;
        int boxWidth = Math.max(
            120,
            Math.max(
                    font.getWidth(line1),
                    Math.max(font.getWidth(line2), font.getWidth(line3))
                ) +
                paddingX * 2
        );
        int numLines = matchFound ? 3 : 2;
        int boxHeight = paddingY * 2 + lineHeight * numLines;

        int targetX = (screenWidth - boxWidth) / 2;
        int startX = targetX;
        int boxX = isClosing
            ? (int) (targetX + (screenWidth - targetX + 18) * slideProgress)
            : (int) (startX + (targetX - startX) * slideProgress);
        int boxY = 12;

        int panelBg = applyAlpha(0x90000000, alpha); // Simple shadow

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, panelBg);

        int textY = boxY + paddingY;
        
        // Centered texts
        graphics.drawText(
            font,
            line1,
            boxX + (boxWidth - font.getWidth(line1)) / 2,
            textY,
            applyAlpha(TEXT_PRIMARY, alpha),
            false
        );
        graphics.drawText(
            font,
            line2,
            boxX + (boxWidth - font.getWidth(line2)) / 2,
            textY + lineHeight,
            applyAlpha(TEXT_SECONDARY, alpha),
            false
        );
        
        if (matchFound) {
            graphics.drawText(
                font,
                line3,
                boxX + (boxWidth - font.getWidth(line3)) / 2,
                textY + lineHeight * 2,
                applyAlpha(ACCENT_RED, alpha),
                false
            );
        }
    }

    public void tick() {}

    public void setVisible(boolean visible) {
        if (visible && !this.visible) {
            startTime = System.currentTimeMillis();
            isClosing = false;
        } else if (!visible && this.visible) {
            startTime = System.currentTimeMillis();
            isClosing = true;
        }

        this.visible = visible;
        if (!visible) {
            matchFound = false;
            queueJoinedAtMs = 0L; // reset elapsed timer
        }
    }

    public void setQueueInfo(
        String queueLabel,
        String ladderDisplayName,
        String rulesSummary,
        int queueTimeSeconds,
        int playersInQueue
    ) {
        boolean wasVisible = this.visible;
        this.visible = true;
        this.matchFound = false;
        this.queueLabel = queueLabel;
        this.ladderDisplayName = ladderDisplayName;
        this.rulesSummary = rulesSummary;
        this.queueTimeSeconds = queueTimeSeconds;
        if (!wasVisible) {
            startTime = System.currentTimeMillis();
            isClosing = false;
            // Record local join time for client-side elapsed counter
            queueJoinedAtMs = System.currentTimeMillis();
        }
    }

    /**
     * Arms the local elapsed timer from the current moment.
     * Called by the client as soon as the optimistic queue state is applied
     * (i.e. when the player clicks "Fila Ranqueada/Casual"), BEFORE the server
     * confirms.  This ensures the counter starts at 0:00 and ticks in real
     * time immediately, without waiting for the round-trip confirmation.
     * A no-op if the timer is already running.
     */
    public void armElapsedTimer() {
        if (queueJoinedAtMs == 0L) {
            queueJoinedAtMs = System.currentTimeMillis();
        }
    }

    /**
     * Returns the number of seconds elapsed since the player entered the queue,
     * tracked locally so the counter always ticks in real time even without
     * periodic server updates.
     */
    public int getElapsedSeconds() {
        if (queueJoinedAtMs == 0L) return 0;
        return (int) ((System.currentTimeMillis() - queueJoinedAtMs) / 1000L);
    }

    public void setMatchFound(String opponentName, int countdownSeconds) {
        this.matchFound = true;
        this.opponentName = opponentName;
        this.countdownSeconds = countdownSeconds;
    }

    public boolean isVisible() {
        return visible;
    }

    private String buildTeamLine() {
        return Text.translatable(
            "gui.cobblemon_arena.queue_overlay.team_line",
            extractFirstNumber(rulesSummary, 6)
        ).getString();
    }

    private String buildModeLine() {
        return Text.translatable(
            "gui.cobblemon_arena.queue_overlay.mode_line",
            compactQueueLabel(),
            extractLevelLabel()
        ).getString();
    }

    private String buildStatusLine() {
        return queueTimeSeconds > 0
            ? Text.translatable(
                  "gui.cobblemon_arena.queue_overlay.status_searching_time",
                  formatTime(queueTimeSeconds)
              ).getString()
            : Text.translatable(
                  "gui.cobblemon_arena.queue_overlay.status_searching"
              ).getString();
    }

    private int queueAccentColor() {
        if (matchFound) return ACCENT_GREEN;
        return isRankedQueue() ? ACCENT_RED : ACCENT_ORANGE;
    }

    public int getQueueTimeSeconds() {
        return queueTimeSeconds;
    }

    public boolean isRankedQueue() {
        // The server sends Portuguese labels (e.g. "Ranqueado Singles Nv. 50"),
        // so we check for both the English root "ranked" and the PT root "ranqueado".
        String n =
            queueLabel == null ? "" : queueLabel.toLowerCase(Locale.ROOT);
        return (
            n.contains("ranked") ||
            n.contains("ranqueado") ||
            n.contains("rankeado")
        );
    }

    private String compactQueueLabel() {
        String normalized =
            queueLabel == null ? "" : queueLabel.toLowerCase(Locale.ROOT);
        if (normalized.contains("ranked")) return Text.translatable(
            "gui.cobblemon_arena.label.ranked"
        ).getString();
        if (normalized.contains("quick")) return Text.translatable(
            "gui.cobblemon_arena.label.quick"
        ).getString();
        return queueLabel != null && !queueLabel.isBlank()
            ? queueLabel
            : Text.translatable("gui.cobblemon_arena.label.queue").getString();
    }

    private String extractLevelLabel() {
        String level = extractLevel(ladderDisplayName);
        if (level.isBlank()) level = extractLevel(rulesSummary);
        return level.isBlank()
            ? Text.translatable("gui.cobblemon_arena.label.open").getString()
            : Text.translatable(
                  "gui.cobblemon_arena.label.level_short",
                  level
              ).getString();
    }

    private String extractLevel(String text) {
        if (text == null || text.isBlank()) return "";
        String normalized = text.toLowerCase(Locale.ROOT);
        int lvIndex = normalized.indexOf("lv");
        if (lvIndex < 0) return "";
        String after = normalized.substring(lvIndex + 2).trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (!digits.isEmpty()) break;
        }
        return digits.toString();
    }

    private int extractFirstNumber(String text, int fallback) {
        if (text == null || text.isBlank()) return fallback;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        try {
            return digits.isEmpty()
                ? fallback
                : Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private String formatClock(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        int remainder = Math.max(0, seconds) % 60;
        return String.format("%02d:%02d", minutes, remainder);
    }

    private int applyAlpha(int color, float alpha) {
        int a = (color >> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        int scaled = Math.max(0, Math.min(255, Math.round(a * alpha)));
        return (scaled << 24) | rgb;
    }
}
