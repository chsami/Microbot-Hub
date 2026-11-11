package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Bounded dispatches survive route replans; movement away or a world change releases them. */
final class WalkerDoorAttempts {
    enum Decision { ALLOW, WAIT, BLOCK }
    private final Map<WorldPoint, Attempt> attempts = new HashMap<>();
    private int world = -1;
    private volatile String failure;

    synchronized Decision request(int id, WorldPoint door, WorldPoint player, int currentWorld,
                                  boolean brassKey, long now) {
        if (world != currentWorld) { attempts.clear(); world = currentWorld; }
        attempts.entrySet().removeIf(e -> player == null || player.distanceTo(e.getKey()) > 5);
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
        if (attempt.start != null && crossed(door, attempt.start, player)) {
            attempt = new Attempt();
            attempts.put(door, attempt);
        }
        if (attempt.count > 0 && now - attempt.last < 2500L) return Decision.WAIT;
        if (attempt.count >= 3) {
            failure = "Door did not open after three spaced attempts at " + door;
            return Decision.BLOCK;
        }
        if (attempt.start == null) attempt.start = player;
        attempt.count++;
        attempt.last = now;
        return Decision.ALLOW;
    }

    /** The toll gate is never an ordinary door: its Open option only starts the border guard's toll dialogue. */
    static boolean alKharidTollGate(WorldPoint door) {
        return door != null && door.getPlane() == 0 && door.getX() >= 3267 && door.getX() <= 3268
            && door.getY() >= 3227 && door.getY() <= 3228;
    }

    synchronized String takeFailure() { String result = failure; failure = null; return result; }
    synchronized void reject(String reason) { failure = reason; }
    synchronized void reset() { attempts.clear(); failure = null; world = -1; }
    private static boolean crossed(WorldPoint door, WorldPoint from, WorldPoint to) {
        return (from.getX() - door.getX()) * (to.getX() - door.getX())
            + (from.getY() - door.getY()) * (to.getY() - door.getY()) < 0;
    }
    private static final class Attempt { int count; long last; WorldPoint start; }
}
