package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.network.ToggleControlPayload;
import com.fruityspikes.whaleborne.server.entities.HelmEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class HullbackControlManager {
    private final HullbackEntity whale;
    
    // Cache for third person mod check
    private static Boolean IS_THIRD_PERSON_MOD_LOADED = null;

    public HullbackControlManager(HullbackEntity whale) {
        this.whale = whale;
    }

    @OnlyIn(Dist.CLIENT)
    public void clientHandleControlState() {
        // Check if the local player is controlling
        // Robust check for Helm/Multipart
        LivingEntity controller = whale.getControllingPassenger();
        
        if (controller == null) {
             try {
                 net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
                 if (local != null && local.getRootVehicle() == whale) {
                     controller = local;
                 }
             } catch (Exception e) {}
        }

        if (controller instanceof Player player && player == net.minecraft.client.Minecraft.getInstance().player) {
            
            // Check mod presence only once and cache it
            if (IS_THIRD_PERSON_MOD_LOADED == null) {
                IS_THIRD_PERSON_MOD_LOADED = ModList.get().isLoaded("leawind_third_person");
            }

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.options == null) return;

            boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
            // Logic: Enable vector if mod is present AND NOT in first person
            boolean shouldVector = IS_THIRD_PERSON_MOD_LOADED && !isFirstPerson;
            
            boolean currentVectorState = whale.getEntityData().get(HullbackEntity.DATA_VECTOR_CONTROL);

            // Only send packet if DESIRED state is different from CURRENT
            if (shouldVector != currentVectorState) {
                // 1. Local Prediction (Update immediately to avoid visual lag/spam)
                whale.getEntityData().set(HullbackEntity.DATA_VECTOR_CONTROL, shouldVector);
                
                // 2. Send to server to confirm
                PacketDistributor.sendToServer(new ToggleControlPayload(whale.getId(), shouldVector));
            }
        }
    }

    public boolean isVectorControlActive() {
        // Logic 1: If Client, look at camera IMMEDIATELY and check if it's local player.
        if (whale.level().isClientSide) {
             return isVectorControlActiveClient();
        }
        
        // Logic 2: If Server (or another player viewing), trust synchronized data.
        return whale.getEntityData().get(HullbackEntity.DATA_VECTOR_CONTROL);
    }

    @OnlyIn(Dist.CLIENT)
    private boolean isVectorControlActiveClient() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || whale.getControllingPassenger() != mc.player) {
            // If I am not piloting, trust visual entityData
            return whale.getEntityData().get(HullbackEntity.DATA_VECTOR_CONTROL);
        }

        // Cache mod check (safety)
        if (IS_THIRD_PERSON_MOD_LOADED == null) {
            IS_THIRD_PERSON_MOD_LOADED = ModList.get().isLoaded("leawind_third_person");
        }

        // Absolute priority for current camera
        boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
        return IS_THIRD_PERSON_MOD_LOADED && !isFirstPerson;
    }

    public Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        boolean hasInput = Mth.abs(player.xxa) > 0 || Mth.abs(player.zza) > 0;

        if (hasInput) {
            if (whale.hasAnchorDown()) {
                if (whale.tickCount % 10 == 0) whale.playSound(SoundEvents.WOOD_HIT, 1, 1);
                return Vec3.ZERO; 
            }
            
            if (whale.tickCount % 2 == 0) whale.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.5f, 1.0f);
             
            if(whale.getControllingPassenger() != null && whale.getControllingPassenger().getVehicle() instanceof HelmEntity helmEntity){
                 helmEntity.setWheelRotation(helmEntity.getWheelRotation() + player.xxa / 10);
            }
        } else {
             if(whale.getControllingPassenger() != null && whale.getControllingPassenger().getVehicle() instanceof HelmEntity helmEntity){
                helmEntity.setPrevWheelRotation(helmEntity.getWheelRotation());
            }
        }

        boolean vectorControl = isVectorControlActive();
        
        float xxa = player.xxa * 0.5F;
        float zza = player.zza;

        if (vectorControl) {
            // --- VECTOR MODE (3rd Person) ---
            if (hasInput) {
                // Calculates rotation based on relative camera input
                float targetYaw = player.getYRot() - (float)(Mth.atan2(player.xxa, player.zza) * (180D / Math.PI));
                
                whale.setYRot(Mth.rotLerp(0.05f, whale.getYRot(), targetYaw));
                whale.yBodyRot = whale.getYRot();
                whale.yHeadRot = whale.getYRot();
                
                // Converts lateral movement to forward force
                zza = Mth.sqrt(xxa * xxa + zza * zza); 
                xxa = 0; 
            } else {
                zza = 0;
            }
        } else {
            // --- TANK MODE (1st Person / Vanilla) ---
            if (zza <= 0.0F) {
                zza *= 0.25F; // Slower reverse
            }
            
            // The lateral input (A/D) turns into ROTATION, not lateral movement
            if (player.xxa != 0) {
                whale.setYRot(Mth.rotLerp(0.8f, whale.getYRot(), whale.getYRot() - player.xxa)); 
                whale.yBodyRot = whale.getYRot();
            }
            
            // IMPORTANT: Zero the xxa to prevent "drift" lateral in first person
            xxa = 0; 
        }

        float f3 = 0;
        // Accessing nose via getter if available, or just check via whale if public? 
        // Need access to nose. It's private in HullbackEntity. 
        // But partManager has subEntities array.
        // Or I can just check if whale is in water? Original checked nose.isEyeInFluidType.
        // Assuming whale.getPartManager().subEntities[0] is nose.
        
        if(whale.getPartManager() != null && whale.getPartManager().subEntities[0].isEyeInFluidType(Fluids.WATER.getFluidType()))
             f3 = 1;

        // Anchor
        if (whale.hasAnchorDown() && whale.isInWater()) {
             double targetY = whale.level().getSeaLevel() - 5.0;
             double currentY = whale.getY();
             double diff = targetY - currentY;
             f3 = (float) Mth.clamp(diff * 0.05, -0.05, 0.05);
             
             zza = 0; // Ensure no forward movement with anchor
        }

        return new Vec3(0, f3, zza); 
    }
}
