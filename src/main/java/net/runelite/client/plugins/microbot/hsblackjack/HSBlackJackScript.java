package net.runelite.client.plugins.microbot.hsblackjack;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class HSBlackJackScript extends Script {

    private static final int HISTORY_SIZE = 8;
    private static final int PICKPOCKET_BURST_ATTEMPTS = 2;
    private static final int LURE_MAX_ATTEMPTS = 5;
    private static final int EAT_AT_HP_PERCENT = 35;
    private static final String FOOD_NAME = "Jug of wine";
    private static final String EMPTY_JUG_NAME = "Jug";
    private static final String BAR_NPC_NAME = "Faisal the Barman";
    private static final String TARGET_NPC_NAME = "Menaphite thug";
    private static final int MIN_WINE_STOCK_REQUIRED = 13;
    private static final int WINE_TO_BUY = 13; // his max stock
    private static final int MAX_RESTOCK_ROUNDS = 2; // 2x Faisal's max stock (13) = 26 wine per trip
    private static final int ROOM_CHECK_DISTANCE = 5;
    private static final String COIN_POUCH_NAME = "Coin pouch";
    private static final int COIN_POUCH_OPEN_THRESHOLD = 27;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Tent route (confirmed in-game coordinates).
    private static final WorldPoint START_POSITION = new WorldPoint(3345, 2957, 0);
    private static final WorldPoint CURTAIN_POSITION = new WorldPoint(3345, 2955, 0);
    private static final WorldPoint TENT_DEEP_POSITION = new WorldPoint(3341, 2954, 0);
    private static final WorldPoint CURTAIN_CLOSE_POSITION = new WorldPoint(3344, 2955, 0);
    private static final WorldPoint BAR_POSITION = new WorldPoint(3358, 2956, 0);
    private static final int ARRIVAL_DISTANCE = 1;
    private static final int RISKY_CALL_TIMEOUT_SECONDS = 3;
    private static final int CURTAIN_STUCK_TIMEOUT_MS = 5000;
    private static final int NPC_ENTER_TENT_TIMEOUT_MS = 10000;
    private static final int START_ARRIVAL_DISTANCE = 4;
    private static final int START_STUCK_TIMEOUT_MS = 15000;
    private static final int DOOR_OPEN_ATTEMPT_INTERVAL_MS = 3000;

    // West tent bounding box (confirmed corners) - used to reliably tell
    // "inside THIS tent" from "inside the neighboring east tent", without
    // relying on fragile line-of-sight checks that can be blocked by
    // decorations even within the same tent.
    private static final int TENT_MIN_X = 3340;
    private static final int TENT_MAX_X = 3344;
    private static final int TENT_MIN_Y = 2953;
    private static final int TENT_MAX_Y = 2956;
    private static final int TENT_PLANE = 0;

    // Excluded search zones - NPCs found here are skipped as lure targets,
// since they consistently cause follow issues (getting stuck at walls/
// corners) when led toward the tent. Format: {minX, maxX, minY, maxY}.
    private static final int[][] EXCLUDED_SEARCH_ZONES = {
            {3339, 3345, 2960, 2966}, // zone 1
            {3345, 3347, 2944, 2954}, // zone 2
            {3338, 3344, 2943, 2952}  // zone 3
    };

    // Animation IDs confirmed via manual session logging:
    // 838 = NPC's "knocked unconscious" animation (knock-out succeeded)
    // 395 = NPC's "attacking" animation (knock-out failed, it's retaliating)
    // 829 = player's stun animation (matches Rs2Player.isStunned() exactly)
    private static final int NPC_ANIM_KNOCKED_OUT = 838;
    private static final int NPC_ANIM_ATTACKING = 395;
    private static final int KNOCKOUT_POLL_WINDOW_MS = 1400;
    private static final int KNOCKOUT_POLL_INTERVAL_MS = 120;
    private static final int COMBAT_BLOCKED_MESSAGE_VALID_MS = 2000;
    private static final int COMBAT_ESCAPE_WAIT_MS = 3000;
    private static final int ROOM_BLOCKED_HOP_THRESHOLD_MS = 15000;

    private enum Phase {
        TRAVEL_TO_START,
        CHECK_TENT_OCCUPANCY,
        FIND_TARGET,
        RUN_TO_TARGET,
        LURING,
        WALK_TO_CURTAIN,
        OPEN_CURTAIN,
        WALK_DEEPER,
        WALK_TO_CLOSE_POSITION,
        CLOSE_CURTAIN,
        KNOCK_OUT,
        PICKPOCKET,
        COMBAT_ESCAPE,
        RESTOCK_OPEN_CURTAIN,
        RESTOCK_WALK_TO_BAR,
        RESTOCK_BUY
    }

    public String state = "Idle";
    private final Deque<String> history = new ArrayDeque<>();
    private long tickCounter = 0;

    private final HSBlackJackPlugin plugin;
    private final HSBlackJackConfig config;

    private Phase phase = Phase.TRAVEL_TO_START;
    private Rs2NpcModel luredNpc;
    private boolean needsLureInsideTent = false;

    private int knockoutAttempts;
    private int pickpocketAttempts;
    private Instant startTime;
    private int startXp;
    private int restockRoundsCompleted = 0;

    private long roomBlockedSince = -1;
    private long restockCurtainStuckSince = -1;
    private long npcEnterTentWaitSince = -1;
    private long travelToStartStuckSince = -1;
    private int travelToStartBestDistance = Integer.MAX_VALUE;
    private long travelToStartLastDoorAttempt = -1;

    // Set (from the plugin's chat listener) whenever the exact "You can't do
    // this during combat." message appears. Read+cleared by the main loop.
    private volatile long combatBlockedMessageTime = -1;

    // --- Situation logging (writes every state CHANGE to the IntelliJ console) ---
    private int lastPlayerAnimation = -1;
    private boolean lastPlayerStunned = false;
    private boolean lastPlayerInCombat = false;
    private int lastNpcAnimation = -1;
    private boolean lastNpcInteracting = false;

    @Inject
    public HSBlackJackScript(HSBlackJackPlugin plugin, HSBlackJackConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Called by HSBlackJackPlugin's chat listener the moment the exact
     * "You can't do this during combat." message appears.
     */
    public void onCombatBlockedMessage() {
        combatBlockedMessageTime = System.currentTimeMillis();
        log.info("[SITUATION] Combat-blocked chat message received");
    }

    private boolean wasRecentlyCombatBlocked() {
        return combatBlockedMessageTime > 0
                && (System.currentTimeMillis() - combatBlockedMessageTime) <= COMBAT_BLOCKED_MESSAGE_VALID_MS;
    }

    private void setState(String newState) {
        state = newState;
        String timestamped = LocalTime.now().format(TIME_FORMAT) + " - " + newState;
        history.addFirst(timestamped);
        while (history.size() > HISTORY_SIZE) {
            history.removeLast();
        }
    }

    public Deque<String> getHistory() {
        return history;
    }

    public long getTickCounter() {
        return tickCounter;
    }

    public long getXpGained() {
        return Microbot.getClient().getSkillExperience(Skill.THIEVING) - startXp;
    }

    public double getXpPerHour() {
        double hoursElapsed = Duration.between(startTime, Instant.now()).toMillis() / 3_600_000.0;
        if (hoursElapsed <= 0) return 0;
        return getXpGained() / hoursElapsed;
    }

    /**
     * Returns the elapsed runtime since the script started, formatted as
     * HH:mm:ss for display in the overlay.
     */
    public String getElapsedTime() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        long h = elapsed.toHours();
        long m = elapsed.toMinutes() % 60;
        long s = elapsed.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * Logs any change in player/NPC animation, stun, or combat state to the
     * IntelliJ console (log.info), tagged with the current phase. Only logs
     * when something actually changes. Search the console for "[SITUATION]".
     */
    private void logSituation() {
        int playerAnim = Rs2Player.getAnimation();
        boolean playerStunned = Rs2Player.isStunned();
        boolean playerInCombat = Rs2Player.isInCombat();

        int npcAnim = -1;
        boolean npcInteracting = false;
        if (isLuredNpcValid()) {
            npcAnim = luredNpc.getAnimation();
            npcInteracting = luredNpc.isInteractingWithPlayer();
        }

        StringBuilder changes = new StringBuilder();
        if (playerAnim != lastPlayerAnimation) {
            changes.append("playerAnim ").append(lastPlayerAnimation).append("->").append(playerAnim).append("; ");
            lastPlayerAnimation = playerAnim;
        }
        if (playerStunned != lastPlayerStunned) {
            changes.append("playerStunned ").append(lastPlayerStunned).append("->").append(playerStunned).append("; ");
            lastPlayerStunned = playerStunned;
        }
        if (playerInCombat != lastPlayerInCombat) {
            changes.append("playerInCombat ").append(lastPlayerInCombat).append("->").append(playerInCombat).append("; ");
            lastPlayerInCombat = playerInCombat;
        }
        if (npcAnim != lastNpcAnimation) {
            changes.append("npcAnim ").append(lastNpcAnimation).append("->").append(npcAnim).append("; ");
            lastNpcAnimation = npcAnim;
        }
        if (npcInteracting != lastNpcInteracting) {
            changes.append("npcInteracting ").append(lastNpcInteracting).append("->").append(npcInteracting).append("; ");
            lastNpcInteracting = npcInteracting;
        }

        if (changes.length() > 0) {
            log.info("[SITUATION] [phase={}] {}", phase, changes);
        }
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        knockoutAttempts = 0;
        pickpocketAttempts = 0;
        startTime = Instant.now();
        startXp = Microbot.getClient().getSkillExperience(Skill.THIEVING);
        phase = Phase.TRAVEL_TO_START;
        luredNpc = null;
        needsLureInsideTent = false;
        tickCounter = 0;
        combatBlockedMessageTime = -1;
        roomBlockedSince = -1;
        restockCurtainStuckSince = -1;
        restockRoundsCompleted = 0;
        npcEnterTentWaitSince = -1;
        travelToStartStuckSince = -1;
        travelToStartBestDistance = Integer.MAX_VALUE;
        travelToStartLastDoorAttempt = -1;
        history.clear();
        setState("Started");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                tickCounter++;

                if (!Microbot.isLoggedIn()) {
                    setState("Waiting: not logged in");
                    return;
                }

                if (!super.run()) {
                    setState("BLOCKED: super.run() returned false");
                    return;
                }

                if (!Rs2Inventory.hasItem(FOOD_NAME) && Rs2Inventory.itemQuantity("Coins") <= 0) {
                    setState("!!! REQUIRED: PUT COINS OR JUG OF WINE IN YOUR INVENTORY TO START !!!");
                    log.error("[SITUATION] Missing starting requirements (no coins, no {}) - stopping plugin", FOOD_NAME);
                    Microbot.showMessage("HSBlackJack cannot start: put Coins or " + FOOD_NAME + " in your inventory first.");
                    Microbot.stopPlugin(plugin);
                    return;
                }

                logSituation();

                // Automatic emergency eating - hardcoded, not a config option.
                if (Rs2Player.getHealthPercentage() <= EAT_AT_HP_PERCENT) {
                    if (Rs2Inventory.hasItem(FOOD_NAME)) {
                        setState("Low HP - eating " + FOOD_NAME);
                        Rs2Player.eatAt(EAT_AT_HP_PERCENT);
                        return;
                    } else {
                        setState("Low HP but no " + FOOD_NAME + " in inventory!");
                    }
                }

                // Coin pouches full? Open them all - hardcoded, not a config option.
                if (Rs2Inventory.itemQuantity(COIN_POUCH_NAME) >= COIN_POUCH_OPEN_THRESHOLD) {
                    setState("Opening all coin pouches");
                    Rs2Inventory.interact(COIN_POUCH_NAME, "Open-all");
                    sleepTicks(1);
                    return;
                }

                // Combat safety net: ONLY the exact "You can't do this during
                // combat." chat message triggers the heavy unequip/re-equip
                // escape - a single stray hit (isInCombat true) does not, so
                // a knock-out that still succeeds right after isn't disrupted.
                if (phase != Phase.COMBAT_ESCAPE && wasRecentlyCombatBlocked()) {
                    setState("Combat blocked (exact message received) - starting combat escape");
                    phase = Phase.COMBAT_ESCAPE;
                }

                if (phase == Phase.COMBAT_ESCAPE) {
                    handleCombatEscape();
                    return;
                }

                // Out of Jug of wine (1 or 0 left)? Kick off the restock trip.
                boolean inRestockFlow = phase == Phase.RESTOCK_OPEN_CURTAIN
                        || phase == Phase.RESTOCK_WALK_TO_BAR
                        || phase == Phase.RESTOCK_BUY;

                if (!inRestockFlow && phase != Phase.TRAVEL_TO_START && Rs2Inventory.itemQuantity(FOOD_NAME) <= 0) {
                    setState("Low on " + FOOD_NAME + " - starting restock trip");
                    phase = Phase.RESTOCK_OPEN_CURTAIN;
                    inRestockFlow = true;
                }

                if (inRestockFlow) {
                    handleRestockFlow();
                    return;
                }

                // Make sure the correct blackjack is wielded (not needed while
                // still travelling to the start position).
                if (phase != Phase.TRAVEL_TO_START) {
                    String blackjackName = config.blackjackType().getItemName();
                    if (!Rs2Equipment.isWearing(blackjackName)) {
                        if (Rs2Inventory.hasItem(blackjackName)) {
                            setState("Equipping blackjack");
                            Rs2Inventory.wield(blackjackName);
                        } else {
                            setState("Missing blackjack in inventory!");
                        }
                        return;
                    }
                }

                // Chain phase transitions within the same tick whenever a
                // handler moves us to a new phase without ever needing to
                // wait for real game-time to pass (e.g. "arrived at curtain"
                // -> "open it", or "pickpocket burst done" -> "knock out
                // again"). Every handler that DOES need real time to pass
                // already calls sleep()/sleepTicks() internally, so this
                // never skips necessary waiting - it only removes the extra,
                // artificial ~600ms gap that would otherwise be added on top
                // of that for pure bookkeeping transitions. A phase that
                // stays the same (e.g. still walking, still waiting on a
                // condition) naturally stops the loop, so this can't turn
                // into a busy-loop. chainGuard is just a hard safety cap.
                int chainGuard = 0;
                Phase phaseBeforeHandler;
                do {
                    phaseBeforeHandler = phase;

                    switch (phase) {
                        case TRAVEL_TO_START:
                            handleTravelToStart();
                            break;
                        case CHECK_TENT_OCCUPANCY:
                            handleCheckTentOccupancy();
                            break;
                        case FIND_TARGET:
                            handleFindTarget();
                            break;
                        case RUN_TO_TARGET:
                            handleRunToTarget();
                            break;
                        case LURING:
                            handleLuring();
                            break;
                        case WALK_TO_CURTAIN:
                            handleWalkToCurtain();
                            break;
                        case OPEN_CURTAIN:
                            handleOpenCurtain();
                            break;
                        case WALK_DEEPER:
                            handleWalkDeeper();
                            break;
                        case WALK_TO_CLOSE_POSITION:
                            handleWalkToClosePosition();
                            break;
                        case CLOSE_CURTAIN:
                            handleCloseCurtain();
                            break;
                        case KNOCK_OUT:
                            handleKnockOut();
                            break;
                        case PICKPOCKET:
                            handlePickpocketBurst();
                            break;
                        default:
                            break;
                    }

                    chainGuard++;
                } while (phase != phaseBeforeHandler && chainGuard < 15);

            } catch (Throwable ex) {
                setState("ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                log.error("Throwable in main loop: ", ex);
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isLuredNpcValid() {
        return luredNpc != null && luredNpc.getNpc() != null;
    }

    /**
     * Checks whether a point falls within the confirmed west-tent bounding box
     * (x 3340-3344, y 2953-2956, plane 0) - a simple, reliable rectangle check
     * that can't be tripped up by decorations/objects blocking line-of-sight
     * within the same tent, unlike a distance+LOS check.
     */
    private boolean isInsideWestTent(WorldPoint point) {
        if (point == null) return false;
        return point.getPlane() == TENT_PLANE
                && point.getX() >= TENT_MIN_X && point.getX() <= TENT_MAX_X
                && point.getY() >= TENT_MIN_Y && point.getY() <= TENT_MAX_Y;
    }

    /**
     * Checks whether a point falls within one of the manually confirmed
     * "problem zones" - areas where a lured NPC's follow-AI reliably gets
     * stuck (e.g. behind a wall corner), so we simply don't pick NPCs from
     * there as lure targets in the first place.
     */
    private boolean isInExcludedSearchZone(WorldPoint point) {
        if (point == null || point.getPlane() != TENT_PLANE) return false;
        for (int[] zone : EXCLUDED_SEARCH_ZONES) {
            if (point.getX() >= zone[0] && point.getX() <= zone[1]
                    && point.getY() >= zone[2] && point.getY() <= zone[3]) {
                return true;
            }
        }
        return false;
    }

    private boolean isLuredNpcInsideTent() {
        if (!isLuredNpcValid()) return false;
        return isInsideWestTent(luredNpc.getWorldLocation());
    }

    private boolean isPlayerInsideTent() {
        return isInsideWestTent(Rs2Player.getWorldLocation());
    }

    /**
     * Runs a risky Microbot call with a hard timeout, since some calls can hang
     * forever if the underlying client-thread task throws or never completes.
     */
    private <T> T withTimeout(Callable<T> task, T defaultValue) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).get(RISKY_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            setState("TIMEOUT/ERROR on risky call: " + ex.getClass().getSimpleName());
            log.warn("withTimeout: risky call failed or timed out: {}", ex.getMessage());
            return defaultValue;
        }
    }

    /** Walking (not running) - used in the main lure cycle so a lured NPC keeps up. */
    private boolean walkTo(WorldPoint target) {
        Rs2Player.toggleRunEnergy(false);
        boolean walkStarted = Rs2Walker.walkFastCanvas(target, false);
        if (!walkStarted) {
            Rs2Walker.walkTo(target, 0);
        }
        return walkStarted;
    }

    /**
     * Walks toward a target, but pauses (issues no new movement) whenever the
     * NPC we're actively leading (confirmed via isInteractingWithPlayer()) has
     * fallen more than 2 tiles behind - giving its own slower follow-AI a
     * chance to catch up before we move further, instead of repeatedly
     * dashing ahead and leaving it stuck at a corner or wall. Only applies
     * while an NPC is actually following us - approaching a stationary,
     * not-yet-lured NPC still walks normally.
     */
    private void walkWaitingForNpc(WorldPoint target) {
        if (isLuredNpcValid() && luredNpc.isInteractingWithPlayer()) {
            int npcDistance = luredNpc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation());
            if (npcDistance > 2) {
                setState("Waiting for NPC to catch up (distance=" + npcDistance + ")");
                return;
            }
        }
        walkTo(target);
    }

    /** Running - used for local restock movement, where no NPC needs to keep up. */
    private boolean runTo(WorldPoint target) {
        Rs2Player.toggleRunEnergy(true);
        boolean walkStarted = Rs2Walker.walkFastCanvas(target, true);
        if (!walkStarted) {
            Rs2Walker.walkTo(target, 0);
        }
        return walkStarted;
    }

    /**
     * Smart, transport-aware travel for long distances (used only for the
     * initial journey to the start position, from anywhere in Gielinor).
     * Uses Rs2Walker's full webwalker directly, which can route through
     * fairy rings, Shantay Pass, the desert rug merchant, and other
     * transports - unlike the canvas-click-only walkTo()/runTo() used for
     * short, local movement within Pollnivneach itself.
     */
    private boolean smartTravelTo(WorldPoint target) {
        Rs2Player.toggleRunEnergy(true);
        return Rs2Walker.walkTo(target, 0);
    }

    /**
     * Checks the curtain's REAL current state (open/closed) using the safe
     * findObjectByLocation + convertToObjectComposition path (which degrades
     * gracefully via runOnClientThreadOptional), instead of the tile-object
     * cache's own getName()/getObjectComposition(), which hangs forever for
     * this object. "Close" action available = open. Otherwise = closed.
     */
    private boolean isCurtainOpen() {
        return withTimeout(() -> {
            TileObject curtain = Rs2GameObject.findObjectByLocation(CURTAIN_POSITION);
            if (curtain == null) return false;

            ObjectComposition composition = Rs2GameObject.convertToObjectComposition(curtain.getId());
            if (composition == null || composition.getActions() == null) return false;

            for (String action : composition.getActions()) {
                if (action != null && action.equalsIgnoreCase("Close")) {
                    return true;
                }
            }
            return false;
        }, false);
    }

    /**
     * Attempts to interact with the curtain (Open or Close) by exact location.
     */
    private boolean interactCurtain(String action) {
        return withTimeout(() -> Rs2GameObject.interact(CURTAIN_POSITION, action), false);
    }

    /**
     * Finds ALL NPCs (any type) currently inside THIS (west) tent - used to
     * detect any occupant, not just the target species, since a stray
     * Villager (or anything else) sitting in the tent blocks Knock-Out just
     * as much as another Menaphite Thug would.
     */
    private List<Rs2NpcModel> npcsInTent() {
        return Microbot.getRs2NpcCache().query()
                .toList().stream()
                .filter(npc -> isInsideWestTent(npc.getWorldLocation()))
                .collect(Collectors.toList());
    }

    /**
     * Finds Menaphite Thugs inside THIS tent specifically.
     */
    private List<Rs2NpcModel> targetNpcsInTent() {
        return Microbot.getRs2NpcCache().query()
                .withName(TARGET_NPC_NAME)
                .toList().stream()
                .filter(npc -> isInsideWestTent(npc.getWorldLocation()))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether ANY other NPC (any type) has line-of-sight to the lured
     * NPC right now - if so, Knock-Out won't work, so we wait instead of
     * attempting it. Deliberately not filtered by species: a Villager or any
     * other NPC watching blocks the mechanic just as much as another Thug.
     */
    private boolean isRoomClear() {
        if (!isLuredNpcValid()) return false;

        WorldPoint myLocation = luredNpc.getWorldLocation();
        WorldView worldView = Microbot.getClient().getTopLevelWorldView();

        if (myLocation == null || worldView == null) return false;

        long visibleOthers = Microbot.getRs2NpcCache().query()
                .within(myLocation, ROOM_CHECK_DISTANCE)
                .toList().stream()
                .filter(npc -> npc.getIndex() != luredNpc.getIndex())
                .filter(npc -> {
                    WorldPoint otherLoc = npc.getWorldLocation();
                    if (otherLoc == null) return false;
                    if (otherLoc.equals(myLocation)) return true;
                    return otherLoc.toWorldArea().hasLineOfSightTo(worldView, myLocation);
                })
                .count();

        return visibleOthers == 0;
    }

    /**
     * Finds the nearest Menaphite Thug that the player can actually reach
     * (has line-of-sight to right now) - filters out NPCs stuck behind a
     * closed curtain/wall.
     */
    private Rs2NpcModel findReachableTarget(String targetName) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        WorldView worldView = Microbot.getClient().getTopLevelWorldView();
        if (myLocation == null || worldView == null) return null;

        return Microbot.getRs2NpcCache().query()
                .withName(targetName)
                .toList().stream()
                .filter(npc -> !isInExcludedSearchZone(npc.getWorldLocation()))
                .filter(npc -> {
                    WorldPoint npcLoc = npc.getWorldLocation();
                    if (npcLoc == null) return false;
                    if (npcLoc.equals(myLocation)) return true;
                    return npcLoc.toWorldArea().hasLineOfSightTo(worldView, myLocation);
                })
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(myLocation)))
                .orElse(null);
    }

    /**
     * Hops to a random world - used when the tent stays blocked too long, the
     * bar's stock is too low, or a lure's NPC never makes it inside in time.
     */
    private void hopWorld() {
        sleep(600, 1000);
        int world = Login.getRandomWorld(Rs2Player.isMember());
        sleepUntil(() -> Microbot.hopToWorld(world), 15000);
    }

    /**
     * Step -1: travel to the fixed starting tile (3345, 2957, 0) from anywhere
     * in Gielinor - uses the full transport-aware webwalker (fairy rings,
     * Shantay Pass, rug merchant, etc.), not just local canvas clicking.
     * Tracks whether we're actually making progress (distance decreasing). If
     * we get stuck with no progress at all for a few seconds - e.g. starting
     * inside a random building with a closed door blocking the only path out
     * - we try opening any nearby door. If we're still stuck after a longer
     * timeout regardless, we just accept "close enough" and continue rather
     * than retrying forever.
     */
    private void handleTravelToStart() {
        int distance = Rs2Player.getWorldLocation().distanceTo(START_POSITION);

        if (distance <= START_ARRIVAL_DISTANCE) {
            setState("Arrived at start position");
            travelToStartStuckSince = -1;
            travelToStartBestDistance = Integer.MAX_VALUE;
            phase = Phase.CHECK_TENT_OCCUPANCY;
            return;
        }

        if (distance < travelToStartBestDistance) {
            // Making real progress - reset the stuck timer.
            travelToStartBestDistance = distance;
            travelToStartStuckSince = -1;
        } else {
            if (travelToStartStuckSince < 0) {
                travelToStartStuckSince = System.currentTimeMillis();
            }
            long stuckFor = System.currentTimeMillis() - travelToStartStuckSince;

            // No progress for a while - maybe a closed door is blocking the way.
            // Try opening any nearby door, but don't spam it every tick.
            if (stuckFor >= DOOR_OPEN_ATTEMPT_INTERVAL_MS
                    && (travelToStartLastDoorAttempt < 0
                    || System.currentTimeMillis() - travelToStartLastDoorAttempt >= DOOR_OPEN_ATTEMPT_INTERVAL_MS)) {
                setState("No progress (distance=" + distance + ") - trying to open a nearby door");
                withTimeout(() -> Rs2GameObject.interact("Door", "Open"), false);
                travelToStartLastDoorAttempt = System.currentTimeMillis();
                sleepTicks(1);
            }

            if (stuckFor >= START_STUCK_TIMEOUT_MS) {
                setState("Stuck for " + (stuckFor / 1000) + "s (distance=" + distance + ") - accepting and continuing");
                travelToStartStuckSince = -1;
                travelToStartBestDistance = Integer.MAX_VALUE;
                phase = Phase.CHECK_TENT_OCCUPANCY;
                return;
            }
        }

        setState("Travelling to start position (distance=" + distance + ")");
        smartTravelTo(START_POSITION);
    }

    /**
     * Step 0: check who's already in the (west) tent before doing anything else.
     * - Tent empty -> fall back to the normal outside-search flow.
     * - Exactly 1 NPC, and it IS a Menaphite Thug -> walk in and lure it.
     * - Anything else (more than 1 NPC, or a non-target NPC like a Villager) ->
     *   can't cleanly proceed, hop to a fresh world and re-check.
     */
    private void handleCheckTentOccupancy() {
        List<Rs2NpcModel> allInTent = npcsInTent();

        if (allInTent.isEmpty()) {
            setState("Tent empty - searching outside");
            needsLureInsideTent = false;
            phase = Phase.FIND_TARGET;
            return;
        }

        List<Rs2NpcModel> targetsInTent = targetNpcsInTent();

        if (allInTent.size() != 1 || targetsInTent.size() != 1) {
            setState("Tent occupied by " + allInTent.size() + " NPC(s), not a clean single target - hopping world");
            hopWorld();
            phase = Phase.TRAVEL_TO_START;
            return;
        }

        luredNpc = targetsInTent.get(0);
        needsLureInsideTent = true;
        setState("1 target NPC already in tent - entering to lure it");
        phase = Phase.WALK_TO_CURTAIN;
    }

    /**
     * Step 1: find the nearest reachable Menaphite Thug OUTSIDE. If nothing is
     * found while we're still standing inside the tent, a closed curtain/wall
     * is almost certainly blocking our line of sight to anything outside - so
     * we open the curtain (if needed) and step out first, rather than
     * searching forever from a spot where we can never actually see a target.
     */
    private void handleFindTarget() {
        Rs2NpcModel target = findReachableTarget(TARGET_NPC_NAME);

        if (target == null) {
            if (isPlayerInsideTent()) {
                if (!isCurtainOpen()) {
                    setState("No thug visible from inside - opening curtain to step out");
                    interactCurtain("Open");
                    sleepTicks(1);
                    return;
                }

                int distance = Rs2Player.getWorldLocation().distanceTo(START_POSITION);
                if (distance > ARRIVAL_DISTANCE) {
                    setState("Stepping outside the tent to search (distance=" + distance + ")");
                    walkTo(START_POSITION);
                    return;
                }
            }

            setState("Searching for " + TARGET_NPC_NAME);
            return;
        }

        luredNpc = target;
        setState("Found " + TARGET_NPC_NAME + " - running to it");
        phase = Phase.RUN_TO_TARGET;
    }

    /**
     * Step 1.5: run (not walk) toward the found NPC until within lure range.
     */
    private void handleRunToTarget() {
        if (!isLuredNpcValid()) {
            setState("Target lost while running to it");
            phase = Phase.FIND_TARGET;
            return;
        }

        WorldPoint npcLocation = luredNpc.getWorldLocation();
        int distance = Rs2Player.getWorldLocation().distanceTo(npcLocation);

        if (distance > ARRIVAL_DISTANCE) {
            setState("Running to " + TARGET_NPC_NAME + " (distance=" + distance + ")");
            runTo(npcLocation);
            return;
        }

        setState("Reached NPC - luring");
        phase = Phase.LURING;
    }

    /**
     * Step 2: lure the NPC. Re-fetches a fresh NPC reference on every attempt.
     * A real successful lure means the NPC is actively following: interacting,
     * close by, and not further away than before - a rejected "Go away! I'm
     * busy" briefly flips isInteractingWithPlayer() too, so that flag alone
     * isn't reliable; we require the NPC to also actually stay close. Once
     * confirmed, running is turned off - from here on we walk, so the NPC
     * can keep up.
     */
    private void handleLuring() {
        for (int attempt = 1; attempt <= LURE_MAX_ATTEMPTS; attempt++) {
            Rs2NpcModel target = needsLureInsideTent
                    ? luredNpc
                    : findReachableTarget(TARGET_NPC_NAME);

            if (target == null || target.getNpc() == null) {
                setState("Lure target lost, re-checking");
                luredNpc = null;
                phase = Phase.CHECK_TENT_OCCUPANCY;
                return;
            }

            luredNpc = target;
            setState("Luring (attempt " + attempt + "/" + LURE_MAX_ATTEMPTS + ")");
            luredNpc.click("Lure");

            if (!Rs2Dialogue.sleepUntilInDialogue()) {
                setState("Lure: no dialogue opened, retrying");
                sleep(400, 700);
                continue;
            }

            int safetyCounter = 0;
            while (Rs2Dialogue.isInDialogue() && safetyCounter < 5) {
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                }
                sleep(300, 500);
                safetyCounter++;
            }

            sleepTicks(1); // let the interaction state settle right after dialogue closes

            boolean lureSucceeded = true;
            for (int check = 0; check < 3; check++) {
                sleepTicks(1);
                boolean valid = isLuredNpcValid();
                boolean interacting = valid && luredNpc.isInteractingWithPlayer();
                int dist = valid ? luredNpc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) : Integer.MAX_VALUE;

                if (!interacting || dist > 3) {
                    lureSucceeded = false;
                    break;
                }
            }

            if (lureSucceeded) {
                setState("Lure confirmed (NPC following for 3 ticks) - walking from here");
                Rs2Player.toggleRunEnergy(false);
                if (needsLureInsideTent || (isLuredNpcInsideTent() && isPlayerInsideTent())) {
                    needsLureInsideTent = false;
                    phase = Phase.WALK_TO_CLOSE_POSITION;
                } else {
                    phase = Phase.WALK_TO_CURTAIN;
                }
                return;
            }

            setState("Lure rejected, retrying");
            sleep(400, 800);
        }

        setState("Lure failed after " + LURE_MAX_ATTEMPTS + " attempts");
        luredNpc = null;
        needsLureInsideTent = false;
        phase = Phase.CHECK_TENT_OCCUPANCY;
    }

    /**
     * Step 3: walk (NOT run) to the curtain tile - the lured NPC follows
     * behind and will lose you if you run.
     */
    private void handleWalkToCurtain() {
        if (!isLuredNpcValid()) {
            setState("Lure target lost before walking");
            phase = Phase.CHECK_TENT_OCCUPANCY;
            return;
        }

        int distance = Rs2Player.getWorldLocation().distanceTo(CURTAIN_POSITION);
        if (distance > ARRIVAL_DISTANCE) {
            setState("Walking to curtain (distance=" + distance + ")");
            walkWaitingForNpc(CURTAIN_POSITION);
            return;
        }

        phase = Phase.OPEN_CURTAIN;
    }

    /**
     * Step 4: open the curtain - but only if it's actually closed.
     */
    private void handleOpenCurtain() {
        if (isCurtainOpen()) {
            setState("Curtain already open, continuing");
            phase = Phase.WALK_DEEPER;
            return;
        }

        setState("Opening curtain");
        interactCurtain("Open");
        sleepTicks(1);
        phase = Phase.WALK_DEEPER;
    }

    /**
     * Step 5: walk deep into the tent. If we're here because an NPC was
     * already inside (needsLureInsideTent), we now lure it since we're
     * finally close enough. Otherwise (NPC following us from outside), we're
     * already lured and just continue to the close-curtain position.
     */
    private void handleWalkDeeper() {
        int distance = Rs2Player.getWorldLocation().distanceTo(TENT_DEEP_POSITION);
        if (distance > ARRIVAL_DISTANCE) {
            setState("Walking deeper into tent (distance=" + distance + ")");
            walkWaitingForNpc(TENT_DEEP_POSITION);
            return;
        }

        if (needsLureInsideTent) {
            setState("Inside tent - now luring the NPC that was already here");
            phase = Phase.LURING;
            return;
        }

        phase = Phase.WALK_TO_CLOSE_POSITION;
    }

    /**
     * Step 6: walk back to a spot near the curtain (from the inside).
     */
    private void handleWalkToClosePosition() {
        int distance = Rs2Player.getWorldLocation().distanceTo(CURTAIN_CLOSE_POSITION);
        if (distance > ARRIVAL_DISTANCE) {
            setState("Walking to close curtain (distance=" + distance + ")");
            walkWaitingForNpc(CURTAIN_CLOSE_POSITION);
            return;
        }

        phase = Phase.CLOSE_CURTAIN;
    }

    /**
     * Step 7: close the curtain - but only once the NPC actually made it
     * inside (with a 10s timeout - if it never arrives, hop worlds and
     * restart the whole script from the beginning), and only if it's
     * actually still open.
     */
    private void handleCloseCurtain() {
        if (!isLuredNpcValid()) {
            setState("Lured NPC lost - abandoning");
            luredNpc = null;
            npcEnterTentWaitSince = -1;
            phase = Phase.CHECK_TENT_OCCUPANCY;
            return;
        }

        if (!isLuredNpcInsideTent()) {
            if (npcEnterTentWaitSince < 0) {
                npcEnterTentWaitSince = System.currentTimeMillis();
            }
            long waitedFor = System.currentTimeMillis() - npcEnterTentWaitSince;

            if (waitedFor >= NPC_ENTER_TENT_TIMEOUT_MS) {
                setState("NPC never entered tent (" + (waitedFor / 1000) + "s) - hopping world, restarting");
                hopWorld();
                npcEnterTentWaitSince = -1;
                luredNpc = null;
                needsLureInsideTent = false;
                phase = Phase.TRAVEL_TO_START;
                return;
            }

            setState("Waiting for NPC to enter tent (" + (waitedFor / 1000) + "s)");
            return;
        }
        npcEnterTentWaitSince = -1;

        if (!isCurtainOpen()) {
            setState("Curtain already closed, continuing");
            phase = Phase.KNOCK_OUT;
            return;
        }

        setState("Closing curtain");
        interactCurtain("Close");
        sleepTicks(1);
        phase = Phase.KNOCK_OUT;
    }

    /**
     * Step 8: the knock-out action.
     * - If another NPC (any type) has line-of-sight, wait - but if that's
     *   been going on too long, give up and hop worlds.
     * - If you're currently stunned, spam Knock-Out to interrupt the attack.
     * - Otherwise, click Knock-Out and poll for a fast, definitive signal via
     *   the NPC's own animation.
     */
    private void handleKnockOut() {
        if (!isLuredNpcValid()) {
            setState("NPC no longer valid before knock-out");
            luredNpc = null;
            phase = Phase.CHECK_TENT_OCCUPANCY;
            return;
        }

        if (!isRoomClear()) {
            if (roomBlockedSince < 0) {
                roomBlockedSince = System.currentTimeMillis();
            }
            long blockedFor = System.currentTimeMillis() - roomBlockedSince;

            if (blockedFor >= ROOM_BLOCKED_HOP_THRESHOLD_MS) {
                setState("Tent occupied too long (" + (blockedFor / 1000) + "s) - hopping world");
                hopWorld();
                roomBlockedSince = -1;
                luredNpc = null;
                phase = Phase.TRAVEL_TO_START;
                return;
            }

            setState("Waiting: another NPC has line-of-sight (" + (blockedFor / 1000) + "s)");
            return;
        }
        roomBlockedSince = -1;

        if (Rs2Player.isStunned()) {
            setState("Stunned - spamming Knock-Out to interrupt attack");
            luredNpc.click("Knock-Out");
            sleepTicks(1);
            return;
        }

        setState("Knocking out");
        knockoutAttempts++;
        boolean clicked = luredNpc.click("Knock-Out");

        if (!clicked) {
            setState("Knock-out click failed, retrying");
            sleepTicks(1);
            return;
        }

        long deadline = System.currentTimeMillis() + KNOCKOUT_POLL_WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!isLuredNpcValid()) {
                setState("NPC lost mid knock-out");
                luredNpc = null;
                phase = Phase.CHECK_TENT_OCCUPANCY;
                return;
            }

            if (wasRecentlyCombatBlocked()) {
                setState("Combat-blocked message received mid knock-out");
                phase = Phase.COMBAT_ESCAPE;
                return;
            }

            int npcAnim = luredNpc.getAnimation();

            if (npcAnim == NPC_ANIM_KNOCKED_OUT) {
                setState("Knock-out confirmed (npc anim 838) - pickpocketing immediately");
                handlePickpocketBurst();
                return;
            }

            if (npcAnim == NPC_ANIM_ATTACKING || Rs2Player.isStunned()) {
                setState("Knock-out failed - NPC retaliating, interrupting");
                luredNpc.click("Knock-Out");
                sleepTicks(1);
                return;
            }

            sleep(KNOCKOUT_POLL_INTERVAL_MS, KNOCKOUT_POLL_INTERVAL_MS + 50);
        }

        setState("Knock-out result unclear, re-checking next tick");
    }

    /**
     * Step 9: right after a successful knock-out, the NPC is briefly
     * unconscious - fire both pickpocket clicks spaced 2 game ticks apart.
     */
    private void handlePickpocketBurst() {
        setState("Pickpocketing (2-tick spaced)");
        for (int i = 0; i < PICKPOCKET_BURST_ATTEMPTS; i++) {
            if (!isLuredNpcValid()) break;

            luredNpc.click("Pickpocket");
            pickpocketAttempts++;
            sleepTicks(2);
        }

        phase = Phase.KNOCK_OUT;
    }

    /**
     * Combat escape: triggered specifically by the exact "You can't do this
     * during combat." chat message. Unequip the blackjack, attempt Knock-Out
     * anyway (breaks the NPC out of combat even unarmed), wait for combat to
     * clear, then re-equip and resume on the SAME lured NPC.
     */
    private void handleCombatEscape() {
        if (!isLuredNpcValid()) {
            setState("Combat escape: NPC lost, restarting cycle");
            luredNpc = null;
            combatBlockedMessageTime = -1;
            phase = Phase.CHECK_TENT_OCCUPANCY;
            return;
        }

        setState("Combat escape: unequipping blackjack");
        Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
        sleepTicks(1);

        setState("Combat escape: attempting Knock-Out unarmed to break combat");
        luredNpc.click("Knock-Out");
        sleepTicks(1);

        setState("Combat escape: waiting for combat to clear");
        boolean cleared = sleepUntil(() -> !Rs2Player.isInCombat(), COMBAT_ESCAPE_WAIT_MS);

        if (!cleared) {
            setState("Combat escape: still in combat, retrying next tick");
            return;
        }

        String blackjackName = config.blackjackType().getItemName();
        setState("Combat escape: re-equipping blackjack");
        if (Rs2Inventory.hasItem(blackjackName)) {
            Rs2Inventory.wield(blackjackName);
            sleepTicks(1);
        }

        combatBlockedMessageTime = -1;
        setState("Combat escape complete - resuming Knock-Out on same NPC");
        phase = Phase.KNOCK_OUT;
    }

    /**
     * Restock trip: open the curtain (only if closed), run to the bar, drop
     * empty Jugs, buy 13 Jug of wine (hopping worlds first if stock is too
     * low), then travel straight back to START_POSITION and restart the
     * whole script cycle from there - no need to walk back and close the
     * curtain here, since the normal cycle handles the curtain again anyway
     * once a fresh NPC gets lured.
     */
    private void handleRestockFlow() {
        switch (phase) {
            case RESTOCK_OPEN_CURTAIN: {
                if (isCurtainOpen()) {
                    restockCurtainStuckSince = -1;
                    setState("Restock: curtain already open, continuing");
                    phase = Phase.RESTOCK_WALK_TO_BAR;
                    return;
                }

                int distance = Rs2Player.getWorldLocation().distanceTo(CURTAIN_POSITION);
                if (distance > ARRIVAL_DISTANCE) {
                    restockCurtainStuckSince = -1; // still travelling, not stuck
                    setState("Restock: running to curtain to open it (distance=" + distance + ")");
                    runTo(CURTAIN_POSITION);
                    return;
                }

                if (restockCurtainStuckSince < 0) {
                    restockCurtainStuckSince = System.currentTimeMillis();
                }
                long stuckFor = System.currentTimeMillis() - restockCurtainStuckSince;
                if (stuckFor >= CURTAIN_STUCK_TIMEOUT_MS) {
                    setState("Restock: stuck opening curtain for " + (stuckFor / 1000) + "s - hopping world");
                    hopWorld();
                    restockCurtainStuckSince = -1;
                    return;
                }

                setState("Restock: opening curtain");
                interactCurtain("Open");
                sleepTicks(1);

                if (isCurtainOpen()) {
                    restockCurtainStuckSince = -1;
                    phase = Phase.RESTOCK_WALK_TO_BAR;
                } else {
                    setState("Restock: curtain open failed, retrying");
                }
                break;
            }

            case RESTOCK_WALK_TO_BAR: {
                int distance = Rs2Player.getWorldLocation().distanceTo(BAR_POSITION);
                if (distance > ARRIVAL_DISTANCE) {
                    setState("Restock: running to bar (distance=" + distance + ")");
                    runTo(BAR_POSITION);
                    return;
                }
                phase = Phase.RESTOCK_BUY;
                break;
            }

            case RESTOCK_BUY:
                setState("Restock: dropping empty jugs");
                Rs2Inventory.dropAll(true, EMPTY_JUG_NAME);
                sleepTicks(1);

                if (!Rs2Shop.isOpen()) {
                    Rs2Shop.openShop(BAR_NPC_NAME);
                    sleepTicks(1);
                }

                if (!Rs2Shop.isOpen()) {
                    setState("Restock: shop failed to open, retrying");
                    return;
                }

                if (!Rs2Shop.hasMinimumStock(FOOD_NAME, MIN_WINE_STOCK_REQUIRED)) {
                    setState("Restock: bar has less than " + MIN_WINE_STOCK_REQUIRED
                            + "x " + FOOD_NAME + " - hopping world for fresh stock");
                    Rs2Shop.closeShop();
                    hopWorld();
                    return;
                }

                setState("Restock: buying " + WINE_TO_BUY + "x " + FOOD_NAME);
                Rs2Shop.buyItemOptimally(FOOD_NAME, WINE_TO_BUY);
                sleepTicks(1);
                Rs2Shop.closeShop();
                restockRoundsCompleted++;

                // Faisal only ever has WINE_TO_BUY in stock at once - if we still have
                // inventory room and haven't hit our round cap, hop to a fresh world
                // (instant full restock) and buy another round instead of heading back
                // with a half-empty trip. This roughly doubles how long we can go
                // between restock trips.
                if (restockRoundsCompleted < MAX_RESTOCK_ROUNDS && Rs2Inventory.getEmptySlots() >= WINE_TO_BUY) {
                    setState("Restock: round " + restockRoundsCompleted + "/" + MAX_RESTOCK_ROUNDS
                            + " done, hopping for another full stock");
                    hopWorld();
                    return; // re-enters RESTOCK_BUY on the new world next tick
                }

                setState("Restock complete (" + restockRoundsCompleted + " round(s)) - returning to start");
                restockRoundsCompleted = 0;
                luredNpc = null;
                needsLureInsideTent = false;
                phase = Phase.TRAVEL_TO_START;
                break;
        }
    }

    public int getKnockoutAttempts() {
        return knockoutAttempts;
    }

    public int getPickpocketAttempts() {
        return pickpocketAttempts;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}