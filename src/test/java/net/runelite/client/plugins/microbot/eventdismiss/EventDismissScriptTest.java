package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventDismissScriptTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private MockedStatic<InputArbiter> inputArbiter;
    private BlockingEventManager manager;
    private BlockingEvent randomEvent;
    private EventDismissScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        rs2Player = mockStatic(Rs2Player.class);
        inputArbiter = mockStatic(InputArbiter.class);
        manager = mock(BlockingEventManager.class);
        microbot.when(Microbot::getBlockingEventManager).thenReturn(manager);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(true);
        inputArbiter.when(InputArbiter::isHuman).thenReturn(false);
        randomEvent = mock(BlockingEvent.class);
        when(randomEvent.validate()).thenReturn(true);
        script = new EventDismissScript();
    }

    @AfterEach
    void tearDown() {
        script.shutdown();
        Microbot.pauseAllScripts.set(false);
        inputArbiter.close();
        rs2Player.close();
        microbot.close();
    }

    @Test
    void processesQueuedBlockingEventsWhileARandomEventIsPresent() {
        script.processBlockingEvents(events());

        verify(manager).shouldBlockAndProcess();
    }

    @Test
    void skipsWhenNoRandomEventIsPresent() {
        when(randomEvent.validate()).thenReturn(false);

        script.processBlockingEvents(events());

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void skipsWhileThePlayerIsUsingMouseOrKeyboard() {
        inputArbiter.when(InputArbiter::isHuman).thenReturn(true);

        script.processBlockingEvents(events());

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void skipsWhileScriptsArePaused() {
        Microbot.pauseAllScripts.set(true);

        script.processBlockingEvents(events());

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void skipsWhileLoggedOut() {
        microbot.when(Microbot::isLoggedIn).thenReturn(false);

        script.processBlockingEvents(events());

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void skipsOnTutorialIsland() {
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(false);

        script.processBlockingEvents(events());

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void keepsRunningAfterAFailedPass() {
        when(randomEvent.validate()).thenThrow(new IllegalStateException("client thread")).thenReturn(true);

        assertDoesNotThrow(() -> script.processBlockingEvents(events()));
        script.processBlockingEvents(events());

        verify(manager, times(1)).shouldBlockAndProcess();
    }

    @Test
    void startingTheLoopDoesNotAnnounceAScriptStart() {
        script.start(List.of());

        assertTrue(script.isRunning());
        microbot.verify(Microbot::getEventBus, never());
    }

    @Test
    void shutdownStopsTheLoop() {
        script.start(List.of());
        script.shutdown();

        assertFalse(script.isRunning());
    }

    @Test
    void pluginStartsAndStopsTheLoop() throws Exception {
        EventDismissPlugin plugin = new EventDismissPlugin();
        EventDismissScript pluginScript = mock(EventDismissScript.class);
        setField(plugin, "config", mock(EventDismissConfig.class));
        setField(plugin, "eventDismissScript", pluginScript);

        plugin.startUp();
        verify(pluginScript).start(anyList());

        plugin.shutDown();
        verify(pluginScript).shutdown();
    }

    private List<BlockingEvent> events() {
        return List.of(randomEvent);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
