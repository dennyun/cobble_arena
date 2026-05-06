package cobblemon.arena.command;

import cobblemon.arena.access.ArenaAccessService;
import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.battle.ArenaSpectatorManager;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.quest.PlayerQuestProgress;
import cobblemon.arena.quest.Quest;
import cobblemon.arena.quest.QuestManager;
import cobblemon.arena.queue.MatchmakingQueue;
import cobblemon.arena.stats.PlayerStats;
import cobblemon.arena.stats.StatsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Player-facing Arena commands. All subcommands require permission level 0.
 *
 * <pre>
 *   /arena                     — open the Arena GUI
 *   /arena open                — open the Arena GUI (alias)
 *   /arena status              — show server status summary
 *   /arena queue [format]      — join the matchmaking queue
 *   /arena leave               — leave the matchmaking queue
 *   /arena leavequeue          — leave the matchmaking queue (alias)
 *   /arena spectate            — spectate a random active battle
 *   /arena stats [player]      — view your own or another player's stats
 *   /arena leaderboard [ladder]— view the top-10 ranked leaderboard
 *   /arena formats             — list all available battle formats
 *   /arena rules [format]      — display the rules for a format
 *   /arena season              — display current season information
 *   /arena quests              — list your active daily and weekly quests
 *   /arena claim <questId>     — claim the reward for a completed quest
 * </pre>
 */
public final class ArenaCommands {

    private ArenaCommands() {}

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher
    ) {
        dispatcher.register(
            CommandManager.literal("arena")
                .requires(source -> source.hasPermissionLevel(0))
                // /arena — open GUI directly
                .executes(ctx -> openGui(ctx.getSource()))
                // /arena open
                .then(
                    CommandManager.literal("open").executes(ctx ->
                        openGui(ctx.getSource())
                    )
                )
                // /arena status
                .then(
                    CommandManager.literal("status").executes(ctx ->
                        serverStatus(ctx.getSource())
                    )
                )
                // /arena ranqueada [format]
                .then(
                    CommandManager.literal("ranqueada")
                        .then(
                            CommandManager.argument(
                                "format",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    builder.suggest("solo");
                                    builder.suggest("duplas");
                                    builder.suggest("triplas");
                                    builder.suggest("monotype");
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    queueForFormat(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "format"),
                                        true
                                    )
                                )
                        )
                )
                // /arena casual [format]
                .then(
                    CommandManager.literal("casual")
                        .then(
                            CommandManager.argument(
                                "format",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    builder.suggest("solo");
                                    builder.suggest("duplas");
                                    builder.suggest("triplas");
                                    builder.suggest("monotype");
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    queueForFormat(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "format"),
                                        false
                                    )
                                )
                        )
                )
                // /arena leave
                .then(
                    CommandManager.literal("leave").executes(ctx ->
                        leaveQueue(ctx.getSource())
                    )
                )
                // /arena sair
                .then(
                    CommandManager.literal("sair").executes(ctx ->
                        leaveArena(ctx.getSource())
                    )
                )
                // /arena spectate
                .then(
                    CommandManager.literal("spectate").executes(ctx ->
                        spectate(ctx.getSource())
                    )
                )
                // /arena stats [player]
                .then(
                    CommandManager.literal("stats")
                        .executes(ctx -> statsSelf(ctx.getSource()))
                        .then(
                            CommandManager.argument(
                                "player",
                                EntityArgumentType.player()
                            ).executes(ctx ->
                                statsOther(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "player")
                                )
                            )
                        )
                )
                // /arena rules
                .then(
                    CommandManager.literal("rules")
                        .executes(ctx -> listRules(ctx.getSource(), null))
                )
                // /arena season
                .then(
                    CommandManager.literal("season").executes(ctx ->
                        seasonInfo(ctx.getSource())
                    )
                )
                // /arena quests
                .then(
                    CommandManager.literal("quests").executes(ctx ->
                        listQuests(ctx.getSource())
                    )
                )
                // /arena claim <questId>
                .then(
                    CommandManager.literal("claim").then(
                        CommandManager.argument(
                            "questId",
                            StringArgumentType.word()
                        )
                            .suggests((ctx, builder) -> {
                                ServerPlayerEntity player = asPlayer(
                                    ctx.getSource()
                                );
                                if (player != null) {
                                    QuestManager qm =
                                        QuestManager.getInstance();
                                    qm
                                        .getActiveDailyQuests(player)
                                        .forEach(q ->
                                            builder.suggest(q.getId())
                                        );
                                    qm
                                        .getActiveWeeklyQuests(player)
                                        .forEach(q ->
                                            builder.suggest(q.getId())
                                        );
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx ->
                                claimQuest(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "questId")
                                )
                            )
                    )
                )
                // /arena reload
                .then(
                    CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> reloadServer(ctx.getSource()))
                )
        );
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** Opens the Arena main GUI for the executing player. */
    private static int openGui(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        ArenaAccessService.openMainGui(player);
        return 1;
    }

    /** Reloads the server configuration in real-time. */
    private static int reloadServer(ServerCommandSource source) {
        if (source.getServer() != null) {
            ArenaServerConfig.getInstance().initialize(source.getServer());
            // Re-read quests from quests.json
            QuestManager.getInstance().initialize(source.getServer());
            // Sync all quests to connected players so the UI is updated immediately
            QuestManager.getInstance().syncAllPlayers(source.getServer());
            source.getServer().getPlayerManager().broadcast(
                Text.literal("§a§l[Cobblemon Arena] §r§aConfigurações recarregadas com sucesso!"),
                false
            );
        }
        return 1;
    }

    /** Displays a concise server-side status summary. */
    private static int serverStatus(ServerCommandSource source) {
        int available = ArenaManager.getInstance().getAvailableArenaCount();
        int total = ArenaManager.getInstance().getTotalArenaCount();
        int battles = ArenaBattleManager.getInstance().getActiveBattleCount();
        int queued = MatchmakingQueue.getInstance().getTotalPlayersInQueue();
        String season = ArenaServerConfig.getInstance().getCurrentSeasonName();

        source.sendFeedback(
            () -> Text.literal("§6━━━ §lArena — Status §6━━━"),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7Temporada: §f" + season),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Arenas disponíveis: §a" +
                        available +
                        " §7/ §f" +
                        total +
                        " total"
                ),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7Batalhas ativas: §e" + battles),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Na fila: §b" +
                        queued +
                        " jogador" +
                        (queued == 1 ? "" : "es")
                ),
            false
        );
        return 1;
    }

    /** Joins the queue for a mapped format alias. */
    private static int queueForFormat(
        ServerCommandSource source,
        String formatAlias,
        boolean isRanked
    ) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(Text.literal("§cApenas jogadores podem usar este comando."));
            return 0;
        }

        String targetFormat = formatAlias.toLowerCase(java.util.Locale.ROOT);
        String mapped = switch (targetFormat) {
            case "solo" -> "singles";
            case "duplas" -> "doubles";
            case "triplas" -> "triples";
            case "monotype" -> "monotype";
            default -> targetFormat;
        };

        ArenaLadder ladder = null;
        List<ArenaLadder> laddersToSearch = isRanked ? ArenaLadder.getActiveRankedLadders() : ArenaLadder.getQuickPresets();
        
        for (ArenaLadder l : laddersToSearch) {
            if (l.getBattleTypeId().equals(mapped)) {
                ladder = l;
                break; // prefer the first matching one, typically lv50 or whatever is active
            }
        }

        if (ladder == null) {
            source.sendError(
                Text.literal("§cFormato indisponível no momento: §f" + formatAlias)
            );
            return 0;
        }
        MatchmakingQueue.getInstance().joinQueue(player, ladder, false);
        return 1;
    }

    /** Removes the player from the matchmaking queue. */
    private static int leaveQueue(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        MatchmakingQueue.getInstance().leaveQueue(player, true);
        return 1;
    }

    /** Teleports the player to spectate a random active arena battle. */
    private static int spectate(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        ArenaSpectatorManager.getInstance().spectateRandomBattle(player);
        return 1;
    }

    /** Displays the executing player's ranked and casual statistics. */
    private static int statsSelf(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        return sendStats(source, player, "suas");
    }

    /** Displays another player's statistics. */
    private static int statsOther(
        ServerCommandSource source,
        ServerPlayerEntity target
    ) {
        return sendStats(
            source,
            target,
            "de §f" + target.getName().getString() + "§7"
        );
    }

    /**
     * Shared stats rendering helper.
     *
     * @param displaySuffix portuguese possessive suffix for the header (e.g. "suas" or
     *                       "de §fNome§7")
     */
    private static int sendStats(
        ServerCommandSource source,
        ServerPlayerEntity target,
        String displaySuffix
    ) {
        PlayerStats stats = StatsManager.getInstance().getOrCreateStats(target);
        String ladderId = ArenaServerConfig.getInstance()
            .getRankedLadder()
            .getId();
        int rank = StatsManager.getInstance().getPlayerRank(
            target.getUuid(),
            ladderId
        );
        int totalRanked = StatsManager.getInstance().getTotalRankedPlayers(
            ladderId
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§6━━━ §lEstatísticas §7" + displaySuffix + " §6━━━"
                ),
            false
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Rank: §f" +
                        stats.getRankTitle(ladderId) +
                        (rank > 0
                            ? " §7(§8#" +
                              rank +
                              " §7de §8" +
                              totalRanked +
                              "§7)"
                            : " §8(sem partidas ranqueadas)")
                ),
            false
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Pontos Rank (RP): §e" +
                        stats.getRankedRating(ladderId) +
                        "   §7Ranqueado: §a" +
                        stats.getRankedWins(ladderId) +
                        "§7V §c" +
                        stats.getRankedLosses(ladderId) +
                        "§7D"
                ),
            false
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Casual: §a" +
                        stats.getQuickWins() +
                        "§7V §c" +
                        stats.getQuickLosses() +
                        "§7D" +
                        "   §7Total de batalhas: §f" +
                        stats.getTotalBattles()
                ),
            false
        );

        return 1;
    }



    /**
     * Displays format rules. Prints a summary of all
     * active ranked ladder rules.
     */
    private static int listRules(ServerCommandSource source, String ignored) {
        source.sendFeedback(
            () -> Text.literal("§6━━━ §lResumo das Regras §6━━━"),
            false
        );
        for (ArenaLadder ladder : ArenaLadder.getActiveRankedLadders()) {
            source.sendFeedback(
                () ->
                    Text.literal(
                        "§e" +
                            ladder.getDisplayName() +
                            "§8: §7" +
                            ladder.getRulesSummary()
                    ),
                false
            );
        }
        return 1;
    }



    /** Displays information about the current ranked season. */
    private static int seasonInfo(ServerCommandSource source) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        int rankedPlayers = StatsManager.getInstance().getTotalRankedPlayers(
            config.getRankedLadder().getId()
        );

        source.sendFeedback(
            () -> Text.literal("§6━━━ §lTemporada Atual §6━━━"),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7Nome: §f" + config.getCurrentSeasonName()),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Número: §f" +
                        config.getCurrentSeasonNumber() +
                        "   §7ID: §8" +
                        config.getCurrentSeasonId()
                ),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7Jogadores ranqueados: §f" + rankedPlayers),
            false
        );
        return 1;
    }

    /** Lists the player's active daily and weekly quests with progress indicators. */
    private static int listQuests(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }

        QuestManager qm = QuestManager.getInstance();
        PlayerQuestProgress progress = qm.getPlayerProgress(player);

        source.sendFeedback(
            () -> Text.literal("§6━━━ §lMissões Ativas §6━━━"),
            false
        );

        // ── Daily quests ──────────────────────────────────────────────────────
        List<Quest> daily = qm.getActiveDailyQuests(player);
        source.sendFeedback(
            () -> Text.literal("§e§lDiárias §r§8(renovam em 24 h):"),
            false
        );

        if (daily.isEmpty()) {
            source.sendFeedback(
                () -> Text.literal("  §8Sem missões diárias ativas."),
                false
            );
        } else {
            for (Quest q : daily) {
                int current = progress.getProgress(q.getId());
                boolean done = progress.isCompleted(
                    q.getId(),
                    q.getTargetAmount()
                );
                boolean claimed = progress.isClaimed(q.getId());

                String color = claimed ? "§8" : (done ? "§a" : "§f");
                String badge = claimed
                    ? " §8[Resgatado]"
                    : (done
                          ? " §a[Completo! /arena claim " + q.getId() + "]"
                          : "");

                source.sendFeedback(
                    () ->
                        Text.literal(
                            "  " +
                                color +
                                q.getTitle() +
                                " §8(" +
                                Math.min(current, q.getTargetAmount()) +
                                "/" +
                                q.getTargetAmount() +
                                ")" +
                                " §7— " +
                                q.getDescription() +
                                badge
                        ),
                    false
                );
            }
        }

        // ── Weekly quests ─────────────────────────────────────────────────────
        List<Quest> weekly = qm.getActiveWeeklyQuests(player);
        source.sendFeedback(
            () -> Text.literal("§b§lSemanais §r§8(renovam em 7 dias):"),
            false
        );

        if (weekly.isEmpty()) {
            source.sendFeedback(
                () -> Text.literal("  §8Sem missões semanais ativas."),
                false
            );
        } else {
            for (Quest q : weekly) {
                int current = progress.getProgress(q.getId());
                boolean done = progress.isCompleted(
                    q.getId(),
                    q.getTargetAmount()
                );
                boolean claimed = progress.isClaimed(q.getId());

                String color = claimed ? "§8" : (done ? "§a" : "§f");
                String badge = claimed
                    ? " §8[Resgatado]"
                    : (done
                          ? " §a[Completo! /arena claim " + q.getId() + "]"
                          : "");

                source.sendFeedback(
                    () ->
                        Text.literal(
                            "  " +
                                color +
                                q.getTitle() +
                                " §8(" +
                                Math.min(current, q.getTargetAmount()) +
                                "/" +
                                q.getTargetAmount() +
                                ")" +
                                " §7— " +
                                q.getDescription() +
                                badge
                        ),
                    false
                );
            }
        }

        source.sendFeedback(
            () ->
                Text.literal(
                    "§8Use §7/arena claim <id> §8para resgatar uma missão completa."
                ),
            false
        );
        return 1;
    }

    /** Claims the reward for a completed, unclaimed quest. */
    private static int claimQuest(ServerCommandSource source, String questId) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }

        boolean success = QuestManager.getInstance().claimQuestReward(
            player,
            questId
        );
        if (!success) {
            source.sendError(
                Text.literal(
                    "§cNão foi possível resgatar §f" +
                        questId +
                        "§c. " +
                        "Verifique se a missão está completa e ainda não foi resgatada."
                )
            );
            return 0;
        }
        return 1;
    }

    private static int leaveArena(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(Text.literal("§cApenas jogadores podem usar este comando."));
            return 0;
        }

        if (ArenaSpectatorManager.getInstance().isSpectatingArena(player)) {
            ArenaSpectatorManager.getInstance().leaveSpectate(player);
            return 1;
        }

        cobblemon.arena.battle.ArenaSession session = ArenaBattleManager.getInstance().getSession(player);
        if (session != null) {
            player.sendMessage(Text.literal("§cVoce usou o comando para sair e desistiu da partida!"), false);
            ServerPlayerEntity opponent = session.getOpponent(player);
            if (opponent != null) {
                opponent.sendMessage(Text.literal("§aO oponente fugiu da arena. Voce venceu!"), false);
            }
            if (session.getBattleId() != null) {
                com.cobblemon.mod.common.api.battles.model.PokemonBattle battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattle(session.getBattleId());
                if (battle != null && !battle.getEnded()) {
                    for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor : battle.getActors()) {
                        if (actor instanceof com.cobblemon.mod.common.battles.actor.PlayerBattleActor pa && player.getUuid().equals(pa.getUuid())) {
                            try {
                                pa.setActionResponses(java.util.List.of(new com.cobblemon.mod.common.battles.ForfeitActionResponse()));
                            } catch (Exception e) {}
                            return 1;
                        }
                    }
                }
            }
            ArenaBattleManager.getInstance().endArenaForPlayer(player);
            return 1;
        }

        source.sendError(Text.literal("§cVoce nao esta em uma arena no momento."));
        return 0;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns the {@link ServerPlayerEntity} for the given source, or
     * {@code null} if the source is not a player (e.g. the server console).
     */
    private static ServerPlayerEntity asPlayer(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayerEntity player ? player : null;
    }
}
