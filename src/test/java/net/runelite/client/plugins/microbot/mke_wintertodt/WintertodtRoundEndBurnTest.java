package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.State;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WintertodtRoundEndBurnTest {

    private static final String[] SAVED_FIELDS = {
            "config", "state", "lockState", "estimatedSecondsLeft", "currentBurnableCount",
            "avgFeedMs", "fletchingState", "waitingForRoundEnd", "shouldPriorizeBrazierAtStart",
            "targetRootsForThisRun"
    };

    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach
    void saveState() throws Exception {
        for (String name : SAVED_FIELDS) {
            saved.put(name, staticField(name));
        }
        MKE_WintertodtConfig config = mock(MKE_WintertodtConfig.class);
        when(config.fletchRoots()).thenReturn(true);
        setStaticField("config", config);
        setStaticField("waitingForRoundEnd", false);
        setStaticField("shouldPriorizeBrazierAtStart", false);
        setStaticField("avgFeedMs", 1600.0);
        setStaticField("targetRootsForThisRun", 0);
        setStaticField("fletchingState", new MKE_WintertodtScript.FletchingState());
    }

    @AfterEach
    void restoreState() throws Exception {
        for (String name : SAVED_FIELDS) {
            setStaticField(name, saved.get(name));
        }
    }

    @Test
    void roundEndReleasesFletchLockAndSwitchesToBurning() throws Exception {
        startLockedFletching();
        setStaticField("estimatedSecondsLeft", 10.0);
        setStaticField("currentBurnableCount", 20);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            handleMainGameLoop(gameState(true, true));

            assertEquals(State.BURN_LOGS, MKE_WintertodtScript.state);
            assertFalse((Boolean) staticField("lockState"));
            assertFalse(fletchingState().isActive());
            inventory.verify(Rs2Inventory::isItemSelected, times(1));
            microbot.verify(() -> Microbot.log(startsWith("Fletching stopped")), times(1));
            microbot.verify(() -> Microbot.log("Time is almost over and we have items to burn, lets just burn them!"), times(1));
        }
    }

    @Test
    void switchesToBurningEarlyWhenHeldItemsCannotBeFedInTimeLeft() throws Exception {
        startLockedFletching();
        setStaticField("estimatedSecondsLeft", 30.0);
        setStaticField("currentBurnableCount", 20);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenReturn(true);
            handleMainGameLoop(gameState(true, true));

            assertEquals(State.BURN_LOGS, MKE_WintertodtScript.state);
            assertFalse((Boolean) staticField("lockState"));
        }
    }

    @Test
    void keepsBurningLeftoverRootsInsteadOfGoingBackToFletching() throws Exception {
        MKE_WintertodtScript.state = State.BURN_LOGS;
        setStaticField("lockState", false);
        setStaticField("estimatedSecondsLeft", 40.0);
        setStaticField("currentBurnableCount", 5);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenReturn(true);
            handleMainGameLoop(gameState(true, true));

            assertEquals(State.BURN_LOGS, MKE_WintertodtScript.state);
            player.verify(Rs2Player::getWorldLocation, never());
            microbot.verify(() -> Microbot.log(anyString()), never());
        }
    }

    @Test
    void shouldBurnLogsReportsFalseWhenTheSwitchIsBlockedByAnotherLock() throws Exception {
        MKE_WintertodtScript.state = State.CHOP_ROOTS;
        setStaticField("lockState", true);

        Method method = MKE_WintertodtScript.class.getDeclaredMethod("shouldBurnLogs", gameState(true, true).getClass());
        method.setAccessible(true);
        boolean result = (Boolean) method.invoke(new MKE_WintertodtScript(), gameState(true, true));

        assertFalse(result);
        assertEquals(State.CHOP_ROOTS, MKE_WintertodtScript.state);
    }

    @Test
    void outOfTimeToFeedComparesFeedTimeWithTimeLeftMinusBuffer() {
        assertTrue(MKE_WintertodtScript.isOutOfTimeToFeed(20, 30, 1600));
        assertFalse(MKE_WintertodtScript.isOutOfTimeToFeed(20, 60, 1600));
        assertFalse(MKE_WintertodtScript.isOutOfTimeToFeed(0, 3, 1600));
        assertFalse(MKE_WintertodtScript.isOutOfTimeToFeed(28, 999, 1600));
    }

    private static void startLockedFletching() throws Exception {
        MKE_WintertodtScript.state = State.FLETCH_LOGS;
        setStaticField("lockState", true);
        Field active = MKE_WintertodtScript.FletchingState.class.getDeclaredField("isActive");
        active.setAccessible(true);
        active.set(fletchingState(), true);
    }

    private static MKE_WintertodtScript.FletchingState fletchingState() throws Exception {
        return (MKE_WintertodtScript.FletchingState) staticField("fletchingState");
    }

    private static Object gameState(boolean hasItemsToBurn, boolean hasRootsToFletch) throws Exception {
        Class<?> type = Class.forName(MKE_WintertodtScript.class.getName() + "$GameState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object state = constructor.newInstance();
        Field items = type.getDeclaredField("hasItemsToBurn");
        items.setAccessible(true);
        items.set(state, hasItemsToBurn);
        Field roots = type.getDeclaredField("hasRootsToFletch");
        roots.setAccessible(true);
        roots.set(state, hasRootsToFletch);
        return state;
    }

    private static void handleMainGameLoop(Object gameState) throws Exception {
        Method method = MKE_WintertodtScript.class.getDeclaredMethod("handleMainGameLoop", gameState.getClass());
        method.setAccessible(true);
        method.invoke(new MKE_WintertodtScript(), gameState);
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
