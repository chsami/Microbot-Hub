package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.common.collect.ImmutableSet;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.pestcontrol.Portal;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isQuickPrayerEnabled;
import static net.runelite.client.plugins.pestcontrol.Portal.*;

public class PestControlScript extends Script {

    boolean initialise = true;
    boolean openingSideReached = false;
    private volatile boolean wasInPestControl = false;
    private boolean pendingPostRoundRestore = false;
    private boolean autoRetaliateConfirmedOff = false;
    private boolean autoRetaliateDisableLogged = false;
    private Portal selectedPortal = null;
    private Portal openingPortal = null;
    private PestControlCombatPlan combatPlan;
    private String activeLoadoutKey = null;
    private final Map<String, Long> loadoutRetryAfterByKey = new HashMap<>();
    private boolean startupCombatPrepared = false;
    private boolean startupAutocastPrepared = false;
    private final Set<String> loadoutFailuresLogged = new HashSet<>();
    private final Set<String> missingAttackOptionsLogged = new HashSet<>();
    private final Map<String, Integer> attackOptionIndexByWeaponStyle = new HashMap<>();
    private String activeAttackOptionKey = null;
    private long lastBoardingAttemptAt = 0L;
    private boolean boardingAttemptPending = false;
    private final Set<Portal> destroyedPortals = EnumSet.noneOf(Portal.class);
    PestControlConfig config;
    private final PestControlPlugin plugin;

    @Inject
    public PestControlScript(PestControlPlugin plugin, PestControlConfig config) {
        this.plugin = plugin;
        this.config = config;
    }


    private static final Set<Integer> SPINNER_IDS = ImmutableSet.of(
            NpcID.SPINNER,
            NpcID.SPINNER_1710,
            NpcID.SPINNER_1711,
            NpcID.SPINNER_1712,
            NpcID.SPINNER_1713
    );
    private static final Set<Integer> BRAWLER_IDS = ImmutableSet.of(
            NpcID.BRAWLER,
            NpcID.BRAWLER_1735,
            NpcID.BRAWLER_1736,
            NpcID.BRAWLER_1737,
            NpcID.BRAWLER_1738
    );

    private static final int PORTAL_CROWD_RADIUS = 12;
    private static final int PORTAL_MATCH_RADIUS = 5;
    private static final int SPINNER_PORTAL_RADIUS = 8;
    private static final int PEST_CONTROL_CENTER_REGION_COORD = 32;
    private static final int STAGING_ARRIVAL_DISTANCE = 2;
    private static final int RANGED_ENGAGEMENT_DISTANCE = 6;
    private static final int RANGED_STAGING_DISTANCE = 6;
    private static final int MELEE_ENGAGEMENT_DISTANCE = 1;
    private static final int MINIMAP_STEP_DISTANCE = 14;
    private static final int SOUTH_PERIMETER_REGION_Y = 23;
    private static final int SOUTH_PERIMETER_DETOUR_REGION_Y = 26;
    private static final int WEST_PERIMETER_REGION_X = 15;
    private static final int EAST_PERIMETER_REGION_X = 48;
    private static final int WEST_LANE_GATE_REGION_X = 19;
    private static final int EAST_LANE_GATE_REGION_X = 46;
    private static final int LANE_GATE_REGION_Y = 32;
    private static final int WEST_LANE_INNER_REGION_X = 22;
    private static final int EAST_LANE_INNER_REGION_X = 43;
    private static final int WEST_LANE_OUTER_REGION_X = 16;
    private static final int EAST_LANE_OUTER_REGION_X = 49;
    private static final int LANE_GATE_APPROACH_DISTANCE = 2;
    private static final int LANE_GATE_MATCH_DISTANCE = 2;
    private static final int PERIMETER_WAYPOINT_ARRIVAL_DISTANCE = 2;
    private static final int ACTIVITY_TARGET_RADIUS = 12;
    private static final int BLOCKING_GATE_RADIUS = 10;
    private static final int GATE_CROSSING_DISTANCE = 2;
    private static final double BLOCKING_GATE_ROUTE_WIDTH = 3.0;
    private static final double BRAWLER_ROUTE_PADDING = 0.75;
    private static final int SOUTHWEST_NO_GO_MIN_X = 2627;
    private static final int SOUTHWEST_NO_GO_MAX_X = 2640;
    private static final int SOUTHWEST_NO_GO_MIN_Y = 2567;
    private static final int SOUTHWEST_NO_GO_MAX_Y = 2580;
    private static final int SOUTHWEST_NO_GO_PLANE = 0;
    private static final long MOVEMENT_RETRY_IDLE_MILLIS = 750L;
    private static final long MOVEMENT_RETRY_MOVING_MILLIS = 1_500L;
    private static final long ATTACK_RETRY_MILLIS = 600L;
    private static final long SAME_TARGET_ATTACK_RETRY_MILLIS = 3_000L;
    private static final long DUPLICATE_COMMAND_LOG_MILLIS = 10_000L;
    private static final long PORTAL_REAFFIRM_MILLIS = 3_000L;
    private static final long WATCHDOG_IDLE_MILLIS = 6_000L;
    private static final long WATCHDOG_LOG_INTERVAL_MILLIS = 6_000L;
    private static final long BOARDING_RETRY_MILLIS = 600L;
    private static final long BOARDING_CONFIRM_TIMEOUT_MILLIS = 3_000L;
    private static final long ROUND_TRANSITION_LOGIN_GRACE_MILLIS = 5_000L;
    private static final long ROUND_OUTCOME_GRACE_MILLIS = 12_000L;
    private static final long GATE_OPEN_ANIMATION_MILLIS = 1_200L;
    private static final long GATE_OPEN_CONFIRM_TIMEOUT_MILLIS = 3_000L;
    private static final long GATE_CROSSING_TIMEOUT_MILLIS = 6_000L;
    private static final long GATE_REUSE_COOLDOWN_MILLIS = 8_000L;
    private static final long BRAWLER_FLANK_TIMEOUT_MILLIS = 4_500L;
    private static final long BRAWLER_CLEAR_COMMIT_MILLIS = 8_000L;
    private static final long BRAWLER_COMPASS_STEP_TIMEOUT_MILLIS = 2_500L;
    private static final long LOADOUT_RETRY_MILLIS = 1_500L;
    private static final long MISSING_LOADOUT_RETRY_MILLIS = 5_000L;
    private static final int BRAWLER_COMPASS_CLEARANCE = 2;
    private static final long SPINNER_TARGET_GRACE_MILLIS = 2_500L;
    private static final long PERIMETER_DETOUR_MILLIS = 10_000L;
    private static final Pattern POINT_COUNT = Pattern.compile("([\\d,]+)");
    public static final boolean DEBUG = false;

    public static List<Portal> portals = List.of(PURPLE, BLUE, RED, YELLOW);

    private volatile RuntimeState runtimeState = RuntimeState.STOPPED;
    private volatile String runtimeDetail = "";
    private long stateEnteredAt = 0L;
    private long lastProgressAt = 0L;
    private long lastWatchdogLogAt = 0L;
    private long lastMovementCommandAt = 0L;
    private long lastAttackCommandAt = 0L;
    private long lastPortalCameraTurnAt = 0L;
    private Portal lastCameraPivotPortal = null;
    private long lastSouthwestDetourLogAt = 0L;
    private long lastGateInteractionAt = 0L;
    private WorldPoint lastGateLocation = null;
    private WorldPoint activeGateCrossingTarget = null;
    private final Map<WorldPoint, Long> gateReuseCooldowns = new HashMap<>();
    private long lastGateDiagnosticAt = 0L;
    private Portal brawlerCommitPortal = null;
    private WorldPoint brawlerFlankTarget = null;
    private long brawlerFlankStartedAt = 0L;
    private long brawlerClearUntil = 0L;
    private int movementBlockingBrawlerIndex = -1;
    private String movementBrawlerFlankDirection = null;
    private WorldPoint movementBrawlerFlankTarget = null;
    private WorldPoint movementBrawlerRouteTarget = null;
    private long movementBrawlerFlankStartedAt = 0L;
    private long movementBrawlerClearUntil = 0L;
    private final Set<String> attemptedMovementBrawlerFlanks = new HashSet<>();
    private long perimeterDetourUntil = 0L;
    private Portal spinnerCommitPortal = null;
    private long spinnerCommitUntil = 0L;
    private volatile int shieldDropCount = 0;
    private WorldPoint lastProgressLocation = null;
    private boolean quickPrayerHandled = false;
    private boolean activityRecoveryActive = false;
    private Portal stagingPortal = null;
    private String lastAttackCommandKey = null;
    private long lastDuplicateAttackLogAt = 0L;
    private int suppressedDuplicateAttackCommands = 0;
    private long loginUnavailableSince = 0L;
    private long lastRoundExitAt = 0L;
    private volatile boolean roundCompletionPending = false;
    private String overlayLocation = "Stopped";
    private int overlayActivityPercent = -1;
    private Portal overlayTargetPortal = null;
    private int overlayTargetCrowd = 0;
    private boolean overlayTargetHasAttackAction = false;
    private String overlayCombatWeapon = "Unknown";
    private String overlayCombatStyle = "Unknown";
    private Boolean voidHelmetSwitchingEnabled = null;
    private volatile int sessionPointsEarned = 0;
    private volatile int sessionAwardedPoints = 0;
    private volatile int sessionStartingPoints = -1;
    private volatile int totalPoints = -1;
    private volatile int roundsPlayed = 0;
    private volatile int roundsWon = 0;
    private volatile int roundsLost = 0;
    private volatile String lastRoundResultSummary = "None";
    private volatile TeamOutcome pendingRoundTeamOutcome = TeamOutcome.UNKNOWN;
    private volatile boolean pendingRoundRewardConfirmed = false;
    private volatile String pendingRoundRewardSource = "NONE";
    private volatile int pendingRoundAwardedPoints = 0;
    private volatile int currentRoundStartingPoints = -1;
    private volatile int pendingRoundStartingPoints = -1;
    private volatile int pendingRoundObservedPoints = -1;
    private int pendingRoundFinalActivity = -1;
    private int pendingRoundDestroyedPortals = 0;
    private volatile OverlaySnapshot overlaySnapshot = OverlaySnapshot.initial();

    enum RoundOutcome {
        WON,
        LOST
    }

    enum TeamOutcome {
        UNKNOWN,
        WON,
        LOST
    }

    private enum RuntimeState {
        INITIALISING,
        TRAVELLING,
        REQUEUE,
        BOAT,
        OPENING_SIDE,
        PREPOSITION_PORTAL,
        WAITING_FOR_PORTAL,
        CHASE_PORTAL,
        AVOID_BRAWLER,
        OPEN_GATE,
        KILL_SPINNER,
        ATTACK_PORTAL,
        ACTIVITY_FALLBACK,
        HOLDING_COMBAT,
        ERROR,
        STOPPED
    }

    private void resetPortals() {
        destroyedPortals.clear();
        shieldDropCount = 0;
        for (Portal portal : portals) {
            portal.setHasShield(true);
        }
    }

    private void transitionTo(RuntimeState state, String detail) {
        String normalizedDetail = detail == null ? "" : detail;
        if (runtimeState != state || !runtimeDetail.equals(normalizedDetail)) {
            long now = System.currentTimeMillis();
            runtimeState = state;
            runtimeDetail = normalizedDetail;
            stateEnteredAt = now;
            lastProgressAt = now;
            lastProgressLocation = null;
            lastWatchdogLogAt = 0L;
            Microbot.log("Pest Control state: " + state
                    + (normalizedDetail.isEmpty() ? "" : " - " + normalizedDetail));
        }
        Microbot.status = getRuntimeStatus();
        publishOverlaySnapshot();
    }

    String getRuntimeStatus() {
        RuntimeState state = runtimeState;
        String detail = runtimeDetail;
        return state + (detail.isEmpty() ? "" : ": " + detail);
    }

    OverlaySnapshot getOverlaySnapshot() {
        return overlaySnapshot;
    }

    private void publishOverlaySnapshot() {
        int readyPortals = (int) portals.stream()
                .filter(portal -> !destroyedPortals.contains(portal) && !portal.hasShield)
                .count();
        int remainingPortals = Math.max(0, portals.size() - destroyedPortals.size());
        int recoveryStart = config == null
                ? -1
                : Math.max(0, Math.min(100, config.activityRecoveryStart()));
        int recoveryTarget = config == null
                ? -1
                : Math.max(recoveryStart, Math.max(0, Math.min(100, config.activityRecoveryTarget())));

        overlaySnapshot = new OverlaySnapshot(
                runtimeState.name(),
                runtimeDetail,
                overlayLocation,
                overlayActivityPercent,
                activityRecoveryActive,
                recoveryStart,
                recoveryTarget,
                overlayTargetPortal == null ? "None" : overlayTargetPortal.name(),
                overlayTargetCrowd,
                overlayTargetHasAttackAction,
                openingPortal == null ? "None" : openingPortal.name(),
                remainingPortals,
                readyPortals,
                overlayCombatWeapon,
                overlayCombatStyle,
                sessionPointsEarned,
                totalPoints,
                roundsPlayed,
                roundsWon,
                roundsLost,
                roundResultSummary(),
                autoRetaliateConfirmedOff,
                boardingAttemptPending,
                stateEnteredAt,
                lastProgressAt,
                lastMovementCommandAt,
                lastAttackCommandAt);
    }

    private void observeProgress(WorldPoint location) {
        if (location == null) {
            return;
        }
        if (lastProgressLocation == null || lastProgressLocation.distanceTo(location) >= 2) {
            lastProgressLocation = location;
            lastProgressAt = System.currentTimeMillis();
        }
    }

    private void runWatchdog(WorldPoint location) {
        observeProgress(location);
        if (runtimeState != RuntimeState.OPENING_SIDE
                && runtimeState != RuntimeState.PREPOSITION_PORTAL
                && runtimeState != RuntimeState.CHASE_PORTAL
                && runtimeState != RuntimeState.AVOID_BRAWLER
                && runtimeState != RuntimeState.OPEN_GATE
                && runtimeState != RuntimeState.KILL_SPINNER
                && runtimeState != RuntimeState.ATTACK_PORTAL
                && runtimeState != RuntimeState.ACTIVITY_FALLBACK) {
            return;
        }
        if (Rs2Player.isMoving() || isPlayerInteracting()) {
            lastProgressAt = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        if (lastProgressAt == 0L) {
            lastProgressAt = now;
        }
        if (now - lastProgressAt < WATCHDOG_IDLE_MILLIS
                || now - lastWatchdogLogAt < WATCHDOG_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastWatchdogLogAt = now;
        Microbot.log("Pest Control watchdog recovery: state=" + runtimeState
                + (runtimeDetail.isEmpty() ? "" : " detail=" + runtimeDetail)
                + " idleMs=" + (now - lastProgressAt)
                + " stateMs=" + (now - stateEnteredAt));
        if ((runtimeState == RuntimeState.CHASE_PORTAL
                || runtimeState == RuntimeState.KILL_SPINNER)
                && selectedPortal != null) {
            perimeterDetourUntil = now + PERIMETER_DETOUR_MILLIS;
            Microbot.log("Pest Control movement recovery: using north-lane detour for "
                    + selectedPortal + " portal");
        }
        Rs2Walker.clearWalkingRoute("pest-control:watchdog-" + runtimeState.name().toLowerCase(Locale.ROOT));
        lastMovementCommandAt = 0L;
        lastAttackCommandAt = 0L;
        lastProgressAt = now;
    }

    private static boolean isPlayerInteracting() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getInteracting() != null;
        }).orElse(false);
    }

    private static WorldPoint stepTowards(WorldPoint from, WorldPoint to, int maxStep) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        if (chebyshev <= maxStep) {
            return to;
        }
        double scale = (double) maxStep / chebyshev;
        return new WorldPoint(
                from.getX() + (int) Math.round(dx * scale),
                from.getY() + (int) Math.round(dy * scale),
                from.getPlane());
    }

    private boolean moveToward(WorldPoint playerLocation, WorldPoint target, int arrivalDistance) {
        if (playerLocation == null || target == null || playerLocation.distanceTo(target) <= arrivalDistance) {
            return false;
        }

        observeProgress(playerLocation);
        WorldPoint routeTarget = avoidSouthwestNoGoArea(playerLocation, target);
        long now = System.currentTimeMillis();
        if (!routeTarget.equals(target) && now - lastSouthwestDetourLogAt >= WATCHDOG_LOG_INTERVAL_MILLIS) {
            Microbot.log("Pest Control avoiding southwest no-go grid via " + routeTarget);
            lastSouthwestDetourLogAt = now;
        }
        if (openBlockingGate(playerLocation, routeTarget)) {
            return true;
        }
        if (handleMovementBrawlerObstruction(playerLocation, routeTarget)) {
            return true;
        }
        boolean isMoving = Rs2Player.isMoving();
        long retryMillis = isMoving ? MOVEMENT_RETRY_MOVING_MILLIS : MOVEMENT_RETRY_IDLE_MILLIS;
        if (now - lastMovementCommandAt >= retryMillis) {
            WorldPoint clickTarget = stepTowards(playerLocation, routeTarget, MINIMAP_STEP_DISTANCE);
            // walkFastCanvas prefers a direct scene click when the tile is visible,
            // and only falls back to the minimap when it is not.
            Rs2Walker.walkFastCanvas(clickTarget);
            // Throttle the attempt even when the walker cannot confirm dispatch.
            // Progress is still based only on observed player movement.
            lastMovementCommandAt = now;
        }
        return true;
    }

    private boolean moveTowardPortal(
            WorldPoint playerLocation,
            WorldPoint target,
            int arrivalDistance,
            Portal portal) {
        maybeTurnCameraTowardPortal(playerLocation, portal);
        WorldPoint perimeterWaypoint = southPerimeterWaypoint(playerLocation, portal);
        if (perimeterWaypoint != null) {
            if (System.currentTimeMillis() < perimeterDetourUntil) {
                perimeterWaypoint = regionPoint(
                        playerLocation,
                        perimeterWaypoint.getRegionX(),
                        SOUTH_PERIMETER_DETOUR_REGION_Y);
            }
            return moveToward(
                    playerLocation,
                    perimeterWaypoint,
                    PERIMETER_WAYPOINT_ARRIVAL_DISTANCE);
        }
        return moveToward(playerLocation, target, arrivalDistance);
    }

    private void maybeTurnCameraTowardPortal(WorldPoint playerLocation, Portal portal) {
        if (playerLocation == null || portal == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastCameraPivotPortal == portal
                || now - lastPortalCameraTurnAt < MOVEMENT_RETRY_MOVING_MILLIS) {
            return;
        }

        WorldPoint portalLocation = logicalPortalLocation(portal, playerLocation);
        LocalPoint portalLocal = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            LocalPoint npcLocal = Microbot.getRs2NpcCache().query()
                    .withName("portal")
                    .where(npc -> npc.getNpc() != null
                            && !npc.getNpc().isDead()
                            && matchesPortal(npc, portal))
                    .toList()
                    .stream()
                    .map(Rs2NpcModel::getLocalLocation)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (npcLocal != null) {
                return npcLocal;
            }
            return LocalPoint.fromWorld(
                    Microbot.getClient().getTopLevelWorldView(), portalLocation);
        }
        ).orElse(null);
        if (portalLocal == null) {
            return;
        }

        Rs2Camera.turnTo(portalLocal);
        lastPortalCameraTurnAt = now;
        lastCameraPivotPortal = portal;
        Microbot.log("Pest Control camera pivot: " + portal
                + " portal at distance " + playerLocation.distanceTo(portalLocation));
    }

    synchronized void noteRoundOutcome(boolean won) {
        if (!hasActiveRoundResultWindow()) {
            return;
        }
        pendingRoundTeamOutcome = won ? TeamOutcome.WON : TeamOutcome.LOST;
    }

    void noteShieldDrop(Portal portal) {
        if (portal == null) {
            return;
        }
        if (portal.hasShield) {
            shieldDropCount++;
        }
        portal.setHasShield(false);
    }

    synchronized void recordAwardedPoints(int points) {
        if (points <= 0) {
            return;
        }

        if (hasActiveRoundResultWindow()) {
            confirmPendingRoundReward("AWARD_CHAT");
            int newlyObservedPoints = newlyAwardedPoints(pendingRoundAwardedPoints, points);
            pendingRoundAwardedPoints = Math.max(pendingRoundAwardedPoints, points);
            sessionAwardedPoints += newlyObservedPoints;
            refreshSessionPointsEarned();
        }
    }

    synchronized void recordTotalPoints(int points) {
        if (points >= 0) {
            if (hasActiveRoundResultWindow()) {
                pendingRoundObservedPoints = points;
                int startingPoints = roundCompletionPending
                        ? pendingRoundStartingPoints
                        : currentRoundStartingPoints;
                if (startingPoints >= 0 && points > startingPoints) {
                    confirmPendingRoundReward("POINT_DELTA");
                }
            }
            if (sessionStartingPoints < 0) {
                sessionStartingPoints = points;
            }
            totalPoints = points;
            refreshSessionPointsEarned();
        }
    }

    private void refreshSessionPointsEarned() {
        sessionPointsEarned = reconcileSessionPoints(
                sessionAwardedPoints,
                sessionStartingPoints,
                totalPoints);
    }

    private boolean hasActiveRoundResultWindow() {
        return wasInPestControl || roundCompletionPending;
    }

    private void confirmPendingRoundReward(String source) {
        pendingRoundRewardConfirmed = true;
        if ("AWARD_CHAT".equals(source) || "NONE".equals(pendingRoundRewardSource)) {
            pendingRoundRewardSource = source;
        }
    }

    private synchronized void startRoundAccounting() {
        pendingRoundTeamOutcome = TeamOutcome.UNKNOWN;
        pendingRoundRewardConfirmed = false;
        pendingRoundRewardSource = "NONE";
        pendingRoundAwardedPoints = 0;
        currentRoundStartingPoints = totalPoints;
        pendingRoundStartingPoints = -1;
        pendingRoundObservedPoints = -1;
        pendingRoundFinalActivity = -1;
        pendingRoundDestroyedPortals = 0;
    }

    private synchronized void recordCompletedRound() {
        RoundOutcome outcome = resolveRoundOutcome(pendingRoundRewardConfirmed);
        String resultSource = pendingRoundRewardConfirmed
                ? pendingRoundRewardSource
                : pendingRoundTeamOutcome == TeamOutcome.LOST
                        ? "LOSS_CHAT"
                        : hasReliableZeroPointDelta()
                                ? "NO_POINT_DELTA"
                                : "NO_REWARD_EVIDENCE";
        int pointDelta = observedRoundPointDelta();
        lastRoundResultSummary = (outcome == RoundOutcome.WON ? "WIN" : "NON-WIN")
                + " / " + resultSource;

        Microbot.log("Pest Control round accounted: " + outcome
                + " (team " + pendingRoundTeamOutcome
                + ", source " + resultSource
                + ", points " + formatPointDelta(pointDelta)
                + ", destroyed " + pendingRoundDestroyedPortals + "/" + portals.size()
                + ", activity " + formatActivity(pendingRoundFinalActivity) + ")");

        roundsPlayed++;
        if (outcome == RoundOutcome.WON) {
            roundsWon++;
        }
        roundsLost = reconcileLostRounds(roundsPlayed, roundsWon);
        Microbot.log("Pest Control session: " + roundsPlayed + " played, "
                + roundsWon + " won, " + roundsLost + " lost, "
                + sessionPointsEarned + " points gained");
        roundCompletionPending = false;
        pendingRoundTeamOutcome = TeamOutcome.UNKNOWN;
        pendingRoundRewardConfirmed = false;
        pendingRoundRewardSource = "NONE";
        pendingRoundAwardedPoints = 0;
        currentRoundStartingPoints = -1;
        pendingRoundStartingPoints = -1;
        pendingRoundObservedPoints = -1;
        pendingRoundFinalActivity = -1;
        pendingRoundDestroyedPortals = 0;
    }

    static RoundOutcome resolveRoundOutcome(boolean rewardConfirmed) {
        return rewardConfirmed ? RoundOutcome.WON : RoundOutcome.LOST;
    }

    static int reconcileLostRounds(int played, int won) {
        return Math.max(0, played - won);
    }

    static int newlyAwardedPoints(int previouslyObserved, int awardedPoints) {
        return Math.max(0, awardedPoints - Math.max(0, previouslyObserved));
    }

    static int reconcileSessionPoints(int awardedPoints, int startingPoints, int observedTotalPoints) {
        int observedDelta = startingPoints >= 0 && observedTotalPoints >= 0
                ? Math.max(0, observedTotalPoints - startingPoints)
                : 0;
        return Math.max(Math.max(0, awardedPoints), observedDelta);
    }

    static boolean shouldFinalizeRound(
            boolean rewardConfirmed,
            TeamOutcome teamOutcome,
            long elapsedMillis,
            long graceMillis) {
        return rewardConfirmed
                || teamOutcome == TeamOutcome.LOST
                || elapsedMillis >= graceMillis;
    }

    private boolean hasReliableZeroPointDelta() {
        return pendingRoundStartingPoints >= 0
                && pendingRoundObservedPoints >= 0
                && pendingRoundObservedPoints <= pendingRoundStartingPoints;
    }

    private int observedRoundPointDelta() {
        if (pendingRoundStartingPoints < 0 || pendingRoundObservedPoints < 0) {
            return -1;
        }
        return Math.max(0, pendingRoundObservedPoints - pendingRoundStartingPoints);
    }

    private static String formatPointDelta(int pointDelta) {
        return pointDelta < 0 ? "unknown" : "+" + pointDelta;
    }

    private String roundResultSummary() {
        if (!roundCompletionPending) {
            return lastRoundResultSummary;
        }
        if (pendingRoundRewardConfirmed) {
            return "Pending WIN / " + pendingRoundRewardSource;
        }
        return "Pending / team " + pendingRoundTeamOutcome;
    }

    private static String formatActivity(int activityPercent) {
        return activityPercent < 0 ? "unknown" : activityPercent + "%";
    }

    private void finalisePendingRoundIfReady() {
        if (!roundCompletionPending) {
            return;
        }
        if (!shouldFinalizeRound(
                pendingRoundRewardConfirmed,
                pendingRoundTeamOutcome,
                System.currentTimeMillis() - lastRoundExitAt,
                ROUND_OUTCOME_GRACE_MILLIS)) {
            return;
        }
        recordCompletedRound();
    }

    /**
     * Once outside the Void Knight enclosure, cross between the east and west
     * portal lanes around the south fence. This avoids repeatedly clicking
     * through a closed gate while still keeping the player in the pest lanes.
     */
    private static WorldPoint southPerimeterWaypoint(WorldPoint playerLocation, Portal portal) {
        if (playerLocation == null || portal == null) {
            return null;
        }

        int regionX = playerLocation.getRegionX();
        int regionY = playerLocation.getRegionY();
        boolean westOutside = regionX <= WEST_PERIMETER_REGION_X + 1;
        boolean eastOutside = regionX >= EAST_PERIMETER_REGION_X - 1;
        boolean southOutside = regionY <= SOUTH_PERIMETER_REGION_Y + 2;

        if (portal == BLUE || portal == YELLOW) {
            if (westOutside && !southOutside) {
                return regionPoint(playerLocation, WEST_PERIMETER_REGION_X, SOUTH_PERIMETER_REGION_Y);
            }
            if (portal == BLUE
                    && southOutside
                    && regionX < EAST_PERIMETER_REGION_X - PERIMETER_WAYPOINT_ARRIVAL_DISTANCE) {
                return regionPoint(playerLocation, EAST_PERIMETER_REGION_X, SOUTH_PERIMETER_REGION_Y);
            }
        } else if (portal == PURPLE || portal == RED) {
            if (eastOutside && !southOutside) {
                return regionPoint(playerLocation, EAST_PERIMETER_REGION_X, SOUTH_PERIMETER_REGION_Y);
            }
            if (portal == PURPLE
                    && southOutside
                    && regionX > WEST_PERIMETER_REGION_X + PERIMETER_WAYPOINT_ARRIVAL_DISTANCE) {
                return regionPoint(playerLocation, WEST_PERIMETER_REGION_X, SOUTH_PERIMETER_REGION_Y);
            }
        }
        return null;
    }

    private static WorldPoint regionPoint(WorldPoint playerLocation, int regionX, int regionY) {
        return WorldPoint.fromRegion(
                playerLocation.getRegionID(),
                regionX,
                regionY,
                playerLocation.getPlane());
    }

    /**
     * Own passage out of the central enclosure before any portal or Spinner
     * interaction is allowed. This prevents direct NPC clicks from repeatedly
     * targeting an otherwise visible portal through a closed perimeter gate.
     */
    private boolean ensurePortalLaneAccess(WorldPoint playerLocation, Portal portal) {
        if (playerLocation == null || portal == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        gateReuseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Fence and boundary tiles must not release an in-flight crossing.
        // Finish it even when crowd selection changes the portal mid-step.
        if (lastGateLocation != null && activeGateCrossingTarget != null) {
            openBlockingGate(playerLocation, activeGateCrossingTarget);
            if (lastGateLocation != null) {
                return true;
            }
        }
        if (!isInsideCentralEnclosure(playerLocation)) {
            return false;
        }

        boolean east = isEastSide(portal);
        String side = east ? "east" : "west";
        WorldPoint gateCenter = regionPoint(
                playerLocation,
                east ? EAST_LANE_GATE_REGION_X : WEST_LANE_GATE_REGION_X,
                LANE_GATE_REGION_Y);
        WorldPoint innerApproach = regionPoint(
                playerLocation,
                east ? EAST_LANE_INNER_REGION_X : WEST_LANE_INNER_REGION_X,
                LANE_GATE_REGION_Y);
        WorldPoint outerCrossing = regionPoint(
                playerLocation,
                east ? EAST_LANE_OUTER_REGION_X : WEST_LANE_OUTER_REGION_X,
                LANE_GATE_REGION_Y);

        if (playerLocation.distanceTo(innerApproach) > LANE_GATE_APPROACH_DISTANCE) {
            transitionTo(RuntimeState.OPEN_GATE,
                    "approaching " + side + " lane gate for " + portal);
            moveToward(playerLocation, innerApproach, LANE_GATE_APPROACH_DISTANCE);
            return true;
        }

        List<Rs2TileObjectModel> nearbyGateLeaves = Microbot.getRs2TileObjectCache().query()
                .withName("Gate")
                .within(BLOCKING_GATE_RADIUS)
                .toListOnClientThread()
                .stream()
                .filter(candidate -> {
                    WorldPoint location = templateLocation(candidate);
                    return location != null
                            && location.distanceTo(gateCenter) <= LANE_GATE_MATCH_DISTANCE;
                })
                .collect(Collectors.toList());

        Rs2TileObjectModel destroyedGate = nearbyGateLeaves.stream()
                .filter(PestControlScript::isDestroyedGate)
                .min(Comparator.comparingInt(candidate ->
                        playerLocation.distanceTo(templateLocation(candidate))))
                .orElse(null);
        if (destroyedGate != null) {
            WorldPoint gateLocation = templateLocation(destroyedGate);
            lastGateLocation = null;
            activeGateCrossingTarget = null;
            transitionTo(RuntimeState.OPEN_GATE,
                    "crossing destroyed " + side + " lane gate for " + portal);
            long now = System.currentTimeMillis();
            if (now - lastMovementCommandAt >= MOVEMENT_RETRY_IDLE_MILLIS) {
                Rs2Walker.walkFastCanvas(outerCrossing);
                lastMovementCommandAt = now;
            }
            if (now - lastGateDiagnosticAt >= WATCHDOG_LOG_INTERVAL_MILLIS) {
                Microbot.log("Pest Control bypassing destroyed " + side
                        + " lane gate at " + gateLocation + " for " + portal + " portal");
                lastGateDiagnosticAt = now;
            }
            return true;
        }

        Rs2TileObjectModel closedGate = nearbyGateLeaves.stream()
                .filter(this::hasOpenAction)
                .filter(candidate -> !gateReuseCooldowns.containsKey(templateLocation(candidate)))
                .min(Comparator.comparingInt(candidate ->
                        playerLocation.distanceTo(templateLocation(candidate))))
                .orElse(null);
        if (closedGate != null) {
            WorldPoint gateLocation = templateLocation(closedGate);
            transitionTo(RuntimeState.OPEN_GATE,
                    "opening " + side + " lane gate for " + portal);
            long now = System.currentTimeMillis();
            boolean dispatched = closedGate.click("Open");
            lastMovementCommandAt = now;
            if (dispatched) {
                lastGateLocation = gateLocation;
                activeGateCrossingTarget = outerCrossing;
                lastGateInteractionAt = now;
                Microbot.log("Pest Control opened " + side + " lane gate at "
                        + gateLocation + " for " + portal + " portal");
            }
            return true;
        }

        Rs2TileObjectModel openGate = nearbyGateLeaves.stream()
                .filter(this::hasCloseAction)
                .filter(candidate -> !gateReuseCooldowns.containsKey(templateLocation(candidate)))
                .min(Comparator.comparingInt(candidate ->
                        playerLocation.distanceTo(templateLocation(candidate))))
                .orElse(null);
        if (openGate != null) {
            long now = System.currentTimeMillis();
            lastGateLocation = templateLocation(openGate);
            activeGateCrossingTarget = outerCrossing;
            lastGateInteractionAt = now - GATE_OPEN_ANIMATION_MILLIS;
            transitionTo(RuntimeState.OPEN_GATE,
                    "committing through open " + side + " lane gate for " + portal);
            openBlockingGate(playerLocation, outerCrossing);
            return true;
        }

        transitionTo(RuntimeState.OPEN_GATE,
                "waiting for " + side + " lane gate state for " + portal);
        long now = System.currentTimeMillis();
        if (now - lastGateDiagnosticAt >= WATCHDOG_LOG_INTERVAL_MILLIS) {
            Microbot.log("Pest Control gate diagnostic: no open/closed leaf observed near "
                    + gateCenter + " for " + portal + "; holding safe-side position");
            lastGateDiagnosticAt = now;
        }
        return true;
    }

    private static boolean isInsideCentralEnclosure(WorldPoint point) {
        return point != null
                && point.getRegionX() >= WEST_LANE_GATE_REGION_X
                && point.getRegionX() <= EAST_LANE_GATE_REGION_X
                && point.getRegionY() > SOUTH_PERIMETER_DETOUR_REGION_Y;
    }

    private boolean claimAttackCommand(String commandKey) {
        long now = System.currentTimeMillis();
        boolean sameTarget = Objects.equals(lastAttackCommandKey, commandKey);
        long retryMillis = sameTarget
                ? SAME_TARGET_ATTACK_RETRY_MILLIS
                : ATTACK_RETRY_MILLIS;
        if (now - lastAttackCommandAt < retryMillis) {
            suppressedDuplicateAttackCommands++;
            if (now - lastDuplicateAttackLogAt >= DUPLICATE_COMMAND_LOG_MILLIS) {
                Microbot.log("Pest Control command guard: suppressed "
                        + suppressedDuplicateAttackCommands + " duplicate attack clicks; latest="
                        + commandKey);
                suppressedDuplicateAttackCommands = 0;
                lastDuplicateAttackLogAt = now;
            }
            return false;
        }
        lastAttackCommandAt = now;
        lastAttackCommandKey = commandKey;
        return true;
    }

    private boolean dispatchAttack(Rs2NpcModel target) {
        String commandKey = attackCommandKey(target, "npc");
        if (target == null || !claimAttackCommand(commandKey)) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                target.getNpc() != null
                        && !target.getNpc().isDead()
                        && hasAttackAction(target)
                        && target.click("Attack")
        ).orElse(false);
    }

    private boolean dispatchPortalAttack(Rs2NpcModel target) {
        String commandKey = attackCommandKey(target, "portal");
        if (target == null || !claimAttackCommand(commandKey)) {
            return false;
        }
        return attackPortal(target);
    }

    private static String attackCommandKey(Rs2NpcModel target, String kind) {
        if (target == null) {
            return kind + ":missing";
        }
        return kind + ":" + target.getId() + ":" + target.getIndex();
    }

    private static boolean isNpcOnCanvas(Rs2NpcModel target) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                target != null
                        && target.getNpc() != null
                        && !target.getNpc().isDead()
                        && target.getLocalLocation() != null
                        && Rs2Camera.isTileOnScreen(target.getLocalLocation())
        ).orElse(false);
    }

    private Portal chooseOpeningPortal() {
        return combatPlan.openingPortal(Rs2Random.between(0, 10_000));
    }

    private boolean moveToOpeningSide() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || openingPortal == null) {
            return false;
        }

        if (ensurePortalLaneAccess(playerLocation, openingPortal)) {
            return true;
        }

        PestControlLoadout openingLoadout = combatPlan.loadoutForPortal(openingPortal);
        int engagementDistance = engagementDistance(openingLoadout.combatStyle);
        WorldPoint target = portalApproachLocation(openingPortal, playerLocation, engagementDistance);
        int arrivalDistance = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? STAGING_ARRIVAL_DISTANCE
                : MELEE_ENGAGEMENT_DISTANCE;
        transitionTo(RuntimeState.OPENING_SIDE, openingPortal + " portal");
        if (playerLocation.distanceTo(target) <= arrivalDistance) {
            openingSideReached = true;
            Microbot.log("Pest Control staged for " + openingLoadout.label
                    + " in front of " + openingPortal + " portal");
            return false;
        }

        return moveTowardPortal(playerLocation, target, arrivalDistance, openingPortal);
    }

    private boolean confirmAutoRetaliateOff() {
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1) {
            if (!autoRetaliateConfirmedOff) {
                Microbot.log("Pest Control confirmed Auto Retaliate is OFF");
            }
            autoRetaliateConfirmedOff = true;
            return true;
        }

        if (!autoRetaliateDisableLogged) {
            Microbot.log("Pest Control disabling Auto Retaliate");
            autoRetaliateDisableLogged = true;
        }

        autoRetaliateConfirmedOff = Rs2Combat.setAutoRetaliate(false)
                && Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1;
        if (autoRetaliateConfirmedOff) {
            Microbot.log("Pest Control confirmed Auto Retaliate is OFF");
        }
        return autoRetaliateConfirmedOff;
    }

    public boolean run(PestControlConfig config) {
        this.config = config;
        combatPlan = new PestControlCombatPlan(config);
        combatPlan.validationMessages().forEach(message ->
                Microbot.log("Pest Control combat config: " + message));
        Microbot.log("Pest Control combat styles: " + combatPlan.enabledStyles());
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
        Microbot.log("Pest Control mouse speed: Moderate");
        resetPortals();
        selectedPortal = null;
        openingPortal = null;
        openingSideReached = false;
        pendingPostRoundRestore = false;
        autoRetaliateConfirmedOff = false;
        autoRetaliateDisableLogged = false;
        activeLoadoutKey = null;
        voidHelmetSwitchingEnabled = null;
        loadoutRetryAfterByKey.clear();
        startupCombatPrepared = false;
        startupAutocastPrepared = !combatPlan.supports(PestControlCombatStyle.MAGIC)
                || config.magicCastingMode() != PestControlMagicMode.AUTOCAST;
        loadoutFailuresLogged.clear();
        missingAttackOptionsLogged.clear();
        attackOptionIndexByWeaponStyle.clear();
        activeAttackOptionKey = null;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        runtimeState = RuntimeState.STOPPED;
        runtimeDetail = "";
        stateEnteredAt = 0L;
        lastProgressAt = System.currentTimeMillis();
        lastWatchdogLogAt = 0L;
        lastMovementCommandAt = 0L;
        lastAttackCommandAt = 0L;
        lastAttackCommandKey = null;
        lastDuplicateAttackLogAt = 0L;
        suppressedDuplicateAttackCommands = 0;
        lastPortalCameraTurnAt = 0L;
        lastCameraPivotPortal = null;
        lastGateInteractionAt = 0L;
        lastGateLocation = null;
        activeGateCrossingTarget = null;
        gateReuseCooldowns.clear();
        lastGateDiagnosticAt = 0L;
        perimeterDetourUntil = 0L;
        clearSpinnerCommitment();
        clearBrawlerCommitment();
        lastProgressLocation = null;
        quickPrayerHandled = false;
        activityRecoveryActive = false;
        stagingPortal = null;
        loginUnavailableSince = 0L;
        lastRoundExitAt = 0L;
        roundCompletionPending = false;
        overlayLocation = "Starting";
        overlayActivityPercent = -1;
        overlayTargetPortal = null;
        overlayTargetCrowd = 0;
        overlayTargetHasAttackAction = false;
        overlayCombatWeapon = combatPlan.primaryLoadout().weapon.isEmpty()
                ? "Unknown"
                : combatPlan.primaryLoadout().weapon;
        PestControlLoadout primaryLoadout = combatPlan.primaryLoadout();
        overlayCombatStyle = primaryLoadout.attackOption == null
                ? config.magicCastingMode() + " (pending)"
                : primaryLoadout.attackOption + " (pending)";
        sessionPointsEarned = 0;
        sessionAwardedPoints = 0;
        sessionStartingPoints = -1;
        totalPoints = -1;
        roundsPlayed = 0;
        roundsWon = 0;
        roundsLost = 0;
        lastRoundResultSummary = "None";
        pendingRoundTeamOutcome = TeamOutcome.UNKNOWN;
        pendingRoundRewardConfirmed = false;
        pendingRoundRewardSource = "NONE";
        pendingRoundAwardedPoints = 0;
        currentRoundStartingPoints = -1;
        pendingRoundStartingPoints = -1;
        pendingRoundObservedPoints = -1;
        pendingRoundFinalActivity = -1;
        pendingRoundDestroyedPortals = 0;
        transitionTo(RuntimeState.INITIALISING, "starting script");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    overlayLocation = "Loading";
                    overlayActivityPercent = -1;
                    overlayTargetPortal = null;
                    overlayTargetCrowd = 0;
                    overlayTargetHasAttackAction = false;
                    long now = System.currentTimeMillis();
                    if (loginUnavailableSince == 0L) {
                        loginUnavailableSince = now;
                    }
                    boolean recentRoundExit = lastRoundExitAt > 0L
                            && now - lastRoundExitAt < ROUND_TRANSITION_LOGIN_GRACE_MILLIS;
                    boolean withinGrace = now - loginUnavailableSince
                            < ROUND_TRANSITION_LOGIN_GRACE_MILLIS;
                    if (wasInPestControl && withinGrace) {
                        return;
                    }
                    if (recentRoundExit && withinGrace) {
                        transitionTo(RuntimeState.REQUEUE, "round transition");
                    } else {
                        transitionTo(RuntimeState.INITIALISING, "waiting for login");
                    }
                    return;
                }
                loginUnavailableSince = 0L;
                if (!super.run()) return;
                if (!confirmAutoRetaliateOff()) {
                    transitionTo(RuntimeState.INITIALISING, "disabling Auto Retaliate");
                    return;
                }
                if (!prepareStartupCombat()) {
                    transitionTo(RuntimeState.INITIALISING, "preparing combat loadouts");
                    return;
                }

                final boolean isInPestControl = isInPestControl();
                final boolean isInBoat = isInBoat();
                overlayLocation = isInPestControl ? "Round" : isInBoat ? "Boat" : "Island";
                if (isInPestControl) {
                    handleRoundTick();
                } else {
                    handleLobbyTick(isInBoat);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                transitionTo(RuntimeState.ERROR,
                        ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            } finally {
                publishOverlaySnapshot();
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleRoundTick() {
        initialise = false;
        if (!wasInPestControl) {
            // A fast boat can launch before the normal lobby grace expires. At
            // this point no further boat-overlay evidence can arrive, so finish
            // the previous result before initialising the new round.
            if (roundCompletionPending) {
                recordCompletedRound();
            }
            lastRoundExitAt = 0L;
            autoRetaliateConfirmedOff = false;
            autoRetaliateDisableLogged = false;
            openingPortal = chooseOpeningPortal();
            openingSideReached = false;
            selectedPortal = null;
            stagingPortal = null;
            quickPrayerHandled = false;
            activityRecoveryActive = false;
            lastProgressLocation = null;
            lastProgressAt = System.currentTimeMillis();
            lastMovementCommandAt = 0L;
            lastAttackCommandAt = 0L;
            lastAttackCommandKey = null;
            suppressedDuplicateAttackCommands = 0;
            lastPortalCameraTurnAt = 0L;
            lastCameraPivotPortal = null;
            lastGateInteractionAt = 0L;
            lastGateLocation = null;
            activeGateCrossingTarget = null;
            gateReuseCooldowns.clear();
            lastGateDiagnosticAt = 0L;
            perimeterDetourUntil = 0L;
            clearSpinnerCommitment();
            clearBrawlerCommitment();
            startRoundAccounting();
            Microbot.log("Pest Control opening side: " + openingPortal + " portal");
        }

        wasInPestControl = true;
        if (!confirmAutoRetaliateOff()) {
            transitionTo(RuntimeState.INITIALISING, "confirming Auto Retaliate OFF");
            return;
        }

        pendingPostRoundRestore = false;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        handleQuickPrayerOnce();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        int activityPercent = getActivityPercent();
        overlayActivityPercent = activityPercent;
        updateActivityRecovery(activityPercent);
        if (activityRecoveryActive && recoverActivity(playerLocation, activityPercent)) {
            overlayTargetPortal = null;
            overlayTargetCrowd = 0;
            overlayTargetHasAttackAction = false;
            runWatchdog(playerLocation);
            return;
        }

        PortalTarget portalTarget = selectAdaptivePortalTarget();
        if (portalTarget != null) {
            overlayTargetPortal = portalTarget.portal;
            overlayTargetCrowd = portalTarget.nearbyPlayers;
            overlayTargetHasAttackAction = portalTarget.attackActionAvailable;
            openingSideReached = true;
            handlePortalTarget(portalTarget, playerLocation);
            runWatchdog(playerLocation);
            return;
        }
        overlayTargetPortal = null;
        overlayTargetCrowd = 0;
        overlayTargetHasAttackAction = false;

        if (!openingSideReached && moveToOpeningSide()) {
            runWatchdog(playerLocation);
            return;
        }

        if (shouldHoldOpeningSide() && attackOpeningSideTarget(playerLocation)) {
            runWatchdog(playerLocation);
            return;
        }

        Portal desiredStagingPortal = selectStagingPortal(playerLocation);
        if (desiredStagingPortal != null) {
            if (moveToShieldedPortal(desiredStagingPortal, playerLocation)) {
                runWatchdog(playerLocation);
                return;
            }
            if (attackSpinner()) {
                runWatchdog(playerLocation);
                return;
            }
            if (isPlayerInteracting()) {
                transitionTo(RuntimeState.HOLDING_COMBAT, "finishing current fallback target");
                return;
            }
            transitionTo(RuntimeState.WAITING_FOR_PORTAL,
                    "staged for " + desiredStagingPortal + " shield drop");
            return;
        }

        disableSpecialAttackIfEnabled();
        if (!preparePrimaryLoadout()) {
            transitionTo(RuntimeState.INITIALISING, "restoring Style 1 loadout");
            runWatchdog(playerLocation);
            return;
        }

        if (attackSpinner()) {
            runWatchdog(playerLocation);
            return;
        }

        if (isPlayerInteracting()) {
            transitionTo(RuntimeState.HOLDING_COMBAT, "finishing current fallback target");
            return;
        }

        transitionTo(RuntimeState.WAITING_FOR_PORTAL, "no surviving shielded portal");
    }

    private Portal selectStagingPortal(WorldPoint playerLocation) {
        List<Portal> candidates = portals.stream()
                .filter(portal -> portal.hasShield && !destroyedPortals.contains(portal))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            stagingPortal = null;
            return null;
        }
        if (stagingPortal != null && candidates.contains(stagingPortal)) {
            return stagingPortal;
        }
        if (openingPortal != null && candidates.contains(openingPortal)) {
            stagingPortal = openingPortal;
            return stagingPortal;
        }
        stagingPortal = candidates.stream()
                .min(Comparator
                        .comparingInt((Portal portal) -> playerLocation == null
                                ? Integer.MAX_VALUE
                                : regionDistance(playerLocation, portal.getRegionX(), portal.getRegionY()))
                        .thenComparingInt(portal -> portal == PURPLE ? 0 : 1))
                .orElse(candidates.get(0));
        Microbot.log("Pest Control staging target: " + stagingPortal
                + " (surviving shielded portal)");
        return stagingPortal;
    }

    private boolean moveToShieldedPortal(Portal portal, WorldPoint playerLocation) {
        if (portal == null || playerLocation == null) {
            return false;
        }

        if (ensurePortalLaneAccess(playerLocation, portal)) {
            return true;
        }

        PestControlLoadout stagingLoadout = combatPlan.loadoutForPortal(portal);
        int engagementDistance = engagementDistance(stagingLoadout.combatStyle);
        WorldPoint target = portalApproachLocation(portal, playerLocation, engagementDistance);
        int arrivalDistance = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? STAGING_ARRIVAL_DISTANCE
                : MELEE_ENGAGEMENT_DISTANCE;
        if (playerLocation.distanceTo(target) <= STAGING_ARRIVAL_DISTANCE) {
            if (prepareLoadoutForPortal(portal) == null) {
                transitionTo(RuntimeState.INITIALISING,
                        "preparing " + portal + " staging loadout");
                return true;
            }
            return false;
        }

        long shieldedRemaining = portals.stream()
                .filter(candidate -> candidate.hasShield && !destroyedPortals.contains(candidate))
                .count();
        transitionTo(RuntimeState.PREPOSITION_PORTAL,
                portal + (shieldedRemaining == 1
                        ? " sole shield pending"
                        : " surviving shield staging"));
        moveTowardPortal(playerLocation, target, arrivalDistance, portal);
        if (prepareLoadoutForPortal(portal) == null) {
            transitionTo(RuntimeState.INITIALISING,
                    "preparing " + portal + " staging loadout");
        }
        return true;
    }

    private void handleLobbyTick(boolean isInBoat) {
        if (wasInPestControl) {
            lastRoundExitAt = System.currentTimeMillis();
            roundCompletionPending = true;
            pendingRoundFinalActivity = overlayActivityPercent;
            pendingRoundDestroyedPortals = destroyedPortals.size();
            pendingRoundStartingPoints = currentRoundStartingPoints;
            Rs2Walker.clearWalkingRoute("pest-control:round-ended");
            wasInPestControl = false;
            pendingPostRoundRestore = true;
            selectedPortal = null;
            openingPortal = null;
            openingSideReached = false;
            quickPrayerHandled = false;
            activityRecoveryActive = false;
            lastMovementCommandAt = 0L;
            lastAttackCommandAt = 0L;
            lastPortalCameraTurnAt = 0L;
            perimeterDetourUntil = 0L;
            clearSpinnerCommitment();
            clearBrawlerCommitment();
            boardingAttemptPending = false;
            resetPortals();
            Microbot.log("Pest Control round ended; destroyed "
                    + pendingRoundDestroyedPortals + "/" + portals.size()
                    + ", activity " + formatActivity(pendingRoundFinalActivity)
                    + "; reboarding immediately");
        }

        overlayActivityPercent = -1;
        overlayTargetPortal = null;
        overlayTargetCrowd = 0;
        overlayTargetHasAttackAction = false;
        if (isInBoat) {
            refreshTotalPointsFromBoatOverlay();
        }
        finalisePendingRoundIfReady();

        if (initialise && !isInBoat) {
            transitionTo(RuntimeState.INITIALISING, "checking Pest Control island");
            if (Rs2Player.getWorld() != config.world()) {
                Microbot.hopToWorld(config.world());
                sleepUntil(() -> Rs2Player.getWorld() == config.world(), 7000);
            }

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation != null
                    && playerLocation.getRegionID() == 10537
                    && Rs2Player.getWorld() == config.world()) {
                initialise = false;
            } else {
                transitionTo(RuntimeState.TRAVELLING, "Pest Control island");
                Rs2Walker.walkTo(new WorldPoint(2667, 2653, 0));
                return;
            }
        }

        if (!isInBoat && !initialise) {
            transitionTo(RuntimeState.REQUEUE,
                    boardingAttemptPending ? "waiting for boat entry" : "boarding now");
            boardBoat();
            return;
        }

        resetPortals();
        openingSideReached = false;
        if (isInBoat) {
            transitionTo(RuntimeState.BOAT, "waiting for launch");
            lastBoardingAttemptAt = 0L;
            boardingAttemptPending = false;
            if (pendingPostRoundRestore) {
                pendingPostRoundRestore = !preparePrimaryLoadout();
            }
            if (config.alchInBoat() && !config.alchItem().equalsIgnoreCase("")) {
                Rs2Magic.alch(config.alchItem());
                sleep(Rs2Random.between(1600, 1800));
            }
        }
    }

    private void refreshTotalPointsFromBoatOverlay() {
        Integer points = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(
                    InterfaceID.PestLanderOverlay.PEST_LANDER_OVER_POINTS);
            if (widget == null) {
                return null;
            }

            Matcher matcher = POINT_COUNT.matcher(Text.removeTags(widget.getText()));
            if (!matcher.find()) {
                return null;
            }

            try {
                return Integer.parseInt(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }).orElse(null);
        if (points != null) {
            recordTotalPoints(points);
        }
    }

    private void handleQuickPrayerOnce() {
        if (quickPrayerHandled) {
            return;
        }
        quickPrayerHandled = true;
        int prayerLevel = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER)).orElse(0);
        if (isQuickPrayerEnabled()
                || prayerLevel == 0
                || !config.quickPrayer()) {
            return;
        }

        Rs2Widget.clickWidget(ComponentID.MINIMAP_QUICK_PRAYER_ORB);
    }

    private int getActivityPercent() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(
                    InterfaceID.PestStatusOverlay.ACTIVITY_CONTAINER);
            Widget progress = Microbot.getClient().getWidget(
                    InterfaceID.PestStatusOverlay.ACTIVITY_BAR);
            Widget containerBar = container == null ? null : container.getChild(0);
            Widget progressBar = progress == null ? null : progress.getChild(0);
            if (containerBar == null || progressBar == null || containerBar.getWidth() <= 0) {
                return -1;
            }
            return Math.max(0, Math.min(100, (int) Math.round(
                    100.0 * progressBar.getWidth() / containerBar.getWidth())));
        }).orElse(-1);
    }

    private void updateActivityRecovery(int activityPercent) {
        if (activityPercent < 0) {
            return;
        }
        int recoveryStart = Math.max(0, Math.min(100, config.activityRecoveryStart()));
        int recoveryTarget = Math.max(
                recoveryStart,
                Math.max(0, Math.min(100, config.activityRecoveryTarget())));
        if (activityPercent <= recoveryStart) {
            if (!activityRecoveryActive) {
                Microbot.log("Pest Control activity recovery started at " + activityPercent + "%");
            }
            activityRecoveryActive = true;
        } else if (activityPercent >= recoveryTarget) {
            if (activityRecoveryActive) {
                Microbot.log("Pest Control activity recovered to " + activityPercent + "%");
            }
            activityRecoveryActive = false;
        }
    }

    private boolean recoverActivity(WorldPoint playerLocation, int activityPercent) {
        disableSpecialAttackIfEnabled();
        PestControlLoadout activityLoadout = committedActivityLoadout();
        if (!prepareLoadout(activityLoadout)) {
            transitionTo(RuntimeState.INITIALISING, "preparing activity loadout");
            return true;
        }

        Rs2NpcModel spinner = nearestActivitySpinner(playerLocation);
        if (spinner != null) {
            maintainActivityWith(spinner, playerLocation);
            return true;
        }

        Rs2NpcModel attackableNpc = preferredActivityPest(playerLocation);
        if (attackableNpc != null) {
            maintainActivityWith(attackableNpc, playerLocation);
            return true;
        }

        if (isMaintainingActivityCombat()) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK,
                    "maintaining activity combat");
            return true;
        }

        // With no nearby fallback, keep pursuing a portal instead of wandering.
        return false;
    }

    private PestControlLoadout committedActivityLoadout() {
        if (selectedPortal != null && !destroyedPortals.contains(selectedPortal)) {
            return combatPlan.loadoutForPortal(selectedPortal);
        }
        if (stagingPortal != null && !destroyedPortals.contains(stagingPortal)) {
            return combatPlan.loadoutForPortal(stagingPortal);
        }
        return combatPlan.primaryLoadout();
    }

    private boolean attackOpeningSideTarget(WorldPoint playerLocation) {
        if (isMaintainingActivityCombat()) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "holding opening-side combat");
            return true;
        }

        Rs2NpcModel target = nearestActivitySpinner(playerLocation);
        if (target == null) {
            target = preferredActivityPest(playerLocation);
        }
        if (target == null) {
            return false;
        }

        maintainActivityWith(target, playerLocation);
        return true;
    }

    private static WorldPoint avoidSouthwestNoGoArea(WorldPoint from, WorldPoint target) {
        if (!routeCrossesSouthwestNoGoArea(from, target)) {
            return target;
        }

        List<WorldPoint> corners = Arrays.asList(
                new WorldPoint(SOUTHWEST_NO_GO_MIN_X - 1, SOUTHWEST_NO_GO_MIN_Y - 1, target.getPlane()),
                new WorldPoint(SOUTHWEST_NO_GO_MIN_X - 1, SOUTHWEST_NO_GO_MAX_Y + 1, target.getPlane()),
                new WorldPoint(SOUTHWEST_NO_GO_MAX_X + 1, SOUTHWEST_NO_GO_MIN_Y - 1, target.getPlane()),
                new WorldPoint(SOUTHWEST_NO_GO_MAX_X + 1, SOUTHWEST_NO_GO_MAX_Y + 1, target.getPlane()));

        return corners.stream()
                .filter(corner -> isSouthwestNoGoTile(from)
                        || !routeCrossesSouthwestNoGoArea(from, corner))
                .filter(corner -> !routeCrossesSouthwestNoGoArea(corner, target))
                .min(Comparator.comparingInt(corner ->
                        from.distanceTo(corner) + corner.distanceTo(target)))
                .orElse(target);
    }

    private static boolean routeCrossesSouthwestNoGoArea(WorldPoint from, WorldPoint target) {
        if (from == null || target == null
                || from.getPlane() != SOUTHWEST_NO_GO_PLANE
                || target.getPlane() != SOUTHWEST_NO_GO_PLANE) {
            return false;
        }
        if (isSouthwestNoGoTile(from) || isSouthwestNoGoTile(target)) {
            return true;
        }

        int steps = Math.max(Math.abs(target.getX() - from.getX()),
                Math.abs(target.getY() - from.getY()));
        for (int i = 1; i < steps; i++) {
            double progress = (double) i / steps;
            int x = (int) Math.round(from.getX() + (target.getX() - from.getX()) * progress);
            int y = (int) Math.round(from.getY() + (target.getY() - from.getY()) * progress);
            if (isSouthwestNoGoTile(new WorldPoint(x, y, from.getPlane()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSouthwestNoGoTile(WorldPoint point) {
        return point != null
                && point.getPlane() == SOUTHWEST_NO_GO_PLANE
                && point.getX() >= SOUTHWEST_NO_GO_MIN_X
                && point.getX() <= SOUTHWEST_NO_GO_MAX_X
                && point.getY() >= SOUTHWEST_NO_GO_MIN_Y
                && point.getY() <= SOUTHWEST_NO_GO_MAX_Y;
    }

    private boolean openBlockingGate(WorldPoint playerLocation, WorldPoint target) {
        long now = System.currentTimeMillis();
        gateReuseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (lastGateLocation != null) {
            long elapsed = now - lastGateInteractionAt;
            boolean gateObservedOpen = elapsed >= GATE_OPEN_ANIMATION_MILLIS
                    && isLogicalGateObservedOpen(lastGateLocation);
            if (hasPassedGate(playerLocation, lastGateLocation, target)) {
                registerLogicalGateCooldown(lastGateLocation, now);
                lastGateLocation = null;
                activeGateCrossingTarget = null;
            } else if (elapsed >= GATE_OPEN_CONFIRM_TIMEOUT_MILLIS
                    && !gateObservedOpen) {
                Microbot.log("Pest Control gate open was not confirmed at "
                        + lastGateLocation + "; issuing one fresh open attempt");
                lastGateLocation = null;
                activeGateCrossingTarget = null;
            } else if (elapsed >= GATE_CROSSING_TIMEOUT_MILLIS) {
                Microbot.log("Pest Control gate crossing timed out at " + lastGateLocation
                        + "; suppressing immediate reopen");
                registerLogicalGateCooldown(lastGateLocation, now);
                lastGateLocation = null;
                activeGateCrossingTarget = null;
            } else if (elapsed < GATE_OPEN_ANIMATION_MILLIS) {
                transitionTo(RuntimeState.OPEN_GATE, "waiting for gate to open");
                return true;
            } else if (!gateObservedOpen) {
                transitionTo(RuntimeState.OPEN_GATE, "waiting for open gate confirmation");
                return true;
            } else {
                transitionTo(RuntimeState.OPEN_GATE, "crossing opened gate");
                if (now - lastMovementCommandAt >= MOVEMENT_RETRY_IDLE_MILLIS) {
                    Rs2Walker.walkFastCanvas(gateCrossingPoint(lastGateLocation, target));
                    lastMovementCommandAt = now;
                }
                return true;
            }
        }

        List<Rs2TileObjectModel> gates = Microbot.getRs2TileObjectCache().query()
                .withName("Gate")
                .within(BLOCKING_GATE_RADIUS)
                .where(this::hasOpenAction)
                .toListOnClientThread();
        Rs2TileObjectModel gate = gates.stream()
                .filter(candidate -> !gateReuseCooldowns.containsKey(templateLocation(candidate)))
                .filter(candidate -> isOnMovementRoute(
                        playerLocation,
                        target,
                        templateLocation(candidate)))
                .min(Comparator.comparingInt(candidate ->
                        playerLocation.distanceTo(templateLocation(candidate))))
                .orElse(null);
        if (gate == null) {
            lastGateLocation = null;
            activeGateCrossingTarget = null;
            return false;
        }

        WorldPoint gateLocation = templateLocation(gate);
        transitionTo(RuntimeState.OPEN_GATE, "opening gate at " + gateLocation);
        boolean dispatched = gate.click("Open");
        lastMovementCommandAt = now;
        if (dispatched) {
            lastGateLocation = gateLocation;
            activeGateCrossingTarget = target;
            lastGateInteractionAt = now;
            Microbot.log("Pest Control opened blocking gate at " + gateLocation);
        }
        return true;
    }

    private boolean isLogicalGateObservedOpen(WorldPoint gateLocation) {
        if (gateLocation == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query()
                        .withName("Gate")
                        .within(BLOCKING_GATE_RADIUS)
                        .toList()
                        .stream()
                        .filter(this::hasCloseAction)
                        .anyMatch(candidate -> {
                            LocalPoint local = candidate.getLocalLocation();
                            if (local == null) {
                                return false;
                            }
                            WorldPoint location = WorldPoint.fromLocalInstance(
                                    Microbot.getClient(), local, candidate.getPlane());
                            return location != null
                                    && location.distanceTo(gateLocation)
                                    <= LANE_GATE_MATCH_DISTANCE;
                        })
        ).orElse(false);
    }

    private void registerLogicalGateCooldown(WorldPoint gateLocation, long now) {
        if (gateLocation == null) {
            return;
        }
        long expiresAt = now + GATE_REUSE_COOLDOWN_MILLIS;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                gateReuseCooldowns.put(new WorldPoint(
                        gateLocation.getX() + dx,
                        gateLocation.getY() + dy,
                        gateLocation.getPlane()), expiresAt);
            }
        }
    }

    private static WorldPoint gateCrossingPoint(WorldPoint gate, WorldPoint target) {
        int dx = Integer.compare(target.getX(), gate.getX());
        int dy = Integer.compare(target.getY(), gate.getY());
        return new WorldPoint(
                gate.getX() + dx * GATE_CROSSING_DISTANCE,
                gate.getY() + dy * GATE_CROSSING_DISTANCE,
                gate.getPlane());
    }

    private WorldPoint templateLocation(Rs2TileObjectModel object) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                object == null || object.getLocalLocation() == null
                        ? null
                        : WorldPoint.fromLocalInstance(
                                Microbot.getClient(),
                                object.getLocalLocation(),
                                object.getPlane())
        ).orElse(null);
    }

    private boolean hasOpenAction(Rs2TileObjectModel object) {
        if (object == null || isDestroyedGate(object)) {
            return false;
        }
        ObjectComposition composition = object.getObjectComposition();
        String[] actions = composition == null ? null : composition.getActions();
        return actions != null && Arrays.stream(actions)
                .anyMatch(action -> action != null && action.equalsIgnoreCase("Open"));
    }

    private static boolean isDestroyedGate(Rs2TileObjectModel object) {
        return object != null && isDestroyedGateId(object.getId());
    }

    static boolean isDestroyedGateId(int objectId) {
        return objectId == ObjectID.GATE_14245;
    }

    private boolean hasCloseAction(Rs2TileObjectModel object) {
        if (object == null) {
            return false;
        }
        ObjectComposition composition = object.getObjectComposition();
        String[] actions = composition == null ? null : composition.getActions();
        return actions != null && Arrays.stream(actions)
                .anyMatch(action -> action != null && action.equalsIgnoreCase("Close"));
    }

    private static boolean isOnMovementRoute(
            WorldPoint from,
            WorldPoint to,
            WorldPoint candidate) {
        if (from == null || to == null || candidate == null) {
            return false;
        }

        double routeX = to.getX() - from.getX();
        double routeY = to.getY() - from.getY();
        double routeLengthSquared = routeX * routeX + routeY * routeY;
        if (routeLengthSquared == 0) {
            return false;
        }

        double gateX = candidate.getX() - from.getX();
        double gateY = candidate.getY() - from.getY();
        double projection = (gateX * routeX + gateY * routeY) / routeLengthSquared;
        if (projection <= 0.0 || projection >= 1.0) {
            return false;
        }

        double closestX = from.getX() + projection * routeX;
        double closestY = from.getY() + projection * routeY;
        double offsetX = candidate.getX() - closestX;
        double offsetY = candidate.getY() - closestY;
        return offsetX * offsetX + offsetY * offsetY
                <= BLOCKING_GATE_ROUTE_WIDTH * BLOCKING_GATE_ROUTE_WIDTH;
    }

    private static boolean hasPassedGate(
            WorldPoint playerLocation,
            WorldPoint gateLocation,
            WorldPoint target) {
        if (playerLocation == null || gateLocation == null || target == null) {
            return false;
        }

        int targetX = target.getX() - gateLocation.getX();
        int targetY = target.getY() - gateLocation.getY();
        int playerX = playerLocation.getX() - gateLocation.getX();
        int playerY = playerLocation.getY() - gateLocation.getY();
        return playerX * targetX + playerY * targetY > 0;
    }

    private Rs2NpcModel nearestActivitySpinner(WorldPoint playerLocation) {
        return Microbot.getClientThread().invoke(() ->
                Microbot.getRs2NpcCache().query()
                        .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                        .where(npc -> isNearbyActivityTarget(npc, playerLocation))
                        .toList()
                        .stream()
                        .min(Comparator
                                .comparingInt((Rs2NpcModel npc) ->
                                        isActivityTargetAccessible(playerLocation, npc.getWorldLocation()) ? 0 : 1)
                                .thenComparingInt(PestControlScript::distanceFromPlayerInTiles))
                        .orElse(null));
    }

    private Rs2NpcModel preferredActivityPest(WorldPoint playerLocation) {
        return Microbot.getClientThread().invoke(() ->
                Microbot.getRs2NpcCache().query()
                        .where(npc -> isOrdinaryActivityTarget(npc, playerLocation))
                        .toList()
                        .stream()
                        .min(Comparator
                                .comparingInt((Rs2NpcModel npc) ->
                                        isActivityTargetAccessible(playerLocation, npc.getWorldLocation()) ? 0 : 1)
                                .thenComparingInt((Rs2NpcModel npc) ->
                                        "Torcher".equalsIgnoreCase(npc.getName()) ? 0 : 1)
                                .thenComparingInt(npc -> npc.getNpc().getCombatLevel())
                                .thenComparingInt(PestControlScript::distanceFromPlayerInTiles))
                        .orElse(null));
    }

    private boolean isNearbyActivityTarget(Rs2NpcModel npc, WorldPoint playerLocation) {
        return npc != null
                && npc.getNpc() != null
                && !npc.getNpc().isDead()
                && !isSouthwestNoGoTile(npc.getWorldLocation())
                && npc.getNpc().getCombatLevel() > 0
                && (npc.getNpc().getHealthScale() <= 0 || npc.getNpc().getHealthRatio() > 0)
                && distanceFromPlayerInTiles(npc) <= ACTIVITY_TARGET_RADIUS
                && hasAttackAction(npc);
    }

    private static int distanceFromPlayerInTiles(Rs2NpcModel npc) {
        if (npc == null) {
            return Integer.MAX_VALUE;
        }
        int localDistance = npc.getDistanceFromPlayer();
        if (localDistance == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (localDistance + 127) / 128);
    }

    private boolean isOrdinaryActivityTarget(Rs2NpcModel npc, WorldPoint playerLocation) {
        if (!isNearbyActivityTarget(npc, playerLocation)) {
            return false;
        }
        String name = npc.getName();
        return name != null
                && !"Brawler".equalsIgnoreCase(name)
                && !"Portal".equalsIgnoreCase(name)
                && !"Spinner".equalsIgnoreCase(name);
    }

    private static boolean isActivityTargetAccessible(
            WorldPoint playerLocation,
            WorldPoint targetLocation) {
        if (playerLocation == null || targetLocation == null) {
            return false;
        }
        boolean playerCentral = isInsideCentralEnclosure(playerLocation);
        boolean targetCentral = isInsideCentralEnclosure(targetLocation);
        if (playerCentral || targetCentral) {
            return playerCentral == targetCentral;
        }

        boolean playerWest = playerLocation.getRegionX() <= WEST_LANE_GATE_REGION_X
                && playerLocation.getRegionY() > SOUTH_PERIMETER_DETOUR_REGION_Y;
        boolean playerEast = playerLocation.getRegionX() >= EAST_LANE_GATE_REGION_X
                && playerLocation.getRegionY() > SOUTH_PERIMETER_DETOUR_REGION_Y;
        boolean targetWest = targetLocation.getRegionX() <= WEST_LANE_GATE_REGION_X
                && targetLocation.getRegionY() > SOUTH_PERIMETER_DETOUR_REGION_Y;
        boolean targetEast = targetLocation.getRegionX() >= EAST_LANE_GATE_REGION_X
                && targetLocation.getRegionY() > SOUTH_PERIMETER_DETOUR_REGION_Y;
        return !(playerWest && targetEast) && !(playerEast && targetWest);
    }

    private boolean isMaintainingActivityCombat() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC)) {
                return false;
            }
            NPC target = (NPC) player.getInteracting();
            String name = target.getName();
            if (target.isDead() || name == null || "Brawler".equalsIgnoreCase(name)) {
                return false;
            }
            return "Portal".equalsIgnoreCase(name)
                    || "Spinner".equalsIgnoreCase(name)
                    || target.getCombatLevel() > 0;
        }).orElse(false);
    }

    private void maintainActivityWith(Rs2NpcModel target, WorldPoint playerLocation) {
        WorldPoint targetLocation = Microbot.getClientThread().runOnClientThreadOptional(() ->
                target == null
                        || target.getNpc() == null
                        || target.getNpc().isDead()
                        || !hasAttackAction(target)
                        ? null
                        : target.getWorldLocation()
        ).orElse(null);
        if (targetLocation == null) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "no attackable pest nearby");
            return;
        }

        if (!isActivityTargetAccessible(playerLocation, targetLocation)
                && isInsideCentralEnclosure(playerLocation)) {
            Portal lanePortal = targetLocation.getRegionX() >= PEST_CONTROL_CENTER_REGION_COORD
                    ? BLUE
                    : PURPLE;
            if (ensurePortalLaneAccess(playerLocation, lanePortal)) {
                return;
            }
        }

        if (isNpcOnCanvas(target)
                && !routeCrossesSouthwestNoGoArea(playerLocation, targetLocation)) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "attacking visible activity target");
            dispatchAttack(target);
            return;
        }

        int engagementDistance = engagementDistance(committedActivityLoadout().combatStyle);
        if (playerLocation == null
                || playerLocation.distanceTo(targetLocation) > engagementDistance
                || routeCrossesSouthwestNoGoArea(playerLocation, targetLocation)) {
            transitionTo(RuntimeState.ACTIVITY_FALLBACK, "approaching activity target");
            moveToward(playerLocation, targetLocation, engagementDistance);
            return;
        }

        transitionTo(RuntimeState.ACTIVITY_FALLBACK, "attacking activity target");
        dispatchAttack(target);
    }

    private boolean boardBoat() {
        long now = System.currentTimeMillis();
        if (boardingAttemptPending) {
            if (now - lastBoardingAttemptAt < BOARDING_CONFIRM_TIMEOUT_MILLIS) {
                return false;
            }
            boardingAttemptPending = false;
            Microbot.log("Pest Control boat entry was not observed; retrying gangplank");
        }
        if (now - lastBoardingAttemptAt < BOARDING_RETRY_MILLIS) {
            return false;
        }
        lastBoardingAttemptAt = now;

        int combatLevel = getCombatLevel();
        int gangplankId = combatLevel >= 100
                ? ObjectID.GANGPLANK_25632
                : combatLevel >= 70
                ? ObjectID.GANGPLANK_25631
                : ObjectID.GANGPLANK_14315;
        boolean dispatched = Microbot.getRs2TileObjectCache().query().interact(gangplankId);
        if (dispatched) {
            boardingAttemptPending = true;
        }
        return dispatched;
    }

    public boolean isOutside() {
        WorldPoint playerLoc = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        return playerLoc != null && playerLoc.distanceTo(new WorldPoint(2644, 2644, 0)) < 20;
    }

    public boolean isInBoat() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO) != null
        ).orElse(false);
    }

    public boolean isInPestControl() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWidget(WidgetInfo.PEST_CONTROL_BLUE_SHIELD) != null
        ).orElse(false);
    }

    public void exitBoat() {
        int combatLevel = getCombatLevel();
        if (combatLevel >= 100) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25630);
        } else if (combatLevel >= 70) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_25629);
        } else {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.LADDER_14314);
        }
        sleepUntil(() -> !isInBoat(), 3000);

    }

    private static int getCombatLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? 0 : player.getCombatLevel();
        }).orElse(0);
    }

    private static boolean attackPortal(Rs2NpcModel npcPortal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (npcPortal == null || npcPortal.getNpc() == null || npcPortal.getNpc().isDead()) {
                return false;
            }
            NPCComposition npc = Microbot.getClient().getNpcDefinition(npcPortal.getId());
            if (npc == null) {
                return false;
            }

            String[] actions = npc.getActions();
            if (actions != null
                    && Arrays.stream(actions).anyMatch(x -> x != null && x.equalsIgnoreCase("attack"))) {
                // Rs2NpcModel.click uses the actor's LocalPoint for camera rotation, so it
                // remains valid inside the Pest Control instance and can preempt a pest.
                return npcPortal.click("Attack");
            }
            return false;
        }).orElse(false);
    }


    private void handlePortalTarget(PortalTarget target, WorldPoint playerLocation) {
        if (selectedPortal != target.portal) {
            disableSpecialAttackIfEnabled();
            clearSpinnerCommitment();
            clearBrawlerCommitment();
            selectedPortal = target.portal;
            Microbot.log("Pest Control target: " + target.portal
                    + " portal (" + target.nearbyPlayers + " other players nearby)");
        }

        if (playerLocation == null) {
            transitionTo(RuntimeState.ERROR, "player location unavailable");
            return;
        }
        PestControlLoadout desiredLoadout = combatPlan.loadoutForPortal(target.portal);
        int desiredEngagementDistance = engagementDistance(desiredLoadout.combatStyle);
        maybeTurnCameraTowardPortal(playerLocation, target.portal);
        if (ensurePortalLaneAccess(playerLocation, target.portal)) {
            return;
        }
        if (southPerimeterWaypoint(playerLocation, target.portal) != null) {
            transitionTo(RuntimeState.CHASE_PORTAL,
                    target.portal + " portal via outer perimeter");
            WorldPoint approachLocation = portalApproachLocation(
                    target.portal, playerLocation, desiredEngagementDistance);
            int arrivalDistance = desiredEngagementDistance > MELEE_ENGAGEMENT_DISTANCE
                    ? STAGING_ARRIVAL_DISTANCE
                    : MELEE_ENGAGEMENT_DISTANCE;
            moveTowardPortal(
                    playerLocation,
                    approachLocation,
                    arrivalDistance,
                    target.portal);
            if (prepareLoadoutForPortal(target.portal) == null) {
                transitionTo(RuntimeState.INITIALISING,
                        "preparing " + target.portal + " combat loadout while moving");
            }
            return;
        }
        PestControlLoadout activeLoadout = prepareLoadoutForPortal(target.portal);
        if (activeLoadout == null) {
            transitionTo(RuntimeState.INITIALISING,
                    "preparing " + target.portal + " combat loadout");
            return;
        }

        int engagementDistance = engagementDistance(activeLoadout.combatStyle);
        if (isInteractingWithSpinnerNear(target.portal)) {
            disableSpecialAttackIfEnabled();
            transitionTo(RuntimeState.KILL_SPINNER, target.portal + " portal");
            return;
        }

        Rs2NpcModel spinner = findSpinnerNear(target.portal);
        if (spinner != null) {
            spinnerCommitPortal = target.portal;
            spinnerCommitUntil = System.currentTimeMillis() + SPINNER_TARGET_GRACE_MILLIS;
            disableSpecialAttackIfEnabled();
            transitionTo(RuntimeState.KILL_SPINNER, target.portal + " portal");
            if (isInteractingWith(spinner.getNpc())) {
                return;
            }

            WorldPoint spinnerLocation = Microbot.getClientThread().runOnClientThreadOptional(
                    spinner::getWorldLocation).orElse(null);
            if (isNpcOnCanvas(spinner)
                    && !routeCrossesSouthwestNoGoArea(playerLocation, spinnerLocation)) {
                dispatchAttack(spinner);
                return;
            }
            if (spinnerLocation != null
                    && (playerLocation.distanceTo(spinnerLocation) > engagementDistance
                    || routeCrossesSouthwestNoGoArea(playerLocation, spinnerLocation))) {
                moveTowardPortal(playerLocation, spinnerLocation, engagementDistance, target.portal);
                return;
            }
            dispatchAttack(spinner);
            return;
        }
        if (spinnerCommitPortal == target.portal
                && System.currentTimeMillis() < spinnerCommitUntil) {
            transitionTo(RuntimeState.KILL_SPINNER, target.portal + " portal");
            return;
        }
        clearSpinnerCommitment();

        if (target.blockingBrawler != null
                && handleBlockingBrawler(target, playerLocation, engagementDistance)) {
            return;
        }

        WorldPoint portalLocation = logicalPortalLocation(target.portal, playerLocation);
        if (target.attackActionAvailable
                && isNpcOnCanvas(target.npc)
                && !routeCrossesSouthwestNoGoArea(playerLocation, portalLocation)) {
            engagePortal(target);
            return;
        }

        WorldPoint approachLocation = portalApproachLocation(
                target.portal, playerLocation, engagementDistance);
        int arrivalDistance = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? STAGING_ARRIVAL_DISTANCE
                : MELEE_ENGAGEMENT_DISTANCE;
        if (playerLocation.distanceTo(portalLocation) > engagementDistance) {
            transitionTo(RuntimeState.CHASE_PORTAL, target.portal + " portal");
            moveTowardPortal(playerLocation, approachLocation, arrivalDistance, target.portal);
            return;
        }

        engagePortal(target);
    }

    private boolean handleBlockingBrawler(
            PortalTarget target,
            WorldPoint playerLocation,
            int engagementDistance) {
        disableSpecialAttackIfEnabled();
        long now = System.currentTimeMillis();
        if (brawlerCommitPortal != target.portal) {
            clearBrawlerCommitment();
            brawlerCommitPortal = target.portal;
        }

        if (now < brawlerClearUntil) {
            return clearBlockingBrawler(target);
        }

        if (brawlerFlankTarget == null) {
            brawlerFlankTarget = findBrawlerFlankTile(target, engagementDistance);
            brawlerFlankStartedAt = now;
        }
        if (brawlerFlankTarget != null
                && playerLocation.distanceTo(brawlerFlankTarget) > 1
                && now - brawlerFlankStartedAt < BRAWLER_FLANK_TIMEOUT_MILLIS) {
            transitionTo(RuntimeState.AVOID_BRAWLER,
                    "flanking " + target.portal + " portal");
            moveToward(playerLocation, brawlerFlankTarget, 1);
            return true;
        }

        brawlerFlankTarget = null;
        brawlerClearUntil = now + BRAWLER_CLEAR_COMMIT_MILLIS;
        return clearBlockingBrawler(target);
    }

    private boolean clearBlockingBrawler(PortalTarget target) {
        transitionTo(RuntimeState.AVOID_BRAWLER,
                "clearing Brawler at " + target.portal + " portal");
        if (!isInteractingWith(target.blockingBrawler.getNpc())) {
            dispatchAttack(target.blockingBrawler);
        }
        return true;
    }

    private WorldPoint findBrawlerFlankTile(PortalTarget target, int engagementDistance) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || target.npc == null || target.npc.getNpc() == null) {
                return null;
            }

            WorldArea portalArea = target.npc.getNpc().getWorldArea();
            WorldPoint playerLocation = player.getWorldLocation();
            if (portalArea == null || playerLocation == null) {
                return null;
            }

            List<WorldArea> brawlers = Microbot.getRs2NpcCache().query()
                    .withIds(BRAWLER_IDS.stream().mapToInt(Integer::intValue).toArray())
                    .where(npc -> npc.getNpc() != null && !npc.getNpc().isDead())
                    .toList()
                    .stream()
                    .map(npc -> npc.getNpc().getWorldArea())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            int flankDistance = engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                    ? Math.max(2, engagementDistance - 1)
                    : 1;
            Set<Integer> reachableTiles = new HashSet<>();
            Rs2Reachable.getReachableTiles(playerLocation).forEach((int packed) ->
                    reachableTiles.add(packed));

            WorldPoint flank = perimeterTiles(portalArea, flankDistance).stream()
                    .filter(candidate -> !isSouthwestNoGoTile(candidate))
                    .filter(Rs2Tile::isWalkable)
                    .filter(candidate -> reachableTiles.contains(WorldPointUtil.packWorldPoint(
                            candidate.getX(), candidate.getY(), candidate.getPlane())))
                    .filter(candidate -> candidate.toWorldArea().hasLineOfSightTo(
                            Microbot.getClient().getTopLevelWorldView(), portalArea))
                    .filter(candidate -> !isRouteBlockedByBrawler(
                            playerLocation, candidate.toWorldArea(), brawlers))
                    .filter(candidate -> !isRouteBlockedByBrawler(
                            candidate, portalArea, brawlers))
                    .min(Comparator.comparingInt(playerLocation::distanceTo))
                    .orElse(null);
            if (flank == null) {
                return null;
            }

            LocalPoint localPoint = LocalPoint.fromWorld(
                    Microbot.getClient().getTopLevelWorldView(), flank);
            return localPoint == null
                    ? null
                    : WorldPoint.fromLocalInstance(
                            Microbot.getClient(), localPoint, flank.getPlane());
        }).orElse(null);
    }

    private void clearBrawlerCommitment() {
        brawlerCommitPortal = null;
        brawlerFlankTarget = null;
        brawlerFlankStartedAt = 0L;
        brawlerClearUntil = 0L;
        clearMovementBrawlerRecovery();
    }

    /**
     * Brawlers are solid 5x5 obstacles. Portal-specific avoidance cannot help
     * while the script is following an outer-perimeter waypoint, so every
     * movement command also checks its immediate route for a Brawler. Reachable
     * compass points around the obstacle are tried in target-efficient order;
     * if none produce a route, attacking the Brawler is the deterministic last
     * resort.
     */
    private boolean handleMovementBrawlerObstruction(
            WorldPoint playerLocation,
            WorldPoint routeTarget) {
        long now = System.currentTimeMillis();
        Rs2NpcModel committedBrawler = findLiveBrawlerByIndex(movementBlockingBrawlerIndex);
        boolean movementObjectiveChanged = movementBrawlerRouteTarget != null
                && movementBrawlerRouteTarget.distanceTo(routeTarget) > MINIMAP_STEP_DISTANCE;
        if (movementObjectiveChanged) {
            clearMovementBrawlerRecovery();
            committedBrawler = null;
        } else if (movementBrawlerClearUntil > 0L && committedBrawler != null) {
            return clearMovementBlockingBrawler(committedBrawler);
        }
        if (movementBrawlerClearUntil > 0L) {
            clearMovementBrawlerRecovery();
        }

        Rs2NpcModel blockingBrawler = findMovementBlockingBrawler(playerLocation, routeTarget);
        if (blockingBrawler == null) {
            if (movementBlockingBrawlerIndex >= 0) {
                Microbot.log("Pest Control Brawler recovery: compass flank cleared movement route");
                clearMovementBrawlerRecovery();
            }
            return false;
        }

        int brawlerIndex = npcIndex(blockingBrawler);
        if (brawlerIndex != movementBlockingBrawlerIndex
                || movementBrawlerRouteTarget == null
                || movementBrawlerRouteTarget.distanceTo(routeTarget) > MINIMAP_STEP_DISTANCE) {
            clearMovementBrawlerRecovery();
            movementBlockingBrawlerIndex = brawlerIndex;
            movementBrawlerRouteTarget = routeTarget;
            Microbot.log("Pest Control Brawler recovery: obstacle detected on movement route");
        }

        if (movementBrawlerFlankTarget != null) {
            boolean reached = playerLocation.distanceTo(movementBrawlerFlankTarget) <= 1;
            boolean timedOut = now - movementBrawlerFlankStartedAt
                    >= BRAWLER_COMPASS_STEP_TIMEOUT_MILLIS;
            if (!reached && !timedOut) {
                transitionTo(RuntimeState.AVOID_BRAWLER,
                        "compass flank " + movementBrawlerFlankDirection);
                dispatchMovementStep(playerLocation, movementBrawlerFlankTarget);
                return true;
            }

            attemptedMovementBrawlerFlanks.add(movementBrawlerFlankDirection);
            Microbot.log("Pest Control Brawler recovery: compass "
                    + movementBrawlerFlankDirection + (reached ? " reached" : " failed"));
            movementBrawlerFlankDirection = null;
            movementBrawlerFlankTarget = null;
            movementBrawlerFlankStartedAt = 0L;
        }

        CompassFlank nextFlank = findNextMovementBrawlerFlank(
                blockingBrawler, playerLocation, routeTarget);
        if (nextFlank != null) {
            movementBrawlerFlankDirection = nextFlank.direction;
            movementBrawlerFlankTarget = nextFlank.location;
            movementBrawlerFlankStartedAt = now;
            transitionTo(RuntimeState.AVOID_BRAWLER,
                    "compass flank " + nextFlank.direction);
            Microbot.log("Pest Control Brawler recovery: trying compass "
                    + nextFlank.direction + " via " + nextFlank.location);
            dispatchMovementStep(playerLocation, nextFlank.location);
            return true;
        }

        // Once every viable compass flank has failed, keep clearing this
        // obstacle until it dies or the movement objective materially changes.
        movementBrawlerClearUntil = Long.MAX_VALUE;
        Microbot.log("Pest Control Brawler recovery: no reachable compass flank; attacking Brawler");
        return clearMovementBlockingBrawler(blockingBrawler);
    }

    private void dispatchMovementStep(WorldPoint playerLocation, WorldPoint target) {
        long now = System.currentTimeMillis();
        boolean isMoving = Rs2Player.isMoving();
        long retryMillis = isMoving ? MOVEMENT_RETRY_MOVING_MILLIS : MOVEMENT_RETRY_IDLE_MILLIS;
        if (now - lastMovementCommandAt < retryMillis) {
            return;
        }
        Rs2Walker.walkFastCanvas(stepTowards(playerLocation, target, MINIMAP_STEP_DISTANCE));
        lastMovementCommandAt = now;
    }

    private boolean clearMovementBlockingBrawler(Rs2NpcModel brawler) {
        if (brawler == null || brawler.getNpc() == null || brawler.getNpc().isDead()) {
            clearMovementBrawlerRecovery();
            return false;
        }
        transitionTo(RuntimeState.AVOID_BRAWLER, "clearing movement-blocking Brawler");
        if (!isInteractingWith(brawler.getNpc())) {
            dispatchAttack(brawler);
        }
        return true;
    }

    private Rs2NpcModel findMovementBlockingBrawler(
            WorldPoint playerLocation,
            WorldPoint routeTarget) {
        if (playerLocation == null || routeTarget == null) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2NpcCache().query()
                        .withIds(BRAWLER_IDS.stream().mapToInt(Integer::intValue).toArray())
                        .where(npc -> npc.getNpc() != null
                                && !npc.getNpc().isDead()
                                && npc.getNpc().getWorldArea() != null
                                && playerLocation.distanceTo(npc.getNpc().getWorldLocation())
                                <= ACTIVITY_TARGET_RADIUS
                                && isRouteBlockedByBrawler(
                                playerLocation,
                                routeTarget.toWorldArea(),
                                Collections.singletonList(npc.getNpc().getWorldArea())))
                        .toList()
                        .stream()
                        .min(Comparator.comparingInt(PestControlScript::distanceFromPlayerInTiles))
                        .orElse(null)
        ).orElse(null);
    }

    private Rs2NpcModel findLiveBrawlerByIndex(int index) {
        if (index < 0) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2NpcCache().query()
                        .withIds(BRAWLER_IDS.stream().mapToInt(Integer::intValue).toArray())
                        .where(npc -> npc.getNpc() != null
                                && !npc.getNpc().isDead()
                                && npc.getNpc().getIndex() == index)
                        .toList()
                        .stream()
                        .findFirst()
                        .orElse(null)
        ).orElse(null);
    }

    private CompassFlank findNextMovementBrawlerFlank(
            Rs2NpcModel brawler,
            WorldPoint playerLocation,
            WorldPoint routeTarget) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (brawler == null || brawler.getNpc() == null) {
                return null;
            }
            WorldArea area = brawler.getNpc().getWorldArea();
            if (area == null) {
                return null;
            }

            int west = area.getX() - BRAWLER_COMPASS_CLEARANCE;
            int east = area.getX() + area.getWidth() - 1 + BRAWLER_COMPASS_CLEARANCE;
            int south = area.getY() - BRAWLER_COMPASS_CLEARANCE;
            int north = area.getY() + area.getHeight() - 1 + BRAWLER_COMPASS_CLEARANCE;
            int centerX = area.getX() + (area.getWidth() - 1) / 2;
            int centerY = area.getY() + (area.getHeight() - 1) / 2;
            int plane = area.getPlane();
            List<CompassFlank> candidates = Arrays.asList(
                    new CompassFlank("north", new WorldPoint(centerX, north, plane)),
                    new CompassFlank("north-east", new WorldPoint(east, north, plane)),
                    new CompassFlank("east", new WorldPoint(east, centerY, plane)),
                    new CompassFlank("south-east", new WorldPoint(east, south, plane)),
                    new CompassFlank("south", new WorldPoint(centerX, south, plane)),
                    new CompassFlank("south-west", new WorldPoint(west, south, plane)),
                    new CompassFlank("west", new WorldPoint(west, centerY, plane)),
                    new CompassFlank("north-west", new WorldPoint(west, north, plane)));

            Set<Integer> reachableTiles = new HashSet<>();
            Rs2Reachable.getReachableTiles(playerLocation).forEach((int packed) ->
                    reachableTiles.add(packed));
            List<WorldArea> obstacle = Collections.singletonList(area);
            return candidates.stream()
                    .filter(candidate -> !attemptedMovementBrawlerFlanks.contains(candidate.direction))
                    .filter(candidate -> playerLocation.distanceTo(candidate.location) > 1)
                    .filter(candidate -> !isSouthwestNoGoTile(candidate.location))
                    .filter(candidate -> Rs2Tile.isWalkable(candidate.location))
                    .filter(candidate -> reachableTiles.contains(WorldPointUtil.packWorldPoint(
                            candidate.location.getX(),
                            candidate.location.getY(),
                            candidate.location.getPlane())))
                    .filter(candidate -> !isRouteBlockedByBrawler(
                            playerLocation, candidate.location.toWorldArea(), obstacle))
                    .sorted(Comparator
                            .comparingInt((CompassFlank candidate) -> isRouteBlockedByBrawler(
                                    candidate.location, routeTarget.toWorldArea(), obstacle) ? 1 : 0)
                            .thenComparingInt(candidate -> candidate.location.distanceTo(routeTarget))
                            .thenComparingInt(candidate -> playerLocation.distanceTo(candidate.location)))
                    .findFirst()
                    .orElse(null);
        }).orElse(null);
    }

    private static int npcIndex(Rs2NpcModel npc) {
        return npc == null || npc.getNpc() == null ? -1 : npc.getNpc().getIndex();
    }

    private void clearMovementBrawlerRecovery() {
        movementBlockingBrawlerIndex = -1;
        movementBrawlerFlankDirection = null;
        movementBrawlerFlankTarget = null;
        movementBrawlerRouteTarget = null;
        movementBrawlerFlankStartedAt = 0L;
        movementBrawlerClearUntil = 0L;
        attemptedMovementBrawlerFlanks.clear();
    }

    private static final class CompassFlank {
        private final String direction;
        private final WorldPoint location;

        private CompassFlank(String direction, WorldPoint location) {
            this.direction = direction;
            this.location = location;
        }
    }

    private static List<WorldPoint> perimeterTiles(WorldArea area, int distance) {
        List<WorldPoint> candidates = new ArrayList<>();
        int minX = area.getX() - distance;
        int maxX = area.getX() + area.getWidth() - 1 + distance;
        int minY = area.getY() - distance;
        int maxY = area.getY() + area.getHeight() - 1 + distance;
        for (int x = minX; x <= maxX; x++) {
            candidates.add(new WorldPoint(x, minY, area.getPlane()));
            candidates.add(new WorldPoint(x, maxY, area.getPlane()));
        }
        for (int y = minY + 1; y < maxY; y++) {
            candidates.add(new WorldPoint(minX, y, area.getPlane()));
            candidates.add(new WorldPoint(maxX, y, area.getPlane()));
        }
        return candidates;
    }

    private void engagePortal(PortalTarget target) {
        transitionTo(RuntimeState.ATTACK_PORTAL, target.portal + " portal");
        activateSpecialAttackIfReady(target.portal);
        if (isInteractingWithPortal(target.portal)) {
            long sinceLastAttackCommand = System.currentTimeMillis() - lastAttackCommandAt;
            if (isPlayerMovingOrAnimating()
                    || sinceLastAttackCommand < PORTAL_REAFFIRM_MILLIS) {
                return;
            }
        }

        // The chat message can arrive a tick before the NPC composition gains
        // its Attack action. Stay on the portal instead of falling back to a pest.
        if (!target.attackActionAvailable) {
            return;
        }

        dispatchPortalAttack(target.npc);
    }

    private static int engagementDistance(PestControlCombatStyle style) {
        return style == PestControlCombatStyle.MELEE
                ? MELEE_ENGAGEMENT_DISTANCE
                : RANGED_ENGAGEMENT_DISTANCE;
    }

    private boolean prepareStartupCombat() {
        if (startupCombatPrepared) {
            return true;
        }
        if (!startupAutocastPrepared) {
            PestControlLoadout magicLoadout = combatPlan.magicLoadout();
            if (!magicLoadout.isConfigured()) {
                logLoadoutFailure(magicLoadout, "autocast weapon is not configured", true);
                return false;
            }
            if (!prepareLoadout(magicLoadout)) {
                return false;
            }

            Rs2CombatSpells desiredSpell = config.magicAutocastSpell();
            if (desiredSpell == null || !Rs2Magic.canCast(desiredSpell)) {
                logLoadoutFailure(magicLoadout,
                        "cannot cast configured spell " + String.valueOf(desiredSpell), true);
                return false;
            }
            if (Rs2Magic.getCurrentAutoCastSpell() != desiredSpell) {
                Microbot.log("Pest Control setting remembered autocast: " + desiredSpell.getName()
                        + " (" + magicLoadout.weapon + ")");
                Rs2Combat.setAutoCastSpell(desiredSpell, false);
                if (!sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == desiredSpell, 5000)) {
                    logLoadoutFailure(magicLoadout,
                            "failed to set autocast " + desiredSpell.getName(), false);
                    return false;
                }
            } else {
                Microbot.log("Pest Control retained remembered autocast: " + desiredSpell.getName()
                        + " (" + magicLoadout.weapon + ")");
            }
            startupAutocastPrepared = true;
        }

        if (!preparePrimaryLoadout()) {
            return false;
        }
        resolveVoidHelmetSwitching();
        if (!preparePrimaryLoadout()) {
            return false;
        }
        startupCombatPrepared = true;
        Microbot.log("Pest Control combat startup preparation complete");
        return true;
    }

    private PestControlLoadout prepareLoadoutForPortal(Portal portal) {
        PestControlLoadout desired = combatPlan.loadoutForPortal(portal);
        return prepareLoadout(desired) ? desired : null;
    }

    private boolean preparePrimaryLoadout() {
        return prepareLoadout(combatPlan.primaryLoadout());
    }

    private boolean prepareLoadout(PestControlLoadout loadout) {
        if (loadout == null || !loadout.isConfigured()) {
            if (loadout != null) {
                logLoadoutFailure(loadout, "weapon is not configured", true);
            }
            return false;
        }
        if (System.currentTimeMillis() < loadoutRetryAfterByKey.getOrDefault(loadout.key(), 0L)) {
            return false;
        }
        if (isLoadoutEquipped(loadout)) {
            if (applyLoadoutAttackMode(loadout)) {
                return markLoadoutReady(loadout);
            }
            logLoadoutFailure(loadout, "combat mode verification failed", false);
            return false;
        }
        if (!isItemAvailable(loadout.weapon, EquipmentInventorySlot.WEAPON)) {
            logLoadoutFailure(loadout, "missing weapon " + loadout.weapon, true);
            return false;
        }
        if (usesVoidHelmet(loadout)
                && !isItemAvailable(loadout.helmet, EquipmentInventorySlot.HEAD)) {
            logLoadoutFailure(loadout, "missing helmet " + loadout.helmet, true);
            return false;
        }
        if (!loadout.requiresEmptyOffhand()
                && !isItemAvailable(loadout.offhand, EquipmentInventorySlot.SHIELD)) {
            logLoadoutFailure(loadout, "missing off-hand " + loadout.offhand, true);
            return false;
        }

        String currentOffhand = getEquippedItemName(EquipmentInventorySlot.SHIELD);
        if (loadout.requiresEmptyOffhand()
                && !currentOffhand.isEmpty()
                && Rs2Inventory.emptySlotCount() <= 0) {
            logLoadoutFailure(loadout,
                    "need one free inventory slot to clear off-hand " + currentOffhand, true);
            return false;
        }

        if ((usesVoidHelmet(loadout)
                && !equipItem(loadout.helmet, "Wear", EquipmentInventorySlot.HEAD))
                || !equipItem(loadout.weapon, "Wield", EquipmentInventorySlot.WEAPON)) {
            logLoadoutFailure(loadout, "equipment interaction did not complete", false);
            return false;
        }

        if (loadout.requiresEmptyOffhand()) {
            if (Rs2Equipment.get(EquipmentInventorySlot.SHIELD) != null) {
                if (Rs2Inventory.emptySlotCount() <= 0
                        || !Rs2Equipment.unEquip(EquipmentInventorySlot.SHIELD)
                        || !sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.SHIELD) == null, 2000)) {
                    logLoadoutFailure(loadout, "could not clear equipped off-hand", false);
                    return false;
                }
            }
        } else if (!equipItem(loadout.offhand, "Wield", EquipmentInventorySlot.SHIELD)) {
            logLoadoutFailure(loadout,
                    "off-hand is incompatible with the configured weapon", false);
            return false;
        }

        if (!isLoadoutEquipped(loadout)) {
            logLoadoutFailure(loadout, "slot verification failed after switching", false);
            return false;
        }
        if (!applyLoadoutAttackMode(loadout)) {
            logLoadoutFailure(loadout, "combat mode verification failed", false);
            return false;
        }

        return markLoadoutReady(loadout);
    }

    private boolean equipItem(
            String itemName,
            String action,
            EquipmentInventorySlot slot) {
        if (isItemEquipped(itemName, slot)) {
            return true;
        }
        return Rs2Inventory.interact(itemName, action, true)
                && sleepUntil(() -> isItemEquipped(itemName, slot), 2000);
    }

    private boolean applyLoadoutAttackMode(PestControlLoadout loadout) {
        if (loadout.attackOption != null) {
            return selectAttackOption(loadout.attackOption);
        }
        String magicMode = config.magicCastingMode() == PestControlMagicMode.AUTOCAST
                ? "Autocast " + config.magicAutocastSpell().getName()
                : "Powered staff";
        recordCombatMode(loadout.weapon, magicMode);
        return true;
    }

    private boolean markLoadoutReady(PestControlLoadout loadout) {
        boolean changed = !loadout.key().equals(activeLoadoutKey);
        activeLoadoutKey = loadout.key();
        loadoutFailuresLogged.removeIf(failure -> failure.startsWith(loadout.key() + ":"));
        loadoutRetryAfterByKey.remove(loadout.key());
        if (changed) {
            Microbot.log("Pest Control loadout ready: " + loadout.label
                    + " - " + loadout.weapon
                    + (loadout.offhand.isEmpty() ? " (empty off-hand)" : " + " + loadout.offhand)
                    + (usesVoidHelmet(loadout) ? " + " + loadout.helmet : ""));
        }
        return true;
    }

    private boolean isLoadoutEquipped(PestControlLoadout loadout) {
        boolean offhandMatches = loadout.requiresEmptyOffhand()
                ? Rs2Equipment.get(EquipmentInventorySlot.SHIELD) == null
                : isItemEquipped(loadout.offhand, EquipmentInventorySlot.SHIELD);
        return isItemEquipped(loadout.weapon, EquipmentInventorySlot.WEAPON)
                && (!usesVoidHelmet(loadout)
                || isItemEquipped(loadout.helmet, EquipmentInventorySlot.HEAD))
                && offhandMatches;
    }

    private void resolveVoidHelmetSwitching() {
        if (voidHelmetSwitchingEnabled != null) {
            return;
        }
        boolean allAvailable = PestControlLoadout.hasCompleteVoidHelmetSet(
                combatPlan.enabledStyles(),
                helmet -> isItemAvailable(helmet, EquipmentInventorySlot.HEAD));
        boolean anyAvailable = combatPlan.enabledStyles().stream()
                .map(PestControlLoadout::helmetFor)
                .anyMatch(helmet -> isItemAvailable(helmet, EquipmentInventorySlot.HEAD));
        voidHelmetSwitchingEnabled = allAvailable;
        if (allAvailable) {
            Microbot.log("Pest Control Void helmet switching enabled for "
                    + combatPlan.enabledStyles());
        } else if (anyAvailable) {
            Microbot.log("Pest Control Void helmet switching disabled: incomplete enabled-style helmet set; head slot unchanged");
        } else {
            Microbot.log("Pest Control non-Void setup detected; head slot unchanged");
        }
    }

    private boolean usesVoidHelmet(PestControlLoadout loadout) {
        return loadout != null && Boolean.TRUE.equals(voidHelmetSwitchingEnabled);
    }

    private static boolean isItemAvailable(String itemName, EquipmentInventorySlot slot) {
        return isItemEquipped(itemName, slot) || Rs2Inventory.hasItem(itemName, true);
    }

    private static boolean isItemEquipped(String itemName, EquipmentInventorySlot slot) {
        return itemName != null
                && !itemName.isEmpty()
                && itemName.equalsIgnoreCase(getEquippedItemName(slot));
    }

    private static String getEquippedItemName(EquipmentInventorySlot slot) {
        Rs2ItemModel item = Rs2Equipment.get(slot);
        return item == null || item.getName() == null ? "" : item.getName().trim();
    }

    private void logLoadoutFailure(
            PestControlLoadout loadout,
            String reason,
            boolean missingOrBlocked) {
        String failureKey = loadout.key() + ":" + reason.toLowerCase(Locale.ROOT);
        if (loadoutFailuresLogged.add(failureKey)) {
            Microbot.log("Pest Control loadout unavailable: " + loadout.label + " - " + reason);
        }
        loadoutRetryAfterByKey.put(
                loadout.key(),
                System.currentTimeMillis()
                        + (missingOrBlocked ? MISSING_LOADOUT_RETRY_MILLIS : LOADOUT_RETRY_MILLIS));
    }

    private boolean selectAttackOption(String desiredStyle) {
        String equippedWeapon = getEquippedWeaponName();
        recordCombatMode(equippedWeapon, desiredStyle + " (setting)");
        String attackOptionKey = normalizeWeaponName(equippedWeapon)
                + ":" + desiredStyle.toLowerCase(Locale.ROOT);
        Integer rememberedIndex = attackOptionIndexByWeaponStyle.get(attackOptionKey);
        if (rememberedIndex != null
                && Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == rememberedIndex) {
            if (!attackOptionKey.equals(activeAttackOptionKey)) {
                Microbot.log("Pest Control retained attack style: " + desiredStyle
                        + " (" + equippedWeapon + "); combat tab unchanged");
            }
            activeAttackOptionKey = attackOptionKey;
            recordCombatMode(equippedWeapon, desiredStyle);
            return true;
        }

        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000)) {
            return false;
        }

        WidgetInfo[] styleWidgets = {
                WidgetInfo.COMBAT_STYLE_ONE,
                WidgetInfo.COMBAT_STYLE_TWO,
                WidgetInfo.COMBAT_STYLE_THREE,
                WidgetInfo.COMBAT_STYLE_FOUR
        };
        int selectedIndex = -1;
        int selectedScore = 0;
        for (int index = 0; index < styleWidgets.length; index++) {
            int styleTextId = styleWidgets[index].getId() + 3;
            String styleText = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget widget = Microbot.getClient().getWidget(styleTextId);
                return widget == null ? null : widget.getText();
            }).orElse(null);
            int score = scoreAttackOption(styleText, desiredStyle);
            if (score > selectedScore) {
                selectedIndex = index;
                selectedScore = score;
            }
        }

        if (selectedIndex < 0) {
            if (missingAttackOptionsLogged.add(attackOptionKey)) {
                Microbot.log("Pest Control could not find " + desiredStyle
                        + " combat option for " + equippedWeapon);
            }
            return false;
        }

        int expectedIndex = selectedIndex;
        boolean selected = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex;
        if (!selected) {
            WidgetInfo selectedStyleWidget = styleWidgets[selectedIndex];
            boolean clickDispatched = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Rs2Combat.setAttackStyle(selectedStyleWidget)
            ).orElse(false);
            selected = clickDispatched
                    && sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == expectedIndex, 2000);
        }
        if (selected) {
            attackOptionIndexByWeaponStyle.put(attackOptionKey, expectedIndex);
            activeAttackOptionKey = attackOptionKey;
            missingAttackOptionsLogged.remove(attackOptionKey);
            recordCombatMode(equippedWeapon, desiredStyle);
            Microbot.log("Pest Control attack style confirmed: " + desiredStyle
                    + " (" + equippedWeapon + ")");
        }
        return selected;
    }

    private void recordCombatMode(String weapon, String style) {
        overlayCombatWeapon = weapon == null || weapon.trim().isEmpty()
                ? "Unarmed"
                : weapon.trim();
        overlayCombatStyle = style == null || style.trim().isEmpty()
                ? "Unknown"
                : style.trim();
    }

    static int scoreAttackOption(String widgetText, String desiredStyle) {
        if (widgetText == null || desiredStyle == null) {
            return 0;
        }

        String lineSeparatedText = widgetText.replaceAll("(?i)<br\\s*/?>", "\n");
        String[] lines = Text.removeTags(lineSeparatedText).split("\\R");
        String desired = desiredStyle.trim().toLowerCase(Locale.ROOT);
        int score = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim().toLowerCase(Locale.ROOT);
            if (line.equals(desired)) {
                score = Math.max(score, index == 0 ? 100 : 50);
            } else if (line.contains(desired)) {
                score = Math.max(score, index == 0 ? 75 : 25);
            }
            if (line.contains("strength xp")) {
                score += 10;
            }
        }
        return score;
    }

    private static String getEquippedWeaponName() {
        Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return equippedWeapon == null || equippedWeapon.getName() == null
                ? ""
                : equippedWeapon.getName().trim();
    }

    private static String normalizeWeaponName(String weaponName) {
        return weaponName == null ? "" : weaponName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Select portals that expose an Attack action or whose shield-drop game
     * message has arrived. Join the largest player group to finish one portal
     * at a time, retain the current live target across small crowd fluctuations,
     * and use purple as a tie-break.
     */
    private PortalTarget selectAdaptivePortalTarget() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer == null) {
                return null;
            }

            List<Rs2NpcModel> visiblePortals = Microbot.getRs2NpcCache().query()
                    .withName("portal")
                    .where(npc -> npc.getNpc() != null && !npc.getNpc().isDead())
                    .toList();

            List<WorldPoint> otherPlayers = Microbot.getRs2PlayerCache().getStream()
                    .filter(player -> player.getPlayer() != localPlayer)
                    .map(player -> player.getWorldLocation())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            List<PortalTarget> targets = portals.stream()
                    .map(portal -> toPortalTarget(
                            portal,
                            visiblePortals.stream()
                                    .filter(npc -> matchesPortal(npc, portal))
                                    .findFirst()
                                    .orElse(null),
                            localPlayer,
                            otherPlayers,
                            playerLocation))
                    .filter(this::isPortalReady)
                    .collect(Collectors.toList());

            PortalTarget crowdLeader = targets.stream()
                    .max(Comparator
                            .comparingInt((PortalTarget target) ->
                                    target.blockingBrawler == null ? 1 : 0)
                            .thenComparingInt(target -> target.nearbyPlayers)
                            .thenComparingInt(target -> target.portal == PURPLE ? 1 : 0)
                            .thenComparingInt(target -> -target.distance))
                    .orElse(null);
            if (crowdLeader == null || selectedPortal == null) {
                return crowdLeader;
            }

            PortalTarget currentTarget = targets.stream()
                    .filter(target -> target.portal == selectedPortal)
                    .findFirst()
                    .orElse(null);
            if (currentTarget != null) {
                return currentTarget;
            }
            return crowdLeader;
        }).orElse(null);
    }

    private boolean shouldHoldOpeningSide() {
        return shieldDropCount == 0;
    }

    private static boolean isEastSide(Portal portal) {
        return portal == BLUE || portal == YELLOW;
    }

    private boolean hasAttackAction(Rs2NpcModel npc) {
        if (npc == null || npc.getNpc() == null || npc.getNpc().isDead()) {
            return false;
        }
        NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
        String[] actions = composition == null ? null : composition.getActions();
        return actions != null && Arrays.stream(actions)
                .anyMatch(action -> action != null && action.equalsIgnoreCase("attack"));
    }

    private boolean isPortalReady(PortalTarget target) {
        Widget hitpoints = target.portal.getHitPoints();
        String text = hitpoints == null ? null : hitpoints.getText();
        if ("0".equals(Text.removeTags(text == null ? "" : text).trim())) {
            destroyedPortals.add(target.portal);
            return false;
        }
        if (destroyedPortals.contains(target.portal)) {
            return false;
        }
        if (target.attackActionAvailable) {
            return true;
        }
        if (target.portal.hasShield) {
            return false;
        }
        return true;
    }

    private boolean matchesPortal(Rs2NpcModel npc, Portal portal) {
        WorldPoint location = npc == null ? null : npc.getWorldLocation();
        return location != null
                && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                <= PORTAL_MATCH_RADIUS;
    }

    private PortalTarget toPortalTarget(
            Portal portal,
            Rs2NpcModel npc,
            Player localPlayer,
            List<WorldPoint> otherPlayers,
            WorldPoint playerLocation) {
        boolean attackActionAvailable = hasAttackAction(npc);
        if (attackActionAvailable) {
            // Keep shield tracking resilient when the script starts mid-round or
            // a shield-drop chat message is missed.
            portal.setHasShield(false);
        }
        int nearbyPlayers = (int) otherPlayers.stream()
                .filter(player -> regionDistance(
                        player,
                        portal.getRegionX(),
                        portal.getRegionY()) <= PORTAL_CROWD_RADIUS)
                .count();
        int distance = playerLocation == null
                ? Integer.MAX_VALUE
                : regionDistance(playerLocation, portal.getRegionX(), portal.getRegionY());
        return new PortalTarget(
                portal,
                npc,
                nearbyPlayers,
                distance,
                attackActionAvailable,
                findBlockingBrawler(localPlayer, npc));
    }

    private Rs2NpcModel findBlockingBrawler(Player player, Rs2NpcModel portal) {
        if (player == null || portal == null || portal.getNpc() == null) {
            return null;
        }
        WorldPoint playerLocation = player.getWorldLocation();
        WorldArea portalArea = portal.getNpc().getWorldArea();
        if (playerLocation == null || portalArea == null) {
            return null;
        }

        return Microbot.getRs2NpcCache().query()
                .withIds(BRAWLER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(npc -> npc.getNpc() != null
                        && !npc.getNpc().isDead()
                        && isRouteBlockedByBrawler(
                                playerLocation,
                                portalArea,
                                Collections.singletonList(npc.getNpc().getWorldArea())))
                .toList()
                .stream()
                .min(Comparator.comparingInt(PestControlScript::distanceFromPlayerInTiles))
                .orElse(null);
    }

    private static boolean isRouteBlockedByBrawler(
            WorldPoint from,
            WorldArea destination,
            List<WorldArea> brawlers) {
        if (from == null || destination == null || brawlers == null || brawlers.isEmpty()) {
            return false;
        }

        double targetX = destination.getX() + (destination.getWidth() - 1) / 2.0;
        double targetY = destination.getY() + (destination.getHeight() - 1) / 2.0;
        double deltaX = targetX - from.getX();
        double deltaY = targetY - from.getY();
        int samples = Math.max(1,
                (int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaY)) * 4.0));
        for (int i = 1; i < samples; i++) {
            double progress = (double) i / samples;
            double x = from.getX() + deltaX * progress;
            double y = from.getY() + deltaY * progress;
            for (WorldArea brawler : brawlers) {
                if (brawler == null || brawler.getPlane() != from.getPlane()) {
                    continue;
                }
                double maxX = brawler.getX() + brawler.getWidth() - 1;
                double maxY = brawler.getY() + brawler.getHeight() - 1;
                if (x >= brawler.getX() - BRAWLER_ROUTE_PADDING
                        && x <= maxX + BRAWLER_ROUTE_PADDING
                        && y >= brawler.getY() - BRAWLER_ROUTE_PADDING
                        && y <= maxY + BRAWLER_ROUTE_PADDING) {
                    return true;
                }
            }
        }
        return false;
    }

    private Rs2NpcModel findSpinnerNear(Portal portal) {
        return Microbot.getRs2NpcCache().query()
                .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(spinner -> spinner.getNpc() != null
                        && !spinner.getNpc().isDead()
                        && spinner.getWorldLocation() != null
                        && distanceFromPlayerInTiles(spinner) <= ACTIVITY_TARGET_RADIUS
                        && hasAttackAction(spinner)
                        && regionDistance(
                        spinner.getWorldLocation(),
                        portal.getRegionX(),
                        portal.getRegionY()) <= SPINNER_PORTAL_RADIUS)
                .nearestOnClientThread();
    }

    private void clearSpinnerCommitment() {
        spinnerCommitPortal = null;
        spinnerCommitUntil = 0L;
    }

    private static WorldPoint logicalPortalLocation(Portal portal, WorldPoint playerLocation) {
        return WorldPoint.fromRegion(
                playerLocation.getRegionID(),
                portal.getRegionX(),
                portal.getRegionY(),
                playerLocation.getPlane());
    }

    private static WorldPoint portalApproachLocation(
            Portal portal,
            WorldPoint playerLocation,
            int engagementDistance) {
        return engagementDistance > MELEE_ENGAGEMENT_DISTANCE
                ? rangedPortalStagingLocation(portal, playerLocation)
                : logicalPortalLocation(portal, playerLocation);
    }

    private static WorldPoint rangedPortalStagingLocation(Portal portal, WorldPoint playerLocation) {
        int dx = portal.getRegionX() - PEST_CONTROL_CENTER_REGION_COORD;
        int dy = portal.getRegionY() - PEST_CONTROL_CENTER_REGION_COORD;
        int span = Math.max(Math.abs(dx), Math.abs(dy));
        int offsetX = span == 0 ? 0 : (int) Math.round((double) dx * RANGED_STAGING_DISTANCE / span);
        int offsetY = span == 0 ? 0 : (int) Math.round((double) dy * RANGED_STAGING_DISTANCE / span);
        int regionX = Math.max(0, Math.min(63, portal.getRegionX() - offsetX));
        int regionY = Math.max(0, Math.min(63, portal.getRegionY() - offsetY));
        return WorldPoint.fromRegion(
                playerLocation.getRegionID(),
                regionX,
                regionY,
                playerLocation.getPlane());
    }

    private static boolean isInteractingWith(NPC npc) {
        if (npc == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getInteracting() == npc;
        }).orElse(false);
    }

    private static boolean isPlayerMovingOrAnimating() {
        return Rs2Player.isMoving()
                || Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    Player player = Microbot.getClient().getLocalPlayer();
                    return player != null && player.getAnimation() != -1;
                }).orElse(false);
    }

    private static boolean isInteractingWithPortal(Portal portal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC)) {
                return false;
            }

            NPC target = (NPC) player.getInteracting();
            WorldPoint location = target.getWorldLocation();
            return location != null
                    && target.getName() != null
                    && target.getName().equalsIgnoreCase("portal")
                    && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                    <= PORTAL_MATCH_RADIUS;
        }).orElse(false);
    }

    private static boolean isInteractingWithSpinnerNear(Portal portal) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC)) {
                return false;
            }

            NPC target = (NPC) player.getInteracting();
            WorldPoint location = target.getWorldLocation();
            return location != null
                    && SPINNER_IDS.contains(target.getId())
                    && regionDistance(location, portal.getRegionX(), portal.getRegionY())
                    <= SPINNER_PORTAL_RADIUS;
        }).orElse(false);
    }

    private static boolean isInteractingWithSpinner() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null
                    && player.getInteracting() instanceof NPC
                    && SPINNER_IDS.contains(((NPC) player.getInteracting()).getId());
        }).orElse(false);
    }

    private static int regionDistance(WorldPoint point, int regionX, int regionY) {
        return Math.max(
                Math.abs(point.getRegionX() - regionX),
                Math.abs(point.getRegionY() - regionY));
    }

    private static final class PortalTarget {
        private final Portal portal;
        private final Rs2NpcModel npc;
        private final int nearbyPlayers;
        private final int distance;
        private final boolean attackActionAvailable;
        private final Rs2NpcModel blockingBrawler;

        private PortalTarget(
                Portal portal,
                Rs2NpcModel npc,
                int nearbyPlayers,
                int distance,
                boolean attackActionAvailable,
                Rs2NpcModel blockingBrawler) {
            this.portal = portal;
            this.npc = npc;
            this.nearbyPlayers = nearbyPlayers;
            this.distance = distance;
            this.attackActionAvailable = attackActionAvailable;
            this.blockingBrawler = blockingBrawler;
        }
    }

    static final class OverlaySnapshot {
        final String state;
        final String detail;
        final String location;
        final int activityPercent;
        final boolean activityRecoveryActive;
        final int activityRecoveryStart;
        final int activityRecoveryTarget;
        final String targetPortal;
        final int targetCrowd;
        final boolean targetHasAttackAction;
        final String openingPortal;
        final int remainingPortals;
        final int readyPortals;
        final String combatWeapon;
        final String combatStyle;
        final int pointsEarned;
        final int totalPoints;
        final int roundsPlayed;
        final int roundsWon;
        final int roundsLost;
        final String roundResult;
        final boolean autoRetaliateOff;
        final boolean boardingAttemptPending;
        final long stateEnteredAt;
        final long lastProgressAt;
        final long lastMovementCommandAt;
        final long lastAttackCommandAt;

        private OverlaySnapshot(
                String state,
                String detail,
                String location,
                int activityPercent,
                boolean activityRecoveryActive,
                int activityRecoveryStart,
                int activityRecoveryTarget,
                String targetPortal,
                int targetCrowd,
                boolean targetHasAttackAction,
                String openingPortal,
                int remainingPortals,
                int readyPortals,
                String combatWeapon,
                String combatStyle,
                int pointsEarned,
                int totalPoints,
                int roundsPlayed,
                int roundsWon,
                int roundsLost,
                String roundResult,
                boolean autoRetaliateOff,
                boolean boardingAttemptPending,
                long stateEnteredAt,
                long lastProgressAt,
                long lastMovementCommandAt,
                long lastAttackCommandAt) {
            this.state = state;
            this.detail = detail;
            this.location = location;
            this.activityPercent = activityPercent;
            this.activityRecoveryActive = activityRecoveryActive;
            this.activityRecoveryStart = activityRecoveryStart;
            this.activityRecoveryTarget = activityRecoveryTarget;
            this.targetPortal = targetPortal;
            this.targetCrowd = targetCrowd;
            this.targetHasAttackAction = targetHasAttackAction;
            this.openingPortal = openingPortal;
            this.remainingPortals = remainingPortals;
            this.readyPortals = readyPortals;
            this.combatWeapon = combatWeapon;
            this.combatStyle = combatStyle;
            this.pointsEarned = pointsEarned;
            this.totalPoints = totalPoints;
            this.roundsPlayed = roundsPlayed;
            this.roundsWon = roundsWon;
            this.roundsLost = roundsLost;
            this.roundResult = roundResult;
            this.autoRetaliateOff = autoRetaliateOff;
            this.boardingAttemptPending = boardingAttemptPending;
            this.stateEnteredAt = stateEnteredAt;
            this.lastProgressAt = lastProgressAt;
            this.lastMovementCommandAt = lastMovementCommandAt;
            this.lastAttackCommandAt = lastAttackCommandAt;
        }

        private static OverlaySnapshot initial() {
            return new OverlaySnapshot(
                    RuntimeState.STOPPED.name(),
                    "",
                    "Stopped",
                    -1,
                    false,
                    -1,
                    -1,
                    "None",
                    0,
                    false,
                    "None",
                    portals.size(),
                    0,
                    "Unknown",
                    "Unknown",
                    0,
                    -1,
                    0,
                    0,
                    0,
                    "None",
                    false,
                    false,
                    0L,
                    0L,
                    0L,
                    0L);
        }
    }

    private boolean attackSpinner() {
        if (isInteractingWithSpinner()) {
            disableSpecialAttackIfEnabled();
            transitionTo(RuntimeState.KILL_SPINNER, "nearby Spinner");
            return true;
        }
        Rs2NpcModel spinner = Microbot.getRs2NpcCache().query()
                .withIds(SPINNER_IDS.stream().mapToInt(Integer::intValue).toArray())
                .where(npc -> npc.getNpc() != null
                        && !npc.getNpc().isDead()
                        && distanceFromPlayerInTiles(npc) <= SPINNER_PORTAL_RADIUS)
                .nearestOnClientThread();
        if (spinner == null) {
            return false;
        }
        disableSpecialAttackIfEnabled();
        transitionTo(RuntimeState.KILL_SPINNER, "nearby Spinner");
        if (isInteractingWith(spinner.getNpc())) {
            return true;
        }
        dispatchAttack(spinner);
        return true;
    }

    private void activateSpecialAttackIfReady(Portal portal) {
        if (!useSpecialAttackForPortal(portal) || !isInteractingWithPortal(portal)) {
            disableSpecialAttackIfEnabled();
            return;
        }

        Optional<SpecialAttackWeaponEnum> specialAttackWeapon = getEquippedSpecialAttackWeapon();
        if (specialAttackWeapon.isEmpty()) {
            disableSpecialAttackIfEnabled();
            return;
        }

        Rs2Combat.setSpecState(true, specialAttackWeapon.get().getEnergyRequired());
    }

    private boolean useSpecialAttackForPortal(Portal portal) {
        switch (portal) {
            case PURPLE:
                return config.usePurpleSpecialAttack();
            case BLUE:
                return config.useBlueSpecialAttack();
            case YELLOW:
                return config.useYellowSpecialAttack();
            case RED:
                return config.useRedSpecialAttack();
            default:
                return false;
        }
    }

    private static void disableSpecialAttackIfEnabled() {
        if (Rs2Combat.getSpecState()) {
            Rs2Combat.setSpecState(false);
        }
    }

    private Optional<SpecialAttackWeaponEnum> getEquippedSpecialAttackWeapon() {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (weapon == null || weapon.getName() == null) {
            return Optional.empty();
        }

        String weaponName = weapon.getName().toLowerCase(Locale.ROOT);
        return Arrays.stream(SpecialAttackWeaponEnum.values())
                .sorted(Comparator.comparingInt((SpecialAttackWeaponEnum specWeapon) -> specWeapon.getName().length()).reversed())
                .filter(specWeapon -> weaponName.contains(specWeapon.getName()))
                .findFirst();
    }

    @Override
    public void shutdown() {
        Microbot.log("Pest control about to shutdown");
        initialise = true;
        openingSideReached = false;
        wasInPestControl = false;
        pendingPostRoundRestore = false;
        autoRetaliateConfirmedOff = false;
        autoRetaliateDisableLogged = false;
        selectedPortal = null;
        openingPortal = null;
        activeLoadoutKey = null;
        loadoutRetryAfterByKey.clear();
        loadoutFailuresLogged.clear();
        startupCombatPrepared = false;
        startupAutocastPrepared = false;
        lastBoardingAttemptAt = 0L;
        boardingAttemptPending = false;
        loginUnavailableSince = 0L;
        lastRoundExitAt = 0L;
        roundCompletionPending = false;
        pendingRoundTeamOutcome = TeamOutcome.UNKNOWN;
        pendingRoundRewardConfirmed = false;
        pendingRoundRewardSource = "NONE";
        pendingRoundAwardedPoints = 0;
        currentRoundStartingPoints = -1;
        pendingRoundStartingPoints = -1;
        pendingRoundObservedPoints = -1;
        pendingRoundFinalActivity = -1;
        pendingRoundDestroyedPortals = 0;
        lastPortalCameraTurnAt = 0L;
        lastGateInteractionAt = 0L;
        lastGateLocation = null;
        activeGateCrossingTarget = null;
        gateReuseCooldowns.clear();
        perimeterDetourUntil = 0L;
        clearSpinnerCommitment();
        clearBrawlerCommitment();
        overlayLocation = "Stopped";
        overlayActivityPercent = -1;
        overlayTargetPortal = null;
        overlayTargetCrowd = 0;
        overlayTargetHasAttackAction = false;
        runtimeState = RuntimeState.STOPPED;
        runtimeDetail = "";
        stateEnteredAt = System.currentTimeMillis();
        publishOverlaySnapshot();
        super.shutdown();
    }
}
