package cobblemon.arena.client;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Quaternionf;

public class ArenaPartyPreviewRenderer {
    private static final int PANEL_BG = color(42, 16, 22, 255);
    private static final int PANEL_INSET = color(24, 8, 12, 255);
    private static final int BORDER_COLOR = color(132, 54, 62, 255);
    private static final int BORDER_HIGHLIGHT = color(164, 68, 74, 255);
    private static final int TEXT_PRIMARY = color(248, 242, 236, 255);
    private static final int TEXT_DIM = color(144, 126, 119, 255);
    private static final int ARENA_SLOT_EMPTY = color(44, 22, 26, 255);
    private static final int ARENA_SLOT_FILLED = color(74, 42, 48, 255);
    private static final int ARENA_SLOT_FAINTED = color(58, 22, 30, 255);
    private static final int ARENA_SLOT_BORDER = color(132, 54, 62, 255);
    private static final int ARENA_SLOT_HOVER = color(224, 112, 82, 255);
    private static final int ARENA_SLOT_FAINTED_BORDER = color(190, 72, 82, 255);
    private static final int SLOT_EMPTY_ICON = color(80, 90, 110, 200);
    private static final float LABEL_SCALE = 0.65F;

    private final Map<Integer, FloatingState> slotStates = new HashMap<>();

    public ArenaPartyPreviewRenderer() {
        for (int i = 0; i < 6; i++) {
            slotStates.put(i, new FloatingState());
        }
    }

    public List<Text> render(
            DrawContext graphics,
            TextRenderer font,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        drawPanel(graphics, x, y, width, height);
        List<Pokemon> party = ArenaClientState.getPartyPreview();

        int rowWidth = 308;
        int slotY = y + Math.max(4, (height - 32) / 2);
        int startX = x + (width - rowWidth) / 2;
        List<Text> hoveredTooltip = null;

        for (int i = 0; i < 6; i++) {
            int slotX = startX + i * 52;
            Pokemon pokemon = i < party.size() ? party.get(i) : null;
            boolean hovered = mouseX >= slotX && mouseX < slotX + 48 && mouseY >= slotY && mouseY < slotY + 32;
            drawSlot(graphics, pokemon, slotX, slotY, hovered);

            if (pokemon == null) {
                drawEmptySlotIcon(graphics, font, slotX, slotY);
            } else {
                renderPokemonModel(graphics, pokemon, slotX, slotY, i, partialTick);
                drawLevelLabel(graphics, font, pokemon, slotX, slotY);
                drawHeldItemIcon(graphics, pokemon.heldItem(), slotX, slotY);
                if (hovered) hoveredTooltip = buildTooltip(pokemon);
            }
        }

        return hoveredTooltip;
    }

    private void drawPanel(DrawContext graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_INSET);
        graphics.fill(x, y, x + width, y + 1, BORDER_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, darken(BORDER_COLOR, 18));
        graphics.fill(x, y, x + 1, y + height, BORDER_HIGHLIGHT);
        graphics.fill(x + width - 1, y, x + width, y + height, darken(BORDER_COLOR, 18));
        graphics.fill(x + 2, y + 2, x + width - 2, y + 5, color(255, 255, 255, 8));
        graphics.fill(x + 2, y + height - 5, x + width - 2, y + height - 2, color(0, 0, 0, 30));
    }

    private void drawSlot(DrawContext graphics, Pokemon pokemon, int x, int y, boolean hovered) {
        boolean hasPokemon = pokemon != null;
        boolean fainted = hasPokemon && pokemon.isFainted();
        int slotBg = !hasPokemon ? ARENA_SLOT_EMPTY : (fainted ? ARENA_SLOT_FAINTED : ARENA_SLOT_FILLED);
        int borderColor = fainted ? ARENA_SLOT_FAINTED_BORDER : (hovered ? ARENA_SLOT_HOVER : ARENA_SLOT_BORDER);

        graphics.fill(x + 1, y + 1, x + 48 - 1, y + 32 - 1, slotBg);
        graphics.fill(x + 1, y + 32 - 4, x + 48 - 1, y + 32 - 1, darken(slotBg, 20));

        graphics.fill(x + 1, y, x + 48 - 1, y + 1, borderColor);
        graphics.fill(x + 1, y + 32 - 1, x + 48 - 1, y + 32, darken(borderColor, 30));
        graphics.fill(x, y + 1, x + 1, y + 32 - 1, borderColor);
        graphics.fill(x + 48 - 1, y + 1, x + 48, y + 32 - 1, darken(borderColor, 30));

        graphics.fill(x + 2, y + 1, x + 48 - 2, y + 2, lighten(slotBg, 40));
        graphics.fill(x + 1, y + 2, x + 2, y + 32 - 2, lighten(slotBg, 20));

        if (hovered && hasPokemon) {
            for (int g = 3; g > 0; g--) {
                int alpha = 80 * g / 3;
                int glowColor = ARENA_SLOT_HOVER & 0xFFFFFF | (alpha << 24);
                graphics.fill(x - g, y - g, x + 48 + g, y - g + 1, glowColor);
                graphics.fill(x - g, y + 32 + g - 1, x + 48 + g, y + 32 + g, glowColor);
                graphics.fill(x - g, y - g, x - g + 1, y + 32 + g, glowColor);
                graphics.fill(x + 48 + g - 1, y - g, x + 48 + g, y + 32 + g, glowColor);
            }
        }

        if (fainted) {
            graphics.fill(x + 1, y + 1, x + 48 - 1, y + 32 - 1, color(8, 0, 4, 68));
        }
    }

    private void drawEmptySlotIcon(DrawContext graphics, TextRenderer font, int slotX, int slotY) {
        graphics.drawCenteredTextWithShadow(font, "○", slotX + 24, slotY + 16 - 4, SLOT_EMPTY_ICON);
    }

    private void renderPokemonModel(DrawContext graphics, Pokemon pokemon, int slotX, int slotY, int slotIndex, float partialTick) {
        FloatingState state = slotStates.computeIfAbsent(slotIndex, k -> new FloatingState());

        try {
            state.setCurrentAspects(pokemon.getAspects());
            Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(13.0), (float) Math.toRadians(35.0), 0.0F);

            graphics.getMatrices().push();
            graphics.getMatrices().translate(slotX + 23.0F, slotY - 1.0F, 0.0F);
            graphics.getMatrices().scale(2.6F, 2.6F, 1.0F);

            PokemonGuiUtilsKt.drawProfilePokemon(
                    pokemon.getSpecies().getResourceIdentifier(),
                    graphics.getMatrices(),
                    rotation,
                    PoseType.PROFILE,
                    state,
                    partialTick,
                    4.5F,
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
        } catch (Exception ignored) {
            graphics.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, pokemon.getDisplayName(false), slotX + 24, slotY + 12, TEXT_PRIMARY);
        }
    }

    private void drawLevelLabel(DrawContext graphics, TextRenderer font, Pokemon pokemon, int slotX, int slotY) {
        String levelLabel = Text.translatable("gui.cobblemon_arena.label.level_short", pokemon.getLevel()).getString();
        drawScaledText(graphics, font, levelLabel, slotX + 3, slotY + 32 - 8, TEXT_DIM, LABEL_SCALE);
    }

    private void drawHeldItemIcon(DrawContext graphics, ItemStack heldItem, int slotX, int slotY) {
        if (heldItem != null && !heldItem.isEmpty()) {
            graphics.getMatrices().push();
            graphics.getMatrices().translate(slotX + 48 - 10, slotY + 32 - 10, 0.0F);
            graphics.getMatrices().scale(0.5F, 0.5F, 1.0F);
            graphics.drawItem(heldItem, 0, 0);
            graphics.getMatrices().pop();
        }
    }

    private List<Text> buildTooltip(Pokemon pokemon) {
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(pokemon.getDisplayName(false).copy().formatted(Formatting.WHITE, Formatting.BOLD));
        tooltip.add(Text.translatable("gui.cobblemon_arena.label.level", pokemon.getLevel()).formatted(Formatting.GRAY));

        Nature nature = pokemon.getNature();
        tooltip.add(
                Text.translatable("gui.cobblemon_arena.label.nature").formatted(Formatting.GRAY)
                        .append(Text.translatable(nature.getDisplayName()).formatted(Formatting.AQUA))
                        .append(Text.translatable("gui.cobblemon_arena.label.nature_delta", formatNatureDelta(nature)).formatted(Formatting.DARK_GRAY))
        );

        tooltip.add(Text.translatable("gui.cobblemon_arena.label.ability").formatted(Formatting.GRAY).append(Text.translatable(pokemon.getAbility().getDisplayName()).formatted(Formatting.GOLD)));

        ItemStack held = pokemon.heldItem();
        if (held != null && !held.isEmpty()) {
            tooltip.add(Text.translatable("gui.cobblemon_arena.label.held_item").formatted(Formatting.GRAY).append(held.getName().copy().formatted(Formatting.YELLOW)));
        }

        if (pokemon.getShiny()) {
            tooltip.add(Text.translatable("gui.cobblemon_arena.label.shiny").formatted(Formatting.YELLOW, Formatting.BOLD));
        }

        tooltip.add(Text.translatable("gui.cobblemon_arena.label.moves").formatted(Formatting.GRAY));
        pokemon.getMoveSet()
                .getMoves()
                .forEach(move -> tooltip.add(Text.translatable("gui.cobblemon_arena.label.move_prefix").formatted(Formatting.DARK_GRAY).append(move.getDisplayName())));
        return tooltip;
    }

    private String formatNatureDelta(Nature nature) {
        try {
            var increased = nature.getIncreasedStat();
            var decreased = nature.getDecreasedStat();
            if (increased == null || decreased == null) return "";
            return "+" + increased.getDisplayName().getString() + ", -" + decreased.getDisplayName().getString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void drawScaledText(DrawContext graphics, TextRenderer font, String text, int x, int y, int color, float scale) {
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x, y, 0.0F);
        graphics.getMatrices().scale(scale, scale, 1.0F);
        graphics.drawText(font, text, 0, 0, color, false);
        graphics.getMatrices().pop();
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int b = Math.max(0, (color & 0xFF) - amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

