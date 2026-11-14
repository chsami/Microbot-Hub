package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PohSpellbookAltarTest {
    private static final String[] OCCULT = {"Venerate", "Standard", "Ancient", "Lunar", "Arceuus", "Remove"};
    private static final String[] SINGLE = {"Venerate", null, null, null, "Remove"};

    @Test void occultAltarUsesTheNamedBookInEitherIdForm() {
        assertEquals("Lunar", PohSpellbookAltar.action(ObjectID.POH_ALTAR_OCCULT, OCCULT, Rs2Spellbook.MODERN, Rs2Spellbook.LUNAR));
        assertEquals("Ancient", PohSpellbookAltar.action(ObjectID.POH_ALTAR_OCCULT_ARCEUUS, OCCULT, Rs2Spellbook.ARCEUUS, Rs2Spellbook.ANCIENT));
        assertTrue(PohSpellbookAltar.IDS.contains(ObjectID.POH_ALTAR_OCCULT_LUNAR));
    }

    @Test void singleBookAltarsVenerateBetweenStandardAndTheirOwnBook() {
        assertEquals("Venerate", PohSpellbookAltar.action(ObjectID.POH_ALTAR_ANCIENT, SINGLE, Rs2Spellbook.MODERN, Rs2Spellbook.ANCIENT));
        assertEquals("Venerate", PohSpellbookAltar.action(ObjectID.POH_ALTAR_DARK, SINGLE, Rs2Spellbook.ARCEUUS, Rs2Spellbook.MODERN));
        assertNull(PohSpellbookAltar.action(ObjectID.POH_ALTAR_ANCIENT, SINGLE, Rs2Spellbook.MODERN, Rs2Spellbook.LUNAR));
        assertNull(PohSpellbookAltar.action(ObjectID.POH_ALTAR_LUNAR, SINGLE, Rs2Spellbook.ANCIENT, Rs2Spellbook.LUNAR));
    }

    @Test void alreadyOnTargetOrNoActionsIsNotAMatch() {
        assertNull(PohSpellbookAltar.action(ObjectID.POH_ALTAR_OCCULT, OCCULT, Rs2Spellbook.LUNAR, Rs2Spellbook.LUNAR));
        assertNull(PohSpellbookAltar.action(ObjectID.POH_ALTAR_OCCULT, null, Rs2Spellbook.MODERN, Rs2Spellbook.LUNAR));
    }
}
