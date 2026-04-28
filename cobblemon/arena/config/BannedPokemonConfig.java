package cobblemon.arena.config;

import cobblemon.arena.CobblemonArena;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/**
 * Manages the configurable list of restricted/legendary Pokémon that are banned
 * from non-legendary ranked and casual formats.
 *
 * <p>On the first server start the default list is written to
 * {@code <world>/cobblemon_arena/banned_pokemon.json}. Admins can then edit
 * that file and restart the server to apply changes.</p>
 *
 * <p><strong>Important:</strong> {@link #load(MinecraftServer)} must be called
 * <em>before</em> {@link cobblemon.arena.ladder.ArenaLadder} is first referenced
 * (i.e. before {@link ArenaServerConfig#initialize}) so that the static ladder
 * presets pick up any customised list.</p>
 */
public final class BannedPokemonConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Default ban list, mirroring the original hardcoded set in ArenaLadder. */
    private static final List<String> HARDCODED_DEFAULTS = List.of(
        // Gen I
        "mewtwo", "articuno", "zapdos", "moltres",
        // Gen II
        "lugia", "ho-oh", "raikou", "entei", "suicune",
        // Gen III
        "kyogre", "groudon", "rayquaza", "deoxys",
        "regirock", "regice", "registeel", "latias", "latios",
        // Gen IV
        "dialga", "palkia", "giratina", "arceus",
        "uxie", "mesprit", "azelf", "heatran", "regigigas", "cresselia",
        // Gen V
        "reshiram", "zekrom", "kyurem",
        "cobalion", "terrakion", "virizion",
        "tornadus", "thundurus", "landorus",
        // Gen VI
        "xerneas", "yveltal", "zygarde",
        // Gen VII
        "cosmog", "cosmoem", "solgaleo", "lunala", "necrozma",
        "tapu-koko", "tapu-lele", "tapu-bulu", "tapu-fini",
        // Gen VIII
        "zacian", "zamazenta", "eternatus", "calyrex",
        "kubfu", "urshifu", "regieleki", "regidrago",
        "glastrier", "spectrier", "enamorus",
        // Gen IX
        "koraidon", "miraidon", "terapagos",
        // Treasures of Ruin
        "wo-chien", "chien-pao", "ting-lu", "chi-yu",
        // Hisui / Paldea special forms
        "wyrdeer", "ursaluna", "sneasler"
    );

    /**
     * Live set of banned species keys (plain names, no namespace prefix).
     * Starts as the hardcoded defaults; replaced after {@link #load} runs.
     */
    private static volatile Set<String> restrictedList =
            new HashSet<>(HARDCODED_DEFAULTS);

    private BannedPokemonConfig() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a defensive copy of the current restricted-Pokémon set.
     *
     * <p>Called during {@link cobblemon.arena.ladder.ArenaLadder} class
     * initialisation to seed the static preset ban lists, so it must return
     * whatever data was loaded (or the defaults) at that moment.</p>
     *
     * @return a mutable {@link HashSet} snapshot of all currently banned species
     */
    public static Set<String> getRestrictedList() {
        return new HashSet<>(restrictedList);
    }

    /**
     * Loads (or creates) the banned-Pokémon config file for the given server.
     *
     * <ul>
     *   <li>If the file does <em>not</em> exist, the hardcoded defaults are
     *       written to {@code <world>/cobblemon_arena/banned_pokemon.json} as a
     *       pre-populated template and the defaults continue to be used.</li>
     *   <li>If the file <em>does</em> exist, its contents replace the live
     *       {@link #restrictedList} for this session.</li>
     * </ul>
     *
     * @param server the running Minecraft server instance
     */
    public static void load(MinecraftServer server) {
        File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        File arenaDir = new File(worldDir, "cobblemon_arena");
        if (!arenaDir.exists()) {
            arenaDir.mkdirs();
        }
        File configFile = new File(arenaDir, "banned_pokemon.json");

        if (!configFile.exists()) {
            writeDefaults(configFile);
            CobblemonArena.LOGGER.info(
                "[BannedPokemonConfig] banned_pokemon.json criado com {} entradas padrão: {}",
                HARDCODED_DEFAULTS.size(), configFile.getAbsolutePath());
            // restrictedList already contains the defaults – nothing more to do.
        } else {
            readFromFile(configFile);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Writes the hardcoded defaults to {@code file} using the cobblemon: prefix format. */
    private static void writeDefaults(File file) {
        ConfigData data = new ConfigData();
        data.restricted_legendaries = new ArrayList<>();
        for (String name : HARDCODED_DEFAULTS) {
            data.restricted_legendaries.add("cobblemon:" + name);
        }
        data.notes =
            "Keys must match Cobblemon species keys (lowercase, with cobblemon: prefix)";
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(data, fw);
        } catch (IOException e) {
            CobblemonArena.LOGGER.error(
                "[BannedPokemonConfig] Falha ao escrever banned_pokemon.json", e);
        }
    }

    /** Reads species entries from {@code file} and updates {@link #restrictedList}. */
    private static void readFromFile(File file) {
        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(fr, ConfigData.class);
            if (data == null || data.restricted_legendaries == null) {
                CobblemonArena.LOGGER.warn(
                    "[BannedPokemonConfig] banned_pokemon.json vazio ou inválido" +
                    " — mantendo lista padrão.");
                return;
            }
            Set<String> loaded = new HashSet<>();
            for (String entry : data.restricted_legendaries) {
                if (entry == null || entry.isBlank()) continue;
                String normalized = entry.trim().toLowerCase(Locale.ROOT);
                // Strip optional "cobblemon:" (or any "namespace:") prefix
                int colon = normalized.indexOf(':');
                if (colon >= 0) {
                    normalized = normalized.substring(colon + 1);
                }
                if (!normalized.isBlank()) {
                    loaded.add(normalized);
                }
            }
            restrictedList = loaded;
            CobblemonArena.LOGGER.info(
                "[BannedPokemonConfig] {} espécies banidas carregadas de {}",
                loaded.size(), file.getName());
        } catch (IOException e) {
            CobblemonArena.LOGGER.error(
                "[BannedPokemonConfig] Falha ao ler banned_pokemon.json" +
                " — mantendo lista padrão.", e);
        }
    }

    // ── JSON DTO ──────────────────────────────────────────────────────────────

    private static final class ConfigData {
        List<String> restricted_legendaries;
        String notes;
    }
}
