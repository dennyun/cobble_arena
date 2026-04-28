package cobblemon.arena.network;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.access.ArenaAccessService;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.battle.ArenaSession;
import cobblemon.arena.battle.ArenaSpectatorManager;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.ladder.ArenaPartyValidator;
import cobblemon.arena.network.ClaimQuestRewardPacket;
import cobblemon.arena.network.QuestEntryPayload;
import cobblemon.arena.network.SyncQuestDataPacket;
import cobblemon.arena.queue.MatchmakingQueue;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ServerPacketHandler {

    private ServerPacketHandler() {}

    public static void register() {
        registerPayloadTypes();
        registerReceivers();
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
            ArenaServerStatusPacket.TYPE,
            ArenaServerStatusPacket.CODEC
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
        ServerPlayNetworking.registerGlobalReceiver(
            JoinQueuePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context.server().execute(() -> handleJoinQueue(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            CancelQueuePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() ->
                        MatchmakingQueue.getInstance().leaveQueue(player, true)
                    );
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            RequestRankedConfigPacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context.server().execute(() -> sendRankedConfig(player));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            UpdateRankedConfigPacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleUpdateRankedConfig(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ResetRankedLadderPacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context.server().execute(() -> handleResetRankedLadder(player));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            SaveCustomLadderTemplatePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleSaveTemplate(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            LoadCustomLadderTemplatePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleLoadTemplate(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            DeleteCustomLadderTemplatePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleDeleteTemplate(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            SelectArenaLeadPacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleSelectLead(player, packet));
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            SpectateArenaBattlePacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() ->
                        ArenaSpectatorManager.getInstance().spectateRandomBattle(
                            player
                        )
                    );
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ClaimQuestRewardPacket.TYPE,
            (packet, context) -> {
                ServerPlayerEntity player = context.player();
                context
                    .server()
                    .execute(() -> handleClaimQuestReward(player, packet));
            }
        );
    }

    private static void handleJoinQueue(
        ServerPlayerEntity player,
        JoinQueuePacket packet
    ) {
        String ladderId = packet.ladderId();

        // Try direct ArenaLadder lookup first
        ArenaLadder ladder = ArenaLadder.byId(ladderId);

        // If not found, try FormatPreset (new client sends FormatPreset IDs)
        if (ladder == null) {
            cobblemon.arena.format.FormatPreset preset =
                cobblemon.arena.format.FormatPreset.byId(ladderId);
            if (preset != null) {
                ladder = resolveArenaLadderFromPreset(preset);
            }
        }

        if (ladder == null) {
            player.sendMessage(
                Text.literal(
                    "§cFormato inválido: §f" +
                        ladderId +
                        "§c. Use §f/arena formats §cpara ver os disponíveis."
                ),
                false
            );
            sendQueueRejected(player);
            return;
        }

        // ── Party validation ────────────────────────────────────────────────
        java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> party =
            cobblemon.arena.ladder.ArenaPartyValidator.getPartyPokemon(player);

        // 1. Team size (all formats require 6 Pokémon for queue entry)
        int required = ladder.getRequiredTeamSize();
        if (party.size() < required) {
            player.sendMessage(
                net.minecraft.text.Text.literal(
                    "§cTime insuficiente: precisa de §f" +
                        required +
                        " §cPokémon para entrar na fila. Você tem §f" +
                        party.size() +
                        "§c."
                ),
                false
            );
            sendQueueRejected(player);
            return;
        }

        // 2. All-fainted guard
        boolean hasHealthy = party.stream().anyMatch(p -> !p.isFainted());
        if (!hasHealthy) {
            player.sendMessage(
                net.minecraft.text.Text.literal(
                    "§cTodos os seus Pokémon estão debilitados. Cure seu time antes de entrar em batalha."
                ),
                false
            );
            sendQueueRejected(player);
            return;
        }

        // 3. Item clause (no duplicate held items)
        if (ladder.enforcesItemClause()) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (com.cobblemon.mod.common.pokemon.Pokemon p : party) {
                if (p.heldItem() != null && !p.heldItem().isEmpty()) {
                    String itemId = p.heldItem().getItem().toString();
                    if (!seen.add(itemId)) {
                        player.sendMessage(
                            net.minecraft.text.Text.literal(
                                "§cItem repetido no time: §f" +
                                    itemId +
                                    "§c. Cada Pokémon deve ter um item diferente."
                            ),
                            false
                        );
                        sendQueueRejected(player);
                        return;
                    }
                }
            }
        }

        MatchmakingQueue.getInstance().joinQueue(player, ladder);
    }

    /** Sends a QueueStatusPacket(inQueue=false) so the client resets the counter. */
    private static void sendQueueRejected(
        net.minecraft.server.network.ServerPlayerEntity player
    ) {
        cobblemon.arena.access.ArenaNet.send(
            player,
            new QueueStatusPacket(
                false,
                "",
                "",
                "",
                0,
                MatchmakingQueue.getInstance().getTotalPlayersInQueue()
            )
        );
    }

    private static ArenaLadder resolveArenaLadderFromPreset(
        cobblemon.arena.format.FormatPreset preset
    ) {
        String format = preset.getStructure().getCobblemonId(); // "singles", "doubles", "triples"
        String level = String.valueOf(preset.getLevelCap()); // "50" or "100"
        boolean allowLeg = preset.allowsRestrictedPokemon();

        if (preset.isRanked()) {
            return ArenaLadder.rankedPresetByChoice(format, level, false);
        } else if (preset.isMonotype()) {
            return ArenaLadder.vgcPresetByChoice("monotype", level, allowLeg);
        } else {
            return ArenaLadder.vgcPresetByChoice(format, level, allowLeg);
        }
    }

    private static void sendRankedConfig(ServerPlayerEntity player) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        boolean canEdit = player.hasPermissionLevel(2);
        ServerPlayNetworking.send(
            player,
            new SyncRankedConfigPacket(
                ArenaServerConfig.snapshotToJson(config.toSnapshot()),
                canEdit,
                canEdit,
                config.listSavedCustomLadderTemplateNames()
            )
        );
    }

    private static void handleUpdateRankedConfig(
        ServerPlayerEntity player,
        UpdateRankedConfigPacket packet
    ) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(
                Text.literal("§cSem permissão para editar a ladder ranqueada."),
                false
            );
            return;
        }
        ArenaServerConfig.Snapshot snapshot =
            ArenaServerConfig.snapshotFromJson(packet.configJson());
        ArenaServerConfig.getInstance().setSnapshot(snapshot);
        sendRankedConfig(player);
        ArenaAccessService.openMainGui(player);
    }

    private static void handleResetRankedLadder(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(
                Text.literal("§cSem permissão para resetar a ladder."),
                false
            );
            return;
        }
        ArenaServerConfig.getInstance().setSnapshot(
            new ArenaServerConfig.Snapshot()
        );
        sendRankedConfig(player);
    }

    private static void handleSaveTemplate(
        ServerPlayerEntity player,
        SaveCustomLadderTemplatePacket packet
    ) {
        if (!player.hasPermissionLevel(2)) {
            return;
        }
        ArenaServerConfig.RankedLadderConfig ladderConfig =
            ArenaServerConfig.rankedLadderFromJson(packet.ladderConfigJson());
        ArenaServerConfig.getInstance().saveCustomLadderTemplate(ladderConfig);
        sendRankedConfig(player);
    }

    private static void handleLoadTemplate(
        ServerPlayerEntity player,
        LoadCustomLadderTemplatePacket packet
    ) {
        if (!player.hasPermissionLevel(2)) {
            return;
        }
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        ArenaServerConfig.RankedLadderConfig loaded =
            config.loadCustomLadderTemplate(
                packet.templateName(),
                packet.ladderIndex()
            );
        if (loaded == null) {
            player.sendMessage(
                Text.literal("§cModelo de ladder não encontrado."),
                false
            );
            return;
        }

        ArenaServerConfig.Snapshot snapshot = config.toSnapshot();
        List<ArenaServerConfig.RankedLadderConfig> ladders =
            snapshot.getRankedLadders();
        if (
            packet.ladderIndex() < 0 || packet.ladderIndex() >= ladders.size()
        ) {
            player.sendMessage(
                Text.literal("§cÍndice de ladder inválido."),
                false
            );
            return;
        }
        ladders.set(packet.ladderIndex(), loaded);
        snapshot.setRankedLadders(ladders);
        config.setSnapshot(snapshot);
        sendRankedConfig(player);
    }

    private static void handleDeleteTemplate(
        ServerPlayerEntity player,
        DeleteCustomLadderTemplatePacket packet
    ) {
        if (!player.hasPermissionLevel(2)) {
            return;
        }
        boolean deleted =
            ArenaServerConfig.getInstance().deleteCustomLadderTemplate(
                packet.templateName()
            );
        if (!deleted) {
            player.sendMessage(
                Text.literal(
                    "§eNenhum modelo encontrado com o nome: " +
                        packet.templateName()
                ),
                false
            );
        }
        sendRankedConfig(player);
    }

    private static void handleSelectLead(
        ServerPlayerEntity player,
        SelectArenaLeadPacket packet
    ) {
        ArenaSession session = ArenaBattleManager.getInstance().getSession(
            player
        );
        if (session == null || !session.isActive()) {
            return;
        }
        List<Pokemon> party = ArenaPartyValidator.getPartyPokemon(player);
        int slot = packet.slotIndex();
        if (slot < 0 || slot >= party.size()) {
            return;
        }
        Pokemon selected = party.get(slot);
        if (selected == null) {
            return;
        }

        UUID selectedId = selected.getUuid();
        session.setSelectedLead(player, selectedId);
        CobblemonArena.LOGGER.debug(
            "Player {} selected arena lead slot {} ({})",
            player.getName().getString(),
            slot,
            selectedId
        );
        ArenaBattleManager.getInstance().tryStartPendingBattleIfReady(session);
    }

    private static void handleClaimQuestReward(
        ServerPlayerEntity player,
        ClaimQuestRewardPacket packet
    ) {
        cobblemon.arena.quest.QuestManager qm =
            cobblemon.arena.quest.QuestManager.getInstance();
        boolean success = qm.claimQuestReward(player, packet.questId());
        if (success) {
            // Re-send updated quest state so client reflects the claim
            ServerPlayNetworking.send(player, buildQuestPacket(player));
        } else {
            player.sendMessage(
                net.minecraft.text.Text.literal(
                    "§cMissão não encontrada ou já resgatada: §f" +
                        packet.questId()
                ),
                false
            );
        }
    }

    public static SyncQuestDataPacket buildQuestPacket(
        ServerPlayerEntity player
    ) {
        cobblemon.arena.quest.QuestManager qm =
            cobblemon.arena.quest.QuestManager.getInstance();
        cobblemon.arena.quest.PlayerQuestProgress progress =
            qm.getPlayerProgress(player);

        java.util.List<QuestEntryPayload> daily = qm
            .getActiveDailyQuests(player)
            .stream()
            .map(q -> toQuestEntry(q, progress))
            .toList();

        java.util.List<QuestEntryPayload> weekly = qm
            .getActiveWeeklyQuests(player)
            .stream()
            .map(q -> toQuestEntry(q, progress))
            .toList();

        return new SyncQuestDataPacket(daily, weekly);
    }

    private static QuestEntryPayload toQuestEntry(
        cobblemon.arena.quest.Quest q,
        cobblemon.arena.quest.PlayerQuestProgress progress
    ) {
        int current = progress.getProgress(q.getId());
        boolean completed = progress.isCompleted(
            q.getId(),
            q.getTargetAmount()
        );
        boolean claimed = progress.isClaimed(q.getId());
        String reward = (q.getReward() != null)
            ? q.getReward().getDescription()
            : "";
        return new QuestEntryPayload(
            q.getId(),
            q.getTitle(),
            q.getDescription(),
            Math.min(current, q.getTargetAmount()),
            q.getTargetAmount(),
            completed,
            claimed,
            reward
        );
    }
}
