package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Rewards;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The carried items an activity does not need, to bank before its work starts: everything but the items it keeps
 * (its needs and food), the bonds and the special items an activity hands out ({@link Rewards}: clue scrolls,
 * bird nests, caskets and the like). Once the inventory has held none of them, this stays empty while the activity
 * keeps running, so items the work brings later (a bird nest, loot, the catch) do not send the player back to the
 * bank. When another activity of the queue takes over, {@link #rearm()} clears that state: the other activity's
 * tool, bait and catch are unneeded here, and are banked when this activity resumes.
 */
public final class UnneededItems {
    private final Collection<Integer> kept;
    private final boolean none;
    private boolean cleared;

    public UnneededItems(Collection<Integer> kept) {
        this(kept, false);
    }

    private UnneededItems(Collection<Integer> kept, boolean none) {
        this.kept = kept;
        this.none = none;
    }

    /** Nothing is ever unneeded: the activity keeps what it carries. */
    public static UnneededItems none() {
        UnneededItems none = new UnneededItems(Collections.emptySet(), true);
        none.cleared = true;
        return none;
    }

    /** Another activity has run since: check the inventory again at the next visit. Never for an activity that keeps what it carries. */
    public void rearm() {
        cleared = none;
    }

    /** The unneeded item IDs carried now, in ID order; empty once the inventory has been clear of them. */
    public Set<Integer> in(GameState state) {
        Set<Integer> out = new TreeSet<>();
        if (cleared) {
            return out;
        }
        state.getItems().forEach((id, count) -> {
            if (count > 0 && !kept.contains(id) && !Bonds.IDS.contains(id) && !Rewards.IDS.contains(id)) {
                out.add(id);
            }
        });
        cleared = out.isEmpty();
        return out;
    }
}
