package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guarantees a five-block blast radius for Dominion-controlled grenades and missiles. */
@Mixin(Explosion.class)
public abstract class PomkotsExplosionRadiusMixin {
    private static final float DOMINION$MINIMUM_EXPLOSION_POWER = 2.5F;

    @Shadow @Final @Mutable private float radius;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/damagesource/DamageSource;"
                    + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZ"
                    + "Lnet/minecraft/world/level/Explosion$BlockInteraction;)V",
            at = @At("RETURN")
    )
    private void dominion$increaseControlledOrdnanceRadius(Level level, Entity source,
                                                            DamageSource damageSource,
                                                            ExplosionDamageCalculator calculator,
                                                            double x, double y, double z, float power,
                                                            boolean fire, Explosion.BlockInteraction interaction,
                                                            CallbackInfo ci) {
        if (source == null) return;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        if (key == null || !"pomkotsmechs".equals(key.getNamespace())) return;
        String id = key.getPath();
        if (!id.contains("grenade") && !id.contains("missile")) return;

        Entity owner = source instanceof Projectile projectile ? projectile.getOwner() : null;
        PomkotsVehicleBase mech = owner instanceof PomkotsVehicleBase vehicle ? vehicle
                : owner != null && owner.getVehicle() instanceof PomkotsVehicleBase vehicle ? vehicle : null;
        if (mech == null) return;
        Entity driver = mech.getDrivingPassenger();
        if (!(driver instanceof Mob mob) || !PomkotsPilotState.belongsTo(mob, mech)) return;
        radius = Math.max(radius, DOMINION$MINIMUM_EXPLOSION_POWER);
    }
}
