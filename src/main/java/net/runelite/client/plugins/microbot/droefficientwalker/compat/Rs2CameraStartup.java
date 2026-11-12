package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-login startup framing, so profiles and the AIO session camera do not start every user on one
 * view. Pure value rolls: callers still apply them through {@link Rs2Camera}.
 */
public final class Rs2CameraStartup {
    /** The settings default: "not set", so the per-session band below applies. */
    public static final int DEFAULT_ZOOM = 100;
    /** Startup zoom band (CAMERA_DO_ZOOM scale, bigger = closer), rolled once per session. */
    public static final int ZOOM_MIN = 150;
    public static final int ZOOM_MAX = 200;

    private static final int LEGACY_MIN_PITCH = 128;
    private static final int LEGACY_MAX_PITCH = 383;
    private static final int CLIENT_ANGLE_SCALE = 8;

    /** A startup zoom: the band for the shared default, or an explicitly configured zoom unchanged. */
    public static int rollZoom(int configured) {
        return configured == DEFAULT_ZOOM ? randomZoom() : configured;
    }

    /** One roll from the startup zoom band. */
    public static int randomZoom() {
        return ThreadLocalRandom.current().nextInt(ZOOM_MIN, ZOOM_MAX + 1);
    }

    /**
     * The long-standing startup pitch roll, passed to the camera unconverted. Profile settings hold the
     * client's 8x units (2200..2849), which the camera clamps to its top pitch; that is the framing
     * players have always had, so it is deliberately kept (only the zoom is randomized).
     */
    public static int rollPitch(int min, int maxExclusive) {
        int low = Math.min(min, maxExclusive - 1);
        int high = Math.max(low + 1, maxExclusive);
        return ThreadLocalRandom.current().nextInt(low, high);
    }

    /** Wilderness Agility's own historical conversion of 8x pitch units to legacy units. */
    public static int toLegacyPitch(int pitch) {
        int legacy = pitch > LEGACY_MAX_PITCH ? pitch / CLIENT_ANGLE_SCALE : pitch;
        return Math.max(LEGACY_MIN_PITCH, Math.min(LEGACY_MAX_PITCH, legacy));
    }

    private Rs2CameraStartup() {}
}
