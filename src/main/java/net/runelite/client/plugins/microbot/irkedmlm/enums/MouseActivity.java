package net.runelite.client.plugins.microbot.irkedmlm.enums;

import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality;

/**
 * How the cursor behaves during a mining bout (human-like mode only).
 *
 * <p>The three modes differ along two independent axes, so they are genuinely distinct rather than
 * the same behaviour at three speeds: <b>how often the cursor leaves the canvas</b>, and <b>how much
 * it does while it is on the canvas</b> (pre-hovering the next vein, glancing at the inventory).
 *
 * <ul>
 *   <li>{@link #AFK} — click the vein, leave, stay gone. No hovering, no glancing. Motherlode Mine
 *       is an AFK skill and this is what most people actually do.</li>
 *   <li>{@link #BALANCED} — parks most bouts, at this login's personality-driven rate, but the bouts
 *       spent on-canvas behave normally: pre-hovers, late hovers, occasional inventory checks.</li>
 *   <li>{@link #ACTIVE} — rarely parks and is busy while watching: hovers the next vein far more
 *       often and checks the inventory more often, like someone actually following the screen.</li>
 * </ul>
 *
 * <p>The multipliers scale {@link SessionPersonality}'s per-login propensities rather than replacing
 * them, so a relaxed player is still relaxed on ACTIVE and a twitchy one still twitchy on BALANCED.
 */
@RequiredArgsConstructor
public enum MouseActivity {
    /** Park every bout; do nothing else on-canvas. */
    AFK(Park.ALWAYS, 0.0, 0.0),
    /** Personality-rate parking, normal on-canvas attention. */
    BALANCED(Park.PERSONALITY, 1.0, 1.0),
    /** Seldom parks, and busy while watching. */
    ACTIVE(12, 1.8, 1.2);

    /** Sentinels for {@link #parkChance}. Nested because an enum constant cannot forward-reference a
     *  static field of its own type. */
    private static final class Park {
        /** Park on every bout. */
        private static final int ALWAYS = -1;
        /** Use this login's {@link SessionPersonality#offScreenParkChance()}. */
        private static final int PERSONALITY = -2;

        private Park() {
        }
    }

    private final int parkChance;
    private final double hoverMultiplier;
    private final double glanceMultiplier;

    /** Percent chance that a given mining bout is spent with the cursor parked off-canvas. */
    public int offScreenChance(SessionPersonality personality) {
        switch (parkChance) {
            case Park.ALWAYS:
                return 100;
            case Park.PERSONALITY:
                return personality.offScreenParkChance();
            default:
                return parkChance;
        }
    }

    /** True when this mode never wants the cursor doing anything on-canvas between clicks. */
    public boolean isPureAfk() {
        return this == AFK;
    }

    public int earlyHoverChance(SessionPersonality personality) {
        return scale(personality.earlyHoverChance(), hoverMultiplier);
    }

    public int lateHoverChance(SessionPersonality personality) {
        return scale(personality.lateHoverChance(), hoverMultiplier);
    }

    public int inventoryGlanceChance(SessionPersonality personality) {
        return scale(personality.inventoryGlanceChance(), glanceMultiplier);
    }

    private static int scale(int basePercent, double multiplier) {
        return Math.max(0, Math.min(100, (int) Math.round(basePercent * multiplier)));
    }

    @Override
    public String toString() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
