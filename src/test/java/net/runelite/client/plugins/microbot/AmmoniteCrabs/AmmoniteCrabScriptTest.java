package net.runelite.client.plugins.microbot.AmmoniteCrabs;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class AmmoniteCrabScriptTest {

    private final WorldPoint fightLocation = new WorldPoint(3733, 3846, 0);

    @Test
    void nullPlayerLocationIsNotWithinRange() {
        assertFalse(AmmoniteCrabScript.isWithin(null, fightLocation, 3));
    }

    @Test
    void playerLocationWithinRangeIsDetected() {
        assertTrue(AmmoniteCrabScript.isWithin(new WorldPoint(3735, 3846, 0), fightLocation, 3));
        assertFalse(AmmoniteCrabScript.isWithin(new WorldPoint(3740, 3846, 0), fightLocation, 3));
    }

    @Test
    void nullLocationDetectsNoOtherPlayerWithoutQueryingPlayers() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertFalse(AmmoniteCrabScript.otherPlayerDetected(null));
            player.verify(() -> Rs2Player.getPlayers(any()), never());
        }
    }

    @Test
    void keyIsPressedWhenIdleLogoutIsNear() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(true);

            assertTrue(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verify(() -> Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE));
        }
    }

    @Test
    void noKeyIsPressedWhileInputIsRecent() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class)) {
            player.when(() -> Rs2Player.checkIdleLogout(8000L)).thenReturn(false);

            assertFalse(AmmoniteCrabScript.preventIdleLogout(8000L));
            keyboard.verifyNoInteractions();
        }
    }

    @Test
    void idleMarginKeepsTheKeyPressWellInsideTheIdleWindow() {
        assertTrue(AmmoniteCrabScript.MIN_IDLE_MARGIN_TICKS >= 7500);
        assertTrue(AmmoniteCrabScript.MAX_IDLE_MARGIN_TICKS >= AmmoniteCrabScript.MIN_IDLE_MARGIN_TICKS);
    }

    @Test
    void resetStartsAfterShortCountdownOnceAggroIsLost() {
        assertFalse(AmmoniteCrabScript.afkCountdownDone(0));
        assertFalse(AmmoniteCrabScript.afkCountdownDone(AmmoniteCrabScript.AFK_LOOPS_BEFORE_RESET - 1));
        assertTrue(AmmoniteCrabScript.afkCountdownDone(AmmoniteCrabScript.AFK_LOOPS_BEFORE_RESET));
        assertTrue(AmmoniteCrabScript.AFK_LOOPS_BEFORE_RESET <= 3);
    }
}
