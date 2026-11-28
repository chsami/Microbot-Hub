package net.runelite.client.plugins.microbot.irkedmlm;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Centralised timing/odds profile for MLM-specific human-like behaviour.
 */
public final class HumanBehaviorProfile {
    private HumanBehaviorProfile() {
    }

    public static int pauseDelayMs(boolean humanLike, int minMs, int maxMs, boolean urgent) {
        if (!humanLike) {
            return 0;
        }
        int quickChance = urgent ? 65 : 32;
        if (Rs2Random.between(0, 100) < quickChance) {
            int qMin = Math.max(30, minMs / 2);
            int qMax = Math.min(220, (minMs + maxMs) / 2);
            if (qMax < qMin) {
                qMax = qMin + 30;
            }
            return Rs2Random.between(qMin, qMax);
        }
        return Rs2Random.between(minMs, maxMs);
    }

    public static long ladderInteractionCooldownMs(boolean humanLike) {
        return humanLike ? Rs2Random.between(300, 700) : 300L;
    }

    /**
     * Delay after a Climb-up/down click before the state machine re-evaluates. The climb and the
     * floor-height update are already covered by the animation guard, so this is only the player's
     * own reaction on landing — and a player climbing a ladder is mid-chore, not idling. Kept short
     * with a shallow tail; the old 1.8-3.5s tail is what made every floor change feel like a hang.
     */
    public static long postLadderSettleDelayMs(boolean humanLike) {
        if (!humanLike) {
            return Rs2Random.between(250, 600);
        }

        int roll = Rs2Random.between(0, 100);
        if (roll < 75) {
            return Rs2Random.between(250, 600);
        }
        if (roll < 95) {
            return Rs2Random.between(600, 1100);
        }
        return Rs2Random.between(1100, 1800);
    }

    public static long repairPostLadderSettleDelayMs(boolean humanLike) {
        if (!humanLike) {
            return Rs2Random.between(250, 500);
        }

        // Mostly short — after descending to repair, a human is ready to go straight to the crate/wheel.
        int roll = Rs2Random.between(0, 100);
        if (roll < 78) {
            return Rs2Random.between(220, 550);
        }
        if (roll < 95) {
            return Rs2Random.between(550, 1000);
        }
        return Rs2Random.between(1000, 1600);
    }
}
