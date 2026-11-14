package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Shared "Use POH" switch for activities. The activity only supplies the house teleport
 * (tablets or Construction cape); Efficient Walker's Use POH mode then routes from the house
 * through the mounted Digsite pendant and other facilities.
 */
public final class PohTravelSupport {
    public static final int HOUSE_TAB_COUNT = 10;
    public static final int HOUSE_TAB_ID = ItemID.POH_TABLET_TELEPORTTOHOUSE;
    public static final int[] CAPE_IDS = {net.runelite.api.ItemID.CONSTRUCT_CAPET, net.runelite.api.ItemID.CONSTRUCT_CAPE};

    public enum Item {
        HOUSE_TABS("House tabs (10)"),
        CONSTRUCTION_CAPE("Construction cape");

        private final String label;
        Item(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final String KEY = "useMaxPoh";
    private static String previous;
    private static int holders;
    private static boolean enabledByUs;

    private PohTravelSupport() { }

    /** Turns on the walker's Max POH hub for this run unless it is already on; balanced by {@link #release()}. */
    public static synchronized void acquire() {
        ConfigManager configs = Microbot.getConfigManager();
        if (configs == null) return;
        if (holders++ > 0) return;
        previous = configs.getConfiguration(EfficientWalkerConfig.GROUP, KEY);
        enabledByUs = !Boolean.parseBoolean(previous);
        if (enabledByUs) configs.setConfiguration(EfficientWalkerConfig.GROUP, KEY, true);
    }

    public static synchronized void release() {
        if (holders == 0 || --holders > 0) return;
        ConfigManager configs = Microbot.getConfigManager();
        if (configs != null && enabledByUs) {
            if (previous == null) configs.unsetConfiguration(EfficientWalkerConfig.GROUP, KEY);
            else configs.setConfiguration(EfficientWalkerConfig.GROUP, KEY, previous);
        }
        previous = null;
        enabledByUs = false;
    }
}
