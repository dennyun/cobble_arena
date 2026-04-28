package cobblemon.arena.client;

import cobblemon.arena.network.ArenaTransitionPokemonEntryPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Fullscreen overlay shown during the arena lead-selection transition.
 * Displays both players' teams, a VS banner, countdown timer and progress bar.
 * Not a Screen subclass — rendered directly from ArenaBattleLeadPreviewScreen.
 */
public final class ArenaBattleTransitionOverlay {

    // ── Singleton ────────────────────────────────────────────────────────────────
    private static final ArenaBattleTransitionOverlay INSTANCE =
        new ArenaBattleTransitionOverlay();

    // ── Layout constants ─────────────────────────────────────────────────────────
    /** Size of each Pokémon slot box in pixels. */
    private static final int SLOT_SIZE = 36;
    /** Gap between slot boxes. */
    private static final int SLOT_GAP = 6;
    /** Number of slot columns per player side. */
    private static final int SLOT_COLS = 3;

    // ── Color palette (mirrors ArenaScreenBase constants) ────────────────────────
    private static final int PANEL_BG = color(26, 9, 13, 220);
    private static final int RANKED_ACCENT = color(235, 204, 106, 255);
    private static final int QUICK_ACCENT = color(255, 118, 62, 255);
    private static final int SUCCESS_ACCENT = color(112, 220, 132, 255);
    private static final int WARNING_ACCENT = color(235, 173, 76, 255);
    private static final int TEXT_PRIMARY = color(248, 242, 236, 255);
    private static final int TEXT_DIM = color(144, 126, 119, 255);
    private static final int BORDER_COLOR = color(132, 54, 62, 255);
    private static final int BORDER_HIGHLIGHT = color(164, 68, 74, 255);

    /** Cycled per slot index for visual variety. */
    private static final int[] SLOT_COLORS = {
        color(112, 184, 248, 200),
        color(112, 220, 132, 200),
        color(235, 173, 76, 200),
        color(235, 204, 106, 200),
        color(255, 118, 62, 200),
        color(192, 134, 92, 200),
    };

    // ── State ────────────────────────────────────────────────────────────────────
    private boolean active = false;
    private String leftName = "";
    private String rightName = "";
    private UUID leftPlayerUuid;
    private UUID rightPlayerUuid;
    private List<ArenaTransitionPokemonEntryPayload> leftTeam = List.of();
    private List<ArenaTransitionPokemonEntryPayload> rightTeam = List.of();
    private int durationTicks = 120;
    private int remainingTicks;

    private ArenaBattleTransitionOverlay() {}

    public static ArenaBattleTransitionOverlay getInstance() {
        return INSTANCE;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    public void start(
        String leftName,
        String leftPlayerUuid,
        String rightName,
        String rightPlayerUuid,
        List<ArenaTransitionPokemonEntryPayload> leftTeam,
        List<ArenaTransitionPokemonEntryPayload> rightTeam,
        int durationTicks
    ) {
        this.leftName = leftName != null ? leftName : "";
        this.rightName = rightName != null ? rightName : "";
        this.leftPlayerUuid = parseUuid(leftPlayerUuid);
        this.rightPlayerUuid = parseUuid(rightPlayerUuid);
        this.leftTeam = leftTeam != null ? List.copyOf(leftTeam) : List.of();
        this.rightTeam = rightTeam != null ? List.copyOf(rightTeam) : List.of();
        this.durationTicks = Math.max(1, durationTicks);
        this.remainingTicks = this.durationTicks;
        this.active = true;
    }

    public void tick() {
        if (!active) return;
        if (remainingTicks > 0) remainingTicks--;
        if (remainingTicks <= 0) active = false;
    }

    public void clear() {
        active = false;
        leftTeam = List.of();
        rightTeam = List.of();
        remainingTicks = 0;
        durationTicks = 120;
    }

    public boolean isActive() {
        return active;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    // ── Hit-detection ────────────────────────────────────────────────────────────

    /**
     * Returns true when the left team has a non-null entry at {@code slotIndex}.
     */
    public boolean hasOwnPokemonAt(int slotIndex) {
        return (
            slotIndex >= 0 &&
            slotIndex < leftTeam.size() &&
            leftTeam.get(slotIndex) != null
        );
    }

    /**
     * Returns the left-side (own) party slot index under the given screen
     * coordinates, or {@code -1} if none.
     * <p>
     * The layout mirrors exactly what {@link #drawPlayerSection} draws for the
     * own (left) player so click targets align with painted boxes.
     */
    public int getOwnPartySlotAt(double mouseX, double mouseY, int sw, int sh) {
        int slotStartX = ownSlotStartX(sw);
        int slotStartY = ownSlotStartY(sh);
        int count = Math.min(leftTeam.size(), 6);

        for (int i = 0; i < count; i++) {
            int col = i % SLOT_COLS;
            int row = i / SLOT_COLS;
            int sx = slotStartX + col * (SLOT_SIZE + SLOT_GAP);
            int sy = slotStartY + row * (SLOT_SIZE + SLOT_GAP);
            if (
                mouseX >= sx &&
                mouseX < sx + SLOT_SIZE &&
                mouseY >= sy &&
                mouseY < sy + SLOT_SIZE
            ) {
                return i;
            }
        }
        return -1;
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    /**
     * Full render pass. Called every frame from
     * {@link ArenaBattleLeadPreviewScreen#render}.
     */
    public void render(
        DrawContext gfx,
        int sw,
        int sh,
        float partialTick,
        int selectedOwnSlot,
        int hoveredOwnSlot
    ) {
        if (!active) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // 1 ── Fullscreen dark background ──────────────────────────────────────
        gfx.fill(0, 0, sw, sh, color(0, 0, 0, 210));
        // Subtle warm tint on the top third
        gfx.fill(0, 0, sw, sh / 3, color(44, 15, 20, 70));

        // 2 ── Banner: "BATALHA NA ARENA" ──────────────────────────────────────
        drawBanner(gfx, tr, sw, sh);

        // 3 ── Center decorative divider + "VS" ────────────────────────────────
        drawVsDivider(gfx, tr, sw, sh);

        // 4 ── Left (own) player section ───────────────────────────────────────
        int sectionW = (int) (sw * 0.44);
        int sectionY = (int) (sh * 0.22);
        int leftSectionX = (int) (sw * 0.02);

        drawPlayerSection(
            gfx,
            tr,
            leftName,
            leftTeam,
            leftSectionX,
            sectionY,
            sectionW,
            selectedOwnSlot,
            hoveredOwnSlot,
            /* isOwn = */ true
        );

        // 5 ── Right (opponent) player section ─────────────────────────────────
        int rightSectionX = (int) (sw * 0.54);
        drawPlayerSection(
            gfx,
            tr,
            rightName,
            rightTeam,
            rightSectionX,
            sectionY,
            sectionW,
            -1,
            -1,
            /* isOwn = */ false
        );

        // 6 ── Instruction line ─────────────────────────────────────────────────
        drawInstruction(gfx, tr, sw, sh);

        // 7 ── Countdown label ──────────────────────────────────────────────────
        drawCountdown(gfx, tr, sw, sh);

        // 8 ── Progress bar ─────────────────────────────────────────────────────
        drawProgressBar(gfx, sw, sh);
    }

    // ── Private draw helpers ─────────────────────────────────────────────────────

    private void drawBanner(DrawContext gfx, TextRenderer tr, int sw, int sh) {
        String title = "BATALHA NA ARENA";
        float scale = 2.0f;
        int titleW = (int) (tr.getWidth(title) * scale);
        int titleY = (int) (sh * 0.07);

        // Glow band behind title
        gfx.fill(
            sw / 2 - titleW / 2 - 12,
            titleY - 4,
            sw / 2 + titleW / 2 + 12,
            titleY + (int) (10 * scale) + 4,
            color(80, 20, 10, 120)
        );

        // Left and right accent lines
        int lineY = titleY + (int) (5 * scale);
        gfx.fill(
            0,
            lineY,
            sw / 2 - titleW / 2 - 16,
            lineY + 2,
            BORDER_HIGHLIGHT
        );
        gfx.fill(
            sw / 2 + titleW / 2 + 16,
            lineY,
            sw,
            lineY + 2,
            BORDER_HIGHLIGHT
        );

        gfx.getMatrices().push();
        gfx.getMatrices().translate(sw / 2.0 - titleW / 2.0, titleY, 0.0);
        gfx.getMatrices().scale(scale, scale, 1.0f);
        gfx.drawText(tr, title, 0, 0, RANKED_ACCENT, false);
        gfx.getMatrices().pop();

        // Subtitle "Selecione seu Pokémon inicial!"
        String sub = "Selecione seu Pokemon inicial!";
        int subW = tr.getWidth(sub);
        int subY = titleY + (int) (10 * scale) + 6;
        gfx.drawText(tr, sub, sw / 2 - subW / 2, subY, TEXT_DIM, false);
    }

    private void drawVsDivider(
        DrawContext gfx,
        TextRenderer tr,
        int sw,
        int sh
    ) {
        int midX = sw / 2;
        int lineTop = (int) (sh * 0.26);
        int lineBot = (int) (sh * 0.80);

        // Vertical rule (three layered lines for glow effect)
        gfx.fill(midX - 1, lineTop, midX + 1, lineBot, color(164, 68, 74, 200));
        gfx.fill(midX - 2, lineTop, midX - 1, lineBot, color(164, 68, 74, 80));
        gfx.fill(midX + 1, lineTop, midX + 2, lineBot, color(164, 68, 74, 80));

        // "VS" text
        String vs = "VS";
        float vsScl = 3.0f;
        int vsW = (int) (tr.getWidth(vs) * vsScl);
        int vsH = (int) (8 * vsScl);
        int vsY = (int) (sh * 0.36);

        // Dark pill behind VS
        gfx.fill(
            midX - vsW / 2 - 10,
            vsY - 6,
            midX + vsW / 2 + 10,
            vsY + vsH + 6,
            color(26, 9, 13, 230)
        );
        gfx.fill(
            midX - vsW / 2 - 10,
            vsY - 6,
            midX + vsW / 2 + 10,
            vsY - 4,
            BORDER_HIGHLIGHT
        );
        gfx.fill(
            midX - vsW / 2 - 10,
            vsY + vsH + 4,
            midX + vsW / 2 + 10,
            vsY + vsH + 6,
            BORDER_HIGHLIGHT
        );

        gfx.getMatrices().push();
        gfx.getMatrices().translate(midX - vsW / 2.0, vsY, 0.0);
        gfx.getMatrices().scale(vsScl, vsScl, 1.0f);
        gfx.drawText(tr, vs, 0, 0, QUICK_ACCENT, false);
        gfx.getMatrices().pop();
    }

    /**
     * Draws one player's name header and up to 6 Pokémon slots.
     *
     * @param isOwn  {@code true} = left side (interactive), {@code false} = right side.
     */
    private void drawPlayerSection(
        DrawContext gfx,
        TextRenderer tr,
        String playerName,
        List<ArenaTransitionPokemonEntryPayload> team,
        int x,
        int y,
        int width,
        int selectedSlot,
        int hoveredSlot,
        boolean isOwn
    ) {
        // ── Name header ──────────────────────────────────────────────────────
        int nameAccent = isOwn ? QUICK_ACCENT : SUCCESS_ACCENT;
        int nameW = tr.getWidth(playerName);
        int nameX = isOwn ? x + 4 : x + width - nameW - 4;

        // Background band
        gfx.fill(x, y - 2, x + width, y + 14, color(20, 8, 12, 180));
        gfx.fill(x, y + 12, x + width, y + 14, BORDER_COLOR);

        gfx.drawText(tr, playerName, nameX, y, nameAccent, false);

        // Role label
        String roleLabel = isOwn ? "(Voce)" : "(Oponente)";
        int roleLabelColor = TEXT_DIM;
        if (isOwn) {
            gfx.drawText(
                tr,
                roleLabel,
                nameX + nameW + 4,
                y,
                roleLabelColor,
                false
            );
        } else {
            int roleLabelX = nameX - tr.getWidth(roleLabel) - 4;
            if (roleLabelX < x) roleLabelX = x + 2;
            gfx.drawText(tr, roleLabel, roleLabelX, y, roleLabelColor, false);
        }

        // ── Pokémon slots ────────────────────────────────────────────────────
        int slotStartY = y + 22;
        int totalSlotW = SLOT_COLS * SLOT_SIZE + (SLOT_COLS - 1) * SLOT_GAP;
        int slotStartX = isOwn ? x + 4 : x + width - totalSlotW - 4;
        int slotCount = Math.min(team.size(), 6);

        for (int i = 0; i < slotCount; i++) {
            ArenaTransitionPokemonEntryPayload entry = team.get(i);
            int col = i % SLOT_COLS;
            int row = i / SLOT_COLS;
            int sx = slotStartX + col * (SLOT_SIZE + SLOT_GAP);
            int sy = slotStartY + row * (SLOT_SIZE + SLOT_GAP);

            boolean selected = isOwn && i == selectedSlot;
            boolean hovered = isOwn && i == hoveredSlot;

            drawSlot(gfx, tr, sx, sy, i, entry, selected, hovered, isOwn);
        }

        // ── Status line below slots ──────────────────────────────────────────
        if (isOwn) {
            int rows =
                slotCount > 0 ? (slotCount + SLOT_COLS - 1) / SLOT_COLS : 0;
            int statusY = slotStartY + rows * (SLOT_SIZE + SLOT_GAP) + 2;
            String status =
                selectedSlot >= 0
                    ? "Lead selecionado! Aguardando inicio..."
                    : "Clique em um Pokemon para selecionar o lead";
            int statusColor = selectedSlot >= 0 ? SUCCESS_ACCENT : TEXT_DIM;
            gfx.drawText(tr, status, slotStartX, statusY, statusColor, false);
        }
    }

    /**
     * Draws a single Pokémon slot box with background, border, and species name abbreviation.
     */
    private void drawSlot(
        DrawContext gfx,
        TextRenderer tr,
        int sx,
        int sy,
        int index,
        ArenaTransitionPokemonEntryPayload entry,
        boolean selected,
        boolean hovered,
        boolean isOwn
    ) {
        int fillColor = SLOT_COLORS[index % SLOT_COLORS.length];

        // Outer dark border
        gfx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, color(10, 4, 6, 220));
        // Colored fill (inset 1 px)
        gfx.fill(
            sx + 1,
            sy + 1,
            sx + SLOT_SIZE - 1,
            sy + SLOT_SIZE - 1,
            fillColor
        );

        // ── State border ──────────────────────────────────────────────────────
        if (selected) {
            // Bright green border — 2 px thick on all sides
            drawBorder(gfx, sx, sy, SLOT_SIZE, SLOT_SIZE, 2, SUCCESS_ACCENT);
            // Checkmark in top-right corner
            gfx.drawText(
                tr,
                "v",
                sx + SLOT_SIZE - 10,
                sy + 2,
                SUCCESS_ACCENT,
                true
            );
        } else if (hovered) {
            // Gold hover border
            drawBorder(gfx, sx, sy, SLOT_SIZE, SLOT_SIZE, 2, RANKED_ACCENT);
            // Dim highlight overlay
            gfx.fill(
                sx + 1,
                sy + 1,
                sx + SLOT_SIZE - 1,
                sy + SLOT_SIZE - 1,
                color(255, 255, 255, 20)
            );
        } else if (isOwn) {
            // Subtle clickable border
            drawBorder(
                gfx,
                sx,
                sy,
                SLOT_SIZE,
                SLOT_SIZE,
                1,
                color(200, 160, 140, 140)
            );
        }

        // ── Species abbreviation text ─────────────────────────────────────────
        if (entry != null) {
            String name = entry.speciesName();
            if (name == null || name.isBlank()) name = "???";

            // Truncate to 5 chars and draw centred at 80 % scale
            String abbr = name.length() > 5 ? name.substring(0, 5) : name;
            float scale = 0.8f;
            int abbrW = (int) (tr.getWidth(abbr) * scale);
            int abbrH = (int) (8 * scale);
            int tx = sx + (SLOT_SIZE - abbrW) / 2;
            int ty = sy + (SLOT_SIZE - abbrH) / 2;

            gfx.getMatrices().push();
            gfx.getMatrices().translate(tx, ty, 0.0);
            gfx.getMatrices().scale(scale, scale, 1.0f);
            gfx.drawText(tr, abbr, 0, 0, TEXT_PRIMARY, true);
            gfx.getMatrices().pop();
        } else {
            // Empty slot — show dash
            int dashX = sx + SLOT_SIZE / 2 - tr.getWidth("-") / 2;
            int dashY = sy + SLOT_SIZE / 2 - 4;
            gfx.drawText(tr, "-", dashX, dashY, color(80, 60, 60, 180), false);
        }
    }

    /** Draws a rectangular border of given thickness using {@code graphics.fill}. */
    private static void drawBorder(
        DrawContext gfx,
        int x,
        int y,
        int w,
        int h,
        int thickness,
        int borderColor
    ) {
        // Top
        gfx.fill(x, y, x + w, y + thickness, borderColor);
        // Bottom
        gfx.fill(x, y + h - thickness, x + w, y + h, borderColor);
        // Left
        gfx.fill(
            x,
            y + thickness,
            x + thickness,
            y + h - thickness,
            borderColor
        );
        // Right
        gfx.fill(
            x + w - thickness,
            y + thickness,
            x + w,
            y + h - thickness,
            borderColor
        );
    }

    private void drawInstruction(
        DrawContext gfx,
        TextRenderer tr,
        int sw,
        int sh
    ) {
        String msg = "Clique em um Pokemon para seleciona-lo como inicial";
        int msgW = tr.getWidth(msg);
        int msgY = (int) (sh * 0.83);
        gfx.drawText(tr, msg, sw / 2 - msgW / 2, msgY, TEXT_DIM, false);
    }

    private void drawCountdown(
        DrawContext gfx,
        TextRenderer tr,
        int sw,
        int sh
    ) {
        int remainSec = (int) Math.ceil(remainingTicks / 20.0);
        String timeStr = remainSec > 0 ? remainSec + "s" : "Iniciando...";
        int timerColor =
            remainSec > 10
                ? TEXT_PRIMARY
                : remainSec > 5
                    ? RANKED_ACCENT
                    : QUICK_ACCENT;
        String label = "Tempo para selecionar: " + timeStr;
        int labelW = tr.getWidth(label);
        int labelY = (int) (sh * 0.88);

        // Backdrop pill
        gfx.fill(
            sw / 2 - labelW / 2 - 8,
            labelY - 4,
            sw / 2 + labelW / 2 + 8,
            labelY + 14,
            color(0, 0, 0, 160)
        );

        gfx.drawText(tr, label, sw / 2 - labelW / 2, labelY, timerColor, false);
    }

    private void drawProgressBar(DrawContext gfx, int sw, int sh) {
        float progress =
            durationTicks > 0 ? (float) remainingTicks / durationTicks : 0.0f;
        int barW = (int) (sw * 0.60);
        int barX = (sw - barW) / 2;
        int barY = (int) (sh * 0.92);
        int barH = 6;

        // Track (empty bar)
        gfx.fill(barX, barY, barX + barW, barY + barH, color(20, 8, 12, 220));
        gfx.fill(barX, barY, barX + barW, barY + 1, color(60, 30, 30, 200));

        // Filled portion — color shifts from green → gold → red as time runs out
        int fillW = (int) (barW * progress);
        int fillColor =
            progress > 0.5f
                ? SUCCESS_ACCENT
                : progress > 0.2f
                    ? WARNING_ACCENT
                    : QUICK_ACCENT;
        if (fillW > 0) {
            gfx.fill(barX, barY, barX + fillW, barY + barH, fillColor);
            // Shine on top
            gfx.fill(
                barX,
                barY,
                barX + fillW,
                barY + 2,
                color(255, 255, 255, 40)
            );
        }

        // Border around the whole bar
        drawBorder(
            gfx,
            barX - 1,
            barY - 1,
            barW + 2,
            barH + 2,
            1,
            BORDER_COLOR
        );
    }

    // ── Layout coordinate helpers ────────────────────────────────────────────────

    /**
     * X origin of the own (left) player's slot grid.
     * Must match what {@link #drawPlayerSection} uses for the own side.
     */
    private static int ownSlotStartX(int sw) {
        int sectionX = (int) (sw * 0.02);
        return sectionX + 4;
    }

    /**
     * Y origin of the own (left) player's slot grid.
     */
    private static int ownSlotStartY(int sh) {
        int sectionY = (int) (sh * 0.22);
        // +22 matches the name-header height used in drawPlayerSection
        return sectionY + 22;
    }

    // ── Utilities ────────────────────────────────────────────────────────────────

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
