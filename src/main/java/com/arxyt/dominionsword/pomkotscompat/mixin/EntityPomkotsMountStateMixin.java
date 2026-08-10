package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures Mob AI before Pomkots can replace it on the first mounted server tick. */
@Mixin(Entity.class)
abstract class EntityPomkotsMountStateMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"))
    private void dominion$capturePomkotsPilotState(Entity vehicle, boolean force,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Mob mob && vehicle instanceof PomkotsVehicleBase) {
            PomkotsPilotState.recordBeforeMount(mob, vehicle);
        }
    }

    @Inject(method = "stopRiding()V", at = @At("TAIL"))
    private void dominion$clearUnusedPomkotsSnapshot(CallbackInfo ci) {
        if ((Object) this instanceof Mob mob) PomkotsPilotState.discardPending(mob);
    }
}
