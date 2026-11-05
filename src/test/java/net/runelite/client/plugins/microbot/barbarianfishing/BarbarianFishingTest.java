package net.runelite.client.plugins.microbot.barbarianfishing;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BarbarianFishingTest
{
    @Test
    public void testIsLeapingFish()
    {
        assertTrue(BarbarianFishingScript.isLeapingFish("Leaping trout"));
        assertTrue(BarbarianFishingScript.isLeapingFish("leaping trout"));
        assertTrue(BarbarianFishingScript.isLeapingFish("LEAPING SALMON"));
        assertTrue(BarbarianFishingScript.isLeapingFish("Leaping sturgeon"));

        assertFalse(BarbarianFishingScript.isLeapingFish(null));
        assertFalse(BarbarianFishingScript.isLeapingFish("Raw trout"));
        assertFalse(BarbarianFishingScript.isLeapingFish("Shark"));
        assertFalse(BarbarianFishingScript.isLeapingFish("Feather"));
    }

    @Test
    public void testLocations()
    {
        assertEquals(10038, BarbarianFishingScript.Location.OTTOS_GROTTO.getRegionId());
        assertEquals("Otto's Grotto", BarbarianFishingScript.Location.OTTOS_GROTTO.getDisplayName());

        assertEquals(4919, BarbarianFishingScript.Location.MOUNT_QUIDAMORTEM.getRegionId());
        assertEquals("Mount Quidamortem (CoX)", BarbarianFishingScript.Location.MOUNT_QUIDAMORTEM.getDisplayName());

        assertEquals(-1, BarbarianFishingScript.Location.OTHER.getRegionId());
    }

    @Test
    public void testMountQuidamortemContains()
    {
        // Null point
        assertFalse(MountQuidamortem.contains(null));

        // Wrong plane
        assertFalse(MountQuidamortem.contains(new WorldPoint(1254, 3571, 1)));

        // CoX bank area
        assertTrue(MountQuidamortem.contains(new WorldPoint(1254, 3571, 0)));
        assertTrue(MountQuidamortem.contains(MountQuidamortem.BANK_CHEST_TILE));
        assertTrue(MountQuidamortem.contains(MountQuidamortem.RECOVERY_CENTER));

        // Outside area
        assertFalse(MountQuidamortem.contains(new WorldPoint(3200, 3200, 0)));
    }

    @Test
    public void testMountQuidamortemIsAtBank()
    {
        assertFalse(MountQuidamortem.isAtBank(null));
        assertTrue(MountQuidamortem.isAtBank(MountQuidamortem.BANK_FRONT_TILE));
        assertTrue(MountQuidamortem.isAtBank(new WorldPoint(1254, 3570, 0)));
        assertTrue(MountQuidamortem.isAtBank(new WorldPoint(1255, 3572, 0)));

        // Far away from bank
        assertFalse(MountQuidamortem.isAtBank(MountQuidamortem.RECOVERY_CENTER));
    }

    @Test
    public void testBankApproach()
    {
        MountQuidamortem.BankApproach approach = MountQuidamortem.randomBankApproach();
        assertNotNull(approach);
        assertNotNull(approach.getTarget());
        assertEquals(0, approach.getTarget().getPlane());
        assertTrue(approach.getArrivalRadius() >= 0);
    }

    @Test
    public void testBankChestId()
    {
        assertEquals(30087, MountQuidamortem.BANK_CHEST_ID);
    }

    @Test
    public void testRandomReturnAnchor()
    {
        WorldPoint anchor = MountQuidamortem.randomReturnAnchor();
        assertNotNull(anchor);
        assertEquals(0, anchor.getPlane());
        assertTrue(MountQuidamortem.contains(anchor));
    }

    @Test
    public void testCoxTravelEnum()
    {
        assertEquals(5, BarbarianFishingScript.CoxTravel.values().length);
        assertEquals(BarbarianFishingScript.CoxTravel.NONE, BarbarianFishingScript.CoxTravel.valueOf("NONE"));
    }
}
