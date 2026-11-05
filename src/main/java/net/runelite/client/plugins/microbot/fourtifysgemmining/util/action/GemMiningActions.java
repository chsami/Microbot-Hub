package net.runelite.client.plugins.microbot.fourtifysgemmining.util.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Singleton
public class GemMiningActions
{
    private static final Set<Integer> GEMS = Set.of(1625, 1627, 1629, 1623, 1621, 1619, 1617);
    private static final Set<Integer> BAG_GEMS = Set.of(1623, 1621, 1619, 1617, 1631);
    private static final int GEM_BAG = 12020;
    private static final int OPEN_GEM_BAG = 24481;
    private static final WorldPoint SURFACE_BANK = new WorldPoint(2852, 2951, 0);
    private final Client client;
    private final ClientThread clientThread;
    private final Supplier<ExecutorService> workers;
    private final AtomicBoolean executing = new AtomicBoolean();
    private volatile int generation;
    private int snapshotCycle;
    private FutureTask<Void> pending;
    private ExecutorService worker;
    private final ActionQueue queue = new ActionQueue();
    private long dispatchedRequests;
    private final List<BagRequest> bagRequests = new ArrayList<>();

    private static final class BagRequest
    {
        private final int slot;
        private final int itemId;
        private final int operation;
        private final String option;
        private final int expires;

        private BagRequest(int slot, int itemId, int operation, String option, int expires)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.operation = operation;
            this.option = option;
            this.expires = expires;
        }

        private int slot()
        {
            return slot;
        }

        private int itemId()
        {
            return itemId;
        }

        private int operation()
        {
            return operation;
        }

        private String option()
        {
            return option;
        }

        private int expires()
        {
            return expires;
        }
    }

    @Inject
    public GemMiningActions(Client client, ClientThread clientThread)
    {
        this(client, clientThread, () -> Executors.newSingleThreadExecutor(action ->
        {
            Thread thread = new Thread(action, "fourtify-gem-interaction");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public GemMiningActions(Client client, ClientThread clientThread, Supplier<ExecutorService> workers)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.workers = workers;
    }

    public void reset()
    {
        queue.clear();
        bagRequests.clear();
        generation++;
        if (pending != null)
        {
            pending.cancel(true);
        }
        if (worker != null)
        {
            worker.shutdownNow();
        }
    }

    public void nextTick()
    {
        // Requests belong to the scene and inventory snapshot that produced them.
        queue.clear();
        snapshotCycle = client.getGameCycle();
        bagRequests.removeIf(request -> client.getGameCycle() >= request.expires());
    }

    boolean enqueue(Runnable action)
    {
        return queue.offerAtPlayerPosition(client, action);
    }

    public void dispatch()
    {
        queue.dispatch();
    }

    public long getRequestsDispatched()
    {
        return dispatchedRequests;
    }

    public Item[] items()
    {
        ItemContainer inventory = Rs2Inventory.inventory();
        return inventory == null ? new Item[0] : inventory.getItems();
    }

    public int gemCount()
    {
        return Arrays.stream(items()).filter(item -> item != null && GEMS.contains(item.getId()))
                .mapToInt(Item::getQuantity).sum();
    }

    public boolean hasGemBag()
    {
        return Arrays.stream(items()).anyMatch(item -> item != null && isGemBag(item.getId()));
    }

    public boolean gemBagOpen()
    {
        return Arrays.stream(items()).anyMatch(item -> item != null && item.getId() == OPEN_GEM_BAG);
    }

    public int baggableGemCount()
    {
        return Arrays.stream(items()).filter(item -> item != null && BAG_GEMS.contains(item.getId()))
            .mapToInt(Item::getQuantity).sum();
    }

    public boolean inventoryBagAction(String option, Runnable onDispatched)
    {
        return itemAction(149 << 16, GemMiningActions::isGemBag, option,
            () -> canUseInventoryBag(option), () ->
            {
                rememberBagRequest(option);
                if (onDispatched != null)
                {
                    onDispatched.run();
                }
            });
    }

    private void rememberBagRequest(String option)
    {
        Item[] items = items();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot] != null && isGemBag(items[slot].getId()))
            {
                Widget parent = client.getWidget(149 << 16);
                Widget child = parent == null ? null : parent.getChild(slot);
                int index = child == null ? -1 : indexOf(child.getActions(), option);
                if (index >= 0)
                {
                    bagRequests.add(new BagRequest(slot, items[slot].getId(), index + 1, option,
                        client.getGameCycle() + 29));
                }
                return;
            }
        }
    }

    public boolean consumeBagRequest(MenuOptionClicked event)
    {
        bagRequests.removeIf(request -> client.getGameCycle() >= request.expires());
        for (int index = 0; index < bagRequests.size(); index++)
        {
            BagRequest request = bagRequests.get(index);
            MenuAction action = MenuAction.CC_OP;
            if (event.getParam1() == (149 << 16) && event.getParam0() == request.slot()
                && event.getItemId() == request.itemId() && event.getId() == request.operation()
                && event.getMenuAction() == action && request.option().equals(event.getMenuOption()))
            {
                bagRequests.remove(index);
                return true;
            }
        }
        return false;
    }

    public boolean emptyGemBag(Runnable onDispatched)
    {
        return itemAction(bankInventoryId(), GemMiningActions::isGemBag, "Empty",
            () -> bankingOpen() && gemCount() == 0, onDispatched);
    }

    private boolean canUseInventoryBag(String option)
    {
        Player player = client.getLocalPlayer();
        return client.getGameState() == GameState.LOGGED_IN && player != null && !player.isDead()
            && player.getWorldView() == client.getTopLevelWorldView() && !bankingOpen()
            && hasGemBag() && (!"Open".equals(option) || !gemBagOpen())
            && (!"Fill".equals(option) || baggableGemCount() > 0);
    }

    private static boolean isGemBag(int id)
    {
        return id == GEM_BAG || id == OPEN_GEM_BAG;
    }

    public int freeSlots()
    {
        return 28 - (int) Arrays.stream(items()).limit(28).filter(item -> item != null && item.getId() > 0).count();
    }

    public boolean hasPickaxe()
    {
        ItemContainer equipment = Rs2Equipment.equipment();
        return Arrays.stream(items()).anyMatch(this::usablePickaxe)
            || equipment != null && Arrays.stream(equipment.getItems()).anyMatch(this::usablePickaxe);
    }

    public String prerequisite(GemMine mine)
    {
        if (!client.getWorldType().contains(WorldType.MEMBERS))
        {
            return "Shilo Village is members-only. Switch to a members world";
        }
        if (client.getBoostedSkillLevel(Skill.MINING) < 40)
        {
            return "Requires Mining level 40";
        }
        if (Quest.SHILO_VILLAGE.getState(client) != QuestState.FINISHED)
        {
            return "Requires the Shilo Village quest";
        }
        if (mine.isUnderground() && client.getVarbitValue(3599) < 2)
        {
            return "Underground mine needs the medium Karamja Diary. Set Mine to Surface or complete the diary";
        }
        if (!hasPickaxe())
        {
            return "Wear or carry a pickaxe you can use";
        }
        return null;
    }

    public List<GameObject> rocks(GemMine mine)
    {
        List<GameObject> result = loadedRocks(mine);
        result.removeIf(object -> !withinObjectRange(object));
        return result;
    }

    private List<GameObject> loadedRocks(GemMine mine)
    {
        List<GameObject> result = new ArrayList<>();
        for (GameObject object : sceneObjects())
        {
            if ((object.getId() == 11380 || object.getId() == 11381)
                    && mine.contains(object.getWorldLocation()) && hasAction(object, "Mine")
                    && objectDistance(object) != Integer.MAX_VALUE)
            {
                result.add(object);
            }
        }
        result.sort(Comparator.comparingInt(this::objectDistance));
        return result;
    }

    public WorldPoint rockDestination(GemMine mine)
    {
        return loadedRocks(mine).stream().map(GameObject::getWorldLocation).findFirst().orElse(null);
    }

    public boolean sceneLoaded()
    {
        Player player = client.getLocalPlayer();
        return player != null && sceneTileLoaded(player.getWorldLocation());
    }

    public boolean mineSceneLoaded(GemMine mine)
    {
        return sceneTileLoaded(mine.getMineDestination());
    }

    private Tile sceneTile(WorldPoint point)
    {
        WorldView view = client.getTopLevelWorldView();
        if (point == null || view == null || view.getPlane() != point.getPlane() || view.getScene() == null)
        {
            return null;
        }
        Scene scene = view.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = point.getPlane();
        int x = point.getX() - view.getBaseX();
        int y = point.getY() - view.getBaseY();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
            || x < 0 || x >= tiles[plane].length || tiles[plane][x] == null
            || y < 0 || y >= tiles[plane][x].length)
        {
            return null;
        }
        Tile tile = tiles[plane][x][y];
        return tile != null && point.equals(tile.getWorldLocation()) ? tile : null;
    }

    private boolean sceneTileLoaded(WorldPoint point)
    {
        return sceneTile(point) != null;
    }

    private boolean liveObject(GameObject object)
    {
        Tile tile = sceneTile(object.getWorldLocation());
        return tile != null && tile.getGameObjects() != null
            && Arrays.stream(tile.getGameObjects()).anyMatch(candidate -> candidate == object);
    }

    public GameObject nearestRock(GemMine mine)
    {
        return rocks(mine).stream().findFirst().orElse(null);
    }

    public WorldPoint bankDestination(GemMine mine)
    {
        GameObject object = sceneObjects().stream()
            .filter(candidate -> objectDistance(candidate) != Integer.MAX_VALUE && eligibleBankObject(mine, candidate))
            .min(Comparator.comparingInt(this::objectDistance)).orElse(null);
        if (object != null)
        {
            return object.getWorldLocation();
        }
        if (!mine.isUnderground())
        {
            return client.getNpcs().stream().filter(npc -> bankActionIndex(npc, false) >= 0)
                .min(Comparator.comparingInt(npc -> TargetRange.npcDistance(client, npc)))
                .map(NPC::getWorldLocation).orElse(null);
        }
        return null;
    }

    public GameObject nearestAvailableRock(GemMine mine)
    {
        return rocks(mine).stream().filter(object -> !occupied(object)).findFirst().orElse(null);
    }

    private boolean occupied(GameObject object)
    {
        WorldPoint point = object.getWorldLocation();
        Player local = client.getLocalPlayer();
        return client.getPlayers().stream().anyMatch(player -> player != null && player != local
            && player.getWorldLocation() != null && player.getWorldLocation().distanceTo(point) <= 1);
    }

    public boolean mine(GemMine mine, GameObject object, Runnable onDispatched)
    {
        if (!canMine(mine, object))
        {
            return false;
        }
        return objectAction(object, "Mine", () -> canMine(mine, object), onDispatched);
    }

    private boolean canMine(GemMine mine, GameObject object)
    {
        Player player = client.getLocalPlayer();
        return object != null && (object.getId() == 11380 || object.getId() == 11381)
                && player != null && player.getAnimation() == -1 && freeSlots() > 0 && !bankingOpen()
                && mine.contains(object.getWorldLocation()) && !occupied(object)
                && prerequisite(mine) == null;
    }

    public boolean bankingOpen()
    {
        // Rs2Bank.isOpen() enters a bank PIN itself, and its waits cannot work on the client thread.
        return Rs2DepositBox.isOpen() || Rs2Widget.isWidgetVisible(12, 1);
    }

    public boolean bankPinOpen()
    {
        return Rs2Bank.isBankPinWidgetVisible();
    }

    /** Enters the PIN saved in the Microbot profile from the action worker. */
    public boolean enterBankPin()
    {
        return request(this::bankPinOpen, Rs2Bank::handleBankPin, null);
    }

    public boolean bankInRange(GemMine mine)
    {
        return bankObject(mine) != null || !mine.isUnderground() && banker() != null;
    }

    public boolean openBank(GemMine mine, Runnable onDispatched)
    {
        if (bankingOpen())
        {
            return true;
        }
        GameObject object = bankObject(mine);
        if (object != null)
        {
            String option = hasAction(object, "Bank") ? "Bank" : "Deposit";
            return objectAction(object, option, () -> !bankingOpen(), onDispatched);
        }
        NPC npc = mine.isUnderground() ? null : banker();
        if (npc == null)
        {
            return false;
        }
        int index = bankActionIndex(npc);
        Rs2NpcModel banker = new Rs2NpcModel(npc);
        return request(() -> (!Microbot.isCantReachTargetDetectionEnabled || !Microbot.cantReachTarget || banker.hasLineOfSight()) && !bankingOpen()
            && client.getNpcs().contains(npc) && bankActionIndex(npc) == index,
            () -> (!Microbot.isCantReachTargetDetectionEnabled || !Microbot.cantReachTarget || banker.hasLineOfSight()) && banker.click("Bank"), onDispatched);
    }

    private GameObject bankObject(GemMine mine)
    {
        if (client.getLocalPlayer() == null)
        {
            return null;
        }
        for (GameObject object : sceneObjects())
        {
            if (!withinObjectRange(object))
            {
                continue;
            }
            if (eligibleBankObject(mine, object))
            {
                return object;
            }
        }
        return null;
    }

    private boolean eligibleBankObject(GemMine mine, GameObject object)
    {
        if (mine.isUnderground())
        {
            return object.getId() == 10530 && mine.contains(object.getWorldLocation())
                && hasAction(object, "Deposit");
        }
        ObjectComposition definition = client.getObjectDefinition(object.getId());
        return object.getWorldLocation().distanceTo(SURFACE_BANK) <= 10 && definition != null
            && definition.getName() != null && definition.getName().startsWith("Bank")
            && (hasAction(object, "Bank") || hasAction(object, "Deposit"));
    }

    private NPC banker()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return null;
        }
        return client.getNpcs().stream().filter(npc -> bankActionIndex(npc) >= 0)
            .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(player.getWorldLocation())))
            .orElse(null);
    }

    private int bankActionIndex(NPC npc)
    {
        return bankActionIndex(npc, true);
    }

    private int bankActionIndex(NPC npc, boolean requireRange)
    {
        Player player = client.getLocalPlayer();
        if (player == null || npc.getComposition() == null
                || npc.getWorldView() != client.getTopLevelWorldView()
                || TargetRange.npcDistance(client, npc) == Integer.MAX_VALUE
                || requireRange && TargetRange.npcDistance(client, npc) > TargetRange.NPC_SEARCH_RADIUS
                || npc.getWorldLocation().distanceTo(SURFACE_BANK) > 10)
        {
            return -1;
        }
        return indexOf(npc.getComposition().getActions(), "Bank");
    }

    public boolean depositGem(Runnable onDispatched)
    {
        return itemAction(bankInventoryId(), GEMS::contains, "Deposit-All", () -> bankingOpen(), onDispatched);
    }

    public boolean closeBank(Runnable onDispatched)
    {
        if (!bankingOpen())
        {
            return true;
        }
        boolean depositBox = Rs2DepositBox.isOpen();
        int group = depositBox ? 192 : 12;
        int frameChild = depositBox ? 1 : 2;
        Widget frame = Rs2Widget.getWidget(group, frameChild);
        Widget close = frame == null ? null : frame.getChild(11);
        if (close == null || close.isHidden())
        {
            return false;
        }
        return request(() -> bankingOpen() && gemCount() == 0 && !close.isHidden()
            && Rs2Widget.getWidget(group, frameChild) == frame && frame.getChild(11) == close,
            () -> depositBox ? Rs2DepositBox.closeDepositBox() : Rs2Bank.closeBank(), onDispatched);
    }

    private boolean objectAction(GameObject object, String option, BooleanSupplier valid, Runnable onDispatched)
    {
        if (!valid.getAsBoolean() || !ready() || !withinObjectRange(object)
            || !sceneObjects().contains(object) || !hasAction(object, option))
        {
            return false;
        }
        int id = object.getId();
        Point minimum = object.getSceneMinLocation();
        Point maximum = object.getSceneMaxLocation();
        WorldPoint location = object.getWorldLocation();
        if (minimum == null || maximum == null || location == null)
        {
            return false;
        }
        return request(() -> valid.getAsBoolean() && object.getId() == id
            && minimum.equals(object.getSceneMinLocation()) && maximum.equals(object.getSceneMaxLocation())
            && location.equals(object.getWorldLocation()) && withinObjectRange(object) && withinNativeRange(object)
            && liveObject(object) && hasAction(object, option),
            () -> ObjectClick.click(object, option), onDispatched);
    }

    private boolean withinNativeRange(GameObject object)
    {
        LocalPoint player = client.getLocalPlayer().getLocalLocation();
        LocalPoint target = object.getLocalLocation();
        return player != null && target != null && target.isInScene()
            && Math.max(Math.abs(player.getSceneX() - target.getSceneX()),
                Math.abs(player.getSceneY() - target.getSceneY())) <= 51;
    }

    private boolean withinObjectRange(GameObject object)
    {
        return objectDistance(object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS;
    }

    private int objectDistance(GameObject object)
    {
        return TargetRange.objectDistance(client, object);
    }

    private List<GameObject> sceneObjects()
    {
        Player player = client.getLocalPlayer();
        if (player == null || client.getTopLevelWorldView() == null)
        {
            return List.of();
        }
        WorldView view = player.getWorldView();
        return Microbot.getRs2TileObjectCache().getStream().filter(object -> object.getWorldView() == view)
            .map(Rs2TileObjectModel::getTileObject).filter(GameObject.class::isInstance).map(GameObject.class::cast)
            .distinct().sorted(Comparator.comparingInt(this::objectDistance)).collect(Collectors.toUnmodifiableList());
    }

    private static boolean hasAction(GameObject object, String option)
    {
        return new Rs2TileObjectModel(object).hasActionContaining(option);
    }

    private static int indexOf(String[] actions, String option)
    {
        if (actions != null)
        {
            for (int index = 0; index < actions.length; index++)
            {
                if (option.equalsIgnoreCase(actions[index]))
                {
                    return index;
                }
            }
        }
        return -1;
    }

    public boolean isBusy()
    {
        return executing.get() || pending != null && !pending.isDone();
    }

    private boolean request(BooleanSupplier valid, BooleanSupplier operation, Runnable callback)
    {
        if (isBusy() || !ready() || !valid.getAsBoolean())
        {
            return false;
        }
        return enqueue(() ->
        {
            if (isBusy() || !ready() || !valid.getAsBoolean())
            {
                return;
            }
            int expectedGeneration = generation;
            int expires = snapshotCycle + 29;
            WorldPoint origin = client.getLocalPlayer().getWorldLocation();
            worker = workers.get();
            pending = new FutureTask<>(() ->
            {
                if (!executing.compareAndSet(false, true))
                {
                    return;
                }
                try
                {
                    boolean current = clientThread.runOnClientThreadOptional(() ->
                    {
                        if (generation != expectedGeneration || client.getGameCycle() >= expires || !ready()
                            || !origin.equals(client.getLocalPlayer().getWorldLocation()) || !valid.getAsBoolean())
                        {
                            return false;
                        }
                        // Observation starts before native input can reply.
                        dispatched(callback);
                        return true;
                    }).orElse(false);
                    if (current && generation == expectedGeneration && !Thread.currentThread().isInterrupted())
                    {
                        operation.getAsBoolean();
                    }
                }
                finally
                {
                    executing.set(false);
                }
            }, null);
            worker.execute(pending);
            worker.shutdown();
        });
    }

    private boolean ready()
    {
        Player player = client.getLocalPlayer();
        return client.getGameState() == GameState.LOGGED_IN && player != null && !player.isDead()
            && !client.isWidgetSelected() && player.getWorldView() == client.getTopLevelWorldView();
    }

    private void dispatched(Runnable callback)
    {
        dispatchedRequests++;
        if (callback != null)
        {
            callback.run();
        }
    }

    private int bankInventoryId()
    {
        return Rs2DepositBox.isOpen() ? (192 << 16) | 24 : (15 << 16) | 3;
    }

    private static int actionIndex(Widget widget, String option)
    {
        return widget == null || widget.isHidden() ? -1 : indexOf(widget.getActions(), option);
    }

    private boolean itemAction(int parentId, IntPredicate eligible, String option, BooleanSupplier valid, Runnable callback)
    {
        Widget parent = Rs2Widget.getWidget(parentId);
        if (!ready() || !valid.getAsBoolean() || parent == null || parent.isHidden() || parent.getChildren() == null)
        {
            return false;
        }
        for (Widget child : parent.getChildren())
        {
            if (validItem(parent, child, eligible) && actionIndex(child, option) >= 0)
            {
                int slot = child.getIndex();
                int id = child.getItemId();
                int index = actionIndex(child, option);
                Rs2ItemModel item = new Rs2ItemModel(items()[slot], client.getItemDefinition(id), slot);
                return request(() -> valid.getAsBoolean() && validItem(parent, child, eligible)
                    && child.getItemId() == id && child.getIndex() == slot && actionIndex(child, option) == index,
                    () -> Rs2Inventory.interact(item, option), callback);
            }
        }
        return false;
    }

    private boolean validItem(Widget parent, Widget child, IntPredicate eligible)
    {
        if (parent.isHidden() || Rs2Widget.getWidget(parent.getId()) != parent || child == null || child.isHidden()
            || !eligible.test(child.getItemId()) || parent.getChildren() == null
            || Arrays.stream(parent.getChildren()).noneMatch(candidate -> candidate == child))
        {
            return false;
        }
        int slot = child.getIndex();
        Item[] items = items();
        return slot >= 0 && slot < items.length && items[slot] != null && items[slot].getId() == child.getItemId();
    }

    private boolean usablePickaxe(Item item)
    {
        if (item == null)
        {
            return false;
        }
        int level = pickaxeLevel(item.getId());
        return level > 0 && client.getBoostedSkillLevel(Skill.MINING) >= level
            && (level != 71 || Quest.SONG_OF_THE_ELVES.getState(client) == QuestState.FINISHED);
    }

    /** The Mining level a pickaxe needs; 0 when the item is not a pickaxe. */
    private static int pickaxeLevel(int id)
    {
        switch (id)
        {
            case 1265:
            case 1267:
                return 1;
            case 1269:
                return 6;
            case 12297:
                return 11;
            case 1273:
                return 21;
            case 1271:
                return 31;
            case 1275:
            case 23276:
                return 41;
            case 11920:
            case 23677:
            case 25376:
            case 13243:
            case 25063:
            case 30345:
            case 13244:
            case 25369:
            case 30346:
                return 61;
            case 23680:
            case 23863:
            case 23682:
                return 71;
            default:
                return 0;
        }
    }
}
