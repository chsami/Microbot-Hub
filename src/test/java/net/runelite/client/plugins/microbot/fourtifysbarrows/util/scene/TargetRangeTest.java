package net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsInteractions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.MicrobotTestSupport;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TargetRangeTest
{
    @Test
    public void npcLookupIncludesTwelveTilesAndExcludesThirteen() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget outside = fixture.npc(13);
        assertNull(fixture.actions.brotherNpc());
        NpcTarget boundary = fixture.npc(12);
        assertSame(boundary.npc, fixture.actions.brotherNpc());
        NpcTarget nearer = fixture.npc(7);
        assertSame(nearer.npc, fixture.actions.brotherNpc());
        assertFalse(fixture.interactions.npc(outside.npc, "Talk-to", () -> true, null));
    }

    @Test
    public void npcAtTwelveTilesQueuesThenDispatchesItsNativeAction() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget target = fixture.npc(12);
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
        fixture.actions.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(MenuAction.NPC_FIRST_OPTION, fixture.requests.get(0)[2]);
        assertEquals(target.index, fixture.requests.get(0)[3]);
        assertEquals(1, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void npcLeavingRangeBeforeDispatchIsRejected() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget target = fixture.npc(12);
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        target.distance = 13;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void npcIdentityIndexAndOptionChangesRejectStaleRequests() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget target = fixture.npc(5);
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        fixture.npcs.clear();
        fixture.npc(5);
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        fixture.npcs.add(target.npc);
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        target.index++;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        target.options = new String[] {"Trade"};
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void unavailableTransformedNpcCompositionRejectsTheCurrentAction() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget target = fixture.npc(4);
        assertTrue(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        target.transformedAvailable = false;
        fixture.actions.dispatch();
        assertFalse(fixture.interactions.npc(target.npc, "Talk-to", () -> true, null));
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void gameObjectLookupUsesTheNearestFootprintEdgeAtTwentyTiles() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectTarget outside = fixture.object(21, 21);
        assertNull(fixture.actions.object(outside.id));
        ObjectTarget footprint = fixture.object(20, 26);
        assertTrue(footprint.object.getWorldLocation().distanceTo(fixture.player.getWorldLocation()) > 20);
        assertSame(footprint.object, fixture.actions.object(footprint.id));
        ObjectTarget nearer = fixture.object(9, 9);
        assertSame(nearer.object, fixture.actions.object(nearer.id));
        assertFalse(fixture.interactions.object(outside.object, "Use", () -> true, null));
    }

    @Test
    public void gameObjectAtTwentyTilesQueuesThenDispatchesItsNativeAction() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectTarget target = fixture.object(20, 20);
        assertTrue(fixture.interactions.object(target.object, "Use", () -> true, null));
        assertTrue(fixture.requests.isEmpty());
        fixture.actions.dispatch();
        assertEquals(1, fixture.requests.size());
        Object[] request = fixture.requests.get(0);
        assertEquals(target.minimum.getX(), request[0]);
        assertEquals(target.minimum.getY(), request[1]);
        assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION, request[2]);
        assertEquals(target.id, request[3]);
        assertEquals(1, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void gameObjectLeavingRangeBeforeDispatchIsRejected() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectTarget target = fixture.object(20, 20);
        assertTrue(fixture.interactions.object(target.object, "Use", () -> true, null));
        fixture.playerX = 49;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void gameObjectIdentityIdAndMenuChangesRejectStaleRequests() throws Exception
    {
        Fixture fixture = new Fixture();
        ObjectTarget target = fixture.object(4, 4);
        assertTrue(fixture.interactions.object(target.object, "Use", () -> true, null));
        fixture.objects.clear();
        fixture.object(4, 4);
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        fixture.objects.add(target.object);
        assertTrue(fixture.interactions.object(target.object, "Use", () -> true, null));
        target.id++;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertTrue(fixture.interactions.object(target.object, "Use", () -> true, null));
        target.options = new String[] {"Examine"};
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void foreignWorldViewsAndOtherPlanesAreExcludedFromBothLookups() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget npc = fixture.npc(1);
        ObjectTarget object = fixture.object(1, 1);
        npc.topLevel = false;
        object.topLevel = false;
        assertNull(fixture.actions.brotherNpc());
        assertNull(fixture.actions.object(object.id));
        assertFalse(fixture.interactions.npc(npc.npc, "Talk-to", () -> true, null));
        assertFalse(fixture.interactions.object(object.object, "Use", () -> true, null));
        npc.topLevel = true;
        object.topLevel = true;
        npc.plane = 1;
        object.plane = 1;
        assertNull(fixture.actions.brotherNpc());
        assertNull(fixture.actions.object(object.id));
    }

    @Test
    public void pendingTargetsAreRecheckedAfterWorldViewOrLoginChanges() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget npc = fixture.npc(1);
        ObjectTarget object = fixture.object(1, 1);
        assertTrue(fixture.interactions.npc(npc.npc, "Talk-to", () -> true, null));
        npc.topLevel = false;
        fixture.actions.dispatch();
        assertTrue(fixture.interactions.object(object.object, "Use", () -> true, null));
        fixture.state = GameState.LOGIN_SCREEN;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void newHelpersKeepOneDispatchPerPollAndExpirationAtTheNextTick() throws Exception
    {
        Fixture fixture = new Fixture();
        NpcTarget npc = fixture.npc(12);
        ObjectTarget object = fixture.object(20, 20);
        assertTrue(fixture.interactions.npc(npc.npc, "Talk-to", () -> true, null));
        assertTrue(fixture.interactions.object(object.object, "Use", () -> true, null));
        fixture.actions.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(MenuAction.NPC_FIRST_OPTION, fixture.requests.get(0)[2]);
        fixture.actions.nextTick();
        fixture.actions.dispatch();
        assertEquals(1, fixture.requests.size());
        assertEquals(1, fixture.actions.getRequestsDispatched());
    }

    private static final class Fixture
    {
        final BarrowsActions actions = new BarrowsActions();
        Client client;
        BarrowsInteractions interactions;
        final List<Object[]> requests = new ArrayList<>();
        final List<NPC> npcs = new ArrayList<>();
        final List<GameObject> objects = new ArrayList<>();
        final List<ObjectTarget> objectTargets = new ArrayList<>();
        int playerX = 50;
        GameState state = GameState.LOGGED_IN;
        final IndexedObjectSet<NPC> npcSet = proxy(IndexedObjectSet.class,
            (name, args) -> name.equals("iterator") ? npcs.iterator() : null);
        final Scene scene = proxy(Scene.class, (name, args) -> {
            if (!name.equals("getTiles"))
            {
                return null;
            }
            Tile tile = proxy(Tile.class,
                (method, arguments) -> method.equals("getGameObjects") ? objects.toArray(new GameObject[0]) : null);
            return new Tile[][][] {{{tile}}};
        });
        final WorldView view = proxy(WorldView.class, (name, args) -> {
            switch (name)
            {
                case "npcs": return npcSet;
                case "getScene": return scene;
                case "getPlane": return 0;
                default: return null;
            }
        });
        final Player player = proxy(Player.class, (name, args) -> {
            switch (name)
            {
                case "getWorldLocation": return new WorldPoint(3150 + playerX, 3200, 0);
                case "getLocalLocation": return LocalPoint.fromScene(playerX, 50);
                case "getWorldView": return view;
                default: return null;
            }
        });

        Fixture() throws Exception
        {
            client = proxy(Client.class, (name, args) -> {
                switch (name)
                {
                    case "getLocalPlayer": return player;
                    case "getTopLevelWorldView": return view;
                    case "getGameState": return state;
                    case "getNpcs": return npcs;
                    case "getObjectDefinition":
                        int id = (Integer) args[0];
                        return objectTargets.stream().filter(target -> target.id == id)
                            .map(target -> target.definition).findFirst().orElse(null);
                    case "menuAction":
                        requests.add(args.clone());
                        return null;
                    default: return null;
                }
            });
            MicrobotTestSupport.attach(actions, client);
            Field field = BarrowsActions.class.getDeclaredField("interactions");
            field.setAccessible(true);
            interactions = (BarrowsInteractions) field.get(actions);
        }

        NpcTarget npc(int distance)
        {
            NpcTarget target = new NpcTarget(this, distance);
            npcs.add(target.npc);
            return target;
        }

        ObjectTarget object(int minimumDistance, int maximumDistance)
        {
            ObjectTarget target = new ObjectTarget(this, minimumDistance, maximumDistance);
            objectTargets.add(target);
            objects.add(target.object);
            return target;
        }
    }

    private static final class NpcTarget
    {
        int id = 1000;
        int index = 7;
        int distance;
        int plane;
        boolean topLevel = true;
        boolean transformedAvailable = true;
        String[] options = {"Talk-to"};
        final NPCComposition definition = proxy(NPCComposition.class,
            (name, args) -> name.equals("getActions") ? options : null);
        final NPC npc;

        NpcTarget(Fixture fixture, int distance)
        {
            this.distance = distance;
            npc = proxy(NPC.class, (name, args) -> {
                switch (name)
                {
                    case "getId": return id;
                    case "getIndex": return index;
                    case "getWorldLocation": return new WorldPoint(3200 + this.distance, 3200, plane);
                    case "getWorldView": return topLevel ? fixture.view : null;
                    case "getComposition": return definition;
                    case "getTransformedComposition": return transformedAvailable ? definition : null;
                    case "getName": return "Dharok the Wretched";
                    default: return null;
                }
            });
        }
    }

    private static final class ObjectTarget
    {
        int id = 2000;
        int plane;
        boolean topLevel = true;
        String[] options = {"Use"};
        final Point minimum;
        final Point maximum;
        final ObjectComposition definition = proxy(ObjectComposition.class, (name, args) -> {
            switch (name)
            {
                case "getActions": return options;
                case "getName": return "Target";
                default: return null;
            }
        });
        final GameObject object;

        ObjectTarget(Fixture fixture, int minimumDistance, int maximumDistance)
        {
            minimum = new Point(50 + minimumDistance, 50);
            maximum = new Point(50 + maximumDistance, 50);
            object = proxy(GameObject.class, (name, args) -> {
                switch (name)
                {
                    case "getId": return id;
                    case "getPlane": return plane;
                    case "getWorldView": return topLevel ? fixture.view : null;
                    case "getSceneMinLocation": return minimum;
                    case "getSceneMaxLocation": return maximum;
                    case "getWorldLocation":
                        return new WorldPoint(3150 + (minimum.getX() + maximum.getX()) / 2, 3200, plane);
                    default: return null;
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> behavior)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, args) -> {
            if (method.getName().equals("equals"))
            {
                return instance == args[0];
            }
            if (method.getName().equals("hashCode"))
            {
                return System.identityHashCode(instance);
            }
            Object result = behavior.apply(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive())
            {
                return result;
            }
            if (method.getReturnType() == boolean.class)
            {
                return false;
            }
            return 0;
        });
    }
}
