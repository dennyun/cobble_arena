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
        int r = 6;
        int textWidth = Math.round(font.getWidth(text) * scale);
        int totalWidth = textWidth + r*2 + 8;
        int x = rightX - totalWidth;
        
        drawCircularBadge(graphics, x + r, y + r, r, text);
        
        int textColor = text.toLowerCase(java.util.Locale.ROOT).contains("sem rank") ? color(102, 95, 138, 255) : accent;
        drawCenteredScaledText(graphics, font, text, x + r*2 + 4 + textWidth/2, y + 2, textColor, scale);
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
        String t = text.toLowerCase(java.util.Locale.ROOT);
        return t.contains("sem rank") || t.contains("bronze") || t.contains("prata") || 
               t.contains("ouro") || t.contains("platina") || t.contains("diamante") || 
               t.contains("mestre") || t.contains("grão") || t.contains("grao");
    }

    public static void drawCircularBadge(DrawContext c, int cx, int cy, int r, String tierName) {
        tierName = tierName.toLowerCase(java.util.Locale.ROOT);
        
        int bgRing = color(100, 100, 100, 255);
        int bgInner = color(130, 130, 130, 255);
        int iconCol = color(80, 80, 80, 255);
        String txt = "?";

        if (tierName.contains("bronze")) {
            bgRing = color(150, 80, 40, 255);
            bgInner = color(180, 100, 50, 255);
            iconCol = color(100, 40, 10, 255);
            txt = "v";
        } else if (tierName.contains("prata")) {
            bgRing = color(160, 170, 180, 255);
            bgInner = color(200, 210, 220, 255);
            iconCol = color(230, 240, 250, 255);
            txt = "W";
        } else if (tierName.contains("ouro")) {
            bgRing = color(210, 150, 20, 255);
            bgInner = color(255, 200, 40, 255);
            iconCol = color(255, 240, 100, 255);
            txt = "V";
        } else if (tierName.contains("platina")) {
            bgRing = color(140, 180, 200, 255);
            bgInner = color(180, 220, 240, 255);
            iconCol = color(220, 240, 255, 255);
            txt = "V"; 
        } else if (tierName.contains("diamante")) {
            bgRing = color(100, 200, 255, 255);
            bgInner = color(160, 230, 255, 255);
            iconCol = color(220, 255, 255, 255);
            txt = "V";
        } else if (tierName.contains("grão") || tierName.contains("grao")) {
            bgRing = color(220, 180, 40, 255); 
            bgInner = color(60, 20, 80, 255);  
            iconCol = color(255, 100, 255, 255); 
            txt = "V";
        } else if (tierName.contains("mestre")) {
            bgRing = color(40, 20, 40, 255);
            bgInner = color(80, 10, 20, 255);
            iconCol = color(255, 40, 40, 255);
            txt = "V";
        }

        fillCircle(c, cx, cy + 1, r, color(20, 20, 20, 255));
        fillCircle(c, cx, cy, r, bgRing);
        fillCircle(c, cx, cy, (int)(r * 0.75f), bgInner);
        
        if (txt.equals("?")) {
            drawCenteredScaledText(c, net.minecraft.client.MinecraftClient.getInstance().textRenderer, "?", cx, cy - (int)(r*0.4f), iconCol, r / 6.0f);
        } else if (txt.equals("v")) { 
            drawDownwardArrow(c, cx, cy, r/3, iconCol);
        } else if (txt.equals("W")) { 
            drawWedge(c, cx, cy, r/3, iconCol);
            c.fill(cx - r/2, cy - r/3, cx - r/2 + 2, cy + r/4, iconCol);
            c.fill(cx + r/2 - 2, cy - r/3, cx + r/2, cy + r/4, iconCol);
            c.fill(cx - r/2, cy + r/4 - 2, cx + r/2, cy + r/4, iconCol);
        } else if (txt.equals("V")) { 
            drawWedge(c, cx, cy - r/6, r/2, iconCol);
            if (tierName.contains("platina") || tierName.contains("diamante") || tierName.contains("mestre")) {
                drawWedge(c, cx, cy + r/4, r/3, iconCol);
            }
            if (tierName.contains("diamante")) {
                fillCircle(c, cx, cy, r/2, color(255, 255, 255, 80));
            }
        }
        
        c.fill(cx - r/2, cy - r + 1, cx + r/2, cy - r + 2, color(255, 255, 255, 80));
        
        if (tierName.contains("platina")) {
            c.fill(cx, cy - r + 2, cx + 1, cy - r + 4, color(80,80,80,255));
            c.fill(cx, cy + r - 4, cx + 1, cy + r - 2, color(80,80,80,255));
            c.fill(cx - r + 2, cy, cx - r + 4, cy + 1, color(80,80,80,255));
            c.fill(cx + r - 4, cy, cx + r - 2, cy + 1, color(80,80,80,255));
        } else if (tierName.contains("mestre")) {
            int rc = color(255, 20, 20, 255);
            c.fill(cx, cy - r + 2, cx + 2, cy - r + 4, rc);
            c.fill(cx, cy + r - 4, cx + 2, cy + r - 2, rc);
            c.fill(cx - r + 2, cy, cx - r + 4, cy + 2, rc);
            c.fill(cx + r - 4, cy, cx + r - 2, cy + 2, rc);
        } else if (tierName.contains("grão") || tierName.contains("grao")) {
            c.fill(cx - r/2, cy - r/3, cx - r/2 + 1, cy - r/3 + 1, color(255,255,255,255));
            c.fill(cx + r/3, cy - r/2, cx + r/3 + 1, cy - r/2 + 1, color(150,200,255,255));
            c.fill(cx - r/4, cy + r/2, cx - r/4 + 1, cy + r/2 + 1, color(255,200,255,255));
        }
    }

    private static void fillCircle(DrawContext c, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int dx = (int) Math.sqrt(r * r - y * y);
            c.fill(cx - dx, cy + y, cx + dx + 1, cy + y + 1, color);
        }
    }

    private static void drawWedge(DrawContext c, int cx, int cy, int size, int color) {
        for (int y = -size; y <= size; y++) {
            int w = size - Math.abs(y);
            c.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, color);
        }
    }

    private static void drawDownwardArrow(DrawContext c, int cx, int cy, int size, int color) {
        c.fill(cx - size/4, cy - size, cx + size/4 + 1, cy, color);
        for (int y = 0; y <= size; y++) {
            int w = size - y;
            c.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, color);
        }
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

