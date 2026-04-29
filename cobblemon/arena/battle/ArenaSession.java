package cobblemon.arena.battle;

import cobblemon.arena.arena.ArenaInstance;
import cobblemon.arena.arena.PlayerState;
import cobblemon.arena.ladder.ArenaLadder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.entity.EquipmentSlot;

public class ArenaSession {
   private final UUID sessionId;
   private final ServerPlayerEntity player1;
   private final ServerPlayerEntity player2;
   private final ArenaInstance arena;
   private final PlayerState player1State;
   private final PlayerState player2State;
   private BlockPos player1ViewPos;
   private BlockPos player2ViewPos;
   private GameMode player1OriginalGameMode;
   private GameMode player2OriginalGameMode;
   private boolean player1OriginalFovEffects;
   private boolean player2OriginalFovEffects;
   private boolean active = true;
   private ArenaLadder ladder;
   private boolean isRankedMatch = false;
   private boolean isQueueMatch = false;
   private UUID battleId;
   private long pendingBattleStartTick = -1L;
   private long battleInitializationDeadlineTick = -1L;
   private boolean battleLaunchInProgress = false;
   private List<ArenaSession.TeamPokemonSnapshot> player1TeamSnapshot = List.of();
   private List<ArenaSession.TeamPokemonSnapshot> player2TeamSnapshot = List.of();
   private final List<UUID> player1SelectedLeadIds = new ArrayList<>();
   private final List<UUID> player2SelectedLeadIds = new ArrayList<>();
   private final Map<UUID, ArenaSession.DecisionTimerState> decisionTimers = new HashMap<>();

   public ArenaSession(ServerPlayerEntity player1, ServerPlayerEntity player2, ArenaInstance arena) {
      this.sessionId = UUID.randomUUID();
      this.player1 = player1;
      this.player2 = player2;
      this.arena = arena;
      this.player1State = new PlayerState(player1);
      this.player2State = new PlayerState(player2);
      this.player1OriginalGameMode = player1.interactionManager.getGameMode();
      this.player2OriginalGameMode = player2.interactionManager.getGameMode();
      this.player1OriginalFovEffects = false;
      this.player2OriginalFovEffects = false;
      this.decisionTimers.put(player1.getUuid(), new ArenaSession.DecisionTimerState());
      this.decisionTimers.put(player2.getUuid(), new ArenaSession.DecisionTimerState());
   }

   public UUID getSessionId() {
      return this.sessionId;
   }

   public ServerPlayerEntity getPlayer1() {
      return this.player1;
   }

   public ServerPlayerEntity getPlayer2() {
      return this.player2;
   }

   public ArenaInstance getArena() {
      return this.arena;
   }

   public PlayerState getPlayer1State() {
      return this.player1State;
   }

   public PlayerState getPlayer2State() {
      return this.player2State;
   }

   public ServerPlayerEntity getOpponent(ServerPlayerEntity player) {
      if (player.getUuid().equals(this.player1.getUuid())) {
         return this.player2;
      } else {
         return player.getUuid().equals(this.player2.getUuid()) ? this.player1 : null;
      }
   }

   public boolean isActive() {
      return this.active;
   }

   public void setMatchMetadata(ArenaLadder ladder, boolean isQueueMatch) {
      this.ladder = ladder;
      this.isRankedMatch = ladder != null && ladder.isRanked();
      this.isQueueMatch = isQueueMatch;
   }

   public ArenaLadder getLadder() {
      return this.ladder;
   }

   public boolean isRankedMatch() {
      return this.isRankedMatch;
   }

   public boolean isQueueMatch() {
      return this.isQueueMatch;
   }

   public void setBattleId(UUID battleId) {
      this.battleId = battleId;
   }

   public UUID getBattleId() {
      return this.battleId;
   }

   public void armBattleInitializationDeadline(long deadlineTick) {
      this.battleInitializationDeadlineTick = deadlineTick;
   }

   public void clearBattleInitializationDeadline() {
      this.battleInitializationDeadlineTick = -1L;
   }

   public boolean hasBattleInitializationDeadline() {
      return this.battleInitializationDeadlineTick >= 0L;
   }

   public boolean hasBattleInitializationTimedOut(long currentTick) {
      return this.battleInitializationDeadlineTick >= 0L && currentTick >= this.battleInitializationDeadlineTick;
   }

   public void scheduleBattleStart(long startTick) {
      this.pendingBattleStartTick = startTick;
   }

   public boolean isBattleLaunchInProgress() {
      return this.battleLaunchInProgress;
   }

   public void setBattleLaunchInProgress(boolean battleLaunchInProgress) {
      this.battleLaunchInProgress = battleLaunchInProgress;
   }

   public boolean hasPendingBattleStart() {
      return this.pendingBattleStartTick >= 0L && this.battleId == null;
   }

   public boolean shouldStartBattle(long currentTick) {
      return this.hasPendingBattleStart() && (this.hasBothPlayersReadyLeads() || currentTick >= this.pendingBattleStartTick);
   }

   public void clearPendingBattleStart() {
      this.pendingBattleStartTick = -1L;
   }

   public boolean hasLeadSelection(ServerPlayerEntity player) {
      return this.hasMinimumLeadSelection(player);
   }

   public boolean hasBothLeadSelections() {
      return this.hasBothPlayersReadyLeads();
   }

   public void setSelectedLead(ServerPlayerEntity player, UUID pokemonId) {
      if (pokemonId == null) return;
      this.setSelectedLeads(player, List.of(pokemonId));
   }

   public UUID getSelectedLead(ServerPlayerEntity player) {
      List<UUID> ids = this.getSelectedLeads(player);
      return ids.isEmpty() ? null : ids.get(0);
   }

   public void setSelectedLeads(ServerPlayerEntity player, List<UUID> pokemonIds) {
      List<UUID> target = this.selectedLeadList(player);
      if (target == null) return;
      target.clear();
      if (pokemonIds == null || pokemonIds.isEmpty()) return;

      int required = this.getRequiredLeadCount();
      for (UUID id : pokemonIds) {
         if (id == null || target.contains(id)) continue;
         target.add(id);
         if (target.size() >= required) break;
      }
   }

   public List<UUID> getSelectedLeads(ServerPlayerEntity player) {
      List<UUID> src = this.selectedLeadList(player);
      return src == null ? List.of() : List.copyOf(src);
   }

   public int getRequiredLeadCount() {
      String battleTypeId = this.ladder != null ? this.ladder.getBattleTypeId() : "singles";
      if ("triples".equalsIgnoreCase(battleTypeId)) return 3;
      if ("doubles".equalsIgnoreCase(battleTypeId)) return 2;
      return 1;
   }

   public boolean hasMinimumLeadSelection(ServerPlayerEntity player) {
      List<UUID> selected = this.selectedLeadList(player);
      return selected != null && selected.size() >= this.getRequiredLeadCount();
   }

   public boolean hasBothPlayersReadyLeads() {
      return this.hasMinimumLeadSelection(this.player1) && this.hasMinimumLeadSelection(this.player2);
   }

   private List<UUID> selectedLeadList(ServerPlayerEntity player) {
      if (player == null) return null;
      if (player.getUuid().equals(this.player1.getUuid())) return this.player1SelectedLeadIds;
      if (player.getUuid().equals(this.player2.getUuid())) return this.player2SelectedLeadIds;
      return null;
   }

   public void setTeamSnapshots(List<ArenaSession.TeamPokemonSnapshot> player1TeamSnapshot, List<ArenaSession.TeamPokemonSnapshot> player2TeamSnapshot) {
      this.player1TeamSnapshot = immutableTeamSnapshot(player1TeamSnapshot);
      this.player2TeamSnapshot = immutableTeamSnapshot(player2TeamSnapshot);
   }

   public List<ArenaSession.TeamPokemonSnapshot> getTeamSnapshot(ServerPlayerEntity player) {
      if (player == null) {
         return List.of();
      } else if (player.getUuid().equals(this.player1.getUuid())) {
         return this.player1TeamSnapshot;
      } else {
         return player.getUuid().equals(this.player2.getUuid()) ? this.player2TeamSnapshot : List.of();
      }
   }

   public void armDecisionTimer(ServerPlayerEntity player, long deadlineTick) {
      ArenaSession.DecisionTimerState state = this.timerState(player);
      if (state != null) {
         state.deadlineTick = deadlineTick;
         state.lastWarningSeconds = Integer.MAX_VALUE;
      }
   }

   public void resetDecisionTimer(ServerPlayerEntity player) {
      ArenaSession.DecisionTimerState state = this.timerState(player);
      if (state != null) {
         state.deadlineTick = -1L;
         state.lastWarningSeconds = Integer.MAX_VALUE;
      }
   }

   public long getDecisionDeadlineTick(ServerPlayerEntity player) {
      ArenaSession.DecisionTimerState state = this.timerState(player);
      return state != null ? state.deadlineTick : -1L;
   }

   public int getLastWarningSeconds(ServerPlayerEntity player) {
      ArenaSession.DecisionTimerState state = this.timerState(player);
      return state != null ? state.lastWarningSeconds : Integer.MAX_VALUE;
   }

   public void setLastWarningSeconds(ServerPlayerEntity player, int seconds) {
      ArenaSession.DecisionTimerState state = this.timerState(player);
      if (state != null) {
         state.lastWarningSeconds = seconds;
      }
   }

   public void teleportToArena() {
      BlockPos player1BattlePos = this.arena.getPlayer1Position();
      BlockPos player2BattlePos = this.arena.getPlayer2Position();
      ServerWorld arenaLevel = this.player1.getServer().getWorld(this.arena.getDimension());
      if (arenaLevel == null) {
         throw new IllegalStateException("Arena dimension not found: " + this.arena.getDimension().getValue());
      } else {
         this.ensureChunkLoaded(arenaLevel, player1BattlePos);
         this.ensureChunkLoaded(arenaLevel, player2BattlePos);
         this.ensureChunkLoaded(
            arenaLevel,
            new BlockPos(
               (player1BattlePos.getX() + player2BattlePos.getX()) / 2,
               player1BattlePos.getY(),
               (player1BattlePos.getZ() + player2BattlePos.getZ()) / 2
            )
         );
         this.player1ViewPos = player1BattlePos;
         this.player2ViewPos = player2BattlePos;
         double centerX = (player1BattlePos.getX() + player2BattlePos.getX()) / 2.0;
         double centerY = player1BattlePos.getY();
         double centerZ = (player1BattlePos.getZ() + player2BattlePos.getZ()) / 2.0;
         this.teleportPlayerToView(this.player1, this.player1ViewPos, this.arena.getDimension(), centerX, centerY, centerZ);
         this.teleportPlayerToView(this.player2, this.player2ViewPos, this.arena.getDimension(), centerX, centerY, centerZ);
         this.player1.getAbilities().flying = false;
         this.player1.getAbilities().allowFlying = false;
         this.player1.sendAbilitiesUpdate();
         this.player2.getAbilities().flying = false;
         this.player2.getAbilities().allowFlying = false;
         this.player2.sendAbilitiesUpdate();
         this.disableFovEffects(this.player1);
         this.disableFovEffects(this.player2);
         this.applyInvisibility(this.player1);
         this.applyInvisibility(this.player2);
         this.hideEquipment(this.player1);
         this.hideEquipment(this.player2);
         this.applyMovementLock(this.player1);
         this.applyMovementLock(this.player2);
      }
   }

   public void restorePlayers() {
      this.removeInvisibility(this.player1);
      this.removeInvisibility(this.player2);
      this.showEquipment(this.player1);
      this.showEquipment(this.player2);
      this.removeMovementLock(this.player1);
      this.removeMovementLock(this.player2);
      this.player1.getAbilities().allowFlying = this.player1OriginalGameMode == GameMode.CREATIVE || this.player1OriginalGameMode == GameMode.SPECTATOR;
      this.player1.sendAbilitiesUpdate();
      this.player2.getAbilities().allowFlying = this.player2OriginalGameMode == GameMode.CREATIVE || this.player2OriginalGameMode == GameMode.SPECTATOR;
      this.player2.sendAbilitiesUpdate();
      this.restoreFovEffects(this.player1, this.player1OriginalFovEffects);
      this.restoreFovEffects(this.player2, this.player2OriginalFovEffects);
      this.teleportPlayer(this.player1, this.player1State.getOriginalPosition(), this.player1State.getOriginalDimension());
      this.teleportPlayer(this.player2, this.player2State.getOriginalPosition(), this.player2State.getOriginalDimension());
      this.player1.setYaw(this.player1State.getOriginalYRot());
      this.player1.setPitch(this.player1State.getOriginalXRot());
      this.player2.setYaw(this.player2State.getOriginalYRot());
      this.player2.setPitch(this.player2State.getOriginalXRot());
   }

   private void teleportPlayerToView(ServerPlayerEntity player, BlockPos pos, RegistryKey<World> dimension, double targetX, double targetY, double targetZ) {
      ServerWorld targetLevel = player.getServer().getWorld(dimension);
      if (targetLevel == null) {
         throw new IllegalStateException("Arena dimension not found: " + dimension.getValue());
      } else {
         this.ensureChunkLoaded(targetLevel, pos);
         double viewX = pos.getX() + 0.5;
         double viewY = pos.getY();
         double viewZ = pos.getZ() + 0.5;
         double deltaX = targetX - viewX;
         double deltaZ = targetZ - viewZ;
         float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
         float pitch = 0.0F;
         player.teleport(targetLevel, viewX, viewY, viewZ, yaw, pitch);
         this.ensureChunkLoaded(targetLevel, pos);
      }
   }

   private void teleportPlayer(ServerPlayerEntity player, BlockPos pos, RegistryKey<World> dimension) {
      ServerWorld targetLevel = player.getServer().getWorld(dimension);
      if (targetLevel == null) {
         throw new IllegalStateException("Arena dimension not found: " + dimension.getValue());
      } else {
         this.ensureChunkLoaded(targetLevel, pos);
         player.teleport(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
         this.ensureChunkLoaded(targetLevel, pos);
      }
   }

   private void teleportPlayer(ServerPlayerEntity player, Vec3d pos, RegistryKey<World> dimension) {
      ServerWorld targetLevel = player.getServer().getWorld(dimension);
      if (targetLevel == null) {
         throw new IllegalStateException("Target dimension not found: " + dimension.getValue());
      } else {
         BlockPos chunkPos = BlockPos.ofFloored(pos);
         this.ensureChunkLoaded(targetLevel, chunkPos);
         player.teleport(targetLevel, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
         this.ensureChunkLoaded(targetLevel, chunkPos);
      }
   }

   private void ensureChunkLoaded(ServerWorld level, BlockPos pos) {
      level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
   }

   private void applyInvisibility(ServerPlayerEntity player) {
      StatusEffectInstance invisibility = new StatusEffectInstance(StatusEffects.INVISIBILITY, 999999, 0, false, false);
      player.addStatusEffect(invisibility);
   }

   private void applyMovementLock(ServerPlayerEntity player) {
      EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (movementSpeed != null) {
         Identifier modifierId = Identifier.of("cobblemon_arena", "movement_lock");
         movementSpeed.removeModifier(modifierId);
         double currentSpeed = movementSpeed.getValue();
         movementSpeed.addPersistentModifier(new EntityAttributeModifier(modifierId, -currentSpeed, EntityAttributeModifier.Operation.ADD_VALUE));
      }

      EntityAttributeInstance jumpStrength = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
      if (jumpStrength != null) {
         Identifier jumpModifierId = Identifier.of("cobblemon_arena", "jump_lock");
         jumpStrength.removeModifier(jumpModifierId);
         jumpStrength.addPersistentModifier(new EntityAttributeModifier(jumpModifierId, -1.0, EntityAttributeModifier.Operation.ADD_VALUE));
      }
   }

   private void removeMovementLock(ServerPlayerEntity player) {
      EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (movementSpeed != null) {
         Identifier modifierId = Identifier.of("cobblemon_arena", "movement_lock");
         movementSpeed.removeModifier(modifierId);
      }

      EntityAttributeInstance jumpStrength = player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH);
      if (jumpStrength != null) {
         Identifier jumpModifierId = Identifier.of("cobblemon_arena", "jump_lock");
         jumpStrength.removeModifier(jumpModifierId);
      }
   }

   private void removeInvisibility(ServerPlayerEntity player) {
      player.removeStatusEffect(StatusEffects.INVISIBILITY);
   }

   private void hideEquipment(ServerPlayerEntity player) {
      player.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      player.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
      player.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
      player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
      player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
   }

   private void showEquipment(ServerPlayerEntity player) {
      ItemStack[] equipment;
      if (player.getUuid().equals(this.player1.getUuid())) {
         equipment = this.player1State.getOriginalEquipment();
      } else {
         equipment = this.player2State.getOriginalEquipment();
      }

      player.equipStack(EquipmentSlot.MAINHAND, equipment[0]);
      player.equipStack(EquipmentSlot.OFFHAND, equipment[1]);
      player.equipStack(EquipmentSlot.FEET, equipment[2]);
      player.equipStack(EquipmentSlot.LEGS, equipment[3]);
      player.equipStack(EquipmentSlot.CHEST, equipment[4]);
      player.equipStack(EquipmentSlot.HEAD, equipment[5]);
   }

   public void end() {
      this.active = false;
      this.battleId = null;
      this.battleInitializationDeadlineTick = -1L;
      this.battleLaunchInProgress = false;
      this.resetDecisionTimer(this.player1);
      this.resetDecisionTimer(this.player2);
      this.restorePlayers();
   }

   public boolean involves(ServerPlayerEntity player) {
      return player.getUuid().equals(this.player1.getUuid()) || player.getUuid().equals(this.player2.getUuid());
   }

   public BlockPos getPlayerViewPosition(ServerPlayerEntity player) {
      if (player.getUuid().equals(this.player1.getUuid())) {
         return this.player1ViewPos;
      } else {
         return player.getUuid().equals(this.player2.getUuid()) ? this.player2ViewPos : null;
      }
   }

   private void disableFovEffects(ServerPlayerEntity player) {
      if (player.getUuid().equals(this.player1.getUuid())) {
         this.player1OriginalFovEffects = true;
      } else {
         this.player2OriginalFovEffects = true;
      }
   }

   private void restoreFovEffects(ServerPlayerEntity player, boolean originalSetting) {
   }

   private ArenaSession.DecisionTimerState timerState(ServerPlayerEntity player) {
      return player != null ? this.decisionTimers.get(player.getUuid()) : null;
   }

   private static List<ArenaSession.TeamPokemonSnapshot> immutableTeamSnapshot(List<ArenaSession.TeamPokemonSnapshot> entries) {
      if (entries != null && !entries.isEmpty()) {
         List<ArenaSession.TeamPokemonSnapshot> copy = new ArrayList<>(entries.size());

         for (ArenaSession.TeamPokemonSnapshot entry : entries) {
            if (entry != null) {
               copy.add(entry);
            }
         }

         return Collections.unmodifiableList(copy);
      } else {
         return List.of();
      }
   }

   private static final class DecisionTimerState {
      private long deadlineTick = -1L;
      private int lastWarningSeconds = Integer.MAX_VALUE;
   }

   public static final class TeamPokemonSnapshot {
      private final String speciesKey;
      private final String speciesName;
      private final String abilityName;
      private final String heldItemName;
      private final List<String> typeNames;
      private final List<String> moveNames;
      private final String natureName;
      private final int level;

      public TeamPokemonSnapshot(String speciesKey, String speciesName) {
         this(speciesKey, speciesName, "", "", List.of(), List.of(), "", 0);
      }

      public TeamPokemonSnapshot(
         String speciesKey,
         String speciesName,
         String abilityName,
         String heldItemName,
         List<String> typeNames,
         List<String> moveNames,
         String natureName,
         int level
      ) {
         this.speciesKey = speciesKey == null ? "" : speciesKey;
         this.speciesName = speciesName == null ? "" : speciesName;
         this.abilityName = abilityName == null ? "" : abilityName;
         this.heldItemName = heldItemName == null ? "" : heldItemName;
         this.typeNames = typeNames == null ? List.of() : List.copyOf(typeNames);
         this.moveNames = moveNames == null ? List.of() : List.copyOf(moveNames);
         this.natureName = natureName == null ? "" : natureName;
         this.level = level;
      }

      public String getSpeciesKey() {
         return this.speciesKey;
      }

      public String getSpeciesName() {
         return this.speciesName;
      }

      public String getAbilityName() {
         return this.abilityName;
      }

      public String getHeldItemName() {
         return this.heldItemName;
      }

      public List<String> getTypeNames() {
         return this.typeNames;
      }

      public List<String> getMoveNames() {
         return this.moveNames;
      }

      public String getNatureName() {
         return this.natureName;
      }

      public int getLevel() {
         return this.level;
      }
   }
}
