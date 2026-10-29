package net.runelite.client.plugins.microbot.fourtify.core.tasks.interact;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * When idle, clicks a menu action on the nearest NPC of a name inside the area, such as "Net" on a fishing spot
 * or "Talk-to" on a quest's NPC. Given several names for the action, it clicks whichever the NPC has. NPCs are found
 * again from each snapshot, so a fishing spot that moved (every 250 to 530 ticks) or a person who walked off is
 * clicked where it is now. Given NPC ids, it clicks only NPCs of those ids: several kinds of NPC share a name and
 * an action, such as the fishing spots, and the ids tell them apart. With none there it waits. Not while a dialogue is open: one with options is for
 * {@code ChooseOption} to answer.
 * <p>
 * A click that works makes the player walk, start the action's animation, or open a dialogue within
 * {@code waitTicks} ticks, or {@code gained} shows that fast; otherwise the click did nothing, a failure.
 * {@link #refusingDialogues()} is for actions that never open a dialogue when they work, such as a fishing
 * spot: there a dialogue alone is the game refusing the click ("You need a heavy fishing rod to lure these
 * fish."), a failure at once that names the dialogue's text.
 */
public class InteractWithNpc implements Task {
    private final String npcName;
    private final List<String> actions;
    /** The ids of the NPCs to click; null for any NPC of the name. */
    private final Set<Integer> npcIds;
    private final Function<GameState, ? extends Area> area;
    private final int waitTicks;
    private final BiPredicate<GameState, GameState> gained;
    private boolean dialogueIsSuccess = true;

    public InteractWithNpc(String npcName, String action, Function<GameState, ? extends Area> area, int waitTicks,
                           BiPredicate<GameState, GameState> gained) {
        this(npcName, Collections.singletonList(action), area, waitTicks, gained);
    }

    public InteractWithNpc(String npcName, List<String> actions, Function<GameState, ? extends Area> area, int waitTicks,
                           BiPredicate<GameState, GameState> gained) {
        this(npcName, actions, null, area, waitTicks, gained);
    }

    public InteractWithNpc(String npcName, List<String> actions, Set<Integer> npcIds, Function<GameState, ? extends Area> area,
                           int waitTicks, BiPredicate<GameState, GameState> gained) {
        this.npcName = npcName;
        this.actions = actions;
        this.npcIds = npcIds;
        this.area = area;
        this.waitTicks = waitTicks;
        this.gained = gained;
    }

    /** A dialogue after the click, with no walking, animation or gain, is the game refusing it: a failure naming the text. */
    public InteractWithNpc refusingDialogues() {
        this.dialogueIsSuccess = false;
        return this;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.isIdle() && !state.inDialogue();
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Npc target = target(state, npcName, actions, npcIds, area.apply(state));
        if (target == null) {
            return null; // none here now; wait for one
        }
        String action = actions.stream().filter(target::hasAction).findFirst().orElseThrow(IllegalStateException::new);
        if (!act.interact(target, action)) {
            throw new IllegalStateException("could not click " + target.getName());
        }
        WaitFor waitFor = new WaitFor("walk to or " + action.toLowerCase() + " the " + npcName, waitTicks, (before, now) ->
                now.isMoving() || now.isAnimating() || (dialogueIsSuccess && now.inDialogue()) || gained.test(before, now));
        if (dialogueIsSuccess) {
            return waitFor;
        }
        return waitFor.refusedBy((before, now) -> now.inDialogue()
                ? "the game answered the " + action + " click with a dialogue: \"" + now.getDialogue().getText() + "\"" : null);
    }

    /** The NPC to click: that name with that action (any, when null), inside the area, nearest to the player within twice its radius. */
    public static Npc target(GameState state, String name, String action, Area area) {
        return target(state, name, action == null ? null : Collections.singletonList(action), area);
    }

    /** As {@link #target(GameState, String, String, Area)}, with any of the actions. */
    public static Npc target(GameState state, String name, List<String> actions, Area area) {
        return target(state, name, actions, null, area);
    }

    /** As {@link #target(GameState, String, List, Area)}, only an NPC with one of the ids (any, when null). */
    public static Npc target(GameState state, String name, List<String> actions, Set<Integer> npcIds, Area area) {
        if (area == null) {
            return null;
        }
        return state.npcs().stream()
                .filter(n -> n.getName().equals(name) && (actions == null || actions.stream().anyMatch(n::hasAction)))
                .filter(n -> npcIds == null || npcIds.contains(n.getId()))
                .filter(n -> n.getLocation().distanceTo(area.getCentre()) <= area.getRadius())
                .filter(n -> state.distanceTo(n.getLocation()) <= area.getRadius() * 2)
                .min(Comparator.comparingInt(n -> state.distanceTo(n.getLocation())))
                .orElse(null);
    }
}
