package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

public final class HumanBehaviorProfile {
    private HumanBehaviorProfile() { }

    public static int pauseDelayMs(boolean enabled, int minMs, int maxMs, boolean urgent) {
        if (!enabled) return 0;
        int quickChance = urgent ? 65 : 32;
        if (Rs2Random.between(0, 100) < quickChance) {
            int quickMin = Math.max(30, minMs / 2);
            int quickMax = Math.min(220, (minMs + maxMs) / 2);
            if (quickMax < quickMin) quickMax = quickMin + 30;
            return Rs2Random.between(quickMin, quickMax);
        }
        return Rs2Random.between(minMs, maxMs);
    }
}
