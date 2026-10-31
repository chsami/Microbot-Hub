package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
