package com.fruityspikes.whaleborne.client.events;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.server.entities.CannonEntity;
import com.fruityspikes.whaleborne.server.entities.HelmEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Whaleborne.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientEvents {
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Player player = Minecraft.getInstance().player;

        if (player != null && player.getRootVehicle() instanceof HullbackEntity hullback) {
            double newRoll = Mth.lerp(0.5f, event.getRoll(), Mth.clamp((float) ((hullback.getPartYRot(3) - hullback.getPartYRot(0)) * 0.25f * Minecraft.getInstance().options.fovEffectScale().get()), -2.23f, 2.23f));
            event.setRoll((float) newRoll);
        }
    }
    @SubscribeEvent
    public static void onFovUpdate(ViewportEvent.ComputeFov event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.getRootVehicle() instanceof HullbackEntity hullback) {
            if (hullback.getDeltaMovement().length() > 0.2) {
                double newFOV = Mth.lerp(0.5f, event.getFOV(), event.getFOV() + hullback.getDeltaMovement().length() * (Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK ? 50 : 20) * Minecraft.getInstance().options.fovEffectScale().get());
                event.setFOV(newFOV);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.getVehicle() instanceof HelmEntity) {
            if (player.getRootVehicle() instanceof HullbackEntity hullback) {
                if (hullback.getDeltaMovement().length() > 0.2) {
                    event.getRenderer().getModel().rightArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                    event.getRenderer().getModel().leftArmPose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                }
            }
        }
        
        if (player.getVehicle() instanceof CannonEntity cannon) {
            if (player.getUUID().equals(cannon.getBarrelRider())) {
                event.setCanceled(true); // Completely hide player (Body, Skin, Armor, Equipment)
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (event.getRenderer().getModel() instanceof PlayerModel model) {
            if (!model.body.visible) { 
                model.body.visible = true;
                model.leftArm.visible = true;
                model.rightArm.visible = true;
                model.leftLeg.visible = true;
                model.rightLeg.visible = true;
                model.jacket.visible = true;
                model.leftSleeve.visible = true;
                model.rightSleeve.visible = true;
                model.leftPants.visible = true;
                model.rightPants.visible = true;
            }
        } else {
            HumanoidModel<?> model = event.getRenderer().getModel();
             if (!model.body.visible) {
                model.body.visible = true;
                model.leftArm.visible = true;
                model.rightArm.visible = true;
                model.leftLeg.visible = true;
                model.rightLeg.visible = true;
             }
        }
    }
}
