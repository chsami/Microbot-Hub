package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PhosanisNightmareOverlayTest
{
    @Test
    public void lootValuesShortenToThousandsAndMillions()
    {
        assertEquals("0", PhosanisNightmareOverlay.shortValue(0));
        assertEquals("9999", PhosanisNightmareOverlay.shortValue(9_999));
        assertEquals("10.0K", PhosanisNightmareOverlay.shortValue(10_000));
        assertEquals("512.3K", PhosanisNightmareOverlay.shortValue(512_340));
        assertEquals("1.0M", PhosanisNightmareOverlay.shortValue(1_000_000));
        assertEquals("3.2M", PhosanisNightmareOverlay.shortValue(3_215_400));
    }
}
