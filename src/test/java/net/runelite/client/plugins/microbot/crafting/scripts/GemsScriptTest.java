package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.client.plugins.microbot.crafting.enums.BoltTips;
import net.runelite.client.plugins.microbot.crafting.enums.Gems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemsScriptTest {

    @Test
    void rubyMapsToRubyBoltTips() {
        assertEquals(BoltTips.RUBY, GemsScript.getBoltTip(Gems.RUBY));
    }

    @Test
    void gemsWithoutBoltTipsHaveNoBoltTip() {
        assertNull(GemsScript.getBoltTip(Gems.ZENYTE));
        assertNull(GemsScript.getBoltTip(Gems.AMETHYST_BOLT_TIPS));
        assertNull(GemsScript.getBoltTip(Gems.NONE));
        assertNull(GemsScript.getBoltTip(null));
    }

    @Test
    void cutGemsCountAsWorkWhenFletching() {
        assertTrue(GemsScript.hasGemsToProcess(false, true, true));
    }

    @Test
    void cutGemsDoNotCountAsWorkWithoutFletching() {
        assertFalse(GemsScript.hasGemsToProcess(false, true, false));
    }

    @Test
    void uncutGemsAlwaysCountAsWork() {
        assertTrue(GemsScript.hasGemsToProcess(true, false, false));
        assertTrue(GemsScript.hasGemsToProcess(true, true, true));
    }

    @Test
    void noGemsIsNoWork() {
        assertFalse(GemsScript.hasGemsToProcess(false, false, true));
    }

    @Test
    void fletchUnavailableMessageNamesLevelForLowFletching() {
        assertEquals("Fletching level 63 needed for Ruby bolt tips, only cutting gems.",
                GemsScript.fletchUnavailableMessage("Ruby", BoltTips.RUBY));
    }

    @Test
    void fletchUnavailableMessageExplainsGemWithoutBoltTips() {
        assertEquals("Zenyte cannot be fletched into bolt tips, only cutting gems.",
                GemsScript.fletchUnavailableMessage("Zenyte", GemsScript.getBoltTip(Gems.ZENYTE)));
    }

    @Test
    void uncutGemsAreNoWorkWhenCraftingLevelIsTooLow() {
        boolean canCut = false;
        assertFalse(GemsScript.hasGemsToProcess(canCut && true, false, true));
        assertTrue(GemsScript.hasGemsToProcess(canCut && true, true, true));
    }

    @Test
    void fletchNoticeIsShownWhenBoltTipsAreUnavailable() {
        assertTrue(GemsScript.fletchNoticeNeeded(Gems.ZENYTE, true, false, false));
        assertTrue(GemsScript.fletchNoticeNeeded(Gems.RUBY, true, false, false));
    }

    @Test
    void fletchNoticeIsNotShownWithoutGemChoice() {
        assertFalse(GemsScript.fletchNoticeNeeded(Gems.NONE, true, false, false));
        assertFalse(GemsScript.fletchNoticeNeeded(null, true, false, false));
    }

    @Test
    void fletchNoticeIsNotShownWhenNotNeeded() {
        assertFalse(GemsScript.fletchNoticeNeeded(Gems.RUBY, false, false, false));
        assertFalse(GemsScript.fletchNoticeNeeded(Gems.RUBY, true, false, true));
        assertFalse(GemsScript.fletchNoticeNeeded(Gems.AMETHYST_BOLT_TIPS, true, true, false));
    }

    @Test
    void craftingLevelMessageNamesCuttingLevel() {
        assertEquals("Crafting level 34 needed to cut Ruby. Stopping.",
                GemsScript.craftingLevelMessage("Ruby", Gems.RUBY.getLevelRequired(), false, null));
    }

    @Test
    void craftingLevelMessageNamesFletchingLevelWhenBoltTipsAreAlsoOutOfReach() {
        assertEquals("Crafting level 34 needed to cut Ruby. Fletching level 63 needed for Ruby bolt tips. Stopping.",
                GemsScript.craftingLevelMessage("Ruby", Gems.RUBY.getLevelRequired(), false, BoltTips.RUBY));
    }

    @Test
    void craftingLevelMessageForAmethyst() {
        assertEquals("Crafting level 83 needed to craft Amethyst bolt tips. Stopping.",
                GemsScript.craftingLevelMessage("Amethyst bolt tips", Gems.AMETHYST_BOLT_TIPS.getLevelRequired(), true, null));
    }

    @Test
    void bankCutGemsMessageExplainsLowCraftingLevel() {
        assertEquals("Your Crafting level is below 34, so uncut Ruby is skipped. Fletching cut Ruby from your bank into bolt tips.",
                GemsScript.bankCutGemsMessage("Ruby", false, 34));
        assertEquals("No uncut Ruby left, fletching cut Ruby from your bank into bolt tips.",
                GemsScript.bankCutGemsMessage("Ruby", true, 34));
    }

    @Test
    void outOfCutGemsMessageNamesCraftingLevel() {
        assertEquals("Out of cut Ruby in your bank. Crafting level 34 is needed to cut uncut Ruby, so add more cut Ruby to your bank to keep fletching. Stopping.",
                GemsScript.outOfCutGemsMessage("Ruby", 34));
    }

    @Test
    void noRoomForCutGemsMessageNamesGem() {
        assertEquals("No room for cut Ruby in your inventory. Make room in your inventory and start again. Stopping.",
                GemsScript.noRoomForCutGemsMessage("Ruby"));
    }

    @Test
    void lowCraftingDoesNotStopWhenCutGemsCanBeFletched() {
        assertFalse(GemsScript.shouldStopForLevels(false, true));
    }

    @Test
    void lowCraftingStopsWhenCutGemsCannotBeFletched() {
        assertTrue(GemsScript.shouldStopForLevels(false, false));
    }

    @Test
    void metCraftingLevelNeverStops() {
        assertFalse(GemsScript.shouldStopForLevels(true, false));
        assertFalse(GemsScript.shouldStopForLevels(true, true));
    }

    @Test
    void outOfMaterialsMessageNamesBothGemFormsWhenFletching() {
        assertEquals("Out of uncut and cut Ruby in your bank. Stopping.",
                GemsScript.outOfMaterialsMessage("Ruby", false, true));
    }

    @Test
    void outOfMaterialsMessageNamesUncutGemWithoutFletching() {
        assertEquals("Out of uncut Ruby in your bank. Stopping.",
                GemsScript.outOfMaterialsMessage("Ruby", false, false));
    }

    @Test
    void outOfMaterialsMessageNamesAmethyst() {
        assertEquals("Out of amethyst in your bank. Stopping.",
                GemsScript.outOfMaterialsMessage("Amethyst bolt tips", true, false));
    }
}
