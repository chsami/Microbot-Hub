package net.runelite.client.plugins.microbot.mahoganyhomez;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MahoganyHomesPluginTest {

    @Test
    void plankSackCountReadsSinglePlankTypeCheck() {
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount("Mahogany planks: 28"));
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount("Mahogany planks: 28"));
        assertEquals(0, MahoganyHomesPlugin.parsePlankSackCount("Teak planks: 0"));
    }

    @Test
    void plankSackCountSumsEveryPlankTypeInCheck() {
        assertEquals(28, MahoganyHomesPlugin.parsePlankSackCount(
                "Basic planks: 3, Oak planks: 5, Teak planks: 8, Mahogany planks: 12"));
        assertEquals(17, MahoganyHomesPlugin.parsePlankSackCount("Oak planks: 5, Mahogany planks: 12"));
    }

    @Test
    void plankSackCountIgnoresColourTags() {
        assertEquals(14, MahoganyHomesPlugin.parsePlankSackCount(
                "<col=ef1020>Basic planks: 0</col>, <col=ef1020>Mahogany planks: 14</col>"));
    }

    @Test
    void plankSackCountIsNegativeForOtherMessages() {
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount(null));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount(""));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("Your sack is full."));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("You haven't got any planks that can go in the sack."));
        assertEquals(-1, MahoganyHomesPlugin.parsePlankSackCount("Expert Contract: Go see Noella in Ardougne."));
    }
}
