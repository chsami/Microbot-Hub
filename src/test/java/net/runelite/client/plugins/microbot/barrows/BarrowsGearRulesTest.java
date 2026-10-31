package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrowsGearRulesTest
{
    @Test
    void brokenBarrowsPieceNeverFillsTheRow()
    {
        assertFalse(BarrowsGearRules.fillsSetupRow("Dharok's helm", "Dharok's helm 0"));
        assertFalse(BarrowsGearRules.fillsSetupRow("Karil's crossbow", "Karil's crossbow 0"));
    }

    @Test
    void workingBarrowsPiecesFillTheRow()
    {
        assertTrue(BarrowsGearRules.fillsSetupRow("Dharok's helm", "Dharok's helm"));
        assertTrue(BarrowsGearRules.fillsSetupRow("Dharok's helm", "Dharok's helm 100"));
        assertTrue(BarrowsGearRules.fillsSetupRow("Dharok's helm", "Dharok's helm 25"));
    }

    @Test
    void otherGearStillMatchesByPartialName()
    {
        assertTrue(BarrowsGearRules.fillsSetupRow("Ring of dueling", "Ring of dueling(8)"));
        assertTrue(BarrowsGearRules.fillsSetupRow("amulet of glory", "Amulet of glory(4)"));
        assertFalse(BarrowsGearRules.fillsSetupRow("Rune platebody", "Dragon platebody"));
    }

    @Test
    void onlyBarrowsNamesEndingInZeroCountAsBroken()
    {
        assertTrue(BarrowsGearRules.isBrokenBarrowsPiece("Verac's flail 0"));
        assertFalse(BarrowsGearRules.isBrokenBarrowsPiece("Verac's flail 100"));
        assertFalse(BarrowsGearRules.isBrokenBarrowsPiece("Verac's flail"));
        assertFalse(BarrowsGearRules.isBrokenBarrowsPiece("Rune scimitar 0"));
        assertFalse(BarrowsGearRules.isBrokenBarrowsPiece(null));
    }

    @Test
    void missingNamesNeverFillTheRow()
    {
        assertFalse(BarrowsGearRules.fillsSetupRow("", "Dharok's helm 100"));
        assertFalse(BarrowsGearRules.fillsSetupRow(null, "Dharok's helm 100"));
        assertFalse(BarrowsGearRules.fillsSetupRow("Dharok's helm", null));
    }

    @Test
    void rowEmptiedInThePanelCountsAsEmpty()
    {
        InventorySetupsItem emptied = new InventorySetupsItem(-1, "", 0, true, InventorySetupsStackCompareID.None, true, 0);
        InventorySetupsItem emptiedKeepingName = new InventorySetupsItem(-1, "Dharok's helm", 0, false, InventorySetupsStackCompareID.Standard, false, 0);

        assertFalse(InventorySetupsItem.itemIsDummy(emptied));
        assertTrue(BarrowsGearRules.isEmptySetupRow(emptied));
        assertTrue(BarrowsGearRules.isEmptySetupRow(emptiedKeepingName));
        assertTrue(BarrowsGearRules.isEmptySetupRow(InventorySetupsItem.getDummyItem()));
        assertTrue(BarrowsGearRules.isEmptySetupRow(null));
    }

    @Test
    void realSetupRowIsNotEmpty()
    {
        InventorySetupsItem helm = new InventorySetupsItem(4716, "Dharok's helm", 1, false, InventorySetupsStackCompareID.None, false, 0);

        assertFalse(BarrowsGearRules.isEmptySetupRow(helm));
    }

    private static Map.Entry<Integer, String> candidate(int id, String name)
    {
        return new SimpleEntry<>(id, name);
    }

    private static Map.Entry<Integer, String> closest(int setupId, String setupName, List<Map.Entry<Integer, String>> candidates)
    {
        return candidates.stream()
                .filter(c -> BarrowsGearRules.fillsSetupRow(setupName, c.getValue()))
                .min(BarrowsGearRules.closestToSetupRow(setupId, setupName, Map.Entry::getKey, Map.Entry::getValue))
                .orElse(null);
    }

    @Test
    void exactItemWinsOverATrimmedVariantInAnEarlierSlot()
    {
        List<Map.Entry<Integer, String>> bank = Arrays.asList(
                candidate(2615, "Rune platebody (g)"),
                candidate(2623, "Rune platebody (t)"),
                candidate(1127, "Rune platebody"));

        assertEquals(1127, closest(1127, "Rune platebody", bank).getKey());
    }

    @Test
    void exactNameWinsWhenTheSetupIdIsNotThere()
    {
        List<Map.Entry<Integer, String>> bank = Arrays.asList(
                candidate(20575, "Mystic robe top (dark)"),
                candidate(4091, "Mystic robe top"));

        assertEquals(4091, closest(-2, "mystic robe top", bank).getKey());
    }

    @Test
    void setupIdWinsOverOtherChargeStages()
    {
        List<Map.Entry<Integer, String>> bank = Arrays.asList(
                candidate(4716, "Dharok's helm"),
                candidate(4881, "Dharok's helm 75"),
                candidate(4880, "Dharok's helm 100"));

        assertEquals(4880, closest(4880, "Dharok's helm", bank).getKey());
    }

    @Test
    void shortestNameWinsWhenNothingMatchesExactly()
    {
        List<Map.Entry<Integer, String>> bank = Arrays.asList(
                candidate(4881, "Dharok's helm 75"),
                candidate(4880, "Dharok's helm 100"),
                candidate(4884, "Dharok's helm 0"));

        assertEquals(4881, closest(4716, "Dharok's helm", bank).getKey());
    }

    @Test
    void brokenCopyIsNeverPickedEvenWithTheSetupId()
    {
        List<Map.Entry<Integer, String>> bank = Arrays.asList(
                candidate(4884, "Dharok's helm 0"),
                candidate(4883, "Dharok's helm 25"));

        assertEquals(4883, closest(4884, "Dharok's helm", bank).getKey());
    }

    @Test
    void rowIsBrokenOnlyWhenEveryMatchingCopyIsBroken()
    {
        assertEquals("Dharok's helm 0", BarrowsGearRules.brokenOnlyMatch("Dharok's helm",
                Arrays.asList("Dharok's greataxe 100", "Dharok's helm 0", "Rune platebody")));
    }

    @Test
    void workingCopyMeansTheRowIsNotBrokenOnly()
    {
        assertNull(BarrowsGearRules.brokenOnlyMatch("Dharok's helm",
                Arrays.asList("Dharok's helm 0", "Dharok's helm 25")));
        assertNull(BarrowsGearRules.brokenOnlyMatch("Dharok's helm",
                Arrays.asList("Dharok's helm", "Dharok's helm 0")));
    }

    @Test
    void rowWithNoCopyAtAllIsNotBrokenOnly()
    {
        assertNull(BarrowsGearRules.brokenOnlyMatch("Dharok's helm", Arrays.asList("Guthan's helm 0", "Rune platebody")));
        assertNull(BarrowsGearRules.brokenOnlyMatch("Dharok's helm", Collections.emptyList()));
        assertNull(BarrowsGearRules.brokenOnlyMatch("Dharok's helm", null));
    }

    @Test
    void brokenOnlyMatchIgnoresCaseAndMissingNames()
    {
        assertEquals("Karil's crossbow 0", BarrowsGearRules.brokenOnlyMatch("karil's crossbow",
                Arrays.asList(null, "Karil's crossbow 0")));
        assertNull(BarrowsGearRules.brokenOnlyMatch("", Arrays.asList("Karil's crossbow 0")));
    }
}
