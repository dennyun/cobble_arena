package cobblemon.arena.ladder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum ArenaRankedPreset {
    CUSTOM(
        "custom",
        "Personalizado",
        "Ladder Personalizada",
        "Regras de ladder editáveis.",
        "singles",
        50,
        false,
        true,
        true,
        List.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    SOLO(
        "solo",
        "Solo",
        "Solo",
        "Batalhas individuais 1v1.",
        "singles",
        50,
        true,
        true,
        true,
        List.of("Standard"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    DUPLAS(
        "duplas",
        "Duplas",
        "Duplas",
        "Batalhas em duplas 2v2.",
        "doubles",
        50,
        true,
        true,
        true,
        List.of("Standard"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    TRIPLAS(
        "triplas",
        "Triplas",
        "Triplas",
        "Batalhas em trios 3v3.",
        "triples",
        50,
        true,
        true,
        true,
        List.of("Standard"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    ),
    MONOTYPE(
        "monotype",
        "Monotype",
        "Monotype",
        "Batalhas onde o time deve compartilhar um tipo.",
        "singles",
        50,
        true,
        true,
        true,
        List.of("Standard", "Same Type Clause"),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of()
    );

    private final String key;
    private final String selectionLabel;
    private final String displayName;
    private final String description;
    private final String battleTypeId;
    private final int adjustLevel;
    private final boolean allowRestrictedPokemon;
    private final boolean enforceSpeciesClause;
    private final boolean enforceItemClause;
    private final List<String> showdownRules;
    private final Set<String> bannedSpecies;
    private final Set<String> bannedTiers;
    private final Set<String> bannedAbilities;
    private final Set<String> bannedItems;
    private final Set<String> bannedMoves;

    private ArenaRankedPreset(
        String key,
        String selectionLabel,
        String displayName,
        String description,
        String battleTypeId,
        int adjustLevel,
        boolean allowRestrictedPokemon,
        boolean enforceSpeciesClause,
        boolean enforceItemClause,
        List<String> showdownRules,
        Set<String> bannedSpecies,
        Set<String> bannedTiers,
        Set<String> bannedAbilities,
        Set<String> bannedItems,
        Set<String> bannedMoves
    ) {
        this.key = key;
        this.selectionLabel = selectionLabel;
        this.displayName = displayName;
        this.description = description;
        this.battleTypeId = battleTypeId;
        this.adjustLevel = adjustLevel;
        this.allowRestrictedPokemon = allowRestrictedPokemon;
        this.enforceSpeciesClause = enforceSpeciesClause;
        this.enforceItemClause = enforceItemClause;
        this.showdownRules = List.copyOf(showdownRules);
        this.bannedSpecies = Set.copyOf(bannedSpecies);
        this.bannedTiers = Set.copyOf(bannedTiers);
        this.bannedAbilities = Set.copyOf(bannedAbilities);
        this.bannedItems = Set.copyOf(bannedItems);
        this.bannedMoves = Set.copyOf(bannedMoves);
    }

    public String getKey() {
        return this.key;
    }

    public String getSelectionLabel() {
        return this.selectionLabel;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public String getBattleTypeId() {
        return this.battleTypeId;
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

    public List<String> getShowdownRules() {
        return this.showdownRules;
    }

    public Set<String> getBannedSpecies() {
        return this.bannedSpecies;
    }

    public Set<String> getBannedTiers() {
        return this.bannedTiers;
    }

    public Set<String> getBannedAbilities() {
        return this.bannedAbilities;
    }

    public Set<String> getBannedItems() {
        return this.bannedItems;
    }

    public Set<String> getBannedMoves() {
        return this.bannedMoves;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public boolean isLockedPreset() {
        return this != CUSTOM;
    }

    public static ArenaRankedPreset fromKey(String key) {
        String normalized = normalizeKey(key);

        for (ArenaRankedPreset preset : values()) {
            if (preset.key.equals(normalized)) {
                return preset;
            }
        }

        return CUSTOM;
    }

    public static List<String> selectionOptions() {
        return List.of(
            CUSTOM.selectionLabel,
            SOLO.selectionLabel,
            DUPLAS.selectionLabel,
            TRIPLAS.selectionLabel,
            MONOTYPE.selectionLabel
        );
    }

    public static String selectionForKey(String key) {
        return fromKey(key).selectionLabel;
    }

    public static String keyForSelection(String selection) {
        if (selection != null && !selection.isBlank()) {
            String normalized = selection.trim().toLowerCase(Locale.ROOT);

            for (ArenaRankedPreset preset : values()) {
                if (
                    preset.selectionLabel
                        .toLowerCase(Locale.ROOT)
                        .equals(normalized)
                ) {
                    return preset.key;
                }
            }

            return CUSTOM.key;
        } else {
            return CUSTOM.key;
        }
    }

    public static boolean matchesTier(String speciesId, String tierKey) {
        // Tiers não existem mais no novo formato. Se precisar, adicione verificações aqui.
        return false;
    }

    public static String formatTierName(String tierKey) {
        String var1 = normalizeKey(tierKey);
        return switch (var1) {
            case "solo" -> "Solo";
            case "duplas" -> "Duplas";
            case "triplas" -> "Triplas";
            case "monotype" -> "Monotype";
            default -> tierKey.toUpperCase(Locale.ROOT);
        };
    }

    private static String normalizeKey(String key) {
        return key != null
            ? key.trim().toLowerCase(Locale.ROOT).replace(" ", "_")
            : "";
    }
}
