package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Exclusive, instance-scoped navigation ownership. This policy never sends game input.
 * A cooperating caller must suspend its whole input loop before requesting a voyage,
 * and keep it suspended until {@link Snapshot#released} is true.
 */
public final class NavigationSessions {
    public enum State {
        REQUESTED, NAVIGATING, PAUSED, STOPPING, ARRIVED, CANCELLED, FAILED, TAKEN_OVER, RELEASE_FAILED, UNKNOWN
    }
    public enum Rejection { NONE, INVALID_CALLER, INPUT_NOT_SUSPENDED, INVALID_DESTINATION, BUSY, CLOSED }

    /** Opaque caller capability. Use the actual calling plugin instance as its identity. */
    public static final class Caller {
        private final Object identity;
        private final BooleanSupplier inputSuspended;
        private boolean closed;

        private Caller(Object identity, BooleanSupplier inputSuspended) {
            this.identity = identity;
            this.inputSuspended = inputSuspended;
        }
    }

    /** Opaque voyage capability. A stale session cannot cancel a newer owner's voyage. */
    public static final class Session {
        private final NavigationSessions registry;
        private final Caller caller;
        private final SeaPoint destination;
        private volatile Snapshot snapshot;
        private State releaseOutcome = State.CANCELLED;
        private boolean held;

        private Session(NavigationSessions registry, Caller caller, SeaPoint destination, long generation) {
            this.registry = registry;
            this.caller = caller;
            this.destination = destination;
            snapshot = new Snapshot(State.REQUESTED, "Accepted; awaiting navigation", false, destination, generation);
        }
    }

    public static final class Snapshot {
        public final State state;
        public final String detail;
        /** True means navigation input ownership ended. It does not imply the boat stopped. */
        public final boolean released;
        public final SeaPoint destination;
        public final long generation;

        private Snapshot(State state, String detail, boolean released, SeaPoint destination, long generation) {
            this.state = state;
            this.detail = detail;
            this.released = released;
            this.destination = destination;
            this.generation = generation;
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final Session session;
        public final Rejection rejection;

        private Result(Session session, Rejection rejection) {
            this.accepted = session != null;
            this.session = session;
            this.rejection = rejection;
        }
    }

    /** Capture before preparing an action; recheck at final dispatch, including queued callbacks. */
    public static final class DispatchTicket {
        private final Session session;
        private final long generation;
        private final boolean cleanup;

        private DispatchTicket(Session session, long generation, boolean cleanup) {
            this.session = session;
            this.generation = generation;
            this.cleanup = cleanup;
        }
    }

    private final Set<Caller> callers = Collections.newSetFromMap(new IdentityHashMap<>());
    private Session active;
    private long generation;
    private boolean closed;

    /** inputSuspended must be a pure, nonblocking, thread-safe supplier. */
    public synchronized Caller registerCaller(Object pluginIdentity, BooleanSupplier inputSuspended) {
        if (closed || pluginIdentity == null || inputSuspended == null) return null;
        Caller caller = new Caller(pluginIdentity, inputSuspended);
        callers.add(caller);
        return caller;
    }

    public synchronized Result request(Caller caller, SeaPoint destination) {
        if (closed) return rejected(Rejection.CLOSED);
        if (!valid(caller)) return rejected(Rejection.INVALID_CALLER);
        if (destination == null || destination.x < 1 || destination.y < 1
                || destination.x > 16383 || destination.y > 16383) return rejected(Rejection.INVALID_DESTINATION);
        if (!suspended(caller)) return rejected(Rejection.INPUT_NOT_SUSPENDED);
        if (active != null) {
            // Repeating a tick's request cannot restart a voyage or its pending actions.
            if (active.caller == caller && active.destination.equals(destination)
                    && active.snapshot.state != State.STOPPING) return new Result(active, Rejection.NONE);
            return rejected(Rejection.BUSY);
        }
        active = new Session(this, caller, destination, ++generation);
        return new Result(active, Rejection.NONE);
    }

    public synchronized Session activeSession() { return active; }

    /** Pure identity check for routing a caller lifecycle event to only its own voyage. */
    public synchronized boolean owns(Session session, Caller caller) {
        return session != null && session == active && session.caller == caller;
    }

    public synchronized Snapshot status(Session session) {
        return session == null || session.registry != this
                ? new Snapshot(State.UNKNOWN, "Unrecognized navigation session", true, null, generation)
                : session.snapshot;
    }

    public synchronized void update(Session session, String detail) {
        if (session != active || active == null || active.snapshot.state == State.STOPPING) return;
        snapshot(session, session.held ? State.PAUSED : State.NAVIGATING, detail, false);
    }

    /** Retain ownership, but invalidate every prepared input until a fresh resume. */
    public synchronized void hold(Session session, String detail) {
        if (session == null || session != active) return;
        if (!session.held) { session.held = true; ++generation; }
        snapshot(session, session.snapshot.state == State.STOPPING ? State.STOPPING : State.PAUSED, detail, false);
    }

    public synchronized void resume(Session session) {
        if (session == null || session != active || !session.held) return;
        session.held = false; ++generation;
        snapshot(session, session.snapshot.state == State.STOPPING ? State.STOPPING : State.NAVIGATING,
                "Resuming from fresh game state", false);
    }
    public synchronized boolean isHeld(Session session) {
        return session != null && session == active && session.held;
    }

    /** A temporary input hold is distinct from the caller abandoning its ownership. */
    public synchronized boolean ownerSuspended(Session session) {
        if (session == null || session != active) return false;
        if (suspended(session.caller)) return true;
        manualTakeover(session, "Caller input suspension ended before navigation release; boat stop is unconfirmed");
        return false;
    }

    /** Cancellation begins release; the controller still exclusively owns bounded stop cleanup. */
    public synchronized boolean cancel(Session session) {
        if (session == null || session != active) return false;
        return requestRelease(session, State.CANCELLED, "Cancellation requested; awaiting release");
    }

    public synchronized boolean requestRelease(Session session, State outcome, String detail) {
        if (session == null || session != active || !terminalOutcome(outcome)) return false;
        if (session.snapshot.state == State.STOPPING) return true;
        ++generation;
        session.releaseOutcome = outcome;
        snapshot(session, State.STOPPING, detail, false);
        return true;
    }

    /** Call only after observed stopping, or after the controller's bounded cleanup timed out. */
    public synchronized boolean confirmRelease(Session session, boolean stopped, String detail) {
        if (session == null || session != active || session.snapshot.state != State.STOPPING) return false;
        snapshot(session, stopped ? session.releaseOutcome : State.RELEASE_FAILED, detail, true);
        active = null;
        pruneClosed(session.caller);
        return true;
    }

    /** Arrival must mean the navigator observed arrival and the required stationary acknowledgement. */
    public synchronized boolean arrived(Session session, String detail) {
        if (session == null || session != active || session.snapshot.state == State.STOPPING) return false;
        ++generation;
        snapshot(session, State.ARRIVED, detail, true);
        active = null;
        pruneClosed(session.caller);
        return true;
    }

    /** Caller shutdown invalidates normal actions immediately; keep its loop suspended through release. */
    public synchronized boolean callerShutdown(Caller caller) {
        if (caller == null || !callers.contains(caller)) return false;
        caller.closed = true;
        if (active != null && active.caller == caller) {
            return requestRelease(active, State.CANCELLED, "Caller stopped; awaiting navigation release");
        }
        pruneClosed(caller);
        return true;
    }

    /** Human/external takeover releases without fighting the new controller with a stop click. */
    public synchronized void manualTakeover(String detail) {
        if (active == null) return;
        ++generation;
        Session old = active;
        snapshot(old, State.TAKEN_OVER, detail, true);
        active = null;
        pruneClosed(old.caller);
    }

    /** Worker callbacks may yield only their own voyage, never a newly queued caller. */
    public synchronized boolean manualTakeover(Session expected, String detail) {
        if (expected == null || expected != active) return false;
        manualTakeover(detail);
        return true;
    }

    public synchronized DispatchTicket inputTicket(Session session) {
        return session != null && session == active && session.snapshot.state != State.STOPPING
                ? new DispatchTicket(session, generation, false) : null;
    }

    public synchronized DispatchTicket cleanupTicket(Session session) {
        return session != null && session == active && session.snapshot.state == State.STOPPING
                ? new DispatchTicket(session, generation, true) : null;
    }

    public synchronized boolean canDispatch(DispatchTicket ticket) {
        if (closed || ticket == null || active == null || ticket.session != active
                || ticket.generation != generation) return false;
        if (!suspended(active.caller)) {
            // A broken handoff is a takeover, not a pause which can later resurrect
            // an old prepared command when the caller's flag becomes true again.
            manualTakeover("Caller input suspension ended before navigation release; boat stop is unconfirmed");
            return false;
        }
        if (active.held) return false;
        return ticket.cleanup ? active.snapshot.state == State.STOPPING
                : !active.caller.closed && active.snapshot.state != State.STOPPING;
    }

    /** Only this exact plugin's cooperative input gate can exempt it from the competitor guard. */
    public synchronized boolean cooperating(Object pluginIdentity) {
        return active != null && active.caller.identity == pluginIdentity && suspended(active.caller);
    }

    /** Plugin/service shutdown cannot promise a stop after the worker has been removed. */
    public synchronized void close(String detail) {
        manualTakeover(detail);
        closed = true;
        for (Caller caller : callers) caller.closed = true;
        callers.clear();
    }

    private boolean valid(Caller caller) {
        return caller != null && callers.contains(caller) && !caller.closed;
    }

    private static boolean suspended(Caller caller) {
        try { return caller.inputSuspended.getAsBoolean(); }
        catch (RuntimeException ex) { return false; }
    }

    private void snapshot(Session session, State state, String detail, boolean released) {
        session.snapshot = new Snapshot(state, detail == null ? "" : detail, released, session.destination, generation);
    }

    private void pruneClosed(Caller caller) { if (caller.closed) callers.remove(caller); }
    private static Result rejected(Rejection reason) { return new Result(null, reason); }
    private static boolean terminalOutcome(State outcome) {
        return outcome == State.CANCELLED || outcome == State.FAILED;
    }
}
