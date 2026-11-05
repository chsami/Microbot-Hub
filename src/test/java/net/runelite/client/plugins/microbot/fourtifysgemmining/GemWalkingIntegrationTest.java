package net.runelite.client.plugins.microbot.fourtifysgemmining;

import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GemWalkingIntegrationTest
{
    @Test
    public void mineTravelWaitsForObservedRangeThenUsesTheNormalMiningAction() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2804, 3000, 0);
        fixture.enableAndDrain();
        for (int i = 0; i < 10; i++)
        {
            fixture.tick();
        }
        assertEquals("Walking to the surface gem mine", fixture.plugin.getStatus());
        assertEquals(1, fixture.walking.destinations.size());
        assertEquals(new WorldPoint(2825, 3000, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.position = new WorldPoint(2805, 3000, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertTrue(fixture.world.requests.isEmpty());
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void arrivingAtADepletedMineWaitsAndResumesWhenARockAppears() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2809, 3000, 0);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Walking to the surface gem mine", fixture.plugin.getStatus());
        assertTrue(fixture.walking.active);

        fixture.world.position = new WorldPoint(2824, 3000, 0);
        fixture.tick();
        assertEquals("Waiting for gem rocks", fixture.plugin.getStatus());
        assertFalse(fixture.walking.active);
        for (int tick = 0; tick < 10; tick++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.rock(2825, 3000);
        fixture.tick();
        fixture.tick();
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void distantRockInsideUndergroundMineWalksThenHandsOffAtTwentyTiles() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2818, 9380, 0);
        fixture.world.rock(2839, 9380);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Walking to the underground gem mine", fixture.plugin.getStatus());
        assertEquals(new WorldPoint(2839, 9380, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.position = new WorldPoint(2819, 9380, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void bankTravelWaitsForObservedBankerRangeThenUsesTheNormalBankAction() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2839, 2951, 0);
        fixture.world.banker(7, new WorldPoint(2852, 2951, 0));
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.tick();
        assertEquals("Walking to Shilo Village bank", fixture.plugin.getStatus());
        assertEquals(new WorldPoint(2852, 2951, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.position = new WorldPoint(2840, 2951, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertTrue(fixture.world.requests.isEmpty());
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void undergroundTripWalksToTheSelectedDepositChest() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.position = new WorldPoint(2840, 9385, 0);
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Walking to the bank deposit chest in the underground mine", fixture.plugin.getStatus());
        assertEquals(GemMine.UNDERGROUND.getBankDestination(), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void legacyManualTravelFlagDoesNotSuppressAuthorizedWalking() throws Exception
    {
        String previous = System.getProperty("fourtify.gemMining.manualTravel");
        System.setProperty("fourtify.gemMining.manualTravel", "true");
        try
        {
            PluginTestFixture fixture = new PluginTestFixture();
            fixture.world.position = new WorldPoint(2804, 3000, 0);
            fixture.enableAndDrain();
            fixture.tick();
            assertEquals("Walking to the surface gem mine", fixture.plugin.getStatus());
            assertEquals(1, fixture.walking.destinations.size());
        }
        finally
        {
            if (previous == null)
            {
                System.clearProperty("fourtify.gemMining.manualTravel");
            }
            else
            {
                System.setProperty("fourtify.gemMining.manualTravel", previous);
            }
        }
    }

    @Test
    public void bankObjectAtTwentyOneTilesWalksAndAtTwentyUsesQueuedBankAction() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2852, 2972, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2852, 2951, 0));
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2852, 2951, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.position = new WorldPoint(2852, 2971, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
        assertEquals(1, fixture.walking.destinations.size());
    }

    @Test
    public void loadedRockReplacesFallbackWithoutClaimingArrival() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2804, 3000, 0);
        fixture.world.objects.clear();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(GemMine.SURFACE.getMineDestination(), fixture.walking.destinations.get(0));

        fixture.world.rock(2827, 3000);
        fixture.tick();
        assertEquals(new WorldPoint(2827, 3000, 0), fixture.walking.destinations.get(1));
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.position = new WorldPoint(2807, 3000, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void undergroundRoundTripDepositsThenObservesMineRangeBeforeResuming() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2820, 9383, 0);
        fixture.world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2842, 9383, 0));
        fixture.world.rock(2845, 9395);
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2842, 9383, 0), fixture.walking.destinations.get(0));

        fixture.world.position = new WorldPoint(2822, 9383, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Deposit", fixture.world.requests.get(0)[5]);
        fixture.world.depositBoxWithGems();
        fixture.tick();
        assertEquals("Deposit-All", fixture.world.requests.get(1)[5]);
        fixture.world.inventory = new Item[] {new Item(1265, 1)};
        fixture.world.depositBoxWithGems();
        fixture.tick();
        assertEquals("Close", fixture.world.requests.get(2)[5]);
        assertFalse(fixture.walking.active);
        fixture.world.closeBanking();
        fixture.tick();
        assertEquals(new WorldPoint(2845, 9395, 0), fixture.walking.destinations.get(1));
        assertEquals(0, fixture.plugin.getGemsMined());

        fixture.world.position = new WorldPoint(2825, 9383, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Mine", fixture.world.requests.get(3)[5]);
    }

    @Test
    public void mineHandoffWaitsForAsynchronousTravelCleanupBeforeDispatch() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2804, 3000, 0);
        fixture.enableAndDrain();
        fixture.tick();
        fixture.walking.afterCancel = () -> fixture.walking.cancelling = true;
        fixture.world.position = new WorldPoint(2805, 3000, 0);
        fixture.tick();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.plugin.getStatus().contains("travel to stop"));

        fixture.walking.cancelling = false;
        fixture.tick();
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void bankHandoffWaitsForAsynchronousTravelCleanupBeforeDispatch() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.walking.afterCancel = () -> fixture.walking.cancelling = true;
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
        fixture.tick();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.plugin.getStatus().contains("travel to stop"));

        fixture.walking.cancelling = false;
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void failedTravelCleanupPausesBeforeAnyMineOrBankAction() throws Exception
    {
        for (boolean banking : new boolean[] {false, true})
        {
            PluginTestFixture fixture = new PluginTestFixture();
            fixture.world.position = new WorldPoint(2804, 3000, 0);
            if (banking)
            {
                fixture.world.fullInventory();
            }
            fixture.enableAndDrain();
            fixture.tick();
            fixture.walking.cancellationFailure = "Travel cleanup failed";
            if (banking)
            {
                fixture.world.position = new WorldPoint(2852, 2954, 0);
                fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
            }
            else
            {
                fixture.world.position = new WorldPoint(2805, 3000, 0);
            }
            for (int tick = 0; tick < 3; tick++)
            {
                fixture.tick();
            }
            assertTrue(fixture.world.requests.isEmpty());
            assertTrue(fixture.plugin.getStatus().contains("cleanup"));
            assertTrue(fixture.plugin.getStatus().toLowerCase().contains("disable and enable"));
            assertEquals(1, fixture.walking.destinations.size());
        }
    }

    @Test
    public void changedMineBeforeDeferredCleanupCannotReuseTheOldTripDestination() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        ((PluginTestFixture.MutableConfig) fixture.config).mine = GemMine.UNDERGROUND;
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(GemMiningConfig.GROUP);
        changed.setKey("mine");
        fixture.plugin.onConfigChanged(changed);
        fixture.tick();
        assertEquals(GemMine.UNDERGROUND.getBankDestination(),
            fixture.walking.destinations.get(fixture.walking.destinations.size() - 1));
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals(0, fixture.plugin.getGemsMined());
    }

    @Test
    public void intermediateBankDuringOutboundTravelDoesNotDepositOrReturnToMine() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        WorldPoint selectedBank = fixture.walking.destinations.get(0);
        fixture.world.bankWithGems();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertTrue(fixture.walking.active);
        assertEquals(1, fixture.walking.destinations.size());
        assertEquals(selectedBank, fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals(27, fixture.world.actions.gemCount());
        assertEquals(0, fixture.plugin.getGemsMined());
        assertEquals("Walking to Shilo Village bank", fixture.plugin.getStatus());

        fixture.world.closeBanking();
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
        fixture.tick();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void intermediateBankDuringReturnTravelDoesNotCloseOrRestartBanking() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
        fixture.tick();
        fixture.tick();
        fixture.world.bankWithGems();
        fixture.tick();
        fixture.world.inventory = new Item[] {new Item(1265, 1)};
        fixture.world.bankWithGems();
        fixture.tick();
        fixture.world.closeBanking();
        fixture.tick();
        assertEquals("Close", fixture.world.requests.get(2)[5]);
        assertEquals(2, fixture.walking.destinations.size());

        fixture.world.bankWithGems();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertEquals(3, fixture.world.requests.size());
        assertTrue(fixture.walking.active);
        assertEquals(2, fixture.walking.destinations.size());
        assertEquals(GemMine.SURFACE.getMineDestination(), fixture.walking.destinations.get(1));
        assertEquals("Walking to the surface gem mine", fixture.plugin.getStatus());
        assertEquals(0, fixture.plugin.getGemsMined());

        fixture.world.closeBanking();
        fixture.world.position = new WorldPoint(2824, 3000, 0);
        fixture.tick();
        fixture.tick();
        assertEquals(4, fixture.world.requests.size());
        assertEquals("Mine", fixture.world.requests.get(3)[5]);
    }

}
