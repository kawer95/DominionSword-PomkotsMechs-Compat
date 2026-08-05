package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.debug.PomkotsAimDebug;
import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.custom.PomkotsCustomThrowableProjectile;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/** Records the actual server trajectory and collision result of sampled direct-fire rounds. */
@Mixin(PomkotsCustomThrowableProjectile.class)
public abstract class PomkotsDirectFireTraceMixin {
    @Unique private Vec3 dominion$traceTickStart;

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), remap = false, require = 0)
    private void dominion$captureTraceStart(CallbackInfo ci) {
        PomkotsCustomThrowableProjectile projectile = (PomkotsCustomThrowableProjectile) (Object) this;
        if (PomkotsAimDebug.tracing(projectile)) dominion$traceTickStart = projectile.position();
    }

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"), remap = false, require = 0)
    private void dominion$logTraceStep(CallbackInfo ci) {
        PomkotsCustomThrowableProjectile projectile = (PomkotsCustomThrowableProjectile) (Object) this;
        if (dominion$traceTickStart != null) {
            PomkotsAimDebug.tick(projectile, dominion$traceTickStart);
            dominion$traceTickStart = null;
        }
    }

    @Inject(method = {"onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V"},
            at = @At("HEAD"), remap = false, require = 0)
    private void dominion$logEntityHit(EntityHitResult hit, CallbackInfo ci) {
        PomkotsAimDebug.hitEntity((PomkotsCustomThrowableProjectile) (Object) this, hit.getEntity());
    }

    @Inject(method = {"onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V",
            "m_8060_(Lnet/minecraft/world/phys/BlockHitResult;)V"},
            at = @At("HEAD"), remap = false, require = 0)
    private void dominion$logBlockHit(BlockHitResult hit, CallbackInfo ci) {
        PomkotsAimDebug.hitBlock((PomkotsCustomThrowableProjectile) (Object) this, hit.getLocation());
    }

    /**
     * Alpha.8 ray-tests the target without accounting for the projectile's own two-block width.
     * Use the real projectile half-width for Dominion-controlled machine-gun and grenade sweeps.
     */
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
