package net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action;

import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.GroundDrop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * The session's queued requests and the Microbot calls they make. Each request is checked when it is
 * queued, again when it is dispatched, and once more on the client thread before the worker runs it.
 * A sent request is not proof of success; tasks observe the result on later ticks.
 */
@Singleton
public class DemonActions
{
    static final int INVENTORY = 93;
    static final int EQUIPMENT = 94;
    static final int BANK = 95;
    private static final int WITHDRAW_WAIT_MILLIS = 3000;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private final Supplier<ExecutorService> workers;
    private final AtomicBoolean executing = new AtomicBoolean();
    private final ActionQueue queue = new ActionQueue();
    private final Map<Integer, Long> prices = new HashMap<>();
    private volatile int generation;
    private int snapshotCycle;
    private FutureTask<Void> pending;
    private ExecutorService worker;
    private long dispatchedRequests;
    private String failure;

    @Inject
    public DemonActions(Client client, ClientThread clientThread, ItemManager itemManager)
    {
        this(client, clientThread, itemManager, () -> Executors.newSingleThreadExecutor(action ->
        {
            Thread thread = new Thread(action, "fourtify-demon-interaction");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DemonActions(Client client, ClientThread clientThread, ItemManager itemManager, Supplier<ExecutorService> workers)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
        this.workers = workers;
    }

    public void reset()
    {
        queue.clear();
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

    public void newSession()
    {
        reset();
        failure = null;
        prices.clear();
    }

    public void nextTick()
    {
        // Requests belong to the scene and inventory snapshot that produced them.
        queue.clear();
        snapshotCycle = client.getGameCycle();
    }

    public void dispatch()
    {
        queue.dispatch();
    }

    public long getRequestsDispatched()
    {
        return dispatchedRequests;
    }

    public void fail(String reason)
    {
        if (failure == null)
        {
            failure = reason;
        }
        reset();
    }

    public String getFailure()
    {
        return failure;
    }

    public boolean isBusy()
    {
        return executing.get() || pending != null && !pending.isDone();
    }

    public Player player()
    {
        return client.getLocalPlayer();
    }

    public WorldPoint position()
    {
        Player player = client.getLocalPlayer();
        return player == null ? null : player.getWorldLocation();
    }

    public int hitpoints()
    {
        return client.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    public int prayerPoints()
    {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }

    public int maxHitpoints()
    {
        return client.getRealSkillLevel(Skill.HITPOINTS);
    }

    public int maxPrayer()
    {
        return client.getRealSkillLevel(Skill.PRAYER);
    }

    /** Reads the prayer's varbit on the client thread. */
    public boolean prayerActive(Rs2PrayerEnum prayer)
    {
        return prayer != null && client.getVarbitValue(prayer.getVarbit()) == 1;
    }

    /** Full Hitpoints, Prayer and run energy, as the Pool of Refreshment leaves them. */
    public boolean restored()
    {
        return hitpoints() >= maxHitpoints() && prayerPoints() >= maxPrayer() && client.getEnergy() >= 10_000;
    }

    public boolean stackable(int id)
    {
        return itemManager != null && itemManager.getItemComposition(id) != null && itemManager.getItemComposition(id).isStackable();
    }

    /** True when {@code current} is at or below {@code percent} of {@code maximum}. */
    public static boolean atOrBelow(int current, int percent, int maximum)
    {
        return current * 100 <= percent * maximum;
    }

    public Item[] items()
    {
        return items(INVENTORY);
    }

    private Item[] items(int container)
    {
        ItemContainer items = client.getItemContainer(container);
        return items == null ? new Item[0] : items.getItems();
    }

    public int count(int id)
    {
        return count(items(), Set.of(id));
    }

    public int count(Collection<Integer> ids)
    {
        return count(items(), ids);
    }

    public int bankCount(int id)
    {
        return count(items(BANK), Set.of(id));
    }

    static int count(Item[] items, Collection<Integer> ids)
    {
        long total = 0;
        for (Item item : items)
        {
            if (item != null && ids.contains(item.getId()))
            {
                total += Math.max(0, item.getQuantity());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int freeSlots()
    {
        Item[] items = items();
        int used = 0;
        for (Item item : items)
        {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0)
            {
                used++;
            }
        }
        return Math.max(0, 28 - used);
    }

    public boolean wearing(int id)
    {
        return count(items(EQUIPMENT), Set.of(id)) > 0;
    }

    public boolean inventoryLoaded()
    {
        return client.getItemContainer(INVENTORY) != null;
    }

    public boolean bankOpen()
    {
        Widget frame = client.getWidget(12, 1);
        return frame != null && !frame.isHidden() && client.getItemContainer(BANK) != null;
    }

    /** The bank PIN keypad, which opening the bank shows first on an account with a PIN. */
    public boolean bankPinShown()
    {
        Widget keypad = client.getWidget(213, 0);
        return keypad != null && !keypad.isHidden();
    }

    /** The first ring of dueling worn or carried, or -1. */
    public int duelingRing()
    {
        for (int id : TempleData.DUELING_RINGS)
        {
            if (wearing(id) || count(id) > 0)
            {
                return id;
            }
        }
        return -1;
    }

    /** Grand Exchange guide price per item, remembered for the session. Read on the client thread; 0 is unknown. */
    public long price(int id)
    {
        Long known = prices.get(id);
        if (known != null)
        {
            return known;
        }
        long value = itemManager == null ? 0 : Math.max(0, itemManager.getItemPrice(id));
        prices.put(id, value);
        return value;
    }

    public String itemName(int id)
    {
        return itemManager == null || itemManager.getItemComposition(id) == null ? null : itemManager.getItemComposition(id).getName();
    }

    /** The item's name and ID for player messages, such as "Death rune (560)", or "item 560" when the name is unknown. */
    public String describe(int id)
    {
        String name = itemName(id);
        return name == null || name.isBlank() ? "item " + id : name + " (" + id + ")";
    }

    /** Parses item IDs separated by commas, skipping empty entries. The message of a rejection explains the format. */
    public static Set<Integer> ids(String text)
    {
        Set<Integer> result = new LinkedHashSet<>();
        for (String entry : entries(text))
        {
            int id = positive(entry);
            if (id <= 0)
            {
                throw new IllegalArgumentException("\"" + entry + "\" is not an item ID; use whole numbers separated by commas, like 4675,9185");
            }
            result.add(id);
        }
        return result;
    }

    /** Parses itemID:quantity pairs separated by commas, skipping empty entries. */
    public static Map<Integer, Integer> restock(String text)
    {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (String entry : entries(text))
        {
            String[] parts = entry.split(":", -1);
            int id = parts.length == 2 ? positive(parts[0]) : -1;
            int amount = parts.length == 2 ? positive(parts[1]) : -1;
            if (id <= 0 || amount <= 0)
            {
                throw new IllegalArgumentException("\"" + entry + "\" is not an itemID:quantity pair; use pairs separated by commas, like 560:500,9075:200");
            }
            result.put(id, amount);
        }
        return result;
    }

    private static List<String> entries(String text)
    {
        List<String> result = new ArrayList<>();
        for (String entry : text == null ? new String[0] : text.split(","))
        {
            if (!entry.isBlank())
            {
                result.add(entry.trim());
            }
        }
        return result;
    }

    /** The whole number written in the text, or -1 when it is not a positive whole number. */
    private static int positive(String text)
    {
        try
        {
            return Math.max(-1, Integer.parseInt(text.trim()));
        }
        catch (NumberFormatException invalid)
        {
            return -1;
        }
    }

    /** Live Tormented Demons in the top-level world view. */
    public List<NPC> demons()
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return List.of();
        }
        List<NPC> result = new ArrayList<>();
        for (NPC npc : view.npcs())
        {
            if (npc != null && TempleData.DEMON_IDS.contains(npc.getId()) && !npc.isDead() && npc.getWorldView() == view)
            {
                result.add(npc);
            }
        }
        return result;
    }

    /** Ground items on tiles within the NPC search radius, nearest first, excluding other players' drops. */
    public List<GroundDrop> groundDrops()
    {
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        if (player == null || view == null || player.getWorldLocation() == null)
        {
            return List.of();
        }
        Scene scene = view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        int plane = player.getWorldLocation().getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length)
        {
            return List.of();
        }
        List<GroundDrop> result = new ArrayList<>();
        for (Tile[] column : tiles[plane])
        {
            for (Tile tile : column)
            {
                if (tile == null || tile.getGroundItems() == null || tile.getWorldLocation() == null
                    || tile.getWorldLocation().distanceTo(player.getWorldLocation()) > TargetRange.NPC_SEARCH_RADIUS)
                {
                    continue;
                }
                for (TileItem item : tile.getGroundItems())
                {
                    if (item != null && item.getOwnership() != TileItem.OWNERSHIP_OTHER)
                    {
                        result.add(new GroundDrop(tile, item));
                    }
                }
            }
        }
        result.sort(Comparator.comparingInt(drop -> drop.getTile().getWorldLocation().distanceTo(player.getWorldLocation())));
        return result;
    }

    /** The nearest object with one of the IDs within the object search radius. */
    public TileObject object(Set<Integer> ids)
    {
        return objects().stream().filter(object -> ids.contains(object.getId()))
            .filter(object -> TargetRange.objectDistance(client, object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS)
            .min(Comparator.comparingInt(object -> TargetRange.objectDistance(client, object)))
            .orElse(null);
    }

    private List<TileObject> objects()
    {
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        Scene scene = view == null ? null : view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (player == null || tiles == null || player.getWorldLocation() == null)
        {
            return List.of();
        }
        int plane = player.getWorldLocation().getPlane();
        if (plane < 0 || plane >= tiles.length)
        {
            return List.of();
        }
        Set<TileObject> result = new LinkedHashSet<>();
        for (Tile[] column : tiles[plane])
        {
            for (Tile tile : column)
            {
                if (tile == null)
                {
                    continue;
                }
                if (tile.getGameObjects() != null)
                {
                    for (GameObject object : tile.getGameObjects())
                    {
                        if (object != null)
                        {
                            result.add(object);
                        }
                    }
                }
                if (tile.getWallObject() != null)
                {
                    result.add(tile.getWallObject());
                }
                if (tile.getGroundObject() != null)
                {
                    result.add(tile.getGroundObject());
                }
                if (tile.getDecorativeObject() != null)
                {
                    result.add(tile.getDecorativeObject());
                }
            }
        }
        return new ArrayList<>(result);
    }

    public boolean objectHasAction(TileObject object, String action)
    {
        ObjectComposition composition = object == null ? null : client.getObjectDefinition(object.getId());
        if (composition != null && composition.getImpostorIds() != null)
        {
            composition = composition.getImpostor();
        }
        return composition != null && indexOf(composition.getActions(), action) >= 0;
    }

    static boolean npcHasAction(NPC npc, String action)
    {
        NPCComposition composition = npc == null ? null : npc.getTransformedComposition();
        return composition != null && indexOf(composition.getActions(), action) >= 0;
    }

    static int indexOf(String[] actions, String option)
    {
        if (actions != null && option != null)
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

    public boolean attack(NPC npc, Runnable callback)
    {
        BooleanSupplier valid = () -> demons().contains(npc) && npcHasAction(npc, "Attack")
            && TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS;
        return request(valid, () -> clickNpc(npc, "Attack"), callback);
    }

    /**
     * Once the client has seen "I can't reach that!", the NPC model walks to the NPC before clicking unless the
     * NPC is in line of sight; with line of sight it clears that flag and clicks without walking.
     */
    static boolean clickNpc(NPC npc, String option)
    {
        Rs2NpcModel model = new Rs2NpcModel(npc);
        return (!(Microbot.isCantReachTargetDetectionEnabled && Microbot.cantReachTarget) || model.hasLineOfSight())
            && model.click(option);
    }

    /** Toggles one prayer with the non-blocking Microbot API call; the varbit shows the result on a later tick. */
    public boolean prayer(Rs2PrayerEnum prayer, boolean active, Runnable callback)
    {
        BooleanSupplier valid = () -> prayer != null && prayerActive(prayer) != active
            && (!active || prayerPoints() > 0);
        return request(valid, () ->
        {
            Rs2Prayer.toggle(prayer);
            return true;
        }, callback);
    }

    /** Uses a carried item. The action must be one of its inventory actions or ring sub-actions. */
    public boolean inventory(int id, String action, Runnable callback)
    {
        BooleanSupplier valid = () -> count(id) > 0 && !bankOpen() && itemHasAction(Rs2Inventory.get(id), action);
        return request(valid, () -> Rs2Inventory.interact(id, action), callback);
    }

    static boolean itemHasAction(Rs2ItemModel item, String action)
    {
        if (item == null)
        {
            return false;
        }
        if (indexOf(item.getInventoryActions(), action) >= 0)
        {
            return true;
        }
        return item.getIndexOfSubAction(action) != null;
    }

    public boolean equipment(int id, String action, Runnable callback)
    {
        return request(() -> wearing(id) && !bankOpen(), () -> Rs2Equipment.interact(id, action), callback);
    }

    /** Teleports to Ferox Enclave with the first worn or carried ring of dueling. */
    public boolean ringTeleport(Runnable callback)
    {
        int ring = duelingRing();
        if (ring <= 0)
        {
            return false;
        }
        return wearing(ring) ? equipment(ring, "Ferox Enclave", callback) : inventory(ring, "Ferox Enclave", callback);
    }

    public boolean pickup(GroundDrop drop, Runnable callback)
    {
        BooleanSupplier valid = () -> drop != null && !bankOpen() && drop.getTile().getGroundItems() != null
            && drop.getTile().getGroundItems().contains(drop.getItem());
        return request(valid, () -> new Rs2TileItemModel(drop.getTile(), drop.getItem()).pickup(), callback);
    }

    public boolean object(TileObject object, String action, Runnable callback)
    {
        BooleanSupplier valid = () -> object != null && objectHasAction(object, action) && !bankOpen()
            && TargetRange.objectDistance(client, object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS
            && objects().contains(object);
        return request(valid, () -> ObjectClick.click(object, action), callback);
    }

    /** Opens the Ferox Enclave bank through its chest or a banker within range of the bank. */
    public boolean openBank(Runnable callback)
    {
        if (bankOpen())
        {
            return false;
        }
        TileObject chest = object(Set.of(TempleData.FEROX_BANK_CHEST));
        if (chest != null && chest.getWorldLocation().distanceTo(TempleData.FEROX_BANK) <= TargetRange.NPC_SEARCH_RADIUS
            && objectHasAction(chest, "Use"))
        {
            return object(chest, "Use", callback);
        }
        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return false;
        }
        for (NPC npc : view.npcs())
        {
            if (npc != null && npc.getWorldLocation().distanceTo(TempleData.FEROX_BANK) <= TargetRange.NPC_SEARCH_RADIUS
                && npcHasAction(npc, "Bank") && TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS)
            {
                return request(() -> !bankOpen() && npcHasAction(npc, "Bank")
                    && TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS,
                    () -> clickNpc(npc, "Bank"), callback);
            }
        }
        return false;
    }

    /** Types the bank PIN saved in the Microbot profile. It waits for each digit, so it runs on the worker. */
    public boolean enterBankPin(Runnable callback)
    {
        return request(this::bankPinShown, Rs2Bank::handleBankPin, callback);
    }

    public boolean deposit(int id, Runnable callback)
    {
        return request(() -> bankOpen() && count(id) > 0, () -> Rs2Bank.depositAll(id), callback);
    }

    public boolean withdrawOne(int id, Runnable callback)
    {
        return request(() -> bankOpen() && bankCount(id) > 0 && freeSlots() > 0, () -> Rs2Bank.withdrawOne(id), callback);
    }

    /**
     * Withdraws an amount in one request. Above one it uses Withdraw-X, which may type the amount, and the
     * request stays busy until the inventory changes so the next count sees the withdrawn items.
     */
    public boolean withdraw(int id, int amount, Runnable callback)
    {
        if (amount <= 1)
        {
            return withdrawOne(id, callback);
        }
        return request(() -> bankOpen() && bankCount(id) >= amount && (stackable(id) || freeSlots() >= amount),
            () -> Rs2Bank.withdrawX(id, amount) && Rs2Inventory.waitForInventoryChanges(WITHDRAW_WAIT_MILLIS), callback);
    }

    public boolean closeBank(Runnable callback)
    {
        return request(this::bankOpen, Rs2Bank::closeBank, callback);
    }

    /** One scene walk click to an adjacent tile, with no route planning and no confirmation wait. */
    public boolean step(WorldPoint tile, Runnable callback)
    {
        WorldView view = client.getTopLevelWorldView();
        LocalPoint local = view == null || tile == null ? null : LocalPoint.fromWorld(view, tile);
        if (local == null)
        {
            return false;
        }
        return request(() -> !bankOpen(), () ->
        {
            Rs2Walker.walkFastLocal(local);
            return true;
        }, callback);
    }

    private boolean request(BooleanSupplier valid, BooleanSupplier operation, Runnable callback)
    {
        if (failure != null || isBusy() || !ready() || !valid.getAsBoolean())
        {
            return false;
        }
        return queue.offerAtPlayerPosition(client, () ->
        {
            if (failure != null || isBusy() || !ready() || !valid.getAsBoolean())
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

    /** Distinct item IDs carried in the inventory. */
    public Set<Integer> carriedIds()
    {
        return Arrays.stream(items()).filter(item -> item != null && item.getId() > 0 && item.getQuantity() > 0)
            .map(Item::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
