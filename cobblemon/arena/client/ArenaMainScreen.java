package cobblemon.arena.client;

import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.CancelQueuePacket;
import cobblemon.arena.network.JoinQueuePacket;
import cobblemon.arena.network.QuestEntryPayload;
import cobblemon.arena.network.SpectateArenaBattlePacket;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ArenaMainScreen extends ArenaScreenBase {

    private static final int MAIN_HEIGHT = 320;

    // ── Casual queue controls ──────────────────────────────────────────────
    private CycleSelectorButton casualFormatButton;
    private CycleSelectorButton casualLevelButton;
    private CheckboxWidget casualLegendaryCheckbox;
    private String casualFormat = "Singles";
    private String casualLevel = "50";
    private boolean casualAllowLegendaries = false;

    // ── Ranked queue controls (always Lv50, no legendaries) ───────────────
    private CycleSelectorButton rankedFormatButton;
    private String rankedFormat = "Singles";

    // ── Footer Missões button (label updated each frame) ──────────────────
    private StyledButton questButton;

    private final ArenaPartyPreviewRenderer partyPreview =
        new ArenaPartyPreviewRenderer();

    // ─────────────────────────────────────────────────────────────────────
    private static String t(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    public ArenaMainScreen() {
        super(Text.translatable("gui.cobblemon_arena.main_screen.title"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();
        initCasualControls();
        initRankedControls();
        initActionButtons();
        initFooterButtons();
    }

    // ── Casual controls ────────────────────────────────────────────────────

    private void initCasualControls() {
        int cx = casualCardX();
        int cy = casualCardY();

        casualFormatButton = addDrawableChild(
            new CycleSelectorButton(
                cx + 14,
                cy + 50,
                78,
                12,
                List.of("Singles", "Doubles", "Triples", "Monotype"),
                casualFormat,
                ">",
                selection -> casualFormat = selection
            )
        );

        casualLevelButton = addDrawableChild(
            new CycleSelectorButton(
                cx + 96,
                cy + 50,
                48,
                12,
                List.of("50", "100"),
                casualLevel,
                ">",
                selection -> casualLevel = selection
            )
        );

        casualLegendaryCheckbox = new CheckboxWidget(
            cx + 14,
            cy + 68,
            Text.translatable(
                "gui.cobblemon_arena.checkbox.legendaries_allowed"
            ),
            checked -> casualAllowLegendaries = checked
        );
        casualLegendaryCheckbox.setChecked(casualAllowLegendaries);
    }

    // ── Ranked controls ────────────────────────────────────────────────────

    private void initRankedControls() {
        int rx = rankedCardX();
        int ry = rankedCardY();

        rankedFormatButton = addDrawableChild(
            new CycleSelectorButton(
                rx + 14,
                ry + 50,
                130,
                12,
                List.of("Singles", "Doubles", "Triples"),
                rankedFormat,
                ">",
                selection -> rankedFormat = selection
            )
        );
    }

    // ── Queue / leave action buttons ───────────────────────────────────────

    private void initActionButtons() {
        boolean inQueue = QueueStatusOverlay.getInstance().isVisible();
        int buttonWidth = 90;
        int buttonHeight = 16;
        int buttonRightMargin = 14;
        int buttonY = casualCardY() + 28;

        if (!inQueue) {
            addDrawableChild(
                new StyledButton(
                    casualCardX() + 180 - buttonWidth - buttonRightMargin,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    Text.translatable(
                        "gui.cobblemon_arena.button.queue_casual"
                    ),
                    button -> queueForLadder(resolveCasualSelection()),
                    QUICK_ACCENT
                )
            );
            addDrawableChild(
                new StyledButton(
                    rankedCardX() + 180 - buttonWidth - buttonRightMargin,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    Text.translatable(
                        "gui.cobblemon_arena.button.queue_ranked"
                    ),
                    button -> queueForLadder(resolveRankedSelection()),
                    RANKED_ACCENT
                )
            );
        } else {
            addDrawableChild(
                new StyledButton(
                    guiLeft + 126,
                    guiTop + 282,
                    132,
                    16,
                    Text.translatable("gui.cobblemon_arena.button.leave_queue"),
                    button -> cancelQueue()
                )
            );
        }
    }

    // ── Footer buttons ─────────────────────────────────────────────────────
    //
    //  Available: GUI_WIDTH(384) - 2×8 margin = 368px
    //  Rules(52) + Missões(58) + History(64) + Leaderboard(60) + Spectate(60) + Close(56)
    //  = 350 widths + 5×3 gaps = 365px  ✓
    //
    private void initFooterButtons() {
        int footerY = guiTop + MAIN_HEIGHT - 22;
        int gap = 3;
        int startX = guiLeft + 8;

        int wRules = 52;
        int wQuests = 58;
        int wHistory = 64;
        int wLeader = 60;
        int wSpectate = 60;
        int wClose = 56;

        int xQuests = startX + wRules + gap;
        int xHistory = xQuests + wQuests + gap;
        int xLeader = xHistory + wHistory + gap;
        int xSpectate = xLeader + wLeader + gap;
        int xClose = xSpectate + wSpectate + gap;

        addDrawableChild(
            new StyledButton(
                startX,
                footerY,
                wRules,
                16,
                Text.translatable("gui.cobblemon_arena.button.rules"),
                b -> openSubScreen(new ArenaRulesScreen(this))
            )
        );

        questButton = addDrawableChild(
            new StyledButton(
                xQuests,
                footerY,
                wQuests,
                16,
                Text.translatable("gui.cobblemon_arena.button.quests"),
                b -> openSubScreen(new ArenaQuestsScreen(this)),
                SUCCESS_ACCENT
            )
        );

        addDrawableChild(
            new StyledButton(
                xHistory,
                footerY,
                wHistory,
                16,
                Text.translatable("gui.cobblemon_arena.button.history"),
                b -> openSubScreen(new ArenaHistoryScreen(this))
            )
        );

        addDrawableChild(
            new StyledButton(
                xLeader,
                footerY,
                wLeader,
                16,
                Text.translatable("gui.cobblemon_arena.button.leaderboard"),
                b -> openSubScreen(new ArenaLadderScreen(this))
            )
        );

        addDrawableChild(
            new StyledButton(
                xSpectate,
                footerY,
                wSpectate,
                16,
                Text.translatable("gui.cobblemon_arena.button.spectate"),
                b -> spectateBattle()
            )
        );

        addDrawableChild(
            new StyledButton(
                xClose,
                footerY,
                wClose,
                16,
                Text.translatable("gui.cobblemon_arena.button.close"),
                b -> close()
            )
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Ladder resolution
    // ══════════════════════════════════════════════════════════════════════

    private ArenaLadder resolveCasualSelection() {
        return ArenaLadder.vgcPresetByChoice(
            casualFormat,
            casualLevel,
            casualAllowLegendaries
        );
    }

    private ArenaLadder resolveRankedSelection() {
        return ArenaLadder.rankedPresetByChoice(rankedFormat, "50", false);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Network actions
    // ══════════════════════════════════════════════════════════════════════

    private void queueForLadder(ArenaLadder ladder) {
        ClientPlayNetworking.send(new JoinQueuePacket(ladder.getId()));
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.translatable(
                    "message.cobblemon_arena.joining",
                    ladder.getDisplayName()
                ),
                false
            );
        }
        close();
    }

    private void cancelQueue() {
        ClientPlayNetworking.send(new CancelQueuePacket());
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.translatable("message.cobblemon_arena.cancelling"),
                false
            );
        }
        close();
    }

    private void spectateBattle() {
        ClientPlayNetworking.send(new SpectateArenaBattlePacket());
        close();
    }

    private void openSubScreen(Screen screen) {
        MinecraftClient.getInstance().setScreen(screen);
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

        // Dynamically update Missões button label
        if (questButton != null) {
            int claimable = ArenaQuestClientState.getClaimableCount();
            questButton.setMessage(
                claimable > 0
                    ? Text.translatable(
                          "gui.cobblemon_arena.button.quests_claimable"
                      )
                    : Text.translatable("gui.cobblemon_arena.button.quests")
            );
        }

        // Full-screen background — outside scale transform
        graphics.fill(0, 0, width, height, color(0, 0, 0, 182));
        graphics.fill(0, 0, width, height / 3, color(52, 22, 18, 48));

        pushGuiScale(graphics);

        drawPanel(graphics, guiLeft, guiTop, GUI_WIDTH, MAIN_HEIGHT);
        drawHeader(
            graphics,
            t("gui.cobblemon_arena.main_screen.subtitle"),
            ArenaClientState.getCurrentSeasonName(),
            RANKED_ACCENT
        );

        drawPlayerStatsPanel(graphics);
        drawServerStatsPanel(graphics);
        List<Text> hoveredTooltip = drawPartyPreview(graphics, gmx, gmy, delta);
        drawCasualQueueCard(graphics);
        drawRankedQueueCard(graphics);
        drawMissionHintStrip(graphics);

        super.render(graphics, gmx, gmy, delta);

        if (casualLegendaryCheckbox != null) casualLegendaryCheckbox.render(
            graphics,
            gmx,
            gmy
        );

        if (
            hoveredTooltip != null && !hoveredTooltip.isEmpty()
        ) graphics.drawTooltip(textRenderer, hoveredTooltip, gmx, gmy);

        popGuiScale(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double gx = toGuiX(mouseX),
            gy = toGuiY(mouseY);
        if (
            casualLegendaryCheckbox != null &&
            casualLegendaryCheckbox.mouseClicked(gx, gy, button)
        ) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Panel drawing
    // ══════════════════════════════════════════════════════════════════════

    // ── Player stats ───────────────────────────────────────────────────────

    private void drawPlayerStatsPanel(DrawContext graphics) {
        int px = guiLeft + 8,
            py = guiTop + 28;
        int rW = ArenaClientState.getRankedWins(),
            rL = ArenaClientState.getRankedLosses();
        int qW = ArenaClientState.getQuickWins(),
            qL = ArenaClientState.getQuickLosses();
        int totW = rW + qW,
            totL = rL + qL;
        int cy1 = py + 28,
            cy2 = py + 46;
        String name = ArenaClientState.getPlayerName();

        drawSection(graphics, px, py, 180, 66, SECTION_BG);

        // Name + underline
        drawScaledText(graphics, name, px + 8, py + 8, TEXT_PRIMARY, 1.06F);
        graphics.fill(
            px + 8,
            py + 17,
            px +
                Math.min(
                    94,
                    Math.round(textRenderer.getWidth(name) * 1.06F) + 12
                ),
            py + 18,
            darken(QUICK_ACCENT, 12)
        );

        // Rank badge
        drawBadgeRight(
            graphics,
            px + 180 - 8,
            py + 8,
            ArenaClientState.getRankTitle(),
            rankAccentColor(ArenaClientState.getRankTitle()),
            0.76F
        );

        // Chips row 1
        drawMetricChip(
            graphics,
            px + 8,
            cy1,
            50,
            t("gui.cobblemon_arena.label.rating"),
            String.valueOf(ArenaClientState.getRankedRating()),
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 63,
            cy1,
            50,
            t("gui.cobblemon_arena.label.streak"),
            formatStreak(ArenaClientState.getRankedStreak()),
            ArenaClientState.getRankedStreak() >= 0
                ? SUCCESS_ACCENT
                : WARNING_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 118,
            cy1,
            54,
            t("gui.cobblemon_arena.label.win_rate"),
            getWinRate(totW, totL) + "%",
            INFO_ACCENT
        );

        // Chips row 2
        drawMetricChip(
            graphics,
            px + 8,
            cy2,
            50,
            t("gui.cobblemon_arena.label.ranked"),
            rW + "-" + rL,
            RANKED_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 63,
            cy2,
            50,
            t("gui.cobblemon_arena.label.quick"),
            qW + "-" + qL,
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 118,
            cy2,
            54,
            t("gui.cobblemon_arena.label.total"),
            String.valueOf(ArenaClientState.getTotalBattles()),
            TEXT_PRIMARY
        );
    }

    // ── Server stats ──────────────────────────────────────────────────────

    private void drawServerStatsPanel(DrawContext graphics) {
        int px = serverPanelX(),
            py = guiTop + 28;
        int available = ArenaClientState.getAvailableArenas();
        int total = ArenaClientState.getTotalArenas();
        int cy1 = py + 28,
            cy2 = py + 46;

        String status =
            available <= 0
                ? t("gui.cobblemon_arena.status.no_open_arena")
                : (ArenaClientState.getPlayersInQueue() > 0
                      ? t("gui.cobblemon_arena.status.live_queue")
                      : t("gui.cobblemon_arena.status.ready"));

        drawSection(graphics, px, py, 180, 66, SECTION_ALT);
        drawScaledText(
            graphics,
            t("gui.cobblemon_arena.section.arena_status"),
            px + 8,
            py + 8,
            QUICK_ACCENT,
            SECTION_TITLE_SCALE
        );
        graphics.fill(
            px + 8,
            py + 16,
            px + 74,
            py + 17,
            darken(QUICK_ACCENT, 18)
        );
        drawBadgeRight(
            graphics,
            px + 180 - 8,
            py + 8,
            status,
            available <= 0 ? WARNING_ACCENT : SUCCESS_ACCENT,
            0.76F
        );

        drawMetricChip(
            graphics,
            px + 8,
            cy1,
            50,
            t("gui.cobblemon_arena.label.online"),
            String.valueOf(ArenaClientState.getPlayersOnline()),
            TEXT_PRIMARY
        );
        drawMetricChip(
            graphics,
            px + 63,
            cy1,
            50,
            t("gui.cobblemon_arena.label.queued"),
            String.valueOf(ArenaClientState.getPlayersInQueue()),
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 118,
            cy1,
            54,
            t("gui.cobblemon_arena.label.active"),
            String.valueOf(ArenaClientState.getActiveBattles()),
            RANKED_ACCENT
        );

        drawMetricChip(
            graphics,
            px + 8,
            cy2,
            50,
            t("gui.cobblemon_arena.label.ready"),
            String.valueOf(available),
            SUCCESS_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 63,
            cy2,
            50,
            t("gui.cobblemon_arena.label.usage"),
            total <= 0 ? "0%" : ((total - available) * 100) / total + "%",
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            px + 118,
            cy2,
            54,
            t("gui.cobblemon_arena.label.arenas"),
            available + "/" + total,
            TEXT_PRIMARY
        );
    }

    // ── Casual queue card ──────────────────────────────────────────────────

    private void drawCasualQueueCard(DrawContext graphics) {
        int x = casualCardX(),
            y = casualCardY();
        String desc = resolveCasualSelection().getDescription();

        drawSection(graphics, x, y, 180, 108, SECTION_BG);

        // Orange accent bar
        graphics.fill(x, y + 1, x + 3, y + 107, QUICK_ACCENT);
        graphics.fill(
            x,
            y + 1,
            x + 1,
            y + 107,
            mix(QUICK_ACCENT, color(255, 255, 255, 255), 0.3F)
        );
        graphics.fill(x + 3, y + 2, x + 8, y + 106, color(255, 118, 62, 18));

        drawSectionTitle(
            graphics,
            x + 12,
            y + 8,
            t("gui.cobblemon_arena.section.casual_queue"),
            QUICK_ACCENT
        );
        drawWrappedText(graphics, desc, x + 12, y + 20, 84, TEXT_DIM, 2);

        drawScaledText(
            graphics,
            t("gui.cobblemon_arena.label.format"),
            x + 14,
            y + 38,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            t("gui.cobblemon_arena.label.level"),
            x + 96,
            y + 38,
            TEXT_SECONDARY,
            SMALL_SCALE
        );

        // VGC tag bottom-right
        drawScaledText(
            graphics,
            "VGC-style",
            x + 126,
            y + 95,
            TEXT_DIM,
            SMALL_SCALE
        );
    }

    // ── Ranked queue card ──────────────────────────────────────────────────

    private void drawRankedQueueCard(DrawContext graphics) {
        int x = rankedCardX(),
            y = rankedCardY();
        String desc = resolveRankedSelection().getDescription();

        drawSection(graphics, x, y, 180, 108, SECTION_ALT);

        // Gold accent bar
        graphics.fill(x, y + 1, x + 3, y + 107, RANKED_ACCENT);
        graphics.fill(
            x,
            y + 1,
            x + 1,
            y + 107,
            mix(RANKED_ACCENT, color(255, 255, 255, 255), 0.3F)
        );
        graphics.fill(x + 3, y + 2, x + 8, y + 106, color(235, 204, 106, 14));

        drawSectionTitle(
            graphics,
            x + 12,
            y + 8,
            t("gui.cobblemon_arena.section.ranked_ladder"),
            RANKED_ACCENT
        );
        drawWrappedText(graphics, desc, x + 12, y + 20, 84, TEXT_DIM, 2);

        drawScaledText(
            graphics,
            t("gui.cobblemon_arena.label.format"),
            x + 14,
            y + 38,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        // No level label — always Lv50
        // Lock badge bottom-right
        drawScaledText(
            graphics,
            "Lv.50 VGC",
            x + 126,
            y + 95,
            TEXT_DIM,
            SMALL_SCALE
        );

        // ELO / Tier / Record chips at bottom
        drawMetricChip(
            graphics,
            x + 12,
            y + 76,
            50,
            t("gui.cobblemon_arena.label.rating"),
            String.valueOf(ArenaClientState.getRankedRating()),
            RANKED_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 67,
            y + 76,
            50,
            t("gui.cobblemon_arena.label.tier"),
            trimTierTitle(ArenaClientState.getRankTitle()),
            rankAccentColor(ArenaClientState.getRankTitle())
        );
        drawMetricChip(
            graphics,
            x + 122,
            y + 76,
            50,
            t("gui.cobblemon_arena.label.record"),
            ArenaClientState.getRankedWins() +
                "-" +
                ArenaClientState.getRankedLosses(),
            SUCCESS_ACCENT
        );
    }

    // ── Mission hint strip (y=262, h=20) ──────────────────────────────────
    //
    //  When in queue  : shows queue status (green)
    //  Claimable > 0  : "✦ N missão(ões) para resgatar!" (green)
    //  Active mission : title + inline mini progress bar
    //  Otherwise      : idle hint (blue)
    //
    private void drawMissionHintStrip(DrawContext graphics) {
        int x = guiLeft + 8,
            y = guiTop + 262,
            w = 368,
            h = 20;
        boolean inQueue = QueueStatusOverlay.getInstance().isVisible();

        // Background
        graphics.fill(x, y, x + w, y + h, SECTION_BG);
        graphics.fill(
            x + 1,
            y + 1,
            x + w - 1,
            y + h - 1,
            mix(SECTION_BG, color(255, 255, 255, 255), 0.03F)
        );
        graphics.fill(x, y, x + w, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, darken(BORDER_COLOR, 18));
        graphics.fill(x, y, x + 1, y + h, BORDER_HIGHLIGHT);
        graphics.fill(x + w - 1, y, x + w, y + h, darken(BORDER_COLOR, 18));

        if (inQueue) {
            // Green accent bar + queue message
            graphics.fill(x + 2, y + 2, x + 5, y + h - 2, SUCCESS_ACCENT);
            graphics.fill(
                x + 2,
                y + 2,
                x + 3,
                y + h - 2,
                mix(SUCCESS_ACCENT, color(255, 255, 255, 255), 0.4F)
            );
            drawScaledText(
                graphics,
                t("gui.cobblemon_arena.footer.hint.active"),
                x + 12,
                y + 7,
                SUCCESS_ACCENT,
                BODY_SCALE
            );
            return;
        }

        int claimable = ArenaQuestClientState.getClaimableCount();

        if (claimable > 0) {
            // Success accent — quests ready to claim
            graphics.fill(x + 2, y + 2, x + 5, y + h - 2, SUCCESS_ACCENT);
            graphics.fill(
                x + 2,
                y + 2,
                x + 3,
                y + h - 2,
                mix(SUCCESS_ACCENT, color(255, 255, 255, 255), 0.4F)
            );
            String msg =
                "✦  " +
                claimable +
                (claimable == 1
                    ? " missão pronta para resgatar!"
                    : " missões prontas para resgatar!");
            drawScaledText(
                graphics,
                msg,
                x + 12,
                y + 7,
                SUCCESS_ACCENT,
                BODY_SCALE
            );
        } else {
            // Check for an active (incomplete) daily mission to preview
            List<QuestEntryPayload> daily =
                ArenaQuestClientState.getDailyQuests();
            QuestEntryPayload active = daily
                .stream()
                .filter(q -> !q.completed() && !q.claimed())
                .findFirst()
                .orElse(null);

            if (active != null) {
                // Info accent bar + mission preview with mini progress bar
                graphics.fill(x + 2, y + 2, x + 5, y + h - 2, INFO_ACCENT);
                graphics.fill(
                    x + 2,
                    y + 2,
                    x + 3,
                    y + h - 2,
                    mix(INFO_ACCENT, color(255, 255, 255, 255), 0.4F)
                );

                String title = fitText("⬛ " + active.title(), 200, BODY_SCALE);
                String progress =
                    active.currentProgress() + "/" + active.targetAmount();

                drawScaledText(
                    graphics,
                    title,
                    x + 12,
                    y + 7,
                    TEXT_SECONDARY,
                    BODY_SCALE
                );

                // Mini progress bar (right-aligned)
                int barW = 70,
                    barH = 4;
                int barX = x + w - barW - 30;
                int barY = y + h / 2 - barH / 2;
                graphics.fill(
                    barX,
                    barY,
                    barX + barW,
                    barY + barH,
                    darken(SECTION_BG, 12)
                );
                int filled = (int) (barW * active.progressFraction());
                if (filled > 0) graphics.fill(
                    barX,
                    barY,
                    barX + filled,
                    barY + barH,
                    INFO_ACCENT
                );
                graphics.fill(
                    barX,
                    barY,
                    barX + barW,
                    barY + 1,
                    color(255, 255, 255, 18)
                );

                drawScaledText(
                    graphics,
                    progress,
                    x + w - 26,
                    y + 7,
                    INFO_ACCENT,
                    SMALL_SCALE
                );
            } else {
                // Idle hint
                graphics.fill(x + 2, y + 2, x + 5, y + h - 2, INFO_ACCENT);
                graphics.fill(
                    x + 2,
                    y + 2,
                    x + 3,
                    y + h - 2,
                    mix(INFO_ACCENT, color(255, 255, 255, 255), 0.4F)
                );
                drawScaledText(
                    graphics,
                    t("gui.cobblemon_arena.footer.hint.idle"),
                    x + 12,
                    y + 7,
                    TEXT_SECONDARY,
                    BODY_SCALE
                );
            }
        }
    }

    // ── Party preview ──────────────────────────────────────────────────────

    private List<Text> drawPartyPreview(
        DrawContext graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        return partyPreview.render(
            graphics,
            textRenderer,
            guiLeft + 8,
            guiTop + 100,
            368,
            42,
            mouseX,
            mouseY,
            delta
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Layout helpers
    // ══════════════════════════════════════════════════════════════════════

    private int leftColumnX() {
        return guiLeft + 8;
    }

    private int rightColumnX() {
        return leftColumnX() + 180 + 8;
    }

    private int rankedCardX() {
        return leftColumnX();
    }

    private int casualCardX() {
        return rightColumnX();
    }

    private int serverPanelX() {
        return rightColumnX();
    }

    private int casualCardY() {
        return guiTop + 148;
    }

    private int rankedCardY() {
        return guiTop + 148;
    }

    @Override
    protected void updateGuiPosition() {
        computeScale(GUI_WIDTH, MAIN_HEIGHT);
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - MAIN_HEIGHT) / 2;
    }

    private static String trimTierTitle(String tier) {
        if ("Grão-Mestre".equals(tier)) return "GM";
        if ("Sem Rank".equals(tier)) return "Nenhum";
        return tier;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
