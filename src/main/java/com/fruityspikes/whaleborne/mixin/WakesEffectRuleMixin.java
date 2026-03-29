package com.fruityspikes.whaleborne.mixin;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackPartEntity;
import com.fruityspikes.whaleborne.server.entities.WhaleWidgetEntity;
import com.leclowndu93150.wakes.config.enums.EffectSpawningRule;
import com.leclowndu93150.wakes.utils.WakesUtils;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into the Wakes mod's WakesUtils.getEffectRuleFromSource to:
 * 1. DISABLE wake generation for HullbackEntity (submerged hitbox — we handle via parts)
 * 2. DISABLE wake generation for HullbackPartEntity (driven from WakesCompat)
 * 3. DISABLE wake generation for WhaleWidgetEntity (masts, sails, etc. — handled by WakesCompat)
 *
 * This prevents the Wakes mod's default Entity.tick() mixin from interfering,
 * while our WakesCompat class generates proper per-part and per-widget wakes.
 */
@Mixin(value = WakesUtils.class, remap = false)
public class WakesEffectRuleMixin {

    @Inject(method = "getEffectRuleFromSource", at = @At("HEAD"), cancellable = true)
    private static void whaleborne$overrideHullbackRule(Entity source, CallbackInfoReturnable<EffectSpawningRule> cir) {
        if (source instanceof HullbackEntity
                || source instanceof HullbackPartEntity
                || source instanceof WhaleWidgetEntity) {
            cir.setReturnValue(EffectSpawningRule.DISABLED);
        }
    }
}
