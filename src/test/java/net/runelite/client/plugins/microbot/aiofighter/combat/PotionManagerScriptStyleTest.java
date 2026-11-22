package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class PotionManagerScriptStyleTest {

    private static final int SLASH_SWORD = 9;
    private static final int BOW = 3;
    private static final int CHINCHOMPA = 7;
    private static final int SALAMANDER = 6;
    private static final int STAFF = 18;
    private static final int POWERED_STAFF = 24;
    private static final int POWERED_WAND = 29;

    @Test
    void meleeWeaponUsesMeleePotions() {
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, SLASH_SWORD, "Aggressive"));
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, SLASH_SWORD, "Controlled"));
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, SLASH_SWORD, "Defensive"));
    }

    @Test
    void rangedWeaponUsesRangedPotions() {
        assertEquals(AttackStyle.RANGED, PotionManagerScript.currentStyle(false, BOW, "Ranging"));
        assertEquals(AttackStyle.RANGED, PotionManagerScript.currentStyle(false, CHINCHOMPA, "Longrange"));
    }

    @Test
    void castingUsesMagicPotions() {
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, STAFF, "Casting"));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, STAFF, "Defensive Casting"));
    }

    @Test
    void staffMeleeStylesUseMeleePotions() {
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, STAFF, "Accurate"));
    }

    @Test
    void poweredStavesUseMagicPotionsInEveryStyle() {
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, POWERED_STAFF, "Accurate"));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, POWERED_STAFF, "Defensive"));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, POWERED_WAND, "Accurate"));
    }

    @Test
    void salamanderFollowsTheSelectedStyle() {
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, SALAMANDER, "Aggressive"));
        assertEquals(AttackStyle.RANGED, PotionManagerScript.currentStyle(false, SALAMANDER, "Ranging"));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, SALAMANDER, "Casting"));
    }

    @Test
    void useMagicConfigUsesMagicPotions() {
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(true, SLASH_SWORD, "Aggressive"));
    }

    @Test
    void unresolvedStyleFallsBackToMelee() {
        assertEquals(AttackStyle.MELEE, PotionManagerScript.currentStyle(false, SLASH_SWORD, null));
    }
}
