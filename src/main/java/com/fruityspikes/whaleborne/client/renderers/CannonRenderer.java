package com.fruityspikes.whaleborne.client.renderers;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.client.models.CannonModel;
import com.fruityspikes.whaleborne.server.entities.CannonEntity;
import com.fruityspikes.whaleborne.server.entities.WhaleWidgetEntity;
import com.fruityspikes.whaleborne.server.registries.WBEntityModelLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext; 

public class CannonRenderer extends WhaleWidgetRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "textures/entity/cannon.png");
    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public CannonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CannonModel<>(context.bakeLayer(WBEntityModelLayers.CANNON));
        this.itemRenderer = context.getItemRenderer();
    }
    
    @Override
    public void render(WhaleWidgetEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // --- COPIED FROM WhaleWidgetRenderer (Base) ---
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        float f = (float)entity.getHurtTime() - partialTick;
        float f1 = entity.getDamage() - partialTick;
        if (f1 < 0.0F) {
            f1 = 0.0F;
        }

        if (f > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(f) * f * f1 / 10.0F * (float)entity.getHurtDir()));
        }

        poseStack.mulPose(Axis.XN.rotationDegrees(Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot())));
        
        // Setup Anim
        this.model.setupAnim(entity, partialTick, 0.0F, -0.1F, 0.0F, 0.0F);
        
        // Render Body
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        
        // --- INJECTED: Head Rendering ---
        if (entity instanceof CannonEntity cannon) {
             if (this.model instanceof CannonModel cannonModel) {
                 net.minecraft.client.model.geom.ModelPart barrelBone = cannonModel.getCannon();
                 java.util.UUID riderId = cannon.getBarrelRider();
                 if (riderId != null) {
                     // FIX: Inventory is not synced to client. Generate head locally from UUID.
                     net.minecraft.world.item.ItemStack headStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
                     
                     // Try to resolve the profile for the skin
                     net.minecraft.world.entity.player.Player player = cannon.level().getPlayerByUUID(riderId);
                     if (player != null) {
                        // Don't render if it's the local player in first person
                        if (player == net.minecraft.client.Minecraft.getInstance().player && 
                            net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                            // Skip rendering head for self in FPS to avoid blocking view
                        } else {
                            try {
                                 headStack.set(net.minecraft.core.component.DataComponents.PROFILE, new net.minecraft.world.item.component.ResolvableProfile(player.getGameProfile()));
                                 
                                 // Proceed to render if not skipped
                                 if (!headStack.isEmpty()) {
                                     poseStack.pushPose();
                                     
                                     // 1. Attach to Barrel Bone
                                     barrelBone.translateAndRotate(poseStack);
                                     
                                     // 2. Adjust position to Muzzle
                                     // Model Tip is at ~ -29Y. Pushing further to -36.0/18.0 (User manual tweak)
                                     poseStack.translate(0.0, -36.0/18.0, 0.25);
                                     
                                     // 3. Fix Rotation (Face forward - User manual tweak)
                                     poseStack.mulPose(Axis.XP.rotationDegrees(90)); 
                                     
                                     this.itemRenderer.renderStatic(headStack, 
                                         net.minecraft.world.item.ItemDisplayContext.HEAD, 
                                         packedLight, 
                                         OverlayTexture.NO_OVERLAY, 
                                         poseStack, 
                                         buffer, 
                                         entity.level(), 
                                         0);
                                         
                                     poseStack.popPose();
                                 }
                            } catch (Exception e) {
                                 // Fallback
                            }
                        }
                     }
                 }
             }
        }
        
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(WhaleWidgetEntity whaleWidgetEntity) {
        return TEXTURE;
    }
}
