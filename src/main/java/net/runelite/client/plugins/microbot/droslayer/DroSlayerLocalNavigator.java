package net.runelite.client.plugins.microbot.droslayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;

import java.util.*;

/**
 * Deliberately small local navigator used only after Efficient Walker has delivered
 * Dro Slayer to a hard-coded cave/room handoff.
 *
 * This class intentionally does NOT call Rs2Walker, shortest-path teleport handling,
 * banked transports, spells, minigames, or camera movement. Movement is limited to
 * direct minimap clicks on collision-reachable tiles in the player's current local
 * scene plus explicit nearby entrance/ladder interactions.
 */
@Slf4j
public final class DroSlayerLocalNavigator {
    private static final int LOCAL_BFS_STEPS = 72;
    private static final int MAX_MINIMAP_DISTANCE = 12;
    private static final long TRANSITION_PENDING_MS = 8_000L;
    private static final WorldPoint STALKER_DEN_INTERIOR_EXIT = new WorldPoint(1298, 9757, 0);
    private static final int[] STALKER_DEN_REGION_IDS = {5272, 5273, 5016};

    private static long lastClickAt;
    private static WorldPoint lastClickTarget;
    private static long transitionPendingUntil;
    private static int currentClickCooldownMs = 650;

    private DroSlayerLocalNavigator() {}

    public static void reset() {
        lastClickAt = 0L;
        lastClickTarget = null;
        transitionPendingUntil = 0L;
        currentClickCooldownMs = 650;
    }

    public static boolean hasPendingTransition() {
        return transitionPendingUntil > System.currentTimeMillis();
    }

    /**
     * Handle only a dialogue that was caused by one of our explicit local entrance clicks.
     * This prevents the normal TRAVELING dialogue cleanup from immediately escaping a fare/
     * confirmation dialogue opened by a cave entrance.
     */
    public static boolean handlePendingTransitionDialogue() {
        if (!hasPendingTransition() || !Rs2Dialogue.isInDialogue()) {
            return false;
        }

        if (Rs2Dialogue.hasContinue()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickContinue(); });
            return true;
        }

        String[] affirmative = {
                "Yes", "Yes please", "Pay", "Enter", "Travel", "Continue",
                "I'll pay", "I want to enter", "Take me", "Go through"
        };
        for (String option : affirmative) {
            if (Rs2Dialogue.hasDialogueOption(option, false)) {
                return DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Rs2Dialogue.clickOption(option, false));
            }
        }
        return false;
    }

    /**
     * Local movement only. No teleports. No camera. No Rs2Walker.
     */
    public static boolean walkTo(WorldPoint target, int radius) {
        if (target == null) return false;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return false;

        int reached = Math.max(0, radius);
        if (here.getPlane() == target.getPlane() && here.distanceTo2D(target) <= reached) {
            return true;
        }
        if (here.getPlane() != target.getPlane()) {
            return false;
        }
        if (Rs2Player.isMoving()) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastClickAt < currentClickCooldownMs) {
            return true;
        }

        // Build one LOCAL collision map only. This has no transport/teleport layer and
        // never touches the camera. The old greedy "closest tile to target" selection
        // could bounce between two tiles at a wall/corner. When the final target is in
        // this collision map we now reconstruct the actual BFS path and click forward
        // along that path instead, so cave movement is monotonic rather than oscillating.
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(here, LOCAL_BFS_STEPS);
        if (reachable == null || reachable.isEmpty()) {
            return false;
        }

        WorldPoint clickTarget = choosePathClickTarget(here, target, reachable);
        if (clickTarget == null) {
            return false;
        }

        // Avoid repeating the exact same failed click forever while stationary.
        if (clickTarget.equals(lastClickTarget) && now - lastClickAt < 1_500L) {
            return true;
        }

        Point point = Rs2MiniMap.worldToMinimap(clickTarget);
        if (point == null || !Rs2MiniMap.isPointInsideMinimap(point)) {
            return false;
        }

        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Microbot.getMouse().click(point); });
        lastClickTarget = clickTarget;
        lastClickAt = now;
        currentClickCooldownMs = DroSlayerRuntime.sampleLocalWalkCooldownMs();
        return true;
    }

    private static WorldPoint choosePathClickTarget(WorldPoint here,
                                                     WorldPoint target,
                                                     Map<WorldPoint, Integer> reachable) {
        Integer targetSteps = reachable.get(target);
        if (targetSteps != null) {
            List<WorldPoint> path = reconstructReachablePath(here, target, reachable);
            // Pick the furthest point on the real collision path that is still on the minimap.
            // This is especially important in Stalker Den, where direct-distance greedy clicks
            // can choose opposite sides of the same wall on alternating ticks.
            for (int i = path.size() - 1; i >= 0; i--) {
                WorldPoint candidate = path.get(i);
                if (candidate == null || candidate.equals(here)) continue;
                if (here.distanceTo2D(candidate) > MAX_MINIMAP_DISTANCE) continue;
                Point point = Rs2MiniMap.worldToMinimap(candidate);
                if (point != null && Rs2MiniMap.isPointInsideMinimap(point)) {
                    return candidate;
                }
            }
        }

        // Target may be outside the local scene/BFS horizon. Prefer a reachable frontier
        // tile that actually reduces target distance; only allow a non-improving move when
        // there is no improving tile at all. This avoids the A<->B loop from the old code.
        final int currentDistance = here.distanceTo2D(target);
        WorldPoint improving = reachable.entrySet().stream()
                .filter(e -> isUsableLocalCandidate(here, e))
                .map(Map.Entry::getKey)
                .filter(tile -> tile.distanceTo2D(target) < currentDistance)
                .filter(tile -> !tile.equals(lastClickTarget))
                .min(Comparator
                        .comparingInt((WorldPoint tile) -> tile.distanceTo2D(target))
                        .thenComparing((WorldPoint tile) -> -reachable.getOrDefault(tile, 0)))
                .orElse(null);
        if (improving != null) return improving;

        return reachable.entrySet().stream()
                .filter(e -> isUsableLocalCandidate(here, e))
                .map(Map.Entry::getKey)
                .filter(tile -> !tile.equals(lastClickTarget))
                .min(Comparator
                        .comparingInt((WorldPoint tile) -> tile.distanceTo2D(target))
                        .thenComparing((WorldPoint tile) -> -reachable.getOrDefault(tile, 0)))
                .orElse(null);
    }

    private static boolean isUsableLocalCandidate(WorldPoint here, Map.Entry<WorldPoint, Integer> entry) {
        WorldPoint tile = entry.getKey();
        Integer steps = entry.getValue();
        if (tile == null || steps == null || steps <= 0 || steps > LOCAL_BFS_STEPS) return false;
        if (tile.getPlane() != here.getPlane() || tile.equals(here)) return false;
        if (here.distanceTo2D(tile) > MAX_MINIMAP_DISTANCE) return false;
        Point point = Rs2MiniMap.worldToMinimap(tile);
        return point != null && Rs2MiniMap.isPointInsideMinimap(point);
    }

    /** Reconstruct a shortest local collision path from the distance map returned by Rs2Tile. */
    private static List<WorldPoint> reconstructReachablePath(WorldPoint start,
                                                              WorldPoint target,
                                                              Map<WorldPoint, Integer> reachable) {
        Integer step = reachable.get(target);
        if (step == null || step < 0) return Collections.emptyList();

        List<WorldPoint> reverse = new ArrayList<>();
        WorldPoint cursor = target;
        reverse.add(cursor);
        int guard = LOCAL_BFS_STEPS + 4;

        while (!cursor.equals(start) && step > 0 && guard-- > 0) {
            WorldPoint previous = null;
            for (int dx = -1; dx <= 1 && previous == null; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    WorldPoint neighbor = new WorldPoint(cursor.getX() + dx, cursor.getY() + dy, cursor.getPlane());
                    Integer neighborStep = reachable.get(neighbor);
                    if (neighborStep != null && neighborStep == step - 1) {
                        previous = neighbor;
                        break;
                    }
                }
            }
            if (previous == null) {
                return Collections.emptyList();
            }
            cursor = previous;
            step--;
            reverse.add(cursor);
        }

        if (!cursor.equals(start)) return Collections.emptyList();
        Collections.reverse(reverse);
        return reverse;
    }

    /** True when the player is in the Stalker Den interior coordinate-space. */
    public static boolean isInsideStalkerDen(WorldPoint point) {
        if (point == null) return false;
        int region = point.getRegionID();
        for (int id : STALKER_DEN_REGION_IDS) {
            if (region == id) return true;
        }
        // Fallback for private-server region metadata: the live Stalker Den uses this compact
        // underground coordinate band. Keep it deliberately narrow so unrelated caves do not match.
        return point.getPlane() == 0
                && point.getX() >= 1240 && point.getX() <= 1405
                && point.getY() >= 9680 && point.getY() <= 9825;
    }

    /**
     * Leave Stalker Den without ever handing the underground map to Efficient Walker.
     * We first collision-walk back to the known interior entrance area, then click only a
     * visible nearby exit object. No camera and no teleport/walker transport code is used.
     */
    public static boolean exitStalkerDen() {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (!isInsideStalkerDen(here)) return false;

        if (handlePendingTransitionDialogue()) return true;

        if (here.distanceTo2D(STALKER_DEN_INTERIOR_EXIT) > 6) {
            walkTo(STALKER_DEN_INTERIOR_EXIT, 4);
            return true;
        }

        List<Rs2TileObjectModel> exact = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.SOC_CAVE_ENTRANCE)
                .within(STALKER_DEN_INTERIOR_EXIT, 10)
                .toListOnClientThread();
        if (clickFirstExitCandidate(exact)) return true;

        // Some game revisions use a different object id on the inside face. Restrict the fallback
        // to the known entrance pocket and an exit-action allowlist rather than clicking by name.
        List<Rs2TileObjectModel> nearby = Microbot.getRs2TileObjectCache().query()
                .within(STALKER_DEN_INTERIOR_EXIT, 10)
                .toListOnClientThread();
        if (clickFirstExitCandidate(nearby)) return true;

        // Bring the entrance into view naturally; clickVisibleTransition intentionally refuses
        // to rotate the camera when the object is off screen.
        walkTo(STALKER_DEN_INTERIOR_EXIT, 2);
        return true;
    }

    private static boolean clickFirstExitCandidate(List<Rs2TileObjectModel> objects) {
        if (objects == null || objects.isEmpty()) return false;
        String[] exitActions = {"Exit", "Leave", "Climb-up", "Climb up", "Climb", "Enter", "Go-through"};
        List<TransitionCandidate> candidates = new ArrayList<>();
        for (Rs2TileObjectModel object : objects) {
            if (object == null || object.getWorldLocation() == null) continue;
            String action = firstMatchingAction(object, exitActions);
            if (action == null) continue;
            candidates.add(new TransitionCandidate(object, action,
                    object.getWorldLocation().distanceTo2D(STALKER_DEN_INTERIOR_EXIT)));
        }
        candidates.sort(Comparator.comparingInt(c -> c.distance));
        for (TransitionCandidate candidate : candidates) {
            if (clickVisibleTransition(candidate.object, candidate.action)) return true;
        }
        return false;
    }

    /**
     * Explicit entrance handoff. Efficient Walker gets us to {@code approach}; this method
     * clicks the nearby cave/ladder object only when it is already on screen, so the object
     * helper can never rotate the camera for us.
     */
    public static boolean enterAtHandoff(DroSlayerLocation location, WorldPoint approach) {
        if (location == null || approach == null) return false;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.getPlane() != approach.getPlane() || here.distanceTo2D(approach) > 8) {
            return false;
        }

        // Catacombs of Kourend: the surface statue is the handoff itself.
        // Do not use the generic entrance-action allowlist here; once standing on
        // the recorded tile, explicitly invoke the statue's Investigate action.
        if (isCatacombsLocation(location)) {
            List<Rs2TileObjectModel> nearby = Microbot.getRs2TileObjectCache().query()
                    .within(approach, 8)
                    .toListOnClientThread();
            if (nearby == null || nearby.isEmpty()) {
                return false;
            }

            List<TransitionCandidate> candidates = new ArrayList<>();
            for (Rs2TileObjectModel object : nearby) {
                if (object == null || object.getWorldLocation() == null) continue;
                String action = firstMatchingAction(object, new String[]{"Investigate"});
                if (action == null) continue;
                candidates.add(new TransitionCandidate(object, action,
                        object.getWorldLocation().distanceTo2D(approach)));
            }
            candidates.sort(Comparator.comparingInt(c -> c.distance));
            for (TransitionCandidate candidate : candidates) {
                // Hard-coded Catacombs handoff: once Efficient Walker has reached the exact
                // surface tile, click Investigate directly. Do not reject the statue
                // merely because its local tile is currently off-screen; the model click
                // may turn the camera as needed and completes the entrance transition.
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> candidate.object.click(candidate.action))) {
                    transitionPendingUntil = System.currentTimeMillis() + TRANSITION_PENDING_MS;
                    lastClickAt = System.currentTimeMillis();
                    lastClickTarget = candidate.object.getWorldLocation();
                    currentClickCooldownMs = DroSlayerRuntime.sampleLocalWalkCooldownMs();
                    return true;
                }
            }
            return false;
        }

        // Mount Karuulm: the surface handoff is the elevator. Use the exact
        // transport object and its Activate action instead of the generic cave allowlist.
        if (isKaruulmLocation(location)) {
            Rs2TileObjectModel elevator = Microbot.getRs2TileObjectCache().query()
                    .withId(34359)
                    .within(approach, 6)
                    .nearestOnClientThread();
            return clickVisibleTransition(elevator, "Activate");
        }

        // Custodian Stalkers: use the exact Shadows of Custodia cave entrance.
        if (location == DroSlayerLocation.STALKER_DEN_CUSTODIAN_STALKERS) {
            Rs2TileObjectModel entrance = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.SOC_CAVE_ENTRANCE)
                    .within(new WorldPoint(1295, 3373, 0), 4)
                    .nearestOnClientThread();
            return clickVisibleTransition(entrance, "Enter");
        }

        // Other curated caves use their hard-coded approach coordinate and an explicit
        // transition-action allowlist. We never click arbitrary objects outside that small area.
        List<Rs2TileObjectModel> nearby = Microbot.getRs2TileObjectCache().query()
                .within(approach, 6)
                .toListOnClientThread();
        if (nearby == null || nearby.isEmpty()) {
            return false;
        }

        String[] allowed = entryActionsFor(location);
        List<TransitionCandidate> candidates = new ArrayList<>();
        for (Rs2TileObjectModel object : nearby) {
            if (object == null || object.getWorldLocation() == null) continue;
            String action = firstMatchingAction(object, allowed);
            if (action == null) continue;
            candidates.add(new TransitionCandidate(object, action,
                    object.getWorldLocation().distanceTo2D(approach)));
        }

        candidates.sort(Comparator.comparingInt(c -> c.distance));
        for (TransitionCandidate candidate : candidates) {
            if (clickVisibleTransition(candidate.object, candidate.action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKaruulmLocation(DroSlayerLocation location) {
        if (location == null) return false;
        switch (location) {
            case KARUULM:
            case KARUULM_HYDRAS:
            case KARUULM_DRAKES:
            case KARUULM_WYRMS:
            case KARUULM_SULPHUR_LIZARDS:
            case ALCHEMICAL_HYDRA:
                return true;
            default:
                return false;
        }
    }

    private static boolean isCatacombsLocation(DroSlayerLocation location) {
        if (location == null) return false;
        switch (location) {
            case CATACOMBS:
            case CATACOMBS_DUST_DEVILS:
            case CATACOMBS_NECHRYAEL:
            case CATACOMBS_ABYSSAL_DEMONS:
            case CATACOMBS_HELLHOUNDS:
            case CATACOMBS_BLACK_DEMONS:
            case CATACOMBS_GREATER_DEMONS:
            case CATACOMBS_FIRE_GIANTS:
            case CATACOMBS_ANKOU:
            case CATACOMBS_DAGANNOTH:
            case CATACOMBS_MUTATED_BLOODVELDS:
            case CATACOMBS_BRUTAL_BLUE_DRAGONS:
                return true;
            default:
                return false;
        }
    }

    private static String[] entryActionsFor(DroSlayerLocation location) {
        switch (location) {
            case ASGARNIA_ICE_DUNGEON:
            case ASGARNIA_ICE_DUNGEON_WYVERNS:
            case TAVERLEY_DUNGEON:
            case TAVERLEY_BLACK_DRAGONS:
            case TAVERLEY_BABY_BLACK_DRAGONS:
            case TAVERLEY_BABY_BLUE_DRAGONS:
            case TAVERLEY_BLUE_DRAGONS:
            case TAVERLEY_HELLHOUNDS:
            case KALPHITE_LAIR:
            case KALPHITE_CAVE:
            case LIGHTHOUSE_DAGANNOTHS:
                return new String[]{"Climb-down", "Climb down", "Enter"};

            case ANCIENT_CAVERN:
            case ANCIENT_CAVERN_MITHRIL_DRAGONS:
                return new String[]{"Dive into", "Dive", "Enter"};

            case BRIMHAVEN_DUNGEON:
            case BRIMHAVEN_BLACK_DRAGONS:
            case BRIMHAVEN_RED_DRAGONS:
            case BRIMHAVEN_METAL_DRAGONS:
            case BRIMHAVEN_STEEL_DRAGONS:
                return new String[]{"Enter", "Pay", "Climb-down", "Climb down"};

            default:
                return new String[]{"Enter", "Climb-down", "Climb down", "Climb", "Descend", "Go-through", "Squeeze-through", "Pass", "Travel"};
        }
    }

    private static String firstMatchingAction(Rs2TileObjectModel object, String[] allowed) {
        try {
            ObjectComposition composition = object.getObjectComposition();
            if (composition == null) return null;
            String[] actions = composition.getActions();
            if (actions == null) return null;
            for (String wanted : allowed) {
                for (String actual : actions) {
                    if (actual != null && actual.equalsIgnoreCase(wanted)) {
                        return actual;
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Local transition action lookup failed for object {}: {}", object.getId(), ex.getMessage());
        }
        return null;
    }

    private static boolean clickVisibleTransition(Rs2TileObjectModel object, String action) {
        if (object == null || action == null || object.getLocalLocation() == null) return false;

        // Critical: Rs2TileObjectModel.click() turns the camera when an object is off-screen.
        // Refuse the click instead; local minimap movement will bring it into view naturally.
        if (!Rs2Camera.isTileOnScreen(object.getLocalLocation())) {
            return false;
        }

        boolean clicked = DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> object.click(action));
        if (clicked) {
            transitionPendingUntil = System.currentTimeMillis() + TRANSITION_PENDING_MS;
            lastClickAt = System.currentTimeMillis();
            lastClickTarget = object.getWorldLocation();
            currentClickCooldownMs = DroSlayerRuntime.sampleLocalWalkCooldownMs();
        }
        return clicked;
    }

    private static final class TransitionCandidate {
        private final Rs2TileObjectModel object;
        private final String action;
        private final int distance;

        private TransitionCandidate(Rs2TileObjectModel object, String action, int distance) {
            this.object = object;
            this.action = action;
            this.distance = distance;
        }
    }
}
