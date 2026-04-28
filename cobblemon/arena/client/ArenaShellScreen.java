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
    public static final int W = 480;
    public static final int H = 292;
    public static final int SB = 90;
    public static final int CW = W - SB; // 390

    // Play-page column geometry (offsets from content-left = gL+SB)
    private static final int C1W = 118;
    private static final int C2X = C1W + 4; // 122
    private static final int C2W = 138;
    private static final int C3X = C2X + C2W + 4; // 264
    private static final int C3W = CW - C3X - 2; // 124

    // Play-page row offsets from cy (= gT)
    private static final int PY_PLAYER = 4;
    private static final int PY_PARTY = 56;
    private static final int PY_COLS = 103;
    private static final int PY_RANKED = PY_COLS; // 103
    private static final int PY_CASUAL = PY_COLS + 110; // 213  → ranked-card(106) + gap(4)

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int C_BASE = p(10, 8, 18);
    private static final int C_PANEL = p(15, 13, 26);
    private static final int C_CARD = p(24, 20, 40);
    private static final int C_CARD2 = p(30, 26, 50);
    private static final int C_SB = p(12, 10, 22);
    private static final int C_NAVA = pa(200, 50, 32, 105);
    private static final int C_BDR = p(38, 32, 62);
    private static final int C_BDR2 = p(56, 48, 90);
    private static final int C_INSET = p(18, 15, 30);

    private static final int A_PUR = p(108, 62, 230);
    private static final int A_PUR2 = p(76, 44, 165);
    private static final int A_PUR3 = p(50, 28, 110);
    private static final int A_GOLD = p(225, 170, 38);
    private static final int A_GREEN = p(58, 185, 105);
    private static final int A_RED = p(208, 70, 78);
    private static final int A_ORG = p(222, 138, 38);

    private static final int T_W = p(228, 224, 240);
    private static final int T_L = p(165, 158, 195);
    private static final int T_DIM = p(102, 95, 138);
    private static final int T_PUR = p(128, 85, 220);
    private static final int T_MUT = p(65, 60, 95);

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

    // Play widgets
    private String rFmt = "Doubles";
    private String cFmt = "Singles";
    private String cLvl = "50";
    private CycleSelectorButton rFmtBtn, cFmtBtn, cLvlBtn;
    private StyledButton rankedQueueButton;
    private StyledButton casualQueueButton;
    // Queue display state — drives drawRankedCard / drawCasualCard
    private boolean queueInRanked = false;
    private boolean queueInCasual = false;
    private StyledButton cancelQueueButton;

    // Per-card validation errors (separate so each card shows its own error).
    // Auto-cleared after VALIDATION_ERROR_TIMEOUT_MS.
    private String rankedValidationError = null;
    private long rankedValidationErrorMs = 0L;
    private String casualValidationError = null;
    private long casualValidationErrorMs = 0L;
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
    private boolean eloTipVisible = false;
    private int eloTipX = 0,
        eloTipY = 0;

    // ── In-screen chat panel (Col1, fills entire column) ──────────────────
    // Col1 chat fills H - PY_COLS = 292 - 103 = 189 px (Status moved to Col3)
    private static final int CHAT_H = H - PY_COLS - 3; // 186 (3px bottom margin)
    private static final int CHAT_INPUT_H = 14;
    private static final int CHAT_MSG_AREA = CHAT_H - CHAT_INPUT_H - 20; // px for messages
    private net.minecraft.client.gui.widget.TextFieldWidget chatInput;
    private int chatScroll = 0; // 0 = newest at bottom

    // Leaderboard
    private String lbFmt = "doubles";

    // History
    private boolean hMatches = true;
    private int hScr = 0;

    // Missions
    private int qScr = 0;
    private final List<Hit> hits = new ArrayList<>();

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
            }
        }
        buildWidgets();
    }

    private void layout() {
        float mw = Math.max(1, width - 2),
            mh = Math.max(1, height - 2);
        gS = Math.min(W > mw ? mw / W : 1f, H > mh ? mh / H : 1f);
        gL = (width - W) / 2;
        gT = (height - H) / 2;
    }

    private void nav(Page p) {
        page = p;
        hScr = 0;
        qScr = 0;
        navMs = System.currentTimeMillis();
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
        int chatPanelX = gL + SB + 2;
        int chatPanelY = gT + PY_COLS;
        int chatInputY = chatPanelY + CHAT_H - CHAT_INPUT_H - 2;
        chatInput = new net.minecraft.client.gui.widget.TextFieldWidget(
            textRenderer,
            wx(chatPanelX + 2),
            wy(chatInputY),
            ws(C1W - 4),
            ws(CHAT_INPUT_H - 2),
            net.minecraft.text.Text.literal("/g ...")
        );
        chatInput.setMaxLength(200);
        chatInput.setPlaceholder(
            net.minecraft.text.Text.literal("Digite mensagem...")
        );
        chatInput.setDrawsBackground(false);
        addDrawableChild(chatInput);

        // ── Queue-active states: show only the cancel button ──────────────
        if (queueInRanked) {
            cancelQueueButton = addW(
                new StyledButton(
                    wx(c2x + 8),
                    wy(gT + PY_RANKED + 76),
                    ws(C2W - 16),
                    ws(20),
                    Text.literal("Cancelar Fila"),
                    button -> {
                        pendingCancelQueue = true;
                        cancelQueueSent = true; // guard: prevent updatePlayButtons re-enabling
                        ClientPlayNetworking.send(new CancelQueuePacket());
                    },
                    A_RED
                )
            );
            return;
        }

        if (queueInCasual) {
            cancelQueueButton = addW(
                new StyledButton(
                    wx(c2x + 8),
                    wy(gT + PY_CASUAL + 57),
                    ws(C2W - 16),
                    ws(14),
                    Text.literal("Cancelar Fila"),
                    button -> {
                        pendingCancelQueue = true;
                        cancelQueueSent = true;
                        ClientPlayNetworking.send(new CancelQueuePacket());
                    },
                    A_RED
                )
            );
            return;
        }

        // ── Normal state: format selectors + join buttons ─────────────────
        //
        // Widgets are positioned using wx/wy/ws so their hitboxes align with
        // their visual positions even when the GUI is downscaled (gS < 1).
        //
        // Ranked card: rFmtBtn at card-relative y+44  (label above at y+34)
        rFmtBtn = addW(
            new CycleSelectorButton(
                wx(c2x + 10),
                wy(gT + PY_RANKED + 44),
                ws(C2W - 22),
                ws(14),
                List.of("Singles", "Doubles", "Triples"),
                rFmt,
                ">",
                s -> rFmt = s
            )
        );

        // Ranked join button: at card-relative y+86  (stats end ~y+83, 3 px gap)
        rankedQueueButton = addW(
            new StyledButton(
                wx(c2x + 8),
                wy(gT + PY_RANKED + 86),
                ws(C2W - 16),
                ws(16),
                Text.literal("Fila Ranqueada"),
                button -> {
                    String err = validatePartyForQueue();
                    if (err != null) {
                        rankedValidationError = err;
                        rankedValidationErrorMs = System.currentTimeMillis();
                        return;
                    }
                    rankedValidationError = null;
                    cancelQueueSent = false;
                    // Clear any stale rejection signal from a previous cancel/reject
                    // so it doesn't interfere with this new join attempt.
                    ArenaClientState.clearQueueRejection();
                    pendingQueueJoinRanked = true;
                    ClientPlayNetworking.send(
                        new JoinQueuePacket(
                            ArenaLadder.rankedPresetByChoice(
                                rFmt,
                                "50",
                                false
                            ).getId()
                        )
                    );
                },
                A_PUR
            )
        );

        // Casual card: two selectors side-by-side at card-relative y+44
        cFmtBtn = addW(
            new CycleSelectorButton(
                wx(c2x + 10),
                wy(gT + PY_CASUAL + 44),
                ws(60),
                ws(14),
                List.of("Singles", "Doubles", "Triples", "Monotype"),
                cFmt,
                ">",
                s -> cFmt = s
            )
        );
        cLvlBtn = addW(
            new CycleSelectorButton(
                wx(c2x + 74),
                wy(gT + PY_CASUAL + 44),
                ws(52),
                ws(14),
                List.of("50", "100"),
                cLvl,
                ">",
                s -> cLvl = s
            )
        );

        // Casual join button: at card-relative y+60  (selectors end y+58, 2 px gap)
        // Card h = H-PY_CASUAL-3 = 76. Button ends y+73, bottom pad 3 px.
        casualQueueButton = addW(
            new StyledButton(
                wx(c2x + 8),
                wy(gT + PY_CASUAL + 60),
                ws(C2W - 16),
                ws(13),
                Text.literal("Fila Rapida"),
                button -> {
                    String err = validatePartyForQueue();
                    if (err != null) {
                        casualValidationError = err;
                        casualValidationErrorMs = System.currentTimeMillis();
                        return;
                    }
                    casualValidationError = null;
                    cancelQueueSent = false;
                    ArenaClientState.clearQueueRejection();
                    pendingQueueJoinCasual = true;
                    ClientPlayNetworking.send(
                        new JoinQueuePacket(
                            ArenaLadder.vgcPresetByChoice(
                                cFmt,
                                cLvl,
                                false
                            ).getId()
                        )
                    );
                },
                A_GOLD
            )
        );
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
        // Use wx/wy/ws so hitboxes match visual positions when gS < 1
        int tx = gL + SB + 8,
            ty = gT + 38;
        addW(
            new StyledButton(
                wx(tx),
                wy(ty),
                ws(88),
                ws(14),
                Text.literal("Partidas"),
                b -> {
                    hMatches = true;
                    buildHistoryWidgets();
                }
            )
        );
        addW(
            new StyledButton(
                wx(tx + 92),
                wy(ty),
                ws(108),
                ws(14),
                Text.literal("Pokemon usados"),
                b -> {
                    hMatches = false;
                    buildHistoryWidgets();
                }
            )
        );
    }

    private void buildLbWidgets() {
        int tx = gL + SB + 8,
            ty = gT + 38;
        String[] fn = { "Singles", "Doubles", "Triples" };
        String[] fi = { "singles", "doubles", "triples" };
        for (int i = 0; i < 3; i++) {
            String fid = fi[i];
            addW(
                new StyledButton(
                    wx(tx + i * 68),
                    wy(ty),
                    ws(64),
                    ws(14),
                    Text.literal(fn[i]),
                    b -> {
                        lbFmt = fid;
                        buildLbWidgets();
                    }
                )
            );
        }
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
        drawSidebar(ctx, gx, gy);
        drawContent(ctx, gx, gy, delta);

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

        QueueStatusOverlay.getInstance().render(ctx, width, height);
    }

    // ── Panel base ────────────────────────────────────────────────────────────

    private void drawPanel(DrawContext c) {
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
        "Play",
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
        t(c, "COBBLEMON", gL + 10, gT + 9, T_W, 0.72f);
        t(c, "ARENA", gL + 10, gT + 19, A_PUR2, 0.66f);
        c.fill(gL + 8, gT + 28, gL + SB - 8, gT + 29, C_BDR2);

        // Navigation items
        for (int i = 0; i < SB_LABELS.length; i++) navItem(
            c,
            SB_LABELS[i],
            SB_PAGES[i],
            mx,
            my,
            gT + 33 + i * 22
        );

        // Bottom divider
        c.fill(gL + 8, gT + 169, gL + SB - 8, gT + 170, C_BDR);
        t(c, "Config", gL + 14, gT + 174, T_MUT, 0.70f);
        t(c, "v2.0", gL + 14, gT + 184, T_MUT, 0.60f);
    }

    private void navItem(
        DrawContext c,
        String label,
        Page p,
        int mx,
        int my,
        int y
    ) {
        int x = gL + 3,
            w = SB - 6,
            h = 20;
        boolean act = page == p;
        boolean hov = !act && mx >= x && mx < x + w && my >= y && my < y + h;

        if (act) {
            c.fill(x, y, x + w, y + h, C_NAVA);
            c.fill(x, y, x + 3, y + h, A_PUR); // left accent stripe
            c.fill(x, y, x + w, y + 1, pa(20, 108, 62, 230));
            // Active indicator dot on right
            c.fill(gL + SB - 9, y + 8, gL + SB - 5, y + 12, A_PUR);
        } else if (hov) {
            c.fill(x, y, x + w, y + h, pa(70, 80, 58, 120));
        }

        t(c, label, x + 12, y + 6, act ? T_W : hov ? T_L : T_DIM, 0.82f);
    }

    // ── Content dispatcher ────────────────────────────────────────────────────

    private void drawContent(DrawContext c, int mx, int my, float delta) {
        eloTipVisible = false; // reset each frame; set again if mouse over pill
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
        // Draw ELO tooltip on top of everything, still inside the scale pass
        if (eloTipVisible) drawEloTierTooltip(c, eloTipX, eloTipY);
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
        // ── Row 1: Player stats ────────────────────────────────────────────
        drawPlayerStrip(c, mx, my, cx, cy + PY_PLAYER, CW);

        // ── Row 2: Party preview (actual Pokémon rendering) ───────────────
        drawPartyStrip(c, mx, my, delta, cx, cy + PY_PARTY, CW);

        // ── Row 3: Three columns ──────────────────────────────────────────
        int c1 = cx + 2;
        int c2 = cx + C2X;
        int c3 = cx + C3X;
        int colTop = cy + PY_COLS;

        // Col1: chat fills the entire column (status moved to Col3)
        drawChatPanel(c, c1, colTop, C1W, mx, my);
        // Col2: ranked + casual cards (unchanged)
        drawRankedCard(c, c2, colTop, C2W, mx, my);
        drawCasualCard(c, c2, colTop + 110, C2W, mx, my);
        // Col3: missions preview (h=106) + 4px gap + status (h=76, 3px bottom pad)
        drawMissPreview(c, c3, colTop, C3W);
        drawArenaStatus(c, c3, colTop + 110, C3W, 76);
    }

    // ── Full-width player stats strip (h=48) ─────────────────────────────────
    private void drawPlayerStrip(
        DrawContext c,
        int mx,
        int my,
        int x,
        int y,
        int w
    ) {
        card(c, x + 2, y, w - 4, 48);

        // Left: name + tier badge + ELO
        String name = ArenaClientState.getPlayerName();
        int elo = ArenaClientState.getRankedRating();
        t(c, name, x + 14, y + 8, T_W, 1.05f);
        String tierLabel = shortTier(ArenaClientState.getRankTitle());
        int pw = pillW(tierLabel);
        tierPill(c, x + 14, y + 23, tierLabel, eloTierBg(elo));
        t(c, elo + " ELO", x + 14 + pw + 6, y + 25, T_DIM, 0.74f);
        // Hover detection for ELO tooltip
        int pillAreaW =
            pw + 6 + Math.round(textRenderer.getWidth(elo + " ELO") * 0.74f);
        if (over(mx, my, x + 14, y + 22, pillAreaW, 14)) {
            eloTipVisible = true;
            eloTipX = x + 14;
            eloTipY = y + 22;
        }

        // Right: 4 stat chips
        int rW = ArenaClientState.getRankedWins(),
            rL = ArenaClientState.getRankedLosses();
        int qW = ArenaClientState.getQuickWins(),
            qL = ArenaClientState.getQuickLosses();
        int tot = rW + qW + rL + qL;
        int wr = tot == 0 ? 0 : Math.round(((rW + qW) * 100f) / tot);
        int str = ArenaClientState.getRankedStreak();

        String[] cl = { "Ranqueado", "Casual", "Win Rate", "Streak" };
        String[] cv = {
            rW + "-" + rL,
            qW + "-" + qL,
            wr + "%",
            (str >= 0 ? "+" : "") + str,
        };
        int[] cc = {
            A_PUR,
            A_GOLD,
            A_GREEN,
            str > 0 ? A_GREEN : str < 0 ? A_RED : T_DIM,
        };

        int chipW = 68,
            chipStart = x + w - 4 * (chipW + 4) - 8;
        for (int i = 0; i < 4; i++) smallChip(
            c,
            chipStart + i * (chipW + 4),
            y + 10,
            chipW,
            cl[i],
            cv[i],
            cc[i]
        );
    }

    // ── Full-width party strip with actual Pokémon rendering (h=43) ───────────
    private void drawPartyStrip(
        DrawContext c,
        int mx,
        int my,
        float delta,
        int x,
        int y,
        int w
    ) {
        card(c, x + 2, y, w - 4, 43);
        t(c, "SEU TIME", x + 12, y + 5, T_DIM, 0.66f);
        // ArenaPartyPreviewRenderer handles Cobblemon model rendering
        List<Text> tooltip = partyRenderer.render(
            c,
            textRenderer,
            x + 4,
            y,
            w - 8,
            43,
            mx,
            my,
            delta
        );
        if (tooltip != null && !tooltip.isEmpty()) c.drawTooltip(
            textRenderer,
            tooltip,
            mx,
            my
        );
    }

    // ── Col1: In-screen chat panel (h=CHAT_H=105) ──────────────────────────
    // Renders the last N chat messages and a text-input field.
    // Sending: when the player presses Enter the text is submitted via /g.
    private void drawChatPanel(
        DrawContext c,
        int x,
        int y,
        int w,
        int mx,
        int my
    ) {
        // Outer card
        c.fill(x, y, x + w, y + CHAT_H, C_CARD);
        c.fill(x, y, x + w, y + 1, C_BDR2);
        c.fill(x, y, x + 1, y + CHAT_H, C_BDR2);
        c.fill(x + w - 1, y, x + w, y + CHAT_H, C_BDR);
        c.fill(x, y + CHAT_H - 1, x + w, y + CHAT_H, C_BDR);

        // Header
        t(c, "CHAT GLOBAL", x + 6, y + 4, T_DIM, 0.60f);
        t(c, "/g", x + w - 18, y + 4, T_MUT, 0.60f);
        c.fill(x + 4, y + 13, x + w - 4, y + 14, C_BDR);

        // Message area (CHAT_MSG_AREA px tall)
        int msgAreaTop = y + 15;
        int msgAreaBot = y + CHAT_H - CHAT_INPUT_H - 4;
        int lineH = 9; // px per line at 0.62f scale
        int visibleLines = (msgAreaBot - msgAreaTop) / lineH; // ≈ 8

        java.util.List<String> all = ArenaChatState.getMessages();
        int total = all.size();
        // Bottom-align: show the last N lines, scroll offsets shift up
        int startIdx = Math.max(0, total - visibleLines - chatScroll);
        int endIdx = Math.max(0, total - chatScroll);

        c.enableScissor(x + 2, msgAreaTop, x + w - 2, msgAreaBot);
        int ly = msgAreaTop;
        for (int i = startIdx; i < endIdx; i++) {
            String msg = all.get(i);
            // Alternate row tint for readability
            if (i % 2 == 0) c.fill(
                x + 2,
                ly,
                x + w - 2,
                ly + lineH - 1,
                pa(20, 80, 60, 140)
            );
            tfit(c, msg, x + 4, ly + 1, w - 8, T_L, 0.60f);
            ly += lineH;
        }
        c.disableScissor();

        // Divider above input
        c.fill(
            x + 4,
            y + CHAT_H - CHAT_INPUT_H - 3,
            x + w - 4,
            y + CHAT_H - CHAT_INPUT_H - 2,
            C_BDR
        );

        // Input field background (the TextFieldWidget renders on top)
        int inputY = y + CHAT_H - CHAT_INPUT_H - 1;
        c.fill(x + 2, inputY, x + w - 2, y + CHAT_H - 2, pa(80, 20, 16, 36));
        c.fill(x + 2, inputY, x + w - 2, inputY + 1, C_BDR2);

        // Scrollbar (if more messages than visible)
        if (total > visibleLines) {
            scbar(
                c,
                x + w - 4,
                msgAreaTop,
                3,
                msgAreaBot - msgAreaTop,
                chatScroll,
                Math.max(1, total - visibleLines)
            );
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter / NumpadEnter: send chat message via /g command
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
                // Send via the /g global-chat command already on the server.
                // ClientPlayNetworkHandler.sendCommand() is the correct API in 1.21.1.
                client.getNetworkHandler().sendCommand("g " + text);
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

    // ── Col2: Ranked queue card (h = PY_CASUAL - PY_RANKED - 4 = 106) ────────────
    // When NOT in queue:
    //   rFmtBtn widget at (c2x+10, gT+PY_RANKED+44, w=C2W-22, h=14)
    //   rankedQueueButton at  (c2x+8,  gT+PY_RANKED+86, w=C2W-16, h=16)
    // When IN ranked queue: drawQueueCounterCard() replaces inner content.
    private void drawRankedCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int mx,
        int my
    ) {
        final int cardH = PY_CASUAL - PY_RANKED - 4; // 106
        card(c, x, y, w, cardH);

        if (queueInRanked) {
            drawQueueCounterCard(c, x, y, w, cardH, true);
            return;
        }

        // ── Header ────────────────────────────────────────────────────────────
        t(c, "Ladder Ranqueada", x + 8, y + 7, T_W, 0.82f);
        int elo = ArenaClientState.getRankedRating();
        tierPill(
            c,
            x + w - 48,
            y + 6,
            shortTier(ArenaClientState.getRankTitle()),
            eloTierBg(elo)
        );
        t(c, "Competitivo - afeta Elo e ranking", x + 8, y + 20, T_DIM, 0.60f);
        c.fill(x + 6, y + 30, x + w - 6, y + 31, C_BDR);

        // ── Format / Level row ─────────────────────────────────────────────
        // Labels at y+34; rFmtBtn widget positioned at y+44 (via buildPlayWidgets)
        t(c, "Formato", x + 8, y + 34, T_DIM, 0.60f);
        t(c, "Nivel", x + 76, y + 34, T_DIM, 0.60f);
        t(c, "50  (Fixo)", x + 76, y + 46, T_L, 0.68f);

        // ── Stats row ─────────────────────────────────────────────────────────
        // rFmtBtn ends at y+58 (h=14).  Divider at y+62.  Labels at y+66,
        // values at y+76 (≈y+83 at 0.80f scale).  Join button at y+86 (3 px gap).
        c.fill(x + 6, y + 62, x + w - 6, y + 63, C_BDR);
        // Auto-clear validation error after timeout
        if (
            rankedValidationError != null &&
            System.currentTimeMillis() - rankedValidationErrorMs >
            VALIDATION_ERROR_TIMEOUT_MS
        ) {
            rankedValidationError = null;
        }
        // If party is invalid, replace stats row with the error message (no overlap).
        if (rankedValidationError != null) {
            c.fill(x + 6, y + 64, x + w - 6, y + 82, pa(35, 208, 70, 78));
            c.fill(x + 6, y + 64, x + w - 6, y + 65, pa(80, 208, 70, 78));
            tfit(
                c,
                rankedValidationError,
                x + 10,
                y + 68,
                w - 20,
                A_RED,
                0.62f
            );
        } else {
            stat3(
                c,
                x + 8,
                y + 66,
                w - 16,
                new String[] { "Rating", "Tier", "Recorde" },
                new String[] {
                    "" + elo,
                    shortTier(ArenaClientState.getRankTitle()),
                    ArenaClientState.getRankedWins() +
                    "-" +
                    ArenaClientState.getRankedLosses(),
                },
                new int[] { T_PUR, T_W, T_W }
            );
        }
        // rankedQueueButton widget rendered at y+86 (buildPlayWidgets)
    }

    // ── Col2: Casual card (h = H - PY_CASUAL = 79) ────────────────────────────
    // When NOT in queue:
    //   cFmtBtn widget at (c2x+10, gT+PY_CASUAL+44, w=60, h=14)
    //   cLvlBtn  widget at (c2x+74, gT+PY_CASUAL+44, w=52, h=14)
    //   casualQueueButton  at  (c2x+8,  gT+PY_CASUAL+62, w=C2W-16, h=15)
    // When IN casual queue: drawQueueCounterCard() replaces inner content.
    private void drawCasualCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int mx,
        int my
    ) {
        // h = H - PY_CASUAL - 3 = 76  (3 px bottom margin so card never touches panel border)
        final int cardH = H - PY_CASUAL - 3;
        card(c, x, y, w, cardH);

        if (queueInCasual) {
            drawQueueCounterCard(c, x, y, w, cardH, false);
            return;
        }

        // ── Header ────────────────────────────────────────────────────────────
        t(c, "Fila Rapida", x + 8, y + 7, T_W, 0.82f);
        t(c, "Casual - sem impacto no ranking", x + 8, y + 20, T_DIM, 0.60f);
        c.fill(x + 6, y + 30, x + w - 6, y + 31, C_BDR);

        // ── Format / Level row ─────────────────────────────────────────────
        // Labels at y+34; selector widgets at y+44 (via buildPlayWidgets)
        t(c, "Formato", x + 8, y + 34, T_DIM, 0.60f);
        t(c, "Nivel", x + 76, y + 34, T_DIM, 0.60f);
        // cFmtBtn (w=60) at x+10, cLvlBtn (w=52) at x+74 — both h=14
        // casualQueueButton at y+60, h=13 (ends y+73; 3 px bottom pad)
        // Auto-clear validation error after timeout
        if (
            casualValidationError != null &&
            System.currentTimeMillis() - casualValidationErrorMs >
            VALIDATION_ERROR_TIMEOUT_MS
        ) {
            casualValidationError = null;
        }
        if (casualValidationError != null) {
            c.fill(x + 6, y + 43, x + w - 6, y + 58, pa(120, 208, 70, 78));
            tfit(c, casualValidationError, x + 10, y + 46, w - 20, T_W, 0.58f);
        }
    }

    // ── Queue counter card ──────────────────────────────────────────────────────
    // Drawn inside the already-rendered card() box when the player is in queue.
    // The cancel StyledButton is added as a widget in buildPlayWidgets().
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
        int accentR = (accent >> 16) & 0xFF;
        int accentG = (accent >> 8) & 0xFF;
        int accentB = accent & 0xFF;

        // Pulse: 0.30 → 1.00 at ~0.83 Hz
        float pulse = (float) (Math.sin(nowMs / 600.0) * 0.35 + 0.65);

        // Subtle tinted background overlay
        c.fill(
            x + 1,
            y + 1,
            x + w - 1,
            y + cardH - 1,
            pa((int) (18 * pulse), accentR, accentG, accentB)
        );

        // Pulsing left accent stripe
        c.fill(
            x + 1,
            y + 1,
            x + 4,
            y + cardH - 1,
            pa((int) (255 * pulse), accentR, accentG, accentB)
        );
        c.fill(
            x + 1,
            y + 1,
            x + 4,
            y + 2,
            pa((int) (80 * pulse), 255, 255, 255)
        ); // top sheen on stripe

        // ── Header row ───────────────────────────────────────────────────
        // Timer top-right — use LOCAL elapsed time so it ticks in real time
        // without relying on periodic server packets.
        int secs = QueueStatusOverlay.getInstance().getElapsedSeconds();
        String timer = String.format("%d:%02d", secs / 60, secs % 60);
        int timerW = Math.round(textRenderer.getWidth(timer) * 0.82f);
        t(c, timer, x + w - timerW - 8, y + 7, ranked ? T_PUR : A_GOLD, 0.82f);

        // Mode title (keeps width away from timer)
        t(
            c,
            ranked ? "EM FILA RANQUEADA" : "EM FILA CASUAL",
            x + 10,
            y + 7,
            T_W,
            0.76f
        );

        // Subtitle: format / level / mode tag
        String sub = ranked
            ? rFmt + "  -  Lv.50  -  Competitivo"
            : cFmt + "  -  Lv." + cLvl + "  -  Casual";
        t(c, sub, x + 10, y + 19, T_DIM, 0.60f);

        // Divider
        c.fill(
            x + 6,
            y + 30,
            x + w - 6,
            y + 31,
            pa(90, accentR, accentG, accentB)
        );

        // ── Searching row ────────────────────────────────────────────────
        // FIX: cast to int AFTER the modulo — casting first causes long→int
        // overflow (nowMs ≈ 1.7e12) which produces a negative int, and
        // String.repeat(negative) throws IllegalArgumentException.
        int dotCount = (int) ((nowMs / 450L) % 4L);
        // For casual (h=76) compact to y+32; ranked (h=106) can use y+34.
        int searchY = ranked ? 34 : 32;
        t(
            c,
            "Procurando adversario" + ".".repeat(dotCount),
            x + 10,
            y + searchY,
            T_DIM,
            0.66f
        );

        // Animated scan bar (loops every 1.8 s)
        int barW = w - 20;
        int barStartY = ranked ? 45 : 42;
        float barFrac = (nowMs % 1800) / 1800.0f;
        c.fill(x + 10, y + barStartY, x + 10 + barW, y + barStartY + 4, C_BDR2);
        int barFill = (int) (barW * barFrac);
        if (barFill > 0) {
            c.fill(
                x + 10,
                y + barStartY,
                x + 10 + barFill,
                y + barStartY + 4,
                ranked ? A_PUR2 : pa(180, accentR, accentG, accentB)
            );
        }
        c.fill(
            x + 10,
            y + barStartY,
            x + 10 + barW,
            y + barStartY + 1,
            pa(14, 255, 255, 255)
        );

        // Players in queue
        int inQueue = ArenaClientState.getPlayersInQueue();
        int playersY = ranked ? 53 : 50;
        t(c, inQueue + " jogadores na fila", x + 10, y + playersY, T_L, 0.62f);

        // Extra hint line (ranked card is taller — h=106 — so it has room)
        if (ranked) {
            t(
                c,
                "Aguarde - a partida comecara em breve",
                x + 10,
                y + 63,
                T_MUT,
                0.58f
            );
        }
        // Cancel button widget added by buildPlayWidgets():
        //   ranked  -> (c2x+8, gT+PY_RANKED+76,  C2W-16, 20)
        //   casual  -> (c2x+8, gT+PY_CASUAL+57,  C2W-16, 14)  ends y+71, pad 5px
    }

    // ── Col3: Missions preview (h=120) ────────────────────────────────────────
    // h = 106 px (same as ranked card) with compact 38 px items (2 fit cleanly)
    private void drawMissPreview(DrawContext c, int x, int y, int w) {
        final int cardH = PY_CASUAL - PY_RANKED - 4; // 106 — matches ranked card
        card(c, x, y, w, cardH);
        t(c, "MISSOES DIARIAS", x + 8, y + 8, T_DIM, 0.64f);
        lnk(c, "Ver todas >", x + w - 52, y + 8, T_PUR, 0.64f);

        List<QuestEntryPayload> daily = ArenaQuestClientState.getDailyQuests();
        if (daily.isEmpty()) {
            t(c, "Abra /arena para", x + 8, y + 28, T_DIM, 0.66f);
            t(c, "sincronizar.", x + 8, y + 38, T_DIM, 0.66f);
            return;
        }

        // Compact layout: 38 px per item so 2 items fit in cardH=106
        // (start y+20, item1 ends y+58, item2 ends y+96, bottom pad 10px)
        int ry = y + 20;
        for (
            int i = 0;
            i < Math.min(2, daily.size()) && ry + 38 < y + cardH;
            i++
        ) {
            QuestEntryPayload q = daily.get(i);

            String rewardDesc = q.rewardDescription();
            int rw = rewardDesc.isBlank()
                ? 0
                : Math.min(
                      50,
                      Math.round(textRenderer.getWidth(rewardDesc) * 0.60f) + 8
                  );

            int titleMaxW = w - 14 - (rw > 0 ? rw + 6 : 0);
            tfit(c, q.title(), x + 8, ry + 2, titleMaxW, T_W, 0.72f);

            if (rw > 0) {
                int bx = x + w - rw - 4;
                c.fill(bx, ry, bx + rw, ry + 10, pa(55, 225, 170, 38));
                c.fill(bx, ry, bx + rw, ry + 1, A_GOLD);
                tfit(c, rewardDesc, bx + 3, ry + 2, rw - 6, A_GOLD, 0.60f);
            }

            t(
                c,
                q.currentProgress() + "/" + q.targetAmount(),
                x + 8,
                ry + 13,
                T_DIM,
                0.62f
            );

            int barW = w - 16;
            c.fill(x + 8, ry + 22, x + 8 + barW, ry + 25, C_BDR2);
            int fl = (int) (barW * q.progressFraction());
            if (fl > 0) c.fill(x + 8, ry + 22, x + 8 + fl, ry + 25, A_PUR);
            c.fill(
                x + 8,
                ry + 22,
                x + 8 + barW,
                ry + 23,
                pa(12, 255, 255, 255)
            );

            // thin divider between items
            if (i < 1) c.fill(x + 6, ry + 32, x + w - 6, ry + 33, C_BDR);

            ry += 38;
        }
    }

    // ── Col3: Quick links (h=60) ──────────────────────────────────────────────
    private void drawQuickLinks(
        DrawContext c,
        int x,
        int y,
        int w,
        int mx,
        int my
    ) {
        // Info box
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
        // ── Player header ─────────────────────────────────────────────────
        int hx = cx + 8,
            hy = cy + 10;
        // Avatar box
        c.fill(hx, hy, hx + 38, hy + 38, C_CARD);
        c.fill(hx, hy, hx + 38, hy + 1, A_PUR2);
        c.fill(hx, hy, hx + 1, hy + 38, A_PUR2);
        c.fill(hx + 37, hy, hx + 38, hy + 38, C_BDR);
        c.fill(hx, hy + 37, hx + 38, hy + 38, C_BDR);
        String playerName = ArenaClientState.getPlayerName();
        // Player head skin (falls back to initial letter if skin not loaded)
        drawPlayerHead(c, hx + 4, hy + 4, 30);

        // Name + badge + ELO
        t(c, ArenaClientState.getPlayerName(), hx + 46, hy + 4, T_W, 1.08f);
        int elo = ArenaClientState.getRankedRating();
        String tierLbl = shortTier(ArenaClientState.getRankTitle());
        int pw = pillW(tierLbl);
        tierPill(c, hx + 46, hy + 21, tierLbl, eloTierBg(elo));
        t(c, elo + " ELO", hx + 46 + pw + 6, hy + 23, T_DIM, 0.74f);
        // ELO tooltip hover
        int eloAreaW =
            pw + 6 + Math.round(textRenderer.getWidth(elo + " ELO") * 0.74f);
        if (over(mx, my, hx + 46, hy + 20, eloAreaW, 14)) {
            eloTipVisible = true;
            eloTipX = hx + 46;
            eloTipY = hy + 20;
        }

        // ── Stats row ─────────────────────────────────────────────────────
        int sy = hy + 48,
            sw = (CW - 20) / 4,
            sx0 = cx + 8;
        int rW = ArenaClientState.getRankedWins(),
            rL = ArenaClientState.getRankedLosses();
        int qW = ArenaClientState.getQuickWins(),
            qL = ArenaClientState.getQuickLosses();
        int tot = rW + qW + rL + qL;
        int wr = tot == 0 ? 0 : Math.round(((rW + qW) * 100f) / tot);
        int str = ArenaClientState.getRankedStreak();

        String[] sl = { "PARTIDAS", "VITORIAS", "WIN RATE", "SEQUENCIA" };
        String[] sv = {
            "" + tot,
            "" + (rW + qW),
            wr + "%",
            (str >= 0 ? "+" : "") + str,
        };
        int[] sc = {
            T_W,
            A_GREEN,
            T_W,
            str > 0 ? A_GREEN : str < 0 ? A_RED : T_DIM,
        };
        for (int i = 0; i < 4; i++) statCard(
            c,
            sx0 + i * (sw + 4),
            sy,
            sw,
            36,
            sl[i],
            sv[i],
            sc[i]
        );

        // ── ELO por formato ───────────────────────────────────────────────
        int fy = sy + 44;
        t(c, "ELO POR FORMATO", cx + 8, fy, T_DIM, 0.72f);
        c.fill(cx + 8, fy + 10, cx + CW - 8, fy + 11, C_BDR);

        int fw = (CW - 24) / 3;
        String[] fmts = { "Singles", "Doubles", "Triples" };
        String[] fids = { "singles", "doubles", "triples" };
        for (int i = 0; i < 3; i++) {
            int felo = eloForFmt(fids[i]);
            fmtCard(
                c,
                cx + 8 + i * (fw + 4),
                fy + 14,
                fw,
                82,
                fmts[i],
                felo,
                winsForFmt(fids[i]),
                gamesForFmt(fids[i])
            );
        }
    }

    private void fmtCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int h,
        String fmt,
        int elo,
        int wins,
        int games
    ) {
        card(c, x, y, w, h);
        t(c, fmt, x + 8, y + 8, T_W, 0.84f);
        tierPill(
            c,
            x + w - 46,
            y + 7,
            shortTier(getRankTierName(elo)),
            eloTierBg(elo)
        );
        // Big ELO value
        t(c, "" + elo, x + 8, y + 22, T_PUR, 1.52f);
        t(
            c,
            games + " partidas  " + wins + " vitorias",
            x + 8,
            y + 50,
            T_DIM,
            0.60f
        );
        // Progress bar to next tier
        int prev = prevTierElo(elo),
            next = nextTierElo(elo);
        float prog =
            next > prev
                ? MathHelper.clamp((float) (elo - prev) / (next - prev), 0f, 1f)
                : 1f;
        int bww = w - 16;
        c.fill(x + 8, y + 60, x + 8 + bww, y + 64, C_BDR2);
        int fl = (int) (bww * prog);
        if (fl > 0) c.fill(x + 8, y + 60, x + 8 + fl, y + 64, A_PUR);
        c.fill(x + 8, y + 60, x + 8 + bww, y + 61, pa(14, 255, 255, 255));
        t(c, elo + " / " + next, x + w / 2 - 22, y + 66, T_DIM, 0.60f);
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
        t(c, "Leaderboard", cx + 8, cy + 10, T_W, 1.0f);
        t(
            c,
            "Top 100 - " + ArenaClientState.getCurrentSeasonName(),
            cx + 8,
            cy + 23,
            T_DIM,
            0.70f
        );

        // Format tab underlines (drawn beneath the StyledButton widgets)
        String[] fi = { "singles", "doubles", "triples" };
        int tx = cx + 8;
        for (int i = 0; i < 3; i++) if (fi[i].equals(lbFmt)) c.fill(
            tx + i * 68,
            cy + 52,
            tx + i * 68 + 64,
            cy + 53,
            A_PUR
        );

        // Table header
        int hdrY = cy + 57;
        c.fill(cx + 8, hdrY, cx + CW - 8, hdrY + 14, pa(220, 16, 13, 26));
        c.fill(cx + 8, hdrY + 14, cx + CW - 8, hdrY + 15, C_BDR2);
        t(c, "#", cx + 12, hdrY + 4, T_DIM, 0.64f);
        t(c, "JOGADOR", cx + 32, hdrY + 4, T_DIM, 0.64f);
        t(c, "RANK", cx + 185, hdrY + 4, T_DIM, 0.64f);
        t(c, "ELO", cx + CW - 90, hdrY + 4, T_DIM, 0.64f);
        t(c, "WIN RATE", cx + CW - 46, hdrY + 4, T_DIM, 0.64f);

        List<String> entries = ArenaClientState.getLeaderboardEntries();
        String me = ArenaClientState.getPlayerName();
        int rowY = hdrY + 17,
            maxY = cy + H - 24;

        for (int i = 0; i < entries.size() && rowY + 15 < maxY; i++) {
            String raw = entries.get(i);
            String[] p = raw.split("\\|");
            String nm = p.length > 0 ? p[0].trim() : raw;
            String els = p.length > 1 ? p[1].replace("Elo", "").trim() : "?";
            String wl = p.length > 2 ? p[2].trim() : "0-0";
            int ev = pInt(els, 1000);
            String[] wlp = wl.split("-");
            int wv = pInt(wlp.length > 0 ? wlp[0] : "0", 0);
            int lv = pInt(wlp.length > 1 ? wlp[1] : "0", 0);
            int wr2 = (wv + lv) == 0 ? 0 : Math.round((wv * 100f) / (wv + lv));
            boolean isMe = nm.equals(me);

            int bg = isMe
                ? pa(160, 44, 28, 95)
                : (i % 2 == 0 ? pa(150, 20, 16, 34) : pa(110, 16, 12, 28));
            c.fill(cx + 8, rowY, cx + CW - 8, rowY + 15, bg);
            if (isMe) c.fill(cx + 8, rowY, cx + 10, rowY + 15, A_PUR);

            // Medal / rank number
            String pos =
                i == 0 ? "1." : i == 1 ? "2." : i == 2 ? "3." : (i + 1) + ".";
            int pc = i == 0 ? A_GOLD : i == 1 ? T_L : i == 2 ? A_ORG : T_DIM;
            t(c, pos, cx + 12, rowY + 4, pc, 0.70f);

            // Name
            tfit(c, nm, cx + 32, rowY + 4, 142, isMe ? T_PUR : T_W, 0.76f);
            if (isMe) {
                int nw = Math.round(textRenderer.getWidth(nm) * 0.76f);
                t(
                    c,
                    "Voce",
                    cx + 32 + nw + 4,
                    rowY + 4,
                    pa(200, 108, 62, 230),
                    0.60f
                );
            }

            // Rank badge
            tierPill(
                c,
                cx + 185,
                rowY + 3,
                shortTier(getRankTierName(ev)),
                eloTierBg(ev)
            );

            // ELO + WR
            t(c, els, cx + CW - 94, rowY + 4, T_W, 0.78f);
            t(c, wr2 + "%", cx + CW - 48, rowY + 4, T_DIM, 0.68f);

            rowY += 16;
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

    // =========================================================================
    // PAGE: HISTORY
    // =========================================================================

    private void renderHistory(DrawContext c, int mx, int my, int cx, int cy) {
        t(c, "Historico de Partidas", cx + 8, cy + 10, T_W, 1.0f);
        t(c, "Suas ultimas batalhas", cx + 8, cy + 23, T_DIM, 0.68f);

        // Tab underlines
        c.fill(cx + 8, cy + 52, cx + 8 + 88, cy + 53, hMatches ? A_PUR : C_BDR);
        c.fill(
            cx + 8 + 92,
            cy + 52,
            cx + 8 + 200,
            cy + 53,
            hMatches ? C_BDR : A_PUR
        );

        int conY = cy + 57,
            conH = H - 75;
        c.enableScissor(cx + 4, conY, cx + CW - 4, conY + conH);
        int ry = conY - hScr;

        if (hMatches) {
            List<ArenaMatchHistoryEntryPayload> ent =
                ArenaClientState.getRecentMatchHistory();
            for (ArenaMatchHistoryEntryPayload e : ent) {
                matchRow(c, cx + 8, ry, CW - 16, e);
                ry += 44;
            }
            if (ent.isEmpty()) tc(
                c,
                "Sem partidas registradas.",
                cx + CW / 2,
                conY + conH / 2,
                T_DIM,
                0.78f
            );
            hScr = MathHelper.clamp(
                hScr,
                0,
                Math.max(0, ent.size() * 44 - conH)
            );
        } else {
            List<ArenaPokemonUsageEntryPayload> ent =
                ArenaClientState.getPokemonUsage();
            // Header row
            c.fill(cx + 8, ry, cx + CW - 8, ry + 14, pa(200, 18, 15, 28));
            t(c, "#", cx + 14, ry + 3, T_DIM, 0.62f);
            t(c, "Pokemon", cx + 32, ry + 3, T_DIM, 0.62f);
            t(c, "Usos", cx + CW - 110, ry + 3, T_DIM, 0.62f);
            t(c, "W/L", cx + CW - 72, ry + 3, T_DIM, 0.62f);
            t(c, "Taxa", cx + CW - 28, ry + 3, T_DIM, 0.62f);
            ry += 15;
            for (int i = 0; i < ent.size(); i++) {
                pokeRow(c, cx + 8, ry, CW - 16, i + 1, ent.get(i));
                ry += 18;
            }
            if (ent.isEmpty()) tc(
                c,
                "Sem dados de Pokemon.",
                cx + CW / 2,
                conY + conH / 2,
                T_DIM,
                0.78f
            );
            hScr = MathHelper.clamp(
                hScr,
                0,
                Math.max(0, ent.size() * 18 + 15 - conH)
            );
        }
        c.disableScissor();

        int maxS = hMatches
            ? Math.max(
                  0,
                  ArenaClientState.getRecentMatchHistory().size() * 44 - conH
              )
            : Math.max(
                  0,
                  ArenaClientState.getPokemonUsage().size() * 18 + 15 - conH
              );
        if (maxS > 0) scbar(c, cx + CW - 6, conY, 4, conH, hScr, maxS);
    }

    private void matchRow(
        DrawContext c,
        int x,
        int y,
        int w,
        ArenaMatchHistoryEntryPayload e
    ) {
        boolean win = e.victory();
        c.fill(x, y, x + w, y + 40, C_CARD);
        c.fill(
            x,
            y,
            x + w,
            y + 1,
            win ? pa(55, 58, 185, 105) : pa(55, 208, 70, 78)
        );
        c.fill(
            x,
            y,
            x + w,
            y + 40,
            win ? pa(8, 58, 185, 105) : pa(8, 208, 70, 78)
        );

        // Icon box
        c.fill(
            x + 6,
            y + 8,
            x + 22,
            y + 26,
            win ? pa(200, 26, 74, 50) : pa(200, 74, 26, 36)
        );
        c.fill(x + 6, y + 8, x + 22, y + 9, win ? A_GREEN : A_RED);
        tc(c, win ? "V" : "X", x + 14, y + 14, win ? A_GREEN : A_RED, 0.84f);

        // Result + opponent
        t(
            c,
            win ? "Vitoria" : "Derrota",
            x + 28,
            y + 6,
            win ? A_GREEN : A_RED,
            0.84f
        );
        int vw = Math.round(
            textRenderer.getWidth(win ? "Vitoria" : "Derrota") * 0.84f
        );
        t(c, "  vs  " + e.opponentName(), x + 28 + vw, y + 6, T_W, 0.84f);

        // Subtitle
        String date = DFMT.format(
            Instant.ofEpochMilli(e.playedAtMs()).atZone(ZoneId.systemDefault())
        );
        String mode = e.ranked() ? "Ranqueada" : "Casual";
        t(
            c,
            e.ladderDisplayName() + "  -  " + mode + "  -  " + date,
            x + 28,
            y + 20,
            T_DIM,
            0.64f
        );

        // ELO delta
        if (e.ranked()) {
            int d = e.ratingDelta();
            t(
                c,
                (d >= 0 ? "+" : "") + d,
                x + w - 36,
                y + 12,
                d >= 0 ? A_GREEN : A_RED,
                0.88f
            );
        }
        c.fill(x, y + 39, x + w, y + 40, C_BDR);
    }

    private void pokeRow(
        DrawContext c,
        int x,
        int y,
        int w,
        int rank,
        ArenaPokemonUsageEntryPayload e
    ) {
        c.fill(
            x,
            y,
            x + w,
            y + 16,
            rank % 2 == 0 ? pa(180, 20, 16, 34) : pa(110, 16, 12, 28)
        );
        t(c, "#" + rank, x + 6, y + 4, rank <= 3 ? A_GOLD : T_DIM, 0.66f);
        tfit(c, e.speciesName(), x + 26, y + 4, w - 148, T_W, 0.74f);
        t(c, "" + e.uses(), x + w - 110, y + 4, T_DIM, 0.64f);
        t(c, e.wins() + "W " + e.losses() + "L", x + w - 74, y + 4, T_L, 0.64f);
        int wr = e.uses() > 0 ? Math.round((e.wins() * 100f) / e.uses()) : 0;
        t(
            c,
            wr + "%",
            x + w - 22,
            y + 4,
            wr >= 60 ? A_GREEN : wr >= 45 ? A_GOLD : A_RED,
            0.64f
        );
    }

    // =========================================================================
    // PAGE: MISSIONS
    // =========================================================================

    private void renderMissions(DrawContext c, int mx, int my, int cx, int cy) {
        hits.clear();
        t(c, "Missoes", cx + 8, cy + 10, T_W, 1.0f);
        t(
            c,
            "Complete missoes para ganhar recompensas",
            cx + 8,
            cy + 22,
            T_DIM,
            0.66f
        );

        int conY = cy + 34,
            conH = H - 42;
        c.enableScissor(cx + 4, conY, cx + CW - 4, conY + conH);
        int ry = conY - qScr;

        List<QuestEntryPayload> daily = ArenaQuestClientState.getDailyQuests();
        List<QuestEntryPayload> weekly =
            ArenaQuestClientState.getWeeklyQuests();

        ry = qHeader(c, cx, ry, CW, "MISSOES DIARIAS", "Renova 24h");
        for (QuestEntryPayload q : daily) {
            mRow(c, cx + 8, ry, CW - 16, q, mx, my);
            ry += 54;
        }
        if (daily.isEmpty()) {
            t(c, "Sem missoes diarias.", cx + 16, ry + 4, T_DIM, 0.66f);
            ry += 18;
        }
        ry += 8;

        ry = qHeader(c, cx, ry, CW, "MISSOES SEMANAIS", "Renova 7d");
        for (QuestEntryPayload q : weekly) {
            mRow(c, cx + 8, ry, CW - 16, q, mx, my);
            ry += 54;
        }
        if (weekly.isEmpty()) {
            t(c, "Sem missoes semanais.", cx + 16, ry + 4, T_DIM, 0.66f);
            ry += 18;
        }

        c.disableScissor();

        int totH = (ry + qScr) - conY;
        int maxS = Math.max(0, totH - conH);
        qScr = MathHelper.clamp(qScr, 0, maxS);
        if (maxS > 0) scbar(c, cx + CW - 6, conY, 4, conH, qScr, maxS);
    }

    private int qHeader(
        DrawContext c,
        int cx,
        int y,
        int cw,
        String title,
        String tag
    ) {
        c.fill(cx + 8, y, cx + cw - 8, y + 18, pa(220, 15, 12, 25));
        t(c, title, cx + 12, y + 5, T_DIM, 0.64f);
        int tw = Math.round(textRenderer.getWidth(title) * 0.64f) + 12;
        int tbx = cx + 12 + tw + 4;
        int tbw = Math.round(textRenderer.getWidth(tag) * 0.62f) + 10;
        c.fill(tbx, y + 4, tbx + tbw, y + 13, pa(200, 38, 32, 62));
        c.fill(tbx, y + 4, tbx + tbw, y + 5, C_BDR2);
        t(c, tag, tbx + 4, y + 5, T_DIM, 0.62f);
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
        c.fill(x, y, x + w, y + 50, C_CARD);
        c.fill(x, y, x + w, y + 1, C_BDR2);

        // Accent dot
        c.fill(x + 6, y + 16, x + 18, y + 28, A_PUR2);
        c.fill(x + 6, y + 16, x + 18, y + 17, pa(90, 108, 62, 230));
        tc(c, "o", x + 12, y + 19, A_PUR, 0.78f);

        // Title + progress
        String rewardText = q.rewardDescription();
        int rewardWidth = 0;
        if (!rewardText.isBlank()) {
            rewardWidth = Math.min(
                80,
                Math.round(textRenderer.getWidth(rewardText) * 0.66f) + 14
            );
        }
        int titleWidth = w - 44 - rewardWidth;
        tfit(c, q.title(), x + 26, y + 7, titleWidth, T_W, 0.78f);
        t(
            c,
            q.currentProgress() + "/" + q.targetAmount(),
            x + 26,
            y + 19,
            T_DIM,
            0.64f
        );

        // Reward badge
        if (!rewardText.isBlank()) {
            int rw2 = rewardWidth;
            int rbx = x + w - rw2 - 4,
                rby = y + 5;
            c.fill(rbx, rby, rbx + rw2, rby + 14, pa(48, 225, 170, 38));
            c.fill(rbx, rby, rbx + rw2, rby + 1, pa(90, 225, 170, 38));
            tfit(c, rewardText, rbx + 4, rby + 3, rw2 - 8, A_GOLD, 0.66f);
        }

        // Progress bar
        int bw2 = w - 16;
        c.fill(x + 8, y + 32, x + 8 + bw2, y + 36, C_BDR2);
        int fl = (int) (bw2 * q.progressFraction());
        if (fl > 0) c.fill(
            x + 8,
            y + 32,
            x + 8 + fl,
            y + 36,
            q.claimed() ? A_PUR2 : A_PUR
        );
        c.fill(x + 8, y + 32, x + 8 + bw2, y + 33, pa(10, 255, 255, 255));

        // Claim / status
        if (q.completed() && !q.claimed()) {
            boolean bHov = over(mx, my, x + w - 68, y + 38, 64, 10);
            c.fill(
                x + w - 68,
                y + 38,
                x + w - 4,
                y + 48,
                bHov ? A_GREEN : pa(95, 58, 185, 105)
            );
            c.fill(x + w - 68, y + 38, x + w - 4, y + 39, pa(90, 58, 185, 105));
            t(
                c,
                "Resgatar",
                x + w - 65,
                y + 40,
                bHov ? p(10, 20, 10) : A_GREEN,
                0.66f
            );
            hits.add(new Hit(q.questId(), x + w - 68, y + 38, 64, 10));
        } else if (q.claimed()) {
            t(c, "Resgatado", x + w - 66, y + 40, pa(105, 58, 185, 105), 0.66f);
        }
        c.fill(x, y + 49, x + w, y + 50, C_BDR);
    }

    // =========================================================================
    // PAGE: SPECTATE
    // =========================================================================

    private void renderSpectate(DrawContext c, int mx, int my, int cx, int cy) {
        int battles = ArenaClientState.getActiveBattles();
        t(c, "Assistir Batalhas", cx + 8, cy + 10, T_W, 1.0f);
        t(c, battles + " batalha(s) ativa(s)", cx + 8, cy + 23, T_DIM, 0.68f);

        // Random spectate button (top-right)
        int rbx = cx + CW - 120,
            rby = cy + 12;
        boolean rbh = over(mx, my, rbx, rby, 116, 16);
        c.fill(rbx, rby, rbx + 116, rby + 16, rbh ? lt(A_PUR, 20) : A_PUR);
        c.fill(rbx, rby, rbx + 116, rby + 1, pa(55, 255, 255, 255));
        t(c, "Assistir Aleatorio", rbx + 8, rby + 4, T_W, 0.74f);

        if (battles == 0) {
            tc(
                c,
                "Nenhuma batalha ativa no momento.",
                cx + CW / 2,
                cy + 130,
                T_DIM,
                0.78f
            );
            return;
        }

        int ry = cy + 36;
        for (
            int i = 0;
            i < Math.min(battles, 4) && ry + 52 < cy + H - 20;
            i++
        ) {
            battleCard(c, cx + 8, ry, CW - 16, 46, i + 1, mx, my);
            ry += 52;
        }
    }

    private void battleCard(
        DrawContext c,
        int x,
        int y,
        int w,
        int h,
        int idx,
        int mx,
        int my
    ) {
        card(c, x, y, w, h);
        // Left player
        t(c, "Treinador " + idx, x + 8, y + 8, T_W, 0.80f);
        t(c, "??? ELO", x + 8, y + 20, T_DIM, 0.64f);
        // VS
        int vx = x + w / 2 - 8;
        c.fill(vx, y + h / 2 - 8, vx + 16, y + h / 2 + 8, C_CARD2);
        tc(c, "X", vx + 8, y + h / 2 - 4, T_DIM, 0.80f);
        // Right player
        t(c, "Oponente", x + w / 2 + 12, y + 8, T_W, 0.80f);
        t(c, "??? ELO", x + w / 2 + 12, y + 20, T_DIM, 0.64f);
        // Info
        t(c, "Turno ?", x + w - 152, y + 7, T_DIM, 0.64f);
        t(c, "Viewers " + idx, x + w - 152, y + 17, T_DIM, 0.64f);
        // Format tag
        c.fill(x + w - 96, y + 6, x + w - 60, y + 15, C_CARD2);
        c.fill(x + w - 96, y + 6, x + w - 60, y + 7, C_BDR2);
        t(c, "Doubles", x + w - 94, y + 7, T_L, 0.64f);
        // Assistir button
        boolean bh = over(mx, my, x + w - 56, y + h - 20, 52, 14);
        c.fill(
            x + w - 56,
            y + h - 20,
            x + w - 4,
            y + h - 6,
            bh ? lt(A_PUR, 18) : A_PUR2
        );
        c.fill(
            x + w - 56,
            y + h - 20,
            x + w - 4,
            y + h - 19,
            pa(50, 255, 255, 255)
        );
        t(c, "Assistir", x + w - 53, y + h - 17, T_W, 0.70f);
    }

    // =========================================================================
    // Mouse & scroll
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int gx = guiX((int) mx),
            gy = guiY((int) my);

        if (btn == 0) {
            // ── Sidebar navigation ─────────────────────────────────────────
            for (int i = 0; i < SB_PAGES.length; i++) {
                int ny = gT + 33 + i * 22;
                if (over(gx, gy, gL + 3, ny, SB - 6, 20)) {
                    nav(SB_PAGES[i]);
                    return true;
                }
            }

            // ── Play page ────────────────────────────────────────────────────
            if (page == Page.PLAY) {
                // "Ver todas >" missions link (Col3 header)
                int c3_x = gL + SB + C3X;
                if (over(gx, gy, c3_x + C3W - 52, gT + PY_COLS + 6, 52, 10)) {
                    nav(Page.MISSIONS);
                    return true;
                }
            }

            // ── Spectate: random button ────────────────────────────────────
            if (page == Page.SPECTATE) {
                int cx = gL + SB;
                if (over(gx, gy, cx + CW - 120, gT + 12, 116, 16)) {
                    ClientPlayNetworking.send(new SpectateArenaBattlePacket());
                    return true;
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
            gy >= gT + PY_COLS &&
            gy < gT + PY_COLS + CHAT_H
        ) {
            int maxScroll = Math.max(
                0,
                ArenaChatState.getMessages().size() - 8
            );
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
    private String validatePartyForQueue() {
        java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> party =
            ArenaClientState.getPartyPreview();

        // 1. Need 6 Pokémon
        // NOTE: partyPreview always has 6 slots (null = empty slot), so we
        // must count non-null entries, NOT use List.size().
        long filled = party
            .stream()
            .filter(p -> p != null)
            .count();
        if (filled < 6) {
            return "Precisa de 6 Pokemon no time (" + filled + "/6)";
        }

        // 2. At least one healthy Pokémon
        boolean hasHealthy = party
            .stream()
            .anyMatch(p -> p != null && !p.isFainted());
        if (!hasHealthy) {
            return "Todos os Pokemon estao debilitados.";
        }

        // 3. No duplicate held items (basic item-clause client check)
        java.util.Set<String> seenItems = new java.util.HashSet<>();
        for (com.cobblemon.mod.common.pokemon.Pokemon p : party) {
            if (p == null) continue;
            try {
                net.minecraft.item.ItemStack held = p.heldItem();
                if (held != null && !held.isEmpty()) {
                    String itemId = held.getItem().toString();
                    if (!seenItems.add(itemId)) {
                        return "Item repetido: " + itemId;
                    }
                }
            } catch (Exception ignored) {
                /* held-item API safe guard */
            }
        }

        return null; // party is valid
    }

    private void drawPlayerHead(DrawContext c, int x, int y, int size) {
        net.minecraft.client.MinecraftClient mc =
            net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        try {
            net.minecraft.client.util.SkinTextures skinTex = mc
                .getSkinProvider()
                .getSkinTextures(mc.player.getGameProfile());
            net.minecraft.util.Identifier skinId = skinTex.texture();
            float s = size / 8f; // 8 px face region → scale to target size
            c.getMatrices().push();
            c.getMatrices().translate(x, y, 0f);
            c.getMatrices().scale(s, s, 1f);
            // Face layer (UV 8,8 → 8x8 in 64x64 skin)
            c.drawTexture(skinId, 0, 0, 8f, 8f, 8, 8, 64, 64);
            // Hat overlay layer (UV 40,8 → 8x8 in 64x64 skin)
            c.drawTexture(skinId, 0, 0, 40f, 8f, 8, 8, 64, 64);
            c.getMatrices().pop();
        } catch (Exception ignored) {
            // Skin not loaded yet — draw a small initial letter as fallback
            String playerName = ArenaClientState.getPlayerName();
            String initial = (playerName == null || playerName.isBlank())
                ? "?"
                : playerName.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            c.fill(x, y, x + size, y + size, pa(80, 108, 62, 230));
            tc(c, initial, x + size / 2, y + size / 4, T_PUR, 1.1f);
        }
    }

    // =========================================================================
    // ELO tier tooltip
    // =========================================================================

    private void drawEloTierTooltip(DrawContext c, int px, int py) {
        // Tier thresholds matching ArenaClientState.getRankTitle()
        String[][] rows = {
            { "SEM RANK", "< 1000" },
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

    /** Coloured pill badge (tier display). */
    private void tierPill(DrawContext c, int x, int y, String label, int bg) {
        int pw = Math.round(textRenderer.getWidth(label) * 0.66f) + 10;
        c.fill(x, y, x + pw, y + 11, bg);
        c.fill(x, y, x + pw, y + 1, pa(28, 255, 255, 255));
        t(c, label, x + 4, y + 2, T_W, 0.66f);
    }

    private int pillW(String label) {
        return Math.round(textRenderer.getWidth(label) * 0.66f) + 10;
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

    private void t(DrawContext c, String text, int x, int y, int col, float s) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0f);
        c.getMatrices().scale(s, s, 1f);
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
        int w = Math.round(textRenderer.getWidth(text) * s);
        t(c, text, cx - w / 2, y, col, s);
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
        if (Math.round(textRenderer.getWidth(text) * s) <= maxW) {
            t(c, text, x, y, col, s);
            return;
        }
        String sfx = "...";
        for (int e = text.length() - 1; e > 0; e--) {
            String cd = text.substring(0, e).trim() + sfx;
            if (Math.round(textRenderer.getWidth(cd) * s) <= maxW) {
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
        ctx.getMatrices().translate(width / 2.0, height / 2.0, 0.0);
        ctx.getMatrices().scale(gS, gS, 1f);
        ctx.getMatrices().translate(-width / 2.0, -height / 2.0, 0.0);
    }

    /** Screen → GUI (logical) coordinate. */
    private int guiX(int sx) {
        return gS >= 1f ? sx : (int) ((sx - width / 2.0) / gS + width / 2.0);
    }

    private int guiY(int sy) {
        return gS >= 1f ? sy : (int) ((sy - height / 2.0) / gS + height / 2.0);
    }

    /** GUI → screen coordinate. */
    private double rawX(int g) {
        return gS >= 1f ? g : g * gS + (width * (1 - gS)) / 2.0;
    }

    private double rawY(int g) {
        return gS >= 1f ? g : g * gS + (height * (1 - gS)) / 2.0;
    }

    // ── Widget visual-coordinate helpers ─────────────────────────────────
    // When gS < 1 the panel is scaled around the screen centre.
    // Widgets must be placed at their *visual* (post-transform) positions so
    // that their hitboxes match what the player sees on screen.
    //
    //   wx(p)  =  (p - cx) * gS + cx   (visual X of a logical GUI X)
    //   wy(p)  =  (p - cy) * gS + cy   (visual Y of a logical GUI Y)
    //   ws(s)  =  s * gS               (visual size of a logical dimension)
    //
    // At gS == 1 all three functions are identity, so there is no overhead.

    private int wx(int x) {
        return gS >= 1f
            ? x
            : (int) Math.round((x - width / 2.0) * gS + width / 2.0);
    }

    private int wy(int y) {
        return gS >= 1f
            ? y
            : (int) Math.round((y - height / 2.0) * gS + height / 2.0);
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
        for (ArenaLadder l : ArenaClientState.getActiveRankedLadders())
            if (l.getBattleTypeId().equals(fid)) {
                RankedLadderSnapshot s = ArenaClientState.getRankedSnapshotById(
                    l.getId()
                );
                if (s != null) return s.rankedRating();
            }
        return ArenaClientState.getRankedRating();
    }

    private int winsForFmt(String fid) {
        for (ArenaLadder l : ArenaClientState.getActiveRankedLadders())
            if (l.getBattleTypeId().equals(fid)) {
                RankedLadderSnapshot s = ArenaClientState.getRankedSnapshotById(
                    l.getId()
                );
                if (s != null) return s.rankedWins();
            }
        return ArenaClientState.getRankedWins();
    }

    private int gamesForFmt(String fid) {
        for (ArenaLadder l : ArenaClientState.getActiveRankedLadders())
            if (l.getBattleTypeId().equals(fid)) {
                RankedLadderSnapshot s = ArenaClientState.getRankedSnapshotById(
                    l.getId()
                );
                if (s != null) return s.rankedWins() + s.rankedLosses();
            }
        return (
            ArenaClientState.getRankedWins() +
            ArenaClientState.getRankedLosses()
        );
    }

    private static String getRankTierName(int elo) {
        if (elo >= 2400) return "Grao-Mestre";
        if (elo >= 2200) return "Mestre";
        if (elo >= 2000) return "Diamante";
        if (elo >= 1800) return "Platina";
        if (elo >= 1600) return "Ouro";
        if (elo >= 1400) return "Prata";
        return elo >= 1000 ? "Bronze" : "Sem Rank";
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
            default -> "SEM RANK";
        };
    }

    private static int eloTierBg(int elo) {
        if (elo >= 2400) return TIER_GM;
        if (elo >= 2200) return TIER_MST;
        if (elo >= 2000) return TIER_DIA;
        if (elo >= 1800) return TIER_PLT;
        if (elo >= 1600) return TIER_GLD;
        if (elo >= 1400) return TIER_SLV;
        return TIER_BRZ;
    }

    private static int nextTierElo(int elo) {
        if (elo < 1400) return 1400;
        if (elo < 1600) return 1600;
        if (elo < 1800) return 1800;
        if (elo < 2000) return 2000;
        if (elo < 2200) return 2200;
        if (elo < 2400) return 2400;
        return 3000;
    }

    private static int prevTierElo(int elo) {
        if (elo >= 2400) return 2400;
        if (elo >= 2200) return 2200;
        if (elo >= 2000) return 2000;
        if (elo >= 1800) return 1800;
        if (elo >= 1600) return 1600;
        if (elo >= 1400) return 1400;
        return 1000;
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
}
