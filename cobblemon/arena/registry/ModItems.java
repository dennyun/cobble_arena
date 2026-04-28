package cobblemon.arena.registry;

import cobblemon.arena.CobblemonArena;
import cobblemon.arena.item.ArenaOrbItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {

    public static final ArenaOrbItem ARENA_ORB = new ArenaOrbItem(
        new Item.Settings().maxCount(1)
    );

    private ModItems() {}

    public static void register() {
        Registry.register(
            Registries.ITEM,
            Identifier.of(CobblemonArena.MOD_ID, "arena_orb"),
            ARENA_ORB
        );
        CobblemonArena.LOGGER.info("Arena items registered.");
    }
}
