package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

public final class Surplus {
    // doubles like 1.08 * 100 land a hair above 108; without this ceil would bid 109
    private static final double EPS = 1e-6;
    private static final Set<Integer> STACKABLE = Set.of(GearPlan.AIR, GearPlan.WATER, GearPlan.EARTH, GearPlan.FIRE,
            GearPlan.MIND, ItemID.BRONZE_ARROW, ItemID.IRON_ARROW);

    private Surplus() {}

    public static int budget(int coinBudget, int spent, int coins, int reserve) {
        return Math.max(0, Math.min(coinBudget - spent, coins - reserve));
    }

    public static int buyQuantity(int itemId, int missing, double buffer) {
        return STACKABLE.contains(itemId) ? humanBuy(missing, buffer) : Math.max(0, missing);
    }

    public static int humanBuy(int missing, double buffer) {
        if (missing <= 0) return 0;
        double raw = (double) missing * buffer;
        int step = raw < 200 ? 25 : raw < 1000 ? 50 : 100;
        return (int) Math.min(roundUp(raw, step), roundUp(missing * 1.5, step));
    }

    private static long roundUp(double n, int step) {
        return (long) Math.ceil(n / step - EPS) * step;
    }

    public static int buyPrice(long guide, double premium, int attempt) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(guide * (premium + (premium - 1) * attempt) - EPS));
    }

}
