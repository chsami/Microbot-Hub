package net.runelite.client.plugins.microbot.hsblackjack;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class HSBlackJackNullPlayerLocationTest {

    @Test
    void travelToStartSkipsTickWhenPlayerLocationIsNull() throws Exception {
        HSBlackJackScript script = new HSBlackJackScript(mock(HSBlackJackPlugin.class), mock(HSBlackJackConfig.class));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            invoke(script, "handleTravelToStart");

            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            assertEquals("TRAVEL_TO_START", phase(script));
        }
    }

    @Test
    void travelToStartWalksWhenPlayerIsFarAway() throws Exception {
        HSBlackJackScript script = new HSBlackJackScript(mock(HSBlackJackPlugin.class), mock(HSBlackJackConfig.class));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3200, 3200, 0));

            invoke(script, "handleTravelToStart");

            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), times(1));
            assertEquals("TRAVEL_TO_START", phase(script));
        }
    }

    private static void invoke(HSBlackJackScript script, String name) throws Exception {
        Method method = HSBlackJackScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(script);
    }

    private static String phase(HSBlackJackScript script) throws Exception {
        Field field = HSBlackJackScript.class.getDeclaredField("phase");
        field.setAccessible(true);
        return ((Enum<?>) field.get(script)).name();
    }
}
