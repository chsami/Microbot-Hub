package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TzhaarVentReceiptTest {
    private static Transport vent(boolean reverse){
        var outside=new WorldPoint(2474,5138,0);var inside=new WorldPoint(2474,5136,0);
        return new Transport(reverse?inside:outside,reverse?outside:inside,"Vent",TransportType.TRANSPORT,true,"Pass","Hot vent door",30266,2);
    }
    @Test void confirmsTheFarSideWithoutDemandingOneExactLandingTile(){
        assertTrue(TzhaarAccess.crossedVent(vent(false),new WorldPoint(2474,5135,0)));
        assertFalse(TzhaarAccess.crossedVent(vent(false),new WorldPoint(2474,5137,0)));
        assertFalse(TzhaarAccess.crossedVent(vent(false),new WorldPoint(2474,5138,0)));
        assertTrue(TzhaarAccess.crossedVent(vent(true),new WorldPoint(2474,5139,0)));
        assertFalse(TzhaarAccess.crossedVent(vent(true),new WorldPoint(2474,5137,0)));
        assertFalse(TzhaarAccess.crossedVent(vent(false),new WorldPoint(2474,5135,1)));
        assertFalse(TzhaarAccess.crossedVent(vent(false),new WorldPoint(2474,5130,0)));
    }
    @Test void walkerCannotReleaseTheTransitionWhileThePlayerStillMovesOrAnimates() throws Exception {
        Client client=mock(Client.class);Player player=mock(Player.class);ClientThread thread=mock(ClientThread.class);
        when(client.getLocalPlayer()).thenReturn(player);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        var method=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedTransportLanded",WorldPoint.class,Transport.class);method.setAccessible(true);
        var destination=new WorldPoint(2474,5135,0);
        try(var dro=mockStatic(Microbot.class);var movement=mockStatic(Rs2Player.class)){
            dro.when(Microbot::getClient).thenReturn(client);dro.when(Microbot::getClientThread).thenReturn(thread);
            when(player.getAnimation()).thenReturn(1234);assertEquals(false,method.invoke(null,destination,vent(false)));
            when(player.getAnimation()).thenReturn(-1);movement.when(Rs2Player::isMoving).thenReturn(true);assertEquals(false,method.invoke(null,destination,vent(false)));
            movement.when(Rs2Player::isMoving).thenReturn(false);assertEquals(true,method.invoke(null,destination,vent(false)));
            when(client.getLocalPlayer()).thenReturn(null);assertEquals(false,method.invoke(null,destination,vent(false)));
        }
    }
}
