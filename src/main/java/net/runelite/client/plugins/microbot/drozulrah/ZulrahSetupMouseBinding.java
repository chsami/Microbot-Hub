package net.runelite.client.plugins.microbot.drozulrah;

import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.runelite.api.Point;

/** Uses a host-provided setup mouse scope when available; ordinary clients keep their setup implementation. */
final class ZulrahSetupMouseBinding {
    private static final String HOST = "net.runelite.client.plugins.microbot.inventorysetups.DroSetupMouse";

    static void register(Object owner, Runnable before, Runnable after, Function<Rectangle, Point> point,
                         Runnable idle, BooleanSupplier allowed) {
        try {
            Class.forName(HOST).getMethod("register", Object.class, Runnable.class, Runnable.class,
                    Function.class, Runnable.class, BooleanSupplier.class)
                    .invoke(null, owner, before, after, point, idle, allowed);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Optional client extension; it is not part of the Hub's minimum client API.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register setup mouse profile", e);
        }
    }

    static void unregister(Object owner) {
        try {
            Class.forName(HOST).getMethod("unregister", Object.class).invoke(null, owner);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // No setup extension was registered on this host.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to release setup mouse profile", e);
        }
    }
}
