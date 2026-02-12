package com.fruityspikes.whaleborne.mixin;

import com.fruityspikes.whaleborne.Config;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import fuzs.healthbars.client.helper.HealthTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiFunction;

@Mixin(targets = "fuzs.healthbars.client.handler.InLevelRenderingHandler", remap = false)
public abstract class HealthBarsCompatMixin {

    @Shadow
    private static void renderHealthBar(PoseStack poseStack, float partialTick, int packedLight, HealthTracker healthTracker, LivingEntity livingEntity, int heightOffset, Font font, BiFunction<PoseStack, Integer, GuiGraphics> factory, @Nullable RenderType renderType) {
        throw new AssertionError();
    }

    @Redirect(
        method = "onRenderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lfuzs/healthbars/client/handler/InLevelRenderingHandler;renderHealthBar(Lcom/mojang/blaze3d/vertex/PoseStack;FILfuzs/healthbars/client/helper/HealthTracker;Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/client/gui/Font;Ljava/util/function/BiFunction;Lnet/minecraft/client/renderer/RenderType;)V"
        )
    )
    private static void redirectRenderHealthBar(PoseStack poseStack, float partialTick, int packedLight, HealthTracker healthTracker, LivingEntity livingEntity, int heightOffset, Font font, BiFunction<PoseStack, Integer, GuiGraphics> factory, @Nullable RenderType renderType) {
        if (livingEntity instanceof HullbackEntity) {
            // Logic improvement:
            // 1. Multiply by 10 to match Neat's scale (~10 pixels per unit feels closer to block-like adjustments).
            // 2. Subtract from heightOffset because in GUI rendering, Y increases downwards.
            //    So "Move UP" means "Decrease Y".
            // 3. User requested that the old "-2" be the new "0".
            //    f(new_config) = old_f(new_config - 2)
            //    old_f(x) = -(x * 10)
            //    new_f(x) = -((x - 2) * 10)
            heightOffset -= ((Config.healthBarsOffset - 2) * 10);
        }
        renderHealthBar(poseStack, partialTick, packedLight, healthTracker, livingEntity, heightOffset, font, factory, renderType);
    }
}
