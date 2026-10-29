package net.runelite.client.plugins.microbot.FourtifyFishing.tasks;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.activity.Activity;
import net.runelite.client.plugins.microbot.fourtify.core.activity.ActivityQueue;
import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.interact.InteractWithNpc;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.interact.WaitWhileAnimating;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.SpotPicker;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.movement.WalkToArea;
import net.runelite.client.plugins.microbot.FourtifyFishing.FourtifyFishingConfig;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.FishingMethod;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.SpotKind;
import net.runelite.client.plugins.microbot.FourtifyFishing.data.Spots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/** The fishing activities, built in one place so the live script and the offline tests run the same tasks. */
public final class Fishing {
    /** Feathers or bait taken from the bank at a time; one is used up per fish. Our choice. */
    static final int BAIT_AMOUNT = 1000;
    /** Spots are looked for this far from the player: from one edge of the largest spot area to the other. */
    static final int SEARCH_RADIUS = 2 * Spots.largestRadius();
    /** A click makes the player walk to the spot or start fishing within this many ticks, as for any click. */
    static final int CLICK_WAIT = 4;
    /**
     * Longest normal stretch without moving, fishing or a catch, the same for every method: a spot lives 250 to
     * 530 ticks before it moves, so within the longest life a spot of the place has shown again. Anglerfish spots
     * move after 8 to 830 ticks, so for them the limit can be shorter than the longest wait. No method has a
     * measured value; this is our reading, and a live run checks it.
     */
    static final int IDLE_LIMIT = 530;
    /**
     * Off for every method: how often a catch is rolled has not been measured for any of them, so there is no basis
     * for a limit on fishing without a catch; the idle limit still stops clicks that do nothing.
     */
    static final int GAIN_LIMIT = 0;

    static final String FULL_OF_OTHER_ITEMS = "inventory is full of items the plugin does not drop; free some space and restart";

    private Fishing() {
    }

    /**
     * The configured method, or Best available: one activity for every method {@link BestMethod#candidates()} lists,
     * of which the one with the best experience per tick ({@link BestMethod#xpPerTick}) that can start runs, at every tick.
     * <p>
     * Why a rate on each activity and not a fixed order: the best method depends on the player's levels, which change
     * during the run (a barbarian rod beats fly fishing only from Fishing 58), and on what they own. The queue ranks
     * the activities at every tick by their rates and runs the best one whose requirements hold ({@link ActivityQueue}),
     * so a level gained moves the run on, and a method whose tool or bait is nowhere falls back to the next best. The rates
     * depend only on real levels (which do not fall), the world and the best harpoon, so they cannot flap between two
     * methods at one level, and the method that runs counts 10% more (see {@link ActivityQueue}), so a method that is only
     * a few percent better for a few levels does not cost a bank trip. Moving on banks the old method's tool and catch and
     * fetches the new one's, as when the queue falls back ({@link ActivityQueue#tasks}).
     * Best available never picks a method whose gate cannot be read ({@link FishingMethod#getNotAutoReason()}); every other
     * requirement, such as a quest, is the method's own ({@link FishingMethod#requirements()}) and holds in both modes.
     */
    public static ActivityQueue queue(FourtifyFishingConfig config) {
        return queue(config, Spots::of);
    }

    /** As {@link #queue(FourtifyFishingConfig)}, with the spots of each kind given. */
    static ActivityQueue queue(FourtifyFishingConfig config, Function<SpotKind, List<Spot>> spots) {
        boolean best = config.method().getMethod() == null;
        List<FishingMethod> methods = best ? BestMethod.candidates() : Collections.singletonList(config.method().getMethod());
        Activity[] activities = methods.stream()
                .map(method -> {
                    Activity activity = activity(method, new SpotPicker(state -> spots.apply(method.getSpotKind()), config.spotChoice(), new Random()),
                            config.fullInventory(), config.targetLevel());
                    if (best) {
                        activity.rate(state -> BestMethod.xpPerTick(method, state));
                    }
                    return activity;
                })
                .toArray(Activity[]::new);
        return ActivityQueue.of("Fishing to " + config.targetLevel(), activities);
    }

    /**
     * Fish with one method at a safe spot of its kind until the target level: fetch the tool and bait from the bank
     * when missing, drop or bank full inventories of fish and junk, walk to the spot, fish the nearest spot of the kind
     * with the method's action (found again when it moves), and wait while fishing. A catch can bring a clue bottle
     * (items "Clue bottle (beginner)" to "(elite)": every successful catch rolls for
     * each tier the player has no clue of). A bottle is a special item, never dropped or banked: it stays in the
     * inventory, one slot per tier at most. It is not a catch either: it never arrives without Fishing XP, so the
     * click check stays on XP and the fish. Junk (leather boots from the big net, say) is no catch either: junk
     * arriving alone does not show that the click worked.
     */
    public static Activity activity(FishingMethod method, SpotPicker spot, FourtifyFishingConfig.FullInventory fullInventory, int targetLevel) {
        String npc = method.getSpotKind().getNpcName();
        Set<Integer> fish = new LinkedHashSet<>(method.getCatchIds());
        List<Requirement> requirements = new ArrayList<>(method.requirements());
        requirements.add(spot.requirement("a " + npc.toLowerCase() + " that is safe at your combat level"));
        Activity activity = Activity.named(method + " to " + targetLevel)
                .requires(requirements.toArray(new Requirement[0]))
                .surroundings(Surroundings.builder().npcName(npc).radius(SEARCH_RADIUS).varbits(method.varbits()).quests(quests(method)).build())
                .stuckTimer(IDLE_LIMIT, GAIN_LIMIT)
                .stopWhen(fullOfOtherItems(fullInventory))
                .needs(method.needs(BAIT_AMOUNT).toArray(new NeededItem[0]));
        // The catch is the work's product, carried and kept at the start of a run even when it is not dropped (a stack).
        activity.keeps(fish);
        if (fullInventory == FourtifyFishingConfig.FullInventory.BANK) {
            activity.banks(method.bankIds(), "fish and junk", GameState::isFull);
        } else {
            activity.drops(method.dropIds(), "fish and junk");
        }
        return activity.work(
                        new WalkToArea(spot, spot::unreachable),
                        new InteractWithNpc(npc, method.getActions(), method.getSpotKind().npcIds(), spot, CLICK_WAIT, (before, now) ->
                                now.xp(Skill.FISHING) > before.xp(Skill.FISHING) || fish.stream().anyMatch(id -> now.count(id) > before.count(id)))
                                // A working click never opens a dialogue; one that does is the game refusing it (no rod, say).
                                .refusingDialogues(),
                        new WaitWhileAnimating())
                .doneWhen(state -> state.level(Skill.FISHING) >= targetLevel);
    }

    /** The quests the method and the gates of its kind's spots read: the snapshot must hold them, or the gates stay shut. */
    static Set<Quest> quests(FishingMethod method) {
        Set<Quest> quests = new LinkedHashSet<>(method.quests());
        Spots.of(method.getSpotKind()).forEach(spot -> quests.addAll(spot.quests()));
        return quests;
    }

    /**
     * Stops a full inventory that holds nothing the plugin drops or banks (no fish): no slot can be freed, so the
     * next click would fail and the run would end after three failures with no reason given. Items are only ever
     * dropped or banked by known ID, never guessed at, and special items such as clue bottles are never dropped, so
     * a full inventory of bottles and other items stops too and the player is asked to make room.
     * <p>
     * The queue checks every activity's stops all the time, so the IDs are those of every method, not only this
     * one's: a full load of shrimp is no reason to stop a run that moved on to fly fishing. And it holds only
     * after the first Fishing XP of the run: before that, the activity's one-time bank trip is still due to bank
     * what it does not need (a full inventory of logs, say), and a stop would pre-empt it.
     */
    static StopCondition fullOfOtherItems(FourtifyFishingConfig.FullInventory fullInventory) {
        Set<Integer> cleared = new LinkedHashSet<>();
        for (FishingMethod method : FishingMethod.values()) {
            cleared.addAll(fullInventory == FourtifyFishingConfig.FullInventory.BANK ? method.bankIds() : method.dropIds());
        }
        int[] startXp = {-1};
        boolean[] fished = {false};
        return new StopCondition(FULL_OF_OTHER_ITEMS, state -> {
            int xp = state.xp(Skill.FISHING);
            startXp[0] = startXp[0] < 0 ? xp : startXp[0];
            fished[0] |= xp > startXp[0];
            return fished[0] && state.isFull() && !state.hasAny(cleared);
        });
    }
}
