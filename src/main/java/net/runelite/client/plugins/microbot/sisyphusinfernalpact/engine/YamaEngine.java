package net.runelite.client.plugins.microbot.sisyphusinfernalpact.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.sisyphusinfernalpact.SisyphusInfernalPactConfig;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Yama's Lair stepping-stone engine — spam-click edition.
 *
 * <ul>
 *   <li>Only targets the 14 whitelisted stone waypoints.</li>
 *   <li>Skips any stone within {@value #FIRE_EXCLUSION_RADIUS} tiles of NPC
 *       ID {@value #FIRE_NPC_ID}.</li>
 *   <li>Never re-clicks the last {@value #RECENT_HISTORY} stones jumped to
 *       (prevents backtracking).</li>
 *   <li>No internal tick delay — fires as fast as the outer scheduler allows.</li>
 * </ul>
 */
@Slf4j
public class YamaEngine {

    private static final int STONE_GROUND_ID      = 13621;
    private static final int FIRE_NPC_ID          = 15608;
    private static final int FIRE_EXCLUSION_RADIUS = 2;
    private static final int STONE_SNAP_RADIUS    = 2;

    /** How many recently-visited stones to remember (prevents backtracking). */
    private static final int RECENT_HISTORY = 3;

    private static final long JUMP_TIMEOUT_MS = 5_000;

    /** Fixed-coordinate loop — entry path followed by the circular loop. */
    private static final WorldPoint[] STONE_ROUTE = {
        // ── Entry path ──
        new WorldPoint(1486, 5598, 0),
        new WorldPoint(1484, 5596, 0),
        new WorldPoint(1481, 5597, 0),
        new WorldPoint(1479, 5599, 0),
        // ── Loop ──
        new WorldPoint(1480, 5602, 0),
        new WorldPoint(1478, 5602, 0),
        new WorldPoint(1477, 5605, 0),
        new WorldPoint(1479, 5605, 0),
        new WorldPoint(1481, 5605, 0),
        new WorldPoint(1481, 5608, 0),
        new WorldPoint(1479, 5608, 0),
        new WorldPoint(1477, 5608, 0),
        new WorldPoint(1478, 5611, 0),
        new WorldPoint(1480, 5611, 0),
    };

    public enum State { IDLE, SCANNING, JUMPING, COMPLETE, STUCK }

    @Getter private String  status  = "Idle";
    @Getter private boolean running = false;
    @Getter private State   state   = State.IDLE;

    private int        stonesJumped       = 0;
    private int        targetStones       = 666;
    private long       jumpStartTime      = 0;
    private WorldPoint lastPlayerLocation = null;
    private int        stuckCounter       = 0;

    /** Rolling window of the last {@value #RECENT_HISTORY} stones jumped to. */
    private final Deque<WorldPoint> recentlyJumped = new ArrayDeque<>();

    public void reset() {
        status             = "Idle";
        running            = false;
        state              = State.IDLE;
        stonesJumped       = 0;
        jumpStartTime      = 0;
        lastPlayerLocation = null;
        stuckCounter       = 0;
        recentlyJumped.clear();
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public boolean tick(SisyphusInfernalPactConfig config) {
        running      = true;
        targetStones = config.yamaStonesToStep();

        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return true;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            status = "Locating player";
            return true;
        }

        if (stonesJumped >= targetStones) {
            state  = State.COMPLETE;
            status = "Complete — " + stonesJumped + "/" + targetStones + " stones";
            return true;
        }

        // ── Detect landing from a jump ──
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            if (state == State.JUMPING) {
                if (lastPlayerLocation != null && !lastPlayerLocation.equals(playerLocation)) {
                    // We moved — record this tile in the history and increment counter
                    recordJump(playerLocation);
                    stonesJumped++;
                    status = "Jumped — " + stonesJumped + "/" + targetStones;
                    // No artificial delay — let the outer scheduler call us back
                    state = State.IDLE;
                } else if (System.currentTimeMillis() - jumpStartTime > JUMP_TIMEOUT_MS) {
                    status = "Jump timed out — retrying";
                    state  = State.IDLE;
                } else {
                    status = "Jumping... " + stonesJumped + "/" + targetStones;
                }
            } else {
                status = "Moving — " + stonesJumped + "/" + targetStones;
            }
            lastPlayerLocation = playerLocation;
            return true;
        }

        // ── Player is still — find and click next safe stone immediately ──
        state = State.SCANNING;

        List<WorldPoint> fireLocs = getAllFireLocations();
        Microbot.log("[Yama] fire=" + fireLocs.size()
                + " recent=" + recentlyJumped.size()
                + " jumped=" + stonesJumped + "/" + targetStones);

        GroundObject target = findBestSafeStone(playerLocation, fireLocs);

        if (target == null) {
            stuckCounter++;
            if (stuckCounter > 8) {
                state  = State.STUCK;
                status = "Stuck — no safe route stone reachable";
            } else {
                status = "Waiting for safe stone (fire=" + fireLocs.size() + ")";
            }
            return true;
        }

        stuckCounter = 0;
        if (Rs2GameObject.interact(target)) {
            state              = State.JUMPING;
            jumpStartTime      = System.currentTimeMillis();
            lastPlayerLocation = playerLocation;
            status = "→ " + target.getWorldLocation()
                    + "  [" + stonesJumped + "/" + targetStones + "]";
        } else {
            status = "Click failed — " + target.getWorldLocation();
        }

        return true;
    }

    // ── Stone selection ───────────────────────────────────────────────────────

    private GroundObject findBestSafeStone(WorldPoint playerLocation, List<WorldPoint> fireLocs) {
        List<GroundObject> allGround = getGroundObjects();

        GroundObject best     = null;
        int          bestDist = Integer.MAX_VALUE;

        for (WorldPoint waypoint : STONE_ROUTE) {
            // Skip the tile the player is currently on
            if (sameXY(waypoint, playerLocation)) continue;

            // Skip stones we jumped to recently (prevents backtracking)
            if (isRecentlyJumped(waypoint)) continue;

            // Skip if within 2 tiles of any fire NPC
            if (isNearFire(waypoint, fireLocs)) continue;

            // Require an actual GroundObject at this waypoint
            GroundObject obj = findStoneNear(waypoint, allGround);
            if (obj == null) continue;

            int dist = distance2D(waypoint, playerLocation);
            if (dist < bestDist) {
                bestDist = dist;
                best     = obj;
            }
        }
        return best;
    }

    private GroundObject findStoneNear(WorldPoint waypoint, List<GroundObject> groundObjects) {
        GroundObject closest = null;
        int          minDist = Integer.MAX_VALUE;
        for (GroundObject obj : groundObjects) {
            if (obj.getId() != STONE_GROUND_ID) continue;
            int d = distance2D(obj.getWorldLocation(), waypoint);
            if (d <= STONE_SNAP_RADIUS && d < minDist) {
                minDist = d;
                closest = obj;
            }
        }
        return closest;
    }

    private List<GroundObject> getGroundObjects() {
        try {
            List<GroundObject> list = Rs2GameObject.getGroundObjects();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.debug("[Yama] Error getting ground objects", e);
            return Collections.emptyList();
        }
    }

    // ── Recent-stone tracking ─────────────────────────────────────────────────

    private void recordJump(WorldPoint location) {
        // Keep only the most recent RECENT_HISTORY positions
        recentlyJumped.addFirst(location);
        while (recentlyJumped.size() > RECENT_HISTORY) {
            recentlyJumped.removeLast();
        }
    }

    private boolean isRecentlyJumped(WorldPoint waypoint) {
        for (WorldPoint p : recentlyJumped) {
            if (sameXY(p, waypoint)) return true;
        }
        return false;
    }

    // ── Fire NPC detection ────────────────────────────────────────────────────

    /**
     * Dual-source fire NPC location collection.
     * Queries both the Microbot NPC cache (manual ID filter) and the raw
     * {@code Client.getNpcs()} list so fast-spawning fire effects are never missed.
     */
    private List<WorldPoint> getAllFireLocations() {
        List<WorldPoint> locations = new ArrayList<>();

        // Source 1: Microbot NPC cache
        try {
            List<Rs2NpcModel> cached = Microbot.getRs2NpcCache().query().toList();
            if (cached != null) {
                for (Rs2NpcModel npc : cached) {
                    if (npc != null && npc.getId() == FIRE_NPC_ID) {
                        WorldPoint loc = npc.getWorldLocation();
                        if (loc != null) locations.add(loc);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Yama] NPC cache error", e);
        }

        // Source 2: Raw client NPC list
        try {
            List<NPC> clientNpcs = Microbot.getClient().getNpcs();
            if (clientNpcs != null) {
                for (NPC npc : clientNpcs) {
                    if (npc != null && npc.getId() == FIRE_NPC_ID) {
                        WorldPoint loc = npc.getWorldLocation();
                        if (loc != null) locations.add(loc);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Yama] Client NPC list error", e);
        }

        return locations;
    }

    private boolean isNearFire(WorldPoint target, List<WorldPoint> fireLocs) {
        for (WorldPoint fire : fireLocs) {
            if (distance2D(fire, target) <= FIRE_EXCLUSION_RADIUS) return true;
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean sameXY(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) return false;
        return a.getX() == b.getX() && a.getY() == b.getY();
    }

    private static int distance2D(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
