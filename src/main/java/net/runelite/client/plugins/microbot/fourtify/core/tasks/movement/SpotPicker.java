package net.runelite.client.plugins.microbot.fourtify.core.tasks.movement;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.game.Gate;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks a spot that is safe for the player's combat level, the first time one is asked for, and keeps it:
 * the nearest (the default) or a random one. Spot safety covers the spot, not the way there, so the nearest
 * spot keeps the walk short and away from aggressive monsters elsewhere on the map; a random spot can mean a
 * long walk past them. A spot reported unreachable, or dropped by a task for another reason, is never picked again in this run, and the next
 * {@link #apply(GameState)} picks another. When the spots to choose from change (such as the tree to chop
 * moving on with a level), it picks again among the new ones. Returns null when no spot is left, or while the player's
 * position is not known yet.
 */
@Slf4j
public class SpotPicker implements Function<GameState, Spot> {
    /** How to choose among the safe spots. */
    public enum Choice {
        /** The closest safe spot: a short walk. */
        NEAREST,
        /** Any safe spot on the map: more variety, but a long walk whose route is not checked. */
        RANDOM
    }

    private final Function<GameState, List<Spot>> spots;
    private final Choice choice;
    private final Random random;
    private final Set<Spot> unreachable = new HashSet<>();
    private Spot current;
    private List<Spot> pickedFrom;

    /** Nearest spot of a fixed list. */
    public SpotPicker(List<Spot> spots) {
        this(state -> spots, Choice.NEAREST, new Random());
    }

    /** {@code spots} gives the spots to choose from now, such as those of the tree to chop. */
    public SpotPicker(Function<GameState, List<Spot>> spots, Choice choice, Random random) {
        this.spots = spots;
        this.choice = choice;
        this.random = random;
    }

    @Override
    public Spot apply(GameState state) {
        List<Spot> now = spots.apply(state);
        if (!now.equals(pickedFrom)) {
            if (current != null) {
                log.info("[Spots] Moving on from {}: different spots to choose from", current);
            }
            current = null;
            pickedFrom = now;
        }
        if (current == null && state.getPosition() != null) {
            int combat = state.getCombatLevel();
            List<Spot> safe = now.stream()
                    .filter(s -> s.isSafeFor(combat) && s.usableIn(state) && !unreachable.contains(s))
                    .collect(Collectors.toList());
            if (safe.isEmpty()) {
                return null;
            }
            current = choice == Choice.RANDOM
                    ? safe.get(random.nextInt(safe.size()))
                    : Collections.min(safe, Comparator.comparingInt(s -> s.getCentre().distanceTo2D(state.getPosition())));
            log.info("[Spots] Chose {}, {} tiles away ({} of {} safe spots at combat {})", current,
                    current.getCentre().distanceTo2D(state.getPosition()), choice.name().toLowerCase(), safe.size(), combat);
        }
        return current;
    }

    /** A requirement that a spot is found: {@code base} is what it says, and what the gates of the spots need once they are why none is. */
    public Requirement requirement(String base) {
        return Requirement.explained(base, state -> apply(state) != null, state -> explainNone(base, state));
    }

    /**
     * The text of a requirement that a spot must be found, when none is: {@code base}, and when spots that are safe
     * and free to use but for their gates exist, what those spots need, such as "(its spots need the quest Royal
     * Trouble, or Fishing 68)". Spots dropped in this run are not counted.
     */
    public String explainNone(String base, GameState state) {
        Set<String> needs = new LinkedHashSet<>();
        for (Spot spot : spots.apply(state)) {
            if (spot.isSafeFor(state.getCombatLevel()) && (!spot.isMembers() || state.isMembersWorld()) && !unreachable.contains(spot)) {
                List<Gate> unmet = spot.unmetGates(state);
                if (!unmet.isEmpty()) {
                    needs.add(unmet.stream().map(Gate::toString).collect(Collectors.joining(" and ")));
                }
            }
        }
        return needs.isEmpty() ? base : base + " (its spots need " + String.join(", or ", needs) + ")";
    }

    /** True for a spot the walker could not reach in this run. */
    public boolean isUnreachable(Spot spot) {
        return unreachable.contains(spot);
    }

    /** A spot that cannot be used in this run for the given reason, such as chopping there does nothing: never pick it again. */
    public void drop(Spot spot, String reason) {
        log.info("[Spots] Dropping {}: {}; picking another spot", spot, reason);
        unreachable.add(spot);
        if (spot.equals(current)) {
            current = null;
        }
    }

    /** The walker could not reach the current spot: never pick it again in this run. */
    public void unreachable() {
        if (current != null) {
            log.info("[Spots] Cannot reach {}; picking another spot", current);
            unreachable.add(current);
            current = null;
        }
    }
}
