package com.fruityspikes.whaleborne.server.entities;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.client.menus.HullbackMenu;
import com.fruityspikes.whaleborne.network.HullbackHurtPayload;
import com.fruityspikes.whaleborne.server.data.HullbackDirtManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackAIManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackEquipmentManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackInteractionManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackPartManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackDirt;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackSeatManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackControlManager;
import com.fruityspikes.whaleborne.server.registries.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.control.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

public class HullbackEntity extends AbstractWhale implements HasCustomInventoryScreen, PlayerRideableJumping, Saddleable {

    // ─── Constants ───────────────────────────────────────────────
    private static final float HEALING_AMOUNT = 0.25f;
    private static final int HEALING_INTERVAL_TICKS = 80;
    private static final int SEAT_VALIDATION_INTERVAL_TICKS = 20;
    private static final int ARMOR_SYNC_INTERVAL_TICKS = 40;
    private static final int MOUTH_CLOSE_INTERVAL_TICKS = 80;
    private static final float MOUTH_OPEN_SPEED = 0.8f;
    private static final float SPEED_THRESHOLD_MOUTH_OPEN = 0.3f;
    private static final float ARMOR_EJECT_THRESHOLD = 0.45f;
    // DIRT_TICK_CHANCE / DIRT_TICK_DENOMINATOR removed — random gate now lives
    // inside HullbackDirt.randomTick(), matching the original 1.20.1 structure.
    private static final int POST_LOAD_VALIDATION_DELAY_TICKS = 5;
    private static final int STATIONARY_TICKS_PLAYER_ABOVE = 5;
    private static final int STATIONARY_TICKS_DISMOUNT = 100;
    private static final int STATIONARY_TICKS_INITIAL = 60;
    private static final int DIRT_INITIAL_SYNC_TICK = 10;
    private static final int EARLY_TICK_THRESHOLD = 20;
    private static final double SPEED_THRESHOLD_MOUTH_OPEN_SQR = SPEED_THRESHOLD_MOUTH_OPEN * SPEED_THRESHOLD_MOUTH_OPEN;
    private static final double PARTICLE_SPEED_THRESHOLD_SQR = 0.03 * 0.03;
    private static final int[] SIDES = {-1, 1};

    // ─── Static fields ───────────────────────────────────────────
    private static final ResourceLocation SAIL_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "sail_speed_modifier");

    // ─── Synched Entity Data ─────────────────────────────────────
    public static final EntityDataAccessor<ItemStack> DATA_CROWN_ID = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> DATA_ARMOR = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_MOUTH_PROGRESS = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_0 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_1 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_2 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_3 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_4 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_5 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SEAT_6 = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Boolean> DATA_VECTOR_CONTROL = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STATIONARY_TICKS = SynchedEntityData.defineId(HullbackEntity.class, EntityDataSerializers.INT);

    // ─── Inventory Slots ────────────────────────────────────────
    public static final int INV_SLOT_CROWN = 0;
    public static final int INV_SLOT_SADDLE = 1;
    public static final int INV_SLOT_ARMOR = 2;

    // ─── Sub-Entity Parts ───────────────────────────────────────
    public final HullbackPartEntity head;
    public final HullbackPartEntity nose;
    public final HullbackPartEntity body;
    public final HullbackPartEntity tail;
    public final HullbackPartEntity fluke;
    private final HullbackPartEntity[] subEntities;

    // ─── Walkable Platforms ──────────────────────────────────────
    public HullbackWalkableEntity moving_head;
    public HullbackWalkableEntity moving_nose;
    public HullbackWalkableEntity moving_body;

    // ─── Component Managers ──────────────────────────────────────
    public final HullbackPartManager partManager;
    public final HullbackDirt HullbackDirt;
    public final HullbackSeatManager hullbackSeatManager;
    public final HullbackAIManager aiManager;
    public final HullbackEquipmentManager equipmentManager;
    public final HullbackInteractionManager interactionManager;
    public final HullbackControlManager controlManager;
    public final HullbackBodyRotationControl bodyControl;

    // ─── State Fields ───────────────────────────────────────────
    private boolean validatedAfterLoad = false;
    private float leftEyeYaw, rightEyeYaw, eyePitch;
    private IItemHandler itemHandler;
    private Vec3 currentTarget;
    public int stationaryTicks;
    public boolean HAS_MOBIUS_SPAWNED = false;
    public float AttributeSpeedModifier = 1;
    public float newRotY = this.getYRot();
    private float mouthOpenProgress;
    private float mouthTarget;
    private boolean isBreaching;

    public HullbackDirt getWhaleDirt() {
        return this.HullbackDirt;
    }

    public void setBreaching(boolean breaching) {
        this.isBreaching = breaching;
    }

    public boolean isBreaching() {
        return this.isBreaching;
    }

    public static ResourceLocation getSailSpeedModifierId() {
        return SAIL_SPEED_MODIFIER_ID;
    }

    public HullbackEntity(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);

        this.inventory.addListener(this);
        this.itemHandler = new InvWrapper(this.inventory);

        this.moveControl = new StationaryAwareMoveControl(this, 1, 2, 0.1F, 0.1F, true);
        this.lookControl = new StationaryAwareLookControl(this, 1);
        this.bodyControl = (HullbackBodyRotationControl) createBodyControl();


        this.nose = new HullbackPartEntity(this, "nose", 5.0F, 5.0F);
        this.head = new HullbackPartEntity(this, "head", 5.0F, 5.0F);
        this.body = new HullbackPartEntity(this, "body", 5.0F, 5.0F);
        this.tail = new HullbackPartEntity(this, "tail", 2.5F, 2.5F);
        this.fluke = new HullbackPartEntity(this, "fluke", 4.0F, 0.6F);

        this.moving_nose = null;
        this.moving_head = null;
        this.moving_body = null;

        this.subEntities = new HullbackPartEntity[]{this.nose, this.head, this.body, this.tail, this.fluke};

        this.setId(ENTITY_COUNTER.getAndAdd(this.subEntities.length + 1) + 1);

        this.partManager = new HullbackPartManager(this, this.subEntities);
        this.partManager.init();

        this.HullbackDirt = new HullbackDirt(this);
        this.HullbackDirt.init();

        this.hullbackSeatManager = new HullbackSeatManager(this, 
            DATA_SEAT_0, DATA_SEAT_1, DATA_SEAT_2, DATA_SEAT_3, 
            DATA_SEAT_4, DATA_SEAT_5, DATA_SEAT_6);

        this.aiManager = new HullbackAIManager(this);
        this.equipmentManager = new HullbackEquipmentManager(this);
        this.interactionManager = new HullbackInteractionManager(this);
        this.controlManager = new HullbackControlManager(this);
        
        // Manual registration since registerGoals() is called by super() before aiManager is init
        this.aiManager.registerGoals();

        this.mouthOpenProgress = 0.0f;
        this.currentTarget = this.position();
        
        // Manual inventory update since the one in super() was skipped due to null manager
        this.updateContainerEquipment();

        this.stationaryTicks = STATIONARY_TICKS_INITIAL;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType spawnReason) {
        return checkHullbackSpawnRules((EntityType<HullbackEntity>) this.getType(), level, spawnReason, this.blockPosition(), level.getRandom());
    }

    public static boolean checkHullbackSpawnRules(EntityType<HullbackEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!WaterAnimal.checkSurfaceWaterAnimalSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }

        if (!hasSpawnSpace(level, pos)) {
            return false;
        }

        if (hasNearbyHullbacks(level, pos)) {
            return false;
        }

        return true;
    }

    private static boolean hasSpawnSpace(LevelAccessor level, BlockPos pos) {
        // Reduced check area to allow more spawns
        int checkRadius = 5;   // Reduced from 10 to 5
        int heightCheck = 4;   // Reduced from 6 to 4

        AABB spawnCheckArea = new AABB(pos).inflate(checkRadius, heightCheck, checkRadius);

        int solidCount = 0;
        int maxSolidAllowed = 20;  // Allow up to 20 solid blocks (tolerance)

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = (int)spawnCheckArea.minX; x <= spawnCheckArea.maxX; x++) {
            for (int y = (int)spawnCheckArea.minY; y <= spawnCheckArea.maxY; y++) {
                for (int z = (int)spawnCheckArea.minZ; z <= spawnCheckArea.maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isSolid() && state.getCollisionShape(level, mutablePos) != Shapes.empty()) {
                        solidCount++;
                        if (solidCount > maxSolidAllowed) {
                            return false;  // Too obstructed
                        }
                    }
                }
            }
        }

        return true;  // Spawn allowed
    }

    private static boolean hasNearbyHullbacks(LevelAccessor level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(32, 16, 32);
        return !level.getEntitiesOfClass(
                HullbackEntity.class,
                checkArea,
                e -> e.isAlive() && !e.isTamed()
        ).isEmpty();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100.0)
            .add(Attributes.MOVEMENT_SPEED, 0.005)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(NeoForgeMod.SWIM_SPEED, 1.2);
    }

    protected int getInventorySize() {
        return 3;
    }
    protected int getSaddleSlot() {
        return INV_SLOT_SADDLE;
    }
    // Inventory created in AbstractWhale
    public void updateContainerEquipment() {
        if (equipmentManager != null) {
            equipmentManager.updateContainerEquipment();
        }
    }
    public void containerChanged(Container invBasic) {
        if (equipmentManager != null) {
            equipmentManager.containerChanged(invBasic);
        }
    }

    @Override
    public void onInsideBubbleColumn(boolean downwards) { }

    public ItemStack getArmor() {
        return equipmentManager.getArmor();
    }
    public ItemStack getCrown() {
        return equipmentManager.getCrown();
    }


    @Override
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        equipmentManager.equipSaddle(stack, source);
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(DATA_SEAT_0, Optional.empty());
        builder.define(DATA_SEAT_1, Optional.empty());
        builder.define(DATA_SEAT_2, Optional.empty());
        builder.define(DATA_SEAT_3, Optional.empty());
        builder.define(DATA_SEAT_4, Optional.empty());
        builder.define(DATA_SEAT_5, Optional.empty());
        builder.define(DATA_SEAT_6, Optional.empty());

        builder.define(DATA_CROWN_ID, ItemStack.EMPTY);
        builder.define(DATA_ARMOR, ItemStack.EMPTY);
        builder.define(DATA_MOUTH_PROGRESS, 0f);
        builder.define(DATA_VECTOR_CONTROL, false);
        builder.define(DATA_STATIONARY_TICKS, 0);
    }

    public int getStationaryTicks() {
        return this.entityData.get(DATA_STATIONARY_TICKS);
    }
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        ListTag listtag = new ListTag();

        for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (!itemstack.isEmpty()) {
                Tag savedTag = itemstack.save(this.registryAccess());
                if (savedTag instanceof CompoundTag itemTag) {
                    itemTag.putByte("Slot", (byte) i);
                    listtag.add(itemTag);
                }
            }
        }
        compound.put("Items", listtag);


        CompoundTag hasMobiusSpawned = new CompoundTag();
        hasMobiusSpawned.putBoolean("HasMobiusSpawned", HAS_MOBIUS_SPAWNED);

        for (int i = 0; i < 7; i++) {
            Optional<UUID> occupant = this.entityData.get(getSeatAccessor(i));
            String seatKey = "Seat_" + i;

            if (occupant.isPresent()) {
                compound.putUUID(seatKey, occupant.get());
            }
        }
        this.HullbackDirt.addAdditionalSaveData(compound);
    }
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        ListTag items = compound.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < this.inventory.getContainerSize()) {
                ItemStack stack = ItemStack.parse(this.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                this.inventory.setItem(slot, stack);
            }
        }

        HAS_MOBIUS_SPAWNED = compound.getBoolean("HasMobiusSpawned");

        for (int i = 0; i < 7; i++) {
            String seatKey = "Seat_" + i;
            if (compound.hasUUID(seatKey)) {
                UUID uuid = compound.getUUID(seatKey);
                this.entityData.set(getSeatAccessor(i), Optional.of(uuid));
            } else {
                this.entityData.set(getSeatAccessor(i), Optional.empty());
            }
        }

        this.HullbackDirt.readAdditionalSaveData(compound);
        this.updateContainerEquipment();
    }

    public EntityDataAccessor<Optional<UUID>> getSeatAccessor(int seatIndex) {
        return hullbackSeatManager.getSeatAccessor(seatIndex);
    }

    public float getArmorProgress() {
        return (float) this.entityData.get(DATA_ARMOR).getCount() / 64f;
    }
    public float getLeftEyeYaw() { return leftEyeYaw; }
    public float getRightEyeYaw() { return rightEyeYaw; }
    public float getEyePitch() { return eyePitch; }
    // Override to suppress default air supply tick — prevents the entity from
    // trying to surface for air, which would fight the custom buoyancy in travel().
    protected void handleAirSupply(int airSupply) { }

    public int getMaxAirSupply() {
        return 10000;
    }
    protected int increaseAirSupply(int currentAir) {
        return this.getMaxAirSupply();
    }

    public boolean isPushable() {
        return false;
    }
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.subEntities.length; i++) {
            this.subEntities[i].setId(id + i + 1);
        }
    }
    public HullbackPartEntity[] getSubEntities() {
        return this.subEntities;
    }
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    public boolean isPickable() {
        return false;
    }

    public HullbackPartManager getPartManager() {
        return this.partManager;
    }



    public boolean isMultipartEntity() {
        return true;
    }

    public HullbackPartEntity[] getParts() {
        return this.subEntities;
    }

    private boolean scanPlayerAbove() {
        for (HullbackPartEntity part : getSubEntities()) {
            AABB box = part.getBoundingBox();
            AABB topSurface = new AABB(
                    box.minX,
                    box.maxY,
                    box.minZ,
                    box.maxX,
                    box.maxY + 2.0,
                    box.maxZ
            );

            List<Entity> entities = level().getEntitiesOfClass(Entity.class, topSurface,
                    entity -> entity instanceof Player &&
                            !entity.isSpectator() &&
                            !entity.isPassenger()
            );

            if (!entities.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public void playAmbientSound() {
        if (this.random.nextFloat() < 0.5) return;

        mouthTarget = 0.2f;


        for (int side : SIDES) {
            Vec3 particlePos = partManager.partPosition[1].add(new Vec3(3.5*side, 2, 0).yRot(-partManager.partYRot[1]));
            double x = particlePos.x;
            double y = particlePos.y;
            double z = particlePos.z;

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        WBParticleRegistry.SMOKE.get(),
                        x,
                        y,
                        z,
                        20,
                        0.2,
                        0.2,
                        0.2,
                        0.02
                );
            }
        }

        playSound(WBSoundRegistry.HULLBACK_AMBIENT.get(), 3.0f, 1);
    }

    @Override
    protected void playSwimSound(float volume) {
        super.playSwimSound(2);
    }

    @Override
    protected SoundEvent getSwimSound() {
        return WBSoundRegistry.HULLBACK_SWIM.get();
    }
    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return WBSoundRegistry.HULLBACK_HURT.get();
    }
    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return WBSoundRegistry.HULLBACK_DEATH.get();
    }
    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return WBSoundRegistry.HULLBACK_AMBIENT.get();
    }

    protected void registerGoals() {
        if (aiManager != null) {
            aiManager.registerGoals();
        }
    }

    public void moveEntitiesOnTop(int index) {
        HullbackPartEntity part = getSubEntities()[index];
        Vec3 offset = partManager.partPosition[index].subtract(partManager.oldPartPosition[index]);

        if (offset.length() <= 0) return;
        for (Entity entity : this.level().getEntities(part, part.getBoundingBox().inflate(0F, 0.01F, 0F), EntitySelector.NO_SPECTATORS.and((entity) -> (!entity.isPassenger())))) {
            if (!entity.noPhysics && !(entity instanceof HullbackPartEntity) && !(entity instanceof HullbackEntity) && !(entity instanceof HullbackWalkableEntity)) {
                double gravity = entity.isNoGravity() ? 0 : 0.08D;
                if (entity instanceof LivingEntity living) {
                    AttributeInstance attribute = living.getAttribute(Attributes.GRAVITY);
                    gravity = attribute.getValue();
                }
                float f2 = 1.0F;
                entity.move(MoverType.SHULKER, new Vec3((double) (f2 * (float) offset.x), (double) (f2 * (float) offset.y), (double) (f2 * (float) offset.z)));
                entity.hurtMarked = true;
            }
        }
    }


    private List<HullbackDirtManager.HullbackDirtEntry> getCandidates(boolean bottom, String partName) {
        return interactionManager.getCandidates(bottom, partName);
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return interactionManager.mobInteract(player, hand);
    }

    public InteractionResult interactDebug(Player player, InteractionHand hand){
        return interactionManager.interactDebug(player, hand);
    }
    public InteractionResult interactRide(Player player, InteractionHand hand, int seatIndex, @Nullable EntityType<?> entityType) {
        return interactionManager.interactRide(player, hand, seatIndex, entityType);
    }

    public InteractionResult interactClean(Player player, InteractionHand hand, HullbackPartEntity part, Boolean top) {
        return interactionManager.interactClean(player, hand, part, top);
    }

    public InteractionResult interactArmor(Player player, InteractionHand hand, HullbackPartEntity part, Boolean top) {
        return interactionManager.interactArmor(player, hand, part, top);
    }
 
    public void setMouthTarget(float target) {
        this.mouthTarget = target;
    }

    @Override
    protected void tickDeath() {
        discardAllPlatforms();
        List<HullbackWalkableEntity> list = level().getEntitiesOfClass(HullbackWalkableEntity.class, this.getBoundingBox().inflate(6));
        for (HullbackWalkableEntity entity : list) entity.discard();

        super.tickDeath();
    }

    public void sendHurtSyncPacket() {
        if (!this.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(this, new HullbackHurtPayload(
                    this.getId(),
                    this.inventory.getItem(INV_SLOT_ARMOR),
                    this.inventory.getItem(INV_SLOT_CROWN),
                    this.entityData.get(DATA_ID_FLAGS)
            ));
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        
        // Initialize part positions immediately
        if (partManager.prevPartPositions[0] == null) {
            for (int i = 0; i < partManager.prevPartPositions.length; i++) {
                partManager.prevPartPositions[i] = position();
                partManager.partPosition[i] = position();
                partManager.oldPartPosition[i] = position();
            }
        }

        // Calculate initial part positions
        partManager.updatePartPositions();

        // Calculate initial seats
        if (isTamed()) {
            partManager.calculateSeats();
        }
        
        // NEW: Sync equipment when adding to world
        if (!this.level().isClientSide) {
            // Ensure inventory is correct
            ItemStack armor = this.inventory.getItem(INV_SLOT_ARMOR);
            ItemStack crown = this.inventory.getItem(INV_SLOT_CROWN);
            ItemStack saddle = this.inventory.getItem(INV_SLOT_SADDLE);
            
            if (!armor.isEmpty()) {
                this.entityData.set(DATA_ARMOR, armor.copy());
            }
            if (!crown.isEmpty()) {
                this.entityData.set(DATA_CROWN_ID, crown.copy());
            }
            
            // Update saddle flag
            this.setFlag(4, !saddle.isEmpty());
            
            // Force synchronization
            this.updateContainerEquipment();
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.tickCount == 20) {
            this.getWhaleDirt().syncDirtToClients();
        }

        // 1. Capture rotation snapshot BEFORE AI and movement processing
        RotationSnapshot snapshot = captureRotationSnapshot();

        // 2. Handle core stationary logic and base entity tick
        handleStationaryState();
        super.tick();

        // 3. Re-enforce the stationary orientation if applicable (Hard Lock)
        applyHardLock(snapshot);

        // 4. Handle client-side control prediction and state
        if (this.level().isClientSide) {
            controlManager.clientHandleControlState();
        }

        // 5. Manage equipment validation, synchronization and client restoration
        manageEquipmentAndSync();

        // 6. Update multipart positions, passenger rotation and seat points
        manageMultipartPhysics();

        // 7. Handle various passive behaviors and entity logic
        managePassiveBehaviors();
    }

    /** Helper record to store rotation data for the Hard Lock. */
    private record RotationSnapshot(float yaw, float bodyYaw, float headYaw) {}

    private RotationSnapshot captureRotationSnapshot() {
        return new RotationSnapshot(this.getYRot(), this.yBodyRot, this.yHeadRot);
    }

    private void applyHardLock(RotationSnapshot snapshot) {
        if (this.getStationaryTicks() > 0) {
            this.setYRot(snapshot.yaw());
            this.setYBodyRot(snapshot.bodyYaw());
            this.setYHeadRot(snapshot.headYaw());
            this.yRotO = snapshot.yaw();
        }
    }

    private void manageEquipmentAndSync() {
        validateEquipmentAfterLoad();
        syncArmorPeriodically();
        restoreClientEquipment();

        if (this.tickCount % SEAT_VALIDATION_INTERVAL_TICKS == 0) {
            validateAssignments();
        }

        if (this.tickCount == DIRT_INITIAL_SYNC_TICK) {
            HullbackDirt.syncDirtToClients();
        }
    }

    private void manageMultipartPhysics() {
        // Update physical part positions
        partManager.setOldPosAndRots();
        partManager.updatePartPositions();

        // Align passengers with their respective platforms/seats
        hullbackSeatManager.rotatePassengers();

        // Handle head rotation offset when not controlled by AI
        if (this.getStationaryTicks() <= 0) {
            this.yHeadRot = this.yBodyRot + (partManager.partYRot[0] - partManager.partYRot[4]) * 1.5f;
        }

        // Refresh seat coordinates for riding entities
        if (this.isTamed() && partManager.partPosition != null && partManager.partYRot != null && partManager.partXRot != null) {
            partManager.calculateSeats();
        }
    }

    private void managePassiveBehaviors() {
        handlePassengerEjection();
        handleHealing();
        handleMouthAnimation();
        handleDirtTicks();
        updateModifiers();
        tickSubEntitiesAndParticles();
    }


    /** One-time validation after loading from NBT to re-sync equipment with clients. */
    private void validateEquipmentAfterLoad() {
        if (!this.level().isClientSide && !validatedAfterLoad && this.tickCount > POST_LOAD_VALIDATION_DELAY_TICKS) {
            validatedAfterLoad = true;

            ItemStack armor = this.inventory.getItem(INV_SLOT_ARMOR);
            ItemStack crown = this.inventory.getItem(INV_SLOT_CROWN);
            ItemStack saddle = this.inventory.getItem(INV_SLOT_SADDLE);

            this.entityData.set(DATA_ARMOR, armor.copy());
            this.entityData.set(DATA_CROWN_ID, crown.copy());
            this.setFlag(4, !saddle.isEmpty());

            sendHurtSyncPacket();
            HullbackDirt.syncDirtToClients();
        }
    }

    /** Periodically syncs armor data to ensure client-server consistency. */
    private void syncArmorPeriodically() {
        if (tickCount % ARMOR_SYNC_INTERVAL_TICKS == 0) {
            ItemStack current = this.inventory.getItem(INV_SLOT_ARMOR);
            if (!ItemStack.matches(this.entityData.get(DATA_ARMOR), current)) {
                this.entityData.set(DATA_ARMOR, current);
            }
        }
    }

    /** On client side, restores inventory from synched entity data if the local inventory is empty. */
    private void restoreClientEquipment() {
        if (this.level().isClientSide) {
            ItemStack armorInInventory = this.inventory.getItem(INV_SLOT_ARMOR);
            ItemStack armorInData = this.entityData.get(DATA_ARMOR);

            if (armorInInventory.isEmpty() && !armorInData.isEmpty()) {
                this.inventory.setItem(INV_SLOT_ARMOR, armorInData.copy());
            }

            ItemStack crownInInventory = this.inventory.getItem(INV_SLOT_CROWN);
            ItemStack crownInData = this.entityData.get(DATA_CROWN_ID);

            if (crownInInventory.isEmpty() && !crownInData.isEmpty()) {
                this.inventory.setItem(INV_SLOT_CROWN, crownInData.copy());
            }

            boolean hasSaddleFlag = this.getFlag(4);
            ItemStack saddleInInventory = this.inventory.getItem(INV_SLOT_SADDLE);

            if (hasSaddleFlag && saddleInInventory.isEmpty()) {
                this.inventory.setItem(INV_SLOT_SADDLE, new ItemStack(Items.SADDLE));
            }
        }

        if (this.level().isClientSide && getArmorProgress() > 0 && this.inventory.getItem(INV_SLOT_ARMOR).getItem().asItem() == Items.AIR.asItem()) {
            this.inventory.setItem(INV_SLOT_ARMOR, getArmor());
        }
    }

    /** Manages walkable platform spawning/removal and player-above detection. */
    private void handleStationaryState() {
        if (stationaryTicks > 0) {
            stopMoving();
            stationaryTicks--;
        }

        if (scanPlayerAbove()) {
            stationaryTicks = STATIONARY_TICKS_PLAYER_ABOVE;
        }

        if (!this.level().isClientSide) {
            this.entityData.set(DATA_STATIONARY_TICKS, stationaryTicks);
        }

        if (stationaryTicks == 0) {
            discardAllPlatforms();
        }
    }

    /** Ejects passengers when unsaddled or when armor is too damaged. */
    private void handlePassengerEjection() {
        if (!isSaddled() && !level().isClientSide && tickCount > EARLY_TICK_THRESHOLD) {
            if (!getPassengers().isEmpty()) {
                if (!getInventory().getItem(INV_SLOT_CROWN).isEmpty()) {
                    spawnAtLocation(getInventory().getItem(INV_SLOT_CROWN));
                    getInventory().setItem(INV_SLOT_CROWN, ItemStack.EMPTY);
                }
                ejectPassengers();
            }
        } else if (tickCount > EARLY_TICK_THRESHOLD) {
            if (getArmorProgress() < ARMOR_EJECT_THRESHOLD && getInventory().getItem(INV_SLOT_ARMOR).getCount() < 64 && !level().isClientSide) {
                if (!getInventory().getItem(INV_SLOT_CROWN).isEmpty()) {
                    spawnAtLocation(getInventory().getItem(INV_SLOT_CROWN));
                    getInventory().setItem(INV_SLOT_CROWN, ItemStack.EMPTY);
                }
                ejectPassengers();
            }
        }
    }

    /** Passive healing when the head is submerged in water. */
    private void handleHealing() {
        if (this.getSubEntities()[1].isEyeInFluidType(Fluids.WATER.getFluidType()) && this.tickCount % HEALING_INTERVAL_TICKS == 0) {
            this.heal(HEALING_AMOUNT);
        }
    }

    /** Controls mouth open/close animation based on water state and movement speed. */
    private void handleMouthAnimation() {
        if (!this.isEyeInFluidType(Fluids.WATER.getFluidType())) {
            mouthTarget = 1;
        }

        if (this.getDeltaMovement().lengthSqr() > SPEED_THRESHOLD_MOUTH_OPEN_SQR) {
            mouthTarget = MOUTH_OPEN_SPEED;
        }

        updateMouthOpening();

        if (this.tickCount % MOUTH_CLOSE_INTERVAL_TICKS == 0) {
            mouthTarget = 0;
        }
    }

    /** Randomly grows dirt on the whale's body; clears top dirt when tamed.
     *  Matches the original 1.20.1 structure: no outer random gate here —
     *  each randomTick call has its own independent random check inside. */
    private void handleDirtTicks() {
        if (!level().isClientSide) {
            // Bottom dirt always ticks (each call rolls independently inside randomTick)
            this.HullbackDirt.randomTick(this.head.name, true);
            this.HullbackDirt.randomTick(this.body.name, true);
            this.HullbackDirt.randomTick(this.tail.name, true);
            this.HullbackDirt.randomTick(this.fluke.name, true);

            // Top dirt ticks only if NOT tamed
            if (!isTamed()) {
                this.HullbackDirt.randomTick(this.head.name, false);
                this.HullbackDirt.randomTick(this.body.name, false);
            } else {
                HullbackDirt.clearTopDirt();
            }
        }
    }

    /** Ticks all sub-entities and spawns wake particles on body/fluke when swimming. */
    private void tickSubEntitiesAndParticles() {
        for (int i = 0; i < subEntities.length; i++) {
            subEntities[i].tick();

            if (i == 2 || i == 4) {
                float offset = 0;
                if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > PARTICLE_SPEED_THRESHOLD_SQR) {
                    for (int side : SIDES) {
                        if (i == 2) {
                            offset = 4;
                        }
                        Vec3 particlePos = partManager.partPosition[i].add(new Vec3((offset + subEntities[i].getBbWidth() / 2) * side, 0, subEntities[i].getBbWidth() / 2).yRot(partManager.partYRot[i]));
                        double x = particlePos.x;
                        double y = particlePos.y;
                        double z = particlePos.z;

                        for (int j = 0; j < 4; ++j) {
                            this.level().addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.1, 0.0);
                            this.level().addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.1, 0.0);
                        }
                    }
                }
            }
        }
    }

    public boolean hasAnchorDown() {
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof AnchorEntity anchor) {
                if (anchor.isDown()) return true;
                break;
            }
        }
        return false;
    }



    /** Discards all walkable platform entities and nullifies references. */
    private void discardAllPlatforms() {
        if (this.moving_nose != null) { this.moving_nose.discard(); this.moving_nose = null; }
        if (this.moving_head != null) { this.moving_head.discard(); this.moving_head = null; }
        if (this.moving_body != null) { this.moving_body.discard(); this.moving_body = null; }
    }

    public void stopMoving() {
        this.getNavigation().stop();

        if (this.moving_nose == null) this.moving_nose = partManager.spawnPlatform(0);
        if (this.moving_head == null) this.moving_head = partManager.spawnPlatform(1);
        if (this.moving_body == null) this.moving_body = partManager.spawnPlatform(2);

        // Preserve vertical only when anchored (buoyancy active); otherwise full stop
        if (hasAnchorDown()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void updateMouthOpening() {
        mouthOpenProgress = (float) Mth.lerp(0.3, mouthOpenProgress, mouthTarget);
        if(!this.level().isClientSide){
            this.entityData.set(DATA_MOUTH_PROGRESS, mouthOpenProgress);
        }
    }

    public float getMouthOpenProgress() {
        return this.entityData.get(DATA_MOUTH_PROGRESS);
    }
    public Vec3 getPartPos(int i){
        return partManager.partPosition[i];
    }

    public float getPartYRot(int i){
        return partManager.partYRot[i];
    }
    public float getPartXRot(int i){
        return partManager.partXRot[i];
    }

    public Vec3 getOldPartPos(int i){
        return partManager.oldPartPosition[i];
    }
    public float getOldPartYRot(int i){
        return partManager.oldPartYRot[i];
    }
    public float getOldPartXRot(int i){
        return partManager.oldPartXRot[i];
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new HullbackBodyRotationControl(this);
    }

    protected class HullbackBodyRotationControl extends BodyRotationControl {
        public HullbackBodyRotationControl(HullbackEntity hullback) {
            super(hullback);
        }

        @Override
        public void clientTick() {
            if (HullbackEntity.this.getStationaryTicks() > 0) {
                return;
            }
            HullbackEntity.this.setYBodyRot(HullbackEntity.this.getYRot());
        }
    }

    /** Prevents SmoothSwimmingMoveControl from rotating the entity when stationary. */
    protected class StationaryAwareMoveControl extends SmoothSwimmingMoveControl {
        public StationaryAwareMoveControl(HullbackEntity entity, int maxTurnX, int maxTurnY, float inWaterSpeedModifier, float outsideWaterSpeedModifier, boolean applyGravity) {
            super(entity, maxTurnX, maxTurnY, inWaterSpeedModifier, outsideWaterSpeedModifier, applyGravity);
        }

        @Override
        public void tick() {
            if (HullbackEntity.this.getStationaryTicks() > 0) {
                this.operation = Operation.WAIT;
                return;
            }
            super.tick();
        }
    }

    /** Prevents SmoothSwimmingLookControl from rotating the entity when stationary. */
    protected class StationaryAwareLookControl extends SmoothSwimmingLookControl {
        public StationaryAwareLookControl(HullbackEntity entity, int maxTurnDegrees) {
            super(entity, maxTurnDegrees);
        }

        @Override
        public void tick() {
            if (HullbackEntity.this.getStationaryTicks() > 0) {
                return;
            }
            super.tick();
        }
    }


    @Override
    public void onPlayerJump(int i) { }

    @Override
    public boolean canJump() { return true; }

    @Override
    public void handleStartJump(int i) { }

    @Override
    public void handleStopJump() { }

    // PASSENGERS
    @Override
    public boolean canAddPassenger(Entity passenger) {
        return (getPassengers().size() < 7);
    }

    // Improve positionRider to handle uninitialized seats
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) return;

        int seatIndex = getSeatByEntity(passenger);
        if (seatIndex == -1) {
            return;
        }

        float yOffset = 0;
        if(this.getArmorProgress() == 0)
            yOffset = 0.5F;

        // Verify if seats were calculated
        if (seatIndex < partManager.seats.length && partManager.seats[seatIndex] != null) {
            callback.accept(passenger,
                    partManager.seats[seatIndex].x,
                    partManager.seats[seatIndex].y - yOffset,
                    partManager.seats[seatIndex].z);
        } else {
            // Fallback: Use entity's main position if seats were not calculated
            callback.accept(passenger,
                    this.getX(),
                    this.getY() + 5.0 - yOffset,
                    this.getZ());
        }
    }



    public static Holder<Attribute> getSwimSpeed() {
        return NeoForgeMod.SWIM_SPEED;
    }

    private void updateModifiers() {
        double speedModifier = 0.0;

        for (Entity passenger : getPassengers()) {
            if (passenger instanceof com.fruityspikes.whaleborne.server.entities.SailEntity sail) {
                speedModifier += sail.getSpeedModifier();
            }
        }

        AttributeInstance inst = this.getAttribute(getSwimSpeed());
        if (inst != null) {
            AttributeModifier old = inst.getModifier(SAIL_SPEED_MODIFIER_ID);
            if (old != null) {
                inst.removeModifier(SAIL_SPEED_MODIFIER_ID);
            }
            if (speedModifier != 0.0) {
                inst.addPermanentModifier(new AttributeModifier(
                        SAIL_SPEED_MODIFIER_ID,
                        speedModifier,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
    }

    public int getSeatByEntity(Entity entity){
        return hullbackSeatManager.getSeatByEntity(entity);
    }

    private void validateAssignments() {
        hullbackSeatManager.validateAssignments();
        updateModifiers();
    }
    public void assignSeat(int seatIndex, @Nullable Entity passenger) {
        hullbackSeatManager.assignSeat(seatIndex, passenger);
    }

    public Optional<Entity> getPassengerForSeat(int seatIndex) {
        return hullbackSeatManager.getPassengerForSeat(seatIndex);
    }
    private boolean isPassengerAssigned(Entity passenger) {
        return hullbackSeatManager.isPassengerAssigned(passenger);
    }
    private int findFreeSeat() {
        return hullbackSeatManager.findFreeSeat();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        // Sync immediately
        if (this.level() instanceof ServerLevel) {
             ((ServerLevel) this.level()).getChunkSource().broadcast(this, new ClientboundSetPassengersPacket(this));
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if(passenger instanceof Player)
            stationaryTicks = STATIONARY_TICKS_DISMOUNT;
        if(passenger.isRemoved())
            for (int i = 0; i < 7; i++) {
                Optional<UUID> occupant = this.entityData.get(getSeatAccessor(i));
                if (occupant.isPresent() && occupant.get().equals(passenger.getUUID())) {
                    this.entityData.set(getSeatAccessor(i), Optional.empty());
                }
            }
        super.removePassenger(passenger);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seatIndex = getSeatByEntity(passenger);
        if (seatIndex != -1) {
            for (int i = 0; i < 7; i++) {
                Optional<UUID> occupant = this.entityData.get(getSeatAccessor(i));
                if (occupant.isPresent() && occupant.get().equals(passenger.getUUID())) {
                    this.entityData.set(getSeatAccessor(i), Optional.empty());
                }
            }

            if (seatIndex >= 0 && seatIndex < partManager.seats.length) {
                Vec3 seatPos = partManager.seats[seatIndex];
                return new Vec3(seatPos.x, seatPos.y, seatPos.z);
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        for (int i = 0; i < 7; i++) {
            Optional<UUID> seatOccupant = this.entityData.get(getSeatAccessor(i));
            if (seatOccupant.isPresent()) {
                Entity entity = getEntityByUUID(seatOccupant.get());

                if (entity instanceof HelmEntity helm) {
                    LivingEntity controller = helm.getControllingPassenger();
                    if (controller != null) {
                        return controller;
                    }
                }
            }
        }
        return null;
    }

    public Entity getEntityByUUID(UUID uuid) {
        for (Entity entity : getPassengers()) {

            if(entity.getUUID().equals(uuid))
                return entity;
        }
        return null;
    }

    private void syncMovement() {
        if (!this.level().isClientSide) {
            ((ServerLevel) this.level()).getChunkSource().broadcast(this, new ClientboundSetEntityMotionPacket(this.getId(), this.getDeltaMovement()));
        }
    }
    
    @Override
    public void travel(Vec3 travelVector) {
        // Stop horizontal movement if anchor is down or stationary
        if ((this.hasAnchorDown() || (this.getStationaryTicks() > 0)) && this.isHullbackInWater()) {
            travelVector = new Vec3(0, 0, 0);
        }

        // AGGRESSIVE LAND SPEED REDUCTION: Scale input vector before vanilla physics
        if (!this.isHullbackInWater()) {
            travelVector = travelVector.multiply(0.1, 1.0, 0.1);
        }

        // Logic: Target specific depth (-5.0 from sea level)
        // This must run even when RIDDEN and regardless of EffectiveAI to maintain buoyancy.
        boolean isBreachingAction = this.isBreaching; // Assume this flag is managed elsewhere
        
        // Robust submerged checks
        boolean noseSubmerged = this.getPartManager() != null && 
                               this.getPartManager().subEntities != null && 
                               this.getPartManager().subEntities.length > 0 && 
                               this.getPartManager().subEntities[0].isEyeInFluidType(Fluids.WATER.getFluidType());
        boolean bodySubmerged = this.getFluidTypeHeight(Fluids.WATER.getFluidType()) > 0.1;
        boolean inWater = this.isInWater() || bodySubmerged; // logic: if nose is NOT submerged, we might be "walking on water" but only if we are actually in water. If on land, isInWater is false.

        if (inWater && !isBreachingAction) {
            boolean isAtHelm = this.getControllingPassenger() != null && this.getControllingPassenger().getVehicle() instanceof HelmEntity;

            if (isAtHelm) {
                // SAILING AT HELM: Maintain target depth -4.7 for perfect deck alignment
                double targetY = this.level().getSeaLevel() - 4.7;
                double currentY = this.getY();
                double diff = targetY - currentY;
                
                if (Math.abs(diff) < 0.1) {
                    this.setDeltaMovement(this.getDeltaMovement().x, 0, this.getDeltaMovement().z);
                } else {
                    double verticalForce = Mth.clamp(diff * 0.05, -0.05, 0.05);
                    this.setDeltaMovement(this.getDeltaMovement().add(0, verticalForce, 0));
                }
            } else if (this.isTamed() || this.hasAnchorDown()) {
                // TAMED OR ANCHORED: Maintain stable -5.0 depth (boarding ease + stability)
                double targetY = this.level().getSeaLevel() - 5.0;
                double currentY = this.getY();
                double diff = targetY - currentY;
                
                if (Math.abs(diff) < 0.1) {
                    this.setDeltaMovement(this.getDeltaMovement().x, 0, this.getDeltaMovement().z);
                } else {
                    double verticalForce = Mth.clamp(diff * 0.05, -0.05, 0.05);
                    this.setDeltaMovement(this.getDeltaMovement().add(0, verticalForce, 0));
                }
            } else {
                // WILD / NOT TAMED: Only push down if surfacing (nose out of water).
                // We don't check isInWater() here because if nose is out, we might be technically "out" but we need to push down.
                if (!noseSubmerged) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0, -0.05, 0));
                }
            }
        }
        super.travel(travelVector);

        // EXTRA FRICTION ON LAND (Compensate for vanilla travel logic)
        // Only apply friction if on ground to allow air momentum (jumps/lunges)
        if (!this.isHullbackInWater() && this.onGround()) {
            BlockState surface = this.getBlockStateOn();
            double friction = 0.3; // Standard land: Very heavy
            
            // SPECIAL: If on ice, apply even more friction to remove the "slide"
            if (surface.is(net.minecraft.world.level.block.Blocks.ICE) || 
                surface.is(net.minecraft.world.level.block.Blocks.PACKED_ICE) || 
                surface.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)) {
                friction = 0.05; // Ice: Extremely locked down
            }

            this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));
        }

        // Final anchor lock to be sure (prevents drift from super.travel)
        if (this.hasAnchorDown() && this.isInWater()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        }
    }

    public boolean isHullbackInWater() {
        // Require significant depth (0.8m) to count as "swimming", avoids beach speed spikes
        return this.getFluidTypeHeight(Fluids.WATER.getFluidType()) > 0.8;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (this.isHullbackInWater() ? this.getAttributeValue(NeoForgeMod.SWIM_SPEED) : this.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider) {
        return true;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        openHullbackMenu(player);
    }
    private void openHullbackMenu(Player player) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.getRootVehicle() instanceof HullbackEntity) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return HullbackEntity.this.getDisplayName();
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
                    return new HullbackMenu(windowId, playerInventory, HullbackEntity.this);
                }
            }, buf -> buf.writeInt(this.getId()));
        }
    }
    
    public void setVectorControl(boolean val) {
         this.entityData.set(DATA_VECTOR_CONTROL, val);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return controlManager.getRiddenInput(player, travelVector);
    }
}