package net.runelite.client.plugins.microbot.drorecorder;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.MouseManager;

/**
 * Recorder-owned arena lifecycle and programmatic recording API.
 * Tracks entry into Inferno, Colosseum, and Fight Caves independently of scripts.
 */
@Singleton
@Slf4j
public final class DroAutomaticRecording
{
    public enum Location { UNKNOWN, OUTSIDE, LOGGED_OUT, INFERNO, COLOSSEUM, FIRECAPE }

    private final Consumer<String> start;
    private final Runnable stop;
    private Location current;
    private Location finished;
    private int outsideTicks;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private Gson gson;
    @Inject private EventBus eventBus;
    @Inject private MouseManager mouseManager;
    @Inject private net.runelite.client.game.WorldService worldService;

    private static final long WORLD_PING_SECONDS = 5;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    public DroAutomaticRecording()
    {
        this(null, null);
    }

    public DroAutomaticRecording(Consumer<String> start, Runnable stop)
    {
        this.start = start;
        this.stop = stop;
    }

    void observe(Location location, boolean gameTick)
    {
        if (isArena(location))
        {
            outsideTicks = 0;
            if (location == finished) { return; }
            if (location == current) { return; }
            close();
            if (start != null)
            {
                start.accept(location.name().toLowerCase(java.util.Locale.ROOT));
            }
            current = location;
        }
        else if (location == Location.LOGGED_OUT)
        {
            close();
        }
        else if (location == Location.UNKNOWN)
        {
            outsideTicks = 0;
        }
        else if ((current != null || finished != null) && gameTick && ++outsideTicks >= 2)
        {
            close();
        }
    }

    boolean recording(Location location)
    {
        return current == location;
    }

    public static boolean isArena(Location location)
    {
        return location == Location.INFERNO || location == Location.COLOSSEUM || location == Location.FIRECAPE;
    }

    /** Suppress automatic restart for the current arena after an explicit recorder stop. */
    void suppressUntilExit(Location location)
    {
        if (!isArena(location))
        {
            close();
            return;
        }
        current = null;
        finished = location;
        outsideTicks = 0;
    }

    /** Do not create another session while the ended run is still in its arena. */
    void finish()
    {
        if (current == null) { return; }
        finished = current;
        current = null;
        outsideTicks = 0;
        if (stop != null) { stop.run(); }
    }

    public void close()
    {
        boolean wasRecording = current != null;
        current = null;
        finished = null;
        outsideTicks = 0;
        if (wasRecording && stop != null)
        {
            stop.run();
        }
    }

    public static boolean isInferno(WorldPoint tile)
    {
        return tile.getPlane() == 0 && tile.getX() >= 2240 && tile.getX() < 2304 && tile.getY() >= 5312 && tile.getY() < 5376;
    }

    public static boolean isColosseum(WorldPoint tile)
    {
        return tile.getPlane() == 0 && tile.getX() >= 1792 && tile.getX() < 1856 && tile.getY() >= 3072 && tile.getY() < 3136;
    }

    public static boolean isFirecape(WorldPoint tile)
    {
        return tile.getPlane() == 0 && tile.getRegionID() == 9551;
    }

    /** Client thread only. Temporary loading does not masquerade as an arena exit. */
    public static Location location(Client client)
    {
        if (client == null) { return Location.UNKNOWN; }
        GameState state = client.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR) { return Location.LOGGED_OUT; }
        if (state != GameState.LOGGED_IN || client.getLocalPlayer() == null || client.getTopLevelWorldView() == null) { return Location.UNKNOWN; }
        if (!client.getTopLevelWorldView().isInstance()) { return Location.OUTSIDE; }
        if (client.getLocalPlayer().getLocalLocation() == null) { return Location.UNKNOWN; }
        WorldPoint tile = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
        if (tile == null) { return Location.UNKNOWN; }
        if (isInferno(tile)) { return Location.INFERNO; }
        if (isColosseum(tile)) { return Location.COLOSSEUM; }
        if (isFirecape(tile)) { return Location.FIRECAPE; }
        return Location.OUTSIDE;
    }

    public static Path directory(Path root, String activity, String stamp, String unique)
    {
        if (!java.util.Set.of("inferno", "colosseum", "firecape").contains(activity))
        {
            throw new IllegalArgumentException("Unknown recording activity");
        }
        return root.resolve(activity + "-" + stamp + "-" + unique);
    }

    public Recording open(String activity)
    {
        return open(activity, null);
    }

    /** {@code engineVersion} names the local engine that drives this run, e.g. "DroColosseum 1.0.5". */
    public Recording open(String activity, String engineVersion)
    {
        Path dir = directory(RuneLite.RUNELITE_DIR.toPath().resolve("dro-recorder"), activity, STAMP.format(Instant.now()), UUID.randomUUID().toString().substring(0, 8));
        Recording recording = new Recording(dir, engineVersion);
        if (clientThread != null)
        {
            clientThread.invokeLater(recording::start);
        }
        return recording;
    }

    public final class Recording implements AutoCloseable
    {
        private final Path directory;
        private DroRecorderSession session;
        private DroRecorderCapture capture;
        private java.util.concurrent.ScheduledExecutorService pinger;
        private volatile int world;
        private volatile boolean closed;
        private boolean finished;
        private final String engineVersion;

        private Recording(Path directory, String engineVersion)
        {
            this.directory = directory;
            this.engineVersion = engineVersion;
        }

        private synchronized void start()
        {
            if (closed) return;
            try
            {
                session = new DroRecorderSession(client, gson, new DroRecorderConfig(){}, directory);
                session.engineVersion(engineVersion);
                session.start();
                capture = new DroRecorderCapture(session, eventBus, mouseManager, () -> {});
                capture.start();
                startPing();
                log.info("[DRO recording] Started {}", directory);
            }
            catch (RuntimeException failure)
            {
                if (capture != null) capture.close();
                if (session != null) session.stop();
                closed = true;
                log.warn("[DRO recording] Start failed type={}", failure.getClass().getSimpleName());
            }
        }

        /** Game-world round trip every 5 s, as Dro's recorder: separates server lag from engine timing. Never fails a recording. */
        private void startPing()
        {
            try
            {
                world = client != null ? client.getWorld() : 0;
                pinger = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "dro-recorder-ping");
                    t.setDaemon(true);
                    return t;
                });
                pinger.scheduleWithFixedDelay(this::ping, WORLD_PING_SECONDS, WORLD_PING_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (RuntimeException unavailable)
            {
                if (pinger != null) { pinger.shutdownNow(); pinger = null; }
            }
        }

        /** Off the client thread: the world list and the ping itself both block. Evidence only; failures are ignored. */
        private void ping()
        {
            try
            {
                DroRecorderSession current = session;
                int id = world;
                if (closed || current == null || id <= 0 || worldService == null) return;
                var worlds = worldService.getWorlds();
                var found = worlds == null ? null : worlds.findWorld(id);
                if (found != null)
                {
                    current.recordWorldPing(found.getId(), net.runelite.client.plugins.worldhopper.ping.Ping.ping(found));
                }
            }
            catch (RuntimeException unavailable) {}
        }

        public Path directory() { return directory; }

        @Override
        public void close()
        {
            closed = true;
            if (clientThread != null)
            {
                clientThread.invokeLater(this::finish);
            }
            else
            {
                finish();
            }
        }

        private synchronized void finish()
        {
            if (finished) return;
            finished = true;
            if (pinger != null) { pinger.shutdownNow(); pinger = null; }
            if (capture != null) capture.close();
            if (session != null) session.stop();
            log.info("[DRO recording] Stopped {}", directory);
        }
    }
}
