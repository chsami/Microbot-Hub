package net.runelite.client.plugins.microbot.thieving;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.security.Login;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ThievingEscapeHopTest {

    private static final int TARGET_WORLD = 420;

    @Test
    void refusedEscapeHopStaysPendingAndIsRetriedUntilItLands() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(() -> Login.getRandomWorld(anyBoolean(), any())).thenReturn(TARGET_WORLD);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(false);

            invoke(script, "requestEscapeHop");
            assertFalse(call(script, "attemptEscapeHop"));
            assertTrue(call(script, "isEscapeHopPending"));

            assertFalse(call(script, "attemptEscapeHop"));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), times(1));
            assertTrue(call(script, "isEscapeHopPending"));

            setField(script, "lastHopAt", System.currentTimeMillis() - 15_000);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(true);
            assertTrue(call(script, "attemptEscapeHop"));

            microbot.verify(() -> Microbot.hopToWorld(TARGET_WORLD), times(2));
            assertFalse(call(script, "isEscapeHopPending"));
        }
    }

    @Test
    void escapeHopIsDroppedOnceTheRetryWindowRunsOut() throws Exception {
        ThievingScript script = script(ThievingNpc.VYRES);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(() -> Login.getRandomWorld(anyBoolean(), any())).thenReturn(TARGET_WORLD);
            microbot.when(() -> Microbot.hopToWorld(TARGET_WORLD)).thenReturn(false);

            invoke(script, "requestEscapeHop");
            setField(script, "escapeHopUntil", System.currentTimeMillis() - 1);

            assertTrue(call(script, "attemptEscapeHop"));
            assertFalse(call(script, "isEscapeHopPending"));
        }
    }

    @Test
    void escapeHopIsDroppedWhenEscapingIsTurnedOff() throws Exception {
        ThievingConfig config = mock(ThievingConfig.class);
        when(config.THIEVING_NPC()).thenReturn(ThievingNpc.VYRES);
        when(config.escapeAttacking()).thenReturn(false);
        ThievingScript script = new ThievingScript(config, mock(ThievingPlugin.class));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            invoke(script, "requestEscapeHop");
            assertTrue(call(script, "attemptEscapeHop"));
            assertFalse(call(script, "isEscapeHopPending"));
            microbot.verify(() -> Microbot.hopToWorld(anyInt()), never());
        }
    }

    private static ThievingScript script(ThievingNpc npc) {
        ThievingConfig config = mock(ThievingConfig.class);
        when(config.THIEVING_NPC()).thenReturn(npc);
        when(config.escapeAttacking()).thenReturn(true);
        return new ThievingScript(config, mock(ThievingPlugin.class));
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static boolean call(Object target, String name) throws Exception {
        return (boolean) invoke(target, name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
