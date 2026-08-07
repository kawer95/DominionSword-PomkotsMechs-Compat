package com.arxyt.dominionsword.pomkotscompat.client.renderer;

import com.arxyt.dominionsword.pomkotscompat.entity.RisingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Renders a ruptured ground block with the realm-warden tilt and jump animation. */
public class RisingBlockRenderer extends EntityRenderer<RisingBlockEntity> {
    private final BlockRenderDispatcher dispatcher;

    public RisingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RisingBlockEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        BlockState blockState = entity.getBlockState();
        if (blockState.getRenderShape() != RenderShape.MODEL || blockState.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.translate(0.0D, Mth.lerp(partialTick, entity.prevAnimY, entity.animY), 0.0D);
        poseStack.mulPose(entity.getRotation());
        poseStack.translate(0.0D, -1.0D, 0.0D);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        this.dispatcher.renderSingleBlock(
                blockState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(RisingBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
