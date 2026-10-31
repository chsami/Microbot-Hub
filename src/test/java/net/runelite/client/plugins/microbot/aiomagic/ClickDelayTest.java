package net.runelite.client.plugins.microbot.aiomagic;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ClickDelayTest {

    @Test
    void staysWithinRangeNeverRepeatsAndSkewsFast() {
        ClickDelay delay = new ClickDelay(new Random(42));
        int previous = -1;
        int fastHalf = 0;
        int samples = 10_000;
        for (int i = 0; i < samples; i++) {
            int value = delay.next(40, 350);
            assertTrue(value >= 40 && value <= 350, "out of range: " + value);
            assertNotEquals(previous, value);
            previous = value;
            if (value < 195) fastHalf++;
        }
        assertTrue(fastHalf > samples * 0.7, "expected most delays in the fast half, got " + fastHalf);
    }

    @Test
    void swapsMinAndMaxWhenReversed() {
        ClickDelay delay = new ClickDelay(new Random(1));
        for (int i = 0; i < 1000; i++) {
            int value = delay.next(350, 40);
            assertTrue(value >= 40 && value <= 350);
        }
    }

    @Test
    void equalBoundsReturnThatValue() {
        assertEquals(100, new ClickDelay(new Random(1)).next(100, 100));
    }
}
