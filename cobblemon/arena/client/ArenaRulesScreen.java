package cobblemon.arena.client;

import cobblemon.arena.ladder.ArenaLadder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ArenaRulesScreen extends ArenaScreenBase {

    private static final int ROWS_PER_PAGE = 5;

    private final Screen parent;
    private StyledButton previousPageButton;
    private StyledButton nextPageButton;
    private int pageIndex = 0;

    public ArenaRulesScreen(Screen parent) {
        super(Text.translatable("gui.cobblemon_arena.title.rules"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();

        addDrawableChild(
            new StyledButton(
                guiLeft + 8,
                guiTop + GUI_HEIGHT - 20,
                76,
                16,
                Text.translatable("gui.cobblemon_arena.button.back"),
                button -> close()
            )
        );
        addDrawableChild(
            new StyledButton(
                guiLeft + GUI_WIDTH - 68,
                guiTop + GUI_HEIGHT - 20,
                60,
                16,
                Text.translatable("gui.cobblemon_arena.button.close"),
                button -> closeAllScreens()
            )
        );

        previousPageButton = addDrawableChild(
            new StyledButton(
                guiLeft + 120,
                guiTop + GUI_HEIGHT - 20,
                56,
                16,
                Text.translatable("gui.cobblemon_arena.button.prev"),
                button -> changePage(-1)
            )
        );
        nextPageButton = addDrawableChild(
            new StyledButton(
                guiLeft + 182,
                guiTop + GUI_HEIGHT - 20,
                56,
                16,
                Text.translatable("gui.cobblemon_arena.button.next"),
                button -> changePage(1)
            )
        );
        updatePageButtons();
    }

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

        renderFullScreenBg(graphics);

        pushGuiScale(graphics);
        renderScreenFrame(
            graphics,
            Text.translatable("gui.cobblemon_arena.subtitle.rules").getString(),
            Text.translatable(
                "gui.cobblemon_arena.badge.presets",
                ArenaLadder.values().length
            ).getString(),
            WARNING_ACCENT
        );

        int panelX = guiLeft + 8;
        int panelY = guiTop + 30;
        int panelW = 368;
        int rowY = panelY + 18;

        ArenaLadder[] ladders = ArenaLadder.values();
        int pageCount = getPageCount(ladders.length);

        drawSection(graphics, panelX, panelY, panelW, 210, SECTION_BG);
        drawSectionTitle(
            graphics,
            panelX + 8,
            panelY + 8,
            Text.translatable(
                "gui.cobblemon_arena.section.all_arena_ladders"
            ).getString(),
            QUICK_ACCENT
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.page",
                pageIndex + 1,
                pageCount
            ).getString(),
            panelX + panelW - 52,
            panelY + 9,
            TEXT_DIM,
            SMALL_SCALE
        );

        int startIndex = pageIndex * ROWS_PER_PAGE;
        int endIndex = Math.min(startIndex + ROWS_PER_PAGE, ladders.length);
        for (int i = startIndex; i < endIndex; i++) {
            drawRuleRow(
                graphics,
                ladders[i],
                panelX + 8,
                rowY + (i - startIndex) * 36,
                panelW - 16,
                i % 2 == 0 ? SECTION_INSET : darken(SECTION_INSET, 3)
            );
        }

        super.render(graphics, gmx, gmy, delta);
        popGuiScale(graphics);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void drawRuleRow(
        DrawContext graphics,
        ArenaLadder ladder,
        int x,
        int y,
        int width,
        int fill
    ) {
        drawSection(graphics, x, y, width, 32, fill);
        drawScaledText(
            graphics,
            ladder.getDisplayName(),
            x + 8,
            y + 5,
            ladder.isRanked() ? RANKED_ACCENT : QUICK_ACCENT,
            BODY_SCALE
        );
        drawWrappedText(
            graphics,
            ladder.getDescription(),
            x + 8,
            y + 14,
            width - 16,
            TEXT_SECONDARY,
            1
        );
        drawWrappedText(
            graphics,
            ladder.getRulesSummary(),
            x + 8,
            y + 22,
            width - 16,
            WARNING_ACCENT,
            1
        );
    }

    private void closeAllScreens() {
        if (client != null) {
            client.setScreen(null);
        }
    }

    private void changePage(int delta) {
        int pageCount = getPageCount(ArenaLadder.values().length);
        if (pageCount > 1) {
            pageIndex = Math.max(0, Math.min(pageCount - 1, pageIndex + delta));
            updatePageButtons();
        }
    }

    private void updatePageButtons() {
        int pageCount = getPageCount(ArenaLadder.values().length);
        if (previousPageButton != null) {
            previousPageButton.active = pageCount > 1 && pageIndex > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = pageCount > 1 && pageIndex < pageCount - 1;
        }
    }

    private int getPageCount(int ladderCount) {
        return Math.max(
            1,
            (int) Math.ceil(ladderCount / (double) ROWS_PER_PAGE)
        );
    }
}
