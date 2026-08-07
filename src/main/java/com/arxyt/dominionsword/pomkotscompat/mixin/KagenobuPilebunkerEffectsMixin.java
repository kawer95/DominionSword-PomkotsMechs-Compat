package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.particle.PomkotsParticles;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.items.parts.weapons.KagenobuItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds PMV01's pilebunker shockwave visual to the Kagenobu (景信) punch, which already uses the
 * pilebunker motion, impact sound and spark particles.
 */
@Mixin(KagenobuItem.class)
public abstract class KagenobuPilebunkerEffectsMixin {
    @Inject(
            method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
                    + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At("HEAD"), remap = false
    )
    private void dominion$spawnShockwave(ActionWeapon.WeaponMechInterface mechInterface, int tick, boolean isOnFire,
                                         CallbackInfo ci) {
        try {
            Level world = mechInterface.getWorld();
            if (!world.isClientSide || !isOnFire) return;
            Vec3 point = new Vec3(2.25D * mechInterface.isRight(), 1.0D, 8.0D)
                    .yRot((float) Math.toRadians(-mechInterface.getYRot()))
                    .add(mechInterface.position());
            world.addParticle(PomkotsParticles.SHOCKWAVE_RING.get(), point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        } catch (RuntimeException ex) {
            DominionSwordPomkotsCompatMod.LOGGER.warn("[DS-POMKOTS-WEAPON] kagenobu shockwave spawn failed", ex);
        }
    }
}
