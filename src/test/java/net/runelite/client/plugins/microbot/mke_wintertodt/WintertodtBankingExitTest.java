package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.location.WintertodtLocationManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class WintertodtBankingExitTest {

    @AfterEach
    void resetWaitingFlag() throws Exception {
        setStaticField("waitingForRoundEnd", false);
    }

    @Test
    void outOfFoodAndLowWarmthLeavesWithoutWaitingForRoundEnd() throws Exception {
        MKE_WintertodtScript script = new MKE_WintertodtScript();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<WintertodtLocationManager> location = mockStatic(WintertodtLocationManager.class)) {
            widget.when(() -> Rs2Widget.getChildWidgetText(396, 6)).thenReturn("Points<br>650");
            location.when(WintertodtLocationManager::isInsideGameRoom).thenReturn(true);
            location.when(() -> WintertodtLocationManager.attemptLeaveWintertodt(anyBoolean())).thenReturn(false);

            handleBankingState(script, gameState(true));

            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(false), times(1));
            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(true), never());
            assertFalse((Boolean) staticField("waitingForRoundEnd"));
            assertEquals(1, field(script, "consecutiveExitFailures"));
        }
    }

    @Test
    void waitingForRoundEndIsNotCountedAsAnExitFailure() throws Exception {
        MKE_WintertodtScript script = new MKE_WintertodtScript();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<WintertodtLocationManager> location = mockStatic(WintertodtLocationManager.class)) {
            widget.when(() -> Rs2Widget.getChildWidgetText(396, 6)).thenReturn("Points<br>650");
            location.when(WintertodtLocationManager::isInsideGameRoom).thenReturn(true);
            location.when(() -> WintertodtLocationManager.attemptLeaveWintertodt(anyBoolean())).thenReturn(false);

            Object state = gameState(false);
            for (int i = 0; i < 25; i++) {
                handleBankingState(script, state);
            }

            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(true), times(25));
            assertTrue((Boolean) staticField("waitingForRoundEnd"));
            assertEquals(0, field(script, "consecutiveExitFailures"));
            player.verify(Rs2Player::logout, never());
        }
    }

    private static Object gameState(boolean lowAndOutOfFood) throws Exception {
        Class<?> type = Class.forName(MKE_WintertodtScript.class.getName() + "$GameState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object state = constructor.newInstance();
        Field field = type.getDeclaredField("lowAndOutOfFood");
        field.setAccessible(true);
        field.set(state, lowAndOutOfFood);
        return state;
    }

    private static void handleBankingState(MKE_WintertodtScript script, Object gameState) throws Exception {
        Method method = MKE_WintertodtScript.class.getDeclaredMethod("handleBankingState", gameState.getClass());
        method.setAccessible(true);
        method.invoke(script, gameState);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object staticField(String name) throws Exception {
        Field field = MKE_WintertodtScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = MKE_WintertodtScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
