package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene;

import net.runelite.api.Point;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TargetRangeTest
{
    private final TestWorld world = new TestWorld();

    @Test
    public void searchRadiiAreTwelveTilesForNpcsAndTwentyForObjects()
    {
        assertEquals(12, TargetRange.NPC_SEARCH_RADIUS);
        assertEquals(20, TargetRange.GAME_OBJECT_SEARCH_RADIUS);
    }

    @Test
    public void npcDistanceIsTheLargerAxisDifferenceAcrossDiagonals()
    {
        assertEquals(12, TargetRange.npcDistance(world.client, world.npc(12).npc));
        assertEquals(13, TargetRange.npcDistance(world.client, world.npc(13).npc));
        WorldPoint origin = world.position;
        assertEquals(12, TargetRange.npcDistance(world.client,
            world.npcAt(new WorldPoint(origin.getX() - 12, origin.getY() + 12, 0)).npc));
        assertEquals(13, TargetRange.npcDistance(world.client,
            world.npcAt(new WorldPoint(origin.getX() - 4, origin.getY() + 13, 0)).npc));
        assertEquals(0, TargetRange.npcDistance(world.client, world.npcAt(origin).npc));
    }

    @Test
    public void npcsOnAnotherPlaneOrWorldViewOrAbsentHaveNoDistance()
    {
        TestWorld.NpcTarget otherPlane = world.npcAt(new WorldPoint(world.position.getX(), world.position.getY(), 1));
        TestWorld.NpcTarget foreign = world.npc(2);
        foreign.topLevel = false;
        assertEquals(Integer.MAX_VALUE, TargetRange.npcDistance(world.client, otherPlane.npc));
        assertEquals(Integer.MAX_VALUE, TargetRange.npcDistance(world.client, foreign.npc));
        assertEquals(Integer.MAX_VALUE, TargetRange.npcDistance(world.client, null));
        world.topLevel = false;
        assertEquals(Integer.MAX_VALUE, TargetRange.npcDistance(world.client, world.npc(2).npc));
        world.topLevel = true;
        world.playerPresent = false;
        assertEquals(Integer.MAX_VALUE, TargetRange.npcDistance(world.client, world.npc(2).npc));
    }

    @Test
    public void objectDistanceMeasuresFromTheNearestEdgeOfTheFootprint()
    {
        assertEquals(20, TargetRange.objectDistance(world.client, world.object(1, 20, 20).object));
        assertEquals(21, TargetRange.objectDistance(world.client, world.object(2, 21, 21).object));
        assertEquals(19, TargetRange.objectDistance(world.client, world.object(3, 19, 25).object));
        assertEquals(0, TargetRange.objectDistance(world.client, world.object(4, -3, 3).object));
        assertEquals(2, TargetRange.objectDistance(world.client, world.object(5, -9, -2).object));
    }

    @Test
    public void objectDistanceUsesTheLargerAxisAcrossDiagonals()
    {
        TestWorld.ObjectTarget diagonal = world.object(1, 0, 0);
        diagonal.minimum = new Point(TestWorld.PLAYER_SCENE - 20, TestWorld.PLAYER_SCENE + 20);
        diagonal.maximum = diagonal.minimum;
        assertEquals(20, TargetRange.objectDistance(world.client, diagonal.object));
        diagonal.maximum = new Point(TestWorld.PLAYER_SCENE - 20, TestWorld.PLAYER_SCENE + 22);
        diagonal.minimum = new Point(TestWorld.PLAYER_SCENE - 20, TestWorld.PLAYER_SCENE + 21);
        assertEquals(21, TargetRange.objectDistance(world.client, diagonal.object));
    }

    @Test
    public void objectsOnAnotherPlaneOrWorldViewHaveNoDistance()
    {
        TestWorld.ObjectTarget otherPlane = world.object(1, 3, 3);
        otherPlane.plane = 1;
        TestWorld.ObjectTarget foreign = world.object(2, 3, 3);
        foreign.topLevel = false;
        assertEquals(Integer.MAX_VALUE, TargetRange.objectDistance(world.client, otherPlane.object));
        assertEquals(Integer.MAX_VALUE, TargetRange.objectDistance(world.client, foreign.object));
        assertEquals(Integer.MAX_VALUE, TargetRange.objectDistance(world.client, null));
        world.topLevel = false;
        assertEquals(Integer.MAX_VALUE, TargetRange.objectDistance(world.client, world.object(3, 3, 3).object));
    }

    @Test
    public void aSingleTileObjectIsMeasuredFromItsOwnTile()
    {
        assertEquals(20, TargetRange.objectDistance(world.client, wall(TestWorld.PLAYER_SCENE + 20, TestWorld.PLAYER_SCENE - 5)));
        assertEquals(21, TargetRange.objectDistance(world.client, wall(TestWorld.PLAYER_SCENE - 3, TestWorld.PLAYER_SCENE + 21)));
    }

    private WallObject wall(int sceneX, int sceneY)
    {
        return TestWorld.proxy(WallObject.class, (name, args) ->
        {
            switch (name)
            {
                case "getWorldView":
                    return world.view;
                case "getPlane":
                    return 0;
                case "getLocalLocation":
                    return LocalPoint.fromScene(sceneX, sceneY);
                default:
                    return null;
            }
        });
    }
}
