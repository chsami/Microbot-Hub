package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinimapOrbsTest
{
    @Test
    void pointOnTheWorldMapOrbIsCovered()
    {
        Client client = mock(Client.class);
        Widget worldMap = mock(Widget.class);
        when(worldMap.isHidden()).thenReturn(false);
        when(worldMap.getBounds()).thenReturn(new Rectangle(1830, 150, 30, 30));
        when(client.getWidget(anyInt())).thenReturn(null);
        when(client.getWidget(InterfaceID.Orbs.WORLDMAP)).thenReturn(worldMap);

        assertTrue(MinimapOrbs.covers(client, new Point(1845, 165)));
        assertFalse(MinimapOrbs.covers(client, new Point(1790, 90)));
    }

    @Test
    void hiddenOrMissingOrbsDoNotCoverTheMinimap()
    {
        Client client = mock(Client.class);
        Widget worldMap = mock(Widget.class);
        when(worldMap.isHidden()).thenReturn(true);
        when(worldMap.getBounds()).thenReturn(new Rectangle(1830, 150, 30, 30));
        when(client.getWidget(InterfaceID.Orbs.WORLDMAP)).thenReturn(worldMap);

        assertFalse(MinimapOrbs.covers(client, new Point(1845, 165)));
        assertFalse(MinimapOrbs.covers(null, new Point(1845, 165)));
        assertFalse(MinimapOrbs.covers(client, null));
    }

    @Test
    void pointInsideAnyBoundsIgnoresNulls()
    {
        Rectangle worldMapOrb = new Rectangle(1830, 150, 30, 30);
        Rectangle compass = new Rectangle(1720, 5, 35, 35);

        assertTrue(MinimapOrbs.isPointInsideAny(new Point(1845, 165), Arrays.asList(compass, worldMapOrb)));
        assertFalse(MinimapOrbs.isPointInsideAny(new Point(1790, 90), Arrays.asList(compass, worldMapOrb)));
        assertFalse(MinimapOrbs.isPointInsideAny(new Point(1845, 165), Collections.singletonList(null)));
        assertFalse(MinimapOrbs.isPointInsideAny(null, Collections.singletonList(worldMapOrb)));
    }
}
