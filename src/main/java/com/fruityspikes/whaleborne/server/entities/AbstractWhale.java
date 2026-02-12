package com.fruityspikes.whaleborne.server.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import javax.annotation.Nullable;

    public abstract class AbstractWhale extends WaterAnimal implements ContainerListener {

    public SimpleContainer inventory;

    public static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(AbstractWhale.class, EntityDataSerializers.BYTE);

    protected AbstractWhale(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);
        this.createInventory();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_FLAGS, (byte)0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if(!this.inventory.getItem(getSaddleSlot()).isEmpty())
            compound.put("SaddleItem", this.inventory.getItem(getSaddleSlot()).save(this.registryAccess()));
        compound.putByte("Flags", this.entityData.get(DATA_ID_FLAGS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("SaddleItem", 10)) {
            ItemStack itemstack = ItemStack.parse(this.registryAccess(), compound.getCompound("SaddleItem")).orElse(ItemStack.EMPTY);
            if (itemstack.is(Items.SADDLE)) {
                this.inventory.setItem(getSaddleSlot(), itemstack);
            }
        }
        if (compound.contains("Flags")) {
            this.entityData.set(DATA_ID_FLAGS, compound.getByte("Flags"));
        }
        this.updateContainerEquipment();
    }

    protected abstract int getInventorySize();
    protected abstract int getSaddleSlot();

    protected void createInventory() {
        SimpleContainer simplecontainer = this.inventory;
        this.inventory = new SimpleContainer(getInventorySize());
        if (simplecontainer != null) {
            simplecontainer.removeListener(this);
            int i = Math.min(simplecontainer.getContainerSize(), this.inventory.getContainerSize());

            for(int j = 0; j < i; ++j) {
                ItemStack itemstack = simplecontainer.getItem(j);
                if (!itemstack.isEmpty()) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

        this.inventory.addListener(this);
        this.updateContainerEquipment();
    }

    protected void updateContainerEquipment() {
        if (!this.level().isClientSide) {
            this.setFlag(4, !this.inventory.getItem(getSaddleSlot()).isEmpty());
        }
    }

    public void containerChanged(Container invBasic) {
        boolean flag = this.isSaddled();
        this.updateContainerEquipment();
        if (this.tickCount > 20 && !flag && this.isSaddled()) {
            this.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 1.0F);
        }
    }

    public boolean isTamed() {
        return this.getFlag(2);
    }
    public void setTamed(boolean tamed) {
        this.setPersistenceRequired();
        this.setFlag(2, tamed);
    }

    public boolean isSaddled() {
        return this.getFlag(4);
    }

    public boolean isSaddleable() {
        return this.isAlive() && this.isTamed();
    }

    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        this.inventory.setItem(getSaddleSlot(), stack);
        if (source != null) {
            this.level().playSound(null, this, SoundEvents.HORSE_SADDLE, source, 0.5F, 1.0F);
        }
    }

    protected boolean getFlag(int flagId) {
        return ((Byte)this.entityData.get(DATA_ID_FLAGS) & flagId) != 0;
    }

    public void setFlag(int flagId, boolean value) {
        byte b0 = (Byte)this.entityData.get(DATA_ID_FLAGS);
        if (value) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 | flagId));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 & ~flagId));
        }
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public boolean isMultipartEntity() {
        return true;
    }

    public abstract PartEntity<?>[] getParts();

    public boolean isPushable() {
        return false;
    }

    public void setId(int id) {
        super.setId(id);
        PartEntity<?>[] parts = this.getParts();
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(id + i + 1);
        }
    }

    public boolean isPickable() {
        return false;
    }
}
