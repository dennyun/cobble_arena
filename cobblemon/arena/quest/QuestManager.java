package cobblemon.arena.quest;

import cobblemon.arena.CobblemonArena;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

/**
 * Manages all quest-related state: quest definitions, per-player progress,
 * daily/weekly refresh, and reward distribution.
 *
 * <h3>Integration points</h3>
 * <ul>
 *   <li>{@code CobblemonArena.SERVER_STARTED} → {@link #initialize(MinecraftServer)}</li>
 *   <li>{@code CobblemonArena.SERVER_STOPPING} → {@link #saveProgress()}</li>
 *   <li>Battle-victory handler → {@link #onMatchCompleted}</li>
 *   <li>Claim command / UI → {@link #claimQuestReward}</li>
 * </ul>
 *
 * <h3>Quest refresh contract</h3>
 * <ul>
 *   <li>Daily quests are refreshed when {@code now - lastDailyRefreshMs > 86 400 000 ms} (24 h).</li>
 *   <li>Weekly quests are refreshed when {@code now - lastWeeklyRefreshMs > 604 800 000 ms} (7 d).</li>
 *   <li>Quest selection is deterministic: same player on the same calendar day/week always
 *       receives the same set, seeded by {@code uuid.mostBits XOR uuid.leastBits XOR epochDay}.</li>
 * </ul>
 */
public final class QuestManager {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final QuestManager INSTANCE = new QuestManager();

    public static QuestManager getInstance() {
        return INSTANCE;
    }

    private QuestManager() {}

    // ── Gson ──────────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    // ── Quest pools ───────────────────────────────────────────────────────────

    /**
     * Full daily quest pool (12 entries). Each player is assigned 3 per day,
     * chosen deterministically from this list.
     */
    static List<Quest> DAILY_QUEST_POOL;

    /**
     * Full weekly quest pool (6 entries). Each player is assigned 2 per week,
     * chosen deterministically from this list.
     */
    static List<Quest> WEEKLY_QUEST_POOL;

    static {
        // ── Daily quests ──────────────────────────────────────────────────────
        DAILY_QUEST_POOL = new ArrayList<>(QuestDataFactory.getDailyQuests());

        // ── Weekly quests ────────────────────────────────────────────────────
        WEEKLY_QUEST_POOL = new ArrayList<>(QuestDataFactory.getWeeklyQuests());
    }

    // ── Runtime state ─────────────────────────────────────────────────────────

    /**
     * Per-player progress, keyed by player UUID.
     * ConcurrentHashMap keeps insertions thread-safe; individual
     * {@link PlayerQuestProgress} mutations are performed on the main server
     * thread and therefore do not require further locking.
     */
    private final Map<UUID, PlayerQuestProgress> playerProgress =
        new ConcurrentHashMap<>();

    private File progressFile;
    private MinecraftServer server;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Initialises the quest manager for the given server instance.
     * Must be called once during {@code SERVER_STARTED}.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;

        File configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile();
        File arenaDir = new File(configDir, "cobblemon_arena");
        if (!arenaDir.exists()) {
            arenaDir.mkdirs();
        }
        this.progressFile = new File(arenaDir, "quest_progress.json");

        loadQuestDefinitions(arenaDir);
        loadProgress();
        CobblemonArena.LOGGER.info(
            "[QuestManager] Inicializado com {} registros de progresso de quests.",
            playerProgress.size()
        );
    }

    // ── JSON quest loading ─────────────────────────────────────────────────────────────────────

    /**
     * Loads quest definitions from {@code <arenaDir>/quests.json}.
     *
     * <ul>
     *   <li>If the file does <em>not</em> exist, the current hardcoded pools are
     *       serialised to JSON as a pre-populated template and used as-is.</li>
     *   <li>If the file <em>does</em> exist, its contents replace
     *       {@link #DAILY_QUEST_POOL} and {@link #WEEKLY_QUEST_POOL} for this
     *       session.</li>
     * </ul>
     *
     * @param arenaDir the {@code cobblemon_arena} directory inside the world save
     */
    private static void loadQuestDefinitions(File arenaDir) {
        File questFile = new File(arenaDir, "quests.json");

        if (!questFile.exists()) {
            // Write the current hardcoded pools as the default template.
            QuestPoolData data = new QuestPoolData();
            data.daily_quests = DAILY_QUEST_POOL.stream()
                .map(QuestManager::questToData)
                .collect(Collectors.toList());
            data.weekly_quests = WEEKLY_QUEST_POOL.stream()
                .map(QuestManager::questToData)
                .collect(Collectors.toList());
            try (FileWriter writer = new FileWriter(questFile)) {
                GSON.toJson(data, writer);
                CobblemonArena.LOGGER.info(
                    "[QuestManager] quests.json criado com {} quests diarias e {} semanais: {}",
                    data.daily_quests.size(),
                    data.weekly_quests.size(),
                    questFile.getAbsolutePath()
                );
            } catch (IOException e) {
                CobblemonArena.LOGGER.error(
                    "[QuestManager] Falha ao escrever quests.json",
                    e
                );
            }
            return; // keep using the hardcoded (now-on-disk) pools
        }

        // File exists — load quest definitions from it.
        try (FileReader reader = new FileReader(questFile)) {
            QuestPoolData data = GSON.fromJson(reader, QuestPoolData.class);
            if (data == null) {
                CobblemonArena.LOGGER.warn(
                    "[QuestManager] quests.json vazio — usando pool padrao."
                );
                return;
            }
            if (data.daily_quests != null && !data.daily_quests.isEmpty()) {
                List<Quest> loaded = new ArrayList<>();
                for (QuestData d : data.daily_quests) {
                    try {
                        loaded.add(dataToQuest(d));
                    } catch (Exception ex) {
                        CobblemonArena.LOGGER.warn(
                            "[QuestManager] Quest diaria invalida ignorada: {}",
                            ex.getMessage()
                        );
                    }
                }
                if (!loaded.isEmpty()) {
                    DAILY_QUEST_POOL.clear();
                    DAILY_QUEST_POOL.addAll(loaded);
                }
            }
            if (data.weekly_quests != null && !data.weekly_quests.isEmpty()) {
                List<Quest> loaded = new ArrayList<>();
                for (QuestData d : data.weekly_quests) {
                    try {
                        loaded.add(dataToQuest(d));
                    } catch (Exception ex) {
                        CobblemonArena.LOGGER.warn(
                            "[QuestManager] Quest semanal invalida ignorada: {}",
                            ex.getMessage()
                        );
                    }
                }
                if (!loaded.isEmpty()) {
                    WEEKLY_QUEST_POOL.clear();
                    WEEKLY_QUEST_POOL.addAll(loaded);
                }
            }
            CobblemonArena.LOGGER.info(
                "[QuestManager] quests.json carregado: {} diarias, {} semanais.",
                DAILY_QUEST_POOL.size(),
                WEEKLY_QUEST_POOL.size()
            );
        } catch (IOException e) {
            CobblemonArena.LOGGER.error(
                "[QuestManager] Falha ao ler quests.json — usando pool padrao.",
                e
            );
        }
    }

    /** Converts a live {@link Quest} into its JSON-serialisable DTO. */
    private static QuestData questToData(Quest q) {
        QuestData d = new QuestData();
        d.id = q.getId();
        d.type = q.getType().name();
        d.title = q.getTitle();
        d.description = q.getDescription();
        d.targetAmount = q.getTargetAmount();
        d.typeFilter = q.getTypeFilter();
        d.rewardDescription = q.getReward().getDescription();
        d.rewardCommands = new ArrayList<>(q.getReward().getCommands());
        d.daily = q.isDaily();
        return d;
    }

    /** Converts a JSON DTO back into a live {@link Quest}. */
    private static Quest dataToQuest(QuestData d) {
        if (d.id == null || d.id.isBlank()) {
            throw new IllegalArgumentException("Quest sem id");
        }
        QuestType type;
        try {
            type = QuestType.valueOf(
                d.type != null ? d.type.toUpperCase(java.util.Locale.ROOT) : ""
            );
        } catch (IllegalArgumentException e) {
            CobblemonArena.LOGGER.warn(
                "[QuestManager] QuestType desconhecido '{}' para quest '{}' — usando PLAY_MATCHES.",
                d.type,
                d.id
            );
            type = QuestType.PLAY_MATCHES;
        }
        return new Quest(
            d.id,
            type,
            d.title != null ? d.title : d.id,
            d.description != null ? d.description : "",
            d.targetAmount,
            d.typeFilter,
            new QuestReward(
                d.rewardDescription != null ? d.rewardDescription : "",
                d.rewardCommands != null ? d.rewardCommands : List.of()
            ),
            d.daily
        );
    }

    // ── JSON DTOs (used only for quests.json serialisation) ────────────────────────

    private static final class QuestPoolData {

        List<QuestData> daily_quests;
        List<QuestData> weekly_quests;
    }

    /** Flat representation of a single quest entry in quests.json. */
    private static final class QuestData {

        String id;
        String type; // QuestType enum name, e.g. "WIN_RANKED"
        String title;
        String description;
        int targetAmount = 1;
        String typeFilter; // optional: Pokémon type name or format substring
        String rewardDescription;
        List<String> rewardCommands;
        boolean daily; // true = daily quest, false = weekly
    }

    // ── Persistence ───────────────────────────────────────────────────────────────────────────

    private void loadProgress() {
        if (progressFile == null || !progressFile.exists()) {
            CobblemonArena.LOGGER.info(
                "[QuestManager] Nenhum arquivo de progresso encontrado — iniciando do zero."
            );
            return;
        }
        try (FileReader reader = new FileReader(progressFile)) {
            Type type = new TypeToken<
                Map<UUID, PlayerQuestProgress>
            >() {}.getType();
            Map<UUID, PlayerQuestProgress> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                playerProgress.putAll(loaded);
                CobblemonArena.LOGGER.info(
                    "[QuestManager] Progresso carregado para {} jogadores.",
                    loaded.size()
                );
            }
        } catch (IOException e) {
            CobblemonArena.LOGGER.error(
                "[QuestManager] Falha ao carregar quest_progress.json",
                e
            );
        }
    }

    /**
     * Saves all quest progress to disk synchronously.
     * Call this on {@code SERVER_STOPPING} to guarantee a final flush.
     */
    public void saveProgress() {
        if (progressFile == null) return;
        try (FileWriter writer = new FileWriter(progressFile)) {
            GSON.toJson(playerProgress, writer);
        } catch (IOException e) {
            CobblemonArena.LOGGER.error(
                "[QuestManager] Falha ao salvar quest_progress.json",
                e
            );
        }
    }

    /**
     * Saves all quest progress to disk on a background thread.
     * Use this after every in-game mutation to avoid blocking the server tick.
     */
    public void saveProgressAsync() {
        Thread t = new Thread(
            () -> {
                try {
                    saveProgress();
                } catch (Exception e) {
                    CobblemonArena.LOGGER.error(
                        "[QuestManager] Falha ao salvar progresso de forma assíncrona",
                        e
                    );
                }
            },
            "Arena-Quest-Save"
        );
        t.setDaemon(true);
        t.start();
    }

    public void syncAllPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player,
                    cobblemon.arena.network.ServerPacketHandler.buildQuestPacket(player)
                );
            } catch (Exception ignored) {}
        }
    }

    // ── Player progress ───────────────────────────────────────────────────────

    /**
     * Returns (or creates) the {@link PlayerQuestProgress} for the given player.
     * This is the primary read/write surface for quest progress data.
     */
    public PlayerQuestProgress getPlayerProgress(ServerPlayerEntity player) {
        return getOrCreateProgress(player.getUuid());
    }

    private PlayerQuestProgress getOrCreateProgress(UUID uuid) {
        return playerProgress.computeIfAbsent(uuid, k ->
            new PlayerQuestProgress()
        );
    }

    // ── Quest refresh ─────────────────────────────────────────────────────────

    /**
     * Checks whether the daily/weekly quest sets for {@code player} need
     * refreshing and, if so, assigns a new deterministic set.
     *
     * <p>This is called automatically by every public method that touches
     * active quests, so callers never need to invoke it manually.</p>
     */
    public void refreshQuestsIfNeeded(ServerPlayerEntity player) {
        PlayerQuestProgress prog = getOrCreateProgress(player.getUuid());
        long now = System.currentTimeMillis();

        // ── Daily (24 h) ──────────────────────────────────────────────────────
        if (now - prog.getLastDailyRefreshMs() > 86_400_000L) {
            List<String> newIds = pickQuestIds(
                player,
                DAILY_QUEST_POOL,
                3,
                false
            );
            prog.setActiveDailyQuestIds(newIds);
            prog.setLastDailyRefreshMs(now);
            saveProgressAsync();
            CobblemonArena.LOGGER.info(
                "[QuestManager] Quests diárias renovadas para {}: {}",
                player.getName().getString(),
                newIds
            );
        }

        // ── Weekly (7 d) ──────────────────────────────────────────────────────
        if (now - prog.getLastWeeklyRefreshMs() > 604_800_000L) {
            List<String> newIds = pickQuestIds(
                player,
                WEEKLY_QUEST_POOL,
                2,
                true
            );
            prog.setActiveWeeklyQuestIds(newIds);
            prog.setLastWeeklyRefreshMs(now);
            saveProgressAsync();
            CobblemonArena.LOGGER.info(
                "[QuestManager] Quests semanais renovadas para {}: {}",
                player.getName().getString(),
                newIds
            );
        }
    }

    /**
     * Picks {@code count} quest IDs from {@code pool} using a deterministic
     * {@link Random} seeded by the player's UUID and the current calendar period.
     *
     * <ul>
     *   <li>Daily seed: {@code uuid.mostBits XOR uuid.leastBits XOR epochDay}</li>
     *   <li>Weekly seed: {@code uuid.mostBits XOR uuid.leastBits XOR (epochDay / 7)}</li>
     * </ul>
     *
     * Because the seed is derived from immutable calendar values, the same player
     * always receives the same quests within the same day/week, even after restarts.
     *
     * @param weekly {@code true} for a weekly-period seed, {@code false} for daily
     */
    private List<String> pickQuestIds(
        ServerPlayerEntity player,
        List<Quest> pool,
        int count,
        boolean weekly
    ) {
        long epochDay = LocalDate.now().toEpochDay();
        long uuidMix =
            player.getUuid().getMostSignificantBits() ^
            player.getUuid().getLeastSignificantBits();
        long seed = weekly ? (uuidMix ^ (epochDay / 7L)) : (uuidMix ^ epochDay);

        List<Quest> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(seed));

        return shuffled
            .subList(0, Math.min(count, shuffled.size()))
            .stream()
            .map(Quest::getId)
            .collect(Collectors.toList());
    }

    // ── Active quest accessors ────────────────────────────────────────────────

    /**
     * Returns the list of active daily {@link Quest}s for {@code player},
     * triggering a refresh first if the 24 h window has elapsed.
     */
    public List<Quest> getActiveDailyQuests(ServerPlayerEntity player) {
        refreshQuestsIfNeeded(player);
        return getOrCreateProgress(player.getUuid())
            .getActiveDailyQuestIds()
            .stream()
            .map(this::findQuestById)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Returns the list of active weekly {@link Quest}s for {@code player},
     * triggering a refresh first if the 7-day window has elapsed.
     */
    public List<Quest> getActiveWeeklyQuests(ServerPlayerEntity player) {
        refreshQuestsIfNeeded(player);
        return getOrCreateProgress(player.getUuid())
            .getActiveWeeklyQuestIds()
            .stream()
            .map(this::findQuestById)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Looks up a {@link Quest} by its ID across both the daily and weekly pools.
     *
     * @return the matching {@link Quest}, or {@code null} if not found
     */
    public Quest findQuestById(String id) {
        if (id == null) return null;
        for (Quest q : DAILY_QUEST_POOL) if (id.equals(q.getId())) return q;
        for (Quest q : WEEKLY_QUEST_POOL) if (id.equals(q.getId())) return q;
        return null;
    }

    // ── Match completion ──────────────────────────────────────────────────────

    /**
     * Updates quest progress for {@code player} based on the outcome of a
     * completed arena match.
     *
     * <p>Call this for <em>both</em> the winner and the loser immediately after
     * the match result is determined (e.g. inside {@code CobblemonBattleHandler}'s
     * {@code BATTLE_VICTORY} subscriber).
     *
     * <pre>{@code
     * // winner
     * QuestManager.getInstance().onMatchCompleted(winner, true,  isRanked, isCasual, formatId, winnerTypes);
     * // loser
     * QuestManager.getInstance().onMatchCompleted(loser,  false, isRanked, isCasual, formatId, loserTypes);
     * }</pre>
     *
     * @param player       the player whose progress to update
     * @param won          {@code true} if this player won the match
     * @param isRanked     {@code true} if the match was a ranked queue match
     * @param isCasual     {@code true} if the match was a casual (non-ranked) queue match
     * @param formatId     the ladder / format identifier (e.g. {@code "ranked_doubles_lv50"});
     *                     used to satisfy format-filtered quests such as
     *                     {@code weekly_win_doubles_3}
     * @param pokemonTypes a flat list of every type name (lower-case) present in the
     *                     player's team during this match; duplicates are fine and
     *                     are ignored when checking {@link QuestType#USE_TYPE} quests
     */
    public void onMatchCompleted(
        ServerPlayerEntity player,
        boolean won,
        boolean isRanked,
        boolean isCasual,
        String formatId,
        List<String> pokemonTypes,
        int turns,
        boolean isForfeit
    ) {
        if (player == null) return;

        // Refresh first so the active-quest lists are up to date.
        refreshQuestsIfNeeded(player);

        PlayerQuestProgress prog = getOrCreateProgress(player.getUuid());

        // Collect all currently active quests in one pass.
        List<Quest> active = new ArrayList<>();
        active.addAll(getActiveDailyQuests(player));
        active.addAll(getActiveWeeklyQuests(player));

        boolean anyChanged = false;

        for (Quest quest : active) {
            // Skip quests whose reward has already been collected.
            if (prog.isClaimed(quest.getId())) continue;

            // Skip quests that were already completed before this match.
            // (Exception: WIN_STREAK on a loss still needs to reset the
            //  counter, but only while the quest is not yet complete — handled
            //  inside applyMatchToQuest.)
            boolean wasCompleted = prog.isCompleted(
                quest.getId(),
                quest.getTargetAmount()
            );
            if (wasCompleted) continue;

            boolean changed = applyMatchToQuest(
                prog,
                quest,
                won,
                isRanked,
                isCasual,
                formatId,
                pokemonTypes,
                turns,
                isForfeit
            );
            if (changed) {
                anyChanged = true;
                // Notify only if this match pushed the quest over the finish line.
                if (prog.isCompleted(quest.getId(), quest.getTargetAmount())) {
                    notifyQuestCompleted(player, quest);
                }
            }
        }

        if (anyChanged) {
            saveProgressAsync();
        }
    }

    /**
     * Applies the match result to a single quest and returns {@code true} if
     * the quest's stored state changed (progress incremented or streak reset).
     */
    private boolean applyMatchToQuest(
        PlayerQuestProgress prog,
        Quest quest,
        boolean won,
        boolean isRanked,
        boolean isCasual,
        String formatId,
        List<String> pokemonTypes,
        int turns,
        boolean isForfeit
    ) {
        return switch (quest.getType()) {
            // ── Any match counts ─────────────────────────────────────────────
            case PLAY_MATCHES -> {
                prog.addProgress(quest.getId(), 1);
                yield true;
            }
            // ── Win required ─────────────────────────────────────────────────
            case WIN_MATCHES -> {
                if (won) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            // ── Win ranked (optional format filter) ──────────────────────────
            // The typeFilter field doubles as a format-ID substring when
            // used with WIN_RANKED / PLAY_RANKED (e.g. "doubles").
            case WIN_RANKED -> {
                if (
                    won &&
                    isRanked &&
                    formatMatches(quest.getTypeFilter(), formatId)
                ) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            // ── Win streak: increment on win, reset on loss ───────────────────
            // Once the quest is completed we never enter this branch
            // (caller already skips completed quests), so resetting after
            // completion is not possible.
            case WIN_STREAK -> {
                if (won) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                } else {
                    // Only reset if progress was non-zero (avoids a no-op save).
                    if (prog.getProgress(quest.getId()) > 0) {
                        prog.setProgress(quest.getId(), 0);
                        yield true;
                    }
                    yield false;
                }
            }
            // ── Play ranked (optional format filter) ─────────────────────────
            case PLAY_RANKED -> {
                if (
                    isRanked && formatMatches(quest.getTypeFilter(), formatId)
                ) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            // ── Play casual ──────────────────────────────────────────────────
            case PLAY_CASUAL -> {
                if (isCasual) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            // ── Used a specific Pokémon type ─────────────────────────────────
            // The quest's typeFilter holds the type name (e.g. "fire").
            // pokemonTypes contains every type present in the player's team;
            // a single type match per match is sufficient.
            case USE_TYPE -> {
                String tf = quest.getTypeFilter();
                if (tf != null && !tf.isEmpty() && pokemonTypes != null) {
                    boolean usedType = pokemonTypes
                        .stream()
                        .anyMatch(t -> t.equalsIgnoreCase(tf));
                    if (usedType) {
                        prog.addProgress(quest.getId(), 1);
                        yield true;
                    }
                }
                yield false;
            }
            case WIN_WITH_ALIVE -> {
                // Not supported currently, auto complete if won
                if (won) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case WIN_FAST -> {
                if (won && turns > 0 && turns <= quest.getTargetAmount()) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case PLAY_LONG -> {
                if (turns >= quest.getTargetAmount() || turns > 20) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case WIN_BY_FORFEIT -> {
                if (won && isForfeit) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case PLAY_NO_FORFEIT -> {
                if (!isForfeit) {
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case PLAY_MONOTYPE -> {
                if (pokemonTypes != null && pokemonTypes.size() <= 2) {
                    // Approximate monotype: either 1 primary type, or all share 1 type
                    prog.addProgress(quest.getId(), 1);
                    yield true;
                }
                yield false;
            }
            case KNOCKOUT_TOTAL -> {
                // Approximate: Winner gets 3 knockouts, loser gets 1
                int estimatedKnockouts = won ? 3 : 1;
                prog.addProgress(quest.getId(), estimatedKnockouts);
                yield true;
            }
        };
    }

    /**
     * Returns {@code true} when {@code filter} is blank/null (any format is
     * acceptable) or when {@code formatId} contains {@code filter} as a
     * case-insensitive substring.
     */
    private static boolean formatMatches(String filter, String formatId) {
        if (filter == null || filter.isEmpty()) return true;
        if (formatId == null || formatId.isEmpty()) return false;
        return formatId
            .toLowerCase(java.util.Locale.ROOT)
            .contains(filter.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Sends an in-game chat notification to {@code player} informing them that
     * {@code quest} has been completed and they can claim the reward.
     */
    private static void notifyQuestCompleted(
        ServerPlayerEntity player,
        Quest quest
    ) {
        player.sendMessage(
            Text.literal(
                "§a§l✔ Quest Concluída! §r§7" +
                    quest.getTitle() +
                    " §8| §7Recompensa: §f" +
                    quest.getReward().getDescription() +
                    " §8| §7Use §f/quest claim " +
                    quest.getId() +
                    " §7para coletar."
            ),
            false
        );
        CobblemonArena.LOGGER.info(
            "[QuestManager] {} completou a quest '{}' ({}).",
            player.getName().getString(),
            quest.getId(),
            quest.getTitle()
        );
    }

    // ── Reward claiming ───────────────────────────────────────────────────────

    /**
     * Attempts to grant the reward for {@code questId} to {@code player}.
     *
     * <p>Validation order:</p>
     * <ol>
     *   <li>Quest must exist in the daily or weekly pool.</li>
     *   <li>Quest must be in the player's current active set.</li>
     *   <li>Player's progress must meet or exceed the quest's target.</li>
     *   <li>Reward must not have been claimed already.</li>
     * </ol>
     *
     * <p>Each command in {@link QuestReward#getCommands()} is executed via the
     * server's command source after replacing the {@code {player}} placeholder
     * with the player's name.</p>
     *
     * @return {@code true} if the reward was successfully granted
     */
    public boolean claimQuestReward(ServerPlayerEntity player, String questId) {
        if (player == null || questId == null) return false;

        Quest quest = findQuestById(questId);
        if (quest == null) {
            player.sendMessage(
                Text.literal("§cQuest não encontrada: §f" + questId),
                false
            );
            return false;
        }

        // Ensure the quest is currently active for this player.
        refreshQuestsIfNeeded(player);
        PlayerQuestProgress prog = getOrCreateProgress(player.getUuid());

        boolean isActive =
            prog.getActiveDailyQuestIds().contains(questId) ||
            prog.getActiveWeeklyQuestIds().contains(questId);
        if (!isActive) {
            player.sendMessage(
                Text.literal(
                    "§cEsta quest não está ativa para você no momento."
                ),
                false
            );
            return false;
        }

        if (!prog.isCompleted(questId, quest.getTargetAmount())) {
            player.sendMessage(
                Text.literal(
                    "§cVocê ainda não concluiu esta quest! §7(" +
                        prog.getProgress(questId) +
                        "/" +
                        quest.getTargetAmount() +
                        ")"
                ),
                false
            );
            return false;
        }

        if (prog.isClaimed(questId)) {
            player.sendMessage(
                Text.literal("§cVocê já coletou a recompensa desta quest!"),
                false
            );
            return false;
        }

        // Mark as claimed before executing commands to prevent double-claiming
        // if a command somehow triggers a recursive call.
        prog.markClaimed(questId);
        saveProgressAsync();

        // Execute reward commands with {player} substituted.
        MinecraftServer srv = player.getServer();
        if (srv != null) {
            String playerName = player.getName().getString();
            for (String command : quest.getReward().getCommands()) {
                String resolved = command.replace("{player}", playerName).replace("%player%", playerName);
                try {
                    srv
                        .getCommandManager()
                        .executeWithPrefix(srv.getCommandSource(), resolved);
                } catch (Exception e) {
                    CobblemonArena.LOGGER.error(
                        "[QuestManager] Erro ao executar comando de recompensa '{}' para {}: {}",
                        resolved,
                        playerName,
                        e.getMessage()
                    );
                }
            }
        }

        player.sendMessage(
            Text.literal(
                "§a§lRecompensa Coletada! §r§7" +
                    quest.getTitle() +
                    " §8→ §f" +
                    quest.getReward().getDescription()
            ),
            false
        );
        CobblemonArena.LOGGER.info(
            "[QuestManager] {} coletou a recompensa da quest '{}' ({}).",
            player.getName().getString(),
            quest.getId(),
            quest.getTitle()
        );
        return true;
    }
}
