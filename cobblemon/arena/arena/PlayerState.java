package cobblemon.arena.arena;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class PlayerState {
   private final Vec3d originalPosition;
   private final RegistryKey<World> originalDimension;
   private final float originalYRot;
   private final float originalXRot;
   private final int originalGameMode;
   private final ItemStack[] originalEquipment;

   public PlayerState(ServerPlayerEntity player) {
      this.originalPosition = player.getPos();
      this.originalDimension = player.getWorld().getRegistryKey();
      this.originalYRot = player.getYaw();
      this.originalXRot = player.getPitch();
      this.originalGameMode = player.interactionManager.getGameMode().getId();
      this.originalEquipment = new ItemStack[6];
      this.originalEquipment[0] = player.getEquippedStack(EquipmentSlot.MAINHAND).copy();
      this.originalEquipment[1] = player.getEquippedStack(EquipmentSlot.OFFHAND).copy();
      this.originalEquipment[2] = player.getEquippedStack(EquipmentSlot.FEET).copy();
      this.originalEquipment[3] = player.getEquippedStack(EquipmentSlot.LEGS).copy();
      this.originalEquipment[4] = player.getEquippedStack(EquipmentSlot.CHEST).copy();
      this.originalEquipment[5] = player.getEquippedStack(EquipmentSlot.HEAD).copy();
   }

   public Vec3d getOriginalPosition() {
      return this.originalPosition;
   }

   public RegistryKey<World> getOriginalDimension() {
      return this.originalDimension;
   }

   public float getOriginalYRot() {
      return this.originalYRot;
   }

   public float getOriginalXRot() {
      return this.originalXRot;
   }

   public int getOriginalGameMode() {
      return this.originalGameMode;
   }

   public ItemStack[] getOriginalEquipment() {
      return this.originalEquipment;
   }
}
