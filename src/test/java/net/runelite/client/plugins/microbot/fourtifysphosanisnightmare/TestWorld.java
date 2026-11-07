package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/** A small fake client: one player, one scene tile column, NPCs, objects, items and skill levels. */
public final class TestWorld
{
    public static final int PLAYER_SCENE = 52;
    public WorldPoint position = new WorldPoint(4133, 4382, 0);
    public int sceneX = PLAYER_SCENE;
    public int sceneY = PLAYER_SCENE;
    public int animation = -1;
    /** The worn weapon's combat style buttons and their descriptions, or null for none. */
    public Object[][] styleButtons;
    public boolean dead;
    public boolean playerPresent = true;
    public boolean topLevel = true;
    public boolean widgetSelected;
    public boolean inventoryPresent = true;
    public boolean bankVisible;
    public GameState state = GameState.LOGGED_IN;
    public int gameCycle;
    public int hitpoints = 99;
    public int maxHitpoints = 99;
    public int prayerPoints = 99;
    public int maxPrayer = 99;
    public int energy = 10_000;
    public Item[] inventory = {};
    public Item[] equipment = {};
    public Item[] bank = {};
    public boolean instance;
    /** The scene's collision flags on the player's plane: open floor unless a test blocks a tile. */
    public final int[][] flags = new int[104][104];
    public final Set<Integer> activeVarbits = new HashSet<>();
    public final Map<Integer, Integer> varps = new HashMap<>(Map.of(VarPlayerID.OPTION_RUN, 1));
    public final Map<Skill, Integer> boosts = new HashMap<>();
    public final List<GraphicsObject> graphics = new ArrayList<>();
    public final Set<Integer> spotAnimations = new HashSet<>();
    public final List<NpcTarget> npcs = new ArrayList<>();
    public final List<NPC> otherNpcs = new ArrayList<>();
    public final List<ObjectTarget> objects = new ArrayList<>();
    public final List<DropTile> drops = new ArrayList<>();
    public Object interacting;
    public final List<String> chat = new ArrayList<>();
    /** Camera changes, such as "pitch 3064" or "script 42 0 0". */
    public final List<String> camera = new ArrayList<>();
    private final IndexedObjectSet<NPC> npcSet = proxy(IndexedObjectSet.class, (name, args) ->
    {
        if (name.equals("iterator"))
        {
            List<NPC> all = new ArrayList<>();
            for (NpcTarget target : npcs)
            {
                all.add(target.npc);
            }
            all.addAll(otherNpcs);
            return all.iterator();
        }
        return null;
    });
    public final Scene scene = proxy(Scene.class, (name, args) ->
    {
        if (!name.equals("getTiles"))
        {
            return null;
        }
        List<Tile> tiles = new ArrayList<>();
        tiles.add(proxy(Tile.class, (method, arguments) ->
        {
            if (method.equals("getGameObjects"))
            {
                List<GameObject> found = new ArrayList<>();
                for (ObjectTarget target : objects)
                {
                    found.add(target.object);
                }
                return found.toArray(new GameObject[0]);
            }
            return null;
        }));
        for (DropTile drop : drops)
        {
            tiles.add(drop.tile);
        }
        Tile[][] column = new Tile[1][];
        column[0] = tiles.toArray(new Tile[0]);
        return new Tile[][][] {column};
    });
    public final WorldView view = proxy(WorldView.class, (name, args) ->
    {
        switch (name)
        {
            case "npcs":
                return npcSet;
            case "getScene":
                return scene;
            case "getPlane":
                return position.getPlane();
            case "isInstance":
                return instance;
            case "getCollisionMaps":
                return new CollisionData[] {proxy(CollisionData.class, (method, arguments) -> method.equals("getFlags") ? flags : null)};
            case "getBaseX":
                return position.getX() - sceneX;
            case "getBaseY":
                return position.getY() - sceneY;
            case "getSizeX":
            case "getSizeY":
                return 104;
            case "getGraphicsObjects":
                return proxy(net.runelite.api.Deque.class, (method, arguments) -> method.equals("iterator") ? graphics.iterator() : null);
            default:
                return null;
        }
    });
    public final Player player = proxy(Player.class, (name, args) ->
    {
        switch (name)
        {
            case "getWorldLocation":
                return position;
            case "getLocalLocation":
                return LocalPoint.fromScene(sceneX, sceneY);
            case "getAnimation":
                return animation;
            case "getWorldView":
                return topLevel ? view : null;
            case "isDead":
                return dead;
            case "getInteracting":
                return interacting;
            case "hasSpotAnim":
                return spotAnimations.contains((Integer) args[0]);
            default:
                return null;
        }
    });
    public final Client client = proxy(Client.class, (name, args) ->
    {
        switch (name)
        {
            case "isClientThread":
                return true;
            case "getLocalPlayer":
                return playerPresent ? player : null;
            case "getGameState":
                return state;
            case "getGameCycle":
                return gameCycle;
            case "getTopLevelWorldView":
            case "getWorldView":
                return view;
            case "isWidgetSelected":
                return widgetSelected;
            case "getViewportXOffset":
            case "getViewportYOffset":
                return 4;
            case "getViewportWidth":
                return 765;
            case "getViewportHeight":
                return 503;
            case "setCameraPitchTarget":
                camera.add("pitch " + args[0]);
                return null;
            case "runScript":
                camera.add("script " + Arrays.stream((Object[]) args[0]).map(String::valueOf)
                    .collect(Collectors.joining(" ")));
                return null;
            case "getEnergy":
                return energy;
            case "getBoostedSkillLevel":
                return args[0] == Skill.HITPOINTS ? hitpoints : args[0] == Skill.PRAYER ? prayerPoints
                    : 99 + boosts.getOrDefault((Skill) args[0], 0);
            case "getRealSkillLevel":
                return args[0] == Skill.HITPOINTS ? maxHitpoints : args[0] == Skill.PRAYER ? maxPrayer : 99;
            case "getVarpValue":
                return varps.getOrDefault((Integer) args[0], 0);
            case "getVarbitValue":
                return activeVarbits.contains((Integer) args[0]) ? 1 : 0;
            case "getDBRowsByValue":
                return styleButtons == null ? List.of() : List.of(1);
            case "getDBTableField":
                return styleButtons[(Integer) args[2] == 0 ? 0 : 1];
            case "getItemContainer":
                return container((Integer) args[0]);
            case "getWidget":
                return args.length == 2 && (Integer) args[0] == 12 && (Integer) args[1] == 1 && bankVisible ? shownWidget() : null;
            case "getObjectDefinition":
                return objects.stream().filter(target -> target.id == (Integer) args[0]).map(target -> target.definition)
                    .findFirst().orElse(null);
            case "addChatMessage":
                chat.add((String) args[2]);
                return null;
            case "menuAction":
                throw new AssertionError("Activity bypassed the Microbot input API");
            default:
                return null;
        }
    });

    public NpcTarget npc(int distance)
    {
        return npcAt(new WorldPoint(position.getX() + distance, position.getY(), position.getPlane()));
    }

    public NpcTarget npcAt(WorldPoint location)
    {
        NpcTarget target = new NpcTarget(this, location);
        npcs.add(target);
        return target;
    }

    /** A graphic playing on a tile of the player's row, the given distance east of the player. */
    public void graphic(int id, int distance)
    {
        graphics.add(proxy(GraphicsObject.class, (name, args) ->
        {
            switch (name)
            {
                case "getId":
                    return id;
                case "getLocation":
                    return LocalPoint.fromScene(sceneX + distance, sceneY);
                case "getStartCycle":
                    return gameCycle;
                default:
                    return null;
            }
        }));
    }

    public ObjectTarget object(int id, int minimumDistance, int maximumDistance, String... actions)
    {
        ObjectTarget target = new ObjectTarget(this, id, minimumDistance, maximumDistance, actions);
        objects.add(target);
        return target;
    }

    public DropTile drop(WorldPoint location, int itemId, int quantity, int ownership)
    {
        DropTile drop = new DropTile(location, itemId, quantity, ownership);
        drops.add(drop);
        return drop;
    }

    public static Item[] items(int... idsAndQuantities)
    {
        Item[] result = new Item[idsAndQuantities.length / 2];
        for (int index = 0; index < result.length; index++)
        {
            result[index] = new Item(idsAndQuantities[index * 2], idsAndQuantities[index * 2 + 1]);
        }
        return result;
    }

    private ItemContainer container(int id)
    {
        if (id == 93 && inventoryPresent)
        {
            return proxy(ItemContainer.class, (name, args) -> name.equals("getItems") ? inventory : null);
        }
        if (id == 94)
        {
            return proxy(ItemContainer.class, (name, args) -> name.equals("getItems") ? equipment : null);
        }
        if (id == 95)
        {
            return proxy(ItemContainer.class, (name, args) -> name.equals("getItems") ? bank : null);
        }
        return null;
    }

    private static Widget shownWidget()
    {
        return proxy(Widget.class, (name, args) -> null);
    }

    /** An NPC whose ID, size, position, target and animation can change; the default is one tile. */
    public static final class NpcTarget
    {
        public int id = 9416;
        public int size = 1;
        public String displayName = "The Nightmare";
        public int index = 1;
        public WorldPoint location;
        public boolean topLevel = true;
        public boolean dead;
        public Object interacting;
        public int animation = -1;
        public int healthRatio = -1;
        public int healthScale = -1;
        public int orientation;
        public short[] overhead = {-1, -1};
        public String[] actions = {"Attack"};
        public final NPCComposition composition = proxy(NPCComposition.class,
            (name, args) -> name.equals("getActions") ? actions : null);
        public final NPC npc;

        NpcTarget(TestWorld world, WorldPoint location)
        {
            this.location = location;
            npc = proxy(NPC.class, (name, args) ->
            {
                switch (name)
                {
                    case "getId":
                        return id;
                    case "getIndex":
                        return index;
                    case "getWorldLocation":
                        return this.location;
                    case "getWorldArea":
                        return new WorldArea(this.location, size, size);
                    case "getWorldView":
                        return topLevel ? world.view : null;
                    case "getTransformedComposition":
                    case "getComposition":
                        return composition;
                    case "isDead":
                        return dead;
                    case "getInteracting":
                        return interacting;
                    case "getAnimation":
                        return animation;
                    case "getHealthRatio":
                        return healthRatio;
                    case "getHealthScale":
                        return healthScale;
                    case "getOrientation":
                        return orientation;
                    case "getOverheadSpriteIds":
                        return overhead;
                    case "getName":
                        return displayName;
                    default:
                        return null;
                }
            });
        }
    }

    /** A game object whose footprint lies on the player's row, given as distances from the player. */
    public static final class ObjectTarget
    {
        public int id;
        public int plane;
        public boolean topLevel = true;
        public String[] actions;
        public Point minimum;
        public Point maximum;
        public WorldPoint location;
        public final ObjectComposition definition = proxy(ObjectComposition.class, (name, args) ->
        {
            switch (name)
            {
                case "getActions":
                    return actions;
                case "getName":
                    return "Target";
                default:
                    return null;
            }
        });
        public final GameObject object;

        ObjectTarget(TestWorld world, int id, int minimumDistance, int maximumDistance, String[] actions)
        {
            this.id = id;
            this.actions = actions;
            minimum = new Point(PLAYER_SCENE + minimumDistance, PLAYER_SCENE);
            maximum = new Point(PLAYER_SCENE + maximumDistance, PLAYER_SCENE);
            object = proxy(GameObject.class, (name, args) ->
            {
                switch (name)
                {
                    case "getId":
                        return this.id;
                    case "getPlane":
                        return plane;
                    case "getWorldView":
                        return topLevel ? world.view : null;
                    case "getSceneMinLocation":
                        return minimum;
                    case "getSceneMaxLocation":
                        return maximum;
                    case "getWorldLocation":
                        return location != null ? location : new WorldPoint(
                            world.position.getX() + (minimum.getX() + maximum.getX()) / 2 - world.sceneX,
                            world.position.getY(), plane);
                    default:
                        return null;
                }
            });
        }
    }

    /** One scene tile holding a single ground item. */
    public static final class DropTile
    {
        public final WorldPoint location;
        public final TileItem item;
        public final List<TileItem> items;
        public final Tile tile;

        DropTile(WorldPoint location, int itemId, int quantity, int ownership)
        {
            this.location = location;
            item = proxy(TileItem.class, (name, args) ->
            {
                switch (name)
                {
                    case "getId":
                        return itemId;
                    case "getQuantity":
                        return quantity;
                    case "getOwnership":
                        return ownership;
                    default:
                        return null;
                }
            });
            items = Collections.singletonList(item);
            tile = proxy(Tile.class, (name, args) ->
            {
                switch (name)
                {
                    case "getWorldLocation":
                        return this.location;
                    case "getGroundItems":
                        return items;
                    default:
                        return null;
                }
            });
        }
    }

    /** Runs client-thread work inline; queued work waits for drain(). A hook can change the world first. */
    public static final class QueuedThread extends ClientThread
    {
        public final Deque<Runnable> pending = new ArrayDeque<>();
        public Runnable beforeInline = () -> { };

        @Override
        public void invokeLater(Runnable task)
        {
            pending.addLast(task);
        }

        @Override
        public <T> Optional<T> runOnClientThreadOptional(Callable<T> action)
        {
            Runnable hook = beforeInline;
            beforeInline = () -> { };
            hook.run();
            try
            {
                return Optional.ofNullable(action.call());
            }
            catch (Exception failure)
            {
                throw new IllegalStateException(failure);
            }
        }

        public void runNext()
        {
            pending.removeFirst().run();
        }

        public void drain()
        {
            while (!pending.isEmpty())
            {
                runNext();
            }
        }
    }

    /** Runs each task on the calling thread, so requests finish before dispatch returns. */
    public static final class DirectExecutor extends AbstractExecutorService
    {
        private boolean shutdown;

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> behavior)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, args) ->
        {
            if (method.getName().equals("equals"))
            {
                return instance == args[0];
            }
            if (method.getName().equals("hashCode"))
            {
                return System.identityHashCode(instance);
            }
            if (method.getName().equals("toString"))
            {
                return type.getSimpleName() + "Proxy";
            }
            Object result = behavior.apply(method.getName(), args == null ? new Object[0] : args);
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
