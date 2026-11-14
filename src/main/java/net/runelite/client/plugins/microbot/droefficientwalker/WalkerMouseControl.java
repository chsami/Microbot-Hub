package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionScope;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionVariation;

import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Shared Dro input gestures, with walker-local cancellation after mouse movement. */
public final class WalkerMouseControl
{
    private static final WalkerDoorAttempts doorAttempts = new WalkerDoorAttempts();
    static String takeDoorFailure() { return doorAttempts.takeFailure(); }
    private static volatile EfficientWalkerConfig config;
    private static final WalkerMouseProfile profile = new WalkerMouseProfile();
    private static volatile boolean idleAllowed;
    private static volatile boolean idleLatched;
    private static volatile long lastClickAt;
    private static volatile Point lastOwnedPoint;
    private static volatile boolean lastClickMinimap;
    static void resetSession() { invalidate(); profile.resetSession(); doorAttempts.reset(); }

    private static final AtomicLong generation = new AtomicLong();
    private static final AtomicReference<Object> pending = new AtomicReference<>();
    private static volatile ExecutorService executor;

    private WalkerMouseControl() { }

    static MouseMotionScope openProfile()
    {
        return MouseMotionScope.open(profileFor(config));
    }

    private static MouseMotionScope.Profile profileFor(EfficientWalkerConfig settings)
    {
        if (settings == null || !settings.useMicrobotMouse()) return null;
        return new MouseMotionScope.Profile() {
            public net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory motion(
                net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory source) {
                return profile.moderateMotion(source, false);
            }
            public long clickPause() { return profile.clickPause(); }
            public boolean isValid() { return config == settings && settings.useMicrobotMouse(); }
        };
    }

    /** Carry caller variation and the selected motion profile through existing helper executors. */
    public static Runnable profiledTask(Runnable task)
    {
        MouseMotionScope.Profile captured = profileFor(config);
        if (captured == null) return MouseMotionScope.capture(task);
        try (MouseMotionScope ignored = MouseMotionScope.open(captured))
        {
            return MouseMotionScope.capture(task);
        }
    }

    public static ExecutorService profiledExecutor(ExecutorService delegate)
    {
        return new java.util.concurrent.AbstractExecutorService() {
            public void shutdown() { delegate.shutdown(); }
            public java.util.List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
            public boolean isShutdown() { return delegate.isShutdown(); }
            public boolean isTerminated() { return delegate.isTerminated(); }
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                return delegate.awaitTermination(timeout, unit);
            }
            public void execute(Runnable command) { delegate.execute(profiledTask(command)); }
        };
    }

    /** The old engine prepositions the cursor before clicking, bypassing any trajectory. */
    public static net.runelite.client.plugins.microbot.util.mouse.Mouse preClickMove(
        net.runelite.client.plugins.microbot.util.mouse.Mouse mouse, Point point)
    {
        EfficientWalkerConfig settings = config;
        return settings != null && settings.useMicrobotMouse() ? mouse : mouse.move(point);
    }

    static synchronized void configure(EfficientWalkerConfig settings)
    {
        invalidate();
        config = settings;
    }

    static void invalidate() { generation.incrementAndGet(); idleAllowed = false; lastOwnedPoint = null; idleLatched = false; }

    static synchronized void shutdown()
    {
        invalidate();
        config = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        pending.set(null);
    }

    private static synchronized ExecutorService executor()
    {
        if (executor == null) executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "EfficientWalker-mouse");
            thread.setDaemon(true);
            return thread;
        });
        return executor;
    }

    /** Entry point used by the embedded engine's original canvas-click call sites. */
    public static void click(Client client, Point point)
    {
        click(client, point, null);
    }

    /**
     * Admit one local walk gesture without changing global route cancellation. True means
     * queued, never that a destination was selected or reached. The cheap owner predicate
     * is checked during motion; the scene predicate is read on the client thread before
     * and after motion. Neither predicate may dispatch input.
     */
    static boolean clickOwned(Client client, Point point, boolean minimap,
        java.util.function.BooleanSupplier ownerValid, java.util.function.BooleanSupplier sceneValid,
        java.util.function.Consumer<Boolean> completion)
    {
        if (point == null || !ownerValid.getAsBoolean()) return false;
        Object ticket = new Object();
        if (!pending.compareAndSet(null, ticket)) return false;
        EfficientWalkerConfig settings = config;
        long request = generation.get();
        idleAllowed = false;
        idleLatched = false;
        java.util.function.BooleanSupplier valid = () -> ownerValid.getAsBoolean()
            && request == generation.get() && config == settings
            && !Thread.currentThread().isInterrupted();
        java.util.concurrent.atomic.AtomicBoolean clicked = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            executor().submit(MouseMotionScope.capture(() -> {
                try {
                    InputLoop.run(emit -> {
                        if (!valid.getAsBoolean() || !Microbot.getClientThread().invoke(
                            (java.util.function.Supplier<Boolean>) sceneValid::getAsBoolean)) return;
                        move(point, minimap, request, emit, false, valid);
                        try { Thread.sleep(profile.clickPause()); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                        if (!valid.getAsBoolean() || !Microbot.getClientThread().invoke(
                            (java.util.function.Supplier<Boolean>) sceneValid::getAsBoolean)) return;
                        emit.checkpoint();
                        if (!valid.getAsBoolean()) return;
                        moveWithTrail(emit, point.getX(), point.getY());
                        if (!valid.getAsBoolean()) return;
                        Microbot.targetMenu = minimap ? null : new NewMenuEntry().option("Walk here")
                            .type(net.runelite.api.MenuAction.WALK).identifier(0).itemId(0)
                            .param0(point.getX()).param1(point.getY());
                        emit.press(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        emit.release(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        emit.click(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        clicked.set(true);
                        Microbot.getMouse().setLastClick(point);
                        lastClickAt = System.currentTimeMillis();
                        // Local hazard movement must not arm the route's later idle parking.
                        lastOwnedPoint = null;
                    });
                } catch (java.util.concurrent.CancellationException ignored) {
                    // Only this owner's stale gesture is discarded.
                } finally {
                    pending.compareAndSet(ticket, null);
                    completion.accept(clicked.get());
                }
            }));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            pending.compareAndSet(ticket, null);
            return false;
        }
    }

    /** Preserve the engine's explicit action across the asynchronous mouse trajectory. */
    public static void interact(Client client, NewMenuEntry entry, java.awt.Rectangle bounds, boolean invoke)
    {
        NewMenuEntry dispatched = entry;
        if (entry != null && ("Open".equalsIgnoreCase(entry.getOption()) || "Pick-lock".equalsIgnoreCase(entry.getOption()))
            && (entry.getIdentifier() == 1804 || entry.getTarget() != null
                && (entry.getTarget().toLowerCase(java.util.Locale.ROOT).contains("door")
                    || entry.getTarget().toLowerCase(java.util.Locale.ROOT).contains("gate"))
                || entry.getIdentifier() == PiratesHideoutDoorAction.DOOR_ID)) {
            dispatched = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                var player = client.getLocalPlayer();
                if (player == null || client.getTopLevelWorldView() == null) return null;
                WorldPoint door = entry.getGameObject() == null
                    ? WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane())
                    : entry.getGameObject().getWorldLocation();
                NewMenuEntry hutAction = null;
                if (MagicAxesDoorAction.isHutDoor(door)) {
                    String problem = MagicAxesDoorAction.accessProblem(
                        net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.hasItem(PiratesHideoutDoorAction.LOCKPICK_ID),
                        net.runelite.client.plugins.microbot.util.player.Rs2Player.getBoostedSkillLevel(net.runelite.api.Skill.THIEVING));
                    if (problem != null) { doorAttempts.reject(problem); return null; }
                    var definition = client.getObjectDefinition(entry.getIdentifier());
                    hutAction = MagicAxesDoorAction.pickLock(entry, definition == null ? null : definition.getActions());
                    if (hutAction == null) { doorAttempts.reject("Magic axe hut door has no Pick-lock action"); return null; }
                }
                if (entry.getIdentifier() == PiratesHideoutDoorAction.DOOR_ID) {
                    String problem = PiratesHideoutDoorAction.accessProblem(
                        net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.hasItem(PiratesHideoutDoorAction.LOCKPICK_ID),
                        net.runelite.client.plugins.microbot.util.player.Rs2Player.getBoostedSkillLevel(net.runelite.api.Skill.THIEVING));
                    if (problem != null) { doorAttempts.reject(problem); return null; }
                }
                if (doorAttempts.request(entry.getIdentifier(), door,
                    player.getWorldLocation(), client.getWorld(),
                    net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.hasItem(983),
                    System.currentTimeMillis()) != WalkerDoorAttempts.Decision.ALLOW) return null;
                if (hutAction != null) return hutAction;
                return entry.getIdentifier() == PiratesHideoutDoorAction.DOOR_ID
                    ? PiratesHideoutDoorAction.pickLock(entry) : entry;
            }).orElse(null);
            if (dispatched == null) return;
        }
        if (invoke) {
            try (MouseMotionScope ignored = openProfile()) { Microbot.doInvoke(dispatched, bounds); }
            return;
        }
        java.awt.Rectangle safe = Rs2UiHelper.isRectangleWithinCanvas(bounds)
            ? bounds : Rs2UiHelper.getDefaultRectangle();
        EfficientWalkerConfig settings = config;
        Point point = settings != null && settings.useMicrobotMouse()
            ? profile.randomPoint(safe) : Rs2UiHelper.getClickingPoint(safe, false);
        click(client, point, dispatched);
    }

    private static void click(Client client, Point point, NewMenuEntry entry)
    {
        EfficientWalkerConfig settings = config;
        if (settings == null || (!settings.useMicrobotMouse() && !net.runelite.client.plugins.microbot.droefficientwalker.compat.CanvasPointerLease.active())) {
            invalidate();
            Microbot.targetMenu = entry;
            CanvasClickDispatcher.clickCanvas(client, point);
            return;
        }
        idleAllowed = false;
        idleLatched = false;
        Object ticket = new Object();
        if (point == null || !pending.compareAndSet(null, ticket)) return;
        boolean minimap = false;
        net.runelite.api.widgets.Widget map = net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap.getMinimapDrawWidget();
        if (map != null && !map.isHidden() && map.getBounds() != null)
            minimap = map.getBounds().contains(point.getX(), point.getY());
        final boolean minimapClick = minimap;
        long request = generation.get();
        int world = client.getWorld();
        int yaw = client.getCameraYaw(), pitch = client.getCameraPitch();
        int width = client.getCanvasWidth(), height = client.getCanvasHeight();
        WorldPoint location = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
        try {
            executor().submit(MouseMotionScope.capture(() -> {
                try {
                    InputLoop.run(emit -> {
                        if (!valid(client, settings, request, world, location, point, yaw, pitch, width, height)) return;
                        move(point, minimapClick, request, emit, false);
                        try { Thread.sleep(profile.clickPause()); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                        // Cancellation and moving targets must be checked AFTER acquiring the
                        // shared input lock and again AFTER a potentially slow mouse trajectory.
                        if (!valid(client, settings, request, world, location, point, yaw, pitch, width, height)) return;
                        // Install the action only after acquiring input ownership and moving.
                        // Raw walking carries null; object/widget clicks carry their exact menu.
                        Microbot.targetMenu = entry;
                        moveWithTrail(emit, point.getX(), point.getY());
                        emit.press(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        emit.release(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        emit.click(point.getX(), point.getY(), MouseEvent.BUTTON1);
                        Microbot.getMouse().setLastClick(point);
                        lastOwnedPoint = point;
                        lastClickAt = System.currentTimeMillis();
                        lastClickMinimap = minimapClick;
                    });
                } catch (java.util.concurrent.CancellationException ignored) {
                    // Route cancellation can interrupt a trajectory before its endpoint.
                } finally { pending.compareAndSet(ticket, null); }
            }));
        } catch (java.util.concurrent.RejectedExecutionException ex) { pending.compareAndSet(ticket, null); }
    }

    /** BaseProfileDro's mouse handoff records the trail before the movement is displayed. */
    private static void moveWithTrail(InputLoop.Emit emit, int x, int y)
    {
        emit.checkpoint();
        // Keep the existing input lock and cancellation gate, then use the same
        // recorder/emitter as Dro's natural mouse. Do not emit a second move here.
        Microbot.getMouse().move(x, y);
    }

    private static void move(Point point, boolean minimap, long request, InputLoop.Emit emit, boolean idle)
    {
        move(point, minimap, request, emit, idle, () -> true);
    }

    private static void move(Point point, boolean minimap, long request, InputLoop.Emit emit, boolean idle,
        java.util.function.BooleanSupplier ownerValid)
    {
        if (!ownerValid.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        if (Microbot.getNaturalMouse() == null) { moveWithTrail(emit, point.getX(), point.getY()); return; }
        net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory source = Microbot.getNaturalMouse().getFactory();
        if (source == null) { Microbot.getNaturalMouse().moveTo(point.getX(), point.getY()); return; }
        net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory motion =
            motionForMovement(source, minimap);
        net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls calls = motion.getSystemCalls();
        motion.setSystemCalls(new net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls() {
            public long currentTimeMillis() { return calls.currentTimeMillis(); }
            public void sleep(long ms) { calls.sleep(ms); }
            public java.awt.Dimension getScreenSize() { return calls.getScreenSize(); }
            public void setMousePosition(int x, int y) {
                if (!ownerValid.getAsBoolean() || request != generation.get() || Thread.currentThread().isInterrupted() || (idle && !idleAllowed)) throw new java.util.concurrent.CancellationException();
                moveWithTrail(emit, x, y);
            }
        });
        try { motion.move(point.getX(), point.getY()); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException(); }
    }

    static net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory motionForMovement(
        net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory source, boolean minimap)
    {
        EfficientWalkerConfig settings = config;
        // Moderate replaces the source's speed/deviation managers. Apply the caller's
        // variation after that selection, retaining its noise, reaction and overshoot policy.
        return settings != null && settings.useMicrobotMouse()
            ? MouseMotionVariation.apply(profile.moderateMotion(source, minimap))
            : WalkerMouseProfile.motion(source, minimap);
    }

    /** Called on the client thread. Idle means the mouse is free while an ordinary walking leg runs. */
    static void tick(Client client, boolean ordinaryWalking)
    {
        EfficientWalkerConfig settings = config;
        idleAllowed = !net.runelite.client.plugins.microbot.droefficientwalker.compat.CanvasPointerLease.active()
            && settings != null && settings.useMicrobotMouse()
            && !lastClickMinimap && ordinaryWalking
            && client.getGameState() == net.runelite.api.GameState.LOGGED_IN
            && client.getLocalPlayer() != null && client.getLocalPlayer().getPoseAnimation() != client.getLocalPlayer().getIdlePoseAnimation()
            && client.getLocalPlayer().getAnimation() == -1;
        if (!idleAllowed) { idleLatched = false; return; }
        if (idleLatched || lastOwnedPoint == null || System.currentTimeMillis() - lastClickAt < 900) return;
        idleLatched = true;
        int width = client.getCanvasWidth(), height = client.getCanvasHeight();
        long request = generation.get();
        Point owner = lastOwnedPoint;
        Object ticket = new Object();
        if (!pending.compareAndSet(null, ticket)) return;
        try {
            executor().submit(MouseMotionScope.capture(() -> {
                try {
                    InputLoop.run(emit -> {
                        if (!idleValid(settings, request, owner)) return;
                        WalkerMouseProfile.Activity activity = settings.walkerMouseActivity();
                        Point target = null;
                        if (settings.walkerMouseParkSide() != WalkerMouseProfile.ParkSide.NONE
                            && profile.roll(profile.parkChance(activity))) {
                            target = profile.parkPoint(settings.walkerMouseParkSide(), owner, width, height);
                        }
                        // Never nudge a minimap target; its coordinates identify a precise world tile.
                        if (target == null && !lastClickMinimap) target = profile.nudge(owner, width, height);
                        if (target == null || !idleValid(settings, request, owner)) return;
                        Point edge = WalkerMouseProfile.canvasEdge(target, width, height);
                        move(edge, false, request, emit, true);
                        if (!idleValid(settings, request, null)) return;
                        Point actual = net.runelite.client.plugins.microbot.util.input.PointerState.lastBotPoint();
                        if (actual == null || Math.abs(actual.getX() - edge.getX()) > 4
                            || Math.abs(actual.getY() - edge.getY()) > 4) return;
                        // Dro's edge-then-cross pattern, under the same input ownership and cancellation.
                        moveWithTrail(emit, target.getX(), target.getY());
                        lastOwnedPoint = target;
                    });
                } catch (java.util.concurrent.CancellationException ignored) {
                } finally { pending.compareAndSet(ticket, null); }
            }));
        } catch (java.util.concurrent.RejectedExecutionException ex) { pending.compareAndSet(ticket, null); }
    }

    private static boolean idleValid(EfficientWalkerConfig settings, long request, Point owner)
    {
        return idleAllowed && config == settings && request == generation.get()
            && !Thread.currentThread().isInterrupted()
            && (owner == null || owner.equals(net.runelite.client.plugins.microbot.util.input.PointerState.lastBotPoint()));
    }

    private static boolean valid(Client client, EfficientWalkerConfig settings, long request,
        int world, WorldPoint location, Point point, int yaw, int pitch, int width, int height)
    {
        if (Thread.currentThread().isInterrupted() || request != generation.get() || config != settings) return false;
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> request == generation.get()
            && (settings.useMicrobotMouse() || net.runelite.client.plugins.microbot.droefficientwalker.compat.CanvasPointerLease.active()) && world == client.getWorld()
            && yaw == client.getCameraYaw() && pitch == client.getCameraPitch()
            && width == client.getCanvasWidth() && height == client.getCanvasHeight()
            && client.getLocalPlayer() != null
            && java.util.Objects.equals(location, client.getLocalPlayer().getWorldLocation())
            && point.getX() >= 0 && point.getY() >= 0
            && point.getX() < client.getCanvasWidth() && point.getY() < client.getCanvasHeight());
    }
}
