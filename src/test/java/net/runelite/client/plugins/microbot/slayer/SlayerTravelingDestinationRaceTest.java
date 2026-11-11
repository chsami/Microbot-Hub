package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class SlayerTravelingDestinationRaceTest {

    private static final WorldPoint DESTINATION = new WorldPoint(3200, 3200, 0);

    @AfterEach
    void resetState() {
        SlayerPlugin.setState(SlayerState.IDLE);
    }

    @Test
    void destinationClearedAfterNullCheckStillArrives() throws Exception {
        SlayerScript script = new SlayerScript();
        setField(script, "taskDestination", DESTINATION);
        SlayerPlugin.setState(SlayerState.TRAVELING);
        try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getWorldLocation).thenAnswer(invocation -> {
                setField(script, "taskDestination", null);
                return DESTINATION;
            });

            assertDoesNotThrow(() -> invoke(script, "handleTravelingState"));

            assertEquals(SlayerState.AT_LOCATION, SlayerPlugin.getState());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        }
    }

    @Test
    void destinationClearedAfterNullCheckStillWalksToSnapshot() throws Exception {
        SlayerScript script = new SlayerScript();
        setField(script, "taskDestination", DESTINATION);
        SlayerPlugin.setState(SlayerState.TRAVELING);
        try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3000, 3000, 0));
            player.when(Rs2Player::isMoving).thenAnswer(invocation -> {
                setField(script, "taskDestination", null);
                return false;
            });

            assertDoesNotThrow(() -> invoke(script, "handleTravelingState"));

            walker.verify(() -> Rs2Walker.walkTo(DESTINATION));
            assertEquals(SlayerState.TRAVELING, SlayerPlugin.getState());
        }
    }

    @Test
    void missingDestinationReturnsToDetectingTask() throws Exception {
        SlayerScript script = new SlayerScript();
        SlayerPlugin.setState(SlayerState.TRAVELING);
        try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);

            invoke(script, "handleTravelingState");

            assertEquals(SlayerState.DETECTING_TASK, SlayerPlugin.getState());
            walker.verifyNoInteractions();
        }
    }

    @Test
    void cannonSpotClearedAfterNullCheckStillWalksToSnapshot() throws Exception {
        SlayerScript script = new SlayerScript();
        setField(script, "isUsingCannon", true);
        setField(script, "cannonPlaced", false);
        setField(script, "cannonSpot", DESTINATION);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenAnswer(invocation -> {
                setField(script, "cannonSpot", null);
                return new WorldPoint(3000, 3000, 0);
            });
            player.when(Rs2Player::isMoving).thenReturn(false);

            Method method = SlayerScript.class.getDeclaredMethod("handleAtLocationState", boolean.class, int.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script, true, 10));

            walker.verify(() -> Rs2Walker.walkTo(DESTINATION));
        }
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
