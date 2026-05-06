package cobblemon.arena.client;

import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.network.ArenaMatchHistoryEntryPayload;
import cobblemon.arena.network.ArenaPokemonUsageEntryPayload;
import cobblemon.arena.network.CancelQueuePacket;
import cobblemon.arena.network.ClaimQuestRewardPacket;
import cobblemon.arena.network.JoinQueuePacket;
import cobblemon.arena.network.QuestEntryPayload;
import cobblemon.arena.network.RankedLadderSnapshot;
import cobblemon.arena.network.SpectateArenaBattlePacket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Unified Cobblemon Arena screen — dark purple SPA-style interface.
 *
 * ┌──────────┬──────────────────────────────────────────┐
 * │ SIDEBAR  │  CONTENT  (CW = 390 px)                  │
 * │  90 px   │                                          │
 * │  Nav     │  Changes per page                        │
 * └──────────┴──────────────────────────────────────────┘
 *
 * All six content pages live inside this one Screen class.
 * Pages transition with a 180 ms ease-out fade.
 */
public final class ArenaShellScreen extends Screen {

    // ── Pages ─────────────────────────────────────────────────────────────────
    public enum Page {
        PLAY,
        PROFILE,
        LEADERBOARD,
        HISTORY,
        MISSIONS,
        SPECTATE,
    }

    // ── Dimensions ────────────────────────────────────────────────────────────
    // ── Dimensions ────────────────────────────────────────────────────────────
    public int W = 480;
    public int H = 292;
    public int SB = 100; // Sidebar width
    public int CW = W - SB;

    // Play-page column geometry (offsets from content-left = gL+SB)
    private int C1X = 2; // Chat column offset
    private int C1W = 200; // Chat column width
    private int C2X = C1W + 10;
    private int C2W = 400; // PlayBox width
    private int C3X = C2X + C2W + 10;
    private int C3W = 224; // Missions width

    // Play-page row offsets from cy (= gT)
    private int PY_PLAYER = 10; // Top header y offset
    private int PY_COLS = 60; // Below header offset for the 3 columns
    private int PY_RANKED = PY_COLS;
    private int PY_CASUAL = PY_COLS;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int C_BASE = p(16, 12, 22);     // #100C16
    private static final int C_PANEL = p(26, 22, 36);    // #1A1624
    private static final int C_CARD = p(26, 22, 36);
    private static final int C_CARD2 = p(35, 30, 48);
    private static final int C_SB = p(16, 12, 22);
    private static final int C_NAVA = pa(200, 38, 20, 56);
    private static final int C_BDR = p(45, 42, 58);      // #2D2A3A
    private static final int C_BDR2 = p(65, 60, 80);
    private static final int C_INSET = p(20, 15, 28);

    private static final int A_PUR = p(155, 81, 224);    // #9B51E0
    private static final int A_PUR2 = p(120, 60, 180);
    private static final int A_PUR3 = p(80, 40, 120);
    private static final int A_GOLD = p(245, 158, 11);   // #F59E0B
    private static final int A_GREEN = p(16, 185, 129);  // #10B981
    private static final int A_RED = p(239, 68, 68);     // #EF4444
    private static final int A_ORG = p(245, 158, 11);

    private static final int T_W = p(243, 244, 246);     // #F3F4F6
    private static final int T_L = p(209, 213, 219);     // #D1D5DB
    private static final int T_DIM = p(156, 163, 175);   // #9CA3AF
    private static final int T_PUR = p(155, 81, 224);
    private static final int T_MUT = p(107, 114, 128);

    private static final int TIER_BRZ = pa(215, 162, 80, 12);
    private static final int TIER_SLV = pa(215, 118, 126, 148);
    private static final int TIER_GLD = pa(215, 182, 140, 14);
    private static final int TIER_PLT = pa(215, 65, 175, 192);
    private static final int TIER_DIA = pa(215, 55, 158, 210);
    private static final int TIER_MST = pa(215, 165, 28, 65);
    private static final int TIER_GM = pa(215, 198, 50, 50);

    // ── State ─────────────────────────────────────────────────────────────────
    private Page page = Page.PLAY;
    private int gL, gT;
    private float gS = 1f;

    // Fade-in transition
    private long navMs = 0;
    private static final long FADE_MS = 180;

    // Party renderer (uses Cobblemon rendering pipeline)
    private final ArenaPartyPreviewRenderer partyRenderer =
        new ArenaPartyPreviewRenderer();

    // Tooltip storage to ensure it renders on top of everything (z-index fix)
    private java.util.List<net.minecraft.text.Text> partyHoverTooltip = null;
    private int partyHoverX = 0;
    private int partyHoverY = 0;

    // Play widgets
    private static String rFmt = "Duplas";
    private static String cFmt = "Solo";
    private static String cLvl = "50";
    private InvisibleButton queueButton;
    // Tab state
    private static boolean playTabRanked = true;
    // Legendary restriction state
    private boolean blockLegendaries = false;
    // Queue display state — drives drawRankedCard / drawCasualCard
    private boolean queueInRanked = false;
    private boolean queueInCasual = false;
    private InvisibleButton cancelQueueButton;

    // Per-card validation errors (separate so each card shows its own error).
    // Auto-cleared after VALIDATION_ERROR_TIMEOUT_MS.
    private String rankedValidationError = null;
    private long rankedValidationErrorMs = 0L;
    private String casualValidationError = null;
    private long casualValidationErrorMs = 0L;
    private InvisibleButton rankedToggleBtn, casualToggleBtn;
    private InvisibleButton fmtPrevBtn, fmtNextBtn;
    private InvisibleButton lvl50Btn, lvl100Btn;

    private static final long VALIDATION_ERROR_TIMEOUT_MS = 4_000L;

    // Pending flags — set in button callbacks (safe: only boolean writes, no list mutation).
    private volatile boolean pendingQueueJoinRanked = false;
    private volatile boolean pendingQueueJoinCasual = false;
    private volatile boolean pendingCancelQueue = false;
    // Set when the player clicks "Cancelar Fila"; prevents updatePlayButtons()
    // from re-enabling the counter while the cancel round-trip is in flight.
    private volatile boolean cancelQueueSent = false;
    private long pendingQueueStartMs = 0L;
    // Safety timeout: server always sends explicit rejection/confirmation,
    // so this only fires if the packet was genuinely lost (e.g. disconnect).
    private static final long PENDING_QUEUE_TIMEOUT_MS = 30_000L;

    // ELO tooltip — set while mouse hovers the tier pill in any page
    private String eloTipFid = null;
    private int eloTipX = 0, eloTipY = 0;

    // Mission tooltip
    private List<Text> missionHoverTooltip = null;
    private int missionHoverX = 0;
    private int missionHoverY = 0;

    private int CHAT_H = 186;
    private static final int CHAT_INPUT_H = 24;
    private int CHAT_MSG_AREA = 152;
    private net.minecraft.client.gui.widget.TextFieldWidget chatInput;
    private int chatScroll = 0; // 0 = newest at bottom
    private int chatWrappedLineCount = 0; // total lines after word-wrap (used by mouseScrolled)

    private static boolean globalChatOpen = false;
    private boolean chatOpen = globalChatOpen;
    private float chatOpenProgress = globalChatOpen ? 1.0f : 0.0f; // 1 = fully open, 0 = fully closed

    // Progress bar animations
    private java.util.Map<String, Float> eloProgressAnim = new java.util.HashMap<>();

    // Leaderboard
    private String lbFmt = "singles";
    private int lbScr = 0;

    // History
    private boolean hMatches = true;
    private int hScr = 0;

    // Missions
    private int qScr = 0;
    
    // Profile
    private int pScr = 0;
    private final List<Hit> hits = new ArrayList<>();
    
    private int tickCounter = 0;

    private static final DateTimeFormatter DFMT = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd"
    );

    // ── Constructors ──────────────────────────────────────────────────────────
    public ArenaShellScreen() {
        this(Page.PLAY);
    }

    public ArenaShellScreen(Page p) {
        super(Text.literal("Cobblemon Arena"));
        page = p;
        navMs = System.currentTimeMillis();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (page == Page.SPECTATE) {
            tickCounter++;
            if (tickCounter % 40 == 0) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new cobblemon.arena.network.RequestActiveBattlesPacket());
            }
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }

    /** Suppress Minecraft's default gaussian blur. */
    @Override
    public void renderBackground(
        DrawContext ctx,
        int mx,
        int my,
        float delta
    ) {}

    @Override
    protected void init() {
        layout();
        // If the queue overlay is still active when this screen re-initialises
        // (player closed /arena and reopened it while still in queue), restore
        // the counter state immediately so the cancel button is shown on the
        // very first frame — no 1-frame flash of normal join buttons.
        if (page == Page.PLAY && !cancelQueueSent) {
            QueueStatusOverlay ov = QueueStatusOverlay.getInstance();
            if (ov.isVisible()) {
                queueInRanked = ov.isRankedQueue();
                queueInCasual = !ov.isRankedQueue();
                playTabRanked = queueInRanked;
            }
        }
        buildWidgets();
    }

    private void layout() {
        int minW = 880;
        int minH = 460;
        float maxScale = 1.2f;
        float scaleW = width >= minW * maxScale ? maxScale : (float) width / minW;
        float scaleH = height >= minH * maxScale ? maxScale : (float) height / minH;
        gS = Math.min(scaleW, scaleH);
        
        int virtualW = (int) (width / gS);
        H = (int) (height / gS);
        
        // Limita a largura máxima do conteúdo para não distorcer em ultrawide
        int maxColWidth = 1100;
        CW = Math.min(virtualW - SB, maxColWidth);
        W = SB + CW; // Largura total utilizada (Sidebar + Content)
        
        // Centraliza o bloco inteiro na tela
        gL = (virtualW - W) / 2;
        gT = 0;

        int availableWidthForCols = CW;
        int colOffsetX = 0; // Sem gap entre sidebar e conteúdo

        C1W = Math.max(200, Math.min(280, availableWidthForCols / 4));
        C3W = Math.max(200, Math.min(280, availableWidthForCols / 4));
        
        C1X = colOffsetX;
        C3X = availableWidthForCols - C3W;
        
        C2W = Math.max(380, Math.min(480, availableWidthForCols / 3));
        C2X = C1X + C1W + ((availableWidthForCols - C1W - C3W) - C2W) / 2;
        
        CHAT_H = H - PY_COLS - 4;
        CHAT_MSG_AREA = CHAT_H - CHAT_INPUT_H - 20;
    }

    private void nav(Page p) {
        page = p;
        lbScr = 0;
        hScr = 0;
        qScr = 0;
        pScr = 0;
        navMs = System.currentTimeMillis();
        layout();
        buildWidgets();
    }

    // ── Widget construction ───────────────────────────────────────────────────

    private void buildWidgets() {
        clearChildren();
        switch (page) {
            case PLAY -> buildPlayWidgets();
            case HISTORY -> buildHistoryWidgets();
            case LEADERBOARD -> buildLbWidgets();
            default -> {
                /* no interactive widgets needed */
            }
        }
    }

    private void buildPlayWidgets() {
        int c2x = gL + SB + C2X;

        // ── Chat input (always present on Play page, bottom of Col1) ──────────
        int chatPanelX = gL + SB + C1X;
        int chatPanelY = gT + PY_COLS;
        int chatInputY = chatPanelY + CHAT_H - CHAT_INPUT_H - 4;
        chatInput = new net.minecraft.client.gui.widget.TextFieldWidget(
            textRenderer,
            wx(chatPanelX + 8),
            wy(chatInputY + 6),
            ws(C1W - 16),
            ws(CHAT_INPUT_H - 8),
            net.minecraft.text.Text.literal("/g ...")
        );
        chatInput.setMaxLength(200);
        chatInput.setDrawsBackground(false);
        chatInput.setVisible(false);
        addDrawableChild(chatInput);

        int playBoxX = gL + SB + C2X;
        int playBoxY = gT;

        int tabW = 200;
        int tabH = 34;
        int tabX = playBoxX + (C2W - tabW) / 2;
        int tabY = playBoxY + 60;

        rankedToggleBtn = addW(new InvisibleButton(wx(tabX), wy(tabY), ws(tabW/2), ws(tabH), b -> { playTabRanked = true; buildWidgets(); }));
        casualToggleBtn = addW(new InvisibleButton(wx(tabX + tabW/2), wy(tabY), ws(tabW/2), ws(tabH), b -> { playTabRanked = false; buildWidgets(); }));

        // Format Arrows
        int boxW = 160;
        int boxH = 50;
        int cy_current = tabY + tabH + 20 + 30 + 20;
        int arrSize = 25;
        int boxX = playBoxX + (C2W - boxW) / 2;
        int arrY = cy_current + (boxH - arrSize) / 2;
        
        fmtPrevBtn = addW(new InvisibleButton(wx(boxX - arrSize - 10), wy(arrY), ws(arrSize), ws(arrSize), b -> { cycleFormat(-1); buildWidgets(); }));
        fmtNextBtn = addW(new InvisibleButton(wx(boxX + boxW + 10), wy(arrY), ws(arrSize), ws(arrSize), b -> { cycleFormat(1); buildWidgets(); }));

        cy_current += boxH + 8;
        cy_current += 30;

        // Level Toggles
        int lvlW = 100;
        int lvlH = 45;
        int lvlX1 = playBoxX + (C2W - lvlW * 2 - 10) / 2;
        int lvlX2 = lvlX1 + lvlW + 10;
        int lvlY = cy_current;
        
        lvl50Btn = addW(new InvisibleButton(wx(lvlX1), wy(lvlY), ws(lvlW), ws(lvlH), b -> { cLvl = "50"; buildWidgets(); }));
        if (!playTabRanked) {
            lvl100Btn = addW(new InvisibleButton(wx(lvlX2), wy(lvlY), ws(lvlW), ws(lvlH), b -> { cLvl = "100"; buildWidgets(); }));
        }

        cy_current += lvlH + 20;
        int startW = 220;
        int startH = 45;
        int startX = playBoxX + (C2W - startW) / 2;
        int startY = cy_current;

        // Single join button: near bottom of playbox
        queueButton = addW(
            new InvisibleButton(
                wx(startX),
                wy(startY),
                ws(startW),
                ws(startH),
                button -> startQueue()
            )
        );
        cancelQueueButton = addW(
            new InvisibleButton(
                wx(startX),
                wy(startY),
                ws(startW),
                ws(startH),
                button -> {
                    pendingCancelQueue = true;
                    ClientPlayNetworking.send(new CancelQueuePacket());
                }
            )
        );

        updatePlayWidgetsVisibility();
    }
    
    private void cycleFormat(int dir) {
        List<String> fmts = playTabRanked ? List.of("Solo", "Duplas", "Triplas") : List.of("Solo", "Duplas", "Triplas", "Monotype");
        String current = playTabRanked ? rFmt : cFmt;
        int idx = fmts.indexOf(current);
        if (idx == -1) idx = 0;
        idx = (idx + dir + fmts.size()) % fmts.size();
        if (playTabRanked) rFmt = fmts.get(idx);
        else cFmt = fmts.get(idx);
    }

    private void startQueue() {
        boolean isMonotype = !playTabRanked && "Monotype".equalsIgnoreCase(cFmt);
        String err = validatePartyForQueue(isMonotype);
        if (err != null) { 
            showQueueValidationError(err); 
            return; 
        }

        if (playTabRanked) {
            rankedValidationError = null;
            cancelQueueSent = false;
            ArenaClientState.clearQueueRejection();
            pendingQueueJoinRanked = true;
            ClientPlayNetworking.send(new JoinQueuePacket(ArenaLadder.rankedPresetByChoice(rFmt, "50", false).getId(), false));
        } else {
            casualValidationError = null;
            cancelQueueSent = false;
            ArenaClientState.clearQueueRejection();
            pendingQueueJoinCasual = true;
            ClientPlayNetworking.send(new JoinQueuePacket(ArenaLadder.vgcPresetByChoice(cFmt, cLvl, false).getId(), false));
        }
    }
    
    private void updatePlayWidgetsVisibility() {
        if (page != Page.PLAY) return;
        
        boolean inQueue = queueInRanked || queueInCasual;
        if (queueButton != null) {
            queueButton.visible = !inQueue;
        }
        
        if (cancelQueueButton != null) {
            cancelQueueButton.visible = inQueue;
        }

        if (rankedToggleBtn != null) rankedToggleBtn.active = !inQueue;
        if (casualToggleBtn != null) casualToggleBtn.active = !inQueue;
        if (fmtPrevBtn != null) fmtPrevBtn.active = !inQueue;
        if (fmtNextBtn != null) fmtNextBtn.active = !inQueue;
        if (lvl50Btn != null) lvl50Btn.active = !inQueue;
        if (lvl100Btn != null) lvl100Btn.active = !inQueue;
    }

    /**
     * Applies pending optimistic queue state changes set by button callbacks.
     * Called at the VERY START of each render frame, before drawContent(), so
     * drawRankedCard / drawCasualCard always see the up-to-date state.
     *
     * Button callbacks ONLY set boolean flags (no list mutation), making them
     * safe to call during widget event handling without ConcurrentModificationException.
     */
    private void applyPendingQueueState() {
        if (page != Page.PLAY) return;

        // ── Cancel (highest priority) ─────────────────────────────────────
        if (pendingCancelQueue) {
            pendingCancelQueue = false;
            pendingQueueJoinRanked = false;
            pendingQueueJoinCasual = false;
            pendingQueueStartMs = 0L;
            cancelQueueSent = true;
            // Reset the elapsed timer immediately so the next join starts at 0:00
            QueueStatusOverlay.getInstance().setVisible(false);
            if (queueInRanked || queueInCasual) {
                queueInRanked = false;
                queueInCasual = false;
                buildWidgets();
            }
            return;
        }

        // ── Optimistic join — apply state if not already shown ────────────
        // IMPORTANT: do NOT clear the pending flag here.
        // It must stay true until the server confirms (or timeout) so that
        // updatePlayButtons() does not reset the counter prematurely.
        if (pendingQueueJoinRanked && !queueInRanked) {
            pendingQueueStartMs = System.currentTimeMillis();
            // Discard any stale rejection from a previous join/cancel cycle.
            ArenaClientState.clearQueueRejection();
            // Start the counter timer NOW (before server confirmation).
            QueueStatusOverlay.getInstance().armElapsedTimer();
            queueInRanked = true;
            queueInCasual = false;
            buildWidgets();
            return;
        }

        if (pendingQueueJoinCasual && !queueInCasual) {
            pendingQueueStartMs = System.currentTimeMillis();
            ArenaClientState.clearQueueRejection();
            QueueStatusOverlay.getInstance().armElapsedTimer();
            queueInCasual = true;
            queueInRanked = false;
            buildWidgets();
        }
    }

    /**
     * Syncs queue display state with the server-side {@link QueueStatusOverlay}.
     * Called every frame AFTER drawContent() so a single-frame visual glitch
     * is avoided.  Pending-join flags guard against premature state reset while
     * waiting for the server reply.
     */
    private void updatePlayButtons() {
        if (page != Page.PLAY) return;
        QueueStatusOverlay overlay = QueueStatusOverlay.getInstance();
        boolean inQueue = overlay.isVisible();

        // ── Priority −1: match found ─────────────────────────────────────────
        // When MatchFoundPacket arrives, clear the in-screen counter immediately.
        // The QueueStatusOverlay continues showing its own "Partida encontrada!"
        // HUD notification; we only remove the counter card from ArenaShellScreen.
        if (ArenaClientState.consumeMatchFound()) {
            pendingQueueJoinRanked = false;
            pendingQueueJoinCasual = false;
            pendingQueueStartMs = 0L;
            cancelQueueSent = false;
            queueInRanked = false;
            queueInCasual = false;
            buildWidgets();
            return;
        }

        // ── Priority 0: consume any queued rejection signal ──────────────────────
        // This check is placed OUTSIDE the pendingJoin block so stale signals
        // from a previous cancel/reject cycle are always consumed, preventing
        // them from firing on the very next join attempt.
        if (!inQueue && ArenaClientState.consumeQueueRejection()) {
            pendingQueueJoinRanked = false;
            pendingQueueJoinCasual = false;
            pendingQueueStartMs = 0L;
            cancelQueueSent = false;
            // Reset elapsed timer so next join starts at 0:00
            QueueStatusOverlay.getInstance().setVisible(false);
            if (queueInRanked || queueInCasual) {
                queueInRanked = false;
                queueInCasual = false;
                buildWidgets();
            }
            return;
        }

        if (inQueue) {
            // ── Server confirmed the queue ──────────────────────────────
            pendingQueueJoinRanked = false;
            pendingQueueJoinCasual = false;
            pendingQueueStartMs = 0L;
            // cancelQueueSent blocks re-enabling the counter while the cancel
            // round-trip is in flight (player clicked Cancel but server hasn’t
            // responded yet).
            if (!cancelQueueSent) {
                boolean nowRanked = overlay.isRankedQueue();
                boolean nowCasual = !nowRanked;
                if (nowRanked != queueInRanked || nowCasual != queueInCasual) {
                    queueInRanked = nowRanked;
                    queueInCasual = nowCasual;
                    buildWidgets();
                }
            }
        } else {
            // ── Not in queue (no rejection signal) ────────────────────────
            boolean pendingJoin =
                pendingQueueJoinRanked || pendingQueueJoinCasual;
            if (pendingJoin) {
                // Still waiting for the server to reply.  Use a 30-second
                // safety timeout in case the server silently dropped the packet.
                boolean timedOut =
                    pendingQueueStartMs > 0L &&
                    (System.currentTimeMillis() - pendingQueueStartMs) >
                    PENDING_QUEUE_TIMEOUT_MS;
                if (timedOut) {
                    pendingQueueJoinRanked = false;
                    pendingQueueJoinCasual = false;
                    pendingQueueStartMs = 0L;
                    queueInRanked = false;
                    queueInCasual = false;
                    buildWidgets();
                }
                // else: keep counter open — server will confirm soon
            } else {
                // No pending join, no rejection: cancel completed normally.
                cancelQueueSent = false;
                if (queueInRanked || queueInCasual) {
                    // Shouldn’t normally reach here, but reset to be safe.
                    queueInRanked = false;
                    queueInCasual = false;
                    buildWidgets();
                }
            }
        }
    }

    private void buildHistoryWidgets() {
    }

    private void buildLbWidgets() {
    }

    @SuppressWarnings("unchecked")
    private <
        T extends net.minecraft.client.gui.Element
            & net.minecraft.client.gui.Drawable
            & net.minecraft.client.gui.Selectable
    > T addW(T w) {
        return addDrawableChild(w);
    }

    // ── Main render ───────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        partyHoverTooltip = null;
        historyHoverTooltip = null;
        missionHoverTooltip = null;
        hits.clear();
        layout();
        int gx = guiX(mx),
            gy = guiY(my);

        // Full-window solid background
        ctx.fill(0, 0, width, height, C_BASE);

        // Consume any pending queue state flags set by button callbacks.
        // Must run before drawContent() so the cards render the correct state
        // in this very frame (no 1-frame delay / flicker).
        applyPendingQueueState();

        // ── Scaled content pass ────────────────────────────────────────────
        if (gS < 1f) pushScale(ctx);
        drawPanel(ctx);
        drawContent(ctx, gx, gy, delta);
        drawSidebar(ctx, gx, gy);

        // Page-transition fade (inside scale, covers content, underneath widgets)
        long elapsed = System.currentTimeMillis() - navMs;
        if (elapsed < FADE_MS) {
            float frac = 1f - (elapsed / (float) FADE_MS);
            frac = frac * frac; // ease-out
            int a = (int) (frac * 235);
            if (a > 0) ctx.fill(gL + SB, gT, gL + W, gT + H, pa(a, 15, 13, 26));
        }
        if (gS < 1f) ctx.getMatrices().pop();
        // ── end scaled pass ────────────────────────────────────────────

        // Check for queue state changes before rendering widgets
        updatePlayButtons();

        // Widgets are positioned at VISUAL (screen) coordinates via wx/wy/ws,
        // so we pass the raw mx/my — no coordinate conversion needed.
        super.render(ctx, mx, my, delta);

        // Keep history modal above widgets so tab buttons from the base page
        // never bleed into the battle-report details window.
        if (page == Page.HISTORY && selectedMatchDetails != null) {
            int gmx = guiX(mx);
            int gmy = guiY(my);
            if (gS < 1f) pushScale(ctx);
            int virtualW = (int) (this.width / gS);
            int virtualH = (int) (this.height / gS);
            ctx.fill(0, 0, virtualW, virtualH, pa(180, 0, 0, 0));
            drawHistoryDetailsModal(
                ctx,
                gmx,
                gmy,
                gL + SB,
                gT,
                selectedMatchDetails
            );
            if (historyHoverTooltip != null) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 1000);
                ctx.drawTooltip(textRenderer, historyHoverTooltip, gmx, gmy);
                ctx.getMatrices().pop();
            }
            if (gS < 1f) ctx.getMatrices().pop();
        }

        // Draw the party tooltip at the absolute top layer if present
        if (partyHoverTooltip != null && !partyHoverTooltip.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(gS, gS, 1f);
            ctx.getMatrices().translate(0, 0, 1000);
            drawPartyTooltip(ctx, partyHoverTooltip, partyHoverX - 40, partyHoverY);
            ctx.getMatrices().pop();
        }

        if (missionHoverTooltip != null && !missionHoverTooltip.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(gS, gS, 1f);
            ctx.getMatrices().translate(0, 0, 1000);
            ctx.drawTooltip(textRenderer, missionHoverTooltip, missionHoverX, missionHoverY);
            ctx.getMatrices().pop();
        }

        // Overlay notification is intentionally hidden while /arena is open.

        if (eloTipFid != null) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(gS, gS, 1f);
            int virtualW = (int) (this.width / gS);
            int virtualH = (int) (this.height / gS);
            int tipW = 160;
            int tipH = 120;
            int clampedX = net.minecraft.util.math.MathHelper.clamp(eloTipX, 0, virtualW - tipW);
            int clampedY = net.minecraft.util.math.MathHelper.clamp(eloTipY, 0, virtualH - tipH);
            drawTierTableTooltip(ctx, clampedX, clampedY, eloTipFid);
            ctx.getMatrices().pop();
            eloTipFid = null; // reset for next frame
        }
    }
    
    private void drawPartyTooltip(DrawContext c, List<net.minecraft.text.Text> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) return;
        int tw = 0;
        for (net.minecraft.text.Text t : lines) {
            tw = Math.max(tw, textRenderer.getWidth(t));
        }
        tw += 16;
        int th = lines.size() * 12 + 8;
        if (x + tw > W) x = W - tw - 10;
        if (y + th > H) y = H - th - 10;
        card(c, x, y, tw, th);
        int cy = y + 6;
        for (net.minecraft.text.Text t : lines) {
            c.drawTextWithShadow(textRenderer, t, x + 8, cy, T_W);
            cy += 12;
        }
    }

    // ── Panel base ────────────────────────────────────────────────────────────

    private void drawPanel(DrawContext c) {
        // Escurece todo o resto da tela (bordas)
        int virtualW = (int) (this.width / gS);
        int virtualH = (int) (this.height / gS);
        c.fill(0, 0, virtualW, virtualH, pa(220, 15, 10, 18));
        
        // Fundo principal da UI
        c.fill(gL, gT, gL + W, gT + H, C_PANEL);
        // Outer border
        c.fill(gL, gT, gL + W, gT + 1, C_BDR2);
        c.fill(gL, gT + H - 1, gL + W, gT + H, C_BDR);
        c.fill(gL, gT, gL + 1, gT + H, C_BDR2);
        c.fill(gL + W - 1, gT, gL + W, gT + H, C_BDR);
        // Top sheen
        c.fill(gL + 1, gT + 1, gL + W - 1, gT + 2, pa(6, 255, 255, 255));
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private static final String[] SB_LABELS = {
        "Jogar",
        "Perfil",
        "Ranking",
        "Historico",
        "Missoes",
        "Assistir",
    };
    private static final Page[] SB_PAGES = {
        Page.PLAY,
        Page.PROFILE,
        Page.LEADERBOARD,
        Page.HISTORY,
        Page.MISSIONS,
        Page.SPECTATE,
    };

    private void drawSidebar(DrawContext c, int mx, int my) {
        c.fill(gL, gT, gL + SB, gT + H, C_SB);
        c.fill(gL + SB - 1, gT, gL + SB, gT + H, C_BDR);

        // Logo
        int logoW = Math.round(textRenderer.getWidth("COBBLESAS") * 0.72f * 1.25f);
        t(c, "COBBLESAS", gL + (SB - logoW) / 2, gT + 12, A_PUR, 0.72f);
        
        c.fill(gL + 15, gT + 26, gL + SB - 15, gT + 27, C_BDR);

        // Navigation items
        for (int i = 0; i < SB_LABELS.length; i++) navItem(
            c,
            SB_LABELS[i],
            SB_PAGES[i],
            mx,
            my,
            gT + 40 + i * 50
        );

        // Bottom config
        int fy = gT + H - 40;
        t(c, "Config", gL + (SB - Math.round(textRenderer.getWidth("Config")*0.70f*1.25f))/2, fy + 10, T_MUT, 0.70f);
        t(c, "v2.0", gL + (SB - Math.round(textRenderer.getWidth("v2.0")*0.60f*1.25f))/2, fy + 22, T_MUT, 0.60f);
    }

    private void navItem(
        DrawContext c,
        String label,
        Page p,
        int mx,
        int my,
        int y
    ) {
        int x = gL + 10,
            w = SB - 20,
            h = 40;
        boolean act = page == p;
        boolean hov = !act && mx >= x && mx < x + w && my >= y && my < y + h;

        if (act) {
            // Draw active purple rounded-looking box
            c.fill(x + 2, y, x + w - 2, y + h, C_NAVA);
            c.fill(x, y + 2, x + w, y + h - 2, C_NAVA);
            
            // Border (simulating rounded rect)
            c.fill(x + 2, y, x + w - 2, y + 1, A_PUR2);
            c.fill(x + 2, y + h - 1, x + w - 2, y + h, A_PUR2);
            c.fill(x, y + 2, x + 1, y + h - 2, A_PUR2);
            c.fill(x + w - 1, y + 2, x + w, y + h - 2, A_PUR2);
        } else if (hov) {
            c.fill(x + 2, y, x + w - 2, y + h, pa(70, 80, 58, 120));
            c.fill(x, y + 2, x + w, y + h - 2, pa(70, 80, 58, 120));
        }

        int tw = Math.round(textRenderer.getWidth(label) * 0.82f * 1.25f);
        t(c, label, x + (w - tw) / 2, y + (h/2) - 4, act ? T_PUR : hov ? T_L : T_DIM, 0.82f);
        
        hits.add(new Hit("page_" + p.name(), x, y, w, h));
    }
    // ── Content dispatcher ────────────────────────────────────────────────────

    private void drawContent(DrawContext c, int mx, int my, float delta) {
        eloTipFid = null; // reset each frame; set again if mouse over pill
        int cx = gL + SB,
            cy = gT;
        switch (page) {
            case PLAY -> renderPlay(c, mx, my, delta, cx, cy);
            case PROFILE -> renderProfile(c, mx, my, cx, cy);
            case LEADERBOARD -> renderLeaderboard(c, mx, my, cx, cy);
            case HISTORY -> renderHistory(c, mx, my, cx, cy);
            case MISSIONS -> renderMissions(c, mx, my, cx, cy);
            case SPECTATE -> renderSpectate(c, mx, my, cx, cy);
        }
        // Tooltip is drawn in render()
    }

    // =========================================================================
    // PAGE: PLAY
    // Row 1 (y=cy+PY_PLAYER, h=48): Player stats strip (full width)
    // Row 2 (y=cy+PY_PARTY,  h=43): Party preview strip (full width, Cobblemon)
    // Row 3 (y=cy+PY_COLS,  h=~185): Three columns
    //   Col1 (w=C1W=118): Arena status
    //   Col2 (w=C2W=138): Ranked + Casual cards
    //   Col3 (w=C3W=124): Missions preview + Quick links
    // =========================================================================

    private void renderPlay(
        DrawContext c,
        int mx,
        int my,
        float delta,
        int cx,
        int cy
    ) {
        // Chat toggle is instant based on saved state
        chatOpenProgress = chatOpen ? 1.0f : 0.0f;

        // Recalculate horizontal offsets relative to cx
        int chatOffset = (int) (-C1W * (1.0f - chatOpenProgress));
        int c1 = cx + C1X + chatOffset;
        int c2 = cx + C2X;
        int c3 = cx + C3X;

        if (chatInput != null) {
            chatInput.setX(wx(c1 + 8));
            chatInput.setVisible(chatOpenProgress > 0.1f);
        }

        // ── 3 Columns ───────────────────────────────────────────
        int colTop = cy;
        int boxHeight = H - 4;

        // Col1: Chat (fills the entire column)
        drawChatPanel(c, c1, colTop, C1W, boxHeight, mx, my);

        // Col2: PlayBox Central
        drawPlayBox(c, c2, colTop, C2W, boxHeight, mx, my);

        // Col3: Missions preview
        drawMissPreview(c, c3, colTop, C3W, boxHeight, mx, my);
    }

    // ── Header Compacto (Top Bar) ──────────────────────────────────────────────
    private void drawTopHeader(
        DrawContext c,
        int mx,
        int my,
        float delta,
        int x,
        int y,
        int w
    ) {
        int h = 55;
        // Background
        c.fill(x, y, x + w, y + h, pa(204, 24, 20, 40)); // bg-card/80
        c.fill(x, y + h - 1, x + w, y + h, C_BDR); // bottom border

        int totalW = 30 + 10 + 100 + 20 + 1 + 20 + 225 + 20 + 1 + 20 + 320;
        int px = x + (w - totalW) / 2;
        int py = y + (h - 30) / 2;

        // Avatar
        drawPlayerHead(c, px, py, 30);
        px += 40;

        // Name & Rank
        String name = ArenaClientState.getPlayerName();
        t(c, name, px, y + (h/2) - 8, T_W, 1.0f);
        
        int elo = 0;
        int winCount = 0;
        int lossCount = 0;
        int streak = 0;
        
        String ladderId = cobblemon.arena.ladder.ArenaLadder.rankedPresetByChoice(rFmt, "50", false).getId();
        cobblemon.arena.network.RankedLadderSnapshot snapshot = ArenaClientState.getRankedSnapshotById(ladderId);
        
        if (playTabRanked) {
            for (cobblemon.arena.network.RankedLadderSnapshot s : ArenaClientState.getRankedSnapshots()) {
                winCount += s.rankedWins();
                lossCount += s.rankedLosses();
                streak += s.rankedStreak();
            }
        } else {
            for (cobblemon.arena.network.CasualLadderSnapshot s : ArenaClientState.getCasualSnapshots()) {
                winCount += s.casualWins();
                lossCount += s.casualLosses();
                streak += s.casualStreak();
            }
        }
        
        int tot = winCount + lossCount;
        int wr = tot == 0 ? 0 : Math.round((winCount * 100f) / tot);
        
        String rankTitle = ArenaClientState.getRankTitle();
        int playerRank = snapshot != null ? snapshot.playerRank() : 0;
        String rankStr = rankTitle.toUpperCase() + (playerRank > 0 ? " #" + playerRank : "");
        t(c, rankStr, px, y + (h/2) + 4, T_DIM, 0.6f);
        
        px += 100; // spacer

        // Divider
        int divY1 = y + 15;
        int divY2 = y + h - 15;
        c.fill(px, divY1, px + 1, divY2, C_BDR);
        px += 20;

        // Stats (Win Rate 100% verde, Streak +1 roxo, Season)
        int statY = y + (h/2) - 3;
        t(c, "WIN RATE", px, statY, T_DIM, 0.6f);
        px += 40;
        t(c, wr + "%", px, statY, A_GREEN, 0.8f);
        px += 30;
        t(c, "STREAK", px, statY, T_DIM, 0.6f);
        px += 30;
        t(c, (streak >= 0 ? "+" : "") + streak, px, statY, A_PUR, 0.8f);
        px += 35;
        
        String seasonName = ArenaClientState.getCurrentSeasonName();
        if (seasonName == null || seasonName.isEmpty()) seasonName = "Season 1";
        
        t(c, "SEASON", px, statY, T_DIM, 0.6f);
        px += 30;
        t(c, seasonName, px, statY, A_GOLD, 0.8f);
        px += 60;

        // Divider
        c.fill(px, divY1, px + 1, divY2, C_BDR);
        px += 20;

        // Team
        int prWidth = 320;
        java.util.List<net.minecraft.text.Text> hover = partyRenderer.render(c, textRenderer, px, y, prWidth, h, mx, my, delta);
        if (hover != null) {
            partyHoverTooltip = hover;
            int startX = px + (prWidth - 308) / 2;
            int hoveredIndex = (mx - startX) / 52;
            if (hoveredIndex >= 0 && hoveredIndex < 6) {
                partyHoverX = startX + hoveredIndex * 52 + 24;
                partyHoverY = y + h + 2;
            } else {
                partyHoverX = mx;
                partyHoverY = my + 20;
            }
        }
    }

    // ── Col1: In-screen chat panel (h=CHAT_H=105) ──────────────────────────
    // Renders the last N chat messages and a text-input field.
    // Sending: when the player presses Enter the text is submitted via /g.
    private void drawChatPanel(
        DrawContext c,
        int x,
        int y,
        int w,
        int boxH,
        int mx,
        int my
    ) {
        CHAT_H = boxH;
        CHAT_MSG_AREA = boxH - CHAT_INPUT_H - 20;

        // Background
        c.fill(x, y, x + w, y + CHAT_H, C_BASE);
        // Vertical right border
        c.fill(x + w - 1, y, x + w, y + CHAT_H, C_BDR);

        if (!chatOpen) {
            int toggleW = 14;
            int toggleH = 40;
            int toggleX = x + w;
            int toggleY = y + (CHAT_H - toggleH) / 2;
            c.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, pa(242, 10, 8, 20));
            c.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 1, C_BDR2);
            c.fill(toggleX + toggleW - 1, toggleY, toggleX + toggleW, toggleY + toggleH, C_BDR);
            c.fill(toggleX, toggleY + toggleH - 1, toggleX + toggleW, toggleY + toggleH, C_BDR);
            
            t(c, ">", toggleX + 4, toggleY + 16, T_W, 0.8f);
        } else {
            // Header
            // Chat icon + Text
            int cy = y + 8;
            int cx = x + 10;
            c.fill(cx, cy, cx + 10, cy + 8, T_DIM);
            c.fill(cx + 2, cy + 8, cx + 4, cy + 10, T_DIM);
            
            t(c, "CHAT GLOBAL", cx + 16, cy, T_DIM, 0.70f);
            
            // Close X
            int btnX = x + w - 16;
            int btnY = y + 2;
            int btnS = 12;
            drawHover(c, btnX, btnY, btnS, btnS, mx, my, true);
            
            int xCenter = btnX + btnS / 2;
            int yCenter = btnY + btnS / 2;
            int crossCol = T_DIM;
            for (int i = -3; i <= 3; i++) {
                c.fill(xCenter + i, yCenter + i, xCenter + i + 1, yCenter + i + 1, crossCol);
                c.fill(xCenter + i, yCenter - i, xCenter + i + 1, yCenter - i + 1, crossCol);
                c.fill(xCenter + i + 1, yCenter + i, xCenter + i + 2, yCenter + i + 1, crossCol);
                c.fill(xCenter + i + 1, yCenter - i, xCenter + i + 2, yCenter - i + 1, crossCol);
            }
            
            // Header separator
            c.fill(x + 10, y + 26, x + w - 10, y + 27, C_BDR);

            // Message area with word-wrap
            // ─────────────────────────────────────────────────────────────────────
            // enableScissor() uses raw GUI coords and DOES NOT honour the pushScale()
            // matrix, so we avoid it here.  Instead we clip by only rendering lines
            // that fit inside msgAreaTop..msgAreaBot.
            int msgAreaTop = y + 35; // increased padding to separate from "CHAT GLOBAL" header
            int msgAreaBot = y + CHAT_H - CHAT_INPUT_H - 10; // extra padding above input box
            int lineH = 12; // screen-pixels per line at 1.0f text scale
            int visLines = Math.max(1, (msgAreaBot - msgAreaTop) / lineH);

            // Max font-pixels per line so text wraps before hitting the card edge.
            int maxFontW = (int) ((w - 12) / 1.0f);

            // Build a flat list of wrapped OrderedText lines from all chat messages.
            java.util.List<net.minecraft.text.OrderedText> wrappedLines =
                new java.util.ArrayList<>();
            for (String msg : ArenaChatState.getMessages()) {
                if (msg == null || msg.isBlank()) {
                    wrappedLines.add(net.minecraft.text.OrderedText.EMPTY);
                    continue;
                }
                java.util.List<net.minecraft.text.OrderedText> chunks =
                    textRenderer.wrapLines(
                        net.minecraft.text.Text.literal(msg),
                        maxFontW
                    );
                if (chunks.isEmpty()) {
                    wrappedLines.add(net.minecraft.text.OrderedText.EMPTY);
                } else {
                    wrappedLines.addAll(chunks);
                }
            }

            int total = wrappedLines.size();
            // Store for mouseScrolled so it knows the real upper bound
            chatWrappedLineCount = total;

            // Clamp scroll so we can’t scroll past the oldest line
            chatScroll = Math.max(
                0,
                Math.min(chatScroll, Math.max(0, total - visLines))
            );

            // Bottom-aligned window into the wrapped lines
            int startIdx = Math.max(0, total - visLines - chatScroll);
            int endIdx = Math.min(total, startIdx + visLines);

            int ly = msgAreaTop;
            for (int i = startIdx; i < endIdx; i++) {
                net.minecraft.text.OrderedText line = wrappedLines.get(i);
                // Alternate row tint
                if (i % 2 == 0) {
                    c.fill(
                        x + 2,
                        ly,
                        x + w - 5,
                        ly + lineH - 1,
                        pa(20, 80, 60, 140)
                    );
                }
                // Draw wrapped line at 1.0f scale
                c.getMatrices().push();
                c.getMatrices().translate(x + 4, ly + 1, 0f);
                c.getMatrices().scale(1.0f, 1.0f, 1f);
                c.drawText(textRenderer, line, 0, 0, T_L, false);
                c.getMatrices().pop();
                ly += lineH;
            }

            // Divider above input (removed since we have a full box now)
            
            // Input field background (the TextFieldWidget renders on top)
            int inputY = y + CHAT_H - CHAT_INPUT_H - 4;
            c.fill(x + 4, inputY, x + w - 4, inputY + CHAT_INPUT_H, pa(15, 255, 255, 255));
            c.fill(x + 4, inputY, x + w - 4, inputY + 1, C_BDR2);
            c.fill(x + 4, inputY + CHAT_INPUT_H - 1, x + w - 4, inputY + CHAT_INPUT_H, C_BDR2);
            c.fill(x + 4, inputY, x + 5, inputY + CHAT_INPUT_H, C_BDR2);
            c.fill(x + w - 5, inputY, x + w - 4, inputY + CHAT_INPUT_H, C_BDR2);

            if (!chatInput.isVisible() || (!chatInput.isFocused() && chatInput.getText().isEmpty())) {
                String ph = "Digite mensagem...";
                int phW = Math.round(textRenderer.getWidth(ph) * 0.9f);
                t(c, ph, x + (w - phW) / 2, inputY + 4, T_DIM, 0.9f);
            }

            // Scrollbar when there are more lines than visible
            if (total > visLines) {
                scbar(
                    c,
                    x + w - 4,
                    msgAreaTop,
                    3,
                    msgAreaBot - msgAreaTop,
                    chatScroll,
                    Math.max(1, total - visLines)
                );
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter / NumpadEnter: send chat message or command
        if (
            (keyCode == 257 || keyCode == 335) &&
            chatInput != null &&
            chatInput.isFocused() &&
            !chatInput.getText().isBlank()
        ) {
            String text = chatInput.getText().trim();
            chatInput.setText("");
            chatScroll = 0; // scroll to bottom
            if (client != null && client.getNetworkHandler() != null) {
                if (text.startsWith("/")) {
                    // Send as command (without the leading slash)
                    client.getNetworkHandler().sendCommand(text.substring(1));
                } else {
                    // Send as regular chat message
                    client.getNetworkHandler().sendChatMessage(text);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Col3: Arena status (h configurable) ──────────────────────────────────
    private void drawArenaStatus(DrawContext c, int x, int y, int w, int h) {
        card(c, x, y, w, h);
        t(c, "STATUS DA ARENA", x + 8, y + 8, T_DIM, 0.63f);
        boolean rdy = ArenaClientState.getAvailableArenas() > 0;
        t(
            c,
            rdy ? "Pronto" : "Lotado",
            x + w - 46,
            y + 8,
            rdy ? A_GREEN : A_RED,
            0.63f
        );

        int cw = (w - 16) / 3;
        String[] lbl = { "Online", "Na Fila", "Batalhas" };
        String[] val = {
            "" + ArenaClientState.getPlayersOnline(),
            "" + ArenaClientState.getPlayersInQueue(),
            "" + ArenaClientState.getActiveBattles(),
        };
        for (int i = 0; i < 3; i++) {
            int sx = x + 8 + i * cw;
            t(c, lbl[i], sx, y + 24, T_DIM, 0.60f);
            t(c, val[i], sx, y + 34, T_W, 0.86f);
        }

        // Arenas progress bar
        int avail = ArenaClientState.getAvailableArenas();
        int total = ArenaClientState.getTotalArenas();
        t(c, "Arenas: " + avail + "/" + total, x + 8, y + 54, T_DIM, 0.60f);
        int bw = w - 16;
        c.fill(x + 8, y + 64, x + 8 + bw, y + 68, C_BDR2);
        if (total > 0) {
            int fl = (int) (bw * ((float) avail / total));
            if (fl > 0) c.fill(x + 8, y + 64, x + 8 + fl, y + 68, A_GREEN);
        }
        c.fill(x + 8, y + 64, x + 8 + bw, y + 65, pa(14, 255, 255, 255));
    }

    private void drawHover(DrawContext c, int x, int y, int w, int h, int mx, int my, boolean active) {
        if (active && mx >= x && mx < x + w && my >= y && my < y + h) {
            c.fill(x, y, x + w, y + h, pa(40, 255, 255, 255)); // highlight sutil
        }
    }

    private void drawPlayBox(
        DrawContext c,
        int x,
        int y,
        int w,
        int cardH,
        int mx,
        int my
    ) {
        boolean inQueue = queueInRanked || queueInCasual;
        int cy_current = y + 20;

        // 1. Tabs Inset
        int tabW = 200;
        int tabH = 34;
        int tabX = x + (w - tabW) / 2;
        int tabY = y + 60;
        
        // Tab background
        c.fill(tabX, tabY, tabX + tabW, tabY + tabH, pa(200, 16, 12, 22));
        
        // Active Tab Highlights
        int halfW = tabW / 2;
        if (playTabRanked) {
            c.fill(tabX, tabY, tabX + halfW, tabY + tabH, A_PUR);
        } else {
            c.fill(tabX + halfW, tabY, tabX + tabW, tabY + tabH, pa(35, 255, 255, 255));
        }

        int ranW = Math.round(textRenderer.getWidth("Ranqueada") * 0.9f * 1.25f);
        int casW = Math.round(textRenderer.getWidth("Casual") * 0.9f * 1.25f);
        
        t(c, "Ranqueada", tabX + (halfW - ranW) / 2, tabY + (tabH / 2) - 4, playTabRanked ? T_W : T_DIM, 0.9f);
        t(c, "Casual", tabX + halfW + (halfW - casW) / 2, tabY + (tabH / 2) - 4, !playTabRanked ? T_W : T_DIM, 0.9f);

        hits.add(new Hit("tab_ranked", tabX, tabY, halfW, tabH));
        hits.add(new Hit("tab_casual", tabX + halfW, tabY, halfW, tabH));

        // 2. Warning Label
        cy_current = tabY + tabH + 20;
        String warnText = playTabRanked ? "- AFETA ELO E RANKING -" : "- SEM IMPACTO NO RANKING -";
        int warnW = Math.round(textRenderer.getWidth(warnText) * 0.6f * 1.25f);
        t(c, warnText, x + (w - warnW) / 2, cy_current, T_DIM, 0.6f);
        
        // 3. Format Section
        cy_current += 30;
        int fmtTxtW = Math.round(textRenderer.getWidth("FORMATO") * 0.8f * 1.25f);
        t(c, "FORMATO", x + (w - fmtTxtW) / 2, cy_current, A_PUR, 0.8f);
        
        cy_current += 20;
        int boxW = 160;
        int boxH = 50;
        int boxX = x + (w - boxW) / 2;
        
        c.fill(boxX + 2, cy_current, boxX + boxW - 2, cy_current + 1, A_RED);
        c.fill(boxX + 2, cy_current + boxH - 1, boxX + boxW - 2, cy_current + boxH, A_RED);
        c.fill(boxX, cy_current + 2, boxX + 1, cy_current + boxH - 2, A_RED);
        c.fill(boxX + boxW - 1, cy_current + 2, boxX + boxW, cy_current + boxH - 2, A_RED);
        
        String currentFmt = playTabRanked ? rFmt : cFmt;
        int currentFmtW = Math.round(textRenderer.getWidth(currentFmt) * 1.1f * 1.25f);
        t(c, currentFmt, x + (w - currentFmtW) / 2, cy_current + 15, A_RED, 1.1f);
        
        String subFmt = currentFmt.toLowerCase().contains("dupla") ? "2v2" : 
                        currentFmt.toLowerCase().contains("tripla") ? "3v3" : "1v1";
        int subFmtW = Math.round(textRenderer.getWidth(subFmt) * 0.7f * 1.25f);
        t(c, subFmt, x + (w - subFmtW) / 2, cy_current + 32, T_L, 0.7f);

        // Format Arrows (Square)
        int arrSize = 25;
        int arrY = cy_current + (boxH - arrSize) / 2;
        int leftArrX = boxX - arrSize - 10;
        int rightArrX = boxX + boxW + 10;

        c.fill(leftArrX, arrY, leftArrX + arrSize, arrY + arrSize, C_BASE);
        c.fill(leftArrX, arrY, leftArrX + arrSize, arrY + 1, C_BDR);
        c.fill(leftArrX, arrY + arrSize - 1, leftArrX + arrSize, arrY + arrSize, C_BDR);
        c.fill(leftArrX, arrY, leftArrX + 1, arrY + arrSize, C_BDR);
        c.fill(leftArrX + arrSize - 1, arrY, leftArrX + arrSize, arrY + arrSize, C_BDR);
        drawHover(c, leftArrX, arrY, arrSize, arrSize, mx, my, !inQueue);
        
        c.fill(rightArrX, arrY, rightArrX + arrSize, arrY + arrSize, C_BASE);
        c.fill(rightArrX, arrY, rightArrX + arrSize, arrY + 1, C_BDR);
        c.fill(rightArrX, arrY + arrSize - 1, rightArrX + arrSize, arrY + arrSize, C_BDR);
        c.fill(rightArrX, arrY, rightArrX + 1, arrY + arrSize, C_BDR);
        c.fill(rightArrX + arrSize - 1, arrY, rightArrX + arrSize, arrY + arrSize, C_BDR);
        drawHover(c, rightArrX, arrY, arrSize, arrSize, mx, my, !inQueue);
        int arrTxtW = Math.round(textRenderer.getWidth("<") * 1.0f * 1.25f);
        int arrTxtW2 = Math.round(textRenderer.getWidth(">") * 1.0f * 1.25f);
        t(c, "<", leftArrX + (arrSize - arrTxtW) / 2 - 1, arrY + (arrSize / 2) - 4, inQueue ? T_MUT : T_L, 1.0f);
        t(c, ">", rightArrX + (arrSize - arrTxtW2) / 2 + 1, arrY + (arrSize / 2) - 4, inQueue ? T_MUT : T_L, 1.0f);

        hits.add(new Hit("fmt_prev", leftArrX, arrY, arrSize, arrSize));
        hits.add(new Hit("fmt_next", rightArrX, arrY, arrSize, arrSize));

        // Format Pagination Dots
        cy_current += boxH + 8;
        java.util.List<String> fmts = playTabRanked ? java.util.List.of("Solo", "Duplas", "Triplas") : java.util.List.of("Solo", "Duplas", "Triplas", "Monotype");
        int fmtIdx = fmts.indexOf(currentFmt);
        if (fmtIdx == -1) fmtIdx = 0;
        
        int dotW = 4;
        int gap = 6;
        int numDots = fmts.size();
        int dotsX = x + w / 2 - (numDots * dotW + (numDots - 1) * gap) / 2;
        for (int i = 0; i < numDots; i++) {
            c.fill(dotsX + i * (dotW + gap), cy_current, dotsX + i * (dotW + gap) + dotW, cy_current + dotW, i == fmtIdx ? A_PUR : C_BDR);
        }

        // 4. Level Section
        cy_current += 30;
        int lvlW = 100;
        int lvlH = 45;
        int lvlY = cy_current;
        int lvlX1 = x + (w - lvlW * 2 - 10) / 2;
        int lvlX2 = lvlX1 + lvlW + 10;
        
        int nTextW = Math.round(textRenderer.getWidth("NÍVEL") * 0.8f * 1.25f);
        t(c, "NÍVEL", x + (w - nTextW) / 2, lvlY - 18, A_PUR, 0.8f);

        boolean lvl50 = "50".equals(cLvl);
        
        c.fill(lvlX1, lvlY, lvlX1 + lvlW, lvlY + lvlH, lvl50 ? pa(30, 26, 237, 164) : C_CARD2);
        // Level Toggles
        boolean is50 = "50".equals(cLvl);
        int col50Txt = is50 ? A_GREEN : T_DIM;
        int col100Txt = !is50 ? A_GREEN : T_DIM;
        
        c.fill(lvlX1, lvlY, lvlX1 + lvlW, lvlY + lvlH, is50 ? pa(30, 26, 237, 164) : C_CARD2);
        if (is50) c.fill(lvlX1, lvlY, lvlX1 + lvlW, lvlY + 1, A_GREEN);
        if (is50) c.fill(lvlX1, lvlY + lvlH - 1, lvlX1 + lvlW, lvlY + lvlH, A_GREEN);
        if (is50) c.fill(lvlX1, lvlY, lvlX1 + 1, lvlY + lvlH, A_GREEN);
        if (is50) c.fill(lvlX1 + lvlW - 1, lvlY, lvlX1 + lvlW, lvlY + lvlH, A_GREEN);
        
        c.fill(lvlX2, lvlY, lvlX2 + lvlW, lvlY + lvlH, !is50 ? pa(30, 26, 237, 164) : C_CARD2);
        if (!is50) c.fill(lvlX2, lvlY, lvlX2 + lvlW, lvlY + 1, A_GREEN);
        if (!is50) c.fill(lvlX2, lvlY + lvlH - 1, lvlX2 + lvlW, lvlY + lvlH, A_GREEN);
        if (!is50) c.fill(lvlX2, lvlY, lvlX2 + 1, lvlY + lvlH, A_GREEN);
        if (!is50) c.fill(lvlX2 + lvlW - 1, lvlY, lvlX2 + lvlW, lvlY + lvlH, A_GREEN);
        
        String t50 = "Nv. 50";
        int t50W = Math.round(textRenderer.getWidth(t50) * 1.1f * 1.25f);
        if (is50 && playTabRanked) {
            String sub50 = "Fixo";
            int sub50W = Math.round(textRenderer.getWidth(sub50) * 0.7f * 1.25f);
            t(c, t50, lvlX1 + (lvlW - t50W) / 2, lvlY + (lvlH / 2) - 10, col50Txt, 1.1f);
            t(c, sub50, lvlX1 + (lvlW - sub50W) / 2, lvlY + (lvlH / 2) + 2, col50Txt, 0.7f);
        } else {
            t(c, t50, lvlX1 + (lvlW - t50W) / 2, lvlY + (lvlH / 2) - 4, col50Txt, 1.1f);
        }
        
        boolean is100 = "100".equals(cLvl) && !playTabRanked;
        
        String t100 = "Nv. 100";
        int t100W = Math.round(textRenderer.getWidth(t100) * 1.1f * 1.25f);
        t(c, t100, lvlX2 + (lvlW - t100W) / 2, lvlY + (lvlH / 2) - 4, col100Txt, 1.1f);

        hits.add(new Hit("lvl_50", lvlX1, lvlY, lvlW, lvlH));
        hits.add(new Hit("lvl_100", lvlX2, lvlY, lvlW, lvlH));

        // 5. Start Button
        int cy = cy_current + lvlH + 20;
        int startW = 220;
        int startH = 45;
        int startX = x + (w - startW) / 2;
        
        if (inQueue) {
            drawHover(c, startX, cy, startW, startH, mx, my, true); 
            long nowMs = System.currentTimeMillis();
            int pulseAlpha = 80 + (int) (Math.sin(nowMs / 250.0) * 60); 
            int startBdr = pa(255, 220, 50, 50);
            int glowColor = pa(pulseAlpha, 220, 50, 50);
            
            c.fill(startX, cy, startX + startW, cy + startH, glowColor);
            
            c.fill(startX + 2, cy, startX + startW - 2, cy + 1, startBdr);
            c.fill(startX + 2, cy + startH - 1, startX + startW - 2, cy + startH, startBdr);
            c.fill(startX, cy + 2, startX + 1, cy + startH - 2, startBdr);
            c.fill(startX + startW - 1, cy + 2, startX + startW, cy + startH - 2, startBdr);
            
            String startTxt = "Procurando partida";
            int startTxtW = Math.round(textRenderer.getWidth(startTxt) * 1.0f * 1.25f);
            t(c, startTxt, x + (w - startTxtW) / 2, cy + (startH / 2) - 10, T_W, 1.0f);
            
            int secs = QueueStatusOverlay.getInstance().getElapsedSeconds();
            String timer = String.format("%02d:%02d", secs / 60, secs % 60);
            int timerW = Math.round(textRenderer.getWidth(timer) * 0.8f * 1.25f);
            t(c, timer, x + (w - timerW) / 2, cy + (startH / 2) + 2, T_W, 0.8f);
        } else {
            drawHover(c, startX, cy, startW, startH, mx, my, true); 
            int startBdr = A_GREEN;
            c.fill(startX + 1, cy + 1, startX + startW - 1, cy + startH - 1, pa(40, 16, 185, 129));
            
            c.fill(startX + 2, cy, startX + startW - 2, cy + 1, startBdr);
            c.fill(startX + 2, cy + startH - 1, startX + startW - 2, cy + startH, startBdr);
            c.fill(startX, cy + 2, startX + 1, cy + startH - 2, startBdr);
            c.fill(startX + startW - 1, cy + 2, startX + startW, cy + startH - 2, startBdr);
            
            String startTxt = "Começar";
            int startTxtW = Math.round(textRenderer.getWidth(startTxt) * 1.3f * 1.25f);
            t(c, startTxt, x + (w - startTxtW) / 2, cy + (startH / 2) - 5, A_GREEN, 1.3f);
        }
        
        // 5.5 Party Preview
        int prWidth = 320;
        int prY = cy + startH + 15;
        int prX = x + (w - prWidth) / 2;
        java.util.List<net.minecraft.text.Text> hover = partyRenderer.render(c, textRenderer, prX, prY, prWidth, 55, mx, my, 0f);
        if (hover != null) {
            partyHoverTooltip = hover;
            int hstartX = prX + (prWidth - 308) / 2;
            int hoveredIndex = (mx - hstartX) / 52;
            if (hoveredIndex >= 0 && hoveredIndex < 6) {
                partyHoverX = hstartX + hoveredIndex * 52 + 24;
                partyHoverY = prY + 55 + 2;
            } else {
                partyHoverX = mx;
                partyHoverY = my + 20;
            }
        }
        
        hits.add(new Hit(inQueue ? "cancel_queue" : "start_queue", startX, cy, startW, startH));

        // 6. Footer Status
        int footY = y + cardH - 20;
        
        String err = playTabRanked ? rankedValidationError : casualValidationError;
        long errMs = playTabRanked ? rankedValidationErrorMs : casualValidationErrorMs;
        if (err != null && errMs > 0) {
            drawValidationToast(c, x + 10, footY - 20, w - 20, err, errMs);
        }

        float fScale = 0.7f * 1.25f;
        int stW1 = Math.round(textRenderer.getWidth("STATUS ") * fScale);
        int stW2 = Math.round(textRenderer.getWidth(inQueue ? "PROCURANDO" : "PRONTO") * fScale);
        int onW1 = Math.round(textRenderer.getWidth("ONLINE ") * fScale);
        int onW2 = Math.round(textRenderer.getWidth("" + ArenaClientState.getPlayersOnline()) * fScale);
        int fiW1 = Math.round(textRenderer.getWidth("NA FILA ") * fScale);
        int fiW2 = Math.round(textRenderer.getWidth("" + ArenaClientState.getPlayersInQueue()) * fScale);
        int baW1 = Math.round(textRenderer.getWidth("BATALHAS ") * fScale);
        int baW2 = Math.round(textRenderer.getWidth("" + ArenaClientState.getActiveBattles()) * fScale);
        
        int totalW = stW1 + stW2 + 15 + onW1 + onW2 + 15 + fiW1 + fiW2 + 15 + baW1 + baW2;
        int fx = x + (w - totalW) / 2;
        
        t(c, "STATUS ", fx, footY, T_DIM, 0.7f);
        fx += stW1;
        t(c, inQueue ? "PROCURANDO" : "PRONTO", fx, footY, inQueue ? A_ORG : A_GREEN, 0.7f);
        fx += stW2 + 15;
        
        t(c, "ONLINE ", fx, footY, T_DIM, 0.7f);
        fx += onW1;
        t(c, "" + ArenaClientState.getPlayersOnline(), fx, footY, A_PUR, 0.7f);
        fx += onW2 + 15;
        
        t(c, "NA FILA ", fx, footY, T_DIM, 0.7f);
        fx += fiW1;
        t(c, "" + ArenaClientState.getPlayersInQueue(), fx, footY, T_W, 0.7f);
        fx += fiW2 + 15;
        
        t(c, "BATALHAS ", fx, footY, T_DIM, 0.7f);
        fx += baW1;
        t(c, "" + ArenaClientState.getActiveBattles(), fx, footY, T_W, 0.7f);
    }

    private void drawQueueCounterCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int cardH,
        boolean ranked
    ) {
        long nowMs = System.currentTimeMillis();
        int accent = ranked ? A_PUR : A_GOLD;

        // ── Timer (Centered) ────────────────────────────────────────────────
        int cy = y + 40;
        int secs = QueueStatusOverlay.getInstance().getElapsedSeconds();
        String timer = String.format("%02d:%02d", secs / 60, secs % 60);
        int tw = Math.round(textRenderer.getWidth(timer) * 1.5f);
        t(c, timer, x + (w - tw) / 2, cy, T_W, 1.5f);

        // ── Subtitle (Left aligned) ──────────────────────────────────────────
        cy += 30;
        String sub = ranked
            ? "Single - Nivel 50 - Ranqueada"
            : cFmt + " - Nivel " + cLvl + " - Casual";
        int subW = Math.round(textRenderer.getWidth(sub) * 0.8f);
        t(c, sub, x + (w - subW) / 2, cy, T_W, 0.8f);
        
        // Divider
        cy += 20;
        c.fill(x + 20, cy, x + w - 20, cy + 1, C_BDR2);

        // ── Searching row ────────────────────────────────────────────────────
        cy += 16;
        int dotCount = (int) ((nowMs / 450L) % 4L);
        String searching = "Procurando adversario" + ".".repeat(dotCount);
        int searchingW = Math.round(textRenderer.getWidth(searching) * 0.8f);
        t(c, searching, x + (w - searchingW) / 2, cy, T_DIM, 0.8f);

        // Animated scan bar
        cy += 20;
        int barW = w - 40;
        float barFrac = (nowMs % 1800) / 1800.0f;
        c.fill(x + 20, cy, x + 20 + barW, cy + 4, C_BDR2);
        int barFill = (int) (barW * barFrac);
        if (barFill > 0) {
            c.fill(
                x + 20,
                cy,
                x + 20 + barFill,
                cy + 4,
                accent
            );
        }
    }

    private void drawMissPreview(DrawContext c, int x, int y, int w, int cardH, int mx, int my) {
        t(c, "MISSÕES DIÁRIAS", x + 12, y + 8, T_DIM, 0.70f);
        lnk(c, "Ver todos >", x + w - 52, y + 8, T_PUR, 0.64f);

        List<QuestEntryPayload> daily = ArenaQuestClientState.getDailyQuests();
        if (daily.isEmpty()) {
            t(c, "Abra /arena para", x + 8, y + 28, T_DIM, 0.66f);
            t(c, "sincronizar.", x + 8, y + 38, T_DIM, 0.66f);
            return;
        }

        int ry = y + 35;
        for (
            int i = 0;
            i < daily.size() && ry + 38 <= y + cardH;
            i++
        ) {
            QuestEntryPayload q = daily.get(i);
            int titleMaxW = w - 18;
            tfit(c, q.title(), x + 12, ry, titleMaxW, T_DIM, 0.75f);

            t(
                c,
                q.currentProgress() + "/" + q.targetAmount(),
                x + 12,
                ry + 15,
                T_MUT,
                0.65f
            );

            int barW = w - 24;
            c.fill(x + 12, ry + 25, x + 12 + barW, ry + 26, C_BDR);
            int fl = (int) (barW * q.progressFraction());
            if (fl > 0) c.fill(x + 12, ry + 25, x + 12 + fl, ry + 26, A_PUR);

            if (over(mx, my, x + 8, ry, w - 16, 38)) {
                missionHoverTooltip = List.of(
                    net.minecraft.text.Text.literal(q.title()).formatted(net.minecraft.util.Formatting.WHITE),
                    net.minecraft.text.Text.literal(q.description()).formatted(net.minecraft.util.Formatting.GRAY),
                    net.minecraft.text.Text.empty(),
                    net.minecraft.text.Text.literal("Recompensa: " + q.rewardDescription()).formatted(net.minecraft.util.Formatting.GOLD)
                );
                missionHoverX = mx;
                missionHoverY = my;
            }

            ry += 42;
        }
    }

    private void drawQuickLinks(
        DrawContext c,
        int x,
        int y,
        int w,
        int mx,
        int my
    ) {
        c.fill(x, y, x + w, y + 34, pa(35, 108, 62, 230));
        c.fill(x, y, x + w, y + 1, pa(80, 108, 62, 230));
        c.fill(x, y, x + 1, y + 34, pa(80, 108, 62, 230));
        t(c, "Use /arena ou o Orbe", x + 5, y + 4, T_DIM, 0.62f);
        t(c, "para reabrir este menu.", x + 5, y + 13, T_DIM, 0.62f);

        int lw = (w - 4) / 2,
            lh = 14,
            ly = y + 38;
        qlnk(c, mx, my, "Ranking", x, ly, lw, lh);
        qlnk(c, mx, my, "Historico", x + lw + 4, ly, lw, lh);
        qlnk(c, mx, my, "Assistir", x, ly + 17, lw, lh);
        qlnk(c, mx, my, "Regras", x + lw + 4, ly + 17, lw, lh);
    }

    private void qlnk(
        DrawContext c,
        int mx,
        int my,
        String lbl,
        int x,
        int y,
        int w,
        int h
    ) {
        boolean hov = over(mx, my, x, y, w, h);
        if (hov) c.fill(x, y, x + w, y + h, pa(40, 108, 62, 230));
        t(c, lbl, x + 5, y + 3, hov ? T_L : T_DIM, 0.70f);
        c.fill(x, y + h - 1, x + w, y + h, C_BDR);
    }

    // =========================================================================
    // PAGE: PROFILE
    // =========================================================================

    private void renderProfile(DrawContext c, int mx, int my, int cx, int cy) {
        int contentW = CW - 16;
        int conY = cy + 10;
        int conH = H - 20;
        int totalH = 560; // 100(header) + 40(gap) + 140(ranked) + 40(gap) + 70(casual) + 40(gap) + 110(monotype) + 20(padding)
        int maxS = Math.max(0, totalH - conH);
        pScr = net.minecraft.util.math.MathHelper.clamp(pScr, 0, maxS);
        
        c.enableScissor(wx(cx + 4), wy(conY), wx(cx + CW - 4), wy(conY + conH));
        int currentY = conY - pScr;
        
        // ── 1. HEADER DINÂMICO ───────────────────────────────────────────────
        int headerH = 100;
        // Fundo com glow sutil
        c.fill(cx + 8, currentY, cx + 8 + contentW, currentY + headerH, C_CARD);
        c.fill(cx + 8, currentY, cx + 8 + contentW, currentY + 1, A_PUR2);
        c.fill(cx + 8, currentY, cx + 9, currentY + headerH, A_PUR2);
        c.fill(cx + 8 + contentW - 1, currentY, cx + 8 + contentW, currentY + headerH, C_BDR);
        c.fill(cx + 8, currentY + headerH - 1, cx + 8 + contentW, currentY + headerH, C_BDR);
        
        int avX = cx + 20;
        int avY = currentY + 15;
        c.fill(avX - 2, avY - 2, avX + 70 + 2, avY + 70 + 2, A_PUR2);
        c.fill(avX, avY, avX + 70, avY + 70, C_BDR);
        drawPlayerHead(c, avX, avY, 70);
        
        int eloMax = ArenaClientState.getRankedRating();
        String tierNameMax = getRankTierName(eloMax);
        
        // Nome e Fav
        int infoX = avX + 85;
        t(c, ArenaClientState.getPlayerName(), infoX, currentY + 20, T_W, 1.2f);
        
        int nameW = Math.round(textRenderer.getWidth(ArenaClientState.getPlayerName()) * 1.2f);
        drawRankBadge(c, avX + 70 - 10, avY + 70 - 10, 12, tierNameMax);
        
        String fav = ArenaClientState.getFavoritePokemon();
        int favW = Math.round(textRenderer.getWidth("★ " + fav) * 0.7f * 1.25f);
        c.fill(infoX + 130, currentY + 22, infoX + 130 + favW + 10, currentY + 34, pa(40, 255, 255, 255));
        t(c, "★ " + fav, infoX + 135, currentY + 25, T_DIM, 0.7f);
        
        t(c, "Elo Maximo: " + eloMax + " (" + tierNameMax + ")", infoX, currentY + 40, T_PUR, 0.8f);
        
        int badgeX = cx + 8 + contentW - 140;
        c.fill(badgeX, currentY + 15, badgeX + 55, currentY + 50, C_CARD2);
        c.fill(badgeX, currentY + 15, badgeX + 55, currentY + 16, C_BDR2);
        long seasonStart = ArenaClientState.getCurrentSeasonStartedAtMs();
        int days = seasonStart > 0 ? (int) ((System.currentTimeMillis() - seasonStart) / (1000 * 60 * 60 * 24)) : 0;
        tc(c, "" + days, badgeX + 27, currentY + 22, T_W, 1.1f);
        tc(c, "DIAS", badgeX + 27, currentY + 38, T_DIM, 0.6f);
        
        int honX = badgeX + 65;
        c.fill(honX, currentY + 15, honX + 55, currentY + 50, C_CARD2);
        c.fill(honX, currentY + 15, honX + 55, currentY + 16, C_BDR2);
        tc(c, ArenaClientState.getHonorRating() + "%", honX + 27, currentY + 22, T_W, 1.1f);
        tc(c, "HONRA", honX + 27, currentY + 38, T_DIM, 0.6f);
        
        // 4 StatBoxes (em linha dentro do header, na base)
        int rW = ArenaClientState.getRankedWins();
        int rL = ArenaClientState.getRankedLosses();
        int tot = rW + rL;
        int wr = tot == 0 ? 0 : Math.round((rW * 100f) / tot);
        
        String[] stLbl = {"PARTIDAS", "VITORIAS", "WIN RATE", "MEDIA TURNOS"};
        String[] stVal = {"" + tot, "" + rW, wr + "%", "" + ArenaClientState.getTurnAverage()};
        int[] stCol = {T_W, A_PUR, T_W, T_W};
        
        int stW = (contentW - 130) / 4;
        for (int i=0; i<4; i++) {
            int sx = infoX + i*(stW + 5);
            int sy = currentY + 60;
            c.fill(sx, sy, sx + stW, sy + 30, C_CARD2);
            c.fill(sx, sy, sx + stW, sy + 1, C_BDR2);
            t(c, stLbl[i], sx + 6, sy + 6, T_DIM, 0.55f);
            t(c, stVal[i], sx + 6, sy + 15, stCol[i], 0.85f);
        }
        
        currentY += headerH + 20;
        
        // ── 2. PAINEL RANQUEADO ──────────────────────────────────────────────
        t(c, "⚔ ELO POR FORMATO - RANQUEADO", cx + 8, currentY, T_DIM, 0.75f);
        c.fill(cx + 8, currentY + 12, cx + 8 + contentW, currentY + 13, C_BDR);
        
        currentY += 20;
        int cardW = (contentW - 20) / 3;
        String[] fms = {"Solo", "Duplas", "Triplas"};
        String[] fids = {"singles", "doubles", "triples"};
        for (int i=0; i<3; i++) {
            drawPassportRankedCard(c, cx + 8 + i*(cardW + 10), currentY, cardW, 140, fms[i], fids[i], mx, my);
        }
        
        currentY += 140 + 20;
        
        // ── 3. CASUAL TRACKER ────────────────────────────────────────────────
        t(c, "🎮 CASUAL TRACKER", cx + 8, currentY, T_DIM, 0.75f);
        c.fill(cx + 8, currentY + 12, cx + 8 + contentW, currentY + 13, C_BDR);
        
        currentY += 20;
        String[] cFms = {"SOLO", "DUPLAS", "TRIPLAS"};
        for (int i=0; i<3; i++) {
            drawCasualTrackerCard(c, cx + 8 + i*(cardW + 10), currentY, cardW, 70, cFms[i]);
        }
        
        currentY += 70 + 20;
        
        // ── 4. MAESTRIA MONOTYPE ─────────────────────────────────────────────
        t(c, "✨ MAESTRIA MONOTYPE", cx + 8, currentY, T_DIM, 0.75f);
        c.fill(cx + 8, currentY + 12, cx + 8 + contentW, currentY + 13, C_BDR);
        
        currentY += 20;
        drawMonotypeMastery(c, cx + 8, currentY, contentW, 110);
        
        c.disableScissor();
        if (maxS > 0) scbar(c, cx + CW - 6, conY, 4, conH, pScr, maxS);
    }
    
    private void drawPassportRankedCard(DrawContext c, int x, int y, int w, int h, String fmt, String fid, int mx, int my) {
        card(c, x, y, w, h);
        tc(c, fmt.toUpperCase(java.util.Locale.ROOT), x + w/2, y + 10, T_W, 0.9f);
        
        int elo = eloForFmt(fid);
        int wins = winsForFmt(fid);
        int games = gamesForFmt(fid);
        String tier = getRankTierName(elo);
        
        int r = 12;
        int iconX = x + w/2 - r;
        int iconY = y + 25;
        drawRankBadge(c, iconX + r, iconY + r, r, tier);
        
        if (over(mx, my, iconX, iconY, r*2, r*2)) {
            eloTipFid = fid;
            eloTipX = mx;
            eloTipY = my + 10;
        }
        
        tc(c, "" + elo, x + w/2, y + 55, T_W, 1.2f);
        
        int trendColor = games > 0 ? A_GREEN : T_DIM;
        t(c, games > 0 ? "~" : "-", x + w/2 + 25, y + 55, trendColor, 1.0f);
        
        tc(c, tier.toUpperCase(java.util.Locale.ROOT), x + w/2, y + 70, eloTierBg(elo), 0.7f);
        
        int wr = games == 0 ? 0 : Math.round((wins * 100f) / games);
        int gw = w / 2;
        tc(c, "PARTIDAS", x + gw/2, y + 85, T_DIM, 0.55f);
        tc(c, "" + games, x + gw/2, y + 95, T_W, 0.8f);
        tc(c, "WIN RATE", x + gw + gw/2, y + 85, T_DIM, 0.55f);
        tc(c, wr + "%", x + gw + gw/2, y + 95, A_PUR, 0.8f);
        
        int prev = prevTierElo(elo), next = nextTierElo(elo);
        float progTarget = next > prev ? net.minecraft.util.math.MathHelper.clamp((float) (elo - prev) / (next - prev), 0f, 1f) : 1f;
        
        float currentAnim = eloProgressAnim.getOrDefault(fid, 0f);
        if (currentAnim < progTarget) {
            currentAnim = Math.min(progTarget, currentAnim + 0.015f);
        } else if (currentAnim > progTarget) {
            currentAnim = Math.max(progTarget, currentAnim - 0.015f);
        }
        eloProgressAnim.put(fid, currentAnim);
        float prog = currentAnim;
        
        int barY = y + 115;
        tc(c, elo + " / " + next, x + w/2, barY - 8, T_DIM, 0.5f);
        
        int bww = w - 20;
        c.fill(x + 10, barY, x + 10 + bww, barY + 4, C_BDR2);
        int fl = (int) (bww * prog);
        if (fl > 0) {
            c.fill(x + 10, barY, x + 10 + fl, barY + 4, eloTierBg(elo));
        }
        
        int rankPos = ArenaClientState.getPlayerRankForFormat(fid);
        String rankStr = rankPos > 0 ? "#" + rankPos : "-";
        t(c, rankStr, x + w - 35, barY + 8, A_PUR, 0.55f);
        int streak = streakForFmt(fid);
        t(c, "Streak: " + (streak > 0 ? "+" + streak : String.valueOf(streak)), x + 10, barY + 8, T_DIM, 0.55f);
    }
    
    private void drawCasualTrackerCard(DrawContext c, int x, int y, int w, int h, String title) {
        card(c, x, y, w, h);
        tc(c, title, x + w/2, y + 10, T_DIM, 0.8f);
        String fid = title.equals("SOLO") ? "singles" : (title.equals("DUPLAS") ? "doubles" : "triples");
        int wins = ArenaClientState.getQuickWins(fid);
        tc(c, "Vitorias", x + w/2, y + 50, T_DIM, 0.6f);
        tc(c, "v " + wins, x + w/2, y + 25, pa(255, 180, 100, 255), 1.4f);
    }
    
    private void drawMonotypeMastery(DrawContext c, int x, int y, int w, int h) {
        card(c, x, y, w, h);
        String[] types = {"Normal", "Fogo", "Agua", "Planta", "Eletrico", "Gelo", "Lutador", "Veneno", "Terra", 
                          "Voador", "Psiquico", "Inseto", "Pedra", "Fantasma", "Dragao", "Sombrio", "Aco", "Fada"};
        String[] textureNames = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
                                 "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};
        int[] tCols = {
            pa(255,168,168,120), pa(255,240,128,48), pa(255,104,144,240), pa(255,120,200,80), pa(255,248,208,48), pa(255,152,216,216),
            pa(255,192,48,40), pa(255,160,64,160), pa(255,224,192,104), pa(255,168,144,240), pa(255,248,88,136), pa(255,168,184,32),
            pa(255,184,160,56), pa(255,112,88,152), pa(255,112,56,248), pa(255,112,88,72), pa(255,184,184,208), pa(255,238,153,172)
        };
        
        int cols = 9;
        int rows = 2;
        int gw = w / cols;
        int gh = h / rows;
        
        for (int i=0; i<18; i++) {
            int cx = x + (i % cols) * gw + gw/2;
            int cy = y + (i / cols) * gh + gh/2 - 5;
            
            int wins = ArenaClientState.getMonotypeWins(textureNames[i]);
            boolean won = wins > 0;
            
            int bCol = won ? tCols[i] : pa(40, 80, 80, 80);
            
            if (won) {
                fillCircle(c, cx, cy, 14, pa(60, (bCol>>16)&0xFF, (bCol>>8)&0xFF, bCol&0xFF));
            }
            
            net.minecraft.util.Identifier typeIcon = net.minecraft.util.Identifier.of("cobblemon", "textures/gui/types.png");
            int size = 16;
            
            if (!won) {
                c.setShaderColor(0.4f, 0.4f, 0.4f, 1.0f); // Aumentado o brilho de 0.2 para 0.4
            } else {
                c.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Garante cor 100% branca
            }
            
            // Alterado size para 16 para casar exatamente com o corte da sprite sheet, evitando vazar a borda
            c.drawTexture(typeIcon, cx - size/2, cy - size/2, i * 16, 0, size, size, 16 * 18, 16);
            
            c.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            
            tc(c, types[i].toUpperCase(java.util.Locale.ROOT), cx, cy + 16, won ? T_DIM : pa(255,80,80,80), 0.5f);
            if (won) {
                tc(c, wins + "W", cx, cy + 24, pa(255, 100, 255, 100), 0.6f); // Cor verde para o número de vitórias
            }
        }
    }

    // =========================================================================
    // PAGE: LEADERBOARD
    // =========================================================================

    private void renderLeaderboard(
        DrawContext c,
        int mx,
        int my,
        int cx,
        int cy
    ) {
        // Crown icon
        int crX = cx + 8;
        int crY = cy + 10;
        c.fill(crX, crY+2, crX+1, crY+8, A_GOLD); 
        c.fill(crX+1, crY+8, crX+9, crY+9, A_GOLD);
        c.fill(crX+9, crY+2, crX+10, crY+8, A_GOLD); 
        c.fill(crX+2, crY+4, crX+3, crY+8, A_GOLD);
        c.fill(crX+4, crY+1, crX+5, crY+8, A_GOLD); 
        c.fill(crX+7, crY+4, crX+8, crY+8, A_GOLD);

        t(c, "Ranking da Ranqueada", cx + 22, cy + 10, T_W, 1.0f);
        t(
            c,
            "Top 100 - " + ArenaClientState.getCurrentSeasonName(),
            cx + 22,
            cy + 25,
            T_DIM, 0.70f
        );

        String[] fi = { "singles", "doubles", "triples" };
        String[] titles = { "SOLO", "DUPLAS", "TRIPLAS" };
        
        int tx = cx + 8;
        for (int i = 0; i < 3; i++) {
            boolean active = fi[i].equals(lbFmt);
            int tw = Math.round(textRenderer.getWidth(titles[i]) * 0.75f);
            int pw = tw + 20;
            
            if (active) {
                // Purple border pill for active
                c.fill(tx + 2, cy + 42, tx + pw - 2, cy + 43, A_PUR);
                c.fill(tx + 2, cy + 61, tx + pw - 2, cy + 62, A_PUR);
                c.fill(tx, cy + 44, tx + 1, cy + 59, A_PUR);
                c.fill(tx + pw - 1, cy + 44, tx + pw, cy + 59, A_PUR);
                // Corners
                c.fill(tx + 1, cy + 43, tx + 2, cy + 44, A_PUR);
                c.fill(tx + pw - 2, cy + 43, tx + pw - 1, cy + 44, A_PUR);
                c.fill(tx + 1, cy + 59, tx + 2, cy + 60, A_PUR);
                c.fill(tx + pw - 2, cy + 59, tx + pw - 1, cy + 60, A_PUR);
            }
            
            hits.add(new Hit("lb_fmt_" + fi[i], tx, cy + 42, pw, 20));
            tc(c, titles[i], tx + pw / 2, cy + 48, active ? T_W : T_DIM, 0.75f);
            tx += pw + 8;
        }

        int hdrY = cy + 70;
        
        // Table Background
        c.fill(cx + 8, hdrY, cx + CW - 8, cy + H - 20, C_BASE); 
        c.fill(cx + 8, hdrY, cx + CW - 8, hdrY + 1, C_BDR); 
        c.fill(cx + 8, cy + H - 20, cx + CW - 8, cy + H - 19, C_BDR); 
        c.fill(cx + 8, hdrY, cx + 9, cy + H - 20, C_BDR); 
        c.fill(cx + CW - 9, hdrY, cx + CW - 8, cy + H - 20, C_BDR); 

        // Header Background
        c.fill(cx + 9, hdrY + 1, cx + CW - 9, hdrY + 30, C_CARD); 
        c.fill(cx + 9, hdrY + 30, cx + CW - 9, hdrY + 31, C_BDR); 

        t(c, "#", cx + 22, hdrY + 10, T_DIM, 0.70f);
        t(c, "JOGADOR", cx + 70, hdrY + 10, T_DIM, 0.70f);
        
        int pCenter = cx + CW - 110;
        int wrCenter = cx + CW - 45;
        tc(c, "POINTS", pCenter, hdrY + 10, T_DIM, 0.70f);
        tc(c, "WIN RATE", wrCenter, hdrY + 10, T_DIM, 0.70f);

        List<String> entries = ArenaClientState.getLeaderboardEntriesForFormat(lbFmt);
        String me = ArenaClientState.getPlayerName();
        int rowY = hdrY + 31, maxY = cy + H - 24;
        
        int rowH = 34;
        int maxVisible = (maxY - rowY) / rowH;
        int maxScroll = Math.max(0, entries.size() - maxVisible);
        lbScr = net.minecraft.util.math.MathHelper.clamp(lbScr, 0, maxScroll);
        
        if (maxScroll > 0) {
            scbar(c, cx + CW - 6, hdrY + 31, 2, maxY - (hdrY + 31), lbScr, maxScroll);
        }

        for (int i = lbScr; i < entries.size() && i < lbScr + maxVisible; i++) {
            String raw = entries.get(i);
            String[] p = raw.split("\\|");
            String nm = p.length > 0 ? p[0].trim() : raw;
            String els = p.length > 1 ? p[1].replace("Elo", "").trim() : "?";
            String wl = p.length > 2 ? p[2].trim() : "0-0";
            UUID playerUuid = null;
            if (p.length > 3) {
                try {
                    playerUuid = UUID.fromString(p[3].trim());
                } catch (Exception ignored) {}
            }
            int ev = pInt(els, 1000);
            String[] wlp = wl.split("-");
            int wv = pInt(wlp.length > 0 ? wlp[0] : "0", 0);
            int lv = pInt(wlp.length > 1 ? wlp[1] : "0", 0);
            int wr2 = (wv + lv) == 0 ? 0 : Math.round((wv * 100f) / (wv + lv));
            boolean isMe = nm.equals(me);

            int bg = isMe ? pa(100, 155, 81, 224) : (i % 2 == 0 ? pa(50, 26, 22, 36) : pa(0, 0, 0, 0));
            c.fill(cx + 9, rowY, cx + CW - 9, rowY + rowH, bg);
            if (isMe) c.fill(cx + 9, rowY, cx + 11, rowY + rowH, A_PUR);

            t(c, (i + 1) + "", cx + 22, rowY + 12, T_DIM, 0.80f);

            int avSize = 22;
            int avX = cx + 45;
            int avY = rowY + 6;
            c.fill(avX, avY, avX + avSize, avY + avSize, C_BASE);
            drawLeaderboardHead(c, avX + 2, avY + 2, 18, playerUuid, nm);

            tfit(c, nm, cx + 75, rowY + 8, 134, isMe ? T_PUR : T_W, 0.90f);
            String tierLabel = getRankTierName(ev);
            t(c, tierLabel, cx + 75, rowY + 20, T_DIM, 0.65f);

            tc(c, els, pCenter, rowY + 12, T_W, 0.90f);
            tc(c, wr2 + "%", wrCenter, rowY + 12, T_DIM, 0.80f);

            rowY += rowH;
        }
        if (entries.isEmpty()) tc(
            c,
            "Nenhuma partida registrada ainda.",
            cx + CW / 2,
            cy + 130,
            T_DIM,
            0.78f
        );
    }

    private void drawLeaderboardHead(
        DrawContext c,
        int x,
        int y,
        int size,
        UUID uuid,
        String name
    ) {
        net.minecraft.util.Identifier skinId = null;
        net.minecraft.client.MinecraftClient mc =
            net.minecraft.client.MinecraftClient.getInstance();
        if (uuid != null && mc.getNetworkHandler() != null) {
            net.minecraft.client.network.PlayerListEntry entry = mc
                .getNetworkHandler()
                .getPlayerListEntry(uuid);
            if (entry != null) {
                skinId = entry.getSkinTextures().texture();
            }
        }
        if (skinId == null && uuid != null) {
            skinId = net.minecraft.client.util.DefaultSkinHelper.getTexture();
        }
        if (skinId == null) {
            c.fill(x, y, x + size, y + size, pa(180, 80, 58, 120));
            String initial =
                name != null && !name.isBlank()
                    ? name.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                    : "?";
            tc(c, initial, x + size / 2, y + 2, T_W, 0.58f);
            return;
        }

        float s = size / 8f;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0f);
        c.getMatrices().scale(s, s, 1f);
        c.drawTexture(skinId, 0, 0, 8f, 8f, 8, 8, 64, 64);
        c.drawTexture(skinId, 0, 0, 40f, 8f, 8, 8, 64, 64);
        c.getMatrices().pop();
    }

    // =========================================================================
    // PAGE: HISTORY
    // =========================================================================

    private void renderHistory(DrawContext c, int mx, int my, int cx, int cy) {
        historyHoverTooltip = null;
        historyMatchHits.clear();
        
        // Header
        int hX = cx + 8;
        int hY = cy + 10;
        // Clock/Arrow icon
        c.fill(hX, hY+2, hX+10, hY+10, A_PUR);
        c.fill(hX+1, hY+3, hX+9, hY+9, C_PANEL);
        c.fill(hX+4, hY+4, hX+5, hY+6, A_PUR); // clock hand
        c.fill(hX+5, hY+6, hX+7, hY+7, A_PUR);

        t(c, "Histórico de Partidas", cx + 22, cy + 10, T_W, 1.0f);
        t(c, "Suas últimas batalhas", cx + 22, cy + 23, T_DIM, 0.68f);

        // Container
        int conY = cy + 40, conH = H - 50;
        
        // Table Header
        int headY = conY + 2;
        t(c, "PARTIDA / POKÉMON USADOS", cx + 16, headY + 4, T_DIM, 0.60f);
        t(c, "ELO", cx + CW - 40, headY + 4, T_DIM, 0.60f);
        c.fill(cx + 10, headY + 16, cx + CW - 10, headY + 17, C_BDR);

        c.enableScissor(wx(cx + 4), wy(headY + 17), wx(cx + CW - 4), wy(conY + conH - 1));

        List<ArenaMatchHistoryEntryPayload> ent = ArenaClientState.getRecentMatchHistory();
        int rowH = 68;
        int rowGap = 8;
        hScr = net.minecraft.util.math.MathHelper.clamp(hScr, 0, Math.max(0, ent.size() * (rowH + rowGap) - (conH - 18)));
        int ry = headY + 25 - hScr;
        int rowIndex = 0;
        for (ArenaMatchHistoryEntryPayload e : ent) {
            matchRow(c, cx + 10, ry, CW - 20, e, rowIndex++, mx, my);
            ry += rowH + rowGap;
        }
        if (ent.isEmpty()) {
            tc(c, "Sem partidas registradas.", cx + CW / 2, conY + conH / 2, T_DIM, 0.78f);
        }
        c.disableScissor();

        int maxS = Math.max(0, ent.size() * rowH - (conH - 18));
        if (maxS > 0) scbar(c, cx + CW - 6, conY + 18, 4, conH - 19, hScr, maxS);
    }

    private void matchRow(
        DrawContext c,
        int x,
        int y,
        int w,
        ArenaMatchHistoryEntryPayload e,
        int rowIndex,
        int mx,
        int my
    ) {
        boolean win = e.victory();
        int rowBg = C_BASE;
        boolean hov = over(mx, my, x, y, w, 68);
        if (hov) rowBg = pa(255, 30, 26, 40);
        
        c.fill(x + 2, y, x + w, y + 68, rowBg);
        c.fill(x + 2, y, x + w, y + 1, C_BDR);
        c.fill(x + 2, y + 67, x + w, y + 68, C_BDR);
        c.fill(x + w - 1, y, x + w, y + 68, C_BDR);

        // Left color bar
        int barC = win ? A_GREEN : A_RED;
        c.fill(x, y, x + 3, y + 68, barC);

        int textX = x + 12;
        // Title
        String rStr = win ? "Vitória" : "Derrota";
        t(c, rStr, textX, y + 8, barC, 0.85f);
        int rW = Math.round(textRenderer.getWidth(rStr) * 0.85f * 1.25f);
        t(c, "vs", textX + rW + 8, y + 8, A_GOLD, 0.85f);
        int vsW = Math.round(textRenderer.getWidth("vs") * 0.85f * 1.25f);
        t(c, e.opponentName(), textX + rW + 8 + vsW + 8, y + 8, T_W, 0.85f);

        // Subtitle
        String date = DFMT.format(java.time.Instant.ofEpochMilli(e.playedAtMs()).atZone(java.time.ZoneId.systemDefault()));
        String mode = e.ranked() ? "Ranqueada" : "Casual";
        t(c, e.ladderDisplayName() + " - " + mode + " - " + date, textX, y + 22, T_DIM, 0.60f);

        // Pokemon Slots
        int slotY = y + 36;
        for (int i = 0; i < 6; i++) {
            if (i < e.ownTeam().size()) {
                drawSmallSlot(c, textX + i * 26, slotY, e.ownTeam().get(i).speciesKey(), e.ownTeam().get(i).speciesName(), false);
            } else {
                drawSmallSlot(c, textX + i * 26, slotY, null, null, false);
            }
        }

        // ELO Delta
        if (e.ranked()) {
            int d = e.ratingDelta();
            String eloStr = (d >= 0 ? "+" : "") + d;
            int eloC = d > 0 ? A_GREEN : (d < 0 ? A_RED : T_DIM);
            int dW = Math.round(textRenderer.getWidth(eloStr) * 0.90f);
            t(c, eloStr, x + w - dW - 20, y + 28, eloC, 0.90f);
        } else {
            t(c, "-", x + w - 24, y + 28, T_DIM, 0.90f);
        }

        historyMatchHits.add(new Hit("match_" + rowIndex, x, y, w, 68));
    }

    private void drawSmallSlot(DrawContext c, int x, int y, String speciesKey, String name, boolean fainted) {
        int w = 24;
        int h = 24;
        c.fill(x, y, x + w, y + h, C_BASE); // slot bg
        c.fill(x, y, x + w, y + 1, C_BDR);
        c.fill(x, y + h - 1, x + w, y + h, C_BDR);
        c.fill(x, y, x + 1, y + h, C_BDR);
        c.fill(x + w - 1, y, x + w, y + h, C_BDR);

        if (speciesKey != null && !speciesKey.isBlank()) {
            try {
                net.minecraft.util.Identifier specId = net.minecraft.util.Identifier.of(speciesKey);
                com.cobblemon.mod.common.client.render.models.blockbench.FloatingState state =
                    historyPokeStates.computeIfAbsent("sm_" + speciesKey, k -> new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState());
                org.joml.Quaternionf rot = new org.joml.Quaternionf().rotationXYZ(0f, (float) Math.toRadians(20), 0f);
                c.getMatrices().push();
                c.getMatrices().translate(x + 11f, y + 2.5f, 0f);
                c.getMatrices().scale(1.75f, 1.75f, 1f);
                com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt.drawProfilePokemon(
                    specId, c.getMatrices(), rot, com.cobblemon.mod.common.entity.PoseType.PROFILE,
                    state, 0f, 4.5f, true, false, false, 1f, 1f, 1f, fainted ? 0.3f : 1f, 0f, 0f
                );
                c.getMatrices().pop();
            } catch (Exception ignored) {
                tc(c, name.substring(0, 1), x + 11, y + 8, T_W, 0.70f);
            }
        } else if (speciesKey != null && !speciesKey.isBlank()) {
            tc(c, name.substring(0, 1), x + 11, y + 8, T_W, 0.70f);
        } else {
            tc(c, "O", x + 11, y + 8, T_DIM, 0.70f);
        }
        if (fainted) {
            c.fill(x, y, x + w, y + h, pa(120, 255, 0, 0));
        }
    }

    // Cache of FloatingState for Pokemon models in the history/usage tab.
    private final java.util.Map<
        String,
        com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
    > historyPokeStates = new java.util.HashMap<>();
    // Tooltip queued for rendering this frame by pokeRow()
    private java.util.List<net.minecraft.text.Text> historyHoverTooltip = null;
    private int historyHoverTooltipX = 0,
        historyHoverTooltipY = 0;
    private final List<Hit> historyMatchHits = new ArrayList<>();
    private ArenaMatchHistoryEntryPayload selectedMatchDetails = null;

    /** Row height for the Pokemon usage list (must fit the 3-D model cleanly). */
    private static final int POKE_ROW_H = 38;

    private void pokeRow(
        DrawContext c,
        int x,
        int y,
        int w,
        int rank,
        ArenaPokemonUsageEntryPayload e,
        int mx,
        int my
    ) {
        // Row background
        c.fill(
            x,
            y,
            x + w,
            y + POKE_ROW_H,
            rank % 2 == 0 ? pa(180, 20, 16, 34) : pa(110, 16, 12, 28)
        );

        // ── Pokemon model (left side, 28x28) ────────────────────────────────
        String speciesKey = e.speciesKey();
        if (speciesKey != null && !speciesKey.isBlank()) {
            try {
                net.minecraft.util.Identifier specId =
                    net.minecraft.util.Identifier.of(speciesKey);
                com.cobblemon.mod.common.client.render.models.blockbench.FloatingState state =
                    historyPokeStates.computeIfAbsent(speciesKey, k ->
                        new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState()
                    );
                org.joml.Quaternionf rot =
                    new org.joml.Quaternionf().rotationXYZ(
                        0f,
                        (float) Math.toRadians(20),
                        0f
                    );
                c.getMatrices().push();
                // Centre the model in the 38-px row:
                // scale 2.0 → model ~24 px tall → offset (38-24)/2 = 7 px
                c.getMatrices().translate(x + 18f, y + 7f, 0f);
                c.getMatrices().scale(2.0f, 2.0f, 1f);
                com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt.drawProfilePokemon(
                    specId,
                    c.getMatrices(),
                    rot,
                    com.cobblemon.mod.common.entity.PoseType.PROFILE,
                    state,
                    0f,
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
                c.getMatrices().pop();
            } catch (Exception ignored) {
                // fallback: show rank number
                t(
                    c,
                    "#" + rank,
                    x + 6,
                    y + 10,
                    rank <= 3 ? A_GOLD : T_DIM,
                    0.66f
                );
            }
        } else {
            t(c, "#" + rank, x + 6, y + 10, rank <= 3 ? A_GOLD : T_DIM, 0.66f);
        }

        // ── Text columns ─────────────────────────────────────────────────────
        int textX = x + 30;
        tfit(c, e.speciesName(), textX, y + 4, w - 170, T_W, 0.76f);
        t(c, e.uses() + "x", x + w - 116, y + 4, T_DIM, 0.64f);
        t(c, e.wins() + "W " + e.losses() + "L", x + w - 78, y + 4, T_L, 0.64f);
        int wr = e.uses() > 0 ? Math.round((e.wins() * 100f) / e.uses()) : 0;
        t(
            c,
            wr + "%",
            x + w - 26,
            y + 4,
            wr >= 60 ? A_GREEN : wr >= 45 ? A_GOLD : A_RED,
            0.64f
        );

        // Rank badge (below name)
        t(c, "#" + rank, textX, y + 17, rank <= 3 ? A_GOLD : T_MUT, 0.58f);

        // ── Hover tooltip ─────────────────────────────────────────────────
        if (over(mx, my, x, y, w, POKE_ROW_H)) {
            java.util.List<net.minecraft.text.Text> tip =
                new java.util.ArrayList<>();
            tip.add(
                net.minecraft.text.Text.literal(e.speciesName()).formatted(
                    net.minecraft.util.Formatting.WHITE,
                    net.minecraft.util.Formatting.BOLD
                )
            );
            tip.add(
                net.minecraft.text.Text.literal(
                    "Usos: " +
                        e.uses() +
                        "  |  " +
                        e.wins() +
                        "V " +
                        e.losses() +
                        "D"
                ).formatted(net.minecraft.util.Formatting.GRAY)
            );
            tip.add(
                net.minecraft.text.Text.literal(
                    "Taxa de vitoria: " + wr + "%"
                ).formatted(
                    wr >= 60
                        ? net.minecraft.util.Formatting.GREEN
                        : wr >= 45
                            ? net.minecraft.util.Formatting.YELLOW
                            : net.minecraft.util.Formatting.RED
                )
            );
            historyHoverTooltip = tip;
            historyHoverTooltipX = mx;
            historyHoverTooltipY = my;
        }

        c.fill(x, y + POKE_ROW_H - 1, x + w, y + POKE_ROW_H, C_BDR);
    }

    private void drawHistoryDetailsModal(
        DrawContext c,
        int mx,
        int my,
        int cx,
        int cy,
        ArenaMatchHistoryEntryPayload match
    ) {
        int mw = 360;
        int mh = 260;
        int x = cx + (CW - mw) / 2;
        int y = cy + (H - mh) / 2;

        // Fundo do modal
        c.fill(x, y, x + mw, y + mh, C_BASE);
        c.fill(x, y, x + mw, y + 1, C_BDR);
        c.fill(x, y + mh - 1, x + mw, y + mh, C_BDR);
        c.fill(x, y, x + 1, y + mh, C_BDR);
        c.fill(x + mw - 1, y, x + mw, y + mh, C_BDR);
        // Faixa de topo com cor de resultado
        int resultCol = match.victory() ? A_GREEN : A_RED;
        c.fill(x, y, x + mw, y + 3, resultCol);

        // Header
        t(c, "Detalhes da Partida", x + 10, y + 7, T_W, 0.88f);
        int btnX = x + mw - 24;
        int btnY = y + 8;
        int btnS = 12;
        boolean closeHover = over(mx, my, btnX, btnY, btnS, btnS);
        
        int xCenter = btnX + btnS / 2;
        int yCenter = btnY + btnS / 2;
        int crossCol = closeHover ? A_RED : T_DIM;
        for (int i = -3; i <= 3; i++) {
            c.fill(xCenter + i, yCenter + i, xCenter + i + 1, yCenter + i + 1, crossCol);
            c.fill(xCenter + i, yCenter - i, xCenter + i + 1, yCenter - i + 1, crossCol);
            c.fill(xCenter + i + 1, yCenter + i, xCenter + i + 2, yCenter + i + 1, crossCol);
            c.fill(xCenter + i + 1, yCenter - i, xCenter + i + 2, yCenter - i + 1, crossCol);
        }

        // Resultado
        String resultText =
            (match.victory() ? "✓ Vitória" : "✗ Derrota") +
            "  vs  " +
            match.opponentName();
        t(c, resultText, x + 10, y + 19, resultCol, 0.78f);

        // Ladder + modo
        String mode = match.ranked() ? "Ranqueada" : "Casual";
        t(
            c,
            match.ladderDisplayName() + "  ·  " + mode,
            x + 10,
            y + 30,
            T_DIM,
            0.62f
        );

        // ELO delta (ranked)
        if (match.ranked()) {
            int d = match.ratingDelta();
            String eloStr =
                "ELO: " +
                match.ratingAfter() +
                "  (" +
                (d >= 0 ? "+" : "") +
                d +
                ")";
            int eloColor = d >= 0 ? A_GREEN : A_RED;
            t(c, eloStr, x + 10, y + 41, eloColor, 0.62f);
        }

        // Data
        String date = DFMT.format(
            java.time.Instant.ofEpochMilli(match.playedAtMs()).atZone(
                java.time.ZoneId.systemDefault()
            )
        );
        int dateW = Math.round(textRenderer.getWidth(date) * 0.60f);
        t(c, date, x + mw - dateW - 10, y + 30, T_DIM, 0.60f);

        // Divisor
        int divY = y + (match.ranked() ? 54 : 43);
        c.fill(x + 8, divY, x + mw - 8, divY + 1, C_BDR);

        // Times
        int teamsY = divY + 6;
        drawHistoryTeamSection(
            c,
            mx,
            my,
            x + 8,
            teamsY,
            mw - 16,
            "Seu Time",
            match.ownTeam()
        );
        int teamSectionH = 14 + 52 + 4; // label + cards + gap
        drawHistoryTeamSection(
            c,
            mx,
            my,
            x + 8,
            teamsY + teamSectionH + 4,
            mw - 16,
            "Time Oponente",
            match.opponentTeam()
        );
    }

    private void drawHistoryTeamSection(
        DrawContext c,
        int mx,
        int my,
        int x,
        int y,
        int w,
        String title,
        List<cobblemon.arena.network.ArenaTransitionPokemonEntryPayload> team
    ) {
        // Label do time
        t(c, title, x, y + 2, T_DIM, 0.62f);

        int cardSize = 48;
        int cardGap = 4;
        int totalW = 6 * cardSize + 5 * cardGap;
        int startX = x + (w - totalW) / 2;
        int startY = y + 13;

        int max = Math.min(6, team.size());
        for (int i = 0; i < 6; i++) {
            var pk = i < max ? team.get(i) : null;
            int px = startX + i * (cardSize + cardGap);
            boolean hov = over(mx, my, px, startY, cardSize, cardSize);

            // Fundo do card
            int bg = hov ? C_PANEL : pa(200, 16, 12, 22);

            c.fill(px, startY, px + cardSize, startY + cardSize, bg);
            c.fill(px, startY, px + cardSize, startY + 1, C_BDR);
            c.fill(px, startY + cardSize - 1, px + cardSize, startY + cardSize, C_BDR);
            c.fill(px, startY, px + 1, startY + cardSize, C_BDR);
            c.fill(px + cardSize - 1, startY, px + cardSize, startY + cardSize, C_BDR);

            if (pk == null) {
                // Shield/empty icon simple
                c.fill(px + cardSize/2 - 4, startY + cardSize/2 - 5, px + cardSize/2 + 4, startY + cardSize/2 + 3, C_BDR);
                c.fill(px + cardSize/2 - 3, startY + cardSize/2 - 4, px + cardSize/2 + 3, startY + cardSize/2 + 2, bg);
                c.fill(px + cardSize/2 - 2, startY + cardSize/2 + 3, px + cardSize/2 + 2, startY + cardSize/2 + 4, C_BDR);
                c.fill(px + cardSize/2 - 1, startY + cardSize/2 + 4, px + cardSize/2 + 1, startY + cardSize/2 + 5, C_BDR);
                continue;
            }

            // Modelo 3D do Pokémon
            boolean rendered = false;
            String speciesKey = pk.speciesKey();
            if (speciesKey != null && !speciesKey.isBlank()) {
                try {
                    net.minecraft.util.Identifier specId =
                        net.minecraft.util.Identifier.of(speciesKey);
                    com.cobblemon.mod.common.client.render.models.blockbench.FloatingState state =
                        historyPokeStates.computeIfAbsent(
                            "det_" + speciesKey + "_" + i,
                            k ->
                                new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState()
                        );
                    org.joml.Quaternionf rot =
                        new org.joml.Quaternionf().rotationXYZ(
                            (float) Math.toRadians(10),
                            (float) Math.toRadians(25),
                            0f
                        );
                    c.getMatrices().push();
                    c
                        .getMatrices()
                        .translate(px + cardSize / 2f, startY + 6f, 0f);
                    c.getMatrices().scale(2.4f, 2.4f, 1f);
                    com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt.drawProfilePokemon(
                        specId,
                        c.getMatrices(),
                        rot,
                        com.cobblemon.mod.common.entity.PoseType.PROFILE,
                        state,
                        0f,
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
                    c.getMatrices().pop();
                    rendered = true;
                } catch (Exception ignored) {}
            }
            if (!rendered) {
                String spName =
                    pk.speciesName() != null ? pk.speciesName() : "?";
                String ab =
                    spName.length() > 4 ? spName.substring(0, 4) : spName;
                tc(
                    c,
                    ab,
                    px + cardSize / 2,
                    startY + cardSize / 2 - 4,
                    T_W,
                    0.62f
                );
            }

            // Nível no canto inferior esquerdo
            if (pk.level() > 0) {
                String lvStr = "Nv " + pk.level();
                c.getMatrices().push();
                c.getMatrices().translate(px + 2, startY + cardSize - 9, 0f);
                c.getMatrices().scale(0.55f, 0.55f, 1f);
                c.drawText(textRenderer, lvStr, 0, 0, T_DIM, false);
                c.getMatrices().pop();
            }

            // Ícone de item (sem quadrado escuro, renderizado por último)
            if (pk.heldItemName() != null && !pk.heldItemName().isBlank()) {
                try {
                    net.minecraft.util.Identifier iid =
                        cobblemon.arena.client.ArenaBattleTransitionOverlay.resolveItemIdentifier(
                            pk.heldItemName()
                        );
                    if (iid != null) {
                        net.minecraft.item.Item it =
                            net.minecraft.registry.Registries.ITEM.get(iid);
                        if (it != null && it != net.minecraft.item.Items.AIR) {
                            int ix = px + cardSize - 12,
                                iy = startY + cardSize - 12;
                            c.getMatrices().push();
                            c.getMatrices().translate(ix - 2, iy - 2, 300.0);
                            c.getMatrices().scale(0.75f, 0.75f, 0.75f);
                            c.drawItem(
                                new net.minecraft.item.ItemStack(it),
                                0,
                                0
                            );
                            c.getMatrices().pop();
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Tooltip ao hover
            if (hov) {
                List<net.minecraft.text.Text> tip = new java.util.ArrayList<>();
                String spName =
                    pk.speciesName() != null ? pk.speciesName() : "???";
                tip.add(
                    net.minecraft.text.Text.literal(
                        spName + (pk.level() > 0 ? "  Nv." + pk.level() : "")
                    ).formatted(net.minecraft.util.Formatting.WHITE)
                );
                if (!pk.abilityName().isBlank()) tip.add(
                    net.minecraft.text.Text.literal(
                        "Hab: " + pk.abilityName()
                    ).formatted(net.minecraft.util.Formatting.GRAY)
                );
                if (
                    pk.heldItemName() != null && !pk.heldItemName().isBlank()
                ) tip.add(
                    net.minecraft.text.Text.literal(
                        "Item: " +
                            cobblemon.arena.client.ArenaBattleTransitionOverlay.cleanItemDisplayName(
                                pk.heldItemName()
                            )
                    ).formatted(net.minecraft.util.Formatting.GRAY)
                );
                if (!pk.natureName().isBlank()) tip.add(
                    net.minecraft.text.Text.literal(
                        "Nat: " + pk.natureName()
                    ).formatted(net.minecraft.util.Formatting.GRAY)
                );
                if (!pk.moveNames().isEmpty()) {
                    tip.add(
                        net.minecraft.text.Text.literal("Golpes:").formatted(
                            net.minecraft.util.Formatting.DARK_AQUA
                        )
                    );
                    for (String move : pk.moveNames())
                        tip.add(
                            net.minecraft.text.Text.literal(
                                "  " + move
                            ).formatted(net.minecraft.util.Formatting.GRAY)
                        );
                }
                historyHoverTooltip = tip;
                historyHoverTooltipX = mx;
                historyHoverTooltipY = my;
            }
        }
    }

    // =========================================================================
    // PAGE: MISSIONS
    // =========================================================================

    private void renderMissions(DrawContext c, int mx, int my, int cx, int cy) {
        hits.clear();
        
        int cbX = cx + 8;
        int cbY = cy + 10;
        c.fill(cbX, cbY, cbX+10, cbY+10, A_PUR);
        c.fill(cbX+1, cbY+1, cbX+9, cbY+9, C_PANEL); 
        c.fill(cbX+3, cbY+5, cbX+5, cbY+7, A_PUR);
        c.fill(cbX+5, cbY+3, cbX+8, cbY+6, A_PUR);
        
        t(c, "Missões", cx + 22, cy + 9, T_W, 1.05f);
        t(
            c,
            "Complete missões para ganhar recompensas",
            cx + 22,
            cy + 22,
            T_DIM,
            0.66f
        );

        int conY = cy + 34,
            conH = H - 42;
        c.enableScissor(wx(cx + 4), wy(conY), wx(cx + CW - 4), wy(conY + conH));
        c.fill(cx + 4, conY, cx + CW - 4, conY + conH, pa(255, 12, 10, 16));

        List<QuestEntryPayload> daily = ArenaQuestClientState.getDailyQuests();
        List<QuestEntryPayload> weekly = ArenaQuestClientState.getWeeklyQuests();
        
        int estimatedTotH = 24 + daily.size() * 54 + (daily.isEmpty() ? 18 : 0) + 12 + 24 + weekly.size() * 54 + (weekly.isEmpty() ? 18 : 0);
        int maxS = Math.max(0, estimatedTotH - conH);
        qScr = net.minecraft.util.math.MathHelper.clamp(qScr, 0, maxS);
        int ry = conY - qScr + 6;

        ry = qHeader(c, cx, ry, CW, "MISSÕES DIÁRIAS", "Renova 24h", true);
        for (QuestEntryPayload q : daily) {
            mRow(c, cx + 8, ry, CW - 16, q, mx, my);
            ry += 54;
        }
        if (daily.isEmpty()) {
            t(c, "Sem missões diárias.", cx + 26, ry + 4, T_DIM, 0.66f);
            ry += 18;
        }
        ry += 12;

        ry = qHeader(c, cx, ry, CW, "MISSÕES SEMANAIS", "Renova 7d", false);
        for (QuestEntryPayload q : weekly) {
            mRow(c, cx + 8, ry, CW - 16, q, mx, my);
            ry += 54;
        }
        if (weekly.isEmpty()) {
            t(c, "Sem missões semanais.", cx + 26, ry + 4, T_DIM, 0.66f);
            ry += 18;
        }

        c.disableScissor();

        if (maxS > 0) scbar(c, cx + CW - 6, conY, 4, conH, qScr, maxS);
    }

    private int qHeader(
        DrawContext c,
        int cx,
        int y,
        int cw,
        String title,
        String tag,
        boolean isDaily
    ) {
        t(c, title, cx + 12, y + 3, T_DIM, 0.70f);
        
        int tw = Math.round(textRenderer.getWidth(title) * 0.70f * 1.25f) + 24;
        int tbx = cx + 20 + tw;
        int tbw = Math.round(textRenderer.getWidth(tag) * 0.60f * 1.25f) + 12;
        
        // Purple pill
        c.fill(tbx + 2, y + 1, tbx + tbw - 2, y + 2, A_PUR); 
        c.fill(tbx + 2, y + 10, tbx + tbw - 2, y + 11, A_PUR); 
        c.fill(tbx, y + 2, tbx + 1, y + 10, A_PUR); 
        c.fill(tbx + tbw - 1, y + 2, tbx + tbw, y + 10, A_PUR); 
        
        t(c, tag, tbx + 6, y + 3, A_PUR, 0.60f);
        
        return y + 20;
    }

    private void mRow(
        DrawContext c,
        int x,
        int y,
        int w,
        QuestEntryPayload q,
        int mx,
        int my
    ) {
        c.fill(x, y, x + w, y + 46, C_BASE);
        c.fill(x, y, x + w, y + 1, C_BDR);
        c.fill(x, y + 45, x + w, y + 46, C_BDR);
        c.fill(x, y, x + 1, y + 46, C_BDR);
        c.fill(x + w - 1, y, x + w, y + 46, C_BDR);

        int cbX = x + 14;
        int cbY = y + 16;
        int cbC = q.completed() ? A_GREEN : C_BDR2;
        c.fill(cbX, cbY, cbX+12, cbY+12, cbC);
        c.fill(cbX+1, cbY+1, cbX+11, cbY+11, C_BASE);
        if (q.completed()) {
            c.fill(cbX+3, cbY+6, cbX+5, cbY+8, A_GREEN);
            c.fill(cbX+5, cbY+4, cbX+9, cbY+8, A_GREEN);
        }

        int textX = x + 38;

        tfit(c, q.title(), textX, y + 10, w - 180, T_W, 0.75f);
        
        t(
            c,
            q.currentProgress() + "/" + q.targetAmount(),
            textX,
            y + 20,
            T_DIM,
            0.60f
        );

        String rewardText = q.rewardDescription();
        if (!rewardText.isBlank()) {
            int rw = Math.round(textRenderer.getWidth(rewardText) * (0.70f * 1.25f)) + 16;
            int rbx = x + w - rw - 14;
            int rby = y + 15;
            
            c.fill(rbx + 2, rby, rbx + rw - 2, rby + 1, A_GOLD);
            c.fill(rbx + 2, rby + 15, rbx + rw - 2, rby + 16, A_GOLD);
            c.fill(rbx, rby + 2, rbx + 1, rby + 14, A_GOLD);
            c.fill(rbx + rw - 1, rby + 2, rbx + rw, rby + 14, A_GOLD);
            c.fill(rbx + 1, rby + 1, rbx + 2, rby + 2, A_GOLD);
            c.fill(rbx + rw - 2, rby + 1, rbx + rw - 1, rby + 2, A_GOLD);
            c.fill(rbx + 1, rby + 14, rbx + 2, rby + 15, A_GOLD);
            c.fill(rbx + rw - 2, rby + 14, rbx + rw - 1, rby + 15, A_GOLD);
            
            tfit(c, rewardText, rbx + 8, rby + 4, rw - 10, A_GOLD, 0.70f);
        }

        int fl = (int) (w * q.progressFraction());
        if (fl > 0) {
            c.fill(x, y + 44, x + fl, y + 46, q.claimed() ? A_PUR2 : A_PUR);
        }

        if (over(mx, my, x, y, w, 46)) {
            missionHoverTooltip = List.of(
                net.minecraft.text.Text.literal(q.title()).formatted(net.minecraft.util.Formatting.WHITE),
                net.minecraft.text.Text.literal(q.description()).formatted(net.minecraft.util.Formatting.GRAY)
            );
            missionHoverX = mx;
            missionHoverY = my;
        }

        if (q.completed() && !q.claimed()) {
            boolean bHov = over(mx, my, x, y, w, 46);
            if (bHov) {
                c.fill(x, y, x + w, y + 46, pa(40, 255, 255, 255));
            }
            hits.add(new Hit(q.questId(), x, y, w, 46));
        }
    }

    // =========================================================================
    // PAGE: SPECTATE
    // =========================================================================

    private void renderSpectate(DrawContext c, int mx, int my, int cx, int cy) {
        List<cobblemon.arena.network.ActiveBattlePayload> battles = ArenaClientState.getActiveBattlesList();
        int activeCount = battles.size();
        
        // Eye Icon
        int iX = cx + 8;
        int iY = cy + 10;
        c.fill(iX, iY+3, iX+10, iY+7, A_PUR);
        c.fill(iX+3, iY+1, iX+7, iY+9, A_PUR);
        c.fill(iX+4, iY+4, iX+6, iY+6, C_PANEL); // pupil
        
        t(c, "Assistir Batalhas", cx + 22, cy + 9, T_W, 1.05f);
        t(c, activeCount + " batalha(s) ativa(s)", cx + 22, cy + 22, T_DIM, 0.68f);

        // Random spectate pill
        int rw = Math.round(textRenderer.getWidth("ASSISTIR ALEATÓRIO") * 0.75f) + 24;
        int rbx = cx + CW - rw - 8, rby = cy + 12;
        boolean rbh = over(mx, my, rbx, rby, rw, 22);
        
        c.fill(rbx, rby, rbx + rw, rby + 22, rbh ? lt(pa(255, 128, 80, 255), 20) : pa(255, 128, 80, 255));
        c.fill(rbx, rby, rbx + rw, rby + 1, pa(50, 255, 255, 255)); // highlight
        tc(c, "ASSISTIR ALEATÓRIO", rbx + rw/2, rby + 7, T_W, 0.75f);
        hits.add(new Hit("SPECTATE_RANDOM", rbx, rby, rw, 22));

        if (activeCount == 0) {
            tc(c, "Nenhuma batalha ativa no momento.", cx + CW / 2, cy + 130, T_DIM, 0.78f);
            return;
        }

        int ry = cy + 44;
        for (int i = 0; i < Math.min(activeCount, 3) && ry + 100 < cy + H - 20; i++) {
            battleCard(c, cx + 12, ry, CW - 24, 94, battles.get(i), mx, my);
            ry += 104;
        }
    }

    private void battleCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int h,
        cobblemon.arena.network.ActiveBattlePayload b,
        int mx,
        int my
    ) {
        boolean hov = over(mx, my, x, y, w, h);
        int cardBgColor = hov ? pa(255, 22, 18, 30) : pa(255, 18, 14, 26);
        int cardBdrColor = pa(255, 40, 30, 50);
        
        c.fill(x, y, x + w, y + h, cardBgColor);
        c.fill(x, y, x + w, y + 1, cardBdrColor);
        c.fill(x, y, x + 1, y + h, cardBdrColor);
        c.fill(x + w - 1, y, x + w, y + h, dk(cardBdrColor, 30));
        c.fill(x, y + h - 1, x + w, y + h, dk(cardBdrColor, 30));
        
        int pY = y + 16;
        
        // P1
        int p1X = x + 16;
        c.fill(p1X, pY, p1X + 24, pY + 24, pa(255, 30, 24, 40));
        c.fill(p1X, pY, p1X + 24, pY + 1, C_BDR2);
        c.fill(p1X, pY, p1X + 1, pY + 24, C_BDR2);
        c.fill(p1X+23, pY, p1X+24, pY+24, C_BDR2);
        c.fill(p1X, pY+23, p1X+24, pY+24, C_BDR2);
        
        net.minecraft.client.network.PlayerListEntry entry1 = client.getNetworkHandler() != null ? client.getNetworkHandler().getPlayerListEntry(b.player1Name()) : null;
        net.minecraft.util.Identifier skin1 = entry1 != null ? entry1.getSkinTextures().texture() : net.minecraft.client.util.DefaultSkinHelper.getTexture();
        net.minecraft.client.gui.PlayerSkinDrawer.draw(c, skin1, p1X + 4, pY + 4, 16);
        
        t(c, b.player1Name(), p1X + 32, pY + 6, T_W, 0.90f);
        
        int hotbarY = pY + 30;
        for (int i = 0; i < 6; i++) {
            if (i < b.player1Team().size()) {
                drawSmallSlot(c, p1X + (i * 26), hotbarY, b.player1Team().get(i).speciesKey(), b.player1Team().get(i).speciesKey(), b.player1Team().get(i).fainted());
            } else {
                drawSmallSlot(c, p1X + (i * 26), hotbarY, null, null, false);
            }
        }

        // P2
        int p2X = x + w - 40;
        c.fill(p2X, pY, p2X + 24, pY + 24, pa(255, 30, 24, 40));
        c.fill(p2X, pY, p2X + 24, pY + 1, C_BDR2);
        c.fill(p2X, pY, p2X + 1, pY + 24, C_BDR2);
        c.fill(p2X+23, pY, p2X+24, pY+24, C_BDR2);
        c.fill(p2X, pY+23, p2X+24, pY+24, C_BDR2);
        
        net.minecraft.client.network.PlayerListEntry entry2 = client.getNetworkHandler() != null ? client.getNetworkHandler().getPlayerListEntry(b.player2Name()) : null;
        net.minecraft.util.Identifier skin2 = entry2 != null ? entry2.getSkinTextures().texture() : net.minecraft.client.util.DefaultSkinHelper.getTexture();
        net.minecraft.client.gui.PlayerSkinDrawer.draw(c, skin2, p2X + 4, pY + 4, 16);
        
        int name2W = Math.round(client.textRenderer.getWidth(b.player2Name()) * 0.90f);
        t(c, b.player2Name(), p2X - name2W - 8, pY + 6, T_W, 0.90f);
        
        int startP2X = x + w - 16 - (6 * 26);
        for (int i = 0; i < 6; i++) {
            if (i < b.player2Team().size()) {
                drawSmallSlot(c, startP2X + (i * 26), hotbarY, b.player2Team().get(i).speciesKey(), b.player2Team().get(i).speciesKey(), b.player2Team().get(i).fainted());
            } else {
                drawSmallSlot(c, startP2X + (i * 26), hotbarY, null, null, false);
            }
        }

        // Center
        int centerX = x + w / 2;
        tc(c, "TURNO " + b.turn(), centerX, pY - 2, T_DIM, 0.70f);
        
        long elapsedSec = 0;
        if (b.battleStartTimeMs() > 0) {
            elapsedSec = Math.max(0, System.currentTimeMillis() - b.battleStartTimeMs()) / 1000;
        }
        String timeStr = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60);
        tc(c, timeStr, centerX, pY + 8, T_DIM, 0.70f);
        
        tc(c, "VS", centerX, pY + 22, T_W, 1.4f);
        
        int btnW = 70;
        int btnH = 18;
        int btnX = centerX - btnW / 2;
        int btnY = pY + 42;
        boolean bh = over(mx, my, btnX, btnY, btnW, btnH);
        
        c.fill(btnX, btnY, btnX + btnW, btnY + btnH, bh ? lt(pa(255, 160, 60, 255), 20) : pa(255, 160, 60, 255));
        c.fill(btnX, btnY, btnX + btnW, btnY + 1, pa(50, 255, 255, 255));
        tc(c, "ASSISTIR", centerX, btnY + 5, T_W, 0.80f);
        hits.add(new Hit("SPECTATE_" + b.sessionId().toString(), btnX, btnY, btnW, btnH));
        
        // Mode Subtitle
        c.fill(x, y + h - 18, x + w, y + h - 17, C_BDR);
        t(c, b.formatName() + (b.isRanked() ? " - Ranqueada" : " - Casual"), x + 16, y + h - 12, T_DIM, 0.60f);
    }

    private void drawSpectatePokemonSlot(DrawContext graphics, cobblemon.arena.network.ActiveBattlePokemonPayload pokemon, int cx, int cy) {
        int slotBg = pokemon.fainted() ? pa(255, 58, 22, 30) : pa(255, 74, 42, 48);
        int borderColor = pokemon.fainted() ? pa(255, 190, 72, 82) : pa(255, 132, 54, 62);
        
        int w = 26;
        int h = 18;
        
        // Inner fill
        graphics.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, slotBg);
        graphics.fill(cx + 1, cy + h - 3, cx + w - 1, cy + h - 1, dk(slotBg, 20));
        
        // Borders
        graphics.fill(cx, cy, cx + w, cy + 1, borderColor);
        graphics.fill(cx, cy + h - 1, cx + w, cy + h, dk(borderColor, 15));
        graphics.fill(cx, cy, cx + 1, cy + h, borderColor);
        graphics.fill(cx + w - 1, cy, cx + w, cy + h, dk(borderColor, 15));

        com.cobblemon.mod.common.client.render.models.blockbench.FloatingState state = new com.cobblemon.mod.common.client.render.models.blockbench.FloatingState();
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(13.0), (float) Math.toRadians(35.0), 0.0F);

        graphics.getMatrices().push();
        graphics.getMatrices().translate(cx + (w / 2.0F), cy + (h / 2.0F) + 2.0F, 0.0F);
        graphics.getMatrices().scale(1.15F, 1.15F, 1.0F);

        com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt.drawProfilePokemon(
            net.minecraft.util.Identifier.of(pokemon.speciesKey()),
            graphics.getMatrices(),
            rotation,
            com.cobblemon.mod.common.entity.PoseType.PROFILE,
            state,
            client.getRenderTickCounter().getTickDelta(true),
            3.5F,
            true,
            false,
            false,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            0.0F,
            0.0F
        );
        graphics.getMatrices().pop();
        
        // Item Indicator
        if (pokemon.heldItem() != null && !pokemon.heldItem().isEmpty()) {
            net.minecraft.item.ItemStack itemStack = new net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(pokemon.heldItem())));
            if (!itemStack.isEmpty()) {
                graphics.getMatrices().push();
                graphics.getMatrices().translate(cx + w - 8.0F, cy + h - 8.0F, 0.0F);
                graphics.getMatrices().scale(0.40F, 0.40F, 1.0F);
                graphics.drawItem(itemStack, 0, 0);
                graphics.getMatrices().pop();
            }
        }
        
        if (pokemon.fainted()) {
            graphics.fill(cx, cy, cx + w, cy + h, pa(120, 0, 0, 0));
        }
    }

    private void drawEmptySpectateSlot(DrawContext graphics, int cx, int cy) {
        int slotBg = pa(255, 44, 22, 26);
        int borderColor = pa(255, 132, 54, 62);
        int w = 26;
        int h = 18;
        graphics.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, slotBg);
        graphics.fill(cx + 1, cy + h - 3, cx + w - 1, cy + h - 1, dk(slotBg, 20));
        
        graphics.fill(cx, cy, cx + w, cy + 1, borderColor);
        graphics.fill(cx, cy + h - 1, cx + w, cy + h, dk(borderColor, 15));
        graphics.fill(cx, cy, cx + 1, cy + h, borderColor);
        graphics.fill(cx + w - 1, cy, cx + w, cy + h, dk(borderColor, 15));
    }


    // =========================================================================
    // Mouse & scroll
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int gx = guiX((int) mx),
            gy = guiY((int) my);

        if (btn == 0) {
            // ── Universal Sidebar & Hits ─────────────────────────────────────────
            for (Hit h : hits) {
                if (h.id.startsWith("page_") && over(gx, gy, h.x, h.y, h.w, h.h)) {
                    String pageStr = h.id.replace("page_", "");
                    nav(Page.valueOf(pageStr));
                    return true;
                }
            }

            // ── Play page ────────────────────────────────────────────────────
            if (page == Page.PLAY) {
                int c2x = gL + SB + C2X;
                int colTop = gT;
                boolean inQueue = queueInRanked || queueInCasual;
                
                // Chat toggle button
                int chatOffset = (int) (-C1W * (1.0f - chatOpenProgress));
                int currentC1X = gL + SB + C1X + chatOffset;
                
                if (!chatOpen) {
                    int toggleW = 14;
                    int toggleH = 40;
                    int toggleX = currentC1X + C1W;
                    int toggleY = colTop + (CHAT_H - toggleH) / 2;
                    if (over(gx, gy, toggleX, toggleY, toggleW, toggleH)) {
                        chatOpen = true;
                        globalChatOpen = true;
                        return true;
                    }
                } else {
                    if (over(gx, gy, currentC1X + C1W - 16, colTop + 2, 12, 12)) {
                        chatOpen = false;
                        globalChatOpen = false;
                        return true;
                    }
                }
                
                // "Ver todas >" missions link (Col3 header)
                int c3_x = gL + SB + C3X;
                if (over(gx, gy, c3_x + C3W - 52, colTop + 6, 52, 10)) {
                    nav(Page.MISSIONS);
                    return true;
                }
            }

            if (page == Page.SPECTATE) {
                int cx = gL + SB;
                if (over(gx, gy, cx + CW - 120, gT + 12, 116, 16)) {
                    ClientPlayNetworking.send(new SpectateArenaBattlePacket(java.util.Optional.empty()));
                    this.close();
                    return true;
                }
                for (Hit h : hits) {
                    if (h.id.startsWith("SPECTATE_") && over(gx, gy, h.x, h.y, h.w, h.h)) {
                        String sessionIdStr = h.id.replace("SPECTATE_", "");
                        ClientPlayNetworking.send(new SpectateArenaBattlePacket(java.util.Optional.of(UUID.fromString(sessionIdStr))));
                        this.close();
                        return true;
                    }
                }
            }

            if (page == Page.LEADERBOARD) {
                for (Hit h : hits) {
                    if (h.id.startsWith("lb_fmt_") && over(gx, gy, h.x, h.y, h.w, h.h)) {
                        lbFmt = h.id.replace("lb_fmt_", "");
                        lbScr = 0;
                        return true;
                    }
                }
            }

            // ── History: open/close match details ───────────────────────────
            if (page == Page.HISTORY && hMatches) {
                if (selectedMatchDetails != null) {
                    int cx = gL + SB;
                    int cy = gT;
                    int mw = 360;
                    int mh = 260;
                    int mx0 = cx + (CW - mw) / 2;
                    int my0 = cy + (H - mh) / 2;
                    
                    // Click on the 'X' button
                    if (over(gx, gy, mx0 + mw - 24, my0 + 8, 12, 12)) {
                        selectedMatchDetails = null;
                        return true;
                    }
                    // Click outside the modal
                    if (!over(gx, gy, mx0, my0, mw, mh)) {
                        selectedMatchDetails = null;
                        return true;
                    }
                    // Click inside the modal (consume click to prevent interacting with background)
                    return true;
                } else {
                    List<ArenaMatchHistoryEntryPayload> history =
                        ArenaClientState.getRecentMatchHistory();
                    for (Hit h : historyMatchHits) {
                        if (over(gx, gy, h.x, h.y, h.w, h.h)) {
                            int idx = pInt(h.id.replace("match_", ""), -1);
                            if (idx >= 0 && idx < history.size()) {
                                selectedMatchDetails = history.get(idx);
                                return true;
                            }
                        }
                    }
                }
            }

            // ── Chat panel click logic ──────────────────────────────────────
            if (page == Page.PLAY) {
                int colTop = gT;
                boolean inChatPanel = gx >= gL + SB + 2 && gx < gL + SB + 2 + C1W && gy >= colTop && gy < colTop + CHAT_H;
                if (chatInput != null) {
                    if (inChatPanel) {
                        chatInput.setVisible(true);
                        chatInput.setFocused(true);
                    } else {
                        chatInput.setFocused(false);
                        if (chatInput.getText().isEmpty()) {
                            chatInput.setVisible(false);
                        }
                    }
                }
            }

            // ── Missions: claim buttons ────────────────────────────────────
            if (page == Page.MISSIONS) for (Hit h : hits)
                if (over(gx, gy, h.x, h.y, h.w, h.h)) {
                    ClientPlayNetworking.send(new ClaimQuestRewardPacket(h.id));
                    return true;
                }
        }

        // Delegate to widgets — they are positioned at visual (screen) coordinates,
        // so we pass mx/my directly (rawX(guiX(mx)) == mx anyway).
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int gx = guiX((int) mx),
            gy = guiY((int) my);
        int scrollDir = (int) Math.signum(v); // +1 = scroll up (show older), -1 = scroll down

        // Scroll chat panel (Col1 below status, Play page)
        if (
            page == Page.PLAY &&
            gx >= gL + SB + 2 &&
            gx < gL + SB + 2 + C1W &&
            gy >= gT &&
            gy < gT + CHAT_H
        ) {
            // Use chatWrappedLineCount (set by drawChatPanel each frame) so the
            // scroll limit accounts for word-wrapped multi-line messages.
            int visLines = Math.max(1, (CHAT_H - CHAT_INPUT_H - 20) / 9);
            int maxScroll = Math.max(0, chatWrappedLineCount - visLines);
            chatScroll = Math.max(
                0,
                Math.min(maxScroll, chatScroll - scrollDir)
            );
            return true;
        }

        if (gx >= gL + SB) {
            int d = (int) (-Math.signum(v) * 14);
            switch (page) {
                case HISTORY -> hScr = Math.max(0, hScr + d);
                case MISSIONS -> qScr = Math.max(0, qScr + d);
                case PROFILE -> pScr = Math.max(0, pScr + d);
                case LEADERBOARD -> lbScr = Math.max(0, lbScr - scrollDir * 3);
                default -> {
                }
            }
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        return super.mouseReleased(mx, my, btn);
    }

    // =========================================================================
    // Drawing primitives
    // =========================================================================

    // =========================================================================
    // Player head renderer
    // =========================================================================

    /**
     * Renders the local player's skin head (face + hat layers) at (x, y)
     * scaled to {@code size} x {@code size} pixels.
     * Falls back to a letter avatar if the skin texture is not ready yet.
     */
    // =========================================================================
    // Party validation (client-side pre-check)
    // =========================================================================

    /**
     * Quick client-side party validation based on the cached party preview.
     * Returns {@code null} if the party looks valid, or a short error message.
     * The server ALSO validates — this check just gives immediate UI feedback.
     */
    private String validatePartyForQueue(boolean isMonotype) {
        java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> party =
            ArenaClientState.getPartyPreview();

        // 1. Need 6 Pokémon
        long filled = party.stream().filter(p -> p != null).count();
        if (filled < 6) {
            return "Precisa de 6 Pokemon no time (" + filled + "/6)";
        }

        // 2. Monotype se ativo
        if (isMonotype) {
            String commonType = validateMonotypeTeam();
            if (commonType == null) {
                return "Monotype: todos devem ter um tipo em comum";
            }
        }

        // 3. No duplicate species
        java.util.Set<String> seenSpecies = new java.util.HashSet<>();
        for (com.cobblemon.mod.common.pokemon.Pokemon p : party) {
            if (p == null) continue;
            try {
                String speciesId = p.getSpecies().getResourceIdentifier().toString().toLowerCase(java.util.Locale.ROOT);
                if (!seenSpecies.add(speciesId)) {
                    return "Pokemon repetido: " + p.getSpecies().getName();
                }
            } catch (Exception ignored) {}
        }

        // 4. No duplicate held items
        java.util.Set<String> seenItems = new java.util.HashSet<>();
        for (com.cobblemon.mod.common.pokemon.Pokemon p : party) {
            if (p == null) continue;
            try {
                net.minecraft.item.ItemStack held = p.heldItem();
                if (held != null && !held.isEmpty()) {
                    net.minecraft.util.Identifier itemIdObj = net.minecraft.registry.Registries.ITEM.getId(held.getItem());
                    String itemId = itemIdObj != null ? itemIdObj.toString() : held.getItem().toString();
                    if (!seenItems.add(itemId)) {
                        return "Item repetido: " + held.getName().getString();
                    }
                }
            } catch (Exception ignored) {}
        }

        // 5. At least one healthy Pokémon
        boolean hasHealthy = party.stream().anyMatch(p -> p != null && !p.isFainted());
        if (!hasHealthy) {
            return "Todos os Pokemon estao debilitados.";
        }

        return null; // party is valid
    }

    private void drawValidationToast(
        DrawContext c,
        int x,
        int y,
        int w,
        String message,
        long shownAtMs
    ) {
        if (message == null || message.isBlank() || shownAtMs <= 0L) return;
        long elapsed = System.currentTimeMillis() - shownAtMs;
        if (elapsed > VALIDATION_ERROR_TIMEOUT_MS) return;
        float t = elapsed / (float) VALIDATION_ERROR_TIMEOUT_MS;
        float alpha = t < 0.7f ? 1.0f : Math.max(0.0f, (1.0f - t) / 0.3f);
        int a = Math.max(45, Math.min(170, (int) (170 * alpha)));
        c.fill(x, y, x + w, y + 15, pa(a, 208, 70, 78));
        c.fill(x, y, x + w, y + 1, pa(Math.min(255, a + 40), 255, 180, 180));
        float f = 0.58f * 1.25f; // TEXT_MULT = 1.25f
        int txtW = Math.round(textRenderer.getWidth(message) * f);
        if (txtW <= w - 8) {
            tc(c, message, x + w / 2, y + 5, T_W, 0.58f);
        } else {
            tfit(c, message, x + 4, y + 5, w - 8, T_W, 0.58f);
        }
    }

    private void showQueueValidationError(String message) {
        String cleaned = message == null ? "" : message.trim();
        if (cleaned.isBlank()) return;
        if (playTabRanked) {
            rankedValidationError = cleaned;
            rankedValidationErrorMs = System.currentTimeMillis();
        } else {
            casualValidationError = cleaned;
            casualValidationErrorMs = System.currentTimeMillis();
        }
    }

    /**
     * Additional validation for Monotype casual mode:
     * all 6 Pokémon must share at least ONE common type.
     * Returns the common type key if valid, or {@code null} if the team is
     * not monotype.
     */
    private String validateMonotypeTeam() {
        java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> party =
            ArenaClientState.getPartyPreview();
        // Collect types shared by ALL non-null Pokemon
        java.util.Set<String> commonTypes = null;
        for (com.cobblemon.mod.common.pokemon.Pokemon p : party) {
            if (p == null) continue;
            try {
                java.util.Set<String> pokeTypes = new java.util.HashSet<>();
                var primary = p.getSpecies().getPrimaryType();
                if (primary != null) pokeTypes.add(
                    primary.getName().toLowerCase(java.util.Locale.ROOT)
                );
                var secondary = p.getSpecies().getSecondaryType();
                if (secondary != null) pokeTypes.add(
                    secondary.getName().toLowerCase(java.util.Locale.ROOT)
                );
                if (commonTypes == null) {
                    commonTypes = new java.util.HashSet<>(pokeTypes);
                } else {
                    commonTypes.retainAll(pokeTypes);
                }
            } catch (Exception ignored) {}
        }
        if (commonTypes == null || commonTypes.isEmpty()) return null;
        return commonTypes.iterator().next(); // e.g. "fire"
    }

    private void drawPlayerHead(DrawContext c, int x, int y, int size) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        java.util.UUID uuid = mc.player != null ? mc.player.getUuid() : null;
        String name = mc.player != null ? mc.player.getName().getString() : "?";
        drawLeaderboardHead(c, x, y, size, uuid, name);
    }

    // =========================================================================
    // ELO tier tooltip
    // =========================================================================

    private void drawEloTierTooltip(DrawContext c, int px, int py) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 1000);

        // Tier thresholds matching ArenaClientState.getRankTitle()
        String[][] rows = {
            { "INICIANTE", "< 1000" },
            { "BRONZE", "1000-1399" },
            { "PRATA", "1400-1599" },
            { "OURO", "1600-1799" },
            { "PLATINA", "1800-1999" },
            { "DIAMANTE", "2000-2199" },
            { "MESTRE", "2200-2399" },
            { "GRAO-MESTRE", "2400+" },
        };
        int[] rowColors = {
            T_DIM,
            pa(
                255,
                (TIER_BRZ >> 16) & 0xFF,
                (TIER_BRZ >> 8) & 0xFF,
                TIER_BRZ & 0xFF
            ),
            pa(
                255,
                (TIER_SLV >> 16) & 0xFF,
                (TIER_SLV >> 8) & 0xFF,
                TIER_SLV & 0xFF
            ),
            pa(
                255,
                (TIER_GLD >> 16) & 0xFF,
                (TIER_GLD >> 8) & 0xFF,
                TIER_GLD & 0xFF
            ),
            pa(
                255,
                (TIER_PLT >> 16) & 0xFF,
                (TIER_PLT >> 8) & 0xFF,
                TIER_PLT & 0xFF
            ),
            pa(
                255,
                (TIER_DIA >> 16) & 0xFF,
                (TIER_DIA >> 8) & 0xFF,
                TIER_DIA & 0xFF
            ),
            pa(
                255,
                (TIER_MST >> 16) & 0xFF,
                (TIER_MST >> 8) & 0xFF,
                TIER_MST & 0xFF
            ),
            pa(
                255,
                (TIER_GM >> 16) & 0xFF,
                (TIER_GM >> 8) & 0xFF,
                TIER_GM & 0xFF
            ),
        };

        int tw = 124,
            rowH = 11,
            padV = 5,
            padH = 6,
            hdrH = 14;
        int th = hdrH + rows.length * rowH + padV * 2;

        // Anchor below-left of the hovered element; clamp to panel bounds
        int tx = Math.min(px, gL + W - tw - 4);
        int ty = Math.max(gT + 4, py - th - 4);

        // Panel background
        c.fill(tx, ty, tx + tw, ty + th, pa(242, 10, 8, 20));
        c.fill(tx, ty, tx + tw, ty + 1, C_BDR2);
        c.fill(tx, ty, tx + 1, ty + th, C_BDR2);
        c.fill(tx + tw - 1, ty, tx + tw, ty + th, C_BDR);
        c.fill(tx, ty + th - 1, tx + tw, ty + th, C_BDR);

        // Header
        t(c, "TABELA DE TIERS", tx + padH, ty + padV, T_DIM, 0.60f);
        c.fill(tx + 3, ty + hdrH - 1, tx + tw - 3, ty + hdrH, C_BDR);

        // Rows
        int curElo = ArenaClientState.getRankedRating();
        String curTier = shortTier(ArenaClientState.getRankTitle());
        for (int i = 0; i < rows.length; i++) {
            int ry2 = ty + hdrH + padV + i * rowH;
            boolean isCurrentTier =
                rows[i][0].equalsIgnoreCase(curTier) ||
                (i == 0 && curElo < 1000);
            int txtCol = isCurrentTier ? T_W : rowColors[i];
            // Highlight current tier
            if (isCurrentTier) {
                c.fill(
                    tx + 2,
                    ry2,
                    tx + tw - 2,
                    ry2 + rowH,
                    pa(35, 108, 62, 230)
                );
            }
            t(c, rows[i][0], tx + padH, ry2 + 2, txtCol, 0.60f);
            t(
                c,
                rows[i][1],
                tx + 72,
                ry2 + 2,
                isCurrentTier ? T_L : T_DIM,
                0.60f
            );
        }

        c.getMatrices().pop();
    }

    /** Filled card with subtle inner border. */
    private void card(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, C_CARD);
        c.fill(x, y, x + w, y + 1, C_BDR2);
        c.fill(x, y, x + 1, y + h, C_BDR2);
        c.fill(x + w - 1, y, x + w, y + h, C_BDR);
        c.fill(x, y + h - 1, x + w, y + h, C_BDR);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, pa(5, 255, 255, 255));
    }

    /** Small 2-line chip used on the player strip. */
    private void smallChip(
        DrawContext c,
        int x,
        int y,
        int w,
        String lbl,
        String val,
        int col
    ) {
        c.fill(x, y, x + w, y + 28, C_CARD2);
        c.fill(x, y, x + w, y + 1, C_BDR2);
        c.fill(x, y, x + 1, y + 28, C_BDR2);
        t(c, lbl, x + 5, y + 4, T_DIM, 0.58f);
        t(c, val, x + 5, y + 14, col, 0.84f);
    }

    /** Stat card for the profile page. */
    private void statCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int h,
        String lbl,
        String val,
        int vc
    ) {
        c.fill(x, y, x + w, y + h, C_CARD);
        c.fill(x, y, x + w, y + 1, C_BDR2);
        c.fill(x, y, x + 1, y + h, C_BDR2);
        c.fill(x + w - 1, y, x + w, y + h, C_BDR);
        c.fill(x, y + h - 1, x + w, y + h, C_BDR);
        t(c, lbl, x + 6, y + 6, T_DIM, 0.60f);
        t(c, val, x + 6, y + 20, vc, 0.88f);
    }

    /** Three-column inline stat row (for ranked card). */
    private void stat3(
        DrawContext c,
        int x,
        int y,
        int w,
        String[] lbls,
        String[] vals,
        int[] cols
    ) {
        int cw = w / 3;
        for (int i = 0; i < 3; i++) {
            int sx = x + i * cw;
            t(c, lbls[i], sx, y, T_DIM, 0.58f);
            t(c, vals[i], sx, y + 10, cols[i], 0.80f);
        }
    }

    // =========================================================================
    // Procedural Rank Badges
    // =========================================================================
    
    private void fillCircle(DrawContext c, int cx, int cy, float r, int color) {
        int radius = Math.round(r);
        for (int y = -radius; y <= radius; y++) {
            int dx = (int) Math.round(Math.sqrt(r * r - y * y));
            c.fill(cx - dx, cy + y, cx + dx + 1, cy + y + 1, color);
        }
    }

    private void drawWedge(DrawContext c, int cx, int cy, float size, int color) {
        int sz = Math.round(size);
        for (int y = -sz; y <= sz; y++) {
            int w = sz - Math.abs(y);
            c.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, color);
        }
    }

    private void drawDownwardArrow(DrawContext c, int cx, int cy, float size, int color) {
        int sz = Math.round(size);
        c.fill(cx - sz/4, cy - sz, cx + sz/4 + 1, cy, color);
        for (int y = 0; y <= sz; y++) {
            int w = sz - y;
            c.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, color);
        }
    }

    private void drawRankBadge(DrawContext c, int cx, int cy, float r, String tierName) {
        tierName = tierName.toLowerCase(java.util.Locale.ROOT);
        String pokeball = "poke_ball";
        if (tierName.contains("bronze")) pokeball = "premier_ball";
        else if (tierName.contains("prata")) pokeball = "great_ball";
        else if (tierName.contains("ouro")) pokeball = "ultra_ball";
        else if (tierName.contains("platina")) pokeball = "quick_ball";
        else if (tierName.contains("diamante")) pokeball = "luxury_ball";
        else if (tierName.contains("mestre") && !tierName.contains("grao") && !tierName.contains("grão")) pokeball = "cherish_ball";
        else if (tierName.contains("grão") || tierName.contains("grao")) pokeball = "master_ball";
        
        net.minecraft.util.Identifier itemId = net.minecraft.util.Identifier.of("cobblemon", pokeball);
        net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(itemId);
        
        if (item != null && item != net.minecraft.item.Items.AIR) {
            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item);
            float targetSize = r * 2.5f;
            float scale = targetSize / 16.0f;
            c.getMatrices().push();
            c.getMatrices().translate(cx - targetSize / 2f, cy - targetSize / 2f, 0);
            c.getMatrices().scale(scale, scale, 1.0f);
            c.drawItem(stack, 0, 0);
            c.getMatrices().pop();
        } else {
            fillCircle(c, cx, cy, r, p(100, 100, 100));
        }
    }

    private void drawTierTableTooltip(DrawContext c, int x, int y, String fid) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 1000); // ensure above everything
        
        String[] tiers = {"Iniciante", "Bronze", "Prata", "Ouro", "Platina", "Diamante", "Mestre", "Grão-Mestre"};
        int[] elos = {0, 400, 800, 1200, 1600, 2000, 2400, 2800};
        
        int boxW = 140;
        int boxH = 26 + (tiers.length * 20);
        
        // Removed internal clamp because it clashes with gS virtual coordinates

        
        c.fill(x, y, x + boxW, y + boxH, C_CARD);
        c.fill(x, y, x + boxW, y + 1, C_BDR2);
        c.fill(x, y, x + 1, y + boxH, C_BDR2);
        c.fill(x + boxW - 1, y, x + boxW, y + boxH, C_BDR);
        c.fill(x, y + boxH - 1, x + boxW, y + boxH, C_BDR);
        
        t(c, "PROGRESSAO DE ELO", x + 10, y + 8, T_DIM, 0.64f);
        c.fill(x + 10, y + 18, x + boxW - 10, y + 19, C_BDR);
        
        int currentElo = eloForFmt(fid);
        
        int ry = y + 24;
        for (int i = 0; i < tiers.length; i++) {
            boolean isCurrent = currentElo >= elos[i] && (i == tiers.length - 1 || currentElo < elos[i+1]);
            
            if (isCurrent) {
                c.fill(x + 4, ry - 2, x + boxW - 4, ry + 16, pa(40, 108, 62, 230));
                c.fill(x + 4, ry - 2, x + 5, ry + 16, A_PUR);
            }
            
            drawRankBadge(c, x + 18, ry + 6, 6f, tiers[i]);
            t(c, tiers[i], x + 32, ry + 4, isCurrent ? T_W : T_L, 0.76f);
            t(c, elos[i] + "+", x + boxW - 35, ry + 4, isCurrent ? A_GREEN : T_DIM, 0.70f);
            
            ry += 20;
        }
        
        c.getMatrices().pop();
    }



    /** Coloured pill badge (tier display) - Now using Procedural Badge. */
    private void tierPill(DrawContext c, int x, int y, String label, int bg) {
        int r = 6;
        drawRankBadge(c, x + r, y + r, r, label);
        int textColor = label.toLowerCase(java.util.Locale.ROOT).contains("iniciante") ? T_DIM : bg;
        t(c, label, x + r * 2 + 6, y + 2, textColor, 0.66f);
    }

    private int pillW(String label) {
        return 12 + 6 + Math.round(textRenderer.getWidth(label) * 0.66f);
    }

    /** Vertical scrollbar. */
    private void scbar(
        DrawContext c,
        int x,
        int y,
        int w,
        int h,
        int scroll,
        int maxS
    ) {
        c.fill(x, y, x + w, y + h, C_BDR);
        int thH = Math.max(12, (h * h) / (h + maxS));
        int thY = y + (int) (((float) scroll / maxS) * (h - thH));
        c.fill(x, thY, x + w, thY + thH, A_PUR2);
        c.fill(x, thY, x + w, thY + 1, pa(40, 255, 255, 255));
    }

    // =========================================================================
    // Text helpers
    // =========================================================================

    private static final float TEXT_MULT = 1.25f;

    private void t(DrawContext c, String text, int x, int y, int col, float s) {
        float f = s * TEXT_MULT;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0f);
        c.getMatrices().scale(f, f, 1f);
        c.drawText(textRenderer, text, 0, 0, col, false);
        c.getMatrices().pop();
    }

    private void tc(
        DrawContext c,
        String text,
        int cx,
        int y,
        int col,
        float s
    ) {
        float f = s * TEXT_MULT;
        int w = Math.round(textRenderer.getWidth(text) * f);
        c.getMatrices().push();
        c.getMatrices().translate(cx - w / 2, y, 0f);
        c.getMatrices().scale(f, f, 1f);
        c.drawText(textRenderer, text, 0, 0, col, false);
        c.getMatrices().pop();
    }

    private void lnk(
        DrawContext c,
        String text,
        int x,
        int y,
        int col,
        float s
    ) {
        t(c, text, x, y, col, s);
    }

    private void tfit(
        DrawContext c,
        String text,
        int x,
        int y,
        int maxW,
        int col,
        float s
    ) {
        if (text == null || text.isBlank()) return;
        float f = s * TEXT_MULT;
        if (Math.round(textRenderer.getWidth(text) * f) <= maxW) {
            c.getMatrices().push();
            c.getMatrices().translate(x, y, 0f);
            c.getMatrices().scale(f, f, 1f);
            c.drawText(textRenderer, text, 0, 0, col, false);
            c.getMatrices().pop();
            return;
        }
        String sfx = "...";
        for (int e = text.length() - 1; e > 0; e--) {
            String cd = text.substring(0, e).trim() + sfx;
            if (Math.round(textRenderer.getWidth(cd) * f) <= maxW) {
                t(c, cd, x, y, col, s);
                return;
            }
        }
        t(c, sfx, x, y, col, s);
    }

    // =========================================================================
    // Scale / coordinate helpers
    // =========================================================================

    private void pushScale(DrawContext ctx) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(gS, gS, 1f);
    }

    // Removed duplicates from here

    /** Screen → GUI (logical) coordinate. */
    private int guiX(int sx) {
        return gS >= 1f ? sx : (int) (sx / gS);
    }

    private int guiY(int sy) {
        return gS >= 1f ? sy : (int) (sy / gS);
    }

    /** GUI → screen coordinate. */
    private double rawX(int g) {
        return gS >= 1f ? g : g * gS;
    }

    private double rawY(int g) {
        return gS >= 1f ? g : g * gS;
    }

    // ── Widget visual-coordinate helpers ─────────────────────────────────
    // When gS < 1 the panel is scaled around the screen centre.
    // Widgets must be placed at their *visual* (post-transform) positions so
    // that their hitboxes match what the player sees on screen.
    //
    //   wx(p)  =  p * gS               (visual X of a logical GUI X)
    //   wy(p)  =  p * gS               (visual Y of a logical GUI Y)
    //   ws(s)  =  s * gS               (visual size of a logical dimension)
    //
    // At gS == 1 all three functions are identity, so there is no overhead.

    private int wx(int x) {
        return gS >= 1f ? x : (int) Math.round(x * gS);
    }

    private int wy(int y) {
        return gS >= 1f ? y : (int) Math.round(y * gS);
    }

    private int ws(int size) {
        return gS >= 1f ? size : Math.max(1, (int) Math.round(size * gS));
    }

    private boolean over(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    // =========================================================================
    // ELO / tier helpers
    // =========================================================================

    private int eloForFmt(String fid) {
        for (RankedLadderSnapshot s : ArenaClientState.getRankedSnapshots()) {
            cobblemon.arena.ladder.ArenaLadder l = cobblemon.arena.ladder.ArenaLadder.byId(s.ladderId());
            if (l != null && l.getBattleTypeId().equalsIgnoreCase(fid)) {
                return s.rankedRating();
            }
        }
        return ArenaClientState.getRankedRating();
    }

    private int winsForFmt(String fid) {
        for (RankedLadderSnapshot s : ArenaClientState.getRankedSnapshots()) {
            cobblemon.arena.ladder.ArenaLadder l = cobblemon.arena.ladder.ArenaLadder.byId(s.ladderId());
            if (l != null && l.getBattleTypeId().equalsIgnoreCase(fid)) {
                return s.rankedWins();
            }
        }
        return ArenaClientState.getRankedWins();
    }

    private int gamesForFmt(String fid) {
        for (RankedLadderSnapshot s : ArenaClientState.getRankedSnapshots()) {
            cobblemon.arena.ladder.ArenaLadder l = cobblemon.arena.ladder.ArenaLadder.byId(s.ladderId());
            if (l != null && l.getBattleTypeId().equalsIgnoreCase(fid)) {
                return s.rankedWins() + s.rankedLosses();
            }
        }
        return (
            ArenaClientState.getRankedWins() +
            ArenaClientState.getRankedLosses()
        );
    }

    private int streakForFmt(String fid) {
        for (RankedLadderSnapshot s : ArenaClientState.getRankedSnapshots()) {
            cobblemon.arena.ladder.ArenaLadder l = cobblemon.arena.ladder.ArenaLadder.byId(s.ladderId());
            if (l != null && l.getBattleTypeId().equalsIgnoreCase(fid)) {
                return s.rankedBestStreak();
            }
        }
        return 0;
    }

    private static String getRankTierName(int elo) {
        if (elo >= 2800) return "Grao-Mestre";
        if (elo >= 2400) return "Mestre";
        if (elo >= 2000) return "Diamante";
        if (elo >= 1600) return "Platina";
        if (elo >= 1200) return "Ouro";
        if (elo >= 800) return "Prata";
        return elo >= 400 ? "Bronze" : "Iniciante";
    }

    private static String shortTier(String t) {
        return switch (t) {
            case "Grao-Mestre" -> "GRAO-MESTRE";
            case "Mestre" -> "MESTRE";
            case "Diamante" -> "DIAMANTE";
            case "Platina" -> "PLATINA";
            case "Ouro" -> "OURO";
            case "Prata" -> "PRATA";
            case "Bronze" -> "BRONZE";
            default -> "INICIANTE";
        };
    }

    private static int eloTierBg(int elo) {
        if (elo >= 2800) return TIER_GM;
        if (elo >= 2400) return TIER_MST;
        if (elo >= 2000) return TIER_DIA;
        if (elo >= 1600) return TIER_PLT;
        if (elo >= 1200) return TIER_GLD;
        if (elo >= 800) return TIER_SLV;
        if (elo >= 400) return TIER_BRZ;
        return 0xFFAAAAAA; // Iniciante
    }

    private static int nextTierElo(int elo) {
        if (elo < 400) return 400;
        if (elo < 800) return 800;
        if (elo < 1200) return 1200;
        if (elo < 1600) return 1600;
        if (elo < 2000) return 2000;
        if (elo < 2400) return 2400;
        if (elo < 2800) return 2800;
        return 3500;
    }

    private static int prevTierElo(int elo) {
        if (elo >= 2800) return 2800;
        if (elo >= 2400) return 2400;
        if (elo >= 2000) return 2000;
        if (elo >= 1600) return 1600;
        if (elo >= 1200) return 1200;
        if (elo >= 800) return 800;
        if (elo >= 400) return 400;
        return 0; // Ensures Bronze (0-400) calculates progress correctly
    }

    // =========================================================================
    // Colour utilities
    // =========================================================================

    /** Fully opaque RGB. */
    private static int p(int r, int g, int b) {
        return pa(255, r, g, b);
    }

    /** ARGB. */
    private static int pa(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Lighten (add to R,G,B). */
    private static int lt(int col, int amt) {
        int a = (col >> 24) & 0xFF;
        int r = Math.min(255, ((col >> 16) & 0xFF) + amt);
        int g = Math.min(255, ((col >> 8) & 0xFF) + amt);
        int b = Math.min(255, (col & 0xFF) + amt);
        return pa(a, r, g, b);
    }

    /** Darken (subtract from R,G,B). */
    private static int dk(int col, int amt) {
        int a = (col >> 24) & 0xFF;
        int r = Math.max(0, ((col >> 16) & 0xFF) - amt);
        int g = Math.max(0, ((col >> 8) & 0xFF) - amt);
        int b = Math.max(0, (col & 0xFF) - amt);
        return pa(a, r, g, b);
    }

    private static int pInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ── Internal hit-test record ──────────────────────────────────────────────
    private record Hit(String id, int x, int y, int w, int h) {}

    private static class InvisibleButton extends net.minecraft.client.gui.widget.ButtonWidget {
        public InvisibleButton(int x, int y, int width, int height, PressAction onPress) {
            super(x, y, width, height, net.minecraft.text.Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        }
        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
    }
}
