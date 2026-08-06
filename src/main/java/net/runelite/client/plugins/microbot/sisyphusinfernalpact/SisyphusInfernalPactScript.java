package net.runelite.client.plugins.microbot.sisyphusinfernalpact;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.sisyphusinfernalpact.engine.YamaEngine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Execution loop for Sisyphus: Infernal Pact.
 * Schedules the YamaEngine at 200-400 ms for near-instant stone hops.
 */
@Slf4j
public class SisyphusInfernalPactScript extends Script {

    private final YamaEngine yamaEngine = new YamaEngine();

    public boolean run(SisyphusInfernalPactConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.actionCooldownActive = false;

        mainScheduledFuture = scheduledExecutorService.schedule(
                () -> scheduleNextTick(config), 0, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        yamaEngine.reset();
        log.info("[Sisyphus: Infernal Pact] Script shutdown.");
    }

    private void scheduleNextTick(SisyphusInfernalPactConfig config) {
        int delayMs = ThreadLocalRandom.current().nextInt(200, 401);

        mainScheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (BreakHandlerScript.isBreakActive()) return;

                if (!yamaEngine.tick(config)) {
                    log.info("[Sisyphus: Infernal Pact] Engine stopped — shutting down.");
                    shutdown();
                    return;
                }

            } catch (Exception e) {
                log.error("[Sisyphus: Infernal Pact] Error in tick: {}", e.getMessage(), e);
            } finally {
                if (isRunning()) {
                    scheduleNextTick(config);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
