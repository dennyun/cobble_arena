package cobblemon.arena.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S — player requests to claim a completed quest reward via GUI. */
public record ClaimQuestRewardPacket(String questId) implements CustomPayload {

    public static final Id<ClaimQuestRewardPacket> TYPE =
        new Id<>(Identifier.of("cobblemon_arena", "claim_quest_reward"));

    public static final PacketCodec<ByteBuf, ClaimQuestRewardPacket> CODEC =
        PacketCodec.tuple(
            PacketCodecs.STRING, ClaimQuestRewardPacket::questId,
            ClaimQuestRewardPacket::new
        );

    @Override
    public Id<? extends CustomPayload> getId() { return TYPE; }
}
