package cobblemon.arena.config;

import cobblemon.arena.CobblemonArena;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArenasConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String dimension = "cobblemon:void";
    private List<ArenaPosition> arenas = new ArrayList<>();

    public static class ArenaPosition {
        public int id;
        public int center_x;
        public int center_y;
        public int center_z;

        public ArenaPosition(int id, int center_x, int center_y, int center_z) {
            this.id = id;
            this.center_x = center_x;
            this.center_y = center_y;
            this.center_z = center_z;
        }
    }

    public static ArenasConfig loadOrCreate() {
        File configDir = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_arena").toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File arenasFile = new File(configDir, "arenas.json");

        if (arenasFile.exists()) {
            try (FileReader reader = new FileReader(arenasFile)) {
                ArenasConfig config = GSON.fromJson(reader, ArenasConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                CobblemonArena.LOGGER.error("[ArenasConfig] Falha ao carregar arenas.json, criando padrão.", e);
            }
        }

        // Create default
        ArenasConfig config = new ArenasConfig();
        for (int i = 0; i < 10; i++) {
            config.arenas.add(new ArenaPosition(i, 100000 + (i * 1000), 200, 100000));
        }

        try (FileWriter writer = new FileWriter(arenasFile)) {
            GSON.toJson(config, writer);
            CobblemonArena.LOGGER.info("[ArenasConfig] arenas.json criado com 10 arenas padrão.");
        } catch (IOException e) {
            CobblemonArena.LOGGER.error("[ArenasConfig] Falha ao salvar arenas.json", e);
        }

        return config;
    }

    public String getDimension() {
        return dimension;
    }

    public List<ArenaPosition> getArenas() {
        return arenas;
    }
}
