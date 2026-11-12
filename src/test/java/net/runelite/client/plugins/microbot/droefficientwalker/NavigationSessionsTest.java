package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class NavigationSessionsTest {
    private static final SeaPoint DESTINATION = new SeaPoint(3500, 2100);

    @Test
    public void enabledIdleHasNoInputCapabilityAndUnsuspendedRequestsFail() {
        NavigationSessions registry = new NavigationSessions();
        Object plugin = new Object();
        NavigationSessions.Caller caller = registry.registerCaller(plugin, () -> false);
        assertNull(registry.activeSession());
        assertFalse(registry.cooperating(plugin));
        assertFalse(registry.canDispatch(null));
        assertEquals(NavigationSessions.Rejection.INPUT_NOT_SUSPENDED, registry.request(caller, DESTINATION).rejection);
        assertNull(registry.activeSession());
    }

    @Test
    public void repeatedRequestsAreIdempotentAndOtherOwnersCannotReplaceOrCancel() {
        NavigationSessions registry = new NavigationSessions();
        Object plugin = new Object();
        NavigationSessions.Caller first = registry.registerCaller(plugin, () -> true);
        NavigationSessions.Caller second = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Result accepted = registry.request(first, DESTINATION);
        NavigationSessions.DispatchTicket ticket = registry.inputTicket(accepted.session);
        assertSame(accepted.session, registry.request(first, DESTINATION).session);
        assertTrue(registry.canDispatch(ticket));
        assertEquals(NavigationSessions.Rejection.BUSY, registry.request(second, DESTINATION).rejection);
        assertEquals(NavigationSessions.Rejection.BUSY, registry.request(first, DESTINATION.add(20, 0)).rejection);
        assertTrue(registry.cooperating(plugin));
        assertFalse(registry.cooperating(new Object()));
        NavigationSessions other = new NavigationSessions();
        assertFalse(other.cancel(accepted.session));
        assertEquals(NavigationSessions.State.UNKNOWN, other.status(accepted.session).state);
        assertTrue(registry.canDispatch(ticket));
    }

    @Test
    public void cancellationKeepsExclusiveStopOwnershipAndInvalidatesPreparedActions() {
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller first = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Caller second = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Session old = registry.request(first, DESTINATION).session;
        NavigationSessions.DispatchTicket beforeCancel = registry.inputTicket(old);
        assertTrue(registry.cancel(old));
        assertTrue(registry.cancel(old));
        assertFalse(registry.canDispatch(beforeCancel));
        assertNull(registry.inputTicket(old));
        NavigationSessions.DispatchTicket cleanup = registry.cleanupTicket(old);
        assertTrue(registry.canDispatch(cleanup));
        assertFalse(registry.status(old).released);
        assertEquals(NavigationSessions.Rejection.BUSY, registry.request(second, DESTINATION).rejection);
        assertTrue(registry.confirmRelease(old, true, "Observed stationary boat"));
        assertEquals(NavigationSessions.State.CANCELLED, registry.status(old).state);
        assertTrue(registry.status(old).released);
        NavigationSessions.Session current = registry.request(second, DESTINATION).session;
        assertNotNull(current);
        assertFalse(registry.cancel(old));
        assertFalse(registry.canDispatch(cleanup));
        assertTrue(registry.canDispatch(registry.inputTicket(current)));
    }

    @Test
    public void callerShutdownInvalidatesNormalInputAndWaitsForRelease() {
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        NavigationSessions.DispatchTicket queued = registry.inputTicket(session);
        assertTrue(registry.callerShutdown(caller));
        assertFalse(registry.canDispatch(queued));
        assertTrue(registry.canDispatch(registry.cleanupTicket(session)));
        assertEquals(NavigationSessions.Rejection.INVALID_CALLER, registry.request(caller, DESTINATION).rejection);
        assertTrue(registry.confirmRelease(session, true, "Caller stopped; boat stationary"));
        assertNull(registry.activeSession());
    }

    @Test
    public void unrelatedCallerShutdownCannotClaimAnExistingOwnersRelease() {
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller owner = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Caller unrelated = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Session session = registry.request(owner, DESTINATION).session;
        assertTrue(registry.cancel(session));
        NavigationSessions.DispatchTicket cleanup = registry.cleanupTicket(session);
        assertTrue(registry.owns(session, owner));
        assertFalse(registry.owns(session, unrelated));
        assertTrue(registry.callerShutdown(unrelated));
        assertTrue(registry.canDispatch(cleanup));
        assertSame(session, registry.activeSession());
        assertFalse(registry.status(session).released);
        assertTrue(registry.confirmRelease(session, true, "Owner release confirmed"));
        assertFalse(registry.owns(session, owner));
    }

    @Test
    public void manualTakeoverAndLostCallerSuspensionPreventAllLateDispatch() {
        NavigationSessions registry = new NavigationSessions();
        AtomicBoolean suspended = new AtomicBoolean(true);
        Object plugin = new Object();
        NavigationSessions.Caller caller = registry.registerCaller(plugin, suspended::get);
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        NavigationSessions.DispatchTicket queued = registry.inputTicket(session);
        suspended.set(false);
        assertFalse(registry.canDispatch(queued));
        assertFalse(registry.cooperating(plugin));
        suspended.set(true);
        assertFalse(registry.canDispatch(queued)); // Suspension cannot resurrect this voyage.
        assertEquals(NavigationSessions.State.TAKEN_OVER, registry.status(session).state);
        registry.manualTakeover("Human owns input; stopping unconfirmed");
        suspended.set(true);
        assertFalse(registry.canDispatch(queued));
        assertNull(registry.cleanupTicket(session));
        assertTrue(registry.status(session).released);
        assertEquals(NavigationSessions.State.TAKEN_OVER, registry.status(session).state);
        assertFalse(registry.arrived(session, "Late arrival callback"));
    }

    @Test
    public void cleanupLosesOwnershipPermanentlyWhenItsCallerResumesInput() {
        NavigationSessions registry = new NavigationSessions();
        AtomicBoolean suspended = new AtomicBoolean(true);
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), suspended::get);
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        assertTrue(registry.cancel(session));
        NavigationSessions.DispatchTicket cleanup = registry.cleanupTicket(session);
        suspended.set(false);
        assertFalse(registry.canDispatch(cleanup));
        suspended.set(true);
        assertFalse(registry.canDispatch(cleanup));
        assertNull(registry.activeSession());
        assertEquals(NavigationSessions.State.TAKEN_OVER, registry.status(session).state);
    }

    @Test
    public void stopTimeoutIsReportedSeparatelyFromAConfirmedStop() {
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        assertTrue(registry.requestRelease(session, NavigationSessions.State.FAILED, "Chart unavailable"));
        assertTrue(registry.confirmRelease(session, false, "Stop unconfirmed after bounded cleanup"));
        assertEquals(NavigationSessions.State.RELEASE_FAILED, registry.status(session).state);
        assertTrue(registry.status(session).released);
        assertNull(registry.activeSession());
    }

    @Test
    public void staleArrivalAndServiceShutdownCannotAffectANewerSession() {
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), () -> true);
        NavigationSessions.Session old = registry.request(caller, DESTINATION).session;
        assertTrue(registry.arrived(old, "Arrived stationary"));
        NavigationSessions.Session current = registry.request(caller, DESTINATION.add(10, 0)).session;
        assertFalse(registry.arrived(old, "Delayed old callback"));
        assertSame(current, registry.activeSession());
        NavigationSessions.DispatchTicket queued = registry.inputTicket(current);
        registry.close("Navigator disabled; boat stop unconfirmed");
        registry.close("Repeated shutdown");
        assertFalse(registry.canDispatch(queued));
        assertEquals(NavigationSessions.State.TAKEN_OVER, registry.status(current).state);
        assertEquals(NavigationSessions.Rejection.CLOSED, registry.request(caller, DESTINATION).rejection);
    }

    @Test
    public void salvagerWholeLoopCanLendNavigationAndResumeAfterStationaryArrival() {
        // The actual DroSailingScript calls tickProfile before controller.tick. Both
        // calls belong inside this gate, as do any separate input-producing tasks.
        CooperativeInputGate gate = new CooperativeInputGate();
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), gate::isSuspended);
        AtomicInteger profileInputs = new AtomicInteger();
        AtomicInteger salvageInputs = new AtomicInteger();
        callerTick(gate, profileInputs::incrementAndGet, salvageInputs::incrementAndGet);
        assertEquals(1, profileInputs.get());
        assertEquals(1, salvageInputs.get());

        CooperativeInputGate.Step inProgress = gate.enterStep();
        assertNotNull(inProgress);
        assertFalse(gate.trySuspend()); // Never lend while an old helper is in flight.
        inProgress.close();
        inProgress.close();
        assertTrue(gate.trySuspend());
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        assertNotNull(session);
        callerTick(gate, profileInputs::incrementAndGet, salvageInputs::incrementAndGet);
        assertEquals(1, profileInputs.get());
        assertEquals(1, salvageInputs.get());
        assertFalse(gate.resumeAfterRelease(registry.status(session)));
        assertTrue(registry.canDispatch(registry.inputTicket(session)));
        assertTrue(registry.arrived(session, "Observed stationary arrival near wreck"));
        assertTrue(gate.resumeAfterRelease(registry.status(session)));
        callerTick(gate, profileInputs::incrementAndGet, salvageInputs::incrementAndGet);
        assertEquals(2, profileInputs.get());
        assertEquals(2, salvageInputs.get());
    }

    @Test
    public void handoffGateStaysSuspendedOnHumanTakeoverOrUnconfirmedStop() {
        CooperativeInputGate gate = new CooperativeInputGate();
        NavigationSessions registry = new NavigationSessions();
        NavigationSessions.Caller caller = registry.registerCaller(new Object(), gate::isSuspended);
        assertTrue(gate.trySuspend());
        NavigationSessions.Session session = registry.request(caller, DESTINATION).session;
        registry.manualTakeover("Human steering");
        assertFalse(gate.resumeAfterRelease(registry.status(session)));
        assertNull(gate.enterStep());
    }

    private static void callerTick(CooperativeInputGate gate, Runnable profile, Runnable controller) {
        try (CooperativeInputGate.Step step = gate.enterStep()) {
            if (step == null) return;
            profile.run();
            controller.run();
        }
    }
}
