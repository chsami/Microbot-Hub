package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.util.*;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FirecapeInventoryWidgetTest {
    @Test void liveActionIsRequiredAndDeterminesTheOperation() throws Exception {
        try(var micro=mockStatic(Microbot.class);var inventory=mockStatic(Rs2Inventory.class)) {
            Client client=mock(Client.class); ClientThread thread=mock(ClientThread.class);
            micro.when(Microbot::getClient).thenReturn(client); micro.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            ItemContainer items=mock(ItemContainer.class); when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(items);
            when(items.getItem(2)).thenReturn(new Item(123,1));
            Widget root=mock(Widget.class),slot=mock(Widget.class); inventory.when(Rs2Inventory::getInventory).thenReturn(root);
            when(root.getChildren()).thenReturn(new Widget[]{slot}); when(root.getId()).thenReturn(9764864);
            when(slot.getIndex()).thenReturn(2); when(slot.getBounds()).thenReturn(new Rectangle(600,300,32,32));
            when(client.getCanvasWidth()).thenReturn(800); when(client.getCanvasHeight()).thenReturn(600);
            ItemComposition definition=mock(ItemComposition.class); when(client.getItemDefinition(123)).thenReturn(definition);
            when(definition.getInventoryActions()).thenReturn(new String[]{"Eat"});
            FcActions actions=new FcActions(); FcFrame.ItemSlot item=new FcFrame.ItemSlot(2,123,1,"Food",List.of("Eat"));
            assertNull(actions.inventoryClick(item,"Eat"));
            when(slot.getActions()).thenReturn(new String[]{"Drop"}); assertNull(actions.inventoryClick(item,"Eat"));
            when(slot.getActions()).thenReturn(new String[]{"Drop",null,"Eat"});
            var click=actions.inventoryClick(item,"Eat"); assertNotNull(click); assertEquals(3,click.entry().getIdentifier());
        }
    }
}
