package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.coords.LocalPoint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Select an existing stand from observed position, rather than a stale alternating flag. */
final class ZulrahMeleeDodge
{
    static boolean cloudCovers(LocalPoint tile, LocalPoint cloud)
    {
        // The helper renders each toxic-cloud object as a 3-by-3 area centered on its tile.
        return distance(tile, cloud) <= 1;
    }

    static ZulrahRotation.Stand choose(LocalPoint player, ZulrahRotation.Stand home,
                                      Predicate<LocalPoint> clouded)
    {
        Set<ZulrahRotation.Stand> candidates = new LinkedHashSet<>();
        candidates.add(home);
        candidates.add(home.meleeAlternate());
        candidates.add(ZulrahRotation.Stand.SW_MELEE);
        candidates.add(ZulrahRotation.Stand.SE_MELEE);
        ZulrahRotation.Stand best = null;
        int nearest = Integer.MAX_VALUE;
        for (ZulrahRotation.Stand candidate : candidates)
        {
            int distance = distance(player, candidate.local());
            // At least two tiles clears the targeted tile's neighbouring melee area.
            if (distance < 2 || clouded.test(candidate.local())) continue;
            if (distance < nearest)
            {
                nearest = distance;
                best = candidate;
            }
        }
        return best;
    }

    static boolean escaped(LocalPoint player, LocalPoint origin)
    {
        return origin == null || distance(player, origin) >= 2;
    }

    private static int distance(LocalPoint a, LocalPoint b)
    {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) / 128;
    }
}
