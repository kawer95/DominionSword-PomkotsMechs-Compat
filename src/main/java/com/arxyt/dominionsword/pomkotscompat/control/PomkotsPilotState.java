package com.arxyt.dominionsword.pomkotscompat.control;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/** Tracks the AI state that Pomkots Mechs overwrites while a Dominion unit is piloting. */
public final class PomkotsPilotState {
    private static final String ACTIVE = "DominionPomkotsPilot";
    private static final String VEHICLE = "DominionPomkotsPilotVehicle";
    private static final String PREVIOUS_NO_AI = "DominionPomkotsPilotPreviousNoAi";
    private static final String SNAPSHOT_TRUSTED = "DominionPomkotsPilotSnapshotTrusted";
    private static final String PENDING = "DominionPomkotsPendingMount";
    private static final String PENDING_VEHICLE = "DominionPomkotsPendingVehicle";
    private static final String PENDING_NO_AI = "DominionPomkotsPendingNoAi";

    private PomkotsPilotState() {
    }

    /** Records the real state before Pomkots' first vehicle tick forces Mob drivers to NoAI. */
    public static void recordBeforeMount(Mob pilot, Entity vehicle) {
        CompoundTag data = pilot.getPersistentData();
        if (belongsTo(pilot, vehicle)) return;
        data.putBoolean(PENDING_NO_AI, pilot.isNoAi());
        data.putUUID(PENDING_VEHICLE, vehicle.getUUID());
        data.putBoolean(PENDING, true);
    }

    /** Starts a Dominion-controlled boarding operation while the pilot is still outside. */
    public static void beginBeforeMount(Mob pilot, Entity vehicle) {
        CompoundTag data = pilot.getPersistentData();
        if (data.getBoolean(ACTIVE)) {
            if (data.hasUUID(VEHICLE) && data.getUUID(VEHICLE).equals(vehicle.getUUID())) return;
            restore(pilot);
        }
        bind(data, vehicle, pilot.isNoAi());
    }

    /**
     * Attaches Dominion control to a pilot that is already mounted. New builds consume the
     * pre-mount snapshot. Old active records had no trusted marker and may have captured
     * Pomkots' forced NoAI=true, so migrate those records to the usable AI-enabled default.
     */
    public static void attachMounted(Mob pilot, Entity vehicle) {
        CompoundTag data = pilot.getPersistentData();
        if (belongsTo(pilot, vehicle)) {
            if (!data.getBoolean(SNAPSHOT_TRUSTED)) {
                data.putBoolean(PREVIOUS_NO_AI, pendingMatches(data, vehicle)
                        ? data.getBoolean(PENDING_NO_AI) : false);
                data.putBoolean(SNAPSHOT_TRUSTED, true);
                clearPending(data);
            }
            return;
        }
        if (data.getBoolean(ACTIVE)) restore(pilot);
        boolean previousNoAi = pendingMatches(data, vehicle) && data.getBoolean(PENDING_NO_AI);
        bind(data, vehicle, previousNoAi);
    }

    private static void bind(CompoundTag data, Entity vehicle, boolean previousNoAi) {
        data.putBoolean(PREVIOUS_NO_AI, previousNoAi);
        data.putUUID(VEHICLE, vehicle.getUUID());
        data.putBoolean(ACTIVE, true);
        data.putBoolean(SNAPSHOT_TRUSTED, true);
        clearPending(data);
    }

    public static boolean belongsTo(Mob pilot, Entity vehicle) {
        CompoundTag data = pilot.getPersistentData();
        return data.getBoolean(ACTIVE)
                && data.hasUUID(VEHICLE)
                && data.getUUID(VEHICLE).equals(vehicle.getUUID());
    }

    public static void restore(Mob pilot) {
        CompoundTag data = pilot.getPersistentData();
        if (!data.getBoolean(ACTIVE)) return;
        boolean previousNoAi = data.getBoolean(PREVIOUS_NO_AI);
        data.remove(ACTIVE);
        data.remove(VEHICLE);
        data.remove(PREVIOUS_NO_AI);
        data.remove(SNAPSHOT_TRUSTED);
        clearPending(data);
        pilot.setNoAi(previousNoAi);
        pilot.zza = 0.0F;
        pilot.xxa = 0.0F;
        pilot.getNavigation().stop();
    }

    /** Drops an unused native-mount snapshot without touching an active Dominion binding. */
    public static void discardPending(Mob pilot) {
        CompoundTag data = pilot.getPersistentData();
        if (!data.getBoolean(ACTIVE)) clearPending(data);
    }

    private static boolean pendingMatches(CompoundTag data, Entity vehicle) {
        return data.getBoolean(PENDING) && data.hasUUID(PENDING_VEHICLE)
                && data.getUUID(PENDING_VEHICLE).equals(vehicle.getUUID());
    }

    private static void clearPending(CompoundTag data) {
        data.remove(PENDING);
        data.remove(PENDING_VEHICLE);
        data.remove(PENDING_NO_AI);
    }
}
