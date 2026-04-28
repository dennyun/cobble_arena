package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Serializable snapshot of one quest's state for a specific player.
 */
public record QuestEntryPayload(
    String questId,
    String title,
    String description,
    int currentProgress,
    int targetAmount,
    boolean completed,
    boolean claimed,
    String rewardDescription
) {
    public static final PacketCodec<ByteBuf, QuestEntryPayload> CODEC = PacketCodec.of(
        (entry, buf) -> {
            PacketCodecs.STRING.encode(buf, entry.questId());
            PacketCodecs.STRING.encode(buf, entry.title());
            PacketCodecs.STRING.encode(buf, entry.description());
            buf.writeInt(entry.currentProgress());
            buf.writeInt(entry.targetAmount());
            buf.writeBoolean(entry.completed());
            buf.writeBoolean(entry.claimed());
            PacketCodecs.STRING.encode(buf, entry.rewardDescription());
        },
        buf -> new QuestEntryPayload(
            PacketCodecs.STRING.decode(buf),
            PacketCodecs.STRING.decode(buf),
            PacketCodecs.STRING.decode(buf),
            buf.readInt(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            PacketCodecs.STRING.decode(buf)
        )
    );

    /** Progress as a float between 0.0 and 1.0. */
    public float progressFraction() {
        if (targetAmount <= 0) return 1.0f;
        return Math.min(1.0f, (float) currentProgress / targetAmount);
    }
}
