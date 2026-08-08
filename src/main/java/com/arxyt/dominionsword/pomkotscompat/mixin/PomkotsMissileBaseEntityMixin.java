package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.MissileBaseEntity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives legacy missiles a real owner and the commanded hard-lock target before homing runs. */
@Mixin(MissileBaseEntity.class)
public abstract class PomkotsMissileBaseEntityMixin {
    @Shadow protected LivingEntity shooter;
    @Shadow protected LivingEntity target;

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), remap = false)
    private void dominion$bindCommandedMissileTarget(CallbackInfo ci) {
        MissileBaseEntity missile = (MissileBaseEntity)(Object)this;
        if (!(shooter instanceof PomkotsVehicleBase mech)
                || !(mech.getDrivingPassenger() instanceof Mob pilot)
                || !PomkotsPilotState.belongsTo(pilot, mech)) return;
        if (missile.getOwner() == null && shooter != null) {
            Entity owner = mech.getDrivingPassenger() != null ? mech.getDrivingPassenger() : shooter;
            missile.setOwner(owner);
        }
        if (!missile.level().isClientSide) {
            Entity locked = mech.getLockTargets().getLockTargetHard();
            if (locked instanceof LivingEntity living && living.isAlive()) target = living;
        }
    }
}
