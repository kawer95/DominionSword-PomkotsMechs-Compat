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
 * Swaps PMVC01's saber arm animations for the ported PMV01 pilebunker animation. The native
 * controller keeps requesting "animation.pmv01.w_saber_*" (a name that is guaranteed to resolve),
 * and this mixin substitutes the pilebunker animation content at resolution time.
 */
@Mixin(GeoModel.class)
public abstract class Pmvc01AnimationFallbackMixin {
    @Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private void dominion$replaceSaberWithPilebunker(GeoAnimatable animatable, String name,
                                                     CallbackInfoReturnable<Animation> cir) {
        if (!(animatable instanceof Pmvc01Entity)) return;
        if (!"animation.pmv01.w_saber_right".equals(name)
                && !"animation.pmv01.w_saber_left".equals(name)) return;
        ResourceLocation file = new ResourceLocation(DominionSwordPomkotsCompatMod.MODID,
                "animations/pmvc01_pilebunker.json");
        var baked = GeckoLibCache.getBakedAnimations().get(file);
        Animation animation = baked == null ? null : baked.getAnimation("animation.pmv01.pilebunker");
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-ANIM] replace saber {} mech={} cached={} found={}",
                name,
                ((Pmvc01Entity) animatable).getUUID(), baked != null, animation != null);
        if (animation != null) cir.setReturnValue(animation);
    }
}
