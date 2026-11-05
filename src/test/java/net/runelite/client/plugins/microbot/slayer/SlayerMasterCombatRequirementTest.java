package net.runelite.client.plugins.microbot.slayer;

import net.runelite.api.Skill;
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
    void masterIsAvailableOnlyFromItsCombatAndSlayerLevel() {
        assertFalse(SlayerMaster.DURADEL.isAvailableAt(63, 12));
        assertFalse(SlayerMaster.DURADEL.isAvailableAt(100, 49));
        assertTrue(SlayerMaster.DURADEL.isAvailableAt(100, 50));
        assertFalse(SlayerMaster.KURADAL.isAvailableAt(126, 49));
        assertTrue(SlayerMaster.KURADAL.isAvailableAt(100, 50));
        assertTrue(SlayerMaster.NIEVE.isAvailableAt(85, 1));
        assertFalse(SlayerMaster.NIEVE.needsQuest());
        assertTrue(SlayerMaster.STEVE.needsQuest());
        assertTrue(SlayerMaster.VANNAKA.isAvailableAt(40, 1));
        assertFalse(SlayerMaster.VANNAKA.isAvailableAt(39, 1));
    }

    @Test
    void suggestedMasterNeedsNoQuest() {
        assertEquals(SlayerMaster.TURAEL, SlayerMaster.highestAvailableAt(3, 1, false));
        assertEquals(SlayerMaster.TURAEL, SlayerMaster.highestAvailableAt(30, 1, false));
        assertEquals(SlayerMaster.VANNAKA, SlayerMaster.highestAvailableAt(63, 12, false));
        assertEquals(SlayerMaster.VANNAKA, SlayerMaster.highestAvailableAt(75, 40, false));
        assertEquals(SlayerMaster.NIEVE, SlayerMaster.highestAvailableAt(88, 40, false));
        assertEquals(SlayerMaster.NIEVE, SlayerMaster.highestAvailableAt(126, 99, false));
    }

    @Test
    void questMasterOptionRespectsSlayerLevel() {
        assertEquals(SlayerMaster.MAZCHNA, SlayerMaster.highestAvailableAt(30, 1, true));
        assertEquals(SlayerMaster.CHAELDAR, SlayerMaster.highestAvailableAt(75, 1, true));
        assertEquals(SlayerMaster.NIEVE, SlayerMaster.highestAvailableAt(126, 12, true));
        assertEquals(SlayerMaster.DURADEL, SlayerMaster.highestAvailableAt(126, 60, true));
    }

    @Test
    void lowCombatMessageNamesLevelAndQuestFreeMaster() {
        String message = SlayerScript.slayerMasterTooHighMessage(SlayerMaster.DURADEL, 63, 12);

        assertTrue(message.contains("Duradel only gives tasks from combat level 100; you are combat level 63."));
        assertTrue(message.contains("Vannaka works at your level with no quest needed."));
        assertTrue(message.contains("Pick a Slayer Master you can use in the plugin settings, then start the plugin again."));
    }

    @Test
    void messageNamesQuestForHigherQuestMaster() {
        String message = SlayerScript.slayerMasterTooHighMessage(SlayerMaster.DURADEL, 75, 40);

        assertTrue(message.contains("Vannaka works at your level with no quest needed (Chaeldar also works if you have finished Lost City)."));
    }

    @Test
    void messageSuggestsNieveAndSteveAfterMonkeyMadness() {
        String message = SlayerScript.slayerMasterTooHighMessage(SlayerMaster.DURADEL, 88, 40);

        assertTrue(message.contains("Nieve works at your level (pick Steve instead if you have finished Monkey Madness II)."));
        assertFalse(message.contains("Vannaka"));
    }

    @Test
    void lowSlayerMessageNamesSlayerLevel() {
        String message = SlayerScript.slayerMasterTooHighMessage(SlayerMaster.DURADEL, 100, 12);

        assertTrue(message.contains("Duradel only gives tasks from Slayer level 50; you are Slayer level 12."));
        assertTrue(message.contains("Nieve works at your level (pick Steve instead if you have finished Monkey Madness II)."));
        assertFalse(message.contains("Duradel also works"));
    }

    @Test
    void tooLowCombatStopsInsteadOfWalkingToMaster() throws Exception {
        assertStopsWithoutWalking(63, 12);
    }

    @Test
    void tooLowSlayerStopsInsteadOfWalkingToMaster() throws Exception {
        assertStopsWithoutWalking(100, 12);
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
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(12);
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);
            player.when(Rs2Player::isMoving).thenReturn(false);

            gettingTask(script);

            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
            walker.verify(() -> Rs2Walker.walkTo(SlayerMaster.VANNAKA.getLocation()));
        }
    }

    @Test
    void unknownLevelsWaitWithoutStopping() throws Exception {
        SlayerScript script = scriptWithMaster(SlayerMaster.DURADEL);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getCombatLevel).thenReturn(0);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(0);
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);

            gettingTask(script);

            microbot.verify(() -> Microbot.stopPlugin(SlayerPlugin.class), never());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        }
    }

    private static void assertStopsWithoutWalking(int combatLevel, int slayerLevel) throws Exception {
        SlayerScript script = scriptWithMaster(SlayerMaster.DURADEL);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            dialogue.when(Rs2Dialogue::isInDialogue).thenReturn(false);
            player.when(Rs2Player::getCombatLevel).thenReturn(combatLevel);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.SLAYER)).thenReturn(slayerLevel);
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
