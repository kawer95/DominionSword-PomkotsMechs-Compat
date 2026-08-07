package com.arxyt.dominionsword.pomkotscompat.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Short-lived visual entity for the PMV01-style pilebunker blade-ring effect. Rendered on the
 * client; the server only tracks it briefly so every nearby player sees the same flash.
 */
public class PilebunkerEffectEntity extends Entity {
    private static final int LIFETIME_TICKS = 14;

    public PilebunkerEffectEntity(EntityType<? extends PilebunkerEffectEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount >= LIFETIME_TICKS) {
            this.discard();
        }
    }
}
