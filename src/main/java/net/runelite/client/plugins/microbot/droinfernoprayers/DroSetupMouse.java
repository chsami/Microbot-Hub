package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.awt.Rectangle;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Borrows the running Dro profile for setup gestures without owning its lifecycle or breaks. */
public final class DroSetupMouse {
    private static volatile Binding active;
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IN_CALLBACK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<BaseProfileDro> FALLBACK = ThreadLocal.withInitial(() -> {
        BaseProfileDro profile = new BaseProfileDro(new BaseProfileDro.Settings()
                .nativeAntibanEnabled(false).customBreaksEnabled(false)
                .startupCameraEnabled(false).actionCameraNudges(false)
                .postLoginSettleSeconds(0).overlayEnabled(false).writeBreakStatusToMicrobot(false));
        profile.start();
        return profile;
    });

    private DroSetupMouse() { }

    public static synchronized void register(Object owner, Runnable before, Runnable after,
            Function<Rectangle, Point> point, Runnable idle) {
        register(owner, before, after, point, idle, () -> true);
    }

    public static synchronized void register(Object owner, Runnable before, Runnable after,
            Function<Rectangle, Point> point, Runnable idle, BooleanSupplier allowed) {
        if (active == null || active.owner != owner) {
            if (active != null) active.live = false;
            active = new Binding(owner, before, after, point, idle, allowed);
        }
    }

    public static synchronized void unregister(Object owner) {
        if (active != null && active.owner == owner) {
            active.live = false;
            active = null;
        }
    }

    /** Shared maintenance must not disturb the running host's active break. */
    public static boolean isHostInputAllowed() {
        Binding binding = active;
        return binding == null || binding.live && binding.allowed.getAsBoolean();
    }

    public static Scope open() {
        if (Microbot.getClient() != null && Microbot.getClient().isClientThread()) {
            throw new IllegalStateException("Inventory setup input must run off the client thread");
        }
        Context previous = CURRENT.get();
        if (previous != null) return new Scope(previous, false);
        Binding binding = active;
        if (binding == null) {
            BaseProfileDro fallback = FALLBACK.get();
            if (Microbot.getClient() != null) fallback.tick(false, false);
            binding = new Binding(fallback,
                    () -> fallback.beforeAction(BaseProfileDro.ActionPhase.SETUP, false),
                    () -> fallback.afterAction(BaseProfileDro.ActionPhase.SETUP),
                    fallback::randomPoint, () -> fallback.tick(false, true), () -> true);
        }
        Context context = new Context(binding);
        CURRENT.set(context);
        return new Scope(context, true);
    }

    public static Point randomPoint(Rectangle bounds) {
        Context context = CURRENT.get();
        if (context == null || !context.binding.live || IN_CALLBACK.get()
                || bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
        return context.binding.point.apply(bounds);
    }

    public static Gesture captureGesture() {
        return new Gesture(IN_CALLBACK.get() ? null : CURRENT.get());
    }

    private static void callback(Runnable action) {
        boolean previous = IN_CALLBACK.get();
        IN_CALLBACK.set(true);
        try { action.run(); }
        finally {
            if (previous) IN_CALLBACK.set(true);
            else IN_CALLBACK.remove();
        }
    }

    public static final class Gesture {
        private final Context context;
        private Gesture(Context context) { this.context = context; }

        public void run(Runnable gesture) {
            if (context == null) { gesture.run(); return; }
            // Delays are outside InputLoop's lock; human takeover cancels subsequent input.
            if (!canInput()) return;
            callback(context.binding.before);
            if (!canInput()) return;
            gesture.run();
            context.used = true;
            if (canInput()) callback(context.binding.after);
        }

        private boolean canInput() {
            return context.binding.live && context.binding.allowed.getAsBoolean()
                    && !Microbot.pauseAllScripts.get() && !InputArbiter.isHuman()
                    && !Thread.currentThread().isInterrupted();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Context context;
        private final boolean outer;
        private boolean closed;
        private Scope(Context context, boolean outer) { this.context = context; this.outer = outer; }

        @Override public void close() {
            if (closed || !outer) return;
            closed = true;
            CURRENT.remove();
            // Parking is an idle decision after the setup sequence, never between replacement clicks.
            if (context.used && context.binding.live && context.binding.allowed.getAsBoolean()
                    && !Microbot.pauseAllScripts.get() && !InputArbiter.isHuman() && !Thread.currentThread().isInterrupted()
                    && Microbot.getClient() != null && Microbot.isLoggedIn()) callback(context.binding.idle);
        }
    }

    private static final class Context {
        private final Binding binding;
        private volatile boolean used;
        private Context(Binding binding) { this.binding = binding; }
    }

    private static final class Binding {
        private final Object owner;
        private final Runnable before;
        private final Runnable after;
        private final Function<Rectangle, Point> point;
        private final Runnable idle;
        private final BooleanSupplier allowed;
        private volatile boolean live = true;
        private Binding(Object owner, Runnable before, Runnable after, Function<Rectangle, Point> point,
                Runnable idle, BooleanSupplier allowed) {
            this.owner = owner;
            this.before = before;
            this.after = after;
            this.point = point;
            this.idle = idle;
            this.allowed = allowed;
        }
    }
}
