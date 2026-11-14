package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TirannwnForestEgressTest {
    private static final WorldPoint LLETYA=new WorldPoint(2345,3163,0);
    private static final WorldPoint EXIT=new WorldPoint(2274,3192,0);


    @Test void intermediateDenseTreesAndTheirAnimationsRetainTheExistingTraversalOwner() throws Exception {
        try(var f=new Fixture()) {
            set(f.support,"lastLocation",new WorldPoint(2265,3192,0));
            f.here.set(new WorldPoint(2268,3192,0));
            assertFalse(observe(f));verify(f.walker,never()).resumeCommittedTirannwnWalk(any());
            set(f.support,"lastLocation",new WorldPoint(2271,3192,0));f.here.set(EXIT);
            f.playerState.when(Rs2Player::isMoving).thenReturn(true);
            assertFalse(observe(f));verify(f.walker,never()).resumeCommittedTirannwnWalk(any());
            f.playerState.when(Rs2Player::isMoving).thenReturn(false);
            assertTrue(observe(f));verify(f.walker).resumeCommittedTirannwnWalk(LLETYA);
        }
    }


    @Test void unrelatedMovementAndAChangedDestinationDoNotClaimAForestExit() throws Exception {
        try(var f=new Fixture()) {
            set(f.support,"lastLocation",new WorldPoint(2273,3191,0));
            f.here.set(new WorldPoint(2275,3191,0));assertFalse(observe(f));
            verify(f.walker,never()).resumeCommittedTirannwnWalk(any());
            set(f.support,"lastLocation",new WorldPoint(2271,3192,0));f.here.set(EXIT);
            f.playerState.when(Rs2Player::isMoving).thenReturn(true);assertFalse(observe(f));
            when(f.walker.getLogicalDestination()).thenReturn(new WorldPoint(2200,3200,0));
            f.playerState.when(Rs2Player::isMoving).thenReturn(false);
            set(f.support,"lastLocation",EXIT);assertFalse(observe(f));
            verify(f.walker,never()).resumeCommittedTirannwnWalk(any());
        }
    }

    private static boolean observe(Fixture f)throws Exception {
        var method=WalkerTestReflection.declaredMethod(TirannwnObstacleSupport.class, "observeNativeForestExit",WorldPoint.class,WorldPoint.class,Player.class);
        method.setAccessible(true);return (Boolean)method.invoke(f.support,f.here.get(),f.walker.getLogicalDestination(),f.player);
    }
    private static void set(Object o,String n,Object v)throws Exception {Field f=WalkerTestReflection.declaredField(o.getClass(), n);f.setAccessible(true);f.set(o,v);}
    private static Object get(Object o,String n)throws Exception {Field f=WalkerTestReflection.declaredField(o.getClass(), n);f.setAccessible(true);return f.get(o);}

    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);final Player player=mock(Player.class);
        final EfficientWalker walker=mock(EfficientWalker.class);
        final AtomicReference<WorldPoint> here=new AtomicReference<>(EXIT),engineTarget=new AtomicReference<>(LLETYA);
        final MockedStatic<Rs2Player> playerState=mockStatic(Rs2Player.class);
        final MockedStatic<SceneObjects> objects=mockStatic(SceneObjects.class);
        final MockedStatic<Microbot> micro=mockStatic(Microbot.class);
        final TirannwnObstacleSupport support;
        Fixture() {
            when(client.getLocalPlayer()).thenReturn(player);when(client.getTickCount()).thenReturn(100);
            when(player.getWorldLocation()).thenAnswer(i->here.get());when(player.getAnimation()).thenReturn(-1);
            when(walker.getLogicalDestination()).thenReturn(LLETYA);when(walker.getDestination()).thenAnswer(i->engineTarget.get());
            when(walker.getStatus()).thenReturn(EfficientWalker.Status.WALKING);
            when(walker.resumeCommittedTirannwnWalk(any())).thenAnswer(i->{engineTarget.set(i.getArgument(0));return true;});
            when(walker.walkCommittedTirannwnWaypoint(any(),eq(LLETYA))).thenAnswer(i->{engineTarget.set(i.getArgument(0));return true;});
            when(walker.getTransportEndpoints()).thenReturn(Set.of(new WorldPoint(2284,3188,0)));
            support=new TirannwnObstacleSupport(client,walker);
        }
        void nativeExit()throws Exception {
            set(support,"lastLocation",new WorldPoint(2271,3192,0));
            assertTrue(support.tick());verify(walker).resumeCommittedTirannwnWalk(LLETYA);
        }
        void pathToWire() {
            List<WorldPoint> route=new ArrayList<>();route.add(EXIT);
            for(int x=2275;x<=2304;x++)route.add(new WorldPoint(x,Math.max(3188,3192-(x-2274)),0));
            when(walker.getRemainingRoute()).thenReturn(route);
        }
        @Override public void close(){micro.close();objects.close();playerState.close();}
    }
}
