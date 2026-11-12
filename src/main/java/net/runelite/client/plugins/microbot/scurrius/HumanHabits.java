package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;

import java.util.Random;

/**
 * Pure decisions for the occasional human habits and the per-kill prayer mode. Everything is random and rare, and
 * none of it applies inside the fight except the harmless ones (a slightly early eat, a re-click up to a tick late).
 */
public final class HumanHabits {
    public static final int OFF_SCREEN_PERCENT = 30;
    public static final int SKILL_CHECK_PERCENT = 25;
    public static final int CAMERA_NUDGE_PERCENT = 35;
    public static final int PAUSE_PERCENT = 40;
    public static final int LATE_RECLICK_PERCENT = 12;
    public static final int EAT_EARLY_PERCENT = 15;
    public static final int MIN_PAUSE_MS = 1_000;
    public static final int MAX_PAUSE_MS = 8_000;
    public static final int MIN_RECLICK_DELAY_MS = 100;
    /** Less than one game tick. */
    public static final int MAX_RECLICK_DELAY_MS = 550;
    public static final int MAX_EAT_BOOST_PERCENT = 8;
    public static final int MAX_CAMERA_NUDGE_DEGREES = 30;

    private HumanHabits() {
    }

    public static boolean roll(Random random, int percent) {
        return random.nextInt(100) < percent;
    }

    /** A pause of 1-8 seconds, or 0 when this time it does not happen. */
    public static int pauseMs(Random random, boolean enabled) {
        if (!enabled || !roll(random, PAUSE_PERCENT)) {
            return 0;
        }
        return MIN_PAUSE_MS + random.nextInt(MAX_PAUSE_MS - MIN_PAUSE_MS + 1);
    }

    /** How long to wait before re-clicking Scurrius after the attack stopped: 0 mostly, up to under a tick sometimes. */
    public static int reclickDelayMs(Random random, boolean enabled) {
        if (!enabled || !roll(random, LATE_RECLICK_PERCENT)) {
            return 0;
        }
        return MIN_RECLICK_DELAY_MS + random.nextInt(MAX_RECLICK_DELAY_MS - MIN_RECLICK_DELAY_MS + 1);
    }

    /** The eat threshold in percent: now and then a few points above the rolled one, never above 99. */
    public static int eatThreshold(int base, Random random, boolean enabled) {
        if (!enabled || !roll(random, EAT_EARLY_PERCENT)) {
            return base;
        }
        return Math.min(99, base + 1 + random.nextInt(MAX_EAT_BOOST_PERCENT));
    }

    /** A camera turn of up to 30 degrees either way, never 0. */
    public static int cameraNudgeDegrees(Random random) {
        int amount = 1 + random.nextInt(MAX_CAMERA_NUDGE_DEGREES);
        return random.nextBoolean() ? amount : -amount;
    }

    /**
     * Mixed prayer: the mode for the next kill, always on or lazy flick, by weight. Both weights 0 (or negative) means
     * lazy flick, the default mode.
     */
    public static PrayerMode pickKillMode(int alwaysOnWeight, int lazyWeight, Random random) {
        int always = Math.max(0, alwaysOnWeight);
        int lazy = Math.max(0, lazyWeight);
        if (always + lazy == 0) {
            return PrayerMode.LAZY_FLICK;
        }
        return random.nextInt(always + lazy) < always ? PrayerMode.ALWAYS_ON : PrayerMode.LAZY_FLICK;
    }
}
