package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroSlayerMasterRequirementTest {

    @Test
    void masterIsAvailableOnlyFromItsCombatAndSlayerLevel() {
        assertFalse(DroSlayerMaster.DURADEL.isAvailableAt(63, 12));
        assertFalse(DroSlayerMaster.DURADEL.isAvailableAt(100, 49));
        assertTrue(DroSlayerMaster.DURADEL.isAvailableAt(100, 50));
        assertFalse(DroSlayerMaster.KURADAL.isAvailableAt(126, 49));
        assertTrue(DroSlayerMaster.KURADAL.isAvailableAt(100, 50));
        assertTrue(DroSlayerMaster.NIEVE.isAvailableAt(85, 1));
        assertFalse(DroSlayerMaster.NIEVE.needsQuest());
        assertTrue(DroSlayerMaster.STEVE.needsQuest());
        assertTrue(DroSlayerMaster.VANNAKA.isAvailableAt(40, 1));
        assertFalse(DroSlayerMaster.VANNAKA.isAvailableAt(39, 1));
        assertTrue(DroSlayerMaster.KRYSTILIA.isAvailableAt(3, 1), "Krystilia gives tasks at any level");
    }

    @Test
    void suggestedMasterNeedsNoQuestAndIsNeverKrystilia() {
        assertEquals(DroSlayerMaster.TURAEL, DroSlayerMaster.highestAvailableAt(3, 1, false));
        assertEquals(DroSlayerMaster.TURAEL, DroSlayerMaster.highestAvailableAt(30, 1, false));
        assertEquals(DroSlayerMaster.VANNAKA, DroSlayerMaster.highestAvailableAt(63, 12, false));
        assertEquals(DroSlayerMaster.VANNAKA, DroSlayerMaster.highestAvailableAt(75, 40, false));
        assertEquals(DroSlayerMaster.NIEVE, DroSlayerMaster.highestAvailableAt(88, 40, false));
        assertEquals(DroSlayerMaster.NIEVE, DroSlayerMaster.highestAvailableAt(126, 99, false));
        for (int combat = 3; combat <= 126; combat++) {
            assertFalse(DroSlayerMaster.highestAvailableAt(combat, 99, true) == DroSlayerMaster.KRYSTILIA);
        }
    }

    @Test
    void aLevel80PlayerOnTheDefaultDuradelIsToldWhyAndWhoToPick() {
        String message = DroSlayerScript.slayerMasterTooHighMessage(DroSlayerMaster.DURADEL, 80, 60);
        assertTrue(message.contains("Duradel only gives tasks from combat level 100; you are combat level 80."), message);
        assertTrue(message.contains("Vannaka works at your level with no quest needed"), message);
        assertTrue(message.contains("Chaeldar also works if you have finished Lost City"), message);
        assertTrue(message.contains("Pick a Slayer Master you can use in the plugin settings"), message);
    }

    @Test
    void aSlayerLevelShortfallIsNamedAsSuch() {
        String message = DroSlayerScript.slayerMasterTooHighMessage(DroSlayerMaster.DURADEL, 110, 40);
        assertTrue(message.contains("Duradel only gives tasks from Slayer level 50; you are Slayer level 40."), message);
        assertTrue(message.contains("Nieve works at your level"), message);
        assertTrue(message.contains("Steve instead if you have finished Monkey Madness II"), message);
    }

    @Test
    void theWildernessCannonIsOffUnlessThePlayerTurnsItOn() {
        DroSlayerConfig config = new DroSlayerConfig() { };
        assertFalse(config.wildernessPreferCannon(), "a Wilderness cannon can be lost on death");
        assertFalse(config.enableCannon());
        assertEquals(DroSlayerMaster.DURADEL, config.slayerMaster());
    }
}
