package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ActiveBattlesUpdatePacket(
    List<ActiveBattlePayload> activeBattlesList
) implements CustomPayload {
    public static final CustomPayload.Id<ActiveBattlesUpdatePacket> TYPE = new CustomPayload.Id<>(Identifier.of("cobblemon_arena", "active_battles_update"));

    public static final PacketCodec<ByteBuf, ActiveBattlesUpdatePacket> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeInt(value.activeBattlesList().size());
            for (ActiveBattlePayload b : value.activeBattlesList()) {
                ActiveBattlePayload.CODEC.encode(buf, b);
            }
        },
        buf -> {
            int size = buf.readInt();
            List<ActiveBattlePayload> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(ActiveBattlePayload.CODEC.decode(buf));
            }
            return new ActiveBattlesUpdatePacket(list);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
