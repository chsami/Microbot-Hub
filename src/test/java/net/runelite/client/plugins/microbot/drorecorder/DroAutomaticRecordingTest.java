package net.runelite.client.plugins.microbot.drorecorder;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
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

    @Test void deathAndFinalBossCompletionFlushEvidenceAndDoNotRestartInEachArena() throws Exception
    {
        for (var arena : List.of(INFERNO, COLOSSEUM, FIRECAPE)) for (var reason : List.of("DEATH", "COMPLETION", "ZERO_HP"))
        {
            var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
            var config = mock(DroRecorderConfig.class); var bus = new EventBus(); var player = mock(Player.class); var boss = mock(NPC.class);
            var work = new ArrayList<Runnable>(); var location = new java.util.concurrent.atomic.AtomicReference<>(OUTSIDE);
            when(config.autoArenaRecording()).thenReturn(true); when(client.getLocalPlayer()).thenReturn(player);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(99);
            when(boss.getId()).thenReturn(arena == INFERNO ? net.runelite.api.gameval.NpcID.INFERNO_TZKALZUK_PLACEHOLDER : arena == COLOSSEUM ? net.runelite.api.gameval.NpcID.COLOSSEUM_SOL_P1 : net.runelite.api.gameval.NpcID.TZHAAR_FIGHTCAVE_SWARM_BOSS);
            doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
            set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
            set(plugin, "eventBus", bus); set(plugin, "mouseManager", mock(MouseManager.class)); set(plugin, "keyManager", mock(KeyManager.class));
            bus.register(plugin);
            try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS); var sessions = mockConstruction(DroRecorderSession.class))
            {
                locations.when(() -> DroAutomaticRecording.location(client)).thenAnswer(call -> location.get());
                plugin.startUp(); work.remove(0).run(); location.set(arena); bus.post(new net.runelite.api.events.ClientTick());
                // 1.3.5: the plugin records while it is on; the plain recording outside is saved on arena entry.
                verify(sessions.constructed().get(0), times(1)).stop();
                var session = sessions.constructed().get(1);
                bus.post(new ActorDeath(mock(NPC.class))); assertTrue(plugin.hasSession(), "Ordinary NPC death is not completion");
                if (reason.equals("ZERO_HP"))
                {
                    when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(0); bus.post(new net.runelite.api.events.GameTick());
                    assertTrue(plugin.hasSession(), "Uninitialized skills are not proof of death");
                    when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99); bus.post(new net.runelite.api.events.GameTick());
                }
                else
                {
                    var actor = reason.equals("DEATH") ? player : boss;
                    bus.post(new ActorDeath(actor));
                    var order = inOrder(session);
                    order.verify(session).recordActorEvent("actor_death", actor);
                    order.verify(session).recordDiagnostic(argThat(values -> "END".equals(values.get("stage")) && reason.equals(values.get("reason"))));
                    order.verify(session).stop();
                }
                assertFalse(plugin.hasSession(), arena + " " + reason);
                for (int i = 0; i < 5; i++) { bus.post(new net.runelite.api.events.ClientTick()); bus.post(new net.runelite.api.events.GameTick()); }
                assertEquals(2, sessions.constructed().size(), "A finished arena visit must not restart capture");
                verify(session, times(1)).stop();
            }
            finally { plugin.shutDown(); bus.unregister(plugin); }
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

    @Test void explicitStopSavesAutomaticSessionAndNextArenaCanStart() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var work = new ArrayList<Runnable>();
        var location = new java.util.concurrent.atomic.AtomicReference<>(OUTSIDE);
        when(config.autoArenaRecording()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
        set(plugin, "keyManager", mock(KeyManager.class)); set(plugin, "eventBus", new EventBus());
        set(plugin, "mouseManager", mock(MouseManager.class));
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class);
             var captures = mockConstruction(DroRecorderCapture.class))
        {
            locations.when(() -> DroAutomaticRecording.location(client)).thenAnswer(call -> location.get());
            plugin.startUp(); work.remove(0).run();
            location.set(INFERNO);
            plugin.onClientTick(null);
            assertTrue(plugin.isRecording());
            // 1.3.5: session 0 is the plain recording from startup outside, saved on arena entry.
            verify(sessions.constructed().get(0), times(1)).stop();

            plugin.requestStopRecording(); work.remove(0).run();
            assertFalse(plugin.isRecording());
            verify(sessions.constructed().get(1), times(1)).stop();
            for (int i = 0; i < 4; i++) { plugin.onClientTick(null); plugin.onGameTick(null); }
            assertEquals(2, sessions.constructed().size(), "The stopped automatic session must not reopen in Inferno");

            location.set(COLOSSEUM);
            plugin.onClientTick(null);
            assertTrue(plugin.isRecording(), "A different arena starts a new automatic recording");
            assertEquals(3, sessions.constructed().size());
            assertTrue(config.autoArenaRecording());
            plugin.shutDown();
            verify(sessions.constructed().get(2), times(1)).stop();
        }
        finally { plugin.shutDown(); }
    }

    @Test void manualStopInsideArenaSuppressesAutomaticRestartWithoutDisablingPreference() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var work = new ArrayList<Runnable>();
        var location = new java.util.concurrent.atomic.AtomicReference<>(OUTSIDE);
        when(config.autoArenaRecording()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
        set(plugin, "keyManager", mock(KeyManager.class)); set(plugin, "eventBus", new EventBus());
        set(plugin, "mouseManager", mock(MouseManager.class));
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class);
             var captures = mockConstruction(DroRecorderCapture.class))
        {
            locations.when(() -> DroAutomaticRecording.location(client)).thenAnswer(call -> location.get());
            plugin.startUp(); work.remove(0).run();
            plugin.requestManualRecording(); work.remove(0).run();
            assertTrue(plugin.isRecording());
            location.set(INFERNO);

            plugin.requestStopRecording(); work.remove(0).run();
            assertFalse(plugin.isRecording());
            for (int i = 0; i < 4; i++) { plugin.onClientTick(null); plugin.onGameTick(null); }
            assertEquals(1, sessions.constructed().size(), "The stopped manual session must suppress automatic capture in its current arena");
            verify(sessions.constructed().get(0), times(1)).stop();
            assertTrue(config.autoArenaRecording());

            location.set(OUTSIDE);
            plugin.onGameTick(null);
            assertFalse(plugin.isRecording());
            plugin.onGameTick(null);
            location.set(INFERNO);
            plugin.onClientTick(null);
            assertTrue(plugin.isRecording(), "Automatic recording resumes after two actual outside game ticks");
            assertEquals(2, sessions.constructed().size());
            plugin.shutDown();
            verify(sessions.constructed().get(1), times(1)).stop();
        }
        finally { plugin.shutDown(); }
    }

    @Test void recorderOwnsCaptureThroughGlobalPauseAndStopsOnlyOnExitOrExplicitDisable() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var keys = mock(KeyManager.class); var work = new ArrayList<Runnable>();
        when(config.autoArenaRecording()).thenReturn(true);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config); set(plugin, "keyManager", keys);
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class);
             var captures = mockConstruction(DroRecorderCapture.class))
        {
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(OUTSIDE);
            // 1.3.5: an enabled plugin records outside the arenas too (it used to wait for an arena).
            plugin.startUp(); work.remove(0).run(); assertTrue(plugin.hasSession());
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(INFERNO);
            plugin.onClientTick(null); assertTrue(plugin.hasSession());
            verify(sessions.constructed().get(0), times(1)).stop();
            boolean oldPause = Microbot.pauseAllScripts.getAndSet(true);
            try { for (int i = 0; i < 10; i++) { plugin.onClientTick(null); plugin.onGameTick(null); } }
            finally { Microbot.pauseAllScripts.set(oldPause); }
            assertEquals(2, sessions.constructed().size()); verify(sessions.constructed().get(1), never()).stop();
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(OUTSIDE);
            plugin.onGameTick(null); verify(sessions.constructed().get(1), never()).stop();
            plugin.onGameTick(null); verify(sessions.constructed().get(1), times(1)).stop();
            assertTrue(plugin.hasSession(), "Back outside, the plain recording resumes"); assertEquals(3, sessions.constructed().size());
            plugin.shutDown(); verify(sessions.constructed().get(2), times(1)).stop(); verify(captures.constructed().get(1), times(1)).close();
        }
        finally { plugin.shutDown(); }
    }

    @Test void explicitStartRecordsOutsideArenasWithoutChangingAutomaticPreference() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var work = new ArrayList<Runnable>();
        when(config.autoArenaRecording()).thenReturn(true);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
        set(plugin, "keyManager", mock(KeyManager.class));
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class); var captures = mockConstruction(DroRecorderCapture.class))
        {
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(OUTSIDE);
            plugin.requestManualRecording(); plugin.startUp(); work.remove(0).run();
            assertTrue(plugin.hasSession());
            for (int i=0;i<4;i++) plugin.onGameTick(null);
            assertTrue(plugin.hasSession()); verify(sessions.constructed().get(0), never()).stop();
            plugin.shutDown(); verify(sessions.constructed().get(0)).stop();
            plugin.startUp(); work.remove(0).run();
            // 1.3.5: an enabled plugin records; the restored startup opens a new recording, not the old one.
            assertTrue(plugin.hasSession(), "An enabled plugin records outside arenas with the arena option on");
            assertEquals(2, sessions.constructed().size());
            assertTrue(config.autoArenaRecording());
        }
        finally { plugin.shutDown(); }
    }

    /** 1.3.5: with the arena option on, turning the plugin on outside an arena records, and a death there does not end it. */
    @Test void enabledPluginRecordsOutsideArenasWithTheArenaOptionOn() throws Exception
    {
        var plugin = new DroRecorderPlugin(); var client = mock(Client.class); var thread = mock(ClientThread.class);
        var config = mock(DroRecorderConfig.class); var work = new ArrayList<Runnable>(); var player = mock(Player.class);
        when(config.autoArenaRecording()).thenReturn(true); when(client.getLocalPlayer()).thenReturn(player);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        set(plugin, "client", client); set(plugin, "clientThread", thread); set(plugin, "config", config);
        set(plugin, "keyManager", mock(KeyManager.class));
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS);
             var sessions = mockConstruction(DroRecorderSession.class); var captures = mockConstruction(DroRecorderCapture.class))
        {
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(OUTSIDE);
            plugin.startUp(); work.remove(0).run();
            assertTrue(plugin.hasSession(), "Turning the plugin on outside an arena starts recording");
            plugin.onActorDeath(new ActorDeath(player));
            for (int i = 0; i < 4; i++) { plugin.onClientTick(null); plugin.onGameTick(null); }
            assertTrue(plugin.hasSession()); assertEquals(1, sessions.constructed().size());
            verify(sessions.constructed().get(0), never()).stop();
            plugin.requestStopRecording(); work.remove(0).run();
            for (int i = 0; i < 4; i++) { plugin.onClientTick(null); plugin.onGameTick(null); }
            assertFalse(plugin.hasSession(), "An explicit stop is not undone by the next tick");
        }
        finally { plugin.shutDown(); }
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
        assertFalse(new DroRecorderConfig(){}.autoArenaRecording()); assertFalse(new DroRecorderConfig(){}.showOverlay());
    }

    @Test void localOptInEnablesRecorderOnlyAfterArenaEntry() throws Exception
    {
        var config = mock(ConfigManager.class); var manager = mock(PluginManager.class); var recorder = new DroRecorderPlugin();
        var client = mock(Client.class);
        when(config.getConfiguration(DroRecorderConfig.GROUP, "autoArenaRecording")).thenReturn("true");
        when(manager.getPlugins()).thenReturn(List.of(recorder));
        doAnswer(call -> { assertTrue(javax.swing.SwingUtilities.isEventDispatchThread()); return true; }).when(manager).startPlugin(recorder);
        try (var locations = mockStatic(DroAutomaticRecording.class, CALLS_REAL_METHODS))
        {
            for (var location : List.of(UNKNOWN, OUTSIDE, LOGGED_OUT))
            {
                locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(location);
                DroRecorderPlugin.startAutomaticIfConfigured(config, manager, client);
            }
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            verifyNoInteractions(manager);
            locations.when(() -> DroAutomaticRecording.location(client)).thenReturn(FIRECAPE);
            DroRecorderPlugin.startAutomaticIfConfigured(config, manager, client);
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            verify(manager).setPluginEnabled(recorder, true); verify(manager).startPlugin(recorder);
            when(manager.isActive(recorder)).thenReturn(true);
            DroRecorderPlugin.startAutomaticIfConfigured(config, manager, client);
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            verify(manager, times(1)).startPlugin(recorder);
        }
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

    @Test void defaultAndWithdrawnOptInsDoNotEnableRecorder() throws Exception
    {
        var config = mock(ConfigManager.class); var manager = mock(PluginManager.class);
        DroRecorderPlugin.startAutomaticIfConfigured(config, manager, null);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            when(config.getConfiguration(DroRecorderConfig.GROUP, "autoArenaRecording")).thenReturn("true");
            DroRecorderPlugin.startAutomaticIfConfigured(config, manager, null);
            when(config.getConfiguration(DroRecorderConfig.GROUP, "autoArenaRecording")).thenReturn("false");
        });
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        verifyNoInteractions(manager);
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
}
