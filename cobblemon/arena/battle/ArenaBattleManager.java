package cobblemon.arena.battle;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.access.ArenaNet;
import cobblemon.arena.arena.ArenaInstance;
import cobblemon.arena.arena.ArenaManager;
import cobblemon.arena.config.ArenaServerConfig;
import cobblemon.arena.ladder.ArenaLadder;
import cobblemon.arena.ladder.ArenaPartyValidator;
import cobblemon.arena.network.ArenaBattleTransitionPacket;
import cobblemon.arena.network.ArenaTransitionPokemonEntryPayload;
import cobblemon.arena.queue.MatchmakingQueue;
import cobblemon.arena.stats.StatsManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartError;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.ForfeitActionResponse;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ArenaBattleManager {

    private static final ArenaBattleManager INSTANCE = new ArenaBattleManager();
    private static final int PRE_BATTLE_TRANSITION_TICKS = 600;
    private static final int BATTLE_INITIALIZATION_TIMEOUT_TICKS = 120;
    private final Map<UUID, ArenaSession> activeSessions = new HashMap<>();
    private final Map<UUID, ArenaSession> playerToSession = new HashMap<>();

    private ArenaBattleManager() {}

    public static ArenaBattleManager getInstance() {
        return INSTANCE;
    }

    public ArenaSession startArena(
        ServerPlayerEntity player1,
        ServerPlayerEntity player2
    ) {
        if (this.isInArena(player1)) {
            CobblemonArena.LOGGER.warn(
                "{} is already in an arena battle",
                player1.getName().getString()
            );
            return null;
        } else if (this.isInArena(player2)) {
            CobblemonArena.LOGGER.warn(
                "{} is already in an arena battle",
                player2.getName().getString()
            );
            return null;
        } else if (
            BattleRegistry.getBattleByParticipatingPlayer(player1) != null
        ) {
            CobblemonArena.LOGGER.warn(
                "{} is already in a Cobblemon battle",
                player1.getName().getString()
            );
            return null;
        } else if (
            BattleRegistry.getBattleByParticipatingPlayer(player2) != null
        ) {
            CobblemonArena.LOGGER.warn(
                "{} is already in a Cobblemon battle",
                player2.getName().getString()
            );
            return null;
        } else {
            Optional<ArenaInstance> arenaOpt =
                ArenaManager.getInstance().claimArena(UUID.randomUUID());
            if (arenaOpt.isEmpty()) {
                CobblemonArena.LOGGER.warn(
                    "No arena available for battle between {} and {}",
                    player1.getName().getString(),
                    player2.getName().getString()
                );
                return null;
            } else {
                ArenaInstance arena = arenaOpt.get();
                ArenaSession session = new ArenaSession(
                    player1,
                    player2,
                    arena
                );
                arena.release();
                arena.claim(session.getSessionId());
                this.activeSessions.put(session.getSessionId(), session);
                this.playerToSession.put(player1.getUuid(), session);
                this.playerToSession.put(player2.getUuid(), session);
                session.teleportToArena();
                CobblemonArena.LOGGER.info(
                    "Arena battle started: {} vs {} in arena {} (Session: {})",
                    new Object[] {
                        player1.getName().getString(),
                        player2.getName().getString(),
                        arena.getArenaId(),
                        session.getSessionId(),
                    }
                );
                return session;
            }
        }
    }

    public ArenaSession startMatch(
        ServerPlayerEntity player1,
        ServerPlayerEntity player2,
        ArenaLadder ladder,
        boolean queueMatch
    ) {
        ArenaSession session = this.startArena(player1, player2);
        if (session == null) {
            return null;
        } else {
            try {
                session.setTeamSnapshots(
                    this.captureTeamSnapshot(player1, ladder),
                    this.captureTeamSnapshot(player2, ladder)
                );
                session.setMatchMetadata(ladder, queueMatch);
                session.scheduleBattleStart(
                    player1.getServer().getTicks() + PRE_BATTLE_TRANSITION_TICKS
                );
                this.sendBattleTransition(session);
                CobblemonArena.LOGGER.info(
                    "Queued arena battle start for session {} on ladder {}",
                    session.getSessionId(),
                    ladder.getId()
                );
                return session;
            } catch (Exception var7) {
                CobblemonArena.LOGGER.error(
                    "Falha ao iniciar batalha de arena para a ladder {}",
                    ladder.getId(),
                    var7
                );
                this.endArena(session.getSessionId());
                return null;
            }
        }
    }

    public void endArena(UUID sessionId) {
        ArenaSession session = this.activeSessions.remove(sessionId);
        if (session != null) {
            // Remove do mapa de jogadores PRIMEIRO para que isInArena() retorne false imediatamente
            this.playerToSession.remove(session.getPlayer1().getUuid());
            this.playerToSession.remove(session.getPlayer2().getUuid());
            try {
                session.end();
            } catch (Exception e) {
                CobblemonArena.LOGGER.error(
                    "Erro ao finalizar sessão de arena {}",
                    sessionId,
                    e
                );
            }
            ArenaManager.getInstance().releaseArenaBySession(sessionId);
            CobblemonArena.LOGGER.info(
                "Batalha de arena encerrada: Sessao {}",
                sessionId
            );
        }
    }

    public void endArenaForPlayer(ServerPlayerEntity player) {
        ArenaSession session = this.playerToSession.get(player.getUuid());
        if (session != null) {
            this.endArena(session.getSessionId());
        }
    }

    public ArenaSession getSession(ServerPlayerEntity player) {
        return this.playerToSession.get(player.getUuid());
    }

    public ArenaSession getSession(UUID sessionId) {
        return this.activeSessions.get(sessionId);
    }

    public boolean isInArena(ServerPlayerEntity player) {
        return this.playerToSession.containsKey(player.getUuid());
    }

    public ServerPlayerEntity getOpponent(ServerPlayerEntity player) {
        ArenaSession session = this.getSession(player);
        return session != null ? session.getOpponent(player) : null;
    }

    public boolean isMovementLocked(ServerPlayerEntity player) {
        return this.isInArena(player);
    }

    public int getActiveBattleCount() {
        return this.activeSessions.size();
    }

    public List<ArenaSession> getSpectatableSessions() {
        List<ArenaSession> sessions = new ArrayList<>();

        for (ArenaSession session : this.activeSessions.values()) {
            if (session != null && session.isActive()) {
                if (session.getBattleId() != null) {
                    PokemonBattle battle = BattleRegistry.getBattle(session.getBattleId());
                    if (battle != null && battle.getEnded()) {
                        continue;
                    }
                }
                sessions.add(session);
            }
        }

        return sessions;
    }

    public void tick(MinecraftServer server) {
        if (server != null && !this.activeSessions.isEmpty()) {
            int actionTimerSeconds =
                ArenaServerConfig.getInstance().getActionTimerSeconds();
            long nowTick = server.getTicks();

            for (ArenaSession session : new ArrayList<>(
                this.activeSessions.values()
            )) {
                if (session != null && session.isActive()) {
                    this.checkPlayerBounds(session);
                    if (!session.isActive()) continue;

                    if (session.shouldStartBattle(nowTick)) {
                        this.tryStartPendingBattle(session);
                    } else if (session.getBattleId() != null) {
                        PokemonBattle battle = BattleRegistry.getBattle(
                            session.getBattleId()
                        );
                        if (battle == null) {
                            CobblemonArena.LOGGER.info(
                                "Arena session {} battle {} was cleaned up natively; ending arena session",
                                session.getSessionId(),
                                session.getBattleId()
                            );
                            this.endArena(session.getSessionId());
                        } else if (!battle.getStarted()) {
                            if (
                                session.hasBattleInitializationTimedOut(nowTick)
                            ) {
                                this.handleBattleInitializationTimeout(
                                    session,
                                    battle
                                );
                            }
                        } else {
                            session.clearBattleInitializationDeadline();
                            if (battle.getEnded()) {
                                // Aguarda o Cobblemon remover a batalha nativamente (battle == null)
                                // para não interromper a animação de vitória e a restauração da HUD do cliente.
                                session.resetDecisionTimer(
                                    session.getPlayer1()
                                );
                                session.resetDecisionTimer(
                                    session.getPlayer2()
                                );
                            } else {
                                if (
                                    this.processDecisionTimer(
                                        session,
                                        battle,
                                        session.getPlayer1(),
                                        actionTimerSeconds,
                                        nowTick
                                    )
                                ) {
                                    return;
                                }

                                if (
                                    this.processDecisionTimer(
                                        session,
                                        battle,
                                        session.getPlayer2(),
                                        actionTimerSeconds,
                                        nowTick
                                    )
                                ) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean hasActiveRankedQueueMatch() {
        return this.activeSessions.values()
            .stream()
            .anyMatch(
                session -> session.isQueueMatch() && session.isRankedMatch()
            );
    }

    private void checkPlayerBounds(ArenaSession session) {
        if (!session.isActive()) return;
        this.checkPlayerBound(session, session.getPlayer1());
        if (session.isActive()) {
            this.checkPlayerBound(session, session.getPlayer2());
        }
    }

    private void checkPlayerBound(ArenaSession session, ServerPlayerEntity player) {
        if (player.isRemoved()) return;

        boolean outOfBounds = false;
        if (!player.getWorld().getRegistryKey().equals(session.getArena().getDimension())) {
            outOfBounds = true;
        } else {
            net.minecraft.util.math.BlockPos center = session.getArena().getBattleCenter();
            if (player.getBlockPos().getSquaredDistance(center) > 10000) { // 100 blocks radius
                outOfBounds = true;
            }
        }

        if (outOfBounds) {
            CobblemonArena.LOGGER.info("Jogador {} saiu da arena. Declarando forfeit.", player.getName().getString());
            player.sendMessage(Text.literal("§cVoce saiu da arena e perdeu a partida!"), false);
            ServerPlayerEntity opponent = session.getOpponent(player);
            if (opponent != null) {
                opponent.sendMessage(Text.literal("§aO oponente fugiu da arena. Voce venceu!"), false);
            }
            if (session.getBattleId() != null) {
                PokemonBattle battle = BattleRegistry.getBattle(session.getBattleId());
                if (battle != null && !battle.getEnded()) {
                    PlayerBattleActor actor = this.findPlayerActor(battle, player);
                    if (actor != null) {
                        try {
                            actor.setActionResponses(java.util.List.of(new ForfeitActionResponse()));
                        } catch (Exception e) {
                            CobblemonArena.LOGGER.error("Falha ao forcar forfeit por abandono para {}", player.getName().getString(), e);
                            this.endArena(session.getSessionId());
                        }
                        return;
                    }
                }
            }
            this.endArena(session.getSessionId());
        }
    }

    private boolean processDecisionTimer(
        ArenaSession session,
        PokemonBattle battle,
        ServerPlayerEntity player,
        int actionTimerSeconds,
        long nowTick
    ) {
        PlayerBattleActor actor = this.findPlayerActor(battle, player);
        if (
            actor != null && actor.getMustChoose() && actor.getRequest() != null
        ) {
            long deadlineTick = session.getDecisionDeadlineTick(player);
            if (deadlineTick < 0L) {
                session.armDecisionTimer(
                    player,
                    nowTick + actionTimerSeconds * 20L
                );
                return false;
            } else {
                long remainingTicks = deadlineTick - nowTick;
                if (remainingTicks <= 0L) {
                    ServerPlayerEntity opponent = session.getOpponent(player);
                    PlayerBattleActor opponentActor = this.findPlayerActor(
                        battle,
                        opponent
                    );
                    long opponentDeadline = session.getDecisionDeadlineTick(
                        opponent
                    );
                    boolean opponentTimedOut =
                        opponent != null &&
                        opponentActor != null &&
                        opponentActor.getMustChoose() &&
                        opponentActor.getRequest() != null &&
                        opponentDeadline >= 0L &&
                        opponentDeadline - nowTick <= 0L;
                    if (opponentTimedOut) {
                        this.handleMutualTimeoutForfeit(
                            session,
                            battle,
                            player,
                            opponent
                        );
                        return true;
                    }
                    this.forceTimeoutForfeit(session, battle, player, actor);
                    return true;
                } else {
                    int remainingSeconds = (int) Math.ceil(
                        remainingTicks / 20.0
                    );
                    if (
                        (remainingSeconds == 10 || remainingSeconds == 5) &&
                        session.getLastWarningSeconds(player) !=
                        remainingSeconds
                    ) {
                        session.setLastWarningSeconds(player, remainingSeconds);
                        this.sendDecisionWarning(
                            session,
                            player,
                            remainingSeconds
                        );
                    }

                    return false;
                }
            }
        } else {
            session.resetDecisionTimer(player);
            return false;
        }
    }

    private void sendDecisionWarning(
        ArenaSession session,
        ServerPlayerEntity player,
        int remainingSeconds
    ) {
        ServerPlayerEntity opponent = session.getOpponent(player);
        player.sendMessage(
            Text.literal(
                "§eTempo de acao da Arena: §f" +
                    remainingSeconds +
                    "s§e para escolher uma acao ou voce ira desistir."
            ),
            false
        );
        if (opponent != null) {
            opponent.sendMessage(
                Text.literal(
                    "§eTempo de acao da Arena: aguardando §f" +
                        player.getName().getString() +
                        "§e (" +
                        remainingSeconds +
                        "s restantes)."
                ),
                false
            );
        }
    }

    /**
     * Called when a player’s action timer expires.
     * Instead of forfeiting the ENTIRE battle, the player’s active Pokémon
     * uses its <em>first available move</em> (or Struggle if none) so the
     * battle continues.  The player merely loses their free choice of action
     * for that turn.
     */
    private void forceTimeoutForfeit(
        ArenaSession session,
        PokemonBattle battle,
        ServerPlayerEntity player,
        PlayerBattleActor actor
    ) {
        ServerPlayerEntity opponent = session.getOpponent(player);
        CobblemonArena.LOGGER.info(
            "Arena action timer expired for {} in session {} (battle {}) — auto-selecting move",
            new Object[] {
                player.getName().getString(),
                session.getSessionId(),
                battle.getBattleId(),
            }
        );

        // Notify both players
        player.sendMessage(
            Text.literal(
                "§cTempo esgotado! Voce perdeu a partida por inatividade."
            ),
            false
        );
        if (opponent != null) {
            opponent.sendMessage(
                Text.literal(
                    "§a" +
                        player.getName().getString() +
                        " ficou sem tempo e perdeu por inatividade."
                ),
                false
            );
        }

        session.resetDecisionTimer(player);

        // Cobblemon 1.7.3 does not expose a public "pass turn" API.
        // ForfeitActionResponse is the only available response that resolves
        // a pending action; it ends this player’s participation in the battle.
        // TODO: replace with a move auto-select when Cobblemon exposes the API.
        try {
            actor.setActionResponses(List.of(new ForfeitActionResponse()));
        } catch (Exception e) {
            CobblemonArena.LOGGER.error(
                "Failed to apply arena timeout response for {} in session {}",
                new Object[] {
                    player.getName().getString(),
                    session.getSessionId(),
                    e,
                }
            );
        }
    }

    private void handleMutualTimeoutForfeit(
        ArenaSession session,
        PokemonBattle battle,
        ServerPlayerEntity player,
        ServerPlayerEntity opponent
    ) {
        if (player == null || opponent == null) {
            return;
        }
        CobblemonArena.LOGGER.info(
            "Both players timed out in session {} (battle {}) — applying double loss",
            session.getSessionId(),
            battle.getBattleId()
        );
        player.sendMessage(
            Text.literal(
                "§cAmbos ficaram sem tempo para agir. A partida foi encerrada com derrota para os dois."
            ),
            false
        );
        opponent.sendMessage(
            Text.literal(
                "§cAmbos ficaram sem tempo para agir. A partida foi encerrada com derrota para os dois."
            ),
            false
        );
        try {
            if (session.isRankedMatch()) {
                StatsManager.getInstance().recordRankedDoubleLoss(
                    player,
                    opponent,
                    session.getLadder(),
                    session.getTeamSnapshot(player),
                    session.getTeamSnapshot(opponent)
                );
            } else {
                StatsManager.getInstance().recordQuickDoubleLoss(
                    player,
                    opponent,
                    session.getTeamSnapshot(player),
                    session.getTeamSnapshot(opponent)
                );
            }
        } catch (Exception e) {
            CobblemonArena.LOGGER.error(
                "Failed to apply double-loss stats for session {}",
                session.getSessionId(),
                e
            );
        }
        session.resetDecisionTimer(player);
        session.resetDecisionTimer(opponent);
        // Marca a sessão como encerrada por timeout mútuo ANTES de submeter os forfeits,
        // para que o handler de BATTLE_FLED/BATTLE_VICTORY não registre stats duplicadas.
        session.setMutualTimeoutEnded(true);
        // Usa ForfeitActionResponse para encerrar a batalha de forma nativa no Cobblemon
        // (em vez de BattleRegistry.closeBattle que pode corromper o party)
        PlayerBattleActor actor1 = this.findPlayerActor(battle, player);
        PlayerBattleActor actor2 = this.findPlayerActor(battle, opponent);
        boolean forfeitSubmitted = false;
        if (actor1 != null) {
            try {
                actor1.setActionResponses(
                    java.util.List.of(new ForfeitActionResponse())
                );
                forfeitSubmitted = true;
                CobblemonArena.LOGGER.info(
                    "Forfeit submetido para {} por timeout mútuo",
                    player.getName().getString()
                );
            } catch (Exception e) {
                CobblemonArena.LOGGER.error(
                    "Falha ao submeter forfeit para {}",
                    player.getName().getString(),
                    e
                );
            }
        }
        if (actor2 != null) {
            try {
                actor2.setActionResponses(
                    java.util.List.of(new ForfeitActionResponse())
                );
                CobblemonArena.LOGGER.info(
                    "Forfeit submetido para {} por timeout mútuo",
                    opponent.getName().getString()
                );
            } catch (Exception e) {
                CobblemonArena.LOGGER.error(
                    "Falha ao submeter forfeit para {}",
                    opponent.getName().getString(),
                    e
                );
            }
        }
        // Se nenhum forfeit pôde ser submetido (atores não encontrados), encerra diretamente
        if (!forfeitSubmitted) {
            CobblemonArena.LOGGER.warn(
                "Nenhum forfeit submetido para sessão {} — encerrando arena diretamente",
                session.getSessionId()
            );
            this.endArena(session.getSessionId());
        }
        // Caso contrário, BATTLE_FLED/BATTLE_VICTORY irá disparar e chamar endArena normalmente
    }

    private PlayerBattleActor findPlayerActor(
        PokemonBattle battle,
        ServerPlayerEntity player
    ) {
        for (BattleActor battleActor : battle.getActors()) {
            if (
                battleActor instanceof PlayerBattleActor playerActor &&
                player.getUuid().equals(playerActor.getUuid())
            ) {
                return playerActor;
            }
        }

        return null;
    }

    private List<ArenaSession.TeamPokemonSnapshot> captureTeamSnapshot(
        ServerPlayerEntity player,
        ArenaLadder ladder
    ) {
        List<ArenaSession.TeamPokemonSnapshot> team = new ArrayList<>();

        for (Pokemon pokemon : ArenaPartyValidator.getPartyPokemon(player)) {
            String ability = "";
            try {
                Object dn = pokemon.getAbility().getDisplayName();
                String rawAbility =
                    dn instanceof net.minecraft.text.Text
                        ? ((net.minecraft.text.Text) dn).getString()
                        : String.valueOf(dn);
                ability = formatDisplayName(rawAbility);
            } catch (Exception ignored) {}

            String heldItem = "";
            try {
                net.minecraft.item.ItemStack h = pokemon.heldItem();
                if (h != null && !h.isEmpty()) {
                    // Armazena o ID de registro do item (ex: "cobblemon:choice_band")
                    // para que o cliente possa renderizar o ícone do item
                    net.minecraft.util.Identifier registryId =
                        net.minecraft.registry.Registries.ITEM.getId(
                            h.getItem()
                        );
                    heldItem = registryId.toString();
                }
            } catch (Exception ignored) {}

            List<String> types = new ArrayList<>();
            try {
                types.add(pokemon.getSpecies().getPrimaryType().getName());
                if (pokemon.getSpecies().getSecondaryType() != null) {
                    types.add(
                        pokemon.getSpecies().getSecondaryType().getName()
                    );
                }
            } catch (Exception ignored) {}

            List<String> moves = new ArrayList<>();
            try {
                pokemon
                    .getMoveSet()
                    .getMoves()
                    .forEach(m -> moves.add(m.getDisplayName().getString()));
            } catch (Exception ignored) {}

            String nature = "";
            try {
                Object ndn = pokemon.getNature().getDisplayName();
                String rawNature =
                    ndn instanceof net.minecraft.text.Text
                        ? ((net.minecraft.text.Text) ndn).getString()
                        : String.valueOf(ndn);
                nature = formatDisplayName(rawNature);
            } catch (Exception ignored) {}

            team.add(
                new ArenaSession.TeamPokemonSnapshot(
                    pokemon.getSpecies().getResourceIdentifier().toString(),
                    pokemon.getSpecies().getName(),
                    ability,
                    heldItem,
                    types,
                    moves,
                    nature,
                    ladder != null && ladder.getAdjustLevel() > 0 ? ladder.getAdjustLevel() : pokemon.getLevel()
                )
            );
        }

        return team;
    }

    public void tryStartPendingBattleIfReady(ArenaSession session) {
        if (
            session != null &&
            session.isActive() &&
            session.getBattleId() == null &&
            !session.isBattleLaunchInProgress() &&
            session.hasBothLeadSelections()
        ) {
            if (!session.hasPendingBattleStart()) {
                session.scheduleBattleStart(
                    session.getPlayer1().getServer().getTicks()
                );
            }

            this.tryStartPendingBattle(session);
        }
    }

    private void tryStartPendingBattle(ArenaSession session) {
        session.clearPendingBattleStart();
        if (this.hasExternalBattleConflict(session)) {
            this.cancelPreBattleSessionForExternalBattle(session);
        } else {
            ArenaLadder ladder =
                session.getLadder() != null
                    ? session.getLadder()
                    : ArenaLadder.defaultQuick();
            UUID player1Lead = this.resolveSelectedLead(
                session,
                session.getPlayer1()
            );
            UUID player2Lead = this.resolveSelectedLead(
                session,
                session.getPlayer2()
            );
            CobblemonArena.LOGGER.debug(
                "Leads selecionados — P1: {} uuid={} | P2: {} uuid={}",
                session.getPlayer1().getName().getString(),
                player1Lead,
                session.getPlayer2().getName().getString(),
                player2Lead
            );
            // Garante que os leads selecionados estejam nos primeiros slots da party
            // (swap seguro, sem null-bridge que corrompia storeCoordinates)
            applySelectedLeadOrder(session, session.getPlayer1());
            applySelectedLeadOrder(session, session.getPlayer2());
            BattleFormat battleFormat = ladder.createBattleFormat();

            try {
                session.setBattleLaunchInProgress(true);
                CobblemonArena.LOGGER.info(
                    "Attempting Arena battle launch for session {} on ladder {} with format {}",
                    new Object[] {
                        session.getSessionId(),
                        ladder.getId(),
                        battleFormat.toFormatJSON(),
                    }
                );
                BattleStartResult result = BattleBuilder.INSTANCE.pvp1v1(
                    session.getPlayer1(),
                    session.getPlayer2(),
                    player1Lead,
                    player2Lead,
                    battleFormat,
                    false,
                    true,
                    player -> PlayerExtensionsKt.party(player)
                );
                if (result instanceof ErroredBattleStart erroredBattleStart) {
                    this.logBattleStartErrors(
                        session,
                        ladder,
                        erroredBattleStart
                    );
                    session.setBattleLaunchInProgress(false);
                    this.endArena(session.getSessionId());
                    return;
                }

                if (
                    !(result instanceof
                            SuccessfulBattleStart successfulBattleStart)
                ) {
                    CobblemonArena.LOGGER.warn(
                        "Falha ao iniciar batalha atrasada do Cobblemon para sessao de arena {} na ladder {}",
                        session.getSessionId(),
                        ladder.getId()
                    );
                    session.setBattleLaunchInProgress(false);
                    this.endArena(session.getSessionId());
                    return;
                }

                session.setBattleId(
                    successfulBattleStart.getBattle().getBattleId()
                );
                session.armBattleInitializationDeadline(
                    session.getPlayer1().getServer().getTicks() +
                        BATTLE_INITIALIZATION_TIMEOUT_TICKS
                );
                session.setBattleLaunchInProgress(false);
                CobblemonArena.LOGGER.info(
                    "Started delayed arena battle {} on ladder {}",
                    session.getSessionId(),
                    ladder.getId()
                );
            } catch (Exception var8) {
                session.setBattleLaunchInProgress(false);
                CobblemonArena.LOGGER.error(
                    "Falha ao iniciar batalha de arena atrasada para a ladder {}",
                    ladder.getId(),
                    var8
                );
                this.endArena(session.getSessionId());
            }
        }
    }

    /**
     * Move os Pokémon selecionados como leads para os primeiros slots do party,
     * fazendo apenas os swaps estritamente necessários (mínimo de chamadas set).
     * Usa reflexão defensiva para encontrar o método set correto,
     * independente da versão exata do Cobblemon.
     */
    private void applySelectedLeadOrder(
        ArenaSession session,
        ServerPlayerEntity player
    ) {
        try {
            List<UUID> selected = session.getSelectedLeads(player);
            int required = session.getRequiredLeadCount();
            if (selected.isEmpty() || selected.size() < required) return;

            com.cobblemon.mod.common.api.storage.party.PlayerPartyStore partyStore =
                Cobblemon.INSTANCE.getStorage().getParty(player);

            // Encontra o método 'swap(int, int)' via reflexão
            java.lang.reflect.Method foundSwapMethod = null;
            for (java.lang.reflect.Method m : partyStore.getClass().getMethods()) {
                if (!m.getName().equals("swap") || m.getParameterCount() != 2) continue;
                Class<?>[] pTypes = m.getParameterTypes();
                if (pTypes[0] == int.class && pTypes[1] == int.class) {
                    foundSwapMethod = m;
                    break;
                }
            }

            if (foundSwapMethod == null) {
                CobblemonArena.LOGGER.error(
                    "Não foi possível encontrar método 'swap(int, int)' no PlayerPartyStore para {}",
                    player.getName().getString()
                );
                return;
            }

            final java.lang.reflect.Method swapMethod = foundSwapMethod;

            // Função helper para invocar swap(slot1, slot2)
            java.util.function.BiConsumer<Integer, Integer> doSwap = (slot1, slot2) -> {
                try {
                    swapMethod.invoke(partyStore, slot1, slot2);
                } catch (Exception ex) {
                    CobblemonArena.LOGGER.error(
                        "Falha ao realizar swap entre os slots {} e {}: {}",
                        slot1, slot2, ex.getMessage()
                    );
                }
            };

            // Algoritmo: para cada lead selecionado, faz um swap seguro
            // Move o lead para o slot alvo (targetSlot = 0, 1, 2 ...)
            int appliedCount = 0;
            for (int targetSlot = 0; targetSlot < selected.size(); targetSlot++) {
                UUID targetId = selected.get(targetSlot);

                // Descobre o slot atual do Pokémon selecionado
                int currentSlot = -1;
                for (int j = 0; j < 6; j++) {
                    Pokemon p = partyStore.get(j);
                    if (p != null && targetId.equals(p.getUuid())) {
                        currentSlot = j;
                        break;
                    }
                }

                if (currentSlot < 0) {
                    CobblemonArena.LOGGER.warn(
                        "Pokemon selecionado {} não encontrado no party de {}",
                        targetId, player.getName().getString()
                    );
                    continue;
                }

                if (currentSlot == targetSlot) {
                    appliedCount++;
                    continue; // Já está no lugar certo
                }

                CobblemonArena.LOGGER.debug(
                    "Swap seguro party[{}] <-> party[{}] para {}",
                    targetSlot, currentSlot, player.getName().getString()
                );

                // Realiza a troca usando o método oficial de swap do Cobblemon, que preserva as coordenadas
                doSwap.accept(targetSlot, currentSlot);
                appliedCount++;
            }

            CobblemonArena.LOGGER.info(
                "Lead order aplicada para {}: {}/{} leads trocados",
                player.getName().getString(), appliedCount, selected.size()
            );
        } catch (Exception e) {
            CobblemonArena.LOGGER.error(
                "Falha geral ao aplicar ordem de leads para {}",
                player.getName().getString(),
                e
            );
        }
    }

    private void handleBattleInitializationTimeout(
        ArenaSession session,
        PokemonBattle battle
    ) {
        ArenaLadder ladder =
            session.getLadder() != null
                ? session.getLadder()
                : ArenaLadder.defaultQuick();
        CobblemonArena.LOGGER.error(
            "Arena battle {} for session {} on ladder {} never initialized. Format: {}",
            new Object[] {
                battle.getBattleId(),
                session.getSessionId(),
                ladder.getId(),
                battle.getFormat().toFormatJSON(),
            }
        );
        session.clearBattleInitializationDeadline();

        try {
            BattleRegistry.closeBattle(battle);
        } catch (Exception var5) {
            CobblemonArena.LOGGER.error(
                "Failed to close uninitialized Arena battle {} for session {}",
                new Object[] {
                    battle.getBattleId(),
                    session.getSessionId(),
                    var5,
                }
            );
        }

        session
            .getPlayer1()
            .sendMessage(
                Text.literal(
                    "§cA batalha de Arena falhou ao iniciar e foi cancelada."
                ),
                false
            );
        session
            .getPlayer2()
            .sendMessage(
                Text.literal(
                    "§cA batalha de Arena falhou ao iniciar e foi cancelada."
                ),
                false
            );
        this.endArena(session.getSessionId());
    }

    private void logBattleStartErrors(
        ArenaSession session,
        ArenaLadder ladder,
        ErroredBattleStart battleStart
    ) {
        List<String> messages = new ArrayList<>();

        for (BattleStartError error : battleStart.getErrors()) {
            String message = error
                .getMessageFor(session.getPlayer1())
                .getString();
            if (!message.isBlank()) {
                messages.add(message);
            }
        }

        String summary = messages.isEmpty()
            ? "Unknown battle start error"
            : String.join(" | ", messages);
        CobblemonArena.LOGGER.warn(
            "Falha ao iniciar batalha de arena para sessao {} na ladder {}: {}",
            new Object[] { session.getSessionId(), ladder.getId(), summary }
        );

        for (BattleStartError errorx : battleStart.getErrors()) {
            session
                .getPlayer1()
                .sendMessage(errorx.getMessageFor(session.getPlayer1()), false);
            session
                .getPlayer2()
                .sendMessage(errorx.getMessageFor(session.getPlayer2()), false);
        }
    }

    public boolean handleExternalBattleStarted(
        ServerPlayerEntity player,
        UUID battleId
    ) {
        ArenaSession session = this.getSession(player);
        if (session != null && session.isActive()) {
            if (
                session.getBattleId() != null &&
                session.getBattleId().equals(battleId)
            ) {
                return false;
            } else if (session.isBattleLaunchInProgress()) {
                return false;
            } else {
                this.cancelPreBattleSessionForExternalBattle(session, player);
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean hasExternalBattleConflict(ArenaSession session) {
        PokemonBattle player1Battle =
            BattleRegistry.getBattleByParticipatingPlayer(session.getPlayer1());
        if (
            player1Battle != null &&
            !player1Battle.getBattleId().equals(session.getBattleId())
        ) {
            return true;
        } else {
            PokemonBattle player2Battle =
                BattleRegistry.getBattleByParticipatingPlayer(
                    session.getPlayer2()
                );
            return (
                player2Battle != null &&
                !player2Battle.getBattleId().equals(session.getBattleId())
            );
        }
    }

    private void cancelPreBattleSessionForExternalBattle(ArenaSession session) {
        ServerPlayerEntity player1 = session.getPlayer1();
        ServerPlayerEntity player2 = session.getPlayer2();
        boolean player1Busy =
            BattleRegistry.getBattleByParticipatingPlayer(player1) != null;
        boolean player2Busy =
            BattleRegistry.getBattleByParticipatingPlayer(player2) != null;
        this.cancelPreBattleSessionForExternalBattle(
            session,
            player1Busy ? player1 : (player2Busy ? player2 : null)
        );
    }

    private void cancelPreBattleSessionForExternalBattle(
        ArenaSession session,
        ServerPlayerEntity externalBattlePlayer
    ) {
        ServerPlayerEntity player1 = session.getPlayer1();
        ServerPlayerEntity player2 = session.getPlayer2();
        ArenaLadder ladder = session.getLadder();
        boolean queueMatch = session.isQueueMatch();
        boolean player1Busy =
            BattleRegistry.getBattleByParticipatingPlayer(player1) != null;
        boolean player2Busy =
            BattleRegistry.getBattleByParticipatingPlayer(player2) != null;
        this.endArena(session.getSessionId());
        if (player1Busy) {
            player1.sendMessage(
                Text.literal(
                    "§eSua partida de arena foi cancelada porque voce entrou em outra batalha."
                ),
                false
            );
        }

        if (player2Busy) {
            player2.sendMessage(
                Text.literal(
                    "§eSua partida de arena foi cancelada porque voce entrou em outra batalha."
                ),
                false
            );
        }

        if (!player1Busy) {
            player1.sendMessage(
                Text.literal(
                    "§eSua partida de arena foi cancelada porque seu oponente entrou em outra batalha."
                ),
                false
            );
            if (queueMatch && ladder != null) {
                MatchmakingQueue.getInstance().requeueAfterArenaAbort(
                    player1,
                    ladder,
                    "§7Voce voltou para a fila de " +
                        ladder.getDisplayName() +
                        "."
                );
            }
        }

        if (!player2Busy) {
            player2.sendMessage(
                Text.literal(
                    "§eSua partida de arena foi cancelada porque seu oponente entrou em outra batalha."
                ),
                false
            );
            if (queueMatch && ladder != null) {
                MatchmakingQueue.getInstance().requeueAfterArenaAbort(
                    player2,
                    ladder,
                    "§7Voce voltou para a fila de " +
                        ladder.getDisplayName() +
                        "."
                );
            }
        }

        if (externalBattlePlayer != null) {
            CobblemonArena.LOGGER.info(
                "Cancelled Arena pre-battle session {} because {} entered another battle",
                session.getSessionId(),
                externalBattlePlayer.getName().getString()
            );
        }
    }

    private void sendBattleTransition(ArenaSession session) {
        ServerPlayerEntity player1 = session.getPlayer1();
        ServerPlayerEntity player2 = session.getPlayer2();
        ArenaLadder ladder = session.getLadder();
        // Use full-detail capture (ability, held item, moves, types, nature)
        List<ArenaTransitionPokemonEntryPayload> player1Team =
            captureDetailedPayload(player1, ladder);
        List<ArenaTransitionPokemonEntryPayload> player2Team =
            captureDetailedPayload(player2, ladder);
        // Determine battle format so the client can show the correct
        // number of lead-Pokemon to select (1 for singles, 2 doubles, 3 triples)
        String battleTypeId = (ladder != null)
            ? ladder.getBattleTypeId()
            : "singles";

        int p1Elo = 0;
        int p2Elo = 0;
        if (ladder != null && ladder.isRanked()) {
            cobblemon.arena.stats.PlayerStats s1 = cobblemon.arena.stats.StatsManager.getInstance().getStats(player1.getUuid());
            cobblemon.arena.stats.PlayerStats s2 = cobblemon.arena.stats.StatsManager.getInstance().getStats(player2.getUuid());
            if (s1 != null) p1Elo = s1.getRankedRating(ladder.getId());
            if (s2 != null) p2Elo = s2.getRankedRating(ladder.getId());
        }

        ArenaNet.send(
            player1,
            new ArenaBattleTransitionPacket(
                player1.getName().getString(),
                player1.getUuid().toString(),
                player2.getName().getString(),
                player2.getUuid().toString(),
                player1Team,
                player2Team,
                PRE_BATTLE_TRANSITION_TICKS,
                battleTypeId,
                p1Elo,
                p2Elo
            )
        );
        ArenaNet.send(
            player2,
            new ArenaBattleTransitionPacket(
                player2.getName().getString(),
                player2.getUuid().toString(),
                player1.getName().getString(),
                player1.getUuid().toString(),
                player2Team,
                player1Team,
                PRE_BATTLE_TRANSITION_TICKS,
                battleTypeId,
                p2Elo,
                p1Elo
            )
        );
    }

    private List<ArenaTransitionPokemonEntryPayload> toTransitionPayload(
        List<ArenaSession.TeamPokemonSnapshot> snapshot
    ) {
        List<ArenaTransitionPokemonEntryPayload> payload = new ArrayList<>(
            snapshot.size()
        );
        for (ArenaSession.TeamPokemonSnapshot entry : snapshot) {
            payload.add(
                new ArenaTransitionPokemonEntryPayload(
                    entry.getSpeciesKey(),
                    entry.getSpeciesName()
                )
            );
        }
        return payload;
    }

    /**
     * Captures complete battle-relevant info for every Pokémon in the player’s
     * active party: species, ability, held item, types, moves, nature, level.
     * This data is sent to BOTH players in the transition packet so each can
     * inspect the opponent’s team before the battle begins.
     */
    private List<ArenaTransitionPokemonEntryPayload> captureDetailedPayload(
        ServerPlayerEntity player,
        ArenaLadder ladder
    ) {
        List<ArenaTransitionPokemonEntryPayload> result = new ArrayList<>();
        for (com.cobblemon.mod.common.pokemon.Pokemon pk : cobblemon.arena.ladder.ArenaPartyValidator.getPartyPokemon(
            player
        )) {
            try {
                String key = pk.getSpecies().getResourceIdentifier().toString();
                String name = pk.getSpecies().getName();

                String ability = "";
                try {
                    Object dn = pk.getAbility().getDisplayName();
                    String rawAbility =
                        dn instanceof net.minecraft.text.Text
                            ? ((net.minecraft.text.Text) dn).getString()
                            : String.valueOf(dn);
                    ability = formatDisplayName(rawAbility);
                } catch (Exception ignored) {}

                String heldItem = "";
                try {
                    net.minecraft.item.ItemStack h = pk.heldItem();
                    if (h != null && !h.isEmpty()) {
                        net.minecraft.util.Identifier registryId =
                            net.minecraft.registry.Registries.ITEM.getId(
                                h.getItem()
                            );
                        heldItem = registryId.toString();
                    }
                } catch (Exception ignored) {}

                java.util.List<String> types = new java.util.ArrayList<>();
                try {
                    types.add(pk.getSpecies().getPrimaryType().getName());
                    if (pk.getSpecies().getSecondaryType() != null) types.add(
                        pk.getSpecies().getSecondaryType().getName()
                    );
                } catch (Exception ignored) {}

                java.util.List<String> moves = new java.util.ArrayList<>();
                try {
                    pk
                        .getMoveSet()
                        .getMoves()
                        .forEach(m ->
                            moves.add(m.getDisplayName().getString())
                        );
                } catch (Exception ignored) {}

                String nature = "";
                try {
                    Object ndn = pk.getNature().getDisplayName();
                    String rawNature =
                        ndn instanceof net.minecraft.text.Text
                            ? ((net.minecraft.text.Text) ndn).getString()
                            : String.valueOf(ndn);
                    nature = formatDisplayName(rawNature);
                } catch (Exception ignored) {}

                int level = pk.getLevel();

                result.add(
                    new ArenaTransitionPokemonEntryPayload(
                        key,
                        name,
                        ability,
                        heldItem,
                        types,
                        moves,
                        nature,
                        ladder != null && ladder.getAdjustLevel() > 0 ? ladder.getAdjustLevel() : pk.getLevel()
                    )
                );
            } catch (Exception ignored) {
                // Malformed party slot — skip gracefully
            }
        }
        return result;
    }

    private UUID getFirstPartyPokemonId(ServerPlayerEntity player) {
        List<Pokemon> party = ArenaPartyValidator.getPartyPokemon(player);
        return party.isEmpty() ? null : party.get(0).getUuid();
    }

    private UUID resolveSelectedLead(
        ArenaSession session,
        ServerPlayerEntity player
    ) {
        UUID selectedLead = session.getSelectedLead(player);
        if (selectedLead == null) {
            return this.getFirstPartyPokemonId(player);
        } else {
            for (Pokemon pokemon : ArenaPartyValidator.getPartyPokemon(
                player
            )) {
                if (selectedLead.equals(pokemon.getUuid())) {
                    return selectedLead;
                }
            }

            return this.getFirstPartyPokemonId(player);
        }
    }

    /**
     * Extrai nome legível de chave de tradução do Cobblemon ou retorna o valor direto.
     * Ex: "cobblemon.ability.prankster" -> "Prankster"
     *     "cobblemon.nature.jolly"      -> "Jolly"
     *     "Swift Swim"                  -> "Swift Swim"
     */
    private static String formatDisplayName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        int lastDot = raw.lastIndexOf('.');
        String name;
        if (lastDot > 0 && lastDot < raw.length() - 1) {
            name = raw.substring(lastDot + 1);
        } else {
            return raw;
        }
        name = name.replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb
                    .append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
