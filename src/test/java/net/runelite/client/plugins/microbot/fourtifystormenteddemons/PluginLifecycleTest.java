package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import java.time.Instant;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
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
    public void startingInTheRoomAttacksTheNearestDemonOnTheFirstTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlayShown());
        fixture.tick();
        assertEquals(1, fixture.requests());
        assertEquals("Attacking Tormented Demon", fixture.plugin.getStatus());
    }

    @Test
    public void enablingLoggedOutWaitsForLoginBeforeStartingRuntimeAndOverlay() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.state = GameState.LOGIN_SCREEN;
        fixture.enableAndDrain();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlayShown());
        assertEquals(1, fixture.requests());
    }

    @Test
    public void loggedInWithoutALocalPlayerStillWaits() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.playerPresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertFalse(fixture.overlayShown());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void disableImmediatelyStopsQueuedRequestsBeforeQueuedCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertEquals(0, fixture.requests());
        fixture.disable();
        fixture.clientTick();
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertEquals(1, fixture.thread.pending.size());
        fixture.thread.drain();
        fixture.actions.dispatch();
        assertEquals(0, fixture.requests());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        assertEquals("Stopped", fixture.plugin.getStatus());
    }

    @Test
    public void queuedStartupCannotActAfterDisable() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enable();
        fixture.disable();
        fixture.thread.drain();
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Stopped", fixture.plugin.getStatus());
        assertFalse(fixture.overlayShown());
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
        assertTrue(fixture.overlayShown());
        fixture.tick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void logoutCancelsQueuedRequestsAndLoginResumesTheSameSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Instant start = fixture.plugin.getStartTime();
        fixture.decisionOnly();
        fixture.world.state = GameState.LOGIN_SCREEN;
        stateChanged(fixture, GameState.LOGIN_SCREEN);
        fixture.clientTick();
        for (int index = 0; index < 5; index++)
        {
            fixture.tick();
        }
        assertEquals(0, fixture.requests());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertSame(start, fixture.plugin.getStartTime());
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals(1, fixture.requests());
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
        fixture.actions.dispatch();
        assertEquals(0, fixture.requests());
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
    }

    @Test
    public void stateChangesThatKeepTheLoginDoNotCancelQueuedRequests() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        stateChanged(fixture, GameState.LOGGED_IN);
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void changedSettingsBlockDispatchBeforeQueuedRestartThenRestartTheSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        configChanged(fixture, "room");
        assertEquals(1, fixture.thread.pending.size());
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.thread.drain();
        fixture.tick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void showingOrHidingTheOverlayKeepsTheSessionAndItsCounters() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Instant started = fixture.plugin.getStartTime();
        fixture.decisionOnly();
        configChanged(fixture, "showOverlay");
        assertTrue(fixture.thread.pending.isEmpty());
        assertSame(started, fixture.plugin.getStartTime());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void settingsOfOtherGroupsAreIgnored() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        ConfigChanged event = new ConfigChanged();
        event.setGroup("someOtherPlugin");
        event.setKey("room");
        fixture.plugin.onConfigChanged(event);
        assertTrue(fixture.thread.pending.isEmpty());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void movementAndForeignWorldViewCannotDispatch() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.standAt(new WorldPoint(4134, 4382, 0));
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.world.topLevel = false;
        for (int index = 0; index < 5; index++)
        {
            fixture.tick();
        }
        assertEquals(0, fixture.requests());
    }

    @Test
    public void deathBeforeDispatchRejectsThePendingRequestAndStopsTheSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.dead = true;
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.tick();
        assertEquals("Player died; disable and recover before restarting", fixture.plugin.getStatus());
        fixture.world.dead = false;
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertEquals("Player died; disable and recover before restarting", fixture.plugin.getStatus());
    }

    @Test
    public void requestsDispatchOnlyBeforeTheTwentyNinthClientCycle() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertEquals(0, fixture.requests());
        fixture.world.gameCycle += 29;
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.world.gameCycle--;
        fixture.clientTick();
        assertEquals(1, fixture.requests());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void nextGameTickExpiresOldRequestsAndAdmitsAFreshDecision() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        for (int index = 0; index < 5; index++)
        {
            fixture.decisionOnly();
        }
        assertEquals(0, fixture.requests());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void startingBeforeTheInventoryArrivesWaitsInsteadOfFailing() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventoryPresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting for inventory", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        assertEquals(0, fixture.requests());
        fixture.world.inventoryPresent = true;
        fixture.tick();
        assertNotNull(fixture.plugin.getStartTime());
        assertEquals(1, fixture.requests());
    }

    @Test
    public void aRunningSessionWaitsWhenTheInventoryDisappears() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.inventoryPresent = false;
        fixture.tick();
        assertEquals("Waiting for inventory", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingAwayFromFeroxAndTheTempleFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.standAt(new WorldPoint(3200, 3200, 0));
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Walk to Ferox Enclave or into the Ancient Guthixian Temple, then restart the plugin", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void aBadItemIdNamesTheSettingAndTheFormat() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.melee = "Torva,";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Melee equipment IDs: \"Torva\" is not an item ID; use whole numbers separated by commas, like 4675,9185",
            fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void aTrailingCommaInAnItemIdListIsIgnored() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.magic = PluginTestFixture.STAFF + ",";
        fixture.config.restock = "560:500,";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Attacking Tormented Demon", fixture.plugin.getStatus());
    }

    @Test
    public void aStopIsSentToTheChatAndAsANotificationOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.enableAndDrain();
        fixture.tick();
        fixture.tick();
        String message = "[Tormented Demons] Stopped: Carry or wear a Ring of dueling, then restart the plugin";
        assertEquals(List.of(message), fixture.world.chat);
        assertEquals(List.of(message), fixture.notifications);
    }

    @Test
    public void pausedScriptsStopTheFightUntilResumed() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Microbot.pauseAllScripts.set(true);
        try
        {
            fixture.tick();
            assertEquals("Paused", fixture.plugin.getStatus());
            assertEquals(0, fixture.requests());
        }
        finally
        {
            Microbot.pauseAllScripts.set(false);
        }
        fixture.tick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void startingWithOneEquipmentStyleFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.ranged = "";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Fill in at least two of Magic, Ranged and Melee equipment IDs", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingInTheTempleWithoutARingFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Carry or wear a Ring of dueling, then restart the plugin", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingAtFeroxWithoutARingBanksToFetchOne() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.standAt(TempleData.FEROX_BANK);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Opening the bank", fixture.plugin.getStatus());
    }

    @Test
    public void aFailedSessionStaysFailedAfterTheConditionClears() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        ChatMessage message = new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", TempleData.QUEST_MESSAGE, "", 0);
        fixture.plugin.onChatMessage(message);
        fixture.tick();
        String reason = "The Guthixian temple teleport needs While Guthix Sleeps; complete the quest, then restart the plugin";
        assertEquals(reason, fixture.plugin.getStatus());
        fixture.tick();
        assertEquals(reason, fixture.plugin.getStatus());
        assertEquals(1, fixture.requests());
    }

    @Test
    public void aBindMessageStartsTheBombWindowOnTheNextTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        ChatMessage message = new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", TempleData.BIND_MESSAGE, "", 0);
        fixture.plugin.onChatMessage(message);
        DemonFight fight = (DemonFight) fixture.read("fight");
        assertEquals(fixture.plugin.getTick() + 1, fight.getBindTick());
    }

    @Test
    public void aDemonDeathAnimationCountsOneKillEvenWhenRepeated() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.demon.interacting = fixture.world.player;
        fixture.demon.animation = TempleData.DEATH_ANIMATION;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.demon.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.plugin.onAnimationChanged(event);
        assertEquals(1, fixture.plugin.getKills());
    }

    @Test
    public void aDemonThatStoppedInteractingAsItDiedStillCounts() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.demon.interacting = fixture.world.player;
        fixture.tick();
        fixture.demon.interacting = null;
        fixture.tick();
        fixture.demon.animation = TempleData.DEATH_ANIMATION;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.demon.npc);
        fixture.plugin.onAnimationChanged(event);
        assertEquals(1, fixture.plugin.getKills());
        TripState trip = (TripState) fixture.read("trip");
        assertTrue(trip.isWaitingForDrop(fixture.plugin.getTick()), "the drop is waited for");
    }

    @Test
    public void onlyThePlayersOwnHitOnTheEngagedDemonShowsTheAttackGoesOn() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.demon.interacting = fixture.world.player;
        fixture.tick();
        TripState trip = (TripState) fixture.read("trip");
        int tick = fixture.plugin.getTick();
        trip.equipmentChanged(tick - 5);
        HitsplatApplied others = new HitsplatApplied();
        others.setActor(fixture.demon.npc);
        others.setHitsplat(hitsplat(HitsplatID.DAMAGE_OTHER));
        fixture.plugin.onHitsplatApplied(others);
        assertTrue(trip.isAttackNeeded(tick + 3));
        HitsplatApplied mine = new HitsplatApplied();
        mine.setActor(fixture.demon.npc);
        mine.setHitsplat(hitsplat(HitsplatID.BLOCK_ME));
        fixture.plugin.onHitsplatApplied(mine);
        assertFalse(trip.isAttackNeeded(tick + 3), "a miss also counts");
    }

    private static Hitsplat hitsplat(int type)
    {
        return new Hitsplat()
        {
            @Override
            public int getHitsplatType()
            {
                return type;
            }

            @Override
            public int getAmount()
            {
                return 0;
            }

            @Override
            public int getDisappearsOnGameCycle()
            {
                return 0;
            }
        };
    }

    @Test
    public void aDemonFightingSomeoneElseIsNotCounted() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.demon.animation = TempleData.DEATH_ANIMATION;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.demon.npc);
        fixture.plugin.onAnimationChanged(event);
        assertEquals(0, fixture.plugin.getKills());
    }

    private static void stateChanged(PluginTestFixture fixture, GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        fixture.plugin.onGameStateChanged(event);
    }

    private static void configChanged(PluginTestFixture fixture, String key)
    {
        ConfigChanged event = new ConfigChanged();
        event.setGroup(TormentedDemonsConfig.GROUP);
        event.setKey(key);
        fixture.plugin.onConfigChanged(event);
    }
}
