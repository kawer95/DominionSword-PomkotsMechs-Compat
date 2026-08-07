package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.entity.GroundCrackEffectEntity;
import com.arxyt.dominionsword.pomkotscompat.entity.RisingBlockEntity;
import com.arxyt.dominionsword.pomkotscompat.registry.PomkotsEntities;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.equipment.action.custom.ActionWeapon;
import grcmcs.minecraft.mods.pomkotsmechs.items.parts.weapons.TakaoItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the stahl-style ground-break effect to the Takao (大锤) charge hammer impact: explosion,
 * dust cloud, ground block debris burst and a debris ring at the hammer head.
 */
@Mixin(TakaoItem.class)
public abstract class TakaoGroundBreakEffectsMixin {
    @Inject(
            method = "tickWeaponInAction(Lgrcmcs/minecraft/mods/pomkotsmechs/entity/vehicle/equipment/action/"
                    + "custom/ActionWeapon$WeaponMechInterface;IZ)V",
            at = @At("HEAD"), remap = false
    )
    private void dominion$spawnGroundBreak(ActionWeapon.WeaponMechInterface mechInterface, int tick, boolean isOnFire,
                                           CallbackInfo ci) {
        try {
            if (!isOnFire) return;
            Level world = mechInterface.getWorld();
            if (!(world instanceof ServerLevel serverLevel)) return;

            float scale = tick > 60 ? 1.6F : tick > 40 ? 1.25F : 1.0F;
            Vec3 forward = new Vec3(0.0D, 0.0D, 5.0D).yRot((float) Math.toRadians(-mechInterface.getYRot()));
            BlockPos base = BlockPos.containing(mechInterface.position().add(forward));
            int groundY = dominion$findGroundY(serverLevel, base.getX(), base.getZ(), base.getY());
            Vec3 center = new Vec3(base.getX() + 0.5D, groundY + 1.0D, base.getZ() + 0.5D);
            BlockState ground = serverLevel.getBlockState(new BlockPos(base.getX(), groundY, base.getZ()));
            if (ground.isAir()) {
                ground = serverLevel.getBlockState(base.below());
            }

            BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.15D, center.z,
                    2, 0.45D * scale, 0.05D, 0.45D * scale, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.1D, center.z,
                    18, 1.8D * scale, 0.05D, 1.8D * scale, 0.08D);
            serverLevel.sendParticles(blockParticle, center.x, center.y + 0.25D, center.z,
                    90, 2.6D * scale, 0.35D, 2.6D * scale, 0.35D);
            for (int i = 0; i < 36; i++) {
                double angle = (Math.PI * 2.0D * i) / 36;
                double radius = (2.0D + serverLevel.random.nextDouble() * 3.6D) * scale;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                serverLevel.sendParticles(blockParticle, x, center.y + 0.12D, z, 3, 0.18D, 0.08D, 0.18D, 0.18D);
            }
            dominion$spawnRupturedBlocks(serverLevel, center, scale);
            GroundCrackEffectEntity crack = new GroundCrackEffectEntity(
                    PomkotsEntities.GROUND_CRACK.get(), serverLevel);
            crack.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);
            crack.setEffectScale(scale);
            serverLevel.addFreshEntity(crack);
        } catch (RuntimeException ex) {
            DominionSwordPomkotsCompatMod.LOGGER.warn("[DS-POMKOTS-WEAPON] takao ground break spawn failed", ex);
        }
    }

    @Unique
    private static void dominion$spawnRupturedBlocks(ServerLevel level, Vec3 center, float scale) {
        double radius = 2.2D * scale;
        int minX = Mth.floor(center.x - radius);
        int maxX = Mth.ceil(center.x + radius);
        int minZ = Mth.floor(center.z - radius);
        int maxZ = Mth.ceil(center.z + radius);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5D - center.x;
                double dz = z + 0.5D - center.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius || distance < 0.4D) {
                    continue;
                }

                int y = dominion$findGroundY(level, x, z, Mth.floor(center.y));
                mutable.set(x, y, z);
                BlockState blockState = level.getBlockState(mutable);
                if (!dominion$canRenderAsRisingBlock(level, mutable, blockState)) {
                    continue;
                }

                double distanceToMax = radius - distance;
                double bounceExponent = Math.min(1.0D / (radius * radius), 0.1D)
                        * (0.75D + distanceToMax / radius * 1.25D);
                Vec3 rotAxis = new Vec3(0.0D, -1.0D, 0.0D).cross(new Vec3(dx, 0.0D, dz)).normalize();
                float rotAngle = (float) Math.toRadians(
                        distance / radius * 15.0F + level.random.nextFloat() * 10.0F - 5.0F);
                Quaternionf rotation = new Quaternionf()
                        .rotationAxis(rotAngle, (float) rotAxis.x, (float) rotAxis.y, (float) rotAxis.z);
                rotation.mul(new Quaternionf().rotationX(
                        (float) Math.toRadians(level.random.nextFloat() * 12.0F - 6.0F)));
                rotation.mul(new Quaternionf().rotationY(
                        (float) Math.toRadians(level.random.nextFloat() * 40.0F - 20.0F)));
                rotation.mul(new Quaternionf().rotationZ(
                        (float) Math.toRadians(level.random.nextFloat() * 12.0F - 6.0F)));
                float bounce = 0.6F + (float) (distance * bounceExponent);
                int life = 20 + level.random.nextInt(Math.max(1, (int) (radius * 20.0D)));
                RisingBlockEntity risingBlock = new RisingBlockEntity(
                        level, x + 0.5D, y + 1.0D, z + 0.5D, blockState, life, rotation, bounce);
                level.addFreshEntity(risingBlock);
            }
        }
    }

    @Unique
    private static boolean dominion$canRenderAsRisingBlock(ServerLevel level, BlockPos pos, BlockState blockState) {
        return !blockState.isAir() && !blockState.hasBlockEntity() && !level.getBlockState(pos.above()).blocksMotion();
    }

    @Unique
    private static int dominion$findGroundY(ServerLevel level, int x, int z, int originY) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, originY, z);
        if (!level.getBlockState(mutable).isAir()) {
            return level.getBlockState(mutable.above()).isAir() ? originY : originY + 1;
        }
        for (int offset = 1; offset <= 6; offset++) {
            mutable.set(x, originY - offset, z);
            if (!level.getBlockState(mutable).isAir()) {
                return originY - offset;
            }
        }
        return originY;
    }
}
