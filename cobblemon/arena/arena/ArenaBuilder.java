package cobblemon.arena.arena;

import cobblemon.arena.CobblemonArena;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

public class ArenaBuilder {
   public static void buildArena(ServerWorld level, BlockPos center) {
      StructureTemplateManager templateManager = level.getStructureTemplateManager();
      StructurePlacementData placeSettings = new StructurePlacementData().setIgnoreEntities(true);
      int minChunkX = center.getX() - 50 >> 4;
      int maxChunkX = center.getX() + 50 >> 4;
      int minChunkZ = center.getZ() - 50 >> 4;
      int maxChunkZ = center.getZ() + 50 >> 4;

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            level.getChunk(chunkX, chunkZ);
         }
      }

      placeQuadrant(level, templateManager, placeSettings, "cobblemon_arena:arenanw", center, ArenaBuilder.Quadrant.NW);
      placeQuadrant(level, templateManager, placeSettings, "cobblemon_arena:arenane", center, ArenaBuilder.Quadrant.NE);
      placeQuadrant(level, templateManager, placeSettings, "cobblemon_arena:arenasw", center, ArenaBuilder.Quadrant.SW);
      placeQuadrant(level, templateManager, placeSettings, "cobblemon_arena:arenase", center, ArenaBuilder.Quadrant.SE);
      CobblemonArena.LOGGER
         .info("Built arena from structures at ({}, {}, {})", new Object[]{center.getX(), center.getY(), center.getZ()});
      CobblemonArena.LOGGER.info("Partes da arena posicionadas: NW=arenanw(37x30x37), NE=arenane(36x30x37), SW=arenasw(37x30x36), SE=arenase(36x30x36)");
   }

   private static void placeQuadrant(
      ServerWorld level,
      StructureTemplateManager templateManager,
      StructurePlacementData placeSettings,
      String structurePath,
      BlockPos centerSeam,
      ArenaBuilder.Quadrant quadrant
   ) {
      StructureTemplate template = loadTemplate(templateManager, structurePath);
      if (template == null) {
         CobblemonArena.LOGGER
            .error(
               "Failed to load structure: {}. Make sure the NBT file exists in data/cobblemon_arena/structures/ (or legacy data/cobblemon_arena/structure/)",
               structurePath
            );
      } else {
         Vec3i size = template.getSize();
         int sizeX = size.getX();
         int sizeZ = size.getZ();
         int evenShiftX = sizeX % 2 == 0 ? 1 : 0;
         int evenShiftZ = sizeZ % 2 == 0 ? 1 : 0;
         int originX = centerSeam.getX();
         int originY = centerSeam.getY();
         int originZ = centerSeam.getZ();
         switch (quadrant) {
            case NW:
               originX = centerSeam.getX() - (sizeX - 1) + evenShiftX - 1;
               originZ = centerSeam.getZ() - (sizeZ - 1) + evenShiftZ - 1;
               break;
            case NE:
               originX = centerSeam.getX();
               originZ = centerSeam.getZ() - (sizeZ - 1) + evenShiftZ - 1;
               break;
            case SW:
               originX = centerSeam.getX() - (sizeX - 1) + evenShiftX - 1;
               originZ = centerSeam.getZ();
               break;
            case SE:
               originX = centerSeam.getX();
               originZ = centerSeam.getZ();
         }

         BlockPos origin = new BlockPos(originX, originY, originZ);
         template.place(level, origin, origin, placeSettings, level.getRandom(), 2);
         CobblemonArena.LOGGER
            .info(
               "Placed quadrant {} ({}) centerSeam=({}, {}, {}) origin=({}, {}, {}) sizeX={} sizeZ={} evenShiftX={} evenShiftZ={}",
               new Object[]{
                  quadrant,
                  structurePath,
                  centerSeam.getX(),
                  centerSeam.getY(),
                  centerSeam.getZ(),
                  origin.getX(),
                  origin.getY(),
                  origin.getZ(),
                  sizeX,
                  sizeZ,
                  evenShiftX,
                  evenShiftZ
               }
            );
      }
   }

   private static StructureTemplate loadTemplate(StructureTemplateManager templateManager, String structurePath) {
      Identifier structureLocation = Identifier.of(structurePath);
      StructureTemplate template = templateManager.getTemplate(structureLocation).orElse(null);
      if (template == null) {
         Identifier legacyLocation = Identifier.of("cobblemon_arena", "structure/" + structureLocation.getPath());
         template = templateManager.getTemplate(legacyLocation).orElse(null);
         if (template != null) {
            CobblemonArena.LOGGER.debug("Estrutura {} resolvida via caminho legado {}", structureLocation, legacyLocation);
         }
      }

      return template;
   }

   private static enum Quadrant {
      NW,
      NE,
      SW,
      SE;
   }
}
