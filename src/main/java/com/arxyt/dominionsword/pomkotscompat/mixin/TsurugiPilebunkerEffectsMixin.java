package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.particle.PomkotsParticles;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.items.parts.weapons.TsurugiItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ports PMV01's pilebunker sound and shockwave visual onto PMVC01's saber swing.
 */
@Mixin(TsurugiItem.class)
public abstract class TsurugiPilebunkerEffectsMixin {
    @Redirect(
            method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
                    + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/custom/"
                            + "ActionWeapon$WeaponMechInterface;playSoundEffect(Lnet/minecraft/sounds/SoundEvent;)V"
            ),
            remap = false
    )
    private void dominion$pilebunkerSound(ActionWeapon.WeaponMechInterface mechInterface, SoundEvent original) {
        SoundEvent pilebunker = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("pomkotsmechs", "se_pilebunker"));
        if (pilebunker != null) {
            mechInterface.playSoundEffect(pilebunker);
        } else {
            mechInterface.playSoundEffect(original);
        }
    }

    @Inject(
            method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
                    + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At("HEAD"), remap = false
    )
    private void dominion$spawnShockwave(ActionWeapon.WeaponMechInterface mechInterface, int tick, boolean isOnFire,
                                         CallbackInfo ci) {
        Level world = mechInterface.getWorld();
        if (!world.isClientSide || !isOnFire) return;
        Vec3 point = new Vec3(2.25D, 1.0D, 8.0D)
                .yRot((float) Math.toRadians(-mechInterface.getYRot()))
                .add(mechInterface.position());
        world.addParticle(PomkotsParticles.SHOCKWAVE_RING.get(), point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        DominionSwordPomkotsCompatMod.LOGGER.debug("[DS-POMKOTS-WEAPON] pilebunker shockwave at {}", point);
    }
}
