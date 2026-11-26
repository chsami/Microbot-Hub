package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

public final class LoopRules {
    private LoopRules() {}

    public static Map<Skill, Integer> targets(Target attack, Target strength, Target defence, Target ranged, Target magic,
                                              GatherTarget fishing, GatherTarget woodcutting, GatherTarget mining) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        m.put(Skill.ATTACK, attack.level());
        m.put(Skill.STRENGTH, strength.level());
        m.put(Skill.DEFENCE, defence.level());
        m.put(Skill.RANGED, ranged.level());
        m.put(Skill.MAGIC, magic.level());
        m.put(Skill.FISHING, fishing.level());
        m.put(Skill.WOODCUTTING, woodcutting.level());
        m.put(Skill.MINING, mining.level());
        return m;
    }

    public static Choices withoutParked(Choices c, Set<ApTask> parked) {
        if (parked.isEmpty()) return c;
        Set<ApTask> chosen = EnumSet.noneOf(ApTask.class);
        chosen.addAll(c.getChosen());
        chosen.removeAll(parked);
        return new Choices(Collections.unmodifiableSet(chosen), c.getProtectedSkills(), c.isBury(), c.isFires(), c.isCook());
    }

    public static int tilesTo(WorldPoint here, List<Spot> spots) {
        if (here == null || spots.isEmpty()) return 0;
        int best = Integer.MAX_VALUE;
        for (Spot s : spots) best = Math.min(best, here.distanceTo2D(s.getCenter()));
        return best;
    }

    /** Varbits and stats read 0 for a moment after every login. */
    public static final int SETTLE_TICKS = 3;

    public static boolean settled(int nowTick, int firstTick, AccountSnapshot s) {
        return firstTick >= 0 && nowTick - firstTick >= SETTLE_TICKS && !s.getLevels().isEmpty() && s.totalLevel() > 0;
    }

    public static String fingerprint(AccountSnapshot s) {
        return s.getProgress() + "|kit=" + s.isStarterKitClaimed() + "|lamp=" + s.owned(ApTask.LAMP_ID)
                + "|voucher=" + s.owned(ApTask.VOUCHER_ID) + "|quest=" + s.isMudfootToDo() + "|questLamps=" + s.carried(ApTask.QUEST_LAMP_ID);
    }

    public static boolean repeatsWithoutProgress(Decision.Kind prevKind, String prevFingerprint, Decision.Kind kind,
                                                 String fingerprint) {
        return prevKind != null && prevKind == kind && fingerprint.equals(prevFingerprint);
    }

    public static List<Integer> gearPicks(ApTask task, AccountSnapshot s, Choices c) {
        List<Integer> out = new ArrayList<>();
        if (task.path() != ApPath.COMBAT) return out;
        Set<Skill> prot = c.getProtectedSkills();
        if (!prot.contains(task.skill()) && XpGuard.styleFor(task.skill(), prot).isPresent()) {
            GearPlan.bestWeapon(task.skill(), s).ifPresent(out::add);
        }
        out.addAll(GearPlan.bestArmour(s, task.skill()));
        return out;
    }
}
