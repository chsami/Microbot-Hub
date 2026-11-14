package net.runelite.client.plugins.microbot.droefficientwalker.compat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.shortestpath.PohPanel;

import javax.swing.SwingUtilities;
import java.lang.reflect.Method;

/**
 * Fills Microbot's POH panel from the house the player is standing in. The released client has
 * the detection routine but no public entry point for it, so its Detect action is reached by
 * reflection. If a client version changes that routine, the panel is simply left as it is.
 */
@Slf4j
public final class PohPanelDetect {
    private static volatile boolean unavailableLogged;

    private PohPanelDetect() {
    }

    /** No-op when the panel is not loaded. */
    public static void autoDetect() {
        try {
            PohPanel panel = PohPanel.instance;
            if (panel == null) return;
            Method detect = PohPanel.class.getDeclaredMethod("detectPortalsFromPOH");
            detect.setAccessible(true);
            SwingUtilities.invokeLater(() -> {
                try {
                    detect.invoke(panel);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    unavailable(ex);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            unavailable(ex);
        }
    }

    private static void unavailable(Throwable ex) {
        if (unavailableLogged) return;
        unavailableLogged = true;
        log.debug("POH panel auto-detect is unavailable on this client: {}", ex.toString());
    }
}
