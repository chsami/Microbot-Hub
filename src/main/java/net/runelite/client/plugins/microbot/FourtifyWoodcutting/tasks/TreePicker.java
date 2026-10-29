package net.runelite.client.plugins.microbot.FourtifyWoodcutting.tasks;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.FourtifyWoodcuttingConfig;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Axe;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Spots;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.TreeType;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which tree to chop: one fixed type, or the best one available, which is the tree with the best expected Woodcutting
 * XP per tick ({@link #xpPerTick}) among those the player can chop: their real Woodcutting level allows it, it is not
 * members-only on a free-to-play world, its own gates are met ({@link TreeType#gates()}), and it has a spot safe for their combat level. The rate uses the best axe they
 * can use ({@link Axe#bestChopChance}). Tasks ask every tick, so a level gained during the run moves the player on to
 * the next tree. A higher tree is not always the better one: arctic pines give 40 XP a log against 125 for mahogany,
 * and a yew gives a log far less often than a willow with a good axe.
 * <p>
 * With the best tree, logs from earlier trees are still in the inventory after an upgrade, so dropping and
 * banking handle the logs of every tree type, still by item ID only.
 */
public final class TreePicker implements Function<GameState, TreeType> {
    private final TreeType fixed;
    private final Function<TreeType, List<Spot>> spots;
    private Predicate<Spot> unreachable = spot -> false;

    private TreePicker(TreeType fixed, Function<TreeType, List<Spot>> spots) {
        this.fixed = fixed;
        this.spots = spots;
    }

    public static TreePicker fixed(TreeType tree) {
        return new TreePicker(tree, TreeType::getSpots);
    }

    public static TreePicker bestAvailable() {
        return bestAvailable(TreeType::getSpots);
    }

    /** The best tree with chosen spots per tree, for tests. */
    public static TreePicker bestAvailable(Function<TreeType, List<Spot>> spots) {
        return new TreePicker(null, spots);
    }

    public static TreePicker of(FourtifyWoodcuttingConfig.TreeChoice choice) {
        return choice.getTree() == null ? bestAvailable() : fixed(choice.getTree());
    }

    /**
     * Spots the walker could not reach no longer count, so when every spot of the best tree is unreachable (a
     * quest or boat the account lacks) the plan falls back to the next tree.
     */
    public TreePicker skipping(Predicate<Spot> unreachable) {
        this.unreachable = unreachable;
        return this;
    }

    public boolean isFixed() {
        return fixed != null;
    }

    /** The fixed tree, or null for the best available. */
    public TreeType fixedTree() {
        return fixed;
    }

    /** The tree to chop now. With no safe spot for any allowed tree, the plain tree, whose spot check then fails. */
    @Override
    public TreeType apply(GameState state) {
        if (fixed != null) {
            return fixed;
        }
        int level = state.level(Skill.WOODCUTTING);
        int axe = Axe.bestChopChance(state);
        TreeType best = null;
        double bestRate = 0;
        for (TreeType tree : TreeType.values()) {
            if (tree.getLevel() > level || tree.isMembers() && !state.isMembersWorld()
                    || !tree.gates().stream().allMatch(gate -> gate.isMet(state))) {
                continue;
            }
            // The cheap rate first: the spot check only matters for a tree that would win. On a tie the later (higher) tree wins.
            double rate = xpPerTick(tree, level, axe);
            if (rate >= bestRate && spots.apply(tree).stream().anyMatch(s -> s.isSafeFor(state.getCombatLevel()) && s.usableIn(state)
                    && !unreachable.test(s))) {
                best = tree;
                bestRate = rate;
            }
        }
        return best != null ? best : TreeType.TREE;
    }

    /**
     * Ticks lost each time a tree falls and the next one is chopped: a tick to click it, a few ticks to walk to it
     * and the roll timer starting again. Our own estimate, used by {@link #xpPerTick}. Anywhere from 3 to 10 moves
     * only near ties: the free-to-play switch from oak to willow by two levels (69 to 71), and whether teak, rosewood
     * or ironwood leads from 80 up, where they are within 4% of each other.
     */
    static final int SWITCH_TICKS = 6;

    /**
     * Expected Woodcutting XP per game tick of working a spot: the tree's XP per log, times the chance a roll gives
     * one, over the {@value Woodcutting#ROLL_TICKS} ticks between rolls, less the time lost each time a tree falls.
     * A tree lasts its chopping time plus the wait for the log that fells it (4 ticks over the chance), then
     * {@value #SWITCH_TICKS} ticks go on the next tree, so a regular tree, which falls after one log, loses the most.
     * Left out: the respawn wait (a spot has several trees), dropping or banking logs, and the Woodcutting Guild's
     * invisible boost.
     */
    public static double xpPerTick(TreeType tree, int woodcuttingLevel, int axeChopChance) {
        double chance = tree.chanceIn256(woodcuttingLevel, axeChopChance) / 256.0;
        double life = tree.getChopTicks() + Woodcutting.ROLL_TICKS / chance;
        return tree.getXp() * chance / Woodcutting.ROLL_TICKS * life / (life + SWITCH_TICKS);
    }

    /** Every tree this plan may chop during the run. */
    public List<TreeType> trees() {
        return fixed != null ? Collections.singletonList(fixed) : Arrays.asList(TreeType.values());
    }

    /** The quests the gates of this plan's trees and of their spots read, for the snapshot ({@code Surroundings.quests}). */
    public Set<Quest> quests() {
        Set<Quest> quests = new LinkedHashSet<>();
        for (TreeType tree : trees()) {
            tree.gates().forEach(gate -> quests.addAll(gate.quests()));
            spots.apply(tree).forEach(spot -> quests.addAll(spot.quests()));
        }
        return quests;
    }

    /** The longest a felled tree of this plan takes to grow back, in ticks. */
    public int longestRespawn() {
        return trees().stream().mapToInt(TreeType::getRespawnTicks).max().orElse(0);
    }

    /** The spots of the tree to chop now. */
    public List<Spot> spots(GameState state) {
        return spots.apply(apply(state));
    }

    /** Logs to drop or bank: this tree's, or every tree's when the tree can change during the run. */
    public Set<Integer> logIds() {
        if (fixed != null) {
            return Collections.singleton(fixed.getLogId());
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (TreeType tree : TreeType.values()) {
            ids.add(tree.getLogId());
        }
        return ids;
    }

    /** Object names the snapshot must include. */
    public Set<String> objectNames() {
        if (fixed != null) {
            return Collections.singleton(fixed.getObjectName());
        }
        Set<String> names = new LinkedHashSet<>();
        for (TreeType tree : TreeType.values()) {
            names.add(tree.getObjectName());
        }
        return names;
    }

    /** "Logs" for one tree, "logs" for all of them, as in "drop the Logs". */
    public String logLabel() {
        return fixed != null ? fixed.getLogName() : "logs";
    }

    @Override
    public String toString() {
        return fixed != null ? fixed.toString() : "best tree";
    }
}
