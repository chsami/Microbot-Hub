package net.runelite.client.plugins.microbot.fourtifysgemmining;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.Callable;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.client.callback.ClientThread;
import java.lang.reflect.Constructor;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import net.runelite.client.eventbus.EventBus;
import java.awt.Rectangle;
import java.awt.Canvas;
import net.runelite.client.plugins.PluginManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;

/** Microbot API boundary fixture; selectors and menu dispatch use the real helper. */
public final class TestWorld
{
    public Item[] inventory = {new Item(1265, 1)};
    public Item[] equipment = {};
    public WorldPoint position = new WorldPoint(2824, 3000, 0);
    public int animation = -1;
    public int level = 75;
    public int questState = 2;
    public int diary = 2;
    public boolean members = true;
    public final Canvas canvas = new Canvas();
    public int gameCycle;
    private int sceneRevision;
    public boolean dead;
    public boolean playerPresent = true;
    public boolean inventoryPresent = true;
    public boolean scenePresent = true;
    public boolean mineAnchorPresent = true;
    public boolean playerTilePresent = true;
    public WorldPoint mineAnchorLocationOverride;
    public WorldPoint playerTileLocationOverride;
    public boolean topLevel = true;
    public GameState state = GameState.LOGGED_IN;
    public Consumer<MenuEntry> onMenuRequest = entry -> { };
    public final List<Object[]> requests = new ArrayList<>();
    public final List<GameObject> objects = new ArrayList<>();
    public final List<NPC> npcs = new ArrayList<>();
    public final List<Player> players = new ArrayList<>();
    public final Map<Integer, NPCComposition> npcDefinitions = new HashMap<>();
    public final Map<Integer, ObjectComposition> definitions = new HashMap<>();
    public final Map<Integer, Widget> widgets = new HashMap<>();
    public Widget[] roots = {};
    public final Scene scene = proxy(Scene.class, (name, args) -> {
        switch (name)
        {
            case "getBaseX":
                return position.getX() - 52;
            case "getBaseY":
                return position.getY() - 52;
            case "getTiles":
                return sceneTiles();
            case "getTileHeights":
                return new int[4][105][105];
            case "getTileSettings":
                return new byte[4][104][104];
            default:
                return null;
        }
    });
    private final IndexedObjectSet<NPC> npcSet = proxy(IndexedObjectSet.class, (name, args) -> {
        switch (name)
        {
            case "iterator":
                return npcs.iterator();
            case "stream":
                return npcs.stream();
            case "byIndex":
                return npcs.stream().filter(npc -> npc.getIndex() == (Integer) args[0]).findFirst().orElse(null);
            default:
                return null;
        }
    });
    public final WorldView view = proxy(WorldView.class, (name, args) -> {
        switch (name)
        {
            case "getScene":
                return scenePresent ? scene : null;
            case "getTileHeights":
                return new int[4][105][105];
            case "getTileSettings":
                return new byte[4][104][104];
            case "getPlane":
                return position.getPlane();
            case "getBaseX":
                return position.getX() - 52;
            case "getBaseY":
                return position.getY() - 52;
            case "isTopLevel":
                return true;
            case "getId":
                return WorldView.TOPLEVEL;
            case "npcs":
                return npcSet;
            default:
                return null;
        }
    });
    public final Player player = proxy(Player.class, (name, args) -> {
        switch (name)
        {
            case "getWorldLocation":
                return position;
            case "getLocalLocation":
                return LocalPoint.fromScene(52, 52);
            case "getAnimation":
                return animation;
            case "getWorldView":
                return topLevel ? view : null;
            case "isDead":
                return dead;
            default:
                return null;
        }
    });
    public final Client client = proxy(Client.class, (name, args) -> {
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
            case "getTickCount":
                return ++sceneRevision;
            case "getTopLevelWorldView":
            case "getWorldView":
                return view;
            case "getBaseX":
                return position.getX() - 52;
            case "getBaseY":
                return position.getY() - 52;
            case "getPlane":
                return position.getPlane();
            case "getScene":
                return scene;
            case "getCanvas":
                return canvas;
            case "getVarcIntValue":
                return 3;
            case "getViewportWidth":
            case "getViewportHeight":
            case "getCanvasWidth":
            case "getCanvasHeight":
                return 100000;
            case "getScale":
                return 512;
            case "getCameraPitch":
                return 256;
            case "getCameraY":
            case "getCameraZ":
                return -10000;
            case "getTileHeights":
                return new int[4][105][105];
            case "getTileSettings":
                return new byte[4][104][104];
            case "getNpcDefinition":
                return npcDefinitions.get((Integer) args[0]);
            case "getItemContainer":
                int id = args[0] instanceof InventoryID ? ((InventoryID) args[0]).getId() : (Integer) args[0];
                if (id == 93 && inventoryPresent)
                {
                    return proxy(ItemContainer.class, (n, a) -> n.equals("getItems") ? inventory : null);
                }
                if (id == 94)
                {
                    return proxy(ItemContainer.class, (n, a) -> n.equals("getItems") ? equipment : null);
                }
                return null;
            case "getObjectDefinition":
                return definitions.get((Integer) args[0]);
            case "getItemDefinition":
                int itemId = (Integer) args[0];
                if (itemId == 12020 || itemId == 24481)
                {
                    return proxy(ItemComposition.class, (n, a) -> {
                        switch (n)
                        {
                            case "getId":
                                return itemId;
                            case "getName":
                                return itemId == 12020 ? "Gem bag" : "Open gem bag";
                            case "getInventoryActions":
                                return new String[] {"Fill", itemId == 12020 ? "Open" : "Close", "Check", "Empty", "Destroy"};
                            default:
                                return null;
                        }
                    });
                }
                return proxy(ItemComposition.class, (n, a) -> {
                    switch (n)
                    {
                        case "getId":
                            return itemId;
                        case "getName":
                            return "Item " + itemId;
                        case "getInventoryActions":
                            return new String[5];
                        default:
                            return null;
                    }
                });
            case "getWidget":
                int widgetId = args.length == 1 ? (Integer) args[0] : ((Integer) args[0] << 16) | (Integer) args[1];
                Widget direct = widgets.get(widgetId);
                if (direct != null)
                {
                    return direct;
                }
                for (Widget root : roots)
                {
                    Widget found = findWidget(root, widgetId);
                    if (found != null)
                    {
                        return found;
                    }
                }
                return null;
            case "getWidgetRoots":
                return roots;
            case "getWorldType":
                return members ? EnumSet.of(WorldType.MEMBERS) : EnumSet.noneOf(WorldType.class);
            case "getBoostedSkillLevel":
                return level;
            case "getIntStack":
                return new int[] {questState};
            case "getVarbitValue":
                return (Integer) args[0] == 3599 ? diary : 0;
            case "getNpcs":
                return npcs;
            case "getPlayers":
                return players;
            case "menuAction":
                throw new AssertionError("Activity bypassed the Microbot input API");
            default:
                return null;
        }
    });
    public final GemMiningActions actions;
    public boolean deferNativeWork;
    public final Deque<Runnable> nativeWork = new ArrayDeque<>();

    public TestWorld()
    {
        try
        {
            Field clientField = Microbot.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(null, client);
            Field mouseField = Microbot.class.getDeclaredField("mouse");
            mouseField.setAccessible(true);
            mouseField.set(null, new ClickLogMouse());
            Field threadField = Microbot.class.getDeclaredField("clientThread");
            threadField.setAccessible(true);
            threadField.set(null, new ClientThread()
            {
                @Override
                public <T> Optional<T> runOnClientThreadOptional(Callable<T> action)
                {
                    try
                    {
                        return Optional.ofNullable(action.call());
                    }
                    catch (Exception exception)
                    {
                        throw new IllegalStateException(exception);
                    }
                }

                @Override
                public <T> T invoke(Supplier<T> action)
                {
                    return action.get();
                }
            });
            ClientThread sdkThread = (ClientThread) threadField.get(null);
            Field boundClient = ClientThread.class.getDeclaredField("client");
            boundClient.setAccessible(true);
            boundClient.set(sdkThread, client);
            Constructor<ConfigProfile> profile = ConfigProfile.class.getDeclaredConstructor(long.class);
            profile.setAccessible(true);
            ConfigManager manager = PluginTestFixture.configManager();
            Field profileField = ConfigManager.class.getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(manager, profile.newInstance(-1L));
            Field managerField = Microbot.class.getDeclaredField("configManager");
            managerField.setAccessible(true);
            managerField.set(null, manager);
            Constructor<?> pluginManagerConstructor = PluginManager.class.getDeclaredConstructors()[0];
            pluginManagerConstructor.setAccessible(true);
            Field pluginsField = Microbot.class.getDeclaredField("pluginManager");
            pluginsField.setAccessible(true);
            pluginsField.set(null, pluginManagerConstructor.newInstance(false, new EventBus(), null, manager, null, null));
            Microbot.getWorldViewIds().clear();
            Microbot.getWorldViewIds().add(WorldView.TOPLEVEL);
            Field stateField = Microbot.class.getDeclaredField("rs2PlayerStateCache");
            stateField.setAccessible(true);
            stateField.set(null, new Rs2PlayerStateCache(new EventBus(), client, sdkThread));
            Field objectsField = Microbot.class.getDeclaredField("rs2TileObjectCache");
            objectsField.setAccessible(true);
            objectsField.set(null, new Rs2TileObjectCache(client, sdkThread));
            actions = new GemMiningActions(client, sdkThread, NativeExecutor::new);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    public void runNativeWork()
    {
        while (!nativeWork.isEmpty())
        {
            nativeWork.removeFirst().run();
        }
    }

    private final class NativeExecutor extends AbstractExecutorService
    {
        private boolean stopped;

        @Override
        public void shutdown()
        {
            stopped = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            stopped = true;
            return List.of();
        }

        @Override
        public boolean isShutdown()
        {
            return stopped;
        }

        @Override
        public boolean isTerminated()
        {
            return stopped;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return stopped;
        }

        @Override
        public void execute(Runnable task)
        {
            if (deferNativeWork)
            {
                nativeWork.addLast(task);
            }
            else
            {
                task.run();
                try
                {
                    ((java.util.concurrent.Future<?>) task).get();
                }
                catch (Exception exception)
                {
                    throw new IllegalStateException(exception);
                }
            }
        }
    }

    private final class ClickLogMouse extends VirtualMouse
    {
        private ClickLogMouse()
        {
            shutdown();
            getTimer().stop();
        }

        @Override
        public Mouse quickClick(Point point, NewMenuEntry entry)
        {
            requests.add(new Object[] {entry.getParam0(), entry.getParam1(), entry.getType(),
                entry.getIdentifier(), entry.getItemId(), entry.getOption(), entry.getTarget()});
            onMenuRequest.accept(entry);
            return this;
        }

        @Override
        public Mouse click(Rectangle bounds)
        {
            for (int group : new int[] {12, 192})
            {
                Widget frame = client.getWidget(group, group == 12 ? 2 : 1);
                Widget close = frame == null ? null : frame.getChild(11);
                if (close != null && bounds.equals(close.getBounds()))
                {
                    requests.add(new Object[] {11, close.getId(), net.runelite.api.MenuAction.CC_OP,
                        1, -1, "Close", ""});
                    return this;
                }
            }
            throw new AssertionError("Unexpected physical Microbot API widget click: " + bounds);
        }

        @Override
        public Mouse click(Point point, NewMenuEntry entry)
        {
            return quickClick(point, entry);
        }
    }

    private Tile[][][] sceneTiles()
    {
        int plane = position.getPlane();
        Tile[][][] tiles = new Tile[4][][];
        if (plane < 0 || plane >= tiles.length)
        {
            return tiles;
        }
        tiles[plane] = new Tile[104][104];
        Map<WorldPoint, List<GameObject>> onTile = new LinkedHashMap<>();
        if (playerTilePresent)
        {
            onTile.put(position, new ArrayList<>());
        }
        if (mineAnchorPresent)
        {
            for (GemMine mine : GemMine.values())
            {
                WorldPoint anchor = mine.getMineDestination();
                if (playerTilePresent || !position.equals(anchor))
                {
                    onTile.putIfAbsent(anchor, new ArrayList<>());
                }
            }
        }
        for (GameObject object : objects)
        {
            WorldPoint point = object.getWorldLocation();
            boolean missingAnchor = !mineAnchorPresent && Arrays.stream(GemMine.values())
                .anyMatch(mine -> mine.getMineDestination().equals(point));
            if (!missingAnchor && (playerTilePresent || !position.equals(point)))
            {
                onTile.computeIfAbsent(point, ignored -> new ArrayList<>()).add(object);
            }
        }
        int baseX = position.getX() - 52;
        int baseY = position.getY() - 52;
        for (Map.Entry<WorldPoint, List<GameObject>> entry : onTile.entrySet())
        {
            WorldPoint point = entry.getKey();
            int x = point.getX() - baseX;
            int y = point.getY() - baseY;
            if (point.getPlane() != plane || x < 0 || x >= 104 || y < 0 || y >= 104)
            {
                continue;
            }
            boolean anchor = Arrays.stream(GemMine.values())
                .anyMatch(mine -> mine.getMineDestination().equals(point));
            WorldPoint reported = position.equals(point) && playerTileLocationOverride != null ? playerTileLocationOverride
                : anchor && mineAnchorLocationOverride != null ? mineAnchorLocationOverride : point;
            List<GameObject> tileObjects = entry.getValue();
            tiles[plane][x][y] = proxy(Tile.class, (name, args) -> {
                switch (name)
                {
                    case "getWorldLocation":
                        return reported;
                    case "getSceneLocation":
                        return tileObjects.isEmpty() ? new Point(x, y) : tileObjects.get(0).getSceneMinLocation();
                    case "getPlane":
                        return reported.getPlane();
                    case "getGameObjects":
                        return tileObjects.toArray(new GameObject[0]);
                    default:
                        return null;
                }
            });
        }
        return tiles;
    }

    public GameObject rock(int x, int y)
    {
        return object(11381, "Gem rocks", new String[] {"Mine"}, new WorldPoint(x, y, position.getPlane()));
    }

    public void otherPlayer(WorldPoint point)
    {
        players.add(proxy(Player.class, (name, args) -> name.equals("getWorldLocation") ? point : null));
    }

    public GameObject object(int id, String name, String[] options, WorldPoint point)
    {
        definitions.put(id, proxy(ObjectComposition.class, (n, a) -> n.equals("getActions") ? options : n.equals("getName") ? name : null));
        GameObject object = proxy(GameObject.class, (n, a) -> {
            switch (n)
            {
                case "getId":
                    return id;
                case "getWorldView":
                    return view;
                case "getPlane":
                    return point.getPlane();
                case "getWorldLocation":
                    return point;
                case "getLocalLocation":
                    return LocalPoint.fromScene(point.getX() - (position.getX() - 52), point.getY() - (position.getY() - 52));
                case "sizeX":
                case "sizeY":
                    return 1;
                case "getSceneMinLocation":
                case "getSceneMaxLocation":
                    return new Point(point.getX() - (position.getX() - 52), point.getY() - (position.getY() - 52));
                default:
                    return null;
            }
        });
        objects.add(object);
        return object;
    }

    public NPC banker(int index, WorldPoint point)
    {
        NPCComposition composition = proxy(NPCComposition.class, (name, args) -> {
            switch (name)
            {
                case "getActions":
                    return new String[] {"Talk-to", "Bank"};
                case "getName":
                    return "Banker";
                default:
                    return null;
            }
        });
        npcDefinitions.put(100, composition);
        NPC npc = proxy(NPC.class, (name, args) -> {
            switch (name)
            {
                case "getIndex":
                    return index;
                case "getId":
                    return 100;
                case "getWorldLocation":
                    return point;
                case "getLocalLocation":
                    return LocalPoint.fromScene(point.getX() - (position.getX() - 52), point.getY() - (position.getY() - 52));
                case "getWorldView":
                    return view;
                case "getComposition":
                case "getTransformedComposition":
                    return composition;
                case "getName":
                    return "Banker";
                default:
                    return null;
            }
        });
        npcs.add(npc);
        return npc;
    }

    public Widget widget(int id, int slot, int item, String[] options, Widget[] children)
    {
        return proxy(Widget.class, (name, args) -> {
            switch (name)
            {
                case "getId":
                    return id;
                case "getIndex":
                    return slot;
                case "getItemId":
                    return item;
                case "getActions":
                    return options;
                case "getName":
                    return "Item";
                case "getBounds":
                    return new Rectangle((id & 65535) * 40, Math.max(0, slot) * 40, 32, 32);
                case "getDynamicChildren":
                case "getChildren":
                    return children;
                case "getChild":
                    int child = (Integer) args[0];
                    return children != null && child >= 0 && child < children.length ? children[child] : null;
                default:
                    return null;
            }
        });
    }

    private Widget[] bankRoots(int group)
    {
        int frameChild = group == 12 ? 2 : 1;
        Widget[] buttons = new Widget[12];
        buttons[11] = widget((group << 16) | frameChild, 11, -1, new String[] {"Close"}, null);
        Widget frame = widget((group << 16) | frameChild, -1, -1, null, buttons);
        Widget[] children = group == 12 ? new Widget[] {widget((group << 16) | 1, -1, -1, null, null), frame}
            : new Widget[] {frame};
        return new Widget[] {widget(group << 16, -1, -1, null, children)};
    }

    public void bankWithGems()
    {
        widgets.remove(149 << 16);
        int id = (15 << 16) | 3;
        Widget[] children = new Widget[inventory.length];
        for (int slot = 0; slot < inventory.length; slot++)
        {
            int itemId = inventory[slot].getId();
            String[] options = itemId == 12020 || itemId == 24481
                ? new String[] {"Deposit-1", "Deposit-5", "Deposit-10", "Deposit-All", null, null, null, null, "Empty", "Examine"}
                : new String[] {"Deposit-1", "Deposit-5", "Deposit-10", "Deposit-All"};
            children[slot] = widget(id, slot, itemId, options, null);
        }
        widgets.put(id, widget(id, -1, -1, null, children));
        roots = bankRoots(12);
    }

    public void depositBoxWithGems()
    {
        widgets.remove(149 << 16);
        widgets.remove((12 << 16) | 1);
        int id = (192 << 16) | 24;
        Widget[] children = new Widget[inventory.length];
        for (int slot = 0; slot < inventory.length; slot++)
        {
            int itemId = inventory[slot].getId();
            String[] options = new String[] {"Deposit-1", "Deposit-1", "Deposit-5", "Deposit-10", "Deposit-X", "Deposit-All",
                null, null, itemId == 12020 || itemId == 24481 ? "Empty" : null, "Examine"};
            children[slot] = widget(id, slot, itemId, options, null);
        }
        widgets.put(id, widget(id, -1, -1, null, children));
        roots = bankRoots(192);
    }

    public void inventoryWidgets()
    {
        int id = 149 << 16;
        Widget[] children = new Widget[inventory.length];
        for (int slot = 0; slot < inventory.length; slot++)
        {
            int itemId = inventory[slot].getId();
            String[] options = itemId == 12020 || itemId == 24481
                ? new String[] {null, "Fill", itemId == 12020 ? "Open" : "Close", "Check", null, "Empty", "Destroy", null, null, "Examine"}
                : null;
            children[slot] = widget(id, slot, itemId, options, null);
        }
        widgets.put(id, widget(id, -1, -1, null, children));
    }

    public void setBagOpen(boolean open)
    {
        for (int slot = 0; slot < inventory.length; slot++)
        {
            if (inventory[slot].getId() == 12020 || inventory[slot].getId() == 24481)
            {
                inventory[slot] = new Item(open ? 24481 : 12020, 1);
                inventoryWidgets();
                return;
            }
        }
        throw new IllegalStateException("No gem bag in inventory");
    }

    public void closeBanking()
    {
        widgets.remove((12 << 16) | 1);
        widgets.remove((15 << 16) | 3);
        widgets.remove((192 << 16) | 24);
        roots = new Widget[0];
        inventoryWidgets();
    }

    public void fullInventory()
    {
        inventory = new Item[28];
        Arrays.fill(inventory, new Item(1625, 1));
        inventory[0] = new Item(1265, 1);
    }

    private static Widget findWidget(Widget widget, int id)
    {
        if (widget == null || widget.getId() == id)
        {
            return widget;
        }
        Widget[] children = widget.getDynamicChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                Widget found = findWidget(child, id);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> behavior)
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
            if (method.getName().equals("toString"))
            {
                return type.getSimpleName() + " fixture";
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
            if (method.getReturnType() == long.class)
            {
                return 0L;
            }
            if (method.getReturnType() == double.class)
            {
                return 0d;
            }
            if (method.getReturnType() == float.class)
            {
                return 0f;
            }
            return 0;
        });
    }
}
