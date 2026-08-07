package com.arxyt.dominionsword.pomkotscompat.entity;

/**
 * Derived from EEEAB's Mobs (EEEABsMobs) by EEEAB, licensed under LGPL-3.0
 * (https://github.com/EEEAB/EEEABsMobs). Modified for this addon.
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

/**
 * A ground block that visually cracks open like the realm-warden ground pound: it tilts with a
 * random rotation, jumps up, falls back and disappears, while the original block stays so the
 * ground recovers flat.
 */
public class GroundCrackBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Quaternionf> ROTATION =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> ANIM_V_Y =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION =
            SynchedEntityData.defineId(GroundCrackBlockEntity.class, EntityDataSerializers.INT);
    private static final int MAX_ACTIVE = 600;
    private static final String TAG_BLOCK_STATE = "BlockState";
    private static final String TAG_DURATION = "Duration";
    private static final String TAG_ANIM_VY = "AnimVY";

    public float animY;
    public float prevAnimY;

    public GroundCrackBlockEntity(EntityType<? extends GroundCrackBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setDuration(20);
    }

    public GroundCrackBlockEntity(Level level, double x, double y, double z, BlockState blockState,
                                  int duration, Quaternionf rotation, float vy) {
        this(PomkotsEntities.GROUND_CRACK_BLOCK.get(), level);
        this.setPos(x, y, z);
        this.setBlockState(blockState);
        this.setDuration(duration);
        this.setRotation(rotation);
        this.setAnimVY(vy);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK_STATE, Blocks.DIRT.defaultBlockState());
        this.entityData.define(ROTATION, new Quaternionf());
        this.entityData.define(ANIM_V_Y, 0.6F);
        this.entityData.define(DURATION, 20);
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK_STATE);
    }

    public void setBlockState(BlockState blockState) {
        this.entityData.set(BLOCK_STATE, blockState);
    }

    public Quaternionf getRotation() {
        return this.entityData.get(ROTATION);
    }

    public void setRotation(Quaternionf rotation) {
        this.entityData.set(ROTATION, rotation == null ? new Quaternionf() : rotation);
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
    public void tick() {
        super.tick();
        if (this.tickCount >= MAX_ACTIVE) {
            this.discard();
            return;
        }
        this.prevAnimY = this.animY;
        this.animY += this.getAnimVY();
        if (this.animY < -0.5F) {
            this.discard();
            return;
        }
        this.setAnimVY(this.getAnimVY() - 0.2F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setBlockState(NbtUtils.readBlockState(
                this.level().holderLookup(Registries.BLOCK), tag.getCompound(TAG_BLOCK_STATE)));
        this.setDuration(tag.getInt(TAG_DURATION));
        this.setAnimVY(tag.getFloat(TAG_ANIM_VY));
        this.setRotation(new Quaternionf(
                tag.getFloat("QX"), tag.getFloat("QY"), tag.getFloat("QZ"), tag.getFloat("QW")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put(TAG_BLOCK_STATE, NbtUtils.writeBlockState(this.getBlockState()));
        tag.putInt(TAG_DURATION, this.getDuration());
        tag.putFloat(TAG_ANIM_VY, this.getAnimVY());
        Quaternionf rotation = this.getRotation();
        tag.putFloat("QX", rotation.x);
        tag.putFloat("QY", rotation.y);
        tag.putFloat("QZ", rotation.z);
        tag.putFloat("QW", rotation.w);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
