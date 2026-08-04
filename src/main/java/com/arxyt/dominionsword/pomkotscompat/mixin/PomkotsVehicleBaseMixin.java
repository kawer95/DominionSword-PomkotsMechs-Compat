package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PomkotsVehicleBase.class)
public abstract class PomkotsVehicleBaseMixin implements MechControlBridge {
    @Unique private MechControlFrame dominion$controlFrame = MechControlFrame.INACTIVE;

    @Override
    public void dominion$setControlFrame(MechControlFrame frame) {
        dominion$controlFrame = frame == null ? MechControlFrame.INACTIVE : frame;
    }

    @Override
    public MechControlFrame dominion$getControlFrame() {
        return dominion$controlFrame;
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void dominion$applyUnitPilotInput(Vec3 travelVector, CallbackInfo ci) {
        MechControlFrame frame = dominion$controlFrame;
        if (frame == null || !frame.active()) return;
        LivingEntity pilot = ((PomkotsVehicleBase) (Object) this).getDrivingPassenger();
        if (pilot == null || pilot instanceof net.minecraft.world.entity.player.Player) return;
        pilot.zza = frame.forward();
        pilot.xxa = frame.strafe();
        pilot.setYRot(frame.yaw());
        pilot.setYHeadRot(frame.yaw());
        pilot.yBodyRot = frame.yaw();
        pilot.setXRot(frame.pitch());
    }
}
