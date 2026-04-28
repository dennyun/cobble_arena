package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C — server sends active quest state to a specific player. */
public record SyncQuestDataPacket(
    List<QuestEntryPayload> dailyQuests,
    List<QuestEntryPayload> weeklyQuests
) implements CustomPayload {

    public static final Id<SyncQuestDataPacket> TYPE =
        new Id<>(Identifier.of("cobblemon_arena", "sync_quest_data"));

    public static final PacketCodec<ByteBuf, SyncQuestDataPacket> CODEC = PacketCodec.of(
        (packet, buf) -> {
            buf.writeInt(packet.dailyQuests().size());
            for (QuestEntryPayload e : packet.dailyQuests())
                QuestEntryPayload.CODEC.encode(buf, e);
            buf.writeInt(packet.weeklyQuests().size());
            for (QuestEntryPayload e : packet.weeklyQuests())
                QuestEntryPayload.CODEC.encode(buf, e);
        },
        buf -> {
            int dailyCount = buf.readInt();
            List<QuestEntryPayload> daily = new ArrayList<>(dailyCount);
            for (int i = 0; i < dailyCount; i++)
                daily.add(QuestEntryPayload.CODEC.decode(buf));
            int weeklyCount = buf.readInt();
            List<QuestEntryPayload> weekly = new ArrayList<>(weeklyCount);
            for (int i = 0; i < weeklyCount; i++)
                weekly.add(QuestEntryPayload.CODEC.decode(buf));
            return new SyncQuestDataPacket(daily, weekly);
        }
    );

    /** Compact constructor — defensive copy so the lists are always unmodifiable. */
    public SyncQuestDataPacket(
        List<QuestEntryPayload> dailyQuests,
        List<QuestEntryPayload> weeklyQuests
    ) {
        this.dailyQuests  = dailyQuests  != null ? List.copyOf(dailyQuests)  : List.of();
        this.weeklyQuests = weeklyQuests != null ? List.copyOf(weeklyQuests) : List.of();
    }

    @Override
    public Id<? extends CustomPayload> getId() { return TYPE; }
}
