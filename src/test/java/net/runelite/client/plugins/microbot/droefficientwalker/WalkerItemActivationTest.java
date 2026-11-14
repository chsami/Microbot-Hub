package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import java.awt.Rectangle;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerItemActivationTest {
    static class Engine { Object activeTeleportProvider; }
    static class Provider { Spell spell; int[] itemIds = {11988}; }

    @Disabled("Relies on the droplugins client's Rs2Inventory slot dispatcher, which the Hub client does not have")
    @Test void wealthCrystalAndTabletWaitForTheirActualSlotBeforeTheEngineDispatches() throws Exception {
        int[] ids={11988,6100,8007};
        String[] names={"Ring of wealth: Grand Exchange","Teleport crystal: Prifddinas","Varrock tablet"};
        String[] actions={"Grand Exchange","Prifddinas","Break"};
        var ctor=Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader").getDeclaredConstructor(ClassLoader.class);
        ctor.setAccessible(true);ClassLoader loader=(ClassLoader)ctor.newInstance(getClass().getClassLoader());
        Class<?> type=Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.TeleportProviders$TeleportProvider",true,loader);
        var pc=type.getDeclaredConstructor(String.class,Supplier.class,Spell.class,int.class,String.class,IntSupplier.class,int.class,int[].class,Supplier.class,BooleanSupplier.class,boolean.class);
        pc.setAccessible(true);Method activate=WalkerTestReflection.declaredMethod(type, "activate",WorldPoint.class,WorldPoint.class);activate.setAccessible(true);
        for(int n=0;n<ids.length;n++) {
            int id=ids[n];String action=actions[n];WorldPoint start=new WorldPoint(2443,3083,0),landing=new WorldPoint(3162,3480,0);
            Object provider=pc.newInstance(names[n],(Supplier<WorldPoint>)()->landing,null,-1,null,(IntSupplier)()->1,-1,new int[]{id},(Supplier<String>)()->action,(BooleanSupplier)()->true,true);
            Engine engine=new Engine();engine.activeTeleportProvider=provider;Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            try(var micro=mockStatic(Microbot.class);var inventory=mockStatic(Rs2Inventory.class);
                var widgets=mockStatic(Rs2Widget.class);var bank=mockStatic(Rs2Bank.class);
                var ge=mockStatic(Rs2GrandExchange.class);var shop=mockStatic(Rs2Shop.class);var player=mockStatic(Rs2Player.class)) {
                micro.when(Microbot::getClient).thenReturn(client);player.when(Rs2Player::getWorldLocation).thenReturn(start);
                ClientThread thread=mock(ClientThread.class);
                when(thread.runOnClientThreadOptional(any())).thenAnswer(call->Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
                micro.when(Microbot::getClientThread).thenReturn(thread);
                Rs2ItemModel item=mock(Rs2ItemModel.class);when(item.getId()).thenReturn(id);when(item.getSlot()).thenReturn(7);when(item.getName()).thenReturn(names[n]);
                inventory.when(()->Rs2Inventory.get(id)).thenReturn(item);inventory.when(()->Rs2Inventory.hasItem(id)).thenReturn(true);
                inventory.when(()->Rs2Inventory.interact(id,action)).thenCallRealMethod();inventory.when(()->Rs2Inventory.interact(item,action)).thenCallRealMethod();
                Widget inv=mock(Widget.class),slot=mock(Widget.class);when(inv.isHidden()).thenReturn(true);
                when(client.getWidget(InterfaceID.Inventory.ITEMS)).thenReturn(inv);widgets.when(()->Rs2Widget.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(inv);
                when(inv.getChildren()).thenReturn(new Widget[]{slot});when(slot.getIndex()).thenReturn(7);when(slot.getItemId()).thenReturn(id);
                when(slot.getActions()).thenReturn(new String[]{action});when(slot.getBounds()).thenReturn(new Rectangle(10,10,20,20));
                when(client.getItemDefinition(id)).thenReturn(mock(ItemComposition.class));
                CatalogTeleportSupport support=new CatalogTeleportSupport(client,mock(EfficientWalkerConfig.class),loader,Engine.class,engine,"net.runelite.client.plugins.microbot.droefficientwalker.engine");
                assertEquals(false,activate.invoke(provider,start,landing),names[n]+" reproduces activation-unavailable when the slot is hidden");
                assertTrue(support.prepareItemActivation(0));assertTrue(support.prepareItemActivation(100));
                verify(client,times(1)).runScript(915,net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY.getVarcIntIndex());
                when(inv.isHidden()).thenReturn(false);when(slot.getBounds()).thenReturn(new Rectangle());
                assertTrue(support.prepareItemActivation(200),"Visible but unlaid-out inventory is still not ready");
                when(slot.getBounds()).thenReturn(new Rectangle(10,10,20,20));when(slot.getItemId()).thenReturn(id+1000);
                assertTrue(support.prepareItemActivation(300),"A stale slot must not release activation");
                when(slot.getItemId()).thenReturn(id);assertFalse(support.prepareItemActivation(400));
                assertSame(provider,engine.activeTeleportProvider);assertEquals(true,activate.invoke(provider,start,landing));
                micro.verify(()->Microbot.doInvoke(any(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry.class),any(Rectangle.class)),times(1));
            }
        }
    }

    @Test void wornOnlyAndSpellProvidersKeepTheirExistingActivationPaths() {
        Engine engine=new Engine();Provider provider=new Provider();engine.activeTeleportProvider=provider;Client client=mock(Client.class);
        try(var inventory=mockStatic(Rs2Inventory.class)) {
            CatalogTeleportSupport support=new CatalogTeleportSupport(client,mock(EfficientWalkerConfig.class),getClass().getClassLoader(),Engine.class,engine,"unused");
            assertFalse(support.prepareItemActivation(0));verify(client,never()).runScript(anyInt(),any());
            provider.spell=mock(Spell.class);inventory.when(()->Rs2Inventory.get(11988)).thenReturn(mock(Rs2ItemModel.class));
            assertFalse(support.prepareItemActivation(100));verify(client,never()).getWidget(anyInt());
        }
    }
}
