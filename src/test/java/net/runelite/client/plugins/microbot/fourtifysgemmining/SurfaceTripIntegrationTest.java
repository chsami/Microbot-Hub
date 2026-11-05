package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.util.Arrays;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SurfaceTripIntegrationTest
{
    @Test
    public void surfaceRoundTripBanksThenRequiresObservedMineArrival() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        assertEquals(1, fixture.walking.destinations.size());
        for (int i = 0; i < 10; i++)
        {
            fixture.tick();
        }
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.world.requests.isEmpty());
        arriveAtBank(fixture);
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
        depositAndClose(fixture);
        assertEquals(2, fixture.walking.destinations.size());
        assertEquals(new WorldPoint(2825, 3000, 0), fixture.walking.destinations.get(1));
        int requests = fixture.world.requests.size();
        fixture.tick();
        assertEquals(requests, fixture.world.requests.size());
        assertEquals(0, fixture.plugin.getGemsMined());
        fixture.world.position = new WorldPoint(2816, 3000, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Mine", fixture.world.requests.get(requests)[5]);
    }

    @Test
    public void bankInRangeStopsWalkingWithoutRequiringAdjacency() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.position = new WorldPoint(2852, 2966, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2852, 2951, 0));
        fixture.tick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
        assertEquals(1, fixture.walking.destinations.size());
    }

    @Test
    public void normalWalkerDetoursDoNotAbortTheTripOrResumeMining() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.position = new WorldPoint(2840, 2980, 1);
        fixture.tick();
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.tick();
        assertTrue(fixture.walking.active);
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals("Walking to Shilo Village bank", fixture.plugin.getStatus());
        arriveAtBank(fixture);
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void undergroundTravelWalksToTheDepositChestWhenFull() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture underground = new PluginTestFixture(config);
        underground.world.position = new WorldPoint(2840, 9385, 0);
        underground.world.fullInventory();
        underground.enableAndDrain();
        underground.tick();
        assertEquals(GemMine.UNDERGROUND.getBankDestination(), underground.walking.destinations.get(0));
        assertTrue(underground.world.requests.isEmpty());
    }

    @Test
    public void undergroundRockAtTwentyOneTilesTriggersWalkingThenInteractsAtTwenty() throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = GemMine.UNDERGROUND;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2818, 9380, 0);
        fixture.world.rock(2839, 9380);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2839, 9380, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.position = new WorldPoint(2819, 9380, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void depletedMinesWaitBeyondRespawnTimeAndMineARealReplacement() throws Exception
    {
        for (GemMine mine : GemMine.values())
        {
            PluginTestFixture fixture = emptyMine(mine);
            WorldPoint position = fixture.world.position;
            WorldPoint depleted = new WorldPoint(position.getX() + 1, position.getY(), 0);
            fixture.world.object(11390, "Rocks", new String[] {"Mine"}, depleted);
            fixture.world.object(11391, "Rocks", new String[] {"Mine"},
                new WorldPoint(position.getX() + 2, position.getY(), 0));
            fixture.enableAndDrain();
            for (int tick = 0; tick < 105; tick++)
            {
                fixture.tick();
            }
            assertEquals("Waiting for gem rocks", fixture.plugin.getStatus());
            assertTrue(fixture.walking.destinations.isEmpty());
            assertFalse(fixture.walking.active);
            assertTrue(fixture.world.requests.isEmpty());

            fixture.world.objects.clear();
            fixture.world.object(mine == GemMine.SURFACE ? 11380 : 11381,
                "Gem rocks", new String[] {"Mine"}, depleted);
            fixture.tick();
            fixture.tick();
            assertEquals(1, fixture.world.requests.size());
            assertEquals("Mine", fixture.world.requests.get(0)[5]);
            assertTrue(fixture.walking.destinations.isEmpty());
        }
    }

    @Test
    public void unavailableMineSceneOrAnchorWaitsWithoutClaimingDepletion() throws Exception
    {
        for (GemMine mine : GemMine.values())
        {
            PluginTestFixture fixture = emptyMine(mine);
            fixture.world.scenePresent = false;
            fixture.enableAndDrain();
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());
            assertTrue(fixture.walking.destinations.isEmpty());

            fixture.world.scenePresent = true;
            fixture.world.playerTilePresent = false;
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());
            fixture.world.playerTilePresent = true;
            WorldPoint position = fixture.world.position;
            fixture.world.playerTileLocationOverride = new WorldPoint(position.getX(), position.getY() + 1, 0);
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());
            fixture.world.playerTileLocationOverride = null;
            fixture.world.mineAnchorPresent = false;
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());

            fixture.world.mineAnchorPresent = true;
            WorldPoint anchor = mine.getMineDestination();
            fixture.world.mineAnchorLocationOverride = new WorldPoint(anchor.getX() + 1, anchor.getY(), 0);
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());
            fixture.world.mineAnchorLocationOverride = new WorldPoint(anchor.getX(), anchor.getY(), 1);
            fixture.tick();
            assertEquals("Waiting for scene", fixture.plugin.getStatus());

            fixture.world.mineAnchorLocationOverride = null;
            fixture.tick();
            assertEquals("Waiting for gem rocks", fixture.plugin.getStatus());
            assertTrue(fixture.walking.destinations.isEmpty());
            assertTrue(fixture.world.requests.isEmpty());
        }
    }

    @Test
    public void unavailableScenePausesAndResumesTravelOutsideMine() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.world.scenePresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertTrue(fixture.walking.destinations.isEmpty());

        fixture.world.scenePresent = true;
        fixture.world.playerTilePresent = false;
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertTrue(fixture.walking.destinations.isEmpty());
        fixture.world.playerTilePresent = true;
        fixture.tick();
        assertEquals(GemMine.SURFACE.getMineDestination(), fixture.walking.destinations.get(0));
        assertTrue(fixture.walking.active);

        fixture.world.scenePresent = false;
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertFalse(fixture.walking.active);
        fixture.world.scenePresent = true;
        fixture.tick();
        assertEquals(GemMine.SURFACE.getMineDestination(), fixture.walking.destinations.get(1));
        assertTrue(fixture.walking.active);
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void activeRockWaitsForThePlayersSceneTileBeforeMining() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.playerTilePresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());
        assertTrue(fixture.walking.destinations.isEmpty());

        fixture.world.playerTilePresent = true;
        WorldPoint position = fixture.world.position;
        fixture.world.playerTileLocationOverride = new WorldPoint(position.getX() + 1, position.getY(), 0);
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.playerTileLocationOverride = null;
        fixture.tick();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
        assertTrue(fixture.walking.destinations.isEmpty());
    }

    @Test
    public void playerAtMineAnchorStillRequiresThePlayerSceneTile() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = GemMine.SURFACE.getMineDestination();
        fixture.world.objects.clear();
        fixture.world.rock(2826, 3000);
        fixture.world.playerTilePresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.playerTilePresent = true;
        fixture.tick();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
        assertTrue(fixture.walking.destinations.isEmpty());
    }

    @Test
    public void fullInventoryStillBanksWhenPlayerSceneTileIsUnavailable() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2840, 2951, 0);
        fixture.world.banker(7, new WorldPoint(2852, 2951, 0));
        fixture.world.fullInventory();
        fixture.world.playerTilePresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
        assertTrue(fixture.walking.destinations.isEmpty());
    }

    @Test
    public void reachingAnOccupiedRockStopsTravelUntilItBecomesAvailable() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2809, 3000, 0);
        fixture.enableAndDrain();
        fixture.tick();
        assertTrue(fixture.walking.active);

        fixture.world.position = new WorldPoint(2824, 3000, 0);
        fixture.world.rock(2825, 3000);
        fixture.world.otherPlayer(new WorldPoint(2826, 3000, 0));
        fixture.tick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Waiting for an unoccupied gem rock", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());

        fixture.world.players.clear();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void fullGemInventoryInterruptsWaitingToTravelToBank() throws Exception
    {
        for (GemMine mine : GemMine.values())
        {
            PluginTestFixture fixture = emptyMine(mine);
            fixture.enableAndDrain();
            fixture.tick();
            assertEquals("Waiting for gem rocks", fixture.plugin.getStatus());
            fixture.world.fullInventory();
            fixture.tick();
            assertEquals(mine.getBankDestination(), fixture.walking.destinations.get(0));
            assertTrue(fixture.walking.active);
            assertTrue(fixture.world.requests.isEmpty());
        }
    }

    @Test
    public void arrivingAtADepletedMineCancelsTheActiveRoute() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.objects.clear();
        fixture.world.position = new WorldPoint(2809, 3000, 0);
        fixture.enableAndDrain();
        fixture.tick();
        assertTrue(fixture.walking.active);

        fixture.world.position = new WorldPoint(2824, 3000, 0);
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Waiting for gem rocks", fixture.plugin.getStatus());
        for (int tick = 0; tick < 10; tick++)
        {
            fixture.tick();
        }
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void surfaceRockInRangeOutsideTheMineAreaDoesNotStartWalking() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2815, 3000, 0);
        fixture.enableAndDrain();
        fixture.tick();
        assertTrue(fixture.walking.destinations.isEmpty());
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.tick();
        assertEquals(1, fixture.plugin.getGemsMined());
    }

    @Test
    public void aLatchedCantReachFlagStillSendsTheRockClickWithoutAWalk() throws Exception
    {
        boolean detection = Microbot.isCantReachTargetDetectionEnabled;
        boolean latched = Microbot.cantReachTarget;
        Microbot.isCantReachTargetDetectionEnabled = true;
        Microbot.cantReachTarget = true;
        try
        {
            PluginTestFixture fixture = new PluginTestFixture();
            fixture.world.position = new WorldPoint(2815, 3000, 0);
            fixture.enableAndDrain();
            fixture.tick();
            assertTrue(fixture.walking.destinations.isEmpty());
            assertEquals("Mine", fixture.world.requests.get(0)[5]);
            assertTrue(Microbot.cantReachTarget, "a recovery walk would have cleared the flag");
        }
        finally
        {
            Microbot.isCantReachTargetDetectionEnabled = detection;
            Microbot.cantReachTarget = latched;
        }
    }

    @Test
    public void surfaceBankerAtThirteenTilesTriggersWalkingAndStopsAtTwelve() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(2839, 2951, 0);
        fixture.world.banker(7, new WorldPoint(2852, 2951, 0));
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2852, 2951, 0), fixture.walking.destinations.get(0));
        fixture.world.position = new WorldPoint(2840, 2951, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void initialSurfaceTravelWalksToMineAndWaitsForObservedArrival() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2825, 3000, 0), fixture.walking.destinations.get(0));
        assertEquals("Walking to the surface gem mine", fixture.plugin.getStatus());
        fixture.tick();
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.position = new WorldPoint(2825, 3000, 0);
        fixture.tick();
        fixture.tick();
        assertFalse(fixture.walking.active);
        assertEquals("Mine", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void fullGemInventoryStartsWithTheBankEvenFromADistantLocation() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(new WorldPoint(2852, 2954, 0), fixture.walking.destinations.get(0));
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void missingPrerequisitesPreventInitialWebwalking() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.world.level = 1;
        fixture.enableAndDrain();
        fixture.tick();
        assertTrue(fixture.walking.destinations.isEmpty());
        assertTrue(fixture.world.requests.isEmpty());
        assertTrue(fixture.plugin.getStatus().contains("40"));
    }

    @Test
    public void fullInventoryWithoutGemsDoesNotStartATrip() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = new Item[28];
        Arrays.fill(fixture.world.inventory, new Item(1265, 1));
        fixture.enableAndDrain();
        fixture.tick();
        assertTrue(fixture.walking.destinations.isEmpty());
        assertTrue(fixture.plugin.getStatus().contains("other items"));
    }

    @Test
    public void shutdownCancelsWalkingBeforeDeferredCleanup() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.disable();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals(1, fixture.walking.destinations.size());
        fixture.thread.drain();
    }

    @Test
    public void shutdownDisablesDecisionsBeforeCancellingWalking() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.walking.afterCancel = fixture::tick;
        fixture.disable();
        assertFalse(fixture.walking.active);
        assertEquals(1, fixture.walking.destinations.size());
        fixture.thread.drain();
    }

    @Test
    public void mineConfigurationCancelsWalkingImmediately() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        ((PluginTestFixture.MutableConfig) fixture.config).mine = GemMine.UNDERGROUND;
        ConfigChanged event = new ConfigChanged();
        event.setGroup(GemMiningConfig.GROUP);
        event.setKey("mine");
        fixture.plugin.onConfigChanged(event);
        assertFalse(fixture.walking.active);
        fixture.thread.drain();
        fixture.tick();
        assertEquals(2, fixture.walking.destinations.size());
        assertEquals(GemMine.UNDERGROUND.getBankDestination(), fixture.walking.destinations.get(1));
    }

    @Test
    public void loadingPreservesOutboundTripAndReturnIntent() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        loading(fixture);
        fixture.world.position = new WorldPoint(2840, 2985, 0);
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals(2, fixture.walking.destinations.size());
        assertEquals(fixture.walking.destinations.get(0), fixture.walking.destinations.get(1));
        arriveAtBank(fixture);
        depositAndClose(fixture);
        loading(fixture);
        fixture.world.position = new WorldPoint(2840, 2975, 0);
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals(new WorldPoint(2825, 3000, 0), fixture.walking.destinations.get(3));
        assertTrue(fixture.walking.active);
    }

    @Test
    public void loadingDuringPartialDepositRetainsTheBankLatch() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        arriveAtBank(fixture);
        fixture.world.bankWithGems();
        fixture.tick();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.world.widgets.clear();
        fixture.world.roots = new Widget[0];
        loading(fixture);
        fixture.world.state = GameState.LOGGED_IN;
        int requests = fixture.world.requests.size();
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(requests)[5]);
        assertEquals(1, fixture.walking.destinations.size());
    }

    @Test
    public void loadingAfterThirdBankAttemptCannotAdmitAFourthRetry() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        arriveAtBank(fixture);
        for (int i = 0; i < 16; i++)
        {
            fixture.tick();
        }
        assertEquals(3, fixture.world.requests.size());
        loading(fixture);
        fixture.world.state = GameState.LOGGED_IN;
        for (int i = 0; i < 10; i++)
        {
            fixture.tick();
        }
        assertEquals(3, fixture.world.requests.size());
        assertTrue(fixture.plugin.getStatus().contains("after three tries"));
    }

    @Test
    public void successfulThirdAttemptObservedAfterLoadingResetsTheNextActionBudget() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        arriveAtBank(fixture);
        for (int i = 0; i < 16; i++)
        {
            fixture.tick();
        }
        assertEquals(3, fixture.world.requests.size());
        fixture.world.bankWithGems();
        loading(fixture);
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals("Deposit-All", fixture.world.requests.get(3)[5]);
        for (int i = 0; i < 8; i++)
        {
            fixture.tick();
        }
        assertEquals("Deposit-All", fixture.world.requests.get(4)[5]);
        assertFalse(fixture.plugin.getStatus().contains("after three tries"));
    }

    @Test
    public void arrivingBeforeBankTargetsAppearWaitsForTheScene() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        fixture.tick();
        assertEquals("Waiting for Shilo Village bank", fixture.plugin.getStatus());
        fixture.tick();
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
        fixture.tick();
        assertEquals("Bank", fixture.world.requests.get(0)[5]);
    }

    @Test
    public void missingBankTargetsHaveABoundedWaitAfterArrival() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        for (int i = 0; i < 10; i++)
        {
            fixture.tick();
        }
        assertTrue(fixture.plugin.getStatus().contains("Found no banker or bank booth"));
        assertTrue(fixture.world.requests.isEmpty());
        assertFalse(fixture.walking.active);
    }

    @Test
    public void failedWalkDoesNotRetryForeverOrIssueBankActions() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.walking.fail = true;
        fixture.tick();
        assertTrue(fixture.plugin.getStatus().startsWith("Could not walk to Shilo Village bank: "), fixture.plugin.getStatus());
        assertTrue(fixture.plugin.getStatus().endsWith("Walk closer, then disable and enable to retry"));
        assertFalse(fixture.walking.active);
        for (int i = 0; i < 10; i++)
        {
            fixture.tick();
        }
        assertEquals(1, fixture.walking.destinations.size());
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void deathAndForeignWorldViewCancelBeforeClientDispatch() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.dead = true;
        fixture.clientTick();
        assertFalse(fixture.walking.active);
        fixture.tick();
        assertEquals("Player is dead", fixture.plugin.getStatus());
        PluginTestFixture foreign = surfaceTrip();
        foreign.world.topLevel = false;
        foreign.clientTick();
        assertFalse(foreign.walking.active);
        foreign.tick();
        assertTrue(foreign.plugin.getStatus().contains("Step off the boat"));
    }

    @Test
    public void logoutClearsOldTripAndLoginSelectsTheCurrentDestination() throws Exception
    {
        PluginTestFixture fixture = surfaceTrip();
        fixture.world.state = GameState.LOGIN_SCREEN;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        fixture.plugin.onGameStateChanged(event);
        assertFalse(fixture.walking.active);
        fixture.world.state = GameState.LOGGED_IN;
        fixture.world.position = new WorldPoint(3222, 3218, 0);
        fixture.tick();
        assertEquals(2, fixture.walking.destinations.size());
        assertEquals(new WorldPoint(2852, 2954, 0), fixture.walking.destinations.get(1));
    }

    private static PluginTestFixture surfaceTrip() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.fullInventory();
        fixture.enableAndDrain();
        fixture.tick();
        return fixture;
    }

    private static PluginTestFixture emptyMine(GemMine mine) throws Exception
    {
        PluginTestFixture.MutableConfig config = new PluginTestFixture.MutableConfig();
        config.mine = mine;
        PluginTestFixture fixture = new PluginTestFixture(config);
        fixture.world.objects.clear();
        fixture.world.position = mine == GemMine.SURFACE
            ? new WorldPoint(2824, 3000, 0) : new WorldPoint(2838, 9385, 0);
        return fixture;
    }

    private static void arriveAtBank(PluginTestFixture fixture)
    {
        fixture.world.position = new WorldPoint(2852, 2954, 0);
        fixture.world.object(2213, "Bank booth", new String[] {"Bank"}, new WorldPoint(2853, 2953, 0));
        fixture.tick();
        fixture.tick();
    }

    private static void depositAndClose(PluginTestFixture fixture)
    {
        fixture.world.bankWithGems();
        fixture.tick();
        fixture.world.inventory = new Item[] {new Item(1265, 1)};
        fixture.world.bankWithGems();
        fixture.tick();
        fixture.world.widgets.clear();
        fixture.world.roots = new Widget[0];
        fixture.tick();
    }

    private static void loading(PluginTestFixture fixture)
    {
        fixture.world.state = GameState.LOADING;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOADING);
        fixture.plugin.onGameStateChanged(event);
        assertFalse(fixture.walking.active);
    }
}
