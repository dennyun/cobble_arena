package cobblemon.arena;

import cobblemon.arena.client.ArenaShellScreen;
import cobblemon.arena.network.ArenaBattleTransitionPacket;
import cobblemon.arena.network.CancelQueuePacket;
import cobblemon.arena.network.ClaimQuestRewardPacket;
import cobblemon.arena.network.ClientPacketHandler;
import cobblemon.arena.network.DeleteCustomLadderTemplatePacket;
import cobblemon.arena.network.JoinQueuePacket;
import cobblemon.arena.network.LoadCustomLadderTemplatePacket;
import cobblemon.arena.network.MatchFoundPacket;
import cobblemon.arena.network.ArenaRankedSyncPacket;
import cobblemon.arena.network.OpenArenaGuiPacket;
import cobblemon.arena.network.PostMatchResultsPacket;
import cobblemon.arena.network.QueueStatusPacket;
import cobblemon.arena.network.RequestRankedConfigPacket;
import cobblemon.arena.network.ResetRankedLadderPacket;
import cobblemon.arena.network.SaveCustomLadderTemplatePacket;
import cobblemon.arena.network.SelectArenaLeadPacket;
import cobblemon.arena.network.SpectateArenaBattlePacket;
import cobblemon.arena.network.SyncQuestDataPacket;
import cobblemon.arena.network.SyncRankedConfigPacket;
import cobblemon.arena.network.UpdateRankedConfigPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side bootstrap for Cobblemon Arena.
 */
public final class CobblemonArenaClient {

    private CobblemonArenaClient() {}

    public static void init() {
        registerPayloadTypes();
        registerReceivers();
        ClientPacketHandler.setScreenOpener(() ->
            MinecraftClient.getInstance().setScreen(new ArenaShellScreen())
        );

        // Arena Chat Panel — capture ONLY global (/g) messages.
        //
        // Strategy:
        //  • GAME event: messages formatted as "[G] Name: text" or
        //    starting with "[G]" (standard plugin output for /g command).
        //  • CHAT event: all signed chat is captured but ONLY kept when
        //    it looks like a /g broadcast (contains "[G]" marker or the
        //    raw command echo "/g "). Regular Minecraft chat is skipped.
        //
        // Both routes strip the [G] prefix so the panel shows clean text.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
            (message, overlay) -> {
                if (overlay) return; // skip action-bar / title messages
                String raw = message.getString();
                // Accept only messages that carry the /g command marker.
                boolean isGlobal =
                    raw.startsWith("[G]") || raw.startsWith("/g ");
                if (isGlobal) {
                    String clean = raw
                        .replaceFirst("^\\[G\\]\\s*", "")
                        .replaceFirst("^/g\\s+", "")
                        .trim();
                    if (!clean.isBlank()) {
                        cobblemon.arena.client.ArenaChatState.addMessage(clean);
                    }
                }
            }
        );
        // Some servers deliver /g messages as signed CHAT packets.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register(
            (message, signedMessage, sender, params, receptionTimestamp) -> {
                String raw = message.getString();
                boolean isGlobal =
                    raw.startsWith("[G]") || raw.startsWith("/g ");
                if (isGlobal) {
                    String clean = raw
                        .replaceFirst("^\\[G\\]\\s*", "")
                        .replaceFirst("^/g\\s+", "")
                        .trim();
                    if (!clean.isBlank()) {
                        cobblemon.arena.client.ArenaChatState.addMessage(clean);
                    }
                }
            }
        );
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            (drawContext, tickCounter) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof ArenaShellScreen) {
                    return;
                }
                cobblemon.arena.client.QueueStatusOverlay.getInstance().render(
                    drawContext,
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight()
                );
            }
        );

        CobblemonArena.LOGGER.info("Cobblemon Arena client initialized.");
    }

    private static void registerPayloadTypes() {
        registerS2CPayloadType(OpenArenaGuiPacket.ID, OpenArenaGuiPacket.CODEC);
        registerS2CPayloadType(QueueStatusPacket.TYPE, QueueStatusPacket.CODEC);
        registerS2CPayloadType(MatchFoundPacket.TYPE, MatchFoundPacket.CODEC);
        registerS2CPayloadType(
            ArenaBattleTransitionPacket.TYPE,
            ArenaBattleTransitionPacket.CODEC
        );
        registerS2CPayloadType(
            SyncRankedConfigPacket.TYPE,
            SyncRankedConfigPacket.CODEC
        );
        registerS2CPayloadType(
            PostMatchResultsPacket.ID,
            PostMatchResultsPacket.CODEC
        );
        registerS2CPayloadType(
            SyncQuestDataPacket.TYPE,
            SyncQuestDataPacket.CODEC
        );
        registerS2CPayloadType(
            cobblemon.arena.network.ArenaServerStatusPacket.TYPE,
            cobblemon.arena.network.ArenaServerStatusPacket.CODEC
        );
        registerS2CPayloadType(
            ArenaRankedSyncPacket.TYPE,
            ArenaRankedSyncPacket.CODEC
        );

        registerC2SPayloadType(JoinQueuePacket.TYPE, JoinQueuePacket.CODEC);
        registerC2SPayloadType(CancelQueuePacket.TYPE, CancelQueuePacket.CODEC);
        registerC2SPayloadType(
            RequestRankedConfigPacket.TYPE,
            RequestRankedConfigPacket.CODEC
        );
        registerC2SPayloadType(
            UpdateRankedConfigPacket.TYPE,
            UpdateRankedConfigPacket.CODEC
        );
        registerC2SPayloadType(
            ResetRankedLadderPacket.TYPE,
            ResetRankedLadderPacket.CODEC
        );
        registerC2SPayloadType(
            SaveCustomLadderTemplatePacket.TYPE,
            SaveCustomLadderTemplatePacket.CODEC
        );
        registerC2SPayloadType(
            LoadCustomLadderTemplatePacket.TYPE,
            LoadCustomLadderTemplatePacket.CODEC
        );
        registerC2SPayloadType(
            DeleteCustomLadderTemplatePacket.TYPE,
            DeleteCustomLadderTemplatePacket.CODEC
        );
        registerC2SPayloadType(
            SelectArenaLeadPacket.TYPE,
            SelectArenaLeadPacket.CODEC
        );
        registerC2SPayloadType(
            SpectateArenaBattlePacket.TYPE,
            SpectateArenaBattlePacket.CODEC
        );
        registerC2SPayloadType(
            ClaimQuestRewardPacket.TYPE,
            ClaimQuestRewardPacket.CODEC
        );
    }

    private static <
        T extends net.minecraft.network.packet.CustomPayload
    > void registerS2CPayloadType(
        net.minecraft.network.packet.CustomPayload.Id<T> type,
        net.minecraft.network.codec.PacketCodec<
            ? super net.minecraft.network.RegistryByteBuf,
            T
        > codec
    ) {
        try {
            PayloadTypeRegistry.playS2C().register(type, codec);
        } catch (IllegalArgumentException ignored) {
            // Already registered.
        }
    }

    private static <
        T extends net.minecraft.network.packet.CustomPayload
    > void registerC2SPayloadType(
        net.minecraft.network.packet.CustomPayload.Id<T> type,
        net.minecraft.network.codec.PacketCodec<
            ? super net.minecraft.network.RegistryByteBuf,
            T
        > codec
    ) {
        try {
            PayloadTypeRegistry.playC2S().register(type, codec);
        } catch (IllegalArgumentException ignored) {
            // Already registered.
        }
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
            OpenArenaGuiPacket.ID,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleOpenArenaGui(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            QueueStatusPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleQueueStatus(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            MatchFoundPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() -> ClientPacketHandler.handleMatchFound(packet))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ArenaBattleTransitionPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleArenaBattleTransition(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            SyncRankedConfigPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleSyncRankedConfig(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            PostMatchResultsPacket.ID,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handlePostMatchResults(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            SyncQuestDataPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleSyncQuestData(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            cobblemon.arena.network.ArenaServerStatusPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() ->
                        ClientPacketHandler.handleServerStatus(packet)
                    )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ArenaRankedSyncPacket.TYPE,
            (packet, context) ->
                context
                    .client()
                    .execute(() -> ClientPacketHandler.handleRankedSync(packet))
        );
    }
}
