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

    private PomkotsPilotState() {
    }

    public static void begin(Mob pilot, Entity vehicle) {
        CompoundTag data = pilot.getPersistentData();
        if (data.getBoolean(ACTIVE)) {
            if (data.hasUUID(VEHICLE) && data.getUUID(VEHICLE).equals(vehicle.getUUID())) return;
            restore(pilot);
        }
        data.putBoolean(PREVIOUS_NO_AI, pilot.isNoAi());
        data.putUUID(VEHICLE, vehicle.getUUID());
        data.putBoolean(ACTIVE, true);
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
        pilot.setNoAi(previousNoAi);
        pilot.zza = 0.0F;
        pilot.xxa = 0.0F;
        pilot.getNavigation().stop();
    }
}
