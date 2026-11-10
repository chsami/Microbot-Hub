package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.drocolosseumprayers.MouseMotionScope;

/** Arena-only click routing over Main's existing caller-owned NaturalMouse scope. */
public final class ArenaInputScope implements AutoCloseable
{
    public interface Profile extends MouseMotionScope.Profile
    {
        default boolean immediateTabs() { return false; }
        default ClickHandler mouse() { return null; }
    }

    @FunctionalInterface
    public interface ClickHandler
    {
        void click(Point point, boolean rightClick, NewMenuEntry entry);
        /** Several left clicks meant for the same tick, in order (a gear switch). */
        default void burst(java.util.List<Point> points, java.util.List<NewMenuEntry> entries)
        {
            for (int i = 0; i < entries.size(); i++) click(points.get(i), false, entries.get(i));
        }
    }

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();
    private final Profile previous;
    private final MouseMotionScope nativeScope;

    private ArenaInputScope(Profile profile)
    {
        previous = CURRENT.get();
        if (profile == null) CURRENT.remove(); else CURRENT.set(profile);
        nativeScope = MouseMotionScope.open(profile);
    }

    public static ArenaInputScope open(Profile profile) { return new ArenaInputScope(profile); }
    public static boolean immediateTabs() { Profile profile = CURRENT.get(); return profile != null && profile.immediateTabs(); }
    public static boolean hasCustomMouse() { Profile profile = CURRENT.get(); return profile != null && profile.mouse() != null; }

    public static boolean customClick(Point point, boolean rightClick, NewMenuEntry entry)
    {
        Profile profile = CURRENT.get();
        ClickHandler handler = profile == null ? null : profile.mouse();
        if (handler == null) return false;
        handler.click(point, rightClick, entry);
        return true;
    }

    public static boolean customBurst(java.util.List<Point> points, java.util.List<NewMenuEntry> entries)
    {
        Profile profile = CURRENT.get();
        ClickHandler handler = profile == null ? null : profile.mouse();
        if (handler == null) return false;
        handler.burst(points, entries);
        return true;
    }

    public static Runnable capture(Runnable task)
    {
        Profile profile = CURRENT.get();
        Runnable observed = ArenaInputDiagnostics.capture(task);
        if (profile == null) return MouseMotionScope.capture(observed);
        return MouseMotionScope.capture(() -> {
            try (var ignored = open(profile)) { observed.run(); }
        });
    }

    public static BooleanSupplier capture(BooleanSupplier task)
    {
        Profile profile = CURRENT.get();
        BooleanSupplier observed = ArenaInputDiagnostics.capture(task);
        if (profile == null) return MouseMotionScope.capture(observed);
        return MouseMotionScope.capture(() -> {
            try (var ignored = open(profile)) { return observed.getAsBoolean(); }
        });
    }

    /** Main captures its native scope automatically; this also carries the local click mode and trace. */
    public static <T> Optional<T> onClientThread(Callable<T> task)
    {
        Profile profile = CURRENT.get();
        ArenaInputDiagnostics.Trace trace = ArenaInputDiagnostics.current();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try (var scope = profile == null ? null : open(profile); var diagnostic = ArenaInputDiagnostics.open(trace)) { return task.call(); }
        });
    }

    public static void checkpoint() { MouseMotionScope.checkpoint(); }
    public static void beforeClick() { MouseMotionScope.beforeClick(); }
    public static boolean scheduleGesture(Runnable gesture) { return MouseMotionScope.scheduleGesture(gesture); }

    @Override public void close()
    {
        nativeScope.close();
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
