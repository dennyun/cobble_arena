package cobblemon.arena.format;

import com.cobblemon.mod.common.battles.BattleFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central enum that describes every matchmaking format available in Cobblemon
 * Arena.  This replaces the old {@code ArenaLadder} class with a clean,
 * type-safe enum while retaining all method names needed for backward
 * compatibility with existing callers.
 *
 * <p>Ranked formats always enforce full VGC rules (Species Clause, Item Clause,
 * no restricted Pokémon) and lock the level cap to 50.  Casual formats relax
 * these restrictions progressively.</p>
 */
public enum FormatPreset {

    // ── Ranked (always Lv50, full VGC rules) ─────────────────────────────────
    RANKED_SINGLES(
            "ranked_singles",
            BattleMode.RANKED,
            BattleStructure.SINGLES,
            50,
            false
    ),
    RANKED_DOUBLES(
            "ranked_doubles",
            BattleMode.RANKED,
            BattleStructure.DOUBLES,
            50,
            false
    ),
    RANKED_TRIPLES(
            "ranked_triples",
            BattleMode.RANKED,
            BattleStructure.TRIPLES,
            50,
            false
    ),

    // ── Casual Singles ────────────────────────────────────────────────────────
    CASUAL_SINGLES_50(
            "casual_singles_50",
            BattleMode.CASUAL,
            BattleStructure.SINGLES,
            50,
            false
    ),
    CASUAL_SINGLES_100(
            "casual_singles_100",
            BattleMode.CASUAL,
            BattleStructure.SINGLES,
            100,
            true
    ),

    // ── Casual Doubles ────────────────────────────────────────────────────────
    CASUAL_DOUBLES_50(
            "casual_doubles_50",
            BattleMode.CASUAL,
            BattleStructure.DOUBLES,
            50,
            false
    ),
    CASUAL_DOUBLES_100(
            "casual_doubles_100",
            BattleMode.CASUAL,
            BattleStructure.DOUBLES,
            100,
            true
    ),

    // ── Casual Triples ────────────────────────────────────────────────────────
    CASUAL_TRIPLES_50(
            "casual_triples_50",
            BattleMode.CASUAL,
            BattleStructure.TRIPLES,
            50,
            false
    ),
    CASUAL_TRIPLES_100(
            "casual_triples_100",
            BattleMode.CASUAL,
            BattleStructure.TRIPLES,
            100,
            true
    ),

    // ── Casual Monotype ───────────────────────────────────────────────────────
    CASUAL_MONOTYPE_50(
            "casual_monotype_50",
            BattleMode.CASUAL,
            BattleStructure.MONOTYPE,
            50,
            false
    ),
    CASUAL_MONOTYPE_100(
            "casual_monotype_100",
            BattleMode.CASUAL,
            BattleStructure.MONOTYPE,
            100,
            true
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Stable lower-case identifier used for serialisation and lookups. */
    private final String id;

    /** Whether this format is ranked (affects ELO) or casual. */
    private final BattleMode mode;

    /** Battle structure (Singles, Doubles, Triples, Monotype). */
    private final BattleStructure structure;

    /**
     * Level cap applied by Cobblemon during battle.
     * Ranked formats always use 50; casual formats may use 50 or 100.
     */
    private final int levelCap;

    /**
     * For casual formats only — when {@code true} restricted / legendary
     * Pokémon are allowed.  Ranked formats always ban restricted Pokémon
     * regardless of this flag.
     */
    private final boolean allowLegendaries;

    // ── Constructor ───────────────────────────────────────────────────────────

    FormatPreset(
            String id,
            BattleMode mode,
            BattleStructure structure,
            int levelCap,
            boolean allowLegendaries
    ) {
        this.id           = id;
        this.mode         = mode;
        this.structure    = structure;
        this.levelCap     = levelCap;
        this.allowLegendaries = allowLegendaries;
    }

    // ── Core getters ──────────────────────────────────────────────────────────

    /** Stable lower-case identifier used for serialisation and lookups. */
    public String getId() {
        return id;
    }

    /** The {@link BattleMode} (RANKED or CASUAL) for this format. */
    public BattleMode getMode() {
        return mode;
    }

    /** The {@link BattleStructure} (SINGLES, DOUBLES, TRIPLES, MONOTYPE). */
    public BattleStructure getStructure() {
        return structure;
    }

    /** Level cap applied to all Pokémon during battles in this format. */
    public int getLevelCap() {
        return levelCap;
    }

    /**
     * Alias for {@link #getLevelCap()} — kept for compatibility with code that
     * previously used {@code ArenaLadder.getAdjustLevel()}.
     */
    public int getAdjustLevel() {
        return levelCap;
    }

    /**
     * Returns the Cobblemon {@code battleTypeId} string ({@code "singles"},
     * {@code "doubles"}, or {@code "triples"}) used when constructing a
     * {@link BattleFormat}.  Kept for compatibility with code that previously
     * used {@code ArenaLadder.getBattleTypeId()}.
     */
    public String getBattleTypeId() {
        return structure.getCobblemonId();
    }

    // ── Boolean predicates ────────────────────────────────────────────────────

    /** Returns {@code true} if this is a ranked format that affects ELO. */
    public boolean isRanked() {
        return mode == BattleMode.RANKED;
    }

    /** Returns {@code true} if this format enforces the same-type (Monotype) rule. */
    public boolean isMonotype() {
        return structure == BattleStructure.MONOTYPE;
    }

    /**
     * Returns {@code true} if restricted / legendary Pokémon are permitted.
     * Ranked formats always return {@code false}; casual Lv.100 variants
     * return {@code true}.
     */
    public boolean allowsLegendaries() {
        return allowLegendaries;
    }

    /**
     * Returns {@code true} if species listed in {@link VGCRules#RESTRICTED_POKEMON}
     * may appear in a team for this format.  Only casual Lv.100 variants allow
     * restricted Pokémon; ranked formats never do.
     */
    public boolean allowsRestrictedPokemon() {
        return mode == BattleMode.CASUAL && allowLegendaries;
    }

    // ── Team-size helpers ─────────────────────────────────────────────────────

    /**
     * Returns the minimum number of Pokémon a player must have in their party
     * to enter this format's queue.
     *
     * <ul>
     *   <li>Singles / Monotype → 1</li>
     *   <li>Doubles            → 2</li>
     *   <li>Triples            → 3</li>
     * </ul>
     */
    public int getMinTeamSize() {
        return switch (structure) {
            case DOUBLES -> 2;
            case TRIPLES -> 3;
            default      -> 1;
        };
    }

    /**
     * Alias for {@link #getMinTeamSize()} — kept for compatibility with code
     * that previously used {@code ArenaLadder.getRequiredTeamSize()}.
     */
    public int getRequiredTeamSize() {
        return getMinTeamSize();
    }

    // ── Display / UI helpers ──────────────────────────────────────────────────

    /**
     * Human-readable name shown in queues, scoreboards, and GUIs.
     *
     * <ul>
     *   <li>Ranked:  {@code "Ranqueado Singles"} / {@code "Ranqueado Duplas"}</li>
     *   <li>Casual:  {@code "Casual Duplas Nv.50"} / {@code "Casual Monotype Nv.100"}</li>
     * </ul>
     */
    public String getDisplayName() {
        if (isRanked()) {
            return mode.getDisplayName() + " " + structure.getDisplayName();
        }
        return mode.getDisplayName() + " " + structure.getDisplayName() + " Nv." + levelCap;
    }

    /**
     * Short label shown on the queue HUD.
     *
     * @return {@code "Fila Ranqueada"} or {@code "Fila Casual"}
     */
    public String getQueueLabel() {
        return isRanked() ? "Fila Ranqueada" : "Fila Casual";
    }

    /**
     * Single-line pipe-separated summary of the active rules, used in tooltips
     * and chat messages.
     *
     * <p>Example: {@code "Duplas | Nv.50 | Species Clause | Item Clause | Sem Restritos"}</p>
     */
    public String getRulesSummary() {
        List<String> parts = new ArrayList<>();
        parts.add(structure.getDisplayName());
        parts.add("Nv." + levelCap);
        if (isRanked()) {
            parts.add("Species Clause");
            parts.add("Item Clause");
            parts.add("Sem Restritos");
        } else {
            if (allowLegendaries) {
                parts.add("Restritos Permitidos");
            }
        }
        if (isMonotype()) {
            parts.add("Monotype");
        }
        return String.join(" | ", parts);
    }

    // ── Battle format integration ─────────────────────────────────────────────

    /**
     * Constructs and returns the Cobblemon {@link BattleFormat} for this preset.
     *
     * <p>The Gen 9 base format is selected by structure type; additional rules
     * (Same Type Clause for Monotype, Species/Item clauses for ranked) are
     * merged into the rule set before the format is copied with this preset's
     * level cap.</p>
     *
     * @return a fully configured {@link BattleFormat} ready for use with
     *         Cobblemon's {@code BattleBuilder}
     */
    public BattleFormat createCobblemonBattleFormat() {
        BattleFormat base = switch (structure.getCobblemonId()) {
            case "doubles" -> BattleFormat.Companion.getGEN_9_DOUBLES();
            case "triples" -> BattleFormat.Companion.getGEN_9_TRIPLES();
            default        -> BattleFormat.Companion.getGEN_9_SINGLES();
        };

        Set<String> rules = new LinkedHashSet<>(base.getRuleSet());

        // Nós aplicamos as cláusulas ranqueadas se isRanked for verdadeiro
        if (isRanked()) {  // Explicit enforcement of VGC clauses in the Cobblemon rule set
            rules.add("Species Clause");
            rules.add("Item Clause");
        }

        return base.copy(
                base.getMod(),
                base.getBattleType(),
                rules,
                base.getGen(),
                levelCap
        );
    }

    /**
     * Alias for {@link #createCobblemonBattleFormat()} — kept for compatibility
     * with code that previously called {@code ArenaLadder.createBattleFormat()}.
     */
    public BattleFormat createBattleFormat() {
        return createCobblemonBattleFormat();
    }

    // ── Static lookup / factory helpers ──────────────────────────────────────

    /**
     * Returns the {@code FormatPreset} whose {@link #getId()} matches the
     * supplied string (case-insensitive, leading/trailing whitespace stripped),
     * or {@code null} if no match is found.
     *
     * @param id the preset ID to look up (e.g. {@code "ranked_doubles"})
     * @return the matching preset, or {@code null}
     */
    public static FormatPreset byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (FormatPreset preset : values()) {
            if (preset.id.equals(normalized)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Returns an immutable list of all ranked presets in declaration order.
     */
    public static List<FormatPreset> rankedFormats() {
        return Arrays.stream(values())
                .filter(FormatPreset::isRanked)
                .toList();
    }

    /**
     * Returns an immutable list of all casual presets in declaration order.
     */
    public static List<FormatPreset> casualFormats() {
        return Arrays.stream(values())
                .filter(f -> !f.isRanked())
                .toList();
    }

    /**
     * Returns an immutable list of all preset IDs, useful for command
     * auto-completion and configuration validation.
     */
    public static List<String> ids() {
        return Arrays.stream(values())
                .map(FormatPreset::getId)
                .toList();
    }

    /**
     * Default ranked preset used when no specific format has been configured.
     * Ranked Doubles is the closest equivalent to the standard VGC format.
     */
    public static FormatPreset defaultRanked() {
        return RANKED_DOUBLES;
    }

    /**
     * Default casual preset used when no specific format has been configured.
     */
    public static FormatPreset defaultCasual() {
        return CASUAL_DOUBLES_50;
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "FormatPreset{"
                + "id='" + id + '\''
                + ", mode=" + mode
                + ", structure=" + structure
                + ", levelCap=" + levelCap
                + ", allowLegendaries=" + allowLegendaries
                + '}';
    }
}
