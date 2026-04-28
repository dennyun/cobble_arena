package cobblemon.arena.client;

import cobblemon.arena.network.ClaimQuestRewardPacket;
import cobblemon.arena.network.QuestEntryPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ArenaQuestsScreen extends ArenaScreenBase {

    // ── Layout constants ──────────────────────────────────────────────────
    private static final int QUEST_ROW_H   = 54;   // height of each quest row
    private static final int SECTION_HDR_H = 22;   // section header height
    private static final int ROW_GAP       = 3;    // gap between rows
    private static final int SECTION_GAP   = 6;    // gap between the two sections
    private static final int SECTION_PAD   = 4;    // bottom padding inside section

    // ── State ─────────────────────────────────────────────────────────────
    private final Screen parent;
    /** Tracks each rendered "Resgatar" button so mouseClicked can hit-test it. */
    private final List<ClaimArea> claimAreas = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────

    public ArenaQuestsScreen(Screen parent) {
        super(Text.literal("Missões"));
        this.parent = parent;
    }

    // ══════════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();

        // Back button
        addDrawableChild(new StyledButton(
            guiLeft + 8,
            guiTop + GUI_HEIGHT - 20,
            76, 16,
            Text.translatable("gui.cobblemon_arena.button.back"),
            b -> close()
        ));

        // Close button
        addDrawableChild(new StyledButton(
            guiLeft + GUI_WIDTH - 68,
            guiTop + GUI_HEIGHT - 20,
            60, 16,
            Text.translatable("gui.cobblemon_arena.button.close"),
            b -> { if (client != null) client.setScreen(null); }
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        updateGuiPosition();
        int gmx = (int) toGuiX(mouseX);
        int gmy = (int) toGuiY(mouseY);

        renderFullScreenBg(graphics);
        pushGuiScale(graphics);

        // ── Header ────────────────────────────────────────────────────────
        int claimable = ArenaQuestClientState.getClaimableCount();
        String badge    = claimable > 0 ? claimable + " para resgatar" : "Sincronizado";
        int    badgeClr = claimable > 0 ? SUCCESS_ACCENT : TEXT_DIM;
        renderScreenFrame(graphics, "Missões Diárias & Semanais", badge, badgeClr);

        // ── Content ───────────────────────────────────────────────────────
        claimAreas.clear();

        List<QuestEntryPayload> daily  = ArenaQuestClientState.getDailyQuests();
        List<QuestEntryPayload> weekly = ArenaQuestClientState.getWeeklyQuests();

        int contentX = guiLeft + 8;
        int contentY = guiTop + 30;
        int contentW = GUI_WIDTH - 16;

        if (ArenaQuestClientState.isEmpty()) {
            drawEmptyState(graphics, contentX, contentY, contentW);
        } else {
            // Daily section
            int dailyH = sectionHeight(daily.size());
            drawSection(graphics, contentX, contentY, contentW, dailyH, SECTION_BG);
            drawQuestSectionContent(
                graphics, gmx, gmy,
                contentX, contentY, contentW,
                "⬛  Diárias", QUICK_ACCENT,
                "Renovam a cada 24 horas",
                daily
            );

            // Weekly section
            int weeklyY = contentY + dailyH + SECTION_GAP;
            int weeklyH = sectionHeight(weekly.size());
            drawSection(graphics, contentX, weeklyY, contentW, weeklyH, SECTION_ALT);
            drawQuestSectionContent(
                graphics, gmx, gmy,
                contentX, weeklyY, contentW,
                "◆  Semanais", INFO_ACCENT,
                "Renovam a cada 7 dias",
                weekly
            );
        }

        // ── Widgets (Back / Close buttons) ────────────────────────────────
        super.render(graphics, gmx, gmy, delta);
        popGuiScale(graphics);
    }

    // ── Section ───────────────────────────────────────────────────────────

    private void drawQuestSectionContent(
        DrawContext graphics, int mouseX, int mouseY,
        int x, int y, int w,
        String title, int accent, String subtitle,
        List<QuestEntryPayload> quests
    ) {
        // Section title
        drawSectionTitle(graphics, x + 8, y + 7, title, accent);

        // Subtitle (right-aligned)
        int subW = Math.round(textRenderer.getWidth(subtitle) * SMALL_SCALE);
        drawScaledText(graphics, subtitle, x + w - subW - 6, y + 9, TEXT_DIM, SMALL_SCALE);

        // Divider under header
        graphics.fill(x + 6, y + SECTION_HDR_H - 1, x + w - 6, y + SECTION_HDR_H, BORDER_HIGHLIGHT);

        if (quests.isEmpty()) {
            drawCenteredScaledText(
                graphics,
                "Nenhuma missão ativa.",
                x + w / 2, y + SECTION_HDR_H + 10,
                TEXT_DIM, BODY_SCALE
            );
            return;
        }

        int rowY = y + SECTION_HDR_H + 2;
        for (QuestEntryPayload quest : quests) {
            drawQuestRow(graphics, mouseX, mouseY, x + 6, rowY, w - 12, quest, accent);
            rowY += QUEST_ROW_H + ROW_GAP;
        }
    }

    // ── Quest row ─────────────────────────────────────────────────────────

    private void drawQuestRow(
        DrawContext graphics, int mouseX, int mouseY,
        int x, int y, int w,
        QuestEntryPayload quest, int accent
    ) {
        // Row background
        int rowBg = quest.claimed()
            ? darken(SECTION_INSET, 10)
            : SECTION_INSET;
        drawSection(graphics, x, y, w, QUEST_ROW_H, rowBg);

        // Left status stripe
        int stripeColor = quest.claimed()
            ? darken(accent, 40)
            : (quest.completed() ? SUCCESS_ACCENT : accent);
        graphics.fill(x, y, x + 3, y + QUEST_ROW_H, stripeColor);

        // ── Text block (left side, leaves 90px for the button on the right) ─
        int textMaxW = w - 98;

        // Title row
        int titleColor = quest.claimed() ? TEXT_DIM : TEXT_PRIMARY;
        drawScaledText(
            graphics,
            fitText(quest.title(), textMaxW, BODY_SCALE),
            x + 8, y + 5,
            titleColor, BODY_SCALE
        );

        // Progress text  (e.g. "2 / 3")
        String progressText = quest.currentProgress() + " / " + quest.targetAmount();
        int progW = Math.round(textRenderer.getWidth(progressText) * SMALL_SCALE);
        drawScaledText(graphics, progressText,
            x + 8, y + 17,
            accent, SMALL_SCALE
        );

        // Progress bar
        int barX  = x + 8 + progW + 4;
        int barY  = y + 19;
        int barW  = Math.min(textMaxW - progW - 8, 90);
        int barH  = 4;
        graphics.fill(barX, barY, barX + barW, barY + barH, darken(SECTION_BG, 12));
        int filled = (int)(barW * quest.progressFraction());
        if (filled > 0) {
            int fillColor = quest.claimed()
                ? darken(accent, 30)
                : (quest.completed() ? SUCCESS_ACCENT : accent);
            graphics.fill(barX, barY, barX + filled, barY + barH, fillColor);
        }
        // Sheen on progress bar
        graphics.fill(barX, barY, barX + barW, barY + 1, color(255, 255, 255, 18));

        // Description
        drawScaledText(
            graphics,
            fitText(quest.description(), textMaxW, SMALL_SCALE),
            x + 8, y + 27,
            TEXT_SECONDARY, SMALL_SCALE
        );

        // Reward (if any)
        if (!quest.rewardDescription().isBlank()) {
            drawScaledText(
                graphics,
                fitText("🎁 " + quest.rewardDescription(), textMaxW, SMALL_SCALE),
                x + 8, y + 38,
                RANKED_ACCENT, SMALL_SCALE
            );
        }

        // ── Right side: claim button OR status label ───────────────────────
        int btnW  = 82;
        int btnH  = 16;
        int btnX  = x + w - btnW - 4;
        int btnY  = y + QUEST_ROW_H / 2 - btnH / 2;

        if (quest.completed() && !quest.claimed()) {
            // "Resgatar" button
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;

            int btnBg = hovered
                ? mix(SUCCESS_ACCENT, color(255, 255, 255, 255), 0.2f)
                : darken(SUCCESS_ACCENT, 25);

            graphics.fill(btnX,     btnY,          btnX + btnW, btnY + btnH, btnBg);
            graphics.fill(btnX,     btnY,          btnX + btnW, btnY + 1,    color(255, 255, 255, 50));
            graphics.fill(btnX,     btnY + btnH - 1, btnX + btnW, btnY + btnH, darken(SUCCESS_ACCENT, 50));

            if (hovered) {
                // Subtle outer glow
                graphics.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY,          color(112, 220, 132, 60));
                graphics.fill(btnX - 1, btnY + btnH, btnX + btnW + 1, btnY + btnH + 1, color(112, 220, 132, 40));
            }

            drawCenteredScaledText(
                graphics,
                "✔ Resgatar",
                btnX + btnW / 2, btnY + 4,
                color(255, 255, 255, 255), SMALL_SCALE
            );

            claimAreas.add(new ClaimArea(quest.questId(), btnX, btnY, btnW, btnH));

        } else if (quest.claimed()) {
            // "Resgatado" label
            drawCenteredScaledText(
                graphics,
                "✔ Resgatado",
                btnX + btnW / 2, btnY + 4,
                darken(SUCCESS_ACCENT, 18), SMALL_SCALE
            );
        } else {
            // Not complete yet — show percentage chip
            int pct     = (int)(quest.progressFraction() * 100);
            String pctTxt = pct + "%";
            int chipW   = 36;
            int chipX   = btnX + (btnW - chipW) / 2;
            int chipY2  = btnY + btnH / 2 - 7;

            graphics.fill(chipX, chipY2, chipX + chipW, chipY2 + 13, darken(SECTION_BG, 8));
            graphics.fill(chipX, chipY2, chipX + chipW, chipY2 + 1,  BORDER_HIGHLIGHT);
            drawCenteredScaledText(
                graphics, pctTxt,
                chipX + chipW / 2, chipY2 + 3,
                TEXT_DIM, SMALL_SCALE
            );
        }
    }

    // ── Empty state ───────────────────────────────────────────────────────

    private void drawEmptyState(DrawContext graphics, int x, int y, int w) {
        int panelH = GUI_HEIGHT - 56;
        drawSection(graphics, x, y, w, panelH, SECTION_BG);
        drawCenteredScaledText(
            graphics,
            "Missões não disponíveis.",
            x + w / 2, y + panelH / 2 - 12,
            TEXT_PRIMARY, BODY_SCALE
        );
        drawCenteredScaledText(
            graphics,
            "Abra o menu principal para sincronizar.",
            x + w / 2, y + panelH / 2 + 2,
            TEXT_DIM, SMALL_SCALE
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mouse handling
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double gx = toGuiX(mouseX);
            double gy = toGuiY(mouseY);
            for (ClaimArea area : claimAreas) {
                if (gx >= area.x && gx < area.x + area.w
                        && gy >= area.y && gy < area.y + area.h) {
                    ClientPlayNetworking.send(new ClaimQuestRewardPacket(area.questId));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    /**
     * Computes the total pixel height of a quest section.
     *
     * @param questCount number of quests in this section
     */
    private static int sectionHeight(int questCount) {
        if (questCount == 0) {
            return SECTION_HDR_H + 28; // header + "no quests" message
        }
        // header + rows + row gaps + bottom padding
        return SECTION_HDR_H + 2
            + questCount * QUEST_ROW_H
            + (questCount - 1) * ROW_GAP
            + SECTION_PAD;
    }

    // ── Internal data class ───────────────────────────────────────────────

    /** Hit-test area for a single "Resgatar" button rendered this frame. */
    private record ClaimArea(String questId, int x, int y, int w, int h) {}
}
