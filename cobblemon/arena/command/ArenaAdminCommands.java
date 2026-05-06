package cobblemon.arena.command;

import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.battle.ArenaBattleManager;
import cobblemon.arena.battle.ArenaSession;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.queue.MatchmakingQueue;
import cobblemon.arena.stats.PlayerStats;
import cobblemon.arena.stats.StatsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Administrator Arena commands. All subcommands require permission level 2.
 *
 * <pre>
 *   /arenaadmin status                           — full server status
 *   /arenaadmin reload                           — reload configuration from disk
 *   /arenaadmin forcestart <p1> <p2> [ladder]   — force a match between two players
 *   /arenaadmin end <player>                     — forcefully end a player's arena battle
 *   /arenaadmin clearqueue                       — remove all players from the queue
 *   /arenaadmin season                           — show current season details
 *   /arenaadmin season status                    — show current season details (alias)
 *   /arenaadmin season rollover [name]           — end the current season and start a new one
 *   /arenaadmin stats <player> [ladder]          — view a player's detailed statistics
 *   /arenaadmin resetladder                      — reset ranked stats for ALL players
 *   /arenaadmin resetladder <player>             — reset ranked stats for one player
 *   /arenaadmin leaderboard [ladder] [limit]     — view the ranked leaderboard
 *   /arenaadmin arenas                           — show arena pool status
 *   /arenaadmin battles                          — list all active arena battles
 * </pre>
 */
public final class ArenaAdminCommands {

    private ArenaAdminCommands() {}

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher
    ) {
        dispatcher.register(
            CommandManager.literal("arenaadmin")
                .requires(source -> source.hasPermissionLevel(2))
                // /arenaadmin status
                .then(
                    CommandManager.literal("status").executes(ctx ->
                        fullStatus(ctx.getSource())
                    )
                )
                // /arenaadmin reload
                .then(
                    CommandManager.literal("reload").executes(ctx -> {
                        ArenaServerConfig.getInstance().initialize(
                            ctx.getSource().getServer()
                        );
                        ctx
                            .getSource()
                            .sendFeedback(
                                () ->
                                    Text.literal(
                                        "§aConfiguração da Arena recarregada com sucesso."
                                    ),
                                true
                            );
                        return 1;
                    })
                )
                // /arenaadmin forcestart <player1> <player2> [ladder]
                .then(
                    CommandManager.literal("forcestart").then(
                        CommandManager.argument(
                            "player1",
                            EntityArgumentType.player()
                        ).then(
                            CommandManager.argument(
                                "player2",
                                EntityArgumentType.player()
                            )
                                .executes(ctx ->
                                    forceStart(
                                        ctx.getSource(),
                                        EntityArgumentType.getPlayer(
                                            ctx,
                                            "player1"
                                        ),
                                        EntityArgumentType.getPlayer(
                                            ctx,
                                            "player2"
                                        ),
                                        null
                                    )
                                )
                                .then(
                                    CommandManager.argument(
                                        "ladder",
                                        StringArgumentType.string()
                                    )
                                        .suggests((ctx, builder) -> {
                                            for (ArenaLadder l : ArenaLadder.values()) {
                                                builder.suggest(l.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx ->
                                            forceStart(
                                                ctx.getSource(),
                                                EntityArgumentType.getPlayer(
                                                    ctx,
                                                    "player1"
                                                ),
                                                EntityArgumentType.getPlayer(
                                                    ctx,
                                                    "player2"
                                                ),
                                                StringArgumentType.getString(
                                                    ctx,
                                                    "ladder"
                                                )
                                            )
                                        )
                                )
                        )
                    )
                )
                // /arenaadmin end <player>
                .then(
                    CommandManager.literal("end").then(
                        CommandManager.argument(
                            "player",
                            EntityArgumentType.player()
                        ).executes(ctx ->
                            endArena(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "player")
                            )
                        )
                    )
                )
                // /arenaadmin clearqueue
                .then(
                    CommandManager.literal("clearqueue").executes(ctx -> {
                        MatchmakingQueue.getInstance().clearRankedQueues(
                            ctx.getSource().getServer(),
                            "§eA fila foi limpa por um administrador."
                        );
                        ctx
                            .getSource()
                            .sendFeedback(
                                () ->
                                    Text.literal(
                                        "§aFila de matchmaking limpa."
                                    ),
                                true
                            );
                        return 1;
                    })
                )
                // /arenaadmin season [status|rollover]
                .then(
                    CommandManager.literal("season")
                        .executes(ctx -> seasonStatus(ctx.getSource()))
                        .then(
                            CommandManager.literal("status").executes(ctx ->
                                seasonStatus(ctx.getSource())
                            )
                        )
                        .then(
                            CommandManager.literal("rollover")
                                .executes(ctx ->
                                    rolloverSeason(ctx.getSource(), null)
                                )
                                .then(
                                    CommandManager.argument(
                                        "name",
                                        StringArgumentType.greedyString()
                                    ).executes(ctx ->
                                        rolloverSeason(
                                            ctx.getSource(),
                                            StringArgumentType.getString(
                                                ctx,
                                                "name"
                                            )
                                        )
                                    )
                                )
                        )
                )
                // /arenaadmin stats <player> [ladder]
                .then(
                    CommandManager.literal("stats").then(
                        CommandManager.argument(
                            "player",
                            EntityArgumentType.player()
                        )
                            .executes(ctx ->
                                adminStats(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "player"),
                                    null
                                )
                            )
                            .then(
                                CommandManager.argument(
                                    "ladder",
                                    StringArgumentType.string()
                                )
                                    .suggests((ctx, builder) -> {
                                        for (ArenaLadder l : ArenaLadder.getActiveRankedLadders()) {
                                            builder.suggest(l.getId());
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx ->
                                        adminStats(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(
                                                ctx,
                                                "player"
                                            ),
                                            StringArgumentType.getString(
                                                ctx,
                                                "ladder"
                                            )
                                        )
                                    )
                            )
                    )
                )
                // /arenaadmin resetladder [player]
                .then(
                    CommandManager.literal("resetladder")
                        .executes(ctx -> resetLadderAll(ctx.getSource()))
                        .then(
                            CommandManager.argument(
                                "player",
                                EntityArgumentType.player()
                            ).executes(ctx ->
                                resetLadderPlayer(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "player")
                                )
                            )
                        )
                )
                // /arenaadmin leaderboard [ladder] [limit]
                .then(
                    CommandManager.literal("leaderboard")
                        .executes(ctx ->
                            adminLeaderboard(ctx.getSource(), null, 10)
                        )
                        .then(
                            CommandManager.argument(
                                "ladder",
                                StringArgumentType.string()
                            )
                                .suggests((ctx, builder) -> {
                                    for (ArenaLadder l : ArenaLadder.getActiveRankedLadders()) {
                                        builder.suggest(l.getId());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                    adminLeaderboard(
                                        ctx.getSource(),
                                        StringArgumentType.getString(
                                            ctx,
                                            "ladder"
                                        ),
                                        10
                                    )
                                )
                                .then(
                                    CommandManager.argument(
                                        "limit",
                                        IntegerArgumentType.integer(1, 100)
                                    ).executes(ctx ->
                                        adminLeaderboard(
                                            ctx.getSource(),
                                            StringArgumentType.getString(
                                                ctx,
                                                "ladder"
                                            ),
                                            IntegerArgumentType.getInteger(
                                                ctx,
                                                "limit"
                                            )
                                        )
                                    )
                                )
                        )
                )
                // /arenaadmin arenas
                .then(
                    CommandManager.literal("arenas").executes(ctx ->
                        listArenas(ctx.getSource())
                    )
                )
                // /arenaadmin battles
                .then(
                    CommandManager.literal("battles").executes(ctx ->
                        listBattles(ctx.getSource())
                    )
                )
        );
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** Prints a comprehensive server-side status overview. */
    private static int fullStatus(ServerCommandSource source) {
        int available = ArenaManager.getInstance().getAvailableArenaCount();
        int total = ArenaManager.getInstance().getTotalArenaCount();
        int inUse = total - available;
        int battles = ArenaBattleManager.getInstance().getActiveBattleCount();
        int queued = MatchmakingQueue.getInstance().getTotalPlayersInQueue();
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        int activeLadders = config.getActiveRankedLadders().size();

        source.sendFeedback(
            () -> Text.literal("§6━━━ §l[ArenaAdmin] Status Completo §6━━━"),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Temporada: §f" +
                        config.getCurrentSeasonName() +
                        " §8(ID: " +
                        config.getCurrentSeasonId() +
                        " · #" +
                        config.getCurrentSeasonNumber() +
                        ")"
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Arenas: §a" +
                        available +
                        " livres  §e" +
                        inUse +
                        " em uso  §7/ §f" +
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
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Timer de ação: §f" + config.getActionTimerSeconds() + "s"
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal("§7Ladders ranqueadas ativas: §f" + activeLadders),
            false
        );
        return 1;
    }

    /**
     * Forces a match between two players.
     *
     * @param ladderId optional ladder ID; falls back to the default quick ladder when
     *                 {@code null} or blank
     */
    private static int forceStart(
        ServerCommandSource source,
        ServerPlayerEntity p1,
        ServerPlayerEntity p2,
        String ladderId
    ) {
        if (p1.getUuid().equals(p2.getUuid())) {
            source.sendError(
                Text.literal("§cOs dois jogadores precisam ser diferentes.")
            );
            return 0;
        }

        ArenaLadder ladder = (ladderId == null || ladderId.isBlank())
            ? ArenaLadder.defaultQuick()
            : ArenaLadder.byId(ladderId);

        if (ladder == null) {
            source.sendError(
                Text.literal(
                    "§cLadder inválida: §f" +
                        ladderId +
                        "§c. " +
                        "Use §f/arenaadmin leaderboard §cpara ver as opções."
                )
            );
            return 0;
        }

        ArenaSession session = ArenaBattleManager.getInstance().startMatch(
            p1,
            p2,
            ladder,
            false
        );

        if (session == null) {
            source.sendError(
                Text.literal(
                    "§cNão foi possível iniciar a partida. " +
                        "Verifique se ambos os jogadores estão disponíveis e se há arenas livres."
                )
            );
            return 0;
        }

        String sessionShort = session.getSessionId().toString().substring(0, 8);
        source.sendFeedback(
            () ->
                Text.literal(
                    "§aPartida iniciada: §f" +
                        p1.getName().getString() +
                        " §7vs §f" +
                        p2.getName().getString() +
                        " §7[§e" +
                        ladder.getDisplayName() +
                        "§7]" +
                        " §8(sessão: " +
                        sessionShort +
                        "...)"
                ),
            true
        );
        return 1;
    }

    /** Forcefully ends the arena battle session of a given player. */
    private static int endArena(
        ServerCommandSource source,
        ServerPlayerEntity player
    ) {
        ArenaSession session = ArenaBattleManager.getInstance().getSession(
            player
        );
        if (session == null) {
            source.sendError(
                Text.literal(
                    "§c" +
                        player.getName().getString() +
                        " não está em uma batalha de arena no momento."
                )
            );
            return 0;
        }

        ArenaBattleManager.getInstance().endArena(session.getSessionId());
        source.sendFeedback(
            () ->
                Text.literal(
                    "§aBatalha encerrada para §f" +
                        player.getName().getString() +
                        "§a."
                ),
            true
        );
        return 1;
    }

    /** Displays the current season's metadata. */
    private static int seasonStatus(ServerCommandSource source) {
        ArenaServerConfig config = ArenaServerConfig.getInstance();
        int completed = config.getCompletedRankedSeasons().size();
        int ranked = StatsManager.getInstance().getTotalRankedPlayers(
            config.getRankedLadder().getId()
        );

        source.sendFeedback(
            () -> Text.literal("§6━━━ §l[ArenaAdmin] Temporada §6━━━"),
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
            () -> Text.literal("§7Jogadores ranqueados: §f" + ranked),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Temporadas anteriores arquivadas: §f" + completed
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§eUse §f/arenaadmin season rollover [nome] " +
                        "§epara encerrar a temporada atual."
                ),
            false
        );
        return 1;
    }

    /**
     * Ends the current ranked season, archives it, and starts a new one.
     * Broadcasts the transition to all online players.
     *
     * @param newName optional name for the incoming season; auto-generated when
     *                {@code null} or blank
     */
    private static int rolloverSeason(
        ServerCommandSource source,
        String newName
    ) {
        ArenaServerConfig.SeasonRolloverResult result =
            StatsManager.getInstance().rolloverRankedSeason(
                newName,
                source.getServer()
            );

        String prevName = result.getPreviousSeason().getSeasonName();
        String nextName = result.getCurrentSeason().getSeasonName();

        source.sendFeedback(
            () ->
                Text.literal(
                    "§a§lTemporada encerrada! " +
                        "§r§7Anterior: §f" +
                        prevName +
                        " §7→ Nova: §a" +
                        nextName
                ),
            true
        );

        if (source.getServer() != null) {
            source
                .getServer()
                .getPlayerManager()
                .broadcast(
                    Text.literal(
                        "§6§l★ §r§eA temporada §f" +
                            prevName +
                            " §eencerrou! " +
                            "A nova temporada §a" +
                            nextName +
                            " §ecomeçou!"
                    ),
                    false
                );
        }
        return 1;
    }

    /**
     * Displays detailed statistics for a player, scoped to the given ladder.
     * Falls back to the server's default ranked ladder when {@code ladderId} is
     * {@code null} or blank.
     */
    private static int adminStats(
        ServerCommandSource source,
        ServerPlayerEntity target,
        String ladderId
    ) {
        PlayerStats stats = StatsManager.getInstance().getOrCreateStats(target);
        String resolvedId = (ladderId == null || ladderId.isBlank())
            ? ArenaServerConfig.getInstance().getRankedLadder().getId()
            : ladderId;

        int rank = StatsManager.getInstance().getPlayerRank(
            target.getUuid(),
            resolvedId
        );
        int totalRanked = StatsManager.getInstance().getTotalRankedPlayers(
            resolvedId
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§6━━━ §l[ArenaAdmin] Stats — §f" +
                        target.getName().getString() +
                        " §6━━━"
                ),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7UUID: §8" + target.getUuidAsString()),
            false
        );
        source.sendFeedback(
            () -> Text.literal("§7Ladder: §f" + resolvedId),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Rank: §f" +
                        stats.getRankTitle(resolvedId) +
                        "   §7Elo: §e" +
                        stats.getRankedRating(resolvedId)
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Ranqueado: §a" +
                        stats.getRankedWins(resolvedId) +
                        "§7V §c" +
                        stats.getRankedLosses(resolvedId) +
                        "§7D"
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Casual (Quick): §a" +
                        stats.getQuickWins() +
                        "§7V §c" +
                        stats.getQuickLosses() +
                        "§7D"
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Total de batalhas: §f" + stats.getTotalBattles()
                ),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Posição no ranking: §f" +
                        (rank > 0
                            ? "#" + rank + " de " + totalRanked
                            : "Iniciante")
                ),
            false
        );
        return 1;
    }

    /** Resets ranked ladder statistics for every player on the server. */
    private static int resetLadderAll(ServerCommandSource source) {
        StatsManager.getInstance().resetRankedLadder();
        source.sendFeedback(
            () ->
                Text.literal(
                    "§a§lLadder ranqueada resetada para TODOS os jogadores."
                ),
            true
        );
        return 1;
    }

    /** Resets ranked ladder statistics for a single player. */
    private static int resetLadderPlayer(
        ServerCommandSource source,
        ServerPlayerEntity target
    ) {
        PlayerStats stats = StatsManager.getInstance().getStats(
            target.getUuid()
        );
        if (stats == null) {
            source.sendError(
                Text.literal(
                    "§cNenhuma estatística encontrada para §f" +
                        target.getName().getString() +
                        "§c."
                )
            );
            return 0;
        }

        stats.resetRankedLadder();
        StatsManager.getInstance().saveStats();

        source.sendFeedback(
            () ->
                Text.literal(
                    "§aEstatísticas ranqueadas de §f" +
                        target.getName().getString() +
                        "§a foram resetadas."
                ),
            true
        );
        target.sendMessage(
            Text.literal(
                "§eUm administrador resetou suas estatísticas ranqueadas de arena."
            ),
            false
        );
        return 1;
    }

    /**
     * Displays the ranked leaderboard for the specified (or default) ladder.
     *
     * @param ladderId optional ladder ID; falls back to the default ranked ladder
     * @param limit    number of entries to display (1–100)
     */
    private static int adminLeaderboard(
        ServerCommandSource source,
        String ladderId,
        int limit
    ) {
        String resolvedId = (ladderId == null || ladderId.isBlank())
            ? ArenaServerConfig.getInstance().getRankedLadder().getId()
            : ladderId;

        ArenaLadder ladder = ArenaLadder.byId(resolvedId);
        String ladderName =
            ladder != null ? ladder.getDisplayName() : resolvedId;

        List<PlayerStats> top = StatsManager.getInstance().getTopPlayers(
            resolvedId,
            limit
        );

        source.sendFeedback(
            () ->
                Text.literal(
                    "§6━━━ §l[ArenaAdmin] Leaderboard — §f" +
                        ladderName +
                        " §8(Top " +
                        limit +
                        ") §6━━━"
                ),
            false
        );

        if (top.isEmpty()) {
            source.sendFeedback(
                () ->
                    Text.literal("§8Nenhuma partida registrada nessa ladder."),
                false
            );
            return 1;
        }

        for (int i = 0; i < top.size(); i++) {
            PlayerStats entry = top.get(i);
            int pos = i + 1;
            String uuidStr = entry.getPlayerUUID().toString();
            source.sendFeedback(
                () ->
                    Text.literal(
                        "§8#" +
                            pos +
                            " §f" +
                            entry.getPlayerName() +
                            " §7| §e" +
                            entry.getRankedRating(resolvedId) +
                            " Elo" +
                            " §7| §a" +
                            entry.getRankedWins(resolvedId) +
                            "§7V §c" +
                            entry.getRankedLosses(resolvedId) +
                            "§7D" +
                            " §8[" +
                            uuidStr +
                            "]"
                    ),
                false
            );
        }
        return 1;
    }

    /** Displays the current arena pool utilization. */
    private static int listArenas(ServerCommandSource source) {
        int available = ArenaManager.getInstance().getAvailableArenaCount();
        int total = ArenaManager.getInstance().getTotalArenaCount();
        int inUse = total - available;

        source.sendFeedback(
            () -> Text.literal("§6━━━ §l[ArenaAdmin] Arenas §6━━━"),
            false
        );
        source.sendFeedback(
            () ->
                Text.literal(
                    "§7Total: §f" +
                        total +
                        "   §7Disponíveis: §a" +
                        available +
                        "   §7Em uso: §e" +
                        inUse
                ),
            false
        );
        return 1;
    }

    /** Lists every active arena battle with player names, arena slot, and ladder. */
    private static int listBattles(ServerCommandSource source) {
        List<ArenaSession> sessions =
            ArenaBattleManager.getInstance().getSpectatableSessions();

        source.sendFeedback(
            () -> Text.literal("§6━━━ §l[ArenaAdmin] Batalhas Ativas §6━━━"),
            false
        );

        if (sessions.isEmpty()) {
            source.sendFeedback(
                () ->
                    Text.literal(
                        "§8Nenhuma batalha de arena acontecendo no momento."
                    ),
                false
            );
            return 1;
        }

        source.sendFeedback(
            () ->
                Text.literal(
                    "§7" + sessions.size() + " batalha(s) em andamento:"
                ),
            false
        );

        for (ArenaSession session : sessions) {
            String p1Name = session.getPlayer1().getName().getString();
            String p2Name = session.getPlayer2().getName().getString();
            int arenaSlot = session.getArena().getArenaId() + 1;
            String ladderLabel =
                session.getLadder() != null
                    ? " §7| §6" + session.getLadder().getDisplayName()
                    : " §7| §8Sem ladder";
            String shortId = session.getSessionId().toString().substring(0, 8);

            source.sendFeedback(
                () ->
                    Text.literal(
                        "§7Arena §e" +
                            arenaSlot +
                            " §8| §f" +
                            p1Name +
                            " §7vs §f" +
                            p2Name +
                            ladderLabel +
                            " §8[" +
                            shortId +
                            "...]"
                    ),
                false
            );
        }
        return 1;
    }
}
