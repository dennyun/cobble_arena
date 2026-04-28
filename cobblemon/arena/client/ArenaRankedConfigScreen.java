package cobblemon.arena.client;

import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaRankedPreset;
import cobblemon.arena.network.RequestRankedConfigPacket;
import cobblemon.arena.network.ResetRankedLadderPacket;
import cobblemon.arena.network.UpdateRankedConfigPacket;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Ranked config UI (ported from the original client UI, simplified but functional).
 *
 * This screen stays read-only unless the server grants edit permissions.
 */
public class ArenaRankedConfigScreen extends ArenaScreenBase {

    private static final List<String> ACTION_TIMER_OPTIONS = List.of(
        "15s",
        "20s",
        "30s",
        "45s",
        "60s",
        "90s",
        "120s"
    );

    private final Screen parent;

    private CycleSelectorButton activeCountButton;
    private CycleSelectorButton ladderSlotButton;
    private CycleSelectorButton timerButton;
    private CycleSelectorButton presetButton;

    private StyledButton backButton;
    private StyledButton saveButton;
    private StyledButton resetButton;

    private TextFieldWidget ladderNameField;
    private TextFieldWidget bannedPokemonField;
    private TextFieldWidget bannedItemsField;
    private TextFieldWidget bannedMovesField;

    private ArenaServerConfig.Snapshot draftSnapshot =
        ArenaServerConfig.copySnapshot(new ArenaServerConfig.Snapshot());
    private int editingLadderIndex = 0;
    private boolean dirty = false;
    private boolean loadingFields = false;

    public ArenaRankedConfigScreen(Screen parent) {
        super(Text.translatable("gui.cobblemon_arena.title.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateGuiPosition();
        applySyncedConfig();

        activeCountButton = addDrawableChild(
            new CycleSelectorButton(
                guiLeft + 22,
                guiTop + 64,
                64,
                16,
                ladderCountOptions(),
                String.valueOf(draftSnapshot.getActiveRankedLadderCount()),
                "",
                selection -> {
                    draftSnapshot.setActiveRankedLadderCount(
                        parsePositiveInt(selection, 1)
                    );
                    dirty = true;
                }
            )
        );
        ladderSlotButton = addDrawableChild(
            new CycleSelectorButton(
                guiLeft + 96,
                guiTop + 64,
                76,
                16,
                ladderSlotOptions(),
                ladderSlotLabel(editingLadderIndex),
                "",
                this::switchEditingLadder
            )
        );
        timerButton = addDrawableChild(
            new CycleSelectorButton(
                guiLeft + 178,
                guiTop + 64,
                58,
                16,
                ACTION_TIMER_OPTIONS,
                formatTimerOption(draftSnapshot.getActionTimerSeconds()),
                "",
                selection -> {
                    draftSnapshot.setActionTimerSeconds(
                        parseTimerOption(selection)
                    );
                    dirty = true;
                }
            )
        );

        ladderNameField = addEditorField(guiLeft + 22, guiTop + 106, 90, 16);
        ladderNameField.setChangedListener(value -> {
            if (!loadingFields) {
                currentLadder().setName(value);
                dirty = true;
            }
        });

        presetButton = addDrawableChild(
            new CycleSelectorButton(
                guiLeft + 114,
                guiTop + 106,
                74,
                16,
                ArenaRankedPreset.selectionOptions(),
                ArenaRankedPreset.selectionForKey(
                    currentLadder().getPresetKey()
                ),
                "",
                selection -> {
                    currentLadder().setPresetKey(
                        ArenaRankedPreset.keyForSelection(selection)
                    );
                    dirty = true;
                    loadCurrentLadderIntoWidgets();
                }
            )
        );

        bannedPokemonField = addEditorField(
            guiLeft + 22,
            guiTop + 184,
            160,
            14
        );
        bannedPokemonField.setChangedListener(value -> {
            if (!loadingFields) {
                currentLadder().setBannedPokemon(parseCsv(value));
                dirty = true;
            }
        });

        bannedItemsField = addEditorField(guiLeft + 22, guiTop + 208, 160, 14);
        bannedItemsField.setChangedListener(value -> {
            if (!loadingFields) {
                currentLadder().setBannedItems(parseCsv(value));
                dirty = true;
            }
        });

        bannedMovesField = addEditorField(guiLeft + 22, guiTop + 232, 160, 14);
        bannedMovesField.setChangedListener(value -> {
            if (!loadingFields) {
                currentLadder().setBannedMoves(parseCsv(value));
                dirty = true;
            }
        });

        backButton = addDrawableChild(
            new StyledButton(
                guiLeft + 8,
                guiTop + GUI_HEIGHT - 20,
                76,
                16,
                Text.translatable("gui.cobblemon_arena.button.back"),
                b -> close()
            )
        );
        saveButton = addDrawableChild(
            new StyledButton(
                guiLeft + 218,
                guiTop + GUI_HEIGHT - 20,
                76,
                16,
                Text.translatable("gui.cobblemon_arena.button.save"),
                b -> saveConfig()
            )
        );
        resetButton = addDrawableChild(
            new StyledButton(
                guiLeft + 124,
                guiTop + GUI_HEIGHT - 20,
                88,
                16,
                Text.translatable("gui.cobblemon_arena.button.reset_ladder"),
                b -> resetLadder()
            )
        );

        requestLatestConfig();
        loadCurrentLadderIntoWidgets();
        refreshControls();
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

        refreshControls();
        renderFullScreenBg(graphics);

        pushGuiScale(graphics);
        renderScreenFrame(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.subtitle.config"
            ).getString(),
            statusBadgeText(),
            statusBadgeColor()
        );

        drawSection(graphics, guiLeft + 8, guiTop + 30, 368, 228, SECTION_BG);
        drawSectionTitle(
            graphics,
            guiLeft + 16,
            guiTop + 38,
            Text.translatable(
                "gui.cobblemon_arena.section.rotation"
            ).getString(),
            RANKED_ACCENT
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.active_ladders"
            ).getString(),
            guiLeft + 22,
            guiTop + 56,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable("gui.cobblemon_arena.label.editing").getString(),
            guiLeft + 96,
            guiTop + 56,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.action_timer"
            ).getString(),
            guiLeft + 178,
            guiTop + 56,
            TEXT_SECONDARY,
            SMALL_SCALE
        );

        drawSectionTitle(
            graphics,
            guiLeft + 16,
            guiTop + 84,
            Text.translatable(
                "gui.cobblemon_arena.section.ladder_rules"
            ).getString(),
            QUICK_ACCENT
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.display_name"
            ).getString(),
            guiLeft + 22,
            guiTop + 98,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable("gui.cobblemon_arena.label.preset").getString(),
            guiLeft + 114,
            guiTop + 98,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.banned_pokemon"
            ).getString(),
            guiLeft + 22,
            guiTop + 176,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.banned_items"
            ).getString(),
            guiLeft + 22,
            guiTop + 200,
            TEXT_SECONDARY,
            SMALL_SCALE
        );
        drawScaledText(
            graphics,
            Text.translatable(
                "gui.cobblemon_arena.label.banned_moves"
            ).getString(),
            guiLeft + 22,
            guiTop + 224,
            TEXT_SECONDARY,
            SMALL_SCALE
        );

        drawWrappedText(
            graphics,
            footerStatusText(),
            guiLeft + 16,
            guiTop + 262,
            352,
            footerStatusColor(),
            2
        );

        super.render(graphics, gmx, gmy, delta);
        popGuiScale(graphics);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private void requestLatestConfig() {
        if (client != null && client.player != null) {
            ClientPlayNetworking.send(new RequestRankedConfigPacket());
        }
    }

    private void saveConfig() {
        ClientPlayNetworking.send(
            new UpdateRankedConfigPacket(
                ArenaServerConfig.snapshotToJson(draftSnapshot)
            )
        );
        dirty = false;
        refreshControls();
    }

    private void resetLadder() {
        ClientPlayNetworking.send(new ResetRankedLadderPacket());
        dirty = false;
        refreshControls();
    }

    private void applySyncedConfig() {
        draftSnapshot = ArenaRankedConfigClientState.getSnapshot();
        editingLadderIndex = Math.min(
            editingLadderIndex,
            Math.max(0, draftSnapshot.getRankedLadders().size() - 1)
        );
        dirty = false;
    }

    private void switchEditingLadder(String selection) {
        editingLadderIndex = Math.max(
            0,
            Math.min(
                draftSnapshot.getRankedLadders().size() - 1,
                parsePositiveInt(selection.replace("Ladder ", ""), 1) - 1
            )
        );
        loadCurrentLadderIntoWidgets();
    }

    private void loadCurrentLadderIntoWidgets() {
        if (ladderNameField == null) return;
        ArenaServerConfig.RankedLadderConfig ladder = currentLadder();
        ArenaRankedPreset preset = ArenaRankedPreset.fromKey(
            ladder.getPresetKey()
        );

        loadingFields = true;
        ladderNameField.setText(
            preset.isLockedPreset() ? preset.getDisplayName() : ladder.getName()
        );
        bannedPokemonField.setText(
            preset.isLockedPreset()
                ? ""
                : String.join(", ", ladder.getBannedPokemon())
        );
        bannedItemsField.setText(
            preset.isLockedPreset()
                ? ""
                : String.join(", ", ladder.getBannedItems())
        );
        bannedMovesField.setText(
            preset.isLockedPreset()
                ? ""
                : String.join(", ", ladder.getBannedMoves())
        );
        if (presetButton != null) presetButton.setSelected(
            ArenaRankedPreset.selectionForKey(ladder.getPresetKey())
        );
        if (ladderSlotButton != null) ladderSlotButton.setSelected(
            ladderSlotLabel(editingLadderIndex)
        );
        loadingFields = false;
    }

    private void refreshControls() {
        boolean hasPlayer = client != null && client.player != null;
        boolean loaded = ArenaRankedConfigClientState.isLoaded();
        boolean canEdit =
            hasPlayer && loaded && ArenaRankedConfigClientState.canEdit();
        boolean editingPreset = ArenaRankedPreset.fromKey(
            currentLadder().getPresetKey()
        ).isLockedPreset();

        setWidgetState(activeCountButton, canEdit);
        setWidgetState(ladderSlotButton, canEdit);
        setWidgetState(timerButton, canEdit);
        setWidgetState(presetButton, canEdit);

        if (ladderNameField != null) ladderNameField.setEditable(
            canEdit && !editingPreset
        );
        if (bannedPokemonField != null) bannedPokemonField.setEditable(
            canEdit && !editingPreset
        );
        if (bannedItemsField != null) bannedItemsField.setEditable(
            canEdit && !editingPreset
        );
        if (bannedMovesField != null) bannedMovesField.setEditable(
            canEdit && !editingPreset
        );

        if (saveButton != null) saveButton.active = canEdit && dirty;
        if (resetButton != null) resetButton.active =
            hasPlayer && loaded && ArenaRankedConfigClientState.canReset();
    }

    private TextFieldWidget addEditorField(
        int x,
        int y,
        int width,
        int height
    ) {
        TextFieldWidget field = new TextFieldWidget(
            textRenderer,
            x,
            y,
            width,
            height,
            Text.empty()
        );
        field.setMaxLength(120);
        field.setEditableColor(TEXT_PRIMARY);
        field.setUneditableColor(TEXT_DIM);
        field.setDrawsBackground(true);
        addDrawableChild(field);
        return field;
    }

    private void setWidgetState(CycleSelectorButton button, boolean active) {
        if (button != null) button.active = active;
    }

    private ArenaServerConfig.RankedLadderConfig currentLadder() {
        return draftSnapshot.getRankedLadders().get(editingLadderIndex);
    }

    private static List<String> ladderCountOptions() {
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 8; i++) options.add(String.valueOf(i));
        return options;
    }

    private static List<String> ladderSlotOptions() {
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 8; i++) options.add(
            Text.translatable(
                "gui.cobblemon_arena.ranked_config.ladder_slot",
                i
            ).getString()
        );
        return options;
    }

    private static String ladderSlotLabel(int index) {
        return Text.translatable(
            "gui.cobblemon_arena.ranked_config.ladder_slot",
            index + 1
        ).getString();
    }

    private static List<String> parseCsv(String value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) return values;
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) values.add(trimmed);
        }
        return values;
    }

    private static String formatTimerOption(int seconds) {
        return seconds + "s";
    }

    private static int parseTimerOption(String option) {
        if (option == null || option.isBlank()) return 30;
        try {
            String numeric = option.endsWith("s")
                ? option.substring(0, option.length() - 1)
                : option;
            return Integer.parseInt(numeric);
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String statusBadgeText() {
        if (client == null || client.player == null) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.status.offline"
        ).getString();
        if (!ArenaRankedConfigClientState.isLoaded()) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.status.syncing"
        ).getString();
        return ArenaRankedConfigClientState.canEdit()
            ? Text.translatable(
                  "gui.cobblemon_arena.ranked_config.status.admin"
              ).getString()
            : Text.translatable(
                  "gui.cobblemon_arena.ranked_config.status.read_only"
              ).getString();
    }

    private int statusBadgeColor() {
        if (client == null || client.player == null) return WARNING_ACCENT;
        if (!ArenaRankedConfigClientState.isLoaded()) return INFO_ACCENT;
        return ArenaRankedConfigClientState.canEdit()
            ? SUCCESS_ACCENT
            : WARNING_ACCENT;
    }

    private String footerStatusText() {
        if (client == null || client.player == null) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.footer.offline"
        ).getString();
        if (!ArenaRankedConfigClientState.isLoaded()) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.footer.syncing"
        ).getString();
        if (!ArenaRankedConfigClientState.canEdit()) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.footer.permission_denied"
        ).getString();
        if (!ArenaRankedConfigClientState.canReset()) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.footer.reset_unavailable"
        ).getString();
        if (dirty) return Text.translatable(
            "gui.cobblemon_arena.ranked_config.footer.unsaved_changes"
        ).getString();

        return ArenaRankedPreset.fromKey(
                currentLadder().getPresetKey()
            ).isLockedPreset()
            ? Text.translatable(
                  "gui.cobblemon_arena.ranked_config.footer.locked_preset"
              ).getString()
            : Text.translatable(
                  "gui.cobblemon_arena.ranked_config.footer.editing_live_rotation"
              ).getString();
    }

    private int footerStatusColor() {
        if (client == null || client.player == null) return WARNING_ACCENT;
        if (!ArenaRankedConfigClientState.isLoaded()) return INFO_ACCENT;
        if (!ArenaRankedConfigClientState.canEdit()) return WARNING_ACCENT;
        if (!ArenaRankedConfigClientState.canReset()) return WARNING_ACCENT;
        return dirty ? QUICK_ACCENT : SUCCESS_ACCENT;
    }
}
