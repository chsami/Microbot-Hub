package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Timer;
import net.runelite.api.Point;
import net.runelite.client.plugins.devtools.DevToolsButton;
import net.runelite.client.plugins.devtools.DevToolsPlugin;
import net.runelite.client.plugins.devtools.MicrobotMouseOverlay;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests the full existing motion-to-trail-to-render path, not just cursor position. */
public class WalkerMouseTrailRegressionTest {
    private static void step(InputLoop.Emit emit,int x,int y) {
        try {
            Method move=WalkerMouseControl.class.getDeclaredMethod("moveWithTrail",InputLoop.Emit.class,int.class,int.class);
            move.setAccessible(true);move.invoke(null,emit,x,y);
        } catch(java.lang.reflect.InvocationTargetException ex) {
            if(ex.getCause() instanceof RuntimeException) throw (RuntimeException)ex.getCause();
            throw new AssertionError(ex.getCause());
        } catch(ReflectiveOperationException ex) {throw new AssertionError(ex);}
    }

    @Test public void walkingTrailIsVisibleOnTheFrameReceivingMovement() throws Exception {
        VirtualMouse mouse=new VirtualMouse();Timer timer=mock(Timer.class);
        Field fade=Mouse.class.getDeclaredField("timer");fade.setAccessible(true);fade.set(mouse,timer);
        when(timer.isRunning()).thenReturn(true);
        DevToolsPlugin plugin=mock(DevToolsPlugin.class);DevToolsButton button=mock(DevToolsButton.class);
        when(plugin.getMouseMovement()).thenReturn(button);when(button.isActive()).thenReturn(true);
        MicrobotMouseOverlay overlay=mock(MicrobotMouseOverlay.class,CALLS_REAL_METHODS);
        Field owner=MicrobotMouseOverlay.class.getDeclaredField("plugin");owner.setAccessible(true);owner.set(overlay,plugin);
        try(var micro=mockStatic(Microbot.class);var arbiter=mockStatic(InputArbiter.class);
            var awt=mockStatic(AwtEmitter.class)) {
            micro.when(Microbot::getMouse).thenReturn(mouse);arbiter.when(InputArbiter::isHuman).thenReturn(false);
            awt.when(()->AwtEmitter.moved(anyInt(),anyInt())).thenAnswer(i->{
                int x=i.getArgument(0),y=i.getArgument(1);PointerState.setFromBot(x,y);
                BufferedImage frame=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);
                var g=frame.createGraphics();
                try {overlay.render(g);} finally {g.dispose();}
                if(x==220) {
                    assertTrue((frame.getRGB(150,50)>>>24)>0, "A walking trail must be visible during the move, away from the cursor");
                }
                return null;
            });
            assertEquals(InputLoop.Result.COMPLETED,InputLoop.run(emit->{step(emit,80,50);step(emit,220,50);}));
            awt.verify(()->AwtEmitter.moved(80,50),times(1));awt.verify(()->AwtEmitter.moved(220,50),times(1));
            assertEquals(2,mouse.getPoints().size());
        } finally {PointerState.reset();}
    }

    @Test public void humanTakeoverCannotRecordOrEmitAnotherWalkingStep() {
        VirtualMouse mouse=new VirtualMouse();java.util.concurrent.atomic.AtomicBoolean human=new java.util.concurrent.atomic.AtomicBoolean();
        try(var micro=mockStatic(Microbot.class);var arbiter=mockStatic(InputArbiter.class);var awt=new AwtEmitterStub()) {
            micro.when(Microbot::getMouse).thenReturn(mouse);arbiter.when(InputArbiter::isHuman).thenAnswer(i->human.get());
            assertEquals(InputLoop.Result.ABORTED,InputLoop.run(emit->{human.set(true);step(emit,80,50);}));
            assertTrue(mouse.getPoints().isEmpty());assertFalse(awt.sentAnything());
        }
    }

    @Test public void naturalTrajectoryFeedsTheExistingOverlayWithoutChangingItsFactory() throws Exception {
        VirtualMouse mouse=new VirtualMouse();
        NaturalMouse natural=mock(NaturalMouse.class);
        AtomicReference<java.awt.Point> position=new AtomicReference<>(new java.awt.Point(20,30));
        AtomicLong time=new AtomicLong();
        SystemCalls calls=new SystemCalls() {
            public long currentTimeMillis() {return time.get();}
            public void sleep(long ms) {time.addAndGet(ms);}
            public Dimension getScreenSize() {return new Dimension(300,200);}
            public void setMousePosition(int x,int y) {throw new AssertionError("Trajectory must use Walker's guarded emitter");}
        };
        DefaultMouseMotionNature nature=new DefaultMouseMotionNature();
        nature.setSystemCalls(calls);nature.setMouseInfo(position::get);
        MouseMotionFactory factory=new MouseMotionFactory(nature);
        when(natural.getFactory()).thenReturn(factory);
        var speed=nature.getSpeedManager();var overshoot=nature.getOvershootManager();
        try(var micro=mockStatic(Microbot.class);var arbiter=mockStatic(InputArbiter.class);
            var awt=mockStatic(AwtEmitter.class)) {
            micro.when(Microbot::getMouse).thenReturn(mouse);
            micro.when(Microbot::getNaturalMouse).thenReturn(natural);
            arbiter.when(InputArbiter::isHuman).thenReturn(false);
            awt.when(()->AwtEmitter.moved(anyInt(),anyInt())).thenAnswer(i->{
                int x=i.getArgument(0),y=i.getArgument(1);
                position.set(new java.awt.Point(x,y));PointerState.setFromBot(x,y);return null;
            });
            Field generation=WalkerMouseControl.class.getDeclaredField("generation");generation.setAccessible(true);
            long request=((AtomicLong)generation.get(null)).get();
            Method move=WalkerMouseControl.class.getDeclaredMethod("move",Point.class,boolean.class,long.class,InputLoop.Emit.class,boolean.class);
            move.setAccessible(true);
            assertEquals(InputLoop.Result.COMPLETED,InputLoop.run(emit->{
                try {move.invoke(null,new Point(240,120),true,request,emit,false);}
                catch(ReflectiveOperationException e) {throw new AssertionError(e);}
            }));
            assertTrue(mouse.getPoints().size()>10, "Natural motion must retain intermediate trail points");
            assertEquals(new Point(240,120),mouse.getPoints().peekLast());
            assertSame(calls,nature.getSystemCalls());assertSame(speed,nature.getSpeedManager());assertSame(overshoot,nature.getOvershootManager());
            Mouse rendered=mock(Mouse.class);Timer timer=mock(Timer.class);
            when(rendered.getPoints()).thenReturn(mouse.getPoints());when(rendered.getTimer()).thenReturn(timer);
            when(timer.isRunning()).thenReturn(true);when(rendered.getRainbowColor()).thenReturn(Color.BLUE);
            micro.when(Microbot::getMouse).thenReturn(rendered);
            DevToolsPlugin plugin=mock(DevToolsPlugin.class);DevToolsButton button=mock(DevToolsButton.class);
            when(plugin.getMouseMovement()).thenReturn(button);when(button.isActive()).thenReturn(true);
            MicrobotMouseOverlay overlay=mock(MicrobotMouseOverlay.class,CALLS_REAL_METHODS);
            Field owner=MicrobotMouseOverlay.class.getDeclaredField("plugin");owner.setAccessible(true);owner.set(overlay,plugin);
            BufferedImage image=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);
            var graphics=image.createGraphics();
            try {overlay.render(graphics);} finally {graphics.dispose();}
            int trailPixels=0;
            for(int x=0;x<210;x++) for(int y=0;y<200;y++) if((image.getRGB(x,y)>>>24)>0)trailPixels++;
            assertTrue(trailPixels>50, "The path must be drawn away from the cursor sprite");
        } finally {PointerState.reset();}
    }
}
