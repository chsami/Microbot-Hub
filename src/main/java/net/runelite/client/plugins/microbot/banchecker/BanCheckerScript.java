package net.runelite.client.plugins.microbot.banchecker;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class BanCheckerScript extends Script {
    @Inject
    private Client client;
    @Inject
    private ConfigManager configManager;
    public static boolean isBanned = false;
    public int bannedLoginIndex = 14;
    public boolean shouldRun = true;

    public boolean run(BanCheckerConfig config) {
        if (config.enabled() && shouldRun) {
            GameState gameState = client.getGameState();

            boolean banned = gameState == GameState.LOGIN_SCREEN
                    && Microbot.getClient().getLoginIndex() == bannedLoginIndex;

            // Save value into config so other plugins can read it
            if (config.writeConfig()) {
                configManager.setConfiguration("banchecker", "isBanned", banned);
            }

            if (banned && !isBanned) {
                isBanned = true;
                stopLoop();
                Microbot.log("Your account is banned.");
            }
        }
        return true;
    }

    public void loopCheck(BanCheckerConfig config)
    {
        shouldRun = true;
        if (config.loopCheck() && config.enabled()) {
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                try {
                    run(config);
                } catch (Exception ex) {
                    Microbot.log("Error in BanChecker: " + ex.getMessage());
                }
            }, 0, 600, TimeUnit.MILLISECONDS);
        }
    }

    public void stopLoop()
    {
        shouldRun = false;
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
            mainScheduledFuture = null;
        }
    }
}
