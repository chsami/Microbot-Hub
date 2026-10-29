package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The nests a tree drops while it is chopped, by item ID (each named "Bird nest" or "Clue nest (tier)"):
 * egg, seed and ring nests, the older seed nests, and the five clue nests.
 * Left out on purpose, though their names hold "nest": nest boxes (a different item, "Nest box"), crushed nests,
 * nest hats and every placeholder (an empty bank slot, never lying on the ground). The game has no noted version
 * of a filled nest (only an empty nest and a crushed nest can be noted, and those never fall from a tree), so
 * there is none to leave out.
 */
public final class Nests {
    public static final Set<Integer> IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ItemID.BIRD_NEST_EGG_RED, ItemID.BIRD_NEST_EGG_GREEN, ItemID.BIRD_NEST_EGG_BLUE, ItemID.BIRD_NEST_SEEDS,
            ItemID.BIRD_NEST_RING, ItemID.BIRD_NEST_EMPTY, ItemID.BIRD_NEST_CHEAPSEEDS, ItemID.BIRD_NEST_DECENTSEEDS,
            ItemID.BIRD_NEST_SEEDS_JAN2019, ItemID.BIRD_NEST_DECENTSEEDS_JAN2019,
            ItemID.WC_CLUE_NEST_BEGINNER, ItemID.WC_CLUE_NEST_EASY, ItemID.WC_CLUE_NEST_MEDIUM, ItemID.WC_CLUE_NEST_HARD,
            ItemID.WC_CLUE_NEST_ELITE)));

    private Nests() {
    }
}
