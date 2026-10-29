package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A work area, such as trees to chop or fishing spots: its centre, a radius that covers them, the lowest combat
 * level at which no monster nearby is aggressive, whether it is members-only, and the {@link Gate}s (quests, skill
 * levels) an account needs to use it.
 * A level-based aggressive monster attacks players whose combat level is at most twice its own, so
 * minCombat = 2 x (strongest aggressive level) + 1.
 */
@Value
public class Spot implements Area {
    String name;
    WorldPoint centre;
    int radius;
    int minCombat;
    @With
    boolean members;
    /** All of these must hold. Not part of what makes two spots the same: the spot's place is. */
    @EqualsAndHashCode.Exclude
    List<Gate> gates;

    public Spot(String name, WorldPoint centre, int radius, int minCombat, boolean members) {
        this(name, centre, radius, minCombat, members, Collections.emptyList());
    }

    public Spot(String name, WorldPoint centre, int radius, int minCombat, boolean members, List<Gate> gates) {
        this.name = name;
        this.centre = centre;
        this.radius = radius;
        this.minCombat = minCombat;
        this.members = members;
        this.gates = Collections.unmodifiableList(new ArrayList<>(gates));
    }

    /** A free-to-play spot on the ground floor. */
    public static Spot of(String name, int x, int y, int radius, int minCombat) {
        return new Spot(name, new WorldPoint(x, y, 0), radius, minCombat, false);
    }

    /** Free-to-play spots work anywhere; members spots only on a members world; and every gate of the spot must hold. */
    public boolean usableIn(GameState state) {
        return (!members || state.isMembersWorld()) && gates.stream().allMatch(gate -> gate.isMet(state));
    }

    /** The gates of the spot that do not hold now. */
    public List<Gate> unmetGates(GameState state) {
        List<Gate> unmet = new ArrayList<>();
        for (Gate gate : gates) {
            if (!gate.isMet(state)) {
                unmet.add(gate);
            }
        }
        return unmet;
    }

    /** The quests the spot's gates read, to ask for in the surroundings. */
    public Set<Quest> quests() {
        Set<Quest> quests = new LinkedHashSet<>();
        gates.forEach(gate -> quests.addAll(gate.quests()));
        return quests;
    }

    public boolean isSafeFor(int combatLevel) {
        return combatLevel >= minCombat;
    }

    @Override
    public String toString() {
        return name;
    }
}
