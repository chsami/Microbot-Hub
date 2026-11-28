package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

public final class Needs {
    // anchovies (319) left out: they heal 1
    public static final Set<Integer> FOOD = Set.of(333, 329, 315, 379, 2309, 325, 347);

    static final int MIN_ARROWS = 25;

    private Needs() {}

    public static int arrows(AccountSnapshot snap, int target) {
        return GearPlan.arrowsNeeded(GearPlan.xpHave(snap, Skill.RANGED), GearPlan.xpNeeded(snap, Skill.RANGED, target),
                GearPlan.ARROW_MISS_PICKUP);
    }

    public static List<Need> forTask(ApTask task, AccountSnapshot snap) {
        List<Need> out = new ArrayList<>();
        Map<Skill, Integer> lv = snap.getLevels();
        switch (task.skill()) {
            case FISHING:
                out.add(new Need("a small fishing net", Set.of(303), 1, FreeSource.FISHING_TUTOR, 303, "small fishing net"));
                break;
            case WOODCUTTING:
                out.add(new Need("an axe", set(GearPlan.ids(GearPlan.AXES, lv.getOrDefault(Skill.WOODCUTTING, 1))), 1,
                        FreeSource.WOODCUTTING_TUTOR, 1351, "bronze axe"));
                break;
            case MINING:
                out.add(new Need("a pickaxe", set(GearPlan.ids(GearPlan.PICKAXES, lv.getOrDefault(Skill.MINING, 1))), 1,
                        FreeSource.MINING_TUTOR, 1265, "bronze pickaxe"));
                break;
            case RANGED: {
                Set<Integer> bows = new HashSet<>(set(GearPlan.ids(GearPlan.SHORTBOWS, lv.getOrDefault(Skill.RANGED, 1))));
                bows.add(GearPlan.TRAINING_BOW);
                out.add(new Need("a shortbow", bows, 1, harlan(snap), 841, "shortbow"));
                if (GearPlan.usesTrainingBow(snap)) {
                    // untradeable and not handed out: a few left still fight
                    out.add(new Need("training arrows", Set.of(GearPlan.TRAINING_ARROWS), 10, null, 0,
                            "training arrows", 10, 1));
                } else {
                    int need = arrows(snap, task.target());
                    int start = Math.min(need, Math.max(MIN_ARROWS, snap.owned(882) + snap.owned(884)));
                    out.add(new Need("arrows", Set.of(882, 884), need, arrowSource(snap), 882, "bronze arrows", start, 1));
                }
                break;
            }
            case MAGIC:
                runes(snap, lv.getOrDefault(Skill.MAGIC, 1), task.target(), out);
                break;
            default:
                break;
        }
        if (task.path() == ApPath.COMBAT) {
            int small = smallStock(snap.getFoodStock());
            // every combat spot hits 1 at most, so no target starts with food
            int min = snap.isWantsFood() ? small : 0;
            out.add(new Need("food", FOOD, small, FreeSource.CATCH_AND_COOK, 315, "shrimps", min, 0));
        }
        return Collections.unmodifiableList(out);
    }

    public static int smallStock(int foodStock) {
        return (foodStock + 1) / 2;
    }

    public static int bankedFood(AccountSnapshot s) {
        return FOOD.stream().filter(id -> s.getBank().getOrDefault(id, 0) > 0)
                .max(Comparator.comparingInt(id -> s.getBank().get(id))).orElse(0);
    }

    public static int edible(AccountSnapshot s) {
        return FOOD.stream().mapToInt(id -> s.getInventory().getOrDefault(id, 0) - s.getNotedInventory().getOrDefault(id, 0)).sum();
    }

    private static void runes(AccountSnapshot snap, int magic, int target, List<Need> out) {
        int xp = GearPlan.xpNeeded(snap, target);
        GearPlan.Magic m = GearPlan.magic(magic, GearPlan.ownedMap(snap), xp);
        int casts = GearPlan.casts(m.getSpell(), magic, xp);
        int worn = m.getStaff();
        GearPlan.cost(m.getSpell()).forEach((rune, per) -> {
            int staff = GearPlan.staffFor(rune);
            if (staff != 0 && staff == worn) return;
            int start = Math.min(per * casts, Math.max(per * GearPlan.MIN_CASTS, snap.owned(rune)));
            out.add(new Need("runes", Set.of(rune), per * casts, runeSource(snap, rune), rune, runeName(rune), start, per));
        });
    }

    private static FreeSource arrowSource(AccountSnapshot snap) {
        if (snap.isHarlanReady()) return FreeSource.HARLAN;
        return snap.isShopsAllowed() && snap.getCoins() > 0 ? FreeSource.LOWE : null;
    }

    /** Harlan only hands out air and mind runes. */
    private static FreeSource runeSource(AccountSnapshot snap, int rune) {
        if ((rune == GearPlan.AIR || rune == GearPlan.MIND) && snap.isHarlanReady()) return FreeSource.HARLAN;
        return snap.isShopsAllowed() && snap.getCoins() >= shopPrice(rune) ? FreeSource.AUBURY : null;
    }

    public static int shopPrice(int itemId) {
        if (itemId == 882) return 1;
        return itemId == GearPlan.MIND ? 3 : 4;
    }

    private static FreeSource harlan(AccountSnapshot snap) {
        return snap.isHarlanReady() ? FreeSource.HARLAN : null;
    }

    private static String runeName(int id) {
        switch (id) {
            case GearPlan.AIR: return "air runes";
            case GearPlan.WATER: return "water runes";
            case GearPlan.EARTH: return "earth runes";
            case GearPlan.FIRE: return "fire runes";
            default: return "mind runes";
        }
    }

    private static Set<Integer> set(int[] ids) {
        return Arrays.stream(ids).boxed().collect(Collectors.toSet());
    }
}
