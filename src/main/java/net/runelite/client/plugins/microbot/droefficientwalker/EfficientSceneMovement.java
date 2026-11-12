package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroSetupMouse;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneRenderGeometry;

/**
 * Direct, nonblocking local movement through Efficient Walker's shared input gestures.
 * There is no route planner, transport selection, instance-template conversion or arrival
 * shortcut here: clicking the current tile can intentionally interrupt an interaction.
 * Call from the activity worker, never the client thread, and cancel before other actions.
 */
public final class EfficientSceneMovement
{
    public enum RequestStatus { IDLE, REQUEST_QUEUED, GESTURE_QUEUED, CLICK_DISPATCHED, REJECTED, CANCELLED }

    interface Port
    {
        boolean ready();
        boolean submit(LocalPoint local, WorldPoint rawSceneWorld, BooleanSupplier valid,
            Consumer<RequestStatus> progress);
    }

    private final Port port;
    private final AtomicLong generation = new AtomicLong();
    private volatile RequestStatus status = RequestStatus.IDLE;
    private LocalPoint pendingLocal;
    private WorldPoint pendingWorld;

    public EfficientSceneMovement() { this(new GamePort()); }
    EfficientSceneMovement(Port port) { this.port = Objects.requireNonNull(port); }

    /** True means queued for projection/dispatch, never selected destination or arrival.
     * Uses the minimap if the local tile cannot safely be clicked in the scene. */
    public boolean walk(LocalPoint target) { return request(target, null); }

    /** Accepts raw scene-world coordinates, including inside an instance, not template tiles. */
    public boolean walkMinimap(WorldPoint rawSceneWorld) { return request(null, rawSceneWorld); }

    /** CLICK_DISPATCHED records emitted input only; observe player/destination separately. */
    public RequestStatus getRequestStatus() { return status; }

    /** Invalidate only this instance's queued requests and in-progress mouse trajectory. */
    public synchronized void cancel()
    {
        generation.incrementAndGet();
        status = RequestStatus.CANCELLED;
        pendingLocal = null;
        pendingWorld = null;
    }

    private synchronized boolean request(LocalPoint local, WorldPoint rawSceneWorld)
    {
        if ((local == null && rawSceneWorld == null) || !port.ready()) {
            generation.incrementAndGet();
            status = RequestStatus.REJECTED;
            return false;
        }
        if ((status == RequestStatus.REQUEST_QUEUED || status == RequestStatus.GESTURE_QUEUED)
            && Objects.equals(local, pendingLocal) && Objects.equals(rawSceneWorld, pendingWorld)) return true;
        long request = generation.incrementAndGet();
        pendingLocal = local;
        pendingWorld = rawSceneWorld;
        status = RequestStatus.REQUEST_QUEUED;
        boolean accepted = port.submit(local, rawSceneWorld,
            () -> generation.get() == request, progress -> {
                synchronized (EfficientSceneMovement.this) {
                    if (generation.get() != request) return;
                    // A cancelled worker can finish before the submitting client-thread callback
                    // publishes admission. Never revive that terminal request as pending.
                    if (progress == RequestStatus.GESTURE_QUEUED && status != RequestStatus.REQUEST_QUEUED) return;
                    status = progress;
                }
            });
        if (!accepted) status = RequestStatus.REJECTED;
        return accepted;
    }

    private static boolean inputAllowed()
    {
        return !Thread.currentThread().isInterrupted() && !Microbot.pauseAllScripts.get()
            && !InputArbiter.isHuman() && DroSetupMouse.isHostInputAllowed();
    }

    /** Check the point's own scene before any height/projection API can index its tiles. */
    static boolean inScene(LocalPoint point, WorldView view)
    {
        return point != null && view != null && point.getWorldView() == view.getId()
            && point.getSceneX() >= 0 && point.getSceneY() >= 0
            && point.getSceneX() < view.getSizeX() && point.getSceneY() < view.getSizeY();
    }

    static LocalPoint rawSceneLocal(WorldView view, WorldPoint rawSceneWorld)
    {
        if (view == null || rawSceneWorld == null || rawSceneWorld.getPlane() != view.getPlane()) return null;
        LocalPoint point = LocalPoint.fromWorld(view, rawSceneWorld);
        return inScene(point, view) ? point : null;
    }

    static Point project(Client client, WorldView view, LocalPoint target, boolean minimap)
    {
        if (!inScene(target, view)) return null;
        if (minimap) {
            var map = Rs2MiniMap.getMinimapDrawWidget();
            if (map == null || map.isHidden() || map.getBounds() == null) return null;
            Point point = Perspective.localToMinimap(client, target);
            Shape clip = Rs2MiniMap.getMinimapClipArea();
            return onCanvas(client, point) && clip != null && clip.contains(point.getX(), point.getY()) ? point : null;
        }
        if (view.getScene() == null) return null;
        int distance = SceneRenderGeometry.renderedDrawDistance(client.isGpu(), view.getScene().getDrawDistance());
        if (!SceneRenderGeometry.isWithinRenderedArea(client.getCameraX(), client.getCameraY(), target, distance)) return null;
        Point point = Perspective.localToCanvas(client, target, view.getPlane());
        Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
            client.getViewportWidth(), client.getViewportHeight());
        if (!onCanvas(client, point) || !viewport.contains(new Rectangle(point.getX() - 4, point.getY() - 4, 8, 8))) return null;
        var map = Rs2MiniMap.getMinimapDrawWidget();
        return map != null && !map.isHidden() && map.getBounds() != null
            && map.getBounds().contains(point.getX(), point.getY()) ? null : point;
    }

    private static boolean onCanvas(Client client, Point point)
    {
        return point != null && point.getX() >= 0 && point.getY() >= 0
            && point.getX() < client.getCanvasWidth() && point.getY() < client.getCanvasHeight();
    }

    private static final class GamePort implements Port
    {
        public boolean ready()
        {
            return inputAllowed() && Microbot.isLoggedIn() && Microbot.getClient() != null && Microbot.getClientThread() != null
                && !Microbot.getClientThread().isClientThread();
        }

        public boolean submit(LocalPoint local, WorldPoint rawSceneWorld, BooleanSupplier ownerValid,
            Consumer<RequestStatus> progress)
        {
            BooleanSupplier valid = () -> ownerValid.getAsBoolean() && inputAllowed();
            // Projection itself is queued: the activity never waits for the client or mouse.
            Microbot.getClientThread().invokeLater((Runnable) () -> {
                Client client = Microbot.getClient();
                if (!valid.getAsBoolean() || client == null || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()
                    || client.getLocalPlayer() == null) { progress.accept(RequestStatus.REJECTED); return; }
                WorldView view = client.getLocalPlayer().getWorldView();
                // Camera-relative rendering/minimap geometry below is top-level scene geometry.
                // Decline boat/sub-worldview points instead of interpreting their local units there.
                if (view == null || view != client.getTopLevelWorldView()) { progress.accept(RequestStatus.REJECTED); return; }
                boolean requestedMinimap = rawSceneWorld != null;
                LocalPoint target = requestedMinimap ? rawSceneLocal(view, rawSceneWorld) : local;
                Point scenePoint = project(client, view, target, requestedMinimap);
                boolean minimap = requestedMinimap || scenePoint == null;
                Point point = scenePoint != null || requestedMinimap ? scenePoint : project(client, view, target, true);
                if (point == null) { progress.accept(RequestStatus.REJECTED); return; }
                int world = client.getWorld(), plane = view.getPlane();
                int baseX = view.getBaseX(), baseY = view.getBaseY();
                int yaw = client.getCameraYaw(), pitch = client.getCameraPitch();
                WorldPoint player = client.getLocalPlayer().getWorldLocation();
                boolean admitted = WalkerMouseControl.clickOwned(client, point, minimap, valid, () ->
                    valid.getAsBoolean() && client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen()
                        && client.getWorld() == world && client.getLocalPlayer() != null
                        && client.getLocalPlayer().getWorldView() == view
                        && client.getWorldView(target.getWorldView()) == view
                        && view.getBaseX() == baseX && view.getBaseY() == baseY && view.getPlane() == plane
                        && client.getCameraYaw() == yaw && client.getCameraPitch() == pitch
                        && Objects.equals(player, client.getLocalPlayer().getWorldLocation())
                        && point.equals(project(client, view, target, minimap)),
                    clicked -> progress.accept(clicked ? RequestStatus.CLICK_DISPATCHED : RequestStatus.REJECTED));
                progress.accept(admitted ? RequestStatus.GESTURE_QUEUED : RequestStatus.REJECTED);
            });
            return true;
        }
    }
}
