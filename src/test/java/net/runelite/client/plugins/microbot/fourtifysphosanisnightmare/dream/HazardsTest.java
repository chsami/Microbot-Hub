package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HazardsTest
{
    private static WorldPoint at(int x, int y)
    {
        return new WorldPoint(x, y, 3);
    }

    private static List<WorldPoint> square(WorldPoint centre, int radius)
    {
        List<WorldPoint> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                tiles.add(centre.dx(dx).dy(dy));
            }
        }
        return tiles;
    }

    @Test
    public void underHerIsDangerousWhileHerRiftsAreOutAndTheDodgeLeavesHer()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(20, 20), 5, 5);
        hazards.observeBoss(boss);
        assertFalse(hazards.isDangerous(at(22, 21)), "without rifts her body is not a hazard");
        hazards.observeRift(at(40, 40), 5, 0);
        hazards.expire(6);
        assertTrue(hazards.isDangerous(at(22, 21)));
        WorldPoint tile = hazards.escape(at(22, 21), square(at(22, 21), 4), boss);
        assertNotNull(tile);
        assertFalse(boss.contains(tile));
        hazards.expire(10);
        assertFalse(hazards.isDangerous(at(22, 21)), "the claws have landed");
    }

    @Test
    public void whileRiftsAreOutATileNextToHerIsUnsafeUntilSheReachesThePlayer()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(22, 27), 5, 5);
        hazards.observeBoss(new WorldArea(at(22, 28), 5, 5));
        hazards.observeBoss(boss);
        hazards.observePlayer(at(22, 25));
        assertFalse(hazards.isDangerous(at(23, 26)), "without rifts a tile next to her is safe");
        hazards.observeRift(at(40, 40), 5, 0);
        hazards.expire(6);
        assertTrue(hazards.isDangerous(at(23, 26)), "she steps toward a player out of her reach");
        assertNull(hazards.beside(at(22, 25), square(at(22, 25), 3), boss), "the player waits for her to come");
        hazards.observePlayer(at(23, 26));
        assertFalse(hazards.isDangerous(at(23, 26)), "a player in her reach is not stepped onto");
        assertFalse(hazards.isDangerous(at(21, 26)));
    }

    @Test
    public void betweenHerStepsATileNextToHerStaysUnsafe()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(22, 27), 5, 5);
        hazards.observeBoss(new WorldArea(at(22, 28), 5, 5));
        hazards.observeBoss(boss);
        hazards.observeBoss(boss);
        hazards.observePlayer(at(22, 21));
        hazards.observeRift(at(40, 40), 5, 0);
        hazards.expire(6);
        assertTrue(hazards.isDangerous(at(23, 26)), "walking toward a player she steps every other tick");
        hazards.observeBoss(boss);
        assertFalse(hazards.isDangerous(at(23, 26)), "two ticks still, she has stopped");
    }

    @Test
    public void whileSheStandsStillATileNextToHerStaysSafe()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(22, 27), 5, 5);
        hazards.observeBoss(boss);
        hazards.observeBoss(boss);
        hazards.observePlayer(at(22, 21));
        hazards.observeRift(at(40, 40), 5, 0);
        hazards.expire(6);
        assertFalse(hazards.isDangerous(at(23, 26)), "in her final phase she claws without walking");
        assertNotNull(hazards.beside(at(22, 21), square(at(22, 21), 6), boss));
    }

    @Test
    public void aRiftCountsFromWhenItOpenedNotWhenItIsSeen()
    {
        Hazards hazards = new Hazards();
        hazards.observeRift(at(10, 10), 20, 3);
        hazards.expire(21);
        assertTrue(hazards.isDangerous(at(10, 10)));
        hazards.expire(22);
        assertFalse(hazards.isDangerous(at(10, 10)), "it opened at 17 and its claws came up at 21");
        hazards.observeRift(at(10, 10), 22, 5);
        assertFalse(hazards.isActive(), "a portal still in view after its claws came up is not counted again");
    }

    @Test
    public void aRiftIsDangerousUntilItLandsFourTicksLater()
    {
        Hazards hazards = new Hazards();
        hazards.observeRift(at(10, 10), 5, 0);
        hazards.observeRift(at(10, 10), 7, 0);
        assertTrue(hazards.isDangerous(at(10, 10)));
        assertFalse(hazards.isDangerous(at(11, 10)));
        hazards.expire(9);
        assertTrue(hazards.isDangerous(at(10, 10)), "the first sighting sets the landing tick");
        hazards.expire(10);
        assertFalse(hazards.isDangerous(at(10, 10)));
        assertFalse(hazards.isActive());
    }

    @Test
    public void sporesCoverTheThreeByThreeAroundThem()
    {
        Hazards hazards = new Hazards();
        hazards.setSpores(Set.of(at(10, 10)));
        assertTrue(hazards.isDangerous(at(11, 9)));
        assertFalse(hazards.isDangerous(at(12, 10)));
    }

    @Test
    public void aSurgeCoversHerLaneAcrossTheArena()
    {
        Hazards hazards = new Hazards();
        hazards.observeSurge(new WorldArea(at(0, 20), 5, 5), 3, 1);
        assertTrue(hazards.isDangerous(at(30, 22)));
        assertFalse(hazards.isDangerous(at(30, 25)));
        hazards.expire(10);
        assertFalse(hazards.isDangerous(at(30, 22)));
        hazards.observeSurge(new WorldArea(at(20, 0), 5, 5), 2, 1);
        assertTrue(hazards.isDangerous(at(24, 40)));
        assertFalse(hazards.isDangerous(at(25, 40)));
    }

    @Test
    public void theSafeFlowerQuarterLiesBetweenTheTwoSafeArms()
    {
        Set<WorldPoint> safe = Set.of(at(12, 10), at(13, 10), at(10, 8), at(10, 7));
        Set<WorldPoint> unsafe = Set.of(at(8, 10), at(7, 10), at(10, 12), at(10, 13), at(10, 10));
        FlowerLayout layout = FlowerLayout.read(safe, unsafe);
        assertNotNull(layout);
        assertTrue(layout.isSafe(at(12, 8)), "east and south of the centre");
        assertFalse(layout.isSafe(at(8, 8)));
        assertFalse(layout.isSafe(at(12, 12)));
        assertFalse(layout.isSafe(at(12, 10)), "the flower line itself");
        assertNull(FlowerLayout.read(Set.of(at(12, 10)), unsafe), "one safe arm is not enough");
        assertNull(FlowerLayout.read(Set.of(), Set.of()));
    }

    @Test
    public void escapePrefersTheNearestSafeTileBesideHer()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(10, 10), 5, 5);
        WorldPoint player = at(9, 12);
        assertNull(hazards.escape(player, square(player, 2), boss), "already safe");
        hazards.observeRift(player, 1, 0);
        WorldPoint tile = hazards.escape(player, square(player, 2), boss);
        assertEquals(1, Math.max(Math.abs(tile.getX() - player.getX()), Math.abs(tile.getY() - player.getY())));
        assertEquals(1, boss.distanceTo(tile), "beside her so melee goes on");
        assertFalse(boss.contains(tile));
    }

    @Test
    public void besideFindsOnlySafeTilesNextToHer()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(10, 10), 5, 5);
        WorldPoint player = at(7, 16);
        hazards.setSpores(Set.of(at(9, 12)));
        WorldPoint tile = hazards.beside(player, square(player, 4), boss);
        assertNotNull(tile);
        assertEquals(1, boss.distanceTo(tile));
        assertFalse(hazards.isDangerous(tile));
        assertTrue(hazards.isActive());
    }

    @Test
    public void besideSkipsHerCornersWhichMeleeCannotReach()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(10, 10), 5, 5);
        // Standing on her south-west corner diagonal: only the tiles along her sides count.
        WorldPoint corner = at(9, 9);
        WorldPoint tile = hazards.beside(corner, List.of(corner, at(10, 9), at(9, 10)), boss);
        assertTrue(tile.equals(at(10, 9)) || tile.equals(at(9, 10)), String.valueOf(tile));
    }

    @Test
    public void aReturnToTheCentreIsHerFlowersNotASurge()
    {
        Hazards hazards = new Hazards();
        hazards.observeBoss(new WorldArea(at(10, 10), 5, 5));
        hazards.observeBoss(new WorldArea(at(12, 10), 5, 5));
        hazards.observeArrival(new WorldArea(at(10, 10), 5, 5), 0, 1);
        assertFalse(hazards.isActive(), "she came back to where she slept");
        hazards.observeArrival(new WorldArea(at(10, 0), 5, 5), 2002, 1);
        assertTrue(hazards.isDangerous(at(12, 30)), "a lane up her column from the south edge");
        assertFalse(hazards.isDangerous(at(20, 30)));
    }

    @Test
    public void herLaneFollowsTheEdgeSheReappearsOnNotHerFacing()
    {
        Hazards hazards = new Hazards();
        hazards.observeBoss(new WorldArea(at(10, 10), 5, 5));
        hazards.observeArrival(new WorldArea(at(3, 14), 5, 5), 1024, 1);
        assertTrue(hazards.isDangerous(at(12, 15)), "from the west edge across her row, while she still faces north");
        assertFalse(hazards.isDangerous(at(5, 25)));
        assertEquals(at(17, 14), hazards.surgeLanding().toWorldPoint());
        assertEquals(1, Hazards.laneDirection(at(10, 10), new WorldArea(at(17, 6), 5, 5)), "the east edge charges west");
        assertEquals(0, Hazards.laneDirection(at(10, 10), new WorldArea(at(14, 18), 5, 5)), "the north edge charges south");
    }

    @Test
    public void aLaneDownHerCentreColumnIsTwoTilesLongerThanTheOthers()
    {
        Hazards hazards = new Hazards();
        hazards.observeBoss(new WorldArea(at(10, 10), 5, 5));
        hazards.observeArrival(new WorldArea(at(10, 2), 5, 5), 0, 1);
        assertEquals(at(10, 18), hazards.surgeLanding().toWorldPoint(), "her centre column, from the south edge");
        hazards.observeArrival(new WorldArea(at(14, 2), 5, 5), 0, 1);
        assertEquals(at(14, 16), hazards.surgeLanding().toWorldPoint(), "a column beside it");
        hazards.observeArrival(new WorldArea(at(3, 10), 5, 5), 0, 1);
        assertEquals(at(17, 10), hazards.surgeLanding().toWorldPoint(), "her centre row");
        assertTrue(hazards.atLaneStart(new WorldArea(at(3, 10), 5, 5)));
        assertFalse(hazards.atLaneStart(new WorldArea(at(11, 9), 5, 5)), "a step from her centre");
    }

    @Test
    public void herFacingIsRoundedToTheNearestOfTheFourDirections()
    {
        assertEquals(0, Hazards.direction(2002), "just east of south");
        assertEquals(0, Hazards.direction(40));
        assertEquals(2, Hazards.direction(1060));
        assertEquals(1, Hazards.direction(470), "just south of west");
        assertEquals(3, Hazards.direction(1580));
    }

    @Test
    public void herSurgeLandsFourteenTilesAlongTheWaySheFacesUntilSheCharges()
    {
        Hazards hazards = new Hazards();
        assertNull(hazards.surgeLanding());
        hazards.observeSurge(new WorldArea(at(10, 0), 5, 5), 2, 1);
        assertEquals(at(10, 14), hazards.surgeLanding().toWorldPoint());
        hazards.observeSurge(new WorldArea(at(20, 10), 5, 5), 1, 1);
        assertEquals(at(6, 10), hazards.surgeLanding().toWorldPoint());
        hazards.surgeLanded();
        assertNull(hazards.surgeLanding());
        assertFalse(hazards.isDangerous(at(10, 12)), "the lane is clear once she has charged");
    }

    @Test
    public void theWayToWhereSheLandsStaysOnThePlayersSideOfHerLane()
    {
        Hazards hazards = new Hazards();
        // She reappears on the south edge and charges north up columns 26 to 30; the player stands west of them.
        hazards.observeSurge(new WorldArea(at(26, 22), 5, 5), 2, 1);
        WorldArea landing = hazards.surgeLanding();
        WorldPoint player = at(25, 28);
        // Beside her landing, the west and east sides are equally near: the east one is offered first.
        List<WorldPoint> reachable = new ArrayList<>(List.of(at(31, 36), at(25, 36)));
        assertEquals(at(25, 36), hazards.beside(player, reachable, landing), "crossing the lane walks into her charge");
        assertNull(hazards.beside(player, List.of(at(31, 36)), landing), "only the far side: wait instead");
        assertEquals(at(31, 36), hazards.escape(at(28, 30), List.of(at(31, 36)), null), "from inside the lane either side will do");
    }
    @Test
    public void aStepPrefersAWayThatDoesNotCrossUnderHerOrARift()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(20, 20), 5, 5);
        hazards.observeBoss(boss);
        hazards.observeRift(at(40, 40), 5, 0);
        hazards.expire(8);
        WorldPoint player = at(26, 24);
        // West of her is as near as north of her, but either way west runs under her as her claws land.
        assertFalse(hazards.wayClear(player, at(19, 22)));
        assertTrue(hazards.wayClear(player, at(26, 31)));
        List<WorldPoint> reachable = List.of(at(19, 22), at(26, 31));
        assertEquals(at(26, 31), hazards.inReach(player, reachable, boss, tile -> true));
        // A rift on the way north blocks it too.
        hazards.observeRift(at(26, 28), 5, 0);
        assertFalse(hazards.wayClear(player, at(26, 31)));
        assertNull(hazards.inReach(player, List.of(at(19, 22)), boss, tile -> true),
            "with no clear way, the player waits rather than end a tick on her claws");
        assertEquals(at(19, 22), hazards.escape(at(26, 28), List.of(at(19, 22)), boss),
            "from a tile her claws will hit, the nearest safe tile is still the way out");
    }

    @Test
    public void aWayOutOfASporeNeverCrossesHerClaws()
    {
        Hazards hazards = new Hazards();
        hazards.setSpores(List.of(at(10, 11)));
        hazards.observeRift(at(11, 10), 5, 0);
        hazards.expire(8);
        WorldPoint player = at(10, 10);
        assertNull(hazards.escape(player, List.of(at(12, 10)), null),
            "a slowed player would end a tick on the rift: drowsy beats her claws");
        assertEquals(at(10, 13), hazards.escape(player, List.of(at(12, 10), at(10, 13)), null),
            "a way past the spore itself will do");
        Hazards lane = new Hazards();
        lane.observeSurge(new WorldArea(at(0, 10), 5, 5), 3, 0);
        lane.observeRift(at(10, 9), 0, 0);
        assertEquals(at(10, 8), lane.escape(player, List.of(at(10, 8)), null),
            "her surge would hit where the player stands, so the way out may cross her claws");
    }

    @Test
    public void aWalkBesideHerMayCrossClawsThatLandAfterItEnds()
    {
        Hazards hazards = new Hazards();
        WorldArea boss = new WorldArea(at(20, 20), 5, 5);
        hazards.observeBoss(boss);
        WorldPoint player = at(27, 22);
        hazards.observePlayer(player);
        for (int y = 17; y <= 27; y++)
        {
            hazards.observeRift(at(26, y), 10, 0);
        }
        hazards.expire(10);
        WorldPoint tile = hazards.beside(player, square(player, 3), boss);
        assertNotNull(tile, "they land in four ticks, the walk takes two");
        assertEquals(25, tile.getX());
        hazards.expire(13);
        assertNull(hazards.beside(player, square(player, 3), boss), "they land next tick, on the way");
    }

    @Test
    public void withNoClearWayAStepNeverEndsBesideASpore()
    {
        Hazards hazards = new Hazards();
        // Each way to the tile passes beside the spore.
        hazards.setSpores(List.of(at(24, 32)));
        assertNull(hazards.inReach(at(25, 34), List.of(at(22, 31)), null, tile -> true),
            "a step beside the spore slows the player");
        hazards.setSpores(List.of(at(22, 33)));
        assertEquals(at(20, 33), hazards.escape(at(23, 32), List.of(at(20, 33)), null),
            "a player beside a spore is slowed already, so the way out may pass it");
    }
}
