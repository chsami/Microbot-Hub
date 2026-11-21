package net.runelite.client.plugins.microbot.drorecorder;

import java.util.List;
import java.util.Map;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroRecorderBagContextTest {
    @Test void recordsAbsentMenuMetadataAndActualBagActionsWithoutOtherMenuTargets() {
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);
        ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(new Item[]{new Item(22586,1)});
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        Widget bank=mock(Widget.class),container=mock(Widget.class),cell=mock(Widget.class);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(bank);
        when(client.getWidget(InterfaceID.Bankside.ITEMS)).thenReturn(container);
        when(container.getChild(0)).thenReturn(cell);when(cell.getItemId()).thenReturn(22586);
        when(cell.getActions()).thenReturn(new String[]{"Deposit-1"});
        MenuEntry view=mock(MenuEntry.class),other=mock(MenuEntry.class);
        when(view.getParam1()).thenReturn(InterfaceID.Bankside.ITEMS);when(view.getOption()).thenReturn("View");
        when(view.getItemId()).thenReturn(-1);when(view.getIdentifier()).thenReturn(8);when(view.getType()).thenReturn(MenuAction.CC_OP_LOW_PRIORITY);
        when(other.getParam0()).thenReturn(7);when(other.getParam1()).thenReturn(InterfaceID.Bankside.ITEMS);
        when(client.getMenuEntries()).thenReturn(new MenuEntry[]{view,other});
        var context=DroRecorderSession.lootingBagBankContext(client);
        assertEquals(22586,context.get("widgetItemId"));assertEquals(true,context.get("bankOpen"));
        var menu=(List<Map<String,Object>>)context.get("menu");assertEquals(1,menu.size());
        assertEquals(-1,menu.get(0).get("itemId"));assertEquals("View",menu.get(0).get("option"));
        assertFalse(menu.get(0).containsKey("target"));
        when(client.isClientThread()).thenReturn(false);
        assertTrue(DroRecorderSession.lootingBagBankContext(client).isEmpty());
    }
}
