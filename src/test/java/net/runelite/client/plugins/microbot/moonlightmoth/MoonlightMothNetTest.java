package net.runelite.client.plugins.microbot.moonlightmoth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoonlightMothNetTest {

    @Test
    void netIsMatchedByItemIdNotByName() {
        assertArrayEquals(new int[]{10010, 11259}, MoonlightMothScript.NET_IDS);
    }

    @Test
    void renamedInsectNetIsFound() {
        assertEquals(10010, MoonlightMothScript.findNet(id -> id == 10010));
    }

    @Test
    void magicNetIsFound() {
        assertEquals(11259, MoonlightMothScript.findNet(id -> id == 11259));
    }

    @Test
    void regularNetIsPreferredWhenBothAreAvailable() {
        assertEquals(10010, MoonlightMothScript.findNet(id -> true));
    }

    @Test
    void missingNetIsReported() {
        assertEquals(-1, MoonlightMothScript.findNet(id -> false));
    }
}
