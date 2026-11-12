package net.runelite.client.plugins.microbot.droelf;

import com.google.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DroElfScript extends Script {
    private static final int BANK_CLICK_RANGE = 15;
    private static final int ROUTE_STEP_MIN = 7;
    private static final int ROUTE_STEP_MAX = 11;
    private static final int MAX_COIN_POUCHES = 25;
    private static final int ELF_DETECTION_RADIUS = 26;
    private static final long ELF_REACQUIRE_TIMEOUT_MS = 6_000L;
    private static final long ELF_AREA_INSURANCE_INTERVAL_MS = 20_000L;
    private static final int ELF_AREA_INSURANCE_RADIUS = 6;
    private static final long ELF_REACHABILITY_GRACE_MS = 2_000L;
    private static final int MAX_ELF_LOCK_RECOVERY_FAILURES = 2;
    private static final long ELF_LOCK_RECOVERY_RETRY_DELAY_MS = 2_000L;
    private static final long MIN_WORLD_HOP_INTERVAL_MS = 45_000L;
    private static final long CANT_REACH_STREAK_RESET_MS = 30_000L;
    private static final long MIN_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(25);
    private static final long MIN_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long MAX_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(7);
    private static final Set<String> ROUTE_DOOR_ACTIONS = Set.of("Open", "Close");

    private final DroElfConfig config;
    private final DroElfAdaptiveBehavior adaptiveBehavior;
    private final DroElfPrifWalker prifWalker = new DroElfPrifWalker();
    private DroElfBreakManager breakManager;

    @Getter
    private volatile String status = "Starting";
    private volatile Rs2NpcModel elf;
    private String lockedElfName;
    private int lockedElfId = -1;
    private int lockedElfIndex = -1;
    private WorldPoint lockedElfStartTile;
    private WorldPoint lastKnownElfTile;
    private WorldPoint elfAnchor;
    private BankLocation lockedBank;
    private List<WorldPoint> routeToBank = Collections.emptyList();
    private WorldPoint routeDoor;
    private int doorClickRange;
    private long nextShadowVeilAt;
    private long shadowVeilForcedActiveUntil;
    private long nextRestAt;
    private long restUntil;
    private long nextMisclickAt;
    private long nextShortDelayAt;
    private SessionPersonality personality = SessionPersonality.neutral();
    private AfkParkSide sessionParkSide = AfkParkSide.BOTTOM;
    private int offScreenParksSinceInventoryGlance;
    private int nextInventoryGlanceAfterParks = 10;
    private long elfMissingSince;
    private long elfUnreachableSince;
    private long lastElfReachabilityCheckAt;
    private long nextElfAreaInsuranceCheckAt;
    private volatile boolean elfLockRecoveryRequested;
    private volatile boolean elfLockRecoveryInProgress;
    private volatile int cantReachMessagesSinceRecovery;
    private int confirmedCantReachRecoveryCycles;
    private int elfLockRecoveryFailures;
    private long lastCantReachMessageAt;
    private long lastWorldHopAt;
    private long nextElfLockRecoveryAt;
    private Instant startedAt;

    @Inject
    public DroElfScript(DroElfConfig config) {
        this.config = config;
        this.adaptiveBehavior = new DroElfAdaptiveBehavior(config);
    }

    public boolean run() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) return true;

        // MKE-derived native profile, adapted to General Thieving. This also
        // owns the native Natural Mouse speed setting (MODERATE).
        adaptiveBehavior.configureNativeAntiban();
        breakManager = new DroElfBreakManager(config);

        resetSession();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                loop();
            } catch (Exception ex) {
                log.error("DroElf loop failed", ex);
                status = "Recovering";
                elf = null;
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void resetSession() {
        lockedElfName = null;
        lockedElfId = -1;
        lockedElfIndex = -1;
        lockedElfStartTile = null;
        lastKnownElfTile = null;
        elfAnchor = null;
        lockedBank = null;
        routeToBank = Collections.emptyList();
        routeDoor = null;
        elf = null;
        doorClickRange = randomBetween(5, 10);
        nextShadowVeilAt = System.currentTimeMillis();
        shadowVeilForcedActiveUntil = 0L;
        final long now = System.currentTimeMillis();
        nextRestAt = now + randomLong(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
        nextMisclickAt = now + randomLong(TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(15));
        nextShortDelayAt = now + randomLong(TimeUnit.MINUTES.toMillis(3), TimeUnit.MINUTES.toMillis(4));
        personality = SessionPersonality.roll();
        sessionParkSide = AfkParkSide.randomEdge();
        offScreenParksSinceInventoryGlance = 0;
        nextInventoryGlanceAfterParks = randomBetween(7, 14);
        elfMissingSince = 0L;
        elfUnreachableSince = 0L;
        lastElfReachabilityCheckAt = 0L;
        nextElfAreaInsuranceCheckAt = now + ELF_AREA_INSURANCE_INTERVAL_MS;
        elfLockRecoveryRequested = false;
        elfLockRecoveryInProgress = false;
        cantReachMessagesSinceRecovery = 0;
        confirmedCantReachRecoveryCycles = 0;
        elfLockRecoveryFailures = 0;
        lastCantReachMessageAt = 0L;
        lastWorldHopAt = 0L;
        nextElfLockRecoveryAt = 0L;
        restUntil = 0L;
        adaptiveBehavior.resetSession();
        startedAt = Instant.now();
        status = "Finding starting elf";
    }

    private void loop() {
        if (!isRunning()) return;

        // Update the custom break manager before the login guard. Logout breaks
        // must be able to expire and log back in while the character is offline.
        if (breakManager != null && breakManager.update(isSafeForBreak())) {
            status = breakManager.getStatus();
            return;
        }

        if (!Microbot.isLoggedIn() || Rs2Player.getWorldLocation() == null) return;

        if (handleNativePauseAndBreakState()) return;

        if (lockedElfName == null) {
            initializeSession();
            return;
        }

        if (handleScheduledRest()) return;

        if (needsBank()) {
            bankAndRestock();
            return;
        }

        if (Rs2Inventory.emptySlotCount() < 2) {
            handleFullInventory();
            if (Rs2Inventory.emptySlotCount() < 2) bankAndRestock();
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleep(50, 250);
        }

        if (handleElfAreaInsurance()) return;

        if (Rs2Player.getHealthPercentage() <= config.eatBelow()) {
            // HP floor: eat when there is food, otherwise stop instead of thieving on.
            // With "Use food" on and no food left, needsBank() above already sends the
            // player to the bank, and an empty bank stops the plugin there.
            if (config.useFood() && !Rs2Inventory.getInventoryFood().isEmpty()) {
                status = "Eating";
                pauseBeforeAction();
                Rs2Player.useFood();
                sleep(250, 500);
                return;
            }
            // Ride out a one-off stale HP read before stopping.
            if (sleepUntil(() -> Rs2Player.getHealthPercentage() > config.eatBelow(), 1_200)) return;
            stopWithMessage("HP is at or below " + config.eatBelow() + "% with nothing to heal"
                    + (config.useFood() ? "." : " (\"Use food\" is off)."));
            return;
        }

        if (Rs2Inventory.hasItemAmount("coin pouch", MAX_COIN_POUCHES, true)) {
            status = "Opening coin pouches";
            pauseBeforeAction();
            Rs2Inventory.interact("coin pouch", "Open-all");
            sleep(100, 300);
            return;
        }

        if (config.shadowVeil() && shouldCastShadowVeil()) {
            status = "Casting Shadow Veil";
            pauseBeforeAction();

            boolean cast = false;
            try {
                cast = Rs2Magic.canCast(MagicAction.SHADOW_VEIL)
                        && Rs2Magic.cast(MagicAction.SHADOW_VEIL);
            } catch (Exception ex) {
                // The Magic utility can briefly have no spell widget after login/tab changes.
                // A failed widget lookup must never stop the thieving loop.
                log.debug("[DroElf] Shadow Veil widget unavailable; retrying later", ex);
            }

            if (cast) {
                nextShadowVeilAt = System.currentTimeMillis() + 60_000L;
                sleepUntil(() -> Rs2Magic.isShadowVeilActive()
                        || shadowVeilForcedActiveUntil > System.currentTimeMillis(), 2_400);
                return;
            }

            // Avoid hammering a missing/unavailable spell widget every 300 ms.
            nextShadowVeilAt = System.currentTimeMillis() + 5_000L;
        }

        if (!Rs2Equipment.isWearing("dodgy necklace") && Rs2Inventory.hasItem("dodgy necklace")) {
            status = "Equipping necklace";
            pauseBeforeAction();
            Rs2Inventory.wield("dodgy necklace");
            sleepUntil(() -> Rs2Equipment.isWearing("dodgy necklace"), 1_500);
            return;
        }

        for (String piece : ThievingData.ROGUE_SET) {
            if (Rs2Inventory.hasItem(piece) && !Rs2Equipment.isWearing(piece)) {
                status = "Equipping rogue outfit";
                pauseBeforeAction();
                Rs2Inventory.wear(piece);
                sleep(100, 300);
                return;
            }
        }

        elf = findLockedElf();
        if (elf == null || elf.getNpc() == null) {
            // A recovery request can survive a world hop/scene rebuild before the NPC
            // has repopulated in cache. In that case, return to the original locked
            // area first; do not invent a replacement elf.
            if (elfLockRecoveryRequested && System.currentTimeMillis() >= nextElfLockRecoveryAt) {
                handleElfLockRecovery(null);
                return;
            }
            handleMissingElf();
            return;
        }
        final long missingForMs = elfMissingSince == 0L
                ? 0L
                : System.currentTimeMillis() - elfMissingSince;
        rememberLockedElf(elf);
        if (missingForMs > 0L) {
            log.info("[DroElf] Reacquired locked elf={} id={} index={} at={} after={}ms",
                    lockedElfName, lockedElfId, lockedElfIndex, lastKnownElfTile, missingForMs);
        }
        elfMissingSince = 0L;

        // A single old reachability incident should not remain "hot" forever. If the
        // game has gone quiet for 30 seconds, treat later failures as a fresh incident.
        if (confirmedCantReachRecoveryCycles > 0
                && lastCantReachMessageAt > 0L
                && System.currentTimeMillis() - lastCantReachMessageAt >= CANT_REACH_STREAK_RESET_MS) {
            confirmedCantReachRecoveryCycles = 0;
            cantReachMessagesSinceRecovery = 0;
        }

        // Keep identity separate from reachability. The exact ID/name lock remains
        // authoritative, but never knowingly issue Pickpocket through a closed door
        // or sealed wall. Reachability means "can I path to at least one adjacent tile
        // that can actually interact across collision?" -- never "can I stand on the
        // NPC's occupied tile." Recovery pathing owns any required door opening.
        final long reachabilityCheckAt = System.currentTimeMillis();
        final boolean reachable = isLockedElfReachable(elf);
        // A pause, bank trip, or scene transition must not age an old failed sample
        // into an immediate recovery. Normal failed checks repeat every 300 ms.
        if (reachable || reachabilityCheckAt - lastElfReachabilityCheckAt > ELF_REACHABILITY_GRACE_MS) {
            elfUnreachableSince = 0L;
        }
        lastElfReachabilityCheckAt = reachabilityCheckAt;
        if (elfLockRecoveryRequested || !reachable) {
            if (!elfLockRecoveryRequested) {
                if (elfUnreachableSince == 0L) elfUnreachableSince = reachabilityCheckAt;
                if (reachabilityCheckAt - elfUnreachableSince < ELF_REACHABILITY_GRACE_MS) {
                    status = "Checking locked elf reachability";
                    return;
                }
                requestElfLockRecovery("live collision says locked elf is unreachable for 2 seconds");
            }
            elfUnreachableSince = 0L;
            if (System.currentTimeMillis() >= nextElfLockRecoveryAt) {
                handleElfLockRecovery(elf);
            }
            return;
        }

        if (handlePickpocketAntiban()) return;

        status = "Pickpocketing " + lockedElfName;
        adaptiveBehavior.maybePreClickPause(personality);
        pauseBeforeAction();
        boolean clicked = elf.click("Pickpocket");
        if (clicked) {
            boolean mouseMoved = handlePickpocketMouseBehavior();
            adaptiveBehavior.afterSuccessfulClick(personality, mouseMoved);
        }
        sleep(250, 450);
        if (Rs2Player.isStunned()) sleepUntil(() -> !Rs2Player.isStunned(), 8_000);
    }

    /**
     * Low-frequency insurance check for rare positional drift around the locked elf area.
     * Normal thieving is untouched. Every 20 seconds, when the script is otherwise idle
     * enough to inspect position, verify that we are still close to the original session
     * anchor or beside the reachable locked elf. Otherwise, route from the CURRENT
     * tile back to that anchor. The self-contained
     * Prif walker owns collision replanning and door opening during that recovery.
     */
    private boolean handleElfAreaInsurance() {
        final long now = System.currentTimeMillis();
        if (now < nextElfAreaInsuranceCheckAt) return false;
        nextElfAreaInsuranceCheckAt = now + ELF_AREA_INSURANCE_INTERVAL_MS;

        final WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || elfAnchor == null || lockedElfName == null) return false;
        if (player.getPlane() != elfAnchor.getPlane()) return false;

        // Insurance only: never interrupt a bank interaction, an existing movement,
        // or an active animation. Those states get another check on the next interval.
        if (Rs2Bank.isOpen() || Rs2Player.isMoving() || Rs2Player.isAnimating()) return false;

        final int distance = player.distanceTo(elfAnchor);
        if (distance <= ELF_AREA_INSURANCE_RADIUS) return false;

        // Following the exact locked elf is normal thieving, even outside the
        // starting radius. Re-query its identity rather than trusting a cached actor.
        final Rs2NpcModel nearbyElf = findLockedElf();
        if (nearbyElf != null
                && distanceOrMax(player, nearbyElf.getWorldLocation()) <= 1
                && isLockedElfReachable(nearbyElf)) {
            return false;
        }

        log.warn("[DroElf] Elf-area insurance: {} tiles from locked anchor {}. Returning from {}.",
                distance, elfAnchor, player);
        status = "Insurance return to " + lockedElfName;
        followLockedRoute(false);
        return true;
    }

    private boolean isLockedElfReachable(Rs2NpcModel candidate) {
        if (candidate == null || candidate.getNpc() == null) return false;
        final WorldPoint player = Rs2Player.getWorldLocation();
        final WorldPoint target = candidate.getWorldLocation();
        if (player == null || target == null || player.getPlane() != target.getPlane()) return false;

        try {
            // Do NOT test the NPC's occupied tile. NPC tiles are commonly non-walkable,
            // which caused false "unreachable" results while standing right beside an
            // elf. Instead, require at least one surrounding tile that is both reachable
            // from the player and locally interactable with the NPC without a wall/closed
            // door between the two tiles.
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (Microbot.getClient() == null || Microbot.getClient().getTopLevelWorldView() == null) {
                    return false;
                }

                final Rs2WorldArea npcArea = new Rs2WorldArea(target.toWorldArea());
                final net.runelite.api.WorldView worldView = Microbot.getClient().getTopLevelWorldView();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;

                        final WorldPoint interactTile = new WorldPoint(
                                target.getX() + dx,
                                target.getY() + dy,
                                target.getPlane());

                        if (!Rs2Tile.isTileReachable(interactTile)) continue;

                        final Rs2WorldArea interactArea = new Rs2WorldArea(interactTile.toWorldArea());
                        if (npcArea.canMelee(worldView, interactArea)) {
                            return true;
                        }
                    }
                }
                return false;
            }).orElse(false);
        } catch (Exception ex) {
            // Reachability is a safety gate. If live collision cannot be read, do not
            // fall through to a blind NPC click; let the locked-route recovery retry.
            log.debug("[DroElf] Locked-elf interaction reachability check unavailable for {} at {}",
                    lockedElfName, target, ex);
            return false;
        }
    }

    private void requestElfLockRecovery(String reason) {
        if (!elfLockRecoveryRequested) {
            log.warn("[DroElf] Requesting locked-elf recovery: {}. elf={} id={} index={} lastKnown={} player={} anchor={}",
                    reason, lockedElfName, lockedElfId, lockedElfIndex, lastKnownElfTile,
                    Rs2Player.getWorldLocation(), elfAnchor);
        }
        elfLockRecoveryRequested = true;
        nextElfLockRecoveryAt = Math.min(
                nextElfLockRecoveryAt <= 0L ? Long.MAX_VALUE : nextElfLockRecoveryAt,
                System.currentTimeMillis());
    }

    /**
     * Recovery is deliberately isolated from normal thieving. It only runs after a
     * live reachability failure or an explicit "I can't reach that!" game message.
     * The target remains the exact locked elf ID/name. If that actor is present, walk
     * toward its current tile (stopping adjacent); otherwise return to the original
     * session anchor. The self-contained Prif walker opens only a closed door lying on
     * the recovery path.
     */
    private boolean handleElfLockRecovery(Rs2NpcModel candidate) {
        if (!elfLockRecoveryRequested || System.currentTimeMillis() < nextElfLockRecoveryAt) return false;

        final WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || elfAnchor == null) return false;

        final WorldPoint currentElfTile = candidate == null ? null : candidate.getWorldLocation();
        final WorldPoint recoveryTarget = currentElfTile != null ? currentElfTile : elfAnchor;
        final int arrivalDistance = currentElfTile != null ? 1 : 0;

        status = "Recovering locked " + lockedElfName;
        log.warn("[DroElf] Locked-elf recovery start failureCount={} confirmedChatCycles={} chatCantReach={} player={} target={} anchor={}",
                elfLockRecoveryFailures, confirmedCantReachRecoveryCycles,
                cantReachMessagesSinceRecovery, player, recoveryTarget, elfAnchor);

        // Consume this request. Chat messages that arrive while recovery is physically
        // running are recorded as diagnostics, but they do not create a second confirmed
        // recovery cycle until this one has completed.
        elfLockRecoveryRequested = false;
        elfLockRecoveryInProgress = true;

        boolean routed = false;
        if (prifWalker.supports(player) && prifWalker.supports(recoveryTarget)) {
            routed = prifWalker.walkTo(
                    recoveryTarget,
                    this::isRunning,
                    this::pauseBeforeAction,
                    value -> status = value,
                    arrivalDistance);
        }

        if (!routed && recoveryTarget.equals(elfAnchor)) {
            // Preserve the already-proven legacy fallback only for the original anchor.
            routed = followLockedRoute(false);
        }

        final Rs2NpcModel reacquired = findLockedElf();
        if (reacquired != null && reacquired.getNpc() != null) {
            elf = reacquired;
            rememberLockedElf(reacquired);
        }

        // If the NPC has not repopulated yet (common directly after a world hop), the
        // route back to the locked area is enough for this pass. Exact-ID reacquisition
        // continues normally on later loops; do not count this as a failed recovery.
        if (reacquired == null || reacquired.getNpc() == null) {
            elfLockRecoveryInProgress = false;
            cantReachMessagesSinceRecovery = 0;
            nextElfLockRecoveryAt = System.currentTimeMillis() + ELF_LOCK_RECOVERY_RETRY_DELAY_MS;
            status = routed ? "Reacquiring locked " + lockedElfName : "Returning to locked " + lockedElfName;
            return true;
        }

        final boolean reachable = isLockedElfReachable(reacquired);
        elfLockRecoveryInProgress = false;

        // Final live state wins. A stale "I can't reach that!" that arrived while the
        // walker was moving must never turn a successfully recovered position into a
        // failure. Clear the request and resume normally.
        if (reachable) {
            log.info("[DroElf] Locked-elf recovery succeeded. elf={} id={} index={} player={} elfTile={} confirmedChatCycles={}",
                    lockedElfName, lockedElfId, lockedElfIndex,
                    Rs2Player.getWorldLocation(), reacquired.getWorldLocation(),
                    confirmedCantReachRecoveryCycles);
            elfLockRecoveryRequested = false;
            elfLockRecoveryFailures = 0;
            cantReachMessagesSinceRecovery = 0;
            nextElfLockRecoveryAt = 0L;
            status = "Locked elf reachable";
            return true;
        }

        elfLockRecoveryFailures++;
        log.warn("[DroElf] Locked-elf recovery failed {}/{}. routed={} reachable={} confirmedChatCycles={} player={} elfTile={}",
                elfLockRecoveryFailures, MAX_ELF_LOCK_RECOVERY_FAILURES,
                routed, reachable, confirmedCantReachRecoveryCycles,
                Rs2Player.getWorldLocation(), reacquired.getWorldLocation());

        // Collision-only failures may retry indefinitely; they are not enough to hop.
        // Hopping is reserved for repeated, game-confirmed reach failures separated by
        // completed recovery attempts. This prevents a bad collision reading from
        // causing rapid world chains.
        final boolean gameConfirmedEnough = confirmedCantReachRecoveryCycles >= MAX_ELF_LOCK_RECOVERY_FAILURES;
        final boolean hopCooldownReady = lastWorldHopAt <= 0L
                || System.currentTimeMillis() - lastWorldHopAt >= MIN_WORLD_HOP_INTERVAL_MS;
        if (gameConfirmedEnough
                && elfLockRecoveryFailures >= MAX_ELF_LOCK_RECOVERY_FAILURES
                && hopCooldownReady) {
            return hopWorldForLockedElfRecovery();
        }

        if (gameConfirmedEnough && !hopCooldownReady) {
            final long waitMs = MIN_WORLD_HOP_INTERVAL_MS
                    - (System.currentTimeMillis() - lastWorldHopAt);
            log.warn("[DroElf] Hop escalation suppressed for {}ms by cooldown; retrying locked route.",
                    Math.max(0L, waitMs));
        }

        elfLockRecoveryRequested = true;
        nextElfLockRecoveryAt = System.currentTimeMillis() + ELF_LOCK_RECOVERY_RETRY_DELAY_MS;
        status = "Retrying locked-elf route";
        return true;
    }

    private boolean hopWorldForLockedElfRecovery() {
        if (!Microbot.isLoggedIn()) return false;

        final int currentWorld = Rs2Player.getWorld();
        int targetWorld = -1;
        for (int attempt = 0; attempt < 6; attempt++) {
            final int candidateWorld = LoginManager.getRandomWorld(Rs2Player.isMember());
            if (candidateWorld > 0 && candidateWorld != currentWorld) {
                targetWorld = candidateWorld;
                break;
            }
        }

        if (targetWorld <= 0 || targetWorld == currentWorld) {
            log.warn("[DroElf] Could not select a different world for locked-elf recovery; will retry route.");
            elfLockRecoveryFailures = Math.max(1, MAX_ELF_LOCK_RECOVERY_FAILURES - 1);
            elfLockRecoveryRequested = true;
            nextElfLockRecoveryAt = System.currentTimeMillis() + 2_000L;
            return true;
        }

        status = "Hopping after blocked elf";
        log.warn("[DroElf] Repeated game-confirmed locked-elf recovery failures; hopping {} -> {} while preserving elf={} id={} anchor={}",
                currentWorld, targetWorld, lockedElfName, lockedElfId, elfAnchor);

        // Preserve the durable session identity and original anchor. The scene NPC
        // index is not durable across worlds, so force a fresh exact-ID reacquisition.
        elf = null;
        lockedElfIndex = -1;
        elfMissingSince = 0L;
        cantReachMessagesSinceRecovery = 0;
        elfLockRecoveryFailures = 0;

        final int destinationWorld = targetWorld;
        final boolean initiated = Microbot.hopToWorld(destinationWorld);
        if (!initiated) {
            log.warn("[DroElf] World hop to {} was not initiated; retrying locked route instead.", destinationWorld);
            elfLockRecoveryRequested = true;
            nextElfLockRecoveryAt = System.currentTimeMillis() + 2_000L;
            return true;
        }

        lastWorldHopAt = System.currentTimeMillis();
        confirmedCantReachRecoveryCycles = 0;

        sleepUntil(() -> !Microbot.isLoggedIn() || Microbot.isHopping()
                || Rs2Player.getWorld() == destinationWorld, 5_000);
        sleepUntil(() -> Microbot.isLoggedIn()
                && !Microbot.isHopping()
                && Rs2Player.getWorld() == destinationWorld, 15_000);

        // Start the same locked-elf return on the new world. No replacement elf is
        // permitted here: findLockedElf() still requires the original name + ID.
        elfLockRecoveryRequested = true;
        nextElfLockRecoveryAt = System.currentTimeMillis() + 750L;
        nextElfAreaInsuranceCheckAt = System.currentTimeMillis() + ELF_AREA_INSURANCE_INTERVAL_MS;
        status = "Reacquiring " + lockedElfName + " after hop";
        return true;
    }

    private void initializeSession() {
        final Rs2NpcModel nearest = Microbot.getRs2NpcCache().query()
                .where(npc -> {
                    final String name = safeNpcName(npc);
                    return name != null && ThievingData.ELVES.contains(name);
                })
                .toListOnClientThread().stream()
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);
        if (nearest == null || nearest.getWorldLocation() == null) {
            status = "Start near an elf";
            return;
        }

        lockedElfName = safeNpcName(nearest);
        lockedElfId = nearest.getId();
        lockedElfIndex = nearest.getIndex();
        lockedElfStartTile = nearest.getWorldLocation();
        lastKnownElfTile = lockedElfStartTile;
        elfAnchor = Rs2Player.getWorldLocation();
        elf = nearest;
        lockedBank = elfAnchor.distanceTo(BankLocation.PRIFDDINAS_NORTH.getWorldPoint())
                <= elfAnchor.distanceTo(BankLocation.PRIFDDINAS_SOUTH.getWorldPoint())
                ? BankLocation.PRIFDDINAS_NORTH : BankLocation.PRIFDDINAS_SOUTH;
        status = "Locking local route to " + lockedBank.name();
        routeToBank = expandRoute(List.of(elfAnchor, lockedBank.getWorldPoint()));
        routeDoor = findDoorOnLockedRoute();
        nextElfAreaInsuranceCheckAt = System.currentTimeMillis() + ELF_AREA_INSURANCE_INTERVAL_MS;
        log.info("[DroElf] Locked elf={} id={} index={} npcStart={} anchor={} bank={} routeTiles={} door={}",
                lockedElfName, lockedElfId, lockedElfIndex, lockedElfStartTile,
                elfAnchor, lockedBank, routeToBank.size(), routeDoor);
        status = "Route locked: " + lockedElfName;
    }

    private List<WorldPoint> expandRoute(List<WorldPoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) return Collections.emptyList();
        final List<WorldPoint> expanded = new ArrayList<>();
        expanded.add(checkpoints.get(0));
        for (int i = 1; i < checkpoints.size(); i++) {
            WorldPoint current = expanded.get(expanded.size() - 1);
            final WorldPoint end = checkpoints.get(i);
            while (!current.equals(end)) {
                current = new WorldPoint(
                        current.getX() + Integer.compare(end.getX(), current.getX()),
                        current.getY() + Integer.compare(end.getY(), current.getY()),
                        end.getPlane());
                expanded.add(current);
            }
        }
        return List.copyOf(expanded);
    }

    private WorldPoint findDoorOnLockedRoute() {
        if (routeToBank.isEmpty() || elfAnchor == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query().within(elfAnchor, 15)
                        .where(this::isDoor)
                        .toList().stream()
                        .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo(elfAnchor)))
                        .map(Rs2TileObjectModel::getWorldLocation)
                        .orElse(null)
        ).orElse(null);
    }

    private boolean isDoor(Rs2TileObjectModel object) {
        if (object == null) return false;
        ObjectComposition composition = object.getObjectComposition();
        if (composition == null) return false;
        if (composition.getImpostorIds() != null && composition.getImpostor() != null) {
            composition = composition.getImpostor();
        }
        final String name = composition.getName();
        final String[] actions = composition.getActions();
        return name != null && name.toLowerCase().contains("door") && actions != null
                && Arrays.stream(actions).anyMatch(ROUTE_DOOR_ACTIONS::contains);
    }

    private boolean handleLockedDoor() {
        if (routeDoor == null) return false;
        final WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.distanceTo(routeDoor) > doorClickRange) return false;

        final Rs2TileObjectModel closedDoor = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query().within(routeDoor, 1)
                        .where(object -> hasAction(object, "Open"))
                        .nearest()).orElse(null);
        if (closedDoor == null) return false;

        status = "Opening locked route door";
        final WorldPoint before = player;
        pauseBeforeAction();
        if (closedDoor.click("Open")) {
            sleepUntil(() -> {
                final WorldPoint now = Rs2Player.getWorldLocation();
                return now == null || !now.equals(before) || !hasClosedDoorAtRoute();
            }, 2_500);
            sleep(50, 250);
        }
        return true;
    }

    private boolean hasClosedDoorAtRoute() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query().within(routeDoor, 1)
                        .where(object -> hasAction(object, "Open"))
                        .toList().size() > 0).orElse(false);
    }

    private boolean hasAction(Rs2TileObjectModel object, String wanted) {
        if (object == null) return false;
        ObjectComposition composition = object.getObjectComposition();
        if (composition == null) return false;
        if (composition.getImpostorIds() != null && composition.getImpostor() != null) composition = composition.getImpostor();
        final String[] actions = composition.getActions();
        return actions != null && Arrays.stream(actions)
                .anyMatch(action -> action != null && action.equalsIgnoreCase(wanted));
    }

    private boolean followLockedRoute(boolean towardBank) {
        final WorldPoint player = Rs2Player.getWorldLocation();
        final WorldPoint target = towardBank
                ? (lockedBank == null ? null : lockedBank.getWorldPoint())
                : elfAnchor;

        // Primary route: self-contained Prifddinas A* reconstructed from the
        // Dro Efficient Walker collision graph. No Efficient Walker plugin
        // or JAR is required at runtime. The original locked route stays below
        // as a safety fallback while we validate every Prif route in practice.
        if (player != null && target != null
                && prifWalker.supports(player)
                && prifWalker.supports(target)) {
            status = towardBank ? "Planning Prif route to bank" : "Planning Prif route to " + lockedElfName;
            if (prifWalker.walkTo(
                    target,
                    this::isRunning,
                    this::pauseBeforeAction,
                    value -> status = value)) {
                return true;
            }
            log.debug("[DroElf][PrifWalker] Falling back to legacy locked route {} -> {}", player, target);
        }

        if (routeToBank.isEmpty()) return false;
        doorClickRange = randomBetween(5, 10);
        int index = nearestRouteIndex(Rs2Player.getWorldLocation());
        final int goal = towardBank ? routeToBank.size() - 1 : 0;
        final int direction = towardBank ? 1 : -1;

        int attempts = 0;
        final int maxAttempts = Math.max(12, routeToBank.size() * 2);
        while (isRunning() && index != goal && attempts++ < maxAttempts) {
            if (towardBank && Rs2Player.getWorldLocation().distanceTo(lockedBank.getWorldPoint()) <= BANK_CLICK_RANGE) {
                return true;
            }
            if (!towardBank && Rs2Player.getWorldLocation().distanceTo(elfAnchor) <= 3) return true;
            if (handleLockedDoor()) {
                index = nearestRouteIndex(Rs2Player.getWorldLocation());
                continue;
            }

            int step = randomBetween(ROUTE_STEP_MIN, ROUTE_STEP_MAX);
            int selected = towardBank ? Math.min(goal, index + step) : Math.max(goal, index - step);
            // Vary the clicked tile while remaining on the already validated route.
            int variance = randomBetween(-2, 2);
            selected = towardBank
                    ? Math.max(index + 1, Math.min(goal, selected + variance))
                    : Math.min(index - 1, Math.max(goal, selected + variance));
            final WorldPoint destination = routeToBank.get(selected);
            status = "Walking locked route";
            pauseBeforeAction();
            DroElfPrifWalker.restoreRunIfOff();
            boolean clicked = Rs2Walker.walkMiniMap(destination);
            if (!clicked) clicked = Rs2Walker.walkFastCanvas(destination);
            if (!clicked) return false;

            sleepUntil(() -> Rs2Player.isMoving()
                    || Rs2Player.getWorldLocation().distanceTo(destination) <= 2, 1_500);
            // One destination per action: allow the character to finish this
            // movement before selecting another randomized route tile.
            if (Rs2Player.isMoving()) sleepUntil(() -> !Rs2Player.isMoving(), 8_000);
            sleep(50, 250);
            index = nearestRouteIndex(Rs2Player.getWorldLocation());
        }
        return index == goal
                || (towardBank && Rs2Player.getWorldLocation().distanceTo(lockedBank.getWorldPoint()) <= BANK_CLICK_RANGE)
                || (!towardBank && Rs2Player.getWorldLocation().distanceTo(elfAnchor) <= 3);
    }

    private int nearestRouteIndex(WorldPoint point) {
        int best = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < routeToBank.size(); i++) {
            final int candidate = routeToBank.get(i).distanceTo(point);
            if (candidate < distance) {
                distance = candidate;
                best = i;
            }
        }
        return best;
    }

    private void bankAndRestock() {
        status = "Banking at " + lockedBank.name();
        if (!Rs2Bank.isOpen()) {
            if (Rs2Player.getWorldLocation().distanceTo(lockedBank.getWorldPoint()) > BANK_CLICK_RANGE
                    && !followLockedRoute(true)) return;
            final int bankEpoch = Rs2Bank.getBankLiveEpoch();
            pauseBeforeAction();
            if (!Rs2Bank.openBank()) return;
            sleepUntil(Rs2Bank::isOpen, 2_500);
            if (!Rs2Bank.isOpen() || !sleepUntil(
                    () -> Rs2Bank.verifyBankMirrorAfterOpen(false, bankEpoch), 4_000)) return;
        }
        if (!Rs2Bank.isOpen()) return;

        final List<String> keep = inventoryKeepItems();
        pauseBeforeAction();
        Rs2Bank.depositAllExcept(keep);
        sleep(50, 250);

        if (config.useFood()) {
            withdrawDeficit(config.food().getName(), config.foodAmount());
            sleep(50, 250);
        }
        withdrawDeficit("Dodgy necklace", config.dodgyAmount());
        sleep(50, 250);
        if (config.shadowVeil()) {
            // Earth and Fire come from a Lava battlestaff or from runes. Only fetch the
            // staff when neither is there, and fall back to runes if the bank has no staff.
            if (!hasShadowVeilElementals() && Rs2Bank.hasItem("Lava battlestaff")) {
                Rs2Bank.withdrawItem("Lava battlestaff");
                sleepUntil(() -> Rs2Inventory.hasItem("Lava battlestaff"), 1_500);
            }
            if (!hasShadowVeilElementals()) {
                withdrawDeficit("Earth rune", SHADOW_VEIL_RUNE_STOCK);
                withdrawDeficit("Fire rune", SHADOW_VEIL_RUNE_STOCK);
            }
            withdrawDeficit("Cosmic rune", SHADOW_VEIL_RUNE_STOCK);
        }
        sleep(50, 250);

        // Anything still missing is not in the bank: stop rather than walk bank <-> elf forever.
        sleepUntil(() -> missingSupplies().isEmpty(), 1_200);
        final List<String> missing = missingSupplies();
        pauseBeforeAction();
        Rs2Bank.closeBank();
        sleep(50, 250);
        if (!missing.isEmpty()) {
            stopWithMessage("Out of supplies in the bank: " + String.join(", ", missing) + ".");
            return;
        }
        followLockedRoute(false);
    }

    private static final int SHADOW_VEIL_RUNES_PER_CAST = 5;
    private static final int SHADOW_VEIL_RUNE_STOCK = 100;

    /** Earth and Fire for Shadow Veil: a Lava battlestaff, or enough runes in the inventory. */
    private boolean hasShadowVeilElementals() {
        if (Rs2Equipment.isWearing("Lava battlestaff") || Rs2Inventory.hasItem("Lava battlestaff")) return true;
        return Rs2Inventory.itemQuantity("Earth rune") >= SHADOW_VEIL_RUNES_PER_CAST
                && Rs2Inventory.itemQuantity("Fire rune") >= SHADOW_VEIL_RUNES_PER_CAST;
    }

    /** Supplies the current settings need that the inventory/equipment does not have. */
    private List<String> missingSupplies() {
        final List<String> missing = new ArrayList<>();
        if (config.useFood()
                && !Rs2Inventory.hasUnNotedItem(config.food().getName(), true)
                && Rs2Inventory.getInventoryFood().isEmpty()) missing.add(config.food().getName());
        if (config.dodgyAmount() > 0 && !Rs2Equipment.isWearing("dodgy necklace")
                && !Rs2Inventory.hasUnNotedItem("Dodgy necklace", true)) missing.add("Dodgy necklace");
        if (config.shadowVeil()) {
            if (Rs2Inventory.itemQuantity("Cosmic rune") < SHADOW_VEIL_RUNES_PER_CAST) missing.add("Cosmic rune");
            if (!hasShadowVeilElementals()) missing.add("Lava battlestaff or Earth + Fire runes");
        }
        return missing;
    }

    private void stopWithMessage(String message) {
        status = "Stopped: " + message;
        log.warn("[DroElf] Stopping: {}", message);
        Microbot.showMessage("[Dro] Elf Thieving stopped. " + message);
        try {
            final Plugin plugin = Microbot.getPlugin(DroElfPlugin.class.getName());
            if (plugin != null) Microbot.stopPlugin(plugin);
        } catch (Exception ex) {
            log.debug("[DroElf] Plugin stop failed", ex);
        }
        shutdown();
    }

    private void withdrawDeficit(String name, int wanted) {
        final int deficit = Math.max(0, wanted - Rs2Inventory.itemQuantity(name, true));
        if (deficit > 0) {
            pauseBeforeAction();
            Rs2Bank.withdrawX(name, deficit, true);
            sleepUntil(() -> Rs2Inventory.itemQuantity(name, true) >= wanted, 1_500);
        }
    }

    private boolean needsBank() {
        return !missingSupplies().isEmpty();
    }

    private void handleFullInventory() {
        status = "Making inventory space";

        // Empty jugs are disposable clutter from Jug of wine. Drop them before
        // considering food excess or any thieving loot.
        if (Rs2Inventory.emptySlotCount() < 2
                && !isKeepItemName("Jug")
                && Rs2Inventory.contains("Jug", true)) {
            status = "Dropping empty jugs";
            pauseBeforeAction();
            Rs2Inventory.dropAll(true, "Jug");
            sleepUntil(() -> Rs2Inventory.emptySlotCount() >= 2
                    || !Rs2Inventory.contains("Jug", true), 1_800);
        }

        if (Rs2Inventory.emptySlotCount() >= 2) return;

        if (config.useFood()) {
            final String foodName = config.food().getName();
            final int excess = Rs2Inventory.itemQuantity(foodName, true) - config.foodAmount();
            if (excess > 0) {
                pauseBeforeAction();
                Rs2Inventory.dropAmount(foodName, excess, InteractOrder.STANDARD, true);
                sleepUntil(() -> Rs2Inventory.emptySlotCount() >= 2, 1_800);
            }
        }

        if (Rs2Inventory.emptySlotCount() >= 2) return;

        dropLowestValueItemsUntilSpace();
    }

    private void dropLowestValueItemsUntilSpace() {
        final int keepAtOrAbove = Math.max(0, config.keepItemsAboveValue());

        final List<Rs2ItemModel> candidates = Rs2Inventory.all().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getName() != null)
                .filter(item -> !isKeepItemName(item.getName()))
                .filter(item -> !item.getName().equalsIgnoreCase("Jug"))
                // Untradeables (Crystal shards, the player's own gear) price at 0: never drop them.
                .filter(item -> safeInventoryValue(item) > 0)
                // Rs2ItemModel#getPrice is the total GE value of this inventory slot,
                // matching Microbot's native value-aware drop behavior.
                .filter(item -> safeInventoryValue(item) < keepAtOrAbove)
                .sorted(Comparator
                        .comparingLong(this::safeInventoryValue)
                        .thenComparingInt(Rs2ItemModel::getSlot))
                .collect(java.util.stream.Collectors.toList());

        for (Rs2ItemModel item : candidates) {
            if (Rs2Inventory.emptySlotCount() >= 2) return;

            // Re-check the keep list because earlier drops can shift inventory slots.
            if (item == null || item.getName() == null || isKeepItemName(item.getName())) continue;

            status = "Dropping " + item.getName();
            pauseBeforeAction();
            if (Rs2Inventory.drop(item.getId())) {
                sleep(80, 180);
            }
        }

        sleepUntil(() -> Rs2Inventory.emptySlotCount() >= 2, 1_800);
    }

    private long safeInventoryValue(Rs2ItemModel item) {
        if (item == null) return Long.MAX_VALUE;
        try {
            return Math.max(0, item.getPrice());
        } catch (Exception ex) {
            // Unknown prices are treated as protected instead of being dropped first.
            return Long.MAX_VALUE;
        }
    }

    private boolean isKeepItemName(String itemName) {
        if (itemName == null) return false;
        return inventoryKeepItems().stream().anyMatch(keep -> keep.equalsIgnoreCase(itemName));
    }

    private List<String> inventoryKeepItems() {
        final List<String> keep = new ArrayList<>(ThievingData.ROGUE_SET);
        Collections.addAll(keep, "coins", "coin pouch", "book of the dead", "dodgy necklace",
                "Lava battlestaff", "Earth rune", "Fire rune", "Cosmic rune",
                "Crystal shard", "Enhanced crystal teleport seed");
        if (config.useFood()) keep.add(config.food().getName());
        if (config.keepItems() != null && !config.keepItems().isBlank()) {
            Arrays.stream(config.keepItems().split(","))
                    .map(String::strip).filter(item -> !item.isBlank()).forEach(keep::add);
        }
        return keep;
    }

    /**
     * Reacquire the exact elf identity selected at session start. The definition ID
     * is the durable lock; the live NPC index is preferred when it still matches,
     * and the last known elf tile is used only to rank same-ID candidates.
     *
     * We intentionally do not fall through to "any elf" here. A session that starts
     * on Oropher stays locked to Oropher unless a future explicit long-term search
     * failsafe decides otherwise.
     */
    private Rs2NpcModel findLockedElf() {
        if (lockedElfId < 0 || lockedElfName == null || elfAnchor == null) return null;

        final WorldPoint lastSeen = lastKnownElfTile;
        final int preferredIndex = lockedElfIndex;

        return Microbot.getRs2NpcCache().query().where(npc -> {
                    if (npc == null || npc.getNpc() == null) return false;
                    final String name = safeNpcName(npc);
                    final WorldPoint location = npc.getWorldLocation();
                    return npc.getId() == lockedElfId
                            && lockedElfName.equals(name)
                            && location != null
                            && location.distanceTo(elfAnchor) <= ELF_DETECTION_RADIUS;
                }).toListOnClientThread().stream()
                .min(Comparator
                        .comparingInt((Rs2NpcModel npc) -> npc.getIndex() == preferredIndex ? 0 : 1)
                        .thenComparingInt(npc -> distanceOrMax(npc.getWorldLocation(), lastSeen))
                        .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);
    }

    private void rememberLockedElf(Rs2NpcModel npc) {
        if (npc == null || npc.getNpc() == null || npc.getId() != lockedElfId) return;
        final WorldPoint location = npc.getWorldLocation();
        if (location == null) return;

        // The NPC index is a useful live-scene discriminator, but may change after
        // banking/scene reloads. The definition ID remains the permanent session lock.
        lockedElfIndex = npc.getIndex();
        lastKnownElfTile = location;
    }

    private int distanceOrMax(WorldPoint a, WorldPoint b) {
        if (a == null || b == null || a.getPlane() != b.getPlane()) return Integer.MAX_VALUE;
        return a.distanceTo(b);
    }

    private void handleMissingElf() {
        final long now = System.currentTimeMillis();
        if (elfMissingSince == 0L) {
            elfMissingSince = now;
            log.info("[DroElf] Lost locked elf={} id={} index={} lastKnown={} anchor={}",
                    lockedElfName, lockedElfId, lockedElfIndex, lastKnownElfTile, elfAnchor);
        }

        // Keep the exact session identity. findLockedElf() is retried every loop, while
        // the existing 20-second area insurance independently returns the player to the
        // original elf area if positional drift occurred. No target reset or replacement
        // elf is allowed in phase 1.
        if (now - elfMissingSince >= ELF_REACQUIRE_TIMEOUT_MS) {
            status = "Reacquiring locked " + lockedElfName;
        } else {
            status = "Searching for locked " + lockedElfName;
        }
        elf = null;
    }

    private String safeNpcName(Rs2NpcModel npc) {
        if (npc == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(npc::getName).orElse(null);
    }

    private boolean shouldCastShadowVeil() {
        final long now = System.currentTimeMillis();
        return now > shadowVeilForcedActiveUntil && (now >= nextShadowVeilAt || !Rs2Magic.isShadowVeilActive());
    }

    private boolean isSafeForBreak() {
        if (!Microbot.isLoggedIn()) return false;
        final WorldPoint player = Rs2Player.getWorldLocation();
        return lockedElfName != null
                && elfAnchor != null
                && player != null
                && player.distanceTo(elfAnchor) <= 8
                && !Rs2Bank.isOpen()
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isStunned();
    }

    private boolean handleNativePauseAndBreakState() {
        if (Microbot.pauseAllScripts.get()) {
            status = "Paused";
            return true;
        }

        return false;
    }

    private boolean handleScheduledRest() {
        if (!config.scheduledRests()) return false;
        final WorldPoint player = Rs2Player.getWorldLocation();
        // Rest only at the locked thieving area. The option remains active for
        // the whole session but cannot interrupt a bank trip or route segment.
        if (player == null || elfAnchor == null || player.distanceTo(elfAnchor) > 8
                || Rs2Bank.isOpen() || Rs2Player.isMoving() || Rs2Player.isAnimating()) return false;
        final long now = System.currentTimeMillis();
        if (restUntil > now) {
            status = "Resting (" + Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(restUntil - now)) + "s)";
            return true;
        }
        if (restUntil != 0L) {
            restUntil = 0L;
            nextRestAt = now + randomLong(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
        }
        if (now < nextRestAt) return false;
        restUntil = now + randomLong(MIN_REST_DURATION_MS, MAX_REST_DURATION_MS);
        nextRestAt = Long.MAX_VALUE;
        status = "Starting rest";
        Rs2Antiban.moveMouseOffScreen();
        return true;
    }

    private boolean handlePickpocketAntiban() {
        final long now = System.currentTimeMillis();
        if (config.timedPauses() && now >= nextShortDelayAt) {
            status = "Short pause";
            sleep(randomBetween(1_000, 4_000));
            nextShortDelayAt = System.currentTimeMillis()
                    + randomLong(TimeUnit.MINUTES.toMillis(3), TimeUnit.MINUTES.toMillis(4));
            return true;
        }
        if (config.safeMisclicks() && now >= nextMisclickAt && elfAnchor != null) {
            final WorldPoint candidate = elfAnchor.dx(randomBetween(-1, 1)).dy(randomBetween(-1, 1));
            nextMisclickAt = now + randomLong(TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(15));
            if (!candidate.equals(elfAnchor) && Rs2Walker.isWalkableInCollisionMap(candidate)) {
                status = "Safe misclick";
                pauseBeforeAction();
                Rs2Walker.walkFastCanvas(candidate, Rs2Player.isRunEnabled());
                sleepUntil(() -> !Rs2Player.isMoving(), 2_500);
                sleep(50, 250);
                return true;
            }
        }

        // MKE-style subtle yaw/pitch turns, but only while safely idle at the
        // locked elf. No camera zoom correction is used in DroElf.
        if (adaptiveBehavior.maybeCameraTurn(isSafeForBreak())) {
            status = "Looking around";
            return true;
        }

        return false;
    }

    private void pauseBeforeAction() {
        sleep(adaptiveBehavior.reactionDelayMs(personality));
    }

    private boolean handlePickpocketMouseBehavior() {
        MouseActivity mode = MouseActivity.ACTIVE;
        if (randomBetween(0, 100) < mode.offScreenChance(personality)) {
            parkMouseOffScreen();
            offScreenParksSinceInventoryGlance++;
            return true;
        }

        // Keep inventory glances rare relative to off-screen parks: roughly
        // one glance every 7-14 off-screen parks, which works out to about
        // 1-2 inventory glances per 12-15 off-screen movements.
        if (!mode.isPureAfk()
                && offScreenParksSinceInventoryGlance >= nextInventoryGlanceAfterParks
                && glanceAtInventory()) {
            offScreenParksSinceInventoryGlance = 0;
            nextInventoryGlanceAfterParks = randomBetween(7, 14);
            return true;
        }
        return false;
    }

    private void parkMouseOffScreen() {
        if (!sessionParkSide.parksOffScreen()) return;

        // One exit movement only. Do not first visit a visible canvas edge;
        // Rs2Antiban owns the complete off-screen movement.
        Rs2Antiban.moveMouseOffScreen();
    }

    private boolean glanceAtInventory() {
        net.runelite.api.Point point = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget inventory = Microbot.getClient().getWidget(InterfaceID.Inventory.ITEMS);
            if (inventory == null || inventory.isHidden()) return null;
            Rectangle bounds = inventory.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
            int x = bounds.x + randomBetween(4, Math.max(5, bounds.width - 4));
            int y = bounds.y + randomBetween(4, Math.max(5, bounds.height - 4));
            return new net.runelite.api.Point(x, y);
        }).orElse(null);
        if (point == null) return false;
        Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        return true;
    }

    public void onChatMessage(ChatMessage event) {
        if (event == null || event.getMessage() == null) return;
        final String message = event.getMessage()
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');

        if (message.contains("you can only cast shadow veil every 30 seconds")) {
            shadowVeilForcedActiveUntil = System.currentTimeMillis() + 30_000L;
        }

        if (lockedElfName != null
                && (message.contains("i can't reach that") || message.contains("cant reach that"))) {
            final long now = System.currentTimeMillis();
            cantReachMessagesSinceRecovery++;
            lastCantReachMessageAt = now;

            // Only a message that occurs outside an already-running recovery starts a
            // new game-confirmed recovery cycle. Repeated chat spam while one walker
            // pass is in flight is one incident, not three failures.
            if (!elfLockRecoveryInProgress && !elfLockRecoveryRequested) {
                confirmedCantReachRecoveryCycles++;
            }

            log.warn("[DroElf] Game reported unreachable locked elf (messageCount={} confirmedCycles={} inRecovery={}): {}",
                    cantReachMessagesSinceRecovery, confirmedCantReachRecoveryCycles,
                    elfLockRecoveryInProgress, event.getMessage());
            requestElfLockRecovery("game message: I can't reach that");
        }
    }

    private int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public String getLockedElfDisplay() {
        return lockedElfName == null ? "Not locked" : lockedElfName;
    }

    public String getLockedBankDisplay() {
        return lockedBank == null ? "Not locked" : lockedBank.name();
    }

    public int getRouteTileCount() {
        return routeToBank.size();
    }

    public String getRuntimeDisplay() {
        if (startedAt == null) return "00:00:00";
        final Duration runtime = Duration.between(startedAt, Instant.now());
        return String.format("%02d:%02d:%02d", runtime.toHours(), runtime.toMinutesPart(), runtime.toSecondsPart());
    }

    @Override
    public void shutdown() {
        if (breakManager != null) {
            breakManager.shutdown();
            breakManager = null;
        }
        super.shutdown();
        resetSession();
    }
}
