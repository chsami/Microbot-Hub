package net.runelite.client.plugins.microbot.virewatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PVirewatchKillerPluginFormatNumberTest
{
    @Test
    void formatsLootTotalsAboveIntRange()
    {
        assertEquals("3000m", PVirewatchKillerPlugin.formatNumber(3_000_000_000L));
    }

    @Test
    void formatsSmallTotalsUnchanged()
    {
        assertEquals("999", PVirewatchKillerPlugin.formatNumber(999L));
        assertEquals("12k", PVirewatchKillerPlugin.formatNumber(12_345L));
        assertEquals("2m", PVirewatchKillerPlugin.formatNumber(2_500_000L));
    }
}
