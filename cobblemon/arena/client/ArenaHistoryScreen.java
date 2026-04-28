package cobblemon.arena.client;

import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ArenaHistoryScreen extends ArenaScreenBase {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int MATCH_ROW_H = 26;
    private static final int POKEMON_ROW_H = 18;
    private static final int CONTENT_TOP_OFFSET = 114; // guiTop + this
    private static final int CONTENT_BOT_OFFSET = 28; // from guiTop+GUI_HEIGHT

    // ── State ──────────────────────────────────────────────────────────────
    private final Screen parent;
    private StyledButton matchesTabBtn;
    private StyledButton pokemonTabBtn;
    private HistoryTab activeTab = HistoryTab.MATCHES;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private boolean dragging = false;

    // ─────────────────────────────────────────────────────────────────────

    public ArenaHistoryScreen(Screen parent) {
        super(Text.translatable("gui.cobblemon_arena.title.history"));
        this.parent = parent;
    }

    // ══════════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();
        scrollOffset = 0;

        // ── Tab buttons ────────────────────────────────────────────────────
        matchesTabBtn = addDrawableChild(
            new StyledButton(
                guiLeft + 16,
                guiTop + 94,
                96,
                14,
                Text.literal("⚔  Partidas Recentes"),
                b -> switchTab(HistoryTab.MATCHES)
            )
        );
        pokemonTabBtn = addDrawableChild(
            new StyledButton(
                guiLeft + 116,
                guiTop + 94,
                108,
                14,
                Text.literal("◉  Pokémon Mais Usados"),
                b -> switchTab(HistoryTab.POKEMON)
            )
        );

        // ── Navigation ────────────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        updateGuiPosition();
        int mx = (int) toGuiX(mouseX);
        int my = (int) toGuiY(mouseY);

        renderFullScreenBg(g);
        pushGuiScale(g);

        // ── Header ─────────────────────────────────────────────────────────
        renderScreenFrame(
            g,
            "Histórico  —  " + ArenaClientState.getCurrentSeasonName(),
            "Perfil",
            INFO_ACCENT
        );

        // ── Summary strip ─────────────────────────────────────────────────
        drawSummaryStrip(g);

        // ── Content panel ─────────────────────────────────────────────────
        int panelX = guiLeft + 8;
        int panelY = guiTop + 110;
        int panelW = GUI_WIDTH - 16;
        int panelH = GUI_HEIGHT - CONTENT_BOT_OFFSET - (panelY - guiTop);

        drawSection(g, panelX, panelY, panelW, panelH, SECTION_ALT);
        // Tab highlight line under active tab button
        drawTabHighlight(g);

        contentHeight = computeContentHeight();
        scrollOffset = MathHelper.clamp(
            scrollOffset,
            0,
            Math.max(0, contentHeight - (panelH - 8))
        );

        int clipX1 = panelX + 4;
        int clipY1 = panelY + 4;
        int clipX2 = panelX + panelW - 8;
        int clipY2 = panelY + panelH - 4;

        g.enableScissor(clipX1, clipY1, clipX2, clipY2);
        int drawY = clipY1 - scrollOffset;

        if (activeTab == HistoryTab.MATCHES) {
            drawMatchRows(g, mx, my, clipX1, drawY, clipX2 - clipX1);
        } else {
            drawPokemonRows(g, clipX1, drawY, clipX2 - clipX1);
        }
        g.disableScissor();

        // ── Scrollbar ─────────────────────────────────────────────────────
        if (contentHeight > panelH - 8) {
            drawScrollbar(
                g,
                panelX + panelW - 6,
                clipY1,
                4,
                clipY2 - clipY1,
                contentHeight - (panelH - 8)
            );
        }

        super.render(g, mx, my, delta);
        popGuiScale(g);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Summary strip (y=30..93)
    // ══════════════════════════════════════════════════════════════════════

    private void drawSummaryStrip(DrawContext g) {
        int x = guiLeft + 8,
            y = guiTop + 30;
        int rW = ArenaClientState.getRankedWins();
        int rL = ArenaClientState.getRankedLosses();
        int qW = ArenaClientState.getQuickWins();
        int qL = ArenaClientState.getQuickLosses();

        int entryCount =
            activeTab == HistoryTab.MATCHES
                ? ArenaClientState.getRecentMatchHistory().size()
                : ArenaClientState.getPokemonUsage().size();

        drawSection(g, x, y, GUI_WIDTH - 16, 58, SECTION_BG);

        // Player name
        drawScaledText(
            g,
            ArenaClientState.getPlayerName(),
            x + 8,
            y + 8,
            TEXT_PRIMARY,
            BODY_SCALE
        );

        // Rank badge
        drawBadgeRight(
            g,
            x + GUI_WIDTH - 16 - 8,
            y + 8,
            ArenaClientState.getRankTitle(),
            rankAccentColor(ArenaClientState.getRankTitle()),
            SMALL_SCALE
        );

        // Metric chips
        int cw = 82,
            gap = 5,
            cy = y + 28;
        drawMetricChip(
            g,
            x + 8,
            cy,
            cw,
            "Total Batalhas",
            String.valueOf(ArenaClientState.getTotalBattles()),
            TEXT_PRIMARY
        );
        drawMetricChip(
            g,
            x + 8 + cw + gap,
            cy,
            cw,
            "Ranqueado",
            rW + " - " + rL,
            RANKED_ACCENT
        );
        drawMetricChip(
            g,
            x + 8 + (cw + gap) * 2,
            cy,
            cw,
            "Casual",
            qW + " - " + qL,
            QUICK_ACCENT
        );
        drawMetricChip(
            g,
            x + 8 + (cw + gap) * 3,
            cy,
            cw,
            activeTab == HistoryTab.MATCHES ? "Recentes" : "Rastreados",
            String.valueOf(entryCount),
            INFO_ACCENT
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Match rows
    // ══════════════════════════════════════════════════════════════════════

    private void drawMatchRows(
        DrawContext g,
        int mx,
        int my,
        int x,
        int startY,
        int w
    ) {
        List<ArenaMatchHistoryEntryPayload> entries =
            ArenaClientState.getRecentMatchHistory();

        if (entries.isEmpty()) {
            drawCenteredScaledText(
                g,
                "Nenhuma partida registrada ainda.",
                x + w / 2,
                startY + 36,
                TEXT_DIM,
                BODY_SCALE
            );
            return;
        }

        int rowY = startY;
        for (int i = 0; i < entries.size(); i++) {
            ArenaMatchHistoryEntryPayload e = entries.get(i);
            boolean win = e.victory();

            // Row bg — alternating, with tinted win/loss left edge
            int rowBg = (i % 2 == 0) ? SECTION_INSET : darken(SECTION_INSET, 4);
            drawSection(g, x, rowY, w, MATCH_ROW_H, rowBg);

            // Left result stripe
            int stripe = win ? SUCCESS_ACCENT : color(220, 90, 90, 255);
            g.fill(x, rowY, x + 3, rowY + MATCH_ROW_H, stripe);
            g.fill(
                x,
                rowY,
                x + 1,
                rowY + MATCH_ROW_H,
                mix(stripe, color(255, 255, 255, 255), 0.4f)
            );

            // Result badge (WIN / LOSS)
            String badge = win ? "W" : "L";
            int badgeColor = win ? SUCCESS_ACCENT : color(220, 90, 90, 255);
            int badgeBg = win
                ? mix(SECTION_INSET, SUCCESS_ACCENT, 0.14f)
                : mix(SECTION_INSET, color(220, 90, 90, 255), 0.14f);
            int badgeX = x + 6,
                badgeY = rowY + MATCH_ROW_H / 2 - 5;
            g.fill(badgeX, badgeY, badgeX + 12, badgeY + 10, badgeBg);
            g.fill(
                badgeX,
                badgeY,
                badgeX + 12,
                badgeY + 1,
                mix(badgeColor, color(255, 255, 255, 255), 0.3f)
            );
            drawCenteredScaledText(
                g,
                badge,
                badgeX + 6,
                badgeY + 2,
                badgeColor,
                0.78f
            );

            // Opponent name
            String opponentLabel =
                "vs  " + fitText(e.opponentName(), w - 180, BODY_SCALE);
            drawScaledText(
                g,
                opponentLabel,
                x + 22,
                rowY + 4,
                TEXT_PRIMARY,
                BODY_SCALE
            );

            // Ladder / mode chip
            boolean ranked = e.ranked();
            String modeText = ranked ? "Ranked" : "Casual";
            int modeColor = ranked ? RANKED_ACCENT : QUICK_ACCENT;
            drawScaledText(
                g,
                modeText,
                x + 22,
                rowY + 15,
                modeColor,
                SMALL_SCALE
            );

            String ladderFmt = fitText(e.ladderDisplayName(), 100, SMALL_SCALE);
            drawScaledText(
                g,
                "·  " + ladderFmt,
                x +
                    22 +
                    Math.round(textRenderer.getWidth(modeText) * SMALL_SCALE) +
                    3,
                rowY + 15,
                TEXT_DIM,
                SMALL_SCALE
            );

            // Timestamp
            String timeStr = TIME_FMT.format(
                Instant.ofEpochMilli(e.playedAtMs()).atZone(
                    ZoneId.systemDefault()
                )
            );
            int timeW = Math.round(
                textRenderer.getWidth(timeStr) * SMALL_SCALE
            );
            drawScaledText(
                g,
                timeStr,
                x + w - timeW - 4,
                rowY + 4,
                TEXT_DIM,
                SMALL_SCALE
            );

            // ELO delta (ranked only)
            if (ranked) {
                int delta = e.ratingDelta();
                String deltaStr =
                    (delta >= 0 ? "+" : "") + delta + "  →  " + e.ratingAfter();
                int deltaColor =
                    delta >= 0 ? SUCCESS_ACCENT : color(220, 90, 90, 255);
                int deltaW = Math.round(
                    textRenderer.getWidth(deltaStr) * SMALL_SCALE
                );
                drawScaledText(
                    g,
                    deltaStr,
                    x + w - deltaW - 4,
                    rowY + 15,
                    deltaColor,
                    SMALL_SCALE
                );
            } else {
                drawScaledText(
                    g,
                    "casual",
                    x + w - 34,
                    rowY + 15,
                    TEXT_DIM,
                    SMALL_SCALE
                );
            }

            rowY += MATCH_ROW_H + 1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Pokémon usage rows
    // ══════════════════════════════════════════════════════════════════════

    private void drawPokemonRows(DrawContext g, int x, int startY, int w) {
        List<ArenaPokemonUsageEntryPayload> entries =
            ArenaClientState.getPokemonUsage();

        if (entries.isEmpty()) {
            drawCenteredScaledText(
                g,
                "Nenhum dado de Pokémon registrado ainda.",
                x + w / 2,
                startY + 36,
                TEXT_DIM,
                BODY_SCALE
            );
            return;
        }

        // Column header
        int headerY = startY;
        drawInsetBand(g, x, headerY, w, POKEMON_ROW_H);
        drawScaledText(g, "#", x + 4, headerY + 4, TEXT_DIM, SMALL_SCALE);
        drawScaledText(
            g,
            "Pokémon",
            x + 20,
            headerY + 4,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            g,
            "Usos",
            x + w - 126,
            headerY + 4,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            g,
            "Vitórias",
            x + w - 96,
            headerY + 4,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            g,
            "Derrotas",
            x + w - 62,
            headerY + 4,
            TEXT_DIM,
            SMALL_SCALE
        );
        drawScaledText(
            g,
            "Taxa",
            x + w - 26,
            headerY + 4,
            TEXT_DIM,
            SMALL_SCALE
        );

        int rowY = headerY + POKEMON_ROW_H + 1;
        for (int i = 0; i < entries.size(); i++) {
            ArenaPokemonUsageEntryPayload e = entries.get(i);
            int rowBg = (i % 2 == 0) ? SECTION_INSET : darken(SECTION_INSET, 4);
            int winRate = getWinRate(e.wins(), e.losses());

            drawSection(g, x, rowY, w, POKEMON_ROW_H, rowBg);

            // Rank number (top 3 highlighted)
            int posColor = switch (i) {
                case 0 -> WARNING_ACCENT;
                case 1 -> color(192, 200, 210, 255);
                case 2 -> QUICK_ACCENT;
                default -> TEXT_DIM;
            };
            drawScaledText(
                g,
                "#" + (i + 1),
                x + 4,
                rowY + 4,
                posColor,
                SMALL_SCALE
            );

            // Species name
            String specLabel = fitText(e.speciesName(), w - 150, BODY_SCALE);
            drawScaledText(
                g,
                specLabel,
                x + 20,
                rowY + 4,
                TEXT_PRIMARY,
                BODY_SCALE
            );

            // Stats
            drawScaledText(
                g,
                String.valueOf(e.uses()),
                x + w - 126,
                rowY + 4,
                INFO_ACCENT,
                SMALL_SCALE
            );
            drawScaledText(
                g,
                String.valueOf(e.wins()),
                x + w - 96,
                rowY + 4,
                SUCCESS_ACCENT,
                SMALL_SCALE
            );
            drawScaledText(
                g,
                String.valueOf(e.losses()),
                x + w - 62,
                rowY + 4,
                color(220, 90, 90, 255),
                SMALL_SCALE
            );

            // Win-rate with colour coding
            int rateColor =
                winRate >= 60
                    ? SUCCESS_ACCENT
                    : winRate >= 45
                        ? RANKED_ACCENT
                        : color(220, 90, 90, 255);
            drawScaledText(
                g,
                winRate + "%",
                x + w - 26,
                rowY + 4,
                rateColor,
                SMALL_SCALE
            );

            rowY += POKEMON_ROW_H + 1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tab highlight
    // ══════════════════════════════════════════════════════════════════════

    private void drawTabHighlight(DrawContext g) {
        StyledButton sel =
            activeTab == HistoryTab.MATCHES ? matchesTabBtn : pokemonTabBtn;
        if (sel == null) return;
        int ax = sel.getX(),
            ay = sel.getY(),
            aw = sel.getWidth(),
            ah = sel.getHeight();
        int accent =
            activeTab == HistoryTab.MATCHES ? QUICK_ACCENT : RANKED_ACCENT;

        g.fill(ax, ay, ax + aw, ay + 1, accent);
        g.fill(ax, ay, ax + 1, ay + ah, accent);
        g.fill(ax + aw - 1, ay, ax + aw, ay + ah, darken(accent, 28));
        g.fill(ax, ay + ah - 1, ax + aw, ay + ah, darken(accent, 28));
        g.fill(
            ax + 3,
            ay + ah - 2,
            ax + aw - 3,
            ay + ah - 1,
            mix(accent, color(255, 255, 255, 255), 0.35f)
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Scrollbar
    // ══════════════════════════════════════════════════════════════════════

    private void drawScrollbar(
        DrawContext g,
        int x,
        int y,
        int w,
        int trackH,
        int maxScroll
    ) {
        // Track
        g.fill(x, y, x + w, y + trackH, darken(SECTION_INSET, 6));
        g.fill(x, y, x + w, y + 1, darken(BORDER_HIGHLIGHT, 16));
        g.fill(x, y + trackH - 1, x + w, y + trackH, darken(BORDER_COLOR, 20));

        if (maxScroll <= 0) return;

        int thumbH = Math.max(16, (trackH * trackH) / (trackH + maxScroll));
        int travel = trackH - thumbH;
        int thumbY =
            y + Math.round(((float) scrollOffset / maxScroll) * travel);

        g.fill(x, thumbY, x + w, thumbY + thumbH, BORDER_HIGHLIGHT);
        g.fill(x, thumbY, x + w, thumbY + 1, color(255, 255, 255, 36));
        g.fill(
            x,
            thumbY + thumbH - 1,
            x + w,
            thumbY + thumbH,
            darken(BORDER_COLOR, 18)
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mouse / scroll events
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(
        double mx,
        double my,
        double horizontal,
        double vertical
    ) {
        if (isOverContent(mx, my)) {
            scroll((int) (-Math.signum(vertical) * 14));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && isOverScrollbar(mx, my)) {
            dragging = true;
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(
        double mx,
        double my,
        int btn,
        double dx,
        double dy
    ) {
        if (dragging) {
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private void switchTab(HistoryTab tab) {
        if (activeTab == tab) return;
        activeTab = tab;
        scrollOffset = 0;
    }

    private void scroll(int amount) {
        scrollOffset = MathHelper.clamp(
            scrollOffset + amount,
            0,
            Math.max(0, contentHeight - contentViewHeight())
        );
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackY = contentAreaY();
        int trackH = contentViewHeight();
        int maxScroll = Math.max(1, contentHeight - trackH);
        int thumbH = Math.max(16, (trackH * trackH) / (trackH + maxScroll));
        int travel = Math.max(1, trackH - thumbH);
        int rel = MathHelper.clamp(
            (int) mouseY - trackY - thumbH / 2,
            0,
            travel
        );
        scrollOffset = Math.round(((float) rel / travel) * maxScroll);
    }

    private boolean isOverContent(double mx, double my) {
        int cx = guiLeft + 12,
            cy = contentAreaY(),
            cw = GUI_WIDTH - 28,
            ch = contentViewHeight();
        return mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
    }

    private boolean isOverScrollbar(double mx, double my) {
        int sx = guiLeft + GUI_WIDTH - 14,
            sy = contentAreaY(),
            sw = 6,
            sh = contentViewHeight();
        return mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
    }

    private int contentAreaY() {
        return guiTop + CONTENT_TOP_OFFSET;
    }

    private int contentViewHeight() {
        return guiTop + GUI_HEIGHT - CONTENT_BOT_OFFSET - contentAreaY();
    }

    private int computeContentHeight() {
        if (activeTab == HistoryTab.MATCHES) {
            List<?> e = ArenaClientState.getRecentMatchHistory();
            return e.isEmpty() ? 64 : e.size() * (MATCH_ROW_H + 1);
        } else {
            List<?> e = ArenaClientState.getPokemonUsage();
            return e.isEmpty()
                ? 64
                : POKEMON_ROW_H + 1 + e.size() * (POKEMON_ROW_H + 1);
        }
    }

    // ── Tab enum ──────────────────────────────────────────────────────────

    private enum HistoryTab {
        MATCHES,
        POKEMON,
    }
}
