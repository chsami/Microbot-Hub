package net.runelite.client.plugins.microbot.eventdismiss;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class EventDismissScript {

    private static final long LOOP_DELAY_MS = 600;

    private final AtomicBoolean failureLogged = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public synchronized void start(List<BlockingEvent> events) {
        if (executor != null) {
            return;
        }
        List<BlockingEvent> handled = List.copyOf(events);
        failureLogged.set(false);
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "EventDismissScript");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> processBlockingEvents(handled), 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    void processBlockingEvents(List<BlockingEvent> events) {
        try {
            if (!Microbot.isLoggedIn() || !Rs2Player.hasCompletedTutorialIsland()) {
                return;
            }
            if (InputArbiter.isHuman() || Microbot.pauseAllScripts.get()) {
                return;
            }
            if (events.stream().anyMatch(BlockingEvent::validate)) {
                Microbot.getBlockingEventManager().shouldBlockAndProcess();
            }
            failureLogged.set(false);
        } catch (Exception ex) {
            if (failureLogged.compareAndSet(false, true)) {
                log.warn("Random Event Handler could not check for random events ({}); it will keep retrying. If this repeats, turn the Random Event Handler off and on.", ex.getMessage());
            }
            log.debug("Random Event Handler pass failed", ex);
        }
    }
}
