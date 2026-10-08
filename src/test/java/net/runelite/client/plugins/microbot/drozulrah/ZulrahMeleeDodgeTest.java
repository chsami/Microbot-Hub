package net.runelite.client.plugins.microbot.drozulrah;

import java.util.Arrays;
import java.util.function.Predicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZulrahMeleeDodgeTest
{
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64); }
    private static Predicate<LocalPoint> clouds(int[][] centers)
    {
        return point -> Arrays.stream(centers).anyMatch(c -> ZulrahMeleeDodge.cloudCovers(point, tile(c[0], c[1])));
    }

    @Test public void recordedFirstMeleeEscapesOccupiedAlternate()
    {
        // Recording tick 2275: already at SW_MELEE, with venom centered at (57,59).
        Predicate<LocalPoint> clouded = clouds(new int[][]{{53,53},{56,53},{47,54},{50,53},{57,56},{57,59},{47,60}});
        assertEquals(ZulrahRotation.Stand.SW,
                ZulrahMeleeDodge.choose(tile(56,62), ZulrahRotation.Stand.SW, clouded));
    }

    @Test public void recordedSecondMeleeStaysOnTheSameSide()
    {
        // Tick 2363: old 5x5 cloud exclusion rejected EP and sent the player across the arena.
        Predicate<LocalPoint> clouded = clouds(new int[][]{{47,54},{50,53},{56,53},{57,56}});
        assertEquals(ZulrahRotation.Stand.EP,
                ZulrahMeleeDodge.choose(tile(46,60), ZulrahRotation.Stand.EP, clouded));
    }

    @Test public void nextDodgeUsesObservedArrivalRatherThanToggleHistory()
    {
        assertEquals(ZulrahRotation.Stand.SW_MELEE,
                ZulrahMeleeDodge.choose(ZulrahRotation.Stand.SW.local(), ZulrahRotation.Stand.SW, point -> false));
        assertEquals(ZulrahRotation.Stand.SW,
                ZulrahMeleeDodge.choose(ZulrahRotation.Stand.SW_MELEE.local(), ZulrahRotation.Stand.SW, point -> false));
    }

    @Test public void rejectsCloudedTargetsAndDoesNotPretendToDodge()
    {
        assertNull(ZulrahMeleeDodge.choose(tile(56,62), ZulrahRotation.Stand.SW, point -> true));
    }

    @Test public void cloudFootprintMatchesHelperThreeByThree()
    {
        assertTrue(ZulrahMeleeDodge.cloudCovers(tile(58,60), tile(57,59)));
        assertFalse(ZulrahMeleeDodge.cloudCovers(tile(58,61), tile(57,59)));
    }

    @Test public void arrivalToleranceCannotCancelEscapeAfterOnlyOneTile()
    {
        assertFalse(ZulrahMeleeDodge.escaped(tile(57,61), tile(56,62)));
        assertTrue(ZulrahMeleeDodge.escaped(tile(58,61), tile(56,62)));
        assertTrue(ZulrahMeleeDodge.escaped(tile(56,62), null));
    }
}
