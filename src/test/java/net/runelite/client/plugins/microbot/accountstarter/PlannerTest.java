package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision.Kind;
import net.runelite.client.plugins.microbot.accountstarter.logic.PacingMode;
import net.runelite.client.plugins.microbot.accountstarter.logic.LoopRules;
import net.runelite.client.plugins.microbot.accountstarter.logic.Planner;
import net.runelite.client.plugins.microbot.accountstarter.logic.PlannerMemory;
import net.runelite.client.plugins.microbot.accountstarter.logic.PlannerTuning;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.logic.QuestLampChoice;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {
    private static final PlannerTuning TUNING = new PlannerTuning(0, 3, 1000, 2000, 1.25, 0);

    private static Progress progress(Map<ApTask, TaskState> tasks) {
        return new Progress(tasks, Collections.emptyMap(), Collections.emptySet(), true, false, false);
    }

    private static AccountSnapshot.AccountSnapshotBuilder snap(Map<ApTask, TaskState> tasks) {
        return AccountSnapshot.builder().loggedIn(true).bankKnown(true).starterKitClaimed(true).progress(progress(tasks));
    }

    private static AccountSnapshot.AccountSnapshotBuilder snap() {
        return snap(Collections.emptyMap());
    }

    private static Choices only(Skill... skills) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        for (Skill s : skills) m.put(s, 20);
        return Choices.from(Preset.CUSTOM, m, true, true, true);
    }

    private static Choices everything() {
        return Choices.from(Preset.EVERYTHING, Collections.emptyMap(), true, true, true);
    }

    private static Choices skiller() {
        return Choices.from(Preset.SKILLER, Collections.emptyMap(), true, true, true);
    }

    private static Decision next(AccountSnapshot s, Choices c, PacingMode m, PlannerMemory mem, long now, long seed) {
        return Planner.next(s, c, m, mem, TUNING, now, new Random(seed), true, 0, id -> 100, t -> 0);
    }

    private static Decision next(AccountSnapshot s, Choices c, PacingMode m) {
        return next(s, c, m, new PlannerMemory(), 0, 1);
    }

    private static final Map<Integer, Integer> BOW = Map.of(841, 1, 882, 1000);

    private static Map<ApTask, TaskState> meleeDone() {
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        for (ApTask t : ApTask.values()) {
            if (t.skill() == Skill.ATTACK || t.skill() == Skill.STRENGTH || t.skill() == Skill.DEFENCE) tasks.put(t, TaskState.CLAIMED);
        }
        return tasks;
    }

    @Test
    void rangedAndMagicWaitForMelee() {
        Decision d = next(snap().levels(Map.of(Skill.RANGED, 1)).build(), only(Skill.RANGED, Skill.ATTACK), PacingMode.SMART);
        assertEquals(ApTask.ATTACK_5, d.getTask());
        assertEquals(Planner.MELEE_FIRST, d.getParked().get(ApTask.RANGED_5));
        assertEquals(ApTask.RANGED_5, next(snap().bank(BOW).build(), only(Skill.RANGED), PacingMode.SMART).getTask());
    }

    @Test
    void questAfterMeleeBeforeRangedAndMagic() {
        Map<Skill, Integer> lv = Map.of(Skill.ATTACK, 20, Skill.STRENGTH, 20, Skill.DEFENCE, 20);
        Choices c = only(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED);
        assertEquals(Kind.DO_QUEST, next(snap(meleeDone()).levels(lv).mudfootToDo(true).build(), c, PacingMode.SMART).getKind());
        assertEquals(ApTask.RANGED_5, next(snap(meleeDone()).levels(lv).bank(BOW).build(), c, PacingMode.SMART).getTask());
        Map<Skill, Integer> low = Map.of(Skill.ATTACK, 20, Skill.STRENGTH, 20, Skill.DEFENCE, 9);
        assertEquals(ApTask.RANGED_5, next(snap(meleeDone()).levels(low).bank(BOW).mudfootToDo(true).build(), c, PacingMode.SMART).getTask());
        Choices melee = only(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
        assertNotEquals(Kind.DO_QUEST, next(snap(meleeDone()).levels(lv).mudfootToDo(true).build(), melee, PacingMode.SMART).getKind());
    }

    @Test
    void noDecisionWhileNotLoggedIn() {
        AccountSnapshot s = AccountSnapshot.builder().loggedIn(false).progress(progress(Collections.emptyMap())).build();
        assertEquals(Kind.WAIT, next(s, everything(), PacingMode.SMART).getKind());
    }

    @Test
    void notParticipatingStops() {
        Progress p = new Progress(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), false, false, false);
        Decision d = next(snap().progress(p).build(), everything(), PacingMode.SMART);
        assertEquals(Kind.STOP, d.getKind());
        assertEquals("account is not taking part in Adventure Paths", d.getReason());
    }

    @Test
    void ironmanStops() {
        Progress p = new Progress(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), true, false, true);
        Decision d = next(snap().progress(p).build(), everything(), PacingMode.SMART);
        assertEquals("ironmen cannot do Adventure Paths", d.getReason());
    }

    @Test
    void starterKitFirstForCombat() {
        AccountSnapshot s = snap().starterKitClaimed(false).build();
        assertEquals(Kind.STARTER_KIT, next(s, everything(), PacingMode.SMART).getKind());
        assertEquals(Kind.STARTER_KIT, next(snap().inventory(Map.of(ApTask.STARTER_KIT_ID, 1)).build(), everything(), PacingMode.SMART).getKind());
    }

    @Test
    void skillerSkipsStarterKit() {
        AccountSnapshot s = snap().starterKitClaimed(false).build();
        assertEquals(Kind.DO_TASK, next(s, skiller(), PacingMode.SMART).getKind());
    }

    @Test
    void aTierWaitsForTheOneBelowEvenWhenThatOneIsParked() {
        Choices c = LoopRules.withoutParked(only(Skill.RANGED, Skill.STRENGTH), java.util.Set.of(ApTask.RANGED_5));
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        tasks.put(ApTask.STRENGTH_5, TaskState.CLAIMED);
        Decision d = next(snap(tasks).levels(Map.of(Skill.RANGED, 3, Skill.STRENGTH, 5)).build(), c, PacingMode.SMART);
        assertNotEquals(ApTask.RANGED_10, d.getTask());
        assertNotEquals(ApTask.RANGED_20, d.getTask());
    }

    @Test
    void claimWhenRewardUnparksAnother() {
        AccountSnapshot s = snap(Map.of(ApTask.RANGED_5, TaskState.DONE_UNCLAIMED)).levels(Map.of(Skill.RANGED, 5)).build();
        assertEquals(Kind.CLAIM_AT_JON, next(s, only(Skill.RANGED), PacingMode.SMART).getKind());
    }

    @Test
    void claimWhenAnUnchosenTasksRewardCoversANeed() {
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        tasks.put(ApTask.ATTACK_5, TaskState.CLAIMED);
        tasks.put(ApTask.ATTACK_10, TaskState.CLAIMED);
        tasks.put(ApTask.STRENGTH_5, TaskState.DONE_UNCLAIMED);
        AccountSnapshot s = snap(tasks).levels(Map.of(Skill.ATTACK, 10)).wantsFood(true).build();
        Choices c = only(Skill.ATTACK);
        assertFalse(c.getChosen().contains(ApTask.STRENGTH_5));
        assertEquals(Kind.CLAIM_AT_JON, next(s, c, PacingMode.SMART).getKind());
    }

    @Test
    void levelReachedButNotFlaggedIsCheapest() {
        AccountSnapshot s = snap().levels(Map.of(Skill.FISHING, 5)).build();
        // SMART is a weighted pick, so FISHING_5 wins only ~58% of the time: set seeds keep this deterministic
        int fishing = 0;
        for (long seed = 0; seed < 2000; seed++) {
            Decision d = next(s, only(Skill.FISHING, Skill.MINING), PacingMode.SMART, new PlannerMemory(), 0, seed);
            if (d.getTask() == ApTask.FISHING_5) fishing++;
        }
        assertTrue(fishing > 1080, "FISHING_5 picked " + fishing + "/2000");
    }

    @Test
    void mixItUpNeverRepeatsWhileTwoLeft() {
        Choices c = only(Skill.FISHING, Skill.MINING);
        for (long seed = 0; seed < 20; seed++) {
            PlannerMemory mem = new PlannerMemory();
            mem.setLastSkill(Skill.FISHING);
            mem.setBlockEndsAtMs(100);
            Decision d = next(snap().build(), c, PacingMode.MIX_IT_UP, mem, 500, seed);
            assertEquals(ApTask.MINING_5, d.getTask());
            assertTrue(mem.getBlockEndsAtMs() >= 1500 && mem.getBlockEndsAtMs() <= 2500);
        }
    }

    @Test
    void mixItUpKeepsTheCurrentTaskOnlyWhileItsBlockRuns() {
        Choices c = only(Skill.FISHING, Skill.MINING);
        PlannerMemory running = new PlannerMemory();
        running.setLastSkill(Skill.FISHING);
        running.setCurrentTask(ApTask.FISHING_5);
        running.setBlockEndsAtMs(1000);
        assertEquals(ApTask.FISHING_5, next(snap().build(), c, PacingMode.MIX_IT_UP, running, 500, 1).getTask());

        PlannerMemory expired = new PlannerMemory();
        expired.setLastSkill(Skill.FISHING);
        expired.setCurrentTask(ApTask.FISHING_5);
        expired.setBlockEndsAtMs(100);
        assertEquals(ApTask.MINING_5, next(snap().build(), c, PacingMode.MIX_IT_UP, expired, 500, 1).getTask());
    }

    @Test
    void mixItUpLastTaskStillRuns() {
        PlannerMemory mem = new PlannerMemory();
        mem.setLastSkill(Skill.FISHING);
        Decision d = next(snap().build(), only(Skill.FISHING), PacingMode.MIX_IT_UP, mem, 500, 1);
        assertEquals(ApTask.FISHING_5, d.getTask());
    }

    @Test
    void wholeSetStaysOnSkill() {
        Choices c = only(Skill.FISHING, Skill.MINING, Skill.WOODCUTTING);
        for (long seed = 0; seed < 20; seed++) {
            PlannerMemory mem = new PlannerMemory();
            mem.setLastSkill(Skill.MINING);
            assertEquals(ApTask.MINING_5, next(snap().build(), c, PacingMode.WHOLE_SET, mem, 0, seed).getTask());
        }
    }

    @Test
    void pathByPathGathererFirst() {
        for (long seed = 0; seed < 20; seed++) {
            Decision d = next(snap().build(), everything(), PacingMode.PATH_BY_PATH, new PlannerMemory(), 0, seed);
            assertEquals(ApPath.GATHERER, d.getTask().path());
        }
    }

    @Test
    void allDoneIsDone() {
        Map<ApTask, TaskState> done = new EnumMap<>(ApTask.class);
        for (ApTask t : ApTask.values()) done.put(t, TaskState.CLAIMED);
        Progress p = new Progress(done, Map.of(ApPath.COMBAT, TaskState.CLAIMED, ApPath.GATHERER, TaskState.CLAIMED),
                Set.of(VoucherReward.values()), true, false, false);
        assertEquals(Kind.DONE, next(snap().progress(p).build(), everything(), PacingMode.SMART).getKind());
    }

    @Test
    void everythingParkedStopsWithReasons() {
        Decision d = Planner.next(snap().bank(Map.of(841, 1)).build(), only(Skill.RANGED), PacingMode.SMART, new PlannerMemory(), TUNING, 0,
                new Random(1), false, 0, id -> 100, t -> 0);
        assertEquals(Kind.STOP, d.getKind());
        assertTrue(d.getParked().containsKey(ApTask.RANGED_5));
        assertTrue(d.getReason().contains("RANGED_5"));
    }

    @Test
    void altPathsRunGathererOnly() {
        Progress p = new Progress(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), true, true, false);
        for (long seed = 0; seed < 20; seed++) {
            Decision d = next(snap().progress(p).build(), everything(), PacingMode.SMART, new PlannerMemory(), 0, seed);
            assertEquals(ApPath.GATHERER, d.getTask().path());
            assertEquals("Warrior/Wizard/Ranger paths are not supported yet", d.getParked().get(ApTask.ATTACK_5));
        }
    }

    @Test
    void lampGoesToLowestChosenGatherSkill() {
        Map<Skill, Integer> lv = new HashMap<>(Map.of(Skill.FISHING, 5, Skill.WOODCUTTING, 3, Skill.MINING, 3));
        assertEquals(Optional.of(Skill.WOODCUTTING), Planner.lampSkill(skiller(), lv));
        AccountSnapshot s = snap().levels(lv).inventory(Map.of(ApTask.LAMP_ID, 1)).build();
        assertEquals(Kind.USE_LAMP, next(s, skiller(), PacingMode.SMART).getKind());
    }

    @Test
    void lampSkipsProtected() {
        Map<Skill, Integer> lv = Map.of(Skill.FISHING, 5, Skill.WOODCUTTING, 1, Skill.MINING, 3);
        assertEquals(Optional.of(Skill.MINING), Planner.lampSkill(only(Skill.FISHING, Skill.MINING), lv));
        assertFalse(Planner.lampSkill(only(Skill.ATTACK), lv).isPresent());
    }

    @Test
    void questLampsGoWhereTheUserSaysUnlessProtected() {
        Map<Skill, Integer> lv = Map.of(Skill.RANGED, 4, Skill.MAGIC, 2, Skill.ATTACK, 20);
        Choices c = only(Skill.ATTACK, Skill.RANGED, Skill.MAGIC);
        assertEquals(Optional.of(Skill.MAGIC), QuestLampChoice.AUTO.resolve(c, lv));
        assertEquals(Optional.of(Skill.ATTACK), Planner.questLampSkill(only(Skill.ATTACK), lv));
        assertFalse(Planner.questLampSkill(skiller(), lv).isPresent());
        assertEquals(Optional.of(Skill.RANGED), QuestLampChoice.RANGED.resolve(c, lv));
        assertEquals(Optional.of(Skill.MAGIC), QuestLampChoice.DEFENCE.resolve(c, lv));
        AccountSnapshot s = snap(meleeDone()).levels(lv).bank(BOW).inventory(Map.of(ApTask.QUEST_LAMP_ID, 4)).build();
        assertEquals(Kind.USE_LAMP, next(s, c, PacingMode.SMART).getKind());
    }
}
