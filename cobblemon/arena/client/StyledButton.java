package cobblemon.arena.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class StyledButton extends ButtonWidget {
   private static final float TEXT_SCALE = 0.82F;
   private static final int BTN_BG = color(52, 18, 14, 255);
   private static final int BTN_HOVER = color(76, 24, 18, 255);
   private static final int BTN_DISABLED = color(30, 14, 13, 255);
   private static final int BTN_BORDER = color(148, 58, 40, 255);
   private static final int BTN_BORDER_HOVER = color(220, 96, 58, 255);
   private static final int TEXT_PRIMARY = color(255, 245, 240, 255);
   private static final int TEXT_DIM = color(130, 100, 90, 255);
   private final TextRenderer font = MinecraftClient.getInstance().textRenderer;
   private int accentColor;

   public StyledButton(int x, int y, int width, int height, Text message, PressAction onPress) {
      this(x, y, width, height, message, onPress, -1);
   }

   public StyledButton(int x, int y, int width, int height, Text message, PressAction onPress, int accentColor) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
      this.accentColor = accentColor;
   }

   public void setAccentColor(int accentColor) {
      this.accentColor = accentColor;
   }

   @Override
   protected void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovered = this.isHovered();
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      boolean tinted = this.accentColor != -1 && this.active;

      int bgColor;
      if (!this.active) {
         bgColor = BTN_DISABLED;
      } else if (tinted) {
         bgColor = hovered ? mix(BTN_HOVER, this.accentColor, 0.18F) : mix(BTN_BG, this.accentColor, 0.12F);
      } else {
         bgColor = hovered ? BTN_HOVER : BTN_BG;
      }

      graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
      if (this.active) {
         int sheenColor = tinted ? mix(color(255, 255, 255, 22), this.accentColor, 0.25F) : color(255, 200, 160, hovered ? 28 : 16);
         graphics.fill(x + 2, y + 1, x + w - 2, y + 3, sheenColor);
      }

      if (this.active) {
         graphics.fill(x + 1, y + h - 4, x + w - 1, y + h - 1, darken(bgColor, 22));
      }

      if (tinted) {
         graphics.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, mix(darken(bgColor, 18), this.accentColor, 0.3F));
      }

      int borderColor = tinted ? mix(BTN_BORDER, this.accentColor, hovered ? 0.7F : 0.45F) : (hovered ? BTN_BORDER_HOVER : BTN_BORDER);
      graphics.fill(x + 1, y, x + w - 1, y + 1, borderColor);
      graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, darken(borderColor, 35));
      graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);
      graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, darken(borderColor, 35));
      if (this.active) {
         graphics.fill(x + 2, y + 1, x + w - 2, y + 2, lighten(bgColor, 28));
         graphics.fill(x + 1, y + 2, x + 2, y + h - 3, lighten(bgColor, 14));
         graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, darken(bgColor, 14));
      }

      if (hovered) {
         int glowColor = tinted ? this.accentColor & 16777215 | 1342177280 : color(236, 106, 68, 60);
         int glowColorFaint = tinted ? this.accentColor & 16777215 | 805306368 : color(236, 106, 68, 35);
         graphics.fill(x + 1, y - 1, x + w - 1, y, glowColor);
         graphics.fill(x - 1, y + 1, x, y + h - 1, glowColorFaint);
         graphics.fill(x + 1, y + h, x + w - 1, y + h + 1, glowColorFaint);
         graphics.fill(x + w, y + 1, x + w + 1, y + h - 1, glowColorFaint);
      }

      int textColor = !this.active ? TEXT_DIM : (tinted ? mix(TEXT_PRIMARY, this.accentColor, 0.55F) : TEXT_PRIMARY);
      int scaledTextWidth = Math.round(this.font.getWidth(this.getMessage()) * TEXT_SCALE);
      graphics.getMatrices().push();
      graphics.getMatrices().translate(x + (w - scaledTextWidth) / 2.0F, y + Math.max(2.0F, (h - 8) / 2.0F), 0.0F);
      graphics.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
      if (this.active) {
         graphics.drawText(this.font, this.getMessage(), 1, 1, color(0, 0, 0, 90), false);
      }

      graphics.drawText(this.font, this.getMessage(), 0, 0, textColor, false);
      graphics.getMatrices().pop();
   }

   private static int color(int r, int g, int b, int a) {
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int lighten(int color, int amount) {
      int a = color >> 24 & 0xFF;
      int r = Math.min(255, (color >> 16 & 0xFF) + amount);
      int g = Math.min(255, (color >> 8 & 0xFF) + amount);
      int b = Math.min(255, (color & 0xFF) + amount);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int darken(int color, int amount) {
      int a = color >> 24 & 0xFF;
      int r = Math.max(0, (color >> 16 & 0xFF) - amount);
      int g = Math.max(0, (color >> 8 & 0xFF) - amount);
      int b = Math.max(0, (color & 0xFF) - amount);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int mix(int first, int second, float amount) {
      float t = Math.max(0.0F, Math.min(1.0F, amount));
      int a = Math.round((first >> 24 & 0xFF) + ((second >> 24 & 0xFF) - (first >> 24 & 0xFF)) * t);
      int r = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * t);
      int g = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * t);
      int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }
}
