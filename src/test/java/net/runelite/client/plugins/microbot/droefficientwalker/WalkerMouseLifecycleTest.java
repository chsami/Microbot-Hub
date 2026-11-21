package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WalkerMouseLifecycleTest {
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);
        final EfficientWalkerConfig config=mock(EfficientWalkerConfig.class,CALLS_REAL_METHODS);
        final EfficientWalker backend=mock(EfficientWalker.class);
        final EfficientWalkerPlugin plugin=new EfficientWalkerPlugin();
        Fixture() throws Exception {
            when(config.useMicrobotMouse()).thenReturn(true);
            set(plugin,"client",client);set(plugin,"config",config);set(plugin,"walker",backend);
            set(plugin,"keyManager",mock(KeyManager.class));set(plugin,"overlayManager",mock(OverlayManager.class));
            set(plugin,"overlay",mock(SceneRouteOverlay.class));set(plugin,"mapOverlay",mock(WorldMapRouteOverlay.class));
            set(plugin,"statusOverlay",mock(EfficientWalkerStatusOverlay.class));
            set(plugin,"worldMapOverlay",mock(WorldMapOverlay.class));set(plugin,"eventBus",mock(EventBus.class));
            set(plugin,"pluginManager",mock(PluginManager.class));set(plugin,"configManager",mock(ConfigManager.class));
            // The same Guice singleton is reused on re-enable; shutdown performs this cleanup.
            doAnswer(i->{WalkerMouseControl.shutdown();return null;}).when(backend).shutdown();
            WalkerMouseControl.configure(config);
        }
        @Override public void close() {plugin.shutDown();WalkerMouseControl.shutdown();PointerState.reset();}
    }
    private static void set(Object instance,String name,Object value) throws Exception {
        Field field=WalkerTestReflection.declaredField(instance.getClass(), name);field.setAccessible(true);field.set(instance,value);
    }
    private static void bindImmediateMouseExecutor() throws Exception {
        ExecutorService executor=mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(i->{((Runnable)i.getArgument(0)).run();return CompletableFuture.completedFuture(null);});
        Field worker=WalkerTestReflection.declaredField(WalkerMouseControl.class, "executor");worker.setAccessible(true);worker.set(null,executor);
    }
    private static void nativeWalkingClick(Point point) throws Exception {
        Class<?> type=Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader");
        var constructor=type.getDeclaredConstructor(ClassLoader.class);constructor.setAccessible(true);
        ClassLoader loader=(ClassLoader)constructor.newInstance(EfficientWalker.class.getClassLoader());
        Class<?> nativeWalker=Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.WalkerEngine",true,loader);
        var click=WalkerTestReflection.declaredMethod(nativeWalker, "clickThroughWalkerMouse",Point.class);click.setAccessible(true);click.invoke(null,point);
    }

    @Test public void reenabledPluginWalkingUsesNaturalMouseAndTrailsAndOffStillUsesLegacyClicks() throws Exception {
        try(Fixture f=new Fixture();var micro=mockStatic(Microbot.class);var arbiter=mockStatic(InputArbiter.class);
            var awt=new AwtEmitterStub();var legacy=mockStatic(CanvasClickDispatcher.class);var minimap=mockStatic(Rs2MiniMap.class)) {
            VirtualMouse mouse=new VirtualMouse();NaturalMouse natural=mock(NaturalMouse.class);
            AtomicReference<java.awt.Point> position=new AtomicReference<>(new java.awt.Point(20,30));
            AtomicLong time=new AtomicLong();
            DefaultMouseMotionNature nature=new DefaultMouseMotionNature();
            nature.setMouseInfo(position::get);
            nature.setSystemCalls(new SystemCalls() {
                public long currentTimeMillis() {return time.get();}
                public void sleep(long ms) {time.addAndGet(ms);}
                public Dimension getScreenSize() {return new Dimension(300,200);}
                public void setMousePosition(int x,int y) {throw new AssertionError("Walking must use its guarded emitter");}
            });
            when(natural.getFactory()).thenReturn(new MouseMotionFactory(nature));
            micro.when(Microbot::getClient).thenReturn(f.client);
            micro.when(Microbot::getMouse).thenReturn(mouse);micro.when(Microbot::getNaturalMouse).thenReturn(natural);
            ClientThread thread=mock(ClientThread.class);
            when(thread.invoke(any(Supplier.class))).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
            micro.when(Microbot::getClientThread).thenReturn(thread);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            Player player=mock(Player.class);when(f.client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
            when(f.client.getCanvasWidth()).thenReturn(300);when(f.client.getCanvasHeight()).thenReturn(200);
            awt.onMove((x,y)->position.set(new java.awt.Point(x,y)));
            f.plugin.startUp();f.plugin.shutDown();f.plugin.startUp();
            bindImmediateMouseExecutor();
            Point target=new Point(240,120);
            nativeWalkingClick(target);
            assertTrue(mouse.getPoints().size()>10, "Re-enabling must preserve the selected mouse mode and intermediate trail");
            assertEquals(target,PointerState.lastBotPoint());assertEquals(target,mouse.getLastClick());
            assertEquals(1,awt.count("click",240,120));
            legacy.verifyNoInteractions();
            when(f.config.useMicrobotMouse()).thenReturn(false);
            ConfigChanged changed=new ConfigChanged();changed.setGroup(EfficientWalkerConfig.GROUP);changed.setKey("useMicrobotMouse");
            f.plugin.onConfigChanged(changed);
            nativeWalkingClick(new Point(245,120));
            legacy.verify(()->CanvasClickDispatcher.clickCanvas(f.client,new Point(245,120)),times(1));
        }
    }

    @Test public void changingTheMouseSelectionRebindsTheCurrentConfigAfterCleanup() throws Exception {
        try(Fixture f=new Fixture()) {
            f.plugin.startUp();WalkerMouseControl.shutdown();
            ConfigChanged changed=new ConfigChanged();changed.setGroup(EfficientWalkerConfig.GROUP);changed.setKey("useMicrobotMouse");
            f.plugin.onConfigChanged(changed);
            Field settings=WalkerTestReflection.declaredField(WalkerMouseControl.class, "config");settings.setAccessible(true);
            assertSame(f.config, settings.get(null), "A selected mode must not leave the walking controller detached");
        }
    }
}
