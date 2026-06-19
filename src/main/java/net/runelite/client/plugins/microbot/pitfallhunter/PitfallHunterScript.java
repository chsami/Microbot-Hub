package net.runelite.client.plugins.microbot.pitfallhunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.ItemID;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
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
import java.util.concurrent.ThreadLocalRandom;
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
     * - Nearby local trees: Tree ids 51762/51764
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
    private static final int[] OBSERVED_PIT_OBJECT_IDS = {
            51673, 51674, 51675, 51676, 51677,
            51679, 51680, 51681, 51682, 51683, 51684, 51685,
            51686, 51687, 51688, 51689,
            51691, 51692, 51693, 51694,
            51701, 51702, 51703, 51704
    };
    private static final int TEASING_STICK_ITEM_ID = 10029;
    private static final int LARGE_MEAT_POUCH_CLOSED_ID = 29297;
    private static final int LARGE_MEAT_POUCH_OPEN_ID = 29464;
    private static final int[] LOCAL_TREE_IDS = {51762, 51764};

    private static final String SUNLIGHT_ANTELOPE_NAME = "Sunlight antelope";
    private static final String PIT_OBJECT_NAME = "Pit";
    private static final String SPIKED_PIT_OBJECT_NAME = "Spiked Pit";
    private static final String SPIKED_TRAP_OBJECT_NAME = "Spiked trap";
    private static final String COLLAPSED_TRAP_OBJECT_NAME = "Collapsed Trap";
    private static final String TRAP_PIT_ACTION = "Trap";
    private static final String JUMP_PIT_ACTION = "Jump";
    private static final String DISMANTLE_TRAP_ACTION = "Dismantle";
    private static final String LURE_NPC_ACTION = "Tease";
    private static final int LURE_INTERACTION_TIMEOUT_MS = 2800;
    private static final int LURE_MOVEMENT_TIMEOUT_MS = 3800;
    private static final int LURE_CONFIRMATION_GRACE_MS = 15000;
    private static final int LURE_MAX_ATTEMPTS = 2;
    private static final int BUSY_WATCHDOG_TIMEOUT_MS = 15000;
    private static final int STATE_WATCHDOG_TIMEOUT_MS = 30000;
    private static final int CAPTURE_TIMEOUT_MIN_MS = 11000;
    private static final int CAPTURE_REJUMP_DELAY_MS = 3600;
    private static final int CAPTURE_REJUMP_EXTRA_DELAY_MIN_MS = 900;
    private static final int CAPTURE_REJUMP_EXTRA_DELAY_MAX_MS = 1200;
    private static final int CAPTURE_REJUMP_MAX_ATTEMPTS = 3;
    private static final int PIT_JUMP_ANIMATION_ID = 3067;
    private static final int PIT_JUMP_ANIMATION_START_TIMEOUT_MS = 5300;
    private static final int DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS = 5000;
    private static final int PREPARE_TRAP_TIMEOUT_MS = 6500;
    private static final int JUMP_OBJECT_APPEAR_TIMEOUT_MS = 3700;
    private static final int WALK_TIMEOUT_MS = 5000;
    private static final int PREPARE_PIT_MAX_ATTEMPTS = 3;
    private static final int PIT_OBJECT_MATCH_RADIUS = 2;
    private static final int[] CAPTURE_DEATH_GRAPHIC_IDS = {993};
    private static final int CAPTURE_LOOT_DELAY_MIN_MS = 600;
    private static final int CAPTURE_LOOT_DELAY_MAX_MS = 1500;
    private static final int SHORT_RANDOM_WAIT_MIN_MS = 600;
    private static final int SHORT_RANDOM_WAIT_MAX_MS = 1500;
    private static final int BANK_HEAL_MAX_FOOD_ATTEMPTS = 28;
    private static final int BANK_LOCATION_REACHED_DISTANCE = 3;
    private static final WorldPoint BANK_LOCATION = new WorldPoint(1779, 3095, 0);
    private static final WorldPoint START_LOCATION = new WorldPoint(1743, 3019, 0);
    private static final int FLETCH_ANTLERS_CHANCE_PERCENT = 40;
    private static final String LOG_NAME = "Logs";
    private static final String WILLOW_LOG_NAME = "Willow logs";
    private static final String[] LOG_ITEM_NAMES = {LOG_NAME, WILLOW_LOG_NAME, "Oak logs"};
    private static final String BIG_BONES_NAME = "Big bones";
    private static final String CHISEL_NAME = "Chisel";
    private static final String KNIFE_NAME = "Knife";
    private static final String SUNLIGHT_ANTELOPE_ANTLER_NAME = "Sunlight antelope antler";
    private static final String[] SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES = {
            "Sunlight antelope",
            "Raw sunlight antelope",
            "Sunlight antelope meat",
            "Sunlight antelope fur"
    };
    private static final int[] SUNLIGHT_ANTELOPE_DROP_ITEM_IDS = {
            ItemID.SUNLIGHT_ANTELOPE,
            ItemID.RAW_SUNLIGHT_ANTELOPE,
            ItemID.SUNLIGHT_ANTELOPE_FUR
    };
    private static final int[] SUNLIGHT_ANTELOPE_POUCHABLE_MEAT_IDS = {
            ItemID.RAW_SUNLIGHT_ANTELOPE
    };
    private static final String[] SUNLIGHT_ANTELOPE_POUCHABLE_MEAT_NAMES = {
            "Raw sunlight antelope"
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
     * - All known local pitfall traps are stored as 2x2 footprints.
     * - Pit 1 has verified per-tile object IDs.
     * - Object IDs increment in the same role order as Pit 1:
     *   top-left, bottom-left, top-right, bottom-right.
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
                    List.of(
                            new PitfallTile("top-left", 51682, new WorldPoint(1749, 3015, 0)),
                            new PitfallTile("bottom-left", 51683, new WorldPoint(1749, 3014, 0)),
                            new PitfallTile("top-right", 51684, new WorldPoint(1750, 3015, 0)),
                            new PitfallTile("bottom-right", 51685, new WorldPoint(1750, 3014, 0))
                    ),
                    null,
                    null,
                    null,
                    2
            ),
            new PitfallDefinition(
                    "Pit 3 - south-east",
                    PitOrientation.WEST_EAST,
                    JumpAxis.NORTH_SOUTH,
                    List.of(
                            new PitfallTile("top-left", 51691, new WorldPoint(1751, 3010, 0)),
                            new PitfallTile("bottom-left", 51692, new WorldPoint(1751, 3009, 0)),
                            new PitfallTile("top-right", 51693, new WorldPoint(1752, 3010, 0)),
                            new PitfallTile("bottom-right", 51694, new WorldPoint(1752, 3009, 0))
                    ),
                    null,
                    null,
                    null,
                    3
            ),
            new PitfallDefinition(
                    "Pit 4 - south-west",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(
                            new PitfallTile("top-left", 51679, new WorldPoint(1738, 3002, 0)),
                            new PitfallTile("bottom-left", 51680, new WorldPoint(1738, 3001, 0)),
                            new PitfallTile("top-right", 51681, new WorldPoint(1739, 3002, 0)),
                            new PitfallTile("bottom-right", 51682, new WorldPoint(1739, 3001, 0))
                    ),
                    null,
                    null,
                    null,
                    4
            ),
            new PitfallDefinition(
                    "Pit 5 - south",
                    PitOrientation.NORTH_SOUTH,
                    JumpAxis.EAST_WEST,
                    List.of(
                            new PitfallTile("top-left", 51701, new WorldPoint(1749, 3000, 0)),
                            new PitfallTile("bottom-left", 51702, new WorldPoint(1749, 2999, 0)),
                            new PitfallTile("top-right", 51703, new WorldPoint(1750, 3000, 0)),
                            new PitfallTile("bottom-right", 51704, new WorldPoint(1750, 2999, 0))
                    ),
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
    private long busyStartedAt;
    private long captureDetectedAt;
    private long captureLootReadyAt;
    private long lastCaptureJumpAt;
    private long pendingJumpClickedAt;
    private int captureRejumpDelayMs = CAPTURE_REJUMP_DELAY_MS;
    private int captureRejumpAttempts;
    private boolean waitingForJumpAnimation;
    private JumpRoute activeJumpRoute;
    private WorldPoint npcJumpStartTile;
    private WorldPoint npcLastTrackedTile;
    private String npcJumpStartSide = TrapSide.UNKNOWN.name();
    private String npcLastTrackedSide = TrapSide.UNKNOWN.name();
    private boolean npcCrossedTrapLogged;
    private boolean kandarinHeadgearAvailable;
    private boolean meatPouchAvailable;
    private String lastDecision = "Starting";
    private String lastFailure = "";
    private String lastLureEvidence = "None";
    private String lastNpcQuery = "None";
    private String lastPitQuery = "None";
    private int luredNpcIndex = -1;
    private long lureConfirmedAt;

    public boolean run(PitfallHunterConfig config)
    {
        this.config = config;
        lastNpcQuery = "None";
        lastPitQuery = "None";
        transition(State.CHECK_REQUIREMENTS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || !isRunning()) {
                    return;
                }

                if (handleBusySkip()) {
                    return;
                }

                if (recoverTimedOutState()) {
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
        if (forceLootPhaseBeforeAction()) {
            return;
        }

        if (shouldStartBanking()) {
            beginBanking();
            return;
        }

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
            case BANK:
                bank();
                break;
            case RETURN_TO_START:
                returnToStart();
                break;
            case RECOVER:
                recover();
                break;
            case STOP:
                shutdown();
                break;
        }
    }

    private boolean forceLootPhaseBeforeAction()
    {
        if (selectedPit == null
                || state == State.LOOT_PIT
                || state == State.HANDLE_MEAT_POUCH
                || state == State.BANK
                || state == State.RETURN_TO_START
                || state == State.STOP) {
            return false;
        }

        return forceLootPhaseIfCollapsed("pre-action scan");
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

        if (!hasLogs() || shouldPrepareMoreLogs()) {
            recordDecision("Preparing logs " + logCount() + "/" + targetPreparedLogs());
            boolean prepared = cutLogsLocally();
            if (prepared) {
                return;
            }
            if (shouldPrepareMoreLogs()) {
                return;
            }
            if (hasLogs()) {
                return;
            }

            if (!hasLogs()) {
                stop("No verified log type available");
                return;
            }
        }

        if (!hasKnife()) {
            stop("No knife available");
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
        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = 0;
        pendingJumpClickedAt = 0;
        captureRejumpDelayMs = CAPTURE_REJUMP_DELAY_MS;
        captureRejumpAttempts = 0;
        waitingForJumpAnimation = false;
        resetNpcJumpTracking();
        resetLureTracking();
        skippedTrappedPits.clear();

        if (selectCollapsedPit()) {
            return;
        }

        recordDecision("Looking for nearest antelope");
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
            kandarinHeadgearAvailable = hasKandarinHeadgear();
            recordDecision("Out of logs; returning to log preparation");
            transition(State.CHECK_REQUIREMENTS);
            return;
        }

        selectedNpc = findClosestSunlightAntelope();
        if (selectedNpc == null) {
            recordDecision("No antelope nearby; refreshing");
            shortRandomWait();
            transition(State.REFRESH_PITS);
            return;
        }

        log("Selected closest NPC: id=" + selectedNpc.getId()
                + " tile=" + selectedNpc.getWorldLocation()
                + " playerTile=" + Rs2Player.getWorldLocation());
        recordDecision("Teasing selected NPC");
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
        recordDecision("Preparing selected pit");

        transition(State.PREPARE_PIT);
    }

    private void preparePit()
    {
        if (selectedPit == null || selectedPit.getAnchorTile() == null) {
            transition(State.RECOVER);
            return;
        }

        if (forceLootPhaseIfCollapsed("prepare state scan")) {
            return;
        }

        if (!hasLogs()) {
            transition(State.CHECK_REQUIREMENTS);
            return;
        }

        if (!hasKnife()) {
            stop("No knife available");
            return;
        }

        if (!prepareSelectedPit()) {
            recordDecision("Trap setup was not detected; recovering");
            transition(State.RECOVER);
            return;
        }

        recordDecision("Jumping prepared pit");
        shortRandomWait();
        transition(State.JUMP_PIT);
    }

    private void lureNpc()
    {
        if (selectedNpc == null || !isSunlightAntelope(selectedNpc)) {
            log("Lure failed: selected NPC is no longer valid", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        recordDecision("Watching for tease lure evidence");
        boolean lured = teaseNpcUntilInteraction(selectedNpc);
        log("Lure interaction result: " + lured);
        if (!lured) {
            transition(State.RECOVER);
            return;
        }

        if (!selectClosestPitForNpc(selectedNpc, "after-tease")) {
            log("No usable pit found after tease; refreshing instead of stopping", Level.WARN);
            transition(State.REFRESH_PITS);
            return;
        }

        recordDecision("Preparing closest pit after tease");
        shortRandomWait();
        transition(State.PREPARE_PIT);
    }

    private void jumpPit()
    {
        if (selectedPit == null) {
            log("Jump aborted: missing selected pit", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        recordDecision("Waiting for Jump action on " + selectedPit.name);
        Rs2TileObjectModel pitObject = waitForPitObject(selectedPit, JUMP_PIT_ACTION, JUMP_OBJECT_APPEAR_TIMEOUT_MS);
        if (pitObject == null) {
            selectedPitState = getPitState(selectedPit);
            if (selectedPitState == PitfallState.COLLAPSED && forceLootPhaseIfCollapsed("jump state scan")) {
                return;
            }
            log("Jump failed: trapped pit object missing. state=" + selectedPitState, Level.WARN);
            transition(State.RECOVER);
            return;
        }

        JumpRoute jumpRoute = selectedPit.getNearestJumpRoute(Rs2Player.getWorldLocation());
        log("Jump route selected: pit=" + selectedPit.name
                + " from=" + (jumpRoute == null ? "none" : jumpRoute.from)
                + " to=" + (jumpRoute == null ? "none" : jumpRoute.to)
                + " playerTile=" + Rs2Player.getWorldLocation()
                + " npcTile=" + (selectedNpc == null ? "none" : selectedNpc.getWorldLocation()));

        if (jumpRoute != null && selectedNpc != null) {
            startNpcJumpTracking(jumpRoute);
        }
        recordDecision("Clicking Jump on " + selectedPit.name);
        boolean jumped = clickPitObject(pitObject, JUMP_PIT_ACTION);
        log("Jump result: " + jumped);

        if (!jumped) {
            transition(State.RECOVER);
            return;
        }

        startCaptureTimersOnNextJumpAnimation(true);
        recordDecision("Waiting for capture");
        transition(State.WAIT_FOR_CAPTURE);
    }

    private void waitForCapture()
    {
        updateJumpAnimationTimer();
        selectedPitState = getPitState(selectedPit);
        log("Capture wait state: " + selectedPit.name + " -> " + selectedPitState);
        trackNpcJumpAcrossTrap();

        if (selectedPitState == PitfallState.COLLAPSED && forceLootPhaseIfCollapsed("capture state scan")) {
            return;
        }

        if (waitingForJumpAnimation) {
            recordDecision("Waiting for jump animation");
            return;
        }

        if (captureDetectedAt == 0 && getSelectedNpcCaptureGraphic() > 0) {
            markCaptureDetected("NPC graphic " + getSelectedNpcCaptureGraphic());
        }

        if (captureDetectedAt == 0 && retryJumpIfStillTrapped()) {
            return;
        }

        if (captureDetectedAt > 0) {
            long remainingDelay = captureLootReadyAt - System.currentTimeMillis();
            if (remainingDelay > 0) {
                recordDecision("Loot delay " + remainingDelay + "ms");
                return;
            }

            selectedPitState = getPitState(selectedPit);
            Rs2TileObjectModel lootObject = waitForCollapsedTrapObject(selectedPit, DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS);
            if (lootObject == null) {
                captureLootReadyAt = System.currentTimeMillis() + 1000;
                recordDecision("Waiting for collapsed trap");
                log("Capture delay elapsed but Collapsed Trap with Dismantle is not available yet; state="
                        + selectedPitState, Level.WARN);
                return;
            }

            selectedPitState = PitfallState.COLLAPSED;
            skippedTrappedPits.remove(selectedPit.name);
            recordDecision("Looting collapsed trap");
            transition(State.LOOT_PIT);
            return;
        }

        if (timedOut(getCaptureTimeoutMs())) {
            PitfallObjectCandidate collapsed = findNearestCollapsedTrapCandidate(25);
            if (collapsed != null) {
                selectedPit = collapsed.pit;
                selectedPitState = PitfallState.COLLAPSED;
                lastPitQuery = formatPitCandidate("Late collapsed", collapsed, null);
                forceLootPhaseIfCollapsed("late collapsed pit scan");
                return;
            }

            log("No collapse after jump; rotating away from pit: " + selectedPit.name, Level.WARN);
            if (selectedPitState == PitfallState.TRAPPED) {
                skippedTrappedPits.add(selectedPit.name);
            }
            if (isNpcFollowingPlayer(selectedNpc)) {
                selectedPit = null;
                selectedPitState = PitfallState.UNKNOWN;
                recordDecision("NPC still lured; selecting another pit");
                transition(State.SELECT_PIT);
            } else {
                recordDecision("Lure lost; refreshing");
                transition(State.REFRESH_PITS);
            }
        }

        if (isNpcFollowingPlayer(selectedNpc)) {
            return;
        }
    }

    private void markCaptureDetected(String evidence)
    {
        captureDetectedAt = System.currentTimeMillis();
        int delay = ThreadLocalRandom.current().nextInt(CAPTURE_LOOT_DELAY_MIN_MS, CAPTURE_LOOT_DELAY_MAX_MS + 1);
        captureLootReadyAt = captureDetectedAt + delay;
        log("Capture detected by " + evidence + "; loot ready in " + delay + "ms");
        recordDecision("Waiting before loot");
    }

    private boolean forceLootPhaseIfCollapsed(String evidence)
    {
        Rs2TileObjectModel collapsedTrap = getCollapsedTrapObject(selectedPit);
        if (collapsedTrap == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        selectedPitState = PitfallState.COLLAPSED;
        captureDetectedAt = now;
        captureLootReadyAt = now;
        waitingForJumpAnimation = false;
        pendingJumpClickedAt = 0;
        if (selectedPit != null) {
            skippedTrappedPits.remove(selectedPit.name);
        }

        log("Collapsed Trap with Dismantle detected by " + evidence + "; forcing loot phase"
                + (selectedPit == null ? "" : " for " + selectedPit.name));
        recordDecision("Looting collapsed trap");
        shortRandomWait();
        transition(State.LOOT_PIT);
        return true;
    }

    private boolean retryJumpIfStillTrapped()
    {
        if (waitingForJumpAnimation) {
            return false;
        }

        if (captureRejumpAttempts >= CAPTURE_REJUMP_MAX_ATTEMPTS) {
            return false;
        }

        selectedPitState = getPitState(selectedPit);
        if (selectedPitState == PitfallState.COLLAPSED) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (lastCaptureJumpAt == 0 || now - lastCaptureJumpAt < captureRejumpDelayMs) {
            return false;
        }

        Rs2TileObjectModel pitObject = getPitObject(selectedPit, JUMP_PIT_ACTION);
        if (pitObject == null) {
            PitfallObjectCandidate jumpCandidate = findNearestPitObjectCandidate(JUMP_PIT_ACTION, 25);
            if (jumpCandidate == null) {
                return false;
            }
            selectedPit = jumpCandidate.pit;
            selectedPitState = PitfallState.TRAPPED;
            lastPitQuery = formatPitCandidate("Retry jump", jumpCandidate, null);
            pitObject = jumpCandidate.object;
        }

        selectedPitState = PitfallState.TRAPPED;
        captureRejumpAttempts++;
        recordDecision("Retrying Jump " + captureRejumpAttempts);
        log("Capture wait still has Jump option; retrying jump attempt " + captureRejumpAttempts
                + "; next retry delay=" + captureRejumpDelayMs + "ms", Level.WARN);
        boolean clicked = clickPitObject(pitObject, JUMP_PIT_ACTION);
        log("Capture retry jump result: " + clicked);
        if (clicked) {
            startCaptureTimersOnNextJumpAnimation(false);
        }
        return clicked;
    }

    private void startCaptureTimersOnNextJumpAnimation(boolean resetAttempts)
    {
        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = 0;
        pendingJumpClickedAt = System.currentTimeMillis();
        waitingForJumpAnimation = true;
        scheduleNextCaptureRejumpDelay();
        if (resetAttempts) {
            captureRejumpAttempts = 0;
        }
    }

    private void updateJumpAnimationTimer()
    {
        if (!waitingForJumpAnimation) {
            return;
        }

        if (Rs2Player.getAnimation() == PIT_JUMP_ANIMATION_ID) {
            long now = System.currentTimeMillis();
            waitingForJumpAnimation = false;
            pendingJumpClickedAt = 0;
            lastCaptureJumpAt = now;
            stateStartedAt = now;
            log("Pit jump animation detected; capture timers started. anim=" + PIT_JUMP_ANIMATION_ID
                    + " retryDelay=" + captureRejumpDelayMs + "ms");
            return;
        }

        if (System.currentTimeMillis() - pendingJumpClickedAt > PIT_JUMP_ANIMATION_START_TIMEOUT_MS) {
            waitingForJumpAnimation = false;
            pendingJumpClickedAt = 0;
            lastCaptureJumpAt = System.currentTimeMillis();
            stateStartedAt = lastCaptureJumpAt;
            log("Pit jump animation was not observed after click; starting capture timers from fallback", Level.WARN);
        }
    }

    private void scheduleNextCaptureRejumpDelay()
    {
        captureRejumpDelayMs = CAPTURE_REJUMP_DELAY_MS + ThreadLocalRandom.current()
                .nextInt(CAPTURE_REJUMP_EXTRA_DELAY_MIN_MS, CAPTURE_REJUMP_EXTRA_DELAY_MAX_MS + 1);
    }

    private int getCaptureTimeoutMs()
    {
        return Math.max(config.captureTimeoutMs(), CAPTURE_TIMEOUT_MIN_MS);
    }

    private boolean prepareSelectedPit()
    {
        for (int attempt = 1; attempt <= PREPARE_PIT_MAX_ATTEMPTS; attempt++) {
            if (!clearSelectedItem("before Trap")) {
                log("Prepare attempt " + attempt + " could not clear selected item", Level.WARN);
            }

            Rs2TileObjectModel pitObject = getPitObject(selectedPit, TRAP_PIT_ACTION);
            if (pitObject == null) {
                selectedPitState = getPitState(selectedPit);
                log("Prepare attempt " + attempt + " failed: no Trap action object for "
                        + selectedPit.name + " currentState=" + selectedPitState, Level.WARN);
                return selectedPitState == PitfallState.TRAPPED;
            }

            if (!clearSelectedItem("before Trap click")) {
                log("Prepare attempt " + attempt + " still has selected item before clicking Trap", Level.WARN);
            }

            log("Preparing pit with logs: " + selectedPit.name + " attempt=" + attempt);
            recordDecision("Clicking Trap on " + selectedPit.name + " attempt " + attempt);
            boolean clicked = clickPitObject(pitObject, TRAP_PIT_ACTION);
            log("Prepare attempt " + attempt + " click result: " + clicked);

            if (!clicked) {
                shortRandomWait();
                continue;
            }

            boolean jumpReady = waitUntil(() -> getPitObject(selectedPit, JUMP_PIT_ACTION) != null,
                    PREPARE_TRAP_TIMEOUT_MS);
            selectedPitState = getPitState(selectedPit);
            log("Prepare attempt " + attempt + " jump action detected: " + jumpReady
                    + " state=" + selectedPitState);
            if (jumpReady || selectedPitState == PitfallState.TRAPPED) {
                selectedPitState = PitfallState.TRAPPED;
                return true;
            }

            log("Prepare attempt " + attempt + " advancing after Trap click; Jump step will verify object", Level.WARN);
            shortRandomWait();
            return true;
        }

        selectedPitState = getPitState(selectedPit);
        return selectedPitState == PitfallState.TRAPPED;
    }

    private void lootPit()
    {
        selectedPitState = getPitState(selectedPit);
        Rs2TileObjectModel pitObject = waitForCollapsedTrapObject(selectedPit, DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS);
        if (pitObject == null) {
            selectedPitState = getPitState(selectedPit);
            log("Loot deferred: Collapsed Trap with Dismantle missing. state=" + selectedPitState, Level.WARN);
            if (selectedPitState == PitfallState.TRAPPED) {
                recordDecision("Still trapped; waiting for capture");
                transition(State.WAIT_FOR_CAPTURE);
            } else {
                transition(State.RECOVER);
            }
            return;
        }

        if (isSpikedTrapObject(pitObject)) {
            jumpSpikedTrapInsteadOfLooting(pitObject);
            return;
        }

        selectedPitState = PitfallState.COLLAPSED;
        int before = Rs2Inventory.count();
        recordDecision("Dismantling collapsed trap");
        shortRandomWait();
        boolean clicked = clickPitObject(pitObject, DISMANTLE_TRAP_ACTION);
        boolean changed = clicked && waitUntil(() -> Rs2Inventory.count() != before
                || getPitState(selectedPit) != PitfallState.COLLAPSED, 8000);

        log("Loot result: clicked=" + clicked + " changed=" + changed);
        if (!clicked) {
            transition(State.RECOVER);
            return;
        }

        if (changed) {
            shortRandomWait();
        }
        transition(State.HANDLE_MEAT_POUCH);
    }

    private void handleMeatPouch()
    {
        if (dropSunlightAntelopesForSpace() > 0) {
            recordDecision("Dropped antelope loot after loot");
        }

        if (!meatPouchAvailable || !hasPouchableMeat()) {
            recordDecision("Handling inventory after loot");
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
        recordDecision("Handling inventory after pouch");
        handlePostLootInventory(true);
        transition(State.REFRESH_PITS);
    }

    private boolean shouldStartBanking()
    {
        if (!config.bankingEnabled()
                || state == State.BANK
                || state == State.RETURN_TO_START
                || state == State.STOP) {
            return false;
        }

        int threshold = Math.max(0, config.bankBelowHitpoints());
        boolean lowHp = threshold > 0
                && currentHitpoints() > 0
                && currentHitpoints() <= threshold;
        if (!lowHp) {
            return false;
        }

        if (shouldFinishPitBeforeBanking()) {
            recordDecision("Finish trapped pit before banking");
            return false;
        }

        return true;
    }

    private boolean shouldFinishPitBeforeBanking()
    {
        if (state == State.JUMP_PIT
                || state == State.WAIT_FOR_CAPTURE
                || state == State.LOOT_PIT
                || state == State.HANDLE_MEAT_POUCH) {
            return true;
        }

        if (selectedPit == null) {
            return false;
        }

        selectedPitState = getPitState(selectedPit);
        return selectedPitState == PitfallState.TRAPPED
                || selectedPitState == PitfallState.COLLAPSED;
    }

    private void beginBanking()
    {
        log("Banking triggered at HP " + currentHitpoints() + "/" + maxHitpoints());
        recordDecision("Banking for low HP");
        selectedNpc = null;
        selectedPit = null;
        selectedPitState = PitfallState.UNKNOWN;
        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = 0;
        pendingJumpClickedAt = 0;
        captureRejumpAttempts = 0;
        waitingForJumpAnimation = false;
        resetNpcJumpTracking();
        resetLureTracking();
        skippedTrappedPits.clear();
        transition(State.BANK);
    }

    private void bank()
    {
        if (!clearSelectedItem("before banking")) {
            log("Could not clear selected item before banking", Level.WARN);
        }

        recordDecision("Walking to bank");
        if (!openBankAtBankLocation()) {
            log("Banking: bank is not open yet", Level.WARN);
            return;
        }

        recordDecision("Emptying containers");
        boolean emptiedContainers = Rs2Bank.emptyContainers();
        log("Bank empty containers result: " + emptiedContainers);

        depositBankableInventory();

        if (!healToFullAtBank()) {
            stop("Banking failed: no edible food available to heal");
            return;
        }

        if (!Rs2Bank.isOpen() && !openBankAtBankLocation()) {
            log("Banking: could not reopen bank after healing", Level.WARN);
            return;
        }

        depositBankableInventory();
        Rs2Bank.closeBank();
        waitUntil(() -> !Rs2Bank.isOpen(), 2000);

        recordDecision("Returning to start");
        transition(State.RETURN_TO_START);
    }

    private boolean healToFullAtBank()
    {
        PitfallHunterConfig.BankFood food = config.bankFood();
        if (food == null) {
            log("Banking heal failed: no supported food selected", Level.WARN);
            return false;
        }

        while (isRunning() && !Rs2Player.isFullHealth()) {
            if (!Rs2Bank.isOpen() && !openBankAtBankLocation()) {
                return false;
            }

            int missingHp = Math.max(0, maxHitpoints() - currentHitpoints());
            if (missingHp == 0) {
                break;
            }

            int neededFood = Math.max(1, (int) Math.ceil((double) missingHp / food.healAmount()));
            int availableFood = Rs2Bank.count(food.itemId());
            if (availableFood <= 0) {
                log("Banking heal failed: no " + food + " found in bank", Level.WARN);
                return false;
            }

            int withdrawAmount = Math.min(neededFood, availableFood);
            withdrawAmount = Math.min(withdrawAmount, Math.max(0, Rs2Inventory.emptySlotCount()));
            if (withdrawAmount <= 0) {
                log("Banking heal failed: no inventory space for " + food, Level.WARN);
                return false;
            }

            int beforeFoodCount = Rs2Inventory.count(food.itemId());
            int expectedFoodCount = beforeFoodCount + withdrawAmount;

            recordDecision("Withdrawing " + withdrawAmount + " food");
            boolean withdrew = withdrawAmount == 1
                    ? Rs2Bank.withdrawOne(food.itemId())
                    : Rs2Bank.withdrawX(food.itemId(), withdrawAmount);
            if (!withdrew || !waitUntil(() -> Rs2Inventory.count(food.itemId()) >= expectedFoodCount, 5000)) {
                log("Banking heal failed: could not withdraw " + withdrawAmount + " " + food, Level.WARN);
                return false;
            }

            Rs2Bank.closeBank();
            waitUntil(() -> !Rs2Bank.isOpen(), 2000);

            recordDecision("Eating to full HP");
            if (!eatWithdrawnFoodToFull(food)) {
                return false;
            }
        }

        if (!Rs2Bank.isOpen()) {
            openBankAtBankLocation();
        }
        return Rs2Player.isFullHealth();
    }

    private boolean openBankAtBankLocation()
    {
        if (Rs2Bank.isOpen()) {
            return true;
        }

        WorldPoint current = Rs2Player.getWorldLocation();
        if (current == null) {
            return false;
        }

        if (current.distanceTo(BANK_LOCATION) > BANK_LOCATION_REACHED_DISTANCE) {
            recordDecision("Walking to bank tile");
            Rs2Walker.walkTo(BANK_LOCATION, BANK_LOCATION_REACHED_DISTANCE);
            waitUntil(() -> {
                WorldPoint tile = Rs2Player.getWorldLocation();
                return tile != null && tile.distanceTo(BANK_LOCATION) <= BANK_LOCATION_REACHED_DISTANCE;
            }, WALK_TIMEOUT_MS);
        }

        current = Rs2Player.getWorldLocation();
        if (current == null || current.distanceTo(BANK_LOCATION) > BANK_LOCATION_REACHED_DISTANCE) {
            log("Banking: not at manual bank location " + BANK_LOCATION, Level.WARN);
            return false;
        }

        recordDecision("Opening bank");
        shortRandomWait();
        return Rs2Bank.openBank();
    }

    private boolean eatWithdrawnFoodToFull(PitfallHunterConfig.BankFood food)
    {
        int attempts = 0;
        while (isRunning()
                && !Rs2Player.isFullHealth()
                && Rs2Inventory.hasItem(food.itemId())
                && attempts < BANK_HEAL_MAX_FOOD_ATTEMPTS) {
            int beforeHp = currentHitpoints();
            int beforeFoodCount = Rs2Inventory.count(food.itemId());
            boolean ate = Rs2Inventory.interact(food.itemId(), "Eat");
            if (!ate || !waitUntil(() -> Rs2Player.isFullHealth()
                    || currentHitpoints() > beforeHp
                    || Rs2Inventory.count(food.itemId()) < beforeFoodCount, 3000)) {
                log("Banking heal failed: could not eat " + food, Level.WARN);
                return false;
            }

            attempts++;
            if (!Rs2Player.isFullHealth() && Rs2Inventory.hasItem(food.itemId())) {
                shortRandomWait();
            }
        }

        return Rs2Player.isFullHealth() || !Rs2Inventory.hasItem(food.itemId());
    }

    private void depositBankableInventory()
    {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        boolean deposited = Rs2Bank.depositAllExcept(this::shouldRetainAfterBanking);
        log("Bank deposit loot result: " + deposited);
        shortRandomWait();
    }

    private boolean shouldRetainAfterBanking(Rs2ItemModel item)
    {
        if (item == null || item.getName() == null) {
            return false;
        }

        String name = item.getName();
        return item.getId() == TEASING_STICK_ITEM_ID
                || name.equalsIgnoreCase(TEASING_STICK_NAME)
                || name.equalsIgnoreCase(KNIFE_NAME)
                || name.equalsIgnoreCase(KANDARIN_HEADGEAR_NAME)
                || Arrays.stream(LOG_ITEM_NAMES).anyMatch(logName -> logName.equalsIgnoreCase(name))
                || Arrays.stream(MEAT_POUCH_NAMES).anyMatch(pouchName -> pouchName.equalsIgnoreCase(name))
                || (config.fletchAntlers() && name.equalsIgnoreCase(CHISEL_NAME));
    }

    private void returnToStart()
    {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        recordDecision("Walking to start");
        WorldPoint current = Rs2Player.getWorldLocation();
        if (current != null && current.distanceTo(START_LOCATION) <= 2) {
            transition(State.CHECK_REQUIREMENTS);
            return;
        }

        Rs2Walker.walkTo(START_LOCATION);
        waitUntil(() -> {
            WorldPoint tile = Rs2Player.getWorldLocation();
            return tile != null && tile.distanceTo(START_LOCATION) <= 2;
        }, WALK_TIMEOUT_MS);
    }

    private int currentHitpoints()
    {
        return Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    private int maxHitpoints()
    {
        return Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
    }

    private void recover()
    {
        log("Recovering from state: " + state + " pit=" + (selectedPit == null ? "none" : selectedPit.name));
        selectedNpc = null;
        selectedPit = null;
        selectedPitState = PitfallState.UNKNOWN;
        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = 0;
        pendingJumpClickedAt = 0;
        captureRejumpAttempts = 0;
        waitingForJumpAnimation = false;
        resetNpcJumpTracking();
        resetLureTracking();
        shortRandomWait();
        transition(State.REFRESH_PITS);
    }

    private boolean selectCollapsedPit()
    {
        PitfallObjectCandidate candidate = findNearestCollapsedTrapCandidate(25);

        if (candidate == null) {
            PitfallObjectCandidate spikedCandidate = findNearestSpikedTrapCandidate(25);
            if (spikedCandidate == null) {
                lastPitQuery = "Collapsed: none";
                return false;
            }

            selectedPit = spikedCandidate.pit;
            selectedNpc = null;
            selectedPitState = PitfallState.TRAPPED;
            lastPitQuery = formatPitCandidate("Spiked trap", spikedCandidate, null);
            recordDecision("Found spiked trap; jumping instead of looting");
            transition(State.JUMP_PIT);
            return true;
        }

        if (isSpikedTrapObject(candidate.object)) {
            selectedPit = candidate.pit;
            selectedNpc = null;
            selectedPitState = PitfallState.TRAPPED;
            lastPitQuery = formatPitCandidate("Spiked trap", candidate, null);
            recordDecision("Found spiked trap; jumping instead of looting");
            transition(State.JUMP_PIT);
            return true;
        }

        selectedPit = candidate.pit;
        selectedNpc = null;
        lastPitQuery = formatPitCandidate("Collapsed", candidate, null);
        log("Selected collapsed pit: " + selectedPit.name
                + " object=" + candidate.object.getId()
                + " tile=" + candidate.object.getWorldLocation());

        selectedPitState = PitfallState.COLLAPSED;
        recordDecision("Found collapsed pit; looting first");
        transition(State.LOOT_PIT);
        return true;
    }

    private Rs2NpcModel findClosestSunlightAntelope()
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                .withName(SUNLIGHT_ANTELOPE_NAME)
                .where(this::isSunlightAntelope)
                .where(candidate -> candidate.getWorldLocation() != null)
                .where(candidate -> !candidate.isInteracting()
                        || candidate.isInteractingWithPlayer()
                        || isRecentlyConfirmedLure(candidate))
                .nearestOnClientThread(20);

        lastNpcQuery = npc == null
                ? "Nearest antelope: none"
                : "Nearest antelope: " + npc.getId()
                + " d=" + distanceBetween(npc.getWorldLocation(), Rs2Player.getWorldLocation())
                + " tile=" + npc.getWorldLocation();
        return npc;
    }

    private PitfallDefinition findClosestUsablePitForNpc(Rs2NpcModel npc)
    {
        if (npc == null || npc.getWorldLocation() == null) {
            return null;
        }

        if (!hasLogs()) {
            lastPitQuery = "Usable pit: no logs";
            return null;
        }

        WorldPoint npcTile = npc.getWorldLocation();
        PitfallObjectCandidate selectedCandidate = Microbot.getRs2TileObjectCache().query()
                .within(25)
                .where(this::isPitfallObject)
                .where(object -> object.getWorldLocation() != null)
                .where(object -> hasPitObjectAction(object, TRAP_PIT_ACTION))
                .toListOnClientThread()
                .stream()
                .map(this::toPitObjectCandidate)
                .filter(pitCandidate -> pitCandidate.pit != null)
                .filter(pitCandidate -> !skippedTrappedPits.contains(pitCandidate.pit.name))
                .min(Comparator.comparingInt((PitfallObjectCandidate pitCandidate) -> distanceToPlayer(pitCandidate.object))
                        .thenComparingInt(pitCandidate -> distanceBetween(pitCandidate.object.getWorldLocation(), npcTile))
                        .thenComparingInt(pitCandidate -> pitCandidate.pit.getPriority()))
                .orElse(null);

        lastPitQuery = formatPitCandidate("Usable", selectedCandidate, npcTile);
        return selectedCandidate == null ? null : selectedCandidate.pit;
    }

    private boolean selectClosestPitForNpc(Rs2NpcModel npc, String phase)
    {
        PitfallDefinition pit = findClosestUsablePitForNpc(npc);
        if (pit == null) {
            return false;
        }

        selectedPit = pit;
        selectedPitState = getPitState(selectedPit);
        Rs2TileObjectModel selectedPitObject = getPitObject(selectedPit, TRAP_PIT_ACTION);
        log("Selected closest pit " + phase + ": " + selectedPit.name + " state=" + selectedPitState
                + " playerDistance=" + distanceToPlayer(selectedPit)
                + " npcDistance=" + distanceTo(selectedPit, npc.getWorldLocation())
                + " objectId=" + (selectedPitObject == null ? "none" : selectedPitObject.getId())
                + " objectTile=" + (selectedPitObject == null ? "none" : selectedPitObject.getWorldLocation())
                + " npc=" + npc.getId() + " tile=" + npc.getWorldLocation());
        return true;
    }

    private int distanceToPlayer(PitfallDefinition pit)
    {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile == null || pit == null) {
            return Integer.MAX_VALUE;
        }
        return distanceTo(pit, playerTile);
    }

    private int distanceToPlayer(WorldPoint tile)
    {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile == null || tile == null) {
            return Integer.MAX_VALUE;
        }
        return playerTile.distanceTo(tile);
    }

    private int distanceToPlayer(Rs2TileObjectModel object)
    {
        return object == null ? Integer.MAX_VALUE : distanceToPlayer(object.getWorldLocation());
    }

    private int distanceBetween(WorldPoint first, WorldPoint second)
    {
        if (first == null || second == null) {
            return Integer.MAX_VALUE;
        }
        return first.distanceTo(second);
    }

    private int distanceTo(PitfallDefinition pit, WorldPoint tile)
    {
        if (pit == null || tile == null) {
            return Integer.MAX_VALUE;
        }

        return pit.distanceTo(tile);
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
        return npc != null
                && (npc.isInteractingWithPlayer()
                || isPlayerInteractingWithNpc(npc)
                || isRecentlyConfirmedLure(npc));
    }

    private boolean isPlayerInteractingWithNpc(Rs2NpcModel npc)
    {
        Actor interacting = Rs2Player.getInteracting();
        if (!(interacting instanceof Rs2NpcModel)) {
            return false;
        }

        Rs2NpcModel interactingNpc = (Rs2NpcModel) interacting;
        return interactingNpc.getId() == npc.getId()
                && interactingNpc.getIndex() == npc.getIndex();
    }

    private void startNpcJumpTracking(JumpRoute jumpRoute)
    {
        activeJumpRoute = jumpRoute;
        npcJumpStartTile = selectedNpc == null ? null : selectedNpc.getWorldLocation();
        npcLastTrackedTile = npcJumpStartTile;
        npcJumpStartSide = selectedPit == null ? TrapSide.UNKNOWN.name() : selectedPit.getTrapSide(npcJumpStartTile).name();
        npcLastTrackedSide = npcJumpStartSide;
        npcCrossedTrapLogged = false;

        log("NPC jump tracking start: pit=" + selectedPit.name
                + " route=" + selectedPit.getTrapSide(jumpRoute.from) + "->" + selectedPit.getTrapSide(jumpRoute.to)
                + " npcStartTile=" + npcJumpStartTile
                + " npcStartSide=" + npcJumpStartSide
                + " playerFrom=" + jumpRoute.from
                + " playerTo=" + jumpRoute.to);
    }

    private void trackNpcJumpAcrossTrap()
    {
        if (selectedPit == null || selectedNpc == null || activeJumpRoute == null || npcJumpStartTile == null) {
            return;
        }

        WorldPoint currentTile = selectedNpc.getWorldLocation();
        if (currentTile == null || currentTile.equals(npcLastTrackedTile)) {
            return;
        }

        String currentSide = selectedPit.getTrapSide(currentTile).name();
        if (!Objects.equals(currentSide, npcLastTrackedSide)) {
            log("NPC trap side changed: pit=" + selectedPit.name
                    + " from=" + npcLastTrackedSide
                    + " to=" + currentSide
                    + " startTile=" + npcJumpStartTile
                    + " currentTile=" + currentTile);
        }

        if (!npcCrossedTrapLogged && selectedPit.hasCrossedTrap(npcJumpStartTile, currentTile)) {
            npcCrossedTrapLogged = true;
            log("NPC crossed trap after player jump: pit=" + selectedPit.name
                    + " startTile=" + npcJumpStartTile
                    + " startSide=" + npcJumpStartSide
                    + " currentTile=" + currentTile
                    + " currentSide=" + currentSide);
        }

        npcLastTrackedTile = currentTile;
        npcLastTrackedSide = currentSide;
    }

    private void resetNpcJumpTracking()
    {
        activeJumpRoute = null;
        npcJumpStartTile = null;
        npcLastTrackedTile = null;
        npcJumpStartSide = TrapSide.UNKNOWN.name();
        npcLastTrackedSide = TrapSide.UNKNOWN.name();
        npcCrossedTrapLogged = false;
    }

    private int getSelectedNpcCaptureGraphic()
    {
        if (selectedNpc == null) {
            return -1;
        }

        try {
            int graphic = selectedNpc.getGraphic();
            if (contains(CAPTURE_DEATH_GRAPHIC_IDS, graphic)) {
                return graphic;
            }

            return Arrays.stream(CAPTURE_DEATH_GRAPHIC_IDS)
                    .filter(selectedNpc::hasSpotAnim)
                    .findFirst()
                    .orElse(-1);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private String getLureAction(Rs2NpcModel npc)
    {
        String action = getAvailableNpcAction(npc, List.of(LURE_NPC_ACTION, "Poke"));
        if (action.isEmpty()) {
            log("No direct Tease/Poke action found on NPC");
        }
        return action;
    }

    private boolean teaseNpcUntilInteraction(Rs2NpcModel npc)
    {
        if (isNpcFollowingPlayer(npc)) {
            return true;
        }

        for (int attempt = 1; attempt <= LURE_MAX_ATTEMPTS; attempt++) {
            WorldPoint npcStartTile = npc.getWorldLocation();
            WorldPoint playerStartTile = Rs2Player.getWorldLocation();
            int startDistance = distanceBetween(npcStartTile, playerStartTile);
            String action = getLureAction(npc);
            boolean clicked = false;
            if (!action.isEmpty()) {
                log("Tease attempt " + attempt + " using NPC action: " + action);
                clicked = npc.click(action);
            }

            if (!clicked) {
                log("Tease attempt " + attempt + " using teasing stick on NPC");
                clicked = useTeasingStickOnNpc(npc);
            }

            LureEvidence evidence = waitForLureEvidence(npc, npcStartTile, playerStartTile, startDistance, clicked);
            log("Tease attempt " + attempt + " result: clicked=" + clicked
                    + " evidence=" + evidence);
            if (evidence != LureEvidence.NONE) {
                markLureConfirmed(npc, evidence);
                return true;
            }

            shortRandomWait();
        }

        log("Tease failed: no interaction or NPC movement evidence was detected", Level.WARN);
        return false;
    }

    private LureEvidence waitForLureEvidence(Rs2NpcModel npc, WorldPoint npcStartTile, WorldPoint playerStartTile,
                                             int startDistance, boolean clicked)
    {
        int timeout = clicked ? LURE_MOVEMENT_TIMEOUT_MS : LURE_INTERACTION_TIMEOUT_MS;
        long start = System.currentTimeMillis();
        while (isRunning() && System.currentTimeMillis() - start < timeout) {
            LureEvidence evidence = getLureEvidence(npc, npcStartTile, playerStartTile, startDistance, clicked);
            if (evidence != LureEvidence.NONE) {
                return evidence;
            }
            sleep(100, 150);
        }
        return LureEvidence.NONE;
    }

    private LureEvidence getLureEvidence(Rs2NpcModel npc, WorldPoint npcStartTile, WorldPoint playerStartTile,
                                         int startDistance, boolean clicked)
    {
        if (npc == null) {
            return LureEvidence.NONE;
        }

        if (npc.isInteractingWithPlayer()) {
            return LureEvidence.NPC_TARGETS_PLAYER;
        }

        if (isPlayerInteractingWithNpc(npc)) {
            return LureEvidence.PLAYER_TARGETS_NPC;
        }

        if (!clicked || npcStartTile == null || playerStartTile == null || startDistance == Integer.MAX_VALUE) {
            return LureEvidence.NONE;
        }

        WorldPoint currentNpcTile = npc.getWorldLocation();
        if (currentNpcTile == null || currentNpcTile.equals(npcStartTile)) {
            return LureEvidence.NONE;
        }

        int currentDistanceToStartPlayer = currentNpcTile.distanceTo(playerStartTile);
        int currentDistanceToPlayer = distanceBetween(currentNpcTile, Rs2Player.getWorldLocation());
        if (currentDistanceToStartPlayer < startDistance || currentDistanceToPlayer <= 3) {
            return LureEvidence.NPC_MOVED_TOWARD_PLAYER;
        }

        return LureEvidence.NONE;
    }

    private void markLureConfirmed(Rs2NpcModel npc, LureEvidence evidence)
    {
        luredNpcIndex = npc == null ? -1 : npc.getIndex();
        lureConfirmedAt = System.currentTimeMillis();
        lastLureEvidence = evidence.name();
        recordDecision("Lure confirmed by " + lastLureEvidence);
    }

    private boolean isRecentlyConfirmedLure(Rs2NpcModel npc)
    {
        return npc != null
                && luredNpcIndex == npc.getIndex()
                && lureConfirmedAt > 0
                && System.currentTimeMillis() - lureConfirmedAt <= LURE_CONFIRMATION_GRACE_MS;
    }

    private void resetLureTracking()
    {
        luredNpcIndex = -1;
        lureConfirmedAt = 0;
        lastLureEvidence = "None";
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
                + " tile=" + object.getWorldLocation()
                + " onScreen=" + isTileObjectOnScreen(object));

        return object.click(action);
    }

    private boolean isTileObjectOnScreen(Rs2TileObjectModel object)
    {
        return object != null
                && object.getLocalLocation() != null
                && Rs2Camera.isTileOnScreen(object.getLocalLocation());
    }

    private void handlePostLootInventory(boolean afterLoot)
    {
        boolean fletched = config.fletchAntlers()
                && ThreadLocalRandom.current().nextInt(100) < FLETCH_ANTLERS_CHANCE_PERCENT
                && fletchSunlightAntlers();

        if (fletched && (Rs2Player.isAnimating() || Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_ANTLER_NAME, true))) {
            dropSunlightAntelopesForSpace();
            log("Skipping bones while fletching is still in progress");
            return;
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
            if (Rs2Inventory.hasItem(BIG_BONES_NAME, true)) {
                shortRandomWait();
            }
        }
        log("Big bones bury count: " + buried);
    }

    private int dropSunlightAntelopesForSpace()
    {
        int threshold = config.antelopeDropThreshold().emptySlots();
        if (threshold <= 0 || Rs2Inventory.emptySlotCount() >= threshold) {
            return 0;
        }

        int dropped = 0;
        while (isRunning()
                && hasSunlightAntelopeDrop()) {
            int beforeSlots = Rs2Inventory.emptySlotCount();
            boolean clicked = Rs2Inventory.drop(this::isSunlightAntelopeDrop);
            if (!clicked || !waitUntil(() -> Rs2Inventory.emptySlotCount() > beforeSlots, 2000)) {
                break;
            }
            dropped++;
        }

        if (dropped > 0) {
            log("Dropped Sunlight antelope items for space: " + dropped);
        }
        return dropped;
    }

    private boolean hasSunlightAntelopeDrop()
    {
        return Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_DROP_ITEM_IDS)
                || Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES, true);
    }

    private boolean isSunlightAntelopeDrop(Rs2ItemModel item)
    {
        return item != null
                && (contains(SUNLIGHT_ANTELOPE_DROP_ITEM_IDS, item.getId())
                || Arrays.stream(SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES)
                .anyMatch(name -> name.equalsIgnoreCase(item.getName())));
    }

    private boolean fletchSunlightAntlers()
    {
        if (!Rs2Inventory.hasItem(CHISEL_NAME, true) || !Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_ANTLER_NAME, true)) {
            return false;
        }

        int before = Rs2Inventory.count(SUNLIGHT_ANTELOPE_ANTLER_NAME, true);
        log("Fletching Sunlight antelope antlers into bolts. Antlers=" + before);
        boolean combined = Rs2Inventory.combineClosest(CHISEL_NAME, SUNLIGHT_ANTELOPE_ANTLER_NAME);
        if (!combined) {
            log("Fletch antlers failed: could not use chisel on antler", Level.WARN);
            return false;
        }

        if (waitUntil(Rs2Dialogue::hasSelectAnOption, 1500)) {
            boolean selected = Rs2Dialogue.clickOption(false, "bolt", "bolts");
            log("Fletch antlers dialogue option result: " + selected);
        }

        if (waitUntil(Rs2Widget::isProductionWidgetOpen, 1500)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }

        boolean finished = waitUntil(() -> !Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_ANTLER_NAME, true)
                || (Rs2Inventory.count(SUNLIGHT_ANTELOPE_ANTLER_NAME, true) < before
                && !Rs2Player.isAnimating()
                && !Rs2Widget.isProductionWidgetOpen()), 45000);
        log("Fletch antlers result: " + finished);
        return combined;
    }

    private PitfallState getPitState(PitfallDefinition pit)
    {
        if (getCollapsedTrapObject(pit) != null) {
            return PitfallState.COLLAPSED;
        }

        Rs2TileObjectModel object = getPitObject(pit, JUMP_PIT_ACTION);
        if (object == null) {
            object = getPitObject(pit, TRAP_PIT_ACTION);
        }

        if (object == null) {
            object = getPitObject(pit);
        }

        if (object == null) {
            return PitfallState.UNKNOWN;
        }

        if (isSpikedTrapObject(object)) {
            return PitfallState.TRAPPED;
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

        return PitfallState.UNKNOWN;
    }

    private void jumpSpikedTrapInsteadOfLooting(Rs2TileObjectModel pitObject)
    {
        if (!hasPitObjectAction(pitObject, JUMP_PIT_ACTION)) {
            log("Spiked trap cannot be looted and has no Jump action: id=" + pitObject.getId()
                    + " tile=" + pitObject.getWorldLocation(), Level.WARN);
            transition(State.RECOVER);
            return;
        }

        selectedPitState = PitfallState.TRAPPED;
        startCaptureTimersOnNextJumpAnimation(true);

        recordDecision("Jumping spiked trap instead of dismantling");
        log("Spiked trap selected for loot; clicking Jump instead of Dismantle: id=" + pitObject.getId()
                + " tile=" + pitObject.getWorldLocation(), Level.WARN);
        boolean jumped = clickPitObject(pitObject, JUMP_PIT_ACTION);
        log("Spiked trap jump result: " + jumped);

        if (!jumped) {
            transition(State.RECOVER);
            return;
        }

        transition(State.WAIT_FOR_CAPTURE);
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
        return getPitObject(pit, null);
    }

    private Rs2TileObjectModel getPitObject(PitfallDefinition pit, String action)
    {
        if (pit == null || pit.getAnchorTile() == null) {
            return null;
        }

        return Microbot.getRs2TileObjectCache().query()
                .within(pit.getAnchorTile(), config.pitObjectSearchRadius())
                .where(this::isPitfallObject)
                .where(object -> object.getWorldLocation() != null)
                .where(object -> action == null || hasPitObjectAction(object, action))
                .toListOnClientThread()
                .stream()
                .filter(object -> matchesPitFootprint(pit, object))
                .min(Comparator.comparingInt((Rs2TileObjectModel object) -> pitObjectMatchRank(pit, object))
                        .thenComparingInt(object -> pit.distanceTo(object.getWorldLocation()))
                        .thenComparingInt(this::distanceToPlayer))
                .orElse(null);
    }

    private Rs2TileObjectModel getCollapsedTrapObject(PitfallDefinition pit)
    {
        if (pit == null || pit.getAnchorTile() == null) {
            return null;
        }

        return Microbot.getRs2TileObjectCache().query()
                .within(pit.getAnchorTile(), config.pitObjectSearchRadius())
                .where(this::isCollapsedTrapDismantleObject)
                .where(object -> object.getWorldLocation() != null)
                .toListOnClientThread()
                .stream()
                .filter(object -> matchesPitFootprint(pit, object))
                .min(Comparator.comparingInt((Rs2TileObjectModel object) -> pitObjectMatchRank(pit, object))
                        .thenComparingInt(object -> pit.distanceTo(object.getWorldLocation()))
                        .thenComparingInt(this::distanceToPlayer))
                .orElse(null);
    }

    private Rs2TileObjectModel waitForPitObject(PitfallDefinition pit, String action, int timeoutMs)
    {
        final Rs2TileObjectModel[] found = new Rs2TileObjectModel[1];
        waitUntil(() -> {
            found[0] = getPitObject(pit, action);
            return found[0] != null;
        }, timeoutMs);
        return found[0];
    }

    private Rs2TileObjectModel waitForCollapsedTrapObject(PitfallDefinition pit, int timeoutMs)
    {
        final Rs2TileObjectModel[] found = new Rs2TileObjectModel[1];
        waitUntil(() -> {
            found[0] = getCollapsedTrapObject(pit);
            return found[0] != null;
        }, timeoutMs);
        return found[0];
    }

    private PitfallDefinition findConfiguredPit(Rs2TileObjectModel object)
    {
        if (object == null || object.getWorldLocation() == null) {
            return null;
        }

        WorldPoint objectTile = object.getWorldLocation();
        PitfallDefinition exactMatch = configuredPits().stream()
                .filter(pit -> pit.contains(object.getId(), objectTile))
                .min(Comparator.comparingInt(PitfallDefinition::getPriority))
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch;
        }

        return configuredPits().stream()
                .filter(pit -> pit.distanceTo(objectTile) <= PIT_OBJECT_MATCH_RADIUS)
                .min(Comparator.comparingInt((PitfallDefinition pit) -> pit.distanceTo(objectTile))
                        .thenComparingInt(pit -> distanceBetween(pit.getAnchorTile(), objectTile))
                        .thenComparingInt(PitfallDefinition::getPriority))
                .orElse(null);
    }

    private PitfallObjectCandidate toPitObjectCandidate(Rs2TileObjectModel object)
    {
        return new PitfallObjectCandidate(findConfiguredPit(object), object);
    }

    private PitfallObjectCandidate findNearestPitObjectCandidate(String action, int maxDistance)
    {
        return Microbot.getRs2TileObjectCache().query()
                .within(maxDistance)
                .where(this::isPitfallObject)
                .where(object -> object.getWorldLocation() != null)
                .where(object -> action == null || hasPitObjectAction(object, action))
                .toListOnClientThread()
                .stream()
                .map(this::toPitObjectCandidate)
                .filter(candidate -> candidate.pit != null)
                .min(Comparator.comparingInt((PitfallObjectCandidate candidate) -> distanceToPlayer(candidate.object))
                        .thenComparingInt(candidate -> candidate.pit.distanceTo(candidate.object.getWorldLocation()))
                        .thenComparingInt(candidate -> candidate.pit.getPriority()))
                .orElse(null);
    }

    private PitfallObjectCandidate findNearestCollapsedTrapCandidate(int maxDistance)
    {
        return Microbot.getRs2TileObjectCache().query()
                .within(maxDistance)
                .where(this::isCollapsedTrapDismantleObject)
                .where(object -> object.getWorldLocation() != null)
                .toListOnClientThread()
                .stream()
                .map(this::toPitObjectCandidate)
                .filter(candidate -> candidate.pit != null)
                .min(Comparator.comparingInt((PitfallObjectCandidate candidate) -> distanceToPlayer(candidate.object))
                        .thenComparingInt(candidate -> candidate.pit.distanceTo(candidate.object.getWorldLocation()))
                        .thenComparingInt(candidate -> candidate.pit.getPriority()))
                .orElse(null);
    }

    private PitfallObjectCandidate findNearestSpikedTrapCandidate(int maxDistance)
    {
        return Microbot.getRs2TileObjectCache().query()
                .within(maxDistance)
                .where(this::isSpikedTrapObject)
                .where(object -> object.getWorldLocation() != null)
                .toListOnClientThread()
                .stream()
                .map(this::toPitObjectCandidate)
                .filter(candidate -> candidate.pit != null)
                .min(Comparator.comparingInt((PitfallObjectCandidate candidate) -> distanceToPlayer(candidate.object))
                        .thenComparingInt(candidate -> candidate.pit.distanceTo(candidate.object.getWorldLocation()))
                        .thenComparingInt(candidate -> candidate.pit.getPriority()))
                .orElse(null);
    }

    private boolean matchesPitFootprint(PitfallDefinition pit, Rs2TileObjectModel object)
    {
        return pit != null
                && object != null
                && object.getWorldLocation() != null
                && (pit.contains(object.getId(), object.getWorldLocation())
                || pit.distanceTo(object.getWorldLocation()) <= PIT_OBJECT_MATCH_RADIUS);
    }

    private int pitObjectMatchRank(PitfallDefinition pit, Rs2TileObjectModel object)
    {
        return pit != null
                && object != null
                && object.getWorldLocation() != null
                && pit.contains(object.getId(), object.getWorldLocation()) ? 0 : 1;
    }

    private String formatPitCandidate(String phase, PitfallObjectCandidate candidate, WorldPoint npcTile)
    {
        if (candidate == null || candidate.pit == null || candidate.object == null) {
            return phase + ": none";
        }

        return phase + ": " + candidate.pit.name
                + " pD=" + distanceToPlayer(candidate.object)
                + (npcTile == null ? "" : " nD=" + distanceBetween(candidate.object.getWorldLocation(), npcTile))
                + " obj=" + candidate.object.getId()
                + " @ " + candidate.object.getWorldLocation();
    }

    private boolean isSpikedTrapObject(Rs2TileObjectModel object)
    {
        return object != null
                && String.valueOf(object.getName()).equalsIgnoreCase(SPIKED_TRAP_OBJECT_NAME);
    }

    private boolean isCollapsedTrapDismantleObject(Rs2TileObjectModel object)
    {
        return object != null
                && String.valueOf(object.getName()).equalsIgnoreCase(COLLAPSED_TRAP_OBJECT_NAME)
                && hasPitObjectAction(object, DISMANTLE_TRAP_ACTION);
    }

    private boolean isPitfallObject(Rs2TileObjectModel object)
    {
        if (object == null) {
            return false;
        }

        int id = object.getId();
        if (contains(OBSERVED_PIT_OBJECT_IDS, id)) {
            return true;
        }

        String name = String.valueOf(object.getName()).toLowerCase();
        return name.equalsIgnoreCase(PIT_OBJECT_NAME)
                || name.equalsIgnoreCase(SPIKED_PIT_OBJECT_NAME)
                || name.equalsIgnoreCase(SPIKED_TRAP_OBJECT_NAME)
                || name.equalsIgnoreCase(COLLAPSED_TRAP_OBJECT_NAME)
                || name.contains("pitfall");
    }

    private boolean hasLogs()
    {
        return logCount() > 0;
    }

    private int logCount()
    {
        return Arrays.stream(LOG_ITEM_NAMES)
                .mapToInt(logName -> Rs2Inventory.count(logName, true))
                .sum();
    }

    private int targetPreparedLogs()
    {
        return Math.max(1, Math.min(28, config.logsToPrepare()));
    }

    private boolean shouldPrepareMoreLogs()
    {
        return logCount() < targetPreparedLogs()
                && Rs2Inventory.emptySlotCount() > 0;
    }

    private boolean hasKnife()
    {
        return Rs2Inventory.hasItem(KNIFE_NAME, true);
    }

    private boolean hasMeatPouch()
    {
        return Rs2Inventory.hasItem(LARGE_MEAT_POUCH_CLOSED_ID, LARGE_MEAT_POUCH_OPEN_ID)
                || Arrays.stream(MEAT_POUCH_NAMES).anyMatch(name -> Rs2Inventory.hasItem(name, false));
    }

    private boolean hasPouchableMeat()
    {
        return Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_POUCHABLE_MEAT_IDS)
                || Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_POUCHABLE_MEAT_NAMES, true);
    }

    private boolean hasKandarinHeadgear()
    {
        return Rs2Equipment.isWearing(KANDARIN_HEADGEAR_NAME)
                || Rs2Inventory.hasItem(KANDARIN_HEADGEAR_NAME, false);
    }

    private boolean cutLogsLocally()
    {
        int targetLogs = Math.min(targetPreparedLogs(), logCount() + Math.max(0, Rs2Inventory.emptySlotCount()));
        if (logCount() >= targetLogs) {
            return true;
        }

        log("Preparing logs: current=" + logCount() + " target=" + targetLogs);
        while (isRunning() && logCount() < targetLogs && Rs2Inventory.emptySlotCount() > 0) {
            int before = logCount();
            Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
                    .withIds(LOCAL_TREE_IDS)
                    .where(this::isReachableTree)
                    .nearestOnClientThread(20);

            if (tree == null) {
                tree = Microbot.getRs2TileObjectCache().query()
                        .withIds(LOCAL_TREE_IDS)
                        .where(this::isReachableTree)
                        .nearestOnClientThread(15);
            }

            if (tree == null) {
                log("Local log cutting failed: no nearby tree");
                return logCount() >= targetLogs;
            }

            boolean clicked = tree.click("Chop down");
            if (!clicked || !waitUntil(() -> logCount() >= targetLogs
                    || (logCount() > before && !isBusy()), 12000)) {
                log("Local log cutting stopped: clicked=" + clicked + " current=" + logCount() + " target=" + targetLogs);
                return logCount() >= targetLogs;
            }
        }

        log("Prepared logs: " + logCount() + "/" + targetLogs);
        return logCount() >= targetLogs;
    }

    private boolean isReachableTree(Rs2TileObjectModel tree)
    {
        return tree != null && tree.getWorldLocation() != null && tree.isReachable();
    }

    private boolean clearSelectedItem(String phase)
    {
        if (!Rs2Inventory.isItemSelected()) {
            return true;
        }

        String selectedName = Rs2Inventory.getSelectedItemName();
        int selectedId = Rs2Inventory.getSelectedItemId();
        log("Deselecting selected item " + selectedName + " (" + selectedId + ") " + phase);
        Rs2Inventory.deselect();
        return waitUntil(() -> !Rs2Inventory.isItemSelected(), 1200);
    }

    private boolean walkTo(WorldPoint tile)
    {
        if (tile == null) {
            return false;
        }

        WorldPoint playerTile = Rs2Player.getWorldLocation();
        if (playerTile != null && playerTile.distanceTo(tile) <= 1) {
            return true;
        }

        Rs2Walker.walkTo(tile);
        return waitUntil(() -> {
            WorldPoint current = Rs2Player.getWorldLocation();
            return current != null && current.distanceTo(tile) <= 1;
        }, WALK_TIMEOUT_MS);
    }

    private boolean isBusy()
    {
        return Rs2Player.isMoving() || Rs2Player.isAnimating();
    }

    private boolean handleBusySkip()
    {
        updateJumpAnimationTimer();

        if (!isBusy()) {
            busyStartedAt = 0;
            return false;
        }

        long now = System.currentTimeMillis();
        if (busyStartedAt == 0) {
            busyStartedAt = now;
        }

        recordDecision("Waiting for player movement/animation");
        if (state != State.STOP
                && state != State.BANK
                && state != State.RETURN_TO_START
                && now - busyStartedAt > BUSY_WATCHDOG_TIMEOUT_MS) {
            log("Busy watchdog recovered from state " + state, Level.WARN);
            busyStartedAt = 0;
            transition(State.RECOVER);
        }
        return true;
    }

    private boolean recoverTimedOutState()
    {
        if (state == State.STOP
                || state == State.WAIT_FOR_CAPTURE
                || state == State.BANK
                || state == State.RETURN_TO_START) {
            return false;
        }

        if (timedOut(STATE_WATCHDOG_TIMEOUT_MS)) {
            log("State watchdog recovered from " + state, Level.WARN);
            transition(State.RECOVER);
            return true;
        }
        return false;
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

    private void shortRandomWait()
    {
        sleep(SHORT_RANDOM_WAIT_MIN_MS, SHORT_RANDOM_WAIT_MAX_MS);
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
        busyStartedAt = 0;
        log("State -> " + state);
    }

    private void stop(String reason)
    {
        lastFailure = reason;
        recordDecision("Stopped: " + reason);
        log("Stopping: " + reason, Level.WARN);
        transition(State.STOP);
    }

    private void recordDecision(String decision)
    {
        lastDecision = decision == null ? "" : decision;
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

    public String getStateDisplay()
    {
        return state.name();
    }

    public long getStateAgeMillis()
    {
        return System.currentTimeMillis() - stateStartedAt;
    }

    public String getSelectedPitDisplay()
    {
        return selectedPit == null ? "None" : selectedPit.name;
    }

    public String getSelectedPitStateDisplay()
    {
        return selectedPitState.name();
    }

    public String getSelectedNpcDisplay()
    {
        if (selectedNpc == null) {
            return "None";
        }
        return Integer.toString(selectedNpc.getId());
    }

    public String getSelectedNpcInteractionDisplay()
    {
        if (selectedNpc == null) {
            return "None";
        }
        return "P->N " + isPlayerInteractingWithNpc(selectedNpc)
                + " / N->P " + selectedNpc.isInteractingWithPlayer();
    }

    public String getLastLureEvidence()
    {
        return lastLureEvidence;
    }

    public String getLastNpcQuery()
    {
        return lastNpcQuery;
    }

    public String getLastPitQuery()
    {
        if (lastPitQuery == null) {
            return "";
        }

        int tileMarker = lastPitQuery.indexOf(" @ ");
        return tileMarker < 0 ? lastPitQuery : lastPitQuery.substring(0, tileMarker);
    }

    public String getSelectedItemDisplay()
    {
        if (!Rs2Inventory.isItemSelected()) {
            return "None";
        }
        return Rs2Inventory.getSelectedItemName() + " (" + Rs2Inventory.getSelectedItemId() + ")";
    }

    public String getLastDecision()
    {
        return lastDecision;
    }

    public String getLastFailure()
    {
        return lastFailure;
    }

    public String getNextStepDisplay()
    {
        switch (state) {
            case CHECK_REQUIREMENTS:
                return "Check tools/logs";
            case REFRESH_PITS:
                return "Reset target state";
            case SELECT_NPC:
                return "Find nearest antelope";
            case LURE_NPC:
                return "Tease until lure evidence";
            case SELECT_PIT:
                return "Choose usable pit";
            case PREPARE_PIT:
                return "Trap pit with logs";
            case JUMP_PIT:
                return "Walk and jump pit";
            case WAIT_FOR_CAPTURE:
                return "Wait for collapse";
            case LOOT_PIT:
                return "Dismantle trap";
            case HANDLE_MEAT_POUCH:
                return "Store/drop/fletch loot";
            case BANK:
                return "Bank loot and heal";
            case RETURN_TO_START:
                return "Return to start";
            case RECOVER:
                return "Clear targets and retry";
            case STOP:
                return "Stopped";
            default:
                return "Unknown";
        }
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
        BANK,
        RETURN_TO_START,
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

        private JumpRoute getNearestJumpRoute(WorldPoint from)
        {
            WorldPoint firstSide = getPreJumpTile();
            WorldPoint secondSide = getPostJumpTile();
            if (firstSide == null || secondSide == null) {
                return null;
            }

            if (from == null || firstSide.distanceTo(from) <= secondSide.distanceTo(from)) {
                return new JumpRoute(firstSide, secondSide);
            }
            return new JumpRoute(secondSide, firstSide);
        }

        private int distanceTo(WorldPoint tile)
        {
            if (tile == null || footprint == null || footprint.isEmpty()) {
                return Integer.MAX_VALUE;
            }

            return footprint.stream()
                    .map(PitfallTile::getTile)
                    .filter(Objects::nonNull)
                    .mapToInt(footprintTile -> footprintTile.distanceTo(tile))
                    .min()
                    .orElse(Integer.MAX_VALUE);
        }

        private TrapSide getTrapSide(WorldPoint tile)
        {
            if (tile == null) {
                return TrapSide.UNKNOWN;
            }

            if (jumpAxis == JumpAxis.EAST_WEST) {
                if (tile.getX() < minX()) {
                    return TrapSide.BEFORE;
                }
                if (tile.getX() > maxX()) {
                    return TrapSide.AFTER;
                }
                return TrapSide.ON_TRAP;
            }

            if (tile.getY() < minY()) {
                return TrapSide.BEFORE;
            }
            if (tile.getY() > maxY()) {
                return TrapSide.AFTER;
            }
            return TrapSide.ON_TRAP;
        }

        private boolean hasCrossedTrap(WorldPoint from, WorldPoint to)
        {
            TrapSide fromSide = getTrapSide(from);
            TrapSide toSide = getTrapSide(to);
            return (fromSide == TrapSide.BEFORE && toSide == TrapSide.AFTER)
                    || (fromSide == TrapSide.AFTER && toSide == TrapSide.BEFORE);
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

    @RequiredArgsConstructor
    private static class JumpRoute
    {
        private final WorldPoint from;
        private final WorldPoint to;
    }

    @RequiredArgsConstructor
    private static class PitfallObjectCandidate
    {
        private final PitfallDefinition pit;
        private final Rs2TileObjectModel object;
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

    private enum TrapSide
    {
        BEFORE,
        AFTER,
        ON_TRAP,
        UNKNOWN
    }

    private enum LureEvidence
    {
        NONE,
        NPC_TARGETS_PLAYER,
        PLAYER_TARGETS_NPC,
        NPC_MOVED_TOWARD_PLAYER
    }

    private interface Check
    {
        boolean ok();
    }
}
