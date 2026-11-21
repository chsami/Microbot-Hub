package net.runelite.client.plugins.microbot.droslayer;

/** Pre-trip recovery threshold; an actual pool visit still restores fully. */
public final class DroSlayerRecoveryPolicy {
    private DroSlayerRecoveryPolicy() {}

    public static boolean needsRecovery(int hp, int maxHp, int prayer, int maxPrayer, int runEnergy) {
        return maxHp <= 0 || maxPrayer <= 0
                || (long) hp * 100 <= (long) maxHp * 70
                || (long) prayer * 100 <= (long) maxPrayer * 70
                || runEnergy <= 70;
    }
}
