package com.arxyt.dominionsword.pomkotscompat.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rebuilds the full axis-aligned bounds of a rotated melee hit box.
 *
 * Pomkots Mechs alpha.8 builds its melee damage box with
 * {@code new AABB(rotatedCorner1, rotatedCorner2)}. The two corners are opposite corners of
 * a box that is rotated around the mech, and an AABB built from only two opposite corners of
 * a rotated box collapses into a thin diagonal sliver whenever the mech is not facing exactly
 * along an axis. Targets standing right in front of the hammer can then be outside the box, so
 * the swing deals no knockback and no damage. This helper rotates all corners explicitly and
 * returns the true bounding box.
 */
public final class MeleeAabbFix {
    private MeleeAabbFix() {
    }

    /**
     * @param pos  mech position (box origin before rotation)
     * @param yaw  mech yaw in degrees (same value the weapon code rotates by)
     * @param xs   local x coordinates of the box corners (already multiplied by isRight)
     * @param ys   local y coordinates of the box corners
     * @param zs   local z coordinates of the box corners
     */
    public static AABB rotatedBox(Vec3 pos, float yaw, double[] xs, double[] ys, double[] zs) {
        double angle = Math.toRadians(-yaw);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double rx = x * cos + z * sin;
                    double rz = -x * sin + z * cos;
                    minX = Math.min(minX, rx);
                    maxX = Math.max(maxX, rx);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, rz);
                    maxZ = Math.max(maxZ, rz);
                }
            }
        }
        return new AABB(pos.x + minX, pos.y + minY, pos.z + minZ,
                pos.x + maxX, pos.y + maxY, pos.z + maxZ);
    }
}
