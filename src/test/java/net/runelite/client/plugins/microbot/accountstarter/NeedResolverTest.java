package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.FreeSource;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.NeedResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.logic.Resolution;
import net.runelite.client.plugins.microbot.accountstarter.logic.Resolution.Kind;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedResolverTest {
    private static Progress progress(Map<ApTask, TaskState> tasks) {
        return new Progress(tasks, Collections.emptyMap(), Collections.emptySet(), true, false, false);
    }

    private static AccountSnapshot.AccountSnapshotBuilder snap() {
        return AccountSnapshot.builder().loggedIn(true).bankKnown(true).progress(progress(Collections.emptyMap()));
    }

    private static Need hungryFood(AccountSnapshot s) {
        Need n = only(ApTask.ATTACK_20, s);
        return new Need(n.getLabel(), n.getAnyOf(), n.getQuantity(), n.getFree(), n.getBuyItemId(), n.getBuyName(), n.getMinimum(), 1);
    }

    private static Need only(ApTask t, AccountSnapshot s) {
        return Needs.forTask(t, s).get(0);
    }

    @Test
    void ownedNetIsUsed() {
        AccountSnapshot s = snap().inventory(Map.of(303, 1)).build();
        assertEquals(Kind.OWNED, NeedResolver.resolve(only(ApTask.FISHING_5, s), s, true, 1000, id -> 5, 1.25).getKind());
    }

    @Test
    void unclaimedRewardBeatsBuying() {
        AccountSnapshot s = snap().levels(Map.of(Skill.RANGED, 10)).coins(10000)
                .progress(progress(Map.of(ApTask.RANGED_10, TaskState.DONE_UNCLAIMED))).build();
        Resolution r = NeedResolver.resolve(only(ApTask.RANGED_20, s), s, true, 10000, id -> 50, 1.25);
        assertEquals(Kind.CLAIM_REWARD, r.getKind());
        assertEquals(ApTask.RANGED_10, r.getFromReward());
    }

    @Test
    void unknownBankMeansOpenBankNotBuy() {
        AccountSnapshot s = snap().bankKnown(false).coins(10000).build();
        assertEquals(Kind.OPEN_BANK, NeedResolver.resolve(only(ApTask.RANGED_5, s), s, true, 10000, id -> 50, 1.25).getKind());
    }

    @Test
    void tutorGivesFreeAxe() {
        AccountSnapshot s = snap().build();
        Resolution r = NeedResolver.resolve(only(ApTask.WOODCUTTING_5, s), s, false, 0, id -> 50, 1.25);
        assertEquals(Kind.FREE, r.getKind());
        assertNull(r.getFromReward());
    }

    @Test
    void noCoinsParksWithReason() {
        AccountSnapshot s = snap().coins(0).wantsFood(true).protectedSkills(Set.of(Skill.FISHING)).build();
        Resolution r = NeedResolver.resolve(hungryFood(s), s, true, 1000, id -> 50, 1.25);
        assertEquals(Kind.PARK, r.getKind());
        assertEquals("need 250 coins for food", r.getReason());
    }

    @Test
    void affordableBuysAndBuyingOffParks() {
        AccountSnapshot s = snap().coins(500).wantsFood(true).protectedSkills(Set.of(Skill.FISHING)).build();
        Need food = hungryFood(s);
        assertEquals(Kind.BUY, NeedResolver.resolve(food, s, true, 1000, id -> 50, 1.25).getKind());
        assertEquals(Kind.PARK, NeedResolver.resolve(food, s, true, 10, id -> 50, 1.25).getKind());
        assertEquals("no food: Fishing/Cooking protected and buying off", NeedResolver.resolve(food, s, false, 1000, id -> 50, 1.25).getReason());
    }

    @Test
    void noFoodStockUntilAFightRunsOut() {
        AccountSnapshot s = snap().foodStock(8).build();
        for (ApTask t : new ApTask[]{ApTask.ATTACK_5, ApTask.ATTACK_10, ApTask.ATTACK_20, ApTask.RANGED_10, ApTask.MAGIC_5}) {
            Need small = Needs.forTask(t, s).stream().filter(n -> n.getLabel().equals("food")).findFirst().get();
            assertEquals(4, small.getQuantity(), t.name());
            assertEquals(0, small.getMinimum(), t.name());
            assertEquals(Kind.OWNED, NeedResolver.resolve(small, s, true, 1000, id -> 50, 1.25).getKind(), t.name());
        }
        AccountSnapshot hungry = snap().foodStock(8).wantsFood(true).inventory(Map.of(303, 1)).build();
        Need want = only(ApTask.ATTACK_10, hungry);
        assertEquals(4, want.getMinimum());
        assertEquals(Kind.FREE, NeedResolver.resolve(want, hungry, false, 0, id -> 50, 1.25).getKind());
        Need big = only(ApTask.ATTACK_20, hungry);
        assertEquals(4, big.getQuantity());
        assertEquals(4, big.getMinimum());
        assertEquals(0, big.getLeast());
        assertEquals(FreeSource.CATCH_AND_COOK, big.getFree());
        for (int stock = 6; stock <= 10; stock++) {
            assertTrue(Needs.smallStock(stock) >= 3 && Needs.smallStock(stock) <= 5);
        }
        AccountSnapshot none = snap().wantsFood(true).protectedSkills(Set.of(Skill.FISHING)).build();
        assertEquals(Kind.OWNED, NeedResolver.resolve(only(ApTask.ATTACK_5, none), none, false, 0, id -> 50, 1.25).getKind());
    }

    @Test
    void strengthRewardFoodBeatsCatching() {
        AccountSnapshot s = snap().inventory(Map.of(303, 1)).wantsFood(true)
                .progress(progress(Map.of(ApTask.STRENGTH_5, TaskState.DONE_UNCLAIMED))).build();
        Resolution r = NeedResolver.resolve(only(ApTask.ATTACK_20, s), s, true, 1000, id -> 50, 1.25);
        assertEquals(Kind.CLAIM_REWARD, r.getKind());
        assertEquals(ApTask.STRENGTH_5, r.getFromReward());
    }

    @Test
    void catchAndCookWhenNetOwnedAndSkillsAllowed() {
        AccountSnapshot s = snap().inventory(Map.of(303, 1)).coins(10_000).wantsFood(true).build();
        Resolution r = NeedResolver.resolve(only(ApTask.ATTACK_20, s), s, true, 10_000, id -> 50, 1.25);
        assertEquals(Kind.FREE, r.getKind());
        assertEquals("food: catch and cook", r.getReason());
        assertTrue(NeedResolver.canCatchAndCook(s));
    }

    @Test
    void noCatchWhenCookingProtected() {
        AccountSnapshot s = snap().inventory(Map.of(303, 1)).wantsFood(true).protectedSkills(Set.of(Skill.COOKING)).build();
        Resolution r = NeedResolver.resolve(hungryFood(s), s, false, 0, id -> 50, 1.25);
        assertEquals(Kind.PARK, r.getKind());
        assertEquals("no food: Fishing/Cooking protected and buying off", r.getReason());
        AccountSnapshot rich = snap().coins(1000).wantsFood(true).protectedSkills(Set.of(Skill.COOKING)).build();
        assertEquals(Kind.BUY, NeedResolver.resolve(hungryFood(rich), rich, true, 1000, id -> 50, 1.25).getKind());
    }

    @Test
    void notedFoodIsNotEdible() {
        AccountSnapshot s = snap().inventory(Map.of(333, 12, 315, 2)).notedInventory(Map.of(333, 12)).build();
        assertEquals(2, Needs.edible(s));
        assertEquals(0, s.carried(333));
        assertEquals(12, s.owned(333));
    }

    @Test
    void rangedBuysShortbowWhenNoBow() {
        AccountSnapshot s = snap().coins(500).build();
        List<Need> ns = Needs.forTask(ApTask.RANGED_5, s);
        assertTrue(ns.get(0).getAnyOf().contains(GearPlan.TRAINING_BOW));
        Resolution r = NeedResolver.resolve(ns.get(0), s, true, 1000, id -> 50, 1.25);
        assertEquals(Kind.BUY, r.getKind());
        assertEquals("a shortbow: buy shortbow", r.getReason());
        assertEquals(Set.of(882, 884), ns.get(1).getAnyOf());
        AccountSnapshot poor = snap().build();
        assertEquals("need 50 coins for a shortbow",
                NeedResolver.resolve(Needs.forTask(ApTask.RANGED_5, poor).get(0), poor, true, 1000, id -> 50, 1.25).getReason());
        AccountSnapshot real = snap().bank(Map.of(841, 1, GearPlan.TRAINING_BOW, 1, 882, 100)).build();
        assertEquals(841, GearPlan.bestWeapon(Skill.RANGED, real).getAsInt());
        assertEquals(Set.of(882, 884), Needs.forTask(ApTask.RANGED_5, real).get(1).getAnyOf());
    }

    @Test
    void ownedArrowsWithTrainingBowNeedTrainingArrows() {
        AccountSnapshot s = snap().bank(Map.of(GearPlan.TRAINING_BOW, 1, 882, 75)).build();
        assertEquals(GearPlan.TRAINING_BOW, GearPlan.bestWeapon(Skill.RANGED, s).getAsInt());
        List<Need> ns = Needs.forTask(ApTask.RANGED_5, s);
        Need arrows = ns.get(1);
        assertEquals(Set.of(GearPlan.TRAINING_ARROWS), arrows.getAnyOf());
        assertEquals(10, arrows.getMinimum());
        assertEquals(Kind.OWNED, NeedResolver.resolve(ns.get(0), s, false, 0, id -> 50, 1.25).getKind());
        assertEquals("training arrows: cannot be bought", NeedResolver.resolve(arrows, s, false, 0, id -> 50, 1.25).getReason());
        AccountSnapshot few = snap().bank(Map.of(GearPlan.TRAINING_BOW, 1, GearPlan.TRAINING_ARROWS, 4)).build();
        assertEquals(Kind.OWNED, NeedResolver.resolve(Needs.forTask(ApTask.RANGED_5, few).get(1), few, false, 0, id -> 50, 1.25).getKind());
    }

    @Test
    void magicRunesHaveNoFreeSource() {
        AccountSnapshot s = snap().levels(Map.of(Skill.MAGIC, 1)).build();
        List<Need> ns = Needs.forTask(ApTask.MAGIC_5, s);
        assertEquals(3, ns.size());
        ns = ns.stream().filter(n -> n.getLabel().equals("runes")).collect(java.util.stream.Collectors.toList());
        assertEquals(2, ns.size());
        for (Need n : ns) {
            assertNull(n.getFree());
            assertEquals(GearPlan.MIN_CASTS, n.getMinimum());
            assertEquals("runes: buying is off", NeedResolver.resolve(n, s, false, 0, id -> 5, 1.25).getReason());
        }
    }

    private static Need air(AccountSnapshot s) {
        return Needs.forTask(ApTask.MAGIC_5, s).stream().filter(n -> n.getAnyOf().contains(GearPlan.AIR)).findFirst().get();
    }

    @Test
    void runesBoughtWhenCoinsCoverTheRestOfTheTask() {
        AccountSnapshot s = snap().levels(Map.of(Skill.MAGIC, 1)).coins(375).build();
        Resolution r = NeedResolver.resolve(air(s), s, true, 1000, id -> 5, 1.25);
        assertEquals(Kind.BUY, r.getKind());
        assertEquals(375, r.getCoinsNeeded());
        assertEquals(75, r.getBuyQuantity());
        Resolution capped = NeedResolver.resolve(air(s), s, true, 374, id -> 5, 1.25);
        assertEquals(Kind.BUY, capped.getKind());
        assertEquals("runes: buy a start of air runes", capped.getReason());
        assertEquals(25, capped.getBuyQuantity());
        Resolution broke = NeedResolver.resolve(air(s), s, true, 64, id -> 5, 1.25);
        assertEquals("need 375 coins for runes", broke.getReason());
    }

    @Test
    void ownedRunesFightOnWhenBuyingOff() {
        AccountSnapshot s = snap().levels(Map.of(Skill.MAGIC, 1)).bank(Map.of(GearPlan.AIR, 100, GearPlan.MIND, 4)).build();
        Need mind = Needs.forTask(ApTask.MAGIC_5, s).stream().filter(n -> n.getAnyOf().contains(GearPlan.MIND)).findFirst().get();
        Resolution off = NeedResolver.resolve(mind, s, false, 0, id -> 5, 1.25);
        assertEquals(Kind.OWNED, off.getKind());
        assertEquals("runes: 4 owned, all there is for now", off.getReason());
        AccountSnapshot rich = snap().levels(Map.of(Skill.MAGIC, 1)).coins(1000).bank(Map.of(GearPlan.AIR, 100, GearPlan.MIND, 4)).build();
        assertEquals(Kind.BUY, NeedResolver.resolve(mind, rich, true, 1000, id -> 5, 1.25).getKind());
    }

    @Test
    void needsFollowTheTask() {
        AccountSnapshot s = snap().levels(Map.of(Skill.RANGED, 5, Skill.MAGIC, 1)).build();
        assertEquals(3, Needs.forTask(ApTask.RANGED_5, s).size());
        assertEquals(Set.of(GearPlan.TRAINING_BOW, 841, 843), Needs.forTask(ApTask.RANGED_5, s).get(0).getAnyOf());
        assertEquals(3, Needs.forTask(ApTask.RANGED_10, s).size());
        assertEquals("food", Needs.forTask(ApTask.ATTACK_5, s).get(0).getLabel());
        assertEquals("food", Needs.forTask(ApTask.ATTACK_10, s).get(0).getLabel());
        assertEquals(3, Needs.forTask(ApTask.MAGIC_5, s).size());
        AccountSnapshot staffed = snap().equipment(Map.of(1381, 1)).build();
        assertEquals(Set.of(558), Needs.forTask(ApTask.MAGIC_5, staffed).get(0).getAnyOf());
    }
}
