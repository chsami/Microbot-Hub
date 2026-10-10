package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotion;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultOvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider;

/** Owned worker gestures. Callers capture fresh target geometry before passing a nonblocking guard. */
final class GeflipperMouseMotion {
    enum Action { MOVE, CLICK, INVOKE, OFFSCREEN }

    interface Sink {
        void checkpoint();
        void move(int x, int y);
        void press(int x, int y);
        void release(int x, int y);
        void click(int x, int y);
    }

    interface Backend {
        boolean worker();
        boolean human();
        Snapshot snapshot();
        Point position();
        boolean gesture(Consumer<Sink> action);
        void delegate(Action action, Point point, NewMenuEntry entry);
        MenuEntry pendingMenu();
        void pendingMenu(MenuEntry entry);
        void clicked(Point point);
        long millis();
        void sleep(long millis) throws InterruptedException;
    }

    interface Motion {
        void move(MouseMotionNature nature, Random random, int x, int y) throws InterruptedException;
    }

    static final class Snapshot {
        final int width;
        final int height;
        final MouseMotionNature nature;

        Snapshot(int width, int height, MouseMotionNature nature) {
            this.width = width;
            this.height = height;
            this.nature = nature;
        }
    }

    private static final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Backend backend;
    private final WaitingMouse.RandomInt speedRandom;
    private final Random pathRandom;
    private final Motion motion;

    GeflipperMouseMotion() {
        this(new VirtualBackend(), (minimum, maximum) ->
            ThreadLocalRandom.current().nextInt(minimum, maximum + 1), new Random(),
            (nature, random, x, y) -> new MouseMotion(nature, random, x, y).move());
    }

    GeflipperMouseMotion(Backend backend, WaitingMouse.RandomInt speedRandom, Random pathRandom, Motion motion) {
        this.backend = Objects.requireNonNull(backend);
        this.speedRandom = Objects.requireNonNull(speedRandom);
        this.pathRandom = Objects.requireNonNull(pathRandom);
        this.motion = Objects.requireNonNull(motion);
    }

    boolean move(Point point, boolean varySpeed, BooleanSupplier guard) {
        return perform(Action.MOVE, point, null, varySpeed, guard);
    }

    boolean click(Point point, boolean varySpeed, BooleanSupplier guard) {
        return perform(Action.CLICK, point, null, varySpeed, guard);
    }

    boolean invoke(NewMenuEntry entry, Point point, boolean varySpeed, BooleanSupplier guard) {
        return entry != null && perform(Action.INVOKE, point, entry, varySpeed, guard);
    }

    boolean offscreen(Point point, boolean varySpeed, BooleanSupplier guard) {
        return perform(Action.OFFSCREEN, point, null, varySpeed, guard);
    }

    private boolean perform(Action action, Point point, NewMenuEntry entry, boolean varySpeed, BooleanSupplier guard) {
        boolean[] published = {false};
        boolean completed = false;
        try {
            if (point == null || guard == null || !allowed(guard) || backend.pendingMenu() != null) return false;
            if (!varySpeed) {
                backend.delegate(action, point, entry);
                return true;
            }
            // All client-thread reads and policy construction happen before taking the input lock.
            Snapshot snapshot = backend.snapshot();
            if (!validTarget(snapshot, point, action == Action.OFFSCREEN)) return false;
            MouseMotionNature source = snapshot.nature;
            if (source.getNoiseProvider() == null || source.getDeviationProvider() == null
                || source.getOvershootManager() == null || source.getSpeedManager() == null) return false;
            if (source.getNoiseProvider().getClass() != DefaultNoiseProvider.class
                || source.getDeviationProvider().getClass() != SinusoidalDeviationProvider.class
                || source.getOvershootManager().getClass() != DefaultOvershootManager.class) {
                // Unknown policies retain their usual SDK movement and its usual cancellation limits.
                if (!allowed(guard) || backend.pendingMenu() != null) return false;
                backend.delegate(action, point, entry);
                return true;
            }
            MouseMotionNature nature = privateNature(snapshot.nature);
            if (nature == null || !allowed(guard)) return false;
            completed = backend.gesture(sink -> {
                check(guard, sink);
                if (backend.pendingMenu() != null) throw new Cancelled();
                Point initial = backend.position();
                if (initial == null) throw new Cancelled();
                // Offscreen paths get padding; ordinary targets retain the SDK's canvas clipping.
                int padding = action == Action.OFFSCREEN ? 2 : 0;
                java.awt.Point[] logical = {new java.awt.Point(initial.getX() + padding, initial.getY() + padding)};
                nature.setMouseInfo(() -> {
                    check(guard, sink);
                    return new java.awt.Point(logical[0]);
                });
                nature.setSystemCalls(new SystemCalls() {
                    public long currentTimeMillis() { check(guard, sink); return backend.millis(); }
                    public Dimension getScreenSize() { return new Dimension(snapshot.width + padding * 2, snapshot.height + padding * 2); }
                    public void setMousePosition(int x, int y) {
                        check(guard, sink);
                        sink.move(x - padding, y - padding);
                        // The SDK does not update PointerState for repeated already-outside emissions.
                        logical[0] = new java.awt.Point(x, y);
                    }
                    public void sleep(long millis) { pause(millis, guard, sink); }
                });
                try {
                    motion.move(nature, pathRandom, point.getX() + padding, point.getY() + padding);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new Cancelled();
                }
                check(guard, sink);
                if (action == Action.MOVE || action == Action.OFFSCREEN) return;
                if (backend.pendingMenu() != null) throw new Cancelled();
                if (entry != null) {
                    backend.pendingMenu(entry);
                    published[0] = true;
                }
                check(guard, sink);
                sink.press(point.getX(), point.getY());
                check(guard, sink);
                sink.release(point.getX(), point.getY());
                check(guard, sink);
                sink.click(point.getX(), point.getY());
                backend.clicked(point);
            });
            return completed;
        } catch (RuntimeException unavailableOrCancelled) {
            return false;
        } finally {
            // Success leaves the normal SDK menu entry for the client hook to consume.
            if (!completed && published[0] && backend.pendingMenu() == entry) backend.pendingMenu(null);
        }
    }

    private boolean allowed(BooleanSupplier guard) {
        return backend.worker() && !Thread.currentThread().isInterrupted() && !backend.human() && guard.getAsBoolean();
    }

    private void check(BooleanSupplier guard, Sink sink) {
        if (!allowed(guard)) throw new Cancelled();
        sink.checkpoint();
    }

    private void pause(long millis, BooleanSupplier guard, Sink sink) {
        check(guard, sink);
        long remaining = Math.max(0, millis);
        while (remaining > 0) {
            long slice = Math.min(remaining, 25);
            try {
                backend.sleep(slice);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new Cancelled();
            }
            remaining -= slice;
            check(guard, sink);
        }
    }

    private static boolean validTarget(Snapshot snapshot, Point point, boolean outside) {
        if (snapshot == null || snapshot.nature == null || snapshot.width <= 0 || snapshot.height <= 0
            || snapshot.width > Integer.MAX_VALUE - 4 || snapshot.height > Integer.MAX_VALUE - 4) return false;
        int x = point.getX(), y = point.getY();
        boolean inside = x >= 0 && y >= 0 && x < snapshot.width && y < snapshot.height;
        return outside ? !inside && x >= -1 && y >= -1 && x <= snapshot.width + 1 && y <= snapshot.height + 1 : inside;
    }

    private MouseMotionNature privateNature(MouseMotionNature source) {
        double divider = source.getTimeToStepsDivider();
        if (!Double.isFinite(divider) || divider <= 0 || source.getMinSteps() <= 0 || source.getEffectFadeSteps() <= 0
            || source.getReactionTimeBaseMs() < 0 || source.getReactionTimeVariationMs() < 0
            || source.getReactionTimeBaseMs() > 10_000 || source.getReactionTimeVariationMs() > 10_000
            || source.getSpeedManager() == null) return null;
        MouseMotionNature copy = new MouseMotionNature();
        copy.setTimeToStepsDivider(divider);
        copy.setMinSteps(source.getMinSteps());
        copy.setEffectFadeSteps(source.getEffectFadeSteps());
        copy.setReactionTimeBaseMs(source.getReactionTimeBaseMs());
        copy.setReactionTimeVariationMs(source.getReactionTimeVariationMs());
        // Exact stock providers are stateless; custom implementations never enter this path.
        copy.setDeviationProvider(source.getDeviationProvider());
        copy.setNoiseProvider(source.getNoiseProvider());
        DefaultOvershootManager overshoot = new DefaultOvershootManager(pathRandom);
        if (source.getOvershootManager() != null && source.getOvershootManager().getClass() == DefaultOvershootManager.class) {
            DefaultOvershootManager original = (DefaultOvershootManager) source.getOvershootManager();
            if (original.getOvershoots() < 0 || original.getMinDistanceForOvershoots() < 0 || original.getMinOvershootMovementMs() <= 0
                || !Double.isFinite(original.getOvershootRandomModifierDivider()) || original.getOvershootRandomModifierDivider() <= 0
                || !Double.isFinite(original.getOvershootSpeedupDivider()) || original.getOvershootSpeedupDivider() <= 0) return null;
            overshoot.setOvershoots(original.getOvershoots());
            overshoot.setMinDistanceForOvershoots(original.getMinDistanceForOvershoots());
            overshoot.setMinOvershootMovementMs(original.getMinOvershootMovementMs());
            overshoot.setOvershootRandomModifierDivider(original.getOvershootRandomModifierDivider());
            overshoot.setOvershootSpeedupDivider(original.getOvershootSpeedupDivider());
        }
        copy.setOvershootManager(overshoot);
        copy.setSpeedManager(MouseSpeedVariation.forMovement(source.getSpeedManager(), true, speedRandom));
        return copy;
    }

    private static final class VirtualBackend implements Backend {
        public boolean worker() {
            return Microbot.getClient() != null && !Microbot.getClient().isClientThread() && !SwingUtilities.isEventDispatchThread();
        }
        public boolean human() { return InputArbiter.isHuman(); }
        public Snapshot snapshot() {
            if (Microbot.getClientThread() == null || Microbot.naturalMouse == null || Microbot.getMouse() == null) return null;
            return Microbot.getClientThread().invoke((java.util.function.Supplier<Snapshot>) () ->
                new Snapshot(Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight(), Microbot.naturalMouse.nature));
        }
        public Point position() { return PointerState.get(); }
        public boolean gesture(Consumer<Sink> action) {
            return InputLoop.run(emit -> action.accept(new Sink() {
                public void checkpoint() { emit.checkpoint(); }
                public void move(int x, int y) { emit.move(x, y); }
                public void press(int x, int y) { emit.press(x, y, MouseEvent.BUTTON1); }
                public void release(int x, int y) { emit.release(x, y, MouseEvent.BUTTON1); }
                public void click(int x, int y) { emit.click(x, y, MouseEvent.BUTTON1); }
            })) == InputLoop.Result.COMPLETED;
        }
        public void delegate(Action action, Point point, NewMenuEntry entry) {
            if (action == Action.CLICK) Microbot.getMouse().click(point);
            else if (action == Action.INVOKE) Microbot.getMouse().click(point, entry);
            else {
                if (Microbot.naturalMouse != null) Microbot.naturalMouse.moveTo(point.getX(), point.getY());
                else Microbot.getMouse().move(point);
                if (action == Action.OFFSCREEN) Microbot.getMouse().move(point);
            }
        }
        public MenuEntry pendingMenu() { return Microbot.targetMenu; }
        public void pendingMenu(MenuEntry entry) { Microbot.targetMenu = entry; }
        public void clicked(Point point) { Microbot.getMouse().setLastClick(point); }
        public long millis() { return System.currentTimeMillis(); }
        public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
    }
}
