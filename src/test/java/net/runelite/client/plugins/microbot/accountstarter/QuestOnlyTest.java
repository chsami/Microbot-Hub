package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Planner;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import org.junit.jupiter.api.Test;

class QuestOnlyTest {
    private static final int MYSTIC_AIR = 1405, MYSTIC_FIRE = 1401, BATTLE_AIR = 1397;
    private static final int LOTS = 10_000;

    @Test
    void aBankWithRunesGivesFireStrikeOnTheAirStaff() {
        GearPlan.QuestKit k = GearPlan.questKit(98, 83, Map.of(MYSTIC_AIR, 1, GearPlan.FIRE, LOTS, GearPlan.MIND, LOTS, GearPlan.AIR, LOTS));
        assertEquals(MYSTIC_AIR, k.getStaff());
        assertEquals(Rs2CombatSpells.FIRE_STRIKE, k.getSpell());
        assertEquals(Map.of(GearPlan.FIRE, 3 * GearPlan.QUEST_CASTS, GearPlan.MIND, GearPlan.QUEST_CASTS), k.runes());
    }

    @Test
    void aStaffCoversItsRune() {
        GearPlan.QuestKit k = GearPlan.questKit(98, 83, Map.of(MYSTIC_FIRE, 1, GearPlan.AIR, LOTS, GearPlan.MIND, LOTS));
        assertEquals(Rs2CombatSpells.FIRE_STRIKE, k.getSpell());
        assertEquals(Map.of(GearPlan.AIR, 2, GearPlan.MIND, 1), k.getPerCast());
    }

    @Test
    void wieldLevelsNeedAttackAndMagic() {
        Map<Integer, Integer> owned = Map.of(MYSTIC_AIR, 1, BATTLE_AIR, 1, GearPlan.MIND, LOTS, GearPlan.AIR, LOTS);
        assertEquals(BATTLE_AIR, GearPlan.questKit(98, 35, owned).getStaff());
        assertNull(GearPlan.questKit(98, 20, owned));
        assertNull(GearPlan.questKit(20, 98, owned));
    }

    @Test
    void lowerStrikeWhenRunesOrLevelFallShort() {
        Map<Integer, Integer> owned = Map.of(GearPlan.STAFF, 1, GearPlan.AIR, LOTS, GearPlan.MIND, LOTS, GearPlan.FIRE, 3);
        assertEquals(Rs2CombatSpells.WIND_STRIKE, GearPlan.questKit(98, 1, owned).getSpell());
        Map<Integer, Integer> water = Map.of(GearPlan.STAFF, 1, GearPlan.AIR, LOTS, GearPlan.MIND, LOTS, GearPlan.WATER, LOTS);
        assertEquals(Rs2CombatSpells.WIND_STRIKE, GearPlan.questKit(4, 1, water).getSpell());
    }

    @Test
    void noKitWithoutStaffOrEnoughRunes() {
        assertNull(GearPlan.questKit(98, 83, Map.of(GearPlan.AIR, LOTS, GearPlan.MIND, LOTS)));
        assertNull(GearPlan.questKit(98, 83, Map.of(MYSTIC_AIR, 1, GearPlan.MIND, GearPlan.QUEST_CASTS - 1)));
    }

    @Test
    void hasCastCountsCarriedRunesOnly() {
        GearPlan.QuestKit k = GearPlan.questKit(98, 83, Map.of(MYSTIC_AIR, 1, GearPlan.FIRE, LOTS, GearPlan.MIND, LOTS));
        assertTrue(k.hasCast(snap(false).inventory(Map.of(GearPlan.FIRE, 3, GearPlan.MIND, 1)).build()));
        assertFalse(k.hasCast(snap(false).inventory(Map.of(GearPlan.FIRE, 2, GearPlan.MIND, 1)).bank(Map.of(GearPlan.FIRE, LOTS)).build()));
    }

    @Test
    void plannerRunsTheQuestThenStops() {
        assertEquals(Decision.Kind.DO_QUEST, Planner.questOnly(snap(true).build(), false).getKind());
        assertEquals(Decision.Kind.DONE, Planner.questOnly(snap(false).build(), false).getKind());
        assertEquals(Decision.Kind.WAIT, Planner.questOnly(snap(true).loggedIn(false).build(), false).getKind());
    }

    @Test
    void lampsOnlyOnceASkillIsPicked() {
        AccountSnapshot lamps = snap(false).inventory(Map.of(ApTask.QUEST_LAMP_ID, 4)).build();
        assertEquals(Decision.Kind.USE_LAMP, Planner.questOnly(lamps, true).getKind());
        assertEquals(Decision.Kind.DONE, Planner.questOnly(lamps, false).getKind());
    }

    @Test
    void questOnlyChoosesAndProtectsNothing() {
        assertTrue(Choices.questOnly().getChosen().isEmpty());
        assertTrue(Choices.questOnly().getProtectedSkills().isEmpty());
    }

    private static AccountSnapshot.AccountSnapshotBuilder snap(boolean toDo) {
        return AccountSnapshot.builder().loggedIn(true).mudfootToDo(toDo);
    }
}
