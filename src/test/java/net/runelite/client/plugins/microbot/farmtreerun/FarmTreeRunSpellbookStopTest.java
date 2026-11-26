package net.runelite.client.plugins.microbot.farmtreerun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmTreeRunSpellbookStopTest {

    @Test
    void nonStandardSpellbookWithRuneTravelGivesVisibleReason() {
        String reason = FarmTreeRunScript.spellbookStopReason(false, false, () -> false);
        assertEquals(FarmTreeRunScript.SPELLBOOK_STOP_REASON, reason);
        assertTrue(reason.contains("standard spellbook"));
        assertTrue(reason.contains("Tablets instead of runes"));
        assertTrue(reason.contains("Prefer Nexus / house fairy ring"));
    }

    @Test
    void standardSpellbookDoesNotStop() {
        assertNull(FarmTreeRunScript.spellbookStopReason(false, false, () -> true));
    }

    @Test
    void tabletsOrHouseTravelSkipSpellbookCheck() {
        assertNull(FarmTreeRunScript.spellbookStopReason(true, false, () -> {
            throw new AssertionError("spellbook should not be read");
        }));
        assertNull(FarmTreeRunScript.spellbookStopReason(false, true, () -> {
            throw new AssertionError("spellbook should not be read");
        }));
    }
}
