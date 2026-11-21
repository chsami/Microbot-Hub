package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EquipmentVariantTest {
    @Test public void staleIdsAcceptSameNameButRejectNotedAndDifferentVariants() {
        InventorySetupsItem row = mock(InventorySetupsItem.class); when(row.getName()).thenReturn("Graceful hood");
        Rs2ItemModel live = mock(Rs2ItemModel.class); when(live.getName()).thenReturn("Graceful hood");
        assertTrue(DroInventorySetup.matches(row,live));
        when(live.getName()).thenReturn("Graceful cape"); assertFalse(DroInventorySetup.matches(row,live));
        when(live.getName()).thenReturn("Graceful hood"); when(live.isNoted()).thenReturn(true); assertFalse(DroInventorySetup.matches(row,live));
    }
}
