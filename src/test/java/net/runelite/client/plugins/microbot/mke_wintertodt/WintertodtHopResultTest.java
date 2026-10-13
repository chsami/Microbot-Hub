package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class WintertodtHopResultTest {

    @Test
    void refusedHopKeepsTheScriptRunningAndRetriesAfterACooldown() throws Exception {
        MKE_WintertodtScript script = new MKE_WintertodtScript();
        Client client = mock(Client.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<BreakHandlerScript> breakHandler = mockStatic(BreakHandlerScript.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            player.when(Rs2Player::getWorld).thenReturn(301);
            random.when(() -> Rs2Random.between(anyInt(), anyInt())).thenReturn(0, 1, 2, 3);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(false);

            assertTrue(ensureWintertodtWorld(script));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(1));
            assertFalse((Boolean) field(script, "worldChecked"));

            assertTrue(ensureWintertodtWorld(script));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(1));

            setField(script, "lastRefusedHopAt", System.currentTimeMillis() - 5_000);
            microbot.when(() -> Microbot.hopToWorld(anyInt())).thenReturn(true);
            assertTrue(ensureWintertodtWorld(script));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(2));
            assertEquals(Boolean.TRUE, field(script, "worldChecked"));
        }
    }

    private static boolean ensureWintertodtWorld(MKE_WintertodtScript script) throws Exception {
        Method method = MKE_WintertodtScript.class.getDeclaredMethod("ensureWintertodtWorldSafely");
        method.setAccessible(true);
        return (Boolean) method.invoke(script);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
