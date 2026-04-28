package cobblemon.arena.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * Lightweight checkbox used by Arena screens.
 */
public class CheckboxWidget {
    private static final float TEXT_SCALE = 0.8F;
    private static final int BOX_SIZE = 8;
    private static final int BOX_BG = color(48, 16, 14, 255);
    private static final int BOX_BORDER = color(166, 66, 48, 255);
    private static final int BOX_CHECK = color(255, 116, 58, 255);
    private static final int TEXT_COLOR = color(200, 170, 160, 255);

    private final TextRenderer font;
    private final int x;
    private final int y;
    private final Text label;
    private final Consumer<Boolean> onChange;
    private boolean checked = false;

    public CheckboxWidget(int x, int y, Text label, Consumer<Boolean> onChange) {
        this.font = MinecraftClient.getInstance().textRenderer;
        this.x = x;
        this.y = y;
        this.label = label == null ? Text.empty() : label;
        this.onChange = onChange != null ? onChange : v -> {};
    }

    public void render(DrawContext graphics, int mouseX, int mouseY) {
        graphics.fill(x, y, x + BOX_SIZE, y + BOX_SIZE, BOX_BG);
        graphics.drawBorder(x, y, BOX_SIZE, BOX_SIZE, BOX_BORDER);
        if (checked) {
            graphics.fill(x + 2, y + 2, x + BOX_SIZE - 2, y + BOX_SIZE - 2, BOX_CHECK);
        }

        graphics.getMatrices().push();
        graphics.getMatrices().translate(x + BOX_SIZE + 4, y + 1, 0.0F);
        graphics.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawText(font, label, 0, 0, TEXT_COLOR, false);
        graphics.getMatrices().pop();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int labelWidth = Math.round(font.getWidth(label) * TEXT_SCALE);
        int totalWidth = 12 + labelWidth;
        if (mouseX >= x && mouseX < x + totalWidth && mouseY >= y && mouseY < y + BOX_SIZE) {
            checked = !checked;
            onChange.accept(checked);
            return true;
        }
        return false;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

