package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Lightweight server→client packet broadcast every ~3 s so the Arena screen's
 * Status Box always shows live data (players online, queue size, active battles,
 * available arenas).
 *
 * <p>Uses Fabric's {@code ServerPlayNetworking.send()} (not Architectury) to
 * avoid the codec-null NPE that can occur during early-tick Architectury sends.</p>
 */
public record ArenaServerStatusPacket(
    int playersOnline,
    int playersInQueue,
    int activeBattles,
    int availableArenas,
    int totalArenas
) implements CustomPayload {

    public static final CustomPayload.Id<ArenaServerStatusPacket> TYPE =
        new CustomPayload.Id<>(
            Identifier.of("cobblemon_arena", "server_status")
        );

    public static final PacketCodec<ByteBuf, ArenaServerStatusPacket> CODEC =
        PacketCodec.of(
            (packet, buf) -> {
                buf.writeInt(packet.playersOnline());
                buf.writeInt(packet.playersInQueue());
                buf.writeInt(packet.activeBattles());
                buf.writeInt(packet.availableArenas());
                buf.writeInt(packet.totalArenas());
            },
            buf -> new ArenaServerStatusPacket(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
            )
        );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
