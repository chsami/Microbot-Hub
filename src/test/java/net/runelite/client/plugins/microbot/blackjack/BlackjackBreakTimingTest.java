package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackBreakTimingTest
{
    private final BlackjackConfig config = new BlackjackConfig() {};

    @Test
    void defaultsPreserveExistingBreaks()
    {
        assertEquals(9, config.microBreakIntervalMin());
        assertEquals(11, config.microBreakIntervalMax());
        assertEquals(25, config.microBreakDurationMin());
        assertEquals(35, config.microBreakDurationMax());
        assertEquals(20, config.smallBreakIntervalMin());
        assertEquals(30, config.smallBreakIntervalMax());
        assertEquals(60, config.smallBreakDurationMin());
        assertEquals(120, config.smallBreakDurationMax());
    }

    @Test
    void configuredRangesUseCorrectUnitsAndOrderBounds()
    {
        for (int i = 0; i < 100; i++)
        {
            long interval = BlackjackScript.randomBreakDelay(11, 9, 60_000, 240);
            assertTrue(interval >= 540_000 && interval <= 660_000);
            long duration = BlackjackScript.randomBreakDelay(25, 35, 1_000, 600);
            assertTrue(duration >= 25_000 && duration <= 35_000);
        }
        assertEquals(60_000, BlackjackScript.randomBreakDelay(1, 1, 60_000, 240));
    }

    @Test
    void rawConfigOutsideBoundsCannotCreateInvalidDelays()
    {
        assertEquals(1_000, BlackjackScript.randomBreakDelay(0, Integer.MIN_VALUE, 1_000, 600));
        assertEquals(600_000, BlackjackScript.randomBreakDelay(Integer.MAX_VALUE, 601, 1_000, 600));
    }
}
