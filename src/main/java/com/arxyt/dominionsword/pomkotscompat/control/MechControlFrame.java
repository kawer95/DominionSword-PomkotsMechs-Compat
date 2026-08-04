package com.arxyt.dominionsword.pomkotscompat.control;

/** One server-tick worth of deterministic pilot input consumed by the movement mixin. */
public record MechControlFrame(boolean active, float forward, float strafe, float yaw, float pitch) {
    public static final MechControlFrame INACTIVE = new MechControlFrame(false, 0.0F, 0.0F, 0.0F, 0.0F);
}
