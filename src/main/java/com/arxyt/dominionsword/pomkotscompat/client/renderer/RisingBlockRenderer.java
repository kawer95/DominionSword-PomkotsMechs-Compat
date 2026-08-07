package com.arxyt.dominionsword.pomkotscompat.client.renderer;

/**
 * Derived from EEEAB's Mobs (EEEABsMobs) by EEEAB, licensed under LGPL-3.0
 * (https://github.com/EEEAB/EEEABsMobs). Modified for this addon.
 */
import com.arxyt.dominionsword.pomkotscompat.entity.RisingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** Renders a flying debris block with a spin (the hammer block splash). */
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
        float age = entity.tickCount + partialTick;
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(age * entity.spinX));
        poseStack.mulPose(Axis.YP.rotationDegrees(age * entity.spinY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * entity.spinZ));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        BakedModel model = this.dispatcher.getBlockModel(blockState);
        BlockPos blockPos = entity.blockPosition();
        RandomSource random = RandomSource.create(blockState.getSeed(blockPos));
        for (RenderType renderType : model.getRenderTypes(blockState, random, ModelData.EMPTY)) {
            this.dispatcher.getModelRenderer().tesselateBlock(
                    entity.level(),
                    model,
                    blockState,
                    blockPos,
                    poseStack,
                    bufferSource.getBuffer(renderType),
                    false,
                    RandomSource.create(),
                    blockState.getSeed(blockPos),
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType
            );
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(RisingBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
