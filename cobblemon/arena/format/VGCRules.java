package cobblemon.arena.format;

import java.util.Locale;
import java.util.Set;

/**
 * Centralised VGC rule constants and validation helpers used across all ranked
 * and casual format presets.
 *
 * <p>All string constants (species IDs, item IDs) are stored lower-case and
 * normalised so that callers can compare after a single {@link String#toLowerCase}
 * call.</p>
 */
public final class VGCRules {

    // ── Structural rule flags ─────────────────────────────────────────────────

    /** No two Pokémon on the same team may share the same species (dex number). */
    public static final boolean SPECIES_CLAUSE = true;

    /** No two Pokémon on the same team may hold the same item. */
    public static final boolean ITEM_CLAUSE = true;

    // ── Level / team-size constants ───────────────────────────────────────────

    /** All Pokémon are auto-adjusted to this level during ranked battles. */
    public static final int DEFAULT_LEVEL_CAP = 50;

    /**
     * Minimum party size required for doubles and triples formats.
     * In standard VGC you bring 4 and pick 2 (or 3 for triples) in team preview.
     */
    public static final int MIN_TEAM_SIZE = 4;

    /** Minimum party size for singles formats. */
    public static final int MIN_TEAM_SIZE_SINGLES = 1;

    // ── Banned items ──────────────────────────────────────────────────────────

    /**
     * Items that are forbidden in all ranked formats.
     * King's Rock and Razor Fang grant a flinch chance to multi-hit moves and
     * are banned to prevent centralisation around flinch-fishing strategies.
     */
    public static final Set<String> BANNED_ITEMS = Set.of(
            "kingsrock",
            "razorfang"
    );

    // ── Restricted / legendary Pokémon ───────────────────────────────────────

    /**
     * Full list of restricted and legendary Pokémon that are banned from ranked
     * formats.  IDs are lower-case Cobblemon resource path segments (e.g.
     * {@code "mewtwo"}, {@code "tapu-koko"}).
     *
     * <p>The list mirrors {@code ArenaLadder.RESTRICTED_LEGENDARY_BAN_LIST} and
     * the requirement specification, and covers:</p>
     * <ul>
     *   <li>Box legendaries from every generation (Gen I – IX)</li>
     *   <li>Sub-legendaries deemed too powerful for standard ranked play</li>
     *   <li>Mythicals that are structurally broken (Deoxys, Arceus, etc.)</li>
     *   <li>Hisui / Paldea special Pokémon (Wyrdeer, Ursaluna, Sneasler)</li>
     *   <li>Treasures of Ruin quartet (Gen IX)</li>
     * </ul>
     */
    public static final Set<String> RESTRICTED_POKEMON = Set.of(
            // ── Gen I ────────────────────────────────────────────────────────
            "mewtwo",
            "articuno",
            "zapdos",
            "moltres",

            // ── Gen II ───────────────────────────────────────────────────────
            "lugia",
            "ho-oh",
            "raikou",
            "entei",
            "suicune",

            // ── Gen III ──────────────────────────────────────────────────────
            "kyogre",
            "groudon",
            "rayquaza",
            "deoxys",
            "regirock",
            "regice",
            "registeel",
            "latias",
            "latios",

            // ── Gen IV ───────────────────────────────────────────────────────
            "dialga",
            "palkia",
            "giratina",
            "arceus",
            "uxie",
            "mesprit",
            "azelf",
            "heatran",
            "regigigas",
            "cresselia",

            // ── Gen V ────────────────────────────────────────────────────────
            "reshiram",
            "zekrom",
            "kyurem",
            "cobalion",
            "terrakion",
            "virizion",
            "tornadus",
            "thundurus",
            "landorus",

            // ── Gen VI ───────────────────────────────────────────────────────
            "xerneas",
            "yveltal",
            "zygarde",

            // ── Gen VII ──────────────────────────────────────────────────────
            "cosmog",
            "cosmoem",
            "solgaleo",
            "lunala",
            "necrozma",
            "tapu-koko",
            "tapu-lele",
            "tapu-bulu",
            "tapu-fini",

            // ── Gen VIII ─────────────────────────────────────────────────────
            "zacian",
            "zamazenta",
            "eternatus",
            "calyrex",
            "kubfu",
            "urshifu",
            "regieleki",
            "regidrago",
            "glastrier",
            "spectrier",
            "enamorus",

            // ── Gen IX ───────────────────────────────────────────────────────
            "koraidon",
            "miraidon",
            "terapagos",
            // Treasures of Ruin
            "wo-chien",
            "chien-pao",
            "ting-lu",
            "chi-yu",

            // ── Hisui / Paldea special forms ─────────────────────────────────
            "wyrdeer",
            "ursaluna",
            "sneasler"
    );

    // ── Private constructor (utility class) ───────────────────────────────────

    private VGCRules() {}

    // ── Public validation helpers ─────────────────────────────────────────────

    /**
     * Returns {@code true} if the given species identifier belongs to the
     * restricted / legendary ban list.
     *
     * <p>The comparison is case-insensitive; the input is normalised to
     * lower-case before the lookup.</p>
     *
     * @param speciesId the Cobblemon resource-path species identifier
     *                  (e.g. {@code "Mewtwo"}, {@code "tapu-koko"})
     * @return {@code true} if the species is restricted in ranked formats
     */
    public static boolean isRestricted(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return false;
        }
        return RESTRICTED_POKEMON.contains(speciesId.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if the given item identifier is banned in ranked
     * formats (King's Rock and Razor Fang).
     *
     * <p>The comparison is case-insensitive; the input is normalised to
     * lower-case before the lookup.</p>
     *
     * @param itemId the item registry path identifier
     *               (e.g. {@code "kingsrock"}, {@code "razorfang"})
     * @return {@code true} if the item is banned in ranked formats
     */
    public static boolean isBannedItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return BANNED_ITEMS.contains(itemId.trim().toLowerCase(Locale.ROOT));
    }
}
