package net.runelite.client.plugins.microbot.hsblackjack;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class HSBlackJackHopResultTest {

    @Test
    void refusedHopIsAttemptedOnceAndReportedAsNotHopped() throws Exception {
        assertEquals(Boolean.FALSE, hopWorld(false));
    }

    @Test
    void successfulHopIsReportedAsHopped() throws Exception {
        assertEquals(Boolean.TRUE, hopWorld(true));
    }

    private static Object hopWorld(boolean hopResult) throws Exception {
        HSBlackJackScript script = new HSBlackJackScript(mock(HSBlackJackPlugin.class), mock(HSBlackJackConfig.class));
        Client client = mock(Client.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Login> login = mockStatic(Login.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            player.when(Rs2Player::isMember).thenReturn(true);
            login.when(() -> Login.getRandomWorld(true)).thenReturn(302);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(hopResult);

            Method hopWorld = HSBlackJackScript.class.getDeclaredMethod("hopWorld");
            hopWorld.setAccessible(true);
            Object result = hopWorld.invoke(script);

            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(1));
            return result;
        }
    }
}
