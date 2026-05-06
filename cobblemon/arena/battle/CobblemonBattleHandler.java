package cobblemon.arena.battle;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.ladder.ArenaPartyValidator;
import cobblemon.arena.quest.QuestManager;
import cobblemon.arena.queue.MatchmakingQueue;
import cobblemon.arena.stats.StatsManager;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;

public class CobblemonBattleHandler {

    public static void register() {
        // ── BATTLE_STARTED_POST ───────────────────────────────────────────────
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(
            Priority.NORMAL,
            event -> {
                Set<UUID> processedPlayers = new HashSet<>();

                for (BattleActor actor : event.getBattle().getActors()) {
                    if (actor instanceof PlayerBattleActor playerActor) {
                        ServerPlayerEntity player = playerActor.getEntity();
                        if (
                            player != null &&
                            processedPlayers.add(player.getUuid()) &&
                            !ArenaBattleManager.getInstance().handleExternalBattleStarted(
                                player,
                                event.getBattle().getBattleId()
                            )
                        ) {
                            MatchmakingQueue.getInstance().cancelParticipationForExternalBattle(
                                player
                            );
                        }
                    }
                }

                return Unit.INSTANCE;
            }
        );

        // ── BATTLE_VICTORY ────────────────────────────────────────────────────
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            CobblemonArena.LOGGER.debug("Vitoria de batalha detectada");

            ServerPlayerEntity winner = null;
            ServerPlayerEntity loser = null;
            ArenaSession session = null;

            // Locate winner from the event's winner actors.
            for (BattleActor actor : event.getWinners()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    ServerPlayerEntity player = playerActor.getEntity();
                    if (player != null) {
                        session = ArenaBattleManager.getInstance().getSession(
                            player
                        );
                        if (session != null && session.isActive()) {
                            winner = player;
                            break;
                        }
                    }
                }
            }

            if (winner != null && session != null) {
                loser = session.getOpponent(winner);
            }

            if (winner != null && loser != null && session != null) {
                // Se foi timeout mútuo, stats já foram registradas — pula direto para endArena
                if (session.isMutualTimeoutEnded()) {
                    CobblemonArena.LOGGER.info(
                        "BATTLE_VICTORY por timeout mútuo — encerrando sessão sem stats duplicadas"
                    );
                    ArenaBattleManager.getInstance().endArena(
                        session.getSessionId()
                    );
                    return Unit.INSTANCE;
                }

                // ── Stats recording (unchanged) ───────────────────────
                if (session.isQueueMatch()) {
                    if (session.isRankedMatch()) {
                        StatsManager.getInstance().recordRankedMatch(
                            winner,
                            loser,
                            session.getLadder(),
                            session.getTeamSnapshot(winner),
                            session.getTeamSnapshot(loser)
                        );
                        CobblemonArena.LOGGER.info(
                            "Resultado de partida ranqueada registrado"
                        );
                    } else {
                        String formatId = session.getLadder() != null ? session.getLadder().getBattleTypeId() : "default";
                        StatsManager.getInstance().recordQuickMatch(
                            formatId,
                            winner,
                            loser,
                            session.getTeamSnapshot(winner),
                            session.getTeamSnapshot(loser)
                        );
                        CobblemonArena.LOGGER.info(
                            "Resultado de partida casual registrado"
                        );
                    }
                }

                // ── Quest progress update ─────────────────────────────
                if (session.isQueueMatch()) {
                    ArenaLadder ladder = session.getLadder();
                    boolean isRanked = session.isRankedMatch();
                    // A "casual" match uses one of the CASUAL_* ladders.
                    boolean isCasual =
                        !isRanked &&
                        ladder != null &&
                        ladder
                            .getId()
                            .toLowerCase(Locale.ROOT)
                            .contains("casual");
                    String formatId = ladder != null ? ladder.getId() : "";

                    List<String> winnerTypes = extractPokemonTypes(winner);
                    List<String> loserTypes = extractPokemonTypes(loser);

                    int turns = 0;
                    try {
                        turns = event.getBattle().getTurn();
                    } catch (Exception ignored) {}
                    StatsManager.getInstance().recordMatchTurns(winner, loser, turns);

                    QuestManager.getInstance().onMatchCompleted(
                        winner,
                        true,
                        isRanked,
                        isCasual,
                        formatId,
                        winnerTypes,
                        turns,
                        false
                    );
                    QuestManager.getInstance().onMatchCompleted(
                        loser,
                        false,
                        isRanked,
                        isCasual,
                        formatId,
                        loserTypes,
                        turns,
                        false
                    );
                }

                // ── End arena session ─────────────────────────────────
                CobblemonArena.LOGGER.info(
                    "Encerrando batalha de arena por vitoria: Sessao {}",
                    session.getSessionId()
                );
                ArenaBattleManager.getInstance().endArena(
                    session.getSessionId()
                );
            } else {
                // Fallback: end any active sessions found on actor players.
                event
                    .getBattle()
                    .getActors()
                    .forEach(battleActor -> {
                        if (
                            battleActor instanceof
                                PlayerBattleActor playerActorx
                        ) {
                            ServerPlayerEntity serverPlayer =
                                playerActorx.getEntity();
                            if (serverPlayer != null) {
                                ArenaSession playerSession =
                                    ArenaBattleManager.getInstance().getSession(
                                        serverPlayer
                                    );
                                if (
                                    playerSession != null &&
                                    playerSession.isActive()
                                ) {
                                    CobblemonArena.LOGGER.info(
                                        "Encerrando batalha de arena por vitoria: Sessao {}",
                                        playerSession.getSessionId()
                                    );
                                    ArenaBattleManager.getInstance().endArena(
                                        playerSession.getSessionId()
                                    );
                                }
                            }
                        }
                    });
            }

            return Unit.INSTANCE;
        });

        // ── BATTLE_FLED ───────────────────────────────────────────────────────
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, event -> {
            CobblemonArena.LOGGER.debug("Fuga de batalha detectada");

            PlayerBattleActor patt0$temp = event.getPlayer();
            if (patt0$temp instanceof PlayerBattleActor) {
                ServerPlayerEntity serverPlayer = patt0$temp.getEntity();
                if (serverPlayer != null) {
                    ArenaSession session =
                        ArenaBattleManager.getInstance().getSession(
                            serverPlayer
                        );
                    if (session != null && session.isActive()) {
                        // Se foi timeout mútuo, stats já foram registradas em handleMutualTimeoutForfeit
                        // Encerra a sessão sem registrar stats novamente
                        if (session.isMutualTimeoutEnded()) {
                            CobblemonArena.LOGGER.info(
                                "BATTLE_FLED por timeout mútuo — encerrando sessão sem stats duplicadas: {}",
                                session.getSessionId()
                            );
                            ArenaBattleManager.getInstance().endArena(
                                session.getSessionId()
                            );
                            return Unit.INSTANCE;
                        }

                        // The fleeing player loses; the opponent implicitly wins.
                        // Quest tracking: the fleeing player still "played" a match.
                        if (session.isQueueMatch()) {
                            ArenaLadder ladder = session.getLadder();
                            boolean isRanked = session.isRankedMatch();
                            boolean isCasual =
                                !isRanked &&
                                ladder != null &&
                                ladder
                                    .getId()
                                    .toLowerCase(Locale.ROOT)
                                    .contains("casual");
                            String formatId =
                                ladder != null ? ladder.getId() : "";

                            int turns = 0;
                            try {
                                turns = event.getBattle().getTurn();
                            } catch (Exception ignored) {}

                            // Fleeing player is the loser.
                            QuestManager.getInstance().onMatchCompleted(
                                serverPlayer,
                                false,
                                isRanked,
                                isCasual,
                                formatId,
                                extractPokemonTypes(serverPlayer),
                                turns,
                                true
                            );

                            // Opponent is the winner (if still online).
                            ServerPlayerEntity opponent = session.getOpponent(
                                serverPlayer
                            );
                            if (opponent != null) {
                                QuestManager.getInstance().onMatchCompleted(
                                    opponent,
                                    true,
                                    isRanked,
                                    isCasual,
                                    formatId,
                                    extractPokemonTypes(opponent),
                                    turns,
                                    true
                                );
                            }
                        }

                        CobblemonArena.LOGGER.info(
                            "Encerrando batalha de arena por fuga: Sessao {}",
                            session.getSessionId()
                        );
                        ArenaBattleManager.getInstance().endArena(
                            session.getSessionId()
                        );
                    }
                }
            }

            return Unit.INSTANCE;
        });

        CobblemonArena.LOGGER.info(
            "Handlers de evento de batalha do Cobblemon registrados"
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Collects every unique Pokémon type (lower-cased) present in the player's
     * current party.  Uses both the primary and secondary type of each species.
     *
     * <p>The result is fed into {@link QuestManager#onMatchCompleted} so that
     * {@link cobblemon.arena.quest.QuestType#USE_TYPE} quests can be satisfied.</p>
     *
     * @param player the player whose party is inspected
     * @return a mutable, deduplicated list of type names; never {@code null}
     */
    private static List<String> extractPokemonTypes(ServerPlayerEntity player) {
        Set<String> types = new LinkedHashSet<>();
        for (Pokemon pokemon : ArenaPartyValidator.getPartyPokemon(player)) {
            try {
                var primary = pokemon.getSpecies().getPrimaryType();
                if (primary != null) {
                    types.add(primary.getName().toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {}
            try {
                var secondary = pokemon.getSpecies().getSecondaryType();
                if (secondary != null) {
                    types.add(secondary.getName().toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(types);
    }
}
