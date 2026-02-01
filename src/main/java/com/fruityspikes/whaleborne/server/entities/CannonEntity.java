package com.fruityspikes.whaleborne.server.entities;

import com.fruityspikes.whaleborne.client.menus.CannonMenu;
import com.fruityspikes.whaleborne.network.CannonFirePayload;
import com.fruityspikes.whaleborne.server.registries.WBItemRegistry;
import com.fruityspikes.whaleborne.server.registries.WBParticleRegistry;
import com.fruityspikes.whaleborne.server.registries.WBSoundRegistry;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix2dc;
import org.joml.Matrix2dc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CannonEntity extends RideableWhaleWidgetEntity implements ContainerListener, HasCustomInventoryScreen, PlayerRideableJumping {
    protected float cannonXRot;
    private IItemHandler itemHandler;

    // Multipart Entities
    public final CannonPartEntity barrel;
    private final CannonPartEntity[] parts;

    private static final EntityDataAccessor<java.util.Optional<UUID>> DATA_BARREL_RIDER = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_CANNON_XROT = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BARREL_ID = SynchedEntityData.defineId(CannonEntity.class, EntityDataSerializers.INT);
    
    // Delayed Launch Variables
    private int launchTimer = 0;
    private Entity entityToLaunch = null;
    private Vec3 launchVelocity = null;

    private static final double BARREL_LENGTH = 3.0;

    public SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
        }

        @Override
        public boolean canTakeItem(Container container, int slot, ItemStack stack) {
             // Prevent Hoppers/Automation from taking the head
             if (slot == 0 && getBarrelRider() != null) {
                 return false;
             }
             return super.canTakeItem(container, slot, stack);
        }
    };

    public CannonEntity(EntityType<?> entityType, Level level) {
        super(entityType, level, WBItemRegistry.CANNON.get());
        cannonXRot = this.getXRot();
        this.inventory.addListener(this);
        this.itemHandler = new InvWrapper(this.inventory);
        
        // Initialize Parts
        // Width/Height for Barrel Hitbox (Mouth)
        this.barrel = new CannonPartEntity(this, "barrel", 1.0F, 1.0F);
        this.parts = new CannonPartEntity[]{this.barrel};
        
        // On Server, set the ID data
        if (!level.isClientSide) {
             this.entityData.set(DATA_BARREL_ID, this.barrel.getId());
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return this.parts;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BARREL_RIDER, java.util.Optional.empty());
        builder.define(DATA_CANNON_XROT, 0.0f);
        builder.define(DATA_BARREL_ID, 0);
    }
    
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BARREL_ID.equals(key)) {
             int partId = this.entityData.get(DATA_BARREL_ID);
             if (this.barrel != null && partId != 0) {
                 this.barrel.setId(partId);
             }
        }
    }

    public void setBarrelRider(@org.jetbrains.annotations.Nullable UUID uuid) {
        this.entityData.set(DATA_BARREL_RIDER, java.util.Optional.ofNullable(uuid));
        
        if (!this.level().isClientSide) {
            if (uuid != null) {
                // Player entered: Put Head in Slot 0
                // Player entered: Put Head in Slot 0
                ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                
                // Try to get player name/profile to skin the head
                // Note: On server, we might have the entity.
                Player p = this.level().getPlayerByUUID(uuid);
                if (p != null) {
                    // Use ResolvableProfile for 1.20.5+ / 1.21
                    // Ensure imports for net.minecraft.world.item.component.ResolvableProfile
                    head.set(net.minecraft.core.component.DataComponents.PROFILE, new net.minecraft.world.item.component.ResolvableProfile(p.getGameProfile()));
                    head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, p.getDisplayName());
                } else {
                     head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Barrel Rider"));
                }
                
                // If there was something else here, drop it (unless it was another head placeholder)
                if (!inventory.getItem(0).isEmpty()) {
                     Containers.dropItemStack(this.level(), this.getX(), this.getY(), this.getZ(), inventory.getItem(0));
                }
                
                inventory.setItem(0, head);
            } else {
                // Player left: Remove Head from Slot 0 if it exists
                // We blindly clear slot 0 if it's a head, assuming it's ours. 
                // Checks preventing accidental ammo deletion if swapped in same tick are tricky, 
                // but standard logic is: Dismount = Clear Slot.
                ItemStack stack = inventory.getItem(0);
                if (stack.is(Items.PLAYER_HEAD)) { 
                    inventory.setItem(0, ItemStack.EMPTY);
                }
            }
        }
    }

    public @org.jetbrains.annotations.Nullable UUID getBarrelRider() {
        return this.entityData.get(DATA_BARREL_RIDER).orElse(null);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            // Prevent dropping the Player Head if it's just the visual placeholder for the rider
            if (getBarrelRider() != null) {
                ItemStack stack = this.inventory.getItem(0);
                if (!stack.isEmpty() && stack.is(Items.PLAYER_HEAD)) {
                     this.inventory.setItem(0, ItemStack.EMPTY);
                }
            }
            Containers.dropContents(this.level(), this.blockPosition(), this.inventory);
        }
        this.setBarrelRider(null);
        super.remove(reason);
    }

    public void setCannonXRot(float cannonXRot) {
        this.entityData.set(DATA_CANNON_XROT, cannonXRot);
    }

    public float getCannonXRot() {
        return this.entityData.get(DATA_CANNON_XROT);
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
             this.cannonXRot = this.getCannonXRot();
        }
        
        // Update Part Positions
        if (this.barrel != null) {
             double barrelLength = 2.5; // Offset to muzzle
             double heightOffset = 0.5; // From pivot
             
             double xRotRad = Math.toRadians(this.getCannonXRot());
             double yRotRad = Math.toRadians(-this.getYRot()); 
             
             double hDist = barrelLength * Math.cos(xRotRad);
             double yDist = barrelLength * Math.sin(-xRotRad); 
             
             double xOff = hDist * Math.sin(yRotRad);
             double zOff = hDist * Math.cos(yRotRad);
             
             this.barrel.setPos(this.getX() + xOff, this.getY() + yDist + heightOffset, this.getZ() + zOff);
             this.barrel.tick(); // Ensure part tick logic runs
        }
        
        // Delayed Launch Logic (Packet Race Fix)
        if (this.launchTimer > 0) {
            this.launchTimer--;
            
            if (this.launchTimer == 0 && this.entityToLaunch != null && this.launchVelocity != null) {
                // Apply Force now that client has settled
                Entity p = this.entityToLaunch;
                if (p.isAlive()) {
                    p.setDeltaMovement(this.launchVelocity);
                    p.hurtMarked = true;
                    p.hasImpulse = true;
                    p.setOnGround(false);
    
                    if (p instanceof ServerPlayer sp) {
                        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p.getId(), this.launchVelocity));
                    }
                }
                
                // Cleanup
                this.entityToLaunch = null;
                this.launchVelocity = null;
            }
        }
        
        // Update Part Positions
        if (this.barrel != null) {
             double barrelLength = 2.5; // Offset to muzzle
             double heightOffset = 0.5; // From pivot
             
             double xRotRad = Math.toRadians(this.getCannonXRot());
             double yRotRad = Math.toRadians(-this.getYRot()); 
             
             double hDist = barrelLength * Math.cos(xRotRad);
             double yDist = barrelLength * Math.sin(-xRotRad); 
             
             double xOff = hDist * Math.sin(yRotRad);
             double zOff = hDist * Math.cos(yRotRad);
             
             this.barrel.setPos(this.getX() + xOff, this.getY() + yDist + heightOffset, this.getZ() + zOff);
             this.barrel.tick(); // Ensure part tick logic runs
        }

        if(this.isVehicle()){
            // Determine who controls rotation: Gunner (Seat) > Ammo (Barrel)
            // Determine who controls rotation: Gunner (Seat) > Ammo (Barrel)
            Entity controller = null;
            for (Entity p : getPassengers()) {
                if (p.getUUID().equals(getBarrelRider())) {
                    continue;
                }
                controller = p;
                break;
            }
            // If no gunner, allow barrel rider to aim
            if (controller == null && !getPassengers().isEmpty()) {
                 controller = getPassengers().get(0);
            }
            
            if (controller != null) {
                // Fetch current synced rotation to avoid local desync/fighting
                float currentXRot = this.getCannonXRot();
                float newXRot = Mth.rotLerp(0.1f, currentXRot, controller.getXRot());
                
                this.setCannonXRot(newXRot);
                this.setYRot(Mth.rotLerp(0.1f, this.getYRot(), controller.getYRot()));
                
                // Update local field to match immediacy
                this.cannonXRot = newXRot;
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (!heldItem.isEmpty()) {
            if (isGunpowder(heldItem)) {
                return tryInsertItem(player, hand, heldItem, 1); // Gunpowder slot
            } else {
                return tryInsertItem(player, hand, heldItem, 0); // Ammo slot
            }
        }
//        else {
//            openCannonMenu(player);
//            return InteractionResult.SUCCESS;
//        }
        return super.interact(player, hand);
    }

    public InteractionResult interactPart(CannonPartEntity part, Player player, Vec3 vec, InteractionHand hand) {
        if (part == this.barrel) {
             if (getBarrelRider() == null) {
                 if (!this.level().isClientSide) {
                     // Check for obstruction (Ammo Slot 0)
                     if (!this.inventory.getItem(0).isEmpty()) {
                         player.displayClientMessage(Component.translatable("message.whaleborne.cannon_full"), true);
                         return InteractionResult.FAIL;
                     }
                     
                     setBarrelRider(player.getUUID());
                     player.startRiding(this);
                 }
                 return InteractionResult.SUCCESS;
             } else {
                 // Cannon already has a rider
                 if (!this.level().isClientSide) {
                      player.displayClientMessage(Component.translatable("message.whaleborne.cannon_full"), true);
                 }
                 return InteractionResult.FAIL;
             }
        }
        return interact(player, hand);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty() && (isGunpowder(player.getItemInHand(hand)) || isValidAmmo(player.getItemInHand(hand)))) {
            return InteractionResult.PASS; // Let interact handle item insertion
        }

        if (this.isVehicle() && this.getPassengers().size() >= 2) {
            return InteractionResult.PASS;
        }

        // Clicking Base (Seat)
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Robust disconnect handling:
        // If the Barrel Rider leaves (dismounts or disconnects), always clear the ammo slot.
        // We match by UUID to ensures we don't clear it for the wrong person, 
        // but if getBarrelRider is null, we might want to check the slot anyway? 
        // No, strict matching is safer.
        if (passenger.getUUID().equals(getBarrelRider())) {
            setBarrelRider(null);
            // double check inventory clear in case setBarrelRider failed silently
            if (!this.inventory.getItem(0).isEmpty() && this.inventory.getItem(0).is(Items.PLAYER_HEAD)) {
                 this.inventory.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        // Prevent adding if we think we have a rider but they aren't in the passenger list
        // This helps sync.
        if (getBarrelRider() != null && passenger instanceof Player p && p.getUUID().equals(getBarrelRider())) {
             return true; 
        }
        return this.getPassengers().size() < 2;
    }
    
    private boolean isValidAmmo(ItemStack stack) {
         return stack.is(Items.ENDER_PEARL) || stack.is(WBItemRegistry.BARNACLE.get()) || stack.getItem() instanceof BoatItem || stack.getItem() instanceof SpawnEggItem || stack.is(Items.TNT) || stack.is(Items.ARROW);
    }

    private boolean isGunpowder(ItemStack stack) {
        return stack.getItem() == Items.GUNPOWDER;
    }

    private InteractionResult tryInsertItem(Player player, InteractionHand hand, ItemStack stack, int slot) {
        ItemStack existing = inventory.getItem(slot);

        if (existing.isEmpty()) {
            inventory.setItem(slot, stack.split(1));
            playSound(SoundEvents.ITEM_FRAME_ADD_ITEM);
            return InteractionResult.SUCCESS;
        } else if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
            int toAdd = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(toAdd);
            stack.shrink(toAdd);
            playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1, (float) existing.getCount() /existing.getMaxStackSize());
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        if (getBarrelRider() != null && passenger.getUUID().equals(getBarrelRider())) {
             // Position in Barrel (Front) - Muzzle Position
             // Align with barrel rotation (XRot/Pitch and YRot/Yaw)
             double barrelLength = 3.0 * scale;  // Increased length to fix "too close"
             double heightOffset = -0.2 * scale; // Adjusted Y to be between "Floating" (0.5) and "Sunk" (-0.5).
             
             // Calculate offset based on rotation in Radians
             // MC Pitch: 0 is level, -90 is UP, 90 is DOWN.
             // MC Yaw: 0 is South, -90 is West, 90 is East.
             double xRotRad = Math.toRadians(this.getCannonXRot());
             double yRotRad = Math.toRadians(-this.getYRot());
             
             // Horizontal Distance (Projected on XZ plane)
             double hDist = barrelLength * Math.cos(xRotRad);
             
             // Vertical Distance (Y offset)
             // If Pitch is -90 (UP), cos(-90)=0, sin(-(-90))=1. yDist = length. Correct.
             double yDist = barrelLength * Math.sin(-xRotRad); 
             
             // X and Z Offsets
             // If Yaw is 0 (South), sin(0)=0, cos(0)=1. x=0, z=hDist. Correct (South is +Z).
             double xOff = hDist * Math.sin(yRotRad);
             double zOff = hDist * Math.cos(yRotRad);

             // Return LOCAL OFFSET (Relative to basic entity position)
             // Game adds vehicle position automatically. Returning World Pos causes double coordinates.
             return new Vec3(xOff, yDist + heightOffset, zOff);
        }
        
        // Default Seat position (Rear) - As specified by user (relative to vehicle)
        // super.getPassengerAttachmentPoint returns the vehicle position + maybe some offset.
        // We want to add (0, height - 1.25, 0) relative to THAT.
        // If super returns this.position(), then we add offset.
        // User provided snippet: return super...add(0, this.getBbHeight() - 1.25f, 0);
        return super.getPassengerAttachmentPoint(passenger, dimensions, scale).add(0, this.getBbHeight() - 1.25f, 0);
    }
    
    @Override
    @javax.annotation.Nullable
    public LivingEntity getControllingPassenger() {
        // Priority: Gunner (Not Barrel Rider)
        UUID barrelRiderId = getBarrelRider();
        for (Entity p : getPassengers()) {
            if (p instanceof LivingEntity le && (barrelRiderId == null || !p.getUUID().equals(barrelRiderId))) {
                return le;
            }
        }
        // Fallback: Barrel Rider (if they are the only one or solo)
        if (!getPassengers().isEmpty() && getPassengers().get(0) instanceof LivingEntity le) {
             return le;
        }
        return null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (passenger.getUUID().equals(getBarrelRider())) {
            // Same calculation as attachment but without seat offset (we want muzzle center)
            double barrelLength = 3.8; 
             Vec3 direction = Vec3.directionFromRotation(this.getCannonXRot(), this.getYRot());
             
            // Return GLOBAL muzzle position
            // .add(0, 1, 0) accounts for pivot height roughly
            return this.position().add(0, 1, 0).add(direction.scale(barrelLength));
        }
        return super.getDismountLocationForPassenger(passenger);
    }
    
    @Override
    public boolean canRiderInteract() {
        return true;
    }
    
    @Override
    public void onPlayerJump(int power) {
        if (this.level().isClientSide) {
            PacketDistributor.sendToServer(new CannonFirePayload(this.getId(), power));
        }
    }

    public void fireCannon(int power) {
            
            // Priority: Launch Barrel Passenger
            Entity barrelPassenger = null;
            for (Entity p : getPassengers()) {
                if (p.getUUID().equals(getBarrelRider())) {
                    barrelPassenger = p;
                    break;
                }
            }

            if (barrelPassenger != null) {
                ItemStack gunpowder = inventory.getItem(1);
                if (gunpowder.isEmpty()) {
                     level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                     return;
                }
                
                gunpowder.shrink(1);
                
                // Launch Player (Packet Race Fix: DELAYED)
                
                // 1. Calculate Vectors
                float launchPower = Math.max(power, 60f); 
                // Tuned Divisor for Approx 25 blocks range at Max Charge
                // Power 100 / 32.0 = ~3.1 blocks/tick.
                // 45 deg -> 2.2 horiz -> est range 25-30 blocks.
                double speed = (double) launchPower / 32.0;

                Vec3 lookAngle = Vec3.directionFromRotation(this.getCannonXRot(), this.getYRot());
                Vec3 dismountPos = this.getDismountLocationForPassenger((LivingEntity)barrelPassenger);

                // 2. DISMOUNT & POSITION (IMMEDIATE)
                barrelPassenger.stopRiding();
                setBarrelRider(null);

                // Teleport to Muzzle
                barrelPassenger.moveTo(dismountPos.x, dismountPos.y, dismountPos.z, this.getYRot(), this.getCannonXRot());
                if (barrelPassenger instanceof ServerPlayer sp) {
                    sp.connection.teleport(dismountPos.x, dismountPos.y, dismountPos.z, this.getYRot(), this.getCannonXRot());
                }

                // 3. SCHEDULE FORCE (DELAYED)
                // Wait 2 ticks for client to process dismount/teleport before sending velocity
                this.entityToLaunch = barrelPassenger;
                this.launchVelocity = lookAngle.scale(speed);
                this.launchTimer = 2; 

                // Effects
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F,
                        launchPower / 100 + (this.random.nextFloat() * 0.4F));
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            WBParticleRegistry.SMOKE.get(),
                            dismountPos.x, dismountPos.y, dismountPos.z,
                            50,
                            0.2,
                            0.2,
                            0.2,
                            0.1
                    );
                }
                
                return; // Fired successfully
            }

            ItemStack gunpowder = inventory.getItem(1);

            if (gunpowder.isEmpty()) {
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.0F);
                return;
            }

            gunpowder.shrink(1);
            ItemStack ammo = inventory.getItem(0).copy().split(1);

            if (ammo.isEmpty()) {
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.0F);
                return;
            } else {
                inventory.getItem(0).shrink(1);
            }

            Vec3 lookAngle = this.getFirstPassenger().getLookAngle();
            Entity projectile = null;

            if(ammo.is(Items.ENDER_PEARL)){
                projectile = new ThrownEnderpearl(
                        this.level(),
                        (LivingEntity) this.getFirstPassenger());
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENDER_PEARL_THROW, SoundSource.BLOCKS, 1.0F,
                        (float) power / 50);
            }
            else if(ammo.is(WBItemRegistry.BARNACLE.get())){
                Entity passenger = this.getFirstPassenger();
                if (passenger != null) {
                    this.ejectPassengers();

                    passenger.setDeltaMovement(
                            lookAngle.x * ((double) power),
                            lookAngle.y * ((double) power),
                            lookAngle.z * ((double) power)
                    );
                    passenger.hurtMarked = true;
                    passenger.setPose(Pose.CROUCHING);
                    level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            WBSoundRegistry.ORGAN.get(), SoundSource.BLOCKS, 1.0F,
                            (float) power / 50);
                    level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F,
                            (float) power / 50);
                }
            }
            else if(ammo.getItem().asItem() instanceof BoatItem boatItem){
                Entity passenger = this.getFirstPassenger();
                if (passenger != null) {
                    String[] boatName = ammo.getItem().asItem().toString().split("_");
                    StringBuilder boatTypeBuilder = new StringBuilder();
                    boolean hasChest = false;
                    for (int i = 0; i < boatName.length - 1; i++) {
                        if (!boatName[i].equals("chest")) {
                            if (boatTypeBuilder.length() > 0) {
                                boatTypeBuilder.append("_");
                            }
                            boatTypeBuilder.append(boatName[i]);
                        } else {
                            hasChest = true;
                        }
                    }
                    Boat boat = (Boat)(hasChest ? new ChestBoat(this.level(), this.getX(), this.getY(), this.getZ()) : new Boat(this.level(), this.getX(), this.getY(), this.getZ()));
                    boat.setVariant(Boat.Type.byName(boatTypeBuilder.toString()));
                    boat.setYRot(passenger.getYRot());
                    passenger.startRiding(boat);

                    projectile = boat;

                    level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F,
                            (float) power / 50);
                    level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F,
                            (float) power / 50);
                }
            }
            else if(ammo.getItem() instanceof SpawnEggItem spawnEggItem){
                projectile = spawnEggItem.getType(null).create(this.level());
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F,
                        (float) power / 50);
            }
            else if(ammo.is(Items.TNT)){
                projectile = new PrimedTnt(
                        this.level(), this.getX(), this.getY(), this.getZ(),
                        (LivingEntity) this.getVehicle());
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F,
                        (float) power / 50);
            }
            else if(ammo.is(Items.ARROW)){
                projectile = new Arrow(
                        this.level(), (LivingEntity) this.getVehicle(), new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0F,
                        (float) power / 50);
            }
            else {
                projectile = new ItemEntity(
                        this.level(), this.getX(), this.getY(), this.getZ(),
                        ammo);
                ((ItemEntity) projectile).setPickUpDelay(10);
                level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0F,
                        (float) power / 50);
            }

            projectile.setPos(this.position().add(0, 1, 0));
            projectile.setDeltaMovement(
                    lookAngle.x * ((double) power / 50),
                    lookAngle.y * ((double) power / 50),
                    lookAngle.z * ((double) power / 50)
            );

            this.getVehicle().push(-lookAngle.x * ((double) power / 200), 0, -lookAngle.z * ((double) power / 200));

            //tnt.setPickUpDelay(20);
            this.level().addFreshEntity(projectile);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        WBParticleRegistry.SMOKE.get(),
                        this.getX(),
                        this.getY() + 2,
                        this.getZ(),
                        5,
                        0.1,
                        0.1,
                        0.1,
                        0.02
                );
            }
            level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F,
                    power / 100 + (this.random.nextFloat() * 0.4F));
    }

    private void openCannonMenu(Player player) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("menu.title.whaleborne.cannon");
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
                    return new CannonMenu(windowId, playerInventory, CannonEntity.this);
                }
            }, buf -> buf.writeInt(this.getId()));
        }
    }



    @Override
    public boolean canJump() {
        return true;
    }

    @Override
    public void handleStartJump(int i) {
        //this.playSound(SoundEvents.BLAZE_SHOOT, (float) i /50, (float) i /50);
    }

    @Override
    public void handleStopJump() {
        //this.playSound(SoundEvents.BLAZE_SHOOT);

    }
    @Override
    public void containerChanged(Container container) {
        if (!this.level().isClientSide) {
             // Log changes
             // Whaleborne.LOGGER.info("CannonEntity Container Changed: Slot 0={}, Slot 1={}", container.getItem(0), container.getItem(1));

             // If Slot 0 is empty, but we have a rider, that means someone TOOK the head out.
             // Eject the rider.
             if (this.inventory.getItem(0).isEmpty() && getBarrelRider() != null) {
                 // Eject!
                 Entity rider = null;
                 for(Entity p : getPassengers()) {
                     if (p.getUUID().equals(getBarrelRider())) {
                         rider = p;
                         break;
                     }
                 }
                 if (rider != null) {
                     rider.stopRiding();
                     // removePassenger will be called, which calls setBarrelRider(null), 
                     // which tries to clear inventory (already empty). All good.
                 } else {
                     // Desync case: Data says rider, reality says no rider. Fix data.
                     setBarrelRider(null);
                 }
             }
        }
    }
    protected void createInventory() {
        // No-Op. Inventory is created on initialization and should not be replaced.
        // This method exists only if superclasses or other logic expects it, 
        // but in 1.20.1 it wasn't used for re-init on load.
    }
    protected int getInventorySize() {
        return 2;
    }
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag items = new ListTag();
        for(int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                // Use robust saving logic matching HullbackEntity for 1.21+
                try {
                     net.minecraft.nbt.Tag savedTag = stack.save(this.registryAccess());
                     if (savedTag instanceof CompoundTag itemTag) {
                         itemTag.putByte("Slot", (byte)i);
                         items.add(itemTag);
                     }
                } catch (Exception e) {
                    // Log error if needed, but ensure we don't crash saving
                }
            }
        }
        tag.put("CannonItems", items);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // FIX: Do NOT call createInventory(). Inventory is final-ish and persists.
        // Just read the data into the existing container.
        
        if (tag.contains("CannonItems")) {
            ListTag items = tag.getList("CannonItems", 10);
            
            for(int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < this.inventory.getContainerSize()) {
                    ItemStack stack = ItemStack.parse(this.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                    this.inventory.setItem(slot, stack);
                }
            }
        }
    }
    @Override
    public void openCustomInventoryScreen(Player player) {
        openCannonMenu(player);
    }
}
