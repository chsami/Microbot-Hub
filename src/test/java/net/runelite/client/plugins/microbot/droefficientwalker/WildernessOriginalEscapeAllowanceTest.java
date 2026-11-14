package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A safe-destination journey must retain the original escape budget across route trimming. */
class WildernessOriginalEscapeAllowanceTest {
    private static final WorldPoint DEEP = new WorldPoint(3200, 3830, 0);
    private static final WorldPoint SHALLOW = new WorldPoint(3200, 3700, 0);
    private static final WorldPoint DETOUR = new WorldPoint(3200, 3770, 0);
    private static final WorldPoint SAFE = new WorldPoint(3200, 3500, 0);

    @Test void suffixOfAnExistingEscapeKeepsItsInitialDangerAllowance() {
        List<WorldPoint> remaining = List.of(SHALLOW, DETOUR, SAFE);
        assertFalse(WildernessTravelPolicy.routeAllowed(remaining, SAFE, false));
        assertTrue(WildernessTravelPolicy.routeAllowed(remaining, SAFE, false, DEEP, false));
        assertFalse(WildernessTravelPolicy.routeAllowed(remaining, SAFE, false, DEEP, true));
    }

    @Test void anEscapeMayNotReenterDangerAfterReachingSafety() {
        List<WorldPoint> reentry = List.of(SHALLOW, SAFE, SHALLOW);
        assertFalse(WildernessTravelPolicy.routeAllowed(reentry, SAFE, false, DEEP, false));
        assertFalse(WildernessTravelPolicy.routeAllowed(reentry, SAFE, false, DEEP, true));
        assertFalse(WildernessTravelPolicy.routeAllowed(List.of(SAFE, SHALLOW), SAFE, false, SAFE, false));
    }

    @Test void planningRetainsOriginalEscapeCapWithoutPermittingNewWildernessEntry() {
        Object previous = WildernessTravelPolicy.beginPlanning(List.of(SHALLOW), List.of(SAFE), false, DEEP, SAFE);
        try {
            assertFalse(WildernessTravelPolicy.plannerBlocked(DETOUR, false));
            assertTrue(WildernessTravelPolicy.plannedPathAllowed(List.of(SHALLOW, DETOUR, SAFE)));
            assertFalse(WildernessTravelPolicy.plannedPathAllowed(List.of(SHALLOW, SAFE, DETOUR)));
        } finally {
            WildernessTravelPolicy.endPlanning(previous);
        }
        previous = WildernessTravelPolicy.beginPlanning(List.of(SAFE), List.of(SAFE), false, DEEP, SAFE);
        try {
            assertTrue(WildernessTravelPolicy.plannerBlocked(DETOUR, false));
        } finally {
            WildernessTravelPolicy.endPlanning(previous);
        }
    }
}
