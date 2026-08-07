package com.arxyt.dominionsword.pomkotscompat.mixin;

import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces PMVC01's saber arm animation with PMV01's full pilebunker attack animation.
 * Motion.SABER is the anonymous Motion$7 subclass in the released alpha.8 jar.
 */
@Mixin(targets = "grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.Motion$7")
public abstract class Pmvc01SaberAnimationMixin {
    @Inject(method = "getAnimationName", at = @At("HEAD"), cancellable = true, remap = false)
    private void dominion$pilebunkerSaberAnimation(ActionWeapon act, String side, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("animation.pmv01.pilebunker");
    }
}
