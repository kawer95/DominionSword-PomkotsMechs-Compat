package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.npc.pilot.ai.MechAutoController;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * PMVC01 overrides PomkotsVehicleBase.travel and calls travelBypass directly, so the base
     * vehicle mixin never gets a chance to copy Dominion's control frame to a Mob pilot.
     */
    @Inject(
            method = {"travel(Lnet/minecraft/world/phys/Vec3;)V", "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"},
            at = @At("HEAD"), remap = false, require = 0
    )
    private void dominion$applyUnitPilotInput(Vec3 travelVector, CallbackInfo ci) {
        MechControlFrame frame = ((MechControlBridge) this).dominion$getControlFrame();
        if (frame == null || !frame.active()) return;
        Entity pilot = ((Pmvc01Entity) (Object) this).getDrivingPassenger();
        if (!(pilot instanceof Mob mob)) return;
        mob.zza = frame.forward();
        mob.xxa = frame.strafe();
        mob.setYRot(frame.yaw());
        mob.setYHeadRot(frame.yaw());
        mob.yBodyRot = frame.yaw();
        mob.setXRot(frame.pitch());
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

    /**
     * PMVC01's native aim always adds exactly four ticks of target velocity. Dominion control
     * deliberately uses direct fire at the target's current hitbox center instead.
     */
    @Inject(
            method = "getShootingAngle(Lnet/minecraft/world/entity/Entity;ZZ)[F",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void dominion$aimAutomaticDirectFire(Entity projectile, boolean useTarget, boolean useDeviation,
                                                  CallbackInfoReturnable<float[]> cir) {
        if (!useTarget) return;
        Pmvc01Entity mech = (Pmvc01Entity) (Object) this;
        Entity driver = mech.getDrivingPassenger();
        if (!(driver instanceof Mob mob) || !PomkotsPilotState.belongsTo(mob, mech)) return;
        Entity target = mech.getLockTargets().getLockTargetHard();
        if (target == null || !target.isAlive()) return;

        var key = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (key == null || !"pomkotsmechs".equals(key.getNamespace())) return;
        String projectileId = key.getPath();
        if (!projectileId.startsWith("bulletmachine") && !projectileId.startsWith("bulletgrenade")) return;

        Vec3 origin = projectile.position();
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        cir.setReturnValue(new float[]{pitch, yaw});
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
