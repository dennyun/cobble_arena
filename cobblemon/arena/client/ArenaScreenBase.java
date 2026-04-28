package cobblemon.arena.client;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public abstract class ArenaScreenBase extends Screen {

    protected static final int GUI_WIDTH = 384;
    protected static final int GUI_HEIGHT = 300;
    protected static final int PANEL_BG = color(26, 9, 13, 248);
    protected static final int PANEL_INSET = color(36, 14, 18, 255);
    protected static final int SECTION_BG = color(42, 16, 22, 255);
    protected static final int SECTION_ALT = color(46, 18, 25, 255);
    protected static final int SECTION_INSET = color(24, 8, 12, 255);
    protected static final int BORDER_COLOR = color(132, 54, 62, 255);
    protected static final int BORDER_HIGHLIGHT = color(164, 68, 74, 255);
    protected static final int QUICK_ACCENT = color(255, 118, 62, 255);
    protected static final int RANKED_ACCENT = color(235, 204, 106, 255);
    protected static final int SUCCESS_ACCENT = color(112, 220, 132, 255);
    protected static final int WARNING_ACCENT = color(235, 173, 76, 255);
    protected static final int INFO_ACCENT = color(112, 184, 248, 255);
    protected static final int TEXT_PRIMARY = color(248, 242, 236, 255);
    protected static final int TEXT_SECONDARY = color(199, 183, 171, 255);
    protected static final int TEXT_DIM = color(144, 126, 119, 255);
    protected static final float TITLE_SCALE = 0.92F;
    protected static final float SUBTITLE_SCALE = 0.8F;
    protected static final float SECTION_TITLE_SCALE = 0.9F;
    protected static final float BODY_SCALE = 0.84F;
    protected static final float SMALL_SCALE = 0.76F;
    protected static final float METRIC_LABEL_SCALE = 0.78F;
    protected static final float METRIC_VALUE_SCALE = 0.7F;
    protected int guiLeft;
    protected int guiTop;
    /** < 1.0 when the GUI is down-scaled to fit the screen; 1.0 otherwise. */
    protected float guiScale = 1.0f;

    protected ArenaScreenBase(Text title) {
        super(title);
    }

    protected void updateGuiPosition() {
        computeScale(GUI_WIDTH, GUI_HEIGHT);
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
    }

    // ── Auto-scaling ────────────────────────────────────────────────────────────

    /**
     * Sets {@link #guiScale} so the panel (panelW x panelH) always fits within
     * the current scaled-GUI screen dimensions, leaving a 4 px safe margin.
     * When the panel already fits, guiScale is 1.0 (no transform applied).
     */
    protected void computeScale(int panelW, int panelH) {
        float maxW = Math.max(1, this.width - 4);
        float maxH = Math.max(1, this.height - 4);
        float sw = panelW > maxW ? maxW / panelW : 1.0f;
        float sh = panelH > maxH ? maxH / panelH : 1.0f;
        guiScale = Math.min(sw, sh);
    }

    /**
     * Pushes a uniform scale matrix centred on the screen centre.
     * Only applies a transform when guiScale &lt; 1.
     * Must always be paired with {@link #popGuiScale}.
     */
    protected void pushGuiScale(DrawContext context) {
        if (guiScale < 1.0f) {
            context.getMatrices().push();
            context
                .getMatrices()
                .translate(this.width / 2.0, this.height / 2.0, 0.0);
            context.getMatrices().scale(guiScale, guiScale, 1.0f);
            context
                .getMatrices()
                .translate(-this.width / 2.0, -this.height / 2.0, 0.0);
        }
    }

    protected void popGuiScale(DrawContext context) {
        if (guiScale < 1.0f) {
            context.getMatrices().pop();
        }
    }

    /**
     * Converts a raw screen X coordinate (as received from Minecraft's mouse
     * events) to the logical GUI coordinate used by widgets, reversing the
     * centre-anchored scale transform so hit-detection stays accurate.
     */
    protected double toGuiX(double screenX) {
        if (guiScale >= 1.0f) return screenX;
        return (screenX - this.width / 2.0) / guiScale + this.width / 2.0;
    }

    protected double toGuiY(double screenY) {
        if (guiScale >= 1.0f) return screenY;
        return (screenY - this.height / 2.0) / guiScale + this.height / 2.0;
    }

    // ── Background ──────────────────────────────────────────────────────────────

    /**
     * Suppress Minecraft's built-in in-game dark overlay and background blur.
     * Every Arena screen draws its own full-screen background before rendering
     * widgets, so the default overlay would appear on top and cause the
     * "blurred content" visual artefact.
     */
    @Override
    public void renderBackground(
        DrawContext context,
        int mouseX,
        int mouseY,
        float delta
    ) {
        // intentionally empty
    }

    protected void renderScreenFrame(
        DrawContext graphics,
        String subtitle,
        String badgeText,
        int badgeAccent
    ) {
        this.drawPanel(graphics, this.guiLeft, this.guiTop, 384, 300);
        this.drawHeader(graphics, subtitle, badgeText, badgeAccent);
    }

    protected void renderFullScreenBg(DrawContext graphics) {
        graphics.fill(0, 0, this.width, this.height, color(0, 0, 0, 182));
        graphics.fill(0, 0, this.width, this.height / 3, color(52, 22, 18, 48));
    }

    @Override
    public void render(
        DrawContext graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        // Pass inverse-transformed mouse coordinates so widget hover detection
        // stays correct when guiScale < 1.
        super.render(
            graphics,
            (int) toGuiX(mouseX),
            (int) toGuiY(mouseY),
            delta
        );
    }

    protected void drawPanel(DrawContext graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_INSET);
        graphics.fill(x, y, x + w, y + 2, BORDER_HIGHLIGHT);
        graphics.fill(x, y + h - 2, x + w, y + h, darken(BORDER_COLOR, 18));
        graphics.fill(x, y, x + 2, y + h, BORDER_HIGHLIGHT);
        graphics.fill(x + w - 2, y, x + w, y + h, darken(BORDER_COLOR, 18));
        graphics.fill(x + 3, y + 3, x + w - 3, y + 7, color(255, 255, 255, 10));
        graphics.fill(
            x + 3,
            y + h - 8,
            x + w - 3,
            y + h - 3,
            color(0, 0, 0, 45)
        );
    }

    protected void drawHeader(
        DrawContext graphics,
        String subtitle,
        String badgeText,
        int badgeAccent
    ) {
        int headerY = this.guiTop + 2;
        int headerH = 22;

        for (int y = 0; y < headerH; y++) {
            float progress = (float) y / headerH;
            int alpha = 180 + (int) (progress * 52.0F);
            graphics.fill(
                this.guiLeft + 2,
                headerY + y,
                this.guiLeft + 384 - 2,
                headerY + y + 1,
                color(44, 24, 28, alpha)
            );
        }

        this.drawCenteredScaledText(
            graphics,
            this.title,
            this.guiLeft + 192,
            headerY + 5,
            QUICK_ACCENT,
            0.92F
        );
        this.drawCenteredScaledText(
            graphics,
            subtitle,
            this.guiLeft + 192,
            headerY + 14,
            TEXT_DIM,
            0.8F
        );
        if (badgeText != null && !badgeText.isBlank()) {
            this.drawBadgeRight(
                graphics,
                this.guiLeft + 384 - 8,
                headerY + 4,
                badgeText,
                badgeAccent,
                0.76F
            );
        }

        graphics.fill(
            this.guiLeft + 6,
            headerY + headerH - 1,
            this.guiLeft + 384 - 6,
            headerY + headerH,
            BORDER_HIGHLIGHT
        );
    }

    protected void drawSection(
        DrawContext graphics,
        int x,
        int y,
        int w,
        int h,
        int fill
    ) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(
            x + 1,
            y + 1,
            x + w - 1,
            y + h - 1,
            mix(fill, color(255, 255, 255, 255), 0.04F)
        );
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
        graphics.fill(x, y, x + w, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, darken(BORDER_COLOR, 18));
        graphics.fill(x, y, x + 1, y + h, BORDER_HIGHLIGHT);
        graphics.fill(x + w - 1, y, x + w, y + h, darken(BORDER_COLOR, 18));
        graphics.fill(x + 2, y + 2, x + w - 2, y + 5, color(255, 255, 255, 8));
        graphics.fill(
            x + 2,
            y + h - 6,
            x + w - 2,
            y + h - 2,
            color(0, 0, 0, 30)
        );
    }

    protected void drawInsetBand(
        DrawContext graphics,
        int x,
        int y,
        int w,
        int h
    ) {
        graphics.fill(x, y, x + w, y + h, SECTION_INSET);
        graphics.fill(x, y, x + w, y + 1, darken(BORDER_HIGHLIGHT, 18));
        graphics.fill(x, y + h - 1, x + w, y + h, darken(BORDER_COLOR, 28));
        graphics.fill(x, y, x + 1, y + h, darken(BORDER_HIGHLIGHT, 18));
        graphics.fill(x + w - 1, y, x + w, y + h, darken(BORDER_COLOR, 28));
    }

    protected void drawSectionTitle(
        DrawContext graphics,
        int x,
        int y,
        String title,
        int color
    ) {
        this.drawScaledText(graphics, title, x, y, color, 0.9F);
        graphics.fill(
            x,
            y + 10,
            x +
                Math.min(
                    92,
                    Math.round(this.textRenderer.getWidth(title) * 0.9F) + 10
                ),
            y + 11,
            darken(color, 20)
        );
    }

    protected void drawMetricChip(
        DrawContext graphics,
        int x,
        int y,
        int w,
        String label,
        String value,
        int accent
    ) {
        int chipHeight = 16;
        graphics.fill(x, y, x + w, y + chipHeight, SECTION_INSET);
        graphics.fill(
            x + 1,
            y,
            x + w - 1,
            y + 2,
            mix(accent, SECTION_INSET, 0.28F)
        );
        graphics.fill(x + 1, y + 2, x + w - 1, y + 3, color(255, 255, 255, 10));
        graphics.fill(
            x + 1,
            y + chipHeight - 3,
            x + w - 1,
            y + chipHeight - 1,
            color(0, 0, 0, 40)
        );
        graphics.fill(x, y, x + w, y + 1, darken(BORDER_HIGHLIGHT, 10));
        graphics.fill(
            x,
            y + chipHeight - 1,
            x + w,
            y + chipHeight,
            darken(BORDER_COLOR, 22)
        );
        graphics.fill(
            x,
            y,
            x + 1,
            y + chipHeight,
            darken(BORDER_HIGHLIGHT, 10)
        );
        graphics.fill(
            x + w - 1,
            y,
            x + w,
            y + chipHeight,
            darken(BORDER_COLOR, 22)
        );
        this.drawScaledText(
            graphics,
            label,
            x + 4,
            y + 2,
            TEXT_SECONDARY,
            0.78F
        );
        this.drawScaledText(graphics, value, x + 4, y + 9, accent, 0.7F);
    }

    protected void drawBadgeRight(
        DrawContext graphics,
        int rightX,
        int y,
        String text,
        int accent,
        float scale
    ) {
        ArenaRankBadgeRenderer.drawBadgeRight(
            graphics,
            this.textRenderer,
            rightX,
            y,
            text,
            accent,
            scale
        );
    }

    protected void drawWrappedText(
        DrawContext graphics,
        String text,
        int x,
        int y,
        int width,
        int color,
        int maxLines
    ) {
        List<OrderedText> lines = this.textRenderer.wrapLines(
            Text.literal(text),
            Math.max(1, Math.round(width / 0.84F))
        );
        int lineCount = Math.min(lines.size(), maxLines);

        for (int i = 0; i < lineCount; i++) {
            this.drawScaledText(
                graphics,
                lines.get(i),
                x,
                y + i * 8,
                color,
                0.84F
            );
        }
    }

    protected void drawScaledText(
        DrawContext graphics,
        String text,
        int x,
        int y,
        int color,
        float scale
    ) {
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x, y, 0.0F);
        graphics.getMatrices().scale(scale, scale, 1.0F);
        graphics.drawText(this.textRenderer, text, 0, 0, color, false);
        graphics.getMatrices().pop();
    }

    protected void drawScaledText(
        DrawContext graphics,
        Text text,
        int x,
        int y,
        int color,
        float scale
    ) {
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x, y, 0.0F);
        graphics.getMatrices().scale(scale, scale, 1.0F);
        graphics.drawText(this.textRenderer, text, 0, 0, color, false);
        graphics.getMatrices().pop();
    }

    protected void drawScaledText(
        DrawContext graphics,
        OrderedText text,
        int x,
        int y,
        int color,
        float scale
    ) {
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x, y, 0.0F);
        graphics.getMatrices().scale(scale, scale, 1.0F);
        graphics.drawText(this.textRenderer, text, 0, 0, color, false);
        graphics.getMatrices().pop();
    }

    protected void drawCenteredScaledText(
        DrawContext graphics,
        String text,
        int centerX,
        int y,
        int color,
        float scale
    ) {
        int scaledWidth = Math.round(this.textRenderer.getWidth(text) * scale);
        this.drawScaledText(
            graphics,
            text,
            centerX - scaledWidth / 2,
            y,
            color,
            scale
        );
    }

    protected void drawCenteredScaledText(
        DrawContext graphics,
        Text text,
        int centerX,
        int y,
        int color,
        float scale
    ) {
        int scaledWidth = Math.round(this.textRenderer.getWidth(text) * scale);
        this.drawScaledText(
            graphics,
            text,
            centerX - scaledWidth / 2,
            y,
            color,
            scale
        );
    }

    protected String fitText(String text, int maxWidth, float scale) {
        if (text != null && !text.isBlank()) {
            if (
                Math.round(this.textRenderer.getWidth(text) * scale) <= maxWidth
            ) {
                return text;
            } else {
                String suffix = "...";

                for (int end = text.length() - 1; end > 0; end--) {
                    String candidate = text.substring(0, end).trim() + suffix;
                    if (
                        Math.round(
                            this.textRenderer.getWidth(candidate) * scale
                        ) <=
                        maxWidth
                    ) {
                        return candidate;
                    }
                }

                return suffix;
            }
        } else {
            return "";
        }
    }

    // ── Mouse coordinate forwarding ─────────────────────────────────────────────
    // Each mouse event received from Minecraft carries raw screen-space coords.
    // We transform them to GUI-space before forwarding so widgets whose positions
    // were set in GUI-space respond to clicks / hovers at the right visual spot.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(toGuiX(mouseX), toGuiY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(toGuiX(mouseX), toGuiY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(
        double mouseX,
        double mouseY,
        int button,
        double deltaX,
        double deltaY
    ) {
        return super.mouseDragged(
            toGuiX(mouseX),
            toGuiY(mouseY),
            button,
            deltaX,
            deltaY
        );
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount
    ) {
        return super.mouseScrolled(
            toGuiX(mouseX),
            toGuiY(mouseY),
            horizontalAmount,
            verticalAmount
        );
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toGuiX(mouseX), toGuiY(mouseY));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected static String formatStreak(int streak) {
        return streak > 0 ? "+" + streak : String.valueOf(streak);
    }

    protected static int getWinRate(int wins, int losses) {
        int total = wins + losses;
        return total <= 0 ? 0 : Math.round((wins * 100.0F) / total);
    }

    protected static int rankAccentColor(String tier) {
        return switch (tier) {
            case "Grão-Mestre" -> color(255, 110, 110, 255);
            case "Mestre" -> color(255, 170, 86, 255);
            case "Diamante" -> color(110, 214, 255, 255);
            case "Platina" -> color(174, 196, 255, 255);
            case "Ouro" -> color(235, 204, 106, 255);
            case "Prata" -> color(196, 206, 216, 255);
            case "Bronze" -> color(192, 134, 92, 255);
            default -> color(156, 132, 128, 255);
        };
    }

    protected static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected static int darken(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected static int mix(int first, int second, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int a1 = (first >> 24) & 0xFF;
        int r1 = (first >> 16) & 0xFF;
        int g1 = (first >> 8) & 0xFF;
        int b1 = first & 0xFF;
        int a2 = (second >> 24) & 0xFF;
        int r2 = (second >> 16) & 0xFF;
        int g2 = (second >> 8) & 0xFF;
        int b2 = second & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * clamped);
        int r = Math.round(r1 + (r2 - r1) * clamped);
        int g = Math.round(g1 + (g2 - g1) * clamped);
        int b = Math.round(b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
