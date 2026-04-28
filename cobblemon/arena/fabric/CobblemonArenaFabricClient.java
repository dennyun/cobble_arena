package cobblemon.arena.fabric;

import cobblemon.arena.CobblemonArenaClient;
import net.fabricmc.api.ClientModInitializer;

public class CobblemonArenaFabricClient implements ClientModInitializer {
   public void onInitializeClient() {
      CobblemonArenaClient.init();
   }
}
