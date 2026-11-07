package net.runelite.client.plugins.microbot.scurrius.enums;

/** How the bot handles Scurrius's protection prayers during the fight. */
public enum PrayerMode {
    /** The protection prayer for Scurrius's current style stays on all fight. */
    ALWAYS_ON("Always on"),
    /** The prayer is on only for the tick before a hit lands and the tick of the hit. */
    LAZY_FLICK("Lazy flick"),
    /** The prayer is switched off and on again every tick so it never drains. */
    ONE_TICK_FLICK("One-tick flick");

    private final String label;

    PrayerMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
