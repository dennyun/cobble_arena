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
 * Rendering responsibilities:
 *  - renderBackground() is a deliberate no-op so Minecraft's built-in dark
 *    vignette / background blur does NOT draw on top of our overlay.
 *  - render() drives the entire frame: it paints the transition overlay first
 *    (background layer), then calls super.render() so that any registered
 *    child widgets (none currently, but kept for future safety) are composited
 *    on top, and finally draws any per-frame HUD elements that must sit above
 *    everything else.
 */
public class ArenaBattleLeadPreviewScreen extends Screen {

    private final ArenaBattleTransitionOverlay overlay =
        ArenaBattleTransitionOverlay.getInstance();

    /** Party-slot index the player has confirmed as their lead, or -1 if none. */
    private int selectedOwnSlot = -1;

    public ArenaBattleLeadPreviewScreen() {
        super(Text.translatable("gui.cobblemon_arena.title.lead_preview"));
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Close as soon as the overlay expires or the actual battle has begun.
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

    /**
     * Intentionally empty — suppresses Minecraft's built-in in-game dark
     * overlay / background blur.  Our overlay draws its own full-screen
     * background in {@link #render}, so the vanilla backdrop must not appear.
     */
    @Override
    public void renderBackground(
        DrawContext graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        // no-op: suppress default Minecraft background
    }

    /**
     * Main render entry point.
     *
     * Draw order:
     *  1. Transition overlay (fullscreen background + team panels + countdown).
     *  2. super.render() → renderBackground() (no-op) + any child widgets.
     */
    @Override
    public void render(
        DrawContext graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        // Determine which own slot is under the cursor this frame so the overlay
        // can paint the hover highlight without an extra query call.
        int hoveredSlot = this.overlay.getOwnPartySlotAt(
            mouseX,
            mouseY,
            this.width,
            this.height
        );

        // 1. Draw the fullscreen transition overlay (background layer).
        this.overlay.render(
            graphics,
            this.width,
            this.height,
            delta,
            this.selectedOwnSlot,
            hoveredSlot
        );

        // 2. Let Screen render registered widgets (none today, but keeps the
        //    widget pipeline intact for future additions).  renderBackground()
        //    is called internally by super.render() but is a no-op above, so
        //    it will not overwrite what we just drew.
        super.render(graphics, mouseX, mouseY, delta);
    }

    // ── Input ────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only respond to left-click.
        if (button != 0) return true;

        int slotIndex = this.overlay.getOwnPartySlotAt(
            mouseX,
            mouseY,
            this.width,
            this.height
        );

        if (slotIndex >= 0 && this.overlay.hasOwnPokemonAt(slotIndex)) {
            this.selectedOwnSlot = slotIndex;
            // Inform the server of the player's lead selection.
            ClientPlayNetworking.send(new SelectArenaLeadPacket(slotIndex));
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Block Escape (keyCode 256) — the player must wait for the timer.
        if (keyCode == 256) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ── Teardown ─────────────────────────────────────────────────────────────────

    @Override
    public void close() {
        this.overlay.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == this) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
