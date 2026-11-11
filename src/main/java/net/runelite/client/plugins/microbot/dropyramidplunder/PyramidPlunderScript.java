package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

/**
 * Pyramid Plunder, built from the owner's 2026-11-09 recording and the OSRS Wiki strategy.
 *
 * <p>Loop: optional hop to world 493, find the Guardian mummy's door, start the game, then in each
 * room pass the spear trap, loot by {@link PyramidPlunderPlan} and pick the tomb doors until the
 * next room. Between runs it eats, cures poison and, when food is low, prays at the
 * temple altar and banks in the Sophanem dungeon. Every input goes through BaseProfileDro, repeat
 * urn clicks follow the recorded rhythm in {@link PyramidPlunderClicks}, and actions wait for
 * observed completion.
 */
@Singleton
public class PyramidPlunderScript extends Script {
    static final int PYRAMID_REGION = 7749;
    static final int BANK_REGION = 11088;
    static final int PLUNDER_WORLD = 493;
    static final int RUN_SECONDS = 300;

    static final WorldPoint NORTH_EXIT = new WorldPoint(3288, 2801, 0);
    /** Beside the bank ladder and the altar, a tile the recording stood on. */
    static final WorldPoint TEMPLE = new WorldPoint(3315, 2796, 0);

    static final int SPEARTRAP = 21280;
    static final int CHEST = 26616;
    static final int CHEST_CLOSED = 20946;
    static final int SARCOPHAGUS = 26626;
    static final int SARCOPHAGUS_CLOSED = 21255;
    static final int SARCOPHAGUS_OPENING = 21257;
    static final int[] TOMB_DOORS = {26618, 26619, 26620, 26621};
    static final int TOMB_DOOR_CLOSED = 20948;
    static final int ROOM_EXIT = 20931;
    static final int LOBBY_EXIT = 20932;
    static final int[] URNS = {26580, 26600, 26601, 26602, 26603, 26604, 26605, 26606, 26607, 26608,
            26609, 26610, 26611, 26612, 26613};
    static final Set<Integer> URN_LOOTED = Set.of(21265, 21266, 21267);
    /** A looted urn is left out of the room scan this long, until its opened state has arrived. */
    static final long LOOTED_URN_HIDE_MS = 1200;
    static final int TEMPLE_ALTAR = 20378;
    static final int TEMPLE_LADDER_DOWN = 20275;
    static final int BANK_LADDER_UP = 20277;

    static final Set<String> ARTEFACTS = Set.of("Ivory comb", "Pottery scarab", "Pottery statuette", "Stone seal",
            "Stone scarab", "Stone statuette", "Gold seal", "Golden scarab", "Golden statuette");
    static final String SCEPTRE = "Pharaoh's sceptre";

    /** Room floors by number, from the recording's object positions; rooms never share a tile. */
    static final int[][] ROOMS = {
            {1921, 4462, 1934, 4479}, {1947, 4462, 1962, 4479}, {1968, 4451, 1982, 4473}, {1925, 4446, 1944, 4461},
            {1950, 4442, 1966, 4456}, {1921, 4421, 1933, 4442}, {1941, 4418, 1957, 4435}, {1965, 4417, 1981, 4439}};

    private final Random random = new Random();
    private final PyramidPlunderEntrances entrances = new PyramidPlunderEntrances();

    private PyramidPlunderConfig config;
    private BaseProfileDro profile;
    private volatile String status = "Starting";
    /** Set once the script cannot continue; it stops clicking until restarted. */
    private volatile String error;
    /** This script holds the break handler's lock while inside the timed game. */
    private boolean breakLockHeld;

    private PyramidPlunderClicks.Cadence cadence = new PyramidPlunderClicks.Cadence(random);
    private volatile boolean runActive;
    private long runStartedAt;
    private volatile int currentRoom;
    private volatile int runs;

    // Retain the target while waiting for its action receipt.
    private String targetKey;
    private long attemptStartedAt;
    private PyramidPlunderAction pendingAction;
    private BooleanSupplier pendingReceipt;
    // Follow-up clicks on the urn being searched, on the recorded repeat-click rhythm.
    private Rs2TileObjectModel repeatTarget;
    private String repeatAction;
    private int repeatsLeft;
    private long nextRepeatAt;
    private int selectedDoor = -1;
    private int selectedUrn = -1;
    // The urn being searched, and the last one looted: its experience drop releases the next urn at once.
    private int pendingUrn = -1;
    private int lootedUrn = -1;
    private long urnLootedAt;

    // Urn pace in the last room, carried between runs.
    private double secondsPerUrn = PyramidPlunderPlan.DEFAULT_SECONDS_PER_URN;
    private long urnPhaseStartedAt;
    private long lastUrnLootAt;
    private int urnsLooted;
    private int urnsLeftBefore = -1;
    private boolean urnPhaseInLastRoom;

    private final Set<Integer> enteredDoors = new HashSet<>();
    private volatile long deadEndAt;
    private long selectedDoorOpenedAt;
    private volatile long trapDisarmedAt;
    private long roomEnteredAt;

    private long lobbyEnteredAt;
    private boolean lobbyResolved;
    private long nextPacedClickAt;
    private long sarcophagusOpeningSince;
    private volatile boolean inPyramid;

    private int eatAt;
    private long threatSeenAt;
    private long prayerLingerMs = 2500;
    private long nextCureAt;

    private boolean restocking;
    private boolean setupApplied;
    private boolean bankUnavailableLogged;
    private int hopFailures;
    private int recoveries;

    public String getStatus() { return status; }

    // Read by the overlay.
    private volatile long startedAt;
    private volatile double lastSecondsLeft = -1;
    private volatile int lastUrnsLeft = -1;

    long getStartedAt() { return startedAt; }
    int getRuns() { return runs; }
    int getCurrentRoom() { return inPyramid ? currentRoom : 0; }
    boolean isInPyramid() { return inPyramid; }
    /** Seconds left on the run timer, or -1 outside a run. */
    double getSecondsLeft() { return inPyramid && runActive ? lastSecondsLeft : -1; }
    int getUrnsLeft() { return inPyramid && runActive ? lastUrnsLeft : -1; }
    PyramidPlunderEntrances.Door getLastGoodDoor() { return entrances.lastGood(); }

    public boolean run(PyramidPlunderConfig config) {
        this.config = config;
        startedAt = System.currentTimeMillis();
        lastSecondsLeft = -1;
        lastUrnsLeft = -1;
        error = null;
        status = "Starting";
        runActive = false;
        restocking = false;
        setupApplied = false;
        bankUnavailableLogged = false;
        hopFailures = 0;
        recoveries = 0;
        runs = 0;
        eatAt = rollEatAt();
        resetTarget();
        pendingAction = null;
        pendingReceipt = null;
        selectedDoor = selectedUrn = -1;
        pendingUrn = lootedUrn = -1;
        urnLootedAt = 0;
        currentRoom = 0;
        lobbyEnteredAt = 0;
        lobbyResolved = false;
        nextCureAt = 0;
        Microbot.pauseAllScripts.compareAndSet(true, false);

        // Input timing and mouse parking; breaks stay with the break handler, locked during the game.
        BaseProfileDro.Settings settings = new BaseProfileDro.Settings()
                .activity(Activity.GENERAL_THIEVING)
                .activityIntensity(ActivityIntensity.MODERATE)
                .playStyle(PlayStyle.MODERATE)
                .nativeRandomMouseChance(0.0)
                .mouseActivity(config.mouseActivity())
                .parkSide(config.parkSide())
                .customBreaksEnabled(false)
                .writeBreakStatusToMicrobot(false)
                .overlayEnabled(false);
        profile = new BaseProfileDro(settings);
        profile.start();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 90, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        setBreakLock(false);
        inPyramid = false;
        BaseProfileDro current = profile;
        profile = null;
        if (current != null) current.shutdown();
        super.shutdown();
    }

    void onGameMessage(String message) {
        if (message == null) return;
        if (message.contains("leads to a dead end")) deadEndAt = System.currentTimeMillis();
        if (message.contains("You deactivate the trap")) trapDisarmedAt = System.currentTimeMillis();
    }

    private void loop() {
        try {
            BaseProfileDro current = profile;
            if (current == null) return;
            if (!Microbot.isLoggedIn() || !super.run()) {
                current.tick(false, false, config.mouseActivity());
                return;
            }
            if (error != null) return;
            View view = read();
            if (view == null) return;
            boolean inside = view.region == PYRAMID_REGION;
            inPyramid = inside;
            // A break never interrupts the five-minute game.
            setBreakLock(inside);
            if (current.tick(!inside, view.waiting, config.mouseActivity())) return;
            step(view);
            Microbot.status = status;
        } catch (RuntimeException ex) {
            if (Thread.currentThread().isInterrupted()) return;
            Microbot.log("[PyramidPlunder] Action failed: " + ex.getClass().getSimpleName(), Level.ERROR);
        }
    }

    private void setBreakLock(boolean locked) {
        if (locked == breakLockHeld) return;
        breakLockHeld = locked;
        BreakHandlerScript.setLockState(locked);
    }

    private void step(View view) {
        if (!inputAllowed() || awaitingAction(view)) return;
        if (view.region == PYRAMID_REGION) {
            int room = roomAt(view.at);
            if (view.plane == 0 && room > 0) plunder(view, room);
            else lobby(view);
            return;
        }
        if (runActive) endRun();
        lobbyEnteredAt = 0;
        lobbyResolved = false;
        if (view.region == BANK_REGION) bankRoom(view);
        else outside(view);
    }

    // ------------------------------------------------------------------ inside the pyramid

    private void plunder(View view, int room) {
        int maxRoom = PyramidPlunderPlan.maxRoom(view.thieving);
        if (!runActive || room < currentRoom) beginRun(view);
        if (room != currentRoom) enterRoom(room);

        if (Rs2Dialogue.hasSelectAnOption()) {
            // "Leave the Tomb?" from a mis-click on a final-room door.
            act(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                    () -> Rs2Dialogue.clickOption("Ah, I think I'll stay a little longer."));
            return;
        }
        RoomScan scan = scan(room);
        if (scan == null) return;
        if (scan.sarcophagusOpening) {
            // It opens on its own in roughly seven seconds; any other click would abandon it.
            long now = System.currentTimeMillis();
            if (sarcophagusOpeningSince == 0) sarcophagusOpeningSince = now;
            if (now - sarcophagusOpeningSince < 15_000) {
                status = "Room " + room + ": opening the sarcophagus";
                return;
            }
        } else {
            sarcophagusOpeningSince = 0;
        }
        if (survive(view, true)) return;
        if (lootedUrn >= 0) {
            // The experience drop can be seen a frame before the urn shows as opened.
            int looted = lootedUrn;
            if (System.currentTimeMillis() - urnLootedAt > LOOTED_URN_HIDE_MS) lootedUrn = -1;
            else scan.urns.removeIf(o -> o.getId() == looted);
        }
        trackUrns(room, maxRoom, scan);

        boolean xpMode = config.betterXpMode();
        boolean sarcophagusWanted = PyramidPlunderPlan.opensSarcophagus(room, config.openSarcophagi(), xpMode);
        double left = secondsLeft(view);
        lastSecondsLeft = left;
        lastUrnsLeft = scan.urns.size();
        double reserve = PyramidPlunderPlan.lastRoomReserveSeconds(maxRoom, secondsPerUrn, config.openSarcophagi(), xpMode);
        PyramidPlunderPlan.Step step = PyramidPlunderPlan.next(room, maxRoom, xpMode, view.trapActive && trapDisarmedAt < roomEnteredAt, left, reserve,
                secondsPerUrn, scan.urns.size(), scan.chest != null && scan.chestClosed,
                sarcophagusWanted && scan.sarcophagus != null && scan.sarcophagusClosed);

        boolean lowAndOutOfFood = view.hpPercent() <= 25 && Rs2Inventory.getInventoryFood().isEmpty();
        // Each room has an exit on the entry side of the trap.
        if (lowAndOutOfFood) step = PyramidPlunderPlan.Step.LEAVE;

        boolean loots = step == PyramidPlunderPlan.Step.URN || step == PyramidPlunderPlan.Step.CHEST
                || step == PyramidPlunderPlan.Step.SARCOPHAGUS;
        if (loots && Rs2Inventory.emptySlotCount() == 0) {
            // A sceptre found with a full inventory lands on the ground outside; keep room for it.
            if (Rs2Inventory.count(item -> ARTEFACTS.contains(item.getName())) > 0) {
                status = "Room " + room + ": dropping artefacts";
                resetTarget();
                act(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                        () -> Rs2Inventory.dropAll(item -> ARTEFACTS.contains(item.getName())));
                return;
            }
            if (!Rs2Inventory.getInventoryFood().isEmpty()) {
                status = "Room " + room + ": making room";
                int before = Rs2Inventory.count();
                resetTarget();
                act(BaseProfileDro.ActionPhase.MAINTENANCE, false, Rs2Player::useFood);
                sleepUntil(() -> Rs2Inventory.count() != before, 1200);
                return;
            }
        }

        switch (step) {
            case TRAP:
                status = "Room " + room + ": passing the spear trap";
                if (scan.trap != null) interactObject(scan.trap, "Pass", this::trapPassed);
                break;
            case URN: {
                Rs2TileObjectModel urn = scan.urns.stream().filter(o -> o.getId() == selectedUrn)
                        .findFirst().orElse(scan.urns.get(0));
                selectedUrn = urn.getId();
                status = "Room " + room + ": urns (" + scan.urns.size() + " left)";
                if (urnPhaseStartedAt == 0) {
                    urnPhaseStartedAt = System.currentTimeMillis();
                    urnPhaseInLastRoom = room >= maxRoom;
                }
                int id = urn.getId();
                int xpBefore = scan.thievingXp;
                interactUrn(urn, () -> urnLooted(id, xpBefore));
                break;
            }
            case CHEST:
                status = "Room " + room + ": Grand Gold Chest";
                interactObject(scan.chest, "Search", () -> PyramidPlunderAction.changed(impostor(CHEST), CHEST_CLOSED));
                break;
            case SARCOPHAGUS:
                status = "Room " + room + ": sarcophagus";
                interactObject(scan.sarcophagus, "Open", () -> PyramidPlunderAction.changed(impostor(SARCOPHAGUS), SARCOPHAGUS_CLOSED));
                break;
            case DOOR:
                pickDoor(room, scan);
                break;
            case LEAVE:
            default:
                status = lowAndOutOfFood ? "Leaving: out of food" : "Room " + room + ": leaving";
                if (scan.exit != null && paced(900, 1700)) {
                    resetTarget();
                    if (act(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> scan.exit.click("Quick-leave")))
                        waitForAction(() -> regionNow() != PYRAMID_REGION);
                }
                break;
        }
    }

    private void pickDoor(int room, RoomScan scan) {
        // An unlocked door can take another couple of seconds to transfer rooms or report a dead end.
        if (selectedDoor >= 0 && scan.openDoors.stream().anyMatch(o -> o.getId() == selectedDoor)) {
            long now = System.currentTimeMillis();
            if (selectedDoorOpenedAt == 0) selectedDoorOpenedAt = now;
            if (deadEndAt <= attemptStartedAt && now - selectedDoorOpenedAt < 2600) return;
        }
        if (selectedDoor >= 0 && deadEndAt > attemptStartedAt) enteredDoors.add(selectedDoor);
        Rs2TileObjectModel door = scan.closedDoors.stream().filter(o -> o.getId() == selectedDoor)
                .findFirst().orElse(scan.closedDoors.isEmpty() ? null : scan.closedDoors.get(0));
        boolean closed = door != null;
        if (closed) {
            enteredDoors.clear();
        } else {
            // Every door is open: the doors changed, and one of the opened ones now leads on.
            door = scan.openDoors.stream()
                    .filter(o -> o.getId() == selectedDoor && !enteredDoors.contains(o.getId()))
                    .findFirst().orElse(null);
            if (door == null) {
                for (Rs2TileObjectModel open : scan.openDoors) {
                    if (!enteredDoors.contains(open.getId())) { door = open; break; }
                }
            }
            if (door == null) {
                enteredDoors.clear();
                return;
            }
        }
        status = "Room " + room + ": tomb doors";
        if (selectedDoor != door.getId()) selectedDoorOpenedAt = 0;
        selectedDoor = door.getId();
        final int id = door.getId();
        final int[] box = ROOMS[room - 1];
        BooleanSupplier leftRoom = () -> !inBox(box, Rs2Player.getWorldLocation());
        if (closed) {
            interactObject(door, "Pick-lock", () -> leftRoom.getAsBoolean() || PyramidPlunderAction.changed(impostor(id), TOMB_DOOR_CLOSED));
        } else if (interactObject(door, "Enter", () -> leftRoom.getAsBoolean() || deadEndAt > attemptStartedAt)) {
            enteredDoors.add(id);
        }
    }

    private void lobby(View view) {
        long now = System.currentTimeMillis();
        if (lobbyEnteredAt == 0) {
            lobbyEnteredAt = now;
            resetTarget();
        }
        Rs2NpcModel mummy = Microbot.getRs2NpcCache().query().withName("Guardian mummy").within(25).nearestOnClientThread();
        if (mummy != null) {
            if (!lobbyResolved) {
                lobbyResolved = true;
                entrances.confirm(now);
            }
            if (survive(view, false)) return;
            status = "Starting the minigame";
            if (!paced(1700, 2600)) return;
            if (act(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> mummy.click("Start-minigame")))
                waitForAction(() -> planeNow() == 0);
            return;
        }
        // NPCs arrive a moment after the room loads; an empty room is only wrong once that passed.
        if (now - lobbyEnteredAt < 1800) return;
        if (!lobbyResolved) {
            lobbyResolved = true;
            PyramidPlunderEntrances.Door door = entrances.attempted();
            entrances.reject();
            Microbot.log("[PyramidPlunder] Empty room behind the " + door + " door; trying another");
        }
        status = "Wrong door: leaving";
        Rs2TileObjectModel exit = Microbot.getRs2TileObjectCache().query().withId(LOBBY_EXIT).nearestOnClientThread();
        if (exit == null || !paced(1500, 2400)) return;
        if (act(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> exit.click("Leave Tomb")))
            waitForAction(() -> regionNow() != PYRAMID_REGION);
    }

    private void beginRun(View view) {
        runActive = true;
        runs++;
        // Each run gets its own click tempo.
        cadence = new PyramidPlunderClicks.Cadence(random);
        runStartedAt = System.currentTimeMillis() - Math.round(view.timerTicks * 600.0);
        currentRoom = 0;
        int maxRoom = PyramidPlunderPlan.maxRoom(view.thieving);
        Microbot.log("[PyramidPlunder] Run " + runs + " started; urns in rooms "
                + PyramidPlunderPlan.firstUrnRoom(maxRoom) + "-" + maxRoom
                + (config.betterXpMode() ? " (Better XP mode)" : ""));
    }

    private void enterRoom(int room) {
        learnUrnPace();
        currentRoom = room;
        roomEnteredAt = System.currentTimeMillis();
        trapDisarmedAt = 0;
        selectedDoorOpenedAt = 0;
        enteredDoors.clear();
        selectedDoor = selectedUrn = -1;
        lootedUrn = -1;
        urnLootedAt = 0;
        deadEndAt = 0;
        urnPhaseStartedAt = 0;
        lastUrnLootAt = 0;
        urnsLooted = 0;
        urnsLeftBefore = -1;
        sarcophagusOpeningSince = 0;
        resetTarget();
    }

    private void endRun() {
        learnUrnPace();
        runActive = false;
        currentRoom = 0;
        urnPhaseStartedAt = 0;
        resetTarget();
    }

    private void trackUrns(int room, int maxRoom, RoomScan scan) {
        int left = scan.urns.size();
        if (urnsLeftBefore >= 0 && left < urnsLeftBefore && urnPhaseStartedAt > 0) {
            urnsLooted += urnsLeftBefore - left;
            lastUrnLootAt = System.currentTimeMillis();
        }
        urnsLeftBefore = left;
    }

    private void learnUrnPace() {
        if (!urnPhaseInLastRoom || urnPhaseStartedAt == 0 || lastUrnLootAt <= urnPhaseStartedAt) return;
        secondsPerUrn = PyramidPlunderPlan.learnSecondsPerUrn(secondsPerUrn,
                (lastUrnLootAt - urnPhaseStartedAt) / 1000.0, urnsLooted);
        urnPhaseInLastRoom = false;
    }

    /** The lower of the game's timer and the clock since room 1, so one bad read cannot overrun. */
    private double secondsLeft(View view) {
        double byVarbit = PyramidPlunderPlan.secondsLeft(view.timerTicks);
        double byClock = RUN_SECONDS - (System.currentTimeMillis() - runStartedAt) / 1000.0;
        return Math.max(0, Math.min(byVarbit, byClock));
    }

    private boolean trapPassed() {
        return trapDisarmedAt >= roomEnteredAt || Microbot.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE) != 1
                || regionNow() != PYRAMID_REGION;
    }

    // ------------------------------------------------------------------ outside

    private void outside(View view) {
        if (view.thieving < 21) {
            fail("Pyramid Plunder needs 21 Thieving");
            return;
        }
        if (Rs2Dialogue.hasContinue()) {
            // The Guardian mummy's "You've had your five minutes" after the timer runs out.
            act(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> { Rs2Dialogue.clickContinue(); return true; });
            return;
        }
        if (survive(view, false)) return;

        if (view.plane != 0 || view.at.distanceTo2D(NORTH_EXIT) > 60) {
            status = "Walking to the Sophanem pyramid";
            act(BaseProfileDro.ActionPhase.SETUP, false, () -> Rs2Walker.walkTo(NORTH_EXIT, 3));
            return;
        }
        Rs2TileItemModel sceptre = Microbot.getRs2TileItemCache().query().withNameContains(SCEPTRE).within(12).nearestOnClientThread();
        if (sceptre != null) {
            // A sceptre found with a full inventory is dropped here when the mummy throws you out.
            status = "Picking up the Pharaoh's sceptre";
            if (Rs2Inventory.emptySlotCount() == 0) {
                act(BaseProfileDro.ActionPhase.MAINTENANCE, true, Rs2Player::useFood);
                return;
            }
            if (!paced(1200, 2000)) return;
            act(BaseProfileDro.ActionPhase.ACTIVE_WORK, true, sceptre::pickup);
            return;
        }
        if (config.hopToPlunderWorld() && view.world != PLUNDER_WORLD && hopFailures < 3) {
            hop();
            return;
        }
        if (restocking || needsRestock(view)) {
            restocking = true;
            restockOnSurface(view);
            return;
        }
        enterPyramid(view);
    }

    private void hop() {
        status = "Hopping to world " + PLUNDER_WORLD;
        resetTarget();
        if (!Microbot.hopToWorld(PLUNDER_WORLD)) {
            hopFailures++;
            if (hopFailures >= 3) Microbot.log("[PyramidPlunder] Could not hop to world " + PLUNDER_WORLD
                    + "; staying on this world", Level.WARN);
            sleep(600 + random.nextInt(900));
            return;
        }
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 20000);
        // A refused or cancelled hop must not be retried forever.
        if (!sleepUntil(() -> Microbot.getClient().getWorld() == PLUNDER_WORLD, 3000)) hopFailures++;
    }

    private boolean needsRestock(View view) {
        int food = Rs2Inventory.getInventoryFood().size();
        // Start with the carried book and food at the threshold; never bypass a below-threshold restock.
        if (runs == 0 && food >= Math.max(1, config.minFood()) && Rs2Inventory.hasItem("Prayer book")) return false;
        if (!PyramidPlunderAction.needsFood(food, config.minFood())) return false;
        if (contactDone()) return true;
        if (!bankUnavailableLogged) {
            bankUnavailableLogged = true;
            Microbot.log("[PyramidPlunder] The Sophanem bank needs the quest Contact!; continuing without banking", Level.WARN);
        }
        if (food == 0 && view.hpPercent() <= config.eatBelow()) {
            fail("Out of food and the Sophanem bank needs Contact!");
        }
        return false;
    }

    private void restockOnSurface(View view) {
        boolean contact = contactDone();
        int food = Rs2Inventory.getInventoryFood().size();
        boolean setupPending = config.inventorySetup() != null && !setupApplied;
        boolean wantsBank = contact && (setupPending || food < config.foodAmount());
        boolean wantsAltar = view.prayer < Math.min(view.prayerLevel, config.restorePrayerBelow());
        if (!wantsBank && !wantsAltar) {
            restocking = false;
            return;
        }
        if (view.at.distanceTo2D(TEMPLE) > 9) {
            status = "Walking to the temple";
            resetTarget();
            act(BaseProfileDro.ActionPhase.SETUP, false, () -> Rs2Walker.walkTo(TEMPLE, 3));
            return;
        }
        if (wantsAltar) {
            status = "Restoring prayer at the altar";
            Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query().withId(TEMPLE_ALTAR).nearestOnClientThread();
            if (altar == null || !paced(1800, 2800)) return;
            act(BaseProfileDro.ActionPhase.SETUP, false, () -> altar.click("Pray-at"));
            sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= view.prayerLevel, 3600);
            return;
        }
        status = "Climbing down to the bank";
        Rs2TileObjectModel ladder = Microbot.getRs2TileObjectCache().query().withId(TEMPLE_LADDER_DOWN).nearestOnClientThread();
        if (ladder == null || !paced(1800, 2800)) return;
        if (act(BaseProfileDro.ActionPhase.SETUP, false, () -> ladder.click("Climb-down")))
            waitForAction(() -> regionNow() == BANK_REGION);
    }

    private void bankRoom(View view) {
        if (!restocking) {
            if (Rs2Bank.isOpen()) {
                act(BaseProfileDro.ActionPhase.SETUP, false, Rs2Bank::closeBank);
                return;
            }
            status = "Climbing back up";
            Rs2TileObjectModel ladder = Microbot.getRs2TileObjectCache().query().withId(BANK_LADDER_UP).nearestOnClientThread();
            if (ladder == null || !paced(1800, 2800)) return;
            if (act(BaseProfileDro.ActionPhase.SETUP, false, () -> ladder.click("Climb-up")))
                waitForAction(() -> regionNow() != BANK_REGION);
            return;
        }
        status = "Banking";
        if (!Rs2Bank.isOpen()) {
            if (!paced(1500, 2400)) return;
            act(BaseProfileDro.ActionPhase.SETUP, false, Rs2Bank::openBank);
            sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }
        if (Rs2Inventory.count(item -> ARTEFACTS.contains(item.getName())) > 0) {
            act(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> Rs2Bank.depositAll(item -> ARTEFACTS.contains(item.getName())));
            return;
        }
        InventorySetup setup = config.inventorySetup();
        if (setup != null) {
            Rs2InventorySetup loader = new Rs2InventorySetup(setup, mainScheduledFuture);
            if (!loader.loadEquipment()) return;
            if (!loader.loadInventory()) return;
            setupApplied = true;
        } else {
            String food = foodName();
            int missing = config.foodAmount() - Rs2Inventory.getInventoryFood().size();
            if (missing > 0) {
                if (Rs2Bank.count(food) <= 0) {
                    if (Rs2Inventory.getInventoryFood().size() < Math.max(1, config.minFood())) {
                        fail("Out of " + food + " in the bank");
                        return;
                    }
                } else {
                    int amount = Math.min(missing, Rs2Inventory.emptySlotCount());
                    if (amount > 0) {
                        int before = Rs2Inventory.count();
                        act(BaseProfileDro.ActionPhase.SETUP, false, () -> Rs2Bank.withdrawX(false, food, amount));
                        sleepUntil(() -> Rs2Inventory.count() != before, 2400);
                        return;
                    }
                }
            }
        }
        restocking = false;
    }

    private void enterPyramid(View view) {
        PyramidPlunderEntrances.Door door = entrances.attempted();
        if (door == null) door = entrances.choose(view.at, playersAtDoors(), System.currentTimeMillis());
        status = "Searching the " + door.name().toLowerCase() + " door";
        if (view.at.distanceTo2D(door.approach) > 14) {
            resetTarget();
            final WorldPoint approach = door.approach;
            act(BaseProfileDro.ActionPhase.SETUP, false, () -> Rs2Walker.walkTo(approach, 3));
            return;
        }
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withId(door.objectId).nearestOnClientThread();
        if (object == null) return;
        interactObject(object, "Search", () -> regionNow() == PYRAMID_REGION);
    }

    /** Other players standing at each entrance; on the themed world they queue at the right one. */
    private int[] playersAtDoors() {
        int[] counts = new int[PyramidPlunderEntrances.Door.values().length];
        String self = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player local = Microbot.getClient().getLocalPlayer();
            return local == null ? null : local.getName();
        }).orElse(null);
        List<Rs2PlayerModel> players = Microbot.getRs2PlayerCache().query().within(NORTH_EXIT, 14).toListOnClientThread();
        for (Rs2PlayerModel player : players) {
            WorldPoint at = player.getWorldLocation();
            if (at == null || (self != null && self.equals(player.getName()))) continue;
            for (PyramidPlunderEntrances.Door door : PyramidPlunderEntrances.Door.values()) {
                // Everyone leaving the pyramid lands on the north approach tile; that is not a queue.
                if (door == PyramidPlunderEntrances.Door.NORTH && at.equals(NORTH_EXIT)) continue;
                if (at.distanceTo2D(door.approach) <= 2) counts[door.ordinal()]++;
            }
        }
        return counts;
    }

    // ------------------------------------------------------------------ survival

    /** Prayer, food and poison; true when this pass was spent on one of them. */
    private boolean survive(View view, boolean inside) {
        long now = System.currentTimeMillis();
        if (config.protectFromMelee() && view.prayerLevel >= 43) {
            boolean threat = inside && threatened();
            boolean active = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE);
            if (threat) {
                threatSeenAt = now;
                if (!active && view.prayer > 0) {
                    resetTarget();
                    act(BaseProfileDro.ActionPhase.MAINTENANCE, true,
                            () -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true, true));
                    return true;
                }
            } else if (active && now - threatSeenAt > prayerLingerMs) {
                prayerLingerMs = 1800 + random.nextInt(2600);
                resetTarget();
                act(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                        () -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false, true));
                return true;
            }
        }
        if (view.hpPercent() <= eatAt && !Rs2Inventory.getInventoryFood().isEmpty()) {
            int before = Rs2Inventory.count();
            resetTarget();
            act(BaseProfileDro.ActionPhase.MAINTENANCE, true, Rs2Player::useFood);
            sleepUntil(() -> Rs2Inventory.count() != before, 1200);
            eatAt = rollEatAt();
            return true;
        }
        if (!inside && view.poisoned && now >= nextCureAt) {
            boolean cured = curePoison();
            nextCureAt = now + (cured ? 6_000 + random.nextInt(6_000) : 30_000);
            if (cured) {
                resetTarget();
                waitForAction(() -> !poisonedNow());
                return true;
            }
        }
        return false;
    }

    private boolean threatened() {
        return !Microbot.getRs2NpcCache().query().withNames("Scarab Swarm", "Mummy").within(12)
                .where(Rs2NpcModel::isInteractingWithPlayer).toListOnClientThread().isEmpty();
    }

    private boolean curePoison() {
        boolean symbol = Rs2Equipment.isWearing("Holy symbol") || Rs2Equipment.isWearing("Unholy symbol");
        if (symbol && Rs2Inventory.hasItem("Prayer book")
                && Rs2Player.getBoostedSkillLevel(Skill.PRAYER) > 0) {
            return act(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                    () -> Rs2Inventory.interact("Prayer book", "Recite-prayer"));
        }
        List<String> cures = Rs2Potion.getAntiPoisonVariants();
        Rs2ItemModel potion = Rs2Inventory.get(item -> {
            String name = item.getName();
            if (name == null) return false;
            for (String cure : cures) if (name.toLowerCase().startsWith(cure.toLowerCase())) return true;
            return false;
        });
        if (potion == null) return false;
        return act(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(potion, "Drink"));
    }

    private int rollEatAt() {
        int base = config == null ? 55 : config.eatBelow();
        return Math.max(8, Math.min(95, base - 4 + random.nextInt(9)));
    }

    private String foodName() {
        return config.food().getName();
    }

    private boolean contactDone() {
        return Rs2Player.getQuestState(Quest.CONTACT) == QuestState.FINISHED;
    }

    private void fail(String message) {
        if (error != null) return;
        error = message;
        status = message;
        Microbot.log("[PyramidPlunder] " + message, Level.WARN);
        Microbot.showMessage(message);
    }

    // ------------------------------------------------------------------ input

    /** One profile-layered action. */
    private boolean act(BaseProfileDro.ActionPhase phase, boolean urgent, BooleanSupplier action) {
        BaseProfileDro current = profile;
        if (current == null || !inputAllowed()) return false;
        return current.performAction(phase, urgent, action);
    }

    /** True once at least {@code min}-{@code max} ms passed since the last single click of this kind. */
    private boolean paced(int min, int max) {
        long now = System.currentTimeMillis();
        if (now < nextPacedClickAt) return false;
        nextPacedClickAt = now + min + random.nextInt(Math.max(1, max - min));
        return true;
    }

    private boolean interactObject(Rs2TileObjectModel target, String action, BooleanSupplier done) {
        return interactObject(target, action, done, false);
    }

    /**
     * Submit once; the loop owns completion and an idle failed attempt can retry next pass.
     * {@code urgent} uses the profile's quick reaction instead of its working pause.
     */
    private boolean interactObject(Rs2TileObjectModel target, String action, BooleanSupplier done, boolean urgent) {
        BaseProfileDro current = profile;
        if (target == null || current == null || !inputAllowed()) return false;
        if (done.getAsBoolean()) return true;
        targetKey = target.getId() + "@" + target.getWorldLocation() + ":" + action;
        attemptStartedAt = System.currentTimeMillis();
        if (act(BaseProfileDro.ActionPhase.ACTIVE_WORK, urgent, () -> target.click(action))) {
            waitForAction(done);
        }
        return false;
    }

    /**
     * An urn search: the first click goes through the profile like any action, then a few follow-up
     * clicks on the same urn on the recorded repeat-click rhythm keep the search going without an idle gap.
     * Straight after an urn's experience drop the next urn is clicked on the profile's quick reaction.
     */
    private boolean interactUrn(Rs2TileObjectModel urn, BooleanSupplier looted) {
        boolean chained = PyramidPlunderAction.chained(System.currentTimeMillis(), urnLootedAt);
        boolean done = interactObject(urn, "Search", looted, chained);
        if (pendingAction != null && repeatTarget == null) {
            pendingAction = new PyramidPlunderAction(System.currentTimeMillis(), true);
            pendingUrn = urn.getId();
            repeatTarget = urn;
            repeatAction = "Search";
            repeatsLeft = 2 + random.nextInt(3);
            nextRepeatAt = System.currentTimeMillis() + cadence.nextIntervalMs();
        }
        return done;
    }

    /** The Thieving experience drop is the loot; the urn showing as opened is the fallback. */
    private static boolean urnLooted(int urnId, int xpBefore) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            return PyramidPlunderAction.xpGained(client.getSkillExperience(Skill.THIEVING), xpBefore)
                    || URN_LOOTED.contains(impostorOnClientThread(client, urnId));
        }).orElse(false);
    }

    private void repeatClick(boolean receipt) {
        Rs2TileObjectModel target = repeatTarget;
        if (target == null || receipt || repeatsLeft <= 0 || !inputAllowed()) return;
        long now = System.currentTimeMillis();
        if (now < nextRepeatAt) return;
        repeatsLeft--;
        nextRepeatAt = now + cadence.nextIntervalMs();
        target.click(repeatAction);
    }

    private void clearRepeat() {
        repeatTarget = null;
        repeatAction = null;
        repeatsLeft = 0;
    }

    private void waitForAction(BooleanSupplier receipt) {
        pendingReceipt = receipt;
        pendingAction = new PyramidPlunderAction(System.currentTimeMillis());
        pendingUrn = -1;
        clearRepeat();
    }

    private boolean awaitingAction(View view) {
        if (pendingAction == null) return false;
        boolean receipt = pendingReceipt.getAsBoolean();
        PyramidPlunderAction.Result result = pendingAction.poll(System.currentTimeMillis(),
                view.animating || view.moving, receipt);
        if (result == PyramidPlunderAction.Result.WAIT) {
            repeatClick(receipt);
            return true;
        }
        int urn = pendingUrn;
        pendingAction = null;
        pendingReceipt = null;
        pendingUrn = -1;
        clearRepeat();
        if (result == PyramidPlunderAction.Result.TIMEOUT) {
            // Watchdog: re-read the room and pick the next step instead of giving up the activity.
            recoveries++;
            resetTarget();
            selectedDoor = selectedUrn = -1;
            nextPacedClickAt = 0;
            if (recoveries >= PyramidPlunderAction.MAX_RECOVERIES) {
                fail("Pyramid Plunder actions did not settle " + recoveries + " times in a row");
                return true;
            }
            Microbot.log("[PyramidPlunder] Action did not settle within 30 seconds; recovering ("
                    + recoveries + "/" + PyramidPlunderAction.MAX_RECOVERIES + ")", Level.WARN);
            return false;
        }
        if (result == PyramidPlunderAction.Result.COMPLETE) {
            recoveries = 0;
            if (urn >= 0) {
                lootedUrn = urn;
                urnLootedAt = System.currentTimeMillis();
            }
            resetTarget();
        }
        return false;
    }

    private void resetTarget() {
        targetKey = null;
        clearRepeat();
    }

    private boolean inputAllowed() {
        return !Thread.currentThread().isInterrupted() && !Microbot.pauseAllScripts.get()
                && Microbot.isLoggedIn() && error == null;
    }

    // ------------------------------------------------------------------ reads

    private static final class View {
        WorldPoint at;
        int region;
        int plane;
        int timerTicks;
        boolean trapActive;
        int thieving;
        int hp;
        int maxHp;
        int prayer;
        int prayerLevel;
        boolean poisoned;
        int world;
        boolean waiting;
        boolean animating;
        boolean moving;

        int hpPercent() { return maxHp <= 0 ? 100 : hp * 100 / maxHp; }
    }

    private View read() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player local = client.getLocalPlayer();
            if (local == null || client.getGameState() != GameState.LOGGED_IN) return null;
            WorldPoint at = local.getWorldLocation();
            if (at == null) return null;
            View view = new View();
            view.at = at;
            view.animating = local.getAnimation() != -1;
            view.moving = local.getPoseAnimation() != local.getIdlePoseAnimation();
            view.region = at.getRegionID();
            view.plane = at.getPlane();
            view.timerTicks = client.getVarbitValue(VarbitID.NTK_PLAYER_TIMER_COUNT);
            view.trapActive = client.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE) == 1;
            view.thieving = client.getRealSkillLevel(Skill.THIEVING);
            view.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
            view.maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
            view.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
            view.prayerLevel = client.getRealSkillLevel(Skill.PRAYER);
            view.poisoned = client.getVarpValue(VarPlayerID.POISON) > 0;
            view.world = client.getWorld();
            // The sarcophagus opens on its own for several seconds: the one natural wait in a run.
            view.waiting = view.region == PYRAMID_REGION && impostorOnClientThread(client, SARCOPHAGUS) == SARCOPHAGUS_OPENING;
            return view;
        }).orElse(null);
    }

    private boolean poisonedNow() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getVarpValue(VarPlayerID.POISON) > 0).orElse(false);
    }

    private int regionNow() {
        WorldPoint at = Rs2Player.getWorldLocation();
        return at == null ? -1 : at.getRegionID();
    }

    private int planeNow() {
        WorldPoint at = Rs2Player.getWorldLocation();
        return at == null ? -1 : at.getPlane();
    }

    static int roomAt(WorldPoint at) {
        if (at == null || at.getPlane() != 0) return 0;
        for (int room = 0; room < ROOMS.length; room++) {
            if (inBox(ROOMS[room], at)) return room + 1;
        }
        return 0;
    }

    static boolean inBox(int[] box, WorldPoint at) {
        return at != null && at.getX() >= box[0] && at.getY() >= box[1] && at.getX() <= box[2] && at.getY() <= box[3];
    }

    /** The state a multi-state object currently shows for this player. */
    private static int impostor(int objectId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> impostorOnClientThread(Microbot.getClient(), objectId))
                .orElse(-1);
    }

    private static int impostorOnClientThread(Client client, int objectId) {
        ObjectComposition composition = client.getObjectDefinition(objectId);
        if (composition == null) return -1;
        if (composition.getImpostorIds() == null) return composition.getId();
        ObjectComposition shown = composition.getImpostor();
        return shown == null ? -1 : shown.getId();
    }

    private static final class RoomScan {
        Rs2TileObjectModel trap;
        Rs2TileObjectModel chest;
        boolean chestClosed;
        Rs2TileObjectModel sarcophagus;
        boolean sarcophagusClosed;
        boolean sarcophagusOpening;
        Rs2TileObjectModel exit;
        /** Thieving experience when the room was read: the baseline for the next urn's experience drop. */
        int thievingXp;
        final List<Rs2TileObjectModel> urns = new ArrayList<>();
        final List<Rs2TileObjectModel> closedDoors = new ArrayList<>();
        final List<Rs2TileObjectModel> openDoors = new ArrayList<>();
    }

    /** Everything lootable in this room, nearest first. Objects of other rooms share the scene. */
    private RoomScan scan(int room) {
        int[] box = ROOMS[room - 1];
        int[] ids = new int[URNS.length + TOMB_DOORS.length + 4];
        System.arraycopy(URNS, 0, ids, 0, URNS.length);
        System.arraycopy(TOMB_DOORS, 0, ids, URNS.length, TOMB_DOORS.length);
        ids[ids.length - 4] = SPEARTRAP;
        ids[ids.length - 3] = CHEST;
        ids[ids.length - 2] = SARCOPHAGUS;
        ids[ids.length - 1] = ROOM_EXIT;

        // One client-thread call per pass: every hop waits for a client frame, and the old per-object
        // reads cost seconds a pass (2026-11-09 test: 8-11 s idle between actions).
        return Microbot.getClientThread().runOnClientThreadOptional(() -> scanOnClientThread(box, ids)).orElse(null);
    }

    private RoomScan scanOnClientThread(int[] box, int[] ids) {
        Client client = Microbot.getClient();
        Map<Integer, Integer> shown = new HashMap<>();
        for (int id : ids) shown.put(id, impostorOnClientThread(client, id));

        Player local = client.getLocalPlayer();
        WorldPoint player = local == null ? null : local.getWorldLocation();
        List<Rs2TileObjectModel> objects = new ArrayList<>();
        Map<Rs2TileObjectModel, WorldPoint> at = new HashMap<>();
        for (Rs2TileObjectModel object : Microbot.getRs2TileObjectCache().query().withIds(ids).toListOnClientThread()) {
            WorldPoint location = object.getWorldLocation();
            if (inBox(box, location)) {
                objects.add(object);
                at.put(object, location);
            }
        }
        if (player != null) objects.sort(Comparator.comparingInt(o -> at.get(o).distanceTo2D(player)));

        RoomScan scan = new RoomScan();
        scan.thievingXp = client.getSkillExperience(Skill.THIEVING);
        for (Rs2TileObjectModel object : objects) {
            int id = object.getId();
            int state = shown.getOrDefault(id, -1);
            if (id == SPEARTRAP) {
                if (scan.trap == null) scan.trap = object;
            } else if (id == CHEST) {
                scan.chest = object;
                scan.chestClosed = state == CHEST_CLOSED;
            } else if (id == SARCOPHAGUS) {
                scan.sarcophagus = object;
                scan.sarcophagusClosed = state == SARCOPHAGUS_CLOSED;
                scan.sarcophagusOpening = state == SARCOPHAGUS_OPENING;
            } else if (id == ROOM_EXIT) {
                if (scan.exit == null) scan.exit = object;
            } else if (isTombDoor(id)) {
                if (state == TOMB_DOOR_CLOSED) scan.closedDoors.add(object);
                else if (state >= 0) scan.openDoors.add(object);
            } else if (state >= 0 && !URN_LOOTED.contains(state)) {
                scan.urns.add(object);
            }
        }
        return scan;
    }

    private static boolean isTombDoor(int id) {
        for (int door : TOMB_DOORS) if (door == id) return true;
        return false;
    }
}
