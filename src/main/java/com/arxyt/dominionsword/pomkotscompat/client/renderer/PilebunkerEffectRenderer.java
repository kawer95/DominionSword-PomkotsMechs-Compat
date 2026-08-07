package com.arxyt.dominionsword.pomkotscompat.client.renderer;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.entity.PilebunkerEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Renders a PMV01 pilebunker-style ring of spinning light blades at the impact point: 24 thin
 * vertical blades arranged in a circle, growing and fading like the native pileeffek disc.
 */
public class PilebunkerEffectRenderer extends EntityRenderer<PilebunkerEffectEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DominionSwordPomkotsCompatMod.MODID, "textures/entity/pilebunker_effect.png");
    private static final int BLADE_COUNT = 24;
    private static final float LIFETIME = 14.0F;

    public PilebunkerEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PilebunkerEffectEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTick;
        float progress = Math.min(1.0F, age / LIFETIME);
        float envelope = (float) Math.sin(Math.PI * progress);
        float radius = 0.6F + 5.4F * envelope;
        float height = radius * 0.9F;
        float width = Math.max(0.12F, radius * 0.11F);
        float rotation = progress * 720.0F;
        float alpha = Math.min(1.0F, envelope * 1.5F);

        poseStack.pushPose();
        poseStack.translate(0.0D, 1.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        for (int i = 0; i < BLADE_COUNT; i++) {
            double angle = (Math.PI * 2.0D * i) / BLADE_COUNT;
            double cx = Math.cos(angle) * radius;
            double cz = Math.sin(angle) * radius;
            double tx = -Math.sin(angle) * width / 2.0D;
            double tz = Math.cos(angle) * width / 2.0D;
            consumer.vertex(pose, (float) (cx + tx), 0.0F, (float) (cz + tz))
                    .color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 1.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
            consumer.vertex(pose, (float) (cx - tx), 0.0F, (float) (cz - tz))
                    .color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 1.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
            consumer.vertex(pose, (float) (cx - tx), height, (float) (cz - tz))
                    .color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 0.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
            consumer.vertex(pose, (float) (cx + tx), height, (float) (cz + tz))
                    .color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 0.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(PilebunkerEffectEntity entity) {
        return TEXTURE;
    }
}
