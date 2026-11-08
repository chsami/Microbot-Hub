package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.Collection;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Potion doses from item IDs listed from four doses down to one. */
public final class Doses
{
    public static final List<Integer> SUPER_RESTORE = List.of(3024, 3026, 3028, 3030);
    public static final List<Integer> PRAYER_POTION = List.of(2434, 139, 141, 143);

    private Doses()
    {
    }

    /** Doses in one potion of the ID, or 0 when the ID is not in the list. */
    public static int of(List<Integer> potion, int id)
    {
        int index = potion.indexOf(id);
        return index < 0 ? 0 : potion.size() - index;
    }

    /** Doses carried across the potions, given how many of each ID are carried. */
    public static int count(Collection<List<Integer>> potions, IntUnaryOperator carried)
    {
        int total = 0;
        for (List<Integer> potion : potions)
        {
            for (int id : potion)
            {
                total += of(potion, id) * carried.applyAsInt(id);
            }
        }
        return total;
    }

    /** The carried ID with the fewest doses, so part-used potions go first; -1 when none is carried. */
    public static int lowest(Collection<List<Integer>> potions, IntUnaryOperator carried)
    {
        int best = -1;
        int bestDoses = Integer.MAX_VALUE;
        for (List<Integer> potion : potions)
        {
            for (int id : potion)
            {
                if (carried.applyAsInt(id) > 0 && of(potion, id) < bestDoses)
                {
                    best = id;
                    bestDoses = of(potion, id);
                }
            }
        }
        return best;
    }
}
