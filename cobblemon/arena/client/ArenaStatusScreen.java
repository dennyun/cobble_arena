package cobblemon.arena.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ArenaStatusScreen extends ArenaScreenBase {

    private final Screen parent;

    public ArenaStatusScreen(Screen parent) {
        super(Text.translatable("gui.cobblemon_arena.title.status"));
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
            Text.translatable(
                "gui.cobblemon_arena.subtitle.status"
            ).getString(),
            ArenaClientState.getAvailableArenas() > 0
                ? Text.translatable(
                      "gui.cobblemon_arena.badge.ready"
                  ).getString()
                : Text.translatable(
                      "gui.cobblemon_arena.badge.busy"
                  ).getString(),
            ArenaClientState.getAvailableArenas() > 0
                ? SUCCESS_ACCENT
                : WARNING_ACCENT
        );
        drawOverviewPanel(graphics);
        drawCapacityPanel(graphics);
        drawNotesPanel(graphics);
        super.render(graphics, gmx, gmy, delta);
        popGuiScale(graphics);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void drawOverviewPanel(DrawContext graphics) {
        int x = guiLeft + 8;
        int y = guiTop + 30;
        drawSection(graphics, x, y, 180, 62, SECTION_BG);
        drawSectionTitle(
            graphics,
            x + 8,
            y + 8,
            Text.translatable(
                "gui.cobblemon_arena.section.arena_network"
            ).getString(),
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 8,
            y + 28,
            50,
            Text.translatable("gui.cobblemon_arena.label.online").getString(),
            String.valueOf(ArenaClientState.getPlayersOnline()),
            TEXT_PRIMARY
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 28,
            50,
            Text.translatable("gui.cobblemon_arena.label.queued").getString(),
            String.valueOf(ArenaClientState.getPlayersInQueue()),
            QUICK_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 28,
            54,
            Text.translatable("gui.cobblemon_arena.label.active").getString(),
            String.valueOf(ArenaClientState.getActiveBattles()),
            RANKED_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 8,
            y + 44,
            50,
            Text.translatable("gui.cobblemon_arena.label.ready").getString(),
            String.valueOf(ArenaClientState.getAvailableArenas()),
            SUCCESS_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 44,
            50,
            Text.translatable("gui.cobblemon_arena.label.total").getString(),
            String.valueOf(ArenaClientState.getTotalArenas()),
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 44,
            54,
            Text.translatable("gui.cobblemon_arena.label.queue").getString(),
            QueueStatusOverlay.getInstance().isVisible()
                ? Text.translatable(
                      "gui.cobblemon_arena.label.active"
                  ).getString()
                : Text.translatable(
                      "gui.cobblemon_arena.label.idle"
                  ).getString(),
            QueueStatusOverlay.getInstance().isVisible()
                ? SUCCESS_ACCENT
                : TEXT_DIM
        );
    }

    private void drawCapacityPanel(DrawContext graphics) {
        int x = guiLeft + 196;
        int y = guiTop + 30;
        int total = ArenaClientState.getTotalArenas();
        int available = ArenaClientState.getAvailableArenas();
        int inUse = Math.max(0, total - available);
        int usage = total <= 0 ? 0 : (inUse * 100) / total;

        drawSection(graphics, x, y, 180, 62, SECTION_ALT);
        drawSectionTitle(
            graphics,
            x + 8,
            y + 8,
            Text.translatable(
                "gui.cobblemon_arena.section.arena_capacity"
            ).getString(),
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 8,
            y + 28,
            50,
            Text.translatable("gui.cobblemon_arena.label.in_use").getString(),
            String.valueOf(inUse),
            WARNING_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 63,
            y + 28,
            50,
            Text.translatable("gui.cobblemon_arena.label.usage").getString(),
            usage + "%",
            INFO_ACCENT
        );
        drawMetricChip(
            graphics,
            x + 118,
            y + 28,
            54,
            Text.translatable("gui.cobblemon_arena.label.battles").getString(),
            String.valueOf(ArenaClientState.getActiveBattles()),
            RANKED_ACCENT
        );
        drawInsetBand(graphics, x + 8, y + 44, 164, 10);
        drawScaledText(
            graphics,
            usage >= 100
                ? Text.translatable(
                      "gui.cobblemon_arena.status.all_arenas_occupied"
                  ).getString()
                : (available > 0
                      ? Text.translatable(
                            "gui.cobblemon_arena.status.arenas_open",
                            available
                        ).getString()
                      : Text.translatable(
                            "gui.cobblemon_arena.status.waiting_for_slot"
                        ).getString()),
            x + 12,
            y + 46,
            usage >= 100 ? WARNING_ACCENT : TEXT_SECONDARY,
            SMALL_SCALE
        );
    }

    private void drawNotesPanel(DrawContext graphics) {
        int x = guiLeft + 8;
        int y = guiTop + 102;
        int width = 368;
        boolean queueActive = QueueStatusOverlay.getInstance().isVisible();

        drawSection(graphics, x, y, width, 126, SECTION_BG);
        drawSectionTitle(
            graphics,
            x + 8,
            y + 8,
            Text.translatable(
                "gui.cobblemon_arena.section.status_notes"
            ).getString(),
            QUICK_ACCENT
        );
        drawWrappedText(
            graphics,
            queueActive
                ? Text.translatable(
                      "gui.cobblemon_arena.note.queue_active"
                  ).getString()
                : Text.translatable(
                      "gui.cobblemon_arena.note.queue_idle"
                  ).getString(),
            x + 8,
            y + 24,
            width - 16,
            queueActive ? SUCCESS_ACCENT : TEXT_SECONDARY,
            3
        );
        drawWrappedText(
            graphics,
            ArenaClientState.getPlayersInQueue() > 0
                ? Text.translatable(
                      "gui.cobblemon_arena.note.queue_live"
                  ).getString()
                : Text.translatable(
                      "gui.cobblemon_arena.note.no_queue_players"
                  ).getString(),
            x + 8,
            y + 54,
            width - 16,
            TEXT_DIM,
            3
        );
        drawWrappedText(
            graphics,
            ArenaClientState.getAvailableArenas() > 0
                ? Text.translatable(
                      "gui.cobblemon_arena.note.infrastructure_ready"
                  ).getString()
                : Text.translatable(
                      "gui.cobblemon_arena.note.all_arenas_busy"
                  ).getString(),
            x + 8,
            y + 84,
            width - 16,
            ArenaClientState.getAvailableArenas() > 0
                ? SUCCESS_ACCENT
                : WARNING_ACCENT,
            3
        );
    }

    private void closeAllScreens() {
        if (client != null) {
            client.setScreen(null);
        }
    }
}
