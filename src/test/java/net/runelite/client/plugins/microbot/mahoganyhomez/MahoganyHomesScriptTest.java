package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MahoganyHomesScriptTest {

    @Test
    void closestContractLocationIsNullWithoutPlayerLocation() {
        assertNull(MahoganyHomesScript.getClosestContractLocation(null));
    }

    @Test
    void closestContractLocationPicksNearestOffice() {
        assertEquals(ContractLocation.MAHOGANY_HOMES_VARROCK.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(3213, 3424, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_FALADOR.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(2965, 3380, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(2662, 3305, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_HOSIDIUS.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(1760, 3600, 0)));
    }
}
