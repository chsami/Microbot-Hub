package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.plugins.microbot.BlockingEventManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EventDismissScriptTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private BlockingEventManager manager;
    private EventDismissScript script;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        rs2Player = mockStatic(Rs2Player.class);
        manager = mock(BlockingEventManager.class);
        microbot.when(Microbot::getBlockingEventManager).thenReturn(manager);
        script = new EventDismissScript();
    }

    @AfterEach
    void tearDown() {
        script.shutdown();
        rs2Player.close();
        microbot.close();
    }

    @Test
    void processesQueuedBlockingEventsWithoutAnotherScript() {
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(true);

        script.processBlockingEvents();

        verify(manager).shouldBlockAndProcess();
    }

    @Test
    void skipsWhileLoggedOut() {
        microbot.when(Microbot::isLoggedIn).thenReturn(false);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(true);

        script.processBlockingEvents();

        verify(manager, never()).shouldBlockAndProcess();
    }

    @Test
    void skipsOnTutorialIsland() {
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        rs2Player.when(Rs2Player::hasCompletedTutorialIsland).thenReturn(false);

        script.processBlockingEvents();

        verify(manager, never()).shouldBlockAndProcess();
    }
}
