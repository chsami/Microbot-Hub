package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/** Chooses the tile to step to when the player stands on a fire bomb tile. */
public final class BombStep
{
    private BombStep()
    {
    }

    /**
     * Picks a reachable tile that is not bombed and not under a demon. One step is preferred, because run
     * is off while bound; a two-step tile is the fallback. Among equal steps, a tile from which the player
     * can still reach the demon with {@code reach} tiles comes first, then the tile nearest {@code toward},
     * so repeated steps do not drift the player out of the room.
     *
     * @param reachable tiles mapped to their walking distance in steps from the player
     * @param toward the room centre, or null for no preference
     * @return the chosen tile, or null when no safe tile is reachable
     */
    public static WorldPoint choose(WorldPoint player, Set<WorldPoint> bombs, WorldArea demon,
                                    Map<WorldPoint, Integer> reachable, int reach, WorldPoint toward)
    {
        if (player == null || reachable == null)
        {
            return null;
        }
        return reachable.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() >= 1 && entry.getValue() <= 2)
            .filter(entry -> entry.getKey().getPlane() == player.getPlane())
            .filter(entry -> !bombs.contains(entry.getKey()))
            .filter(entry -> demon == null || !demon.contains(entry.getKey()))
            .min(Comparator.<Map.Entry<WorldPoint, Integer>>comparingInt(Map.Entry::getValue)
                .thenComparingInt(entry -> demon == null || demon.distanceTo(entry.getKey()) <= reach ? 0 : 1)
                .thenComparingInt(entry -> toward == null ? 0 : toward.distanceTo2D(entry.getKey()))
                .thenComparingInt(entry -> entry.getKey().getX())
                .thenComparingInt(entry -> entry.getKey().getY()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
