package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision.Kind;
import net.runelite.client.plugins.microbot.accountstarter.logic.FreeSource;
import net.runelite.client.plugins.microbot.accountstarter.logic.GearPlan;
import net.runelite.client.plugins.microbot.accountstarter.logic.Need;
import net.runelite.client.plugins.microbot.accountstarter.logic.NeedResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.Needs;
import net.runelite.client.plugins.microbot.accountstarter.logic.PacingMode;
import net.runelite.client.plugins.microbot.accountstarter.logic.Planner;
import net.runelite.client.plugins.microbot.accountstarter.logic.PlannerMemory;
import net.runelite.client.plugins.microbot.accountstarter.logic.PlannerTuning;
import net.runelite.client.plugins.microbot.accountstarter.logic.Preset;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.logic.Resolution;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PlannerScenarioTest {
    private static final PlannerTuning TUNING = new PlannerTuning(0, 3, 90_000, 180_000, 1.25, 0);
    private static final long STEP_MS = 60_000;
    private static final int MAX_STEPS = 80;
    private static final int MAX_KIT_STEPS = 400;
    private static final int DROP_GP = 60;
    private static final LongUnaryOperator DROP_PRICE = id -> Set.of(GearPlan.AIR, GearPlan.WATER, GearPlan.EARTH, GearPlan.FIRE,
            GearPlan.MIND).contains((int) id) ? 5 : 100;

    private static final Map<String, String> KNOWN_DEFECTS = new HashMap<>();

    static {
        String noRunes = "Magic 5 stops on 'need 125 coins for runes' until the sim does Mudfoot's Big Adventure";
        KNOWN_DEFECTS.put("drop-coins Everything/Smart order", noRunes);
        KNOWN_DEFECTS.put("drop-coins Combat only/Mix it up", noRunes);
    }

    private static class Sim {
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        Map<ApPath, TaskState> paths = new EnumMap<>(ApPath.class);
        Set<VoucherReward> vouchers = EnumSet.noneOf(VoucherReward.class);
        Map<Integer, Integer> bank = new HashMap<>();
        int coins = 100_000;
        long now = 10 * 3_600_000L;
        Set<Skill> prot = EnumSet.noneOf(Skill.class);
        int foodRuns;
        boolean hungry;

        Sim() {
            for (Skill s : Skill.values()) {
                levels.put(s, 1);
                xp.put(s, 0);
            }
            levels.put(Skill.HITPOINTS, 10);
            xp.put(Skill.HITPOINTS, Experience.getXpForLevel(10));
            for (Skill s : new Skill[]{Skill.FISHING, Skill.WOODCUTTING}) {
                levels.put(s, 5);
                xp.put(s, Experience.getXpForLevel(5));
            }
            tasks.put(ApTask.FISHING_5, TaskState.CLAIMED);
            tasks.put(ApTask.WOODCUTTING_5, TaskState.CLAIMED);
            int[][] items = {{303, 1}, {1351, 1}, {1349, 1}, {590, 1}, {307, 1}, {313, 100}, {1137, 1}, {1103, 1}, {1067, 1},
                    {1293, 1}, {579, 1}, {581, 1}, {1015, 1}, {1379, 1}, {556, 40}, {558, 40}, {882, 75}, {1063, 1}, {1265, 1}, {995, 25}};
            for (int[] i : items) bank.put(i[0], i[1]);
        }

        TaskState state(ApTask t) { return tasks.getOrDefault(t, TaskState.NOT_DONE); }

        AccountSnapshot snapshot() {
            Progress p = new Progress(new EnumMap<>(tasks), new EnumMap<>(paths), EnumSet.copyOf(vouchers.isEmpty()
                    ? EnumSet.noneOf(VoucherReward.class) : vouchers), true, false, false);
            return AccountSnapshot.builder().loggedIn(true).bankKnown(true).starterKitClaimed(true).coins(coins)
                    .levels(new EnumMap<>(levels)).xp(new EnumMap<>(xp)).bank(new HashMap<>(bank))
                    .inventory(owned(ApTask.LAMP_ID) > 0 ? Map.of(ApTask.LAMP_ID, 1) : Map.of()).progress(p)
                    .protectedSkills(prot).wantsFood(hungry).build();
        }

        void add(int id, int n) { bank.merge(id, n, Integer::sum); }

        int owned(int id) { return bank.getOrDefault(id, 0); }
    }

    private static String label(Decision d) {
        switch (d.getKind()) {
            case DO_TASK: return d.getTask().name();
            case CLAIM_AT_JON: return "Jon";
            case USE_LAMP: return "lamp";
            case REDEEM_VOUCHER: return "voucher:" + d.getVoucher();
            default: return d.getKind().name();
        }
    }

    @TestFactory
    List<DynamicTest> scenarios() {
        List<DynamicTest> out = new ArrayList<>();
        for (Preset preset : Preset.values()) {
            for (PacingMode mode : PacingMode.values()) {
                String name = preset.name() + "/" + mode.name();
                out.add(DynamicTest.dynamicTest(name, () -> {
                    assumeFalse(KNOWN_DEFECTS.containsKey(name), String.valueOf(KNOWN_DEFECTS.get(name)));
                    run(preset, mode, name, false, 0);
                }));
            }
        }
        return out;
    }

    @TestFactory
    List<DynamicTest> dropCoins() {
        List<DynamicTest> out = new ArrayList<>();
        Object[][] cases = {{Preset.EVERYTHING, PacingMode.SMART}, {Preset.COMBAT_ONLY, PacingMode.MIX_IT_UP}};
        for (Object[] k : cases) {
            String name = "drop-coins " + k[0] + "/" + k[1];
            out.add(DynamicTest.dynamicTest(name, () -> {
                assumeFalse(KNOWN_DEFECTS.containsKey(name), String.valueOf(KNOWN_DEFECTS.get(name)));
                run((Preset) k[0], (PacingMode) k[1], name, false, DROP_GP);
            }));
        }
        return out;
    }

    @TestFactory
    List<DynamicTest> catchAndCook() {
        List<DynamicTest> out = new ArrayList<>();
        for (PacingMode mode : PacingMode.values()) {
            String name = "catch-and-cook COMBAT_ONLY/" + mode.name();
            out.add(DynamicTest.dynamicTest(name, () -> {
                Choices base = Choices.from(Preset.COMBAT_ONLY, new EnumMap<>(Skill.class), true, true, true);
                Set<Skill> prot = EnumSet.copyOf(base.getProtectedSkills());
                prot.remove(Skill.FISHING);
                prot.remove(Skill.COOKING);
                prot.add(Skill.STRENGTH);
                Set<ApTask> chosen = EnumSet.copyOf(base.getChosen());
                chosen.removeIf(t -> t.skill() == Skill.STRENGTH || t.skill() == Skill.RANGED || t.skill() == Skill.MAGIC);
                Choices c = new Choices(chosen, prot, true, true, true);
                hungryRun = true;
                Sim sim;
                try {
                    sim = run(c, null, mode, name, true, 0);
                } finally {
                    hungryRun = false;
                }
                assertTrue(sim.foodRuns > 0, name + " never caught food");
            }));
        }
        return out;
    }

    private static void run(Preset preset, PacingMode mode, String name, boolean buyOff, int dropGp) {
        Map<Skill, Integer> custom = new EnumMap<>(Skill.class);
        if (preset == Preset.CUSTOM) {
            custom.put(Skill.ATTACK, 20);
            custom.put(Skill.STRENGTH, 10);
        }
        run(Choices.from(preset, custom, true, true, true), preset, mode, name, buyOff, dropGp);
    }

    private static boolean hungryRun;

    private static Sim run(Choices c, Preset preset, PacingMode mode, String name, boolean buyOff, int dropGp) {
        Sim sim = new Sim();
        sim.hungry = hungryRun;
        sim.prot = c.getProtectedSkills();
        if (buyOff || dropGp > 0) sim.coins = sim.owned(995);
        boolean kit = buyOff || dropGp > 0;
        LongUnaryOperator price = dropGp > 0 ? DROP_PRICE : id -> 100;
        List<Integer> buyBalances = new ArrayList<>();
        Map<Skill, Integer> startLevels = new EnumMap<>(sim.levels);
        PlannerMemory mem = new PlannerMemory();
        Random r = new Random(42 + name.hashCode());
        List<String> seq = new ArrayList<>();
        List<Decision> done = new ArrayList<>();
        Decision last = null;
        boolean finished = false;
        int maxSteps = kit ? MAX_KIT_STEPS : MAX_STEPS;
        try {
            for (int step = 0; step < maxSteps && !finished; step++, sim.now += STEP_MS) {
                AccountSnapshot snap = sim.snapshot();
                int budget = buyOff ? 0 : dropGp > 0 ? sim.coins : 100_000;
                Decision d = Planner.next(snap, c, mode, mem, TUNING, sim.now, r, !buyOff, budget, price, t -> 0);
                seq.add(label(d));
                assertFalse(d.getKind() == Kind.STOP, name + " STOPPED: " + d.getReason());
                assertFalse(d.getKind() == Kind.STARTER_KIT, name + " re-requested the starter kit");
                assertFalse(d.getKind() == Kind.WAIT, name + " WAIT");
                boolean partial = kit && d.getTask() != null && d.getTask().skill() == Skill.MAGIC;
                assertFalse(!partial && last != null && d.getKind() != Kind.CLAIM_AT_JON && last.getKind() == d.getKind() && last.getTask() == d.getTask()
                        && last.getVoucher() == d.getVoucher(), name + " repeated " + label(d));
                last = d;
                done.add(d);
                if (kit && d.getKind() == Kind.DO_TASK) {
                    kitUp(d.getTask(), snap, sim, name, !buyOff, budget, price, buyBalances);
                    if (d.getTask().skill() == Skill.MAGIC) {
                        castRunes(d.getTask(), sim);
                        continue;
                    }
                }
                apply(d, sim, c);
                if (d.getKind() == Kind.DO_TASK && d.getTask().path() == ApPath.COMBAT && d.getTask().skill() != Skill.RANGED) {
                    sim.coins += dropGp;
                }
                finished = d.getKind() == Kind.DONE;
            }
        } finally {
            System.out.println(name + ": " + String.join(", ", seq));
            if (dropGp > 0) System.out.println(name + ": coins before each BUY " + buyBalances + ", left " + sim.coins);
        }
        assertTrue(finished, name + " did not reach DONE in " + maxSteps + " steps");
        if (buyOff) assertEquals(25, sim.coins, name + " spent coins");
        for (ApTask t : c.getChosen()) assertEquals(TaskState.CLAIMED, sim.state(t), name + " " + t + " not claimed");
        for (Skill s : c.getProtectedSkills()) assertEquals(startLevels.get(s), sim.levels.get(s), name + " protected " + s + " changed");
        long lamps = done.stream().filter(d -> d.getKind() == Kind.USE_LAMP).count();
        long redeems = done.stream().filter(d -> d.getKind() == Kind.REDEEM_VOUCHER).count();
        assertEquals(preset == Preset.EVERYTHING || preset == Preset.SKILLER ? 1 : 0, lamps, name + " lamp uses");
        assertEquals(preset == Preset.EVERYTHING || preset == Preset.COMBAT_ONLY ? 4 : 0, redeems, name + " voucher redemptions");
        if (preset == Preset.SKILLER) {
            assertTrue(done.stream().noneMatch(d -> d.getTask() != null && d.getTask().path() == ApPath.COMBAT), name + " combat task");
        }
        if (preset == Preset.CUSTOM) {
            Set<ApTask> ran = done.stream().filter(d -> d.getKind() == Kind.DO_TASK).map(Decision::getTask).collect(Collectors.toSet());
            assertEquals(EnumSet.of(ApTask.ATTACK_5, ApTask.ATTACK_10, ApTask.ATTACK_20, ApTask.STRENGTH_5, ApTask.STRENGTH_10), ran, name);
        }
        return sim;
    }

    private static void kitUp(ApTask t, AccountSnapshot snap, Sim sim, String name, boolean buy, int budget,
                              LongUnaryOperator price, List<Integer> buyBalances) {
        List<Need> needs = Needs.forTask(t, snap);
        List<Resolution> all = NeedResolver.resolveAll(needs, snap, buy, budget, price, 1.25);
        for (int i = 0; i < needs.size(); i++) {
            Need n = needs.get(i);
            Resolution res = all.get(i);
            assertFalse(res.getKind() == Resolution.Kind.PARK, name + " " + t + ": " + res.getReason());
            if (res.getKind() == Resolution.Kind.BUY) {
                assertTrue(buy, name + " " + t + " bought with buying off: " + res.getReason());
                int qty = res.getBuyQuantity();
                long cost = price.applyAsLong(n.getBuyItemId()) * qty;
                assertTrue(cost <= sim.coins, name + " " + t + " bought " + qty + " " + n.getBuyName() + " for " + cost + " with " + sim.coins);
                System.out.println(name + " " + t + ": " + sim.coins + " coins before BUY " + qty + " " + n.getBuyName() + " (" + cost + " gp)");
                buyBalances.add(sim.coins);
                sim.coins -= cost;
                sim.add(n.getBuyItemId(), qty);
                continue;
            }
            if (res.getKind() == Resolution.Kind.FREE && n.getFree() == FreeSource.CATCH_AND_COOK) {
                sim.add(315, n.getQuantity());
                sim.foodRuns++;
                continue;
            }
        }
    }

    private static void castRunes(ApTask t, Sim sim) {
        int level = sim.levels.get(Skill.MAGIC);
        int xpNeeded = Experience.getXpForLevel(t.target()) - sim.xp.get(Skill.MAGIC);
        GearPlan.Magic m = GearPlan.magic(level, new HashMap<>(sim.bank), xpNeeded);
        double perCast = m.getSpell() == Rs2CombatSpells.FIRE_STRIKE ? 11.5 : m.getSpell() == Rs2CombatSpells.EARTH_STRIKE ? 9.5
                : m.getSpell() == Rs2CombatSpells.WATER_STRIKE ? 7.5 : 5.5;
        int casts = (int) Math.ceil(xpNeeded / perCast);
        Map<Integer, Integer> cost = GearPlan.cost(m.getSpell());
        for (Map.Entry<Integer, Integer> e : cost.entrySet()) {
            if (GearPlan.staffFor(e.getKey()) == m.getStaff() && m.getStaff() != 0) continue;
            casts = Math.min(casts, sim.owned(e.getKey()) / e.getValue());
        }
        assertTrue(casts > 0, t + " started with no casts");
        for (Map.Entry<Integer, Integer> e : cost.entrySet()) {
            if (GearPlan.staffFor(e.getKey()) == m.getStaff() && m.getStaff() != 0) continue;
            sim.add(e.getKey(), -casts * e.getValue());
        }
        int xp = sim.xp.get(Skill.MAGIC) + (int) Math.ceil(casts * perCast);
        sim.xp.put(Skill.MAGIC, xp);
        sim.levels.put(Skill.MAGIC, Experience.getLevelForXp(xp));
        if (Experience.getLevelForXp(xp) >= t.target()) sim.tasks.put(t, TaskState.DONE_UNCLAIMED);
    }

    private static void apply(Decision d, Sim sim, Choices c) {
        switch (d.getKind()) {
            case DO_TASK: {
                ApTask t = d.getTask();
                assertTrue(c.getChosen().contains(t), "did unchosen " + t);
                assertEquals(TaskState.NOT_DONE, sim.state(t), "redid " + t);
                sim.levels.put(t.skill(), Math.max(sim.levels.get(t.skill()), t.target()));
                sim.xp.put(t.skill(), Math.max(sim.xp.get(t.skill()), Experience.getXpForLevel(t.target())));
                sim.tasks.put(t, TaskState.DONE_UNCLAIMED);
                break;
            }
            case CLAIM_AT_JON: {
                for (ApTask t : ApTask.values()) {
                    if (sim.state(t) == TaskState.DONE_UNCLAIMED) {
                        sim.tasks.put(t, TaskState.CLAIMED);
                        for (RewardItem ri : t.f2pRewards()) sim.add(ri.getItemId(), ri.getQuantity());
                    }
                }
                for (ApPath p : ApPath.values()) {
                    if (sim.paths.get(p) == TaskState.DONE_UNCLAIMED) {
                        sim.paths.put(p, TaskState.CLAIMED);
                        sim.add(p == ApPath.GATHERER ? ApTask.LAMP_ID : ApTask.VOUCHER_ID, 1);
                    } else if (!sim.paths.containsKey(p) && ApTask.of(p).stream().allMatch(t -> sim.state(t) == TaskState.CLAIMED)) {
                        sim.paths.put(p, TaskState.DONE_UNCLAIMED);
                    }
                }
                break;
            }
            case USE_LAMP: {
                Skill s = Planner.lampSkill(c, sim.levels).get();
                sim.bank.merge(ApTask.LAMP_ID, -1, Integer::sum);
                sim.xp.merge(s, 150, Integer::sum);
                break;
            }
            case REDEEM_VOUCHER: {
                sim.vouchers.add(d.getVoucher());
                for (RewardItem ri : d.getVoucher().rewards()) sim.add(ri.getItemId(), ri.getQuantity());
                if (sim.vouchers.size() == VoucherReward.values().length) sim.bank.remove(ApTask.VOUCHER_ID);
                break;
            }
            default: break;
        }
    }
}
