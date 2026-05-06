package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record ActiveBattlePayload(
    UUID sessionId,
    String formatName,
    String player1Name,
    int player1Elo,
    List<ActiveBattlePokemonPayload> player1Team,
    String player2Name,
    int player2Elo,
    List<ActiveBattlePokemonPayload> player2Team,
    int turn,
    long battleStartTimeMs,
    int viewers,
    boolean isRanked
) {
    public static final PacketCodec<ByteBuf, ActiveBattlePayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeLong(value.sessionId().getMostSignificantBits());
            buf.writeLong(value.sessionId().getLeastSignificantBits());
            PacketCodecs.STRING.encode(buf, value.formatName());
            PacketCodecs.STRING.encode(buf, value.player1Name());
            buf.writeInt(value.player1Elo());
            buf.writeInt(value.player1Team().size());
            for (ActiveBattlePokemonPayload pokemon : value.player1Team()) {
                ActiveBattlePokemonPayload.CODEC.encode(buf, pokemon);
            }
            PacketCodecs.STRING.encode(buf, value.player2Name());
            buf.writeInt(value.player2Elo());
            buf.writeInt(value.player2Team().size());
            for (ActiveBattlePokemonPayload pokemon : value.player2Team()) {
                ActiveBattlePokemonPayload.CODEC.encode(buf, pokemon);
            }
            buf.writeInt(value.turn());
            buf.writeLong(value.battleStartTimeMs());
            buf.writeInt(value.viewers());
            buf.writeBoolean(value.isRanked());
        },
        buf -> {
            UUID sessionId = new UUID(buf.readLong(), buf.readLong());
            String formatName = PacketCodecs.STRING.decode(buf);
            String player1Name = PacketCodecs.STRING.decode(buf);
            int player1Elo = buf.readInt();
            int p1Size = buf.readInt();
            List<ActiveBattlePokemonPayload> player1Team = new ArrayList<>(p1Size);
            for (int i = 0; i < p1Size; i++) {
                player1Team.add(ActiveBattlePokemonPayload.CODEC.decode(buf));
            }
            String player2Name = PacketCodecs.STRING.decode(buf);
            int player2Elo = buf.readInt();
            int p2Size = buf.readInt();
            List<ActiveBattlePokemonPayload> player2Team = new ArrayList<>(p2Size);
            for (int i = 0; i < p2Size; i++) {
                player2Team.add(ActiveBattlePokemonPayload.CODEC.decode(buf));
            }
            int turn = buf.readInt();
            long battleStartTimeMs = buf.readLong();
            int viewers = buf.readInt();
            boolean isRanked = buf.readBoolean();
            return new ActiveBattlePayload(
                sessionId, formatName, player1Name, player1Elo, player1Team,
                player2Name, player2Elo, player2Team, turn, battleStartTimeMs, viewers, isRanked
            );
        }
    );
}
