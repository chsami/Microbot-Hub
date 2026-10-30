package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SlayerMasterCombatRequirementTest {

    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

    @AfterEach
    void resetState() {
        SlayerPlugin.setState(SlayerState.IDLE);
        SlayerPlugin.setCurrentLocation("");
    }

    @Test
    void masterIsAvailableOnlyFromItsCombatLevel() {
        assertFalse(SlayerMaster.DURADEL.isAvailableAt(63));
        assertTrue(SlayerMaster.DURADEL.isAvailableAt(100));
        assertTrue(SlayerMaster.VANNAKA.isAvailableAt(40));
        assertFalse(SlayerMaster.VANNAKA.isAvailableAt(39));
    }

    @Test
    void highestAvailableMasterMatchesCombatLevel() {
        assertEquals(SlayerMaster.TURAEL, SlayerMaster.highestAvailableAt(3));
        assertEquals(SlayerMaster.MAZCHNA, SlayerMaster.highestAvailableAt(20));
        assertEquals(SlayerMaster.VANNAKA, SlayerMaster.highestAvailableAt(63));
        assertEquals(SlayerMaster.CHAELDAR, SlayerMaster.highestAvailableAt(84));
        assertEquals(SlayerMaster.NIEVE, SlayerMaster.highestAvailableAt(99));
        assertEquals(SlayerMaster.DURADEL, SlayerMaster.highestAvailableAt(126));
    }

    @Test
    void messageNamesMasterRequiredLevelAndSuggestion() {
        String message = SlayerScript.slayerMasterTooHighMessage(SlayerMaster.DURADEL, 63);

        assertTrue(message.contains("Duradel needs combat level 100"));
        assertTrue(message.contains("combat level 63"));
        assertTrue(message.contains("Vannaka"));
    }

    @Test
    void tooLowCombatStopsInsteadOfWalkingToMaster() throws Exception {
        SlayerScript script = scriptWithMaster(SlayerMaster.DURADEL);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getCombatLevel).thenReturn(63);
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);
            player.when(Rs2Player::isMoving).thenReturn(false);
            SlayerPlugin.setState(SlayerState.GETTING_TASK);

            gettingTask(script);

            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class));
            microbot.verify(() -> Microbot.showMessage(anyString()));
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
            assertEquals(SlayerState.IDLE, SlayerPlugin.getState());
        }
    }

    @Test
    void highEnoughCombatWalksToMaster() throws Exception {
        SlayerScript script = scriptWithMaster(SlayerMaster.VANNAKA);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getCombatLevel).thenReturn(63);
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);
            player.when(Rs2Player::isMoving).thenReturn(false);

            gettingTask(script);

            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
            walker.verify(() -> Rs2Walker.walkTo(SlayerMaster.VANNAKA.getLocation()));
        }
    }

    @Test
    void unknownCombatLevelWaitsWithoutStopping() throws Exception {
        SlayerScript script = scriptWithMaster(SlayerMaster.DURADEL);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getCombatLevel).thenReturn(0);
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);

            gettingTask(script);

            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        }
    }

    private static SlayerScript scriptWithMaster(SlayerMaster master) throws Exception {
        SlayerScript script = new SlayerScript();
        SlayerConfig config = mock(SlayerConfig.class);
        when(config.slayerMaster()).thenReturn(master);
        Field field = SlayerScript.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(script, config);
        return script;
    }

    private static void gettingTask(SlayerScript script) throws Exception {
        Method method = SlayerScript.class.getDeclaredMethod("handleGettingTaskState", boolean.class, String.class);
        method.setAccessible(true);
        method.invoke(script, false, "");
    }
}
