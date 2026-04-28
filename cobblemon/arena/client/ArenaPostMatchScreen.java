package cobblemon.arena.client;

import cobblemon.arena.network.PostMatchResultsPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ArenaPostMatchScreen extends Screen {

    // ── Panel dimensions ───────────────────────────────────────────────────
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 200;
    private static final int BTN_W = 100;
    private static final int BTN_H = 18;

    // ── Colours (standalone — does not extend ArenaScreenBase) ────────────
    private static final int BG_OVERLAY = color(0, 0, 0, 190);
    private static final int PANEL_BG = color(22, 8, 12, 252);
    private static final int PANEL_INSET = color(32, 12, 16, 255);
    private static final int SECTION_BG = color(40, 15, 20, 255);
    private static final int BORDER_HI = color(164, 68, 74, 255);
    private static final int BORDER_LO = color(100, 40, 46, 255);
    private static final int RANKED_ACCENT = color(235, 204, 106, 255);
    private static final int CASUAL_ACCENT = color(255, 118, 62, 255);
    private static final int WIN_ACCENT = color(112, 220, 132, 255);
    private static final int LOSS_ACCENT = color(220, 102, 102, 255);
    private static final int INFO_ACCENT = color(112, 184, 248, 255);
    private static final int WARN_ACCENT = color(235, 173, 76, 255);
    private static final int TEXT_PRI = color(248, 242, 236, 255);
    private static final int TEXT_SEC = color(199, 183, 171, 255);
    private static final int TEXT_DIM = color(144, 126, 119, 255);

    // ── State ──────────────────────────────────────────────────────────────
    private PostMatchResultsPacket results;
    private int panelLeft;
    private int panelTop;

    // ─────────────────────────────────────────────────────────────────────

    public ArenaPostMatchScreen() {
        super(Text.translatable("gui.cobblemon_arena.title.match_results"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        if (results == null) {
            results = ArenaPostMatchClientState.poll();
            if (results == null) {
                MinecraftClient.getInstance().setScreen(null);
                return;
            }
            recomputeLayout();
            rebuildButtons();
        }
    }

    /** Recompute panel position (called after results arrive and on resize). */
    private void recomputeLayout() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
    }

    /** Add the Close / Continue button. */
    private void rebuildButtons() {
        clearChildren();
        int btnX = panelLeft + (PANEL_W - BTN_W) / 2;
        int btnY = panelTop + PANEL_H - BTN_H - 10;
        addDrawableChild(
            ButtonWidget.builder(Text.literal("✖  Fechar"), b -> close())
                .dimensions(btnX, btnY, BTN_W, BTN_H)
                .build()
        );
    }

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
        if (results != null) rebuildButtons();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Suppress Minecraft's default gaussian blur behind the screen. */
    @Override
    public void renderBackground(
        DrawContext ctx,
        int mouseX,
        int mouseY,
        float delta
    ) {
        // intentionally empty — solid overlay drawn in render()
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (results == null) {
            super.render(ctx, mx, my, delta);
            return;
        }

        // Full-screen dark vignette
        ctx.fill(0, 0, width, height, BG_OVERLAY);

        // Centred scale so the panel always fits even on tiny windows
        float scale = computeScale();
        if (scale < 1f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(width / 2.0, height / 2.0, 0.0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.getMatrices().translate(-width / 2.0, -height / 2.0, 0.0);
        }

        drawPanel(ctx);
        drawHeader(ctx);
        drawResultBanner(ctx);
        drawStatsSection(ctx);
        drawEloSection(ctx);

        super.render(ctx, mx, my, delta); // widgets (Close button)

        if (scale < 1f) ctx.getMatrices().pop();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Drawing helpers
    // ══════════════════════════════════════════════════════════════════════

    /** Bordered panel background. */
    private void drawPanel(DrawContext ctx) {
        int x = panelLeft,
            y = panelTop,
            w = PANEL_W,
            h = PANEL_H;
        ctx.fill(x, y, x + w, y + h, PANEL_BG);
        ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_INSET);
        ctx.fill(x, y, x + w, y + 2, BORDER_HI);
        ctx.fill(x, y + h - 2, x + w, y + h, BORDER_LO);
        ctx.fill(x, y, x + 2, y + h, BORDER_HI);
        ctx.fill(x + w - 2, y, x + w, y + h, BORDER_LO);
        ctx.fill(x + 3, y + 3, x + w - 3, y + 6, color(255, 255, 255, 10));
        ctx.fill(x + 3, y + h - 6, x + w - 3, y + h - 3, color(0, 0, 0, 40));
    }

    /**
     * Top bar: mode label (Ranqueado / Casual) centred,
     * result badge (VITÓRIA / DERROTA) right-aligned.
     */
    private void drawHeader(DrawContext ctx) {
        int hdrY = panelTop + 4;
        int hdrH = 18;
        int cx = panelLeft + PANEL_W / 2;

        // Gradient band
        for (int row = 0; row < hdrH; row++) {
            float p = (float) row / hdrH;
            int a = 160 + (int) (p * 60);
            ctx.fill(
                panelLeft + 2,
                hdrY + row,
                panelLeft + PANEL_W - 2,
                hdrY + row + 1,
                color(44, 18, 24, a)
            );
        }

        // Mode label
        String modeLabel = results.ranked() ? "★  Ranqueado" : "◆  Casual";
        int modeColor = results.ranked() ? RANKED_ACCENT : CASUAL_ACCENT;
        drawCentredScaled(ctx, modeLabel, cx, hdrY + 5, modeColor, 0.84f);

        // Separator
        ctx.fill(
            panelLeft + 6,
            hdrY + hdrH - 1,
            panelLeft + PANEL_W - 6,
            hdrY + hdrH,
            BORDER_HI
        );
    }

    /**
     * Big VITÓRIA / DERROTA banner with coloured background strip.
     * Also shows "vs <Opponent>" beneath.
     */
    private void drawResultBanner(DrawContext ctx) {
        boolean win = results.victory();
        int accent = win ? WIN_ACCENT : LOSS_ACCENT;
        int bannerY = panelTop + 24;
        int bannerH = 30;
        int cx = panelLeft + PANEL_W / 2;

        // Tinted strip
        ctx.fill(
            panelLeft + 4,
            bannerY,
            panelLeft + PANEL_W - 4,
            bannerY + bannerH,
            mix(PANEL_INSET, accent, 0.08f)
        );
        ctx.fill(
            panelLeft + 4,
            bannerY,
            panelLeft + 6,
            bannerY + bannerH,
            accent
        );
        ctx.fill(
            panelLeft + PANEL_W - 6,
            bannerY,
            panelLeft + PANEL_W - 4,
            bannerY + bannerH,
            darken(accent, 30)
        );

        // Large result text
        String result = win ? "VITÓRIA!" : "DERROTA";
        drawCentredScaled(ctx, result, cx, bannerY + 4, accent, 1.6f);

        // Ladder name
        String ladderLabel = results.ladderDisplayName();
        drawCentredScaled(ctx, ladderLabel, cx, bannerY + 21, TEXT_DIM, 0.78f);

        // "vs Opponent" below banner
        String vsLabel = "vs  " + results.opponentName();
        drawCentredScaled(
            ctx,
            vsLabel,
            cx,
            bannerY + bannerH + 4,
            TEXT_SEC,
            0.84f
        );
    }

    /**
     * Stats section: Ranked W/L record · streak · ladder rank.
     */
    private void drawStatsSection(DrawContext ctx) {
        int secX = panelLeft + 8;
        int secY = panelTop + 68;
        int secW = PANEL_W - 16;
        int secH = 34;

        drawSection(ctx, secX, secY, secW, secH);

        int chipW = (secW - 12) / 3;

        // Record W/L
        drawChip(
            ctx,
            secX + 4,
            secY + 4,
            chipW,
            "Recorde",
            results.wins() + " - " + results.losses(),
            results.victory() ? WIN_ACCENT : TEXT_PRI
        );

        // Win streak
        String streakVal =
            (results.streak() >= 0 ? "+" : "") + results.streak();
        drawChip(
            ctx,
            secX + 4 + chipW + 4,
            secY + 4,
            chipW,
            "Sequência",
            streakVal,
            results.streak() > 0
                ? WIN_ACCENT
                : (results.streak() < 0 ? LOSS_ACCENT : TEXT_DIM)
        );

        // Ladder rank
        String rankVal =
            results.rank() > 0
                ? "#" + results.rank() + " / " + results.totalRankedPlayers()
                : "--";
        drawChip(
            ctx,
            secX + 4 + (chipW + 4) * 2,
            secY + 4,
            chipW,
            "Ranking",
            rankVal,
            INFO_ACCENT
        );
    }

    /**
     * ELO change section (only for ranked matches).
     * For casual: shows "Sem alteração de Elo".
     */
    private void drawEloSection(DrawContext ctx) {
        int secX = panelLeft + 8;
        int secY = panelTop + 108;
        int secW = PANEL_W - 16;
        int secH = 40;
        int cx = panelLeft + PANEL_W / 2;

        drawSection(ctx, secX, secY, secW, secH);

        if (!results.ranked()) {
            drawCentredScaled(
                ctx,
                "Partida casual — sem alteração de Elo",
                cx,
                secY + 14,
                TEXT_DIM,
                0.78f
            );
            return;
        }

        boolean gain = results.ratingDelta() >= 0;
        int accent = gain ? WIN_ACCENT : LOSS_ACCENT;
        String delta = (gain ? "+" : "") + results.ratingDelta();
        String arrow = gain ? "▲" : "▼";

        // Before → after
        String eloLine =
            results.ratingBefore() + "  →  " + results.ratingAfter();
        drawCentredScaled(ctx, eloLine, cx, secY + 6, TEXT_PRI, 0.92f);

        // Delta
        String deltaLine = arrow + "  " + delta + "  Elo";
        drawCentredScaled(ctx, deltaLine, cx, secY + 20, accent, 1.1f);

        // Tier label at bottom-right
        String tierLabel = "  " + getRankTier(results.ratingAfter()) + "  ";
        int tierW = Math.round(textRenderer.getWidth(tierLabel) * 0.76f);
        int tierX = secX + secW - tierW - 2;
        ctx.fill(
            tierX,
            secY + secH - 12,
            tierX + tierW,
            secY + secH - 2,
            mix(PANEL_INSET, accent, 0.18f)
        );
        drawScaled(
            ctx,
            tierLabel,
            tierX,
            secY + secH - 11,
            rankTierColor(results.ratingAfter()),
            0.76f
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Low-level drawing primitives
    // ══════════════════════════════════════════════════════════════════════

    private void drawSection(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, SECTION_BG);
        ctx.fill(
            x + 1,
            y + 1,
            x + w - 1,
            y + h - 1,
            mix(SECTION_BG, color(255, 255, 255, 255), 0.04f)
        );
        ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, SECTION_BG);
        ctx.fill(x, y, x + w, y + 1, BORDER_HI);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER_LO);
        ctx.fill(x, y, x + 1, y + h, BORDER_HI);
        ctx.fill(x + w - 1, y, x + w, y + h, BORDER_LO);
        ctx.fill(x + 2, y + 2, x + w - 2, y + 4, color(255, 255, 255, 8));
    }

    /** Metric chip: small label above, bigger value below, optional left stripe. */
    private void drawChip(
        DrawContext ctx,
        int x,
        int y,
        int w,
        String label,
        String value,
        int accent
    ) {
        int h = 26;
        ctx.fill(
            x,
            y,
            x + w,
            y + h,
            mix(PANEL_INSET, color(0, 0, 0, 255), 0.2f)
        );
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.fill(x, y, x + w, y + 1, mix(BORDER_HI, accent, 0.3f));
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER_LO);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER_LO);

        int cx = x + w / 2;
        drawCentredScaled(ctx, label, cx, y + 3, TEXT_DIM, 0.72f);
        drawCentredScaled(ctx, value, cx, y + 13, accent, 0.88f);
    }

    private void drawScaled(
        DrawContext ctx,
        String text,
        int x,
        int y,
        int colour,
        float scale
    ) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0f);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(textRenderer, text, 0, 0, colour, false);
        ctx.getMatrices().pop();
    }

    private void drawCentredScaled(
        DrawContext ctx,
        String text,
        int cx,
        int y,
        int colour,
        float scale
    ) {
        int w = Math.round(textRenderer.getWidth(text) * scale);
        drawScaled(ctx, text, cx - w / 2, y, colour, scale);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private float computeScale() {
        float maxW = Math.max(1, width - 4);
        float maxH = Math.max(1, height - 4);
        float sw = PANEL_W > maxW ? maxW / PANEL_W : 1f;
        float sh = PANEL_H > maxH ? maxH / PANEL_H : 1f;
        return Math.min(sw, sh);
    }

    private static String getRankTier(int elo) {
        if (elo >= 2400) return "Grão-Mestre";
        if (elo >= 2200) return "Mestre";
        if (elo >= 2000) return "Diamante";
        if (elo >= 1800) return "Platina";
        if (elo >= 1600) return "Ouro";
        if (elo >= 1400) return "Prata";
        return elo >= 1000 ? "Bronze" : "Sem Rank";
    }

    private static int rankTierColor(int elo) {
        if (elo >= 2400) return color(255, 110, 110, 255);
        if (elo >= 2200) return color(255, 170, 86, 255);
        if (elo >= 2000) return color(110, 214, 255, 255);
        if (elo >= 1800) return color(174, 196, 255, 255);
        if (elo >= 1600) return color(235, 204, 106, 255);
        if (elo >= 1400) return color(196, 206, 216, 255);
        return elo >= 1000
            ? color(192, 134, 92, 255)
            : color(156, 132, 128, 255);
    }

    // ── Colour math ────────────────────────────────────────────────────────

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int c, int amt) {
        int a = (c >> 24) & 0xFF;
        int r = Math.max(0, ((c >> 16) & 0xFF) - amt);
        int g = Math.max(0, ((c >> 8) & 0xFF) - amt);
        int b = Math.max(0, (c & 0xFF) - amt);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF,
            ra = (a >> 16) & 0xFF,
            ga = (a >> 8) & 0xFF,
            ba = a & 0xFF;
        int ab = (b >> 24) & 0xFF,
            rb = (b >> 16) & 0xFF,
            gb = (b >> 8) & 0xFF,
            bb = b & 0xFF;
        return (
            (Math.round(aa + (ab - aa) * t) << 24) |
            (Math.round(ra + (rb - ra) * t) << 16) |
            (Math.round(ga + (gb - ga) * t) << 8) |
            Math.round(ba + (bb - ba) * t)
        );
    }
}
