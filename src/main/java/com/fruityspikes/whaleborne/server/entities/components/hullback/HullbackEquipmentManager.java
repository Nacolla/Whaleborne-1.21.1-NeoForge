package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Manages equipment-related functionality for the Hullback entity.
 * Handles armor, crown, saddle, and equipment synchronization.
 */
public class HullbackEquipmentManager {
    private final HullbackEntity hullback;

    // Equipment slot constants (from HullbackEntity)
    private static final int INV_SLOT_SADDLE = 1;
    private static final int INV_SLOT_ARMOR = 2;
    private static final int INV_SLOT_CROWN = 0;

    public HullbackEquipmentManager(HullbackEntity hullback) {
        this.hullback = hullback;
    }

    /**
     * Returns the armor ItemStack currently equipped.
     */
    public ItemStack getArmor() {
        return hullback.getEntityData().get(HullbackEntity.DATA_ARMOR);
    }

    /**
     * Returns the crown ItemStack currently equipped.
     */
    public ItemStack getCrown() {
        return hullback.getEntityData().get(HullbackEntity.DATA_CROWN_ID);
    }

    /**
     * Equips a saddle on the Hullback.
     * @param stack The saddle ItemStack
     * @param source Optional sound source for saddle equip sound
     */
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        hullback.inventory.setItem(INV_SLOT_SADDLE, stack);
        if (source != null) {
            hullback.level().playSound(null, hullback, SoundEvents.HORSE_SADDLE, source, 0.5F, 1.0F);
        }
    }

    /**
     * Updates equipment data from inventory to entity data.
     * Synchronizes armor, crown, and saddle status.
     * Called when inventory changes.
     */
    public void updateContainerEquipment() {
        ItemStack crown = hullback.inventory.getItem(INV_SLOT_CROWN);
        ItemStack armor = hullback.inventory.getItem(INV_SLOT_ARMOR);
        ItemStack saddle = hullback.inventory.getItem(INV_SLOT_SADDLE);
        boolean hasSaddle = !saddle.isEmpty();

        // Create copies to avoid reference problems
        hullback.getEntityData().set(HullbackEntity.DATA_CROWN_ID, crown.isEmpty() ? ItemStack.EMPTY : crown.copy());
        hullback.getEntityData().set(HullbackEntity.DATA_ARMOR, armor.isEmpty() ? ItemStack.EMPTY : armor.copy());
        hullback.setFlag(4, hasSaddle);

        // Sync immediately if on server
        if (!hullback.level().isClientSide) {
            hullback.sendHurtSyncPacket();
        }
    }

    /**
     * Called when the container (inventory) changes.
     * Plays appropriate sounds for saddle and armor changes.
     * @param invBasic The container that changed
     */
    public void containerChanged(Container invBasic) {
        ItemStack previousArmor = getArmor();
        boolean wasSaddled = hullback.isSaddled();
        
        updateContainerEquipment();
        
        ItemStack currentArmor = getArmor();
        
        // Play saddle sound if just equipped
        if (hullback.tickCount > 20 && !wasSaddled && hullback.isSaddled()) {
            hullback.playSound(hullback.getSaddleSoundEvent(), 0.5F, 1.0F);
        }
        
        // Play armor sound if armor changed
        if (hullback.tickCount > 20 && previousArmor != currentArmor) {
            hullback.playSound(SoundEvents.HORSE_ARMOR, 0.5F, 1.0F);
        }
    }

    /**
     * Forces equipment synchronization to clients.
     * Useful when equipment needs to be updated immediately.
     */
    public void forceEquipmentSync() {
        if (!hullback.level().isClientSide) {
            hullback.sendHurtSyncPacket();
        }
    }
}
