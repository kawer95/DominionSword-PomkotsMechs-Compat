package com.arxyt.dominionsword.pomkotscompat.mixin;

import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.Motion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces PMVC01's saber arm animation with PMV01's full pilebunker attack animation.
 */
@Mixin(Pmvc01Entity.class)
public abstract class Pmvc01SaberAnimationMixin {
    @Redirect(
            method = "addExtraAnimationController",
            at = @At(
                    value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/custom/Motion;"
                            + "getAnimationName(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
                            + "custom/ActionWeapon;Ljava/lang/String;)Ljava/lang/String;"
            ),
            remap = false
    )
    private String dominion$pilebunkerSaberAnimation(Motion motion, ActionWeapon act, String side) {
        if (motion == Motion.SABER) {
            return "animation.pmv01.pilebunker";
        }
        return motion.getAnimationName(act, side);
    }
}
