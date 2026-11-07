package net.runelite.client.plugins.microbot.scurrius;

import java.util.Random;

/** Pure decisions for using the special attack on Scurrius. Energies are percent (0-100). */
public final class SpecialAttacks {
    /** Cost used when the weapon is not known (a toxic blowpipe costs 50%). */
    public static final int DEFAULT_COST_PERCENT = 50;
    public static final int MAX_DELAY_MS = 1_500;
    /** How long to wait for the energy to drop after turning the spec on before calling it a failed attempt. */
    public static final long VERIFY_TIMEOUT_MS = 6_000;
    /** After a failed attempt, wait this long before trying again. */
    public static final long RETRY_COOLDOWN_MS = 10_000;

    private SpecialAttacks() {
    }

    /**
     * The energy needed in percent: the configured value when set (1-100), else the weapon's own cost when it is known
     * ({@code weaponCostRaw} is 0-1000, 0 when unknown), else 50.
     */
    public static int neededPercent(int configuredPercent, int weaponCostRaw) {
        if (configuredPercent > 0) {
            return Math.min(100, configuredPercent);
        }
        if (weaponCostRaw > 0) {
            return Math.min(100, (weaponCostRaw + 9) / 10);
        }
        return DEFAULT_COST_PERCENT;
    }

    /** Random wait of 0-1.5 s after the energy became available, so it is not instant every time. */
    public static int delayMs(Random random, boolean enabled) {
        return enabled ? random.nextInt(MAX_DELAY_MS + 1) : 0;
    }

    /**
     * Whether to turn the spec on now. Only on Scurrius himself, alive and being attacked, never while attacks are held
     * (dodging rocks), not within the retry cooldown, not while an earlier attempt is still being verified, and, when
     * a skip threshold is set, not when he is nearly dead.
     *
     * @param bossHealthPercent Scurrius's health in percent, 100 when unknown
     * @param skipUnderPercent  skip the spec when his health is under this; 0 = never skip
     */
    public static boolean shouldSpec(boolean enabled, int energyPercent, int neededPercent, boolean bossAlive,
                                     boolean attackingBoss, boolean attackHeld, boolean attemptPending,
                                     boolean cooldownOver, boolean delayElapsed, int bossHealthPercent, int skipUnderPercent) {
        if (!enabled || !bossAlive || !attackingBoss || attackHeld || attemptPending || !cooldownOver || !delayElapsed) {
            return false;
        }
        if (skipUnderPercent > 0 && bossHealthPercent < skipUnderPercent) {
            return false;
        }
        return energyPercent >= neededPercent;
    }

    /** Whether the energy dropped, which is how a fired spec is told from one that never went off. */
    public static boolean fired(int energyBeforeRaw, int energyNowRaw) {
        return energyNowRaw < energyBeforeRaw;
    }
}
