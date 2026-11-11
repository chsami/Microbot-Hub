package net.runelite.client.plugins.microbot.droefficientwalker;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import java.util.concurrent.Callable;
import java.awt.Rectangle;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PassageNecklaceExecutionTest {
    static class Engine { List<Object> teleportProviders=new ArrayList<>();Object activeTeleportProvider; }
    @Disabled("Calls the droplugins client's guarded Rs2Inventory dispatcher; the Hub client's Rs2Inventory.interact differs")
    @Test void passageProviderUsesRubAndItsOwnWizardsTowerDialogue()throws Exception{
        var ctor=Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader").getDeclaredConstructor(ClassLoader.class);ctor.setAccessible(true);
        ClassLoader loader=(ClassLoader)ctor.newInstance(getClass().getClassLoader());
        Class<?> type=Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.TeleportProviders$TeleportProvider",true,loader);
        var pc=type.getDeclaredConstructor(String.class,Supplier.class,Spell.class,int.class,String.class,IntSupplier.class,int.class,int[].class,Supplier.class,BooleanSupplier.class,boolean.class);pc.setAccessible(true);
        Object original=pc.newInstance("Necklace of passage: Wizards' Tower",(Supplier<WorldPoint>)()->new WorldPoint(3114,3181,0),null,-1,null,(IntSupplier)()->1,-1,new int[]{21146,21149,21151,21153,21155},(Supplier<String>)()->"Wizards' Tower",(BooleanSupplier)()->true,true);
        Engine engine=new Engine();engine.teleportProviders.add(original);engine.activeTeleportProvider=original;
        Client client=mock(Client.class);when(client.isClientThread()).thenReturn(true);when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        var config=mock(EfficientWalkerConfig.class);
        try(var micro=mockStatic(Microbot.class);var inventory=mockStatic(Rs2Inventory.class);var player=mockStatic(Rs2Player.class);var dialogue=mockStatic(Rs2Dialogue.class);var widgets=mockStatic(Rs2Widget.class);var bank=mockStatic(Rs2Bank.class);var exchange=mockStatic(Rs2GrandExchange.class);var shop=mockStatic(Rs2Shop.class)){
            micro.when(Microbot::getClient).thenReturn(client);player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3164,3487,0));
            var necklace=mock(Rs2ItemModel.class);when(necklace.getActionFromList(anyList())).thenReturn("Rub");inventory.when(()->Rs2Inventory.get(21146)).thenReturn(necklace);
            var support=new CatalogTeleportSupport(client,config,loader,Engine.class,engine,"net.runelite.client.plugins.microbot.droefficientwalker.engine");
            var action=WalkerTestReflection.declaredField(type, "itemActionSupplier");action.setAccessible(true);
            assertEquals("Wizards' Tower",((Supplier<?>)action.get(original)).get());
            support.install();assertTrue(support.providerCount()>0);
            assertTrue(engine.teleportProviders.contains(original),"Keep the selected provider identity and landing");
            assertEquals("Rub",((Supplier<?>)action.get(original)).get(),"A Rub-only necklace must not receive a nonexistent direct inventory action");
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);dialogue.when(()->Rs2Dialogue.clickOption("wizards' tower",false)).thenReturn(true);
            assertTrue(support.handleDialogue());dialogue.verify(()->Rs2Dialogue.clickOption("wizards' tower",false));
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(false);
            Widget inv=mock(Widget.class),slot=mock(Widget.class);
            ClientThread thread=mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            micro.when(Microbot::getClientThread).thenReturn(thread);
            when(client.getWidget(InterfaceID.Inventory.ITEMS)).thenReturn(inv);
            widgets.when(()->Rs2Widget.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(inv);
            when(inv.getChildren()).thenReturn(new Widget[]{slot});when(slot.getIndex()).thenReturn(7);
            when(slot.getItemId()).thenReturn(21146);when(slot.getBounds()).thenReturn(new Rectangle(10,10,20,20));
            when(slot.getActions()).thenReturn(new String[]{"Wear","Rub","Drop"});
            when(necklace.getName()).thenReturn("Necklace of passage(5)");
            ItemComposition definition=mock(ItemComposition.class);when(client.getItemDefinition(anyInt())).thenReturn(definition);
            inventory.when(()->Rs2Inventory.hasItem(21146)).thenReturn(true);
            inventory.when(()->Rs2Inventory.interact(21146,"Rub")).thenCallRealMethod();
            inventory.when(()->Rs2Inventory.interact(necklace,"Rub")).thenCallRealMethod();
            Method activate=WalkerTestReflection.declaredMethod(type, "activate",WorldPoint.class,WorldPoint.class);activate.setAccessible(true);
            when(inv.isHidden()).thenReturn(true);when(necklace.getId()).thenReturn(21146);when(necklace.getSlot()).thenReturn(7);
            assertEquals(false,activate.invoke(original,new WorldPoint(2945,3368,0),new WorldPoint(3614,9477,0)),"The guarded inventory dispatcher declines a hidden slot");
            assertTrue(support.prepareItemActivation(0));assertTrue(support.prepareItemActivation(100));
            verify(client,times(1)).runScript(915,net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY.getVarcIntIndex());
            assertFalse(support.prepareItemActivation(8000),"Readiness retries have a deadline");
            support.resetActivationReadiness();
            assertTrue(support.prepareItemActivation(9000),"A resumed/new journey receives a fresh readiness budget");
            assertSame(original,engine.activeTeleportProvider,"Readiness must retain the selected teleport rather than replan a ground route");
            when(inv.isHidden()).thenReturn(false);when(inv.getChildren()).thenReturn(new Widget[]{slot});
            when(slot.getIndex()).thenReturn(7);when(slot.getItemId()).thenReturn(21146);
            assertFalse(support.prepareItemActivation(9200));

            assertEquals(true,activate.invoke(original,new WorldPoint(2945,3368,0),new WorldPoint(3614,9477,0)));
            inventory.verify(()->Rs2Inventory.interact(21146,"Rub"),times(2));
            micro.verify(()->Microbot.doInvoke(any(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry.class),any(Rectangle.class)),times(1));
            engine.activeTeleportProvider=null;assertFalse(support.handleDialogue(),"No unrelated dialogue is advanced without the selected necklace");
        }
    }
}
