package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.location.WintertodtLocationManager;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WintertodtBankingExitTest {

    private Object previousConfig;
    private String previousStatus;

    @BeforeEach
    void installConfig() throws Exception {
        previousConfig = staticField("config");
        previousStatus = Microbot.status;
        MKE_WintertodtConfig config = mock(MKE_WintertodtConfig.class);
        when(config.food()).thenReturn(Rs2Food.SALMON);
        when(config.eatAtWarmthLevel()).thenReturn(45);
        setStaticField("config", config);
    }

    @AfterEach
    void restoreState() throws Exception {
        setStaticField("waitingForRoundEnd", false);
        setStaticField("config", previousConfig);
        Microbot.status = previousStatus;
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

            Object state = gameState(true, 20);
            for (int i = 0; i < 3; i++) {
                handleBankingState(script, state);
            }

            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(false), times(3));
            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(true), never());
            assertFalse((Boolean) staticField("waitingForRoundEnd"));
            assertEquals(3, field(script, "consecutiveExitFailures"));
            microbot.verify(() -> Microbot.log(
                    "Out of Salmon and warmth is 20% (at or below your Consume at Warmth Level of 45). "
                            + "Leaving now to stay alive, so this round's 650 points are lost. "
                            + "Raise Healing Items Per Trip to avoid this."), times(1));
            microbot.verify(() -> Microbot.log("Leaving Wintertodt (step 3)..."), times(1));
            assertEquals("Leaving to restock food (low warmth)", Microbot.status);
        }
    }

    @Test
    void waitingForRoundEndIsNotCountedAsAnExitFailureAndLogsOnce() throws Exception {
        MKE_WintertodtScript script = new MKE_WintertodtScript();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<WintertodtLocationManager> location = mockStatic(WintertodtLocationManager.class)) {
            widget.when(() -> Rs2Widget.getChildWidgetText(396, 6)).thenReturn("Points<br>650");
            location.when(WintertodtLocationManager::isInsideGameRoom).thenReturn(true);
            location.when(() -> WintertodtLocationManager.attemptLeaveWintertodt(anyBoolean())).thenReturn(false);

            Object state = gameState(false, 80);
            for (int i = 0; i < 25; i++) {
                handleBankingState(script, state);
            }

            location.verify(() -> WintertodtLocationManager.attemptLeaveWintertodt(true), times(25));
            assertTrue((Boolean) staticField("waitingForRoundEnd"));
            assertEquals(0, field(script, "consecutiveExitFailures"));
            player.verify(Rs2Player::logout, never());
            microbot.verify(() -> Microbot.log(anyString()), times(1));
            microbot.verify(() -> Microbot.log(
                    "Have 650 points - waiting at the door until the round ends to collect rewards"), times(1));
            microbot.verify(() -> Microbot.log(contains("Out of")), never());
            assertEquals("Waiting at door for round end (650 points)", Microbot.status);
        }
    }

    private static Object gameState(boolean lowAndOutOfFood, int warmth) throws Exception {
        Class<?> type = Class.forName(MKE_WintertodtScript.class.getName() + "$GameState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object state = constructor.newInstance();
        Field food = type.getDeclaredField("lowAndOutOfFood");
        food.setAccessible(true);
        food.set(state, lowAndOutOfFood);
        Field playerWarmth = type.getDeclaredField("playerWarmth");
        playerWarmth.setAccessible(true);
        playerWarmth.set(state, warmth);
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
