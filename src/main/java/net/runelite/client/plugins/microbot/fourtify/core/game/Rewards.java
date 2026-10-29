package net.runelite.client.plugins.microbot.fourtify.core.game;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The special items an activity hands out, by item ID: clue scrolls (one item per clue step, every tier), challenge
 * scrolls, scroll boxes, clue bottles, clue geodes, clue nests, bird nests, caskets and reward caskets; the big
 * fish (big bass, big swordfish, big shark); the Varlamore loop half of key; and the skilling pets in the inventory
 * (beaver, heron, rock golem, giant squirrel, rocky, tangleroot and baby chinchompa, with every colour and form of
 * each). The account rule: they are never dropped, and never counted as unneeded items to bank at the start of a run;
 * {@code ClientActions} refuses to drop one, and tasks leave them alone. The IDs are in {@code RewardIds.java}, which
 * is generated and must not be edited by hand. A clue scroll alone has hundreds of items, so they are generated instead
 * of being listed by hand. Noted and placeholder versions have no name of their own and are not in it.
 */
public final class Rewards {
    public static final Set<Integer> IDS = load();

    private Rewards() {
    }

    private static Set<Integer> load() {
        Set<Integer> ids = new HashSet<>();
        for (int id : RewardIds.IDS) {
            ids.add(id);
        }
        return Collections.unmodifiableSet(ids);
    }
}
