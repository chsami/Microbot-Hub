package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Deterministic interleavings at client handoff and candidate preparation boundaries. */
class EfficientWalkerRecoveryConcurrencyTest
{
    private static final WorldPoint TARGET = new WorldPoint(3000, 3000, 0);
    private static final WorldPoint REPLACEMENT = TARGET.dx(20);

    @Test void nearbyRecoveryUsesOneBlockedSnapshotEvenIfNextEngineReadWouldBePreview() throws Exception {
        try (Harness h = new Harness()) {
            h.installNearby();
            h.engine.sequence = new EfficientWalker.Status[]{EfficientWalker.Status.BLOCKED, EfficientWalker.Status.PREVIEW};
            assertEquals(EfficientWalker.Status.PLANNING, h.fixture.walker.getStatus());
            assertEquals(1, h.engine.reads, "A status poll must not combine two engine observations");
        }
    }

    @Test void equipmentTrackingDoesNotMixBlockedAndPreviewObservations() throws Exception {
        try (Harness h = new Harness()) {
            Object restore = get(h.fixture.walker, "fairyRingEquipmentRestore");
            Object tracking = mock(restore.getClass());
            Method isTracking = method(restore.getClass(), "isTracking");
            when((Boolean)isTracking.invoke(tracking)).thenReturn(true);
            set(h.fixture.walker, "fairyRingEquipmentRestore", tracking);
            h.engine.sequence = new EfficientWalker.Status[]{EfficientWalker.Status.BLOCKED, EfficientWalker.Status.PREVIEW};
            assertEquals(EfficientWalker.Status.BLOCKED, h.fixture.walker.getStatus());
            assertEquals(1, h.engine.reads);
        }
    }

    @Test void preparationMasksBlockedForOrdinaryAndOwnedPolls() throws Exception {
        try (Harness h = new Harness()) {
            assertTrue(h.owner.prepare(h.request));
            assertEquals(EfficientWalker.Status.PLANNING, h.fixture.walker.getStatus());
            assertEquals("PLANNING", h.fixture.walker.getOwnedWalkStatus(h.request.id));
            assertFalse(h.fixture.walker.cancelOwnedWalk(h.request.id, "BLOCKED"));
            assertSame(h.request, h.owner.current());
            assertEquals(0, h.engine.cancels);
            assertEquals(0, h.engine.reads);
        }
    }

    @Test void candidatesPublishOnlyAfterPreparationAndReservationMasksBlocked() throws Exception {
        try (Harness h = new Harness()) {
            h.installNearby();
            AtomicBoolean inspected = new AtomicBoolean();
            h.engine.planner.onStandable = () -> {
                assertFalse(Thread.holdsLock(h.owner), "Candidate construction must not hold the ownership monitor");
                assertNull(uncheckedGet(h.nearby, "candidates"), "No partially filtered candidate list may escape");
                assertEquals(EfficientWalker.Status.PLANNING, h.fixture.walker.getStatus());
                inspected.set(true);
            };
            // A final PREVIEW publication makes tryNext stop before issuing another route.
            h.fixture.playerApi.when(net.runelite.client.plugins.microbot.util.player.Rs2Player::getWorldLocation)
                .thenAnswer(call -> { h.engine.status = EfficientWalker.Status.PREVIEW; return null; });
            assertFalse((Boolean)method(h.nearby.getClass(), "tryNext").invoke(h.nearby));
            assertTrue(inspected.get());
            @SuppressWarnings("unchecked") List<WorldPoint> candidates = (List<WorldPoint>)get(h.nearby, "candidates");
            assertNotNull(candidates);
            assertFalse(candidates.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> candidates.add(TARGET));
            assertNull(get(h.nearby, "retryReservation"));
        }
    }

    @Test void replacementDuringPreparationCannotPublishOldCandidates() throws Exception {
        try (Harness h = new Harness()) {
            h.installNearby();
            AtomicBoolean replaced = new AtomicBoolean();
            h.engine.planner.onStandable = () -> {
                if (replaced.compareAndSet(false, true)) h.owner.begin(REPLACEMENT, "replacement", true);
            };
            assertFalse((Boolean)method(h.nearby.getClass(), "tryNext").invoke(h.nearby));
            assertTrue(replaced.get());
            assertNull(get(h.nearby, "candidates"));
            assertEquals(REPLACEMENT, h.owner.current().target);
            assertNull(get(h.nearby, "retryReservation"));
        }
    }

    @Test void queuedBlockedCancellationRechecksRecoveredStatus() throws Exception {
        try (Harness h = new Harness()) {
            assertEquals("BLOCKED", h.fixture.walker.getOwnedWalkStatus(h.request.id));
            h.atClientHandoff(() -> h.engine.status = EfficientWalker.Status.PREVIEW);
            assertFalse(h.fixture.walker.cancelOwnedWalk(h.request.id, "BLOCKED"));
            assertSame(h.request, h.owner.current());
            assertEquals(0, h.engine.cancels);
        }
    }

    @Test void queuedBlockedCancellationCannotCancelReplacementOwner() throws Exception {
        try (Harness h = new Harness()) {
            h.atClientHandoff(() -> h.owner.begin(REPLACEMENT, "replacement", true));
            assertFalse(h.fixture.walker.cancelOwnedWalk(h.request.id, "BLOCKED"));
            assertEquals(REPLACEMENT, h.owner.current().target);
            assertEquals(0, h.engine.cancels);
            assertEquals(0, h.engine.reads);
        }
    }

    @Test void rejectedGuardNeverReplacesCurrentRequest() throws Exception {
        try (Harness h = new Harness()) {
            long epoch = h.owner.epoch();
            assertEquals(0, h.fixture.walker.startOwnedWalk(REPLACEMENT, "rejected", () -> false));
            assertSame(h.request, h.owner.current());
            assertEquals(epoch, h.owner.epoch());
            assertEquals(0, h.engine.cancels);
        }
    }

    static final class Harness implements AutoCloseable {
        final HousePortalRecoveryTest.Fixture fixture = new HousePortalRecoveryTest.Fixture();
        final Engine engine = new Engine();
        final WalkerRequestOwnership owner;
        final WalkerRequestOwnership.Request request;
        final Object nearby;
        Harness() throws Exception {
            set(fixture.walker,"engine",engine);
            set(fixture.walker,"engineClass",Engine.class);
            owner = (WalkerRequestOwnership)get(fixture.walker,"requestOwnership");
            request = owner.begin(TARGET,"test",true);
            owner.finished(request);
            nearby = get(fixture.walker,"nearbyDestinationRecovery");
        }
        void installNearby() throws Exception {
            set(nearby,"requestedTarget",TARGET);
            set(nearby,"recoveryRequest",request);
        }
        void atClientHandoff(Runnable interleave) {
            when(fixture.client.isClientThread()).thenReturn(false);
            ClientThread thread = Microbot.getClientThread();
            doAnswer(call -> {
                interleave.run();
                return Optional.ofNullable(((Callable<?>)call.getArgument(0)).call());
            }).when(thread).runOnClientThreadOptional(any());
        }
        @Override public void close() {
            when(fixture.client.isClientThread()).thenReturn(true);
            fixture.close();
        }
    }

    public static final class Engine {
        EfficientWalker.Status status = EfficientWalker.Status.BLOCKED;
        EfficientWalker.Status[] sequence;
        int reads, cancels;
        final Planner planner = new Planner();
        public EfficientWalker.Status getStatus() {
            int index = reads++;
            return sequence == null ? status : sequence[Math.min(index,sequence.length-1)];
        }
        public EfficientWalker.PlanningFailure getPlanningFailure() { return EfficientWalker.PlanningFailure.ROUTE_UNSUPPORTED; }
        public WorldPoint getDestination() { return TARGET; }
        public void cancel() { cancels++; }
        public void shutdown() { }
    }

    public static final class Planner {
        Runnable onStandable = () -> { };
        public boolean isStandable(WorldPoint point) { onStandable.run(); return true; }
        public boolean canStep(int x,int y,int plane,int nextX,int nextY) {
            return true;
        }
    }

    private static Object uncheckedGet(Object object,String name) {
        try { return get(object,name); } catch (Exception ex) { throw new AssertionError(ex); }
    }
    private static Object get(Object object,String name) throws Exception {
        Field field=WalkerTestReflection.declaredField(object.getClass(),name);field.setAccessible(true);return field.get(object);
    }
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=WalkerTestReflection.declaredField(object.getClass(),name);field.setAccessible(true);field.set(object,value);
    }
    private static Method method(Class<?> type,String name) throws Exception {
        Method method=WalkerTestReflection.declaredMethod(type,name);method.setAccessible(true);return method;
    }
}
