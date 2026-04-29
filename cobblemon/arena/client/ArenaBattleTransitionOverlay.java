package cobblemon.arena.client;

import cobblemon.arena.network.ArenaTransitionPokemonEntryPayload;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

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
    /** Size of each Pokémon slot box in pixels (matches ArenaPartyPreviewRenderer). */
    private static final int SLOT_SIZE = 52;
    /** Gap between slot boxes. */
    private static final int SLOT_GAP = 5;
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
    /** Number of leads to select: 1 = singles, 2 = doubles, 3 = triples. */
    private int leadsRequired = 1;
    /** Slots selected by the local player (index into leftTeam). */
    private final java.util.List<Integer> selectedOwnSlots =
        new java.util.ArrayList<>();

    // One FloatingState per slot per team so Cobblemon’s model animator has
    // per-slot state (pose, animation tick, etc.).
    private final Map<Integer, FloatingState> leftStates = new HashMap<>();
    private final Map<Integer, FloatingState> rightStates = new HashMap<>();
    // Partial-tick value forwarded from the last render() call, used in drawSlot.
    private float lastPartialTick = 0f;

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
        start(
            leftName,
            leftPlayerUuid,
            rightName,
            rightPlayerUuid,
            leftTeam,
            rightTeam,
            durationTicks,
            "singles"
        );
    }

    public void start(
        String leftName,
        String leftPlayerUuid,
        String rightName,
        String rightPlayerUuid,
        List<ArenaTransitionPokemonEntryPayload> leftTeam,
        List<ArenaTransitionPokemonEntryPayload> rightTeam,
        int durationTicks,
        String battleTypeId
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
        this.selectedOwnSlots.clear();
        this.leadsRequired = switch (
            battleTypeId != null
                ? battleTypeId.toLowerCase(java.util.Locale.ROOT)
                : ""
        ) {
            case "doubles" -> 2;
            case "triples" -> 3;
            default -> 1;
        };
        leftStates.clear();
        rightStates.clear();
        for (int i = 0; i < 6; i++) {
            leftStates.put(i, new FloatingState());
            rightStates.put(i, new FloatingState());
        }
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

    public int getLeadsRequired() {
        return leadsRequired;
    }

    public java.util.List<Integer> getSelectedOwnSlots() {
        return java.util.Collections.unmodifiableList(selectedOwnSlots);
    }

    public boolean hasAllLeadsSelected() {
        return selectedOwnSlots.size() >= leadsRequired;
    }

    public int getSelectedOwnCount() {
        return selectedOwnSlots.size();
    }

    /**
     * Toggles slot selection.  If the slot is already selected it is deselected;
     * otherwise it is added (up to {@link #leadsRequired} slots max).
     * Returns {@code true} if the selection changed.
     */
    public boolean toggleOwnSlot(int slotIndex) {
        if (!hasOwnPokemonAt(slotIndex)) return false;
        if (selectedOwnSlots.contains(slotIndex)) {
            selectedOwnSlots.remove((Integer) slotIndex);
            return true;
        }
        if (selectedOwnSlots.size() < leadsRequired) {
            selectedOwnSlots.add(slotIndex);
            return true;
        }
        // Replace the oldest selection when at capacity (singles = just replace)
        selectedOwnSlots.remove(0);
        selectedOwnSlots.add(slotIndex);
        return true;
    }

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
    /** Hit-test the opponent (right) side slots. Returns -1 if none. */
    public int getOpponentSlotAt(double mx, double my, int sw, int sh) {
        int rightSectionX = (int) (sw * 0.54);
        int sectionY = (int) (sh * 0.22);
        int totalW = SLOT_COLS * SLOT_SIZE + (SLOT_COLS - 1) * SLOT_GAP;
        int slotStartX = rightSectionX + (int) (sw * 0.44) - totalW - 4;
        int slotStartY = sectionY + 22;
        int count = Math.min(rightTeam.size(), 6);
        for (int i = 0; i < count; i++) {
            int sx = slotStartX + (i % SLOT_COLS) * (SLOT_SIZE + SLOT_GAP);
            int sy = slotStartY + (i / SLOT_COLS) * (SLOT_SIZE + SLOT_GAP);
            if (
                mx >= sx &&
                mx < sx + SLOT_SIZE &&
                my >= sy &&
                my < sy + SLOT_SIZE
            ) return i;
        }
        return -1;
    }

    /** Returns the opponent's entry at slotIndex (from rightTeam), or null. */
    public ArenaTransitionPokemonEntryPayload getOpponentEntry(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= rightTeam.size()) return null;
        return rightTeam.get(slotIndex);
    }

    public void render(
        DrawContext gfx,
        int sw,
        int sh,
        float partialTick,
        java.util.List<Integer> selectedOwnSlots,
        int hoveredOwnSlot,
        int hoveredOpponentSlot
    ) {
        if (!active) return;
        this.lastPartialTick = partialTick;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // 1 ── Fullscreen dark background
        gfx.fill(0, 0, sw, sh, color(0, 0, 0, 210));
        gfx.fill(0, 0, sw, sh / 3, color(44, 15, 20, 70));

        // 2 ── Banner
        drawBanner(gfx, tr, sw, sh);
        // 3 ── VS divider
        drawVsDivider(gfx, tr, sw, sh);

        int sectionW = (int) (sw * 0.44);
        int sectionY = (int) (sh * 0.22);
        int leftSectionX = (int) (sw * 0.02);
        int rightSectionX = (int) (sw * 0.54);

        // 4 ── Own (left) player section
        drawPlayerSectionMulti(
            gfx,
            tr,
            leftName,
            leftTeam,
            leftSectionX,
            sectionY,
            sectionW,
            selectedOwnSlots,
            hoveredOwnSlot,
            true
        );

        // 5 ── Opponent (right) player section
        drawPlayerSectionMulti(
            gfx,
            tr,
            rightName,
            rightTeam,
            rightSectionX,
            sectionY,
            sectionW,
            java.util.List.of(),
            hoveredOpponentSlot,
            false
        );

        // 6 ── Selection instruction
        drawInstruction(gfx, tr, sw, sh);

        // 7 ── Countdown
        drawCountdown(gfx, tr, sw, sh);

        // 8 ── Progress bar
        drawProgressBar(gfx, sw, sh);

        // 9 ── Hover tooltip — own or opponent Pokemon
        // Shown for BOTH sides so each player can check any Pokemon's info.
        ArenaTransitionPokemonEntryPayload tooltipEntry = null;
        if (hoveredOpponentSlot >= 0) {
            tooltipEntry = getOpponentEntry(hoveredOpponentSlot);
        } else if (hoveredOwnSlot >= 0) {
            tooltipEntry =
                hoveredOwnSlot < leftTeam.size()
                    ? leftTeam.get(hoveredOwnSlot)
                    : null;
        }
        if (tooltipEntry != null) {
            drawOpponentTooltip(
                gfx,
                tr,
                (int) (sw * 0.5),
                (int) (sh * 0.72),
                tooltipEntry
            );
        }
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
     * Draws one player's section — multi-lead version (replaces old single-int).
     */
    private void drawPlayerSectionMulti(
        DrawContext gfx,
        TextRenderer tr,
        String playerName,
        List<ArenaTransitionPokemonEntryPayload> team,
        int x,
        int y,
        int width,
        java.util.List<Integer> selectedSlots,
        int hoveredSlot,
        boolean isOwn
    ) {
        drawPlayerSection(
            gfx,
            tr,
            playerName,
            team,
            x,
            y,
            width,
            selectedSlots,
            hoveredSlot,
            isOwn
        );
    }

    /**
     * Draws a full info tooltip for a Pokémon (own or opponent).
     * Shows name, level, types, ability, held item, nature and moves.
     */
    private void drawOpponentTooltip(
        DrawContext gfx,
        TextRenderer tr,
        int cx,
        int cy,
        ArenaTransitionPokemonEntryPayload entry
    ) {
        String name = entry.speciesName() != null ? entry.speciesName() : "???";
        int lv = entry.level();
        String types = entry.typeNames().isEmpty()
            ? ""
            : String.join(" / ", entry.typeNames());
        String ability = entry.abilityName().isBlank()
            ? ""
            : "Hab: " + entry.abilityName();
        String item = entry.heldItemName().isBlank()
            ? ""
            : "Item: " + entry.heldItemName();
        String nature = entry.natureName().isBlank()
            ? ""
            : "Nat: " + entry.natureName();

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(name + "  Nv." + lv);
        if (!types.isBlank()) lines.add(types);
        if (!ability.isBlank()) lines.add(ability);
        if (!item.isBlank()) lines.add(item);
        if (!nature.isBlank()) lines.add(nature);
        if (!entry.moveNames().isEmpty()) {
            lines.add("Golpes:");
            entry.moveNames().forEach(m -> lines.add("  " + m));
        }

        int lineH = 10;
        int padX = 8;
        int padY = 6;
        int boxW = 0;
        for (String l : lines) boxW = Math.max(boxW, tr.getWidth(l) + padX * 2);
        boxW = Math.max(boxW, 160);
        int boxH = padY * 2 + lines.size() * lineH;

        // Position: prefer centred on cx, clamp to screen
        int bx = cx - boxW / 2;
        if (bx < 4) bx = 4;
        int by = cy - boxH - 8;
        if (by < 4) by = cy + 8;

        // Background
        gfx.fill(bx, by, bx + boxW, by + boxH, color(10, 6, 18, 240));
        gfx.fill(bx, by, bx + boxW, by + 1, BORDER_HIGHLIGHT);
        gfx.fill(bx, by, bx + 1, by + boxH, BORDER_HIGHLIGHT);
        gfx.fill(bx + boxW - 1, by, bx + boxW, by + boxH, BORDER_COLOR);
        gfx.fill(bx, by + boxH - 1, bx + boxW, by + boxH, BORDER_COLOR);
        // Header accent
        gfx.fill(
            bx + 1,
            by + 1,
            bx + boxW - 1,
            by + lineH + padY,
            color(40, 12, 24, 180)
        );

        // Text rows
        int ty = by + padY;
        for (int i = 0; i < lines.size(); i++) {
            int col = (i == 0)
                ? RANKED_ACCENT
                : lines.get(i).startsWith("Golpes")
                    ? WARNING_ACCENT
                    : TEXT_PRIMARY;
            gfx.drawText(tr, lines.get(i), bx + padX, ty, col, false);
            ty += lineH;
        }
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
        java.util.List<Integer> selectedSlots,
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

            boolean selected = isOwn && selectedSlots.contains(i);
            boolean hovered = isOwn && i == hoveredSlot;

            drawSlot(gfx, tr, sx, sy, i, entry, selected, hovered, isOwn);
        }

        // ── Status line below slots ──────────────────────────────────────────
        if (isOwn) {
            int rows =
                slotCount > 0 ? (slotCount + SLOT_COLS - 1) / SLOT_COLS : 0;
            int statusY = slotStartY + rows * (SLOT_SIZE + SLOT_GAP) + 2;
            String status =
                !selectedSlots.isEmpty()
                    ? "Lead selecionado! Aguardando inicio..."
                    : "Clique em um Pokemon para selecionar o lead";
            int statusColor = !selectedSlots.isEmpty()
                ? SUCCESS_ACCENT
                : TEXT_DIM;
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
        int dimmedFill = mix(fillColor, color(8, 6, 14, 255), 0.58f);
        int activeFill = mix(fillColor, color(255, 255, 255, 255), 0.22f);

        // Outer dark border
        gfx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, color(10, 4, 6, 220));
        // Colored fill (inset 1 px)
        gfx.fill(
            sx + 1,
            sy + 1,
            sx + SLOT_SIZE - 1,
            sy + SLOT_SIZE - 1,
            selected || hovered ? activeFill : dimmedFill
        );

        // ── State border ──────────────────────────────────────────────────────
        if (selected) {
            // Bright green border — 2 px thick on all sides
            drawBorder(gfx, sx, sy, SLOT_SIZE, SLOT_SIZE, 2, SUCCESS_ACCENT);
            // Selection mark in top-right corner
            gfx.drawText(
                tr,
                "✓",
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

        // ── Pokemon model (Cobblemon 3D) or fallback text ─────────────────────
        if (entry != null) {
            String speciesKey = entry.speciesKey();
            String speciesName = entry.speciesName();
            if (speciesName == null || speciesName.isBlank()) speciesName =
                "???";

            boolean rendered = false;
            if (speciesKey != null && !speciesKey.isBlank()) {
                try {
                    Identifier speciesId = Identifier.of(speciesKey);
                    Map<Integer, FloatingState> states = isOwn
                        ? leftStates
                        : rightStates;
                    FloatingState state = states.computeIfAbsent(index, k ->
                        new FloatingState()
                    );
                    Quaternionf rot = new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(10),
                        (float) Math.toRadians(25),
                        0f
                    );
                    gfx.getMatrices().push();
                    // Centre the model in the slot:
                    // ArenaPartyPreviewRenderer uses sy-1 for a 32-px slot.
                    // Our slot is 52 px; the model occupies ~32 px vertically
                    // at scale 2.6, so offset by (52-32)/2 = 10 px to centre.
                    gfx
                        .getMatrices()
                        .translate(sx + SLOT_SIZE / 2f, sy + 10f, 0f);
                    gfx.getMatrices().scale(2.6f, 2.6f, 1f);
                    PokemonGuiUtilsKt.drawProfilePokemon(
                        speciesId,
                        gfx.getMatrices(),
                        rot,
                        PoseType.PROFILE,
                        state,
                        lastPartialTick,
                        4.5f,
                        true,
                        false,
                        false,
                        1f,
                        1f,
                        1f,
                        1f,
                        0f,
                        0f
                    );
                    gfx.getMatrices().pop();
                    rendered = true;
                } catch (Exception ignored) {
                    // Model not ready yet — fall through to text fallback
                }
            }
            if (!rendered) {
                // Text fallback: species name centred in the slot
                String abbr =
                    speciesName.length() > 5
                        ? speciesName.substring(0, 5)
                        : speciesName;
                float scale = 0.78f;
                int abbrW = (int) (tr.getWidth(abbr) * scale);
                gfx.getMatrices().push();
                gfx
                    .getMatrices()
                    .translate(
                        sx + (SLOT_SIZE - abbrW) / 2,
                        sy + SLOT_SIZE / 2 - 4,
                        0.0
                    );
                gfx.getMatrices().scale(scale, scale, 1.0f);
                gfx.drawText(tr, abbr, 0, 0, TEXT_PRIMARY, true);
                gfx.getMatrices().pop();
            }

            // Species name label below the model
            String label =
                speciesName.length() > 7
                    ? speciesName.substring(0, 7)
                    : speciesName;
            int labelW = (int) (tr.getWidth(label) * 0.60f);
            gfx.getMatrices().push();
            gfx
                .getMatrices()
                .translate(
                    sx + (SLOT_SIZE - labelW) / 2,
                    sy + SLOT_SIZE - 9,
                    0f
                );
            gfx.getMatrices().scale(0.60f, 0.60f, 1f);
            gfx.drawText(tr, label, 0, 0, TEXT_PRIMARY, true);
            gfx.getMatrices().pop();
        } else {
            // Empty slot — show circle placeholder
            int dashX = sx + SLOT_SIZE / 2 - tr.getWidth("○") / 2;
            int dashY = sy + SLOT_SIZE / 2 - 4;
            gfx.drawText(tr, "○", dashX, dashY, color(80, 60, 60, 180), false);
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

    private void drawInstruction(DrawContext gfx, TextRenderer tr, int sw, int sh) {
        String msg =
            "Selecione " +
            leadsRequired +
            " Pokemon para iniciar (" +
            getSelectedOwnCount() +
            "/" +
            leadsRequired +
            ") e clique em Confirmar";
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

    private static int mix(int first, int second, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int a = Math.round(
            ((first >> 24) & 0xFF) + (((second >> 24) & 0xFF) - ((first >> 24) & 0xFF)) * t
        );
        int r = Math.round(
            ((first >> 16) & 0xFF) + (((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * t
        );
        int g = Math.round(
            ((first >> 8) & 0xFF) + (((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * t
        );
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
