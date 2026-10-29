package net.runelite.client.plugins.microbot.FourtifyFishing.tasks;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.FishingMethod;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.FishingMethod.Yield;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.Harpoon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What Best available fishes: the method with the best expected experience per tick among those the account can run, in
 * every skill the method trains (Fishing, and Strength and Agility for the leaping fish, Strength for a barehanded
 * catch), at the player's real levels (boosts are ignored, as elsewhere). The queue asks each tick, so a level gained
 * during the run moves the player on to the next method; see {@link Fishing#queue}.
 * <p>
 * The rate of a method is what one roll brings, over the ticks between rolls ({@link FishingMethod#getRollTicks()}). A roll
 * rolls for each fish the player may catch, the best fish first, and the next only when that failed, so the chance of a
 * fish is its own roll times the chance that every better one failed (the big net rolls each item on its own). A fish is
 * rolled for only when the player has its Fishing level and the other skills it asks for (leaping salmon: Strength and
 * Agility 30), and the chance of each roll comes from {@code ChanceData}. A harpoon method uses the best harpoon the
 * player carries, wears or banks, or, with none, fishes barehanded when the account can: 20 more Fishing levels per fish,
 * its Strength level, the plain harpoon's chances, and the Strength experience of the catch.
 * <p>
 * Left out, as they are the same for every method or small: the spot moving (every 250 to 530 ticks, about a click and a
 * walk of a few ticks each time, so about 2%), dropping or banking fish, fetching bait, the junk and caskets that give no
 * experience, the pet and clue bottle rolls, and the diabolic worms of anglerfish (faster, but for fewer experience per
 * fish: the rate is that of sandworms). Spot moves of the anglerfish (8 to 830 ticks) and of eels (100 to 300) would cost
 * those methods more, but none of them is near the best at any level.
 */
public final class BestMethod {
    private BestMethod() {
    }

    /** The methods Best available may pick: every one whose gates can be read from the game (see {@link FishingMethod#isAuto()}). */
    public static List<FishingMethod> candidates() {
        List<FishingMethod> methods = new ArrayList<>();
        for (FishingMethod method : FishingMethod.values()) {
            if (method.isAuto()) {
                methods.add(method);
            }
        }
        return methods;
    }

    /** Expected experience per game tick in all skills of fishing with the method now, at the player's real levels and tools. */
    public static double xpPerTick(FishingMethod method, GameState state) {
        return xpPerTick(method, state, true);
    }

    /** As {@link #xpPerTick(FishingMethod, GameState)}, counting the Fishing experience alone when {@code allSkills} is false. */
    static double xpPerTick(FishingMethod method, GameState state, boolean allSkills) {
        boolean harpoonMethod = method.barehandedExtraLevels() > 0;
        boolean barehanded = harpoonMethod && !Harpoon.owned(state) && method.canFishBarehanded(state);
        int harpoonRate = harpoonMethod && !barehanded ? Math.max(100, Harpoon.bestCatchRate(state)) : 100;
        int fishing = state.level(Skill.FISHING);
        double xp = 0;
        double failed = 1;
        for (Yield yield : method.getYields()) {
            if (!mayCatch(method, yield, state, barehanded)) {
                continue;
            }
            double roll = method.rollChance(yield, fishing, harpoonRate);
            double chance = method.isIndependentRolls() ? roll : failed * roll;
            failed *= 1 - roll;
            xp += chance * (allSkills ? yield.totalXp(barehanded) : yield.getFishingXp());
        }
        return xp / method.getRollTicks();
    }

    /** The player has the real levels of the fish: its Fishing level, its other skills, and for barehanded fishing the extra Fishing levels and Strength. */
    private static boolean mayCatch(FishingMethod method, Yield yield, GameState state, boolean barehanded) {
        int first = method.firstLevel(yield);
        if (state.level(Skill.FISHING) < first + (barehanded ? method.barehandedExtraLevels() : 0)) {
            return false;
        }
        if (barehanded && state.level(Skill.STRENGTH) < first) {
            return false;
        }
        for (Map.Entry<Skill, Integer> need : yield.getSkills().entrySet()) {
            if (state.level(need.getKey()) < need.getValue()) {
                return false;
            }
        }
        return true;
    }
}
