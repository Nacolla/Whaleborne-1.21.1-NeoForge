package com.fruityspikes.whaleborne.mixin;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "fuzs.healthbars.client.helper.EntityVisibilityHelper", remap = false)
public class HealthBarsVisibilityMixin {

    @Redirect(method = "shouldShowName", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isVehicle()Z", remap = true))
    private static boolean preventHullbackVehicleCheck(LivingEntity instance) {
        if (instance instanceof HullbackEntity) {
            return false;
        }
        return instance.isVehicle();
    }
}
