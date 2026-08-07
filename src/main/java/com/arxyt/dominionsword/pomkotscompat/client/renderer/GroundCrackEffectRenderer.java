package com.arxyt.dominionsword.pomkotscompat.client.renderer;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.entity.GroundCrackEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Renders the realm-warden ground-pound ring: a soft radial ring lying flat on the ground,
 * growing outward and fading like the ground cracking open and then recovering. */
public class GroundCrackEffectRenderer extends EntityRenderer<GroundCrackEffectEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DominionSwordPomkotsCompatMod.MODID, "textures/entity/ground_crack.png");
    private static final float LIFETIME = 16.0F;

    public GroundCrackEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GroundCrackEffectEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTick;
        float progress = Math.min(1.0F, age / LIFETIME);
        float radius = 8.0F * progress * entity.getEffectScale();
        float alpha = Math.max(0.0F, 1.0F - progress);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.05D, 0.0D);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        float r = radius;
        consumer.vertex(pose, -r, 0.0F, -r).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
        consumer.vertex(pose, -r, 0.0F, r).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
        consumer.vertex(pose, r, 0.0F, r).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
        consumer.vertex(pose, r, 0.0F, -r).color(1.0F, 1.0F, 1.0F, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).endVertex();
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GroundCrackEffectEntity entity) {
        return TEXTURE;
    }
}
