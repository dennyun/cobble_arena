package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestActiveBattlesPacket() implements CustomPayload {
    public static final CustomPayload.Id<RequestActiveBattlesPacket> TYPE = new CustomPayload.Id<>(Identifier.of("cobblemon_arena", "request_active_battles"));
    
    public static final PacketCodec<ByteBuf, RequestActiveBattlesPacket> CODEC = PacketCodec.of(
        (value, buf) -> {},
        buf -> new RequestActiveBattlesPacket()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
