package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.Action;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs native PMVC01 weapon action and ammo state while a Dominion unit is piloting. */
@Mixin(Pmvc01Entity.class)
public abstract class Pmvc01WeaponDebugMixin {
    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"), remap = false, require = 0)
    private void dominion$logWeaponActionStates(CallbackInfo ci) {
        Pmvc01Entity mech = (Pmvc01Entity) (Object) this;
        if (mech.level() == null || mech.level().isClientSide || mech.level().getGameTime() % 10L != 0L) return;
        Entity driver = mech.getDrivingPassenger();
        if (!(driver instanceof Mob mob) || !PomkotsPilotState.belongsTo(mob, mech)) return;

        StringBuilder line = new StringBuilder();
        for (Action action : ((PomkotsVehicleBase) mech).actionController.getAllActions()) {
            if (!(action instanceof ActionWeapon weapon)) continue;
            int slot = weapon.getWeaponItemSlot();
            if (slot < Pmvc01Entity.INV_WEAPON_RIGHT_HAND || slot > Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER) continue;
            Pmvc01Entity.AmmoManager ammo = mech.getAmmoManager(slot);
            line.append(dominion$slotName(slot)).append(':').append(dominion$itemId(mech, slot))
                    .append(" motion=").append(weapon.getMotion().getType().name())
                    .append(" inAction=").append(weapon.isInAction())
                    .append(" cool=").append(action.currentCoolTime)
                    .append(" actionTick=").append(weapon.getCurrentActionTick())
                    .append(" fireStart=").append(weapon.getFireStartTick())
                    .append(" onFire=").append(weapon.isOnFire())
                    .append(" ammo=").append(ammo.getBulletNum()).append('/').append(ammo.getBulletNumPerMagazine())
                    .append('+').append(ammo.getMagazineNum())
                    .append(ammo.isReloading() ? " reloading" : "")
                    .append(" | ");
        }
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DS-POMKOTS-WEAPON] native mech={} slots: {}", mech.getUUID(), line);
    }

    @Unique
    private static String dominion$slotName(int slot) {
        return switch (slot) {
            case Pmvc01Entity.INV_WEAPON_RIGHT_HAND -> "RH";
            case Pmvc01Entity.INV_WEAPON_LEFT_HAND -> "LH";
            case Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER -> "RS";
            case Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER -> "LS";
            default -> "?" + slot;
        };
    }

    @Unique
    private static String dominion$itemId(Pmvc01Entity mech, int slot) {
        var key = BuiltInRegistries.ITEM.getKey(mech.getItem(slot).getItem());
        return key != null && "pomkotsmechs".equals(key.getNamespace()) ? key.getPath() : "";
    }
}
