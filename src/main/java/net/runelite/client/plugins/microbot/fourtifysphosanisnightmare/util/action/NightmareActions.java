package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene.GroundDrop;

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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * The session's queued requests and the Microbot calls they make. Each request is checked when it is
 * queued, again when it is dispatched, and once more on the client thread before the worker runs it.
 * A sent request is not proof of success; tasks observe the result on later ticks.
 */
@Singleton
public class NightmareActions
{
    static final int INVENTORY = 93;
    static final int EQUIPMENT = 94;
    static final int BANK = 95;
    private static final int WITHDRAW_WAIT_MILLIS = 3000;
    private static final int ATTACK_RANGE_PARAM = 13;
    /** How long a request waits for the game to take its click before it gives up. */
    private static final int CLICK_TAKEN_MS = 600;
    /** No click has been taken since it was set; an NPC click reports no item. */
    private static final int NOTHING_TAKEN = Integer.MIN_VALUE;
    private static final WidgetInfo[] STYLE_BUTTONS = {WidgetInfo.COMBAT_STYLE_ONE, WidgetInfo.COMBAT_STYLE_TWO,
        WidgetInfo.COMBAT_STYLE_THREE, WidgetInfo.COMBAT_STYLE_FOUR};
    /** The steepest camera pitch, in the client's 14-bit angle units. */
    static final int OVERHEAD_PITCH = 383 * 8;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private final Supplier<ExecutorService> workers;
    private final AtomicBoolean executing = new AtomicBoolean();
    private final ActionQueue queue = new ActionQueue();
    /** Prayer and dodge requests, which go between the clicks of a burst instead of waiting for all of it. */
    private final ActionQueue urgent = new ActionQueue();
    final ClickBurst burst = new ClickBurst();
    private final Map<Integer, Long> prices = new HashMap<>();
    private volatile int generation;
    /** The item of the last click the game took. */
    private volatile int lastClicked = -1;
    private int snapshotCycle;
    private FutureTask<Void> pending;
    /** One worker thread per session, replaced only after a reset stops it. */
    private ExecutorService worker;
    private long dispatchedRequests;
    private String failure;

    @Inject
    public NightmareActions(Client client, ClientThread clientThread, ItemManager itemManager)
    {
        this(client, clientThread, itemManager, () -> Executors.newSingleThreadExecutor(action ->
        {
            Thread thread = new Thread(action, "fourtify-nightmare-interaction");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public NightmareActions(Client client, ClientThread clientThread, ItemManager itemManager, Supplier<ExecutorService> workers)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
        this.workers = workers;
    }

    public void reset()
    {
        queue.clear();
        urgent.clear();
        burst.clear();
        generation++;
        if (pending != null)
        {
            pending.cancel(true);
        }
        if (worker != null)
        {
            worker.shutdownNow();
            worker = null;
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
        urgent.clear();
        snapshotCycle = client.getGameCycle();
    }

    /** Sends the next request once the one before it has finished, so requests sharing a tick go one after another. */
    public void dispatch()
    {
        if (burst.isActive() && working())
        {
            // A prayer or dodge sent between the burst's clicks finishes before the next one.
            return;
        }
        if (!working() && !burst.isClicking())
        {
            urgent.dispatch();
            if (working())
            {
                return;
            }
        }
        burst.advance();
        if (!isBusy())
        {
            queue.dispatch();
        }
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
        return working() || burst.isActive();
    }

    /** A request is waiting for or running on its worker. */
    private boolean working()
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
        for (int id : DreamData.DUELING_RINGS)
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

    /** The equipment slot index the item is worn in, or -1 when unknown. */
    public int slot(int id)
    {
        ItemStats stats = itemManager == null ? null : itemManager.getItemStats(id);
        return stats == null || stats.getEquipment() == null ? -1 : stats.getEquipment().getSlot();
    }

    public boolean twoHanded(int id)
    {
        ItemStats stats = itemManager == null ? null : itemManager.getItemStats(id);
        return stats != null && stats.getEquipment() != null && stats.getEquipment().isTwoHanded();
    }

    public int reach(int id)
    {
        return itemManager == null || id < 0 || itemManager.getItemComposition(id) == null ? 0
            : itemManager.getItemComposition(id).getIntValue(ATTACK_RANGE_PARAM);
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

    /** Live NPCs with one of the IDs in the top-level world view. */
    public List<NPC> npcs(Set<Integer> ids)
    {
        return npcs(ids, false);
    }

    /** NPCs with one of the IDs in the top-level world view, with those dying when asked. */
    public List<NPC> npcs(Set<Integer> ids, boolean dying)
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return List.of();
        }
        List<NPC> result = new ArrayList<>();
        for (NPC npc : view.npcs())
        {
            if (npc != null && ids.contains(npc.getId()) && (dying || !npc.isDead()) && npc.getWorldView() == view)
            {
                result.add(npc);
            }
        }
        return result;
    }

    /** The nearest live NPC with one of the IDs within the NPC search radius, or null. */
    public NPC nearest(Set<Integer> ids)
    {
        return nearest(ids, npc -> true);
    }

    /** The nearest NPC of the IDs in reach that the filter keeps, or null. */
    public NPC nearest(Set<Integer> ids, Predicate<NPC> keep)
    {
        return npcs(ids).stream().filter(keep).filter(npc -> TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS)
            .min(Comparator.comparingInt(npc -> TargetRange.npcDistance(client, npc))).orElse(null);
    }

    /** The Nightmare in any of her forms, or null. */
    public NPC boss()
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null)
        {
            return null;
        }
        for (NPC npc : view.npcs())
        {
            if (npc != null && Stage.fromNpcId(npc.getId()) != null && npc.getWorldView() == view)
            {
                return npc;
            }
        }
        return null;
    }

    /** True inside an instance such as her dream. */
    public boolean inInstance()
    {
        WorldView view = client.getTopLevelWorldView();
        return view != null && view.isInstance();
    }

    public int varbit(int id)
    {
        return client.getVarbitValue(id);
    }

    public int boosted(Skill skill)
    {
        return client.getBoostedSkillLevel(skill);
    }

    public int real(Skill skill)
    {
        return client.getRealSkillLevel(skill);
    }

    /** Special attack energy, 0 to 100. */
    public int specEnergy()
    {
        return client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
    }

    public boolean specEnabled()
    {
        return client.getVarpValue(VarPlayerID.SA_ATTACK) == 1;
    }

    /** The selected combat style button, 0 to 3. */
    public int styleIndex()
    {
        return client.getVarpValue(VarPlayerID.COM_MODE);
    }

    /**
     * The first combat style button of the worn weapon whose description names the type, such as "(Crush)" or
     * "(Rapid)", or -1 when it has none.
     */
    public int styleButton(String type)
    {
        int category = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        List<Integer> rows = client.getDBRowsByValue(DBTableID.CombatInterfaceWeaponCategory.ID,
            DBTableID.CombatInterfaceWeaponCategory.COL_ID, 0, category);
        if (rows == null || rows.isEmpty())
        {
            return -1;
        }
        int row = rows.get(0);
        return styleButton(client.getDBTableField(row, DBTableID.CombatInterfaceWeaponCategory.COL_BUTTON, 0),
            client.getDBTableField(row, DBTableID.CombatInterfaceWeaponCategory.COL_BUTTON, 2), type);
    }

    /** The button of the first description naming the type; the two lists hold one entry for each button. */
    static int styleButton(Object[] buttons, Object[] descriptions, String type)
    {
        for (int index = 0; buttons != null && descriptions != null && index < Math.min(buttons.length, descriptions.length); index++)
        {
            if (buttons[index] instanceof Integer && descriptions[index] instanceof String
                && ((String) descriptions[index]).contains(type))
            {
                return (Integer) buttons[index];
            }
        }
        return -1;
    }

    /** Tiles where a graphic with the ID is playing. */
    public Map<WorldPoint, Integer> graphicAges(int id)
    {
        WorldView view = client.getTopLevelWorldView();
        Map<WorldPoint, Integer> result = new LinkedHashMap<>();
        if (view == null)
        {
            return result;
        }
        for (GraphicsObject graphic : view.getGraphicsObjects())
        {
            if (graphic != null && graphic.getId() == id && graphic.getLocation() != null)
            {
                // Client cycles are 20 ms, so a game tick is 30 of them.
                int age = Math.max(0, (client.getGameCycle() - graphic.getStartCycle()) / 30);
                result.merge(WorldPoint.fromLocal(view, graphic.getLocation().getX(), graphic.getLocation().getY(), view.getPlane()),
                    age, Math::min);
            }
        }
        return result;
    }

    /** Scene objects with one of the IDs on the player's plane, from one scan of the scene. */
    public List<TileObject> objects(Set<Integer> ids)
    {
        List<TileObject> result = new ArrayList<>();
        for (TileObject object : objects())
        {
            if (ids.contains(object.getId()) && object.getWorldLocation() != null)
            {
                result.add(object);
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

    /**
     * Looks straight down from as far out as the zoom limits allow, so more of the arena stays on screen and clicks
     * land on what they name. Not a click, so it takes no turn in the action queue.
     */
    public void overheadCamera()
    {
        clientThread.invokeLater(() ->
        {
            client.setCameraPitchTarget(OVERHEAD_PITCH);
            // The zoom script keeps the zoom within the player's limits, so 0 is the furthest out they allow.
            client.runScript(ScriptID.CAMERA_DO_ZOOM, 0, 0);
        });
    }

    /** Clicks one of the NPC's options, such as Attack, Charge, Disturb or Collect. */
    public boolean npc(NPC npc, String option, Runnable callback)
    {
        WorldView view = client.getTopLevelWorldView();
        BooleanSupplier valid = () -> view != null && npc.getWorldView() == view && !npc.isDead()
            && npcHasAction(npc, option) && TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS;
        // In her dream a camera turn would hold up the next prayer click for most of a tick.
        return request(valid, () -> inInstance() ? NpcClick.click(npc, option) : clickNpc(npc, option), callback);
    }

    /** Turns the special attack bar on or off. */
    public boolean spec(boolean on, Runnable callback)
    {
        return request(() -> specEnabled() != on && !bankOpen(), () -> Rs2Combat.setSpecState(on), callback);
    }

    /** Opens the combat tab, clicks the combat style button, 0 to 3, and goes back to the inventory. */
    public boolean style(int index, Runnable callback)
    {
        if (index < 0 || index >= STYLE_BUTTONS.length)
        {
            return false;
        }
        return request(() -> styleIndex() != index && !bankOpen(), () ->
        {
            // The style buttons can only be clicked while the combat tab shows.
            if (!Rs2Tab.switchTo(InterfaceTab.COMBAT))
            {
                return false;
            }
            // The click is sent at once; the style shows on the next tick, so nothing waits for it here.
            Rs2Combat.setAttackStyle(STYLE_BUTTONS[index]);
            return Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        }, callback);
    }

    /**
     * Casts a spell that needs no target once its level, spellbook and runes (the rune pouch's too) are there. Its
     * spellbook button, given by its component ID, is pressed directly, as a prayer's is, so the magic tab need not
     * be open; a button looked up by name or icon is not found while that tab is closed.
     */
    public boolean cast(Spell spell, int button, Runnable callback)
    {
        return request(() -> !bankOpen() && Rs2Magic.canCast(spell), () ->
        {
            Microbot.doInvokeQuick(castEntry(spell.getMagicAction().getName(), button), Rs2UiHelper.getDefaultRectangle());
            return true;
        }, callback);
    }

    /** The menu entry that presses a spellbook button. */
    static NewMenuEntry castEntry(String spell, int button)
    {
        return new NewMenuEntry().option("Cast").target(spell).param0(-1).param1(button)
            .opcode(MenuAction.CC_OP.getId()).identifier(1).itemId(-1);
    }

    public boolean dialogueContinue(Runnable callback)
    {
        return request(Rs2Dialogue::hasContinue, () ->
        {
            Rs2Dialogue.clickContinue();
            return true;
        }, callback);
    }

    public boolean dialogueOption(String option, Runnable callback)
    {
        return request(() -> Rs2Dialogue.hasDialogueOption(option), () -> Rs2Dialogue.keyPressForDialogueOption(option), callback);
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

    /** Running is switched on. */
    public boolean running()
    {
        return client.getVarpValue(VarPlayerID.OPTION_RUN) == 1;
    }

    /** Switches running on with the run orb; the setting shows it on a later tick. */
    public boolean run(Runnable callback)
    {
        return request(() -> !running() && client.getEnergy() >= 100, () -> Rs2Player.toggleRunEnergy(true), callback, true);
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
        }, callback, true);
    }

    /** Uses a carried item. The action must be one of its inventory actions or ring sub-actions. */
    public boolean inventory(int id, String action, Runnable callback)
    {
        BooleanSupplier valid = () -> count(id) > 0 && !bankOpen() && itemHasAction(Rs2Inventory.get(id), action);
        return request(valid, () -> Rs2Inventory.interact(id, action), callback);
    }

    /** Wields or wears a carried item. */
    public boolean equip(int id, Runnable callback)
    {
        return inventory(id, "Wield", callback) || inventory(id, "Wear", callback);
    }

    /**
     * Uses several carried items in one request, so the game takes them all on the same tick. Each click is sent
     * from the client thread once the game has taken the one before it.
     */
    public boolean inventoryAll(Map<Integer, String> clicks, Runnable callback)
    {
        BooleanSupplier valid = () -> !clicks.isEmpty() && !bankOpen() && clicks.entrySet().stream()
            .allMatch(click -> count(click.getKey()) > 0 && itemHasAction(Rs2Inventory.get(click.getKey()), click.getValue()));
        return request(valid, () ->
        {
            burst.start(clicks.keySet(), item -> count(item) > 0 && Rs2Inventory.interact(item, clicks.get(item)));
            return true;
        }, callback);
    }

    /** The game took a click on the item. */
    public void clicked(int itemId)
    {
        lastClicked = itemId;
        burst.clicked(itemId);
    }

    /** Wields or wears every listed carried item on the same tick. */
    public boolean equipAll(List<Integer> ids, Runnable callback)
    {
        Map<Integer, String> clicks = new LinkedHashMap<>();
        for (int id : ids)
        {
            clicks.put(id, itemHasAction(Rs2Inventory.get(id), "Wield") ? "Wield" : "Wear");
        }
        return inventoryAll(clicks, callback);
    }

    /**
     * Wields the carried weapon and then chooses the style button in one request, so the first attack does not
     * wait a request for the style. The style is clicked once the game has taken the wield, since a click sent
     * sooner could replace its menu entry.
     */
    public boolean wieldWithStyle(int id, int style, Runnable callback)
    {
        if (style < 0 || style >= STYLE_BUTTONS.length)
        {
            return false;
        }
        String action = itemHasAction(Rs2Inventory.get(id), "Wield") ? "Wield" : "Wear";
        return request(() -> count(id) > 0 && !bankOpen() && itemHasAction(Rs2Inventory.get(id), action), () ->
        {
            lastClicked = -1;
            if (!Rs2Inventory.interact(id, action) || !Global.sleepUntil(() -> lastClicked == id, CLICK_TAKEN_MS)
                || !Rs2Tab.switchTo(InterfaceTab.COMBAT))
            {
                return false;
            }
            Rs2Combat.setAttackStyle(STYLE_BUTTONS[style]);
            return Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        }, callback);
    }

    /**
     * Attacks the NPC and then wields the carried weapon in one request, so the attack goes on the swap's tick. The
     * attack goes first: a swap taken a tick before it would turn the player toward the target already attacked. The
     * wield is clicked once the game has taken the attack, since a click sent sooner could replace its menu entry.
     */
    public boolean attackAndWield(NPC npc, int id, Runnable callback)
    {
        String action = itemHasAction(Rs2Inventory.get(id), "Wield") ? "Wield" : "Wear";
        return request(() -> count(id) > 0 && !bankOpen() && itemHasAction(Rs2Inventory.get(id), action) && !npc.isDead(), () ->
        {
            lastClicked = NOTHING_TAKEN;
            return NpcClick.click(npc, "Attack") && Global.sleepUntil(() -> lastClicked != NOTHING_TAKEN, CLICK_TAKEN_MS)
                && Rs2Inventory.interact(id, action);
        }, callback);
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
        TileObject chest = object(Set.of(DreamData.FEROX_BANK_CHEST));
        if (chest != null && chest.getWorldLocation().distanceTo(DreamData.FEROX_BANK) <= TargetRange.NPC_SEARCH_RADIUS
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
            if (npc != null && npc.getWorldLocation().distanceTo(DreamData.FEROX_BANK) <= TargetRange.NPC_SEARCH_RADIUS
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
        return step(tile, callback, false);
    }

    /**
     * Steps off a tile about to be hit. The rest of a gear switch is dropped and the step goes once the click in
     * flight is taken, since a step held behind the switch arrives after the hit.
     */
    public boolean dodge(WorldPoint tile, Runnable callback)
    {
        if (!step(tile, callback, true))
        {
            return false;
        }
        burst.drop();
        return true;
    }

    private boolean step(WorldPoint tile, Runnable callback, boolean urgent)
    {
        WorldView view = client.getTopLevelWorldView();
        LocalPoint local = view == null || tile == null ? null : LocalPoint.fromWorld(view, tile);
        if (local == null)
        {
            return false;
        }
        return request(() -> !bankOpen(), () ->
        {
            // A tile outside the game view has no point to click in the scene; the minimap reaches it.
            if (!inViewport(Perspective.localToCanvas(client, local, view.getPlane())))
            {
                return Rs2Walker.walkMiniMap(tile);
            }
            Rs2Walker.walkFastLocal(local);
            return true;
        }, callback, urgent);
    }

    /** The canvas point lies inside the game view; a point off it, or none behind the camera, cannot be clicked. */
    boolean inViewport(Point point)
    {
        if (point == null)
        {
            return false;
        }
        int x = point.getX() - client.getViewportXOffset();
        int y = point.getY() - client.getViewportYOffset();
        return x >= 0 && y >= 0 && x < client.getViewportWidth() && y < client.getViewportHeight();
    }

    private boolean request(BooleanSupplier valid, BooleanSupplier operation, Runnable callback)
    {
        return request(valid, operation, callback, false);
    }

    /**
     * An urgent request (a prayer or a dodge) waits only for a running request, not for the rest of a burst. It is
     * queued even while a request runs, and goes as soon as that one finishes, since a tick late lets the hit through.
     */
    private boolean request(BooleanSupplier valid, BooleanSupplier operation, Runnable callback, boolean urgent)
    {
        BooleanSupplier busy = urgent ? this::working : this::isBusy;
        if (failure != null || !urgent && busy.getAsBoolean() || !ready() || !valid.getAsBoolean())
        {
            return false;
        }
        return (urgent ? this.urgent : queue).offerAtPlayerPosition(client, () ->
        {
            if (failure != null || busy.getAsBoolean() || !ready() || !valid.getAsBoolean())
            {
                return;
            }
            int expectedGeneration = generation;
            int expires = snapshotCycle + 29;
            WorldPoint origin = client.getLocalPlayer().getWorldLocation();
            if (worker == null || worker.isShutdown())
            {
                worker = workers.get();
            }
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
        });
    }

    private boolean ready()
    {
        Player player = client.getLocalPlayer();
        return client.getGameState() == GameState.LOGGED_IN && player != null && !ActionQueue.dead(client, player)
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
