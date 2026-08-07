package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.client.GroundCrackBlockHider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips chunk tessellation of ground blocks currently covered by crack entities, so the
 * original blocks are visually hidden while the tilted copies play. The world and its
 * collision are untouched; blocks reappear as soon as the crack recovers.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class GroundCrackBlockHiderMixin {
    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/client/resources/model/BakedModel;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "ZLnet/minecraft/util/RandomSource;"
                    + "JILnet/minecraftforge/client/model/data/ModelData;"
                    + "Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void dominion$skipHiddenGroundBlocks(BlockAndTintGetter level, BakedModel model,
                                                 BlockState state, BlockPos pos, PoseStack poseStack,
                                                 VertexConsumer consumer, boolean checkSides,
                                                 RandomSource random, long seed, int packedLight,
                                                 ModelData modelData, RenderType renderType,
                                                 CallbackInfo ci) {
        if (GroundCrackBlockHider.isHidden(pos)) {
            ci.cancel();
        }
    }
}
