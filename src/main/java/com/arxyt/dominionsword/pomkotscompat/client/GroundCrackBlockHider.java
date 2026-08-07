package com.arxyt.dominionsword.pomkotscompat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side visual hiding of the ground blocks underneath active crack entities.
 * The world (and therefore collision) is never modified: only chunk tessellation for
 * these positions is skipped until the crack recovers. Safe to query from chunk
 * rebuild threads (concurrent set).
 */
public final class GroundCrackBlockHider {
    private static final Set<BlockPos> HIDDEN = ConcurrentHashMap.newKeySet();

    private GroundCrackBlockHider() {
    }

    public static boolean isHidden(BlockPos pos) {
        return pos != null && HIDDEN.contains(pos);
    }

    public static void add(BlockPos pos) {
        if (pos != null && HIDDEN.add(pos.immutable())) {
            markDirty(pos);
        }
    }

    public static void remove(BlockPos pos) {
        if (pos != null && HIDDEN.remove(pos)) {
            markDirty(pos);
        }
    }

    public static void clearAll() {
        HIDDEN.clear();
    }

    private static void markDirty(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.levelRenderer == null) {
            return;
        }
        mc.levelRenderer.setBlocksDirty(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
