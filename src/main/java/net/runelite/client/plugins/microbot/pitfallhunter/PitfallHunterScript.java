package net.runelite.client.plugins.microbot.pitfallhunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class PitfallHunterScript extends Script
{
    /*
     * Agent Server snapshot from the Sunlight Antelope area:
     * - Player tile: 1748,3013,0
     * - Sunlight Antelope NPC: id 13133
     * - Pit objects originally queried around player: ids 51673, 51674, 51675, 51676, 51677
     * - Confirmed trapable pit footprint example:
     *   Pit 1 is a 2x2 footprint. The pit is directionally north/south, so the jump crosses east/west.
     *   Top-left 51686 at 1744,3011,0; bottom-left 51687 at 1744,3010,0;
     *   top-right 51688 at 1745,3011,0; bottom-right 51689 at 1745,3010,0.
     * - Inventory: Teasing stick id 10029, Logs id 1511, Willow logs id 1519,
     *   Large meat pouch closed id 29297, Large meat pouch open id 29464
     * - Nearby local trees: Tree ids 51762/51764, Acacia tree id 51768
     *
     * Still TODO verify:
     * - which pit object IDs represent empty, trapped, or collapsed states
     * - the remaining three footprint tile object IDs for pits where only top-left was provided
     * - exact lure, pre-jump, and post-jump tiles for each pit. Current values are derived
     *   from jump axis as a best-effort default because antelopes are 2x2 and can block
     *   the jump edge if they are not lured out from the pit first.
     * - Empty state: menu "Trap"
     * - Trapped state: menu "Jump"
     * - Collapsed state: menu "Dismantle"
     * - all small pouch IDs, if needed
     */
    private static final int SUNLIGHT_ANTELOPE_NPC_ID = 13133;
    private static final int TODO_EMPTY_PIT_OBJECT_ID = -1;
    private static final int TODO_TRAPPED_PIT_OBJECT_ID = -1;
    private static final int TODO_COLLAPSED_PIT_OBJECT_ID = -1;
    private static final int[] OBSERVED_PIT_OBJECT_IDS = {
            51673, 51674, 51675, 51676, 51677,
            51679, 51682, 51686, 51687, 51688, 51689, 51691, 51701
    };
    private static final int TEASING_STICK_ITEM_ID = 10029;
    private static final int LARGE_MEAT_POUCH_CLOSED_ID = 29297;
    private static final int LARGE_MEAT_POUCH_OPEN_ID = 29464;
    private static final int[] LOCAL_TREE_IDS = {51762, 51764, 51768};

    private static final String SUNLIGHT_ANTELOPE_NAME = "Sunlight antelope";
    private static final String PIT_OBJECT_NAME = "Pit";
    private static final String SPIKED_PIT_OBJECT_NAME = "Spiked Pit";
    private static final String COLLAPSED_TRAP_OBJECT_NAME = "Collapsed Trap";
    private static final String TRAP_PIT_ACTION = "Trap";
    private static final String JUMP_PIT_ACTION = "Jump";
    private static final String DISMANTLE_TRAP_ACTION = "Dismantle";
    private static final String LURE_NPC_ACTION = "Tease";
    private static final String LOG_NAME = "Logs";
    private static final String WILLOW_LOG_NAME = "Willow logs";
    private static final String[] LOG_ITEM_NAMES = {LOG_NAME, WILLOW_LOG_NAME, "Oak logs"};
    private static final String BIG_BONES_NAME = "Big bones";
    private static final String CHISEL_NAME = "Chisel";
    private static final String SUNLIGHT_ANTELOPE_ANTLER_NAME = "Sunlight antelope antler";
    private static final String[] SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES = {
            "Sunlight antelope",
            "Sunlight antelope meat"
    };
    private static final String TEASING_STICK_NAME = "Teasing stick";
    private static final String KANDARIN_HEADGEAR_NAME = "Kandarin headgear";
    private static final String[] MEAT_POUCH_NAMES = {
            "Small meat pouch",
            "Small meat pouch (open)",
            "Large meat pouch",
            "Large meat pouch (open)"
    };

    /*
     * Pit footprints:
     * - Pits are 2x2 objects. Store the known footprint tiles together so object lookups,
     *   NPC scoring, and future jump-tile logic stay pit-first.
     * - Pit 1 is fully known.
     * - Other pits currently have only the top-left tile/id recorded. Add their other three
     *   footprint tiles here once verified through the Agent Server or live menu probing.
     */
    public static final List<PitfallDefinition> PITFALL_ORDER = List.of(
            new PitfallDefinition(
                    "Pit 1 - west",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(
                            new PitfallTile("top-left", 51686, new WorldPoint(1744, 3011, 0)),
                            new PitfallTile("bottom-left", 51687, new WorldPoint(1744, 3010, 0)),
                            new PitfallTile("top-right", 51688, new WorldPoint(1745, 3011, 0)),
                            new PitfallTile("bottom-right", 51689, new WorldPoint(1745, 3010, 0))
                    ),
                    null,
                    null,
                    null,
                    1
            ),
            new PitfallDefinition(
                    "Pit 2 - east",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(new PitfallTile("top-left", 51682, new WorldPoint(1749, 3015, 0))),
                    null,
                    null,
                    null,
                    2
            ),
            new PitfallDefinition(
                    "Pit 3 - south-east",
                    PitOrientation.WEST_EAST,
                    JumpAxis.NORTH_SOUTH,
                    List.of(new PitfallTile("top-left", 51691, new WorldPoint(1751, 3010, 0))),
                    null,
                    null,
                    null,
                    3
            ),
            new PitfallDefinition(
                    "Pit 4 - south-west",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(new PitfallTile("top-left", 51679, new WorldPoint(1738, 3002, 0))),
                    null,
                    null,
                    null,
                    4
            ),
            new PitfallDefinition(
                    "Pit 5 - south",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(new PitfallTile("top-left", 51701, new WorldPoint(1749, 3000, 0))),
                    null,
                    null,
                    null,
                    5
            )
    );

    private PitfallHunterConfig config;
    private State state = State.CHECK_REQUIREMENTS;
    private PitfallDefinition selectedPit;
    private PitfallState selectedPitState = PitfallState.UNKNOWN;
    private Rs2NpcModel selectedNpc;
    private final Set<String> skippedTrappedPits = new HashSet<>();
    private long stateStartedAt;
    private boolean kandarinHeadgearAvailable;
    private boolean meatPouchAvailable;

    public boolean run(PitfallHunterConfig config)
    {
        this.config = config;
        transition(State.CHECK_REQUIREMENTS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || !isRunning()) {
                    return;
                }

                if (isBusy()) {
                    return;
                }

                tick();
            } catch (Exception ex) {
                log("Script error: " + ex.getMessage(), Level.ERROR);
                transition(State.RECOVER);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void tick()
    {
        switch (state) {
            case CHECK_REQUIREMENTS:
                checkRequirements();
                break;
            case REFRESH_PITS:
                refreshPits();
                break;
            case SELECT_NPC:
                selectNpc();
                break;
            case SELECT_PIT:
                selectPit();
                break;
            case PREPARE_PIT:
                preparePit();
                break;
            case LURE_NPC:
                lureNpc();
                break;
            case JUMP_PIT:
                jumpPit();
                break;
            case WAIT_FOR_CAPTURE:
                waitForCapture();
                break;
            case LOOT_PIT:
                lootPit();
                break;
            case HANDLE_MEAT_POUCH:
                handleMeatPouch();
                break;
            case RECOVER:
                recover();
                break;
            case STOP:
                shutdown();
                break;
        }
    }

    private void checkRequirements()
    {
        if (Rs2Inventory.isFull()) {
            handlePostLootInventory(false);
            if (Rs2Inventory.isFull()) {
                stop("Inventory is full");
                return;
            }
        }

        kandarinHeadgearAvailable = hasKandarinHeadgear();
        meatPouchAvailable = hasMeatPouch();
        log("Kandarin headgear available: " + kandarinHeadgearAvailable);
        log("Meat pouch available: " + meatPouchAvailable);

        if (!hasLogs()) {
            if (kandarinHeadgearAvailable && cutLogsLocally()) {
                return;
            }
            stop("No verified log type available");
            return;
        }

        if (configuredPits().isEmpty()) {
            stop("No configured pits. Fill PITFALL_ORDER with verified Sunlight Antelope pit tiles.");
            return;
        }

        transition(State.REFRESH_PITS);
    }

    private void refreshPits()
    {
        selectedPit = null;
        selectedPitState = PitfallState.UNKNOWN;
        selectedNpc = null;
        skippedTrappedPits.clear();
        transition(State.SELECT_NPC);
    }

    private void selectNpc()
    {
        if (Rs2Inventory.isFull()) {
            handlePostLootInventory(false);
            if (Rs2Inventory.isFull()) {
                stop("Inventory is full");
                return;
            }
        }

        if (!hasLogs()) {
            if (kandarinHeadgearAvailable && cutLogsLocally()) {
                return;
            }
            stop("No logs available");
            return;
        }

        selectedNpc = findClosestSunlightAntelope();
        if (selectedNpc == null) {
            stop("No valid Sunlight Antelope found nearby");
            return;
        }

        log("Selected closest NPC: id=" + selectedNpc.getId()
                + " tile=" + selectedNpc.getWorldLocation()
                + " playerTile=" + Rs2Player.getWorldLocation());
        transition(State.LURE_NPC);
    }

    private void selectPit()
    {
        if (selectedNpc == null || !isSunlightAntelope(selectedNpc)) {
            transition(State.SELECT_NPC);
            return;
        }

        PitfallDefinition pit = findClosestUsablePitForNpc(selectedNpc);
        if (pit == null) {
            stop("No usable pit found for lured NPC");
            return;
        }

        selectedPit = pit;
        selectedPitState = getPitState(selectedPit);
        log("Selected pit: " + selectedPit.name + " state=" + selectedPitState
                + (selectedNpc == null ? "" : " npc=" + selectedNpc.getId() + " tile=" + selectedNpc.getWorldLocation()));

        transition(State.PREPARE_PIT);
    }

    private void preparePit()
    {
        if (selectedPit == null || selectedPit.getAnchorTile() == null) {
            transition(State.RECOVER);
            return;
        }

        if (!hasLogs()) {
            transition(State.CHECK_REQUIREMENTS);
            return;
        }

        Rs2TileObjectModel pitObject = getPitObject(selectedPit);
        if (pitObject == null) {
            log("Prepare failed: pit object missing for " + selectedPit.name, Level.WARN);
            transition(State.RECOVER);
            return;
        }

        log("Preparing pit with logs: " + selectedPit.name);
        boolean clicked = clickPitObject(pitObject, TRAP_PIT_ACTION);
        log("Prepare result: " + clicked);

        if (!clicked) {
            transition(State.RECOVER);
            return;
        }

        sleep(900, 1200);
        transition(State.JUMP_PIT);
    }

    private void lureNpc()
    {
        if (selectedNpc == null || !isSunlightAntelope(selectedNpc)) {
            log("Lure failed: selected NPC is no longer valid", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        String action = getLureAction(selectedNpc);
        boolean lured = false;
        if (!action.isEmpty()) {
            log("Luring NPC with action: " + action);
            lured = selectedNpc.click(action);
        }

        if (!lured) {
            log("Trying teasing stick on NPC");
            lured = useTeasingStickOnNpc(selectedNpc);
        }

        log("Lure result: " + lured);
        if (!lured) {
            transition(State.RECOVER);
            return;
        }

        boolean followDetected = waitUntil(() -> isNpcFollowingPlayer(selectedNpc), 1800);
        log("Lure follow detected: " + followDetected);
        transition(State.SELECT_PIT);
    }

    private void jumpPit()
    {
        if (selectedPit == null || selectedNpc == null || !isSunlightAntelope(selectedNpc)) {
            log("Jump aborted: missing selected pit or NPC", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        if (!isNpcFollowingPlayer(selectedNpc)) {
            log("Jump continuing even though follow is not reflected by NPC model", Level.WARN);
        }

        walkTo(selectedPit.getPreJumpTile());
        Rs2TileObjectModel pitObject = getPitObject(selectedPit);
        if (pitObject == null) {
            log("Jump failed: trapped pit object missing", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        boolean jumped = clickPitObject(pitObject, JUMP_PIT_ACTION);
        log("Jump result: " + jumped);

        if (!jumped) {
            transition(State.RECOVER);
            return;
        }

        walkTo(selectedPit.getPostJumpTile());
        transition(State.WAIT_FOR_CAPTURE);
    }

    private void waitForCapture()
    {
        selectedPitState = getPitState(selectedPit);
        log("Capture wait state: " + selectedPit.name + " -> " + selectedPitState);

        if (selectedPitState == PitfallState.COLLAPSED) {
            skippedTrappedPits.remove(selectedPit.name);
            transition(State.LOOT_PIT);
            return;
        }

        if (timedOut(config.captureTimeoutMs())) {
            log("No collapse after jump; rotating away from pit: " + selectedPit.name, Level.WARN);
            if (selectedPitState == PitfallState.TRAPPED) {
                skippedTrappedPits.add(selectedPit.name);
            }
            if (isNpcFollowingPlayer(selectedNpc)) {
                selectedPit = null;
                selectedPitState = PitfallState.UNKNOWN;
                transition(State.SELECT_PIT);
            } else {
                transition(State.REFRESH_PITS);
            }
        }
    }

    private void lootPit()
    {
        Rs2TileObjectModel pitObject = getPitObject(selectedPit);
        if (pitObject == null) {
            log("Loot failed: pit object missing", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        int before = Rs2Inventory.count();
        boolean clicked = clickPitObject(pitObject, DISMANTLE_TRAP_ACTION);
        boolean changed = clicked && waitUntil(() -> Rs2Inventory.count() != before
                || getPitState(selectedPit) != PitfallState.COLLAPSED, 8000);

        log("Loot result: clicked=" + clicked + " changed=" + changed);
        if (!clicked) {
            transition(State.RECOVER);
            return;
        }

        transition(State.HANDLE_MEAT_POUCH);
    }

    private void handleMeatPouch()
    {
        if (!meatPouchAvailable) {
            handlePostLootInventory(true);
            transition(State.REFRESH_PITS);
            return;
        }

        boolean stored = false;
        if (Rs2Inventory.hasItem(LARGE_MEAT_POUCH_CLOSED_ID)) {
            Rs2Inventory.interact(LARGE_MEAT_POUCH_CLOSED_ID, "Open");
            waitUntil(() -> Rs2Inventory.hasItem(LARGE_MEAT_POUCH_OPEN_ID), 1500);
        }

        if (Rs2Inventory.hasItem(LARGE_MEAT_POUCH_OPEN_ID)) {
            stored = Rs2Inventory.interact(LARGE_MEAT_POUCH_OPEN_ID, "Fill");
        }

        for (String pouchName : MEAT_POUCH_NAMES) {
            if (stored) {
                break;
            }
            if (Rs2Inventory.hasItem(pouchName, false)) {
                stored = Rs2Inventory.interact(pouchName, "Fill", false)
                        || Rs2Inventory.interact(pouchName, "Use", false);
                break;
            }
        }

        log("Meat pouch store result: " + stored);
        handlePostLootInventory(true);
        transition(State.REFRESH_PITS);
    }

    private void recover()
    {
        log("Recovering from state: " + state + " pit=" + (selectedPit == null ? "none" : selectedPit.name));
        selectedNpc = null;
        selectedPit = null;
        selectedPitState = PitfallState.UNKNOWN;
        sleep(600, 1000);
        transition(State.REFRESH_PITS);
    }

    private boolean selectCollapsedPit()
    {
        PitfallDefinition pit = configuredPits().stream()
                .filter(candidate -> getPitState(candidate) == PitfallState.COLLAPSED)
                .min(Comparator.comparingInt(candidate -> distanceToPlayer(candidate.getAnchorTile())))
                .orElse(null);

        if (pit == null) {
            return false;
        }

        selectedPit = pit;
        selectedPitState = PitfallState.COLLAPSED;
        selectedNpc = null;
        log("Selected collapsed pit: " + selectedPit.name);
        transition(State.LOOT_PIT);
        return true;
    }

    private Rs2NpcModel findClosestSunlightAntelope()
    {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile == null) {
            return null;
        }

        return Microbot.getRs2NpcCache().query()
                .withName(SUNLIGHT_ANTELOPE_NAME)
                .within(20)
                .toListOnClientThread()
                .stream()
                .filter(this::isSunlightAntelope)
                .filter(npc -> npc.getWorldLocation() != null)
                .filter(npc -> !npc.isInteracting() || npc.isInteractingWithPlayer())
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(playerTile)))
                .orElse(null);
    }

    private PitfallDefinition findClosestUsablePitForNpc(Rs2NpcModel npc)
    {
        if (npc == null || npc.getWorldLocation() == null) {
            return null;
        }

        WorldPoint npcTile = npc.getWorldLocation();
        return configuredPits().stream()
                .filter(pit -> !skippedTrappedPits.contains(pit.name))
                .filter(pit -> hasLogs())
                .filter(pit -> getPitState(pit) == PitfallState.EMPTY)
                .min(Comparator.comparingInt((PitfallDefinition pit) -> pit.getAnchorTile().distanceTo(npcTile))
                        .thenComparingInt(PitfallDefinition::getPriority))
                .orElse(null);
    }

    private int distanceToPlayer(WorldPoint tile)
    {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile == null || tile == null) {
            return Integer.MAX_VALUE;
        }
        return playerTile.distanceTo(tile);
    }

    private boolean isSunlightAntelope(Rs2NpcModel npc)
    {
        if (npc == null) {
            return false;
        }
        return npc.getId() == SUNLIGHT_ANTELOPE_NPC_ID
                && SUNLIGHT_ANTELOPE_NAME.equalsIgnoreCase(npc.getName());
    }

    private boolean isNpcFollowingPlayer(Rs2NpcModel npc)
    {
        return npc != null && npc.isInteractingWithPlayer();
    }

    private String getLureAction(Rs2NpcModel npc)
    {
        String action = getAvailableNpcAction(npc, List.of(LURE_NPC_ACTION, "Poke"));
        if (action.isEmpty()) {
            log("No direct Tease/Poke action found on NPC");
        }
        return action;
    }

    private String getAvailableNpcAction(Rs2NpcModel npc, List<String> possibleActions)
    {
        if (npc == null || possibleActions == null || possibleActions.isEmpty()) {
            return "";
        }

        NPCComposition composition = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(npc.getId()))
                .orElse(null);

        if (composition == null || composition.getActions() == null) {
            return "";
        }

        return Arrays.stream(composition.getActions())
                .filter(action -> action != null && !action.isEmpty())
                .filter(action -> possibleActions.stream().anyMatch(action::equalsIgnoreCase))
                .findFirst()
                .orElse("");
    }

    private boolean useTeasingStickOnNpc(Rs2NpcModel npc)
    {
        if (Rs2Inventory.hasItem(TEASING_STICK_ITEM_ID)) {
            return Rs2Inventory.use(TEASING_STICK_ITEM_ID) && npc.click("Use");
        }

        if (Rs2Inventory.hasItem(TEASING_STICK_NAME, false)) {
            return Rs2Inventory.use(TEASING_STICK_NAME) && npc.click("Use");
        }

        return false;
    }

    private boolean clickPitObject(Rs2TileObjectModel object, String action)
    {
        if (object == null) {
            return false;
        }

        log("Pit object query interact: action=" + action
                + " id=" + object.getId()
                + " tile=" + object.getWorldLocation());

        return Microbot.getRs2TileObjectCache().query().interact(object.getId(), action);
    }

    private void handlePostLootInventory(boolean afterLoot)
    {
        if (config.fletchAntlers()) {
            fletchSunlightAntlers();
        }

        handleBigBones(afterLoot);
        dropSunlightAntelopesForSpace();
    }

    private void handleBigBones(boolean afterLoot)
    {
        PitfallHunterConfig.BigBonesMode mode = config.bigBonesMode();
        if (mode == PitfallHunterConfig.BigBonesMode.KEEP || !Rs2Inventory.hasItem(BIG_BONES_NAME, true)) {
            return;
        }

        if (mode == PitfallHunterConfig.BigBonesMode.DROP) {
            int before = Rs2Inventory.count(BIG_BONES_NAME, true);
            boolean dropped = Rs2Inventory.dropAll(true, BIG_BONES_NAME);
            waitUntil(() -> Rs2Inventory.count(BIG_BONES_NAME, true) < before || !Rs2Inventory.hasItem(BIG_BONES_NAME, true), 2000);
            log("Big bones drop result: " + dropped);
            return;
        }

        boolean shouldBury = mode == PitfallHunterConfig.BigBonesMode.BURY_AFTER_LOOT && afterLoot
                || mode == PitfallHunterConfig.BigBonesMode.BURY_WHEN_FULL && Rs2Inventory.isFull();
        if (!shouldBury) {
            return;
        }

        int buried = 0;
        while (isRunning() && Rs2Inventory.hasItem(BIG_BONES_NAME, true)) {
            int before = Rs2Inventory.count(BIG_BONES_NAME, true);
            boolean clicked = Rs2Inventory.interact(BIG_BONES_NAME, "Bury", true);
            if (!clicked || !waitUntil(() -> Rs2Inventory.count(BIG_BONES_NAME, true) < before, 2500)) {
                break;
            }
            buried++;
        }
        log("Big bones bury count: " + buried);
    }

    private void dropSunlightAntelopesForSpace()
    {
        int threshold = config.antelopeDropThreshold().emptySlots();
        if (threshold <= 0 || Rs2Inventory.emptySlotCount() >= threshold) {
            return;
        }

        int dropped = 0;
        while (isRunning()
                && Rs2Inventory.emptySlotCount() < threshold
                && Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES, true)) {
            int beforeSlots = Rs2Inventory.emptySlotCount();
            boolean clicked = Rs2Inventory.drop(item -> Arrays.stream(SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES)
                    .anyMatch(name -> name.equalsIgnoreCase(item.getName())));
            if (!clicked || !waitUntil(() -> Rs2Inventory.emptySlotCount() > beforeSlots, 2000)) {
                break;
            }
            dropped++;
        }

        if (dropped > 0) {
            log("Dropped Sunlight antelope items for space: " + dropped);
        }
    }

    private void fletchSunlightAntlers()
    {
        if (!Rs2Inventory.hasItem(CHISEL_NAME, true) || !Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_ANTLER_NAME, true)) {
            return;
        }

        int before = Rs2Inventory.count(SUNLIGHT_ANTELOPE_ANTLER_NAME, true);
        log("Fletching Sunlight antelope antlers into bolts. Antlers=" + before);
        boolean combined = Rs2Inventory.combineClosest(CHISEL_NAME, SUNLIGHT_ANTELOPE_ANTLER_NAME);
        if (!combined) {
            log("Fletch antlers failed: could not use chisel on antler", Level.WARN);
            return;
        }

        if (waitUntil(Rs2Dialogue::hasSelectAnOption, 1500)) {
            boolean selected = Rs2Dialogue.clickOption(false, "bolt", "bolts");
            log("Fletch antlers dialogue option result: " + selected);
        }

        if (waitUntil(Rs2Widget::isProductionWidgetOpen, 1500)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }

        boolean finished = waitUntil(() -> Rs2Inventory.count(SUNLIGHT_ANTELOPE_ANTLER_NAME, true) < before
                || !Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_ANTLER_NAME, true), 30000);
        log("Fletch antlers result: " + finished);
    }

    private PitfallState getPitState(PitfallDefinition pit)
    {
        Rs2TileObjectModel object = getPitObject(pit);
        if (object == null) {
            return PitfallState.UNKNOWN;
        }

        if (hasPitObjectAction(object, DISMANTLE_TRAP_ACTION)) {
            return PitfallState.COLLAPSED;
        }
        if (hasPitObjectAction(object, JUMP_PIT_ACTION)) {
            return PitfallState.TRAPPED;
        }
        if (hasPitObjectAction(object, TRAP_PIT_ACTION)) {
            return PitfallState.EMPTY;
        }

        int id = object.getId();
        if (id == TODO_COLLAPSED_PIT_OBJECT_ID && id > 0) {
            return PitfallState.COLLAPSED;
        }
        if (id == TODO_TRAPPED_PIT_OBJECT_ID && id > 0) {
            return PitfallState.TRAPPED;
        }
        if (id == TODO_EMPTY_PIT_OBJECT_ID && id > 0) {
            return PitfallState.EMPTY;
        }

        return PitfallState.UNKNOWN;
    }

    private boolean hasPitObjectAction(Rs2TileObjectModel object, String action)
    {
        if (object == null || action == null) {
            return false;
        }

        try {
            ObjectComposition composition = object.getObjectComposition();
            if (composition == null || composition.getActions() == null) {
                return false;
            }

            return Arrays.stream(composition.getActions())
                    .filter(Objects::nonNull)
                    .anyMatch(action::equalsIgnoreCase);
        } catch (RuntimeException ex) {
            log("Pit object action lookup failed: id=" + object.getId()
                    + " tile=" + object.getWorldLocation()
                    + " error=" + ex.getClass().getSimpleName(), Level.WARN);
            return false;
        }
    }

    private Rs2TileObjectModel getPitObject(PitfallDefinition pit)
    {
        if (pit == null || pit.getAnchorTile() == null) {
            return null;
        }

        return Microbot.getRs2TileObjectCache().query()
                .within(pit.getAnchorTile(), config.pitObjectSearchRadius())
                .toListOnClientThread()
                .stream()
                .filter(object -> object.getWorldLocation() != null)
                .filter(object -> pit.contains(object.getId(), object.getWorldLocation())
                        || object.getWorldLocation().distanceTo(pit.getAnchorTile()) <= config.pitObjectSearchRadius())
                .filter(this::isPitfallObject)
                .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo(pit.getAnchorTile())))
                .orElse(null);
    }

    private boolean isPitfallObject(Rs2TileObjectModel object)
    {
        if (object == null) {
            return false;
        }

        int id = object.getId();
        if (contains(OBSERVED_PIT_OBJECT_IDS, id)
                || id == TODO_EMPTY_PIT_OBJECT_ID
                || id == TODO_TRAPPED_PIT_OBJECT_ID
                || id == TODO_COLLAPSED_PIT_OBJECT_ID) {
            return true;
        }

        String name = String.valueOf(object.getName()).toLowerCase();
        return name.equalsIgnoreCase(PIT_OBJECT_NAME)
                || name.equalsIgnoreCase(SPIKED_PIT_OBJECT_NAME)
                || name.equalsIgnoreCase(COLLAPSED_TRAP_OBJECT_NAME)
                || name.contains("pitfall");
    }

    private boolean hasLogs()
    {
        return Rs2Inventory.hasItem(LOG_ITEM_NAMES, true);
    }

    private boolean hasMeatPouch()
    {
        return Rs2Inventory.hasItem(LARGE_MEAT_POUCH_CLOSED_ID, LARGE_MEAT_POUCH_OPEN_ID)
                || Arrays.stream(MEAT_POUCH_NAMES).anyMatch(name -> Rs2Inventory.hasItem(name, false));
    }

    private boolean hasKandarinHeadgear()
    {
        return Rs2Equipment.isWearing(KANDARIN_HEADGEAR_NAME)
                || Rs2Inventory.hasItem(KANDARIN_HEADGEAR_NAME, false);
    }

    private boolean cutLogsLocally()
    {
        if (hasLogs()) {
            return true;
        }

        log("Logs needed and Kandarin headgear exists. Trying local log cutting.");
        Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
                .withIds(LOCAL_TREE_IDS)
                .within(20)
                .toListOnClientThread()
                .stream()
                .filter(Objects::nonNull)
                .filter(Rs2TileObjectModel::isReachable)
                .findFirst()
                .orElse(null);

        if (tree == null) {
            tree = Microbot.getRs2TileObjectCache().query()
                    .withNameContains("tree")
                    .within(15)
                    .toListOnClientThread()
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(Rs2TileObjectModel::isReachable)
                    .findFirst()
                    .orElse(null);
        }

        if (tree == null) {
            log("Local log cutting failed: no nearby tree");
            return false;
        }

        boolean clicked = tree.click("Chop down");
        return clicked && waitUntil(this::hasLogs, 10000);
    }

    private void walkTo(WorldPoint tile)
    {
        if (tile == null) {
            return;
        }

        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile != null && playerTile.distanceTo(tile) <= 1) {
            return;
        }

        Rs2Walker.walkTo(tile);
        waitUntil(() -> {
            WorldPoint current = Rs2Player.getWorldLocation();
            return current != null && current.distanceTo(tile) <= 1;
        }, 5000);
    }

    private boolean isBusy()
    {
        return Rs2Player.isMoving() || Rs2Player.isAnimating();
    }

    private List<PitfallDefinition> configuredPits()
    {
        return PITFALL_ORDER.stream()
                .filter(PitfallDefinition::isConfigured)
                .sorted(Comparator.comparingInt(PitfallDefinition::getPriority))
                .collect(Collectors.toList());
    }

    private boolean contains(int[] values, int value)
    {
        return Arrays.stream(values).anyMatch(candidate -> candidate == value);
    }

    private boolean waitUntil(Check check, int timeoutMs)
    {
        long start = System.currentTimeMillis();
        while (isRunning() && System.currentTimeMillis() - start < timeoutMs) {
            if (check.ok()) {
                return true;
            }
            sleep(100, 150);
        }
        return false;
    }

    private boolean timedOut(int timeoutMs)
    {
        return System.currentTimeMillis() - stateStartedAt > timeoutMs;
    }

    private void transition(State next)
    {
        state = next;
        stateStartedAt = System.currentTimeMillis();
        log("State -> " + state);
    }

    private void stop(String reason)
    {
        log("Stopping: " + reason, Level.WARN);
        transition(State.STOP);
    }

    private void log(String message)
    {
        log(message, Level.INFO);
    }

    private void log(String message, Level level)
    {
        Microbot.log(level, "[PitfallHunter] " + message);
    }

    @Override
    public void shutdown()
    {
        selectedPit = null;
        selectedNpc = null;
        super.shutdown();
    }

    private enum State
    {
        CHECK_REQUIREMENTS,
        REFRESH_PITS,
        SELECT_NPC,
        SELECT_PIT,
        PREPARE_PIT,
        LURE_NPC,
        JUMP_PIT,
        WAIT_FOR_CAPTURE,
        LOOT_PIT,
        HANDLE_MEAT_POUCH,
        RECOVER,
        STOP
    }

    private enum PitfallState
    {
        UNKNOWN,
        EMPTY,
        TRAPPED,
        COLLAPSED
    }

    @Getter
    @RequiredArgsConstructor
    public static class PitfallDefinition
    {
        private final String name;
        private final PitOrientation orientation;
        private final JumpAxis jumpAxis;
        private final List<PitfallTile> footprint;
        private final WorldPoint lureTile;
        private final WorldPoint preJumpTile;
        private final WorldPoint postJumpTile;
        private final int priority;

        private WorldPoint getAnchorTile()
        {
            if (footprint == null || footprint.isEmpty()) {
                return null;
            }
            return footprint.get(0).getTile();
        }

        private WorldPoint getLureTile()
        {
            if (lureTile != null) {
                return lureTile;
            }

            WorldPoint anchor = getAnchorTile();
            if (anchor == null) {
                return null;
            }

            /*
             * Best-effort defaults:
             * - Antelopes are 2x2, so lure from two tiles behind the pre-jump tile.
             * - For north/south pits, jump east/west: stand west, then jump east.
             * - For west/east pits, jump north/south: stand south, then jump north.
             *
             * These should be replaced with exact per-pit tiles if any pit has blocked
             * terrain or if the antelope pathing wedges on a corner.
             */
            if (jumpAxis == JumpAxis.EAST_WEST) {
                return new WorldPoint(minX() - 3, centerY(), anchor.getPlane());
            }
            return new WorldPoint(centerX(), minY() - 3, anchor.getPlane());
        }

        private WorldPoint getPreJumpTile()
        {
            if (preJumpTile != null) {
                return preJumpTile;
            }

            WorldPoint anchor = getAnchorTile();
            if (anchor == null) {
                return null;
            }

            if (jumpAxis == JumpAxis.EAST_WEST) {
                return new WorldPoint(minX() - 1, centerY(), anchor.getPlane());
            }
            return new WorldPoint(centerX(), minY() - 1, anchor.getPlane());
        }

        private WorldPoint getPostJumpTile()
        {
            if (postJumpTile != null) {
                return postJumpTile;
            }

            WorldPoint anchor = getAnchorTile();
            if (anchor == null) {
                return null;
            }

            if (jumpAxis == JumpAxis.EAST_WEST) {
                return new WorldPoint(maxX() + 1, centerY(), anchor.getPlane());
            }
            return new WorldPoint(centerX(), maxY() + 1, anchor.getPlane());
        }

        private int minX()
        {
            return footprint.stream().map(PitfallTile::getTile).mapToInt(WorldPoint::getX).min()
                    .orElse(getAnchorTile().getX());
        }

        private int maxX()
        {
            return footprint.stream().map(PitfallTile::getTile).mapToInt(WorldPoint::getX).max()
                    .orElse(getAnchorTile().getX());
        }

        private int minY()
        {
            return footprint.stream().map(PitfallTile::getTile).mapToInt(WorldPoint::getY).min()
                    .orElse(getAnchorTile().getY());
        }

        private int maxY()
        {
            return footprint.stream().map(PitfallTile::getTile).mapToInt(WorldPoint::getY).max()
                    .orElse(getAnchorTile().getY());
        }

        private int centerX()
        {
            return (minX() + maxX()) / 2;
        }

        private int centerY()
        {
            return (minY() + maxY()) / 2;
        }

        private boolean contains(int objectId, WorldPoint tile)
        {
            if (footprint == null || tile == null) {
                return false;
            }

            return footprint.stream().anyMatch(knownTile ->
                    (knownTile.getObjectId() == objectId && objectId > 0) || knownTile.getTile().equals(tile));
        }

        private boolean isConfigured()
        {
            return getAnchorTile() != null;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class PitfallTile
    {
        private final String role;
        private final int objectId;
        private final WorldPoint tile;
    }

    private enum PitOrientation
    {
        NORTH_SOUTH,
        WEST_EAST
    }

    private enum JumpAxis
    {
        EAST_WEST,
        NORTH_SOUTH
    }

    private interface Check
    {
        boolean ok();
    }
}
