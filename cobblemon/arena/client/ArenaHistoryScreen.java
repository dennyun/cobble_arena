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
    private static final int MATCH_ROW_H = 60; // 26 info + 32 cards pokemon + 2 gap
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
    /** Tooltip de Pokémon pendente para renderizar após o scissor (evita clipping). */
    private cobblemon.arena.network.ArenaTransitionPokemonEntryPayload pendingTooltipPk =
        null;
    private int pendingTooltipMx = 0,
        pendingTooltipMy = 0;
    /** Cache de FloatingState para modelos 3D dos pokemon no histórico. */
    private final java.util.Map<
        String,
        com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
    > histFloatingStates = new java.util.HashMap<>();

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
        g.fill(clipX1, clipY1, clipX2, clipY2, SECTION_ALT);
        int drawY = clipY1 - scrollOffset;

        pendingTooltipPk = null; // Reseta a cada frame
        if (activeTab == HistoryTab.MATCHES) {
            drawMatchRows(g, mx, my, clipX1, drawY, clipX2 - clipX1);
        } else {
            drawPokemonRows(g, clipX1, drawY, clipX2 - clipX1);
        }
        g.disableScissor();

        // Renderiza tooltip de Pokémon fora do scissor (sem clipping)
        if (pendingTooltipPk != null) {
            drawHistoryPokemonTooltip(
                g,
                pendingTooltipMx,
                pendingTooltipMy,
                pendingTooltipPk
            );
        }

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

            // ── Cards de Pokémon no estilo vermelho ──────────────────────────
            java.util.List<
                cobblemon.arena.network.ArenaTransitionPokemonEntryPayload
            > ownTeam = e.ownTeam(),
                oppTeam = e.opponentTeam();
            int cardSize = 28,
                cardGap = 2;
            int tBW = 6 * (cardSize + cardGap) - cardGap;
            int cardsStartX = x + w / 2 - (tBW * 2 + 10) / 2;
            int cardsY = rowY + 26;

            for (int p = 0; p < 6; p++) {
                int csx = cardsStartX + p * (cardSize + cardGap);
                cobblemon.arena.network.ArenaTransitionPokemonEntryPayload pk =
                    p < ownTeam.size() ? ownTeam.get(p) : null;
                boolean hov =
                    mx >= csx &&
                    mx < csx + cardSize &&
                    my >= cardsY &&
                    my < cardsY + cardSize;
                drawHistoryPokemonCard(
                    g,
                    csx,
                    cardsY,
                    cardSize,
                    pk,
                    hov,
                    "o" + i + "_" + p
                );
                if (hov && pk != null) {
                    pendingTooltipPk = pk;
                    pendingTooltipMx = mx;
                    pendingTooltipMy = my;
                }
            }
            g.fill(
                cardsStartX + tBW + 3,
                cardsY + 3,
                cardsStartX + tBW + 5,
                cardsY + cardSize - 3,
                color(132, 54, 62, 200)
            );
            for (int p = 0; p < 6; p++) {
                int csx = cardsStartX + tBW + 10 + p * (cardSize + cardGap);
                cobblemon.arena.network.ArenaTransitionPokemonEntryPayload pk =
                    p < oppTeam.size() ? oppTeam.get(p) : null;
                boolean hov =
                    mx >= csx &&
                    mx < csx + cardSize &&
                    my >= cardsY &&
                    my < cardsY + cardSize;
                drawHistoryPokemonCard(
                    g,
                    csx,
                    cardsY,
                    cardSize,
                    pk,
                    hov,
                    "p" + i + "_" + p
                );
                if (hov && pk != null) {
                    pendingTooltipPk = pk;
                    pendingTooltipMx = mx;
                    pendingTooltipMy = my;
                }
            }

            rowY += MATCH_ROW_H + 1;
        }
    }

    private void drawHistoryPokemonCard(
        DrawContext g,
        int sx,
        int sy,
        int size,
        cobblemon.arena.network.ArenaTransitionPokemonEntryPayload pk,
        boolean hovered,
        String stateKey
    ) {
        int slotBg =
            pk != null
                ? (hovered ? color(88, 44, 50, 240) : color(58, 24, 30, 220))
                : color(30, 10, 14, 200);
        int bHi = hovered ? color(224, 112, 82, 255) : color(164, 68, 74, 255);
        int bSh = color(80, 30, 36, 255);
        g.fill(sx, sy, sx + size, sy + size, color(10, 4, 6, 220));
        g.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, slotBg);
        g.fill(
            sx + 1,
            sy + size - 3,
            sx + size - 1,
            sy + size - 1,
            color(0, 0, 0, 50)
        );
        g.fill(sx + 1, sy, sx + size - 1, sy + 1, bHi);
        g.fill(sx, sy + 1, sx + 1, sy + size - 1, bHi);
        g.fill(sx + 1, sy + size - 1, sx + size - 1, sy + size, bSh);
        g.fill(sx + size - 1, sy + 1, sx + size, sy + size - 1, bSh);
        g.fill(sx + 2, sy + 1, sx + size - 2, sy + 2, color(255, 255, 255, 12));
        if (pk == null) {
            var tr =
                net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            g.drawText(
                tr,
                "○",
                sx + size / 2 - tr.getWidth("○") / 2,
                sy + size / 2 - 4,
                color(80, 60, 60, 180),
                false
            );
            return;
        }
        var tr =
            net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        String key = pk.speciesKey();
        boolean rendered = false;
        if (key != null && !key.isBlank()) {
            try {
                var sid = net.minecraft.util.Identifier.of(key);
                var state = histFloatingStates.computeIfAbsent(stateKey, k ->
                    new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState()
                );
                var rot = new org.joml.Quaternionf().rotationXYZ(
                    (float) Math.toRadians(12),
                    (float) Math.toRadians(30),
                    0f
                );
                g.getMatrices().push();
                g.getMatrices().translate(sx + size / 2f, sy + 4f, 0f);
                g.getMatrices().scale(1.9f, 1.9f, 1f);
                com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt.drawProfilePokemon(
                    sid,
                    g.getMatrices(),
                    rot,
                    com.cobblemon.mod.common.entity.PoseType.PROFILE,
                    state,
                    0f,
                    3.5f,
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
                g.getMatrices().pop();
                rendered = true;
            } catch (Exception ignored) {}
        }
        if (!rendered) {
            String nm = pk.speciesName() != null ? pk.speciesName() : "?";
            String ab = nm.length() > 4 ? nm.substring(0, 4) : nm;
            float sc = 0.65f;
            int aw = (int) (tr.getWidth(ab) * sc);
            g.getMatrices().push();
            g
                .getMatrices()
                .translate(sx + (size - aw) / 2, sy + size / 2 - 4, 0f);
            g.getMatrices().scale(sc, sc, 1f);
            g.drawText(tr, ab, 0, 0, color(248, 242, 236, 255), true);
            g.getMatrices().pop();
        }
        if (pk.level() > 0) {
            String lv = "Nv " + pk.level();
            float lvSc = 0.55f;
            g.getMatrices().push();
            g.getMatrices().translate(sx + 2, sy + size - 8, 0f);
            g.getMatrices().scale(lvSc, lvSc, 1f);
            g.drawText(tr, lv, 0, 0, color(199, 170, 150, 255), false);
            g.getMatrices().pop();
        }
        // Item icon renderizado POR ÚLTIMO (garante que fica na frente de tudo)
        if (pk.heldItemName() != null && !pk.heldItemName().isBlank()) {
            try {
                var iid = ArenaBattleTransitionOverlay.resolveItemIdentifier(
                    pk.heldItemName()
                );
                if (iid != null) {
                    var it = net.minecraft.registry.Registries.ITEM.get(iid);
                    if (it != null && it != net.minecraft.item.Items.AIR) {
                        int ix = sx + size - 10,
                            iy = sy + size - 10;

                        g.getMatrices().push();
                        g.getMatrices().translate(ix - 2, iy - 2, 300.0); // z=300 garante que fica na frente
                        g.getMatrices().scale(0.70f, 0.70f, 0.70f);
                        g.drawItem(new net.minecraft.item.ItemStack(it), 0, 0);
                        g.getMatrices().pop();
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /** Tooltip exibido ao passar o mouse sobre o slot de item de um Pokémon no histórico. */
    private void drawHistoryPokemonTooltip(
        DrawContext g,
        int mx,
        int my,
        cobblemon.arena.network.ArenaTransitionPokemonEntryPayload pk
    ) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String pkName =
            pk.speciesName() != null && !pk.speciesName().isBlank()
                ? pk.speciesName()
                : "???";
        lines.add(pkName + (pk.level() > 0 ? "  Nv." + pk.level() : ""));
        if (!pk.abilityName().isBlank()) lines.add("Hab: " + pk.abilityName());
        if (!pk.natureName().isBlank()) lines.add("Nat: " + pk.natureName());

        net.minecraft.item.ItemStack itemStack =
            net.minecraft.item.ItemStack.EMPTY;
        String itemName = "";
        if (pk.heldItemName() != null && !pk.heldItemName().isBlank()) {
            try {
                net.minecraft.util.Identifier itemId =
                    ArenaBattleTransitionOverlay.resolveItemIdentifier(
                        pk.heldItemName()
                    );
                if (itemId != null) {
                    net.minecraft.item.Item it =
                        net.minecraft.registry.Registries.ITEM.get(itemId);
                    if (it != null && it != net.minecraft.item.Items.AIR) {
                        itemStack = new net.minecraft.item.ItemStack(it);
                        itemName =
                            ArenaBattleTransitionOverlay.cleanItemDisplayName(
                                itemStack.getName().getString()
                            );
                    }
                }
            } catch (Exception ignored) {}
        }
        boolean hasItem = !itemStack.isEmpty();
        int itemH = hasItem ? 20 : 0;

        int padX = 6,
            padY = 4,
            lineH = 10;
        int boxW = 0;
        for (String l : lines)
            boxW = Math.max(boxW, textRenderer.getWidth(l) + padX * 2);
        if (hasItem) boxW = Math.max(
            boxW,
            18 + textRenderer.getWidth(itemName) + padX * 2
        );
        boxW = Math.max(boxW, 120);
        int boxH = padY * 2 + itemH + lines.size() * lineH;

        int bx = mx + 6;
        int by = my - boxH - 4;
        if (bx + boxW > guiLeft + GUI_WIDTH - 4) bx = mx - boxW - 6;
        if (by < guiTop + 4) by = my + 4;

        g.fill(bx, by, bx + boxW, by + boxH, color(10, 6, 18, 245));
        g.fill(bx, by, bx + boxW, by + 1, INFO_ACCENT);
        g.fill(bx, by, bx + 1, by + boxH, INFO_ACCENT);
        g.fill(bx + boxW - 1, by, bx + boxW, by + boxH, color(60, 50, 80, 200));
        g.fill(bx, by + boxH - 1, bx + boxW, by + boxH, color(60, 50, 80, 200));

        int ty = by + padY;
        if (hasItem) {
            g.drawItem(itemStack, bx + padX, ty + 1);
            g.drawText(
                textRenderer,
                itemName,
                bx + padX + 18,
                ty + 5,
                TEXT_PRIMARY,
                false
            );
            ty += itemH;
        }
        for (int li = 0; li < lines.size(); li++) {
            int col = li == 0 ? RANKED_ACCENT : TEXT_DIM;
            g.drawText(textRenderer, lines.get(li), bx + padX, ty, col, false);
            ty += lineH;
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
