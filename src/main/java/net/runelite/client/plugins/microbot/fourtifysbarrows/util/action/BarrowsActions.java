package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.inventory.InventoryQueries;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation.ShadesTeleport;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene.SceneQueries;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.widget.WidgetQueries;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.Actor;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.TravelMethod;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.TunnelMap;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;

@Singleton
public class BarrowsActions
{
    @Inject private Client client;
    @Inject private BarrowsConfig config;
    @Inject private Walking walking;
    @Inject private MicrobotApi api;
    @Inject private InteractionWorker worker;
    private static final Set<String> TUNNEL_MONSTERS = Set.of("Skeleton", "Bloodworm", "Crypt rat", "Crypt spider",
        "Giant crypt rat", "Giant crypt spider");
    private final ActionQueue queue = new ActionQueue();
    private NPC rejectedTarget;
    private int rejectedTick = Integer.MIN_VALUE;
    private final Map<NPC, Integer> swings = new WeakHashMap<>();
    private final BarrowsInteractions interactions = new BarrowsInteractions(() -> client, queue::offer,
        () -> api, () -> worker, this::interactionReady);
    private String status = "Starting";
    private String failure;
    private String pendingStop;
    private boolean trip;
    private static final int[] BANK_RING_IDS = {2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566};
    /** Prayer potions from four doses to one. */
    public static final List<Integer> PRAYER_POTIONS = List.of(2434, 139, 141, 143);
    /** The dialogue "Select an Option" choices container. */
    public static final int DIALOGUE_OPTIONS = (219 << 16) | 1;
    private long rawRequests;

    public void reset()
    {
        queue.clear();
        if (worker != null)
        {
            worker.cancel();
        }
        if (walking != null)
        {
            walking.cancel();
        }
    }

    public void newSession()
    {
        reset();
        failure = null;
        pendingStop = null;
        trip = false;
    }

    public void nextTick()
    {
        queue.clear();
        if (worker != null && worker.getFailure() != null)
        {
            fail("A game action did not go through; enable the plugin again to retry");
        }
    }

    /** Queues one Microbot API helper that rechecks its guard when dispatched and when the worker starts it. */
    private boolean queueHelper(BooleanSupplier allowed, BooleanSupplier operation, Runnable callback)
    {
        return enqueue(() ->
        {
            if (allowed.getAsBoolean() && worker != null)
            {
                worker.submit(allowed, operation, () ->
                {
                    rawRequests++;
                    if (callback != null)
                    {
                        callback.run();
                    }
                });
            }
        });
    }

    boolean enqueue(Runnable action)
    {
        return queue.offer(action);
    }

    public void dispatch()
    {
        queue.dispatch();
    }

    /** Counts started Microbot API helper attempts; tasks observe game state to establish success. */
    public long getRequestsDispatched()
    {
        return interactions.getRequestsDispatched() + rawRequests;
    }

    public String getWalkingDiagnostic()
    {
        return walking == null ? "Unavailable" : walking.getDiagnostic();
    }

    public Player player()
    {
        return client.getLocalPlayer();
    }

    public BarrowsConfig config()
    {
        return config;
    }

    public int tick()
    {
        return client.getTickCount();
    }

    public WorldPoint position()
    {
        return player() == null ? null : player().getWorldLocation();
    }

    public String prerequisite()
    {
        if (Quest.PRIEST_IN_PERIL.getState(client) != QuestState.FINISHED)
        {
            return "Complete Priest in Peril before starting Barrows";
        }
        if (Quest.HIS_FAITHFUL_SERVANTS.getState(client) == QuestState.NOT_STARTED)
        {
            return "Start His Faithful Servants before digging into Barrows";
        }
        return null;
    }

    public String npcAttackProblem()
    {
        return client.getVarpValue(VarPlayerID.OPTION_ATTACKPRIORITY_NPC) == 3
            ? "NPC Attack options are set to Hidden; choose another option in Settings > Controls, then enable the plugin again"
            : null;
    }

    public boolean ready()
    {
        return failure == null && client.getGameState() == GameState.LOGGED_IN && player() != null
            && !player().isDead() && player().getWorldView() == client.getTopLevelWorldView();
    }

    public void status(String text)
    {
        status = text;
    }

    public String getStatus()
    {
        return failure == null ? status : failure;
    }

    /** Stops the session. In danger at Barrows, the stop waits for the ring teleport out of Barrows. */
    public void fail(String reason)
    {
        if (pendingStop == null)
        {
            pendingStop = reason;
        }
        reset();
        if (!endangered())
        {
            failure = pendingStop;
        }
    }

    /** Ends a pending stop whose teleport out of Barrows could not be completed. */
    public void failEscape()
    {
        failure = pendingStop + "; the teleport out failed, so leave Barrows now";
        reset();
    }

    /** The reason the session stopped, or null while it runs. */
    public String failure()
    {
        return failure;
    }

    /** The reason for a stop that waits for the teleport out of Barrows, or null. */
    public String pendingStop()
    {
        return failure == null ? pendingStop : null;
    }

    /** Idling in a crypt, in the tunnels or under attack at Barrows can get the player killed. */
    boolean endangered()
    {
        Player player = player();
        if (client.getGameState() != GameState.LOGGED_IN || player == null || player.isDead())
        {
            return false;
        }
        if (inside())
        {
            return true;
        }
        List<NPC> npcs = client.getNpcs();
        return atBarrowsSurface() && npcs != null
            && npcs.stream().anyMatch(npc -> npc.getInteracting() == player && !npc.isDead());
    }

    public boolean bankPending()
    {
        return trip;
    }

    public boolean tripPending()
    {
        return trip;
    }

    public void requestBank()
    {
        trip = true;
    }

    private boolean bankTeleportLocationAllowed()
    {
        return !visible(155 << 16);
    }

    public void completeTrip()
    {
        trip = false;
    }

    public boolean inside()
    {
        return Brother.inTomb(position()) != null || TunnelMap.contains(position());
    }

    public boolean killed(Brother brother)
    {
        return client.getVarbitValue(brother.getKilledVarbit()) != 0;
    }

    public int killedCount()
    {
        return (int) Arrays.stream(Brother.values()).filter(this::killed).count();
    }

    public int potential()
    {
        return client.getVarbitValue(463) + 2 * killedCount();
    }

    public int varbit(int id)
    {
        return client.getVarbitValue(id);
    }

    public int maxHp()
    {
        return client.getRealSkillLevel(Skill.HITPOINTS);
    }

    public int maxPrayer()
    {
        return client.getRealSkillLevel(Skill.PRAYER);
    }

    public int hp()
    {
        return client.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    public int prayerPoints()
    {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }

    public int count(int id)
    {
        return InventoryQueries.count(client, id);
    }

    public int foodCount()
    {
        return count(config.foodId());
    }

    public int potionCount()
    {
        return PRAYER_POTIONS.stream().mapToInt(this::count).sum();
    }

    public int prayerRestoreId()
    {
        return Arrays.stream(new int[] {143, 141, 139, 2434}).filter(id -> count(id) > 0).findFirst().orElse(-1);
    }

    public boolean wearing(int id)
    {
        return InventoryQueries.wearing(client, id);
    }

    public boolean inventory(int id, String option, Runnable callback)
    {
        return inventory(id, option, () -> true, callback);
    }

    private boolean inventory(int id, String option, BooleanSupplier allowed, Runnable callback)
    {
        return inventory(id, option, option, allowed, callback);
    }

    private boolean inventory(int id, String option, String menu, BooleanSupplier allowed, Runnable callback)
    {
        if (!allowed.getAsBoolean())
        {
            return false;
        }
        if (!WidgetQueries.visible(client.getWidget(149 << 16)))
        {
            return requestTab(InterfaceTab.INVENTORY);
        }
        return interactions.inventory(id, option, menu, () -> allowed.getAsBoolean() && interactionReady() && !bankingOpen(), callback);
    }

    private boolean requestTab(InterfaceTab tab)
    {
        if (!interactionReady())
        {
            return false;
        }
        return queueHelper(this::interactionReady, () -> api.tab(tab), null);
    }

    public boolean gear(String ids, Runnable callback)
    {
        for (int id : gearIds(ids))
        {
            if (!wearing(id) && !wearingBankChargeVariant(id))
            {
                int carried = id;
                int[] variants = BANK_RING_IDS;
                if (count(carried) == 0 && Arrays.stream(variants).anyMatch(value -> value == id))
                {
                    carried = Arrays.stream(variants).filter(value -> count(value) > 0).findFirst().orElse(id);
                }
                if (count(carried) == 0)
                {
                    String missing = missingGear();
                    fail(missing != null ? missing : "Wear or carry " + gearName(id) + ", then enable the plugin again");
                }
                else if (!inventory(carried, "Wear", callback))
                {
                    inventory(carried, "Wield", callback);
                }
                return false;
            }
        }
        return true;
    }

    /** Names the first configured equipment item that is neither worn nor carried, or returns null. */
    public String missingGear()
    {
        String[][] sets = {{"Magic equipment IDs", config.magicGear()}, {"Ahrim equipment IDs", config.rangedGear()},
            {"Tunnel equipment IDs", config.meleeGear()}};
        for (String[] set : sets)
        {
            for (int id : gearIds(set[1]))
            {
                boolean ring = Arrays.stream(BANK_RING_IDS).anyMatch(value -> value == id);
                if (!wearing(id) && count(id) == 0 && !(ring && hasBankTravelRing()))
                {
                    return "Wear or carry " + gearName(id) + ", or remove it from " + set[0] + ", then enable the plugin again";
                }
            }
        }
        return null;
    }

    public String itemName(int id)
    {
        ItemComposition item = client.getItemDefinition(id);
        return item == null || item.getName() == null ? "item " + id : item.getName();
    }

    /** Equipment settings list IDs, so a named item also shows its ID. */
    private String gearName(int id)
    {
        String name = itemName(id);
        return name.equals("item " + id) ? name : name + " (" + id + ")";
    }

    private boolean wearingBankChargeVariant(int requested)
    {
        int[] variants = BANK_RING_IDS;
        return Arrays.stream(variants).anyMatch(id -> id == requested) && Arrays.stream(variants).anyMatch(this::wearing);
    }

    public Map<Integer, Integer> restockItems()
    {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        String text = config.restockItems();
        if (text != null && !text.isBlank())
        {
            for (String pair : text.split(","))
            {
                String[] parts = pair.trim().split(":");
                if (parts.length != 2)
                {
                    throw new IllegalArgumentException("Use itemID:quantity for restock supplies");
                }
                int id = Integer.parseInt(parts[0].trim());
                int amount = Integer.parseInt(parts[1].trim());
                if (id <= 0 || amount <= 0)
                {
                    throw new IllegalArgumentException("Restock IDs and quantities must be positive");
                }
                result.put(id, amount);
            }
        }
        int travel = travelItem(config.travelMethod());
        if (travel == 19629 || travel == 12406)
        {
            result.merge(travel, 1, Math::max);
        }
        return result;
    }

    public static Set<Integer> gearIds(String text)
    {
        Set<Integer> result = new LinkedHashSet<>();
        if (text != null && !text.isBlank())
        {
            for (String value : text.split(","))
            {
                int id = Integer.parseInt(value.trim());
                if (id <= 0)
                {
                    throw new IllegalArgumentException("Equipment IDs must be positive");
                }
                result.add(id);
            }
        }
        return result;
    }

    public boolean prayerActive(Prayer prayer)
    {
        return client.isPrayerActive(prayer);
    }

    /** The protection prayer still on, or null; outside Barrows it only drains Prayer. */
    public Prayer activeProtection()
    {
        for (Prayer prayer : new Prayer[] {Prayer.PROTECT_FROM_MAGIC, Prayer.PROTECT_FROM_MISSILES, Prayer.PROTECT_FROM_MELEE})
        {
            if (prayerActive(prayer))
            {
                return prayer;
            }
        }
        return null;
    }

    public boolean autoRetaliateDisabled()
    {
        return client.getVarpValue(VarPlayerID.OPTION_NODEF) == 1;
    }

    public boolean disableAutoRetaliate(Runnable callback)
    {
        if (autoRetaliateDisabled())
        {
            return true;
        }
        if (!interactionReady() || bankingOpen())
        {
            return false;
        }
        int id = InterfaceID.CombatInterface.RETALIATE;
        Widget widget = client.getWidget(id);
        if (!WidgetQueries.visible(widget))
        {
            requestTab(InterfaceTab.COMBAT);
            return false;
        }
        BooleanSupplier allowed = () -> interactionReady() && !bankingOpen() && !autoRetaliateDisabled()
            && client.getWidget(id) == widget && widget.getId() == id && WidgetQueries.visible(widget)
            && WidgetQueries.indexOf(widget.getActions(), "Auto retaliate") == 0
            && widget.getBounds() != null && widget.getBounds().width > 0 && widget.getBounds().height > 0
            && new java.awt.Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()).contains(widget.getBounds());
        return queueHelper(allowed, () -> api.disableAutoRetaliate(), callback);
    }

    public boolean protect(Prayer prayer, boolean active, Runnable callback)
    {
        int id;
        if (prayer == Prayer.PROTECT_FROM_MAGIC)
        {
            id = InterfaceID.Prayerbook.PRAYER13;
        }
        else if (prayer == Prayer.PROTECT_FROM_MISSILES)
        {
            id = InterfaceID.Prayerbook.PRAYER14;
        }
        else if (prayer == Prayer.PROTECT_FROM_MELEE)
        {
            id = InterfaceID.Prayerbook.PRAYER15;
        }
        else
        {
            throw new IllegalArgumentException("Unsupported protection prayer");
        }
        if (prayerActive(prayer) == active)
        {
            return true;
        }
        // The helper opens the Prayer tab itself, so a hidden button costs no separate tab action.
        boolean switchTab = !WidgetQueries.visible(client.getWidget(id));
        BooleanSupplier allowed = () -> interactionReady() && prayerActive(prayer) != active && (!active || prayerPoints() > 0);
        return queueHelper(allowed, () -> api.prayer(prayer, active, switchTab), callback);
    }

    /** Our own item on the tile we stand on: chest rewards land there when the inventory is full. */
    public boolean lootOnGround()
    {
        return ownGroundItem(playerTile()) != null;
    }

    public boolean pickUpLoot(Runnable callback)
    {
        Tile tile = playerTile();
        TileItem item = ownGroundItem(tile);
        if (item == null)
        {
            return false;
        }
        BooleanSupplier allowed = () -> interactionReady() && freeSlots() > 0 && ownGroundItem(playerTile()) == item;
        return queueHelper(allowed, () -> api.pickUp(tile, item), callback);
    }

    private Tile playerTile()
    {
        WorldPoint point = position();
        LocalPoint local = point == null ? null
            : LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
        return local == null ? null
            : client.getTopLevelWorldView().getScene().getTiles()[point.getPlane()][local.getSceneX()][local.getSceneY()];
    }

    public static final int EMPTY_VIAL = 229;

    static TileItem ownGroundItem(Tile tile)
    {
        List<TileItem> items = tile == null ? null : tile.getGroundItems();
        return items == null ? null : items.stream()
            // Empty vials we drop are ours too; leaving them is the point of dropping them.
            .filter(item -> item.getOwnership() == TileItem.OWNERSHIP_SELF && item.getId() != EMPTY_VIAL)
            .findFirst().orElse(null);
    }

    public int openTab()
    {
        return client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    }

    public boolean idle()
    {
        Player player = player();
        return player != null && player.getAnimation() == -1;
    }

    public NPC brotherNpc()
    {
        return client.getNpcs().stream().filter(npc -> Brother.fromNpcName(npc.getName()) != null
            && !npc.isDead() && TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS)
            .min(Comparator.comparingInt(npc -> TargetRange.npcDistance(client, npc))).orElse(null);
    }

    public boolean attack(NPC npc, Runnable callback)
    {
        return interactions.npc(npc, "Attack", this::interactionReady, callback);
    }

    public GameObject object(int... ids)
    {
        return SceneQueries.gameObjects(client).stream()
            .filter(object -> Arrays.stream(ids).anyMatch(id -> id == object.getId())
                && TargetRange.objectDistance(client, object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS)
            .min(Comparator.comparingInt(object -> TargetRange.objectDistance(client, object))).orElse(null);
    }

    public boolean objectAction(GameObject object, String option, Runnable callback)
    {
        return interactions.object(object, option, this::interactionReady, callback);
    }

    public boolean visible(int id)
    {
        return WidgetQueries.visible(client.getWidget(id));
    }

    public int model(int id)
    {
        Widget widget = client.getWidget(id);
        return WidgetQueries.visible(widget) ? widget.getModelId() : -1;
    }

    public boolean widget(int id, String option, BooleanSupplier allowed, Runnable callback)
    {
        return interactions.widget(id, option, allowed, callback);
    }

    public boolean rewardsCloseVisible()
    {
        return WidgetQueries.visible(BarrowsInteractions.rewardsCloseButton(client));
    }

    public boolean closeRewards(Runnable callback)
    {
        return interactions.closeRewards(() -> interactionReady() && visible(155 << 16), callback);
    }

    public boolean dialogueOption(int index, BooleanSupplier allowed, Runnable callback)
    {
        BooleanSupplier live = () -> interactionReady() && allowed.getAsBoolean() && visible(DIALOGUE_OPTIONS);
        if (!live.getAsBoolean())
        {
            return false;
        }
        return queueHelper(live, () -> api.dialogue(index), callback);
    }

    public boolean interactionReady()
    {
        if (walking != null && walking.isActive())
        {
            walking.cancel();
        }
        return ready() && (walking == null || !walking.isStopping());
    }

    public boolean walk(WorldPoint point, String label)
    {
        if (!ready() || worker != null && worker.isBusy())
        {
            return false;
        }
        status("Walking to " + label);
        Walking.State state = walking.walkTo(point, label);
        if (state == Walking.State.FAILED)
        {
            fail(walking.getFailureReason());
        }
        return state == Walking.State.ARRIVED;
    }

    public boolean walkToBank()
    {
        if (!tripPending() || inside())
        {
            return false;
        }
        return (atBankTravelArrival() || atSelectedBank()) && walk(bankDestination(), "Ferox bank");
    }

    public WorldPoint bankDestination()
    {
        return new WorldPoint(3130, 3632, 0);
    }

    public boolean atBankTravelArrival()
    {
        return position() != null && position().distanceTo(new WorldPoint(3151, 3636, 0)) <= 8;
    }

    public boolean atSelectedBank()
    {
        return position() != null && position().distanceTo(bankDestination()) <= 12;
    }

    public boolean hasBankTravelRing()
    {
        return bankTravelRing() > 0;
    }

    private int bankTravelRing()
    {
        for (int id : BANK_RING_IDS)
        {
            if (count(id) > 0 || wearing(id))
            {
                return id;
            }
        }
        return -1;
    }

    public int bankReplacementRing()
    {
        for (int id : BANK_RING_IDS)
        {
            if (bankHas(id))
            {
                return id;
            }
        }
        return -1;
    }

    public String bankTravelProblem()
    {
        return hasBankTravelRing() ? null : "Carry or wear a charged Ring of dueling for Ferox bank travel";
    }

    public String bankTravelStartProblem()
    {
        return bankingOpen() || atSelectedBank() ? null : bankTravelProblem();
    }

    public boolean requestBankTravel(Runnable callback)
    {
        return requestRingTravel(this::tripPending, callback);
    }

    public boolean requestEmergencyTravel(Runnable callback)
    {
        return requestRingTravel(() -> true, callback);
    }

    private boolean requestRingTravel(BooleanSupplier context, Runnable callback)
    {
        int id = bankTravelRing();
        BooleanSupplier allowed = () -> context.getAsBoolean() && bankTeleportLocationAllowed()
            && bankTravelProblem() == null;
        if (id <= 0 || !allowed.getAsBoolean())
        {
            return false;
        }
        if (count(id) > 0)
        {
            return inventory(id, "Ferox Enclave", "Rub", allowed, callback);
        }
        int ringWidget = ((387 << 16) | 24);
        if (!visible(ringWidget))
        {
            return requestTab(InterfaceTab.EQUIPMENT);
        }
        Widget observed = client.getWidget(ringWidget);
        int optionIndex = WidgetQueries.indexOf(observed.getActions(), "Ferox Enclave");
        BooleanSupplier live = () -> allowed.getAsBoolean() && interactionReady() && wearing(id)
            && observed == client.getWidget(ringWidget) && WidgetQueries.visible(observed) && WidgetQueries.showsItem(observed, id)
            && optionIndex >= 0 && WidgetQueries.indexOf(observed.getActions(), "Ferox Enclave") == optionIndex;
        if (!live.getAsBoolean())
        {
            return false;
        }
        return queueHelper(live, () -> api.equippedBankRing(id, "Ferox Enclave"), callback);
    }

    public boolean selectBankTravelDestination(Runnable callback)
    {
        BooleanSupplier live = () -> tripPending() && bankTeleportLocationAllowed() && hasBankTravelRing() && interactionReady()
            && api.bankDialogueReady("Ferox Enclave");
        if (!live.getAsBoolean())
        {
            return false;
        }
        return queueHelper(live, () -> api.bankRingDestination("Ferox Enclave"), callback);
    }

    public boolean bankTravelDialogueVisible()
    {
        return tripPending() && api.bankDialogueReady("Ferox Enclave");
    }

    public boolean bankingOpen()
    {
        return interactions.bankingOpen();
    }

    public boolean bankCloseObserved()
    {
        return !bankingOpen() && client.getVarbitValue(12393) == 0;
    }

    public boolean feroxRestored()
    {
        if (client.getEnergy() < 10_000)
        {
            return false;
        }
        for (Skill skill : Skill.values())
        {
            if (skill != Skill.OVERALL && client.getBoostedSkillLevel(skill) < client.getRealSkillLevel(skill))
            {
                return false;
            }
        }
        return true;
    }

    public boolean walkToFeroxPool()
    {
        return tripPending()
            && !inside() && !bankingOpen() && walk(new WorldPoint(3130, 3634, 0), "Ferox Pool of Refreshment");
    }

    public boolean drinkFeroxPool(Runnable callback)
    {
        BooleanSupplier allowed = () -> tripPending() && !inside() && !bankingOpen() && !feroxRestored() && interactionReady();
        if (!allowed.getAsBoolean())
        {
            return false;
        }
        GameObject pool = object(39651);
        if (pool == null || pool.getWorldLocation().distanceTo(new WorldPoint(3128, 3635, 0)) > 6)
        {
            return false;
        }
        return interactions.object(pool, "Drink", allowed, callback);
    }

    public boolean openBank(Runnable callback)
    {
        WorldPoint bankPoint = bankDestination();
        for (GameObject object : SceneQueries.nearbyGameObjects(client))
        {
            if (object.getWorldLocation().distanceTo(bankPoint) <= 12
                && objectAction(object, object.getId() == 26711 ? "Use" : "Bank", callback))
            {
                return true;
            }
        }
        for (NPC npc : client.getNpcs())
        {
            if (npc.getWorldLocation().distanceTo(bankPoint) <= 12 && interactions.npc(npc, "Bank", this::interactionReady, callback))
            {
                return true;
            }
        }
        return false;
    }

    private boolean keep(int id)
    {
        return id == 952 || id == config.foodId() || id == travelItem(config.travelMethod())
            || Arrays.stream(BANK_RING_IDS).anyMatch(value -> id == value)
            || PRAYER_POTIONS.contains(id)
            || gearIds(config.magicGear()).contains(id) || gearIds(config.rangedGear()).contains(id)
            || gearIds(config.meleeGear()).contains(id) || gearIds(config.keepItems()).contains(id) || restockItems().containsKey(id);
    }

    public int lootCount()
    {
        return Arrays.stream(InventoryQueries.items(client)).filter(item -> item != null && item.getId() > 0 && !keep(item.getId())).mapToInt(Item::getQuantity).reduce(0, (total, amount) -> (int) Math.min(Integer.MAX_VALUE, (long) total + amount));
    }

    public boolean hasLoot()
    {
        return Arrays.stream(InventoryQueries.items(client)).anyMatch(item -> item != null && item.getId() > 0 && !keep(item.getId()));
    }

    public boolean depositLoot(Runnable callback)
    {
        return interactions.deposit(id -> !keep(id), this::interactionReady, callback);
    }

    public boolean withdrawOne(int id, Runnable callback)
    {
        if (client.getVarbitValue(3958) != 0)
        {
            fail("Set the bank withdrawal mode to Item, then enable the plugin again");
            return false;
        }
        return interactions.withdrawOne(id, () -> bankingOpen() && client.getVarbitValue(3958) == 0, callback);
    }

    /** Withdraws an amount in one request; more than one item uses Withdraw-X. */
    public boolean withdraw(int id, int amount, Runnable callback)
    {
        if (amount <= 1)
        {
            return withdrawOne(id, callback);
        }
        if (client.getVarbitValue(3958) != 0)
        {
            fail("Set the bank withdrawal mode to Item, then enable the plugin again");
            return false;
        }
        return interactions.withdraw(id, amount, () -> bankingOpen() && client.getVarbitValue(3958) == 0
            && (stackable(id) || freeSlots() >= amount), callback);
    }

    public int bankCount(int id)
    {
        return interactions.bankCount(id);
    }

    public boolean stackable(int id)
    {
        ItemComposition item = client.getItemDefinition(id);
        return item != null && item.isStackable();
    }

    public boolean closeBank(Runnable callback)
    {
        return interactions.closeBank(this::interactionReady, callback);
    }

    public boolean ensureSpade()
    {
        return count(952) > 0;
    }

    private int travelItem(TravelMethod method)
    {
        if (method == null)
        {
            return -1;
        }
        switch (method)
        {
            case BARROWS_TABLET:
                return 19629;
            case MORTTON_SCROLL:
                return 12406;
            case MORYTANIA_LEGS_3:
                return 13114;
            case MORYTANIA_LEGS_4:
                return 13115;
            default:
                return -1;
        }
    }

    /** In a crypt, in the tunnels or on the Barrows surface. */
    public boolean atBarrows()
    {
        return inside() || atBarrowsSurface();
    }

    /** Current Hitpoints are at or below the emergency percentage of maximum Hitpoints. */
    public boolean emergencyHealth()
    {
        return hp() * 100 <= config().emergencyAt() * maxHp();
    }

    public boolean atBarrowsSurface()
    {
        WorldPoint point = position();
        return point != null && point.getPlane() == 0
            && point.getX() >= 3530 && point.getX() <= 3590
            && point.getY() >= 3260 && point.getY() <= 3330;
    }

    public boolean atTravelArrival(TravelMethod method)
    {
        WorldPoint point = position();
        if (point == null || point.getPlane() != 0 || method == null)
        {
            return false;
        }
        WorldPoint destination;
        switch (method)
        {
            case BARROWS_TABLET:
            case ARCEUUS_SPELL:
                destination = new WorldPoint(3565, 3315, 0);
                break;
            case MORTTON_SCROLL:
                destination = new WorldPoint(3489, 3288, 0);
                break;
            case MORYTANIA_LEGS_3:
            case MORYTANIA_LEGS_4:
                destination = new WorldPoint(3482, 3230, 0);
                break;
            case SHADES_MINIGAME:
                destination = new WorldPoint(3506, 3304, 0);
                break;
            default:
                return false;
        }
        return point.distanceTo(destination) <= 8;
    }

    /** Minutes left on the Shades of Mort'ton teleport cooldown, or 0 when it is ready. */
    public long shadesCooldown()
    {
        return ShadesTeleport.cooldownMinutes(client);
    }

    public String travelProblem(TravelMethod method)
    {
        if (method == null)
        {
            return "Choose a Barrows travel method";
        }
        if (!bankCloseObserved())
        {
            return "Close the bank or busy interface before travelling to Barrows";
        }
        if ((method == TravelMethod.BARROWS_TABLET || method == TravelMethod.MORTTON_SCROLL
            || method == TravelMethod.ARCEUUS_SPELL) && client.getVarpValue(302) < 61)
        {
            return "Speak to Drezel and enter Morytania before teleporting";
        }
        int item = travelItem(method);
        if (item > 0 && count(item) == 0)
        {
            return "Carry " + method + " in your inventory";
        }
        if (method == TravelMethod.ARCEUUS_SPELL)
        {
            if (!Rs2Magic.isSpellbook(Rs2Spellbook.ARCEUUS))
            {
                return "Select the Arceuus spellbook before starting";
            }
            if (client.getBoostedSkillLevel(Skill.MAGIC) < 83 || !Rs2Magic.hasRequiredRunes(Rs2Spells.BARROWS_TELEPORT))
            {
                return "Barrows Teleport requires level 83 Magic and its runes";
            }
        }
        if (method == TravelMethod.SHADES_MINIGAME)
        {
            return ShadesTeleport.unavailable(client);
        }
        return null;
    }

    public boolean requestTravel(TravelMethod method, Runnable callback)
    {
        int item = travelItem(method);
        if (item > 0)
        {
            String option = method == TravelMethod.BARROWS_TABLET ? "Break"
                : method == TravelMethod.MORTTON_SCROLL ? "Teleport" : "Burgh Teleport";
            return inventory(item, option, () -> config.travelMethod() == method && travelProblem(method) == null, callback);
        }
        BooleanSupplier allowed = () -> interactionReady() && config.travelMethod() == method
            && travelProblem(method) == null;
        if (!allowed.getAsBoolean())
        {
            return false;
        }
        return queueHelper(allowed, () -> method == TravelMethod.ARCEUUS_SPELL
            ? Rs2Magic.quickCast(Rs2Spells.BARROWS_TELEPORT) : ShadesTeleport.teleport(), callback);
    }

    public int freeSlots()
    {
        return InventoryQueries.freeSlots(client);
    }

    public int currentTunnelRoom()
    {
        WorldPoint point = position();
        if (!ready() || !TunnelMap.contains(point))
        {
            return -1;
        }
        Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(point).keySet();
        int selected = -1;
        int distance = Integer.MAX_VALUE;
        for (int room = 0; room < TunnelMap.ROOM_COUNT; room++)
        {
            WorldPoint center = TunnelMap.roomCenter(room);
            int candidate = point.distanceTo(center);
            if (reachable.contains(center) && candidate < distance)
            {
                selected = room;
                distance = candidate;
            }
        }
        return selected;
    }

    private TileObject nearestDoor(int index)
    {
        return nearestDoor(index, -1);
    }

    private TileObject nearestDoor(int index, int destinationRoom)
    {
        TileObject nearest = null;
        int best = Integer.MAX_VALUE;
        int bestRoomDistance = Integer.MAX_VALUE;
        WorldPoint destination = destinationRoom >= 0 && destinationRoom < TunnelMap.ROOM_COUNT
            ? TunnelMap.roomCenter(destinationRoom) : null;
        for (Tile tile : SceneQueries.playerTiles(client))
        {
            TileObject object = tile.getWallObject();
            int distance = TargetRange.objectDistance(client, object);
            int roomDistance = object == null || destination == null ? 0 : destination.distanceTo(object.getWorldLocation());
            if (object != null && TunnelMap.doorIndex(object.getId()) == index
                && (roomDistance < bestRoomDistance || roomDistance == bestRoomDistance && distance < best)
                && WidgetQueries.objectActionIndex(client, object, "Open") >= 0
                && Rs2Tile.isTileObjectReachable(object))
            {
                best = distance;
                bestRoomDistance = roomDistance;
                nearest = object;
            }
        }
        return nearest;
    }

    /**
     * The game removes the door at a tile while the player crosses it and sends it again when the
     * crossing ends. Each door kind has four copies in the tunnels, so only this tile counts.
     */
    public boolean doorLoaded(WorldPoint point)
    {
        for (Tile tile : SceneQueries.playerTiles(client))
        {
            TileObject object = tile.getWallObject();
            if (object != null && TunnelMap.doorIndex(object.getId()) >= 0 && point.equals(object.getWorldLocation()))
            {
                return true;
            }
        }
        return false;
    }

    public boolean door(int index, Runnable callback)
    {
        TileObject object = nearestDoor(index);
        return object != null && tileAction(object, "Open", callback);
    }

    public boolean door(int index, int destinationRoom, Runnable callback)
    {
        TileObject object = nearestDoor(index, destinationRoom);
        return object != null && tileAction(object, "Open", callback);
    }

    public boolean tileAction(TileObject object, String option, Runnable callback)
    {
        return interactions.object(object, option, this::interactionReady, callback);
    }

    public GameObject namedObject(String name)
    {
        return SceneQueries.nearbyGameObjects(client).stream()
            .filter(object -> objectName(object).equalsIgnoreCase(name)
                && WidgetQueries.objectActionIndex(client, object, name.equalsIgnoreCase("Ladder") || name.equalsIgnoreCase("Staircase") ? "Climb-up" : "Search") >= 0)
            .findFirst().orElse(null);
    }

    private String objectName(GameObject object)
    {
        ObjectComposition definition = WidgetQueries.objectDefinition(client, object);
        return definition == null || definition.getName() == null ? "" : definition.getName();
    }

    public boolean bankHas(int id)
    {
        return interactions.hasBankItem(id);
    }

    public boolean tunnelMonster(Runnable callback)
    {
        NPC npc = nearestTunnelMonster(client.getNpcs(), Rs2Tile.getReachableTilesFromTile(position()).keySet(),
            candidate -> TargetRange.npcDistance(client, candidate), potential(), tunnelAttacker());
        if (npc == null)
        {
            return false;
        }
        if (player().getInteracting() == npc)
        {
            return true;
        }
        return attack(npc, callback);
    }

    public NPC tunnelAttacker()
    {
        Actor target = player().getInteracting();
        return tunnelAttacker(client.getNpcs(), player(), target instanceof NPC ? (NPC) target : null, rejectedTarget,
            rejectedTick, npc -> swings.getOrDefault(npc, Integer.MIN_VALUE));
    }

    /** Records a tunnel monster animating while it faces us: in single combat only the attacker swings. */
    public void npcAnimated(NPC npc)
    {
        if (npc != null && npc.getAnimation() != -1 && player() != null && npc.getInteracting() == player()
            && TUNNEL_MONSTERS.contains(npc.getName()))
        {
            swings.put(npc, tick());
        }
    }

    public int rejectedTargetIndex()
    {
        return rejectedTarget == null ? -1 : rejectedTarget.getIndex();
    }

    /** Records the target the game refused because another monster is already fighting us. */
    public void attackRejected()
    {
        Actor target = player() == null ? null : player().getInteracting();
        rejectedTarget = target instanceof NPC ? (NPC) target : null;
        rejectedTick = tick();
    }

    static NPC tunnelAttacker(List<NPC> npcs, Actor player, NPC target, NPC rejected,
        int rejectedTick, ToIntFunction<NPC> lastSwing)
    {
        // A monster we target also faces us, so facing alone cannot tell two same-named monsters apart.
        // A monster that swung at us stays the attacker while it lives and faces us; swings can be ticks apart.
        List<NPC> facing = npcs.stream().filter(candidate -> !candidate.isDead()
            && (candidate != rejected || lastSwing.applyAsInt(candidate) > rejectedTick)
            && TUNNEL_MONSTERS.contains(candidate.getName()) && candidate.getInteracting() == player)
            .collect(Collectors.toList());
        NPC swinging = facing.stream().filter(candidate -> lastSwing.applyAsInt(candidate) != Integer.MIN_VALUE)
            .max(Comparator.comparingInt(lastSwing)).orElse(null);
        if (swinging != null)
        {
            return swinging;
        }
        return facing.contains(target) ? target : facing.stream().findFirst().orElse(null);
    }

    static NPC nearestTunnelMonster(List<NPC> npcs, Set<WorldPoint> reachable, ToIntFunction<NPC> distance, int potential,
        NPC attacker)
    {
        // The tunnels are single combat: only the exact monster already attacking us can be fought.
        // Monsters behind a closed door are in range but cannot be reached.
        return npcs.stream().filter(candidate -> !candidate.isDead()
            && (attacker == null || candidate == attacker)
            && TUNNEL_MONSTERS.contains(candidate.getName())
            && distance.applyAsInt(candidate) <= 12
            && reachable.contains(candidate.getWorldLocation())
            && candidate.getCombatLevel() > 0 && potential + candidate.getCombatLevel() <= 880)
            .min(Comparator.comparingInt(distance)).orElse(null);
    }

    public WorldPoint doorDestination(int index)
    {
        TileObject object = nearestDoor(index);
        return object == null ? null : object.getWorldLocation();
    }

    public WorldPoint doorDestination(int index, int destinationRoom)
    {
        TileObject object = nearestDoor(index, destinationRoom);
        return object == null ? null : object.getWorldLocation();
    }
}
