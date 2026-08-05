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

/** Removes unsuitable fixed spread and points AI-fired direct weapons at the current hitbox center. */
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
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin);
        if (direction.lengthSqr() > 1.0E-6D) {
            projectile.setDeltaMovement(direction.normalize().scale(velocity));
        }
    }
}
