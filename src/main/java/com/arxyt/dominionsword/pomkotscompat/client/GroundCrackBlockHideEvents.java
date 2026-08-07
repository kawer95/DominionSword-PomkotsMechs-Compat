package com.arxyt.dominionsword.pomkotscompat.client;

import com.arxyt.dominionsword.pomkotscompat.entity.GroundCrackBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Keeps the client-side hidden-block set in sync with the lifetime of crack entities. */
public final class GroundCrackBlockHideEvents {
    private GroundCrackBlockHideEvents() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof GroundCrackBlockEntity) {
            GroundCrackBlockHider.add(entity.blockPosition().below());
        }
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof GroundCrackBlockEntity) {
            GroundCrackBlockHider.remove(entity.blockPosition().below());
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            GroundCrackBlockHider.clearAll();
        }
    }
}
