package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void unresolvedStyleIsUnknown() {
        assertNull(PotionManagerScript.currentStyle(false, SLASH_SWORD, null));
        assertNull(PotionManagerScript.currentStyle(false, BOW, null));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(true, BOW, null));
        assertEquals(AttackStyle.MAGE, PotionManagerScript.currentStyle(false, POWERED_STAFF, null));
    }

    @Test
    void meleeDrinksStrengthAttackAndSuperCombatDefence() {
        Map<Skill, Boolean> expected = new LinkedHashMap<>();
        expected.put(Skill.STRENGTH, true);
        expected.put(Skill.ATTACK, true);
        expected.put(Skill.DEFENCE, true);
        assertEquals(expected, PotionManagerScript.combatPotionsFor(AttackStyle.MELEE));
    }

    @Test
    void rangedDrinksRangingAndDefenceWithoutSuperCombat() {
        Map<Skill, Boolean> expected = new LinkedHashMap<>();
        expected.put(Skill.RANGED, false);
        expected.put(Skill.DEFENCE, false);
        assertEquals(expected, PotionManagerScript.combatPotionsFor(AttackStyle.RANGED));
    }

    @Test
    void magicDrinksMagicAndDefenceWithoutSuperCombat() {
        Map<Skill, Boolean> expected = new LinkedHashMap<>();
        expected.put(Skill.MAGIC, false);
        expected.put(Skill.DEFENCE, false);
        assertEquals(expected, PotionManagerScript.combatPotionsFor(AttackStyle.MAGE));
    }

    @Test
    void unknownStyleDrinksOnlyDefenceWithoutSuperCombat() {
        Map<Skill, Boolean> expected = new LinkedHashMap<>();
        expected.put(Skill.DEFENCE, false);
        assertEquals(expected, PotionManagerScript.combatPotionsFor(null));
    }
}
