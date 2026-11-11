package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.ParasiteCure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TripModelTest
{
    @Test
    public void dosesCountEachPotionByItsDoseAndUseTheEmptiestFirst()
    {
        Map<Integer, Integer> carried = new HashMap<>();
        carried.put(3024, 2);
        carried.put(3030, 1);
        carried.put(141, 1);
        List<List<Integer>> restores = List.of(Doses.SUPER_RESTORE, Doses.PRAYER_POTION);
        assertEquals(4 + 4 + 1 + 2, Doses.count(restores, id -> carried.getOrDefault(id, 0)));
        assertEquals(3030, Doses.lowest(restores, id -> carried.getOrDefault(id, 0)));
        assertEquals(-1, Doses.lowest(List.of(ParasiteCure.SANFEW_SERUM.getDoses()), id -> carried.getOrDefault(id, 0)));
        assertEquals(3, Doses.of(ParasiteCure.RELICYMS_BALM.getDoses(), 4844));
        assertEquals(0, Doses.of(Doses.SUPER_RESTORE, 2434));
    }

    @Test
    public void theTripEndsOnTheFirstSupplyBelowItsMinimum()
    {
        assertNull(TripRules.bankReason(4, 4, 4, 4, 4, 4));
        assertEquals("Low on food", TripRules.bankReason(3, 4, 0, 4, 0, 4));
        assertEquals("Low on restores", TripRules.bankReason(4, 4, 3, 4, 0, 4));
        assertEquals("Low on parasite cures", TripRules.bankReason(4, 4, 4, 4, 3, 4));
    }

    @Test
    public void aSessionStartsInTheDreamTheSanctuaryOrAtFerox()
    {
        assertEquals(TripPhase.FIGHTING, TripRules.startPhase(false, false, true));
        assertEquals(TripPhase.TRAVELLING, TripRules.startPhase(false, true, false));
        assertEquals(TripPhase.BANKING, TripRules.startPhase(true, false, false));
        assertNull(TripRules.startPhase(false, false, false));
    }

    @Test
    public void theSanctuaryAndFeroxAreRecognised()
    {
        assertEquals(true, DreamData.inSanctuary(DreamData.SLEPE_ARRIVAL));
        assertEquals(true, DreamData.inSanctuary(DreamData.POOL));
        assertEquals(false, DreamData.inSanctuary(new net.runelite.api.coords.WorldPoint(3808, 9780, 0)));
        assertEquals(true, DreamData.atFerox(DreamData.FEROX_ARRIVAL));
        assertEquals(true, DreamData.atFerox(DreamData.FEROX_POOL));
    }

    @Test
    public void aNoChargesMessageCountsOnlyForAMagicSetItem()
    {
        List<String> magic = List.of("Tumeken's shadow", "Ancestral hat");
        assertTrue(DreamData.noCharges("Tumeken's shadow has no charges! You need to charge it with soul runes and chaos runes.", magic));
        assertTrue(DreamData.noCharges("Tumeken's Shadow has no charges! You need to charge it with soul runes and chaos runes.", magic),
            "the game's own wording capitalises the name");
        assertTrue(DreamData.noCharges("The Lithic sceptre has no charges! You need to charge it with Earth and Body runes.",
            List.of("Lithic sceptre")));
        assertFalse(DreamData.noCharges("Your book has no charges.", magic), "not an item of the magic set");
        assertFalse(DreamData.noCharges("Tumeken's shadow is already fully charged.", magic));
        assertTrue(DreamData.noCharges("Your book has no charges.", List.of()), "with no names to read, any counts");
    }
}
