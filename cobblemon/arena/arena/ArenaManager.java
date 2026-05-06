package cobblemon.arena.arena;

import cobblemon.arena.CobblemonArena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
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
         CobblemonArena.LOGGER.warn("ArenaManager already initialized!");
      } else {
         ServerWorld level = server.getWorld(dimension);
         ArenaGenerationSavedData generationData = level != null ? ArenaGenerationSavedData.get(level) : null;
         boolean shouldBuildStructures = generationData != null && generationData.needsGeneration();
         if (level == null) {
            CobblemonArena.LOGGER.error("Failed to initialize physical arenas - dimension {} not found", dimension.getValue());
         } else if (shouldBuildStructures) {
            CobblemonArena.LOGGER.info("Arena layout version {} is missing or outdated - building arena structures", generationData.getLayoutVersion());
         } else {
            CobblemonArena.LOGGER.info("Arena structures already exist for layout version {} - skipping rebuild", generationData.getLayoutVersion());
         }

         for (int i = 0; i < 10; i++) {
            BlockPos player1Pos;
            BlockPos player2Pos;
            BlockPos arenaCenter;
            int arenaX = 100000 + i * 1000;
            int arenaZ = 100000;
            arenaCenter = new BlockPos(arenaX + 10, 200, arenaZ);
            player1Pos = new BlockPos(arenaCenter.getX() - 8, 204, arenaCenter.getZ() - 1);
            player2Pos = new BlockPos(arenaCenter.getX() + 8, 204, arenaCenter.getZ() - 1);

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
         CobblemonArena.LOGGER.info("ArenaManager initialized with {} arenas", this.arenaPool.size());
      }
   }

   public Optional<ArenaInstance> claimArena(UUID sessionId) {
      return this.arenaPool.stream().filter(arena -> !arena.isInUse()).findFirst().map(arena -> {
         arena.claim(sessionId);
         CobblemonArena.LOGGER.debug("Arena {} claimed by session {}", arena.getArenaId(), sessionId);
         return (ArenaInstance)arena;
      });
   }

   public void releaseArena(int arenaId) {
      this.arenaPool.stream().filter(arena -> arena.getArenaId() == arenaId).findFirst().ifPresent(arena -> {
         arena.release();
         CobblemonArena.LOGGER.debug("Arena {} released", arenaId);
      });
   }

   public void releaseArenaBySession(UUID sessionId) {
      this.arenaPool.stream().filter(arena -> arena.isInUse() && sessionId.equals(arena.getCurrentSessionId())).findFirst().ifPresent(arena -> {
         arena.release();
         CobblemonArena.LOGGER.debug("Arena {} released by session {}", arena.getArenaId(), sessionId);
      });
   }

   public int getAvailableArenaCount() {
      return (int)this.arenaPool.stream().filter(arena -> !arena.isInUse()).count();
   }

   public int getTotalArenaCount() {
      return this.arenaPool.size();
   }
}
