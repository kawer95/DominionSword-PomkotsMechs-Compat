package com.arxyt.dominionsword.pomkotscompat.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Short-lived flat ground-crack ring spawned by the Takao hammer impact. */
public class GroundCrackEffectEntity extends Entity {
    private static final EntityDataAccessor<Float> EFFECT_SCALE =
            SynchedEntityData.defineId(GroundCrackEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final int LIFETIME_TICKS = 16;

    public GroundCrackEffectEntity(EntityType<? extends GroundCrackEffectEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setEffectScale(float scale) {
        this.entityData.set(EFFECT_SCALE, scale);
    }

    public float getEffectScale() {
        return this.entityData.get(EFFECT_SCALE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(EFFECT_SCALE, 1.0F);
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
