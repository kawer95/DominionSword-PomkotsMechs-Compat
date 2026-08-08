package com.arxyt.dominionsword.pomkotscompat.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last game tick at which each mech actually fired its Takao charge swing.
 * The add-on uses this to verify that a released charge produced a fire tick; if the native
 * single-tick fire check was skipped (action reset or tick drift), the stuck charge can be
 * detected and recovered instead of leaving the mech charging forever.
 */
public final class TakaoFireTracker {
    private static final Map<UUID, Long> LAST_FIRE_TICK = new ConcurrentHashMap<>();

    private TakaoFireTracker() {
    }

    public static void markFire(UUID mechId, long gameTime) {
        LAST_FIRE_TICK.put(mechId, gameTime);
    }

    public static long lastFireTick(UUID mechId) {
        return LAST_FIRE_TICK.getOrDefault(mechId, -1L);
    }

    public static void remove(UUID mechId) {
        LAST_FIRE_TICK.remove(mechId);
    }
}
