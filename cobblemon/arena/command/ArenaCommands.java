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
                // /arena queue [format]
                .then(
                    CommandManager.literal("queue")
                        .executes(ctx -> queueDefault(ctx.getSource()))
                        .then(
                            CommandManager.argument(
                                "format",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    for (ArenaLadder ladder : ArenaLadder.values()) {
                                        builder.suggest(ladder.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    queueForLadder(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                            ctx,
                                            "format"
                                        )
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
                // /arena leavequeue (alias)
                .then(
                    CommandManager.literal("leavequeue").executes(ctx ->
                        leaveQueue(ctx.getSource())
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
                // /arena leaderboard [ladder]
                .then(
                    CommandManager.literal("leaderboard")
                        .executes(ctx -> leaderboard(ctx.getSource(), null))
                        .then(
                            CommandManager.argument(
                                "ladder",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    for (ArenaLadder ladder : ArenaLadder.getActiveRankedLadders()) {
                                        builder.suggest(ladder.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    leaderboard(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                            ctx,
                                            "ladder"
                                        )
                                    )
                                )
                        )
                )
                // /arena formats
                .then(
                    CommandManager.literal("formats").executes(ctx ->
                        listFormats(ctx.getSource())
                    )
                )
                // /arena rules [format]
                .then(
                    CommandManager.literal("rules")
                        .executes(ctx -> listRules(ctx.getSource(), null))
                        .then(
                            CommandManager.argument(
                                "format",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    for (ArenaLadder ladder : ArenaLadder.values()) {
                                        builder.suggest(ladder.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    listRules(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                            ctx,
                                            "format"
                                        )
                                    )
                                )
                        )
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

    /** Joins the default ranked ladder queue. */
    private static int queueDefault(ServerCommandSource source) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        MatchmakingQueue.getInstance().joinQueue(
            player,
            ArenaLadder.defaultRanked()
        );
        return 1;
    }

    /** Joins the queue for a specific ladder by ID. */
    private static int queueForLadder(
        ServerCommandSource source,
        String ladderId
    ) {
        ServerPlayerEntity player = asPlayer(source);
        if (player == null) {
            source.sendError(
                Text.literal("§cApenas jogadores podem usar este comando.")
            );
            return 0;
        }
        ArenaLadder ladder = ArenaLadder.byId(ladderId);
        if (ladder == null) {
            source.sendError(
                Text.literal(
                    "§cFormato inválido: §f" +
                        ladderId +
                        "§c. " +
                        "Use §f/arena formats §cpara ver as opções disponíveis."
                )
            );
            return 0;
        }
        MatchmakingQueue.getInstance().joinQueue(player, ladder);
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
                    "§7Elo: §e" +
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

    /** Displays the ranked leaderboard for the specified (or default) ladder. */
    private static int leaderboard(
        ServerCommandSource source,
        String ladderId
    ) {
        String resolvedId = (ladderId == null || ladderId.isBlank())
            ? ArenaServerConfig.getInstance().getRankedLadder().getId()
            : ladderId;

        ArenaLadder ladder = ArenaLadder.byId(resolvedId);
        String ladderName =
            ladder != null ? ladder.getDisplayName() : resolvedId;

        List<PlayerStats> top = StatsManager.getInstance().getTopPlayers(
            resolvedId,
            10
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§6━━━ §lLeaderboard — §f" + ladderName + " §6━━━"
                ),
            false
        );

        if (top.isEmpty()) {
            source.sendFeedback(
                () ->
                    Text.literal(
                        "§8Nenhuma partida ranqueada registrada ainda."
                    ),
                false
            );
            return 1;
        }

        for (int i = 0; i < top.size(); i++) {
            PlayerStats entry = top.get(i);
            int pos = i + 1;
            String medal = switch (pos) {
                case 1 -> "§6#1 ";
                case 2 -> "§7#2 ";
                case 3 -> "§c#3 ";
                default -> "§8#" + pos + " ";
            };
            source.sendFeedback(
                () ->
                    Text.literal(
                        medal +
                            "§f" +
                            entry.getPlayerName() +
                            " §7— §e" +
                            entry.getRankedRating(resolvedId) +
                            " Elo" +
                            " §7(§a" +
                            entry.getRankedWins(resolvedId) +
                            "§7V §c" +
                            entry.getRankedLosses(resolvedId) +
                            "§7D)"
                    ),
                false
            );
        }
        return 1;
    }

    /** Lists all available battle formats (ranked and quick). */
    private static int listFormats(ServerCommandSource source) {
        source.sendFeedback(
            () -> Text.literal("§6━━━ §lFormatos Disponíveis §6━━━"),
            false
        );

        List<ArenaLadder> rankedLadders = ArenaLadder.getActiveRankedLadders();
        if (!rankedLadders.isEmpty()) {
            source.sendFeedback(
                () -> Text.literal("§e§lRanqueado§r§8:"),
                false
            );
            for (ArenaLadder l : rankedLadders) {
                source.sendFeedback(
                    () ->
                        Text.literal(
                            "  §f" + l.getId() + " §8— §7" + l.getDisplayName()
                        ),
                    false
                );
            }
        }

        List<ArenaLadder> quickLadders = ArenaLadder.getQuickPresets();
        if (!quickLadders.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§b§lRápido§r§8:"), false);
            for (ArenaLadder l : quickLadders) {
                source.sendFeedback(
                    () ->
                        Text.literal(
                            "  §f" + l.getId() + " §8— §7" + l.getDisplayName()
                        ),
                    false
                );
            }
        }

        source.sendFeedback(
            () ->
                Text.literal(
                    "§8Use §7/arena queue <formato> §8para entrar na fila."
                ),
            false
        );
        return 1;
    }

    /**
     * Displays format rules. If no format is specified, prints a summary of all
     * active ranked ladder rules. Otherwise prints the full rule list for the
     * requested ladder.
     */
    private static int listRules(ServerCommandSource source, String ladderId) {
        if (ladderId == null || ladderId.isBlank()) {
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
            source.sendFeedback(
                () ->
                    Text.literal(
                        "§8Use §7/arena rules <formato> §8para ver as regras completas."
                    ),
                false
            );
            return 1;
        }

        ArenaLadder ladder = ArenaLadder.byId(ladderId);
        if (ladder == null) {
            source.sendError(
                Text.literal(
                    "§cFormato inválido: §f" +
                        ladderId +
                        "§c. " +
                        "Use §f/arena formats §cpara ver as opções."
                )
            );
            return 0;
        }

        source.sendFeedback(
            () ->
                Text.literal(
                    "§6━━━ §lRegras — §f" + ladder.getDisplayName() + " §6━━━"
                ),
            false
        );
        for (String line : ladder.getRuleLines()) {
            source.sendFeedback(() -> Text.literal("§7" + line), false);
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
