package cobblemon.arena.client;

import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.RankedLadderSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ArenaLadderScreen extends ArenaScreenBase {

    private final Screen parent;
    private final List<StyledButton> ladderTabButtons = new ArrayList<>();

    public ArenaLadderScreen(Screen parent) {
        super(Text.translatable("gui.cobblemon_arena.title.ranked_ladder"));
        this.parent = parent;
    }

    // ══════════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();
        ArenaClientState.ensureValidSelectedRankedLadder();
        buildLadderTabs();

        addDrawableChild(
            new StyledButton(
                guiLeft + 8,
                guiTop + GUI_HEIGHT - 20,
                76,
                16,
                Text.translatable("gui.cobblemon_arena.button.back"),
                b -> close()
            )
        );
        addDrawableChild(
            new StyledButton(
                guiLeft + GUI_WIDTH - 68,
                guiTop + GUI_HEIGHT - 20,
                60,
                16,
                Text.translatable("gui.cobblemon_arena.button.close"),
                b -> {
                    if (client != null) client.setScreen(null);
                }
            )
        );
    }

    // ── Ladder tab buttons ────────────────────────────────────────────────

    private void buildLadderTabs() {
        ladderTabButtons.clear();
        List<ArenaLadder> ladders = ArenaClientState.getActiveRankedLadders();
        if (ladders.isEmpty()) return;

        int cols = Math.min(5, ladders.size());
        int tabW = (352 - 3 * (cols - 1)) / cols;
        int startX = guiLeft + 16;
        int startY = guiTop + 174;

        for (int i = 0; i < ladders.size(); i++) {
            ArenaLadder ladder = ladders.get(i);
            int col = i % cols,
                row = i / cols;
            int x = startX + col * (tabW + 3);
            int y = startY + row * 13;

            StyledButton btn = addDrawableChild(
                new StyledButton(
                    x,
                    y,
                    tabW,
                    11,
                    Text.literal(
                        fitText(ladder.getDisplayName(), tabW - 8, SMALL_SCALE)
                    ),
                    b ->
                        ArenaClientState.setCurrentRankedLadderId(
                            ladder.getId()
                        )
                )
            );
            ladderTabButtons.add(btn);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(
        DrawContext graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        updateGuiPosition();
        int gmx = (int) toGuiX(mouseX);
        int gmy = (int) toGuiY(mouseY);

        renderFullScreenBg(graphics);
        pushGuiScale(graphics);

        renderScreenFrame(
            graphics,
            ArenaClientState.getCurrentSeasonName() + "  —  Ranking",
            "ELO ao Vivo",
            RANKED_ACCENT
        );

        drawPlayerStatsRow(graphics);
        drawFormatEloRow(graphics);
        drawLadderTabPanel(graphics);
        drawLeaderboard(graphics);

        super.render(graphics, gmx, gmy, delta);
        highlightSelectedTab(graphics);
        popGuiScale(graphics);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sections
    // ══════════════════════════════════════════════════════════════════════

    // ── Top row: player summary ───────────────────────────────────────────
    private void drawPlayerStatsRow(DrawContext graphics) {
        int x = guiLeft + 8,
            y = guiTop + 30,
            w = 180;

        int rank = ArenaClientState.getPlayerRank();
        int total = ArenaClientState.getTotalRankedPlayers();
        String rankLabel = rank > 0 ? "#" + rank + " / " + total : "Iniciante";

        drawSection(graphics, x, y, w, 62, SECTION_BG);

        // Name
        drawScaledText(
            graphics,
            ArenaClientState.getPlayerName(),
            x + 8,
            y + 7,
            TEXT_PRIMARY,
            BODY_SCALE
        );

        // Rank badge
        drawBadgeRight(
            graphics,
            x + w - 8,
            y + 7,
            ArenaClientState.getRankTitle(),
            rankAccentColor(ArenaClientState.getRankTitle()),
            SMALL_SCALE
        );

        // Metric chips — row 1
        drawMetricChip(
            graphics,
            x + 8,
            y + 24,
            50,
            "Rating",
            String.valueOf(ArenaClientState.getRankedRating()),
            RANKED_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 24,
            50,
            "Ranking",
            rankLabel,
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 24,
            54,
            "Recorde",
            ArenaClientState.getRankedWins() +
                "-" +
                ArenaClientState.getRankedLosses(),
            SUCCESS_ACCENT
        );

        // Metric chips — row 2
        drawMetricChip(
            graphics,
            x + 8,
            y + 42,
            50,
            "Streak",
            formatStreak(ArenaClientState.getRankedStreak()),
            ArenaClientState.getRankedStreak() >= 0
                ? SUCCESS_ACCENT
                : WARNING_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 42,
            50,
            "Win Rate",
            getWinRate(
                    ArenaClientState.getRankedWins(),
                    ArenaClientState.getRankedLosses()
                ) +
                "%",
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 42,
            54,
            "Partidas",
            String.valueOf(
                ArenaClientState.getRankedWins() +
                    ArenaClientState.getRankedLosses()
            ),
            TEXT_PRIMARY
        );
    }

    // ── Right column: current ladder info ────────────────────────────────
    // (drawn beside player stats)
    private void drawFormatEloRow(DrawContext graphics) {
        int x = guiLeft + 196,
            y = guiTop + 30,
            w = 180;
        ArenaLadder currentLadder = ArenaClientState.getCurrentRankedLadder();

        drawSection(graphics, x, y, w, 62, SECTION_ALT);

        drawSectionTitle(graphics, x + 8, y + 7, "Ladder Atual", RANKED_ACCENT);
        drawInsetBand(graphics, x + 8, y + 22, w - 16, 14);
        drawScaledText(
            graphics,
            fitText(currentLadder.getDisplayName(), w - 24, BODY_SCALE),
            x + 12,
            y + 26,
            RANKED_ACCENT,
            BODY_SCALE
        );

        // Format + level badge
        String fmtLabel =
            currentLadder.getBattleTypeLabel() +
            "  Lv." +
            currentLadder.getAdjustLevel();
        drawBadgeRight(
            graphics,
            x + w - 8,
            y + 26,
            fmtLabel,
            INFO_ACCENT,
            SMALL_SCALE
        );

        // Chips: total ranked players, in queue, active battles
        drawMetricChip(
            graphics,
            x + 8,
            y + 42,
            50,
            "Players",
            String.valueOf(ArenaClientState.getTotalRankedPlayers()),
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 42,
            50,
            "Na Fila",
            String.valueOf(ArenaClientState.getPlayersInQueue()),
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 42,
            54,
            "Batalhas",
            String.valueOf(ArenaClientState.getActiveBattles()),
            SUCCESS_ACCENT
        );
    }

    // ── Ladder tab panel ─────────────────────────────────────────────────
    private void drawLadderTabPanel(DrawContext graphics) {
        int x = guiLeft + 8,
            y = guiTop + 100;
        int w = GUI_WIDTH - 16;
        int h = tabPanelHeight();

        drawSection(graphics, x, y, w, h, SECTION_ALT);
        drawSectionTitle(
            graphics,
            x + 8,
            y + 7,
            "Ladders Ativas",
            RANKED_ACCENT
        );

        if (ArenaClientState.getActiveRankedLadders().isEmpty()) {
            drawCenteredScaledText(
                graphics,
                "Nenhuma ladder ranqueada configurada.",
                x + w / 2,
                y + 22,
                TEXT_DIM,
                BODY_SCALE
            );
        }
        // Tab buttons are child widgets — rendered by super.render()
    }

    // ── Leaderboard panel ────────────────────────────────────────────────
    private void drawLeaderboard(DrawContext graphics) {
        int tabH = tabPanelHeight();
        int x = guiLeft + 8;
        int y = guiTop + 100 + tabH + 4;
        int w = GUI_WIDTH - 16;
        int footerY = guiTop + GUI_HEIGHT - 28;
        int h = Math.max(90, footerY - y);

        List<String> entries = ArenaClientState.getLeaderboardEntries();

        drawSection(graphics, x, y, w, h, SECTION_BG);
        drawSectionTitle(
            graphics,
            x + 8,
            y + 7,
            "Top Treinadores",
            RANKED_ACCENT
        );

        // Column headers
        int headerY = y + 18;
        drawInsetBand(graphics, x + 6, headerY, w - 12, 9);
        drawScaledText(
            graphics,
            "#",
            x + 10,
            headerY + 2,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            "Nome",
            x + 24,
            headerY + 2,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            "Elo",
            x + w - 90,
            headerY + 2,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            "W / L",
            x + w - 50,
            headerY + 2,
            TEXT_DIM,
            SMALL_SCALE
        );

        if (entries.isEmpty()) {
            drawCenteredScaledText(
                graphics,
                "Nenhuma partida ranqueada registrada ainda.",
                x + w / 2,
                headerY + 20,
                TEXT_DIM,
                BODY_SCALE
            );
            return;
        }

        int rowY = headerY + 11;
        for (
            int i = 0;
            i < Math.min(entries.size(), 10) && rowY + 9 < y + h - 4;
            i++
        ) {
            int rowFill = (i % 2 == 0)
                ? SECTION_INSET
                : darken(SECTION_INSET, 4);
            int accent = switch (i) {
                case 0 -> WARNING_ACCENT; // gold
                case 1 -> color(192, 200, 210, 255); // silver
                case 2 -> QUICK_ACCENT; // bronze-ish
                default -> TEXT_SECONDARY;
            };

            graphics.fill(x + 6, rowY, x + w - 6, rowY + 9, rowFill);
            graphics.fill(x + 6, rowY, x + 8, rowY + 9, accent); // left stripe

            // Position
            String pos = (i + 1) + ".";
            drawScaledText(
                graphics,
                pos,
                x + 10,
                rowY + 2,
                accent,
                SMALL_SCALE
            );

            // Parse raw entry string: "Name  |  Elo  |  W-L"
            String raw = entries.get(i);
            String[] parts = raw.split("\\|");
            String entryName = parts.length > 0 ? parts[0].trim() : raw;
            String entryElo =
                parts.length > 1 ? parts[1].replace("Elo", "").trim() : "";
            String entryWL = parts.length > 2 ? parts[2].trim() : "";

            drawScaledText(
                graphics,
                fitText(entryName, w - 130, SMALL_SCALE),
                x + 24,
                rowY + 2,
                i == 0 ? TEXT_PRIMARY : TEXT_SECONDARY,
                SMALL_SCALE
            );
            drawScaledText(
                graphics,
                entryElo,
                x + w - 90,
                rowY + 2,
                RANKED_ACCENT,
                SMALL_SCALE
            );
            drawScaledText(
                graphics,
                entryWL,
                x + w - 50,
                rowY + 2,
                TEXT_DIM,
                SMALL_SCALE
            );

            rowY += 10;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tab highlight overlay (drawn after super.render so it sits on top)
    // ══════════════════════════════════════════════════════════════════════

    private void highlightSelectedTab(DrawContext graphics) {
        List<ArenaLadder> ladders = ArenaClientState.getActiveRankedLadders();
        String selectedId = ArenaClientState.getCurrentRankedLadder().getId();

        for (
            int i = 0;
            i < Math.min(ladderTabButtons.size(), ladders.size());
            i++
        ) {
            if (!ladders.get(i).getId().equals(selectedId)) continue;

            StyledButton btn = ladderTabButtons.get(i);
            int bx = btn.getX(),
                by = btn.getY(),
                bw = btn.getWidth(),
                bh = btn.getHeight();

            // Bright top + left edge, dimmer bottom + right
            graphics.fill(bx, by, bx + bw, by + 1, RANKED_ACCENT);
            graphics.fill(bx, by, bx + 1, by + bh, RANKED_ACCENT);
            graphics.fill(
                bx + bw - 1,
                by,
                bx + bw,
                by + bh,
                darken(RANKED_ACCENT, 30)
            );
            graphics.fill(
                bx,
                by + bh - 1,
                bx + bw,
                by + bh,
                darken(RANKED_ACCENT, 30)
            );
            // Gold underline
            graphics.fill(
                bx + 3,
                by + bh - 2,
                bx + bw - 3,
                by + bh - 1,
                color(255, 226, 140, 255)
            );
            break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private int tabPanelHeight() {
        int count = ArenaClientState.getActiveRankedLadders().size();
        if (count <= 0) return 32;
        int cols = Math.min(5, count);
        int rows = (int) Math.ceil(count / (double) cols);
        return 20 + rows * 13 + 4;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
