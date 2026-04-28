package cobblemon.arena.client;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class FormatDropdown {
    private static final float TEXT_SCALE = 0.82F;

    private static final int DROPDOWN_BG = color(52, 18, 16, 255);
    private static final int DROPDOWN_HOVER = color(76, 24, 20, 255);
    private static final int DROPDOWN_BORDER = color(170, 68, 48, 255);
    private static final int TEXT_COLOR = color(255, 245, 240, 255);
    private static final int ACCENT = color(220, 80, 50, 255);

    private final TextRenderer font;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<String> options;
    private final Consumer<String> onSelect;
    private int selectedIndex = 0;
    private boolean expanded = false;

    public FormatDropdown(int x, int y, int width, int height, List<String> options, Consumer<String> onSelect) {
        this.font = MinecraftClient.getInstance().textRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.options = new ArrayList<>(options);
        this.onSelect = onSelect;
    }

    public void render(DrawContext graphics, int mouseX, int mouseY) {
        boolean mainHovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        int bgColor = mainHovered ? DROPDOWN_HOVER : DROPDOWN_BG;

        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fill(x, y, x + width, y + height, darken(bgColor, 10));
        graphics.fill(x, y, x + width, y + 1, DROPDOWN_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, darken(DROPDOWN_BORDER, 30));
        graphics.fill(x, y, x + 1, y + height, DROPDOWN_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, darken(DROPDOWN_BORDER, 30));

        String text = options.get(selectedIndex) + " ▼";
        drawScaledText(graphics, text, x + 4, y + Math.max(2, (height - 8) / 2), ACCENT);

        if (expanded) {
            int menuY = y + height + 2;
            for (int i = 0; i < options.size(); i++) {
                int optionY = menuY + i * height;
                boolean optionHovered = isMouseOver(mouseX, mouseY, x, optionY, width, height);
                int optionBg = optionHovered ? DROPDOWN_HOVER : DROPDOWN_BG;

                graphics.fill(x, optionY, x + width, optionY + height, optionBg);
                graphics.fill(x, optionY, x + width, optionY + 1, DROPDOWN_BORDER);
                graphics.fill(x, optionY + height - 1, x + width, optionY + height, DROPDOWN_BORDER);
                graphics.fill(x, optionY, x + 1, optionY + height, DROPDOWN_BORDER);
                graphics.fill(x + width - 1, optionY, x + width, optionY + height, DROPDOWN_BORDER);

                int textColor = i == selectedIndex ? ACCENT : TEXT_COLOR;
                drawScaledText(graphics, options.get(i), x + 4, optionY + Math.max(2, (height - 8) / 2), textColor);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (isMouseOver((int) mouseX, (int) mouseY, x, y, width, height)) {
            expanded = !expanded;
            return true;
        }

        if (expanded) {
            int menuY = y + height + 2;
            for (int i = 0; i < options.size(); i++) {
                int optionY = menuY + i * height;
                if (isMouseOver((int) mouseX, (int) mouseY, x, optionY, width, height)) {
                    selectedIndex = i;
                    expanded = false;
                    onSelect.accept(options.get(i));
                    return true;
                }
            }
            expanded = false;
            return true;
        }

        return false;
    }

    public String getSelected() {
        return options.get(selectedIndex);
    }

    public void setSelected(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) {
                selectedIndex = i;
                break;
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawScaledText(DrawContext graphics, String text, int x, int y, int color) {
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x, y, 0.0F);
        graphics.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawText(font, text, 0, 0, color, false);
        graphics.getMatrices().pop();
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
}

