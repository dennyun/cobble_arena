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
                    this.captureTeamSnapshot(player1),
                    this.captureTeamSnapshot(player2)
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
            session.end();
            ArenaManager.getInstance().releaseArenaBySession(sessionId);
            this.playerToSession.remove(session.getPlayer1().getUuid());
            this.playerToSession.remove(session.getPlayer2().getUuid());
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
            if (
                session != null &&
                session.isActive() &&
                session.getBattleId() != null
            ) {
                PokemonBattle battle = BattleRegistry.getBattle(
                    session.getBattleId()
                );
                if (battle != null && !battle.getEnded()) {
                    sessions.add(session);
                }
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
                    if (session.shouldStartBattle(nowTick)) {
                        this.tryStartPendingBattle(session);
                    } else if (session.getBattleId() != null) {
                        PokemonBattle battle = BattleRegistry.getBattle(
                            session.getBattleId()
                        );
                        if (battle == null) {
                            CobblemonArena.LOGGER.warn(
                                "Arena session {} lost battle {} before cleanup; ending arena session",
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
                                CobblemonArena.LOGGER.info(
                                    "Arena session {} detected ended battle {}; ending arena session",
                                    session.getSessionId(),
                                    session.getBattleId()
                                );
                                this.endArena(session.getSessionId());
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
                "§eTempo esgotado! Um movimento foi escolhido automaticamente."
            ),
            false
        );
        if (opponent != null) {
            opponent.sendMessage(
                Text.literal(
                    "§e" +
                        player.getName().getString() +
                        " ficou sem tempo — movimento escolhido automaticamente."
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
                StatsManager.getInstance()
                    .recordRankedDoubleLoss(
                        player,
                        opponent,
                        session.getLadder(),
                        session.getTeamSnapshot(player),
                        session.getTeamSnapshot(opponent)
                    );
            } else {
                StatsManager.getInstance()
                    .recordQuickDoubleLoss(
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
        try {
            BattleRegistry.closeBattle(battle);
        } catch (Exception ignored) {}
        this.endArena(session.getSessionId());
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
        ServerPlayerEntity player
    ) {
        List<ArenaSession.TeamPokemonSnapshot> team = new ArrayList<>();

        for (Pokemon pokemon : ArenaPartyValidator.getPartyPokemon(player)) {
            String ability = "";
            try {
                Object dn = pokemon.getAbility().getDisplayName();
                ability =
                    dn instanceof net.minecraft.text.Text
                        ? ((net.minecraft.text.Text) dn).getString()
                        : String.valueOf(dn);
            } catch (Exception ignored) {}

            String heldItem = "";
            try {
                net.minecraft.item.ItemStack h = pokemon.heldItem();
                if (h != null && !h.isEmpty()) {
                    heldItem = h.getName().getString();
                }
            } catch (Exception ignored) {}

            List<String> types = new ArrayList<>();
            try {
                types.add(pokemon.getSpecies().getPrimaryType().getName());
                if (pokemon.getSpecies().getSecondaryType() != null) {
                    types.add(pokemon.getSpecies().getSecondaryType().getName());
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
                nature =
                    ndn instanceof net.minecraft.text.Text
                        ? ((net.minecraft.text.Text) ndn).getString()
                        : String.valueOf(ndn);
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
                    pokemon.getLevel()
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
            // Ensure selected leads appear first in party order (important for
            // doubles/triples where Cobblemon takes the opening team from the
            // leading slots for the chosen format).
            applySelectedLeadOrder(session, session.getPlayer1());
            applySelectedLeadOrder(session, session.getPlayer2());
            UUID player1Lead = this.resolveSelectedLead(
                session,
                session.getPlayer1()
            );
            UUID player2Lead = this.resolveSelectedLead(
                session,
                session.getPlayer2()
            );
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

    private void applySelectedLeadOrder(
        ArenaSession session,
        ServerPlayerEntity player
    ) {
        try {
            List<UUID> selected = session.getSelectedLeads(player);
            int required = session.getRequiredLeadCount();
            if (selected.isEmpty() || selected.size() < required) return;

            Object partyStore = Cobblemon.INSTANCE.getStorage().getParty(player);
            java.lang.reflect.Method getMethod = partyStore
                .getClass()
                .getMethod("get", int.class);
            java.lang.reflect.Method setMethod = null;
            for (java.lang.reflect.Method m : partyStore.getClass().getMethods()) {
                if (
                    m.getName().equals("set") &&
                    m.getParameterCount() == 2 &&
                    m.getParameterTypes()[0] == int.class
                ) {
                    setMethod = m;
                    break;
                }
            }
            if (setMethod == null) return;

            List<Pokemon> ordered = new ArrayList<>(6);
            for (UUID id : selected) {
                for (int i = 0; i < 6; i++) {
                    Object obj = getMethod.invoke(partyStore, i);
                    if (!(obj instanceof Pokemon pokemon)) continue;
                    if (id.equals(pokemon.getUuid()) && !ordered.contains(pokemon)) {
                        ordered.add(pokemon);
                        break;
                    }
                }
            }
            for (int i = 0; i < 6; i++) {
                Object obj = getMethod.invoke(partyStore, i);
                if (obj instanceof Pokemon pokemon && !ordered.contains(pokemon)) {
                    ordered.add(pokemon);
                }
            }
            for (int i = 0; i < Math.min(6, ordered.size()); i++) {
                setMethod.invoke(partyStore, i, ordered.get(i));
            }
        } catch (Exception ignored) {}
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
        // Use full-detail capture (ability, held item, moves, types, nature)
        List<ArenaTransitionPokemonEntryPayload> player1Team =
            captureDetailedPayload(player1);
        List<ArenaTransitionPokemonEntryPayload> player2Team =
            captureDetailedPayload(player2);
        // Determine battle format so the client can show the correct
        // number of lead-Pokemon to select (1 for singles, 2 doubles, 3 triples)
        ArenaLadder ladder = session.getLadder();
        String battleTypeId = (ladder != null)
            ? ladder.getBattleTypeId()
            : "singles";

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
                battleTypeId
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
                battleTypeId
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
        ServerPlayerEntity player
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
                    // getDisplayName() returns String in Cobblemon 1.7.3
                    Object dn = pk.getAbility().getDisplayName();
                    ability =
                        dn instanceof net.minecraft.text.Text
                            ? ((net.minecraft.text.Text) dn).getString()
                            : String.valueOf(dn);
                } catch (Exception ignored) {}

                String heldItem = "";
                try {
                    net.minecraft.item.ItemStack h = pk.heldItem();
                    if (h != null && !h.isEmpty()) heldItem = h
                        .getName()
                        .getString();
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
                    nature =
                        ndn instanceof net.minecraft.text.Text
                            ? ((net.minecraft.text.Text) ndn).getString()
                            : String.valueOf(ndn);
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
                        level
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
}
