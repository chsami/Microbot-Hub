package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.runelite.api.Point;

/**
 * Hub build version. The droplugins Microbot client also routes Inventory Setups mouse movement
 * through the registered Dro profile; the released Microbot client has no such hook, so only the
 * profile's "may the host act now" answer is kept for the walker's own checks.
 */
public final class DroSetupMouse {
    private static volatile Object owner;
    private static volatile BooleanSupplier allowed = () -> true;

    private DroSetupMouse() {
    }

    public static synchronized void register(Object profile, Runnable before, Runnable after,
            Function<Rectangle, Point> point, Runnable idle, BooleanSupplier hostAllowed) {
        owner = profile;
        allowed = hostAllowed == null ? () -> true : hostAllowed;
    }

    public static synchronized void unregister(Object profile) {
        if (owner != profile) return;
        owner = null;
        allowed = () -> true;
    }

    /** Shared maintenance must not disturb the running host's active break. */
    public static boolean isHostInputAllowed() {
        return owner == null || allowed.getAsBoolean();
    }
}
