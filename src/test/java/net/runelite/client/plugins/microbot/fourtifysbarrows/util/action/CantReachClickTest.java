package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scene clicks while the client's can't-reach flag is set must never start the Microbot API's recovery walk. */
public class CantReachClickTest
{
    private static final String[] FIELDS = {"client", "clientThread", "mouse", "pluginManager"};
    private static final WorldPoint PLAYER = new WorldPoint(3565, 3288, 0);
    // Camera yaw in client units that already faces a target on the player's own tile, so no camera key is pressed.
    private static final int FACING_OWN_TILE = 270 * 2048 / 360 * 8;
    private static final Map<Class<?>, Object> ZERO = Map.of(boolean.class, false, int.class, 0, long.class, 0L,
        double.class, 0.0, float.class, 0f, short.class, (short) 0, byte.class, (byte) 0, char.class, (char) 0);

    private final List<NewMenuEntry> clicks = new ArrayList<>();
    private final Object[] previous = new Object[FIELDS.length];
    private boolean detection;
    private boolean latched;
    private int retries;
    private WorldView view;
    private Player player;
    private Client client;
    private VirtualMouse mouse;

    @BeforeEach
    public void attach() throws Exception
    {
        detection = Microbot.isCantReachTargetDetectionEnabled;
        latched = Microbot.cantReachTarget;
        retries = Microbot.cantReachTargetRetries;
        Microbot.isCantReachTargetDetectionEnabled = true;
        Microbot.cantReachTarget = true;
        Scene scene = proxy(Scene.class, Map.of("getTiles", new Tile[4][104][104], "getExtendedTileSettings",
            new byte[4][184][184], "getTileHeights", new int[4][105][105], "getTileSettings", new byte[4][104][104]));
        view = proxy(WorldView.class, Map.of("getId", -1, "getSizeX", 104, "getSizeY", 104, "getScene", scene,
            "worldEntities", proxy(IndexedObjectSet.class, Map.of()), "getTileSettings", new byte[4][104][104],
            "getTileHeights", new int[4][105][105]));
        player = proxy(Player.class, Map.of("getWorldLocation", PLAYER, "getWorldView", view));
        client = proxy(Client.class, Map.of("getTopLevelWorldView", view, "getWorldView", view, "getLocalPlayer", player,
            "getViewportWidth", 765, "getViewportHeight", 503, "getCameraYaw", FACING_OWN_TILE, "getTileSettings", new byte[4][104][104],
            "getTileHeights", new int[4][105][105], "getObjectDefinition",
            proxy(ObjectComposition.class, Map.of("getActions", new String[] {"Open", null, null, null, null},
                "getName", "Chest")),
            "getNpcDefinition", proxy(NPCComposition.class, Map.of("getActions",
                new String[] {null, "Attack", null, null, null}, "getName", "Dharok the Wretched"))));
        ClientThread thread = new ClientThread()
        {
            @Override
            public <T> T invoke(Supplier<T> operation)
            {
                return operation.get();
            }

            @Override
            public void invoke(Runnable operation)
            {
                operation.run();
            }

            @Override
            public <T> Optional<T> runOnClientThreadOptional(Callable<T> operation)
            {
                try
                {
                    return Optional.ofNullable(operation.call());
                }
                catch (Exception exception)
                {
                    throw new AssertionError(exception);
                }
            }

            @Override
            public <T> Optional<T> runOnClientThreadOptional(Callable<T> operation, long timeout)
            {
                return runOnClientThreadOptional(operation);
            }
        };
        Field threadClient = ClientThread.class.getDeclaredField("client");
        threadClient.setAccessible(true);
        threadClient.set(thread, client);
        mouse = new VirtualMouse()
        {
            @Override
            public VirtualMouse click(Point point, NewMenuEntry entry)
            {
                clicks.add(entry);
                return this;
            }

            @Override
            public VirtualMouse quickClick(Point point, NewMenuEntry entry)
            {
                clicks.add(entry);
                return this;
            }
        };
        Constructor<?> plugins = PluginManager.class.getDeclaredConstructors()[0];
        plugins.setAccessible(true);
        Object[] values = {client, thread, mouse, plugins.newInstance(false, new EventBus(), null, null, null, null)};
        for (int index = 0; index < FIELDS.length; index++)
        {
            previous[index] = field(FIELDS[index], values[index]);
        }
    }

    @AfterEach
    public void restore() throws Exception
    {
        for (int index = 0; index < FIELDS.length; index++)
        {
            field(FIELDS[index], previous[index]);
        }
        mouse.shutdown();
        Microbot.isCantReachTargetDetectionEnabled = detection;
        Microbot.cantReachTarget = latched;
        Microbot.cantReachTargetRetries = retries;
    }

    @Test
    public void anObjectClickIsSentWithoutAWalkWhileTheFlagIsSet()
    {
        GameObject chest = proxy(GameObject.class, Map.of("getId", 20973, "getWorldView", view,
            "getWorldLocation", PLAYER,
            "getLocalLocation", LocalPoint.fromScene(52, 52, view), "sizeX", 1, "sizeY", 1));
        assertTrue(new MicrobotApi().object(chest, "Open"));
        assertEquals(1, clicks.size());
        assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), clicks.get(0).getType().getId());
        assertEquals(20973, clicks.get(0).getIdentifier());
        assertTrue(Microbot.cantReachTarget, "a recovery walk would have cleared the flag");
    }

    @Test
    public void anNpcOutOfSightIsNotClickedWhileTheFlagIsSet()
    {
        NPC brother = npc(new WorldPoint(3575, 3298, 0));
        assertFalse(new MicrobotApi().npc(brother, "Attack"));
        assertTrue(clicks.isEmpty());
        assertTrue(Microbot.cantReachTarget);
        assertEquals(0, Microbot.cantReachTargetRetries);
    }

    @Test
    public void anNpcInSightIsClickedAndClearsTheFlagWithoutAWalk()
    {
        NPC brother = npc(PLAYER);
        assertTrue(new MicrobotApi().npc(brother, "Attack"));
        assertEquals(1, clicks.size());
        assertEquals(MenuAction.NPC_SECOND_OPTION.getId(), clicks.get(0).getType().getId());
        assertFalse(Microbot.cantReachTarget);
        assertEquals(0, Microbot.cantReachTargetRetries);
    }

    private NPC npc(WorldPoint location)
    {
        return proxy(NPC.class, Map.of("getId", 1673, "getIndex", 7, "getWorldLocation", location, "getWorldView", view,
            "getLocalLocation", LocalPoint.fromScene(52, 52, view), "getName", "Dharok the Wretched",
            "getComposition", client.getNpcDefinition(1673), "getTransformedComposition", client.getNpcDefinition(1673)));
    }

    private static Object field(String name, Object value) throws ReflectiveOperationException
    {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object old = field.get(null);
        field.set(null, value);
        return old;
    }

    /** A stub that answers the named methods and gives the type's zero value for every other call. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> answers)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) ->
        {
            if (answers.containsKey(method.getName()))
            {
                return answers.get(method.getName());
            }
            switch (method.getName())
            {
                case "equals":
                    return self == args[0];
                case "hashCode":
                    return System.identityHashCode(self);
                case "toString":
                    return type.getSimpleName();
                default:
                    break;
            }
            return ZERO.get(method.getReturnType());
        });
    }
}
