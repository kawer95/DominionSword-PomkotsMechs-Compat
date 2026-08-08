package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.util.MeleeAabbFix;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.items.parts.weapons.GassanItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the Gassan's collapsed rotated AABB with the full bounding box of the swing. */
@Mixin(GassanItem.class)
public abstract class GassanMeleeAabbMixin {
    @Unique private static final ThreadLocal<AABB> dominion$correctedAabb = new ThreadLocal<>();

    @Inject(method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
            + "custom/ActionWeapon$WeaponMechInterface;IZ)V", at = @At("HEAD"), remap = false)
    private void dominion$storeCorrectedAabb(ActionWeapon.WeaponMechInterface mech, int tick, boolean isOnFire,
                                             CallbackInfo ci) {
        if (!isOnFire) return;
        double r = mech.isRight();
        dominion$correctedAabb.set(MeleeAabbFix.rotatedBox(mech.position(), mech.getYRot(),
                new double[]{6.5 * r, -2.0 * r}, new double[]{4.0, -4.0}, new double[]{36.0, 28.0}));
    }

    @Redirect(method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
            + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"),
            remap = false)
    private AABB dominion$fullRotatedAabb(Vec3 p1, Vec3 p2) {
        AABB box = dominion$correctedAabb.get();
        return box != null ? box : new AABB(p1, p2);
    }
}
