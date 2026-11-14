package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SailingTabletDispatchTest {
    @Test void onlyAnActuallySentVisibleBreakActionArmsTheOneTabletReceipt() {
        var client=mock(Client.class);var thread=mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(c->Optional.ofNullable(((Callable<?>)c.getArgument(0)).call()));
        var config=mock(EfficientSailingConfig.class);when(config.useSummonBoatTablet()).thenReturn(true);when(config.boatName()).thenReturn("Giant Mind");
        var io=spy(new MicrobotSailingFacade(client,config,s->{}));doReturn(true).when(io).atDock(123);
        var tablet=mock(Rs2ItemModel.class);when(tablet.getSlot()).thenReturn(5);when(tablet.getName()).thenReturn("Summon boat");
        when(tablet.getInventoryActions()).thenReturn(new String[]{"Break",null,null,null,"Drop"});
        var inventory=mock(Widget.class);var item=mock(Widget.class);
        when(inventory.getId()).thenReturn(ComponentID.INVENTORY_CONTAINER);when(inventory.getChild(5)).thenReturn(item);
        when(item.getItemId()).thenReturn(31441);when(item.getBounds()).thenReturn(new Rectangle(100,100,32,32));
        boolean paused=Microbot.pauseAllScripts.get();Microbot.pauseAllScripts.set(false);
        try(var micro=mockStatic(Microbot.class);var bank=mockStatic(Rs2Bank.class);var inv=mockStatic(Rs2Inventory.class);
            var tabs=mockStatic(Rs2Tab.class);var input=mockStatic(SailingInput.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);micro.when(Microbot::isLoggedIn).thenReturn(true);
            io.permit(()->true);tabs.when(Rs2Tab::switchToInventoryTab).thenReturn(true);
            inv.when(()->Rs2Inventory.get(31441)).thenReturn(tablet);
            inv.when(()->Rs2Inventory.interact(31441,"Break")).thenReturn(true);
            assertFalse(io.breakSummonTablet(123),"Missing inventory widget is not a dispatched click");
            input.verifyNoInteractions();
            when(client.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(inventory);
            // The definition says Break is first, but the loaded widget puts Drop first.
            when(item.getActions()).thenReturn(new String[]{"Drop","Last-boat","Use","Break","Examine"});
            assertFalse(io.breakSummonTablet(123),"A rejected input is not a dispatched click");
            input.when(()->SailingInput.click(any(),any(),any())).thenReturn(true);
            assertTrue(io.breakSummonTablet(123));
            input.verify(()->SailingInput.click(any(),argThat(e->e.getOption().equals("Break")&&e.getIdentifier()==4
                &&e.getType()==net.runelite.api.MenuAction.CC_OP
                &&e.getParam0()==5&&e.getParam1()==ComponentID.INVENTORY_CONTAINER&&e.getItemId()==31441),any()),times(2));
            inv.verify(()->Rs2Inventory.interact(31441,"Break"),never());
            when(item.getActions()).thenReturn(new String[]{"Drop","Examine"});
            assertFalse(io.breakSummonTablet(123),"Missing live Break must never fall back to the definition's Drop position");
            when(item.getActions()).thenReturn(null);
            assertFalse(io.breakSummonTablet(123),"An unloaded live action list must not emit a click");
            inv.when(Rs2Inventory::isItemSelected).thenReturn(true);
            assertFalse(io.breakSummonTablet(123));inv.verify(Rs2Inventory::deselect);
            input.verifyNoMoreInteractions();
        }finally{Microbot.pauseAllScripts.set(paused);}
    }
}
