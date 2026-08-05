package com.arxyt.dominionsword.pomkotscompat.debug;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sampled server-side trajectory diagnostics for Dominion-controlled PMVC direct fire. */
public final class PomkotsAimDebug {
    private static final String TRACE = "DominionPomkotsAimTrace";
    private static final String TARGET = "DominionPomkotsAimTarget";
    private static final Map<UUID, Long> NEXT_MACHINE_TRACE = new ConcurrentHashMap<>();

    private PomkotsAimDebug() {}

    public static boolean shouldTrace(Projectile projectile, Pmvc01Entity mech, boolean grenade) {
        if (projectile.level().isClientSide) return false;
        if (grenade) return true;
        long now = projectile.level().getGameTime();
        long next = NEXT_MACHINE_TRACE.getOrDefault(mech.getUUID(), 0L);
        if (now < next) return false;
        NEXT_MACHINE_TRACE.put(mech.getUUID(), now + 20L);
        return true;
    }

    public static void skipped(Projectile projectile, String reason) {
        if (projectile.level().isClientSide) return;
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-AIM] correction skipped projectile={} id={} reason={} pos={} velocity={} owner={}",
                projectile.getType(), projectile.getUUID(), reason, format(projectile.position()),
                format(projectile.getDeltaMovement()), identity(projectile.getOwner()));
    }

    public static void begin(Projectile projectile, Pmvc01Entity mech, Entity target,
                             Vec3 nativeVelocity, Vec3 correctedVelocity) {
        projectile.getPersistentData().putBoolean(TRACE, true);
        projectile.getPersistentData().putUUID(TARGET, target.getUUID());
        AABB box = target.getBoundingBox();
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-AIM] trace begin projectile={} id={} mech={} target={} targetId={} muzzle={} "
                        + "targetBox={} nativeVelocity={} correctedVelocity={}",
                projectile.getType(), projectile.getUUID(), mech.getUUID(), target.getType(), target.getUUID(),
                format(projectile.position()), format(box), format(nativeVelocity), format(correctedVelocity));
    }

    public static boolean tracing(Entity projectile) {
        return projectile.getPersistentData().getBoolean(TRACE);
    }

    public static void tick(Projectile projectile, Vec3 from) {
        if (!tracing(projectile) || !(projectile.level() instanceof ServerLevel level)) return;
        UUID targetId = projectile.getPersistentData().hasUUID(TARGET)
                ? projectile.getPersistentData().getUUID(TARGET) : null;
        Entity target = targetId == null ? null : level.getEntity(targetId);
        Vec3 to = projectile.position();
        if (target == null) {
            DominionSwordPomkotsCompatMod.LOGGER.warn(
                    "[DS-POMKOTS-AIM] trace tick projectileId={} age={} from={} to={} velocity={} target=missing removed={}",
                    projectile.getUUID(), projectile.tickCount, format(from), format(to),
                    format(projectile.getDeltaMovement()), projectile.isRemoved());
            return;
        }
        AABB hitBox = target.getBoundingBox().inflate(projectile.getBbWidth() * 0.5D);
        boolean crossed = hitBox.contains(from) || hitBox.contains(to) || hitBox.clip(from, to).isPresent();
        double centerDistance = to.distanceTo(target.getBoundingBox().getCenter());
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-AIM] trace tick projectileId={} age={} from={} to={} velocity={} targetBox={} "
                        + "centerDistance={} crossedTargetBox={} removed={}",
                projectile.getUUID(), projectile.tickCount, format(from), format(to),
                format(projectile.getDeltaMovement()), format(target.getBoundingBox()),
                String.format(java.util.Locale.ROOT, "%.3f", centerDistance), crossed, projectile.isRemoved());
    }

    public static void hitEntity(Projectile projectile, Entity hit) {
        if (!tracing(projectile)) return;
        UUID targetId = projectile.getPersistentData().hasUUID(TARGET)
                ? projectile.getPersistentData().getUUID(TARGET) : null;
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-AIM] trace entity-hit projectileId={} age={} hit={} hitId={} expectedTarget={} correctTarget={} pos={}",
                projectile.getUUID(), projectile.tickCount, hit.getType(), hit.getUUID(), targetId,
                hit.getUUID().equals(targetId), format(projectile.position()));
    }

    public static void hitBlock(Projectile projectile, Vec3 hitPos) {
        if (!tracing(projectile)) return;
        DominionSwordPomkotsCompatMod.LOGGER.warn(
                "[DS-POMKOTS-AIM] trace block-hit projectileId={} age={} hitPos={} projectilePos={} velocity={}",
                projectile.getUUID(), projectile.tickCount, format(hitPos), format(projectile.position()),
                format(projectile.getDeltaMovement()));
    }

    private static String identity(Entity entity) {
        return entity == null ? "null" : entity.getType() + "/" + entity.getUUID();
    }

    private static String format(Vec3 value) {
        return String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f)", value.x, value.y, value.z);
    }

    private static String format(AABB value) {
        return String.format(java.util.Locale.ROOT, "[(%.3f,%.3f,%.3f)->(%.3f,%.3f,%.3f)]",
                value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ);
    }
}
