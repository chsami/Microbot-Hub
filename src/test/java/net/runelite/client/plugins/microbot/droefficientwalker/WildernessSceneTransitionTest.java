package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.EnumSet;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WildernessSceneTransitionTest {
    @Test void despawnedBoatPlayersCannotBreakTheNextTickAndValidThreatsAreStillDetected() {
        try(var f=new Fixture()) {
            Player stale=mock(Player.class),valid=mock(Player.class);
            when(stale.getWorldLocation()).thenThrow(new NullPointerException("Removed boat world-view"));
            when(valid.getWorldView()).thenReturn(mock(WorldView.class));
            when(valid.getWorldLocation()).thenReturn(new WorldPoint(3201,3600,0));
            when(valid.getCombatLevel()).thenReturn(90);when(valid.getName()).thenReturn("Nearby player");when(valid.getId()).thenReturn(7);
            var staleModel=mock(Rs2PlayerModel.class);when(staleModel.getPlayer()).thenReturn(stale);
            var validModel=mock(Rs2PlayerModel.class);when(validModel.getPlayer()).thenReturn(valid);
            when(f.cache.getStream()).thenReturn(Stream.of(staleModel,validModel));
            var result=assertDoesNotThrow(()->WildernessThreatPolicy.evaluate(f.client,32,5));
            assertTrue(result.isAttackCapablePlayerNearby());assertEquals(java.util.Set.of(7),result.threatPlayers());
            verify(stale,never()).getWorldLocation();
        }
    }

    @Test void aLocalPlayerWhoseWorldViewIsNotReadyWaitsForTheNextTick() {
        try(var f=new Fixture()) {
            when(f.local.getWorldView()).thenReturn(null);
            when(f.local.getWorldLocation()).thenThrow(new NullPointerException("Scene transition"));
            assertFalse(assertDoesNotThrow(()->WildernessThreatPolicy.evaluate(f.client,32,5)).hasThreat());
            verify(f.local,never()).getWorldLocation();verifyNoInteractions(f.cache);
        }
    }

    private static class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);final Player local=mock(Player.class);
        final Rs2PlayerCache cache=mock(Rs2PlayerCache.class);
        final org.mockito.MockedStatic<Microbot> micro=mockStatic(Microbot.class);
        Fixture(){
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(local);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(local.getWorldView()).thenReturn(mock(WorldView.class));when(local.getWorldLocation()).thenReturn(new WorldPoint(3200,3600,0));
            when(local.getCombatLevel()).thenReturn(90);micro.when(Microbot::getRs2PlayerCache).thenReturn(cache);
        }
        public void close(){micro.close();}
    }
}
