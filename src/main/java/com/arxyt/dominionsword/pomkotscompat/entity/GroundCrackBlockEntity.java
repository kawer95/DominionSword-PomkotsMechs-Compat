package com.arxyt.dominionsword.pomkotscompat.entity;

/**
 * Direct port of EEEABsMobs' EntityFallingBlock SIMULATE_RUPTURE mode
 * (https://github.com/EEEAB/EEEABsMobs), LGPL-3.0, by EEEAB. Modified only in package name
 * and entity registration. The world is never modified: the original block stays and the
 * entity renders a tilted copy on top, then disappears.
 */
import com.arxyt.dominionsword.pomkotscompat.registry.PomkotsEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaternionf;

public class GroundCrackBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Quaternionf> QUATERNION =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> ANIM_V_Y =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.INT);
    public static int MAX_ACTIVE = 600;
    public float animY = 0;
    public float prevAnimY = 0;

    public GroundCrackBlockEntity(EntityType<? extends GroundCrackBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.setDuration(20);
    }

    public GroundCrackBlockEntity(Level level, double px, double py, double pz, BlockState blockState,
                                  Quaternionf quaternionf, int duration, float vy) {
        super(PomkotsEntities.GROUND_CRACK_BLOCK.get(), level);
        this.setBlockState(blockState);
        this.setQuaternionf(quaternionf);
        this.setDuration(duration);
        this.setAnimVY(vy);
        this.setPos(px, py, pz);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK_STATE, Blocks.DIRT.defaultBlockState());
        this.entityData.define(QUATERNION, new Quaternionf());
        this.entityData.define(ANIM_V_Y, 1.0F);
        this.entityData.define(DURATION, 20);
    }

    @Override
    public void tick() {
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        super.tick();
        if (this.tickCount >= MAX_ACTIVE) {
            this.discard();
            return;
        }

        float animVY = this.getAnimVY();
        if (animVY < 0.0F && this.tickCount <= this.getDuration()) {
            // Keep the cracked block in place while the ground is split, avoiding interpolation jitter.
            this.prevAnimY = this.animY;
            return;
        }
        this.prevAnimY = this.animY;
        this.animY += animVY;
        if (this.animY < -0.5F) {
            this.discard();
            return;
        }
        this.setAnimVY(animVY - 0.2F);
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK_STATE);
    }

    public void setBlockState(BlockState blockState) {
        this.entityData.set(BLOCK_STATE, blockState);
    }

    public Quaternionf getQuaternionf() {
        return this.entityData.get(QUATERNION);
    }

    public void setQuaternionf(Quaternionf quaternionf) {
        this.entityData.set(QUATERNION, quaternionf == null ? new Quaternionf() : quaternionf);
    }

    public float getAnimVY() {
        return this.entityData.get(ANIM_V_Y);
    }

    public void setAnimVY(float vy) {
        this.entityData.set(ANIM_V_Y, vy);
    }

    public int getDuration() {
        return this.entityData.get(DURATION);
    }

    public void setDuration(int duration) {
        this.entityData.set(DURATION, duration);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        this.setBlockState(NbtUtils.readBlockState(
                this.level().holderLookup(Registries.BLOCK), compoundTag.getCompound("block_state")));
        this.setDuration(compoundTag.getInt("duration"));
        this.setAnimVY(compoundTag.getFloat("vy"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        compoundTag.put("block_state", NbtUtils.writeBlockState(this.getBlockState()));
        compoundTag.putInt("duration", this.getDuration());
        compoundTag.putFloat("vy", this.getAnimVY());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
