package net.runelite.client.plugins.microbot.banksshopper;

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

class BanksShopperHopResultTest {

    @Test
    void refusedHopIsAttemptedOnceAndReportedAsNotHopped() throws Exception {
        assertEquals(Boolean.FALSE, hopWorld(false));
    }

    @Test
    void successfulHopIsReportedAsHopped() throws Exception {
        assertEquals(Boolean.TRUE, hopWorld(true));
    }

    private static Object hopWorld(boolean hopResult) throws Exception {
        BanksShopperScript script = new BanksShopperScript(mock(BanksShopperPlugin.class));
        Client client = mock(Client.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Login> login = mockStatic(Login.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            player.when(Rs2Player::isMember).thenReturn(false);
            login.when(() -> Login.getRandomWorld(false)).thenReturn(301);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(hopResult);

            Method hopWorld = BanksShopperScript.class.getDeclaredMethod("hopWorld");
            hopWorld.setAccessible(true);
            Object result = hopWorld.invoke(script);

            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(1));
            return result;
        }
    }
}
