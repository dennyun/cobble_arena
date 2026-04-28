package cobblemon.arena.battle;

import cobblemon.arena.CobblemonArena;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.PlayerEvent.AttackEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public class MovementLockHandler {
   public static void register() {
      PlayerEvent.ATTACK_ENTITY
         .register(
            (AttackEntity)(player, level, entity, hand, hitResult) -> EventResult.pass()
         );
      CobblemonArena.LOGGER.info("Handler de bloqueio de movimento registrado");
   }

   public static boolean shouldCancelMovement(ServerPlayerEntity player) {
      return ArenaBattleManager.getInstance().isMovementLocked(player);
   }
}
