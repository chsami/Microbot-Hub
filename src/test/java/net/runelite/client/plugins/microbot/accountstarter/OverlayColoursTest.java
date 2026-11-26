package net.runelite.client.plugins.microbot.accountstarter;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlayColoursTest {
    @Test
    void thePanelKeepsItsHouseColours() {
        assertEquals(new Color(105, 114, 107), AccountStarterOverlay.FRAME_INNER);
        assertEquals(new Color(101, 100, 77), AccountStarterOverlay.SPARK);
        assertEquals(new Color(65, 84, 84), AccountStarterOverlay.TRACK);
    }
}
