package cobblemon.arena.mixin.client;

import cobblemon.arena.client.ArenaBattleClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculta a hotbar do jogador enquanto uma batalha de arena está ativa,
 * para não poluir a tela durante o combate Cobblemon.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void arena_hideHotbarDuringBattle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ArenaBattleClientState.isArenaBattleActive() || ArenaBattleClientState.hasPendingTransition() || cobblemon.arena.client.ArenaClientState.isSpectating()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void arena_hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (ArenaBattleClientState.isArenaBattleActive() || ArenaBattleClientState.hasPendingTransition() || cobblemon.arena.client.ArenaClientState.isSpectating()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void arena_hideExpBar(DrawContext context, int x, CallbackInfo ci) {
        if (ArenaBattleClientState.isArenaBattleActive() || ArenaBattleClientState.hasPendingTransition() || cobblemon.arena.client.ArenaClientState.isSpectating()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void arena_renderBattleTimer(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ArenaBattleClientState.isArenaBattleActive()) {
            long ms = ArenaBattleClientState.getBattleDurationMs();
            long s = ms / 1000;
            String timeStr = String.format("%02d:%02d", s / 60, s % 60);
            net.minecraft.client.font.TextRenderer tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            int w = tr.getWidth(timeStr);
            int x = (context.getScaledWindowWidth() - w) / 2;
            int y = 8;
            
            context.fill(x - 24, y - 4, x + w + 24, y + 12, 0x90000000);
            context.fill(x - 24, y - 4, x + w + 24, y - 3, 0xFFaa00aa); // Top purple line
            context.drawText(tr, timeStr, x, y, 0xFFFFFF, true);
        }
    }
}
