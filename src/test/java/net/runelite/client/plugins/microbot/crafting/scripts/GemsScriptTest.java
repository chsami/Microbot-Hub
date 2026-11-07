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
