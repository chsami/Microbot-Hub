package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.Surplus;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurplusTest {
    @Test
    void budgetIsCapLeftOrCoinsAboveReserve() {
        assertEquals(4000, Surplus.budget(5000, 1000, 10_000, 50));
        assertEquals(150, Surplus.budget(5000, 0, 200, 50));
        assertEquals(0, Surplus.budget(5000, 6000, 10_000, 50));
        assertEquals(0, Surplus.budget(5000, 0, 20, 50));
    }

    @Test
    void retriesRaiseBuyAndLowerSellPrice() {
        assertEquals(108, Surplus.buyPrice(100, 1.08, 0));
        assertEquals(124, Surplus.buyPrice(100, 1.08, 2));
        assertEquals(Integer.MAX_VALUE, Surplus.buyPrice(Long.MAX_VALUE / 2, 1.1, 0));
    }

    @Test
    void humanBuyRoundsToSteps() {
        assertEquals(50, Surplus.humanBuy(30, 1.25));
        assertEquals(200, Surplus.humanBuy(160, 1.2));
        assertEquals(950, Surplus.humanBuy(700, 1.3));
        assertEquals(0, Surplus.humanBuy(0, 1.3));
        assertEquals(0, Surplus.humanBuy(-5, 1.3));
        assertEquals(125, Surplus.humanBuy(100, 1.1));
    }

    @Test
    void humanBuyNeverOverHalfAgain() {
        assertEquals(150, Surplus.humanBuy(100, 3.0));
        for (int m = 1; m <= 3000; m++) {
            for (double b = 1.10; b <= 1.40 + 1e-9; b += 0.05) {
                int q = Surplus.humanBuy(m, b);
                int step = m * b < 200 ? 25 : m * b < 1000 ? 50 : 100;
                assertTrue(q >= m && q % step == 0, m + " x " + b);
                assertTrue(q <= Math.ceil(m * 1.5 / step) * step, m + " x " + b);
            }
        }
    }

    @Test
    void onlyStackablesAreRounded() {
        assertEquals(1, Surplus.buyQuantity(841, 1, 1.3));
        assertEquals(50, Surplus.buyQuantity(ItemID.BRONZE_ARROW, 30, 1.3));
    }

}
