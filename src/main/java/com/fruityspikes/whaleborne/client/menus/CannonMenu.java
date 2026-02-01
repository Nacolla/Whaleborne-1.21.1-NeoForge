package com.fruityspikes.whaleborne.client.menus;

import com.fruityspikes.whaleborne.server.entities.CannonEntity;
import com.fruityspikes.whaleborne.server.registries.WBMenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.ClickType;

public class CannonMenu extends AbstractContainerMenu {
    private final Container cannonContainer;
    private final CannonEntity cannon;

    public static CannonMenu fromNetwork(int windowId, Inventory inv, FriendlyByteBuf data) {
        int entityId = data.readInt();
        Level level = inv.player.level();
        Entity entity = level.getEntity(entityId);

        if (entity instanceof CannonEntity cannon) {
            return new CannonMenu(windowId, inv, cannon);
        }
        throw new IllegalStateException("Invalid cannon entity");
    }

    public CannonMenu(int windowId, Inventory playerInventory, CannonEntity cannon) {
        super(WBMenuRegistry.CANNON_MENU.get(), windowId);

        this.cannon = cannon;
        this.cannonContainer = cannon.inventory;

        cannonContainer.startOpen(playerInventory.player);

        this.addSlot(new Slot(cannon.inventory, 0, 79, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });

        this.addSlot(new Slot(cannon.inventory, 1, 79, 51) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.GUNPOWDER;
            }
        });

        for(int i = 0; i < 3; ++i) {
            for(int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for(int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(cannon) < 8.0 * 8.0;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (slot.container == cannon.inventory && slot.getSlotIndex() == 0) {
                 // Interaction with the Ammo Slot (Slot 0)
                 if (cannon.getBarrelRider() != null) {
                     // There is a rider, so this slot contains the "Phantom Head"
                     // Cancel ANY standard interaction that would give the item
                     if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP) {
                          // Allow the "Pick up" action visually to empty the slot, but destroy the cursor item immediately?
                          // Better: Just clear the slot. This triggers 'containerChanged' in Entity -> Eject.
                          slot.set(ItemStack.EMPTY);
                          
                          // If swapping, we need to handle the item being swapped IN
                          if (clickType == ClickType.SWAP && button >= 0 && button < 9) {
                               // The player is trying to swap an item from hotbar to here.
                               ItemStack hotbarItem = player.getInventory().getItem(button);
                               slot.set(hotbarItem.copy()); // Put the new item in
                               // The old head is GONE.
                          }
                          
                          // Play UI sound
                          // player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.25f, 1.0f);
                          return; // Suppress default logic which might put the head in cursor
                     }
                 }
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < 2) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (itemstack1.getItem() == Items.GUNPOWDER) {
                    if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            
            // Security check: If we moved FROM Slot 0, and it was a rider head, DESTROY the copy we just made.
            if (index == 0 && cannon.getBarrelRider() != null) {
                 itemstack = ItemStack.EMPTY; // Don't return the item to be held/moved
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }
    public void removed(Player player) {
        super.removed(player);
        this.cannonContainer.stopOpen(player);
    }
}
