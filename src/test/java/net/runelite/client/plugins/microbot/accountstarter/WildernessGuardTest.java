package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.WildernessGuard;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildernessGuardTest {
    static final Predicate<WorldPoint> WILD = p ->
        (p.getX() >= 2944 && p.getX() < 3392 && p.getY() >= 3525 && p.getY() < 3973)
            || (p.getX() >= 2944 && p.getX() < 3462 && p.getY() >= 9918 && p.getY() < 10376);

    @Test
    void routeThroughDitchIsUnsafe() {
        assertFalse(WildernessGuard.routeSafe(Arrays.asList(
            new WorldPoint(3100, 3515, 0), new WorldPoint(3100, 3526, 0)), WILD));
        assertTrue(WildernessGuard.routeSafe(Arrays.asList(
            new WorldPoint(3100, 3500, 0), new WorldPoint(3100, 3515, 0)), WILD));
        assertFalse(WildernessGuard.destinationSafe(new WorldPoint(3100, 3600, 0), WILD));
    }
}
