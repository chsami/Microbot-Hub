package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SceneChart;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaGrid;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaReferenceMap;

/** Observed or advisory water selection; walking collisions do not identify an ocean. */
final class SailingMapDestinationPolicy {
    static boolean ocean(WorldPoint target, SeaGrid charted, SceneChart live) {
        return ocean(target,charted,live,null);
    }
    /** Candidate selection only. A reference-water click is NOT sailing permission. */
    static boolean ocean(WorldPoint target, SeaGrid charted, SceneChart live, SeaReferenceMap reference) {
        if (target == null || target.getPlane() != 0) return false;
        int x = target.getX(), y = target.getY();
        if (x < 1 || y < 1 || x > 16383 || y > 16383) return false;
        // Even UNKNOWN in the current scene supersedes stale atlas evidence.
        if (live != null && live.contains(x, y)) return live.cell(x, y) == SeaGrid.OPEN;
        byte observed=charted==null?SeaGrid.UNKNOWN:charted.cell(x,y);
        if(observed!=SeaGrid.UNKNOWN)return observed==SeaGrid.OPEN;
        int cell=reference==null?-1:reference.index(x,y);
        return cell>=0 && reference.cell(x,y)==SeaGrid.OPEN;
    }
}
