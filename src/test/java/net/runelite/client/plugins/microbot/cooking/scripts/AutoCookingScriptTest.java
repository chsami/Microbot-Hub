package net.runelite.client.plugins.microbot.cooking.scripts;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.cooking.enums.CookingLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCookingScriptTest {
    private static final WorldPoint AL_KHARID_BANK = new WorldPoint(3269, 3166, 0);

    @Test
    void alKharidBankIsNotWithinReachOfTheRange() {
        WorldPoint range = CookingLocation.AL_KHARID.getCookingObjectWorldPoint();

        assertFalse(AutoCookingScript.isWithinCookingReach(AL_KHARID_BANK, range));
    }

    @Test
    void tileNextToTheRangeIsWithinReach() {
        WorldPoint range = CookingLocation.AL_KHARID.getCookingObjectWorldPoint();

        assertTrue(AutoCookingScript.isWithinCookingReach(range.dx(1), range));
        assertTrue(AutoCookingScript.isWithinCookingReach(range.dy(-AutoCookingScript.COOKING_OBJECT_REACH), range));
        assertFalse(AutoCookingScript.isWithinCookingReach(range.dy(-AutoCookingScript.COOKING_OBJECT_REACH - 1), range));
    }

    @Test
    void otherPlaneOrUnknownLocationIsNotWithinReach() {
        WorldPoint range = CookingLocation.MYTHS_GUILD.getCookingObjectWorldPoint();

        assertFalse(AutoCookingScript.isWithinCookingReach(new WorldPoint(range.getX(), range.getY(), 0), range));
        assertFalse(AutoCookingScript.isWithinCookingReach(null, range));
        assertFalse(AutoCookingScript.isWithinCookingReach(range, null));
    }

    @Test
    void offScreenRangeFromAlKharidBankIsWalkedTo() {
        WorldPoint range = CookingLocation.AL_KHARID.getCookingObjectWorldPoint();

        assertEquals(CookingObjectStep.WALK, AutoCookingScript.nextCookingObjectStep(false, AL_KHARID_BANK, range));
    }

    @Test
    void onScreenRangeFromAlKharidBankIsUsedWithoutWalking() {
        WorldPoint range = CookingLocation.AL_KHARID.getCookingObjectWorldPoint();

        assertEquals(CookingObjectStep.USE, AutoCookingScript.nextCookingObjectStep(true, AL_KHARID_BANK, range));
    }

    @Test
    void offScreenRangeWithinReachTurnsTheCamera() {
        WorldPoint range = CookingLocation.AL_KHARID.getCookingObjectWorldPoint();

        assertEquals(CookingObjectStep.TURN_CAMERA, AutoCookingScript.nextCookingObjectStep(false, range.dx(1), range));
    }

    @Test
    void statusUsesReadableLocationAndCookingObject() {
        assertEquals("Al Kharid", AutoCookingScript.readableLocationName(CookingLocation.AL_KHARID));
        assertEquals("Walking to the range at Al Kharid", AutoCookingScript.walkingStatus(CookingLocation.AL_KHARID));
        assertEquals("Walking to the fire at Draynor Village", AutoCookingScript.walkingStatus(CookingLocation.DRAYNOR_VILLAGE));
        assertEquals("Can't find a range at Al Kharid. Waiting. If this does not change, pick another Location in the settings.",
                AutoCookingScript.missingCookingObjectStatus(CookingLocation.AL_KHARID));
        assertEquals("Can't find a fire at Draynor Village. Waiting for a fire, or pick a location with a range.",
                AutoCookingScript.missingCookingObjectStatus(CookingLocation.DRAYNOR_VILLAGE));
    }
}
