package net.runelite.client.plugins.microbot.scurrius;

/**
 * How to click a prayer when the mouse path is wanted. The click must reach the server before the next game tick, so a
 * mouse click on the prayer icon is only used while there is time left in the tick; otherwise it is the instant menu
 * click. Pure.
 */
public final class PrayerClickPlan {
    public enum Plan { INSTANT, MOUSE, TAB_THEN_MOUSE }

    /** Longest into the tick a tab switch is still started for. */
    public static final long TAB_SWITCH_DEADLINE_MS = 150;
    /** Longest into the tick a mouse click on the icon is still started for. */
    public static final long MOUSE_DEADLINE_MS = 350;
    /** After the tab switch the mouse click still happens only if this much of the tick is left. */
    public static final long AFTER_TAB_DEADLINE_MS = 300;

    private PrayerClickPlan() {
    }

    public static Plan decide(boolean mouseEnabled, boolean prayerTabOpen, long msIntoTick) {
        if (!mouseEnabled) {
            return Plan.INSTANT;
        }
        if (prayerTabOpen) {
            return msIntoTick <= MOUSE_DEADLINE_MS ? Plan.MOUSE : Plan.INSTANT;
        }
        return msIntoTick <= TAB_SWITCH_DEADLINE_MS ? Plan.TAB_THEN_MOUSE : Plan.INSTANT;
    }

    /** Whether, after a tab switch that ended {@code msIntoTick} into the tick, the mouse click is still in time. */
    public static boolean mouseStillInTime(long msIntoTick) {
        return msIntoTick <= AFTER_TAB_DEADLINE_MS;
    }
}
