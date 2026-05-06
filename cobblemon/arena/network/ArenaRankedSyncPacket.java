package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ArenaRankedSyncPacket(
    String currentSeasonName,
    long currentSeasonStartedAtMs,
    List<RankedLadderSnapshot> rankedLadderSnapshots
) implements CustomPayload {

    public static final CustomPayload.Id<ArenaRankedSyncPacket> TYPE =
        new CustomPayload.Id<>(Identifier.of("cobblemon_arena", "ranked_sync"));

    public static final PacketCodec<ByteBuf, ArenaRankedSyncPacket> CODEC =
        PacketCodec.of(
            (packet, buffer) -> {
                net.minecraft.network.codec.PacketCodecs.STRING.encode(
                    buffer,
                    packet.currentSeasonName()
                );
                buffer.writeLong(packet.currentSeasonStartedAtMs());
                buffer.writeInt(packet.rankedLadderSnapshots().size());
                for (RankedLadderSnapshot snapshot : packet.rankedLadderSnapshots()) {
                    RankedLadderSnapshot.CODEC.encode(buffer, snapshot);
                }
            },
            buffer -> {
                String seasonName =
                    net.minecraft.network.codec.PacketCodecs.STRING.decode(buffer);
                long seasonStartedAtMs = buffer.readLong();
                int size = buffer.readInt();
                List<RankedLadderSnapshot> snapshots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    snapshots.add(RankedLadderSnapshot.CODEC.decode(buffer));
                }
                return new ArenaRankedSyncPacket(
                    seasonName,
                    seasonStartedAtMs,
                    snapshots
                );
            }
        );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
