package net.runelite.client.plugins.microbot.fourtifysgemmining.util.action;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysgemmining.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InteractionRangeTest
{
    @Test
    public void nearestEligibleBankerWinsInsteadOfTheFirstNpcInTheList()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2852, 2951, 0);
        world.banker(1, new WorldPoint(2862, 2951, 0));
        world.banker(2, new WorldPoint(2853, 2951, 0));
        assertTrue(world.actions.openBank(GemMine.SURFACE, () -> { }));
        world.actions.dispatch();
        assertEquals(2, world.requests.get(0)[3]);
    }

    @Test
    public void nearestEligibleBankObjectWinsByItsFootprintDistance()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2826, 9385, 0);
        world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2830, 9385, 0));
        assertTrue(world.actions.openBank(GemMine.UNDERGROUND, () -> { }));
        world.actions.dispatch();
        assertEquals(56, world.requests.get(0)[0]);
    }

    @Test
    public void bankerAtTwelveTilesIsAvailableButThirteenTilesIsNot()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2839, 2951, 0);
        world.banker(7, new WorldPoint(2852, 2951, 0));
        AtomicInteger sent = new AtomicInteger();
        assertFalse(world.actions.openBank(GemMine.SURFACE, sent::incrementAndGet));
        world.position = new WorldPoint(2840, 2951, 0);
        assertTrue(world.actions.openBank(GemMine.SURFACE, sent::incrementAndGet));
        assertTrue(world.requests.isEmpty());
        world.actions.dispatch();
        assertEquals(1, sent.get());
        assertEquals("Bank", world.requests.get(0)[5]);
    }

    @Test
    public void bankerMovingPastTwelveTilesBeforeDispatchIsRejected()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2840, 2951, 0);
        NPC base = world.banker(7, new WorldPoint(2852, 2951, 0));
        AtomicReference<WorldPoint> position = new AtomicReference<>(base.getWorldLocation());
        NPC moving = TestWorld.proxy(NPC.class, (name, args) -> {
            switch (name)
            {
                case "getWorldLocation":
                    return position.get();
                case "getWorldView":
                    return world.view;
                case "getComposition":
                case "getTransformedComposition":
                    return base.getComposition();
                case "getIndex":
                    return 7;
                case "getName":
                    return "Banker";
                default:
                    return null;
            }
        });
        world.npcs.clear();
        world.npcs.add(moving);
        AtomicInteger sent = new AtomicInteger();
        assertTrue(world.actions.openBank(GemMine.SURFACE, sent::incrementAndGet));
        position.set(new WorldPoint(2853, 2951, 0));
        world.actions.dispatch();
        assertEquals(0, sent.get());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void rockDiscoveryAndDispatchIncludeTwentyButExcludeTwentyOneTiles()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2819, 9380, 0);
        GameObject inRange = world.rock(2839, 9380);
        GameObject outside = world.rock(2840, 9380);
        assertEquals(1, world.actions.rocks(GemMine.UNDERGROUND).size());
        assertEquals(inRange, world.actions.nearestRock(GemMine.UNDERGROUND));
        AtomicInteger sent = new AtomicInteger();
        assertFalse(world.actions.mine(GemMine.UNDERGROUND, outside, sent::incrementAndGet));
        assertTrue(world.actions.mine(GemMine.UNDERGROUND, inRange, sent::incrementAndGet));
        world.actions.dispatch();
        assertEquals(1, sent.get());
        assertEquals("Mine", world.requests.get(0)[5]);
    }

    @Test
    public void objectRangeUsesTheNearestFootprintEdgeRatherThanItsAnchor()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2825, 9385, 0);
        world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2847, 9385, 0));
        AtomicReference<Point> minimum = new AtomicReference<>(new Point(45, 85));
        GameObject object = depositChest(world, minimum, new Point(49, 85));
        assertTrue(world.actions.openBank(GemMine.UNDERGROUND, () -> { }));
        world.actions.dispatch();
        assertEquals(1, world.requests.size());
        assertEquals(72, world.requests.get(0)[0]);
        assertEquals(22, world.position.distanceTo(object.getWorldLocation()));
    }

    @Test
    public void objectMovingOutOfRangeBeforeDispatchIsRejected()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2825, 9385, 0);
        world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2845, 9385, 0));
        AtomicReference<Point> minimum = new AtomicReference<>(new Point(45, 85));
        depositChest(world, minimum, null);
        AtomicInteger sent = new AtomicInteger();
        assertTrue(world.actions.openBank(GemMine.UNDERGROUND, sent::incrementAndGet));
        minimum.set(new Point(46, 85));
        world.actions.dispatch();
        assertEquals(0, sent.get());
        assertTrue(world.requests.isEmpty());
    }

    private static GameObject depositChest(TestWorld world, AtomicReference<Point> minimum, Point maximum)
    {
        GameObject object = TestWorld.proxy(GameObject.class, (name, args) -> {
            switch (name)
            {
                case "getId":
                    return 10530;
                case "getWorldView":
                    return world.view;
                case "getPlane":
                    return 0;
                case "getWorldLocation":
                    int end = maximum == null ? minimum.get().getX() : maximum.getX();
                    return new WorldPoint(2800 + (minimum.get().getX() + end) / 2, 9385, 0);
                case "getLocalLocation":
                    return net.runelite.api.coords.LocalPoint.fromScene(52 + (minimum.get().getX()
                        + (maximum == null ? minimum.get().getX() : maximum.getX())) / 2 - 25, 52);
                case "sizeX":
                    return maximum == null ? 1 : maximum.getX() - minimum.get().getX() + 1;
                case "sizeY":
                    return 1;
                case "getSceneMinLocation":
                    return new Point(52 + minimum.get().getX() - 25, 52);
                case "getSceneMaxLocation":
                    return new Point(52 + (maximum == null ? minimum.get().getX() : maximum.getX()) - 25, 52);
                default:
                    return null;
            }
        });
        world.objects.clear();
        world.objects.add(object);
        return object;
    }
}
