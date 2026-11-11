package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual helper boundaries: old work cannot borrow a newly rebound caller's permission. */
public class FacadeDispatchIntegrationTest {
    private static void sailPanel(Fixture f,String mainAction) {
        sailPanel(f,mainAction,"Reverse","Set sails");
    }
    private static void sailPanel(Fixture f,String mainAction,String lowerAction,String raiseAction) {
        int id=61407257; // Panel captured in the Little Pearl failure log.
        Widget rows=mock(Widget.class);when(rows.getId()).thenReturn(id);when(rows.getIndex()).thenReturn(-1);
        when(rows.getBounds()).thenReturn(new Rectangle(10,10,100,30));
        Widget[] buttons=new Widget[3];
        String[] labels={mainAction,lowerAction,raiseAction};
        for(int i=0;i<3;i++) {
            buttons[i]=mock(Widget.class);when(buttons[i].getId()).thenReturn(id);when(buttons[i].getIndex()).thenReturn(i);
            when(buttons[i].getActions()).thenReturn(new String[]{labels[i]});
            when(buttons[i].getBounds()).thenReturn(new Rectangle(10+i*30,10,25,25));
            when(rows.getChild(i)).thenReturn(buttons[i]);
        }
        when(rows.getDynamicChildren()).thenReturn(buttons);
        when(f.client.getWidget(id)).thenReturn(rows);when(f.client.getWidgetRoots()).thenReturn(new Widget[]{rows});
    }
    private static final class Fixture implements AutoCloseable {
        final Client client = mock(Client.class);
        final ClientThread clientThread = mock(ClientThread.class);
        final EfficientSailingConfig config = mock(EfficientSailingConfig.class, CALLS_REAL_METHODS);
        final Rs2BoatModel boat = mock(Rs2BoatModel.class);
        final NavigationSessions sessions = new NavigationSessions();
        final NavigationSessions.Caller oldCaller = sessions.registerCaller(new Object(), () -> true);
        final NavigationSessions.Caller nextCaller = sessions.registerCaller(new Object(), () -> true);
        final NavigationSessions.Session oldSession = sessions.request(oldCaller, new SeaPoint(2685, 3100)).session;
        final MicrobotSailingFacade io;
        final MockedStatic<Microbot> dro;
        final MockedStatic<net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue> dialogue;
        final MockedStatic<InputArbiter> arbiter;
        final boolean previousPause;

        Fixture() throws Exception {
            previousPause = Microbot.pauseAllScripts.get();
            Microbot.pauseAllScripts.set(false);
            dro = mockStatic(Microbot.class);
            dialogue = mockStatic(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.class);
            arbiter = mockStatic(InputArbiter.class);
            dro.when(Microbot::isLoggedIn).thenReturn(true);
            net.runelite.client.plugins.microbot.util.mouse.Mouse mouse = mock(net.runelite.client.plugins.microbot.util.mouse.Mouse.class);
            when(mouse.getPoints()).thenReturn(new java.util.ArrayDeque<>());
            dro.when(Microbot::getMouse).thenReturn(mouse);
            dro.when(Microbot::getClientThread).thenReturn(clientThread);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                    Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
            Rs2BoatCache cache = mock(Rs2BoatCache.class);
            Player player = mock(Player.class);
            WorldView deck = mock(WorldView.class);
            dro.when(Microbot::getRs2BoatCache).thenReturn(cache);
            when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldView()).thenReturn(deck);
            when(deck.getId()).thenReturn(2);
            when(cache.getLocalBoat()).thenReturn(boat);
            when(boat.getWorldView()).thenReturn(deck);
            when(boat.isNavigating()).thenReturn(true);
            when(client.getTickCount()).thenReturn(10);
            io = new MicrobotSailingFacade(client, config, ignored -> { });
            NavigationSessions.DispatchTicket oldTicket = sessions.inputTicket(oldSession);
            io.permit(() -> sessions.canDispatch(oldTicket));
            io.beginStep();
        }

        void replaceOwner() {
            sessions.manualTakeover("replacement during helper");
            NavigationSessions.Result next = sessions.request(nextCaller, new SeaPoint(2700, 3100));
            assertTrue(next.accepted);
            NavigationSessions.DispatchTicket nextTicket = sessions.inputTicket(next.session);
            assertTrue(sessions.canDispatch(nextTicket));
            io.permit(() -> sessions.canDispatch(nextTicket));
        }

        @Override public void close() {
            io.endStep();
            dialogue.close(); arbiter.close(); dro.close(); Microbot.pauseAllScripts.set(previousPause);
        }
    }






    @Test public void boardingActionReadReturningAfterReplacementCannotClickTheGangplank() throws Exception {
        try (Fixture f = new Fixture();
             var inventory = mockStatic(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.class);
             var camera = mockStatic(net.runelite.client.plugins.microbot.util.camera.Rs2Camera.class);
             var input = mockStatic(SailingInput.class)) {
            Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
            Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
            Rs2TileObjectModel plank = mock(Rs2TileObjectModel.class);
            f.dro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            when(cache.query()).thenReturn(query);
            when(query.nearestOnClientThread()).thenReturn(plank);
            WorldView view = mock(WorldView.class);
            when(view.getId()).thenReturn(-1);when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            when(plank.getWorldView()).thenReturn(view);when(plank.getId()).thenReturn(12345);
            when(plank.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(10,12,0));
            var local=net.runelite.api.coords.LocalPoint.fromScene(10,12,view);
            when(plank.getLocalLocation()).thenReturn(local);
            when(plank.getClickbox()).thenReturn(new Rectangle(100,100,20,20));
            when(f.client.getViewportWidth()).thenReturn(800);when(f.client.getViewportHeight()).thenReturn(600);
            camera.when(()->net.runelite.client.plugins.microbot.util.camera.Rs2Camera.isTileOnScreen(any(net.runelite.api.coords.LocalPoint.class))).thenReturn(true);
            var definition=mock(net.runelite.api.ObjectComposition.class);
            when(definition.getActions()).thenReturn(new String[]{"Board"});when(definition.getName()).thenReturn("Gangplank");
            when(plank.getObjectComposition()).thenAnswer(invocation -> {
                f.replaceOwner();
                return definition;
            });
            assertFalse(f.io.board(12345));
            verify(plank).getObjectComposition();
            input.verifyNoInteractions();
            verify(plank, never()).click("Board");
            assertEquals(NavigationSessions.State.TAKEN_OVER,f.sessions.status(f.oldSession).state);
        }
    }
}
