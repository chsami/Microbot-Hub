package net.runelite.client.plugins.microbot.sailing.features.trials;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.sailing.SailingConfig;
import net.runelite.client.plugins.microbot.sailing.features.trials.data.*;
import net.runelite.client.plugins.microbot.sailing.features.trials.debug.BoatPathHelper;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class TrialsScript {

    private final Client client;
    private final Rs2BoatCache boatCache;
    private final EventBus eventBus;
    private final SailingConfig config;

    private int currentWaypointIndex = 0;
    private TrialRoute activeRoute = null;
    private boolean rumActionPending;
    private boolean expectedRumState;
    private volatile int rumInteractionTick = -1;

    private final Set<Integer> TRIAL_CRATE_ANIMS = Set.of(8867);
    private final Set<Integer> SPEED_BOOST_ANIMS = Set.of(13159, 13160, 13163);
    private final Set<Integer> DECORATION_ANIMS = Set.of(1071, 13537, 13538, 13539);

    private static final int VISIT_TOLERANCE = 15;
    private static final int WAYPOINT_VISIT_TOLERANCE = 5;
    private static final int RUM_INTERACTION_DISTANCE = 15;
    private static final int RUM_RETRY_TICKS = 2;

    private static final int WIND_MOTE_INACTIVE_SPRITE_ID = 7076;
    private static final int WIND_MOTE_ACTIVE_SPRITE_ID = 7075;


    private TrialInfo currentTrial = null;

    
    private int lastVisitedIndex = -1;

    
    private int toadsThrown = 0;

    // Number of consecutive game ticks where TrialInfo.getCurrent(client) returned null
    // Used to allow a grace period before clearing currentTrial during transient nulls
    private int nullTrialConsecutiveTicks = 0;

    private final Set<Integer> TRIAL_BOAT_GAMEOBJECT_IDS = Set.of(
            ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT,
            ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT,
            ObjectID.SAILING_BT_JUBBLY_JIVE_TOAD_SUPPLIES_PARENT);

    private final Map<Integer, List<GameObject>> toadFlagsById = new HashMap<>();
    
    private final Map<Integer, GameObject> trialCratesById = new HashMap<>();
    
    private final Map<Integer, List<GameObject>> trialBoostsById = new HashMap<>();
    
    private GameObject sailGameObject = null;

    
    private final Set<WorldPoint> obstacleWorldPoints = new HashSet<>();

    
    private final Map<Integer, GameObject> trialBoatsById = new HashMap<>();

    
    private int isInTrial;

    private int boardedBoat;

    
    private Directions currentHeadingDirection = Directions.North;

    
    private Directions requestedHeadingDirection = currentHeadingDirection;

    
    private Widget windMoteButtonWidget;


    private double minCratePickupDistance;


    private double maxCratePickupDistance;

    private int boatSpawnedAngle;
    private boolean needsTrim;
    private int windMoteReleasedTick;
    private Directions hoveredHeadingDirection;
    private int boatSpawnedFineX;
    private int boatSpawnedFineZ;
    private int boatBaseSpeed;
    private int boatSpeedCap;
    private int boatSpeedBoostDuration;
    private int boatAcceleration;

    @Inject
    public TrialsScript(Client client, Rs2BoatCache boatCache, EventBus eventBus, SailingConfig config) {
        this.client = client;
        this.boatCache = boatCache;
        this.eventBus = eventBus;
        this.config = config;
    }

    public void register() {
        eventBus.register(this);
    }

    public void unregister() {
        eventBus.unregister(this);
    }

    public void run(SailingConfig config) {
        try {
            TrialInfo info = Microbot.getClientThread().invoke(() -> TrialInfo.getCurrent(client));

            if (info == null) {
                resetState();
                return;
            }

            TrialRoute route = findRoute(info.Location, info.Rank);
            if (route == null) {
                log.warn("No route found for location={} rank={}", info.Location, info.Rank);
                return;
            }

            List<WorldPoint> routePoints = route.getInterpolatedPoints();
            if (routePoints == null || routePoints.isEmpty()) {
                return;
            }

            if (currentWaypointIndex >= routePoints.size()) {
                currentWaypointIndex = 0;
            }

            WorldPoint boatPos = getBoatPosition();
            if (boatPos == null) {
                return;
            }

            if (!route.equals(activeRoute)) {
                activeRoute = route;
                currentWaypointIndex = 0;
            }

            currentWaypointIndex = getNextWaypointIndex(routePoints, currentWaypointIndex, boatPos);
            WorldPoint target = routePoints.get(currentWaypointIndex);

            final WorldPoint hintTarget = target;
            Microbot.getClientThread().invoke(() -> client.setHintArrow(hintTarget));

            int currentTick = Microbot.getClientThread().invoke(client::getTickCount);
            if (config.autoNavigate() && currentTick != rumInteractionTick) {
                navigateToWaypoint(target);
            }

        } catch (Exception ex) {
            log.error("Error in trials script", ex);
        }
    }

    private TrialRoute findRoute(TrialLocations location, TrialRanks rank) {
        for (TrialRoute route : TrialRoute.AllTrialRoutes) {
            if (route.Location == location && route.Rank == rank) {
                return route;
            }
        }
        return null;
    }

    static int getNextWaypointIndex(List<WorldPoint> routePoints, int currentWaypointIndex, WorldPoint boatPosition) {
        int currentDistance = boatPosition.distanceTo(routePoints.get(currentWaypointIndex));
        if (currentDistance <= WAYPOINT_VISIT_TOLERANCE) {
            return (currentWaypointIndex + 1) % routePoints.size();
        }

        int lastWaypointIndex = routePoints.size() - 1;
        while (currentWaypointIndex < lastWaypointIndex
                && hasPassedWaypoint(routePoints.get(currentWaypointIndex), routePoints.get(currentWaypointIndex + 1), boatPosition)) {
            currentWaypointIndex++;
        }
        return currentWaypointIndex;
    }

    private static boolean hasPassedWaypoint(WorldPoint waypoint, WorldPoint nextWaypoint, WorldPoint boatPosition) {
        long segmentX = nextWaypoint.getX() - waypoint.getX();
        long segmentY = nextWaypoint.getY() - waypoint.getY();
        long boatX = boatPosition.getX() - waypoint.getX();
        long boatY = boatPosition.getY() - waypoint.getY();
        return boatX * segmentX + boatY * segmentY > 0;
    }

    private WorldPoint getBoatPosition() {
        return Microbot.getClientThread().invoke(() -> {
            var localPlayer = client.getLocalPlayer();
            if (localPlayer == null) {
                return null;
            }
            return BoatLocation.fromLocal(client, localPlayer.getLocalLocation());
        });
    }

    private void navigateToWaypoint(WorldPoint target) {
        var boat = Microbot.getClientThread().invoke(() -> boatCache.getLocalBoat());
        if (boat == null) {
            log.info("navigateToWaypoint: No boat found in cache");
            return;
        }

        boat.sailTo(target);
    }

    public void shutdown() {
        resetState();
    }

    private void resetState() {
        currentWaypointIndex = 0;
        activeRoute = null;
        rumActionPending = false;
        rumInteractionTick = -1;
        lastVisitedIndex = -1;
        Microbot.getClientThread().invoke(() -> client.clearHintArrow());
    }

    @Subscribe
    public void onClientTick(ClientTick clientTick) {
        var localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        var tick = client.getTickCount();
        var position = BoatLocation.fromLocal(client, localPlayer.getLocalLocation());
        if (tick <= 0 || position == null) {
            return;
        }

        if (BoatPathHelper.HasTickData(tick)) {
            //log.info("Adding visited point for tick {}: {}", tick, position);
            BoatPathHelper.AddVisitedPoint(tick, position);
        } else {
            BoatPathHelper.StartNewTick(tick, position, currentHeadingDirection);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (client == null || client.getLocalPlayer() == null) {
            return;
        }

        updateFromVarbits();
        updateCurrentTrial();
        updateCurrentHeading();

        updateWindMoteButtonWidget();

        final var player = client.getLocalPlayer();
        var boatLocation = BoatLocation.fromLocal(client, player.getLocalLocation());

        if (boatLocation == null)
            return;

        interactWithRumBoat(boatLocation);

        var active = getActiveTrialRoute();
        if (active != null) {
            markNextWaypointVisited(boatLocation, active, VISIT_TOLERANCE);
        }
    }

    private void interactWithRumBoat(WorldPoint boatLocation) {
        if (!config.trials() || !config.autoNavigate() || isInTrial == 0 || currentTrial == null) {
            return;
        }

        int currentTick = client.getTickCount();
        if (rumActionPending) {
            if (currentTrial.HasRum == expectedRumState) {
                rumActionPending = false;
                return;
            }
            if (currentTick - rumInteractionTick < RUM_RETRY_TICKS) {
                return;
            }
        }

        var rumBoat = trialBoatsById.get(currentTrial.HasRum
                ? ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT
                : ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT);
        var rumBoatLocation = getWorldEntityLocation(rumBoat);
        if (rumBoatLocation == null) {
            return;
        }

        var action = getRumAction(currentTrial, boatLocation.distanceTo(rumBoatLocation));
        if (action != null) {
            new Rs2TileObjectModel(rumBoat).click(action);
            rumActionPending = true;
            expectedRumState = !currentTrial.HasRum;
            rumInteractionTick = currentTick;
        }
    }

    private WorldPoint getWorldEntityLocation(GameObject object) {
        if (object == null || object.getWorldView() == null || client.getTopLevelWorldView() == null) {
            return null;
        }

        var worldEntity = client.getTopLevelWorldView().worldEntities().byIndex(object.getWorldView().getId());
        return worldEntity == null || worldEntity.getLocalLocation() == null
                ? null
                : WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
    }

    static String getRumAction(TrialInfo trial, int distance) {
        if (trial == null
                || trial.Location != TrialLocations.TemporTantrum
                || trial.TotalPrimaryObjectivesNeeded <= 0
                || trial.CollectedPrimaryObjectives >= trial.TotalPrimaryObjectivesNeeded
                || distance > RUM_INTERACTION_DISTANCE) {
            return null;
        }
        return trial.HasRum ? "Deliver-rum" : "Collect-rum";
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.SAILING_BOAT_SPAWNED_ANGLE) {
            boatSpawnedAngle = event.getValue();
            updateCurrentHeadingFromVarbit(boatSpawnedAngle);
        }

        if (event.getVarbitId() == VarbitID.SAILING_BT_IN_TRIAL) {
            updateToadsThrown(currentTrial);
        }

        trackCratePickups(event);
    }

    private void updateCurrentHeadingFromVarbit(int value) {
        var ordinal = value / 128;
        var directions = Directions.values();
        if (ordinal < 0 || ordinal >= directions.length) {
            return;
        }
        currentHeadingDirection = directions[ordinal];
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        var obj = event.getGameObject();
        if (obj == null) {
            return;
        }
        var id = obj.getId();

        var isToadFlag = ToadFlagGameObject.All.stream().anyMatch(t -> t.GameObjectIds.contains(id));
        if (isToadFlag) {
            toadFlagsById.computeIfAbsent(id, k -> new ArrayList<>()).add(obj);
        }

        var isTrialBoat = TRIAL_BOAT_GAMEOBJECT_IDS.contains(id);
        if (isTrialBoat) {
            trialBoatsById.put(id, obj);
            //log.info("Tracked trial boat gameobject id {} at {} - {}", id, obj.getWorldLocation(), BoatLocation.fromLocal(client, obj.getLocalLocation()));

        }

        var isObstacle = ObstacleTracking.OBSTACLE_GAMEOBJECT_IDS.contains(id);
        if (isObstacle) {
            // Add world points for all tiles covered by this obstacle's footprint
            try {
                var worldView = client.getTopLevelWorldView();
                var scene = worldView != null ? worldView.getScene() : null;
                if (scene != null) {
                    var min = obj.getSceneMinLocation();
                    var max = obj.getSceneMaxLocation();
                    if (min != null && max != null) {
                        var plane = worldView.getPlane();
                        for (var x = min.getX(); x <= max.getX(); x++) {
                            for (var y = min.getY(); y <= max.getY(); y++) {
                                WorldPoint wp = WorldPoint.fromScene(worldView, x, y, plane);
                                obstacleWorldPoints.add(wp);
                            }
                        }
                    } else {
                        obstacleWorldPoints.add(obj.getWorldLocation());
                    }
                } else {
                    obstacleWorldPoints.add(obj.getWorldLocation());
                }
            } catch (Exception ex) {
                obstacleWorldPoints.add(obj.getWorldLocation());
            }
            if (currentTrial != null) {
                removeGameObjectFromScene(obj);
            }
        }

        var isSail = AllSails.GAMEOBJECT_IDS.contains(id);
        if (isSail) {
            sailGameObject = obj;
        }

        var renderable = obj.getRenderable();
        if (renderable != null) {
            if (renderable instanceof DynamicObject) {
                var dynObj = (DynamicObject) renderable;
                var anim = dynObj.getAnimation();
                var animId = anim != null ? anim.getId() : -1;
                if (TRIAL_CRATE_ANIMS.contains(animId)) {
                    trialCratesById.put(id, obj);
                } else if (SPEED_BOOST_ANIMS.contains(animId)) {
                    trialBoostsById.computeIfAbsent(id, k -> new ArrayList<>()).add(obj);
                } else if (DECORATION_ANIMS.contains(animId)) {
                    removeGameObjectFromScene(obj);
                }
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        var obj = event.getGameObject();
        if (obj == null)
            return;

        var id = obj.getId();

        var flagList = toadFlagsById.get(id);
        if (flagList != null) {
            flagList.removeIf(x -> x == null || x == obj);
            if (flagList.isEmpty()) {
                toadFlagsById.remove(id);
            }
        }

        if (trialBoatsById.get(id) == obj) {
            trialBoatsById.remove(id);
        }

        if (trialCratesById.get(id) == obj) {
            trialCratesById.remove(id);
        }

        var boostList = trialBoostsById.get(id);
        if (boostList != null) {
            boostList.removeIf(x -> x == null || x == obj);
            if (boostList.isEmpty()) {
                trialBoostsById.remove(id);
            }
        }

        if (sailGameObject == obj) {
            sailGameObject = null;
        }
    }

    @Subscribe
    public void onWorldViewUnloaded(WorldViewUnloaded event) {
        for (var toadFlagList : toadFlagsById.values()) {
            toadFlagList.removeIf(obj -> event.getWorldView() == obj.getWorldView());
        }
        toadFlagsById.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        for (var boat : trialBoatsById.values()) {
            if (event.getWorldView() == boat.getWorldView()) {
                trialBoatsById.remove(boat.getId());
            }
        }

        for (var crate : trialCratesById.values()) {
            if (event.getWorldView() == crate.getWorldView()) {
                trialCratesById.remove(crate.getId());
            }
        }

        for (var boostList : trialBoostsById.values()) {
            boostList.removeIf(obj -> event.getWorldView() == obj.getWorldView());
        }
        trialBoostsById.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        if (sailGameObject != null && event.getWorldView() == sailGameObject.getWorldView()) {
            sailGameObject = null;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING) {
            // on region changes the tiles and gameobjects get set to null
            reset();
        }
    }


    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event == null) {
            return;
        }

        if (event.getGroup().equals(SailingConfig.configGroup)) {
            if (event.getKey().equals("trials") && event.getNewValue().equals("false")) {
                resetState();
            }
            return;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) {
            return;
        }

        var msg = e.getMessage().toLowerCase();
        if (msg == null || msg.isEmpty()) {
            return;
        }

        String WIND_MOTE_RELEASED_TEXT = "you release the wind mote for a burst of speed";
        if (msg.contains(WIND_MOTE_RELEASED_TEXT)) {
            windMoteReleasedTick = client.getTickCount();
        }
        String TRIM_AVAILABLE_TEXT = "you feel a gust of wind.";
        String TRIM_SUCCESS_TEXT = "you trim the sails";
        String TRIM_FAIL_TEXT = "the wind dies down";
        if (msg.contains(TRIM_AVAILABLE_TEXT)) {
            needsTrim = true;
        } else if (msg.contains(TRIM_SUCCESS_TEXT) || msg.contains(TRIM_FAIL_TEXT)) {
            needsTrim = false;
        }
    }

    private void updateCurrentTrial() {
        var newTrialInfo = TrialInfo.getCurrent(client);
        if (newTrialInfo != null) {
            nullTrialConsecutiveTicks = 0;

            // If the trial changed (location/rank/reset time), reset route state
            if (currentTrial == null || currentTrial.Location != newTrialInfo.Location || currentTrial.Rank != newTrialInfo.Rank || newTrialInfo.CurrentTimeSeconds < currentTrial.CurrentTimeSeconds) {
                resetRouteData();
            }

            updateToadsThrown(newTrialInfo);
            currentTrial = newTrialInfo;
        } else {
            if (currentTrial != null) {
                nullTrialConsecutiveTicks += 1;
                if (nullTrialConsecutiveTicks >= 18) {
                    resetRouteData();
                    currentTrial = null;
                }
            } else {
                nullTrialConsecutiveTicks = 0;
            }
        }
    }


    private void resetRouteData() {
        lastVisitedIndex = -1;
        toadsThrown = 0;
        rumActionPending = false;
        rumInteractionTick = -1;
    }

    private void reset() {
        requestedHeadingDirection = currentHeadingDirection;
    }

    private void updateToadsThrown(TrialInfo newTrialInfo) {
        if (currentTrial == null || isInTrial == 0 || newTrialInfo.CurrentTimeSeconds < currentTrial.CurrentTimeSeconds || newTrialInfo.CurrentTimeSeconds == 0) {
            toadsThrown = 0;
            return;
        }
        if (newTrialInfo.ToadCount < currentTrial.ToadCount) {
            toadsThrown += 1;
        }
    }

    public void markNextWaypointVisited(final WorldPoint player, final TrialRoute route, final int tolerance) {
        if (player == null || route == null) {
            return;
        }
        var points = route.getInterpolatedPoints();
        if (points == null || points.isEmpty()) {
            return;
        }
        var nextIdx = lastVisitedIndex + 1;
        if (nextIdx >= points.size()) {
            return;
        }
        var target = points.get(nextIdx);
        if (target == null) {
            return;
        }
        var dist = Math.hypot(player.getX() - target.getX(), player.getY() - target.getY());
        if (dist <= tolerance) {
            lastVisitedIndex = nextIdx;
        }
    }

    public List<Integer> getNextIndicesAfterLastVisited(final TrialRoute route, final int limit) {
        if (route == null || limit <= 0) {
            return Collections.emptyList();
        }
        var points = route.getInterpolatedPoints();
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        var start = Math.max(0, lastVisitedIndex);
        if (start >= points.size()) {
            return Collections.emptyList();
        }
        var out = new ArrayList<Integer>(limit);
        var nextPortal = route.PortalDirections.stream()
                .filter(x -> route.getInterpolatedIndex(x.Index) >= lastVisitedIndex)
                .min((a, b) -> Integer.compare(route.getInterpolatedIndex(a.Index), route.getInterpolatedIndex(b.Index)))
                .orElse(null);
        for (var i = start; i < points.size() && out.size() < limit; i++) {
            if (nextPortal != null && i > route.getInterpolatedIndex(nextPortal.Index)) {
                break;
            }
            out.add(i);
        }
        return out;
    }

    public List<WorldPoint> getVisibleLineForRoute(final WorldPoint player, final TrialRoute route, final int limit) {
        if (player == null || route == null || lastVisitedIndex == -1) {
            return Collections.emptyList();
        }

        final List<Integer> nextIdx = getNextIndicesAfterLastVisited(route, limit);
        if (nextIdx.isEmpty()) {
            return Collections.emptyList();
        }

        var points = route.getInterpolatedPoints();
        List<WorldPoint> out = new ArrayList<>();
        for (var idx : nextIdx) {
            if (idx >= 0 && idx < points.size()) {
                out.add(points.get(idx));
            }
        }
        return out;
    }

    public PortalDirection getVisiblePortalDirection(TrialRoute route) {
        var portalDirection = route.PortalDirections.stream()
                .filter(x -> {
                    int interpolatedIdx = route.getInterpolatedIndex(x.Index);
                    return interpolatedIdx - 1 == lastVisitedIndex || interpolatedIdx == lastVisitedIndex || interpolatedIdx + 1 == lastVisitedIndex;
                })
                .min((a, b) -> Integer.compare(route.getInterpolatedIndex(a.Index), route.getInterpolatedIndex(b.Index)))
                .orElse(null);

        return portalDirection;
    }

    public List<GameObject> getToadFlagGameObjectsForIds(Set<Integer> ids) {
        var out = new ArrayList<GameObject>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (var id : ids) {
            var list = toadFlagsById.get(id);
            if (list != null && !list.isEmpty()) {
                out.addAll(list);
            }
        }
        return out;
    }

    public TrialRoute getActiveTrialRoute() {
        if (currentTrial == null)
            return null;

        for (var route : TrialRoute.AllTrialRoutes) {
            if (route == null) {
                continue;
            }

            if (route.Location == currentTrial.Location && route.Rank == currentTrial.Rank) {
                return route;
            }
        }
        return null;
    }

    public List<WorldPoint> getVisibleActiveLineForPlayer(final WorldPoint player, final int limit) {
        var route = getActiveTrialRoute();
        if (route == null) {
            return Collections.emptyList();
        }

        return getVisibleLineForRoute(player, route, limit);
    }

    public List<Integer> getNextUnvisitedIndicesForActiveRoute(final int limit) {
        var route = getActiveTrialRoute();
        if (route == null) {
            return Collections.emptyList();
        }
        return getNextIndicesAfterLastVisited(route, limit);
    }

    public int getHighlightedToadFlagIndex() {
        var route = getActiveTrialRoute();
        if (route == null || currentTrial == null) {
            return 0;
        }
        return getHighlightedToadFlagIndex(route);
    }

    private int getHighlightedToadFlagIndex(TrialRoute route) {
        return toadsThrown < route.ToadOrder.size() ? toadsThrown : 0;
    }

    public List<GameObject> getToadFlagToHighlight() {
        if (currentTrial == null || currentTrial.Location != TrialLocations.JubblyJive || currentTrial.ToadCount <= 0) {
            return Collections.emptyList();
        }

        var route = getActiveTrialRoute();
        if (route == null || route.ToadOrder == null || route.ToadOrder.isEmpty()) {
            return Collections.emptyList();
        }

        var nextToadIdx = getHighlightedToadFlagIndex(route);
        if (nextToadIdx >= 0 && nextToadIdx < route.ToadOrder.size()) {
            var nextToadColor = route.ToadOrder.get(nextToadIdx);
            var nextToadGameObject = ToadFlagGameObject.getByColor(nextToadColor);
            var cached = getToadFlagGameObjectsForIds(nextToadGameObject.GameObjectIds);
            if (!cached.isEmpty()) {
                return cached;
            }
        }

        return Collections.emptyList();
    }

    public Collection<GameObject> getTrialBoatsToHighlight() {
        var route = getActiveTrialRoute();
        if (route == null || currentTrial == null || trialBoatsById.isEmpty()) {
            return Collections.emptyList();
        }

        if (route.Location == TrialLocations.JubblyJive && !currentTrial.HasToads) {
            return trialBoatsById.values();
        }

        if (route.Location == TrialLocations.TemporTantrum) {
            if (currentTrial.HasRum) {
                var boat = trialBoatsById.get(ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT);
                if (boat != null) {
                    return List.of(boat);
                }
            } else {
                var boat = trialBoatsById.get(ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT);
                if (boat != null) {
                    return List.of(boat);
                }
            }
        }

        return Collections.emptyList();
    }

    private void handleHeadingClicks(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.SET_HEADING) {
            return;
        }
        requestedHeadingDirection = Directions.values()[event.getId()];
    }

    private void updateCurrentHeading() {
        if (currentHeadingDirection == null) {
            currentHeadingDirection = Directions.South;
        }

        if (requestedHeadingDirection == null) {
            requestedHeadingDirection = currentHeadingDirection;
            return;
        }

        if (currentHeadingDirection == requestedHeadingDirection) {
            return;
        }

        var all = Directions.values();
        var n = all.length;
        var currentIndex = currentHeadingDirection.ordinal();
        var targetIndex = requestedHeadingDirection.ordinal();

        var forwardSteps = (targetIndex - currentIndex + n) % n;
        var backwardSteps = (currentIndex - targetIndex + n) % n;

        if (forwardSteps == 0) {
            return;
        }

        if (forwardSteps <= backwardSteps) {
            currentIndex = (currentIndex + 1) % n;
        } else {
            currentIndex = (currentIndex - 1 + n) % n;
        }

        currentHeadingDirection = all[currentIndex];
    }

    private void manageHeadingHovers(PostMenuSort event) {
        var entries = client.getMenuEntries();
        var headingEntry = Arrays.stream(entries)
                .filter(e -> e.getOption().equals("Set heading"))
                .findFirst().orElse(null);
        if (headingEntry != null) {
            hoveredHeadingDirection = Directions.values()[headingEntry.getIdentifier()];
        }
    }

    private void updateFromVarbits() {
        //todo check VarbitID.SAILING_BOAT_TIME_TILL_TRIM and VarbitID.SAILING_BOAT_TIME_TRIM_WINDOW to see if they're working in the future
        boatSpawnedAngle = client.getVarbitValue(VarbitID.SAILING_BOAT_SPAWNED_ANGLE);
        boatSpawnedFineX = client.getVarbitValue(VarbitID.SAILING_BOAT_SPAWNED_FINEX);
        boatSpawnedFineZ = client.getVarbitValue(VarbitID.SAILING_BOAT_SPAWNED_FINEZ);
        boatBaseSpeed = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED);
        boatSpeedCap = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP);
        boatSpeedBoostDuration = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDBOOST_DURATION);
        boatAcceleration = client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION);
        isInTrial = client.getVarbitValue(VarbitID.SAILING_BT_IN_TRIAL);
        boardedBoat = client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT);
    }

    private void removeGameObjectFromScene(GameObject gameObject) {
        if (gameObject != null) {
            var renderable = gameObject == null ? null : gameObject.getRenderable();
            if (renderable != null) {
                var model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
                if (model != null) {
                    var scene = client.getTopLevelWorldView().getScene();
                    if (scene != null) {
                        scene.removeGameObject(gameObject);
                    }
                    var playerWv = client.getLocalPlayer().getWorldView();
                    var playerScene = playerWv != null ? playerWv.getScene() : null;
                    if (playerScene != null) {
                        playerScene.removeGameObject(gameObject);
                    }
                }
            }
        }
    }

    private void updateWindMoteButtonWidget() {
        if (boardedBoat == 0) {
            windMoteButtonWidget = null;
            return;
        }

        if (windMoteButtonWidget != null && !windMoteButtonWidget.isHidden()) {
            return;
        }

        var widget = client.getWidget(InterfaceID.SailingSidepanel.FACILITIES_ROWS);
        if (widget == null) {
            //log.info("updateWindMoteButtonWidget: FACILITIES_ROWS widget is null");
            return;
        }

        var facilityChildren = widget.getChildren();
        Widget button = null;
        if (facilityChildren != null) {
            for (var childWidget : facilityChildren) {
                if (childWidget != null && (childWidget.getSpriteId() == WIND_MOTE_INACTIVE_SPRITE_ID || childWidget.getSpriteId() == WIND_MOTE_ACTIVE_SPRITE_ID)) {
                    button = childWidget;
                    break;
                }
            }
        }
        if (button != null) {
            if (windMoteButtonWidget == null) {
                windMoteButtonWidget = button;
            }
        }
    }

    private void trackCratePickups(VarbitChanged event) {
        if (event.getVarbitId() >= VarbitID.SAILING_BT_OBJECTIVE0 && event.getVarbitId() <= VarbitID.SAILING_BT_OBJECTIVE95) {
            var closestCrate = getClosestTrialCrate();
            if (closestCrate != null) {
                var player = client.getLocalPlayer();
                if (player != null) {
                    var playerPoint = BoatLocation.fromLocal(client, player.getLocalLocation());
                    if (playerPoint != null) {
                        var cratePoint = closestCrate.getWorldLocation();
                        double lastCratePickupDistance = Math.hypot(Math.abs(playerPoint.getX() - cratePoint.getX()), Math.abs(playerPoint.getY() - cratePoint.getY()));
                        log.info("Picked up crate from distance: {}", lastCratePickupDistance);
                        if (minCratePickupDistance == 0 || lastCratePickupDistance < minCratePickupDistance) {
                            minCratePickupDistance = lastCratePickupDistance;
                        }
                        if (lastCratePickupDistance > maxCratePickupDistance) {
                            maxCratePickupDistance = lastCratePickupDistance;
                        }
                    }
                }
            }
        }
    }

    private GameObject getClosestTrialCrate() {
        GameObject closest = null;
        var closestDist = Double.MAX_VALUE;

        var player = client.getLocalPlayer();
        if (player == null) {
            return null;
        }
        var playerPoint = BoatLocation.fromLocal(client, player.getLocalLocation());
        if (playerPoint == null) {
            return null;
        }

        for (var crateEntry : trialCratesById.entrySet()) {
            var crate = crateEntry.getValue();
            if (crate == null) {
                continue;
            }
            var cratePoint = crate.getWorldLocation();
            var dist = playerPoint.distanceTo(cratePoint);
            if (dist < closestDist) {
                closestDist = dist;
                closest = crate;
            }
        }
        return closest;
    }

    private void logCrateAndBoostSpawns(GameObjectSpawned event) {
        var gameObject = event.getGameObject();
        if (gameObject == null) {
            return;
        }

        var renderable = gameObject.getRenderable();
        if (!(renderable instanceof net.runelite.api.DynamicObject)) {
            return; // not an animating dynamic object
        }

        var dyn = (net.runelite.api.DynamicObject) renderable;
        var anim = dyn.getAnimation();
        if (anim == null) {
            return;
        }

        final var animId = anim.getId();
        final var isCrateAnim = TRIAL_CRATE_ANIMS.contains(animId);
        final var isSpeedAnim = SPEED_BOOST_ANIMS.contains(animId);

        if (!isCrateAnim && !isSpeedAnim) {
            return; // ignore unrelated animations
        }

        var wp = gameObject.getWorldLocation();

        var objectComposition = client.getObjectDefinition(gameObject.getId());
        if (objectComposition.getImpostorIds() == null) {
            var name = objectComposition.getName();
            if (Strings.isNullOrEmpty(name) || name.equals("null")) {
                return;
            }
        }

        var minLocation = gameObject.getSceneMinLocation();
        var poly = gameObject.getCanvasTilePoly();

        var type = isCrateAnim ? "CRATE" : "SPEED BOOST";
        if (wp != null) {
            if (isCrateAnim) {
                log.info("[SPAWN] {} -> GameObject id={} world={} (hash={}) minLocation={} poly={}", type, animId, gameObject.getId(), wp, gameObject.getHash(), minLocation, poly);
            }

        } else {
            log.info("[SPAWN] {} -> GameObject id={} (no world point available)", type, gameObject.getId());
        }
    }
}
