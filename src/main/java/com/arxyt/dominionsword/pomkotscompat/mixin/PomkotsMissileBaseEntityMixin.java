package com.arxyt.dominionsword.pomkotscompat.mixin;

import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.MissileBaseEntity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
        if (missile.getOwner() == null && shooter != null) {
            Entity owner = shooter instanceof PomkotsVehicleBase mech && mech.getDrivingPassenger() != null
                    ? mech.getDrivingPassenger() : shooter;
            missile.setOwner(owner);
        }
        if (!missile.level().isClientSide && shooter instanceof PomkotsVehicleBase mech) {
            Entity locked = mech.getLockTargets().getLockTargetHard();
            if (locked instanceof LivingEntity living && living.isAlive()) target = living;
        }
    }
}
