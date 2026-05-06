package cobblemon.arena.arena;

import cobblemon.arena.CobblemonArena;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Persiste posições de retorno para jogadores que desconectaram durante
 * uma batalha de arena, garantindo que eles retornem à posição original
 * (não à arena) ao reconectar.
 */
public final class OfflineRestoreManager {

    private static final OfflineRestoreManager INSTANCE =
        new OfflineRestoreManager();

    public record RestoreData(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String dimensionId
    ) {}

    private final Map<UUID, RestoreData> pending = new ConcurrentHashMap<>();
    private Path saveFile;

    private OfflineRestoreManager() {}

    public static OfflineRestoreManager getInstance() {
        return INSTANCE;
    }

    /** Chamado no SERVER_STARTED para definir o diretório de save e carregar dados. */
    public void initialize(MinecraftServer server) {
        saveFile = server
            .getRunDirectory()
            .resolve("data/cobblemon_arena/pending_restores.json");
        load();
    }

    /**
     * Registra uma posição de retorno para o jogador.
     * Se o teleporte online falhar (desconexão), o dado persiste para o próximo login.
     */
    public void register(
        UUID uuid,
        Vec3d pos,
        RegistryKey<World> dimension,
        float yaw,
        float pitch
    ) {
        pending.put(
            uuid,
            new RestoreData(
                pos.x,
                pos.y,
                pos.z,
                yaw,
                pitch,
                dimension.getValue().toString()
            )
        );
        save();
    }

    /**
     * Remove a entrada do jogador (chamado após teleporte online bem-sucedido).
     */
    public void clear(UUID uuid) {
        if (pending.remove(uuid) != null) save();
    }

    /**
     * Remove e retorna a entrada de restore pendente (usado no JOIN handler).
     */
    public RestoreData pop(UUID uuid) {
        RestoreData data = pending.remove(uuid);
        if (data != null) save();
        return data;
    }

    /** Aplica o restore pendente ao jogador recém-logado (chamado no JOIN event). */
    public void applyIfPending(MinecraftServer server, UUID uuid) {
        RestoreData data = pop(uuid);
        if (data == null) return;
        server.execute(() -> {
            ServerPlayerEntity player = server
                .getPlayerManager()
                .getPlayer(uuid);
            if (player == null) return;
            try {
                RegistryKey<World> dim = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(data.dimensionId())
                );
                ServerWorld world = server.getWorld(dim);
                if (world == null) world = server.getOverworld();
                player.teleport(
                    world,
                    data.x(),
                    data.y(),
                    data.z(),
                    data.yaw(),
                    data.pitch()
                );
                CobblemonArena.LOGGER.info(
                    "Posição de arena restaurada para {} em ({},{},{})",
                    player.getName().getString(),
                    data.x(),
                    data.y(),
                    data.z()
                );
            } catch (Exception e) {
                CobblemonArena.LOGGER.error(
                    "Falha ao restaurar posição offline para {}: {}",
                    uuid,
                    e.getMessage()
                );
            }
        });
    }

    public void save() {
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, RestoreData> entry : pending.entrySet()) {
                JsonObject obj = new JsonObject();
                RestoreData d = entry.getValue();
                obj.addProperty("x", d.x());
                obj.addProperty("y", d.y());
                obj.addProperty("z", d.z());
                obj.addProperty("yaw", d.yaw());
                obj.addProperty("pitch", d.pitch());
                obj.addProperty("dim", d.dimensionId());
                root.add(entry.getKey().toString(), obj);
            }
            Files.writeString(
                saveFile,
                new GsonBuilder().setPrettyPrinting().create().toJson(root)
            );
        } catch (IOException e) {
            CobblemonArena.LOGGER.error("Falha ao salvar pending restores", e);
        }
    }

    private void load() {
        if (saveFile == null || !Files.exists(saveFile)) return;
        try {
            String json = Files.readString(saveFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            pending.clear();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject obj = entry.getValue().getAsJsonObject();
                pending.put(
                    uuid,
                    new RestoreData(
                        obj.get("x").getAsDouble(),
                        obj.get("y").getAsDouble(),
                        obj.get("z").getAsDouble(),
                        obj.get("yaw").getAsFloat(),
                        obj.get("pitch").getAsFloat(),
                        obj.get("dim").getAsString()
                    )
                );
            }
            if (!pending.isEmpty()) {
                CobblemonArena.LOGGER.info(
                    "Carregados {} restores de arena pendentes",
                    pending.size()
                );
            }
        } catch (Exception e) {
            CobblemonArena.LOGGER.error(
                "Falha ao carregar pending restores",
                e
            );
        }
    }
}
