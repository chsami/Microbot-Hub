package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SandCrabScriptTest {

    @Test
    void unselectedInventorySetupIsMissing() {
        assertFalse(SandCrabScript.hasInventorySetup(null));
    }

    @Test
    void setupWithoutNameIsMissing() {
        InventorySetup setup = mock(InventorySetup.class);
        assertFalse(SandCrabScript.hasInventorySetup(setup));

        when(setup.getName()).thenReturn("");
        assertFalse(SandCrabScript.hasInventorySetup(setup));
    }

    @Test
    void namedSetupIsPresent() {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn("Sand crabs");

        assertTrue(SandCrabScript.hasInventorySetup(setup));
    }
}
