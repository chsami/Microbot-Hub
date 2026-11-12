package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * AIO Slayer walks only through the separate [Dro] Efficient Walker Hub plugin. This check runs
 * before Slayer starts so it never starts without a walker to travel with.
 */
final class DroSlayerWalkerRequirement {
    static final String PLUGIN_CLASS =
            "net.runelite.client.plugins.microbot.droefficientwalker.EfficientWalkerPlugin";
    static final String MISSING_MESSAGE =
            "[Dro] AIO Slayer needs the [Dro] Efficient Walker plugin.\n"
                    + "Download it from the Microbot Hub, enable it, then start AIO Slayer again.";

    private DroSlayerWalkerRequirement() {
    }

    /** True when Efficient Walker is installed and enabled. */
    static boolean efficientWalkerEnabled() {
        try {
            for (Plugin plugin : Microbot.getPluginManager().getPlugins()) {
                if (plugin != null && PLUGIN_CLASS.equals(plugin.getClass().getName())) {
                    return Microbot.isPluginEnabled(plugin);
                }
            }
        } catch (RuntimeException ignored) {
            // No plugin manager yet means no walker either.
        }
        return false;
    }
}
