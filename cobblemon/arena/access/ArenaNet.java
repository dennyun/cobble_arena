package cobblemon.arena.access;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Centralised server→client packet-send utility.
 *
 * <p>All server-side sends of <em>this mod's own packets</em> must go through
 * this class instead of Architectury's {@code NetworkManager.sendToPlayer()}.
 *
 * <p>Reason: Architectury maintains its own internal codec map that is only
 * populated when packets are registered via Architectury's own registration
 * API.  Our packets are registered via Fabric's {@code PayloadTypeRegistry},
 * which is <em>not</em> the same map.  Calling {@code NetworkManager.sendToPlayer()}
 * for packets that exist only in Fabric's registry causes a
 * {@code NullPointerException} ("codec is null") inside
 * {@code NetworkAggregator.collectPackets()} whenever the send originates from
 * the <strong>server-tick thread</strong> rather than from a player-packet
 * handler callback.
 *
 * <p>Fabric's {@code ServerPlayNetworking.send()} uses the correct registry
 * and is safe to call from any thread context (tick, packet handler, etc.).
 * This class adds a defensive {@code try/catch} so a transient connection
 * issue never crashes the server.
 */
public final class ArenaNet {

    private ArenaNet() {}

    /**
     * Sends {@code packet} to {@code player} using Fabric's
     * {@code ServerPlayNetworking}.  Silently swallows any exception so that
     * a single send failure never crashes the server tick.
     *
     * @param player the target player (may be {@code null} — call is a no-op)
     * @param packet the payload to send
     * @param <T>    the payload type
     */
    public static <T extends CustomPayload> void send(
        ServerPlayerEntity player,
        T packet
    ) {
        if (player == null || packet == null) return;
        try {
            ServerPlayNetworking.send(player, packet);
        } catch (Exception e) {
            // Player may have disconnected or channel not yet negotiated.
            // Non-critical — client will resync on next GUI open.
            cobblemon.arena.CobblemonArena.LOGGER.debug(
                "[ArenaNet] Failed to send {} to {}: {}",
                packet.getClass().getSimpleName(),
                player.getName().getString(),
                e.getMessage()
            );
        }
    }
}
