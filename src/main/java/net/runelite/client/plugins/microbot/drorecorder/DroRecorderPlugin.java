package net.runelite.client.plugins.microbot.drorecorder;


import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ClientUI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
    name = PluginConstants.DRO + "Recorder",
    description = "Passive gameplay and arena recording for AI analysis",
    authors = {"droplugins"},
    version = "1.3.6",
    minClientVersion = "2.1.32",
    tags = {"dro", "recorder", "telemetry", "analytics", "arena", "microbot"},
    iconUrl = "https://chsami.github.io/Microbot-Hub/DroRecorderPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/DroRecorderPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Singleton
@Slf4j
public class DroRecorderPlugin extends Plugin
{
    public static final String NAME = "DRO's Recorder";
    private static final Path OUTPUT_ROOT = RuneLite.RUNELITE_DIR.toPath().resolve("dro-recorder");

    @Inject
    private Client client;

    @Inject
    private Gson gson;

    @Inject
    private DroRecorderConfig config;

    @Inject
    private DroRecorderOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ClientThread clientThread;

    private volatile DroRecorderSession session;
    private volatile boolean running;
    private volatile long generation;
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    @Inject private net.runelite.client.game.WorldService worldService;
    private static final long WORLD_PING_SECONDS = 5;
    private java.util.concurrent.ScheduledExecutorService pinger;
    private DroRecorderCapture capture;
    private volatile int pingWorldId;
    private DroRecorderSession focusSession;
    private Canvas focusCanvas;
    private Window focusWindow;
    private final FocusAdapter canvasFocusListener = new FocusAdapter()
    {
        @Override
        public void focusGained(FocusEvent event) { recordFocus("canvas", true, "change", event.isTemporary()); }
        @Override
        public void focusLost(FocusEvent event) { recordFocus("canvas", false, "change", event.isTemporary()); }
    };
    private final WindowAdapter windowFocusListener = new WindowAdapter()
    {
        @Override
        public void windowGainedFocus(WindowEvent event) { recordFocus("window", true, "change", false); }
        @Override
        public void windowLostFocus(WindowEvent event) { recordFocus("window", false, "change", false); }
    };

    /** Off the client thread: the world list and the ping itself both block. */
    private void pingWorld()
    {
        try
        {
            DroRecorderSession current = session;
            int worldId = pingWorldId;
            if (current == null || worldId == 0) return;
            var worlds = worldService.getWorlds();
            var world = worlds == null ? null : worlds.findWorld(worldId);
            if (world == null) return;
            current.recordWorldPing(world.getId(), net.runelite.client.plugins.worldhopper.ping.Ping.ping(world));
        }
        catch (RuntimeException unavailable)
        {
            // A ping is evidence only; a failure never affects recording or input.
        }
    }

    private void recordFocus(String scope, boolean focused, String reason, boolean temporary)
    {
        DroRecorderSession current = session;
        if (current != null) current.recordFocus(scope, focused, reason, temporary);
    }

    // AWT owns focus. Attach on its thread, and reattach if RuneLite replaces the canvas.
    private void attachFocusListeners(Canvas canvas, DroRecorderSession expected)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (session != expected || expected == null) return;
            boolean newSession = focusSession != expected;
            focusSession = expected;
            if (focusCanvas != canvas)
            {
                if (focusCanvas != null) focusCanvas.removeFocusListener(canvasFocusListener);
                focusCanvas = canvas;
                if (canvas != null)
                {
                    canvas.addFocusListener(canvasFocusListener);
                    recordFocus("canvas", canvas.isFocusOwner(), "baseline", false);
                }
            }
            else if (newSession && canvas != null)
                recordFocus("canvas", canvas.isFocusOwner(), "baseline", false);
            Window window = ClientUI.getFrame();
            if (focusWindow != window)
            {
                if (focusWindow != null) focusWindow.removeWindowFocusListener(windowFocusListener);
                focusWindow = window;
                if (window != null)
                {
                    window.addWindowFocusListener(windowFocusListener);
                    recordFocus("window", window.isFocused(), "baseline", false);
                }
            }
            else if (newSession && window != null)
                recordFocus("window", window.isFocused(), "baseline", false);
        });
    }

    private void detachFocusListeners()
    {
        SwingUtilities.invokeLater(() ->
        {
            // A rapid restart may already have reused the listeners.
            if (session != null) return;
            if (focusCanvas != null) focusCanvas.removeFocusListener(canvasFocusListener);
            if (focusWindow != null) focusWindow.removeWindowFocusListener(windowFocusListener);
            focusCanvas = null;
            focusWindow = null;
            focusSession = null;
        });
    }

    private final HotkeyListener importantHotkey = new HotkeyListener(() -> config.importantHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            annotate("IMPORTANT");
        }
    };

    private final HotkeyListener safespotHotkey = new HotkeyListener(() -> config.safespotHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            annotate("SAFESPOT");
        }
    };

    private final HotkeyListener mistakeHotkey = new HotkeyListener(() -> config.mistakeHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            annotate("MISTAKE");
        }
    };

    private final HotkeyListener pauseHotkey = new HotkeyListener(() -> config.pauseHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            clientThread.invokeLater(() ->
            {
                DroRecorderSession current = session;
                if (current != null)
                {
                    current.togglePaused();
                }
            });
        }
    };

    @Provides
    DroRecorderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DroRecorderConfig.class);
    }

    public boolean isRecording() { return session != null; }

    /** Explicit stop: saves the current recording; the next recording starts when the plugin is turned on again. */
    public void requestStopRecording()
    {
        if (!running) return;
        long expected = generation;
        clientThread.invokeLater(() -> {
            if (!running || generation != expected) return;
            stopSession();
        });
    }

    /** The plugin records for as long as it is enabled. */
    @Override
    protected void startUp()
    {
        running = true;
        long expected = ++generation;
        keyManager.registerKeyListener(importantHotkey);
        keyManager.registerKeyListener(safespotHotkey);
        keyManager.registerKeyListener(mistakeHotkey);
        keyManager.registerKeyListener(pauseHotkey);
        clientThread.invokeLater(() -> {
            if (!running || generation != expected) return;
            stopSession();
            startSession();
        });
        pinger = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "dro-recorder-ping"); t.setDaemon(true); return t; });
        pinger.scheduleWithFixedDelay(this::pingWorld, WORLD_PING_SECONDS, WORLD_PING_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Client thread only. External scripts never own this session. */
    private synchronized void startSession()
    {
        if (!running || session != null) return;
        try
        {
            session = new DroRecorderSession(client, gson, config);
            session.start();
            attachFocusListeners(client.getCanvas(), session);
            capture = new DroRecorderCapture(session, eventBus, mouseManager, () -> attachFocusListeners(client.getCanvas(), session));
            capture.start();
            if (config.showOverlay() && overlayManager != null && overlay != null)
            {
                overlayManager.add(overlay);
            }
            log.info("DroRecorder started: {}", session.getOutputDirectory());
        }
        catch (RuntimeException failed)
        {
            stopSession();
            log.warn("DroRecorder could not start: {}", failed.getClass().getSimpleName());
        }
    }

    private synchronized void stopSession()
    {
        if (overlayManager != null && overlay != null)
        {
            overlayManager.remove(overlay);
        }
        if (capture != null) { capture.close(); capture = null; }
        DroRecorderSession stopping = session;
        session = null;
        pingWorldId = 0;
        if (stopping != null) { log.info("DroRecorder stopping: {}", stopping.getOutputDirectory()); stopping.stop(); }
        detachFocusListeners();
    }

    @Subscribe public void onClientTick(ClientTick event)
    {
        pingWorldId = session != null && client.getGameState() == net.runelite.api.GameState.LOGGED_IN ? client.getWorld() : 0;
    }

    @Override
    protected void shutDown()
    {
        running = false; generation++;
        keyManager.unregisterKeyListener(importantHotkey);
        keyManager.unregisterKeyListener(safespotHotkey);
        keyManager.unregisterKeyListener(mistakeHotkey);
        keyManager.unregisterKeyListener(pauseHotkey);
        if (pinger != null) { pinger.shutdownNow(); pinger = null; }
        stopSession();
    }

    @Subscribe
    public void onClientShutdown(net.runelite.client.events.ClientShutdown event)
    {
        DroRecorderSession closing = session;
        shutDown();
        // Let the existing writer drain off-thread before the process exits.
        if (closing != null) event.waitFor(closing.getCompletion());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!DroRecorderConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if ("openOutputFolder".equals(event.getKey()))
        {
            if (event.getNewValue() != null && !event.getNewValue().equals(event.getOldValue()))
            {
                openOutputFolder();
            }
            return;
        }

        if ("showOverlay".equals(event.getKey()))
        {
            if (config.showOverlay() && isRecording() && overlayManager != null && overlay != null)
            {
                overlayManager.add(overlay);
            }
            else if (overlayManager != null && overlay != null)
            {
                overlayManager.remove(overlay);
            }
        }
    }

    public static void openOutputFolder()
    {
        Thread opener = new Thread(() ->
        {
            try
            {
                Files.createDirectories(OUTPUT_ROOT);
                String absolutePath = OUTPUT_ROOT.toAbsolutePath().toString();
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                log.info("[Dro] Recorder opening output folder: {}", absolutePath);

                if (os.contains("mac"))
                {
                    if (runOpenCommand(new ProcessBuilder("/usr/bin/open", "-R", absolutePath)))
                    {
                        return;
                    }
                    if (runOpenCommand(new ProcessBuilder("/usr/bin/open", absolutePath)))
                    {
                        return;
                    }

                    String escaped = absolutePath.replace("\\", "\\\\").replace("\"", "\\\"");
                    runOpenCommand(new ProcessBuilder(
                        "/usr/bin/osascript",
                        "-e", "tell application \"Finder\" to reveal POSIX file \"" + escaped + "\"",
                        "-e", "tell application \"Finder\" to activate"));
                    return;
                }

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                {
                    Desktop.getDesktop().open(OUTPUT_ROOT.toFile());
                    return;
                }

                if (os.contains("win"))
                {
                    runOpenCommand(new ProcessBuilder("explorer.exe", absolutePath));
                }
                else
                {
                    runOpenCommand(new ProcessBuilder("xdg-open", absolutePath));
                }
            }
            catch (IOException | SecurityException e)
            {
                log.warn("Failed to open [Dro] Recorder output folder {}. Manual path: ~/.runelite/dro-recorder/", OUTPUT_ROOT, e);
            }
        }, "DroRecorder-OpenFolder");
        opener.setDaemon(true);
        opener.start();
    }

    private static boolean runOpenCommand(ProcessBuilder processBuilder)
    {
        try
        {
            Process process = processBuilder.redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS))
            {
                process.destroy();
                log.warn("[Dro] Recorder output-folder command timed out: {}", processBuilder.command());
                return false;
            }

            if (process.exitValue() == 0)
            {
                log.info("[Dro] Recorder output-folder command succeeded: {}", processBuilder.command());
                return true;
            }

            log.warn("[Dro] Recorder output-folder command failed with exit {}: {}",
                process.exitValue(), processBuilder.command());
            return false;
        }
        catch (IOException e)
        {
            log.warn("[Dro] Recorder could not start output-folder command: {}", processBuilder.command(), e);
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void annotate(String label)
    {
        clientThread.invokeLater(() ->
        {
            DroRecorderSession current = session;
            if (current != null)
            {
                current.annotate(label);
            }
        });
    }

    boolean hasSession()
    {
        return session != null;
    }

    boolean isPaused()
    {
        return session == null || session.isPaused();
    }

    long getEventCount()
    {
        return session == null ? 0 : session.getEventCount();
    }

    long getTickCount()
    {
        return session == null ? 0 : session.getTickCount();
    }

    long getDroppedLines()
    {
        return session == null ? 0 : session.getDroppedLines();
    }

    String getRuntimeText()
    {
        if (session == null)
        {
            return "00:00:00";
        }

        Duration d = Duration.ofMillis(System.currentTimeMillis() - session.getStartedAtMs());
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        long seconds = d.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
