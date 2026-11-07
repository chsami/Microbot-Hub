package net.runelite.client.plugins.microbot.eventdismiss;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

@Slf4j
public class EventDismissScript extends Script {

    private static final long LOOP_DELAY_MS = 600;

    public boolean start() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            return true;
        }
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::processBlockingEvents, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    void processBlockingEvents() {
        try {
            if (!Microbot.isLoggedIn() || !Rs2Player.hasCompletedTutorialIsland()) {
                return;
            }
            Microbot.getBlockingEventManager().shouldBlockAndProcess();
        } catch (Exception ex) {
            log.warn("Random Event Handler loop failed", ex);
        }
    }
}
