package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporossFireScopeTest
{
    private static final WorldPoint EXIT = new WorldPoint(10556, 12804, 0);
    private static final WorldPoint TOTEM_EXIT = new WorldPoint(10566, 12820, 0);
    private static final int TILE = Perspective.LOCAL_TILE_SIZE;
    private static final LocalPoint PLAYER = new LocalPoint(64 * TILE, 64 * TILE, -1);

    private static TemporossWorkArea workArea()
    {
        return new TemporossWorkArea(EXIT, true, TOTEM_EXIT, TemporossSide.A);
    }

    private static TemporossNpcSnapshot fire(int index, WorldPoint world, int localDx, int localDy)
    {
        LocalPoint local = new LocalPoint(PLAYER.getX() + localDx * TILE, PLAYER.getY() + localDy * TILE, -1);
        return new TemporossNpcSnapshot(8643, index, local, world, "Fire");
    }

    @Test
    void otherShipFirePassesSideTestButIsNotAtOurStations()
    {
        TemporossWorkArea area = workArea();
        WorldPoint otherShip = EXIT.dx(16).dy(-4);

        assertTrue(area.isOnOurSide(otherShip));
        assertFalse(area.isNearOurStations(otherShip));
    }

    @Test
    void shipDeckAndTotemCountAsOurStations()
    {
        TemporossWorkArea area = workArea();

        assertTrue(area.isNearOurStations(area.mastPoint));
        assertTrue(area.isNearOurStations(area.harpoonPoint));
        assertTrue(area.isNearOurStations(TOTEM_EXIT.dx(3).dy(2)));
        assertFalse(area.isNearOurStations(null));
    }

    @Test
    void totemAnchorIsIgnoredUntilSighted()
    {
        TemporossWorkArea area = new TemporossWorkArea(EXIT, true, null, TemporossSide.A);

        assertFalse(area.isNearOurStations(TOTEM_EXIT));
        assertTrue(area.isNearOurStations(EXIT.dx(2).dy(-5)));
    }

    @Test
    void continuousDouseSkipsFiresAwayFromUsAndOurStations()
    {
        TemporossWorkArea area = workArea();
        TemporossNpcSnapshot besidePlayer = fire(1, EXIT.dx(12), 3, 2);
        TemporossNpcSnapshot onOurDeck = fire(2, area.pumpPoint, 20, 20);
        TemporossNpcSnapshot onOtherShip = fire(3, EXIT.dx(16).dy(-4), 15, -10);

        List<TemporossNpcSnapshot> picked = TemporossScript.firesToDouse(
                Arrays.asList(besidePlayer, onOurDeck, onOtherShip), area, PLAYER);

        assertEquals(Arrays.asList(besidePlayer, onOurDeck), picked);
    }

    @Test
    void continuousDouseWithoutPlayerLocationKeepsOnlyStationFires()
    {
        TemporossWorkArea area = workArea();
        TemporossNpcSnapshot besidePlayer = fire(1, EXIT.dx(12), 1, 1);
        TemporossNpcSnapshot onOurDeck = fire(2, area.mastPoint, 20, 20);

        List<TemporossNpcSnapshot> picked = TemporossScript.firesToDouse(
                Arrays.asList(besidePlayer, onOurDeck), area, null);

        assertEquals(Arrays.asList(onOurDeck), picked);
    }
}
