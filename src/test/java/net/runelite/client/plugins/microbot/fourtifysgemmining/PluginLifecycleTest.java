package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.awt.event.KeyEvent;
import java.time.Instant;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.overlay.GemMiningOverlay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginLifecycleTest
{
    @Test
    public void pausedScriptsQueueNothingUntilResumed() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Microbot.pauseAllScripts.set(true);
        try
        {
            fixture.tick();
            fixture.tick();
            assertEquals("Paused by Microbot (pause or break)", fixture.plugin.getStatus());
            assertTrue(fixture.world.requests.isEmpty());
        }
        finally
        {
            Microbot.pauseAllScripts.set(false);
        }
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void freeWorldStopsBeforeAnyClick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.members = false;
        fixture.enableAndDrain();
        fixture.tick();
        fixture.tick();
        assertEquals("Shilo Village is members-only. Switch to a members world", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void playerInputCancelsTheQueuedClickAndPauses() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        InputArbiter.resetForTest();
        InputArbiter.onRealKeyPressed(KeyEvent.VK_W);
        try
        {
            fixture.clientTick();
            assertTrue(fixture.world.requests.isEmpty(), "A held key must keep the queued click from dispatching");
            fixture.tick();
            assertEquals("Paused while you use the mouse or keyboard", fixture.plugin.getStatus());
            assertTrue(fixture.world.requests.isEmpty());
        }
        finally
        {
            InputArbiter.resetForTest();
        }
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void depletedRockWaitsForConfiguredDelayAfterLongApproach() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        PluginTestFixture.MutableConfig config = (PluginTestFixture.MutableConfig) fixture.config;
        config.minDelayTicks = 3;
        config.maxDelayTicks = 3;
        fixture.world.position = new WorldPoint(2820, 3000, 0);
        fixture.world.rock(2827, 3000);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        for (int x = 2821; x <= 2824; x++)
        {
            fixture.world.position = new WorldPoint(x, 3000, 0);
            fixture.tick();
        }
        fixture.world.animation = 625;
        fixture.tick();
        fixture.world.objects.remove(0);
        fixture.world.animation = -1;
        fixture.tick();
        assertEquals(1, fixture.world.requests.size(), "No next rock on the completion tick");
        for (int i = 0; i < 2; i++)
        {
            fixture.tick();
            assertEquals(1, fixture.world.requests.size(), "Wait for each configured idle tick");
        }
        fixture.tick();
        assertEquals(2, fixture.world.requests.size());
    }

    @Test
    public void observedGemStartsDelayEvenWhenMiningAnimationContinues() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        PluginTestFixture.MutableConfig config = (PluginTestFixture.MutableConfig) fixture.config;
        config.minDelayTicks = 2;
        config.maxDelayTicks = 2;
        fixture.enableAndDrain();
        fixture.tick();
        fixture.world.animation = 625;
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.tick();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        fixture.world.animation = -1;
        fixture.tick();
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        fixture.tick();
        assertEquals(2, fixture.world.requests.size());
    }

    @Test
    public void enablingLoggedOutWaitsForLoginBeforeStartingRuntimeAndOverlay() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.state = GameState.LOGIN_SCREEN;
        fixture.enableAndDrain();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void loggedInWithoutLocalPlayerStillWaits() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.playerPresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertFalse(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void disableImmediatelyStopsQueuedRequestsBeforeQueuedCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.disable();
        fixture.clientTick();
        fixture.tick();
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals(1, fixture.thread.pending.size());
        fixture.thread.drain();
        fixture.world.actions.dispatch();
        assertTrue(fixture.world.requests.isEmpty());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
    }

    @Test
    public void queuedStartupCannotActAfterDisable() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enable();
        fixture.disable();
        fixture.thread.drain();
        fixture.tick();
        assertTrue(fixture.world.requests.isEmpty());
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Stopped", fixture.plugin.getStatus());
        assertFalse(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
    }

    @Test
    public void rapidReenableRejectsOldCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.disable();
        fixture.enable();
        fixture.plugin.setStatus("New enable");
        fixture.thread.runNext();
        assertEquals("New enable", fixture.plugin.getStatus());
        fixture.thread.runNext();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlays.anyMatch(overlay -> overlay instanceof GemMiningOverlay));
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void logoutCancelsRequestsAndLoginUsesFreshInventoryBaseline() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Instant start = fixture.plugin.getStartTime();
        fixture.decisionOnly();
        fixture.world.state = GameState.LOGIN_SCREEN;
        stateChanged(fixture, GameState.LOGIN_SCREEN);
        fixture.clientTick();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 10)};
        for (int i = 0; i < 20; i++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertSame(start, fixture.plugin.getStartTime());
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
        assertEquals(0, fixture.plugin.getGemsMined());
        assertSame(start, fixture.plugin.getStartTime());
    }

    @Test
    public void loadingSceneCancelsQueuedRequests() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.state = GameState.LOADING;
        stateChanged(fixture, GameState.LOADING);
        fixture.clientTick();
        fixture.world.state = GameState.LOGGED_IN;
        fixture.world.actions.dispatch();
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
    }

    @Test
    public void changedDestinationClearsOldMiningAndRequiresObservedArrival() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        ((PluginTestFixture.MutableConfig) fixture.config).mine = GemMine.UNDERGROUND;
        ConfigChanged event = new ConfigChanged();
        event.setGroup(GemMiningConfig.GROUP);
        event.setKey("mine");
        fixture.plugin.onConfigChanged(event);
        fixture.thread.drain();
        fixture.clientTick();
        for (int i = 0; i < 20; i++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertEquals("Walking to the underground gem mine", fixture.plugin.getStatus());
        assertFalse(fixture.plugin.getActiveMineName().contains("destination"), "The overlay names the mine only");
    }

    @Test
    public void changedDestinationBlocksDispatchBeforeQueuedConfigCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        ((PluginTestFixture.MutableConfig) fixture.config).mine = GemMine.UNDERGROUND;
        ConfigChanged event = new ConfigChanged();
        event.setGroup(GemMiningConfig.GROUP);
        event.setKey("mine");
        fixture.plugin.onConfigChanged(event);
        assertEquals(1, fixture.thread.pending.size());
        fixture.clientTick();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.thread.drain();
        fixture.tick();
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void movementAndForeignWorldViewCannotDispatch() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.position = new WorldPoint(2825, 3000, 0);
        fixture.clientTick();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.tick();
        assertEquals("Waiting for movement to finish", fixture.plugin.getStatus());
        fixture.world.topLevel = false;
        for (int i = 0; i < 20; i++)
        {
            fixture.tick();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertTrue(fixture.plugin.getStatus().contains("Step off the boat"));
    }

    @Test
    public void deathBeforeDispatchRejectsThePendingRequest() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.dead = true;
        fixture.clientTick();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.tick();
        assertEquals("Player is dead", fixture.plugin.getStatus());
        assertTrue(fixture.world.requests.isEmpty());
    }

    @Test
    public void requestsDispatchOnlyBeforeTheTwentyNinthClientCycle() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.gameCycle += 29;
        fixture.clientTick();
        assertTrue(fixture.world.requests.isEmpty());
        fixture.world.gameCycle--;
        fixture.clientTick();
        assertEquals(1, fixture.world.requests.size());
        fixture.clientTick();
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void nextGameTickExpiresOldRequestAndCanAdmitFreshDecision() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        for (int i = 0; i < 40; i++)
        {
            fixture.decisionOnly();
        }
        assertTrue(fixture.world.requests.isEmpty());
        assertFalse(fixture.plugin.getStatus().contains("three attempts"));
        fixture.clientTick();
        assertEquals(1, fixture.world.requests.size());
        fixture.tick();
        assertEquals(1, fixture.world.requests.size());
    }

    @Test
    public void inventoryIncreaseDuringMiningCountsButBankTransferDoesNot() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        fixture.tick();
        assertEquals(1, fixture.plugin.getGemsMined());
        fixture.world.bankWithGems();
        fixture.world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 10)};
        fixture.tick();
        assertEquals(1, fixture.plugin.getGemsMined());
    }

    private static void stateChanged(PluginTestFixture fixture, GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        fixture.plugin.onGameStateChanged(event);
    }
}
