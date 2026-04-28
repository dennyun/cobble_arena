package cobblemon.arena.ladder;

import cobblemon.arena.config.BannedPokemonConfig;
import com.cobblemon.mod.common.battles.BattleFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArenaLadder {

    private static final int DEFAULT_TEAM_SIZE = 6;
    private static final ArenaLadder QUICK_SINGLES_LV50 = quickPreset(
        "quick_singles_lv50",
        "Fila Rápida",
        "Singles Rápido Nv. 50",
        "singles",
        50,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_SINGLES_LV100 = quickPreset(
        "quick_singles_lv100",
        "Fila Rápida",
        "Singles Rápido Nv. 100",
        "singles",
        100,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_SINGLES_LV50_RESTRICTED =
        quickPreset(
            "quick_singles_lv50_restricted",
            "Fila Rápida",
            "Singles Rápido Nv. 50 Restrito",
            "singles",
            50,
            true,
            true,
            true
        );
    private static final ArenaLadder QUICK_SINGLES_LV100_RESTRICTED =
        quickPreset(
            "quick_singles_lv100_restricted",
            "Fila Rápida",
            "Singles Rápido Nv. 100 Restrito",
            "singles",
            100,
            true,
            true,
            true
        );
    private static final ArenaLadder QUICK_DOUBLES_LV50 = quickPreset(
        "quick_doubles_lv50",
        "Fila Rápida",
        "Duplas Rápido Nv. 50",
        "doubles",
        50,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_DOUBLES_LV100 = quickPreset(
        "quick_doubles_lv100",
        "Fila Rápida",
        "Duplas Rápido Nv. 100",
        "doubles",
        100,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_DOUBLES_LV50_RESTRICTED =
        quickPreset(
            "quick_doubles_lv50_restricted",
            "Fila Rápida",
            "Duplas Rápido Nv. 50 Restrito",
            "doubles",
            50,
            true,
            true,
            true
        );
    private static final ArenaLadder QUICK_DOUBLES_LV100_RESTRICTED =
        quickPreset(
            "quick_doubles_lv100_restricted",
            "Fila Rápida",
            "Duplas Rápido Nv. 100 Restrito",
            "doubles",
            100,
            true,
            true,
            true
        );
    private static final ArenaLadder QUICK_TRIPLES_LV50 = quickPreset(
        "quick_triples_lv50",
        "Fila Rápida",
        "Triplas Rápido Nv. 50",
        "triples",
        50,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_TRIPLES_LV100 = quickPreset(
        "quick_triples_lv100",
        "Fila Rápida",
        "Triplas Rápido Nv. 100",
        "triples",
        100,
        false,
        true,
        true
    );
    private static final ArenaLadder QUICK_TRIPLES_LV50_RESTRICTED =
        quickPreset(
            "quick_triples_lv50_restricted",
            "Fila Rápida",
            "Triplas Rápido Nv. 50 Restrito",
            "triples",
            50,
            true,
            true,
            true
        );
    private static final ArenaLadder QUICK_TRIPLES_LV100_RESTRICTED =
        quickPreset(
            "quick_triples_lv100_restricted",
            "Fila Rápida",
            "Triplas Rápido Nv. 100 Restrito",
            "triples",
            100,
            true,
            true,
            true
        );
    private static final List<ArenaLadder> QUICK_LADDERS = List.of(
        QUICK_SINGLES_LV50,
        QUICK_SINGLES_LV100,
        QUICK_SINGLES_LV50_RESTRICTED,
        QUICK_SINGLES_LV100_RESTRICTED,
        QUICK_DOUBLES_LV50,
        QUICK_DOUBLES_LV100,
        QUICK_DOUBLES_LV50_RESTRICTED,
        QUICK_DOUBLES_LV100_RESTRICTED,
        QUICK_TRIPLES_LV50,
        QUICK_TRIPLES_LV100,
        QUICK_TRIPLES_LV50_RESTRICTED,
        QUICK_TRIPLES_LV100_RESTRICTED
    );
    // ── Lista de lendários banidos no modo Ranqueado (Reg F) ──────────────────
    // Quando allowRestrictedPokemon = false, estes Pokémon não são permitidos.
    // A lista é carregada de <world>/cobblemon_arena/banned_pokemon.json via
    // BannedPokemonConfig (que deve ser inicializado antes desta classe).
    private static final Set<String> RESTRICTED_LEGENDARY_BAN_LIST =
        BannedPokemonConfig.getRestrictedList();

    private static final java.util.LinkedHashSet<String> VGC_ITEM_BANS =
        new java.util.LinkedHashSet<>(
            java.util.Arrays.asList("kingsrock", "razorfang")
        );

    // ── Presets Casual/Fila Rápida com regras VGC ─────────────────────────────
    private static final ArenaLadder CASUAL_SINGLES_LV50 = vgcQuickPreset(
        "casual_singles_lv50",
        "Casual Singles Nv. 50",
        "singles",
        50,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_SINGLES_LV50_LEG = vgcQuickPreset(
        "casual_singles_lv50_leg",
        "Casual Singles Nv. 50 (Leg.)",
        "singles",
        50,
        true,
        List.of()
    );
    private static final ArenaLadder CASUAL_SINGLES_LV100 = vgcQuickPreset(
        "casual_singles_lv100",
        "Casual Singles Nv. 100",
        "singles",
        100,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_SINGLES_LV100_LEG = vgcQuickPreset(
        "casual_singles_lv100_leg",
        "Casual Singles Nv. 100 (Leg.)",
        "singles",
        100,
        true,
        List.of()
    );

    private static final ArenaLadder CASUAL_DOUBLES_LV50 = vgcQuickPreset(
        "casual_doubles_lv50",
        "Casual Duplas Nv. 50",
        "doubles",
        50,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_DOUBLES_LV50_LEG = vgcQuickPreset(
        "casual_doubles_lv50_leg",
        "Casual Duplas Nv. 50 (Leg.)",
        "doubles",
        50,
        true,
        List.of()
    );
    private static final ArenaLadder CASUAL_DOUBLES_LV100 = vgcQuickPreset(
        "casual_doubles_lv100",
        "Casual Duplas Nv. 100",
        "doubles",
        100,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_DOUBLES_LV100_LEG = vgcQuickPreset(
        "casual_doubles_lv100_leg",
        "Casual Duplas Nv. 100 (Leg.)",
        "doubles",
        100,
        true,
        List.of()
    );

    private static final ArenaLadder CASUAL_TRIPLES_LV50 = vgcQuickPreset(
        "casual_triples_lv50",
        "Casual Triplas Nv. 50",
        "triples",
        50,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_TRIPLES_LV50_LEG = vgcQuickPreset(
        "casual_triples_lv50_leg",
        "Casual Triplas Nv. 50 (Leg.)",
        "triples",
        50,
        true,
        List.of()
    );
    private static final ArenaLadder CASUAL_TRIPLES_LV100 = vgcQuickPreset(
        "casual_triples_lv100",
        "Casual Triplas Nv. 100",
        "triples",
        100,
        false,
        List.of()
    );
    private static final ArenaLadder CASUAL_TRIPLES_LV100_LEG = vgcQuickPreset(
        "casual_triples_lv100_leg",
        "Casual Triplas Nv. 100 (Leg.)",
        "triples",
        100,
        true,
        List.of()
    );

    private static final ArenaLadder CASUAL_MONOTYPE_LV50 = vgcQuickPreset(
        "casual_monotype_lv50",
        "Casual Monotype Nv. 50",
        "singles",
        50,
        false,
        List.of("Same Type Clause")
    );
    private static final ArenaLadder CASUAL_MONOTYPE_LV50_LEG = vgcQuickPreset(
        "casual_monotype_lv50_leg",
        "Casual Monotype Nv. 50 (Leg.)",
        "singles",
        50,
        true,
        List.of("Same Type Clause")
    );
    private static final ArenaLadder CASUAL_MONOTYPE_LV100 = vgcQuickPreset(
        "casual_monotype_lv100",
        "Casual Monotype Nv. 100",
        "singles",
        100,
        false,
        List.of("Same Type Clause")
    );
    private static final ArenaLadder CASUAL_MONOTYPE_LV100_LEG = vgcQuickPreset(
        "casual_monotype_lv100_leg",
        "Casual Monotype Nv. 100 (Leg.)",
        "singles",
        100,
        true,
        List.of("Same Type Clause")
    );

    private static final List<ArenaLadder> CASUAL_LADDERS = List.of(
        CASUAL_SINGLES_LV50,
        CASUAL_SINGLES_LV50_LEG,
        CASUAL_SINGLES_LV100,
        CASUAL_SINGLES_LV100_LEG,
        CASUAL_DOUBLES_LV50,
        CASUAL_DOUBLES_LV50_LEG,
        CASUAL_DOUBLES_LV100,
        CASUAL_DOUBLES_LV100_LEG,
        CASUAL_TRIPLES_LV50,
        CASUAL_TRIPLES_LV50_LEG,
        CASUAL_TRIPLES_LV100,
        CASUAL_TRIPLES_LV100_LEG,
        CASUAL_MONOTYPE_LV50,
        CASUAL_MONOTYPE_LV50_LEG,
        CASUAL_MONOTYPE_LV100,
        CASUAL_MONOTYPE_LV100_LEG
    );

    // ── Presets Ranqueados ─────────────────────────────────────────────────────
    // Singles
    private static final ArenaLadder RANKED_SINGLES_LV50 = rankedPreset(
        "ranked_singles_lv50",
        "Ranqueado Singles Nv. 50",
        "singles",
        50,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_SINGLES_LV50_LEG = rankedPreset(
        "ranked_singles_lv50_leg",
        "Ranqueado Singles Nv. 50 (Leg.)",
        "singles",
        50,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_SINGLES_LV100 = rankedPreset(
        "ranked_singles_lv100",
        "Ranqueado Singles Nv. 100",
        "singles",
        100,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_SINGLES_LV100_LEG = rankedPreset(
        "ranked_singles_lv100_leg",
        "Ranqueado Singles Nv. 100 (Leg.)",
        "singles",
        100,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    // Duplas
    private static final ArenaLadder RANKED_DOUBLES_LV50 = rankedPreset(
        "ranked_doubles_lv50",
        "Ranqueado Duplas Nv. 50",
        "doubles",
        50,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_DOUBLES_LV50_LEG = rankedPreset(
        "ranked_doubles_lv50_leg",
        "Ranqueado Duplas Nv. 50 (Leg.)",
        "doubles",
        50,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_DOUBLES_LV100 = rankedPreset(
        "ranked_doubles_lv100",
        "Ranqueado Duplas Nv. 100",
        "doubles",
        100,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_DOUBLES_LV100_LEG = rankedPreset(
        "ranked_doubles_lv100_leg",
        "Ranqueado Duplas Nv. 100 (Leg.)",
        "doubles",
        100,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    // Triplas
    private static final ArenaLadder RANKED_TRIPLES_LV50 = rankedPreset(
        "ranked_triples_lv50",
        "Ranqueado Triplas Nv. 50",
        "triples",
        50,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_TRIPLES_LV50_LEG = rankedPreset(
        "ranked_triples_lv50_leg",
        "Ranqueado Triplas Nv. 50 (Leg.)",
        "triples",
        50,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_TRIPLES_LV100 = rankedPreset(
        "ranked_triples_lv100",
        "Ranqueado Triplas Nv. 100",
        "triples",
        100,
        false,
        true,
        true,
        new java.util.ArrayList<>(RESTRICTED_LEGENDARY_BAN_LIST),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    private static final ArenaLadder RANKED_TRIPLES_LV100_LEG = rankedPreset(
        "ranked_triples_lv100_leg",
        "Ranqueado Triplas Nv. 100 (Leg.)",
        "triples",
        100,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );
    // VGC (Duplas Nv. 50 — regras do Campeonato Mundial)
    private static final ArenaLadder RANKED_VGC = rankedPreset(
        "ranked_vgc",
        "Ranqueado VGC Nv. 50",
        "doubles",
        50,
        true,
        true,
        true,
        List.of(),
        new java.util.ArrayList<>(VGC_ITEM_BANS)
    );

    private static final List<ArenaLadder> RANKED_PRESETS = List.of(
        RANKED_SINGLES_LV50,
        RANKED_SINGLES_LV50_LEG,
        RANKED_SINGLES_LV100,
        RANKED_SINGLES_LV100_LEG,
        RANKED_DOUBLES_LV50,
        RANKED_DOUBLES_LV50_LEG,
        RANKED_DOUBLES_LV100,
        RANKED_DOUBLES_LV100_LEG,
        RANKED_TRIPLES_LV50,
        RANKED_TRIPLES_LV50_LEG,
        RANKED_TRIPLES_LV100,
        RANKED_TRIPLES_LV100_LEG,
        RANKED_VGC
    );

    private static final ArenaLadder FALLBACK_RANKED = rankedPreset(
        "ranked_custom_1",
        "Ranqueado Singles Nv. 50",
        "singles",
        50,
        false,
        true,
        true,
        List.of(),
        List.of()
    );
    private static List<ArenaLadder> activeRankedLadders = new ArrayList<>(
        RANKED_PRESETS
    );
    private final String id;
    private final String queueLabel;
    private final String displayName;
    private final String description;
    private final boolean ranked;
    private final String battleTypeId;
    private final int requiredTeamSize;
    private final int adjustLevel;
    private final boolean allowRestrictedPokemon;
    private final boolean enforceSpeciesClause;
    private final boolean enforceItemClause;
    private final Set<String> bannedSpeciesKeys;
    private final Set<String> bannedItemKeys;
    private final Set<String> bannedAbilityKeys;
    private final Set<String> bannedMoveKeys;
    private final Set<String> bannedTierKeys;
    private final Set<String> showdownRules;

    private ArenaLadder(
        String id,
        String queueLabel,
        String displayName,
        String description,
        boolean ranked,
        String battleTypeId,
        int requiredTeamSize,
        int adjustLevel,
        boolean allowRestrictedPokemon,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        Set<String> bannedSpeciesKeys,
        Set<String> bannedItemKeys,
        Set<String> bannedAbilityKeys,
        Set<String> bannedMoveKeys,
        Set<String> bannedTierKeys,
        Set<String> showdownRules
    ) {
        this.id = normalizeId(id);
        this.queueLabel =
            queueLabel != null && !queueLabel.isBlank()
                ? queueLabel
                : (ranked ? "Fila Ranqueada" : "Fila Rápida");
        this.displayName =
            displayName != null && !displayName.isBlank()
                ? displayName.trim()
                : (ranked ? "Ladder Ranqueada" : "Fila Rápida");
        this.description =
            description != null && !description.isBlank()
                ? description
                : buildDescription(
                      ranked,
                      battleTypeId,
                      adjustLevel,
                      allowRestrictedPokemon,
                      enforceSpeciesClause,
                      enforceItemClause,
                      bannedSpeciesKeys,
                      bannedItemKeys,
                      bannedAbilityKeys,
                      bannedMoveKeys,
                      bannedTierKeys
                  );
        this.ranked = ranked;
        this.battleTypeId = normalizeFormat(battleTypeId);
        this.requiredTeamSize = requiredTeamSize <= 0 ? 6 : requiredTeamSize;
        this.adjustLevel = adjustLevel > 0 ? adjustLevel : 50;
        this.allowRestrictedPokemon = allowRestrictedPokemon;
        this.enforceSpeciesClause = enforceSpeciesClause;
        this.enforceItemClause = enforceItemClause;
        this.bannedSpeciesKeys = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                bannedSpeciesKeys == null ? Set.of() : bannedSpeciesKeys
            )
        );
        this.bannedItemKeys = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                bannedItemKeys == null ? Set.of() : bannedItemKeys
            )
        );
        this.bannedAbilityKeys = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                bannedAbilityKeys == null ? Set.of() : bannedAbilityKeys
            )
        );
        this.bannedMoveKeys = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                bannedMoveKeys == null ? Set.of() : bannedMoveKeys
            )
        );
        this.bannedTierKeys = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                bannedTierKeys == null ? Set.of() : bannedTierKeys
            )
        );
        this.showdownRules = Collections.unmodifiableSet(
            new LinkedHashSet<>(
                showdownRules == null ? Set.of() : showdownRules
            )
        );
    }

    public static synchronized void setActiveRankedLadders(
        List<ArenaLadder> ladders
    ) {
        List<ArenaLadder> normalized = new ArrayList<>();
        if (ladders != null) {
            for (ArenaLadder ladder : ladders) {
                if (ladder != null && ladder.isRanked()) {
                    normalized.add(ladder);
                }
            }
        }

        activeRankedLadders = normalized.isEmpty()
            ? new ArrayList<>(RANKED_PRESETS)
            : List.copyOf(normalized);
    }

    public static synchronized List<ArenaLadder> getActiveRankedLadders() {
        return List.copyOf(activeRankedLadders);
    }

    public static List<ArenaLadder> getQuickPresets() {
        return QUICK_LADDERS;
    }

    public static ArenaLadder[] values() {
        java.util.LinkedHashSet<ArenaLadder> all =
            new java.util.LinkedHashSet<>();
        all.addAll(CASUAL_LADDERS);
        all.addAll(RANKED_PRESETS);
        // Keep active ranked ladders too for backward compat
        all.addAll(getActiveRankedLadders());
        return all.toArray(new ArenaLadder[0]);
    }

    public String getId() {
        return this.id;
    }

    public String getQueueLabel() {
        return this.queueLabel;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isRanked() {
        return this.ranked;
    }

    public boolean isQuick() {
        return !this.ranked;
    }

    public String getBattleTypeId() {
        return this.battleTypeId;
    }

    public String getBattleTypeLabel() {
        String var1 = this.battleTypeId;

        return switch (var1) {
            case "doubles" -> "Duplas";
            case "triples" -> "Triplas";
            default -> "Singles";
        };
    }

    public int getRequiredTeamSize() {
        return this.requiredTeamSize;
    }

    public int getAdjustLevel() {
        return this.adjustLevel;
    }

    public boolean allowsRestrictedPokemon() {
        return this.allowRestrictedPokemon;
    }

    public boolean enforcesSpeciesClause() {
        return this.enforceSpeciesClause;
    }

    public boolean enforcesItemClause() {
        return this.enforceItemClause;
    }

    public Set<String> getBannedSpeciesKeys() {
        return this.bannedSpeciesKeys;
    }

    public Set<String> getBannedItemKeys() {
        return this.bannedItemKeys;
    }

    public Set<String> getBannedAbilityKeys() {
        return this.bannedAbilityKeys;
    }

    public Set<String> getBannedMoveKeys() {
        return this.bannedMoveKeys;
    }

    public Set<String> getBannedTierKeys() {
        return this.bannedTierKeys;
    }

    public Set<String> getShowdownRuleKeys() {
        return this.showdownRules;
    }

    public boolean hasShowdownRule(String rule) {
        return rule != null && !rule.isBlank()
            ? this.showdownRules.contains(rule.trim())
            : false;
    }

    public String getRulesSummary() {
        List<String> parts = new ArrayList<>();
        parts.add(this.requiredTeamSize + " Pokémon");
        parts.add("Auto Nv. " + this.adjustLevel);
        parts.add(this.getBattleTypeLabel());
        if (this.enforceSpeciesClause) {
            parts.add("Cláusula de Espécie");
        }

        if (this.enforceItemClause) {
            parts.add("Cláusula de Item");
        }

        if (this.bannedTierKeys.isEmpty()) {
            parts.add(
                this.allowRestrictedPokemon ? "Restrito OK" : "Sem Restrito"
            );
        } else {
            parts.add("Regras de Tier");
        }

        if (!this.bannedSpeciesKeys.isEmpty()) {
            parts.add("Ban Espécie " + this.bannedSpeciesKeys.size());
        }

        if (!this.bannedItemKeys.isEmpty()) {
            parts.add("Ban Item " + this.bannedItemKeys.size());
        }

        if (!this.bannedAbilityKeys.isEmpty()) {
            parts.add("Ban Hab. " + this.bannedAbilityKeys.size());
        }

        if (!this.bannedMoveKeys.isEmpty()) {
            parts.add("Ban Movimento " + this.bannedMoveKeys.size());
        }

        if (!this.bannedTierKeys.isEmpty()) {
            parts.add("Ban Tier " + this.bannedTierKeys.size());
        }

        return String.join(" | ", parts);
    }

    public List<String> getRuleLines() {
        List<String> lines = new ArrayList<>();
        lines.add(
            this.getBattleTypeLabel() +
                " | Time de " +
                this.requiredTeamSize +
                " | Nível auto " +
                this.adjustLevel
        );
        lines.add(
            (this.enforceSpeciesClause
                    ? "Cláusula de Espécie"
                    : "Sem Cláusula de Espécie") +
                " | " +
                (this.enforceItemClause
                    ? "Cláusula de Item"
                    : "Sem Cláusula de Item") +
                " | " +
                (this.bannedTierKeys.isEmpty()
                    ? (this.allowRestrictedPokemon
                          ? "Restrito Permitido"
                          : "Restrito Bloqueado")
                    : "Banimentos por Tier")
        );
        if (!this.bannedSpeciesKeys.isEmpty()) {
            lines.add(
                "Espécies banidas: " + joinPreview(this.bannedSpeciesKeys)
            );
        }

        if (!this.bannedItemKeys.isEmpty()) {
            lines.add("Itens banidos: " + joinPreview(this.bannedItemKeys));
        }

        if (!this.bannedAbilityKeys.isEmpty()) {
            lines.add(
                "Habilidades banidas: " + joinPreview(this.bannedAbilityKeys)
            );
        }

        if (!this.bannedMoveKeys.isEmpty()) {
            lines.add(
                "Movimentos banidos: " + joinPreview(this.bannedMoveKeys)
            );
        }

        if (!this.bannedTierKeys.isEmpty()) {
            lines.add(
                "Tiers banidos: " +
                    this.bannedTierKeys.stream()
                        .map(ArenaRankedPreset::formatTierName)
                        .collect(Collectors.joining(", "))
            );
        }

        return lines;
    }

    public BattleFormat createBattleFormat() {
        String battleType = this.battleTypeId;

        BattleFormat base = switch (battleType) {
            case "doubles" -> BattleFormat.Companion.getGEN_9_DOUBLES();
            case "triples" -> BattleFormat.Companion.getGEN_9_TRIPLES();
            default -> BattleFormat.Companion.getGEN_9_SINGLES();
        };
        Set<String> rules = new LinkedHashSet<>(base.getRuleSet());
        rules.addAll(this.showdownRules);
        if (!this.showdownRules.isEmpty()) {
            rules.remove("Team Preview");
            rules.add("!Team Preview");
        }

        return base.copy(
            base.getMod(),
            base.getBattleType(),
            rules,
            base.getGen(),
            this.adjustLevel
        );
    }

    public static ArenaLadder byId(String id) {
        if (id != null && !id.isBlank()) {
            String normalized = normalizeId(id);

            for (ArenaLadder ladder : values()) {
                if (ladder.id.equals(normalized)) {
                    return ladder;
                }
            }

            // Backward compat: check legacy QUICK_LADDERS not exposed in values()
            for (ArenaLadder ladder : QUICK_LADDERS) {
                if (ladder.id.equals(normalized)) {
                    return ladder;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();

        for (ArenaLadder ladder : values()) {
            ids.add(ladder.id);
        }

        return ids;
    }

    public static ArenaLadder defaultQuick() {
        return QUICK_SINGLES_LV50;
    }

    public static ArenaLadder defaultRanked() {
        List<ArenaLadder> ranked = getActiveRankedLadders();
        return ranked.isEmpty() ? FALLBACK_RANKED : ranked.get(0);
    }

    public static ArenaLadder rankedPreset(String format, String level) {
        return rankedPreset(
            "ranked_default",
            buildRankedDisplayName(format, level, "Ranked"),
            format,
            parseLevel(level),
            false,
            true,
            true,
            List.of(),
            List.of()
        );
    }

    public static ArenaLadder rankedPreset(
        String id,
        String displayName,
        String format,
        int level,
        boolean allowRestricted,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        List<String> bannedSpecies,
        List<String> bannedItems
    ) {
        return new ArenaLadder(
            id,
            "Ranked Match",
            displayName,
            null,
            true,
            format,
            6,
            level,
            allowRestricted,
            enforceSpeciesClause,
            enforceItemClause,
            normalizeList(bannedSpecies, true),
            normalizeList(bannedItems, false),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of()
        );
    }

    public static ArenaLadder rankedPreset(
        String id,
        String displayName,
        String description,
        String format,
        int level,
        boolean allowRestricted,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        List<String> bannedSpecies,
        List<String> bannedItems,
        List<String> bannedAbilities,
        List<String> bannedMoves,
        List<String> bannedTiers,
        List<String> showdownRules
    ) {
        return new ArenaLadder(
            id,
            "Ranked Match",
            displayName,
            description,
            true,
            format,
            6,
            level,
            allowRestricted,
            enforceSpeciesClause,
            enforceItemClause,
            normalizeList(bannedSpecies, true),
            normalizeList(bannedItems, false),
            normalizeRuleList(bannedAbilities),
            normalizeRuleList(bannedMoves),
            normalizeRuleList(bannedTiers),
            normalizeShowdownRules(showdownRules)
        );
    }

    public static ArenaLadder quickPreset(
        String format,
        String level,
        boolean allowRestricted
    ) {
        String normalizedFormat = normalizeFormat(format);
        boolean level100 = "100".equals(level);

        return switch (normalizedFormat) {
            case "doubles" -> allowRestricted
                ? (level100
                      ? QUICK_DOUBLES_LV100_RESTRICTED
                      : QUICK_DOUBLES_LV50_RESTRICTED)
                : (level100 ? QUICK_DOUBLES_LV100 : QUICK_DOUBLES_LV50);
            case "triples" -> allowRestricted
                ? (level100
                      ? QUICK_TRIPLES_LV100_RESTRICTED
                      : QUICK_TRIPLES_LV50_RESTRICTED)
                : (level100 ? QUICK_TRIPLES_LV100 : QUICK_TRIPLES_LV50);
            default -> allowRestricted
                ? (level100
                      ? QUICK_SINGLES_LV100_RESTRICTED
                      : QUICK_SINGLES_LV50_RESTRICTED)
                : (level100 ? QUICK_SINGLES_LV100 : QUICK_SINGLES_LV50);
        };
    }

    /**
     * Resolve o preset ranqueado com base nas seleções do jogador na tela principal.
     * Equivale a quickPreset mas para o modo ranqueado (ELO ativo).
     */
    public static ArenaLadder rankedPresetByChoice(
        String format,
        String level,
        boolean allowLegendaries
    ) {
        String norm = normalizeFormat(format);
        // VGC is not a separate ranked format — all ranked formats follow VGC rules
        boolean level100 = "100".equals(level);
        return switch (norm) {
            case "doubles" -> allowLegendaries
                ? (level100
                      ? RANKED_DOUBLES_LV100_LEG
                      : RANKED_DOUBLES_LV50_LEG)
                : (level100 ? RANKED_DOUBLES_LV100 : RANKED_DOUBLES_LV50);
            case "triples" -> allowLegendaries
                ? (level100
                      ? RANKED_TRIPLES_LV100_LEG
                      : RANKED_TRIPLES_LV50_LEG)
                : (level100 ? RANKED_TRIPLES_LV100 : RANKED_TRIPLES_LV50);
            default -> allowLegendaries
                ? (level100
                      ? RANKED_SINGLES_LV100_LEG
                      : RANKED_SINGLES_LV50_LEG)
                : (level100 ? RANKED_SINGLES_LV100 : RANKED_SINGLES_LV50);
        };
    }

    /**
     * Resolve o preset casual/rápido com regras VGC aplicadas.
     * Suporta: Singles, Duplas, Triplas, Monotype (normalizado via normalizeFormat).
     */
    public static ArenaLadder vgcPresetByChoice(
        String format,
        String level,
        boolean allowLegendaries
    ) {
        String norm = normalizeFormat(format);
        boolean lv100 = "100".equals(level);
        return switch (norm) {
            case "doubles" -> allowLegendaries
                ? (lv100 ? CASUAL_DOUBLES_LV100_LEG : CASUAL_DOUBLES_LV50_LEG)
                : (lv100 ? CASUAL_DOUBLES_LV100 : CASUAL_DOUBLES_LV50);
            case "triples" -> allowLegendaries
                ? (lv100 ? CASUAL_TRIPLES_LV100_LEG : CASUAL_TRIPLES_LV50_LEG)
                : (lv100 ? CASUAL_TRIPLES_LV100 : CASUAL_TRIPLES_LV50);
            case "monotype" -> allowLegendaries
                ? (lv100 ? CASUAL_MONOTYPE_LV100_LEG : CASUAL_MONOTYPE_LV50_LEG)
                : (lv100 ? CASUAL_MONOTYPE_LV100 : CASUAL_MONOTYPE_LV50);
            default -> allowLegendaries
                ? (lv100 ? CASUAL_SINGLES_LV100_LEG : CASUAL_SINGLES_LV50_LEG)
                : (lv100 ? CASUAL_SINGLES_LV100 : CASUAL_SINGLES_LV50);
        };
    }

    private static ArenaLadder quickPreset(
        String id,
        String queueLabel,
        String displayName,
        String format,
        int level,
        boolean allowRestricted,
        boolean enforceSpeciesClause,
        boolean enforceItemClause
    ) {
        return new ArenaLadder(
            id,
            queueLabel,
            displayName,
            null,
            false,
            format,
            6,
            level,
            allowRestricted,
            enforceSpeciesClause,
            enforceItemClause,
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of()
        );
    }

    /**
     * Cria um preset de fila rápida com regras VGC aplicadas:
     * Species Clause, Item Clause, ban King's Rock + Razor Fang.
     * Se allowLegendaries=false, inclui a RESTRICTED_LEGENDARY_BAN_LIST.
     */
    private static ArenaLadder vgcQuickPreset(
        String id,
        String displayName,
        String format,
        int level,
        boolean allowLegendaries,
        java.util.List<String> extraShowdownRules
    ) {
        java.util.Set<String> bannedSpecies = allowLegendaries
            ? java.util.Set.of()
            : new java.util.HashSet<>(RESTRICTED_LEGENDARY_BAN_LIST);
        java.util.Set<String> bannedItems = new java.util.LinkedHashSet<>(
            VGC_ITEM_BANS
        );
        return new ArenaLadder(
            id,
            "Fila Rápida",
            displayName,
            null,
            false,
            format,
            6,
            level,
            allowLegendaries,
            true,
            true,
            bannedSpecies,
            bannedItems,
            java.util.Set.of(),
            java.util.Set.of(),
            java.util.Set.of(),
            normalizeShowdownRules(extraShowdownRules)
        );
    }

    private static String buildDescription(
        boolean ranked,
        String format,
        int level,
        boolean allowRestricted,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        Set<String> bannedSpecies,
        Set<String> bannedItems,
        Set<String> bannedAbilities,
        Set<String> bannedMoves,
        Set<String> bannedTiers
    ) {
        String prefix = ranked ? "Ranqueado " : "Casual ";
        String restriction = !bannedTiers.isEmpty()
            ? "com restrições de tier estilo Smogon"
            : (allowRestricted
                  ? "com Pokémon restritos permitidos"
                  : "com Pokémon restritos bloqueados");
        String extra =
            !enforceSpeciesClause && !enforceItemClause
                ? " e regras de arena personalizadas"
                : " e regras de arena aplicadas";
        String banSummary =
            bannedSpecies.isEmpty() && bannedItems.isEmpty()
                ? ""
                : ", mais banimentos personalizados";
        if (
            !bannedAbilities.isEmpty() ||
            !bannedMoves.isEmpty() ||
            !bannedTiers.isEmpty()
        ) {
            banSummary = ", mais banimentos de preset ou personalizados";
        }

        return (
            prefix +
            normalizeFormat(format) +
            " no nível " +
            level +
            " " +
            restriction +
            extra +
            banSummary +
            "."
        );
    }

    private static String buildRankedDisplayName(
        String format,
        String level,
        String prefix
    ) {
        String var3 = normalizeFormat(format);

        return (
            prefix +
            " " +
            switch (var3) {
                case "doubles" -> "Duplas";
                case "triples" -> "Triplas";
                default -> "Singles";
            } +
            " Lv. " +
            parseLevel(level)
        );
    }

    private static List<String> joinableList(Set<String> values) {
        return values.stream().toList();
    }

    private static String joinPreview(Set<String> values) {
        return (
            values.stream().limit(3L).collect(Collectors.joining(", ")) +
            (values.size() > 3 ? "..." : "")
        );
    }

    private static Set<String> normalizeList(
        List<String> values,
        boolean species
    ) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        } else {
            for (String value : values) {
                String cleaned = species
                    ? normalizeSpeciesKey(value)
                    : normalizeItemKey(value);
                if (!cleaned.isBlank()) {
                    normalized.add(cleaned);
                }
            }

            return normalized;
        }
    }

    private static Set<String> normalizeRuleList(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        } else {
            for (String value : values) {
                String cleaned = normalizeRuleKey(value);
                if (!cleaned.isBlank()) {
                    normalized.add(cleaned);
                }
            }

            return normalized;
        }
    }

    private static Set<String> normalizeShowdownRules(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        } else {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value.trim());
                }
            }

            return normalized;
        }
    }

    public static String normalizeSpeciesKey(String value) {
        if (value != null && !value.isBlank()) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains(":")) {
                normalized = normalized.substring(normalized.indexOf(58) + 1);
            }

            return normalized.replaceAll("[^a-z0-9_/.-]", "");
        } else {
            return "";
        }
    }

    public static String normalizeItemKey(String value) {
        if (value != null && !value.isBlank()) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains(":")) {
                normalized = normalized.substring(normalized.indexOf(58) + 1);
            }

            return normalized.replaceAll("[^a-z0-9]", "");
        } else {
            return "";
        }
    }

    public static String normalizeRuleKey(String value) {
        if (value != null && !value.isBlank()) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains(":")) {
                normalized = normalized.substring(normalized.indexOf(58) + 1);
            }

            return normalized.replaceAll("[^a-z0-9]", "");
        } else {
            return "";
        }
    }

    private static String normalizeId(String value) {
        return value != null && !value.isBlank()
            ? value.trim().toLowerCase(Locale.ROOT)
            : "arena_ladder";
    }

    private static String normalizeFormat(String format) {
        String var1 =
            format == null ? "" : format.trim().toLowerCase(Locale.ROOT);

        return switch (var1) {
            case "doubles", "duplas" -> "doubles";
            case "triples", "triplas" -> "triples";
            case "monotype" -> "monotype";
            case "vgc" -> "vgc";
            default -> "singles";
        };
    }

    private static int parseLevel(String level) {
        if (level != null && !level.isBlank()) {
            try {
                int parsed = Integer.parseInt(level.trim());
                return parsed > 0 ? parsed : 50;
            } catch (NumberFormatException var2) {
                return 50;
            }
        } else {
            return 50;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            return obj instanceof ArenaLadder other
                ? this.id.equals(other.id)
                : false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return this.id;
    }
}
