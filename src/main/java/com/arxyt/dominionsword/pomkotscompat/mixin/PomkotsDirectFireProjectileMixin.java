package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes unsuitable fixed spread and applies collision-box lead to AI-fired direct weapons. */
@Mixin(Projectile.class)
public abstract class PomkotsDirectFireProjectileMixin {
    @Inject(method = "shootFromRotation", at = @At("TAIL"))
    private void dominion$correctAutomaticDirectFire(Entity firingEntity, float pitch, float yaw, float roll,
                                                      float velocity, float inaccuracy, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (key == null || !"pomkotsmechs".equals(key.getNamespace())) return;
        String projectileId = key.getPath();
        boolean machineGun = projectileId.startsWith("bulletmachine") && inaccuracy >= 1.5F;
        boolean directGrenade = projectileId.startsWith("bulletgrenade");
        if (!machineGun && !directGrenade) return;

        Entity owner = projectile.getOwner();
        Pmvc01Entity mech = owner instanceof Pmvc01Entity custom ? custom
                : owner != null && owner.getVehicle() instanceof Pmvc01Entity custom ? custom : null;
        if (mech == null) return;
        Entity driver = mech.getDrivingPassenger();
        if (!(driver instanceof Mob mob) || !PomkotsPilotState.belongsTo(mob, mech)) return;
        Entity target = mech.getLockTargets().getLockTargetHard();
        if (target == null || !target.isAlive()) return;

        Vec3 origin = projectile.position();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 targetVelocity = target.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        double horizontalSpeed = Math.sqrt(targetVelocity.x * targetVelocity.x + targetVelocity.z * targetVelocity.z);
        if (horizontalSpeed > 1.25D) targetVelocity = targetVelocity.scale(1.25D / horizontalSpeed);
        double flightTicks = dominion$interceptTime(targetCenter.subtract(origin), targetVelocity,
                Math.max(0.1D, velocity));
        Vec3 direction = targetCenter.add(targetVelocity.scale(flightTicks)).subtract(origin);
        if (direction.lengthSqr() > 1.0E-6D) {
            projectile.setDeltaMovement(direction.normalize().scale(velocity));
        }
    }

    private static double dominion$interceptTime(Vec3 relative, Vec3 targetVelocity, double projectileSpeed) {
        double a = targetVelocity.lengthSqr() - projectileSpeed * projectileSpeed;
        double b = 2.0D * relative.dot(targetVelocity);
        double c = relative.lengthSqr();
        double time = -1.0D;
        if (Math.abs(a) < 1.0E-7D) {
            if (Math.abs(b) > 1.0E-7D) time = -c / b;
        } else {
            double discriminant = b * b - 4.0D * a * c;
            if (discriminant >= 0.0D) {
                double root = Math.sqrt(discriminant);
                double first = (-b - root) / (2.0D * a);
                double second = (-b + root) / (2.0D * a);
                if (first > 0.0D) time = first;
                if (second > 0.0D && (time < 0.0D || second < time)) time = second;
            }
        }
        if (!(time > 0.0D) || !Double.isFinite(time)) time = Math.sqrt(c) / projectileSpeed;
        return Math.min(time, 20.0D);
    }
}
