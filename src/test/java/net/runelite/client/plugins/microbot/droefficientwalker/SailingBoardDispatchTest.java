package net.runelite.client.plugins.microbot.droefficientwalker;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SailingBoardDispatchTest {
    @Test void boardingUsesActualInputResultAndCurrentGangplankAnchor()throws Exception{
        var client=mock(Client.class);var thread=mock(ClientThread.class);var view=mock(WorldView.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        when(view.getId()).thenReturn(-1);when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
        var cache=mock(Rs2TileObjectCache.class,RETURNS_DEEP_STUBS);var plank=mock(Rs2TileObjectModel.class);var comp=mock(ObjectComposition.class);
        when(cache.query().withIds(any(int[].class)).nearestOnClientThread()).thenReturn(plank);
        when(plank.getId()).thenReturn(123);when(plank.getWorldView()).thenReturn(view);
        when(plank.getWorldLocation()).thenReturn(new WorldPoint(10,12,0));var local=LocalPoint.fromScene(10,12,view);when(plank.getLocalLocation()).thenReturn(local);
        when(plank.getObjectComposition()).thenReturn(comp);when(comp.getActions()).thenReturn(new String[]{"Inspect","Board"});when(comp.getName()).thenReturn("Gangplank");
        when(plank.getClickbox()).thenReturn(new Rectangle(100,100,20,20));when(client.getViewportWidth()).thenReturn(800);when(client.getViewportHeight()).thenReturn(600);
        var io=new MicrobotSailingFacade(client,mock(EfficientSailingConfig.class),s->{});io.permit(()->true);
        boolean paused=Microbot.pauseAllScripts.get();Microbot.pauseAllScripts.set(false);
        try(var micro=mockStatic(Microbot.class);var input=mockStatic(SailingInput.class);var camera=mockStatic(Rs2Camera.class);var inv=mockStatic(Rs2Inventory.class)){
            micro.when(Microbot::getClientThread).thenReturn(thread);micro.when(Microbot::isLoggedIn).thenReturn(true);micro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            camera.when(()->Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
            assertFalse(io.board(123));
            input.when(()->SailingInput.click(any(),any(),any())).thenReturn(true);
            assertTrue(io.board(123));
            input.verify(()->SailingInput.click(any(),argThat(e->e.getOption().equals("Board")&&e.getType()==MenuAction.GAME_OBJECT_SECOND_OPTION&&e.getParam0()==10&&e.getParam1()==12&&e.getIdentifier()==123),any()),times(2));
            verify(plank,never()).click(anyString());
            io.permit(()->false);assertFalse(io.board(123));input.verifyNoMoreInteractions();
        }finally{Microbot.pauseAllScripts.set(paused);}
    }
}
