package com.fruityspikes.whaleborne.server.entities;

import com.fruityspikes.whaleborne.Config;
import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.client.menus.HullbackMenu;
import com.fruityspikes.whaleborne.network.HullbackHurtPayload;
import com.fruityspikes.whaleborne.network.SyncHullbackDirtPayload;
import com.fruityspikes.whaleborne.server.data.HullbackDirtManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackAIManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackBodyRotationControl;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackEquipmentManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackInteractionManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackPartManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackDirt;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackSeatManager;
import com.fruityspikes.whaleborne.server.entities.components.hullback.HullbackControlManager;
import com.fruityspikes.whaleborne.server.entities.goals.hullback.*;
import com.fruityspikes.whaleborne.server.registries.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import com.fruityspikes.whaleborne.network.ToggleControlPayload;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.control.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import org.joml.Matrix2dc;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class HullbackEntity extends AbstractWhale implements HasCustomInventoryScreen, PlayerRideableJumping, Saddleable {
    
    // CACHE para não verificar o ModList todo tick
    private static Boolean IS_THIRD_PERSON_MOD_LOADED = null;
    private static final ResourceLocation SAIL_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "sail_speed_modifier");
    private boolean validatedAfterLoad = false;
    private float leftEyeYaw, rightEyeYaw, eyePitch;
    private IItemHandler itemHandler;
    private boolean immobile;
    private boolean tamedCoolDown;
    private Vec3 currentTarget;
    public int stationaryTicks;

    public static final int INV_SLOT_CROWN = 0;
    public static final int INV_SLOT_SADDLE = 1;
    public static final int INV_SLOT_ARMOR = 2;
    public boolean HAS_MOBIUS_SPAWNED = false;
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
    
    public final HullbackPartEntity head;
    public final HullbackPartEntity nose;
    public final HullbackPartEntity body;
    public final HullbackPartEntity tail;
    public final HullbackPartEntity fluke;

    public HullbackWalkableEntity moving_head;
    public HullbackWalkableEntity moving_nose;
    public HullbackWalkableEntity moving_body;
    private final HullbackPartEntity[] subEntities;
    
    public final HullbackPartManager partManager;
    public final HullbackDirt HullbackDirt;

    public HullbackDirt getWhaleDirt() {
        return this.HullbackDirt;
    }
    public final HullbackSeatManager hullbackSeatManager;
    public final HullbackAIManager aiManager;
    public final HullbackEquipmentManager equipmentManager;
    public final HullbackInteractionManager interactionManager;
    public final HullbackControlManager controlManager;
    public final HullbackBodyRotationControl bodyControl;

    public float newRotY = this.getYRot();
    private float mouthOpenProgress;
    private float mouthTarget;

    public static ResourceLocation getSailSpeedModifierId() {
        return SAIL_SPEED_MODIFIER_ID;
    }

    public float AttributeSpeedModifier = 1;

    public HullbackEntity(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);

        this.inventory.addListener(this);
        this.itemHandler = new InvWrapper(this.inventory);

        this.moveControl = new SmoothSwimmingMoveControl(this, 1, 2, 0.1F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 1);
        this.bodyControl = new HullbackBodyRotationControl(this);


        this.nose = new HullbackPartEntity(this, "nose", 5.0F, 5.0F);
        this.head = new HullbackPartEntity(this, "head", 5.0F, 5.0F);
        this.body = new HullbackPartEntity(this, "body", 5.0F, 5.0F);
        this.tail = new HullbackPartEntity(this, "tail", 2.5F, 2.5F);
        this.fluke = new HullbackPartEntity(this, "fluke", 4.0F, 0.6F);

        this.moving_nose = null;
        this.moving_head = null;
        this.moving_body = null;

        this.subEntities = new HullbackPartEntity[]{this.nose, this.head, this.body, this.tail, this.fluke};
        //this.moving_subEntities = new HullbackPartWalkableEntity[]{this.moving_nose, this.moving_head, this.moving_body, this.moving_tail, this.moving_fluke};
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
        this.mouthOpenProgress = 0.0f;
        this.currentTarget = this.position();
        
        // Manual inventory update since the one in super() was skipped due to null manager
        this.updateContainerEquipment();
        
        this.currentTarget = this.position();

        this.stationaryTicks = 60;
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
        // Área de verificação reduzida para permitir mais spawns
        int checkRadius = 5;   // Reduzido de 10 para 5
        int heightCheck = 4;   // Reduzido de 6 para 4

        AABB spawnCheckArea = new AABB(pos).inflate(checkRadius, heightCheck, checkRadius);

        int solidCount = 0;
        int maxSolidAllowed = 20;  // Permitir até 20 blocos sólidos (tolerância)

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = (int)spawnCheckArea.minX; x <= spawnCheckArea.maxX; x++) {
            for (int y = (int)spawnCheckArea.minY; y <= spawnCheckArea.maxY; y++) {
                for (int z = (int)spawnCheckArea.minZ; z <= spawnCheckArea.maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isSolid() && state.getCollisionShape(level, mutablePos) != Shapes.empty()) {
                        solidCount++;
                        if (solidCount > maxSolidAllowed) {
                            return false;  // Muito obstruído
                        }
                    }
                }
            }
        }

        return true;  // Spawn permitido
    }

    private static boolean hasNearbyHullbacks(LevelAccessor level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(32, 16, 32);
        return !level.getEntitiesOfClass(
                HullbackEntity.class,
                checkArea,
                e -> e.isAlive() && !e.isTamed()
        ).isEmpty();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return super.getAddEntityPacket(entity);
    }



    // forceEquipmentSync removed (redundant with HullbackEquipmentManager)



    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 1.2000000476837158).add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
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
    public void onInsideBubbleColumn(boolean downwards) {

    }

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
    }
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        ListTag listtag = new ListTag();
        
        Whaleborne.LOGGER.info("HullbackEntity.addAdditionalSaveData saving for ID: {}", this.getId());

        for(int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (!itemstack.isEmpty()) {
                Whaleborne.LOGGER.info("Saving item at slot {}: {}", i, itemstack);
                
                try {
                    // FIX: In 1.21+, itemstack.save returns a populated/new tag,
                    // does not necessarily mutate the passed one as before/requires using result.
                    // To ensure precision, we capture the return.
                    Tag savedTag = itemstack.save(this.registryAccess());
                    if (savedTag instanceof CompoundTag itemTag) {
                        itemTag.putByte("Slot", (byte)i);
                        listtag.add(itemTag);
                        Whaleborne.LOGGER.info("Saved tag for slot {}: {}", i, itemTag);
                    } else {
                        Whaleborne.LOGGER.warn("Saved tag is not a CompoundTag: {}", savedTag);
                    }
                } catch (Exception e) {
                    Whaleborne.LOGGER.error("Error saving item at slot {}: {}", i, e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        compound.put("Items", listtag);


        CompoundTag hasMobiusSpawned = new CompoundTag();
        hasMobiusSpawned.putBoolean("HasMobiusSpawned", HAS_MOBIUS_SPAWNED);

        //System.out.println("Saving seat data:");

        for (int i = 0; i < 7; i++) {
            Optional<UUID> occupant = this.entityData.get(getSeatAccessor(i));
            String seatKey = "Seat_" + i;

            //System.out.println("Seat " + i + ": " + occupant);

            if (occupant.isPresent()) {
                compound.putUUID(seatKey, occupant.get());
            }
        }
        this.HullbackDirt.addAdditionalSaveData(compound);
    }
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        
        Whaleborne.LOGGER.info("HullbackEntity.readAdditionalSaveData called for ID: {}", this.getId());
        
        // Inventory initialized in AbstractWhale
        
        ListTag items = compound.getList("Items", 10);
        Whaleborne.LOGGER.info("Loading {} items from NBT.", items.size());

        for(int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            Whaleborne.LOGGER.info("Item {} at slot {}: {}", i, slot, itemTag);
            
            if (slot < this.inventory.getContainerSize()) {
                ItemStack stack = ItemStack.parse(this.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                Whaleborne.LOGGER.info("Parsed Stack: {} (Empty: {})", stack, stack.isEmpty());
                this.inventory.setItem(slot, stack);
            }
        }

        HAS_MOBIUS_SPAWNED = compound.getBoolean("HasMobiusSpawned");

        boolean hasAnySeatData = IntStream.range(0, 7)
                .anyMatch(i -> compound.hasUUID("Seat_" + i));

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

    private CompoundTag saveDirtArray(BlockState[][] array) {
        CompoundTag tag = new CompoundTag();
        for (int x = 0; x < array.length; x++) {
            ListTag column = new ListTag();
            for (int y = 0; y < array[x].length; y++) {
                column.add(NbtUtils.writeBlockState(array[x][y]));
            }
            tag.put("x" + x, column);
        }
        return tag;
    }

    private BlockState[][] loadDirtArray(CompoundTag tag) {
        BlockState[][] array = new BlockState[tag.size()][];
        for (int x = 0; x < array.length; x++) {
            String key = "x" + x;
            if (tag.contains(key)) {
                ListTag column = tag.getList(key, 10);
                array[x] = new BlockState[column.size()];
                for (int y = 0; y < column.size(); y++) {
                    array[x][y] = NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), column.getCompound(y));
                }
            }
        }
        return array;
    }

    public float getArmorProgress() {
        return (float) this.entityData.get(DATA_ARMOR).getCount() / 64f;
    }
    public float getLeftEyeYaw() { return leftEyeYaw; }
    public float getRightEyeYaw() { return rightEyeYaw; }
    public float getEyePitch() { return eyePitch; }
    // canBreatheUnderwater is final in LivingEntity (WaterAnimal returns true by default)
    protected void handleAirSupply(int airSupply) {
    }
    public int getMaxAirSupply() {
        return 10000;
    }
    protected int increaseAirSupply(int currentAir) {
        return this.getMaxAirSupply();
    }

    public boolean isPushable() {
        return false;
    }
    public void setId(int p_20235_) {
        super.setId(p_20235_);

        for(int i = 0; i < this.subEntities.length; i++) {
            this.subEntities[i].setId(p_20235_ + i + 1);
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

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
    }

    //    private void updateWalkerPositions() {
//        for ( HullbackPartEntity part : getSubEntities()) {
//            AABB boundingBox = part.getBoundingBoxForCulling().move(0, 0.5f, 0);
//
//            List<Entity> riders = level().getEntities(part, boundingBox);
//            riders.removeIf(rider -> rider.isPassenger());
//
//            for (Entity entity : riders) {
//                if (entity instanceof HullbackEntity || entity instanceof HullbackPartEntity)
//                    return;
//                Vec3 offset = new Vec3
//            }
//        }
//    }

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
                //playSound(SoundEvents.AMETHYST_BLOCK_CHIME);
                //for (Entity entity : entities) {
                //    entity.hurtMarked = true;
                //    entity.push(0,0.05f,0);
                //}
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


        for (int side : new int[]{-1, 1}) {
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
        if (this.moving_nose != null) {
            this.moving_nose.discard();
            this.moving_nose = null;
        }
        if (this.moving_head != null) {
            this.moving_head.discard();
            this.moving_head = null;
        }
        if (this.moving_body != null) {
            this.moving_body.discard();
            this.moving_body = null;
        }
        List<HullbackWalkableEntity> list = level().getEntitiesOfClass(HullbackWalkableEntity.class, this.getBoundingBox().inflate(6));
        if (!list.isEmpty()) for (HullbackWalkableEntity entity : list) entity.discard();

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
        super.tick();


        
        if (this.level().isClientSide) {
             controlManager.clientHandleControlState();
        }
        
        // NEW: Single validation after loading from NBT
        if (!this.level().isClientSide && !validatedAfterLoad && this.tickCount > 5) {
            validatedAfterLoad = true;
            
            // Revalidate and sync equipment
            ItemStack armor = this.inventory.getItem(INV_SLOT_ARMOR);
            ItemStack crown = this.inventory.getItem(INV_SLOT_CROWN);
            ItemStack saddle = this.inventory.getItem(INV_SLOT_SADDLE);
            
            this.entityData.set(DATA_ARMOR, armor.copy());
            this.entityData.set(DATA_CROWN_ID, crown.copy());
            this.setFlag(4, !saddle.isEmpty());
            
            // Sync to all clients
            sendHurtSyncPacket();
            HullbackDirt.syncDirtToClients();
        }

        if(tickCount % 40 == 0) {
            this.entityData.set(DATA_ARMOR, this.inventory.getItem(INV_SLOT_ARMOR));
        }

        // NEW: On client, restore from entityData if necessary
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
            
            // Check saddle
            boolean hasSaddleFlag = this.getFlag(4);
            ItemStack saddleInInventory = this.inventory.getItem(INV_SLOT_SADDLE);
            
            if (hasSaddleFlag && saddleInInventory.isEmpty()) {
                this.inventory.setItem(INV_SLOT_SADDLE, new ItemStack(Items.SADDLE));
            }
        }

        if (this.level().isClientSide && getArmorProgress() > 0 && this.inventory.getItem(INV_SLOT_ARMOR).getItem().asItem() == Items.AIR.asItem()) {
            this.inventory.setItem(INV_SLOT_ARMOR, getArmor());
        }

        // MOVED TO BEFORE: Update part positions BEFORE any validation
      
        partManager.setOldPosAndRots();
        partManager.updatePartPositions();

        if (stationaryTicks>0){
            stopMoving();
            stationaryTicks--;
        }

        if (scanPlayerAbove())
            stationaryTicks=5;

        if (stationaryTicks==0) {
            if (this.moving_nose != null) {
                this.moving_nose.discard();
                this.moving_nose = null;
            }
            if (this.moving_head != null) {
                this.moving_head.discard();
                this.moving_head = null;
            }
            if (this.moving_body != null) {
                this.moving_body.discard();
                this.moving_body = null;
            }
        }

        if(!this.isEyeInFluidType(Fluids.WATER.getFluidType()))
            mouthTarget = 1;

        if(!isSaddled() && !level().isClientSide && tickCount > 20){
            if(!getPassengers().isEmpty()) {
                if (!getInventory().getItem(INV_SLOT_CROWN).isEmpty()) {
                    spawnAtLocation(getInventory().getItem(INV_SLOT_CROWN));
                    getInventory().setItem(INV_SLOT_CROWN, ItemStack.EMPTY);
                }
                ejectPassengers();
            }
        }
        else if (tickCount > 20){
            if(getArmorProgress() < 0.45f && getInventory().getItem(INV_SLOT_ARMOR).getCount() < 64 && !level().isClientSide) {
                if (!getInventory().getItem(INV_SLOT_CROWN).isEmpty()) {
                    spawnAtLocation(getInventory().getItem(INV_SLOT_CROWN));
                    getInventory().setItem(INV_SLOT_CROWN, ItemStack.EMPTY);
                }
                ejectPassengers();
            }
        }

        // MOVED: rotatePassengers now comes AFTER updatePartPositions
        hullbackSeatManager.rotatePassengers();

        if(this.getSubEntities()[1].isEyeInFluidType(Fluids.WATER.getFluidType()) && this.tickCount % 80 == 0)
            this.heal(0.25f);

        if (this.getDeltaMovement().length() > 0.3) {
            mouthTarget = 0.8f;
        }

        updateMouthOpening();
        


        LivingEntity controller = getControllingPassenger();
        
        if (controller instanceof Player player) {
             // Let getRiddenInput handle movement. Just ensure basic things here if needed.
        }



        if (this.tickCount % 80 == 0)
            mouthTarget = 0;

        // Anchor Physics (Smooth Proportional Control)
        if (hasAnchorDown() && this.isInWater()) {
             double targetY = this.level().getSeaLevel() - 5.0;
             double currentY = this.getY();
             double diff = targetY - currentY;
             
             // Proportional Control: Speed based on distance
             // Gain 0.05: Smooth response
             // Clamp -0.05 to 0.05: Limit speed to prevent abrupt movements
             double smoothSpeed = Mth.clamp(diff * 0.05, -0.05, 0.05);
             
             this.setDeltaMovement(this.getDeltaMovement().x, smoothSpeed, this.getDeltaMovement().z);
        }

        // Validate assignments after seat calculation
        if (this.tickCount % 20 == 0)
            validateAssignments();

        if (tickCount == 10)
            HullbackDirt.syncDirtToClients();

        yHeadRot = yBodyRot + (partManager.partYRot[0] - partManager.partYRot[4]) * 1.5f;

        // Use calculateSeats method
        // Calculate seats only if tamed
        if (isTamed() && partManager.partPosition != null && partManager.partYRot != null && partManager.partXRot != null) {
            partManager.calculateSeats();
        }

        if (!level().isClientSide) {
            // Bottom dirt always ticks
            if (this.random.nextInt(30000) <= 5) {
                this.HullbackDirt.randomTick(this.head.name, true);
                this.HullbackDirt.randomTick(this.body.name, true);
                this.HullbackDirt.randomTick(this.tail.name, true);
                this.HullbackDirt.randomTick(this.fluke.name, true);
                this.HullbackDirt.randomTick(this.nose.name, true);
            }

            // Top dirt ticks only if NOT tamed
            if (!isTamed()) {
                if (this.random.nextInt(30000) <= 5) {
                    this.HullbackDirt.randomTick(this.head.name, false);
                    this.HullbackDirt.randomTick(this.body.name, false);
                }
            } else {
                // Determine if we should clear top dirt (e.g. if blocks exist)
                // Using a simpler check or just clearing periodically/always as per original logic structure
                // Original: loops and clears. We can call clearTopDirt.
                // However, doing it every tick might differ from "else" block of a random check?
                // Original: if (!isTamed) { randomTick } else { clearLoops }
                // So if tamed, it clears EVERY tick.
                HullbackDirt.clearTopDirt();
            }
        }
        updateModifiers();

        for (int i=0; i < subEntities.length; i++) {
            subEntities[i].tick();

            if(i == 2 || i == 4) {
                float offset = 0;
                if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().length() > 0.03) {
                    for (int side : new int[]{-1, 1}) {
                        if(i == 2)
                            offset = 4;
                        Vec3 particlePos = partManager.partPosition[i].add(new Vec3((offset + subEntities[i].getBbWidth() / 2)*side, 0, subEntities[i].getBbWidth() / 2).yRot(partManager.partYRot[i]));
                        double x = particlePos.x;
                        double y = particlePos.y;
                        double z = particlePos.z;

                        for(int j = 0; j < 4; ++j) {
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



    public void stopMoving(){
        this.getNavigation().stop();

        if (this.moving_nose == null) {
            this.moving_nose = partManager.spawnPlatform(0);
        } else {
            if (this.tickCount % 5 == 0) {}
                //this.moving_nose.moveTo(this.nose.getX(), this.getY() + 4.7, this.nose.getZ());
        }
        
        if (this.moving_head == null) {
            this.moving_head = partManager.spawnPlatform(1);
        } else {
            if (this.tickCount % 5 == 0) {}
                //this.moving_head.moveTo(this.head.getX(), this.getY() + 4.7, this.head.getZ());
        }
        
        if (this.moving_body == null) {
            this.moving_body = partManager.spawnPlatform(2);
        } else {
            if (this.tickCount % 5 == 0) {}
                //this.moving_body.moveTo(this.body.getX(), this.getY() + 4.7, this.body.getZ());
        }

        if (hasAnchorDown()) {
            this.setPos(this.xo, this.getY(), this.zo);
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        } else {
            this.setPos(this.xo, this.yo, this.zo);
        }
        this.setYRot(yRotO);
        this.setXRot(xRotO);
    }



//    private void updateWalkerPositions() {
//        for ( HullbackPartEntity part : getSubEntities()) {
//            AABB boundingBox = part.getBoundingBoxForCulling().move(0, 0.5f, 0);
//
//            List<Entity> riders = level().getEntities(part, boundingBox);
//            riders.removeIf(rider -> rider.isPassenger());
//
//            for (Entity entity : riders) {
//                if (entity instanceof HullbackEntity || entity instanceof HullbackPartEntity)
//                    return;
//                Vec3 offset = new Vec3
//            }
//        }
//    }



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



    protected BodyRotationControl createBodyControl() {
        return new HullbackBodyRotationControl(this);
    }

    @Override
    public void onPlayerJump(int i) {

    }

    @Override
    public boolean canJump() {
        return true;
    }

    @Override
    public void handleStartJump(int i) {

    }

    @Override
    public void handleStopJump() {

    }

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
        //if (!this.level().isClientSide) {
        //    // Update seat assignment
        //    int seat = findFreeSeat();
        //    if (seat != -1) {
        //        assignSeat(seat, passenger);
        //    }
            // Sync immediately
            if (this.level() instanceof ServerLevel) {
                 ((ServerLevel) this.level()).getChunkSource().broadcast(this, new ClientboundSetPassengersPacket(this));
            }
        }
//        if (!seatAssignments.containsValue(passenger.getUUID())) {
//
//            int availableSeat = IntStream.range(0, seats.length)
//                    .filter(i -> !seatAssignments.containsKey(i))
//                    .findFirst()
//                    .orElse(0);
//
//            seatAssignments.put(availableSeat, passenger.getUUID());
//            syncSeatAssignments();
//        }
//  }

    @Override
    protected void removePassenger(Entity passenger) {
        if(passenger instanceof Player)
            stationaryTicks = 100;
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
    protected float getRiddenSpeed(Player player) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
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