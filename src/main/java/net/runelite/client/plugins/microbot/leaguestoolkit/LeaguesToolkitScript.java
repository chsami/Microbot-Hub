package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LeaguesToolkitScript extends Script {

    private static final int[] ARROW_KEYS = {
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN
    };

    @Getter
    private final GemCutter gemCutter = new GemCutter();

    private boolean gemCutterWasEnabled = false;

    public boolean run(LeaguesToolkitConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;

                if (config.enableAntiAfk()) {
                    runAntiAfk(config);
                }

                if (config.enableGemCutter()) {
                    if (!gemCutterWasEnabled) {
                        gemCutter.reset();
                        gemCutterWasEnabled = true;
                    }
                    gemCutter.tick(config);
                } else {
                    gemCutterWasEnabled = false;
                }
            } catch (Exception ex) {
                log.error("LeaguesToolkitScript loop error", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void runAntiAfk(LeaguesToolkitConfig config) {
        int minBuffer = Math.min(config.antiAfkBufferMin(), config.antiAfkBufferMax());
        int maxBuffer = Math.max(config.antiAfkBufferMin(), config.antiAfkBufferMax());
        long buffer = Rs2Random.between(minBuffer, maxBuffer);

        if (!Rs2Player.checkIdleLogout(buffer)) return;

        switch (config.antiAfkMethod()) {
            case BACKSPACE:
                Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
                break;
            case CAMERA_ROTATION:
                Rs2Camera.setYaw(Rs2Random.between(0, 2047));
                break;
            case RANDOM_ARROW_KEY:
            default:
                Rs2Keyboard.keyPress(ARROW_KEYS[Rs2Random.between(0, ARROW_KEYS.length - 1)]);
                break;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
