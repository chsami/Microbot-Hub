package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.client.plugins.microbot.util.player.TerminalLogoutPrompt;

/**
 * Terminal logout after an arena run ends (death or completion), with the usual AutoLogin suppression.
 * Runs on its own daemon thread so it still completes when the arena plugin is stopped right after death.
 */
final class ArenaRunLogout {
    static final long RETRY_MILLIS = 600L;
    static final long GIVE_UP_MILLIS = 90_000L;
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dro-arena-logout");
        t.setDaemon(true);
        return t;
    });

    private final TerminalLogoutPrompt prompt;
    private final BooleanSupplier tick;
    private final ScheduledExecutorService executor;
    private final LongSupplier clock;
    private ScheduledFuture<?> task;
    private long startedAt;

    ArenaRunLogout() {
        // Main's prompt owns the observed logout and native AutoLogin suppression.
        this(new TerminalLogoutPrompt(), null, EXECUTOR, System::currentTimeMillis);
    }

    ArenaRunLogout(TerminalLogoutPrompt prompt, BooleanSupplier tick, ScheduledExecutorService executor, LongSupplier clock) {
        this.prompt = prompt;
        this.tick = tick == null ? prompt::tick : tick;
        this.executor = executor;
        this.clock = clock;
    }

    /** Starts once per run end; repeated calls while a logout is in progress are ignored. */
    synchronized void start(String message) {
        if (task != null && !task.isDone()) return;
        prompt.begin(message);
        startedAt = clock.getAsLong();
        task = executor.scheduleWithFixedDelay(this::step, 0, RETRY_MILLIS, TimeUnit.MILLISECONDS);
    }

    synchronized boolean running() { return task != null && !task.isDone(); }
    boolean endsSession() { return true; }

    void step() {
        boolean done;
        try { done = tick.getAsBoolean(); }
        catch (RuntimeException failed) { done = false; }
        synchronized (this) {
            if (done || clock.getAsLong() - startedAt >= GIVE_UP_MILLIS) {
                if (!done) prompt.reset();
                if (task != null) task.cancel(false);
            }
        }
    }
}
