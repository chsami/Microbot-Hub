package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Habits;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.NeedResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.LoopRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.ProgressReader;
import net.runelite.client.plugins.microbot.accountstarter.logic.ResumeState;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.run.FoodRunActivity;
import net.runelite.client.plugins.microbot.accountstarter.run.Kit;
import net.runelite.client.plugins.microbot.accountstarter.run.MudfootActivity;
import net.runelite.client.plugins.microbot.accountstarter.run.Watchdogs;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFixesTest {
    private static AccountSnapshot.AccountSnapshotBuilder snap() {
        return AccountSnapshot.builder().xp(Map.of(Skill.ATTACK, 100));
    }

    @Test
    void theQuestFoodRunNeverStopsShortOfWhatTheQuestWants() {
        for (int foodStock = 6; foodStock <= 10; foodStock++) {
            AccountSnapshot s = snap().foodStock(foodStock).build();
            assertEquals(MudfootActivity.MIN_FOOD, FoodRunActivity.stock(ApTask.RANGED_5, s, MudfootActivity.MIN_FOOD), "stock " + foodStock);
        }
        assertEquals(3, FoodRunActivity.stock(ApTask.RANGED_5, snap().foodStock(6).build(), 0));
    }

    @Test
    void aChangedProtectedSetNeedsAFreshBaseline() {
        Watchdogs w = new Watchdogs(null, () -> { }, reason -> { });
        Set<Skill> mining = Set.of(Skill.MINING);
        assertFalse(w.armedFor(mining));
        w.arm(snap().build(), mining);
        assertTrue(w.armedFor(mining));
        assertFalse(w.armedFor(Set.of()));
        w.disarm();
        assertFalse(w.armedFor(mining));
    }

    @Test
    void finishingTheQuestOrALampCountsAsProgress() {
        AccountSnapshot.AccountSnapshotBuilder base = AccountSnapshot.builder().progress(ProgressReader.read(v -> 0));
        String questOpen = LoopRules.fingerprint(base.mudfootToDo(true).build());
        assertFalse(questOpen.equals(LoopRules.fingerprint(base.mudfootToDo(false).build())));
        assertFalse(questOpen.equals(LoopRules.fingerprint(base.mudfootToDo(true).inventory(Map.of(ApTask.QUEST_LAMP_ID, 2)).build())));
    }

    @Test
    void aSaveOnAnInterruptedThreadStillLandsAndKeepsTheInterrupt(@TempDir Path dir) {
        ResumeStore store = new ResumeStore(dir);
        ResumeState s = new ResumeState();
        s.setMudfootStep(3);
        Thread.currentThread().interrupt();
        store.save("abc", s);
        assertTrue(Thread.interrupted());
        assertEquals(3, new ResumeStore(dir).load("abc").getMudfootStep());
    }

    @Test
    void flyFishingIsUsedWhenTheGearAndLevelsAllowIt() {
        assertEquals(FoodRunActivity.Method.FLY, FoodRunActivity.method(20, 15, true, true));
        assertEquals(FoodRunActivity.Method.BAIT, FoodRunActivity.method(20, 15, true, false));
        assertEquals(FoodRunActivity.Method.BAIT, FoodRunActivity.method(19, 15, true, true), "too low to fly fish");
        assertEquals(FoodRunActivity.Method.NET, FoodRunActivity.method(30, 24, false, true), "salmon could not be cooked");
    }

    @Test
    void theCheapestBurnableLogIsTaken() {
        assertEquals(ItemID.LOGS, FoodRunActivity.fireLog(Map.of(ItemID.OAK_LOGS, 5, ItemID.LOGS, 1), 15));
        assertEquals(0, FoodRunActivity.fireLog(Map.of(ItemID.OAK_LOGS, 5), 14));
        assertEquals(ItemID.YEW_LOGS, FoodRunActivity.fireLog(Map.of(ItemID.YEW_LOGS, 1), 60));
        assertEquals(0, FoodRunActivity.fireLog(Map.of(ItemID.TEAK_LOGS, 9), 99), "members logs are never picked");
    }

    @Test
    void choppingComesBeforeTheRoofAndNoWayMeansNowhere() {
        assertEquals(FoodRunActivity.CookAt.CHOP_LOGS, FoodRunActivity.cookAt(false, false, false, false, false, true, true));
        assertEquals(FoodRunActivity.CookAt.NOWHERE, FoodRunActivity.cookAt(false, false, false, false, false, false, false));
    }

    @Test
    void anIdListedTwiceWithdrawsTwoAndAStackOnlyWhenNoneIsCarried() {
        assertEquals(Map.of(ItemID.LOGS, 1), Kit.extrasMissing(List.of(ItemID.LOGS, ItemID.LOGS), Map.of(ItemID.LOGS, 1)));
        assertEquals(Map.of(), Kit.extrasMissing(List.of(ItemID.FEATHER), Map.of(ItemID.FEATHER, 3)));
        assertEquals(Map.of(ItemID.FEATHER, 1), Kit.extrasMissing(List.of(ItemID.FEATHER), Map.of()));
    }

    @Test
    void cookFoodOffProtectsFiremakingAndABlockedRunStopsAskingForFood() {
        Choices off = Choices.from(Preset.EVERYTHING, Map.of(), true, false, false);
        assertTrue(off.getProtectedSkills().containsAll(Set.of(Skill.COOKING, Skill.FIREMAKING)));
        assertTrue(NeedResolver.canCatchAndCook(snap().build()));
        assertFalse(NeedResolver.canCatchAndCook(snap().foodBlocked(true).build()));
    }

    @Test
    void accountsStartTheVoucherRoundAtDifferentNpcs() {
        Set<Integer> starts = new java.util.HashSet<>();
        for (long seed = 0; seed < 50; seed++) starts.add(Habits.draw(seed).tuning().getVoucherStart());
        assertEquals(Set.of(0, 1, 2, 3), starts);
    }

    @Test
    void aSessionShiftsThePaceAndALapseIsRareButLong() {
        ApPace base = ApPace.draw(7L);
        Random a = new Random(1), b = new Random(1);
        ApPace session = base.forSession(new Random(99));
        assertFalse(base.reactionMs(a) == session.reactionMs(b));
        Random r = new Random(5);
        long lapses = java.util.stream.LongStream.range(0, 5000).map(i -> base.reactionMs(r)).filter(ms -> ms > 1500).count();
        assertTrue(lapses > 40 && lapses < 200, "lapses " + lapses);
        Random g = new Random(3);
        assertTrue(java.util.stream.LongStream.range(0, 200).map(i -> base.levelUpMs(g, 1.0)).min().getAsLong() >= 1000);
    }

    @Test
    void shopsInVarrockGetTheDetourFromLumbridge() {
        WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
        assertFalse(Spots.waypoints(lumbridge, Spots.VARROCK.get("Lowe"), 3).isEmpty());
        assertFalse(Spots.waypoints(lumbridge, Spots.VARROCK.get("Aubury"), 3).isEmpty());
    }

    @Test
    void theDetourEndsWhereTheDarkWizardsStopAttacking() {
        WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
        WorldPoint lowe = Spots.VARROCK.get("Lowe");
        assertFalse(Spots.waypoints(lumbridge, lowe, 0).isEmpty(), "unreadable level");
        assertFalse(Spots.waypoints(lumbridge, lowe, 40).isEmpty(), "level 20 wizards still attack");
        assertTrue(Spots.waypoints(lumbridge, lowe, 41).isEmpty(), "both kinds ignore the player");
    }
}
