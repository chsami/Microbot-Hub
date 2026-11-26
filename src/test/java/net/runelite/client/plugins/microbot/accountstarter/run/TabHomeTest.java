package net.runelite.client.plugins.microbot.accountstarter.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TabHomeTest {
    @Test
    void theWaitIsNeverInstantAndMostlyShort() {
        assertEquals(800, TabHome.delayMs(0));
        assertEquals(6800, TabHome.delayMs(1));
        assertTrue(TabHome.delayMs(0.5) < 2500);
    }
}
