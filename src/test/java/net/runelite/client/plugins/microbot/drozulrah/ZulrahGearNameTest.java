package net.runelite.client.plugins.microbot.drozulrah;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahGearNameTest
{
    private static boolean partialMatch(String itemName, String setupName)
    {
        return itemName.toLowerCase(Locale.ROOT).contains(ZulrahGearName.base(setupName).toLowerCase(Locale.ROOT));
    }

    @Test public void setupTridentAtFullChargeMatchesTheTridentAfterItDropsACharge()
    {
        assertEquals("Trident of the Seas", ZulrahGearName.base("Trident of the Seas (full)"));
        assertTrue(partialMatch("Trident of the seas", "Trident of the Seas (full)"));
        assertTrue(partialMatch("Trident of the seas (full)", "Trident of the Seas (full)"));
    }

    @Test public void numberedChargesMatchAnyChargeOfTheSameItem()
    {
        assertEquals("Amulet of glory", ZulrahGearName.base("Amulet of glory(3)"));
        assertEquals("Ring of dueling", ZulrahGearName.base("Ring of dueling (8)"));
        assertTrue(partialMatch("Amulet of glory(1)", "Amulet of glory(4)"));
    }

    @Test public void variantSuffixesThatAreNotChargesAreKept()
    {
        assertEquals("Magic shortbow (i)", ZulrahGearName.base("Magic shortbow (i)"));
        assertEquals("Trident of the swamp (e)", ZulrahGearName.base("Trident of the swamp (e)"));
        assertEquals("Mystic robe top", ZulrahGearName.base(" Mystic robe top "));
        assertFalse(partialMatch("Magic shortbow", "Magic shortbow (i)"));
        assertFalse(partialMatch("Uncharged trident", "Trident of the Seas (full)"));
        assertEquals("", ZulrahGearName.base(null));
    }
}
