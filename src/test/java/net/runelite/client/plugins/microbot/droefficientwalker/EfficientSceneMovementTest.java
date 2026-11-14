package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.DroSetupMouse;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EfficientSceneMovementTest
{
    @Test void registeredHostBreakRejectsMovementBeforeProjectionIsQueued()
    {
        ClientThread thread = mock(ClientThread.class);
        boolean paused = Microbot.pauseAllScripts.getAndSet(false);
        try (var runtime = mockStatic(Microbot.class); var human = mockStatic(InputArbiter.class);
             var host = mockStatic(DroSetupMouse.class))
        {
            runtime.when(Microbot::isLoggedIn).thenReturn(true);
            runtime.when(Microbot::getClient).thenReturn(mock(Client.class));
            runtime.when(Microbot::getClientThread).thenReturn(thread);
            EfficientSceneMovement movement = new EfficientSceneMovement();
            host.when(DroSetupMouse::isHostInputAllowed).thenReturn(false);
            assertFalse(movement.walkMinimap(new WorldPoint(3200, 3200, 0)));
            assertEquals(EfficientSceneMovement.RequestStatus.REJECTED, movement.getRequestStatus());
            verifyNoInteractions(thread);
            host.when(DroSetupMouse::isHostInputAllowed).thenReturn(true);
            assertTrue(movement.walkMinimap(new WorldPoint(3200, 3200, 0)));
            verify(thread).invokeLater(any(Runnable.class));
        }
        finally { Microbot.pauseAllScripts.set(paused); }
    }

    private static final class Request
    {
        LocalPoint local;
        WorldPoint world;
        BooleanSupplier valid;
        Consumer<EfficientSceneMovement.RequestStatus> progress;
    }

    private static final class Port implements EfficientSceneMovement.Port
    {
        final List<Request> requests = new ArrayList<>();
        boolean ready = true, accept = true;
        public boolean ready() { return ready; }
        public boolean submit(LocalPoint local, WorldPoint world, BooleanSupplier valid,
            Consumer<EfficientSceneMovement.RequestStatus> progress)
        {
            Request request = new Request();
            request.local = local;
            request.world = world;
            request.valid = valid;
            request.progress = progress;
            requests.add(request);
            return accept;
        }
    }

    @Test void sameTileRequestsAreNeverTreatedAsArrival()
    {
        Port port = new Port();
        EfficientSceneMovement movement = new EfficientSceneMovement(port);
        LocalPoint currentTile = new LocalPoint(64, 64, WorldView.TOPLEVEL);
        assertTrue(movement.walk(currentTile));
        assertEquals(EfficientSceneMovement.RequestStatus.REQUEST_QUEUED, movement.getRequestStatus());
        port.requests.get(0).progress.accept(EfficientSceneMovement.RequestStatus.GESTURE_QUEUED);
        assertEquals(EfficientSceneMovement.RequestStatus.GESTURE_QUEUED, movement.getRequestStatus());
        assertTrue(movement.walk(currentTile));
        assertEquals(1, port.requests.size());
        assertTrue(port.requests.get(0).valid.getAsBoolean());
        port.requests.get(0).progress.accept(EfficientSceneMovement.RequestStatus.CLICK_DISPATCHED);
        assertTrue(movement.walk(currentTile));
        assertEquals(2, port.requests.size());
        assertSame(currentTile, port.requests.get(1).local);
        assertNull(port.requests.get(1).world);
    }

    @Test void cancelAndSupersessionInvalidateOnlyTheirOwnQueuedGesture()
    {
        Port port = new Port();
        EfficientSceneMovement first = new EfficientSceneMovement(port);
        EfficientSceneMovement second = new EfficientSceneMovement(port);
        WorldPoint target = new WorldPoint(10000, 10000, 0);
        first.walkMinimap(target);
        second.walkMinimap(target);
        first.cancel();
        assertFalse(port.requests.get(0).valid.getAsBoolean());
        assertTrue(port.requests.get(1).valid.getAsBoolean());
        port.requests.get(0).progress.accept(EfficientSceneMovement.RequestStatus.CLICK_DISPATCHED);
        assertEquals(EfficientSceneMovement.RequestStatus.CANCELLED, first.getRequestStatus());
        second.walkMinimap(target.dx(1));
        assertFalse(port.requests.get(1).valid.getAsBoolean());
        assertTrue(port.requests.get(2).valid.getAsBoolean());
    }

    @Test void rejectedGeometryAndBusyAdmissionDoNotPromiseMovement()
    {
        Port port = new Port();
        EfficientSceneMovement movement = new EfficientSceneMovement(port);
        assertTrue(movement.walkMinimap(new WorldPoint(3200, 3200, 0)));
        port.requests.get(0).progress.accept(EfficientSceneMovement.RequestStatus.REJECTED);
        assertEquals(EfficientSceneMovement.RequestStatus.REJECTED, movement.getRequestStatus());
        port.accept = false;
        assertFalse(movement.walkMinimap(new WorldPoint(3201, 3200, 0)));
        assertEquals(EfficientSceneMovement.RequestStatus.REJECTED, movement.getRequestStatus());
        port.ready = false;
        assertFalse(movement.walkMinimap(new WorldPoint(3202, 3200, 0)));
        assertEquals(2, port.requests.size());
        assertFalse(port.requests.get(0).valid.getAsBoolean());
    }

    @Test void lateAdmissionCannotReviveACompletedRequestAndSuppressItsRetry()
    {
        for (var terminal : new EfficientSceneMovement.RequestStatus[]{
            EfficientSceneMovement.RequestStatus.REJECTED, EfficientSceneMovement.RequestStatus.CLICK_DISPATCHED}) {
            Port port = new Port();
            EfficientSceneMovement movement = new EfficientSceneMovement(port);
            WorldPoint target = new WorldPoint(3200, 3200, 0);
            assertTrue(movement.walkMinimap(target));
            var first = port.requests.get(0);
            first.progress.accept(terminal);
            first.progress.accept(EfficientSceneMovement.RequestStatus.GESTURE_QUEUED);
            assertEquals(terminal, movement.getRequestStatus());
            assertTrue(movement.walkMinimap(target));
            assertEquals(2, port.requests.size(), "A finished request cannot coalesce a later retry");
        }
    }

    @Test void rawInstanceSceneCoordinatesRemainRaw()
    {
        WorldView view = view(104);
        when(view.getBaseX()).thenReturn(10000);
        when(view.getBaseY()).thenReturn(11000);
        when(view.getPlane()).thenReturn(0);
        when(view.isInstance()).thenReturn(true);
        LocalPoint local = EfficientSceneMovement.rawSceneLocal(view, new WorldPoint(10007, 11009, 0));
        assertNotNull(local);
        assertEquals(7, local.getSceneX());
        assertEquals(9, local.getSceneY());
        assertNull(EfficientSceneMovement.rawSceneLocal(view, new WorldPoint(3007, 3009, 0)));
        assertNull(EfficientSceneMovement.rawSceneLocal(view, new WorldPoint(10007, 11009, 1)));
    }

    @Test void smallWorldviewAndExtendedScenePointsAreRejectedBeforeProjection()
    {
        WorldView view = view(8);
        assertTrue(EfficientSceneMovement.inScene(new LocalPoint(7 * 128 + 64, 7 * 128 + 64, 4), view));
        assertFalse(EfficientSceneMovement.inScene(new LocalPoint(8 * 128 + 64, 64, 4), view));
        assertFalse(EfficientSceneMovement.inScene(new LocalPoint(-64, 64, 4), view));
        assertFalse(EfficientSceneMovement.inScene(new LocalPoint(64, 64, WorldView.TOPLEVEL), view));
        assertFalse(EfficientSceneMovement.inScene(null, view));
        verify(view, never()).getTileHeight(anyInt(), anyInt(), anyInt());
    }

    @Test void projectionRejectsOutOfSceneAndSoftwareUnrenderedTiles()
    {
        Client client = mock(Client.class);
        WorldView view = view(104);
        Scene scene = mock(Scene.class);
        when(view.getScene()).thenReturn(scene);
        // A GPU draw distance remains configured even after GPU rendering is switched off.
        when(scene.getDrawDistance()).thenReturn(90);
        LocalPoint beyondSoftwareEdge = new LocalPoint(24 * 128, 64, 4);
        try (var projection = mockStatic(Perspective.class)) {
            assertNull(EfficientSceneMovement.project(client, view, new LocalPoint(104 * 128, 64, 4), false));
            assertNull(EfficientSceneMovement.project(client, view, beyondSoftwareEdge, false));
            projection.verifyNoInteractions();
            when(client.isGpu()).thenReturn(true);
            assertNull(EfficientSceneMovement.project(client, view, beyondSoftwareEdge, false));
            projection.verify(() -> Perspective.localToCanvas(client, beyondSoftwareEdge, 0));
        }
    }

    private static WorldView view(int size)
    {
        WorldView view = mock(WorldView.class);
        when(view.getId()).thenReturn(4);
        when(view.getSizeX()).thenReturn(size);
        when(view.getSizeY()).thenReturn(size);
        return view;
    }
}
