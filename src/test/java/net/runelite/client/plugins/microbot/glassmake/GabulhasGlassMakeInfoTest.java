package net.runelite.client.plugins.microbot.glassmake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GabulhasGlassMakeInfoTest {

    @Test
    void giantSeaweedNeedsEighteenSand() {
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.GiantSeaweed, 100, 10));
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.GiantSeaweed, 100, 17));
        assertTrue(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.GiantSeaweed, 100, 18));
    }

    @Test
    void giantSeaweedNeedsThreeSeaweed() {
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.GiantSeaweed, 2, 100));
        assertTrue(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.GiantSeaweed, 3, 100));
    }

    @Test
    void seaweedNeedsThirteenSand() {
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 100, 3));
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 100, 12));
        assertTrue(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 100, 13));
    }

    @Test
    void statusTextFallsBackToStartingWhenStatusIsNull() {
        GabulhasGlassMakeInfo.states previous = GabulhasGlassMakeInfo.botStatus;
        try {
            GabulhasGlassMakeInfo.botStatus = null;
            assertEquals("Starting", GabulhasGlassMakeInfo.statusText());
            GabulhasGlassMakeInfo.botStatus = GabulhasGlassMakeInfo.states.Glassblowing;
            assertEquals("Glassblowing", GabulhasGlassMakeInfo.statusText());
        } finally {
            GabulhasGlassMakeInfo.botStatus = previous;
        }
    }

    @Test
    void seaweedNeedsThreeSeaweed() {
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 0, 100));
        assertFalse(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 2, 100));
        assertTrue(GabulhasGlassMakeInfo.hasMaterials(GabulhasGlassMakeInfo.items.Seaweed, 3, 100));
    }
}
