package com.arxyt.dominionsword.pomkotscompat.client.renderer;

/**
 * Derived from EEEAB's Mobs (EEEABsMobs) by EEEAB, licensed under LGPL-3.0
 * (https://github.com/EEEAB/EEEABsMobs). Modified for this addon.
 */
import com.arxyt.dominionsword.pomkotscompat.entity.GroundCrackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a cracked-open ground block with the realm-warden tilt/jump animation. The block
 * rises and tilts open, holds, then straightens back (摆正) into the ground before vanishing.
 */
public class GroundCrackBlockRenderer extends EntityRenderer<GroundCrackBlockEntity> {
    private final BlockRenderDispatcher dispatcher;

    public GroundCrackBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GroundCrackBlockEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        BlockState blockState = entity.getBlockState();
        if (blockState.getRenderShape() != RenderShape.MODEL || blockState.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        float t = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.translate(0.0D, entity.getAnimYAt(t), 0.0D);
        poseStack.mulPose(entity.getQuaternionAt(t));
        poseStack.translate(0.0D, -1.0D, 0.0D);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        this.dispatcher.renderSingleBlock(
                blockState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GroundCrackBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
