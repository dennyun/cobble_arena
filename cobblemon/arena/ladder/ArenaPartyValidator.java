package cobblemon.arena.ladder;

import cobblemon.arena.format.FormatPreset;
import cobblemon.arena.format.VGCRules;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Validates a player's party against a {@link FormatPreset}, enforcing all
 * applicable rules (team size, fainted check, Species Clause, Item Clause,
 * banned items, restricted Pokémon bans, and Monotype).
 *
 * <p>All public methods are static; this class is not instantiable.</p>
 */
public final class ArenaPartyValidator {

    private ArenaPartyValidator() {}

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Validates a player's current party against the rules of the given
     * {@link FormatPreset}.
     *
     * @param player the server-side player whose party is to be validated
     * @param format the format preset whose rules apply
     * @return a {@link ArenaPartyValidationResult} describing success or every
     *         rule violation found
     */
    public static ArenaPartyValidationResult validate(
        ServerPlayerEntity player,
        FormatPreset format
    ) {
        List<Pokemon> party = getPartyPokemon(player);
        List<String> problems = new ArrayList<>();

        // ── 1. Team size ──────────────────────────────────────────────────────
        int minSize = format.getMinTeamSize();
        if (party.size() < minSize) {
            problems.add(
                "Time insuficiente: mínimo " +
                    minSize +
                    " Pokémon para " +
                    format.getStructure().getDisplayName() +
                    ". Você tem " +
                    party.size() +
                    "."
            );
        }

        // ── 2. All-fainted guard ──────────────────────────────────────────────
        // Even if the team size check above already failed we continue so all
        // problems are reported at once, which gives better UX.
        if (!party.isEmpty()) {
            boolean hasHealthyPokemon = party
                .stream()
                .anyMatch(p -> !p.isFainted());
            if (!hasHealthyPokemon) {
                problems.add(
                    "Todos os seus Pokémon estão debilitados. " +
                        "Cure seu time antes de entrar em batalha."
                );
            }
        }

        // ── 3. Ranked-only VGC rules ──────────────────────────────────────────
        if (format.isRanked()) {
            validateRankedRules(party, format, problems);
        }

        // ── 4. Monotype check (ranked or casual monotype) ─────────────────────
        if (format.isMonotype()) {
            problems.addAll(validateMonotype(party));
        }

        return problems.isEmpty()
            ? ArenaPartyValidationResult.success()
            : ArenaPartyValidationResult.failure(problems);
    }

    /**
     * Legacy overload retained for compatibility with existing code that still
     * passes an {@link ArenaLadder} instance.  Only performs the original
     * party-count check.
     *
     * @param party  a {@link PlayerPartyStore} (any other type returns false)
     * @param ladder the ladder whose required team size is consulted
     * @return {@code true} if the party has at least
     *         {@code ladder.getRequiredTeamSize()} non-null slots
     */
    public static boolean validate(Object party, ArenaLadder ladder) {
        if (
            !(party instanceof PlayerPartyStore playerParty) || ladder == null
        ) {
            return false;
        }
        int pokemonCount = 0;
        for (int i = 0; i < 6; i++) {
            if (playerParty.get(i) != null) {
                pokemonCount++;
            }
        }
        return pokemonCount >= ladder.getRequiredTeamSize();
    }

    // ── Ranked rule sub-validators ────────────────────────────────────────────

    /**
     * Applies all ranked-specific VGC rules to the party and appends any
     * violations to {@code problems}.
     */
    private static void validateRankedRules(
        List<Pokemon> party,
        FormatPreset format,
        List<String> problems
    ) {
        // ── Species Clause ────────────────────────────────────────────────────
        if (VGCRules.SPECIES_CLAUSE) {
            Map<String, Integer> speciesCount = new LinkedHashMap<>();
            for (Pokemon pokemon : party) {
                String speciesId = normalizeSpeciesId(pokemon);
                if (!speciesId.isBlank()) {
                    speciesCount.merge(speciesId, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> entry : speciesCount.entrySet()) {
                if (entry.getValue() > 1) {
                    problems.add(
                        "Cláusula de Espécie: " +
                            entry.getKey() +
                            " aparece " +
                            entry.getValue() +
                            " vezes no time."
                    );
                }
            }
        }

        // ── Item Clause ───────────────────────────────────────────────────────
        if (VGCRules.ITEM_CLAUSE) {
            Map<String, Integer> itemCount = new LinkedHashMap<>();
            for (Pokemon pokemon : party) {
                String heldItem = getHeldItemId(pokemon);
                if (
                    heldItem != null &&
                    !heldItem.isBlank() &&
                    !heldItem.equals("air")
                ) {
                    itemCount.merge(heldItem, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> entry : itemCount.entrySet()) {
                if (entry.getValue() > 1) {
                    problems.add(
                        "Cláusula de Item: " +
                            entry.getKey() +
                            " está equipado em " +
                            entry.getValue() +
                            " Pokémon."
                    );
                }
            }
        }

        // ── Banned items (King's Rock, Razor Fang) ────────────────────────────
        for (Pokemon pokemon : party) {
            String heldItem = getHeldItemId(pokemon);
            if (heldItem != null && VGCRules.isBannedItem(heldItem)) {
                String pokemonName = safeSpeciesName(pokemon);
                problems.add(
                    "Item banido: " +
                        pokemonName +
                        " segura " +
                        heldItem +
                        " (proibido no Ranqueado)."
                );
            }
        }

        // ── Restricted Pokémon ban ────────────────────────────────────────────
        // Ranked formats never allow restricted Pokémon regardless of the
        // allowsRestrictedPokemon() flag (which is always false for ranked).
        if (!format.allowsRestrictedPokemon()) {
            for (Pokemon pokemon : party) {
                String speciesId = normalizeSpeciesId(pokemon);
                if (VGCRules.isRestricted(speciesId)) {
                    String pokemonName = safeSpeciesName(pokemon);
                    problems.add(
                        "Pokémon restrito: " +
                            pokemonName +
                            " (" +
                            speciesId +
                            ")" +
                            " não é permitido no Ranqueado."
                    );
                }
            }
        }
    }

    // ── Monotype validation ───────────────────────────────────────────────────

    /**
     * Checks that all Pokémon in the party share at least one common
     * elemental type.  Returns an empty list if the team is valid (or if the
     * type API is unavailable on this Cobblemon build).
     *
     * <p>Algorithm: collect the types of the first Pokémon as the candidate
     * set, then intersect with each subsequent Pokémon's types.  If the
     * intersection is empty before we finish the party, the team is invalid.</p>
     */
    private static List<String> validateMonotype(List<Pokemon> party) {
        if (party.isEmpty()) {
            return List.of();
        }
        try {
            Set<String> commonTypes = null;

            for (Pokemon pokemon : party) {
                Set<String> pokemonTypes = getPokemonTypes(pokemon);
                if (pokemonTypes.isEmpty()) {
                    // Can't determine types — skip this Pokémon defensively.
                    continue;
                }
                if (commonTypes == null) {
                    commonTypes = new HashSet<>(pokemonTypes);
                } else {
                    commonTypes.retainAll(pokemonTypes);
                }
                if (commonTypes.isEmpty()) {
                    // Already no common type — no need to continue.
                    break;
                }
            }

            if (commonTypes != null && commonTypes.isEmpty()) {
                return List.of(
                    "Monotype: Seu time não compartilha um tipo comum. " +
                        "Todos os Pokémon devem compartilhar pelo menos um tipo."
                );
            }
            return List.of();
        } catch (Exception e) {
            // If we can't read types for any reason, don't block the player —
            // Cobblemon's own battle engine will enforce the rule in-battle.
            return List.of();
        }
    }

    /**
     * Returns the lower-case elemental type names of the given Pokémon.
     * Tries the species' primary and secondary types first; falls back to
     * an empty set if the API is unavailable.
     */
    private static Set<String> getPokemonTypes(Pokemon pokemon) {
        Set<String> types = new HashSet<>(2);
        try {
            // Primary type — always present on a valid species
            var primaryType = pokemon.getSpecies().getPrimaryType();
            if (primaryType != null) {
                types.add(primaryType.getName().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {}

        try {
            // Secondary type — nullable / optional in Cobblemon
            var secondaryType = pokemon.getSpecies().getSecondaryType();
            if (secondaryType != null) {
                types.add(secondaryType.getName().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {}

        return types;
    }

    // ── Party accessor ────────────────────────────────────────────────────────

    /**
     * Returns a list of all non-null Pokémon in the player's current party
     * (slots 0–5), in slot order.
     *
     * @param player the player whose party is fetched
     * @return an immutable view of the non-null party members; empty if the
     *         player is null or their party cannot be read
     */
    public static List<Pokemon> getPartyPokemon(ServerPlayerEntity player) {
        if (player == null) {
            return List.of();
        }
        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(
                player
            );
            List<Pokemon> pokemon = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                Pokemon member = party.get(i);
                if (member != null) {
                    pokemon.add(member);
                }
            }
            return pokemon;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Private normalisation helpers ─────────────────────────────────────────

    /**
     * Returns the lower-case resource-path species identifier for the given
     * Pokémon (e.g. {@code "mewtwo"}, {@code "tapu-koko"}).
     *
     * <p>Falls back to a sanitised version of the species display name if the
     * resource identifier is unavailable.</p>
     */
    private static String normalizeSpeciesId(Pokemon pokemon) {
        if (pokemon == null) {
            return "";
        }
        try {
            return pokemon
                .getSpecies()
                .getResourceIdentifier()
                .getPath()
                .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            try {
                // Fallback: sanitise the display name into a plain ID
                return pokemon
                    .getSpecies()
                    .getName()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9\\-]", "");
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /**
     * Returns the lower-case item registry identifier of the Pokémon's held
     * item (e.g. {@code "kingsrock"}), normalised by stripping underscores and
     * hyphens so it matches entries in {@link VGCRules#BANNED_ITEMS}.
     *
     * <p>Returns {@code null} if the Pokémon holds nothing or if the item
     * cannot be identified.</p>
     */
    private static String getHeldItemId(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        try {
            var heldItem = pokemon.heldItem();
            if (heldItem == null || heldItem.isEmpty()) {
                return null;
            }

            // Primary path: RegistryEntry → idAsString (e.g. "minecraft:air",
            // "cobblemon:kings_rock").  We keep only the path segment.
            String rawId = null;
            try {
                rawId = heldItem
                    .getRegistryEntry()
                    .getIdAsString()
                    .toLowerCase(Locale.ROOT);
                // Strip namespace (e.g. "minecraft:" or "cobblemon:")
                int colon = rawId.lastIndexOf(':');
                if (colon >= 0) {
                    rawId = rawId.substring(colon + 1);
                }
            } catch (Exception ignored) {}

            // Fallback path: Minecraft Registries lookup
            if (rawId == null) {
                try {
                    var identifier =
                        net.minecraft.registry.Registries.ITEM.getId(
                            heldItem.getItem()
                        );
                    rawId = identifier.getPath().toLowerCase(Locale.ROOT);
                } catch (Exception ignored) {}
            }

            if (rawId == null || rawId.isBlank()) {
                return null;
            }

            // Normalise: remove underscores and hyphens so "kings_rock"
            // matches "kingsrock" in the ban list.
            return rawId.replace("_", "").replace("-", "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely returns the species display name of a Pokémon for use in
     * human-readable error messages.
     */
    private static String safeSpeciesName(Pokemon pokemon) {
        if (pokemon == null) {
            return "Desconhecido";
        }
        try {
            return pokemon.getSpecies().getName();
        } catch (Exception e) {
            return "Desconhecido";
        }
    }
}
