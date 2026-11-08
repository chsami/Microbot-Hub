package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifysagility.course.ObstacleObjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgilityRangeTest
{
    private static final List<AgilityActions> ACTIVE_ACTIONS = new ArrayList<>();
    @AfterEach
    public void restoreInput()
    {
        ACTIVE_ACTIONS.forEach(AgilityActions::reset);
        ACTIVE_ACTIONS.clear();
        MicrobotTestSupport.restoreAll();
    }

    @Test
    public void npcLookupAndDispatchIncludeTwelveTilesAndExcludeThirteen() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcState outside = fixture.npc(100, 63, 50);
        assertNull(fixture.actions.findNearest(100));
        fixture.actions.interactNpc(MenuAction.NPC_FIRST_OPTION.getId(), outside.npc);
        fixture.dispatch();
        assertTrue(fixture.requests.isEmpty());
        NpcState boundary = fixture.npc(100, 62, 62);
        assertSame(boundary.npc, fixture.actions.findNearest(100));
        fixture.actions.interactNpc(MenuAction.NPC_FIRST_OPTION.getId(), boundary.npc);
        fixture.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(boundary.index, fixture.requests.get(0)[3]);
    }

    @Test
    public void queuedNpcMustRemainLiveWithinTwelveTilesAndInTheSamePlaneAndView() throws Exception
    {
        for (int scenario = 0; scenario < 5; scenario++)
        {
            Fixture fixture = new Fixture();
            NpcState target = fixture.npc(100, 62, 50);
            fixture.actions.interactNpc(MenuAction.NPC_FIRST_OPTION.getId(), target.npc);
            switch (scenario)
            {
                case 0:
                    target.x = 63;
                    break;
                case 1:
                    target.plane = 1;
                    break;
                case 2:
                    target.view = fixture.foreign;
                    break;
                case 3:
                    fixture.npcs.clear();
                    fixture.npc(100, 62, 50);
                    break;
                default:
                    fixture.playerX = 49;
                    break;
            }
            fixture.dispatch();
            assertTrue(fixture.requests.isEmpty(), "Scenario " + scenario);
        }
    }

    @Test
    public void npcSelectionRejectsOtherPlanesAndWorldViews() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcState otherPlane = fixture.npc(100, 51, 50);
        otherPlane.plane = 1;
        NpcState otherView = fixture.npc(100, 52, 50);
        otherView.view = fixture.foreign;
        NpcState valid = fixture.npc(100, 62, 50);
        assertSame(valid.npc, fixture.actions.findNearest(100));
        fixture.playerView = fixture.foreign;
        assertNull(fixture.actions.findNearest(100));
    }

    @Test
    public void anObstacleIsInRangeAtTwentyTilesButNotTwentyOne() throws Exception
    {
        for (ObstacleObjectType type : ObstacleObjectType.values())
        {
            Fixture fixture = new Fixture();
            fixture.object(type, 100, 71, 50, 1, 1);
            assertFalse(fixture.actions.obstacleInRange(type, 100), type.name());
            fixture.object(type, 100, 70, 70, 1, 1);
            assertTrue(fixture.actions.obstacleInRange(type, 100), type.name());
            assertFalse(fixture.actions.obstacleInRange(type, 101), type.name());
        }
    }

    @Test
    public void allObstacleObjectTypesIncludeTwentyAndExcludeTwentyOneTiles() throws Exception
    {
        for (ObstacleObjectType type : ObstacleObjectType.values())
        {
            Fixture fixture = new Fixture();
            fixture.object(type, 100, 71, 50, 1, 1);
            fixture.actions.interactObstacle(type, 100, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
            fixture.dispatch();
            assertTrue(fixture.requests.isEmpty(), type.name());
            ObjectState boundary = fixture.object(type, 100, 70, 70, 1, 1);
            fixture.actions.interactObstacle(type, 100, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
            fixture.dispatch();
            assertEquals(1, fixture.requests.size(), type.name());
            assertEquals(70, fixture.requests.get(0)[0]);
            assertEquals(70, fixture.requests.get(0)[1]);
            if (type == ObstacleObjectType.GAME)
            {
                assertSame(boundary.object, fixture.actions.findNearestByIds(List.of(100)));
            }
            if (type == ObstacleObjectType.GROUND)
            {
                assertSame(boundary.object, fixture.actions.findNearestGroundObject(100));
            }
        }
    }

    @Test
    public void aLatchedCantReachFlagStillSendsTheObstacleClickWithoutAWalk() throws Exception
    {
        boolean detection = Microbot.isCantReachTargetDetectionEnabled;
        boolean latched = Microbot.cantReachTarget;
        Microbot.isCantReachTargetDetectionEnabled = true;
        Microbot.cantReachTarget = true;
        try
        {
            Fixture fixture = new Fixture();
            fixture.object(ObstacleObjectType.GAME, 100, 70, 50, 1, 1);
            fixture.actions.interactObstacle(ObstacleObjectType.GAME, 100, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
            fixture.dispatch();
            assertEquals(1, fixture.requests.size());
            assertTrue(Microbot.cantReachTarget, "a recovery walk would have cleared the flag");
        }
        finally
        {
            Microbot.isCantReachTargetDetectionEnabled = detection;
            Microbot.cantReachTarget = latched;
        }
    }

    @Test
    public void largeGameObjectsUseTheNearestFootprintEdgeForRangeAndRanking() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectState large = fixture.object(ObstacleObjectType.GAME, 100, 70, 49, 10, 3);
        assertTrue(large.object.getWorldLocation().distanceTo(fixture.player.getWorldLocation()) > 20);
        assertSame(large.object, fixture.actions.findNearestByIds(List.of(100)));
        fixture.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), large.object);
        fixture.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(large.object.getLocalLocation().getSceneX(), fixture.requests.get(0)[0]);
        fixture.objects.clear();
        ObjectState wideNear = fixture.object(ObstacleObjectType.GAME, 100, 60, 49, 20, 3);
        fixture.object(ObstacleObjectType.GAME, 100, 61, 50, 1, 1);
        assertSame(wideNear.object, fixture.actions.findNearestByIds(List.of(100)));
    }

    @Test
    public void queuedObjectsRecheckRangePlaneViewAndSceneMembership() throws Exception
    {
        for (ObstacleObjectType type : ObstacleObjectType.values())
        {
            for (int scenario = 0; scenario < 5; scenario++)
            {
                Fixture fixture = new Fixture();
                ObjectState target = fixture.object(type, 100, 70, 50, 1, 1);
                fixture.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), target.object);
                switch (scenario)
                {
                    case 0:
                        target.x = 71;
                        break;
                    case 1:
                        target.plane = 1;
                        break;
                    case 2:
                        target.view = fixture.foreign;
                        break;
                    case 3:
                        fixture.objects.clear();
                        break;
                    default:
                        fixture.playerX = 49;
                        break;
                }
                fixture.dispatch();
                assertTrue(fixture.requests.isEmpty(), type + " scenario " + scenario);
            }
        }
    }

    @Test
    public void objectSelectorsRejectForeignPlanesAndViews() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectState otherPlane = fixture.object(ObstacleObjectType.GAME, 100, 51, 50, 1, 1);
        otherPlane.plane = 1;
        ObjectState otherView = fixture.object(ObstacleObjectType.GAME, 100, 52, 50, 1, 1);
        otherView.view = fixture.foreign;
        assertNull(fixture.actions.findNearestByIds(List.of(100)));
        fixture.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), otherView.object);
        fixture.dispatch();
        assertTrue(fixture.requests.isEmpty());
        fixture.playerView = fixture.foreign;
        assertNull(fixture.actions.findNearestByIds(List.of(100)));
    }

    @Test
    public void queuedObjectUsesItsCurrentScenePositionWhileStillInRange() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectState target = fixture.object(ObstacleObjectType.GAME, 100, 65, 50, 1, 1);
        fixture.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), target.object);
        target.x = 64;
        fixture.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(64, fixture.requests.get(0)[0]);
    }

    @Test
    public void exactObstacleLocationStillUsesTheTwentyTileBoundary() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.object(ObstacleObjectType.GAME, 100, 71, 50, 1, 1);
        fixture.actions.interactObstacleAt(ObstacleObjectType.GAME, 100,
            MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), world(71, 50, 0));
        fixture.dispatch();
        assertTrue(fixture.requests.isEmpty());
        fixture.object(ObstacleObjectType.GAME, 100, 70, 50, 1, 1);
        fixture.actions.interactObstacleAt(ObstacleObjectType.GAME, 100,
            MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), world(70, 50, 0));
        fixture.dispatch();
        assertEquals(1, fixture.requests.size());
    }

    @Test
    public void groundItemSelectionKeepsItsExistingAreaScope() throws Exception
    {
        Fixture fixture = new Fixture();
        TileItem mark = proxy(TileItem.class, (name, args) -> name.equals("getId") ? 11849 : null);
        fixture.markTile = proxy(Tile.class, (name, args) -> {
            switch (name)
            {
                case "getWorldView":
                    return fixture.view;
                case "getGroundItems":
                    return List.of(mark);
                case "getWorldLocation":
                    return world(80, 50, 0);
                case "getLocalLocation":
                    return LocalPoint.fromScene(80, 50);
                case "getGameObjects":
                    return new GameObject[0];
                default:
                    return null;
            }
        });
        assertTrue(fixture.actions.takeNearestGroundItemInArea(11849, new WorldArea(world(79, 49, 0), 3, 3)));
        fixture.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(MenuAction.GROUND_ITEM_THIRD_OPTION, fixture.requests.get(0)[2]);
    }

    private static WorldPoint world(int x, int y, int plane)
    {
        return new WorldPoint(3200 + x, 3200 + y, plane);
    }

    private static final class Fixture
    {
        private final AgilityActions actions = new AgilityActions();
        private final List<Object[]> requests = new ArrayList<>();
        private final List<NPC> npcs = new ArrayList<>();
        private final List<ObjectState> objects = new ArrayList<>();
        private final Thread owner = Thread.currentThread();
        private int tick;
        private int playerX = 50;
        private int playerY = 50;
        private Tile markTile;
        private final WorldView foreign = proxy(WorldView.class, (name, args) -> null);
        private final Scene scene = proxy(Scene.class, (name, args) -> name.equals("getTiles") ? tiles()
            : name.equals("getExtendedTileSettings") ? new byte[4][184][184] : null);
        private final IndexedObjectSet<NPC> npcSet = proxy(IndexedObjectSet.class,
            (name, args) -> name.equals("iterator") ? npcs.iterator() : name.equals("stream") ? npcs.stream() : null);
        private final WorldView view = proxy(WorldView.class, (name, args) -> {
            switch (name)
            {
                case "getSizeX":
                case "getSizeY":
                    return 104;
                case "getTileHeights":
                    return new int[4][105][105];
                case "getTileSettings":
                    return new byte[4][104][104];
                case "getBaseX":
                case "getBaseY":
                    return 3200;
                case "getScene":
                    return scene;
                case "getPlane":
                    return 0;
                case "npcs":
                    return npcSet;
                default:
                    return null;
            }
        });
        private WorldView playerView = view;
        private final Player player = proxy(Player.class, (name, args) -> {
            switch (name)
            {
                case "getWorldLocation":
                    return world(playerX, playerY, 0);
                case "getLocalLocation":
                    return LocalPoint.fromScene(playerX, playerY);
                case "getWorldView":
                    return playerView;
                default:
                    return null;
            }
        });

        private Fixture() throws Exception
        {
            ACTIVE_ACTIONS.add(actions);
            Client client = proxy(Client.class, (name, args) -> {
                switch (name)
                {
                    case "isClientThread":
                        return Thread.currentThread() == owner;
                    case "getTickCount":
                        return ++tick;
                    case "getWorldView":
                        return view;
                    case "getGameState":
                        return net.runelite.api.GameState.LOGGED_IN;
                    case "getBaseX":
                    case "getBaseY":
                        return 3200;
                    case "getTileSettings":
                        return new byte[4][104][104];
                    case "getTileHeights":
                        return new int[4][105][105];
                    case "getCameraX":
                    case "getCameraX2":
                        return 6464;
                    case "getCameraZ":
                    case "getCameraZ2":
                        return -2000;
                    case "getCameraPitch":
                        return 256;
                    case "getScale":
                        return 512;
                    case "getCanvasWidth":
                    case "getCanvasHeight":
                    case "getViewportWidth":
                    case "getViewportHeight":
                        return 1000;
                    case "getLocalPlayer":
                        return player;
                    case "getScene":
                        return scene;
                    case "getObjectDefinition":
                        return proxy(net.runelite.api.ObjectComposition.class, (n, a) ->
                            n.equals("getActions") ? new String[] {"Cross", "Use", "Enter", "Exit", "Climb"}
                            : n.equals("getName") ? "Obstacle" : n.equals("getId") ? (Integer) args[0] : null);
                    case "getItemDefinition":
                        return proxy(net.runelite.api.ItemComposition.class, (n, a) ->
                            n.equals("getName") ? "Mark of grace" : n.equals("getId") ? (Integer) args[0] : null);
                    case "getNpcDefinition":
                        return proxy(net.runelite.api.NPCComposition.class, (n, a) ->
                            n.equals("getActions") ? new String[] {"Cross", "Talk-to", "Pay", "Trade", "Use"}
                            : n.equals("getName") ? "Obstacle" : null);
                    case "getTopLevelWorldView":
                        return view;
                    case "menuAction":
                        requests.add(args.clone());
                        return null;
                    default:
                        return null;
                }
            });
            new MicrobotTestSupport(client, (point, entry) -> requests.add(new Object[] {entry.getParam0(), entry.getParam1(), entry.getType(), entry.getIdentifier(), entry.getItemId(), entry.getOption(), entry.getTarget()}));
            inject(actions, "client", client);
        }

        private void dispatch() throws Exception
        {
            actions.dispatch();
            Field field = AgilityActions.class.getDeclaredField("pending");
            field.setAccessible(true);
            java.util.concurrent.Future<?> pending = (java.util.concurrent.Future<?>) field.get(actions);
            if (pending != null)
            {
                pending.get(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        }

        private Tile[][][] tiles()
        {
            List<Tile> tiles = new ArrayList<>();
            for (ObjectState state : objects)
            {
                tiles.add(proxy(Tile.class, (name, args) -> {
                    switch (name)
                    {
                        case "getSceneLocation":
                            return new Point(state.x, state.y);
                        case "getWorldLocation":
                            return world(state.x, state.y, state.plane);
                        case "getGameObjects":
                            return state.type == ObstacleObjectType.GAME
                                ? new GameObject[] {(GameObject) state.object} : new GameObject[0];
                        case "getGroundObject":
                            return state.type == ObstacleObjectType.GROUND ? state.object : null;
                        case "getWallObject":
                            return state.type == ObstacleObjectType.WALL ? state.object : null;
                        case "getDecorativeObject":
                            return state.type == ObstacleObjectType.DECOR ? state.object : null;
                        default:
                            return null;
                    }
                }));
            }
            if (markTile != null)
            {
                tiles.add(markTile);
            }
            Tile[][][] grid = new Tile[4][104][104];
            for (Tile tile : tiles)
            {
                int x = tile.getWorldLocation().getX() - 3200;
                int y = tile.getWorldLocation().getY() - 3200;
                grid[0][x][y] = tile;
            }
            return grid;
        }

        private NpcState npc(int id, int x, int y)
        {
            NpcState state = new NpcState(id, npcs.size(), x, y, view);
            npcs.add(state.npc);
            return state;
        }

        private ObjectState object(ObstacleObjectType type, int id, int x, int y, int width, int height)
        {
            ObjectState state = new ObjectState(type, id, x, y, width, height, view);
            objects.add(state);
            return state;
        }
    }

    private static final class NpcState
    {
        private final int index;
        private int x;
        private int y;
        private int plane;
        private WorldView view;
        private final NPC npc;

        private NpcState(int id, int index, int x, int y, WorldView view)
        {
            this.index = index;
            this.x = x;
            this.y = y;
            this.view = view;
            npc = proxy(NPC.class, (name, args) -> {
                switch (name)
                {
                    case "getName":
                        return "Obstacle";
                    case "getComposition":
                    case "getTransformedComposition":
                        return proxy(net.runelite.api.NPCComposition.class, (n, a) ->
                            n.equals("getActions") ? new String[] {"Cross", "Talk-to", "Pay", "Trade", "Use"}
                            : n.equals("getName") ? "Obstacle" : null);
                    case "getLocalLocation":
                        return LocalPoint.fromScene(this.x, this.y);
                    case "getId":
                        return id;
                    case "getIndex":
                        return index;
                    case "getWorldLocation":
                        return world(this.x, this.y, plane);
                    case "getWorldView":
                        return this.view;
                    default:
                        return null;
                }
            });
        }
    }

    private static final class ObjectState
    {
        private final ObstacleObjectType type;
        private int x;
        private int y;
        private int plane;
        private WorldView view;
        private final TileObject object;

        private ObjectState(ObstacleObjectType type, int id, int x, int y, int width, int height, WorldView view)
        {
            this.type = type;
            this.x = x;
            this.y = y;
            this.view = view;
            Class<? extends TileObject> objectType = type == ObstacleObjectType.GAME ? GameObject.class
                : type == ObstacleObjectType.GROUND ? GroundObject.class
                : type == ObstacleObjectType.WALL ? WallObject.class : DecorativeObject.class;
            object = proxy(objectType, (name, args) -> {
                switch (name)
                {
                    case "getId":
                        return id;
                    case "getPlane":
                        return plane;
                    case "getWorldView":
                        return this.view;
                    case "getLocalLocation":
                        return LocalPoint.fromScene(this.x + width / 2, this.y + height / 2);
                    case "getWorldLocation":
                        return world(this.x + width / 2, this.y + height / 2, plane);
                    case "getSceneMinLocation":
                        return new Point(this.x, this.y);
                    case "getSceneMaxLocation":
                        return new Point(this.x + width - 1, this.y + height - 1);
                    default:
                        return null;
                }
            });
        }
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) -> {
            if (method.getName().equals("hashCode"))
            {
                return System.identityHashCode(object);
            }
            if (method.getName().equals("equals"))
            {
                return object == args[0];
            }
            Object result = handler.apply(method.getName(), args);
            if (result == null && method.getReturnType() == boolean.class)
            {
                return false;
            }
            if (result == null && method.getReturnType() == int.class)
            {
                return 0;
            }
            return result;
        }));
    }

    private static void inject(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
