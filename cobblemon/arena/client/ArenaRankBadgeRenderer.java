package cobblemon.arena.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

final class ArenaRankBadgeRenderer {
    private static final int BADGE_BG = color(24, 8, 12, 255);
    private static final int BADGE_BG_ALT = color(34, 12, 16, 255);
    private static final int TEXT_PRIMARY = color(248, 242, 236, 255);
    private static final int TEXT_SHADOW = color(82, 54, 50, 255);
    private static final int DIVIDER = color(96, 36, 40, 255);

    private ArenaRankBadgeRenderer() {
    }

    static void drawBadgeRight(DrawContext graphics, TextRenderer font, int rightX, int y, String text, int accent, float scale) {
        if (isRankTier(text)) {
            drawRankBadgeRight(graphics, font, rightX, y, text, accent, scale);
        } else {
            drawGenericBadgeRight(graphics, font, rightX, y, text, accent, scale);
        }
    }

    private static void drawGenericBadgeRight(DrawContext graphics, TextRenderer font, int rightX, int y, String text, int accent, float scale) {
        int width = Math.round(font.getWidth(text) * scale) + 10;
        int x = rightX - width;
        int height = 11;
        graphics.fill(x, y, rightX, y + height, BADGE_BG);
        graphics.fill(x, y, rightX, y + 1, accent);
        graphics.fill(x, y + height - 1, rightX, y + height, darken(accent, 25));
        graphics.fill(x, y, x + 1, y + height, accent);
        graphics.fill(rightX - 1, y, rightX, y + height, darken(accent, 25));
        graphics.fill(x + 1, y + 1, rightX - 1, y + 2, color(255, 255, 255, 12));
        drawCenteredScaledText(graphics, font, text, x + width / 2, y + 2, TEXT_PRIMARY, scale);
    }

    private static void drawRankBadgeRight(DrawContext graphics, TextRenderer font, int rightX, int y, String text, int accent, float scale) {
        int emblemWidth = 15;
        int textWidth = Math.round(font.getWidth(text) * scale);
        int width = textWidth + emblemWidth + 14;
        int x = rightX - width;
        int height = 13;
        int topGlow = mix(accent, color(255, 255, 255, 255), 0.22F);
        int sideShadow = darken(accent, 40);
        int emblemBg = mix(BADGE_BG_ALT, accent, 0.12F);

        graphics.fill(x, y, rightX, y + height, BADGE_BG);
        graphics.fill(x + 1, y + 1, rightX - 1, y + height - 1, BADGE_BG_ALT);
        graphics.fill(x, y, rightX, y + 1, topGlow);
        graphics.fill(x, y + height - 1, rightX, y + height, darken(accent, 28));
        graphics.fill(x, y, x + 1, y + height, accent);
        graphics.fill(rightX - 1, y, rightX, y + height, sideShadow);
        graphics.fill(x + 1, y + 1, rightX - 1, y + 2, color(255, 255, 255, 10));
        graphics.fill(x + 2, y + 2, x + emblemWidth, y + height - 2, emblemBg);
        graphics.fill(x + emblemWidth, y + 2, x + emblemWidth + 1, y + height - 2, DIVIDER);
        graphics.fill(x + 2, y + height - 3, rightX - 1, y + height - 1, color(0, 0, 0, 28));

        drawRankEmblem(graphics, x + 4, y + 2, text, accent);
        int textZoneLeft = x + emblemWidth + 3;
        int textZoneWidth = rightX - 2 - textZoneLeft;
        drawCenteredScaledText(graphics, font, text, textZoneLeft + textZoneWidth / 2, y + 3, TEXT_PRIMARY, scale);
    }

    private static void drawRankEmblem(DrawContext graphics, int x, int y, String tier, int accent) {
        int bright = mix(accent, color(255, 255, 255, 255), 0.4F);
        int glow = mix(accent, color(255, 255, 255, 255), 0.2F);
        int shadow = darken(accent, 62);
        int trim = darken(accent, 24);
        int matte = mix(accent, BADGE_BG_ALT, 0.4F);

        if ("Unranked".equals(tier)) {
            graphics.fill(x + 4, y + 1, x + 5, y + 2, matte);
            graphics.fill(x + 3, y + 2, x + 6, y + 3, matte);
            graphics.fill(x + 2, y + 3, x + 7, y + 4, shadow);
            graphics.fill(x + 3, y + 4, x + 6, y + 5, shadow);
            graphics.fill(x + 4, y + 5, x + 5, y + 6, matte);
            return;
        }

        drawCoreGem(graphics, x, y, bright, accent, shadow);
        switch (tier) {
            case "Bronze" -> graphics.fill(x + 3, y + 7, x + 6, y + 8, shadow);
            case "Silver" -> {
                graphics.fill(x + 1, y + 3, x + 2, y + 5, matte);
                graphics.fill(x + 7, y + 3, x + 8, y + 5, matte);
                graphics.fill(x + 3, y + 7, x + 6, y + 8, shadow);
            }
            case "Gold" -> {
                drawCrown(graphics, x, y, bright, trim);
                graphics.fill(x + 1, y + 3, x + 2, y + 5, trim);
                graphics.fill(x + 7, y + 3, x + 8, y + 5, trim);
            }
            case "Platinum" -> {
                drawCrown(graphics, x, y, bright, trim);
                graphics.fill(x, y + 5, x + 2, y + 6, glow);
                graphics.fill(x + 7, y + 5, x + 9, y + 6, glow);
                graphics.fill(x + 1, y + 3, x + 2, y + 5, trim);
                graphics.fill(x + 7, y + 3, x + 8, y + 5, trim);
            }
            case "Diamond" -> {
                drawCrown(graphics, x, y, bright, trim);
                graphics.fill(x, y + 2, x + 2, y + 3, glow);
                graphics.fill(x + 7, y + 2, x + 9, y + 3, glow);
                graphics.fill(x, y + 5, x + 2, y + 6, bright);
                graphics.fill(x + 7, y + 5, x + 9, y + 6, bright);
                graphics.fill(x + 3, y + 7, x + 6, y + 8, shadow);
            }
            case "Master" -> {
                drawCrown(graphics, x, y, bright, trim);
                graphics.fill(x, y + 2, x + 2, y + 3, bright);
                graphics.fill(x + 7, y + 2, x + 9, y + 3, bright);
                graphics.fill(x, y + 5, x + 2, y + 6, bright);
                graphics.fill(x + 7, y + 5, x + 9, y + 6, bright);
                graphics.fill(x + 1, y + 0, x + 2, y + 1, glow);
                graphics.fill(x + 7, y + 0, x + 8, y + 1, glow);
            }
            case "Grandmaster" -> {
                drawCrown(graphics, x, y, bright, trim);
                graphics.fill(x, y + 1, x + 2, y + 2, bright);
                graphics.fill(x + 7, y + 1, x + 9, y + 2, bright);
                graphics.fill(x, y + 5, x + 2, y + 6, bright);
                graphics.fill(x + 7, y + 5, x + 9, y + 6, bright);
                graphics.fill(x, y + 6, x + 1, y + 8, shadow);
                graphics.fill(x + 8, y + 6, x + 9, y + 8, shadow);
                graphics.fill(x + 1, y + 0, x + 2, y + 1, glow);
                graphics.fill(x + 7, y + 0, x + 8, y + 1, glow);
                graphics.fill(x + 4, y + 0, x + 5, y + 1, bright);
            }
            default -> graphics.fill(x + 3, y + 7, x + 6, y + 8, shadow);
        }
    }

    private static void drawCoreGem(DrawContext graphics, int x, int y, int bright, int accent, int shadow) {
        graphics.fill(x + 4, y + 1, x + 5, y + 2, bright);
        graphics.fill(x + 3, y + 2, x + 6, y + 3, bright);
        graphics.fill(x + 2, y + 3, x + 7, y + 5, accent);
        graphics.fill(x + 3, y + 5, x + 6, y + 6, shadow);
        graphics.fill(x + 4, y + 6, x + 5, y + 7, shadow);
    }

    private static void drawCrown(DrawContext graphics, int x, int y, int bright, int trim) {
        graphics.fill(x + 2, y + 0, x + 3, y + 2, bright);
        graphics.fill(x + 4, y + 0, x + 5, y + 1, bright);
        graphics.fill(x + 6, y + 0, x + 7, y + 2, bright);
        graphics.fill(x + 2, y + 1, x + 7, y + 2, trim);
    }

    private static void drawCenteredScaledText(DrawContext graphics, TextRenderer font, String text, int centerX, int y, int color, float scale) {
        int scaledWidth = Math.round(font.getWidth(text) * scale);
        int textX = centerX - scaledWidth / 2;
        graphics.getMatrices().push();
        graphics.getMatrices().translate(textX, y, 0.0F);
        graphics.getMatrices().scale(scale, scale, 1.0F);
        graphics.drawText(font, text, 1, 1, TEXT_SHADOW, false);
        graphics.drawText(font, text, 0, 0, color, false);
        graphics.getMatrices().pop();
    }

    private static boolean isRankTier(String text) {
        return switch (text) {
            case "Unranked", "Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master", "Grandmaster" -> true;
            default -> false;
        };
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int first, int second, float amount) {
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

