package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtifysagility.course.ObstacleObjectType;
import net.runelite.client.plugins.microbot.fourtifysagility.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Singleton
public final class AgilityActions
{
    public static final int NPC_SEARCH_RADIUS = TargetRange.NPC_SEARCH_RADIUS;
    public static final int GAME_OBJECT_SEARCH_RADIUS = TargetRange.GAME_OBJECT_SEARCH_RADIUS;
    private static final Set<Integer> STAMINA = Set.of(12631, 12629, 12627, 12625);
    private static final Set<Integer> ENERGY = Set.of(12631, 12629, 12627, 12625,
        3022, 3020, 3018, 3016, 3014, 3012, 3010, 3008);
    private static final Map<Integer, List<Integer>> FOOD_TIERS = Map.of(
        1891, List.of(1895, 1893, 1891),
        1897, List.of(1901, 1899, 1897),
        6685, List.of(6691, 6689, 6687, 6685));
    @Inject private Client client;
    private final ActionQueue queue = new ActionQueue();
    private final AtomicBoolean executing = new AtomicBoolean();
    private ExecutorService worker;
    private Future<?> pending;
    private volatile int generation;
    private int runThreshold;
    private volatile int pendingEnergySlot = -1;
    private volatile int pendingEnergyItem;
    private volatile int pendingEnergyTick;

    public void reset()
    {
        queue.clear();
        generation++;
        if (pending != null)
        {
            pending.cancel(true);
            pending = null;
        }
        if (worker != null)
        {
            worker.shutdownNow();
            worker = null;
        }
        runThreshold = 0;
        pendingEnergySlot = -1;
    }

    public void nextTick()
    {
        queue.clear();
    }

    public void dispatch()
    {
        if (!executing.get() && (pending == null || pending.isDone()))
        {
            queue.dispatch();
        }
    }

    public void enqueue(Runnable action)
    {
        queue.offer(action);
    }

    int generation()
    {
        return generation;
    }

    public void enqueueBlocking(BooleanSupplier valid, Runnable operation)
    {
        int version = generation;
        enqueue(() ->
        {
            if (version != generation || !valid.getAsBoolean())
            {
                return;
            }
            if (worker == null)
            {
                worker = Executors.newSingleThreadExecutor(task ->
                {
                    Thread thread = new Thread(task, "fourtifys-agility-actions");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            pending = worker.submit(() ->
            {
                if (!executing.compareAndSet(false, true))
                {
                    return;
                }
                try
                {
                    if (version == generation && !Thread.currentThread().isInterrupted()
                        && Microbot.getClientThread().runOnClientThreadOptional(valid::getAsBoolean).orElse(false)
                        && version == generation)
                    {
                        operation.run();
                    }
                }
                finally
                {
                    executing.set(false);
                }
            });
        });
    }

    public boolean visible(int id)
    {
        Widget widget = client.getWidget(id);
        return widget != null && !widget.isHidden();
    }

    public static List<Integer> foodTiers(int id)
    {
        return FOOD_TIERS.getOrDefault(id, List.of(id));
    }

    public void eat(int id)
    {
        int food = foodTiers(id).stream().filter(Rs2Inventory::contains).findFirst().orElse(id);
        Rs2ItemModel item = Rs2Inventory.get(food);
        if (item != null)
        {
            String option = item.getActionFromList(List.of("Eat", "Drink", "Heal"));
            if (option != null)
            {
                useItem(food, option, () -> true, ignored -> { });
            }
        }
    }

    private void useItem(int id, String option, BooleanSupplier valid, IntConsumer dispatched)
    {
        int slot = Rs2Inventory.slot(id);
        if (slot < 0)
        {
            return;
        }
        int version = generation;
        enqueueBlocking(() -> valid.getAsBoolean() && Rs2Inventory.slotContains(slot, id), () ->
        {
            boolean current = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                if (version != generation || !valid.getAsBoolean() || !Rs2Inventory.slotContains(slot, id))
                {
                    return false;
                }
                dispatched.accept(slot);
                return true;
            }).orElse(false);
            if (current && version == generation && !Thread.currentThread().isInterrupted())
            {
                Rs2Inventory.slotInteract(slot, option);
            }
        });
    }

    public void curePoison()
    {
        int poison = client.getVarpValue(102);
        if (poison >= 1000000 && drinkFirst(12919, 12917, 12915, 12913, 29833, 29830, 29827, 29824))
        {
            return;
        }
        if (poison >= 1000003 || poison > 0 && poison < 1000000)
        {
            drinkFirst(12911, 12909, 12907, 12905, 5958, 5956, 5954, 5952,
                5949, 5947, 5945, 5943, 185, 183, 181, 2448, 179, 177, 175, 2446);
        }
    }

    private boolean drinkFirst(int... ids)
    {
        for (int id : ids)
        {
            if (Rs2Inventory.contains(id))
            {
                useItem(id, "Drink", () -> true, ignored -> { });
                return true;
            }
        }
        return false;
    }

    public boolean isRunEnabled()
    {
        return client.getVarpValue(173) == 1;
    }

    public void toggleRun()
    {
        enqueueBlocking(() -> !isRunEnabled(), () -> Rs2Player.toggleRunEnergy(true));
    }

    public void manageRun(int minimum, int spread)
    {
        if (visible(39911430) && !Rs2Equipment.isWearing(6666) && !Rs2Equipment.isWearing(25557))
        {
            return;
        }
        if (pendingEnergySlot >= 0)
        {
            ItemContainer container = Rs2Inventory.inventory();
            Item[] inventory = container == null ? new Item[0] : container.getItems();
            boolean changed = pendingEnergySlot >= inventory.length || inventory[pendingEnergySlot] == null
                || inventory[pendingEnergySlot].getId() != pendingEnergyItem;
            boolean staminaStarted = STAMINA.contains(pendingEnergyItem) && client.getVarbitValue(25) != 0;
            if (!changed && !staminaStarted && client.getTickCount() - pendingEnergyTick < 8)
            {
                return;
            }
            pendingEnergySlot = -1;
        }
        if (runThreshold < minimum || runThreshold > minimum + spread)
        {
            runThreshold = random(minimum, minimum + random(0, spread));
        }
        int threshold = 15 + random(0, 30);
        if (client.getEnergy() / 100 < threshold && !visible(786432))
        {
            for (Rs2ItemModel item : Rs2Inventory.all())
            {
                if (item != null && ENERGY.contains(item.getId())
                    && (!STAMINA.contains(item.getId()) || client.getVarbitValue(25) == 0))
                {
                    int id = item.getId();
                    useItem(id, "Drink",
                        () -> pendingEnergySlot < 0 && client.getEnergy() / 100 < threshold
                            && !visible(786432) && (!STAMINA.contains(id) || client.getVarbitValue(25) == 0),
                        slot -> {
                            pendingEnergySlot = slot;
                            pendingEnergyItem = id;
                            pendingEnergyTick = client.getTickCount();
                        });
                    return;
                }
            }
        }
        if (client.getEnergy() / 100 > runThreshold || client.getVarbitValue(25) != 0)
        {
            if (!isRunEnabled())
            {
                runThreshold = 0;
                toggleRun();
            }
        }
    }

    private static int random(int minimum, int maximum)
    {
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    public List<TileObject> sceneObjects()
    {
        if (client.getLocalPlayer() == null || client.getTopLevelWorldView() == null
            || client.getTopLevelWorldView().getScene() == null)
        {
            return List.of();
        }
        WorldView view = client.getLocalPlayer().getWorldView();
        return Microbot.getRs2TileObjectCache().getStream().filter(object -> object.getWorldView() == view)
            .map(Rs2TileObjectModel::getTileObject).collect(Collectors.toList());
    }

    private TileObject nearest(ObstacleObjectType type, List<Integer> ids)
    {
        return sceneObjects().stream().filter(object -> ids.contains(object.getId())
            && matches(type, object) && TargetRange.objectDistance(client, object) <= GAME_OBJECT_SEARCH_RADIUS)
            .min(Comparator.comparingInt(object -> TargetRange.objectDistance(client, object))).orElse(null);
    }

    private static boolean matches(ObstacleObjectType type, TileObject object)
    {
        return type == ObstacleObjectType.GAME ? object instanceof GameObject
            : type == ObstacleObjectType.GROUND ? object instanceof GroundObject
            : type == ObstacleObjectType.WALL ? object instanceof WallObject : object instanceof DecorativeObject;
    }

    /** Whether a loaded obstacle of this type and id is within the 20-tile object range on the player's plane. */
    public boolean obstacleInRange(ObstacleObjectType type, int id)
    {
        return nearest(type, List.of(id)) != null;
    }

    public GameObject findNearestByIds(List<Integer> ids)
    {
        return (GameObject) nearest(ObstacleObjectType.GAME, ids);
    }

    public GroundObject findNearestGroundObject(int id)
    {
        return (GroundObject) nearest(ObstacleObjectType.GROUND, List.of(id));
    }

    public NPC findNearest(int id)
    {
        return Rs2Npc.getNpcs(id).map(Rs2NpcModel::getRuneliteNpc)
            .filter(npc -> TargetRange.npcDistance(client, npc) <= NPC_SEARCH_RADIUS)
            .min(Comparator.comparingInt(npc -> TargetRange.npcDistance(client, npc))).orElse(null);
    }

    public void interactObstacle(ObstacleObjectType type, int id, int opcode)
    {
        interactObject(opcode, nearest(type, List.of(id)));
    }

    public void interactObstacleAt(ObstacleObjectType type, int id, int opcode, WorldPoint point)
    {
        if (type == ObstacleObjectType.GAME)
        {
            LocalPoint target = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
            if (target != null)
            {
                sceneObjects().stream().filter(object -> object instanceof GameObject && object.getId() == id
                    && ((GameObject) object).getSceneMinLocation().getX() == target.getSceneX()
                    && ((GameObject) object).getSceneMinLocation().getY() == target.getSceneY())
                    .findFirst().ifPresent(object -> interactObject(opcode, object));
            }
        }
    }

    public void interactObject(int opcode, TileObject object)
    {
        if (TargetRange.objectDistance(client, object) > GAME_OBJECT_SEARCH_RADIUS)
        {
            return;
        }
        int id = object.getId();
        int index = opcode >= MenuAction.GAME_OBJECT_FIRST_OPTION.getId()
            && opcode <= MenuAction.GAME_OBJECT_FOURTH_OPTION.getId()
            ? opcode - MenuAction.GAME_OBJECT_FIRST_OPTION.getId() : 4;
        ObjectComposition definition = Rs2TileObjectModel.compositionOf(id);
        String[] options = definition == null ? null : definition.getActions();
        if (options == null || index >= options.length || options[index] == null)
        {
            return;
        }
        String option = options[index];
        enqueueBlocking(() -> object.getId() == id
            && TargetRange.objectDistance(client, object) <= GAME_OBJECT_SEARCH_RADIUS
            && isLiveObject(object)
            && new Rs2TileObjectModel(object).hasActionContaining(option) && nativeObjectRange(object)
            && !client.isWidgetSelected(),
            () -> ObjectClick.click(object, option));
    }

    private boolean nativeObjectRange(TileObject object)
    {
        LocalPoint player = client.getLocalPlayer().getLocalLocation();
        LocalPoint target = object.getLocalLocation();
        return player != null && target != null && target.isInScene()
            && Math.max(Math.abs(player.getSceneX() - target.getSceneX()),
                Math.abs(player.getSceneY() - target.getSceneY())) <= 51;
    }

    private boolean isLiveObject(TileObject object)
    {
        int x = object instanceof GameObject ? ((GameObject) object).getSceneMinLocation().getX()
            : object.getLocalLocation().getSceneX();
        int y = object instanceof GameObject ? ((GameObject) object).getSceneMinLocation().getY()
            : object.getLocalLocation().getSceneY();
        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
        int plane = object.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
            || x < 0 || x >= tiles[plane].length || tiles[plane][x] == null
            || y < 0 || y >= tiles[plane][x].length || tiles[plane][x][y] == null)
        {
            return false;
        }
        Tile tile = tiles[plane][x][y];
        return object instanceof GameObject ? tile.getGameObjects() != null
            && Arrays.stream(tile.getGameObjects()).anyMatch(live -> live == object)
            : object instanceof GroundObject ? tile.getGroundObject() == object
            : object instanceof WallObject ? tile.getWallObject() == object : tile.getDecorativeObject() == object;
    }

    public void interactNpc(int opcode, NPC npc)
    {
        int index = opcode - MenuAction.NPC_FIRST_OPTION.getId();
        String[] options = npc == null || npc.getTransformedComposition() == null
            ? null : npc.getTransformedComposition().getActions();
        if (TargetRange.npcDistance(client, npc) > NPC_SEARCH_RADIUS || options == null
            || index < 0 || index >= options.length || options[index] == null)
        {
            return;
        }
        String option = options[index];
        int id = npc.getId();
        net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel model =
            new net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel(npc);
        enqueueBlocking(() -> npc.getId() == id && TargetRange.npcDistance(client, npc) <= NPC_SEARCH_RADIUS
            && Rs2Npc.getNpcs(id).anyMatch(live -> live.getRuneliteNpc() == npc)
            && (!Microbot.isCantReachTargetDetectionEnabled || !Microbot.cantReachTarget || model.hasLineOfSight())
            && !client.isWidgetSelected(), () -> model.click(option));
    }

    public boolean takeNearestGroundItemInArea(int id, WorldArea area)
    {
        Rs2TileItemModel item = Microbot.getRs2TileItemCache().getStream().filter(candidate -> candidate.getId() == id
            && candidate.getWorldView() == client.getTopLevelWorldView()
            && candidate.getWorldLocation().getPlane() == client.getLocalPlayer().getWorldLocation().getPlane())
            .min(Comparator.comparingInt(candidate -> candidate.getWorldLocation()
                .distanceTo(client.getLocalPlayer().getWorldLocation()))).orElse(null);
        if (item == null || !area.contains(item.getWorldLocation()))
        {
            return false;
        }
        enqueueBlocking(() -> area.contains(item.getWorldLocation()) && item.getWorldView() == client.getTopLevelWorldView()
            && item.getWorldLocation().getPlane() == client.getLocalPlayer().getWorldLocation().getPlane()
            && item.getTile().getGroundItems() != null
            && item.getTile().getGroundItems().stream().anyMatch(live -> live == item.getTileItem()), item::pickup);
        return true;
    }

    public void continueDialogue(int widgetId, int child)
    {
        Widget widget = client.getWidget(widgetId);
        if (widget == null || widget.isHidden())
        {
            return;
        }
        enqueueBlocking(() -> client.getWidget(widgetId) == widget && !widget.isHidden(), () ->
        {
            if (widgetId == 14352385)
            {
                Rs2Dialogue.keyPressForDialogueOption(child);
            }
            else
            {
                Rs2Dialogue.clickContinue();
            }
        });
    }

    public boolean requestWidget(Widget widget, String option, BooleanSupplier valid, Runnable dispatched)
    {
        String[] options = widget == null ? null : widget.getActions();
        int index = options == null ? -1 : Arrays.asList(options).indexOf(option);
        if (widget == null || widget.isHidden() || index < 0 || index > 9)
        {
            return false;
        }
        int id = widget.getId();
        int slot = widget.getIndex();
        int item = widget.getItemId();
        return queue.offer(() ->
        {
            if (valid.getAsBoolean() && !widget.isHidden() && widget.getId() == id && widget.getIndex() == slot
                && widget.getItemId() == item && widget.getActions() != null
                && Arrays.asList(widget.getActions()).indexOf(option) == index)
            {
                Rs2Widget.clickWidgetFast(widget, slot, index + 1);
                dispatched.run();
            }
        });
    }
}
