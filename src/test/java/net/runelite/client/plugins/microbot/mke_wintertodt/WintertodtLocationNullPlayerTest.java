package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.location.WintertodtLocationManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class WintertodtLocationNullPlayerTest {

    private static final WorldPoint BANK = new WorldPoint(1640, 3944, 0);
    private static final WorldPoint GAME_ROOM = new WorldPoint(1630, 3982, 0);

    @Test
    void missingPlayerLocationIsNotAtWintertodt() throws Exception {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null, BANK);

            assertFalse(isAtWintertodt());
            assertTrue(isAtWintertodt());
        }
    }

    @Test
    void missingPlayerLocationIsNotInsideTheGameRoom() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null, GAME_ROOM, BANK);

            assertFalse(WintertodtLocationManager.isInsideGameRoom());
            assertTrue(WintertodtLocationManager.isInsideGameRoom());
            assertFalse(WintertodtLocationManager.isInsideGameRoom());
        }
    }

    @Test
    void missingPlayerLocationIsNotWithinRangeOfATarget() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null, BANK, GAME_ROOM);

            assertFalse(WintertodtLocationManager.isWithin(BANK, 10));
            assertTrue(WintertodtLocationManager.isWithin(BANK, 10));
            assertFalse(WintertodtLocationManager.isWithin(BANK, 10));
        }
    }

    @Test
    void leavingDoesNotReportOutsideWhilePlayerLocationIsMissing() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null, BANK);

            assertFalse(WintertodtLocationManager.attemptLeaveWintertodt());
            assertTrue(WintertodtLocationManager.attemptLeaveWintertodt());
            walker.verifyNoInteractions();
        }
    }

    private static boolean isAtWintertodt() throws Exception {
        Method method = WintertodtLocationManager.class.getDeclaredMethod("isAtWintertodt");
        method.setAccessible(true);
        return (boolean) method.invoke(new WintertodtLocationManager());
    }
}
