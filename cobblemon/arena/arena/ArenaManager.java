package cobblemon.arena.arena;

import cobblemon.arena.CobblemonArena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ArenaManager {
   private static final ArenaManager INSTANCE = new ArenaManager();
   private static final int ARENA_COUNT = 10;
   private static final int BASE_X = 100000;
   private static final int BASE_Z = 100000;
   private static final int ARENA_Y = 200;
   private static final int ARENA_SPACING = 1000;
   private final List<ArenaInstance> arenaPool = new ArrayList<>();
   private boolean initialized = false;

   private ArenaManager() {
   }

   public static ArenaManager getInstance() {
      return INSTANCE;
   }

   public void initialize(RegistryKey<World> dimension, MinecraftServer server) {
      if (this.initialized) {
         CobblemonArena.LOGGER.warn("ArenaManager ja inicializado!");
      } else {
         ServerWorld level = server.getWorld(dimension);
         ArenaGenerationSavedData generationData = level != null ? ArenaGenerationSavedData.get(level) : null;
         boolean shouldBuildStructures = generationData != null && generationData.needsGeneration();
         if (level == null) {
            CobblemonArena.LOGGER.error("Falha ao inicializar arenas fisicas - dimensao {} nao encontrada", dimension.getValue());
         } else if (shouldBuildStructures) {
            CobblemonArena.LOGGER.info("Versao {} do layout da arena ausente ou desatualizada - construindo estruturas", generationData.getLayoutVersion());
         } else {
            CobblemonArena.LOGGER.info("Estruturas da arena ja existem para o layout {} - pulando reconstrucao", generationData.getLayoutVersion());
         }

         for (int i = 0; i < ARENA_COUNT; i++) {
            int arenaX = BASE_X + i * ARENA_SPACING;
            int arenaZ = BASE_Z;
            BlockPos arenaCenter = new BlockPos(arenaX + 10, ARENA_Y, arenaZ);
            BlockPos player1Pos = new BlockPos(arenaCenter.getX() - 8, ARENA_Y + 4, arenaCenter.getZ() - 1);
            BlockPos player2Pos = new BlockPos(arenaCenter.getX() + 8, ARENA_Y + 4, arenaCenter.getZ() - 1);

            ArenaInstance arena = new ArenaInstance(i, player1Pos, player2Pos, dimension);
            this.arenaPool.add(arena);
            if (shouldBuildStructures && level != null) {
               ArenaBuilder.buildArena(level, arenaCenter);
               CobblemonArena.LOGGER
                  .info("Built arena {} at ({}, {}, {})", new Object[]{i, arenaCenter.getX(), arenaCenter.getY(), arenaCenter.getZ()});
            }
         }

         if (shouldBuildStructures && generationData != null) {
            generationData.markGenerated();
         }

         this.initialized = true;
         CobblemonArena.LOGGER.info("ArenaManager inicializado com {} arenas", this.arenaPool.size());
      }
   }

   public Optional<ArenaInstance> claimArena(UUID sessionId) {
      return this.arenaPool.stream().filter(arena -> !arena.isInUse()).findFirst().map(arena -> {
         arena.claim(sessionId);
         CobblemonArena.LOGGER.debug("Arena {} reservada pela sessao {}", arena.getArenaId(), sessionId);
         return (ArenaInstance)arena;
      });
   }

   public void releaseArena(int arenaId) {
      this.arenaPool.stream().filter(arena -> arena.getArenaId() == arenaId).findFirst().ifPresent(arena -> {
         arena.release();
         CobblemonArena.LOGGER.debug("Arena {} liberada", arenaId);
      });
   }

   public void releaseArenaBySession(UUID sessionId) {
      this.arenaPool.stream().filter(arena -> arena.isInUse() && sessionId.equals(arena.getCurrentSessionId())).findFirst().ifPresent(arena -> {
         arena.release();
         CobblemonArena.LOGGER.debug("Arena {} liberada pela sessao {}", arena.getArenaId(), sessionId);
      });
   }

   public int getAvailableArenaCount() {
      return (int)this.arenaPool.stream().filter(arena -> !arena.isInUse()).count();
   }

   public int getTotalArenaCount() {
      return this.arenaPool.size();
   }
}
