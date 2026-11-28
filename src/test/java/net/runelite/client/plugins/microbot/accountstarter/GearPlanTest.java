package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearPlanTest {
    @Test
    void mithrilNeedsAttack20() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.ATTACK, 19))
                .inventory(Map.of(1299, 1, 1297, 1)).build();
        assertEquals(1297, GearPlan.bestWeapon(Skill.ATTACK, s).getAsInt());
        assertTrue(GearPlan.bestWeapon(Skill.RANGED, s).isEmpty());
    }

    @Test
    void plainStaffIsUsedForMagic() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 1)).inventory(Map.of(1379, 1)).build();
        assertEquals(1379, GearPlan.bestWeapon(Skill.MAGIC, s).getAsInt());
    }

    @Test
    void elementalStaffForTheStrikeBeatsPlain() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 1))
                .inventory(Map.of(1379, 1, 1383, 1, 1381, 1)).build();
        assertEquals(1381, GearPlan.bestWeapon(Skill.MAGIC, s).getAsInt());
    }

    @Test
    void fireStrikeNeedsFireRunes() {
        Map<Integer, Integer> airMind = Map.of(556, 1000, 558, 1000);
        assertEquals(Rs2CombatSpells.WIND_STRIKE, GearPlan.bestStrike(15, airMind));
        Map<Integer, Integer> withFire = Map.of(556, 1000, 558, 1000, 554, 300);
        assertEquals(Rs2CombatSpells.FIRE_STRIKE, GearPlan.bestStrike(15, withFire));
        assertEquals(Rs2CombatSpells.WIND_STRIKE, GearPlan.bestStrike(12, withFire));
        assertEquals(Rs2CombatSpells.FIRE_STRIKE, GearPlan.bestStrike(13, Map.of(1381, 1, 558, 100, 554, 300)));
    }

    @Test
    void earthStaffIsWornWhenOnlyAirRunesAreOwned() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 10))
                .bank(Map.of(1381, 1, 1385, 1, 556, 400, 558, 400)).build();
        assertEquals(Rs2CombatSpells.EARTH_STRIKE, GearPlan.bestStrike(10, GearPlan.ownedMap(s)));
        assertEquals(1385, GearPlan.bestWeapon(Skill.MAGIC, s).getAsInt());
        assertEquals(Map.of(556, 225, 558, 225), runeNeeds(s));
    }

    @Test
    void airStaffIsWornWhenOnlyEarthRunesAreOwned() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 10))
                .bank(Map.of(1381, 1, 1385, 1, 557, 700, 558, 400)).build();
        assertEquals(Rs2CombatSpells.EARTH_STRIKE, GearPlan.bestStrike(10, GearPlan.ownedMap(s)));
        assertEquals(1381, GearPlan.bestWeapon(Skill.MAGIC, s).getAsInt());
        assertEquals(Map.of(557, 450, 558, 225), runeNeeds(s));
    }

    @Test
    void neitherStaffCastsEarthStrikeSoWindStrikeWithAirStaff() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 10))
                .bank(Map.of(1381, 1, 1385, 1, 558, 250)).build();
        assertEquals(Rs2CombatSpells.WIND_STRIKE, GearPlan.bestStrike(10, GearPlan.ownedMap(s)));
        assertEquals(1381, GearPlan.bestWeapon(Skill.MAGIC, s).getAsInt());
        assertEquals(Map.of(558, 308), runeNeeds(s));
    }

    @Test
    void magicCastsFollowXpNeeded() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 1)).build();
        Map<Integer, Integer> out = new HashMap<>();
        for (Need n : Needs.forTask(ApTask.MAGIC_5, s)) if (n.getLabel().equals("runes")) out.put(n.getBuyItemId(), n.getQuantity());
        assertEquals(Map.of(556, 57, 558, 57), out);
        assertEquals(57, GearPlan.casts(Rs2CombatSpells.WIND_STRIKE, 1, 388));
        assertEquals(71, GearPlan.casts(Rs2CombatSpells.WIND_STRIKE, 388));
        assertEquals(GearPlan.MIN_CASTS, GearPlan.casts(Rs2CombatSpells.FIRE_STRIKE, 20));
    }

    @Test
    void magicCastsCountDamageXp() {
        assertEquals(73, GearPlan.casts(Rs2CombatSpells.WATER_STRIKE, 5, 766));
        assertEquals(308, GearPlan.casts(Rs2CombatSpells.WIND_STRIKE, 10, 3316));
        assertTrue(GearPlan.casts(Rs2CombatSpells.WIND_STRIKE, 10, 3316) <= GearPlan.casts(Rs2CombatSpells.WIND_STRIKE, 3316));
    }

    @Test
    void arrowsFollowTheRangedXpStillNeeded() {
        assertEquals(71, GearPlan.arrowsNeeded(0, 388, GearPlan.ARROW_MISS_PICKUP));
        assertEquals(96, GearPlan.arrowsNeeded(388, 766, GearPlan.ARROW_MISS_PICKUP));
        assertEquals(350, GearPlan.arrowsNeeded(1154, 3316, GearPlan.ARROW_MISS_PICKUP));
        assertEquals(220, GearPlan.arrowsNeeded(0, 388, 1.0));
        assertEquals(0, GearPlan.arrowsNeeded(388, 0, GearPlan.ARROW_MISS_PICKUP));
    }

    @Test
    void arrowNeedStartsOnWhatIsOwned() {
        AccountSnapshot none = AccountSnapshot.builder().levels(Map.of(Skill.RANGED, 1)).inventory(Map.of(841, 1)).build();
        Need arrows = Needs.forTask(ApTask.RANGED_5, none).get(1);
        assertEquals("arrows", arrows.getLabel());
        assertEquals(71, arrows.getQuantity());
        assertEquals(25, arrows.getMinimum());
        AccountSnapshot kit = none.toBuilder().bank(Map.of(882, 75)).build();
        assertEquals(71, Needs.forTask(ApTask.RANGED_5, kit).get(1).getMinimum());
        AccountSnapshot ten = AccountSnapshot.builder().levels(Map.of(Skill.RANGED, 10)).xp(Map.of(Skill.RANGED, 1154))
                .inventory(Map.of(843, 1, 882, 100)).build();
        Need big = Needs.forTask(ApTask.RANGED_20, ten).get(1);
        assertEquals(350, big.getQuantity());
        assertEquals(100, big.getMinimum());
    }

    private static Map<Integer, Integer> runeNeeds(AccountSnapshot s) {
        Map<Integer, Integer> out = new HashMap<>();
        for (Need n : Needs.forTask(ApTask.MAGIC_20, s)) {
            if (n.getLabel().equals("runes")) out.put(n.getBuyItemId(), n.getQuantity());
        }
        return out;
    }

    @Test
    void bestArmourPicksHighestMetDefence() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.DEFENCE, 10))
                .bank(Map.of(1137, 1, 1141, 1, 1105, 1, 1109, 1, 1061, 1)).build();
        assertEquals(Arrays.asList(1141, 1105, 1061), GearPlan.bestArmour(s, Skill.ATTACK));
    }

    @Test
    void oneHeadItemChosenByStyleAndNoMetalForRangedOrMagic() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.DEFENCE, 10, Skill.RANGED, 10))
                .bank(Map.of(1137, 1, 1141, 1, 1167, 1, 1169, 1, 1105, 1, 1067, 1, 1061, 1, 1063, 1, 1017, 1, 581, 1))
                .inventory(Map.of(1015, 1, 1129, 1)).build();
        assertEquals(Arrays.asList(1141, 1105, 1067, 1061, 1063), GearPlan.bestArmour(s, Skill.STRENGTH));
        assertEquals(Arrays.asList(1167, 1129, 1061, 1063), GearPlan.bestArmour(s, Skill.RANGED));
        AccountSnapshot twenty = s.toBuilder().levels(Map.of(Skill.DEFENCE, 10, Skill.RANGED, 20)).build();
        assertEquals(1169, GearPlan.bestArmour(twenty, Skill.RANGED).get(0).intValue());
        assertEquals(Arrays.asList(1017, 581, 1015, 1061, 1063), GearPlan.bestArmour(s, Skill.MAGIC));
        for (Skill k : new Skill[]{Skill.RANGED, Skill.MAGIC}) {
            assertTrue(GearPlan.bestArmour(s, k).stream().noneMatch(GearPlan.METAL_ARMOUR::contains), k.name());
            assertTrue(GearPlan.takeOff(k).containsAll(Arrays.asList(1137, 1141, 1101, 1105, 1109, 1067)), k.name());
        }
        assertTrue(GearPlan.takeOff(Skill.DEFENCE).isEmpty());
        AccountSnapshot cowlOnly = AccountSnapshot.builder().levels(Map.of(Skill.DEFENCE, 10)).bank(Map.of(1167, 1)).build();
        assertEquals(Arrays.asList(1167), GearPlan.bestArmour(cowlOnly, Skill.ATTACK));
    }
}
