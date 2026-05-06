package cobblemon.arena.arena;

import cobblemon.arena.CobblemonArena;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Prevents wild Pokémon from spawning inside any physical arena structure.
 *
 * <p>Each arena occupies a 120×120 block footprint (radius {@value #ARENA_RADIUS})
 * centred on a point derived from the base coordinates and the per-arena
 * spacing.  The check is a simple squared-distance test so no {@code sqrt}
 * is required at runtime.</p>
 *
 * <p>Constants here must stay in sync with {@link ArenaManager}:</p>
 * <ul>
 *   <li>{@link #BASE_X}         → {@code ArenaManager.BASE_X}         (100 000)</li>
 *   <li>{@link #BASE_Z}         → {@code ArenaManager.BASE_Z}         (100 000)</li>
 *   <li>{@link #ARENA_SPACING}  → {@code ArenaManager.ARENA_SPACING}  (1 000)</li>
 *   <li>{@link #ARENA_OFFSET_X} → centre offset applied in ArenaManager    (10)</li>
 * </ul>
 */
public class SpawnPreventionHandler {

    /** Half-width of the spawn-exclusion zone around each arena centre. */
    private static final int ARENA_RADIUS = 60;

    /** Pre-computed squared radius to avoid a {@code sqrt} on every spawn event. */
    private static final double ARENA_RADIUS_SQ =
        (double) ARENA_RADIUS * ARENA_RADIUS;

    /** X coordinate of the first arena, matching {@code ArenaManager.BASE_X}. */
    private static final int BASE_X = 100_000;

    /** Z coordinate of every arena row, matching {@code ArenaManager.BASE_Z}. */
    private static final int BASE_Z = 100_000;

    /**
     * Distance between consecutive arena origins along the X axis.
     * <strong>Must match {@code ArenaManager.ARENA_SPACING} (1 000).</strong>
     * The original bug used 100 here, causing the exclusion zones to be
     * calculated at the wrong positions for arenas 1+.
     */
    private static final int ARENA_SPACING = 1_000;

    /**
     * Additional X offset applied inside each arena slot so the nominal centre
     * matches the actual arena structure centre.  Mirrors the {@code + 10}
     * applied in {@code ArenaManager.initialize()}.
     */
    private static final int ARENA_OFFSET_X = 10;

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Subscribes to {@code POKEMON_ENTITY_SPAWN} at {@link Priority#HIGHEST}
     * and cancels any spawn that falls inside one of the arena exclusion zones.
     *
     * <p>Call this once during mod initialisation (see {@code CobblemonArena.init()}).</p>
     */
    public static void register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(
            Priority.HIGHEST,
            event -> {
                Entity entity = event.getEntity();
                BlockPos spawnPos = entity.getBlockPos();
                if (isInArenaArea(spawnPos)) {
                    event.cancel();
                    CobblemonArena.LOGGER.debug(
                        "Spawn de Pokémon selvagem bloqueado na arena em {}",
                        spawnPos
                    );
                }
                return Unit.INSTANCE;
            }
        );
        CobblemonArena.LOGGER.info(
            "Handler de prevenção de spawn na arena registrado"
        );
    }

    // ── Spatial check ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code pos} lies within {@link #ARENA_RADIUS}
     * blocks of any arena centre.
     *
     * <p>The check iterates over every arena slot currently registered with
     * {@link ArenaManager} so it automatically covers arenas added at runtime
     * without requiring a restart.</p>
     *
     * @param pos the block position of the attempted Pokémon spawn
     * @return {@code true} if the position is inside an arena exclusion zone
     */
    private static boolean isInArenaArea(BlockPos pos) {
        ArenaManager manager = ArenaManager.getInstance();
        int totalArenas = manager.getTotalArenaCount();

        for (int i = 0; i < totalArenas; i++) {
            // Replicates the centre calculation from ArenaManager.initialize():
            //   arenaX  = BASE_X + i * ARENA_SPACING
            //   centerX = arenaX + ARENA_OFFSET_X
            int centerX = BASE_X + i * ARENA_SPACING + ARENA_OFFSET_X;
            int centerZ = BASE_Z;

            double distSq =
                Math.pow(pos.getX() - centerX, 2.0) +
                Math.pow(pos.getZ() - centerZ, 2.0);

            if (distSq <= ARENA_RADIUS_SQ) {
                return true;
            }
        }

        return false;
    }

    public static void registerBlockProtection() {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (isInArenaArea(pos) && !player.hasPermissionLevel(2)) {
                player.sendMessage(net.minecraft.text.Text.literal("§cVoce nao pode quebrar blocos na arena."), true);
                return false;
            }
            return true;
        });

        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (isInArenaArea(hitResult.getBlockPos()) && !player.hasPermissionLevel(2)) {
                if (!player.getMainHandStack().isEmpty() && player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem) {
                    player.sendMessage(net.minecraft.text.Text.literal("§cVoce nao pode colocar blocos na arena."), true);
                    return net.minecraft.util.ActionResult.FAIL;
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        CobblemonArena.LOGGER.info("Handler de protecao de blocos na arena registrado");
    }
}
