package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.debug.PomkotsAimDebug;
import grcmcs.minecraft.mods.pomkotsmechs.entity.projectile.custom.PomkotsCustomThrowableProjectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}
