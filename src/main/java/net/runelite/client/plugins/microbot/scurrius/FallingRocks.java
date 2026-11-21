package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Tiles under Scurrius's falling rocks (graphics object 2644). A tile is dangerous from the tick its graphics object is
 * created until that object is gone from the scene, with a hard expiry as a fallback. Pure; the plugin feeds it.
 */
public final class FallingRocks {
    /** Fallback life of a rock tile if the graphics object never reports as gone (the old plugin used 8 ticks too). */
    public static final int MAX_LIFE_TICKS = 8;

    private final Map<WorldPoint, Integer> createdAt = new HashMap<>();

    public void add(WorldPoint tile, int tick) {
        if (tile != null) {
            createdAt.merge(tile, tick, Math::max);
        }
    }

    /**
     * Drops tiles whose graphics object is gone or that outlived {@link #MAX_LIFE_TICKS}.
     *
     * @param live tiles that still have a falling-rock graphics object in the scene
     */
    public void refresh(int tick, Set<WorldPoint> live) {
        for (Iterator<Map.Entry<WorldPoint, Integer>> it = createdAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<WorldPoint, Integer> entry = it.next();
            int age = tick - entry.getValue();
            boolean gone = age > 0 && !live.contains(entry.getKey()); // never drop on the creation tick itself
            if (gone || age >= MAX_LIFE_TICKS) {
                it.remove();
            }
        }
    }

    public boolean contains(WorldPoint tile) {
        return createdAt.containsKey(tile);
    }

    public Set<WorldPoint> tiles() {
        return new HashSet<>(createdAt.keySet());
    }

    public boolean isEmpty() {
        return createdAt.isEmpty();
    }

    public void clear() {
        createdAt.clear();
    }
}
