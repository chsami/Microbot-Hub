package net.runelite.client.plugins.microbot.FourtifyWoodcutting.tasks;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.activity.Activity;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StuckTimer;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.interact.WaitWhileAnimating;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory.PickUpItems;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.SpotPicker;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.WalkToArea;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.FourtifyWoodcuttingConfig;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Axe;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Nests;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.Spots;
import net.runelite.client.plugins.microbot.FourtifyWoodcutting.data.TreeType;

import java.util.Map;
import java.util.Random;
import java.util.Set;

/** The woodcutting activity, built in one place so the live script and the offline tests run the same tasks. */
public final class Woodcutting {
    /** Ticks for the click and the walk to a tree after a respawn wait. */
    static final int CLICK_AND_WALK = 51;
    /** Chopping may not stop by mistake more often than once in this many logs. */
    static final double FALSE_STOP_ODDS = 1e9;
    /** A woodcutting roll comes every 4 ticks. */
    static final int ROLL_TICKS = 4;

    /**
     * Longest normal stretch with no movement, no woodcutting animation and nothing gained: every tree of the
     * spot felled, waiting for the first to respawn (the longest respawn of the trees the picker may choose), plus the click and the walk.
     */
    public static int idleLimit(TreePicker tree) {
        return tree.longestRespawn() + CLICK_AND_WALK;
    }

    /**
     * Longest normal stretch with no XP, item or step: a respawn wait, then misses in a row while chopping. For
     * the slowest tree the picker may choose (bronze axe at its level) that many misses happen less than once in a billion logs.
     */
    public static int gainLimit(TreePicker tree) {
        int limit = 0;
        for (TreeType t : tree.trees()) {
            double miss = 1 - t.getSlowestChance() / 256.0;
            int rolls = (int) Math.ceil(Math.log(1 / FALSE_STOP_ODDS) / Math.log(miss));
            limit = Math.max(limit, t.getRespawnTicks() + rolls * ROLL_TICKS);
        }
        return limit;
    }

    /**
     * Trees and nests are looked for this far from the player: twice the largest spot radius, the farthest a tree
     * is clicked from ({@code InteractWithScenery.target} takes trees within twice the spot's radius).
     */
    static final int SEARCH_RADIUS = Spots.maxRadius() * 2;

    /** What the snapshot must hold: the trees the picker may choose, and nests, near the player, and the quests the gates read. */
    public static Surroundings surroundings(TreePicker tree) {
        return Surroundings.builder().objectNames(tree.objectNames()).groundItemIds(Nests.IDS).quests(tree.quests())
                .radius(SEARCH_RADIUS).build();
    }

    public static StuckTimer stuckTimer(TreePicker tree) {
        return new StuckTimer(idleLimit(tree), gainLimit(tree));
    }

    private Woodcutting() {
    }

    /** Nests fall next to the tree being cut, so a few tiles' reach is enough. */
    static final int NEST_REACH = 5;

    /**
     * The whole run ends when the inventory is full and holds a nest but no log of the plan: no log can be dropped
     * or banked to make room, and a nest is never dropped or banked. Only nests, axes and bonds may be carried
     * for this to hold, so unneeded items the first bank trip would still clear do not stop the run early.
     */
    public static StopCondition fullOfNests(TreePicker tree) {
        Set<Integer> axes = Axe.need().ids();
        return new StopCondition("inventory is full of nests; free some space and restart", state ->
                state.isFull() && state.hasAny(Nests.IDS) && !state.hasAny(tree.logIds())
                        && state.getItems().entrySet().stream().filter(item -> item.getValue() > 0).map(Map.Entry::getKey)
                        .allMatch(id -> Nests.IDS.contains(id) || axes.contains(id) || Bonds.IDS.contains(id)));
    }

    public static Activity activity(TreePicker tree, SpotPicker.Choice spotChoice, FourtifyWoodcuttingConfig.FullInventory fullInventory, int targetLevel) {
        return activity(tree, new SpotPicker(tree::spots, spotChoice, new Random()), fullInventory, targetLevel);
    }

    /**
     * Chop the picker's trees at a safe spot until the target level: fetch an axe from the bank if none is carried,
     * then drop or bank full inventories of logs, pick up nests that fall nearby, walk to the spot, chop, and
     * wait while chopping. Nests are special items ({@code Rewards}), so they are never dropped, and the first
     * bank trip does not bank a nest carried at the start: a full inventory loses logs to make room for them
     * (dropping comes before the pick-up in the work order), and one full of nests ends the run
     * ({@link #fullOfNests}). With the same spot picker, for tests.
     */
    public static Activity activity(TreePicker tree, SpotPicker spot, FourtifyWoodcuttingConfig.FullInventory fullInventory, int targetLevel) {
        tree.skipping(spot::isUnreachable);
        boolean bank = fullInventory == FourtifyWoodcuttingConfig.FullInventory.BANK;
        Activity activity = Activity.named("Woodcutting " + tree + " to " + targetLevel)
                .surroundings(surroundings(tree))
                .stuckTimer(idleLimit(tree), gainLimit(tree))
                .needs(Axe.need())
                .stopWhen(fullOfNests(tree));
        if (tree.isFixed()) {
            // On the activity, not on ChopTree: a run that can never chop this tree stops before it walks to the spot.
            // The best tree is always one the level, world and gates allow, so only a fixed tree has these to meet.
            activity.requires(Requirement.level(Skill.WOODCUTTING, tree.fixedTree().getLevel()));
            if (tree.fixedTree().isMembers()) {
                activity.requires(new Requirement("a members world", GameState::isMembersWorld));
            }
            tree.fixedTree().gates().forEach(gate -> activity.requires(gate.requirement()));
        }
        if (bank) {
            activity.banks(tree.logIds(), tree.logLabel(), GameState::isFull);
        } else {
            activity.drops(tree.logIds(), tree.logLabel());
        }
        return activity.work(
                        new PickUpItems(item -> Nests.IDS.contains(item.getId()), NEST_REACH),
                        new WalkToArea(spot, spot::unreachable),
                        new ChopTree(tree, spot, dead -> spot.drop(dead, "chopping there does nothing")),
                        new WaitWhileAnimating())
                .doneWhen(state -> state.level(Skill.WOODCUTTING) >= targetLevel);
    }
}
