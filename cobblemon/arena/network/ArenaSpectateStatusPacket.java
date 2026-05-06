package cobblemon.arena.network;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ArenaSpectateStatusPacket(boolean isSpectating) implements CustomPayload {
    public static final CustomPayload.Id<ArenaSpectateStatusPacket> TYPE = new CustomPayload.Id<>(Identifier.of("cobblemon_arena", "spectate_status"));

    public static final PacketCodec<io.netty.buffer.ByteBuf, ArenaSpectateStatusPacket> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBoolean(value.isSpectating()),
        buf -> new ArenaSpectateStatusPacket(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
