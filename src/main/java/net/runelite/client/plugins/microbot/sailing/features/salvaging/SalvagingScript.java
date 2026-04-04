package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.sailing.AlchOrder;
import net.runelite.client.plugins.microbot.sailing.SailingConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class SalvagingScript {

    /** First decimal number in the occupied-line text (e.g. {@code 160} or {@code 160 / 160}). */
    private static final Pattern CARGO_HOLD_FIRST_NUMBER = Pattern.compile("(\\d+)");

    private static final int SIZE_SALVAGEABLE_AREA = 15;
    private static final int MIN_INVENTORY_FULL = 24;
    private static final int SALVAGE_TIMEOUT = 20000;
    private static final int DEPLOY_TIMEOUT = 5000;
    private static final int WAIT_TIME = 5000;
    private static final int WAIT_TIME_MAX = 10000;
    private static final int CARGO_HOLD_UI_TIMEOUT_MS = 8000;
    private static final int CARGO_HOLD_WITHDRAW_FAIL_THRESHOLD = 5;
    private static final int CARGO_HOLD_WITHDRAW_NO_GAIN_THRESHOLD = 5;
    /** Wait for inventory to reflect the withdraw after the salvage slot is clicked. */
    private static final int CARGO_HOLD_WITHDRAW_INVENTORY_TIMEOUT_MS = 12000;
    /** Pause after clicking the salvage slot so the client can apply the withdraw before inventory polling or Escape. */
    private static final int CARGO_HOLD_POST_WITHDRAW_CLICK_MIN_MS = 2200;
    private static final int CARGO_HOLD_POST_WITHDRAW_CLICK_MAX_MS = 5000;
    /** After salvage appears in inventory, wait again before Escape so the hold does not close mid-pipeline. */
    private static final int CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MIN_MS = 800;
    private static final int CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MAX_MS = 2200;
    /** Periodically re-open the hold and count ITEMS widgets so &quot;full&quot; stays accurate without item-container tracking. */
    private static final int CARGO_HOLD_WIDGET_RESYNC_MIN_MS = 4500;
    private static final int CARGO_HOLD_SCENE_SCAN_RADIUS = 32;
    private final Rs2TileObjectCache tileObjectCache;
    @SuppressWarnings("unused")
    private final Rs2BoatCache boatCache;
    private final EventBus eventBus;

    /**
     * Shipwreck lists rebuilt each {@link GameTick} on the client thread by scanning {@link Client#getTopLevelWorldView()}
     * (the sea layer). Plugin Hub &quot;Sailing&quot; uses game object spawn/despawn events instead; both target the same
     * {@link GameObject} ids. Microbot&apos;s tile cache and {@link Rs2GameObject} iterate the player world view scene,
     * which misses at-sea wrecks.
     */
    private final Map<String, Rs2TileObjectModel> activeWreckByKey = new HashMap<>();
    private final Map<String, Rs2TileObjectModel> inactiveWreckByKey = new HashMap<>();
    private volatile List<Rs2TileObjectModel> activeWreckSnapshot = List.of();
    private volatile List<Rs2TileObjectModel> inactiveWreckSnapshot = List.of();

    /** Max cargo slots for this boat tier ({@link CargoHoldObjectIds#ID_TO_CAPACITY}). */
    private int cargoHoldCapacity = -1;
    /** Occupied slots: first number from {@link CargoHoldInterfaceWidgets} text line after the hold is open; else ITEMS grid count. */
    private int cargoHoldCount = -1;
    /** Salvage stacks in the hold from the same grid (item name contains &quot;salvage&quot;; one slot = one stack). */
    private int cargoHoldSalvageStackCount = -1;
    private boolean cargoHoldProcessing = false;
    private int lastCargoHoldObjectId = -1;
    private int cargoHoldWithdrawFailures = 0;
    /** Consecutive withdraw clicks that did not change inventory (separate from open failures). */
    private int cargoHoldWithdrawNoGainStreak = 0;
    private long lastCargoHoldInitAttemptMs;
    private long lastCargoHoldInitHintLogMs;
    private long lastCargoHoldWidgetResyncMs;

    @Inject
    public SalvagingScript(Rs2TileObjectCache tileObjectCache, Rs2BoatCache boatCache, EventBus eventBus) {
        this.tileObjectCache = tileObjectCache;
        this.boatCache = boatCache;
        this.eventBus = eventBus;
    }

    public void register() {
        eventBus.register(this);
    }

    public void unregister() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        rebuildShipwreckMapsFromTopLevelScene();
        activeWreckSnapshot = List.copyOf(activeWreckByKey.values());
        inactiveWreckSnapshot = List.copyOf(inactiveWreckByKey.values());
    }

    /**
     * Full top-level scene pass (sea layer), same objects the client draws for distant water tiles.
     */
    private void rebuildShipwreckMapsFromTopLevelScene() {
        activeWreckByKey.clear();
        inactiveWreckByKey.clear();
        Client client = Microbot.getClient();
        if (client == null) {
            return;
        }
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) {
            return;
        }
        Scene scene = wv.getScene();
        if (scene == null) {
            return;
        }
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            return;
        }
        int plane = wv.getPlane();
        if (plane < 0) {
            return;
        }
        if (plane >= tiles.length) {
            return;
        }
        Tile[][] planeTiles = tiles[plane];
        if (planeTiles == null) {
            return;
        }
        int maxX = Math.min(Constants.SCENE_SIZE, planeTiles.length);
        for (int x = 0; x < maxX; x++) {
            Tile[] column = planeTiles[x];
            if (column == null) {
                continue;
            }
            int maxY = Math.min(Constants.SCENE_SIZE, column.length);
            for (int y = 0; y < maxY; y++) {
                Tile tile = column[y];
                if (tile == null) {
                    continue;
                }
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject go : gameObjects) {
                        if (go == null) {
                            continue;
                        }
                        if (!go.getSceneMinLocation().equals(tile.getSceneLocation())) {
                            continue;
                        }
                        considerShipwreckTileObjectForRebuild(go);
                    }
                }
                DecorativeObject dec = tile.getDecorativeObject();
                if (dec != null) {
                    considerShipwreckTileObjectForRebuild(dec);
                }
            }
        }
    }

    private void considerShipwreckTileObjectForRebuild(TileObject obj) {
        int id = obj.getId();
        if (SalvageObjectIds.ACTIVE_SHIPWRECK_IDS.contains(id)) {
            activeWreckByKey.put(dedupeKey(obj), new Rs2TileObjectModel(obj));
            return;
        }
        if (SalvageObjectIds.INACTIVE_SHIPWRECK_IDS.contains(id)) {
            inactiveWreckByKey.put(dedupeKey(obj), new Rs2TileObjectModel(obj));
        }
    }

    private static String dedupeKey(TileObject o) {
        WorldPoint p = o.getWorldLocation();
        return o.getId() + ":" + p.getX() + ":" + p.getY() + ":" + p.getPlane();
    }

    private static List<Rs2TileObjectModel> mergeDistinctTileObjectLists(List<Rs2TileObjectModel> a, List<Rs2TileObjectModel> b) {
        Map<String, Rs2TileObjectModel> byKey = new LinkedHashMap<>();
        for (Rs2TileObjectModel m : a) {
            byKey.put(tileObjectDedupeKey(m), m);
        }
        for (Rs2TileObjectModel m : b) {
            String key = tileObjectDedupeKey(m);
            if (!byKey.containsKey(key)) {
                byKey.put(key, m);
            }
        }
        return List.copyOf(byKey.values());
    }

    private static String tileObjectDedupeKey(Rs2TileObjectModel m) {
        return dedupeKey(m);
    }

    public List<Rs2TileObjectModel> getActiveWrecks() {
        return activeWreckSnapshot;
    }

    public List<Rs2TileObjectModel> getInactiveWrecks() {
        return inactiveWreckSnapshot;
    }

    public void run(SailingConfig config) {
        try {
            var player = new Rs2PlayerModel();

            if (!config.useCargoHold()) {
                resetCargoHoldState();
            } else {
                if (cargoHoldCapacity == -1) {
                    initCargoHold();
                }
            }

            if (isPlayerAnimating(player)) {
                log.info("Currently salvaging, waiting...");
                sleep(WAIT_TIME, WAIT_TIME_MAX);
                return;
            }

            if (config.useCargoHold()) {
                if (handleCargoHoldMode(config, player)) {
                    return;
                }
            }

            if (tryRunIdleInventoryCleanup(config)) {
                return;
            }

            if (isInventoryFull()) {
                if (config.useCargoHold() && cargoHoldProcessing && hasSalvageItems()) {
                    log.info("Inventory full during cargo-hold processing; processing salvage at station before more withdraws");
                } else {
                    log.info("Inventory full, handling before salvaging");
                }
                handleFullInventory(config, player);
                return;
            }

            var nearbyWreck = findNearestWreck(player.getWorldLocation());
            if (nearbyWreck == null) {
                log.info("No shipwreck found nearby");
                sleep(WAIT_TIME);
                return;
            }

            deploySalvagingHook(player);

        } catch (Exception ex) {
            log.error("Error in salvaging script", ex);
        }
    }

    private void resetCargoHoldState() {
        cargoHoldCapacity = -1;
        cargoHoldCount = -1;
        cargoHoldSalvageStackCount = -1;
        cargoHoldProcessing = false;
        lastCargoHoldObjectId = -1;
        cargoHoldWithdrawFailures = 0;
        cargoHoldWithdrawNoGainStreak = 0;
        lastCargoHoldInitAttemptMs = 0;
        lastCargoHoldInitHintLogMs = 0;
        lastCargoHoldWidgetResyncMs = 0;
    }

    /**
     * @return true if this tick is fully handled and {@link #run(SailingConfig)} should return.
     */
    private boolean handleCargoHoldMode(SailingConfig config, Rs2PlayerModel player) {
        syncCargoHoldIfObjectVariantChanged();
        if (cargoHoldCapacity == -1) {
            initCargoHold();
            if (cargoHoldCapacity == -1) {
                logCargoHoldInitThrottled(
                        "Cargo hold not initialized yet; salvaging continues. Stand on your boat near the hold.");
                return false;
            }
        }

        if (!cargoHoldProcessing) {
            if (hasNearbySalvageableWreck(player.getWorldLocation()) || hasSalvageItems()) {
                maybeResyncCargoHoldCountsFromOpenUi();
            }
        }

        if (cargoHoldProcessing || shouldProcessCargoHold()) {
            if (!cargoHoldProcessing && shouldProcessCargoHold()) {
                cargoHoldProcessing = true;
                log.info("Cargo hold processing phase started (full or near capacity)");
            }
            if (cargoHoldSalvageStackCount == 0) {
                cargoHoldProcessing = false;
                cargoHoldWithdrawFailures = 0;
                cargoHoldWithdrawNoGainStreak = 0;
                log.info("No salvage left in cargo hold, resuming normal salvaging");
                return false;
            }
            if (hasSalvageItems() && canDepositSalvageToCargoHold()
                    && !suppressSalvageDepositDuringCargoHoldProcessing()) {
                depositToCargoHold();
                return true;
            }
            if (isInventoryFull()) {
                handleFullInventory(config, player);
                return true;
            }
            boolean fillingInventoryFromHold = !isInventoryFull()
                    && (cargoHoldSalvageStackCount > 0
                    || (cargoHoldSalvageStackCount < 0 && cargoHoldCount > 0));
            if (!fillingInventoryFromHold && !hasNearbySalvageableWreck(player.getWorldLocation())) {
                return false;
            }
            processCargoHoldWithdrawStep();
            return true;
        }

        return false;
    }

    private void syncCargoHoldIfObjectVariantChanged() {
        if (cargoHoldCapacity < 0) {
            return;
        }
        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            return;
        }
        int id = hold.getId();
        if (lastCargoHoldObjectId < 0) {
            lastCargoHoldObjectId = id;
            return;
        }
        if (id == lastCargoHoldObjectId) {
            return;
        }
        lastCargoHoldObjectId = id;
        Integer cap = CargoHoldObjectIds.ID_TO_CAPACITY.get(id);
        if (cap != null) {
            cargoHoldCapacity = cap;
        }
        clampCargoHoldCount();
        clampCargoHoldSalvageStackCount();
    }

    private void initCargoHold() {
        if (cargoHoldCapacity != -1) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCargoHoldInitAttemptMs < 2500) {
            return;
        }
        lastCargoHoldInitAttemptMs = now;

        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            logCargoHoldInitThrottled("Cargo hold: no cargo hold object in this scene (stand on your boat).");
            return;
        }
        Integer capObj = CargoHoldObjectIds.ID_TO_CAPACITY.get(hold.getId());
        if (capObj == null) {
            logCargoHoldInitThrottled(
                    "Cargo hold: object id " + hold.getId() + " not mapped to capacity; add it to CargoHoldObjectIds if this is a new boat tier or variant.");
            return;
        }
        int cap = capObj;
        if (!openCargoHoldInterfaceForWithdraw()) {
            logCargoHoldInitThrottled(
                    "Cargo hold: could not open interface for initialization; stand on your boat and use Open on the hold.");
            return;
        }
        sleep(Rs2Random.between(280, 650));
        cargoHoldCapacity = cap;
        if (!readOccupiedCountFromOpenHoldInterface()) {
            cargoHoldCapacity = -1;
            cargoHoldCount = -1;
            cargoHoldSalvageStackCount = -1;
            logCargoHoldInitThrottled(
                    "Cargo hold: could not read hold contents after opening; check client/game updates.");
            closeCargoHoldInterface();
            return;
        }
        closeCargoHoldInterface();
        lastCargoHoldObjectId = hold.getId();
        lastCargoHoldInitHintLogMs = 0;
        lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        log.info(
                "Cargo hold initialized: capacity={} slots, occupied={}, salvage stacks={}; deposits use in-UI Deposit inventory.",
                cargoHoldCapacity, cargoHoldCount, cargoHoldSalvageStackCount);
    }

    private void logCargoHoldInitThrottled(String message) {
        long t = System.currentTimeMillis();
        if (t - lastCargoHoldInitHintLogMs < 15000) {
            return;
        }
        lastCargoHoldInitHintLogMs = t;
        log.info(message);
    }

    /**
     * Resolves the cargo hold on the client thread: tile cache merge, then {@link Rs2GameObject} scene scan (same gap as
     * wrecks), then name match, then nearest to the player.
     */
    private Rs2TileObjectModel findCargoHold() {
        return Microbot.getClientThread().invoke(this::findCargoHoldOnClientThread);
    }

    private Rs2TileObjectModel findCargoHoldOnClientThread() {
        List<Rs2TileObjectModel> fromWorldView = tileObjectCache.query()
                .fromWorldView()
                .where(this::isCargoHoldTileObject)
                .toList();
        List<Rs2TileObjectModel> fromDefaultScene = tileObjectCache.query()
                .where(this::isCargoHoldTileObject)
                .toList();
        List<Rs2TileObjectModel> merged = mergeDistinctTileObjectLists(fromWorldView, fromDefaultScene);
        if (merged.isEmpty()) {
            merged = scanCargoHoldObjectsFromScene();
        }
        if (merged.isEmpty()) {
            WorldPoint anchor = Rs2Player.getWorldLocation();
            if (anchor != null) {
                try {
                    TileObject named = Rs2GameObject.getTileObject("Cargo hold", anchor, CARGO_HOLD_SCENE_SCAN_RADIUS);
                    if (named != null) {
                        merged = List.of(new Rs2TileObjectModel(named));
                    }
                } catch (RuntimeException ex) {
                    log.debug("Cargo hold: Rs2GameObject.getTileObject name fallback failed (known issue on some sea scenes)", ex);
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return merged.get(0);
        }
        return merged.stream()
                .min(Comparator.comparingInt(o -> player.distanceTo(o.getWorldLocation())))
                .orElse(null);
    }

    private List<Rs2TileObjectModel> scanCargoHoldObjectsFromScene() {
        WorldPoint anchor = Rs2Player.getWorldLocation();
        if (anchor == null) {
            return List.of();
        }
        try {
            List<?> raw = Rs2GameObject.getAll(o -> CargoHoldObjectIds.ALL_IDS.contains(o.getId()), anchor, CARGO_HOLD_SCENE_SCAN_RADIUS);
            List<Rs2TileObjectModel> out = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof TileObject) {
                    out.add(new Rs2TileObjectModel((TileObject) o));
                }
            }
            return out;
        } catch (RuntimeException ex) {
            log.debug("Cargo hold: Rs2GameObject.getAll scene scan failed", ex);
            return List.of();
        }
    }

    private boolean isCargoHoldTileObject(Rs2TileObjectModel obj) {
        if (CargoHoldObjectIds.ALL_IDS.contains(obj.getId())) {
            return true;
        }
        String name = obj.getName();
        if (name == null) {
            return false;
        }
        return name.toLowerCase().contains("cargo hold");
    }

    private boolean shouldProcessCargoHold() {
        if (cargoHoldCapacity < 0 || cargoHoldCount < 0) {
            return false;
        }
        int freeSlots = cargoHoldCapacity - cargoHoldCount;
        return freeSlots == 0 || freeSlots < Rs2Inventory.emptySlotCount();
    }

    /**
     * True when the hold is initialized and has reported spare capacity and the player is carrying salvage.
     * Intentionally does <em>not</em> use {@link #shouldProcessCargoHold()} — that check compares hold free slots to
     * empty inventory slots, which stays true while the inventory is &quot;full&quot; (24+ items) but still has
     * several empty spaces, and would block in-UI deposit even though the client may still allow it.
     */
    private boolean canDepositSalvageToCargoHold() {
        if (cargoHoldCapacity < 0) {
            return false;
        }
        if (cargoHoldCount < 0) {
            return false;
        }
        int free = cargoHoldCapacity - cargoHoldCount;
        if (free <= 0) {
            return false;
        }
        return Rs2Inventory.count("salvage") > 0;
    }

    private void depositToCargoHold() {
        if (!openCargoHoldInterfaceForWithdraw()) {
            log.info("Cargo hold: could not open interface for deposit");
            return;
        }
        sleep(Rs2Random.between(200, 480));
        int salvageBefore = Rs2Inventory.count("salvage");
        if (salvageBefore <= 0) {
            closeCargoHoldInterface();
            return;
        }
        AtomicBoolean clicked = new AtomicBoolean(false);
        Microbot.getClientThread().invoke(() -> clicked.set(clickDepositInventoryInOpenCargoHold()));
        if (!clicked.get()) {
            log.warn("Cargo hold: Deposit inventory control not found in interface");
            closeCargoHoldInterface();
            return;
        }
        sleep(Rs2Random.between(280, 620));
        sleepUntil(() -> Rs2Inventory.count("salvage") < salvageBefore, SALVAGE_TIMEOUT);
        if (readOccupiedCountFromOpenHoldInterface()) {
            lastCargoHoldWidgetResyncMs = System.currentTimeMillis();
        }
        closeCargoHoldInterface();
    }

    /**
     * Opens the cargo hold panel (Open on world object). Used for withdraw and for in-UI deposit.
     */
    private boolean openCargoHoldInterfaceForWithdraw() {
        if (Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)) {
            return true;
        }
        Rs2TileObjectModel hold = findCargoHold();
        if (hold == null) {
            return false;
        }
        hold.click("Open");
        return sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE), CARGO_HOLD_UI_TIMEOUT_MS);
    }

    /**
     * Reads occupied + salvage counts while the cargo-hold interface is already open. Call
     * {@link #openCargoHoldInterfaceForWithdraw()} (and a short sleep) first when the panel was not open.
     *
     * @return false if the read failed
     */
    private boolean readOccupiedCountFromOpenHoldInterface() {
        int[] grid = Microbot.getClientThread().invoke(this::countOccupiedAndSalvageStacksInOpenHoldInterface);
        if (grid == null) {
            return false;
        }
        applyCargoHoldCountsFromItemGrid(grid);
        return true;
    }

    /**
     * Re-opens the hold on a throttle and re-counts widgets. Only invoked when a wreck is in range or the player is
     * carrying salvage, so idle sailing does not spam open/close.
     */
    private void maybeResyncCargoHoldCountsFromOpenUi() {
        long now = System.currentTimeMillis();
        if (now - lastCargoHoldWidgetResyncMs < CARGO_HOLD_WIDGET_RESYNC_MIN_MS) {
            return;
        }
        boolean wasVisible = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)).orElse(false);
        if (!wasVisible) {
            if (!openCargoHoldInterfaceForWithdraw()) {
                return;
            }
            sleep(Rs2Random.between(180, 420));
        }
        if (!readOccupiedCountFromOpenHoldInterface()) {
            return;
        }
        lastCargoHoldWidgetResyncMs = now;
        if (!wasVisible) {
            closeCargoHoldInterface();
        }
    }

    private boolean clickDepositInventoryInOpenCargoHold() {
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        Widget universe = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
        if (universe == null || universe.isHidden()) {
            return false;
        }
        Widget deposit = client.getWidget(InterfaceID.SailingBoatCargohold.DEPOSITALL_INVENTORY);
        if (deposit != null && !deposit.isHidden()) {
            Rs2Widget.clickWidget(deposit);
            return true;
        }
        Widget target = findDepositInventoryWidget(universe);
        if (target == null) {
            return false;
        }
        Rs2Widget.clickWidget(target);
        return true;
    }

    private static Widget findDepositInventoryWidget(Widget w) {
        if (w == null) {
            return null;
        }
        String[] actions = w.getActions();
        if (actions != null) {
            for (String a : actions) {
                if (a == null) {
                    continue;
                }
                String lower = a.toLowerCase();
                if (lower.contains("deposit") && lower.contains("inventory")) {
                    return w;
                }
            }
        }
        String text = w.getText();
        if (text != null) {
            String lower = text.toLowerCase().replace("<br>", " ");
            if (lower.contains("deposit") && lower.contains("inventory")) {
                return w;
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return null;
        }
        for (Widget c : children) {
            Widget found = findDepositInventoryWidget(c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Applies {@code grid[0]} = occupied slots, {@code grid[1]} = salvage stack count from the ITEMS grid.
     * {@link #cargoHoldCapacity} is unchanged here (set from {@link CargoHoldObjectIds} at init).
     */
    private void applyCargoHoldCountsFromItemGrid(int[] grid) {
        if (grid == null) {
            return;
        }
        if (grid.length < 2) {
            return;
        }
        int occ = Math.max(0, grid[0]);
        int sal = Math.max(0, grid[1]);
        if (cargoHoldCapacity > 0) {
            cargoHoldCount = Math.min(occ, cargoHoldCapacity);
            cargoHoldSalvageStackCount = Math.min(sal, cargoHoldCount);
            return;
        }
        cargoHoldCount = occ;
        cargoHoldSalvageStackCount = Math.min(sal, occ);
    }

    private void closeCargoHoldInterface() {
        if (!Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE)) {
            return;
        }
        sleep(Rs2Random.between(280, 620));
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleep(Rs2Random.between(280, 520));
    }

    /**
     * {@code [0]} = occupied slots, {@code [1]} = salvage-named stacks in the ITEMS grid.
     * Occupied comes from {@link CargoHoldInterfaceWidgets} (client {@code getWidget(943, 4)}) when parseable; else the ITEMS grid walk.
     * Requires the cargo-hold panel to already be open. Client thread only.
     */
    private int[] countOccupiedAndSalvageStacksInOpenHoldInterface() {
        try {
            Client client = Microbot.getClient();
            if (client == null) {
                return null;
            }
            Widget universe = client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);
            if (universe == null || universe.isHidden()) {
                return null;
            }
            Widget items = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
            if (items == null || items.isHidden()) {
                return null;
            }
            int salvageStacks = countSalvageItemSlotsInHoldRecursive(client, items);
            Integer occupiedFromLine = parseOccupiedSlotsFromCargoHoldTextLine(client);
            int occupied;
            if (occupiedFromLine != null) {
                occupied = occupiedFromLine;
            } else {
                occupied = countNonEmptyItemSlotsRecursive(items);
            }
            return new int[] { occupied, salvageStacks };
        } catch (RuntimeException ex) {
            log.debug("Cargo hold: interface read failed", ex);
            return null;
        }
    }

    /**
     * First number in the occupied/capacity text (e.g. {@code 160} from {@code 160 / 160} or a single value).
     */
    private static Integer parseOccupiedSlotsFromCargoHoldTextLine(Client client) {
        Widget w = client.getWidget(
                CargoHoldInterfaceWidgets.CARGO_HOLD_OCCUPIED_TEXT_GROUP,
                CargoHoldInterfaceWidgets.CARGO_HOLD_OCCUPIED_TEXT_CHILD);
        if (w == null) {
            return null;
        }
        if (w.isHidden()) {
            return null;
        }
        String t = w.getText();
        if (t == null) {
            return null;
        }
        if (t.isEmpty()) {
            return null;
        }
        String plain = Text.removeTags(t).replace("<br>", " ").trim();
        Matcher m = CARGO_HOLD_FIRST_NUMBER.matcher(plain);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    private static int countSalvageItemSlotsInHoldRecursive(Client client, Widget w) {
        int count = 0;
        if (w.getItemId() > 0) {
            var def = client.getItemDefinition(w.getItemId());
            if (def != null) {
                String name = def.getName();
                if (name != null) {
                    if (name.toLowerCase().contains("salvage")) {
                        count++;
                    }
                }
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return count;
        }
        for (Widget c : children) {
            if (c == null) {
                continue;
            }
            count += countSalvageItemSlotsInHoldRecursive(client, c);
        }
        return count;
    }

    private static int countNonEmptyItemSlotsRecursive(Widget w) {
        int count = 0;
        if (w.getItemId() > 0) {
            count++;
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return count;
        }
        for (Widget c : children) {
            if (c == null) {
                continue;
            }
            count += countNonEmptyItemSlotsRecursive(c);
        }
        return count;
    }

    private static int countSalvageItemStacksInInventory() {
        int n = 0;
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            String name = item.getName();
            if (name == null) {
                continue;
            }
            if (name.toLowerCase().contains("salvage")) {
                n++;
            }
        }
        return n;
    }

    private void processCargoHoldWithdrawStep() {
        boolean holdWasAlreadyOpen = Rs2Widget.isWidgetVisible(InterfaceID.SailingBoatCargohold.UNIVERSE);
        if (!openCargoHoldInterfaceForWithdraw()) {
            cargoHoldWithdrawFailures++;
            if (cargoHoldWithdrawFailures >= CARGO_HOLD_WITHDRAW_FAIL_THRESHOLD) {
                log.warn("Cargo hold: interface failed to open repeatedly; exiting processing mode");
                cargoHoldProcessing = false;
                cargoHoldWithdrawFailures = 0;
            }
            return;
        }
        cargoHoldWithdrawFailures = 0;

        if (!holdWasAlreadyOpen) {
            sleep(Rs2Random.between(280, 650));
        }
        sleep(Rs2Random.between(120, 320));

        int trackedSalvageBeforeRead = cargoHoldSalvageStackCount;
        readOccupiedCountFromOpenHoldInterface();
        if (cargoHoldSalvageStackCount == 0) {
            if (trackedSalvageBeforeRead > 0) {
                sleep(Rs2Random.between(450, 900));
                readOccupiedCountFromOpenHoldInterface();
            }
        }
        if (cargoHoldSalvageStackCount == 0) {
            closeCargoHoldInterface();
            return;
        }

        int salvageBefore = Rs2Inventory.count("salvage");
        AtomicBoolean invoked = new AtomicBoolean(false);
        Microbot.getClientThread().invoke(() -> invoked.set(invokeWithdrawOneSalvageStackFromCargoHoldUi()));
        if (!invoked.get()) {
            log.info("Cargo hold: no salvage stack in hold UI; re-reading occupied count from open interface");
            readOccupiedCountFromOpenHoldInterface();
            closeCargoHoldInterface();
            if (cargoHoldSalvageStackCount == 0) {
                cargoHoldProcessing = false;
            }
            return;
        }

        // Only after the salvage slot click — long waits belong here, not before the click.
        sleep(Rs2Random.between(CARGO_HOLD_POST_WITHDRAW_CLICK_MIN_MS, CARGO_HOLD_POST_WITHDRAW_CLICK_MAX_MS));
        boolean gainedInventory = sleepUntil(
                () -> Rs2Inventory.count("salvage") != salvageBefore, CARGO_HOLD_WITHDRAW_INVENTORY_TIMEOUT_MS);
        if (!gainedInventory) {
            sleep(Rs2Random.between(650, 1400));
            gainedInventory = sleepUntil(() -> Rs2Inventory.count("salvage") != salvageBefore, 7000);
        }
        if (!gainedInventory) {
            cargoHoldWithdrawNoGainStreak++;
            log.info("Cargo hold: withdraw not reflected in inventory yet; leaving hold open for retry (avoid closing before click applies)");
            if (cargoHoldWithdrawNoGainStreak >= CARGO_HOLD_WITHDRAW_NO_GAIN_THRESHOLD) {
                log.warn("Cargo hold: withdraw inventory never updated; closing interface and exiting processing mode");
                closeCargoHoldInterface();
                cargoHoldProcessing = false;
                cargoHoldWithdrawNoGainStreak = 0;
            }
            return;
        }
        cargoHoldWithdrawNoGainStreak = 0;

        int gained = Rs2Inventory.count("salvage") - salvageBefore;
        int countBeforeUiRead = cargoHoldCount;
        int salvageCountBeforeUiRead = cargoHoldSalvageStackCount;
        readOccupiedCountFromOpenHoldInterface();
        if (gained > 0) {
            if (cargoHoldCount == countBeforeUiRead) {
                cargoHoldCount = Math.max(0, cargoHoldCount - 1);
                clampCargoHoldCount();
            }
            if (cargoHoldSalvageStackCount == salvageCountBeforeUiRead && cargoHoldSalvageStackCount > 0) {
                cargoHoldSalvageStackCount = Math.max(0, cargoHoldSalvageStackCount - 1);
            }
            clampCargoHoldSalvageStackCount();
        }

        Rs2Antiban.actionCooldown();
        if (isInventoryFull()) {
            sleep(Rs2Random.between(CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MIN_MS, CARGO_HOLD_BEFORE_CLOSE_AFTER_WITHDRAW_MAX_MS));
            if (Rs2Random.dicePercentage(18)) {
                Rs2Antiban.takeMicroBreakByChance();
            }
            closeCargoHoldInterface();
            return;
        }
        if (cargoHoldSalvageStackCount == 0) {
            closeCargoHoldInterface();
            return;
        }
        sleep(Rs2Random.between(180, 480));
    }

    /**
     * Left-clicks the salvage stack widget in the open cargo-hold item grid ({@code Rs2Widget.clickWidget}).
     * Real widget click (same pattern as other hub plugins), not a synthesized menu entry.
     */
    private boolean invokeWithdrawOneSalvageStackFromCargoHoldUi() {
        Client client = Microbot.getClient();
        if (client == null) {
            return false;
        }
        Widget salvageSlot = findFirstSalvageStackWidget(client);
        if (salvageSlot == null) {
            return false;
        }
        Rs2Widget.clickWidget(salvageSlot);
        return true;
    }

    private static Widget findFirstSalvageStackWidget(Client client) {
        Widget items = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
        if (items == null) {
            return null;
        }
        return findSalvageInTree(client, items);
    }

    private static Widget findSalvageInTree(Client client, Widget w) {
        if (w == null) {
            return null;
        }
        if (w.getItemId() > 0) {
            var def = client.getItemDefinition(w.getItemId());
            if (def != null && def.getName().toLowerCase().contains("salvage")) {
                return w;
            }
        }
        Widget[] children = w.getChildren();
        if (children == null) {
            return null;
        }
        for (Widget c : children) {
            Widget found = findSalvageInTree(client, c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void clampCargoHoldCount() {
        if (cargoHoldCapacity < 0) {
            return;
        }
        if (cargoHoldCount < 0) {
            cargoHoldCount = 0;
        }
        if (cargoHoldCount > cargoHoldCapacity) {
            cargoHoldCount = cargoHoldCapacity;
        }
    }

    private void clampCargoHoldSalvageStackCount() {
        if (cargoHoldSalvageStackCount < 0) {
            return;
        }
        cargoHoldSalvageStackCount = Math.max(0, cargoHoldSalvageStackCount);
        if (cargoHoldCount >= 0) {
            cargoHoldSalvageStackCount = Math.min(cargoHoldSalvageStackCount, cargoHoldCount);
        }
    }

    private boolean isPlayerAnimating(Rs2PlayerModel player) {
        return player.getAnimation() != -1;
    }

    /** Stable threshold so cargo-hold processing does not alternate ticks between &quot;full&quot; and not full. */
    private boolean isInventoryFull() {
        return Rs2Inventory.count() >= MIN_INVENTORY_FULL;
    }

    /**
     * While {@link #cargoHoldProcessing} and the hold still has salvage stacks ({@link #cargoHoldSalvageStackCount}
     * &gt; 0), do not {@link #depositToCargoHold()}. Non-salvage items may still occupy slots; deposits resume when
     * salvage stacks in the hold reach 0.
     */
    private boolean suppressSalvageDepositDuringCargoHoldProcessing() {
        return cargoHoldProcessing && cargoHoldSalvageStackCount > 0;
    }

    private boolean hasSalvageItems() {
        return Rs2Inventory.count("salvage") > 0;
    }

    private Rs2TileObjectModel findNearestWreck(WorldPoint playerLocation) {
        var activeWrecks = getActiveWrecks();

        if (activeWrecks.isEmpty()) {
            log.info("No active shipwrecks found");
            sleep(WAIT_TIME);
            return null;
        }

        return activeWrecks.stream()
                .filter(wreck -> isWithinSalvageArea(playerLocation, wreck))
                .min(Comparator.comparingInt(wreck -> playerLocation.distanceTo(wreck.getWorldLocation())))
                .orElse(null);
    }

    private boolean isWithinSalvageArea(WorldPoint playerLocation, Rs2TileObjectModel wreck) {
        return playerLocation.distanceTo(wreck.getWorldLocation()) <= SIZE_SALVAGEABLE_AREA;
    }

    /**
     * True if any active shipwreck is within hook range. Used to avoid opening the cargo hold for withdraw processing
     * while idle with no wreck nearby (which would spam open/close every script tick).
     */
    private boolean hasNearbySalvageableWreck(WorldPoint playerLocation) {
        for (Rs2TileObjectModel wreck : getActiveWrecks()) {
            if (isWithinSalvageArea(playerLocation, wreck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * After cargo-hold mass processing, inventory can sit below the &quot;full&quot; threshold while still holding
     * drop/alch/casket loot. Runs one {@link #clearInventoryViaAlchDropAndCaskets} pass when there is no salvage to
     * protect and configured cleanup would change the inventory.
     *
     * @return true if a cleanup pass was executed (caller should return for this tick).
     */
    private boolean tryRunIdleInventoryCleanup(SailingConfig config) {
        if (hasSalvageItems()) {
            return false;
        }
        if (!inventoryCleanupConfigured(config)) {
            return false;
        }
        if (!inventoryHasCleanupWork(config)) {
            return false;
        }
        log.info("Inventory cleanup (drop/alch/caskets) before salvaging");
        clearInventoryViaAlchDropAndCaskets(config);
        return true;
    }

    private boolean inventoryCleanupConfigured(SailingConfig config) {
        if (config.openCaskets()) {
            return true;
        }
        String drop = config.dropItems();
        if (drop != null) {
            if (!drop.isBlank()) {
                return true;
            }
        }
        if (!config.enableAlching()) {
            return false;
        }
        String alch = config.alchItems();
        return alch != null && !alch.isBlank();
    }

    private boolean inventoryHasCleanupWork(SailingConfig config) {
        if (config.openCaskets()) {
            if (Rs2Inventory.hasItem("casket")) {
                return true;
            }
        }
        String dropItems = config.dropItems();
        if (dropItems != null) {
            if (!dropItems.isBlank()) {
                for (String raw : dropItems.split(",")) {
                    String name = raw.trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    if (Rs2Inventory.hasItem(name)) {
                        return true;
                    }
                }
            }
        }
        if (config.enableAlching()) {
            String alchItems = config.alchItems();
            if (alchItems != null) {
                if (!alchItems.isBlank()) {
                    List<String> fragments = Arrays.stream(alchItems.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    for (Rs2ItemModel item : Rs2Inventory.all()) {
                        String n = item.getName();
                        if (n == null) {
                            continue;
                        }
                        String lower = n.toLowerCase();
                        for (String fragment : fragments) {
                            if (lower.contains(fragment)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void handleFullInventory(SailingConfig config, Rs2PlayerModel player) {
        if (hasSalvageItems() && !isPlayerAnimating(player)) {
            if (config.useCargoHold()) {
                if (canDepositSalvageToCargoHold() && !suppressSalvageDepositDuringCargoHoldProcessing()) {
                    depositToCargoHold();
                    return;
                }
            }
            depositSalvageOrDrop(config);
            return;
        }
        clearInventoryViaAlchDropAndCaskets(config);
    }

    private void clearInventoryViaAlchDropAndCaskets(SailingConfig config) {
        dropJunk(config);
        if (config.openCaskets()) {
            openCaskets();
        }
        if (config.enableAlching()) {
            alchItems(config);
        }
        dropJunk(config);
    }

    private void depositSalvageOrDrop(SailingConfig config) {
        var salvagingStation = findSalvagingStation();

        if (salvagingStation != null) {
            depositAtStation(salvagingStation);
        } else {
            log.info("No salvaging station found, dropping junk items");
            dropJunk(config);
        }
    }

    private Rs2TileObjectModel findSalvagingStation() {
        return tileObjectCache.query()
                .fromWorldView()
                .where(this::isSalvagingStationTileObject)
                .nearestOnClientThread();
    }

    /**
     * Boat and port salvaging stations are identified by object ID ({@code ObjectID1.SAILING_SALVAGING_STATION_*})
     * because composition objects often do not expose the exact menu name &quot;Salvaging station&quot; via
     * {@link Rs2TileObjectModel#getName()}.
     */
    private boolean isSalvagingStationTileObject(Rs2TileObjectModel obj) {
        if (SalvagingStationObjectIds.ALL_IDS.contains(obj.getId())) {
            return true;
        }
        String name = obj.getName();
        if (name == null) {
            return false;
        }
        return name.equalsIgnoreCase("salvaging station");
    }

    private void depositAtStation(Rs2TileObjectModel station) {
        station.click();
        sleepUntil(() -> !hasSalvageItems(), SALVAGE_TIMEOUT);
    }

    private void deploySalvagingHook(Rs2PlayerModel player) {
        var hook = tileObjectCache.query()
                .fromWorldView()
                .where(obj -> obj.getName() != null && obj.getName().toLowerCase().contains("salvaging hook"))
                .nearestOnClientThread();

        if (hook != null) {
            hook.click("Deploy");
            sleepUntil(() -> isPlayerAnimating(player), DEPLOY_TIMEOUT);
        }
    }

    private void openCaskets() {
        while (Rs2Inventory.hasItem("casket")) {
            int slotsBefore = Rs2Inventory.emptySlotCount();
            log.info("Opening casket ({} casket(s) remaining)", Rs2Inventory.count("casket"));
            Rs2Inventory.interact("casket", "Open");
            sleepUntil(() -> !Rs2Inventory.hasItem("casket") ||
                    Rs2Inventory.emptySlotCount() != slotsBefore, 5000);
            if (Rs2Inventory.hasItem("casket") && Rs2Inventory.emptySlotCount() == slotsBefore) {
                log.warn("Casket open had no effect, stopping casket loop");
                break;
            }
            sleep(300, 600);
        }
        log.info("All caskets opened");
    }

    private void alchItems(SailingConfig config) {
        var alchItems = config.alchItems();
        if (alchItems == null || alchItems.isBlank()) return;

        var itemNamesToAlch = Arrays.stream(alchItems.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());

        AlchOrder order = config.alchOrder();

        if (order == AlchOrder.LIST_ORDER) {
            for (String itemName : itemNamesToAlch) {
                while (Rs2Inventory.hasItem(itemName)) {
                    log.info("Alching (list order): {}", itemName);
                    Rs2Magic.alch(itemName);
                    Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
                }
            }
        } else {
            final int COLUMNS = 4;
            Comparator<Rs2ItemModel> slotOrder;
            switch (order) {
                case RIGHT_TO_LEFT:
                    slotOrder = Comparator
                            .comparingInt((Rs2ItemModel i) -> i.getSlot() / COLUMNS)
                            .thenComparingInt(i -> -(i.getSlot() % COLUMNS));
                    break;
                case TOP_TO_BOTTOM:
                    slotOrder = Comparator
                            .comparingInt((Rs2ItemModel i) -> i.getSlot() % COLUMNS)
                            .thenComparingInt(i -> i.getSlot() / COLUMNS);
                    break;
                case BOTTOM_TO_TOP:
                    slotOrder = Comparator
                            .comparingInt((Rs2ItemModel i) -> i.getSlot() % COLUMNS)
                            .thenComparingInt(i -> -(i.getSlot() / COLUMNS));
                    break;
                default: // LEFT_TO_RIGHT
                    slotOrder = Comparator.comparingInt(Rs2ItemModel::getSlot);
                    break;
            }

            boolean alched;
            do {
                alched = false;
                List<Rs2ItemModel> candidates = Rs2Inventory.all().stream()
                        .filter(item -> itemNamesToAlch.stream()
                                .anyMatch(name -> item.getName().toLowerCase().contains(name)))
                        .sorted(slotOrder)
                        .collect(Collectors.toList());
                if (!candidates.isEmpty()) {
                    Rs2ItemModel next = candidates.get(0);
                    log.info("Alching ({}) slot {}: {}", order, next.getSlot(), next.getName());
                    Rs2Magic.alch(next);
                    Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
                    alched = true;
                }
            } while (alched);
        }
    }

    private void dropJunk(SailingConfig config) {
        var dropItems = config.dropItems();
        if (dropItems == null || dropItems.isBlank()) return;

        var junkItems = Arrays.stream(dropItems.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toArray(String[]::new);

        if (junkItems.length > 0) {
            Rs2Inventory.dropAll(junkItems);
        }
    }
}
