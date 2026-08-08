package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.util.MeleeAabbFix;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.items.parts.weapons.GassanItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Replaces the Gassan's collapsed rotated AABB with the full bounding box of the swing. */
@Mixin(GassanItem.class)
public abstract class GassanMeleeAabbMixin {
    @Redirect(method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
            + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_45933_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    remap = false),
            remap = false)
    private List<Entity> dominion$useCorrectedAabb(Level level, Entity ignored, AABB aabb,
                                                   ActionWeapon.WeaponMechInterface mech, int tick,
                                                   boolean isOnFire) {
        double r = mech.isRight();
        return level.getEntities(ignored, MeleeAabbFix.rotatedBox(mech.position(), mech.getYRot(),
                new double[]{6.5 * r, -2.0 * r}, new double[]{4.0, -4.0}, new double[]{36.0, 28.0}));
    }
}
