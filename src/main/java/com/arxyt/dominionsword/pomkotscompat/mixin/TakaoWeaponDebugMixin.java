package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Temporary server-side debug traces for the Takao charge action lifecycle. Logs charge
 * start, the release that sets the fire tick, and any reset while an action is active
 * (which would explain swings that animate but deal no damage). Remove once diagnosed.
 */
@Mixin(ActionWeapon.class)
public abstract class TakaoWeaponDebugMixin {
    @Shadow(remap = false) private int currentActionTick;
    @Shadow(remap = false) private int fireStartTick;
    @Shadow(remap = false) private Pmvc01Entity owner;

    @Inject(method = "startAction()Z", at = @At("HEAD"), remap = false)
    private void dominion$debugChargeStart(CallbackInfoReturnable<Boolean> cir) {
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-TAKAO] startAction mech={} tick={} fireStart={}",
                owner == null ? "?" : owner.getUUID(), currentActionTick, fireStartTick);
    }

    @Inject(method = "fireAction()V", at = @At("HEAD"), remap = false)
    private void dominion$debugFireAction(CallbackInfo ci) {
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-TAKAO] fireAction(release) mech={} tick={} fireStart={}",
                owner == null ? "?" : owner.getUUID(), currentActionTick, fireStartTick);
    }

    @Inject(method = "reset()V", at = @At("HEAD"), remap = false)
    private void dominion$debugReset(CallbackInfo ci) {
        if (currentActionTick < 0) {
            return;
        }
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-TAKAO] RESET-while-active mech={} tick={} fireStart={} fuelNow={}",
                owner == null ? "?" : owner.getUUID(), currentActionTick, fireStartTick,
                owner == null ? -1 : owner.getFuelNow());
    }
}
