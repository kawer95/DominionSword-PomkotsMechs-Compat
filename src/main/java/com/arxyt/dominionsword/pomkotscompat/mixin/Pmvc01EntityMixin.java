package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.npc.pilot.ai.MechAutoController;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pmvc01Entity.class)
public abstract class Pmvc01EntityMixin {
    @Unique private Mob dominion$trackedPilot;

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), remap = false, require = 0)
    private void dominion$trackPilot(CallbackInfo ci) {
        Pmvc01Entity mech = (Pmvc01Entity) (Object) this;
        Entity current = mech.getDrivingPassenger();
        if (dominion$trackedPilot != null && dominion$trackedPilot != current) {
            PomkotsPilotState.restore(dominion$trackedPilot);
            dominion$trackedPilot = null;
        }
        if (current instanceof Mob mob && PomkotsPilotState.belongsTo(mob, mech)) {
            dominion$trackedPilot = mob;
        }
    }

    @Redirect(
            method = {"tick()V", "m_8119_()V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/npc/pilot/ai/MechAutoController;tick()V"
            ),
            remap = false
    )
    private void dominion$pauseNativeMobController(MechAutoController controller) {
        Pmvc01Entity mech = (Pmvc01Entity) (Object) this;
        Entity driver = mech.getDrivingPassenger();
        if (driver instanceof Mob mob && PomkotsPilotState.belongsTo(mob, mech)) return;
        MechControlBridge bridge = (MechControlBridge) this;
        if (!bridge.dominion$getControlFrame().active()) {
            controller.tick();
        }
    }

    @Inject(
            method = {"remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", "m_142687_(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
            at = @At("HEAD"), remap = false, require = 0
    )
    private void dominion$restorePilotOnDestroy(Entity.RemovalReason reason, CallbackInfo ci) {
        Pmvc01Entity mech = (Pmvc01Entity) (Object) this;
        Entity driver = mech.getDrivingPassenger();
        if (reason.shouldDestroy() && driver instanceof Mob mob && PomkotsPilotState.belongsTo(mob, mech)) {
            PomkotsPilotState.restore(mob);
        }
        if (dominion$trackedPilot != null && dominion$trackedPilot != driver) {
            PomkotsPilotState.restore(dominion$trackedPilot);
        }
        if (reason.shouldDestroy() || dominion$trackedPilot != driver) dominion$trackedPilot = null;
    }
}
