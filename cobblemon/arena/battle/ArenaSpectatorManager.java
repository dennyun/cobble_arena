package cobblemon.arena.battle;

import cobblemon.arena.arena.ArenaInstance;
import cobblemon.arena.queue.MatchmakingQueue;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.net.serverhandling.battle.SpectateBattleHandler;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;

public class ArenaSpectatorManager {
   private static final ArenaSpectatorManager INSTANCE = new ArenaSpectatorManager();
   private static final int SPECTATOR_SCAN_XZ_RADIUS = 30;
   private static final int SPECTATOR_SCAN_Y_BELOW = 2;
   private static final int SPECTATOR_SCAN_Y_ABOVE = 8;
   private final Map<UUID, ArenaSpectatorManager.SpectatorState> spectators = new HashMap<>();

   private ArenaSpectatorManager() {
   }

   public static ArenaSpectatorManager getInstance() {
      return INSTANCE;
   }

   public synchronized boolean isSpectatingArena(ServerPlayerEntity player) {
      return player != null && this.spectators.containsKey(player.getUuid());
   }

   public synchronized void spectateRandomBattle(ServerPlayerEntity player) {
      if (player != null && player.getServer() != null) {
         if (this.isSpectatingArena(player)) {
            player.sendMessage(Text.literal("§eVoce ja esta assistindo uma batalha de arena."), false);
         } else if (ArenaBattleManager.getInstance().isInArena(player)) {
            player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver em uma batalha de arena."), false);
         } else if (PlayerExtensionsKt.isInBattle(player)) {
            player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver em batalha."), false);
         } else {
            MatchmakingQueue queue = MatchmakingQueue.getInstance();
            if (!queue.isInQueue(player) && !queue.isInPendingMatch(player.getUuid())) {
               List<ArenaSession> sessions = ArenaBattleManager.getInstance().getSpectatableSessions();
               if (sessions.isEmpty()) {
                  player.sendMessage(Text.literal("§7Nao ha batalhas acontecendo nas arenas no momento."), false);
               } else {
                  ArenaSession session = sessions.get(player.getRandom().nextInt(sessions.size()));
                  PokemonBattle battle = session.getBattleId() != null ? BattleRegistry.getBattle(session.getBattleId()) : null;
                  if (battle == null || !battle.getEnded()) {
                     ServerWorld arenaLevel = player.getServer().getWorld(session.getArena().getDimension());
                     if (arenaLevel == null) {
                        player.sendMessage(Text.literal("§cEssa arena nao esta disponivel para espectadores no momento."), false);
                     } else {
                        BlockPos seat = this.findSpectatorSeat(arenaLevel, session.getArena());
                        if (seat == null) {
                           player.sendMessage(Text.literal("§cNao foi possivel encontrar um assento livre de espectador nessa arena."), false);
                        } else {
                           this.spectators
                              .put(
                                 player.getUuid(),
                                 new ArenaSpectatorManager.SpectatorState(
                                    session.getSessionId(),
                                    session.getBattleId(),
                                    player.getPos(),
                                    player.getWorld().getRegistryKey(),
                                    player.getYaw(),
                                    player.getPitch(),
                                    player.interactionManager.getGameMode()
                                 )
                              );
                           player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                           player.sendAbilitiesUpdate();
                           cobblemon.arena.access.ArenaNet.send(player, new cobblemon.arena.network.ArenaSpectateStatusPacket(true));
                           
                           this.teleportToSeat(player, arenaLevel, seat, session.getArena().getBattleCenter());
                           if (battle != null && session.getBattleId() != null) {
                               SpectateBattleHandler.INSTANCE.spectateBattle(session.getPlayer1(), player);
                           }
                           player.sendMessage(
                              Text.literal(
                                 "§aAssistindo Arena " + (session.getArena().getArenaId() + 1) + ". Use o botao de voltar da batalha para sair do modo espectador."
                              ),
                              false
                           );
                        }
                     }
                  } else {
                     player.sendMessage(Text.literal("§7Nao ha batalhas acontecendo nas arenas no momento."), false);
                  }
               }
            } else {
               player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver na fila ou na contagem regressiva da partida."), false);
            }
         }
      }
   }

   public synchronized void spectateSpecificBattle(ServerPlayerEntity player, UUID sessionId) {
      if (player != null && player.getServer() != null) {
         if (this.isSpectatingArena(player)) {
            player.sendMessage(Text.literal("§eVoce ja esta assistindo uma batalha de arena."), false);
         } else if (ArenaBattleManager.getInstance().isInArena(player)) {
            player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver em uma batalha de arena."), false);
         } else if (PlayerExtensionsKt.isInBattle(player)) {
            player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver em batalha."), false);
         } else {
            MatchmakingQueue queue = MatchmakingQueue.getInstance();
            if (!queue.isInQueue(player) && !queue.isInPendingMatch(player.getUuid())) {
               ArenaSession session = ArenaBattleManager.getInstance().getSession(sessionId);
               if (session == null || !session.isActive()) {
                  player.sendMessage(Text.literal("§cEsta batalha nao esta mais ativa."), false);
               } else {
                  PokemonBattle battle = session.getBattleId() != null ? BattleRegistry.getBattle(session.getBattleId()) : null;
                  if (battle == null || !battle.getEnded()) {
                     ServerWorld arenaLevel = player.getServer().getWorld(session.getArena().getDimension());
                     if (arenaLevel == null) {
                        player.sendMessage(Text.literal("§cEssa arena nao esta disponivel para espectadores no momento."), false);
                     } else {
                        BlockPos seat = this.findSpectatorSeat(arenaLevel, session.getArena());
                        if (seat == null) {
                           player.sendMessage(Text.literal("§cNao foi possivel encontrar um assento livre de espectador nessa arena."), false);
                        } else {
                           this.spectators
                              .put(
                                 player.getUuid(),
                                 new ArenaSpectatorManager.SpectatorState(
                                    session.getSessionId(),
                                    session.getBattleId(),
                                    player.getPos(),
                                    player.getWorld().getRegistryKey(),
                                    player.getYaw(),
                                    player.getPitch(),
                                    player.interactionManager.getGameMode()
                                 )
                              );
                           player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                           player.sendAbilitiesUpdate();
                           cobblemon.arena.access.ArenaNet.send(player, new cobblemon.arena.network.ArenaSpectateStatusPacket(true));
                           
                           this.teleportToSeat(player, arenaLevel, seat, session.getArena().getBattleCenter());
                           if (battle != null && session.getBattleId() != null) {
                               SpectateBattleHandler.INSTANCE.spectateBattle(session.getPlayer1(), player);
                           }
                           player.sendMessage(
                              Text.literal(
                                 "§aAssistindo Arena " + (session.getArena().getArenaId() + 1) + ". Use o botao de voltar da batalha para sair do modo espectador."
                              ),
                              false
                           );
                        }
                     }
                  } else {
                     player.sendMessage(Text.literal("§cEsta batalha nao esta mais ativa."), false);
                  }
               }
            } else {
               player.sendMessage(Text.literal("§cVoce nao pode assistir enquanto estiver na fila ou na contagem regressiva da partida."), false);
            }
         }
      }
   }

   public synchronized void tick(MinecraftServer server) {
      if (server != null && !this.spectators.isEmpty()) {
         for (UUID playerId : new ArrayList<>(this.spectators.keySet())) {
            ArenaSpectatorManager.SpectatorState state = this.spectators.get(playerId);
            if (state != null) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
               if (player == null) {
                  this.spectators.remove(playerId);
               } else {
                  PokemonBattle battle = state.battleId() != null ? BattleRegistry.getBattle(state.battleId()) : null;
                  ArenaSession session = ArenaBattleManager.getInstance().getSession(state.sessionId());
                  
                  // Se o battleId estava nulo mas a sessão agora tem, atualizar e spectate
                  if (state.battleId() == null && session != null && session.getBattleId() != null) {
                      state = new ArenaSpectatorManager.SpectatorState(
                          state.sessionId(), session.getBattleId(), state.position(), state.dimension(), state.yRot(), state.xRot(), state.originalGameMode()
                      );
                      this.spectators.put(playerId, state);
                      battle = BattleRegistry.getBattle(session.getBattleId());
                      if (battle != null) {
                          SpectateBattleHandler.INSTANCE.spectateBattle(session.getPlayer1(), player);
                      }
                  }
                  
                  boolean shouldRestore = session == null || !session.isActive() || battle == null;
                  if (shouldRestore) {
                     this.restorePlayer(player, state, false);
                     this.spectators.remove(playerId);
                  } else if (session != null) {
                     // Check boundary
                     BlockPos center = session.getArena().getBattleCenter();
                     if (player.squaredDistanceTo(center.toCenterPos()) > 900.0) { // ~30 blocks
                         player.teleport(player.getServerWorld(), center.getX() + 0.5, center.getY() + 10, center.getZ() + 0.5, player.getYaw(), player.getPitch());
                         player.sendMessage(Text.literal("§cVoce não pode ir para tão longe da arena."), true);
                     }
                  }
               }
            }
         }
      }
   }

   public synchronized void handleDisconnect(ServerPlayerEntity player) {
      if (player != null) {
         ArenaSpectatorManager.SpectatorState state = this.spectators.remove(player.getUuid());
         if (state != null) {
            PokemonBattle battle = BattleRegistry.getBattle(state.battleId());
            if (battle != null) {
               battle.getSpectators().remove(player.getUuid());
            }

            this.restorePlayerMode(player, state);
         }
      }
   }

   public synchronized void leaveSpectate(ServerPlayerEntity player) {
      if (player != null) {
         ArenaSpectatorManager.SpectatorState state = this.spectators.remove(player.getUuid());
         if (state != null) {
            PokemonBattle battle = state.battleId() != null ? BattleRegistry.getBattle(state.battleId()) : null;
            if (battle != null) {
               battle.getSpectators().remove(player.getUuid());
            }
            this.restorePlayer(player, state, true);
         }
      }
   }

   public synchronized void restoreAll(MinecraftServer server) {
      if (server != null && !this.spectators.isEmpty()) {
         for (UUID playerId : new ArrayList<>(this.spectators.keySet())) {
            ArenaSpectatorManager.SpectatorState state = this.spectators.remove(playerId);
            if (state != null) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
               if (player != null) {
                  PokemonBattle battle = BattleRegistry.getBattle(state.battleId());
                  if (battle != null) {
                     battle.getSpectators().remove(playerId);
                  }

                  this.restorePlayer(player, state, false);
               }
            }
         }
      } else {
         this.spectators.clear();
      }
   }

    private void restorePlayerMode(ServerPlayerEntity player, ArenaSpectatorManager.SpectatorState state) {
       player.changeGameMode(state.originalGameMode());
       player.setInvisible(false);
       if (state.originalGameMode() != net.minecraft.world.GameMode.CREATIVE && state.originalGameMode() != net.minecraft.world.GameMode.SPECTATOR) {
           player.getAbilities().allowFlying = false;
           player.getAbilities().flying = false;
       }
       player.sendAbilitiesUpdate();
       cobblemon.arena.access.ArenaNet.send(player, new cobblemon.arena.network.ArenaSpectateStatusPacket(false));
    }

    private void restorePlayer(ServerPlayerEntity player, ArenaSpectatorManager.SpectatorState state, boolean notify) {
       this.restorePlayerMode(player, state);
      ServerWorld targetLevel = player.getServer().getWorld(state.dimension());
      if (targetLevel != null) {
         BlockPos targetPos = BlockPos.ofFloored(state.position());
         this.ensureChunkLoaded(targetLevel, targetPos);
         player.teleport(targetLevel, state.position().x, state.position().y, state.position().z, state.yRot(), state.xRot());
         player.fallDistance = 0.0F;
         this.ensureChunkLoaded(targetLevel, targetPos);
      }

      if (notify) {
         player.sendMessage(Text.literal("§7Voce saiu da espectacao da Arena."), false);
      }
   }

   private BlockPos findSpectatorSeat(ServerWorld level, ArenaInstance arena) {
      BlockPos center = arena.getBattleCenter();
      List<BlockPos> candidates = new ArrayList<>();
      int cX = center.getX();
      int cY = center.getY();
      int cZ = center.getZ();
      int minX = cX - SPECTATOR_SCAN_XZ_RADIUS;
      int maxX = cX + SPECTATOR_SCAN_XZ_RADIUS;
      int minY = cY - SPECTATOR_SCAN_Y_BELOW;
      int maxY = cY + SPECTATOR_SCAN_Y_ABOVE;
      int minZ = cZ - SPECTATOR_SCAN_XZ_RADIUS;
      int maxZ = cZ + SPECTATOR_SCAN_XZ_RADIUS;

      for (int y = minY; y <= maxY; y++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
               BlockPos benchBlock = new BlockPos(x, y, z);
               if (level.getBlockState(benchBlock).isOf(Blocks.SMOOTH_STONE_SLAB)) {
                  BlockPos seat = benchBlock.up();
                  if (this.isSeatUsable(level, seat)) {
                     candidates.add(seat);
                  }
               }
            }
         }
      }

      if (candidates.isEmpty()) {
         return center.up(2);
      } else {
         candidates.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(center)));
         int startIndex = level.getRandom().nextInt(candidates.size());

         for (int i = 0; i < candidates.size(); i++) {
            BlockPos candidate = candidates.get((startIndex + i) % candidates.size());
            if (this.isSeatUsable(level, candidate)) {
               return candidate;
            }
         }

         return center.up(2);
      }
   }

   private boolean isSeatUsable(ServerWorld level, BlockPos seat) {
      Box seatBox = new Box(
         seat.getX() + 0.1,
         seat.getY(),
         seat.getZ() + 0.1,
         seat.getX() + 0.9,
         seat.getY() + 2.0,
         seat.getZ() + 0.9
      );

      if (!level.getEntitiesByClass(ServerPlayerEntity.class, seatBox, p -> true).isEmpty()) {
         return false;
      }

      return level.getBlockState(seat.down()).isSolidBlock(level, seat.down())
         && level.getBlockState(seat).isAir()
         && level.getBlockState(seat.up()).isAir();
   }

   private void teleportToSeat(ServerPlayerEntity player, ServerWorld level, BlockPos seat, BlockPos focus) {
      this.ensureChunkLoaded(level, seat);
      double viewX = seat.getX() + 0.5;
      double viewY = seat.getY();
      double viewZ = seat.getZ() + 0.5;
      double deltaX = focus.getX() + 0.5 - viewX;
      double deltaZ = focus.getZ() + 0.5 - viewZ;
      float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
      player.teleport(level, viewX, viewY, viewZ, yaw, 0.0F);
      player.fallDistance = 0.0F;
      this.ensureChunkLoaded(level, seat);
   }

   private void ensureChunkLoaded(ServerWorld level, BlockPos pos) {
      level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
   }

   private static final class SpectatorModifierIds {
      private static final Identifier MOVEMENT_LOCK = Identifier.of("cobblemon_arena", "spectator_movement_lock");
      private static final Identifier JUMP_LOCK = Identifier.of("cobblemon_arena", "spectator_jump_lock");
   }

   private record SpectatorState(UUID sessionId, UUID battleId, net.minecraft.util.math.Vec3d position, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, float yRot, float xRot, net.minecraft.world.GameMode originalGameMode) {
   }
}
