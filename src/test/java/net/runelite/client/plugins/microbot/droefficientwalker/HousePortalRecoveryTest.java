package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise the existing facade/coordinator, including the normal planner handoff. */
public class HousePortalRecoveryTest {
    private static final WorldPoint OUTSIDE = HouseLocation.PRIFDDINAS.getPortalLocation();
    private static final WorldPoint TARGET = OUTSIDE.dx(2);

    @Test public void previewHasNoInputAndDuplicatesDoNotRestartAnExit() throws Exception {
        try(Fixture f=new Fixture()) {
            assertTrue(f.walker.previewRouteTo(TARGET));
            assertEquals(EfficientWalker.Status.PREVIEW,f.walker.getStatus());
            assertEquals(TARGET,f.walker.getDestination());
            f.tick();f.tick();
            assertEquals(0,f.exits.get());
            assertTrue(f.walker.commitPreviewOrStartRoute(TARGET));
            assertEquals(EfficientWalker.Status.WALKING,f.walker.getStatus());
            f.tick();
            assertEquals(1,f.exits.get());
            for(int i=0;i<4;i++) {
                assertTrue(f.walker.walkTo(TARGET));
                f.tick();
            }
            assertEquals(1,f.exits.get());
            f.walker.cancel();f.tick();
            assertEquals(EfficientWalker.Status.IDLE,f.walker.getStatus());
            assertNull(f.walker.getDestination());
            assertEquals(1,f.exits.get());
        }
    }


    @Test public void failedExitRetriesAreSpacedBoundedAndWorldChangesStopThem() throws Exception {
        try(Fixture f=new Fixture()) {
            assertTrue(f.walker.walkTo(TARGET));
            f.tick();assertEquals(1,f.exits.get());
            f.clock.set(6);f.tick();assertEquals(1,f.exits.get());
            f.clock.set(12);f.tick();assertEquals(2,f.exits.get());
            f.clock.set(20);f.tick();assertEquals(2,f.exits.get());
            f.clock.set(30);f.tick();assertEquals(3,f.exits.get());
            f.clock.set(40);f.tick();assertEquals(4,f.exits.get());
            f.clock.set(50);f.tick();assertEquals(5,f.exits.get());
            f.clock.set(60);f.tick();assertEquals(6,f.exits.get());
            f.clock.set(70);f.tick();assertEquals(6,f.exits.get());
            f.clock.set(109);f.tick();assertEquals("ESCAPING_INSTANCE",f.state());
            f.clock.set(112);f.tick();
            assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus());
            assertEquals("IDLE",f.state());
            f.walker.cancel();
            assertTrue(f.walker.walkTo(TARGET));
            when(f.client.getWorld()).thenReturn(302);
            f.tick();assertEquals(6,f.exits.get());
            assertEquals(EfficientWalker.Status.BLOCKED,f.walker.getStatus());
        }
    }

    @Test public void maxPohOffDoesNotInterceptTheExistingPreview() throws Exception {
        try(Fixture f=new Fixture()) {
            f.house.when(MaxPohSupport::enabled).thenReturn(false);
            f.raw.set(OUTSIDE);f.cached.set(OUTSIDE);f.inside.set(false);
            assertTrue(f.walker.previewRouteTo(TARGET));
            assertEquals("IDLE",f.state());
            assertEquals(0,f.exits.get());
        }
    }

    private static Object field(Object object,String name) throws Exception {
        Field field=WalkerTestReflection.declaredField(object.getClass(), name);field.setAccessible(true);return field.get(object);
    }

    static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);
        final AtomicBoolean inside=new AtomicBoolean(true);
        final AtomicInteger clock=new AtomicInteger(1),exits=new AtomicInteger();
        final AtomicReference<WorldPoint> raw=new AtomicReference<>(),cached=new AtomicReference<>();
        final MockedStatic<Microbot> micro=mockStatic(Microbot.class);
        final MockedStatic<Rs2Player> playerApi=mockStatic(Rs2Player.class);
        final MockedStatic<MaxPohSupport> house=mockStatic(MaxPohSupport.class,CALLS_REAL_METHODS);
        final EfficientWalker walker;
        final Object coordinator;
        final Method tick;

        Fixture() throws Exception {
            Player player=mock(Player.class);WorldView view=mock(WorldView.class);
            Scene scene=mock(Scene.class);CollisionData collision=mock(CollisionData.class);
            when(client.isClientThread()).thenReturn(true);
            when(client.getWorld()).thenReturn(301);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            when(client.getTickCount()).thenAnswer(i->clock.get());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(view);
            when(player.getWorldLocation()).thenAnswer(i->raw.get());
            when(player.getWorldView()).thenReturn(view);
            when(view.isTopLevel()).thenReturn(true);when(view.getId()).thenReturn(WorldView.TOPLEVEL);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);
            when(view.getBaseX()).thenReturn(OUTSIDE.getX()-50);when(view.getBaseY()).thenReturn(OUTSIDE.getY()-50);
            when(view.getScene()).thenReturn(scene);
            when(scene.getTiles()).thenReturn(new Tile[1][104][104]);
            when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
            when(collision.getFlags()).thenReturn(new int[104][104]);
            playerApi.when(Rs2Player::getWorldLocation).thenAnswer(i->cached.get());
            micro.when(Microbot::getClient).thenReturn(client);
            ClientThread clientThread=mock(ClientThread.class);
            when(clientThread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
            when(clientThread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            micro.when(Microbot::getClientThread).thenReturn(clientThread);
            house.when(MaxPohSupport::enabled).thenReturn(true);
            house.when(MaxPohSupport::inHouse).thenAnswer(i->inside.get());
            house.when(MaxPohSupport::insideHouse).thenAnswer(i->inside.get());
            house.when(MaxPohSupport::currentHouse).thenReturn(HouseLocation.PRIFDDINAS);
            house.when(MaxPohSupport::refresh).thenAnswer(i->null);
            house.when(MaxPohSupport::leaveHouse).thenAnswer(i->{exits.incrementAndGet();return false;});
            EfficientWalkerConfig config=mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);
            when(config.useMaxPoh()).thenReturn(true);when(config.useItemsFromBank()).thenReturn(false);
            walker=new EfficientWalker(client,config,TestRouteSubmissions.immediate());
            coordinator=field(walker,"bankTeleportBootstrap");
            tick=WalkerTestReflection.declaredMethod(coordinator.getClass(), "beforeEngineTick");tick.setAccessible(true);
            ExecutorService executor=mock(ExecutorService.class);
            when(executor.submit(any(Callable.class))).thenAnswer(i->{
                FutureTask<Object> task=new FutureTask<>((Callable<Object>)i.getArgument(0));task.run();return task;
            });
            Field worker=WalkerTestReflection.declaredField(coordinator.getClass(), "bankExecutor");worker.setAccessible(true);
            ((ExecutorService)worker.get(coordinator)).shutdownNow();worker.set(coordinator,executor);
        }

        void tick() throws Exception {tick.invoke(coordinator);}
        String state() throws Exception {return field(coordinator,"state").toString();}
        @Override public void close() {
            walker.shutdown();house.close();playerApi.close();micro.close();
        }
    }
}
