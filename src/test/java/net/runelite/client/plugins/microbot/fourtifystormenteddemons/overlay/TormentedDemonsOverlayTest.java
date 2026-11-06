package net.runelite.client.plugins.microbot.fourtifystormenteddemons.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TormentedDemonsOverlayTest
{
    @Test
    public void lootValuesShortenToThousandsAndMillions()
    {
        assertEquals("0", TormentedDemonsOverlay.shortValue(0));
        assertEquals("9999", TormentedDemonsOverlay.shortValue(9_999));
        assertEquals("10.0K", TormentedDemonsOverlay.shortValue(10_000));
        assertEquals("512.3K", TormentedDemonsOverlay.shortValue(512_340));
        assertEquals("1.0M", TormentedDemonsOverlay.shortValue(1_000_000));
        assertEquals("3.2M", TormentedDemonsOverlay.shortValue(3_215_400));
    }
}
