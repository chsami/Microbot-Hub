package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalvagingScriptTest {

    @Test
    void isWithinSalvageAreaReturnsFalseWhenPlayerLocationIsNull() throws Exception {
        var wreck = wreckAt(new WorldPoint(3000, 3000, 0));

        assertFalse(isWithinSalvageArea(null, wreck));
    }

    @Test
    void isWithinSalvageAreaReturnsFalseWhenWreckLocationIsNull() throws Exception {
        var wreck = wreckAt(null);

        assertFalse(isWithinSalvageArea(new WorldPoint(3000, 3000, 0), wreck));
    }

    @Test
    void isWithinSalvageAreaUsesDistanceToWreck() throws Exception {
        var player = new WorldPoint(3000, 3000, 0);

        assertTrue(isWithinSalvageArea(player, wreckAt(new WorldPoint(3010, 3000, 0))));
        assertFalse(isWithinSalvageArea(player, wreckAt(new WorldPoint(3100, 3000, 0))));
    }

    private static boolean isWithinSalvageArea(WorldPoint playerLocation, Rs2TileObjectModel wreck) throws Exception {
        var script = new SalvagingScript(null, null, null);
        Method method = SalvagingScript.class.getDeclaredMethod("isWithinSalvageArea", WorldPoint.class, Rs2TileObjectModel.class);
        method.setAccessible(true);
        return (boolean) method.invoke(script, playerLocation, wreck);
    }

    private static Rs2TileObjectModel wreckAt(WorldPoint location) {
        var wreck = mock(Rs2TileObjectModel.class);
        when(wreck.getWorldLocation()).thenReturn(location);
        return wreck;
    }
}
