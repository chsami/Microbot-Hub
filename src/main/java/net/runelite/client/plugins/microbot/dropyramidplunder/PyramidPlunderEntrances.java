package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Which of the four anonymous doors to try. The game sends nothing that marks the door leading to
 * the Guardian mummy, and it moves every 25 minutes, so the choice is: the door that last worked,
 * then the door other players are gathered at, then the nearest untried door.
 */
final class PyramidPlunderEntrances {
    enum Door {
        NORTH(26622, new WorldPoint(3288, 2799, 0), new WorldPoint(3288, 2801, 0)),
        EAST(26623, new WorldPoint(3293, 2794, 0), new WorldPoint(3295, 2794, 0)),
        SOUTH(26624, new WorldPoint(3288, 2789, 0), new WorldPoint(3288, 2788, 0)),
        WEST(26625, new WorldPoint(3283, 2794, 0), new WorldPoint(3282, 2795, 0));

        final int objectId;
        /** South-west tile of the 2x2 door. */
        final WorldPoint object;
        /** Where a player stands to search it. */
        final WorldPoint approach;

        Door(int objectId, WorldPoint object, WorldPoint approach) {
            this.objectId = objectId;
            this.object = object;
            this.approach = approach;
        }
    }

    /** The mummy's door is the same for everyone and changes every 25 minutes. */
    static final long ROTATION_MS = 25 * 60_000L;
    /** Fewer players than this at a door is not a crowd. */
    static final int CROWD_MIN = 2;

    private final Set<Door> wrong = EnumSet.noneOf(Door.class);
    private Door lastGood;
    private long lastGoodAt;
    private Door attempted;

    synchronized Door choose(WorldPoint player, int[] playersAtDoor, long now) {
        if (wrong.size() >= Door.values().length) wrong.clear();
        Door choice = null;
        if (lastGood != null && !wrong.contains(lastGood) && now - lastGoodAt < ROTATION_MS) choice = lastGood;
        if (choice == null) choice = crowded(playersAtDoor);
        if (choice == null && lastGood != null && !wrong.contains(lastGood)) choice = lastGood;
        if (choice == null) choice = nearest(player);
        attempted = choice;
        return choice;
    }

    synchronized Door attempted() { return attempted; }

    /** The Guardian mummy was behind the attempted door. */
    synchronized void confirm(long now) {
        if (attempted == null) return;
        lastGood = attempted;
        lastGoodAt = now;
        wrong.clear();
    }

    /** The attempted door led to an empty room. */
    synchronized void reject() {
        if (attempted == null) return;
        wrong.add(attempted);
        if (attempted == lastGood) lastGood = null;
        attempted = null;
    }

    synchronized Door lastGood() { return lastGood; }

    private Door crowded(int[] playersAtDoor) {
        if (playersAtDoor == null) return null;
        Door best = null;
        int bestCount = 0;
        boolean tie = false;
        for (Door door : Door.values()) {
            int count = door.ordinal() < playersAtDoor.length ? playersAtDoor[door.ordinal()] : 0;
            if (wrong.contains(door)) continue;
            if (count > bestCount) { best = door; bestCount = count; tie = false; }
            else if (count == bestCount && count > 0) tie = true;
        }
        return bestCount >= CROWD_MIN && !tie ? best : null;
    }

    private Door nearest(WorldPoint player) {
        Door best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Door door : Door.values()) {
            if (wrong.contains(door)) continue;
            int distance = player == null ? door.ordinal() : player.distanceTo2D(door.approach);
            if (distance < bestDistance) { best = door; bestDistance = distance; }
        }
        return best == null ? Door.NORTH : best;
    }
}
