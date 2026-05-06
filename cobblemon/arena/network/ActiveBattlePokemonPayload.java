package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ActiveBattlePokemonPayload(
    String speciesKey,
    int level,
    boolean fainted,
    String heldItem
) {
    public static final PacketCodec<ByteBuf, ActiveBattlePokemonPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            PacketCodecs.STRING.encode(buf, value.speciesKey());
            buf.writeInt(value.level());
            buf.writeBoolean(value.fainted());
            PacketCodecs.STRING.encode(buf, value.heldItem());
        },
        buf -> {
            String speciesKey = PacketCodecs.STRING.decode(buf);
            int level = buf.readInt();
            boolean fainted = buf.readBoolean();
            String heldItem = PacketCodecs.STRING.decode(buf);
            return new ActiveBattlePokemonPayload(speciesKey, level, fainted, heldItem);
        }
    );
}
