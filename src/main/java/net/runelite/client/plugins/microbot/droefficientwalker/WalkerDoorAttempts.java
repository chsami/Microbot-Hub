package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Bounded dispatches survive immediate route replans; movement away, crossing, staleness, or a world change releases them. */
final class WalkerDoorAttempts {
    static final long STALE_ATTEMPT_MS = 60_000L;

    enum Decision { ALLOW, WAIT, BLOCK }
    private final Map<WorldPoint, Attempt> attempts = new HashMap<>();
    private int world = -1;
    private volatile String failure;

    synchronized Decision request(int id, WorldPoint door, WorldPoint player, int currentWorld,
                                  boolean brassKey, long now) {
        return request(id, door, 0, player, currentWorld, brassKey, now);
    }

    synchronized Decision request(int id, WorldPoint door, int orientation, WorldPoint player, int currentWorld,
                                  boolean brassKey, long now) {
        observe(player, currentWorld, now);
        if (door == null || player == null) return Decision.WAIT;
        if (!EdgevilleDungeonAccess.allowed(id, door, player, brassKey)) {
            failure = "Locked cabin door requires a brass key";
            return Decision.BLOCK;
        }
        if (alKharidTollGate(door)
                && net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(net.runelite.api.Quest.PRINCE_ALI_RESCUE) != net.runelite.api.QuestState.FINISHED) {
            failure = "Al Kharid toll gate requires 10gp or Prince Ali Rescue";
            return Decision.BLOCK;
        }
        Attempt attempt = attempts.computeIfAbsent(door, ignored -> new Attempt());
        if (attempt.start != null && crossed(door, attempt.orientation != 0 ? attempt.orientation : orientation, attempt.start, player)) {
            attempt = new Attempt();
            attempts.put(door, attempt);
        }
        if (attempt.count > 0 && now - attempt.last < 2500L) return Decision.WAIT;
        if (attempt.count >= 3) {
            failure = "Door did not open after three spaced attempts at " + door;
            return Decision.BLOCK;
        }
        if (attempt.start == null) {
            attempt.start = player;
            attempt.orientation = orientation;
        }
        attempt.count++;
        attempt.last = now;
        return Decision.ALLOW;
    }

    /** Release door attempts once the player walks away, crosses the door, changes world, or the attempt goes stale. */
    synchronized void observe(WorldPoint player, int currentWorld, long now) {
        if (world != currentWorld) {
            attempts.clear();
            world = currentWorld;
        }
        attempts.entrySet().removeIf(e -> {
            WorldPoint door = e.getKey();
            Attempt attempt = e.getValue();
            if (player == null || player.distanceTo(door) > 5) return true;
            if (attempt.count > 0 && now - attempt.last >= STALE_ATTEMPT_MS) return true;
            return attempt.start != null && crossed(door, attempt.orientation, attempt.start, player);
        });
    }

    /** The toll gate is never an ordinary door: its Open option only starts the border guard's toll dialogue. */
    static boolean alKharidTollGate(WorldPoint door) {
        return door != null && door.getPlane() == 0 && door.getX() >= 3267 && door.getX() <= 3268
            && door.getY() >= 3227 && door.getY() <= 3228;
    }

    synchronized String takeFailure() { String result = failure; failure = null; return result; }
    synchronized void reject(String reason) { failure = reason; }
    synchronized void reset() { attempts.clear(); failure = null; world = -1; }

    private static boolean crossed(WorldPoint door, int orientation, WorldPoint from, WorldPoint to) {
        if (from.getPlane() != door.getPlane() || to.getPlane() != door.getPlane()) return true;
        switch (orientation) {
            case 1: // west wall of door tile: plane at 2*dx - 1
                return sign(2 * from.getX() - (2 * door.getX() - 1)) * sign(2 * to.getX() - (2 * door.getX() - 1)) < 0;
            case 2: // north wall of door tile: plane at 2*dy + 1
                return sign(2 * from.getY() - (2 * door.getY() + 1)) * sign(2 * to.getY() - (2 * door.getY() + 1)) < 0;
            case 4: // east wall of door tile: plane at 2*dx + 1
                return sign(2 * from.getX() - (2 * door.getX() + 1)) * sign(2 * to.getX() - (2 * door.getX() + 1)) < 0;
            case 8: // south wall of door tile: plane at 2*dy - 1
                return sign(2 * from.getY() - (2 * door.getY() - 1)) * sign(2 * to.getY() - (2 * door.getY() - 1)) < 0;
            default:
                return (from.getX() - door.getX()) * (to.getX() - door.getX())
                    + (from.getY() - door.getY()) * (to.getY() - door.getY()) < 0;
        }
    }

    private static int sign(int value) {
        return Integer.compare(value, 0);
    }

    private static final class Attempt { int count; long last; WorldPoint start; int orientation; }
}
