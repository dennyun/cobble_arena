package cobblemon.arena.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class CycleSelectorButton extends ButtonWidget {
    private static final float TEXT_SCALE = 0.82F;
    private static final int BTN_BG = color(62, 22, 18, 255);
    private static final int BTN_HOVER = color(86, 28, 22, 255);
    private static final int BTN_DISABLED = color(34, 16, 15, 255);
    private static final int BTN_BORDER = color(164, 68, 48, 255);
    private static final int BTN_BORDER_HOVER = color(236, 106, 68, 255);
    private static final int TEXT_PRIMARY = color(255, 245, 240, 255);
    private static final int TEXT_DIM = color(140, 110, 100, 255);

    private final TextRenderer font;
    private final List<String> options;
    private final Consumer<String> onCycle;
    private final String suffix;
    private int selectedIndex = 0;

    public CycleSelectorButton(int x, int y, int width, int height, List<String> options, String initialValue, String suffix, Consumer<String> onCycle) {
        super(x, y, width, height, Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
        this.font = MinecraftClient.getInstance().textRenderer;
        this.options = new ArrayList<>(options == null ? List.of() : options);
        this.onCycle = onCycle != null ? onCycle : s -> {};
        this.suffix = suffix == null ? "" : suffix;
        setSelected(initialValue);
    }

    @Override
    public void onPress() {
        if (options.isEmpty()) return;
        selectedIndex = (selectedIndex + 1) % options.size();
        updateMessage();
        onCycle.accept(options.get(selectedIndex));
    }

    @Override
    protected void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = active && isHovered();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int bgColor = !active ? BTN_DISABLED : (hovered ? BTN_HOVER : BTN_BG);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        if (active) {
            graphics.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, darken(bgColor, 22));
            graphics.fill(x + 2, y + 1, x + w - 2, y + 3, color(255, 200, 160, 14));
        }

        int borderColor = hovered ? BTN_BORDER_HOVER : BTN_BORDER;
        graphics.fill(x + 1, y, x + w - 1, y + 1, borderColor);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, darken(borderColor, 30));
        graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, darken(borderColor, 30));
        if (active) {
            graphics.fill(x + 2, y + 1, x + w - 2, y + 2, lighten(bgColor, 28));
            graphics.fill(x + 1, y + 2, x + 2, y + h - 3, lighten(bgColor, 14));
            graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, darken(bgColor, 14));
        }

        int textColor = active ? TEXT_PRIMARY : TEXT_DIM;
        int scaledTextWidth = Math.round(font.getWidth(getMessage()) * TEXT_SCALE);
        graphics.getMatrices().push();
        graphics.getMatrices().translate(x + (w - scaledTextWidth) / 2.0F, y + Math.max(2.0F, (h - 8) / 2.0F), 0.0F);
        graphics.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawText(font, getMessage(), 0, 0, textColor, false);
        graphics.getMatrices().pop();
    }

    public String getSelected() {
        return options.isEmpty() ? "" : options.get(selectedIndex);
    }

    public void setSelected(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) {
                selectedIndex = i;
                break;
            }
        }
        updateMessage();
    }

    private void updateMessage() {
        String value = getSelected();
        setMessage(Text.literal(suffix.isBlank() ? value : value + " " + suffix));
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

