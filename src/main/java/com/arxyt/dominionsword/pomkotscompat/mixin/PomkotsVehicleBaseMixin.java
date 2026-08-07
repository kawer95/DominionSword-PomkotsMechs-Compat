package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.client.input.DriverInput;
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
    @Unique private boolean dominion$hasQueuedDriverInput;
    @Unique private short dominion$queuedDriverInput;
    @Unique private short dominion$lastAppliedDriverInput;

    @Override
    public void dominion$setControlFrame(MechControlFrame frame) {
        MechControlFrame next = frame == null ? MechControlFrame.INACTIVE : frame;
        MechControlFrame prev = dominion$controlFrame;
        dominion$controlFrame = next;
        PomkotsVehicleBase mech = (PomkotsVehicleBase) (Object) this;
        if (mech.level() != null && !mech.level().isClientSide
                && (prev == null || prev.forward() != next.forward() || prev.strafe() != next.strafe())) {
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-MOVE] frame change mech={} forward {} -> {} strafe {} -> {} active {} -> {} gt={}",
                    mech.getUUID(), prev == null ? -1 : prev.forward(), next.forward(),
                    prev == null ? -1 : prev.strafe(), next.strafe(),
                    prev == null ? false : prev.active(), next.active(),
                    mech.level().getGameTime());
        }
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
        PomkotsVehicleBase mech = (PomkotsVehicleBase) (Object) this;
        DriverInput nativeBefore = mech.getDriverInput();
        short prevApplied = dominion$lastAppliedDriverInput;
        dominion$lastAppliedDriverInput = dominion$queuedDriverInput;
        mech.setDriverInput(new DriverInput(dominion$queuedDriverInput, mech.getDriverInput()));
        if (mech.level() != null && !mech.level().isClientSide) {
            long gt = mech.level().getGameTime();
            if (gt % 10L == 0L || dominion$queuedDriverInput != prevApplied) {
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-INPUT] mech={} queued={} prevApplied={} nativeBefore={} gt={}",
                        mech.getUUID(), dominion$queuedDriverInput, prevApplied,
                        nativeBefore == null ? -1 : nativeBefore.getStatus(), gt);
            }
        }
        if (mech.level() != null && !mech.level().isClientSide && mech.level().getGameTime() % 40L == 0L) {
            LivingEntity driver = mech.getDrivingPassenger();
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-INPUT] pilot mech={} driver={} frame={}",
                    mech.getUUID(),
                    driver == null ? "none" : driver.getType().toString(),
                    dominion$controlFrame);
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
        LivingEntity pilot = ((PomkotsVehicleBase) (Object) this).getDrivingPassenger();
        if (pilot == null || pilot instanceof net.minecraft.world.entity.player.Player) return;
        if (frame == null || !frame.active()) {
            if ((pilot.zza != 0.0F || pilot.xxa != 0.0F)
                    && ((PomkotsVehicleBase) (Object) this).level().getGameTime() % 10L == 0L) {
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-MOVE] leftover pilot input mech={} zza={} xxa={} frame=INACTIVE gt={}",
                        ((PomkotsVehicleBase) (Object) this).getUUID(), pilot.zza, pilot.xxa,
                        ((PomkotsVehicleBase) (Object) this).level().getGameTime());
            }
            return;
        }
        if (((PomkotsVehicleBase) (Object) this).level().getGameTime() % 10L == 0L
                && (frame.forward() != 0.0F || frame.strafe() != 0.0F)) {
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-MOVE] frame move mech={} forward={} strafe={} yaw={} pos=({},{}) gt={}",
                    ((PomkotsVehicleBase) (Object) this).getUUID(), frame.forward(), frame.strafe(), frame.yaw(),
                    String.format(java.util.Locale.ROOT, "%.1f", ((PomkotsVehicleBase) (Object) this).getX()),
                    String.format(java.util.Locale.ROOT, "%.1f", ((PomkotsVehicleBase) (Object) this).getZ()),
                    ((PomkotsVehicleBase) (Object) this).level().getGameTime());
        }
        pilot.zza = frame.forward();
        pilot.xxa = frame.strafe();
        pilot.setYRot(frame.yaw());
        pilot.setYHeadRot(frame.yaw());
        pilot.yBodyRot = frame.yaw();
        pilot.setXRot(frame.pitch());
    }
}
