package net.runelite.client.plugins.microbot.scurrius;

import java.util.Random;

/** Pure decisions for the random restock bank trips. */
public final class RestockTrips {
    private RestockTrips() {
    }

    /**
     * A uniform random number of kills between bank visits, in [min, max]. A swapped range is put right, and anything
     * under 1 counts as 1.
     */
    public static int rollTarget(int min, int max, Random random) {
        int low = Math.max(1, Math.min(min, max));
        int high = Math.max(1, Math.max(min, max));
        return low + random.nextInt(high - low + 1);
    }

    /**
     * Whether to leave for a restock trip now: enough kills since the last bank visit, Scurrius dead, the loot picked up
     * and food still left. Never while the boss is alive, so never mid-fight.
     */
    public static boolean shouldStartRestockTrip(int killsSinceBank, int target, boolean bossAlive, boolean lootPending,
                                                 int foodLeft) {
        return target > 0 && killsSinceBank >= target && !bossAlive && !lootPending && foodLeft > 0;
    }
}
