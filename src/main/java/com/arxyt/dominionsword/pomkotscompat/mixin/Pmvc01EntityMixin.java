package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import grcmcs.minecraft.mods.pomkotsmechs.entity.npc.pilot.ai.MechAutoController;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Pmvc01Entity.class)
public abstract class Pmvc01EntityMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/npc/pilot/ai/MechAutoController;tick()V"
            )
    )
    private void dominion$pauseNativeMobController(MechAutoController controller) {
        MechControlBridge bridge = (MechControlBridge) this;
        if (!bridge.dominion$getControlFrame().active()) {
            controller.tick();
        }
    }
}
