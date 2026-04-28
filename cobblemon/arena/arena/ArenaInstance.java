package cobblemon.arena.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ArenaInstance {
   private static final int[] SPECTATOR_X_OFFSETS = new int[]{-24, -18, -12, -6, 0, 6, 12, 18, 24};
   private static final int[] SPECTATOR_ROW_DISTANCES = new int[]{17, 19, 21, 23, 25};
   private final int arenaId;
   private final BlockPos player1Position;
   private final BlockPos player2Position;
   private final RegistryKey<World> dimension;
   private boolean inUse;
   private UUID currentSessionId;

   public ArenaInstance(int arenaId, BlockPos player1Position, BlockPos player2Position, RegistryKey<World> dimension) {
      this.arenaId = arenaId;
      this.player1Position = player1Position;
      this.player2Position = player2Position;
      this.dimension = dimension;
      this.inUse = false;
   }

   public int getArenaId() {
      return this.arenaId;
   }

   public BlockPos getPlayer1Position() {
      return this.player1Position;
   }

   public BlockPos getPlayer2Position() {
      return this.player2Position;
   }

   public BlockPos getBattleCenter() {
      return new BlockPos(
         (this.player1Position.getX() + this.player2Position.getX()) / 2,
         this.player1Position.getY(),
         (this.player1Position.getZ() + this.player2Position.getZ()) / 2
      );
   }

   public List<BlockPos> getSpectatorSeatCandidates() {
      BlockPos center = this.getBattleCenter();
      int baseY = center.getY();
      List<BlockPos> candidates = new ArrayList<>();

      for (int side : new int[]{-1, 1}) {
         for (int row = 0; row < SPECTATOR_ROW_DISTANCES.length; row++) {
            int seatY = baseY + 1 + row;
            int seatZ = center.getZ() + side * SPECTATOR_ROW_DISTANCES[row];

            for (int xOffset : SPECTATOR_X_OFFSETS) {
               candidates.add(new BlockPos(center.getX() + xOffset, seatY, seatZ));
            }
         }
      }

      return candidates;
   }

   public RegistryKey<World> getDimension() {
      return this.dimension;
   }

   public boolean isInUse() {
      return this.inUse;
   }

   public UUID getCurrentSessionId() {
      return this.currentSessionId;
   }

   public void claim(UUID sessionId) {
      this.inUse = true;
      this.currentSessionId = sessionId;
   }

   public void release() {
      this.inUse = false;
      this.currentSessionId = null;
   }
}
