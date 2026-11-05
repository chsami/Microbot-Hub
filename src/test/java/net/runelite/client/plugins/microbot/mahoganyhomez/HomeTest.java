package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTest {

    @Test
    void isInsideIsFalseWithoutPlayerLocation() {
        assertFalse(Home.JESS.isInside(null));
    }

    @Test
    void isInsideMatchesAnyHomeArea() {
        assertTrue(Home.JESS.isInside(new WorldPoint(2615, 3293, 0)));
        assertTrue(Home.JESS.isInside(new WorldPoint(3238, 3486, 1)));
        assertFalse(Home.JESS.isInside(new WorldPoint(3213, 3424, 0)));
    }
}
