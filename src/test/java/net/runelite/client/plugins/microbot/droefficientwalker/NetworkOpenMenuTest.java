package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkOpenMenuTest {
    @Test void openMinecartAndQuetzalMenusSelectOnceWithoutAnotherWorldInteraction()throws Exception{
        for(String type:List.of("MINECART","QUETZAL","FOSSIL_ROWBOAT")){
            Client client=mock(Client.class);Player player=mock(Player.class);ClientThread thread=mock(ClientThread.class);
            WorldPoint origin=new WorldPoint(1500,3500,0),landing=new WorldPoint(1700,3600,0);
            AtomicReference<WorldPoint> location=new AtomicReference<>(origin);
            when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenAnswer(call->location.get());
            when(thread.invoke(any(Supplier.class))).thenAnswer(call->((Supplier<?>)call.getArgument(0)).get());
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call->Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
            try(var bot=mockStatic(Microbot.class);var widgets=mockStatic(Rs2Widget.class);
                var objects=mockStatic(SceneObjects.class);var npcs=mockStatic(Rs2Npc.class);
                var dialogue=mockStatic(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.class);
                var rowboat=mockStatic(FossilRowboatMenu.class);
                var keys=mockStatic(net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.class)){
                bot.when(Microbot::getClient).thenReturn(client);bot.when(Microbot::getClientThread).thenReturn(thread);
                Widget root=mock(Widget.class),destination=mock(Widget.class);
                when(destination.getText()).thenReturn("Destination");
                when(destination.getBounds()).thenReturn(new java.awt.Rectangle(),new java.awt.Rectangle(10,10,100,30));
                dialogue.when(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::hasSelectAnOption).thenReturn(true);
                dialogue.when(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::getDialogueOptions).thenReturn(List.of(destination));
                rowboat.when(()->FossilRowboatMenu.destination("3. Destination")).thenReturn("Destination");
                rowboat.when(()->FossilRowboatMenu.awaitOption(eq("Destination"),anyLong())).thenReturn(true);
                keys.when(()->net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress('1'))
                    .thenAnswer(call->{location.set(landing);return null;});
                widgets.when(()->Rs2Widget.isWidgetVisible(anyInt())).thenReturn(true);
                widgets.when(()->Rs2Widget.hasWidgetText("Minecart",187,0,false)).thenReturn(false,true);
                widgets.when(()->Rs2Widget.isHidden(947,9)).thenReturn(true);
                widgets.when(()->Rs2Widget.getWidget(anyInt(),anyInt())).thenReturn(root);
                widgets.when(()->Rs2Widget.getWidget(anyInt())).thenReturn(root);
                widgets.when(()->Rs2Widget.findWidget("Destination",List.of(root),false)).thenReturn(destination);
                widgets.when(()->Rs2Widget.clickWidget(destination)).thenAnswer(call->{location.set(landing);return true;});
                var bridge=new NetworkTransportSupport(client,new EfficientWalkerConfig(){},getClass().getClassLoader(),Object.class,new Object(),
                    "net.runelite.client.plugins.microbot.droefficientwalker.engine",mock(CatalogTeleportSupport.class));
                Class<?> kind=Class.forName(NetworkTransportSupport.class.getName()+"$EdgeType");
                Class<?> edge=Class.forName(NetworkTransportSupport.class.getName()+"$NetworkEdge");
                Constructor<?> constructor=edge.getDeclaredConstructor(kind,WorldPoint.class,WorldPoint.class,String.class,String.class,int.class,int.class);
                constructor.setAccessible(true);
                Object route=constructor.newInstance(Enum.valueOf((Class)kind,type),origin,landing,type.equals("FOSSIL_ROWBOAT")?"3. Destination":"Destination","Transport",123,0);
                var method=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, type.equals("MINECART")?"executeMinecart":type.equals("QUETZAL")?"executeQuetzal":"executeFossilRowboat",edge);
                method.setAccessible(true);assertEquals("SUCCESS",method.invoke(bridge,route).toString());
                objects.verifyNoInteractions();npcs.verifyNoInteractions();
                if(type.equals("FOSSIL_ROWBOAT"))keys.verify(()->net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress('1'),times(1));
                else widgets.verify(()->Rs2Widget.clickWidget(destination),times(1));
                bridge.shutdown();
            }
        }
    }
}
