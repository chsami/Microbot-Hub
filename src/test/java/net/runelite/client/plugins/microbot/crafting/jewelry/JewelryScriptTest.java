package net.runelite.client.plugins.microbot.crafting.jewelry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JewelryScriptTest {

    @Test
    void barsAndGemsCanCraft() {
        assertTrue(JewelryScript.hasMaterialsForCraft(true, true, true));
    }

    @Test
    void cutGemsWithoutBarsCannotCraft() {
        assertFalse(JewelryScript.hasMaterialsForCraft(false, true, true));
    }

    @Test
    void barsWithoutNeededGemsCannotCraft() {
        assertFalse(JewelryScript.hasMaterialsForCraft(true, true, false));
    }

    @Test
    void barsAloneCanCraftWhenNoGemIsNeeded() {
        assertTrue(JewelryScript.hasMaterialsForCraft(true, false, false));
    }

    @Test
    void noBarsCannotCraftWhenNoGemIsNeeded() {
        assertFalse(JewelryScript.hasMaterialsForCraft(false, false, false));
    }

    @Test
    void gemTripWithdrawsEqualPairsThatFitFreeSlots() {
        assertEquals(13, JewelryScript.getWithdrawAmount(100, 100, true, 27));
    }

    @Test
    void gemTripIsLimitedByScarcerMaterial() {
        assertEquals(5, JewelryScript.getWithdrawAmount(5, 100, true, 27));
        assertEquals(7, JewelryScript.getWithdrawAmount(100, 7, true, 27));
    }

    @Test
    void gemTripWithOneFreeSlotWithdrawsNothing() {
        assertEquals(0, JewelryScript.getWithdrawAmount(100, 100, true, 1));
    }

    @Test
    void barOnlyTripFillsFreeSlots() {
        assertEquals(27, JewelryScript.getWithdrawAmount(100, 0, false, 27));
        assertEquals(10, JewelryScript.getWithdrawAmount(10, 0, false, 27));
    }

    @Test
    void negativeInputsWithdrawNothing() {
        assertEquals(0, JewelryScript.getWithdrawAmount(-1, 100, true, 27));
        assertEquals(0, JewelryScript.getWithdrawAmount(100, 0, false, -1));
    }
}
