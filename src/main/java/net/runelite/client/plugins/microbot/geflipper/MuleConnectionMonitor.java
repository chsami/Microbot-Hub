package net.runelite.client.plugins.microbot.geflipper;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owns read-only background health checks; rendering reads only the immutable snapshot. */
final class MuleConnectionMonitor implements AutoCloseable {
    static final long PROBE_INTERVAL_MS = 15_000L;
    static final long FRESHNESS_MS = MuleNetwork.CONTACT_FRESHNESS_MS;

    enum State {
        OFF("Off"), CHECKING("Checking"), OFFLINE("No reply"), NEEDS_SETUP("Needs setup"),
        WRONG_MULE("No matching Mule"), STANDBY("Connected (standby)"), READY("Connected (ready)"),
        BUSY("Connected (busy)"), LISTENING("Listening (no sender contact)"),
        SENDER_SEEN("Sender connected"), STALE("No recent reply");

        private final String label;
        State(String label) { this.label = label; }
    }

    static final class Snapshot {
        final State state;
        final boolean connected;
        final boolean availableForPreparation;
        final long checkedAtMillis;

        Snapshot(State state, boolean connected, boolean availableForPreparation, long checkedAtMillis) {
            this.state = state;
            this.connected = connected;
            this.availableForPreparation = availableForPreparation;
            this.checkedAtMillis = checkedAtMillis;
        }

        String label() { return state.label; }
    }

    @FunctionalInterface
    interface Probe {
        MuleNetwork.MuleReply check(String expectedMuleRsn, String flipperRsn, int port);
    }

    private final Probe probe;
    private final LongSupplier clock;
    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> task;
    private SenderTarget target;
    private Snapshot current = new Snapshot(State.OFF, false, false, 0);
    private long generation;
    private boolean closed;
    private boolean receiver;

    MuleConnectionMonitor(Probe probe) {
        this(probe, () -> System.nanoTime() / 1_000_000L,
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Geflipper-Mule-Health");
                thread.setDaemon(true);
                return thread;
            }));
    }

    MuleConnectionMonitor(Probe probe, LongSupplier clock, ScheduledExecutorService worker) {
        this.probe = Objects.requireNonNull(probe);
        this.clock = Objects.requireNonNull(clock);
        this.worker = Objects.requireNonNull(worker);
    }

    synchronized void updateSender(long account, String ownRsn, String expectedMuleRsn, int port) {
        if (closed) return;
        SenderTarget next = new SenderTarget(account, ownRsn, expectedMuleRsn, MulePolicy.clampPort(port));
        if (!receiver && next.equals(target)) return;
        cancelTask();
        receiver = false;
        target = next;
        long expectedGeneration = ++generation;
        if (next.account == 0 || next.ownRsn.isEmpty() || next.expectedMuleRsn.isEmpty()
            || MulePolicy.sameRsn(next.ownRsn, next.expectedMuleRsn)) {
            current = new Snapshot(State.NEEDS_SETUP, false, false, clock.getAsLong());
            return;
        }
        current = new Snapshot(State.CHECKING, false, false, clock.getAsLong());
        // One worker and fixed delay prevent overlapping or accumulating health requests.
        task = worker.scheduleWithFixedDelay(() -> poll(next, expectedGeneration),
            0, PROBE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void updateReceiver(MuleNetwork.ServerHealth health) {
        if (closed) return;
        if (!receiver) {
            cancelTask();
            generation++;
            target = null;
            receiver = true;
        }
        State state = health == null || !health.listening ? State.OFFLINE
            : !health.identityKnown || !health.worldKnown ? State.NEEDS_SETUP
            : health.leaseActive ? State.BUSY
            : health.recentSenderContact ? State.SENDER_SEEN : State.LISTENING;
        current = new Snapshot(state, state == State.BUSY || state == State.SENDER_SEEN,
            false, clock.getAsLong());
    }

    private void poll(SenderTarget expectedTarget, long expectedGeneration) {
        synchronized (this) {
            if (closed || receiver || generation != expectedGeneration || !expectedTarget.equals(target)) return;
        }
        MuleNetwork.MuleReply reply;
        try {
            reply = probe.check(expectedTarget.expectedMuleRsn, expectedTarget.ownRsn, expectedTarget.port);
        } catch (RuntimeException unavailable) {
            reply = null;
        }
        Snapshot checked = classify(reply, expectedTarget, clock.getAsLong());
        synchronized (this) {
            // A cancelled/old request cannot relabel the replacement role, account or target.
            if (!closed && !receiver && generation == expectedGeneration && expectedTarget.equals(target)) {
                current = checked;
            }
        }
    }

    private static Snapshot classify(MuleNetwork.MuleReply reply, SenderTarget target, long now) {
        State state;
        if (reply == null || !reply.responding) state = State.OFFLINE;
        else if (reply.muleRsn.isEmpty()) state = State.NEEDS_SETUP;
        else if (!MulePolicy.sameRsn(reply.muleRsn, target.expectedMuleRsn)) state = State.WRONG_MULE;
        else if (!reply.ok || reply.world <= 0) state = State.NEEDS_SETUP;
        else if (!reply.leaseOwner.isEmpty() && !MulePolicy.sameRsn(reply.leaseOwner, target.ownRsn)) state = State.BUSY;
        else state = reply.ready ? State.READY : State.STANDBY;
        boolean available = state == State.READY || state == State.STANDBY;
        return new Snapshot(state, available || state == State.BUSY, available, now);
    }

    synchronized Snapshot snapshot() {
        long now = clock.getAsLong();
        if (current.connected && (now < current.checkedAtMillis || now - current.checkedAtMillis >= FRESHNESS_MS)) {
            return new Snapshot(State.STALE, false, false, current.checkedAtMillis);
        }
        return current;
    }

    synchronized void disable() {
        cancelTask();
        generation++;
        target = null;
        receiver = false;
        current = new Snapshot(State.OFF, false, false, clock.getAsLong());
    }

    void reset() { disable(); }

    private void cancelTask() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        disable();
        closed = true;
        worker.shutdownNow();
    }

    private static final class SenderTarget {
        final long account;
        final String ownRsn;
        final String expectedMuleRsn;
        final int port;

        SenderTarget(long account, String ownRsn, String expectedMuleRsn, int port) {
            this.account = account;
            this.ownRsn = MulePolicy.tradeRequestName(ownRsn, "");
            this.expectedMuleRsn = MulePolicy.tradeRequestName(expectedMuleRsn, "");
            this.port = port;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof SenderTarget)) return false;
            SenderTarget that = (SenderTarget) other;
            return account == that.account && port == that.port
                && ownRsn.equals(that.ownRsn) && expectedMuleRsn.equals(that.expectedMuleRsn);
        }

        @Override public int hashCode() { return Objects.hash(account, ownRsn, expectedMuleRsn, port); }
    }
}
