package com.fruityspikes.whaleborne.network;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public class ClientPacketHandler {
    public static void handleDirtSync(SyncHullbackDirtPayload payload) {
        Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
        if (entity instanceof HullbackEntity hullback) {
            BlockState[][] dirtArray = SyncHullbackDirtPayload.deserializeDirtArray(payload.dirtData());

            switch (payload.arrayType()) {
                case 0 -> hullback.headDirt = dirtArray;
                case 1 -> hullback.headTopDirt = dirtArray;
                case 2 -> hullback.bodyDirt = dirtArray;
                case 3 -> hullback.bodyTopDirt = dirtArray;
                case 4 -> hullback.tailDirt = dirtArray;
                case 5 -> hullback.flukeDirt = dirtArray;
            }
        }
    }

    public static void handleHullbackHurtSync(HullbackHurtPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity instanceof HullbackEntity whale) {

                whale.inventory.setItem(HullbackEntity.INV_SLOT_ARMOR, payload.armorItem());
                whale.inventory.setItem(HullbackEntity.INV_SLOT_CROWN, payload.crownItem());

                whale.getEntityData().set(HullbackEntity.DATA_ARMOR, payload.armorItem());
                whale.getEntityData().set(HullbackEntity.DATA_CROWN_ID, payload.crownItem());
                whale.getEntityData().set(HullbackEntity.DATA_ID_FLAGS, payload.flags());

                whale.updateContainerEquipment();
            }
        }
    }
}
