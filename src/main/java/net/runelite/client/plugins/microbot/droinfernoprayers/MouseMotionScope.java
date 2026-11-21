package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;

public final class MouseMotionScope implements AutoCloseable
{
    public interface Profile
    {
        MouseMotionFactory motion(MouseMotionFactory source);
        long clickPause();
        boolean isValid();

        default boolean scheduleGesture(Runnable gesture){return false;}

        default boolean shouldClick(){return true;}

        default boolean pressEntry(net.runelite.api.Point point, net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry){return false;}
    }

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicReference<Profile> BACKGROUND = new java.util.concurrent.atomic.AtomicReference<>();
    private static final ThreadLocal<Profile> CAPTURED_BACKGROUND = new ThreadLocal<>();
    private static final ThreadLocal<Double> SPEED = ThreadLocal.withInitial(() -> 1.0);
    private final Profile previous;

    private MouseMotionScope(Profile profile)
    {
        previous = CURRENT.get();
        if (profile == null) CURRENT.remove();
        else CURRENT.set(profile);
    }

    public static MouseMotionScope open(Profile profile) { return new MouseMotionScope(profile); }

    public static AutoCloseable background(Profile profile){
        BACKGROUND.set(java.util.Objects.requireNonNull(profile));
        return ()->BACKGROUND.compareAndSet(profile,null);
    }
    private static Profile background(){Profile captured=CAPTURED_BACKGROUND.get();return captured!=null?captured:BACKGROUND.get();}

    public static SpeedScope speed(double multiplier) { return new SpeedScope(multiplier); }

    public static final class SpeedScope implements AutoCloseable
    {
        private final double previous = SPEED.get();
        private SpeedScope(double multiplier)
        {
            if (!Double.isFinite(multiplier) || multiplier <= 0) throw new IllegalArgumentException("Mouse speed");
            SPEED.set(multiplier);
        }
        @Override public void close() { if (previous == 1.0) SPEED.remove(); else SPEED.set(previous); }
    }

    public static Runnable capture(Runnable task)
    {
        Profile profile = CURRENT.get();
        Profile background=background();
        if (profile == null&&background==null) return MouseMotionVariation.capture(task);
        double speed = SPEED.get();
        return MouseMotionVariation.capture(() -> {
            Profile previous=CAPTURED_BACKGROUND.get();
            if(background!=null)CAPTURED_BACKGROUND.set(background);
            try (SpeedScope rate = speed(speed); MouseMotionScope ignored = open(profile)) { task.run(); }
            finally{if(previous==null)CAPTURED_BACKGROUND.remove();else CAPTURED_BACKGROUND.set(previous);}
        });
    }

    public static BooleanSupplier capture(BooleanSupplier task)
    {
        Profile profile = CURRENT.get();
        Profile background=background();
        if (profile == null&&background==null) return MouseMotionVariation.capture(task);
        double speed = SPEED.get();
        return MouseMotionVariation.capture(() -> {
            Profile previous=CAPTURED_BACKGROUND.get();
            if(background!=null)CAPTURED_BACKGROUND.set(background);
            try (SpeedScope rate = speed(speed); MouseMotionScope ignored = open(profile)) { return task.getAsBoolean(); }
            finally{if(previous==null)CAPTURED_BACKGROUND.remove();else CAPTURED_BACKGROUND.set(previous);}
        });
    }

    public static boolean scheduleGesture(Runnable gesture){
        Profile profile=CURRENT.get();
        return profile!=null&&profile.scheduleGesture(gesture);
    }

    public static boolean pressEntry(net.runelite.api.Point point, net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
    {
        Profile profile = CURRENT.get();
        return profile != null && entry != null && profile.pressEntry(point, entry);
    }

    public static void beforeClick()
    {
        Profile profile = CURRENT.get();
        Profile background=background();
        if (profile == null&&background==null) return;
        checkpoint();
        long pause=(profile==null?0:Math.max(0,profile.clickPause()))+(background==null||background==profile?0:Math.max(0,background.clickPause()));
        try { Thread.sleep(pause); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Cancelled();
        }
        checkpoint();
        if(profile!=null&&!profile.shouldClick())throw new Cancelled();
        checkpoint();
    }

    public static MouseMotionFactory motion(MouseMotionFactory source)
    {
        Profile profile = CURRENT.get();
        Profile background=background();
        if (profile == null&&background==null) return null;
        checkpoint();
        MouseMotionFactory factory = profile==null?source:profile.motion(source);
        if(background!=null&&background!=profile&&factory!=null)factory=background.motion(factory);
        double speed = SPEED.get();
        if (factory == null || speed == 1.0) return factory;

        var original = factory.getNature();
        var copy = new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature();
        copy.setSystemCalls(original.getSystemCalls());
        copy.setMouseInfo(original.getMouseInfo());
        copy.setDeviationProvider(original.getDeviationProvider());
        copy.setNoiseProvider(original.getNoiseProvider());
        copy.setOvershootManager(original.getOvershootManager());
        copy.setTimeToStepsDivider(original.getTimeToStepsDivider());
        copy.setMinSteps(original.getMinSteps());
        copy.setEffectFadeSteps(original.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(original.getReactionTimeBaseMs());
        copy.setReactionTimeVariationMs(original.getReactionTimeVariationMs());
        copy.setSpeedManager(distance -> {
            var flow = original.getSpeedManager().getFlowWithTime(distance);
            return new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair<>(flow.x, Math.max(1L, Math.round(flow.y / speed)));
        });
        return new MouseMotionFactory(copy);
    }

    public static void checkpoint()
    {
        Profile profile = CURRENT.get();
        if (profile != null && (!profile.isValid() || Thread.currentThread().isInterrupted()))
            throw new Cancelled();
        Profile background=background();
        if(background!=null&&(!background.isValid()||Thread.currentThread().isInterrupted()))throw new Cancelled();
    }

    public static final class Cancelled extends RuntimeException
    {
        private Cancelled() { super(null, null, false, false); }
    }

    @Override public void close()
    {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
