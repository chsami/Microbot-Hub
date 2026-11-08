package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BombStepTest
{
    private static final WorldPoint PLAYER = new WorldPoint(100, 100, 0);
    private static final WorldArea DEMON = new WorldArea(new WorldPoint(104, 99, 0), 3, 3);

    private static WorldPoint at(int x, int y)
    {
        return new WorldPoint(x, y, 0);
    }

    private static Map<WorldPoint, Integer> reachable(Object... tilesAndSteps)
    {
        Map<WorldPoint, Integer> result = new HashMap<>();
        for (int index = 0; index < tilesAndSteps.length; index += 2)
        {
            result.put((WorldPoint) tilesAndSteps[index], (Integer) tilesAndSteps[index + 1]);
        }
        return result;
    }

    @Test
    public void picksAOneStepTileThatIsNotBombed()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 1, at(102, 100), 2);
        assertEquals(at(101, 100), BombStep.choose(PLAYER, Set.of(at(99, 100)), DEMON, tiles, 7, null));
    }

    @Test
    public void aOneStepTileBeatsATwoStepTileEvenWhenTheTwoStepTileIsNearerTheDemon()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(102, 100), 2);
        assertEquals(at(99, 100), BombStep.choose(PLAYER, Set.of(), DEMON, tiles, 1, null));
    }

    @Test
    public void prefersATileWithinReachOfTheDemon()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 1, at(100, 101), 1);
        assertEquals(at(101, 100), BombStep.choose(PLAYER, Set.of(), DEMON, tiles, 3, null));
    }

    @Test
    public void whenNoOneStepTileIsInReachItStillStepsToTheLowestOne()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(100, 101), 1, at(99, 100), 1, at(99, 101), 1);
        assertEquals(at(99, 100), BombStep.choose(PLAYER, Set.of(), DEMON, tiles, 1, null));
    }

    @Test
    public void excludesTheDemonsFootprint()
    {
        WorldArea adjacent = new WorldArea(new WorldPoint(101, 99, 0), 3, 3);
        Map<WorldPoint, Integer> tiles = reachable(at(101, 100), 1, at(99, 100), 1);
        assertEquals(at(99, 100), BombStep.choose(PLAYER, Set.of(), adjacent, tiles, 7, null));
    }

    @Test
    public void fallsBackToTwoStepsWhenEveryOneStepTileIsUnsafe()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 1, at(98, 100), 2, at(102, 100), 2);
        assertEquals(at(102, 100), BombStep.choose(PLAYER, Set.of(at(99, 100), at(101, 100)), DEMON, tiles, 3, null));
    }

    @Test
    public void returnsNullWhenNothingIsSafe()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 2);
        assertNull(BombStep.choose(PLAYER, Set.of(at(99, 100), at(101, 100)), DEMON, tiles, 7, null));
        assertNull(BombStep.choose(PLAYER, Set.of(), DEMON, reachable(), 7, null));
    }

    @Test
    public void ignoresTheStartTileFarTilesAndOtherPlanes()
    {
        Map<WorldPoint, Integer> tiles = reachable(PLAYER, 0, at(97, 100), 3, new WorldPoint(99, 100, 1), 1);
        assertNull(BombStep.choose(PLAYER, Set.of(), DEMON, tiles, 7, null));
    }

    @Test
    public void choosesWithoutADemonAndBreaksTiesByCoordinates()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(101, 100), 1, at(99, 101), 1, at(99, 99), 1);
        assertEquals(at(99, 99), BombStep.choose(PLAYER, Set.of(), null, tiles, 7, null));
    }

    @Test
    public void breaksTiesTowardTheRoomCentreSoStepsDoNotDrift()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 1, at(100, 99), 1);
        assertEquals(at(101, 100), BombStep.choose(PLAYER, Set.of(), null, tiles, 7, at(110, 100)));
        assertEquals(at(99, 100), BombStep.choose(PLAYER, Set.of(), null, tiles, 7, at(90, 100)));
    }

    @Test
    public void reachOfTheDemonStillComesBeforeTheRoomCentre()
    {
        Map<WorldPoint, Integer> tiles = reachable(at(99, 100), 1, at(101, 100), 1);
        assertEquals(at(101, 100), BombStep.choose(PLAYER, Set.of(), DEMON, tiles, 3, at(90, 100)));
    }

    @Test
    public void returnsNullWithoutAPlayerOrTiles()
    {
        assertNull(BombStep.choose(null, Set.of(), DEMON, reachable(at(99, 100), 1), 7, null));
        assertNull(BombStep.choose(PLAYER, Set.of(), DEMON, null, 7, null));
    }
}
