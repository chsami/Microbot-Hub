package net.runelite.client.plugins.microbot.drodriftnet;

import net.runelite.api.NullObjectID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DroDriftNetTest
{
    @Test
    public void testDriftNetStatusOf()
    {
        assertEquals(DriftNetStatus.UNSET, DriftNetStatus.of(0));
        assertEquals(DriftNetStatus.SET, DriftNetStatus.of(1));
        assertEquals(DriftNetStatus.CATCH, DriftNetStatus.of(2));
        assertEquals(DriftNetStatus.FULL, DriftNetStatus.of(3));
        assertNull(DriftNetStatus.of(4));
    }

    @Test
    public void testDriftNetTargetingPredicates()
    {
        assertTrue(DriftNetTargeting.acceptsFish(DriftNetStatus.SET));
        assertTrue(DriftNetTargeting.acceptsFish(DriftNetStatus.CATCH));
        assertFalse(DriftNetTargeting.acceptsFish(DriftNetStatus.UNSET));
        assertFalse(DriftNetTargeting.acceptsFish(DriftNetStatus.FULL));

        assertTrue(DriftNetTargeting.shouldEnableRun(false, 35));
        assertFalse(DriftNetTargeting.shouldEnableRun(false, 30));
        assertFalse(DriftNetTargeting.shouldEnableRun(true, 50));
    }

    @Test
    public void testDriftNetTargetingAlley()
    {
        Set<WorldPoint> accepting = Set.of(
                new WorldPoint(3737, 10300, 1),
                new WorldPoint(3737, 10290, 1)
        );
        assertEquals(0, DriftNetTargeting.alley(new WorldPoint(3742, 10301, 1), accepting));
        assertEquals(1, DriftNetTargeting.alley(new WorldPoint(3742, 10289, 1), accepting));
    }

    @Test
    public void testDriftTravelAtFishingEntrance()
    {
        assertTrue(DroDriftTravel.atFishingEntrance(new WorldPoint(3729, 10294, 1)));
        assertTrue(DroDriftTravel.atFishingEntrance(new WorldPoint(3738, 10288, 1)));
        assertFalse(DroDriftTravel.atFishingEntrance(new WorldPoint(3764, 3900, 0)));
        assertFalse(DroDriftTravel.atFishingEntrance(null));
    }

    @Test
    public void testScriptDriftEntryOption()
    {
        assertTrue(DroDriftNetScript.isDriftEntryOption("Enter instance.", false));
        assertTrue(DroDriftNetScript.isDriftEntryOption("Pay 200 numulites", false));
        assertFalse(DroDriftNetScript.isDriftEntryOption("Pay permanent 20,000 numulites", false));
        assertTrue(DroDriftNetScript.isDriftEntryOption("I'll steer clear, thanks.", true));
    }

    @Test
    public void testScriptDriftAmountPrompt()
    {
        assertTrue(DroDriftNetScript.isDriftAmountPrompt("Enter amount:", null));
        assertTrue(DroDriftNetScript.isDriftAmountPrompt(null, "How many?"));
        assertFalse(DroDriftNetScript.isDriftAmountPrompt("Search", "Take"));
    }

    @Test
    public void testScriptWithdrawalAmount()
    {
        // weight=20 (23-20=3), slots=10, stored=100 -> min(19, min(3, 10, 100)) = 3
        assertEquals(3, DroDriftNetScript.driftWithdrawalAmount(20, 10, 100));
        // weight=25 (too heavy: 23-25=-2) -> 0
        assertEquals(0, DroDriftNetScript.driftWithdrawalAmount(25, 10, 100));
        // slots=2 -> 2
        assertEquals(2, DroDriftNetScript.driftWithdrawalAmount(10, 2, 100));
    }

    @Test
    public void testScriptDriftSetupAction()
    {
        assertEquals("Set-up", DroDriftNetScript.driftSetupAction(new String[]{"Examine", "Set-up"}));
        assertEquals("Setup", DroDriftNetScript.driftSetupAction(new String[]{"Setup"}));
        assertNull(DroDriftNetScript.driftSetupAction(new String[]{"Check", "Take"}));
        assertNull(DroDriftNetScript.driftSetupAction(null));
    }

    @Test
    public void testDriftNetProperties()
    {
        DriftNet net = new DriftNet(NullObjectID.NULL_31433, Varbits.NORTH_NET_STATUS, Varbits.NORTH_NET_CATCH_COUNT,
                Collections.emptySet());
        net.setStatus(DriftNetStatus.SET);
        net.setPrevTickStatus(DriftNetStatus.SET);
        assertFalse(net.isNotAcceptingFish());

        net.setStatus(DriftNetStatus.FULL);
        assertTrue(net.isNotAcceptingFish());

        net.setStatus(DriftNetStatus.UNSET);
        assertEquals("", net.getFormattedCountText());

        net.setStatus(DriftNetStatus.CATCH);
        net.setCount(5);
        assertEquals("5/10", net.getFormattedCountText());
    }
}
