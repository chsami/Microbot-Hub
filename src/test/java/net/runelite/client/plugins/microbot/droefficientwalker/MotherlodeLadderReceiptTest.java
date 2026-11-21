package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotherlodeLadderReceiptTest {
    @Test void descentRequiresAnObservedSettledLowerLandingWithLiveBankAccess() throws Exception {
        Client client=mock(Client.class);Player player=mock(Player.class);ClientThread thread=mock(ClientThread.class);
        when(client.getLocalPlayer()).thenReturn(player);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(player.getAnimation()).thenReturn(-1);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        var descent=new Transport(new WorldPoint(3755,5675,0),new WorldPoint(3754,5672,0),
            "MLM ladder",TransportType.TRANSPORT,true,"Climb-down","Ladder",ObjectID.MOTHERLODE_LADDER_TOP);
        Method landed=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "sharedTransportLanded",WorldPoint.class,Transport.class);
        landed.setAccessible(true);
        try(var micro=mockStatic(Microbot.class);var movement=mockStatic(Rs2Player.class);var tiles=mockStatic(Rs2Tile.class)) {
            micro.when(Microbot::getClient).thenReturn(client);micro.when(Microbot::getClientThread).thenReturn(thread);
            var reachable=new HashMap<WorldPoint,Integer>();
            tiles.when(()->Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class),eq(12))).thenReturn(reachable);
            for(var upper:new WorldPoint[]{new WorldPoint(3754,5677,0),new WorldPoint(3755,5675,0),new WorldPoint(3756,5674,0)}) {
                when(player.getWorldLocation()).thenReturn(upper);
                assertEquals(false,landed.invoke(null,upper,descent),"Nearby upper tiles must not confirm descent");
            }
            WorldPoint lower=new WorldPoint(3755,5672,0);
            when(player.getWorldLocation()).thenReturn(lower);
            assertEquals(false,landed.invoke(null,lower,descent),"A lower-looking tile without collision evidence is insufficient");
            reachable.put(MotherlodeTransports.BANK_APPROACH,8);
            assertEquals(true,landed.invoke(null,lower,descent),"The game may land beside the graph endpoint");
            movement.when(Rs2Player::isMoving).thenReturn(true);
            assertEquals(false,landed.invoke(null,lower,descent));
            movement.when(Rs2Player::isMoving).thenReturn(false);when(player.getAnimation()).thenReturn(828);
            assertEquals(false,landed.invoke(null,lower,descent));
            when(player.getAnimation()).thenReturn(-1);when(client.getGameState()).thenReturn(GameState.HOPPING);
            assertEquals(false,landed.invoke(null,lower,descent));
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(null);
            assertEquals(false,landed.invoke(null,lower,descent));
            when(client.getLocalPlayer()).thenReturn(player);micro.when(Microbot::getClientThread).thenReturn(null);
            assertEquals(false,landed.invoke(null,lower,descent));
        }
    }
}
