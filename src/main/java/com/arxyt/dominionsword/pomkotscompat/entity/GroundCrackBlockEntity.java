package com.arxyt.dominionsword.pomkotscompat.entity;

/**
 * Ground-crack block visual derived from EEEABsMobs' EntityFallingBlock SIMULATE_RUPTURE mode
 * (https://github.com/EEEAB/EEEABsMobs), LGPL-3.0, by EEEAB.
 *
 * The world is never modified: the entity renders a tilted copy over the untouched ground
 * that cracks open, holds, then straightens back (摆正) into the ground before disappearing.
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
import net.minecraft.util.Mth;
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
    /** Ticks spent tilting up out of the ground. */
    public static final int RISE_TICKS = 6;
    /** Ticks spent straightening back into the ground. */
    public static final int RECOVER_TICKS = 12;
    /** Highest point (in blocks) the cracked block rises to. */
    public static final float MAX_RISE = 0.34F;

    public float animY = 0;
    public float prevAnimY = 0;
    /** Full tilt rotation, captured once from the synced data so both sides animate identically. */
    private Quaternionf targetRotation;

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
        super.tick();
        if (this.targetRotation == null) {
            this.targetRotation = new Quaternionf(this.getQuaternionf());
        }
        if (this.tickCount >= MAX_ACTIVE) {
            this.discard();
            return;
        }
        this.prevAnimY = this.animY;
        this.animY = this.getAnimYAt(this.tickCount);
        if (this.tickCount >= this.totalTicks()) {
            this.discard();
        }
    }

    /** Total animation length in ticks: rise + hold + recover. */
    public int totalTicks() {
        return RISE_TICKS + Math.max(1, this.getDuration()) + RECOVER_TICKS;
    }

    /** Per-block rise variation, derived from the synced initial bounce so both sides agree. */
    public float getRiseScale() {
        return Mth.clamp(0.85F + this.getAnimVY() * 0.75F, 0.75F, 1.1F);
    }

    /** Vertical offset (blocks) of the cracked block at continuous time t (tick + partial tick). */
    public float getAnimYAt(float t) {
        float rise = MAX_RISE * this.getRiseScale();
        if (t < RISE_TICKS) {
            return rise * easeInOut(clamp01(t / RISE_TICKS));
        }
        if (t < RISE_TICKS + this.getDuration()) {
            return rise;
        }
        float p = clamp01((t - RISE_TICKS - this.getDuration()) / RECOVER_TICKS);
        return rise * (1.0F - easeInOut(p));
    }

    /** Current tilt rotation at continuous time t. Straightens back to flat during recovery. */
    public Quaternionf getQuaternionAt(float t) {
        Quaternionf target = this.targetRotation != null ? this.targetRotation : this.getQuaternionf();
        if (t < RISE_TICKS) {
            float p = easeInOut(clamp01(t / RISE_TICKS));
            return new Quaternionf().slerp(target, p);
        }
        if (t < RISE_TICKS + this.getDuration()) {
            return new Quaternionf(target);
        }
        float p = easeInOut(clamp01((t - RISE_TICKS - this.getDuration()) / RECOVER_TICKS));
        return new Quaternionf(target).slerp(new Quaternionf(), p);
    }

    private static float easeInOut(float p) {
        return p * p * (3.0F - 2.0F * p);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
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
