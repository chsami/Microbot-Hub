package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionScope;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerQueuedMouseContextTest
{
    @AfterEach void cleanup() { WalkerMouseControl.shutdown(); }

    @Test void helperTaskCarriesCallerProfileAndSpeedWhenWalkerProfileIsDisabled()
    {
        WalkerMouseControl.configure(null);
        MouseMotionFactory source = source();
        AtomicReference<MouseMotionFactory> received = new AtomicReference<>();
        Runnable queued;
        try (MouseMotionScope scope = MouseMotionScope.open(identity());
             MouseMotionScope.SpeedScope speed = MouseMotionScope.speed(2))
        {
            queued = WalkerMouseControl.profiledTask(() -> received.set(MouseMotionScope.motion(source)));
        }
        queued.run();
        assertEquals(500L, received.get().getSpeedManager().getFlowWithTime(100).y.longValue());
        assertEquals(1000L, source.getSpeedManager().getFlowWithTime(100).y.longValue());
        assertNull(MouseMotionScope.motion(source));
    }

    @Test void helperExecutorCapturesAtSubmissionAndRestoresWorkerScope()
    {
        WalkerMouseControl.configure(null);
        ExecutorService delegate = mock(ExecutorService.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(call -> { queued.set(call.getArgument(0)); return null; }).when(delegate).execute(any(Runnable.class));
        MouseMotionFactory source = source();
        AtomicReference<MouseMotionFactory> received = new AtomicReference<>();
        try (MouseMotionScope scope = MouseMotionScope.open(identity());
             MouseMotionScope.SpeedScope speed = MouseMotionScope.speed(4))
        {
            WalkerMouseControl.profiledExecutor(delegate).execute(() -> received.set(MouseMotionScope.motion(source)));
        }
        try (MouseMotionScope scope = MouseMotionScope.open(identity());
             MouseMotionScope.SpeedScope speed = MouseMotionScope.speed(2))
        {
            queued.get().run();
            assertEquals(250L, received.get().getSpeedManager().getFlowWithTime(100).y.longValue());
            assertEquals(500L, MouseMotionScope.motion(source).getSpeedManager().getFlowWithTime(100).y.longValue());
        }
        assertNull(MouseMotionScope.motion(source));
    }

    @Test void queuedWalkerProfileStillRejectsConfigurationReplacement()
    {
        EfficientWalkerConfig config = mock(EfficientWalkerConfig.class);
        when(config.useMicrobotMouse()).thenReturn(true);
        WalkerMouseControl.configure(config);
        Runnable queued = WalkerMouseControl.profiledTask(MouseMotionScope::checkpoint);
        WalkerMouseControl.configure(null);
        assertThrows(MouseMotionScope.Cancelled.class, queued::run);
        assertNull(MouseMotionScope.motion(source()));
    }

    @Test void capturedProfileValidityIsCheckedWhenDeferredWorkRuns()
    {
        AtomicBoolean valid = new AtomicBoolean(true);
        Runnable queued;
        try (MouseMotionScope scope = MouseMotionScope.open(new MouseMotionScope.Profile()
        {
            public MouseMotionFactory motion(MouseMotionFactory source) { return source; }
            public long clickPause() { return 0; }
            public boolean isValid() { return valid.get(); }
        }))
        {
            queued = MouseMotionScope.capture((Runnable) MouseMotionScope::checkpoint);
        }
        valid.set(false);
        assertThrows(MouseMotionScope.Cancelled.class, queued::run);
        assertNull(MouseMotionScope.motion(source()));
    }

    @Test void throwingTaskRestoresCapturedProfileAndSpeed()
    {
        Runnable queued;
        try (MouseMotionScope scope = MouseMotionScope.open(identity());
             MouseMotionScope.SpeedScope speed = MouseMotionScope.speed(3))
        {
            queued = WalkerMouseControl.profiledTask(() -> { throw new IllegalStateException("test"); });
        }
        assertThrows(IllegalStateException.class, queued::run);
        try (MouseMotionScope scope = MouseMotionScope.open(identity()))
        {
            assertEquals(1000L, MouseMotionScope.motion(source()).getSpeedManager().getFlowWithTime(100).y.longValue());
        }
    }

    private static MouseMotionScope.Profile identity()
    {
        return new MouseMotionScope.Profile()
        {
            public MouseMotionFactory motion(MouseMotionFactory source) { return source; }
            public long clickPause() { return 0; }
            public boolean isValid() { return true; }
        };
    }

    private static MouseMotionFactory source()
    {
        DefaultMouseMotionNature nature = new DefaultMouseMotionNature();
        nature.setSpeedManager(distance -> new Pair<>(new Flow(new double[]{1}), 1000L));
        return new MouseMotionFactory(nature);
    }
}
