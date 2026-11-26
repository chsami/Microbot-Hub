package net.runelite.client.plugins.microbot.accountstarter.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KitShopTest {
    @Test
    void roundsUpToAStackAndSometimesOne() {
        assertEquals(50, Kit.stockUp(5, 500, 50, false));
        assertEquals(100, Kit.stockUp(5, 500, 50, true));
        assertEquals(60, Kit.stockUp(52, 500, 10, false));
    }

    @Test
    void neverPassesWhatTheCoinsCanPay() {
        assertEquals(15, Kit.stockUp(5, 15, 50, true));
        assertEquals(0, Kit.stockUp(5, 0, 50, false));
        assertEquals(0, Kit.stockUp(0, 500, 50, true));
    }

    @Test
    void neverBuysMoreThanIsLeftToBuy() {
        assertEquals(1, Kit.shopClick(4));
        assertEquals(5, Kit.shopClick(9));
        assertEquals(10, Kit.shopClick(49));
        assertEquals(50, Kit.shopClick(120));
    }
}
