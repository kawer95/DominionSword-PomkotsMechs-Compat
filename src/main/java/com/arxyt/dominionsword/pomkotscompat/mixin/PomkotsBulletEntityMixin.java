package com.arxyt.dominionsword.pomkotscompat.mixin;

import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.BulletEntity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.PomkotsThrowableProjectile;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs alpha.8's unchecked second bullet movement and post-damage invulnerability reset. */
@Mixin(BulletEntity.class)
public abstract class PomkotsBulletEntityMixin extends PomkotsThrowableProjectile {
    @Shadow private int lifeTicks;

    protected PomkotsBulletEntityMixin(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.projectile.ThrowableProjectile> type,
                                       net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), remap = false)
    private void dominion$scanFullIntendedBulletStep(CallbackInfo ci) {
        LivingEntity source = getShooter();
        if (getOwner() == null && source != null) {
            Entity owner = source instanceof PomkotsVehicleBase mech && mech.getDrivingPassenger() != null
                    ? mech.getDrivingPassenger() : source;
            setOwner(owner);
        }
        setDeltaMovement(getDeltaMovement().scale(2.0D));
    }

    @Inject(method = {"tick()V", "m_8119_()V"}, at = {
            @At(value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/projectile/PomkotsThrowableProjectile;tick()V",
                    shift = At.Shift.AFTER, remap = false),
            @At(value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/projectile/PomkotsThrowableProjectile;m_8119_()V",
                    shift = At.Shift.AFTER, remap = false)
    }, cancellable = true, remap = false)
    private void dominion$removeUncheckedSecondMove(CallbackInfo ci) {
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.scale(0.5D));
        if (lifeTicks++ >= 30) discard();
        ci.cancel();
    }

    @Inject(method = {"onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V"}, at = @At("HEAD"), remap = false)
    private void dominion$clearInvulnerabilityBeforeDamage(EntityHitResult hit, CallbackInfo ci) {
        hit.getEntity().invulnerableTime = 0;
    }
}
