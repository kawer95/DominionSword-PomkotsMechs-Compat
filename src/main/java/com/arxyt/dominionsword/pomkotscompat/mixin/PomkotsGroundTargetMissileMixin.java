package com.arxyt.dominionsword.pomkotscompat.mixin;

import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.custom.MissileGenericEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes point-target missiles converge on and detonate at their commanded ground marker. */
@Mixin(MissileGenericEntity.class)
public abstract class PomkotsGroundTargetMissileMixin {
    private static final String DOMINION$GROUND_TARGET = "DominionPomkotsGroundTarget";

    @Shadow(remap = false) protected LivingEntity target;
    @Shadow(remap = false) protected abstract void createExplosion(Vec3 pos);

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"), remap = false, require = 0)
    private void dominion$guideToCommandedGroundPoint(CallbackInfo ci) {
        MissileGenericEntity missile = (MissileGenericEntity) (Object) this;
        if (missile.level().isClientSide || missile.isRemoved() || target == null
                || !target.isAlive() || !target.getPersistentData().getBoolean(DOMINION$GROUND_TARGET)) return;

        Vec3 impactPoint = target.position().add(0.0D, 0.08D, 0.0D);
        Vec3 toTarget = impactPoint.subtract(missile.position());
        Vec3 motion = missile.getDeltaMovement();
        double triggerRadius = Math.max(1.75D, motion.length() * 1.35D);
        if (toTarget.lengthSqr() <= triggerRadius * triggerRadius) {
            missile.setPos(impactPoint);
            createExplosion(impactPoint);
            missile.discard();
            return;
        }

        // Preserve the weapon's launch arc, then replace the native limited-turn terminal
        // guidance. The original guidance stops correcting late in flight and can overshoot
        // a stationary point target even though that point was explicitly selected.
        if (missile.tickCount >= 8 && toTarget.lengthSqr() > 1.0E-6D) {
            double speed = Math.max(0.75D, motion.length());
            missile.setDeltaMovement(toTarget.normalize().scale(speed));
        }
    }
}
