package net.runelite.client.plugins.microbot.pitfallhunter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
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
    private static final int[] LOCAL_TREE_IDS = {51762, 51764, 51768};

    private static final String SUNLIGHT_ANTELOPE_NAME = "Sunlight antelope";
    private static final String PIT_OBJECT_NAME = "Pit";
    private static final String SPIKED_PIT_OBJECT_NAME = "Spiked Pit";
    private static final String COLLAPSED_TRAP_OBJECT_NAME = "Collapsed Trap";
    private static final String TRAP_PIT_ACTION = "Trap";
    private static final String JUMP_PIT_ACTION = "Jump";
    private static final String DISMANTLE_TRAP_ACTION = "Dismantle";
    private static final String LURE_NPC_ACTION = "Tease";
    private static final int LURE_INTERACTION_TIMEOUT_MS = 2400;
    private static final int LURE_MOVEMENT_TIMEOUT_MS = 3600;
    private static final int LURE_CONFIRMATION_GRACE_MS = 15000;
    private static final int LURE_MAX_ATTEMPTS = 2;
    private static final int BUSY_WATCHDOG_TIMEOUT_MS = 15000;
    private static final int STATE_WATCHDOG_TIMEOUT_MS = 30000;
    private static final int CAPTURE_TIMEOUT_MIN_MS = 12000;
    private static final int CAPTURE_REJUMP_DELAY_MS = 3000;
    private static final int CAPTURE_REJUMP_MAX_ATTEMPTS = 3;
    private static final int DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS = 5000;
    private static final int PREPARE_TRAP_TIMEOUT_MS = 6500;
    private static final int JUMP_OBJECT_APPEAR_TIMEOUT_MS = 3500;
    private static final int WALK_TIMEOUT_MS = 5000;
    private static final int PREPARE_PIT_MAX_ATTEMPTS = 3;
    private static final int PIT_OBJECT_MATCH_RADIUS = 2;
    private static final int[] CAPTURE_DEATH_GRAPHIC_IDS = {993};
    private static final int CAPTURE_LOOT_DELAY_MIN_MS = 2500;
    private static final int CAPTURE_LOOT_DELAY_MAX_MS = 3000;
    private static final int FLETCH_ANTLERS_CHANCE_PERCENT = 50;
    private static final String LOG_NAME = "Logs";
    private static final String WILLOW_LOG_NAME = "Willow logs";
    private static final String[] LOG_ITEM_NAMES = {LOG_NAME, WILLOW_LOG_NAME, "Oak logs"};
    private static final String BIG_BONES_NAME = "Big bones";
    private static final String CHISEL_NAME = "Chisel";
    private static final String KNIFE_NAME = "Knife";
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
    private int captureRejumpAttempts;
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
        captureRejumpAttempts = 0;
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
            if (kandarinHeadgearAvailable && cutLogsLocally()) {
                return;
            }
            stop("No logs available");
            return;
        }

        selectedNpc = findClosestSunlightAntelope();
        if (selectedNpc == null) {
            recordDecision("No antelope nearby; refreshing");
            sleep(600, 1000);
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

        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = System.currentTimeMillis();
        captureRejumpAttempts = 0;
        recordDecision("Waiting for capture");
        transition(State.WAIT_FOR_CAPTURE);
    }

    private void waitForCapture()
    {
        selectedPitState = getPitState(selectedPit);
        log("Capture wait state: " + selectedPit.name + " -> " + selectedPitState);
        trackNpcJumpAcrossTrap();

        if (captureDetectedAt == 0 && selectedPitState == PitfallState.COLLAPSED) {
            markCaptureDetected("collapsed trap state");
        } else if (captureDetectedAt == 0 && getSelectedNpcCaptureGraphic() > 0) {
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

            Rs2TileObjectModel lootObject = waitForPitObject(selectedPit, DISMANTLE_TRAP_ACTION, DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS);
            if (lootObject == null) {
                captureLootReadyAt = System.currentTimeMillis() + 1000;
                recordDecision("Waiting for Dismantle");
                log("Capture delay elapsed but Dismantle is not available yet", Level.WARN);
                return;
            }

            skippedTrappedPits.remove(selectedPit.name);
            recordDecision("Looting collapsed trap");
            transition(State.LOOT_PIT);
            return;
        }

        if (timedOut(getCaptureTimeoutMs())) {
            PitfallObjectCandidate collapsed = findNearestPitObjectCandidate(DISMANTLE_TRAP_ACTION, 25);
            if (collapsed != null) {
                selectedPit = collapsed.pit;
                selectedPitState = PitfallState.COLLAPSED;
                lastPitQuery = formatPitCandidate("Late collapsed", collapsed, null);
                markCaptureDetected("late collapsed pit scan");
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

    private boolean retryJumpIfStillTrapped()
    {
        if (captureRejumpAttempts >= CAPTURE_REJUMP_MAX_ATTEMPTS) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lastJumpAt = lastCaptureJumpAt == 0 ? stateStartedAt : lastCaptureJumpAt;
        if (now - lastJumpAt < CAPTURE_REJUMP_DELAY_MS) {
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

        captureRejumpAttempts++;
        lastCaptureJumpAt = now;
        stateStartedAt = now;
        recordDecision("Retrying Jump " + captureRejumpAttempts);
        log("Capture wait still has Jump option; retrying jump attempt " + captureRejumpAttempts, Level.WARN);
        boolean clicked = clickPitObject(pitObject, JUMP_PIT_ACTION);
        log("Capture retry jump result: " + clicked);
        return clicked;
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
                sleep(300, 600);
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
            sleep(500, 800);
            return true;
        }

        selectedPitState = getPitState(selectedPit);
        return selectedPitState == PitfallState.TRAPPED;
    }

    private void lootPit()
    {
        Rs2TileObjectModel pitObject = waitForPitObject(selectedPit, DISMANTLE_TRAP_ACTION, DISMANTLE_OBJECT_APPEAR_TIMEOUT_MS);
        if (pitObject == null) {
            log("Loot failed: pit object missing", Level.WARN);
            transition(State.RECOVER);
            return;
        }

        int before = Rs2Inventory.count();
        recordDecision("Dismantling collapsed trap");
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

    private void recover()
    {
        log("Recovering from state: " + state + " pit=" + (selectedPit == null ? "none" : selectedPit.name));
        selectedNpc = null;
        selectedPit = null;
        selectedPitState = PitfallState.UNKNOWN;
        captureDetectedAt = 0;
        captureLootReadyAt = 0;
        lastCaptureJumpAt = 0;
        captureRejumpAttempts = 0;
        resetNpcJumpTracking();
        resetLureTracking();
        sleep(600, 1000);
        transition(State.REFRESH_PITS);
    }

    private boolean selectCollapsedPit()
    {
        PitfallObjectCandidate candidate = findNearestPitObjectCandidate(DISMANTLE_TRAP_ACTION, 25);

        if (candidate == null) {
            lastPitQuery = "Collapsed: none";
            return false;
        }

        selectedPit = candidate.pit;
        selectedPitState = PitfallState.COLLAPSED;
        selectedNpc = null;
        lastPitQuery = formatPitCandidate("Collapsed", candidate, null);
        log("Selected collapsed pit: " + selectedPit.name
                + " object=" + candidate.object.getId()
                + " tile=" + candidate.object.getWorldLocation());
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

            sleep(300, 600);
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
                sleep(600, 950);
            }
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

    private Rs2TileObjectModel waitForPitObject(PitfallDefinition pit, String action, int timeoutMs)
    {
        final Rs2TileObjectModel[] found = new Rs2TileObjectModel[1];
        waitUntil(() -> {
            found[0] = getPitObject(pit, action);
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
                || name.equalsIgnoreCase(COLLAPSED_TRAP_OBJECT_NAME)
                || name.contains("pitfall");
    }

    private boolean hasLogs()
    {
        return Rs2Inventory.hasItem(LOG_ITEM_NAMES, true);
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
        return Rs2Inventory.hasItem(SUNLIGHT_ANTELOPE_DROP_ITEM_NAMES, true);
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
                .where(this::isReachableTree)
                .nearestOnClientThread(20);

        if (tree == null) {
            tree = Microbot.getRs2TileObjectCache().query()
                    .withNameContains("tree")
                    .where(this::isReachableTree)
                    .nearestOnClientThread(15);
        }

        if (tree == null) {
            log("Local log cutting failed: no nearby tree");
            return false;
        }

        boolean clicked = tree.click("Chop down");
        return clicked && waitUntil(this::hasLogs, 10000);
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
        if (!isBusy()) {
            busyStartedAt = 0;
            return false;
        }

        long now = System.currentTimeMillis();
        if (busyStartedAt == 0) {
            busyStartedAt = now;
        }

        recordDecision("Waiting for player movement/animation");
        if (state != State.STOP && now - busyStartedAt > BUSY_WATCHDOG_TIMEOUT_MS) {
            log("Busy watchdog recovered from state " + state, Level.WARN);
            busyStartedAt = 0;
            transition(State.RECOVER);
        }
        return true;
    }

    private boolean recoverTimedOutState()
    {
        if (state == State.STOP || state == State.WAIT_FOR_CAPTURE) {
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
