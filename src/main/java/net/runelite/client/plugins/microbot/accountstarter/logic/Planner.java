package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.Set;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.LongUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.logic.Decision.Kind;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;

public final class Planner {
    private static final Skill[] LAMP_ORDER = {Skill.FISHING, Skill.WOODCUTTING, Skill.MINING};
    private static final String ALT_REASON = "Warrior/Wizard/Ranger paths are not supported yet";

    private Planner() {}

    public static Optional<Skill> lampSkill(Choices c, Map<Skill, Integer> levels) {
        Skill best = null;
        for (Skill s : LAMP_ORDER) {
            boolean chosen = c.getChosen().stream().anyMatch(t -> t.skill() == s);
            if (!chosen || c.getProtectedSkills().contains(s)) continue;
            if (best == null || levels.getOrDefault(s, 1) < levels.getOrDefault(best, 1)) best = s;
        }
        return Optional.ofNullable(best);
    }

    public static Optional<Skill> questLampSkill(Choices c, Map<Skill, Integer> levels) {
        for (Set<Skill> group : List.of(RANGED_MAGIC, MELEE)) {
            Optional<Skill> best = group.stream()
                    .filter(s -> c.getChosen().stream().anyMatch(t -> t.skill() == s) && !c.getProtectedSkills().contains(s))
                    .min(Comparator.comparingInt(s -> levels.getOrDefault(s, 1)));
            if (best.isPresent()) return best;
        }
        return Optional.empty();
    }

    static final Set<Skill> MELEE = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
    static final Set<Skill> RANGED_MAGIC = EnumSet.of(Skill.RANGED, Skill.MAGIC);
    public static final String MELEE_FIRST = "waits for the chosen melee tasks";
    private static final double SUPPLY_WEIGHT = 3.0;
    /** The quest's last outlaw is level 8. */
    public static final int QUEST_MELEE = 10;

    static boolean questDue(AccountSnapshot s, Choices c, boolean meleeLeft) {
        if (!s.isMudfootToDo() || meleeLeft) return false;
        boolean rangedOrMagic = c.getChosen().stream()
                .anyMatch(x -> RANGED_MAGIC.contains(x.skill()) && s.getProgress().of(x) == TaskState.NOT_DONE);
        return rangedOrMagic && MELEE.stream().allMatch(sk -> s.getLevels().getOrDefault(sk, 1) >= QUEST_MELEE);
    }

    public static Decision questOnly(AccountSnapshot s, boolean lampPicked) {
        Map<ApTask, String> none = Collections.emptyMap();
        if (!s.isLoggedIn()) return d(Kind.WAIT, null, null, "not logged in", none);
        if (lampPicked && s.carried(ApTask.QUEST_LAMP_ID) > 0) return d(Kind.USE_LAMP, null, null, "spend the quest lamps", none);
        if (s.isMudfootToDo()) return d(Kind.DO_QUEST, null, null, "Mudfoot's Big Adventure", none);
        return d(Kind.DONE, null, null, "Mudfoot's Big Adventure is complete", none);
    }

    public static Decision next(AccountSnapshot s, Choices c, PacingMode m, PlannerMemory mem, PlannerTuning t, long nowMs,
                                Random r, boolean buyAllowed, int budgetLeft, LongUnaryOperator guidePrice,
                                ToIntFunction<ApTask> tilesFromHere) {
        if (!s.isLoggedIn()) return d(Kind.WAIT, null, null, "not logged in", Collections.emptyMap());
        Progress p = s.getProgress();
        if (!p.isParticipating()) return d(Kind.STOP, null, null, "account is not taking part in Adventure Paths", Collections.emptyMap());
        if (p.isIronman()) return d(Kind.STOP, null, null, "ironmen cannot do Adventure Paths", Collections.emptyMap());

        boolean kitToOpen = s.carried(ApTask.STARTER_KIT_ID) > 0;
        if ((kitToOpen || !s.isStarterKitClaimed() && s.isBankKnown()) && c.getChosen().stream().anyMatch(x -> x.path() == ApPath.COMBAT)) {
            return d(Kind.STARTER_KIT, null, null, "claim the combat starter kit", Collections.emptyMap());
        }

        boolean meleeLeft = c.getChosen().stream().anyMatch(x -> MELEE.contains(x.skill()) && p.of(x) == TaskState.NOT_DONE);
        // declaration order is ascending target
        Map<ApTask, String> parked = new EnumMap<>(ApTask.class);
        Map<ApTask, List<Need>> needs = new LinkedHashMap<>();
        Map<ApTask, List<Resolution>> res = new LinkedHashMap<>();
        List<Skill> seen = new ArrayList<>();
        for (ApTask task : ApTask.values()) {
            // the game opens one tier per skill at a time: a tier waits until the one below it is done, chosen or not
            if (p.of(task) != TaskState.NOT_DONE || seen.contains(task.skill())) continue;
            seen.add(task.skill());
            if (!c.getChosen().contains(task)) continue;
            if (meleeLeft && RANGED_MAGIC.contains(task.skill())) {
                parked.put(task, MELEE_FIRST);
                continue;
            }
            if (p.isAltPaths() && task.path() == ApPath.COMBAT) {
                parked.put(task, ALT_REASON);
                continue;
            }
            List<Need> ns = Needs.forTask(task, s);
            needs.put(task, ns);
            res.put(task, NeedResolver.resolveAll(ns, s, buyAllowed, budgetLeft, guidePrice, t.getBuyBuffer()));
        }
        List<ApTask> candidates = new ArrayList<>();
        for (ApTask task : res.keySet()) {
            Optional<Resolution> park = res.get(task).stream().filter(x -> x.getKind() == Resolution.Kind.PARK).findFirst();
            if (park.isPresent()) parked.put(task, park.get().getReason());
            else candidates.add(task);
        }

        long unclaimed = c.getChosen().stream().filter(x -> p.of(x) == TaskState.DONE_UNCLAIMED).count();
        boolean pathUnclaimed = Arrays.stream(ApPath.values()).anyMatch(path ->
                p.getPaths().getOrDefault(path, TaskState.NOT_DONE) == TaskState.DONE_UNCLAIMED
                        && c.getChosen().stream().anyMatch(x -> x.path() == path));
        // parked and unchosen tasks count: Jon hands over every earned reward
        boolean unparks = res.values().stream().flatMap(List::stream).anyMatch(x -> x.getKind() == Resolution.Kind.CLAIM_REWARD);
        if (pathUnclaimed || unparks || unclaimed > 0) {
            return d(Kind.CLAIM_AT_JON, null, null, "claim finished rewards from Jon", parked);
        }

        Optional<Skill> lamp = lampSkill(c, s.getLevels());
        if (lamp.isPresent() && s.carried(ApTask.LAMP_ID) > 0) {
            return d(Kind.USE_LAMP, null, null, "spend the lamp on " + lamp.get(), parked);
        }
        // antique lamps cannot be banked, so only carried ones count
        if (s.carried(ApTask.QUEST_LAMP_ID) > 0 && questLampSkill(c, s.getLevels()).isPresent()) {
            return d(Kind.USE_LAMP, null, null, "spend the quest lamps", parked);
        }
        if (s.owned(ApTask.VOUCHER_ID) > 0) {
            VoucherReward[] all = VoucherReward.values();
            for (int i = 0; i < all.length; i++) {
                VoucherReward v = all[(i + t.getVoucherStart()) % all.length];
                if (!p.getVoucherClaimed().contains(v)) {
                    return d(Kind.REDEEM_VOUCHER, null, v, "claim the voucher reward from " + v.npcName(), parked);
                }
            }
        }

        if (questDue(s, c, meleeLeft)) {
            return d(Kind.DO_QUEST, null, null, "Mudfoot's Big Adventure: a bow, a staff, runes and coins", parked);
        }

        if (candidates.isEmpty()) {
            if (finished(s, c)) return d(Kind.DONE, null, null, "everything chosen is done and claimed", parked);
            String why = parked.isEmpty() ? "waiting for rewards" : parked.entrySet().stream()
                    .map(e -> e.getKey().name() + ": " + e.getValue()).collect(Collectors.joining("; "));
            return d(Kind.STOP, null, null, why, parked);
        }

        boolean voucherEarned = p.getPaths().getOrDefault(ApPath.COMBAT, TaskState.NOT_DONE) != TaskState.NOT_DONE;
        Map<ApTask, Double> score = new LinkedHashMap<>();
        for (ApTask task : candidates) {
            double sc = 0;
            if (unlocksAnother(task, res, needs)) sc += 3.0;
            if (task.path() == ApPath.COMBAT && !voucherEarned && lastCombat(task, c, p)) sc += 2.0;
            sc -= remainingXp(task, s) / 2000.0;
            sc -= tilesFromHere.applyAsInt(task) / 200.0;
            if (task.skill() == mem.getLastSkill()) sc -= 1.5;
            if (RANGED_MAGIC.contains(task.skill())) sc += SUPPLY_WEIGHT * supplyShare(needs.get(task), s);
            sc += t.getNoise() * r.nextGaussian();
            score.put(task, sc);
        }

        ApTask current = mem.getCurrentTask();
        boolean keep = current != null && candidates.contains(current) && (m != PacingMode.MIX_IT_UP || nowMs < mem.getBlockEndsAtMs());
        ApTask pick = keep ? current : pick(m, candidates, score, mem, t, nowMs, r);
        mem.setLastSkill(pick.skill());
        mem.setCurrentTask(pick);
        return d(Kind.DO_TASK, pick, null, "next: " + pick.name(), parked);
    }

    private static ApTask pick(PacingMode m, List<ApTask> cands, Map<ApTask, Double> score, PlannerMemory mem, PlannerTuning t,
                               long now, Random r) {
        switch (m) {
            case WHOLE_SET:
                for (ApTask x : cands) if (x.skill() == mem.getLastSkill()) return x;
                return smart(cands, score, r);
            case PATH_BY_PATH: {
                List<ApTask> g = cands.stream().filter(x -> x.path() == ApPath.GATHERER).collect(Collectors.toList());
                return smart(g.isEmpty() ? cands : g, score, r);
            }
            case MIX_IT_UP: {
                if (mem.getLastSkill() != null && now < mem.getBlockEndsAtMs()) {
                    for (ApTask x : cands) if (x.skill() == mem.getLastSkill()) return x;
                }
                List<ApTask> other = cands.stream().filter(x -> x.skill() != mem.getLastSkill()).collect(Collectors.toList());
                List<ApTask> from = other.isEmpty() ? cands : other;
                long span = Math.max(0, t.getBlockMaxMs() - t.getBlockMinMs());
                mem.setBlockEndsAtMs(now + t.getBlockMinMs() + (long) (r.nextDouble() * span));
                return from.get(r.nextInt(from.size()));
            }
            default:
                return smart(cands, score, r);
        }
    }

    private static ApTask smart(List<ApTask> cands, Map<ApTask, Double> score, Random r) {
        if (cands.size() == 1) return cands.get(0);
        List<ApTask> top = cands.stream().sorted(Comparator.comparingDouble((ApTask x) -> score.get(x)).reversed())
                .limit(3).collect(Collectors.toList());
        double min = score.get(top.get(top.size() - 1));
        double roll = r.nextDouble() * top.stream().mapToDouble(x -> score.get(x) - min + 0.5).sum();
        for (ApTask x : top) {
            roll -= score.get(x) - min + 0.5;
            if (roll < 0) return x;
        }
        return top.get(0);
    }

    static double supplyShare(List<Need> needs, AccountSnapshot s) {
        double share = 1;
        for (Need n : needs == null ? List.<Need>of() : needs) {
            if (n.getQuantity() <= 0 || n.getFree() == FreeSource.CATCH_AND_COOK) continue;
            long have = n.getAnyOf().stream().mapToLong(s::owned).sum();
            share = Math.min(share, Math.min(1.0, (double) have / n.getQuantity()));
        }
        return share;
    }

    private static double remainingXp(ApTask task, AccountSnapshot s) {
        int level = s.getLevels().getOrDefault(task.skill(), 1);
        if (level >= task.target()) return 0;
        int have = s.getXp().getOrDefault(task.skill(), Experience.getXpForLevel(level));
        return Math.max(0, Experience.getXpForLevel(task.target()) - have);
    }

    private static boolean lastCombat(ApTask task, Choices c, Progress p) {
        return c.getChosen().stream().noneMatch(o -> o != task && o.path() == ApPath.COMBAT && p.of(o) == TaskState.NOT_DONE);
    }

    private static boolean unlocksAnother(ApTask task, Map<ApTask, List<Resolution>> res, Map<ApTask, List<Need>> needs) {
        for (Map.Entry<ApTask, List<Need>> e : needs.entrySet()) {
            if (e.getKey() == task) continue;
            List<Need> ns = e.getValue();
            for (int i = 0; i < ns.size(); i++) {
                Resolution.Kind k = res.get(e.getKey()).get(i).getKind();
                if (k != Resolution.Kind.PARK && k != Resolution.Kind.BUY) continue;
                for (RewardItem ri : task.f2pRewards()) {
                    if (ns.get(i).getAnyOf().contains(ri.getItemId())) return true;
                }
            }
        }
        return false;
    }

    private static boolean finished(AccountSnapshot s, Choices c) {
        Progress p = s.getProgress();
        if (c.getChosen().stream().anyMatch(x -> p.of(x) != TaskState.CLAIMED)) return false;
        for (ApPath path : ApPath.values()) {
            // a path only completes when all its tasks are chosen
            if (!c.getChosen().containsAll(ApTask.of(path))) continue;
            if (p.getPaths().getOrDefault(path, TaskState.NOT_DONE) != TaskState.CLAIMED) return false;
            if (path == ApPath.COMBAT && !p.getVoucherClaimed().containsAll(Arrays.asList(VoucherReward.values()))) return false;
        }
        return true;
    }

    private static Decision d(Kind k, ApTask t, VoucherReward v, String reason, Map<ApTask, String> parked) {
        return new Decision(k, t, v, reason, parked);
    }
}
