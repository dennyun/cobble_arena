package cobblemon.arena.arena;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class ArenaGenerationSavedData extends PersistentState {
    private static final String DATA_NAME = "cobblemon_arena_generation";
    private static final int CURRENT_LAYOUT_VERSION = 1;

    private boolean generated = false;
    private int layoutVersion = 0;

    public static Type<ArenaGenerationSavedData> factory() {
        return new Type<>(
                ArenaGenerationSavedData::new,
                ArenaGenerationSavedData::load,
                null
        );
    }

    public static ArenaGenerationSavedData get(ServerWorld world) {
        PersistentStateManager storage = world.getPersistentStateManager();
        return storage.getOrCreate(factory(), DATA_NAME);
    }

    private static ArenaGenerationSavedData load(NbtCompound tag, RegistryWrapper.WrapperLookup registries) {
        ArenaGenerationSavedData data = new ArenaGenerationSavedData();
        data.generated = tag.getBoolean("Generated");
        data.layoutVersion = tag.getInt("LayoutVersion");
        return data;
    }

    public boolean needsGeneration() {
        return !generated || layoutVersion < CURRENT_LAYOUT_VERSION;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public void markGenerated() {
        generated = true;
        layoutVersion = CURRENT_LAYOUT_VERSION;
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registries) {
        tag.putBoolean("Generated", generated);
        tag.putInt("LayoutVersion", layoutVersion);
        return tag;
    }
}