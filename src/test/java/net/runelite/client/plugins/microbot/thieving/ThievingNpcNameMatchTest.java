package net.runelite.client.plugins.microbot.thieving;

import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThievingNpcNameMatchTest {

    @Test
    void manTargetMatchesOnlyMan() {
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.MEN, "Man"));
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.MEN, "Wise Old Man"));
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.MEN, "Woman"));
    }

    @Test
    void womanTargetDoesNotMatchWarriorWoman() {
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.WOMAN, "Woman"));
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.WOMAN, "Warrior woman"));
    }

    @Test
    void farmerTargetDoesNotMatchMasterFarmer() {
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.FARMER, "Farmer"));
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.FARMER, "Master Farmer"));
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.MASTER_FARMER, "Master Farmer"));
    }

    @Test
    void mixedCaseTargetsMatchIgnoringCase() {
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.PALADIN, "Paladin"));
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.HERO, "Hero"));
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.WEALTHY_CITIZEN, "Wealthy citizen"));
    }

    @Test
    void warriorTargetMatchesWarriorVariants() {
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.WARRIOR, "Al Kharid warrior"));
        assertTrue(ThievingScript.matchesNpcName(ThievingNpc.WARRIOR, "Warrior woman"));
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.WARRIOR, "Man"));
    }

    @Test
    void nullInputsDoNotMatch() {
        assertFalse(ThievingScript.matchesNpcName(ThievingNpc.MEN, null));
        assertFalse(ThievingScript.matchesNpcName(null, "Man"));
    }
}
