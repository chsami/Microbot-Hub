package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TeleportLandingReplanTest {
    @Test public void monasteryLandingMustRecompareTheFeroxLeg() {
        WorldPoint cave = new WorldPoint(3426,10148,0), landing = new WorldPoint(3052,3490,0);
        WorldPoint actual = new WorldPoint(3053,3487,0), ferox = new WorldPoint(3128,3637,0);
        assertTrue(TeleportLandingReplan.shouldRecompare(cave,landing,actual,ferox));
        assertFalse(TeleportLandingReplan.shouldRecompare(cave,landing,cave,ferox));
        assertFalse(TeleportLandingReplan.shouldRecompare(actual,landing,actual,ferox));
        assertFalse(TeleportLandingReplan.shouldRecompare(cave,landing,actual,actual));
        assertFalse(TeleportLandingReplan.shouldRecompare(null,landing,actual,ferox));
    }
}
