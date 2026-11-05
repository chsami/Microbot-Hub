package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation.Walking;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.NPC;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.plugins.RecordingPluginManager;
import net.runelite.client.plugins.microbot.fourtifysbarrows.NativeConfigTestSupport;
import net.runelite.client.plugins.microbot.fourtifysbarrows.FourtifysBarrowsPlugin;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.TunnelMap;
import net.runelite.client.plugins.microbot.fourtifysbarrows.overlay.BarrowsOverlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginLifecycleTest
{
    @Test
    public void outsideStartupStopsForHiddenNpcAttackOptionsWithoutStartingTravel() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.npcAttackOption = 3;
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertFalse(fixture.actions.ready());
        assertTrue(fixture.plugin.getStatus().contains("NPC Attack options are Hidden"));
        assertEquals(0, fixture.actions.walks);
        assertFalse(fixture.actions.bankPending());
        assertEquals(3, fixture.npcAttackOption);
    }

    @Test
    public void outsideStartupAllowsAllVisibleNpcAttackModesEvenWhenPlayerAttackIsHidden() throws Exception
    {
        for (int option : new int[] {0, 1, 2})
        {
            Fixture fixture = new Fixture();
            fixture.location = new WorldPoint(3130, 3632, 0);
            fixture.npcAttackOption = option;
            fixture.playerAttackOption = 3;
            fixture.gameState = GameState.LOGGED_IN;
            fixture.player = fixture.player();
            fixture.enable();
            fixture.thread.runNext();
            assertTrue(fixture.actions.ready());
            assertFalse(fixture.actions.bankPending());
            assertEquals(option, fixture.npcAttackOption);
            assertEquals(3, fixture.playerAttackOption);
        }
    }

    @Test
    public void hiddenNpcSettingDoesNotBlockAnExistingStartupBankRecovery() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.food = 0;
        fixture.npcAttackOption = 3;
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        assertTrue(fixture.actions.ready());
        assertTrue(fixture.actions.bankPending());
        assertEquals(3, fixture.npcAttackOption);
    }

    @Test
    public void outsideStartupDoesNotFailTheNpcPreflightWhileAlreadyInteracting() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.npcAttackOption = 3;
        fixture.interactingNpc = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class},
            (proxy, method, arguments) -> null);
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        assertTrue(fixture.actions.ready());
        assertEquals(3, fixture.npcAttackOption);
    }

    @Test
    public void startingInsideBarrowsWithHiddenAttackOptionsPreservesEmergencyRecovery() throws Exception
    {
        for (WorldPoint location : new WorldPoint[] {Brother.DHAROK.getMound(),
            Brother.AHRIM.getSarcophagus(), TunnelMap.roomCenter(4)})
        {
            Fixture fixture = new Fixture();
            fixture.location = location;
            fixture.npcAttackOption = 3;
            fixture.hp = 10;
            fixture.gameState = GameState.LOGGED_IN;
            fixture.player = fixture.player();
            fixture.enable();
            fixture.thread.runNext();
            fixture.plugin.onGameTick(new GameTick());
            assertTrue(fixture.actions.ready());
            assertFalse(fixture.actions.bankPending());
            assertTrue(fixture.plugin.getStatus().contains("Ferox Enclave"), fixture.plugin.getStatus());
            assertEquals(3, fixture.npcAttackOption);
        }
    }

    @Test
    public void enableAndSettingsChangesAtBarrowsKeepRemainingSuppliesWithoutToppingUp() throws Exception
    {
        for (WorldPoint location : new WorldPoint[] {Brother.DHAROK.getMound(),
            Brother.AHRIM.getSarcophagus(), TunnelMap.roomCenter(4)})
        {
            Fixture fixture = new Fixture();
            fixture.location = location;
            fixture.food = 7;
            fixture.potions = 3;
            fixture.restock = "19629:2";
            fixture.gameState = GameState.LOGGED_IN;
            fixture.player = fixture.player();
            fixture.enable();
            fixture.thread.runNext();
            assertFalse(fixture.actions.bankPending());
            ConfigChanged event = new ConfigChanged();
            event.setGroup(BarrowsConfig.GROUP);
            event.setKey("restockItems");
            fixture.plugin.onConfigChanged(event);
            fixture.thread.runNext();
            assertFalse(fixture.actions.bankPending());
            assertTrue(fixture.actions.ready());
            assertEquals(7, fixture.actions.foodCount());
            assertEquals(3, fixture.actions.potionCount());
            assertEquals(1, fixture.actions.count(19629));
            assertEquals(Integer.valueOf(2), fixture.actions.restockItems().get(19629));
        }
    }

    @Test
    public void startupOutsideBarrowsStillBanksForEachConfiguredStockShortfall() throws Exception
    {
        for (WorldPoint location : new WorldPoint[] {new WorldPoint(3130, 3632, 0), new WorldPoint(3500, 3300, 0)})
        {
            for (int shortage = 0; shortage < 3; shortage++)
            {
                Fixture fixture = new Fixture();
                fixture.location = location;
                fixture.food = shortage == 0 ? 9 : 10;
                fixture.potions = shortage == 1 ? 3 : 4;
                fixture.tablets = shortage == 2 ? 1 : 2;
                fixture.restock = "19629:2";
                fixture.gameState = GameState.LOGGED_IN;
                fixture.player = fixture.player();
                fixture.enable();
                fixture.thread.runNext();
                assertTrue(fixture.actions.bankPending());
            }
        }
    }

    @Test
    public void startupAtBarrowsStillBanksForMissingSpadeFoodOrRequiredPrayerPotions() throws Exception
    {
        for (WorldPoint location : new WorldPoint[] {Brother.DHAROK.getMound(),
            Brother.AHRIM.getSarcophagus(), TunnelMap.roomCenter(4)})
        {
            for (int missing = 0; missing < 3; missing++)
            {
                Fixture fixture = new Fixture();
                fixture.location = location;
                fixture.spade = missing != 0;
                fixture.food = missing == 1 ? 0 : 7;
                fixture.potions = missing == 2 ? 0 : 3;
                fixture.gameState = GameState.LOGGED_IN;
                fixture.player = fixture.player();
                fixture.enable();
                fixture.thread.runNext();
                assertTrue(fixture.actions.bankPending());
            }
        }
    }

    @Test
    public void timingChangesKeepTheCurrentSessionTasksAndQueuedActions() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        Instant started = fixture.plugin.getStartTime();
        Field crypt = FourtifysBarrowsPlugin.class.getDeclaredField("crypt");
        crypt.setAccessible(true);
        Object task = crypt.get(fixture.plugin);
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, fixture.plugin.getRequestsDispatched());
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        for (String key : new String[] {"tickDelayMin", "tickDelayMax", "tickDelayTarget", "tickDelayDeviation", "tickDelayWeightedDistribution"})
        {
            ConfigChanged event = new ConfigChanged();
            event.setGroup(BarrowsConfig.GROUP);
            event.setKey(key);
            fixture.plugin.onConfigChanged(event);
            assertSame(started, fixture.plugin.getStartTime());
            assertSame(task, crypt.get(fixture.plugin));
            assertEquals(1, fixture.plugin.getRequestsDispatched());
            assertTrue(fixture.thread.tasks.isEmpty());
            assertEquals(1, countOverlays(fixture.overlays));
        }
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, requests.get());
    }

    @Test
    public void enablingLoggedOutWaitsWithoutTimerOrOverlay() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> true));
    }

    @Test
    public void firstLoginValidatesBankJewelleryBeforeStartingOutboundTravel() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.carryRing = false;
        fixture.enable();
        fixture.thread.runNext();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.plugin.onGameTick(new GameTick());
        assertTrue(fixture.plugin.getStatus().contains("charged"));
        assertEquals(0, fixture.actions.walks);
    }

    @Test
    public void firstLoggedInTickStartsTimerTasksAndOverlay() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.plugin.onGameTick(new GameTick());
        assertNotNull(fixture.plugin.getStartTime());
        assertEquals("Walking to Dharok the Wretched mound", fixture.plugin.getStatus());
        assertEquals(1, countOverlays(fixture.overlays));
    }

    @Test
    public void activityWaitsForObservedAutoRetaliateOffAndRechecksWhenEnabledAgain() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.autoRetaliateVarp = 0;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertEquals(0, fixture.actions.walks);
        assertTrue(fixture.plugin.getStatus().contains("stays off"));
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, fixture.tabRequests);
        assertEquals(0, fixture.actions.walks);
        fixture.autoRetaliateVarp = 1;
        fixture.gameCycle = 60;
        fixture.plugin.onGameTick(new GameTick());
        fixture.gameCycle = 90;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals(1, fixture.actions.walks);
        fixture.autoRetaliateVarp = 0;
        fixture.gameCycle = 120;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals(1, fixture.actions.walks);
        assertTrue(fixture.plugin.getStatus().contains("stays off"));
    }

    @Test
    public void loadingAndDisableCancelPendingCombatTabWithoutRestoringSetting() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.autoRetaliateVarp = 0;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.gameState = GameState.LOADING;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOADING);
        fixture.plugin.onGameStateChanged(event);
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(0, fixture.tabRequests);
        fixture.autoRetaliateVarp = 1;
        fixture.disable();
        fixture.thread.runNext();
        fixture.actions.dispatch();
        assertEquals(0, fixture.tabRequests);
        assertEquals(1, fixture.autoRetaliateVarp);
    }

    @Test
    public void foodKeepsPriorityOverAutoRetaliatePreparation() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.autoRetaliateVarp = 0;
        fixture.hp = 50;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(3, fixture.lastTab);
        assertEquals(0, fixture.actions.walks);
    }

    @Test
    public void emergencyKeepsPriorityOverAutoRetaliatePreparation() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.autoRetaliateVarp = 0;
        fixture.hp = 10;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertTrue(fixture.plugin.getStatus().contains("Ferox Enclave"), fixture.plugin.getStatus());
        assertFalse(fixture.actions.bankPending());
    }

    @Test
    public void lowHitpointsAtFeroxRestoreThroughTheBankTripInsteadOfTheRing() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.location = new WorldPoint(3151, 3636, 0);
        fixture.player = fixture.player();
        fixture.hp = 10;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertTrue(fixture.actions.bankPending());
        assertFalse(fixture.plugin.getStatus().contains("Ring of dueling to Ferox"), fixture.plugin.getStatus());
        assertFalse(fixture.plugin.getStatus().contains("mergency"), fixture.plugin.getStatus());
    }

    @Test
    public void aStopInACryptTeleportsOutInsteadOfIdling() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.location = new WorldPoint(3554, 9713, 3);
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.actions.fail("Combat stalled");
        fixture.plugin.onGameTick(new GameTick());
        long before = fixture.actions.getRequestsDispatched();
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals("Teleporting out before stopping: Combat stalled", fixture.plugin.getStatus());
        assertTrue(fixture.actions.ready());
        assertEquals(before + 1, fixture.actions.getRequestsDispatched(), "The ring is rubbed while the stop waits");
    }

    @Test
    public void aStopInACryptWithoutTheRingEndsWithTheNextStep() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.location = new WorldPoint(3554, 9713, 3);
        fixture.carryRing = false;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertFalse(fixture.actions.ready());
        assertTrue(fixture.plugin.getStatus().endsWith("the teleport out failed, so leave Barrows now"), fixture.plugin.getStatus());
    }

    @Test
    public void aStopPostsOneRedChatMessageAndSwitchesThePluginOff() throws Exception
    {
        // Earlier tests can leave switch-off requests on the Swing queue.
        SwingUtilities.invokeAndWait(() -> { });
        RecordingPluginManager manager = RecordingPluginManager.install();
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.npcAttackOption = 3;
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onGameTick(new GameTick());
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, fixture.chat.size());
        assertTrue(fixture.chat.get(0).startsWith("<col=ff0000>Fourtifys Barrows stopped: NPC Attack options are Hidden"), fixture.chat.get(0));
        assertEquals(1, manager.stopped.stream().filter(plugin -> plugin == fixture.plugin).count());
        fixture.thread.runNext();
        assertNull(fixture.plugin.getStartTime());
        assertEquals(0, countOverlays(fixture.overlays));
        assertTrue(fixture.plugin.getStatus().startsWith("NPC Attack options are Hidden"), "The reason stays readable after the stop");
    }

    @Test
    public void startStopsForConfiguredEquipmentThatIsNeitherWornNorCarried() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.magic = "385,4151";
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        assertFalse(fixture.actions.ready());
        assertEquals("Wear or carry item 4151, or remove it from Magic equipment IDs", fixture.actions.getStatus());
    }

    @Test
    public void startAcceptsCarriedEquipmentAndAnyChargeOfTheCarriedRing() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.magic = "385,2554";
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        assertTrue(fixture.actions.ready(), fixture.actions.getStatus());
    }

    @Test
    public void deathCancelsPendingAutoRetaliateTab() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.autoRetaliateVarp = 0;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        fixture.dead = true;
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(0, fixture.tabRequests);
        assertTrue(fixture.plugin.getStatus().contains("Player died"));
    }

    @Test
    public void loginWithoutLocalPlayerDoesNotStart() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> true));
    }

    @Test
    public void disableRejectsQueuedStartupAndImmediatelyPreventsDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.disable();
        fixture.thread.runNext();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.plugin.onClientTick(new ClientTick());
        fixture.plugin.onGameTick(new GameTick());
        fixture.thread.runNext();
        fixture.actions.dispatch();
        assertEquals(0, requests.get());
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Stopped", fixture.plugin.getStatus());
    }

    @Test
    public void disableImmediatelyStopsARunningSessionBeforeCleanupRuns() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.disable();
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(0, requests.get());
        fixture.thread.runNext();
        fixture.actions.dispatch();
        assertEquals(0, requests.get());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlays.anyMatch(overlay -> true));
    }

    @Test
    public void rapidReenableRejectsStaleCleanup() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.enable();
        fixture.thread.runNext();
        fixture.disable();
        fixture.enable();
        fixture.plugin.setStatus("New enable");
        fixture.thread.runNext();
        assertEquals("New enable", fixture.plugin.getStatus());
        fixture.thread.runNext();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
    }

    @Test
    public void dispatchWindowEndsAtCycleTwentyNineAndNextGameTickExpiresPendingRequests() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.gameCycle = 28;
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, requests.get());
        fixture.gameCycle = 29;
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, requests.get());
        fixture.gameCycle = 30;
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, requests.get());
    }

    @Test
    public void ordinaryLogoutClearsRequestsAndRetainsTheStartedSessionTimer() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        Instant start = fixture.plugin.getStartTime();
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.gameState = GameState.LOGIN_SCREEN;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        fixture.plugin.onGameStateChanged(event);
        fixture.plugin.onClientTick(new ClientTick());
        fixture.gameState = GameState.LOGGED_IN;
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(0, requests.get());
        assertSame(start, fixture.plugin.getStartTime());
        assertEquals(1, countOverlays(fixture.overlays));
    }

    @Test
    public void actualMenuDispatchArmsTheDelay() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, fixture.menuRequests);
        assertEquals(1, fixture.actions.getRequestsDispatched());
        fixture.gameCycle = 30;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals("Short pause (1 tick)", fixture.plugin.getStatus());
        fixture.gameCycle = 60;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals("Walking to Dharok the Wretched mound", fixture.plugin.getStatus());
    }

    @Test
    public void rejectedStaleWidgetDoesNotArmTheDelay() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.widgetItemId++;
        fixture.plugin.onClientTick(new ClientTick());
        fixture.gameCycle = 30;
        fixture.plugin.onGameTick(new GameTick());
        assertEquals(0, fixture.menuRequests);
        assertEquals(0, fixture.actions.getRequestsDispatched());
        assertEquals("Walking to Dharok the Wretched mound", fixture.plugin.getStatus());
    }

    @Test
    public void deathClearsQueuedActionsAndStopsFurtherActivity() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.plugin.onGameTick(new GameTick());
        int before = fixture.actions.walks;
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        fixture.dead = true;
        fixture.plugin.onGameTick(new GameTick());
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(0, requests.get());
        assertEquals(before, fixture.actions.walks);
        assertFalse(fixture.actions.ready());
        assertTrue(fixture.plugin.getStatus().contains("Player died"));
    }

    @Test
    public void settingsChangesKeepTheSessionProgressAndQueuedActions() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        Instant started = fixture.plugin.getStartTime();
        Field crypt = FourtifysBarrowsPlugin.class.getDeclaredField("crypt");
        crypt.setAccessible(true);
        Object task = crypt.get(fixture.plugin);
        AtomicInteger requests = new AtomicInteger();
        fixture.actions.enqueue(requests::incrementAndGet);
        for (String key : new String[] {"restockItems", "showOverlay", "foodCount", "travelMethod"})
        {
            ConfigChanged event = new ConfigChanged();
            event.setGroup(BarrowsConfig.GROUP);
            event.setKey(key);
            fixture.plugin.onConfigChanged(event);
            fixture.thread.runNext();
        }
        assertSame(started, fixture.plugin.getStartTime());
        assertSame(task, crypt.get(fixture.plugin));
        assertTrue(fixture.actions.ready());
        assertEquals(1, countOverlays(fixture.overlays));
        fixture.plugin.onClientTick(new ClientTick());
        assertEquals(1, requests.get());
    }

    @Test
    public void anInvalidItemListEnteredWhileRunningStopsTheSession() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.gameState = GameState.LOGGED_IN;
        fixture.location = new WorldPoint(3130, 3632, 0);
        fixture.player = fixture.player();
        fixture.enable();
        fixture.thread.runNext();
        fixture.restock = "19629;2";
        ConfigChanged event = new ConfigChanged();
        event.setGroup(BarrowsConfig.GROUP);
        event.setKey("restockItems");
        fixture.plugin.onConfigChanged(event);
        assertTrue(fixture.actions.ready(), "The check waits for the client thread");
        fixture.thread.runNext();
        assertFalse(fixture.actions.ready());
        assertEquals("Additional supplies/ammunition is \"19629;2\"; use itemID:quantity pairs separated by commas, such as 560:100,9075:200",
            fixture.actions.getStatus());
    }

    private static int countOverlays(OverlayManager overlays)
    {
        AtomicInteger count = new AtomicInteger();
        overlays.anyMatch(overlay -> {
            count.incrementAndGet();
            return false;
        });
        return count.get();
    }

    private static final class Fixture
    {
        final FourtifysBarrowsPlugin plugin = new FourtifysBarrowsPlugin();
        final QueuedClientThread thread = new QueuedClientThread();
        final FixtureActions actions = new FixtureActions();
        BarrowsInteractions interactions;
        final OverlayManager overlays;
        GameState gameState = GameState.LOGIN_SCREEN;
        Player player;
        NPC interactingNpc;
        static final int WIDGET_ID = (123 << 16) | 4;
        int gameCycle;
        int widgetItemId = 1625;
        int menuRequests;
        final List<String> chat = new ArrayList<>();
        int tabRequests;
        int lastTab = -1;
        int autoRetaliateVarp = 1;
        int npcAttackOption = 2;
        int playerAttackOption;
        int hp = 99;
        int food = 10;
        int potions = 4;
        int tablets = 1;
        boolean spade = true;
        String restock = "";
        String magic = "";
        WorldPoint location = new WorldPoint(3560, 3310, 0);
        boolean dead;
        boolean carryRing = true;
        final Widget widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
            (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "getId": return WIDGET_ID;
                    case "getIndex": return 0;
                    case "getItemId": return widgetItemId;
                    case "getActions": return new String[] {"Select"};
                    case "getName": return "Control";
                    case "isHidden": return false;
                    default: return null;
                }
            });

        Fixture() throws Exception
        {
            Constructor<?> constructor = OverlayManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            ConfigManager manager = NativeConfigTestSupport.create(new File("build/unused-overlay-settings-" + System.nanoTime() + ".properties"));
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++)
            {
                if (types[i] == ConfigManager.class)
                {
                    arguments[i] = manager;
                }
                else if (types[i] == RuneLiteConfig.class)
                {
                    arguments[i] = manager.getConfig(RuneLiteConfig.class);
                }
            }
            overlays = (OverlayManager) constructor.newInstance(arguments);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getGameState": return gameState;
                        case "getLocalPlayer": return player;
                        case "getGameCycle": return gameCycle;
                        case "getTickCount": return gameCycle / 30;
                        case "getRealSkillLevel": return 99;
                        case "getBoostedSkillLevel": return args[0] == Skill.HITPOINTS ? hp : 99;
                        case "getEnergy": return 10000;
                        case "getVarbitValue": return 0;
                        case "getVarcIntValue": return 0;
                        case "getVarpValue":
                            return (Integer) args[0] == VarPlayerID.OPTION_ATTACKPRIORITY_NPC ? npcAttackOption
                                : (Integer) args[0] == VarPlayerID.OPTION_ATTACKPRIORITY ? playerAttackOption : autoRetaliateVarp;
                        case "runScript":
                            tabRequests++;
                            lastTab = (Integer) ((Object[]) args[0])[1];
                            return null;
                        case "isPrayerActive": return false;
                        case "getItemContainer": return inventory();
                        case "getWidget":
                            int id = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                            return id == WIDGET_ID ? widget : null;
                        case "menuAction":
                            menuRequests++;
                            return null;
                        case "addChatMessage":
                            chat.add((String) args[2]);
                            return null;
                        default: return null;
                    }
                });
            BarrowsConfig config = new BarrowsConfig()
            {
                @Override
                public String restockItems()
                {
                    return restock;
                }

                @Override
                public String magicGear()
                {
                    return magic;
                }

                @Override
                public int tickDelayMin()
                {
                    return 1;
                }

                @Override
                public int tickDelayMax()
                {
                    return 1;
                }
            };
            MicrobotTestSupport.attach(actions, client);
            Field field = BarrowsActions.class.getDeclaredField("interactions");
            field.setAccessible(true);
            interactions = (BarrowsInteractions) field.get(actions);
            inject(actions, "config", config);
            inject(plugin, "client", client);
            inject(plugin, "clientThread", thread);
            inject(plugin, "config", config);
            inject(plugin, "actions", actions);
            inject(plugin, "overlays", overlays);
            inject(plugin, "overlay", new BarrowsOverlay(plugin, config, client, new TooltipManager()));
        }

        Player player()
        {
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getAnimation": return -1;
                        case "isDead": return dead;
                        case "getInteracting": return interactingNpc;
                        case "getWorldLocation": return location;
                        default: return null;
                    }
                });
        }

        ItemContainer inventory()
        {
            return (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(), new Class<?>[] {ItemContainer.class},
                (proxy, method, args) -> method.getName().equals("getItems")
                    ? carryRing
                        ? new Item[] {new Item(952, spade ? 1 : 0), new Item(385, food), new Item(2434, potions), new Item(19629, tablets), new Item(2552, 1)}
                        : new Item[] {new Item(952, spade ? 1 : 0), new Item(385, food), new Item(2434, potions), new Item(19629, tablets)}
                    : null);
        }

        void enable() throws Exception
        {
            lifecycle("startUp");
        }

        void disable() throws Exception
        {
            lifecycle("shutDown");
        }

        private void lifecycle(String name) throws Exception
        {
            java.lang.reflect.Method method = FourtifysBarrowsPlugin.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(plugin);
        }
    }

    private static final class QueuedClientThread extends ClientThread
    {
        final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void invokeLater(Runnable action)
        {
            tasks.addLast(action);
        }

        void runNext()
        {
            tasks.removeFirst().run();
        }
    }

    private static final class FixtureActions extends BarrowsActions
    {
        private int walks;

        @Override
        public String prerequisite()
        {
            return null;
        }

        @Override
        public NPC brotherNpc()
        {
            return null;
        }

        @Override
        public boolean walk(WorldPoint target, String label)
        {
            walks++;
            status("Walking to " + label);
            return false;
        }
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        Class<?> type = instance.getClass();
        Field field;
        while (true)
        {
            try
            {
                field = type.getDeclaredField(name);
                break;
            }
            catch (NoSuchFieldException missing)
            {
                type = type.getSuperclass();
                if (type == null)
                {
                    throw missing;
                }
            }
        }
        field.setAccessible(true);
        field.set(instance, value);
    }
}
