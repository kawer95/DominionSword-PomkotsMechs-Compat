package com.arxyt.dominionsword.pomkotscompat.entity;

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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** A debris block flung outward and upward by the Takao hammer impact (the block splash). */
public class RisingBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(RisingBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final String TAG_BLOCK_STATE = "BlockState";
    private static final String TAG_LIFE = "Life";
    private static final String TAG_SPIN_X = "SpinX";
    private static final String TAG_SPIN_Y = "SpinY";
    private static final String TAG_SPIN_Z = "SpinZ";
    private static final int DEFAULT_LIFE = 26;
    private static final double GRAVITY = 0.13D;
    private static final double DRAG = 0.96D;

    public float spinX;
    public float spinY;
    public float spinZ;
    private int life = DEFAULT_LIFE;

    public RisingBlockEntity(EntityType<? extends RisingBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RisingBlockEntity(Level level, double x, double y, double z, BlockState blockState, int life, Vec3 velocity) {
        this(PomkotsEntities.RISING_BLOCK.get(), level);
        this.setPos(x, y, z);
        this.setBlockState(blockState);
        this.life = life;
        this.setDeltaMovement(velocity);
        this.spinX = (level.random.nextFloat() - 0.5F) * 24.0F;
        this.spinY = (level.random.nextFloat() - 0.5F) * 36.0F;
        this.spinZ = (level.random.nextFloat() - 0.5F) * 24.0F;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK_STATE, Blocks.STONE.defaultBlockState());
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK_STATE);
    }

    public void setBlockState(BlockState blockState) {
        this.entityData.set(BLOCK_STATE, blockState);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > this.life) {
            this.discard();
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
        this.setDeltaMovement(velocity.x * DRAG, (velocity.y - GRAVITY) * DRAG, velocity.z * DRAG);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setBlockState(NbtUtils.readBlockState(
                this.level().holderLookup(Registries.BLOCK), tag.getCompound(TAG_BLOCK_STATE)));
        this.life = tag.getInt(TAG_LIFE);
        this.spinX = tag.getFloat(TAG_SPIN_X);
        this.spinY = tag.getFloat(TAG_SPIN_Y);
        this.spinZ = tag.getFloat(TAG_SPIN_Z);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put(TAG_BLOCK_STATE, NbtUtils.writeBlockState(this.getBlockState()));
        tag.putInt(TAG_LIFE, this.life);
        tag.putFloat(TAG_SPIN_X, this.spinX);
        tag.putFloat(TAG_SPIN_Y, this.spinY);
        tag.putFloat(TAG_SPIN_Z, this.spinZ);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
