package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droefficientwalker.support.TransportObjectClick;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TransportObjectClickTest {
    @Test public void cachedMultiTileGangplankUsesRawObjectAndSouthWestAnchor() throws Exception { click(true); }
    @Test public void cachedWallGangplankKeepsItsOwnTile() throws Exception { click(false); }
    private void click(boolean multiTile) throws Exception {
        Client client=mock(Client.class);ClientThread thread=mock(ClientThread.class);Player player=mock(Player.class);
        WorldView view=mock(WorldView.class);when(view.getId()).thenReturn(-1);when(view.isTopLevel()).thenReturn(true);
        LocalPoint local=new LocalPoint(50*128+64,50*128+64);
        TileObject raw;
        Rs2TileObjectModel cached;
        if(multiTile) {GameObject object=mock(GameObject.class);when(object.sizeX()).thenReturn(2);when(object.sizeY()).thenReturn(4);raw=object;cached=new Rs2TileObjectModel(object);}
        else {WallObject wall=mock(WallObject.class);raw=wall;cached=new Rs2TileObjectModel(wall);}
        when(raw.getId()).thenReturn(270);when(raw.getLocalLocation()).thenReturn(local);when(raw.getWorldView()).thenReturn(view);
        when(client.getLocalPlayer()).thenReturn(player);when(player.getLocalLocation()).thenReturn(local);
        ObjectComposition composition=mock(ObjectComposition.class);when(composition.getName()).thenReturn("Gangplank");
        when(composition.getActions()).thenReturn(new String[]{"Cross",null,null,null,null});when(client.getObjectDefinition(270)).thenReturn(composition);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        when(thread.invoke(any(java.util.function.Supplier.class))).thenAnswer(i->((java.util.function.Supplier<?>)i.getArgument(0)).get());
        AtomicReference<NewMenuEntry> dispatched=new AtomicReference<>();
        try(var micro=mockStatic(Microbot.class);var camera=mockStatic(Rs2Camera.class);var objects=mockStatic(SceneObjects.class);var ui=mockStatic(Rs2UiHelper.class)) {
            micro.when(Microbot::getClient).thenReturn(client);micro.when(Microbot::getClientThread).thenReturn(thread);
            camera.when(()->Rs2Camera.isTileOnScreen(local)).thenReturn(true);
            objects.when(()->SceneObjects.convertToObjectComposition(cached)).thenReturn(composition);
            ui.when(()->Rs2UiHelper.stripColTags("Cross")).thenReturn("Cross");
            ui.when(()->Rs2UiHelper.getObjectClickbox(raw)).thenReturn(new Rectangle(10,10,20,20));
            micro.when(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class))).thenAnswer(i->{dispatched.set(i.getArgument(0));return null;});
            assertFalse(TransportObjectClick.click(cached,"Climb"));assertNull(dispatched.get());
            assertTrue(TransportObjectClick.click(cached,"Cross"));
            assertNotNull(dispatched.get());assertSame(raw,dispatched.get().getGameObject());
            assertEquals(multiTile?49:50,dispatched.get().getParam0());
            assertEquals(multiTile?48:50,dispatched.get().getParam1());
            assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION,dispatched.get().getType());
        }
    }
}
