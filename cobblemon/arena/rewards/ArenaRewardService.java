package cobblemon.arena.rewards;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.stats.PlayerStats;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Handles all reward distribution for the Arena system.
 *
 * <p>Two entry-points are provided:
 * <ul>
 *   <li>{@link #evaluateMilestoneRewards} — called after every ranked match to check
 *       whether a player has crossed a stat threshold for the first time.</li>
 *   <li>{@link #grantSeasonRolloverRewards} — called once per season rollover to award
 *       top-placement and participation prizes to online players.</li>
 * </ul>
 *
 * <p>All reward commands are executed as the server at permission level 4. The
 * {@code {player}}, {@code {name}}, and {@code {uuid}} placeholders are resolved
 * before dispatch. Every grant is logged for audit purposes.
 */
public final class ArenaRewardService {

    private ArenaRewardService() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Evaluates every configured milestone against the given player's current stats
     * and grants any milestones that have just been reached for the first time.
     *
     * <p>Safe to call even when milestone rewards are disabled in config — the
     * method returns immediately in that case.
     *
     * @param player the player to evaluate; may be {@code null} (no-op)
     * @param stats  the player's current stats; may be {@code null} (no-op)
     */
    public static void evaluateMilestoneRewards(
        ServerPlayerEntity player,
        PlayerStats stats
    ) {
        if (player == null || stats == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ArenaServerConfig config = ArenaServerConfig.getInstance();
        ArenaServerConfig.RewardsConfig rewards = config.getRewards();
        if (rewards == null || !rewards.isMilestoneRewardsEnabled()) return;

        List<ArenaServerConfig.MilestoneRewardConfig> milestones =
            rewards.getMilestoneRewards();
        if (milestones == null || milestones.isEmpty()) return;

        for (ArenaServerConfig.MilestoneRewardConfig milestone : milestones) {
            if (milestone == null) continue;

            String milestoneId = buildMilestoneId(milestone);

            // Skip already-granted milestones — each is awarded exactly once per player.
            if (stats.hasGrantedRewardMilestone(milestoneId)) continue;

            if (!checkMilestoneReached(stats, milestone)) continue;

            // Mark first so that even a command failure doesn't grant twice.
            stats.markRewardMilestoneGranted(milestoneId);

            List<String> commands = milestone.getCommands();
            if (commands == null || commands.isEmpty()) {
                CobblemonArena.LOGGER.info(
                    "[ArenaRewards] Milestone '{}' reached by {} but no commands configured.",
                    milestoneId,
                    player.getName().getString()
                );
                continue;
            }

            executeCommands(
                server,
                player,
                commands,
                "milestone:" + milestoneId
            );

            player.sendMessage(
                Text.literal(
                    "§6§l✦ Recompensa de Marco §r§7— " + milestone.getTitle()
                ),
                false
            );

            CobblemonArena.LOGGER.info(
                "[ArenaRewards] Milestone '{}' granted to {}.",
                milestoneId,
                player.getName().getString()
            );
        }
    }

    /**
     * Distributes season-end rewards to all currently online players after a
     * ranked season rolls over.
     *
     * <p>For each active ladder two reward tiers are evaluated:
     * <ol>
     *   <li><b>Placement rewards</b> — granted to the top-N players on the
     *       leaderboard (by rating), provided they also meet the minimum games
     *       threshold defined per placement tier.</li>
     *   <li><b>Participation rewards</b> — granted to every player who completed
     *       at least the configured minimum number of games on that ladder.</li>
     * </ol>
     *
     * <p>Only players who are currently online receive rewards immediately.
     * Offline players are silently skipped; a separate offline-delivery mechanism
     * should be considered for production servers.
     *
     * @param server          the running server instance
     * @param allPlayerStats  stats for every player that has ever played
     * @param previousSeason  metadata about the season that just ended
     * @param ladders         the ranked ladders to distribute rewards for;
     *                        falls back to {@link ArenaLadder#getActiveRankedLadders()}
     *                        when {@code null} or empty
     */
    public static void grantSeasonRolloverRewards(
        MinecraftServer server,
        Collection<PlayerStats> allPlayerStats,
        ArenaServerConfig.ArchivedSeasonInfo previousSeason,
        List<ArenaLadder> ladders
    ) {
        if (
            server == null || allPlayerStats == null || previousSeason == null
        ) return;

        ArenaServerConfig config = ArenaServerConfig.getInstance();
        ArenaServerConfig.RewardsConfig rewards = config.getRewards();

        if (rewards == null || !rewards.isSeasonRewardsEnabled()) {
            CobblemonArena.LOGGER.info(
                "[ArenaRewards] Season end rewards are disabled — skipping distribution for season '{}'.",
                previousSeason.getSeasonName()
            );
            return;
        }

        ArenaServerConfig.SeasonEndRewardsConfig seasonRewards =
            rewards.getSeasonEndRewards();
        if (seasonRewards == null) {
            CobblemonArena.LOGGER.info(
                "[ArenaRewards] No season end reward config found — skipping distribution for season '{}'.",
                previousSeason.getSeasonName()
            );
            return;
        }

        // Use the provided ladder list, falling back to the currently active ranked ladders.
        List<ArenaLadder> targetLadders = (ladders != null &&
            !ladders.isEmpty())
            ? ladders
            : ArenaLadder.getActiveRankedLadders();

        if (targetLadders == null || targetLadders.isEmpty()) {
            CobblemonArena.LOGGER.info(
                "[ArenaRewards] No active ranked ladders found — skipping season reward distribution."
            );
            return;
        }

        int totalRewarded = 0;

        for (ArenaLadder ladder : targetLadders) {
            if (ladder == null) continue;

            String ladderId = ladder.getId();
            String ladderDisplay = ladder.getDisplayName();

            // Build a leaderboard for this ladder, sorted by rating descending.
            // Only players who actually competed on this ladder are included.
            List<PlayerStats> leaderboard = allPlayerStats
                .stream()
                .filter(s -> s != null && s.getRankedTotalGames(ladderId) > 0)
                .sorted(
                    Comparator.comparingInt((PlayerStats s) ->
                        s.getRankedRating(ladderId)
                    ).reversed()
                )
                .toList();

            if (leaderboard.isEmpty()) {
                CobblemonArena.LOGGER.info(
                    "[ArenaRewards] No players on ladder '{}' — skipping reward tiers for this ladder.",
                    ladderId
                );
                continue;
            }

            CobblemonArena.LOGGER.info(
                "[ArenaRewards] Processing season '{}' rewards for ladder '{}' ({} eligible players).",
                previousSeason.getSeasonName(),
                ladderId,
                leaderboard.size()
            );

            // ------------------------------------------------------------------
            // Tier 1: Placement rewards (top-N by rating)
            // ------------------------------------------------------------------
            List<ArenaServerConfig.PlacementRewardConfig> placements =
                seasonRewards.getPlacementRewards();
            if (placements != null && !placements.isEmpty()) {
                for (ArenaServerConfig.PlacementRewardConfig placement : placements) {
                    if (placement == null) continue;

                    int maxPlacement = placement.getMaxPlacement();
                    int minGamesNeeded = placement.getMinimumGames();
                    List<String> commands = placement.getCommands();

                    if (commands == null || commands.isEmpty()) continue;

                    int rank = 0;
                    for (PlayerStats ps : leaderboard) {
                        rank++;
                        if (rank > maxPlacement) break;

                        // Honour the minimum-games gate for this placement tier.
                        if (
                            minGamesNeeded > 0 &&
                            ps.getRankedTotalGames(ladderId) < minGamesNeeded
                        ) {
                            CobblemonArena.LOGGER.info(
                                "[ArenaRewards] Player {} is rank #{} on '{}' but has too few games ({} < {}) — skipping placement reward.",
                                ps.getPlayerUUID(),
                                rank,
                                ladderId,
                                ps.getRankedTotalGames(ladderId),
                                minGamesNeeded
                            );
                            continue;
                        }

                        ServerPlayerEntity player = server
                            .getPlayerManager()
                            .getPlayer(ps.getPlayerUUID());
                        if (player == null) {
                            CobblemonArena.LOGGER.info(
                                "[ArenaRewards] Rank #{} player {} on '{}' is offline — skipping (no offline delivery).",
                                rank,
                                ps.getPlayerUUID(),
                                ladderId
                            );
                            continue;
                        }

                        executeCommands(
                            server,
                            player,
                            commands,
                            "season-placement:" + ladderId + ":rank" + rank
                        );

                        player.sendMessage(
                            Text.literal(
                                "§6§l★ Recompensa de Temporada §r§7— Top " +
                                    maxPlacement +
                                    " em §f" +
                                    ladderDisplay +
                                    " §7(Temporada: §f" +
                                    previousSeason.getSeasonName() +
                                    "§7)"
                            ),
                            false
                        );

                        CobblemonArena.LOGGER.info(
                            "[ArenaRewards] Season placement reward (top {}) granted to {} [rank #{}, ladder '{}', season '{}'].",
                            maxPlacement,
                            player.getName().getString(),
                            rank,
                            ladderId,
                            previousSeason.getSeasonName()
                        );

                        totalRewarded++;
                    }
                }
            }

            // ------------------------------------------------------------------
            // Tier 2: Participation rewards (any player with >= minGames)
            // ------------------------------------------------------------------
            ArenaServerConfig.ParticipationRewardConfig participation =
                seasonRewards.getParticipationReward();
            if (participation != null) {
                int minGames = participation.getMinimumGames();
                List<String> commands = participation.getCommands();

                if (commands != null && !commands.isEmpty()) {
                    for (PlayerStats ps : leaderboard) {
                        int playerGames = ps.getRankedTotalGames(ladderId);
                        if (playerGames < minGames) continue;

                        ServerPlayerEntity player = server
                            .getPlayerManager()
                            .getPlayer(ps.getPlayerUUID());
                        if (player == null) {
                            CobblemonArena.LOGGER.info(
                                "[ArenaRewards] Participation-eligible player {} on '{}' is offline — skipping.",
                                ps.getPlayerUUID(),
                                ladderId
                            );
                            continue;
                        }

                        executeCommands(
                            server,
                            player,
                            commands,
                            "season-participation:" + ladderId
                        );

                        player.sendMessage(
                            Text.literal(
                                "§a§l✦ Recompensa de Participação §r§7— Temporada §f" +
                                    previousSeason.getSeasonName() +
                                    " §7encerrada!"
                            ),
                            false
                        );

                        CobblemonArena.LOGGER.info(
                            "[ArenaRewards] Season participation reward granted to {} [{} games on ladder '{}', season '{}'].",
                            player.getName().getString(),
                            playerGames,
                            ladderId,
                            previousSeason.getSeasonName()
                        );

                        totalRewarded++;
                    }
                }
            }
        }

        CobblemonArena.LOGGER.info(
            "[ArenaRewards] Season '{}' end reward distribution complete — {} reward(s) granted to online players.",
            previousSeason.getSeasonName(),
            totalRewarded
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Executes a list of server commands on behalf of the given player.
     *
     * <p>Commands run at permission level 4 with feedback silenced.
     * The following placeholders are resolved before dispatch:
     * <ul>
     *   <li>{@code {player}} / {@code {name}} — player's current display name</li>
     *   <li>{@code {uuid}} — player's UUID string</li>
     * </ul>
     *
     * <p>All execution errors are caught and logged individually so that a single
     * bad command does not prevent the remaining commands from running.
     *
     * @param server   the server instance used to dispatch commands
     * @param player   the player for whom the reward is being applied
     * @param commands the list of raw command strings (may contain placeholders)
     * @param context  a short label used in log output to identify the reward tier
     */
    private static void executeCommands(
        MinecraftServer server,
        ServerPlayerEntity player,
        List<String> commands,
        String context
    ) {
        ServerCommandSource source = server
            .getCommandSource()
            .withLevel(4)
            .withSilent();
        String playerName = player.getName().getString();
        String playerUuid = player.getUuidAsString();

        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;

            String resolved = raw
                .replace("{player}", playerName)
                .replace("{name}", playerName)
                .replace("{uuid}", playerUuid);

            try {
                server
                    .getCommandManager()
                    .getDispatcher()
                    .execute(resolved, source);
                CobblemonArena.LOGGER.debug(
                    "[ArenaRewards] [{}] Executed: {}",
                    context,
                    resolved
                );
            } catch (CommandSyntaxException e) {
                CobblemonArena.LOGGER.error(
                    "[ArenaRewards] [{}] Command syntax error — command='{}', error='{}'",
                    context,
                    resolved,
                    e.getMessage()
                );
            } catch (Exception e) {
                CobblemonArena.LOGGER.error(
                    "[ArenaRewards] [{}] Unexpected error executing command '{}'",
                    context,
                    resolved,
                    e
                );
            }
        }
    }

    /**
     * Checks whether the player's current stats satisfy the threshold defined in
     * the given milestone config.
     *
     * @return {@code true} if the milestone threshold has been reached or exceeded
     */
    private static boolean checkMilestoneReached(
        PlayerStats stats,
        ArenaServerConfig.MilestoneRewardConfig milestone
    ) {
        try {
            // getStatType() returns a raw String — convert via the enum factory.
            ArenaServerConfig.RewardStatType statType =
                ArenaServerConfig.RewardStatType.fromName(
                    milestone.getStatType()
                );

            int threshold = milestone.getThreshold();

            return switch (statType) {
                case TOTAL_BATTLES -> stats.getTotalBattles() >= threshold;
                case RANKED_WINS -> stats.getLifetimeRankedWins() >= threshold;
                case RANKED_MATCHES -> stats.getLifetimeRankedMatches() >=
                threshold;
                case QUICK_WINS -> stats.getQuickWins() >= threshold;
                case QUICK_MATCHES -> stats.getQuickMatches() >= threshold;
            };
        } catch (Exception e) {
            CobblemonArena.LOGGER.error(
                "[ArenaRewards] Failed to evaluate milestone '{}': {}",
                milestone.getId(),
                e.getMessage()
            );
            return false;
        }
    }

    /**
     * Derives a stable, unique identifier for a milestone that is safe to store
     * in {@link PlayerStats#markRewardMilestoneGranted(String)}.
     *
     * <p>The admin-configured {@code id} field is preferred. If it is absent or
     * blank the method falls back to a composite of the stat-type name and the
     * numeric threshold (e.g. {@code "ranked_wins_50"}), which is guaranteed to
     * be unique for any sensible configuration.
     */
    private static String buildMilestoneId(
        ArenaServerConfig.MilestoneRewardConfig milestone
    ) {
        String id = milestone.getId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        try {
            String statType = milestone.getStatType();
            String typePart = (statType != null && !statType.isBlank())
                ? statType.trim().toLowerCase()
                : "unknown";
            return typePart + "_" + milestone.getThreshold();
        } catch (Exception e) {
            // Last-resort fallback — should never happen in practice.
            return "milestone_" + Integer.toHexString(milestone.hashCode());
        }
    }
}
