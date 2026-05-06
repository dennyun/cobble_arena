package cobblemon.arena.client;

import cobblemon.arena.network.SelectArenaLeadPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Fullscreen lead-selection screen shown during the arena battle transition.
 *
 * Supports selecting 1 lead (Singles), 2 leads (Doubles) or 3 leads (Triples).
 * Sends {@link SelectArenaLeadPacket} with all selected slots. When all
 * required leads are chosen on both sides the server starts immediately,
 * without waiting for the full countdown.
 */
public class ArenaBattleLeadPreviewScreen extends Screen {

    private final ArenaBattleTransitionOverlay overlay =
        ArenaBattleTransitionOverlay.getInstance();
    private boolean leadSelectionConfirmed = false;

    public ArenaBattleLeadPreviewScreen() {
        super(Text.translatable("gui.cobblemon_arena.title.lead_preview"));
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Advance timers that have no registered ClientTickEvents listener.
        this.overlay.tick();
        ArenaBattleClientState.tick();

        if (!this.overlay.isActive()) {
            this.close();
            return;
        }
        if (ArenaBattleClientState.isArenaBattleActive()) {
            this.close();
            return;
        }
        if (
            this.overlay.getRemainingTicks() <= 0 &&
            !ArenaBattleClientState.hasPendingTransition()
        ) {
            this.close();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext gfx, int mx, int my, float delta) {
        // no-op: suppress default Minecraft dark overlay
    }

    @Override
    public void render(DrawContext gfx, int mx, int my, float delta) {
        int hoveredSlot = this.overlay.getOwnPartySlotAt(
            mx,
            my,
            this.width,
            this.height
        );
        int hoveredOpponentSlot = this.overlay.getOpponentSlotAt(
            mx,
            my,
            this.width,
            this.height
        );

        this.overlay.render(
            gfx,
            this.width,
            this.height,
            delta,
            this.overlay.getSelectedOwnSlots(),
            hoveredSlot,
            hoveredOpponentSlot
        );
        this.drawConfirmButton(gfx);

        super.render(gfx, mx, my, delta);
    }

    // ── Input ────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        int slotIndex = this.overlay.getOwnPartySlotAt(
            mx,
            my,
            this.width,
            this.height
        );
        if (slotIndex >= 0 && this.overlay.hasOwnPokemonAt(slotIndex)) {
            boolean changed = this.overlay.toggleOwnSlot(slotIndex);
            if (changed) {
                this.leadSelectionConfirmed = false;
            }
            return true;
        }

        if (isOverConfirmButton(mx, my) && this.overlay.hasAllLeadsSelected()) {
            java.util.List<Integer> selected =
                this.overlay.getSelectedOwnSlots();
            ClientPlayNetworking.send(new SelectArenaLeadPacket(selected));
            this.leadSelectionConfirmed = true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return true; // Block Escape during transition
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ── Teardown ─────────────────────────────────────────────────────────────────

    @Override
    public void close() {
        this.overlay.clear();
        this.leadSelectionConfirmed = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == this) client.setScreen(null);
    }

    private boolean isOverConfirmButton(double mx, double my) {
        int bw = 130;
        int bh = 20;
        int bx = this.width / 2 - bw / 2;
        int by = ArenaBattleTransitionOverlay.getConfirmButtonY(this.height);
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void drawConfirmButton(DrawContext gfx) {
        int bw = 130;
        int bh = 20;
        int bx = this.width / 2 - bw / 2;
        int by = ArenaBattleTransitionOverlay.getConfirmButtonY(this.height);
        boolean enabled = this.overlay.hasAllLeadsSelected();
        int bg = enabled ? 0xCC3AA76B : 0x882E2E2E;
        int border = enabled ? 0xFF6AE79B : 0xFF666666;
        int text = enabled ? 0xFFF8F2EC : 0xFFB3B3B3;
        if (leadSelectionConfirmed && enabled) {
            bg = 0xCC3656A7;
            border = 0xFF79A3FF;
        }
        gfx.fill(bx, by, bx + bw, by + bh, bg);
        gfx.fill(bx, by, bx + bw, by + 1, border);
        gfx.fill(bx, by + bh - 1, bx + bw, by + bh, border);
        gfx.fill(bx, by, bx + 1, by + bh, border);
        gfx.fill(bx + bw - 1, by, bx + bw, by + bh, border);
        String label = leadSelectionConfirmed
            ? "Confirmado"
            : "Confirmar Leads";
        int tw = this.textRenderer.getWidth(label);
        gfx.drawText(
            this.textRenderer,
            label,
            bx + (bw - tw) / 2,
            by + 6,
            text,
            false
        );
    }
}
