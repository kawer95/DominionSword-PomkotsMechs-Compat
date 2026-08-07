package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Supplies the ported PMV01 pilebunker animation as a fallback animation file for PMVC01,
 * so its controller can play the animation without replacing the 293 KB native file.
 */
@Mixin(GeoModel.class)
public abstract class Pmvc01AnimationFallbackMixin {
    private static final String PILEBUNKER_ANIMATION = "animation.pmv01.pilebunker";

    @Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void dominion$resolvePilebunkerAnimation(GeoAnimatable animatable, String name,
                                                     CallbackInfoReturnable<Animation> cir) {
        if (!(animatable instanceof Pmvc01Entity) || !PILEBUNKER_ANIMATION.equals(name)) return;
        ResourceLocation file = new ResourceLocation(DominionSwordPomkotsCompatMod.MODID,
                "animations/pmvc01_pilebunker.json");
        var baked = GeckoLibCache.getBakedAnimations().get(file);
        Animation animation = baked == null ? null : baked.getAnimation(name);
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-ANIM] resolve pilebunker mech={} cached={} found={}",
                ((Pmvc01Entity) animatable).getUUID(), baked != null, animation != null);
        if (animation != null) cir.setReturnValue(animation);
    }

    @Inject(method = "getAnimationResourceFallbacks", at = @At("RETURN"), cancellable = true, remap = false)
    private void dominion$addPilebunkerAnimation(GeoAnimatable animatable, CallbackInfoReturnable<ResourceLocation[]> cir) {
        if (animatable instanceof Pmvc01Entity) {
            cir.setReturnValue(new ResourceLocation[]{
                    new ResourceLocation(DominionSwordPomkotsCompatMod.MODID, "animations/pmvc01_pilebunker.json")
            });
        }
    }
}
