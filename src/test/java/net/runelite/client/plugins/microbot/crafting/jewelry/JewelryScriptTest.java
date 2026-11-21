package net.runelite.client.plugins.microbot.crafting.jewelry;

import net.runelite.client.plugins.microbot.crafting.jewelry.enums.State;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JewelryScriptTest {

    @Test
    void missingAntibanCategoryIsNotBusy() {
        assertFalse(JewelryScript.isCategoryBusy(null));
    }

    @Test
    void wornStaffWithoutNotedJewelryDoesNotBankOutsideAlching() {
        assertFalse(JewelryScript.shouldBank(State.BANKING, false, true));
        assertFalse(JewelryScript.shouldBank(State.CRAFTING, false, true));
        assertFalse(JewelryScript.shouldBank(State.CUTTING, false, true));
        assertFalse(JewelryScript.shouldBank(null, false, true));
    }

    @Test
    void finishedAlchingBanksWhileAlching() {
        assertTrue(JewelryScript.shouldBank(State.ALCHING, false, true));
        assertFalse(JewelryScript.shouldBank(State.ALCHING, false, false));
    }

    @Test
    void finishedMaterialsBankInAnyState() {
        assertTrue(JewelryScript.shouldBank(State.CRAFTING, true, false));
        assertTrue(JewelryScript.shouldBank(State.CUTTING, true, false));
        assertTrue(JewelryScript.shouldBank(null, true, false));
    }

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
    void outOfBarsMessageNamesGemsLeftInBank() {
        assertEquals("Out of Gold bars (13 x Ruby left in bank). Crafting stopped. Get more Gold bars and press start again.",
                JewelryScript.getOutOfMaterialsMessage("Gold bars", 0, "Ruby", 13, true));
    }

    @Test
    void outOfGemsMessageNamesBarsLeftInBank() {
        assertEquals("Out of Ruby (5 x Gold bars left in bank). Crafting stopped. Get more Ruby and press start again.",
                JewelryScript.getOutOfMaterialsMessage("Gold bars", 5, "Ruby", 0, true));
    }

    @Test
    void outOfBothMessageNamesBoth() {
        assertEquals("Out of Gold bars and Ruby. Crafting stopped.",
                JewelryScript.getOutOfMaterialsMessage("Gold bars", 0, "Ruby", 0, true));
    }

    @Test
    void outOfBarsMessageForGoldOnlyItem() {
        assertEquals("Out of Gold bars. Crafting stopped.",
                JewelryScript.getOutOfMaterialsMessage("Gold bars", 0, "", 0, false));
    }

    @Test
    void capitalizeFormatsEnumAndGemNames() {
        assertEquals("Gold", JewelryScript.capitalize("GOLD"));
        assertEquals("Red topaz", JewelryScript.capitalize("red topaz"));
        assertEquals("", JewelryScript.capitalize(""));
    }

    @Test
    void countDroppedFiresOnlyWhenBarsAreUsed() {
        int[] bars = {27};
        BooleanSupplier dropped = JewelryScript.countDropped(() -> bars[0]);
        assertFalse(dropped.getAsBoolean());
        bars[0] = 26;
        assertTrue(dropped.getAsBoolean());
        assertFalse(dropped.getAsBoolean());
        bars[0] = 25;
        assertTrue(dropped.getAsBoolean());
        bars[0] = 27;
        assertFalse(dropped.getAsBoolean());
    }

    @Test
    void craftIdleTimeoutOutlastsGapBetweenItems() {
        assertTrue(JewelryScript.CRAFT_IDLE_TIMEOUT_MS >= 3000);
    }

    @Test
    void negativeInputsWithdrawNothing() {
        assertEquals(0, JewelryScript.getWithdrawAmount(-1, 100, true, 27));
        assertEquals(0, JewelryScript.getWithdrawAmount(100, 0, false, -1));
    }
}
