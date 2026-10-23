package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.FlaxSpinLocations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlaxSpinScriptTest {

    @Test
    void noneLocationHasNoSpinLocation() {
        CraftingConfig config = mock(CraftingConfig.class);
        when(config.flaxSpinLocation()).thenReturn(FlaxSpinLocations.NONE);
        assertNull(FlaxSpinScript.getSpinLocation(config));
    }

    @Test
    void missingLocationHasNoSpinLocation() {
        CraftingConfig config = mock(CraftingConfig.class);
        when(config.flaxSpinLocation()).thenReturn(null);
        assertNull(FlaxSpinScript.getSpinLocation(config));
    }

    @Test
    void selectedLocationReturnsItsWorldPoint() {
        CraftingConfig config = mock(CraftingConfig.class);
        when(config.flaxSpinLocation()).thenReturn(FlaxSpinLocations.FALADOR);
        assertEquals(FlaxSpinLocations.FALADOR.getWorldPoint(), FlaxSpinScript.getSpinLocation(config));
    }

    @Test
    void unknownSpinLocationIsNotNear() {
        assertFalse(FlaxSpinScript.isNearSpinningWheel(new WorldPoint(2982, 3314, 0), FlaxSpinLocations.NONE.getWorldPoint(), 4));
    }

    @Test
    void unknownPlayerLocationIsNotNear() {
        assertFalse(FlaxSpinScript.isNearSpinningWheel(null, FlaxSpinLocations.FALADOR.getWorldPoint(), 4));
    }

    @Test
    void playerNextToSpinLocationIsNear() {
        assertTrue(FlaxSpinScript.isNearSpinningWheel(new WorldPoint(2984, 3314, 0), FlaxSpinLocations.FALADOR.getWorldPoint(), 4));
    }

    @Test
    void playerFarFromSpinLocationIsNotNear() {
        assertFalse(FlaxSpinScript.isNearSpinningWheel(new WorldPoint(3000, 3314, 0), FlaxSpinLocations.FALADOR.getWorldPoint(), 4));
    }
}
