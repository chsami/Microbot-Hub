package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.event.KeyEvent;

public class NeverLogoutScript {
    // checkIdleLogout(delay) fires when min(mouseIdleTicks, keyboardIdleTicks) >= idleTimeout - delay.
    // Idle ticks are client cycles (~50/sec) and idleTimeout is ~15000 (~5 minutes) on live worlds,
    // so a delay of 7500-10000 presses the key at ~100-150s of idle time, leaving a 150-200s safety
    // margin before the logout boundary. The old 3000-5000 range fired only 60-100s before logout,
    // which a single stalled/late game tick window (world hop, lag, busy client thread) could miss.
    private static long randomDelay = Rs2Random.between(7500, 10000);

    public static void onGameTick(GameTick event) {
        if (Rs2Player.checkIdleLogout(randomDelay)) {
            randomDelay = Rs2Random.between(7500, 10000);
            Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
        }
    }
}
