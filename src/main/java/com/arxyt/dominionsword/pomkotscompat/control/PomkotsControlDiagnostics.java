package com.arxyt.dominionsword.pomkotscompat.control;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rate-limited, anomaly-only control diagnostics retained for field reports. */
public final class PomkotsControlDiagnostics {
    private static final long MIN_INTERVAL_TICKS = 100L;
    private static final Map<Key, Long> LAST_LOG_TICKS = new ConcurrentHashMap<>();

    private PomkotsControlDiagnostics() {
    }

    public static void warn(PomkotsVehicleBase mech, String reason, String state) {
        if (mech == null || mech.level().isClientSide) return;
        long now = mech.level().getGameTime();
        Key key = new Key(mech.getUUID(), reason);
        Long previous = LAST_LOG_TICKS.putIfAbsent(key, now);
        if (previous != null) {
            if (now - previous < MIN_INTERVAL_TICKS || !LAST_LOG_TICKS.replace(key, previous, now)) return;
        }

        MechControlBridge bridge = (MechControlBridge) mech;
        Entity pilot = mech.getDrivingPassenger();
        if (pilot == null) {
            for (Entity passenger : mech.getPassengers()) {
                if (passenger instanceof Mob) {
                    pilot = passenger;
                    break;
                }
            }
        }
        boolean bound = pilot instanceof Mob mob && PomkotsPilotState.belongsTo(mob, mech);
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(mech.getType());
        short current = mech.getDriverInput() == null ? 0 : mech.getDriverInput().getStatus();
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-CONTROL] reason={} mech={} type={} pos={} velocity={} frame={} "
                        + "driverInput={} lastInput={} queuedInput={} hasQueued={} pilot={} bound={} state={}",
                reason, mech.getUUID(), type, mech.position(), mech.getDeltaMovement(),
                bridge.dominion$getControlFrame(), current, bridge.dominion$getLastAppliedDriverInput(),
                bridge.dominion$getQueuedDriverInput(), bridge.dominion$hasQueuedDriverInput(),
                pilot == null ? null : pilot.getUUID(), bound, state);
    }

    public static void clear(UUID mechId) {
        LAST_LOG_TICKS.keySet().removeIf(key -> key.mechId.equals(mechId));
    }

    public static void clearAll() {
        LAST_LOG_TICKS.clear();
    }

    private record Key(UUID mechId, String reason) {
    }
}
