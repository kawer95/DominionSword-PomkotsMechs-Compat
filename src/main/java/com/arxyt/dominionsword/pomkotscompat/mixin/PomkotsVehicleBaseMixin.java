package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.client.input.DriverInput;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PomkotsVehicleBase.class)
public abstract class PomkotsVehicleBaseMixin implements MechControlBridge {
    @Unique private MechControlFrame dominion$controlFrame = MechControlFrame.INACTIVE;
    @Unique private boolean dominion$hasQueuedDriverInput;
    @Unique private short dominion$queuedDriverInput;
    @Unique private short dominion$lastAppliedDriverInput;

    @Override
    public void dominion$setControlFrame(MechControlFrame frame) {
        dominion$controlFrame = frame == null ? MechControlFrame.INACTIVE : frame;
    }

    @Override
    public MechControlFrame dominion$getControlFrame() {
        return dominion$controlFrame;
    }

    @Override
    public void dominion$queueDriverInput(short bits) {
        dominion$queuedDriverInput = bits;
        dominion$hasQueuedDriverInput = true;
    }

    @Override
    public short dominion$getLastAppliedDriverInput() {
        return dominion$lastAppliedDriverInput;
    }

    /**
     * PMVC runs its built-in Mob auto controller before calling super.tick(), which can replace
     * the input submitted by Dominion Sword at the end of the preceding server tick. Apply our
     * queued frame here, after that controller and immediately before Pomkots consumes driverInput.
     */
    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"), remap = false)
    private void dominion$applyQueuedDriverInput(CallbackInfo ci) {
        if (!dominion$hasQueuedDriverInput) return;
        dominion$hasQueuedDriverInput = false;
        dominion$lastAppliedDriverInput = dominion$queuedDriverInput;
        PomkotsVehicleBase mech = (PomkotsVehicleBase) (Object) this;
        mech.setDriverInput(new DriverInput(dominion$queuedDriverInput, mech.getDriverInput()));
        if (mech.level() != null && !mech.level().isClientSide && mech.level().getGameTime() % 10L == 0L) {
            LivingEntity driver = mech.getDrivingPassenger();
            boolean dominionPilot = driver instanceof Mob mob && PomkotsPilotState.belongsTo(mob, mech);
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] input mech={} bits={} last={} driver={} dominionPilot={}",
                    mech.getUUID(), dominion$queuedDriverInput, dominion$lastAppliedDriverInput,
                    driver == null ? "none" : driver.getType().toString(), dominionPilot);
        }
    }

    @Inject(
            method = {
                    "travel(Lnet/minecraft/world/phys/Vec3;)V",
                    "m_7023_(Lnet/minecraft/world/phys/Vec3;)V"
            },
            at = @At("HEAD"),
            remap = false
    )
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
