package cobblemon.arena.fabric;

import cobblemon.arena.CobblemonArena;
import net.fabricmc.api.ModInitializer;

public class CobblemonArenaFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CobblemonArena.init();
    }
}

