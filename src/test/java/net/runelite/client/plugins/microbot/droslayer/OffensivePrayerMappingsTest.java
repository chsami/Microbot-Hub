package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OffensivePrayerMappingsTest
{
    @Test
    void savedSelectionsMapToTheirPrayersAndUnknownOnesFallBackToAutomatic()
    {
        assertEquals(Rs2PrayerEnum.PIETY, DroSlayerOffensivePrayer.fromString("PIETY").getPrayer());
        assertEquals(Rs2PrayerEnum.RIGOUR, DroSlayerOffensivePrayer.fromString("rigour").getPrayer());
        // The released client has no Zeal prayer; a saved Zeal choice falls back to the weapon's prayer.
        assertEquals(DroSlayerOffensivePrayer.AUTOMATIC, DroSlayerOffensivePrayer.fromString("zeal"));
        assertEquals(DroSlayerOffensivePrayer.AUTOMATIC, DroSlayerOffensivePrayer.fromString("unknown"));
    }
}
