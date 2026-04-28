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

        // Capture player chat messages for the Arena chat panel.
        // We use the CHAT event (not GAME) so only real player-sent messages
        // are captured — system messages (join/leave, commands, etc.) are excluded.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register(
            (message, signedMessage, sender, params, receptionTimestamp) -> {
                String raw = message.getString();
                // Strip /g or [G] prefixes that some chat plugins add
                String clean = raw
                    .replaceFirst("^\\[G\\]\\s*", "")
                    .replaceFirst("^/g\\s+", "")
                    .trim();
                if (!clean.isBlank()) {
                    cobblemon.arena.client.ArenaChatState.addMessage(clean);
                }
            }
        );
        // Also capture GAME messages that look like player chat
        // (some servers route /g through game messages).
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
            (message, overlay) -> {
                if (overlay) return; // skip action-bar
                String raw = message.getString();
                // Only include messages that look like "Name: text" (player chat)
                // or that contain the /g prefix added by the global chat command.
                if (raw.contains(": ") || raw.startsWith("[G]")) {
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
    }
}
