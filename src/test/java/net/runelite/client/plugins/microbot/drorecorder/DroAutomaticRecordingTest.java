package net.runelite.client.plugins.microbot.drorecorder;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static net.runelite.client.plugins.microbot.drorecorder.DroAutomaticRecording.Location.*;

class DroAutomaticRecordingTest
{
    private static void set(Object owner, String field, Object value) throws Exception
    {
        var member = owner.getClass().getDeclaredField(field); member.setAccessible(true); member.set(owner, value);
    }

    @Test void allThreeArenasStartOnceAndContinueAcrossLoadingAndScriptIndependentFrames()
    {
        List<String> events = new ArrayList<>();
        var capture = new DroAutomaticRecording(activity -> events.add("start:" + activity), () -> events.add("stop"));
        for (var location : List.of(INFERNO, COLOSSEUM, FIRECAPE))
        {
            capture.observe(location, false);
            for (int i = 0; i < 30; i++) capture.observe(location, i % 2 == 0);
            capture.observe(OUTSIDE, true); capture.observe(UNKNOWN, false); capture.observe(OUTSIDE, true);
            assertNotEquals("stop", events.get(events.size() - 1), "Loading resets the outside confirmation");
            capture.observe(location, true); // a stopped/paused script does not change the observed arena
            capture.observe(OUTSIDE, true);
            for (int i = 0; i < 10; i++) capture.observe(OUTSIDE, false);
            assertNotEquals("stop", events.get(events.size() - 1), "Client frames are not exit ticks");
            capture.observe(OUTSIDE, true); capture.close();
        }
        assertEquals(List.of("start:inferno", "stop", "start:colosseum", "stop", "start:firecape", "stop"), events);
    }

    @Test void arenaSwitchAndLogoutFinishOnlyTheOwnedSession()
    {
        List<String> events = new ArrayList<>();
        var capture = new DroAutomaticRecording(activity -> events.add(activity), () -> events.add("stop"));
        capture.observe(INFERNO, false); capture.observe(COLOSSEUM, false); capture.observe(LOGGED_OUT, false); capture.close();
        assertEquals(List.of("inferno", "stop", "colosseum", "stop"), events);
    }

    @Test void endedRunsStayClosedUntilConfirmedExitAndCanRecordTheNextAttempt()
    {
        for (var arena : List.of(INFERNO, COLOSSEUM, FIRECAPE))
        {
            var start = mock(java.util.function.Consumer.class); var stop = mock(Runnable.class);
            var capture = new DroAutomaticRecording(start, stop);
            capture.observe(arena, true); capture.finish(); capture.finish();
            capture.observe(UNKNOWN, false);
            for (int i = 0; i < 10; i++) capture.observe(arena, true);
            capture.observe(OUTSIDE, true); capture.observe(arena, true);
            verify(start, times(1)).accept(anyString()); verify(stop, times(1)).run();
            capture.observe(OUTSIDE, true); capture.observe(OUTSIDE, true); capture.observe(arena, true);
            verify(start, times(2)).accept(anyString()); capture.close(); verify(stop, times(2)).run();
        }
    }

    @Test void detectorDistinguishesLoadingLogoutAndDeathInsideArena()
    {
        Client client = mock(Client.class); Player player = mock(Player.class); WorldView view = mock(WorldView.class);
        when(client.getGameState()).thenReturn(GameState.LOADING); assertEquals(UNKNOWN, DroAutomaticRecording.location(client));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); assertEquals(LOGGED_OUT, DroAutomaticRecording.location(client));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player); when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.isInstance()).thenReturn(false);
        assertEquals(OUTSIDE, DroAutomaticRecording.location(client));
        when(view.isInstance()).thenReturn(true);
        LocalPoint point = new LocalPoint(6400, 6400, 0);
        when(player.getLocalLocation()).thenReturn(point);
        try (var world = mockStatic(WorldPoint.class))
        {
            world.when(() -> WorldPoint.fromLocalInstance(client, point)).thenReturn(new WorldPoint(2245, 5320, 0));
            assertEquals(INFERNO, DroAutomaticRecording.location(client));
            world.when(() -> WorldPoint.fromLocalInstance(client, point)).thenReturn(new WorldPoint(1798, 3076, 0));
            assertEquals(COLOSSEUM, DroAutomaticRecording.location(client));
            world.when(() -> WorldPoint.fromLocalInstance(client, point)).thenReturn(new WorldPoint(2390, 5080, 0));
            assertEquals(FIRECAPE, DroAutomaticRecording.location(client));
            verify(client, never()).getBoostedSkillLevel(any());
        }
    }

    @Test void explicitSuppressionPersistsThroughUnknownAndUntilTwoOutsideGameTicks()
    {
        List<String> events = new ArrayList<>();
        var capture = new DroAutomaticRecording(activity -> events.add("start:" + activity), () -> events.add("stop"));
        capture.observe(INFERNO, false);
        capture.suppressUntilExit(INFERNO);
        capture.observe(INFERNO, false);
        capture.observe(UNKNOWN, true);
        capture.observe(OUTSIDE, true);
        capture.observe(UNKNOWN, false);
        capture.observe(INFERNO, false);
        assertEquals(List.of("start:inferno"), events);

        capture.observe(OUTSIDE, true);
        capture.observe(OUTSIDE, true);
        capture.observe(INFERNO, false);
        assertEquals(List.of("start:inferno", "start:inferno"), events);
    }

    @Test void cancellationBeforeClientThreadInitializationCreatesNoSession() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var thread = mock(ClientThread.class); var work = new ArrayList<Runnable>();
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "clientThread", thread); set(plugin, "keyManager", mock(KeyManager.class));
        try (var sessions = mockConstruction(DroRecorderSession.class))
        {
            plugin.startUp(); plugin.shutDown(); while (!work.isEmpty()) work.remove(0).run();
            assertTrue(sessions.constructed().isEmpty());
        }
    }

    @Test void ordinaryClientsStayDisabledAndRecorderHasNoOverlay()
    {
        assertFalse(DroRecorderPlugin.class.getAnnotation(PluginDescriptor.class).enabledByDefault());
        assertFalse(new DroRecorderConfig(){}.showOverlay());
    }

    @Test void clientCloseStopsCaptureAndRegistersWriterFlushWithoutWaiting() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var session = mock(DroRecorderSession.class);
        var completion = new java.util.concurrent.CompletableFuture<Void>();
        when(session.getCompletion()).thenReturn(completion);
        set(plugin, "session", session); set(plugin, "keyManager", mock(KeyManager.class));
        var event = new net.runelite.client.events.ClientShutdown();
        plugin.onClientShutdown(event);
        assertFalse(plugin.hasSession()); verify(session).stop();
        assertTrue(event.getTasks().contains(completion));
        assertFalse(completion.isDone(), "Shutdown must not block the event thread waiting for disk writes");
        plugin.onClientShutdown(event); verify(session, times(1)).stop();
    }

    @Test void programmaticStartupAndShutdownOwnOnlyTheirEmbeddedSession() throws Exception
    {
        var service = new DroAutomaticRecording(); var thread = mock(ClientThread.class); var work = new ArrayList<Runnable>();
        doAnswer(c -> { work.add(c.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(service, "clientThread", thread);
        try (var sessions = mockConstruction(DroRecorderSession.class, (s, context) -> {
            var config = (DroRecorderConfig) context.arguments().get(2);
            assertTrue(config.recordCombatEvents()); assertTrue(config.recordPrayerTransitions());
            assertTrue(config.recordTickSnapshots()); assertTrue(config.compressRecordings());
        }); var captures = mockConstruction(DroRecorderCapture.class))
        {
            var recording = service.open("inferno");
            assertEquals(0, sessions.constructed().size());
            work.remove(0).run();
            assertEquals(1, sessions.constructed().size());
            verify(sessions.constructed().get(0)).start();
            verify(captures.constructed().get(0)).start();
            recording.close();
            recording.close();
            while (!work.isEmpty()) work.remove(0).run();
            verify(sessions.constructed().get(0), times(1)).stop();
            verify(captures.constructed().get(0), times(1)).close();
        }
    }

    @Test void programmaticCancelledBeforeClientThreadStartsCreatesNoSession() throws Exception
    {
        var service = new DroAutomaticRecording(); var thread = mock(ClientThread.class); var work = new ArrayList<Runnable>();
        doAnswer(c -> { work.add(c.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(service, "clientThread", thread);
        try (var sessions = mockConstruction(DroRecorderSession.class))
        {
            var recording = service.open("colosseum");
            recording.close();
            while (!work.isEmpty()) work.remove(0).run();
            assertTrue(sessions.constructed().isEmpty());
        }
    }

    /** 1.3.6: an enabled plugin records everywhere as one recording; only disabling or an explicit stop ends it. */
    @Test void enabledPluginRecordsEverywhereUntilDisabled() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var work = new ArrayList<Runnable>(); var player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
        set(plugin, "keyManager", mock(KeyManager.class)); set(plugin, "eventBus", new EventBus());
        set(plugin, "mouseManager", mock(MouseManager.class));
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class); var captures = mockConstruction(DroRecorderCapture.class))
        {
            var location = new java.util.concurrent.atomic.AtomicReference<>(OUTSIDE);
            locations.when(() -> DroAutomaticRecording.location(client)).thenAnswer(call -> location.get());
            plugin.startUp(); work.remove(0).run();
            assertTrue(plugin.isRecording(), "Enabling the plugin starts recording");
            for (var where : List.of(INFERNO, OUTSIDE, COLOSSEUM, LOGGED_OUT, FIRECAPE, OUTSIDE))
            {
                location.set(where);
                for (int i = 0; i < 4; i++) plugin.onClientTick(null);
            }
            assertTrue(plugin.isRecording());
            assertEquals(1, sessions.constructed().size(), "Arena entry and exit do not split or end the recording");
            verify(sessions.constructed().get(0), never()).stop();

            plugin.shutDown();
            assertFalse(plugin.isRecording(), "Disabling the plugin saves the recording");
            verify(sessions.constructed().get(0), times(1)).stop();

            plugin.startUp(); work.remove(0).run();
            assertTrue(plugin.isRecording(), "Enabling it again starts a new recording");
            assertEquals(2, sessions.constructed().size());
            plugin.requestStopRecording(); work.remove(0).run();
            for (int i = 0; i < 4; i++) plugin.onClientTick(null);
            assertFalse(plugin.isRecording(), "An explicit stop is not undone by the next tick");
            verify(sessions.constructed().get(1), times(1)).stop();
        }
        finally { plugin.shutDown(); }
    }
}
