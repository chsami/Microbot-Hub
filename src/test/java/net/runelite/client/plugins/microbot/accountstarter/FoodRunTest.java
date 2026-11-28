package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.AttackerRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.LootRules;
import net.runelite.client.plugins.microbot.accountstarter.run.CombatActivity;
import net.runelite.client.plugins.microbot.accountstarter.run.CombatActivity.OutOfFood;
import net.runelite.client.plugins.microbot.accountstarter.run.FoodRunActivity;
import net.runelite.client.plugins.microbot.accountstarter.run.FoodRunActivity.CookAt;
import net.runelite.client.plugins.microbot.accountstarter.run.FoodRunActivity.Step;
import net.runelite.client.plugins.microbot.accountstarter.run.Kit;
import org.junit.jupiter.api.Test;

class FoodRunTest {
    @Test
    void fishOnlyWhatCanBeCaughtAndCooked() {
        assertEquals(FoodRunActivity.Method.NET, FoodRunActivity.method(30, 30, false));
        assertEquals(FoodRunActivity.Method.NET, FoodRunActivity.method(4, 1, true));
        assertEquals(FoodRunActivity.Method.BAIT, FoodRunActivity.method(5, 1, true));
        assertEquals(FoodRunActivity.Method.NET, FoodRunActivity.method(10, 4, true));
        assertEquals(FoodRunActivity.Method.BAIT, FoodRunActivity.method(10, 5, true));
    }

    @Test
    void foodRunStopRule() {
        assertEquals(Step.FISH, FoodRunActivity.step(0, 5, 20, 8, 4, 0, 3));
        assertEquals(Step.COOK, FoodRunActivity.step(0, 12, 14, 8, 4, 0, 3));
        assertEquals(Step.COOK, FoodRunActivity.step(0, 3, 0, 8, 4, 0, 3));
        assertEquals(Step.COOK, FoodRunActivity.step(9, 2, 10, 8, 4, 1, 3));
        assertEquals(Step.DONE, FoodRunActivity.step(9, 0, 10, 8, 4, 1, 3));
        assertEquals(Step.OUT_OF_TRIES, FoodRunActivity.step(5, 0, 10, 8, 4, 3, 3));
        assertEquals(Step.FULL, FoodRunActivity.step(5, 0, 0, 8, 4, 1, 3));
        assertTrue(FoodRunActivity.extra(1, 7) > FoodRunActivity.extra(30, 7));
    }

    @Test
    void outOfFoodNeverBanksForNothing() {
        assertEquals(OutOfFood.BANK, CombatActivity.outOfFood(true, true, true));
        assertEquals(OutOfFood.BANK, CombatActivity.outOfFood(false, false, false));
        assertEquals(OutOfFood.FOOD_RUN, CombatActivity.outOfFood(true, false, true));
        assertEquals(OutOfFood.REST, CombatActivity.outOfFood(true, false, false));
    }

    @Test
    void kitBanksCoinsAndKeepsBonesWhileBurying() {
        int bones = LootRules.BONES.iterator().next();
        assertTrue(Kit.deposits(ItemID.COINS, false, Set.of(), false));
        assertFalse(Kit.deposits(bones, false, Set.of(), true));
        assertTrue(Kit.deposits(bones, false, Set.of(), false));
        assertTrue(Kit.deposits(ItemID.LOGS, false, Set.of(), true));
        assertFalse(Kit.deposits(ItemID.TROUT, false, Set.of(ItemID.TROUT), false));
        assertTrue(Kit.deposits(ItemID.TROUT, true, Set.of(ItemID.TROUT), false));
    }

    @Test
    void castleRangeOnlyAfterCooksAssistant() {
        assertEquals(CookAt.RANGE, FoodRunActivity.cookAt(true, false, false, false, false, false, true));
        assertEquals(CookAt.LIGHT_FIRE, FoodRunActivity.cookAt(true, false, false, false, true, false, true));
        assertEquals(CookAt.LIGHT_FIRE, FoodRunActivity.cookAt(false, false, false, false, true, false, true));
        assertEquals(CookAt.FIRE_NEAR, FoodRunActivity.cookAt(false, false, true, true, true, false, true));
        assertEquals(CookAt.ROOF_LOG, FoodRunActivity.cookAt(false, false, false, true, true, false, true));
        assertEquals(CookAt.CHOP_LOGS, FoodRunActivity.cookAt(false, false, false, false, false, true, true));
        assertEquals(CookAt.FREE_LOGS, FoodRunActivity.cookAt(false, false, false, false, false, true, false));
        assertEquals(CookAt.NOWHERE, FoodRunActivity.cookAt(false, false, false, false, false, false, false));
    }

    @Test
    void aStartedRodRunKeepsGoingUnderMinBait() {
        FoodRunActivity.Method bait = FoodRunActivity.Method.BAIT, net = FoodRunActivity.Method.NET;
        assertEquals(bait, FoodRunActivity.kept(bait, net, true, 5, 3, true, 19));
        assertEquals(net, FoodRunActivity.kept(bait, net, false, 5, 3, true, 19));
        assertEquals(net, FoodRunActivity.kept(bait, net, true, 5, 3, true, 0));
        assertEquals(net, FoodRunActivity.kept(bait, net, true, 5, 3, false, 19));
        assertEquals(net, FoodRunActivity.kept(bait, net, true, 10, 3, true, 19));
    }

    @Test
    void homeTeleportOnlyFromFarAfterAFailedWalk() {
        assertFalse(FoodRunActivity.homeTeleportOk(68, 5));
        assertFalse(FoodRunActivity.homeTeleportOk(300, 0));
        assertTrue(FoodRunActivity.homeTeleportOk(300, 1));
    }

    @Test
    void treesForAFireGoByLevelAndStopAtWillow() {
        assertEquals(java.util.List.of("Tree", "Dead tree"), FoodRunActivity.trees(1, 1));
        assertEquals(java.util.List.of("Tree", "Dead tree"), FoodRunActivity.trees(40, 10));
        assertEquals(java.util.List.of("Tree", "Dead tree", "Oak tree"), FoodRunActivity.trees(15, 15));
        assertEquals(java.util.List.of("Tree", "Dead tree", "Oak tree", "Willow tree"), FoodRunActivity.trees(99, 99));
    }

    @Test
    void ownArrowsAreWhatTheBowFiresAndLootedAlongTheFight() {
        Set<Integer> own = CombatActivity.ownArrows(Map.of(841, 1));
        assertTrue(own.containsAll(Set.of(ItemID.BRONZE_ARROW, ItemID.IRON_ARROW)));
        assertEquals(Set.of(GearPlan.TRAINING_ARROWS), CombatActivity.ownArrows(Map.of(GearPlan.TRAINING_BOW, 1)));
        WorldPoint kill = new WorldPoint(3200, 3200, 0);
        WorldPoint far = new WorldPoint(3206, 3203, 0);
        Set<WorldPoint> fought = Set.of(far);
        assertTrue(CombatActivity.lootTile(ItemID.BRONZE_ARROW, far, kill, own, fought));
        assertFalse(CombatActivity.lootTile(ItemID.BONES, far, kill, own, fought));
        assertTrue(CombatActivity.lootTile(ItemID.BONES, new WorldPoint(3201, 3201, 0), kill, own, fought));
        WorldPoint tooFar = new WorldPoint(3209, 3200, 0);
        assertFalse(CombatActivity.lootTile(ItemID.BRONZE_ARROW, tooFar, kill, own, Set.of(tooFar)));
        assertFalse(CombatActivity.lootTile(ItemID.BRONZE_ARROW, new WorldPoint(3205, 3203, 0), kill, own, fought));
    }

    @Test
    void weakNuisancesAreFoughtBackOthersWalkedFrom() {
        assertTrue(AttackerRules.fightBack("Giant rat", 3));
        assertTrue(AttackerRules.fightBack("Mugger", 6));
        assertTrue(AttackerRules.fightBack("Dark wizard", 7));
        assertTrue(AttackerRules.fightBack("Giant spider", 2));
        assertFalse(AttackerRules.fightBack("Dark wizard", 20));
        assertFalse(AttackerRules.fightBack("Giant rat", 6));
        assertFalse(AttackerRules.fightBack("Wizard", 9));
        assertFalse(AttackerRules.fightBack(null, 1));
    }

    @Test
    void kitWithdrawsOnlyInItemMode() {
        assertTrue(Kit.withdrawReady(true, false));
        assertFalse(Kit.withdrawReady(true, true));
        assertFalse(Kit.withdrawReady(false, false));
    }

    @Test
    void anchoviesAreNotCaughtForFood() {
        assertFalse(FoodRunActivity.RAW_FOOD.containsKey(net.runelite.api.gameval.ItemID.RAW_ANCHOVIES));
        assertTrue(FoodRunActivity.RAW_FOOD.containsKey(net.runelite.api.gameval.ItemID.RAW_SHRIMP));
        assertEquals(5, FoodRunActivity.RAW_FOOD.size());
    }
}
