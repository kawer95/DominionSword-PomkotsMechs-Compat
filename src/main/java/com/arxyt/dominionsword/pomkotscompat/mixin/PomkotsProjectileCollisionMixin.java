package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.custom.PomkotsCustomThrowableProjectile;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/** Accounts for the actual projectile width in Dominion-controlled direct-fire collision sweeps. */
@Mixin(PomkotsCustomThrowableProjectile.class)
public abstract class PomkotsProjectileCollisionMixin {
    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)"
                    + "Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void dominion$useProjectileWidthForDirectFire(Level level, Entity projectileEntity,
                                                           Vec3 start, Vec3 end, AABB searchBox,
                                                           Predicate<Entity> filter,
                                                           CallbackInfoReturnable<EntityHitResult> cir) {
        if (!(projectileEntity instanceof Projectile projectile)) return;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (key == null || !"pomkotsmechs".equals(key.getNamespace())) return;
        String id = key.getPath();
        if (!id.startsWith("bulletmachine") && !id.startsWith("bulletgrenade")) return;

        Entity owner = projectile.getOwner();
        Pmvc01Entity mech = owner instanceof Pmvc01Entity custom ? custom
                : owner != null && owner.getVehicle() instanceof Pmvc01Entity custom ? custom : null;
        if (mech == null || !(mech.getDrivingPassenger() instanceof Mob mob)
                || !PomkotsPilotState.belongsTo(mob, mech)) return;

        double projectileRadius = Math.max(0.25D, projectile.getBbWidth() * 0.5D);
        Entity closest = null;
        Vec3 closestHit = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities(projectile, searchBox.inflate(projectileRadius), filter)) {
            AABB candidateBox = candidate.getBoundingBox()
                    .inflate(candidate.getPickRadius() + projectileRadius);
            Optional<Vec3> hit = candidateBox.clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
                closestHit = hit.get();
            }
        }
        cir.setReturnValue(closest == null ? null : new EntityHitResult(closest, closestHit));
    }
}
