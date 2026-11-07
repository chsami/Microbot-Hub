package net.runelite.client.plugins.microbot.geflipper;

import java.util.function.LongSupplier;

/**
 * Display-only totals for the Mule receiver: GP received this session, GP received all time, and the
 * coins last seen on the account. It only records what the receiver already verified; it never makes or
 * influences a trade decision.
 *
 * Totals that must survive a restart go through {@link Store}. They belong to the RuneLite profile, and
 * to the account that was last seen using them: if a different account appears on the same profile, the
 * saved totals restart instead of mixing two accounts together.
 */
final class MuleLedger {
    /** Rates over a shorter window are noise, so the first minute is averaged over a full minute. */
    static final long MIN_RATE_WINDOW_MS = 60_000L;
    /** Coin balances can change on every tick; persist them at most this often. Receipts always save. */
    static final long SAVE_INTERVAL_MS = 1_000L;
    private static final double HOUR_MS = 3_600_000.0;
    private static final long UNKNOWN_ACCOUNT = -1L;

    interface Store {
        /** Never null; unreadable or missing values come back as {@link State#EMPTY}. */
        State load();

        void save(State state);

        /** Immutable configuration-profile identity; unknown stores are only useful to isolated callers. */
        default long profileId() { return Long.MIN_VALUE; }
    }

    /** What survives a restart. {@code inventoryGp} is -1 until coins have been seen. */
    static final class State {
        static final State EMPTY = new State(0L, -1L, 0L, 0L);

        final long account;
        final long inventoryGp;
        final long bankGp;
        final long lifetimeGp;

        State(long account, long inventoryGp, long bankGp, long lifetimeGp) {
            this.account = account;
            this.inventoryGp = Math.max(-1L, inventoryGp);
            this.bankGp = Math.max(0L, bankGp);
            this.lifetimeGp = Math.max(0L, lifetimeGp);
        }
    }

    /** Immutable view for rendering. {@code heldGp} is -1 when no coin count was ever seen. */
    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(0L, 0L, -1L, 0L);

        final long sessionGp;
        final long lifetimeGp;
        final long heldGp;
        final long sessionMillis;

        Snapshot(long sessionGp, long lifetimeGp, long heldGp, long sessionMillis) {
            this.sessionGp = Math.max(0L, sessionGp);
            this.lifetimeGp = Math.max(0L, lifetimeGp);
            this.heldGp = Math.max(-1L, heldGp);
            this.sessionMillis = Math.max(0L, sessionMillis);
        }

        /** GP received per hour of session time, counting the time spent logged out waiting for senders. */
        long gpPerHour() {
            if (sessionGp <= 0) return 0L;
            double perHour = sessionGp * HOUR_MS / Math.max(sessionMillis, MIN_RATE_WINDOW_MS);
            return perHour >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) perHour;
        }
    }

    private final LongSupplier clock;
    private final Object writeLock = new Object();
    private Store store;
    private long account;
    private long inventoryGp = -1L;
    private long bankGp;
    private long lifetimeGp;
    private long sessionGp;
    private long sessionStart;
    private boolean dirty;
    private boolean everSaved;
    private long lastSaveAt;

    MuleLedger() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    MuleLedger(LongSupplier clock) {
        this.clock = clock;
        this.sessionStart = clock.getAsLong();
    }

    /** Chooses where totals persist. The saved values are read by the next {@link #startSession()}. */
    synchronized void attachStore(Store store) {
        this.store = store;
    }

    /**
     * Begins a new session: the session total restarts and the saved totals are read again. Anything still
     * unsaved is dropped rather than written, because after a profile switch the store points at another
     * profile; {@link #flush()} before shutting down instead.
     */
    void startSession() {
        startSession(null, false);
    }

    /** Replaces the profile-bound store and reloads atomically with respect to production callbacks. */
    void startSession(Store replacement) {
        startSession(replacement, true);
    }

    private void startSession(Store replacement, boolean replace) {
        synchronized (writeLock) {
            Store source;
            synchronized (this) {
                source = replace ? replacement : store;
            }
            State saved = State.EMPTY;
            if (source != null) {
                try {
                    State loaded = source.load();
                    if (loaded != null) saved = loaded;
                } catch (RuntimeException unavailable) {
                    saved = State.EMPTY;
                }
            }
            synchronized (this) {
                if (replace) store = source;
                account = saved.account;
                inventoryGp = saved.inventoryGp;
                bankGp = saved.bankGp;
                lifetimeGp = saved.lifetimeGp;
                sessionGp = 0L;
                sessionStart = clock.getAsLong();
                dirty = false;
                everSaved = false;
            }
        }
    }

    /** One confirmed incoming trade. */
    void recordReceivedForProfile(long profileId, long observedAccount, long gp) {
        synchronized (writeLock) {
            synchronized (this) {
                if (profileId == Long.MIN_VALUE || store == null || store.profileId() != profileId) return;
            }
            recordReceived(observedAccount, gp);
        }
    }

    void observeHoldingsForProfile(long profileId, long observedAccount, long inventoryCoins,
                                   long bankCoins, boolean bankKnown) {
        synchronized (writeLock) {
            synchronized (this) {
                if (profileId == Long.MIN_VALUE || store == null || store.profileId() != profileId) return;
            }
            observeHoldings(observedAccount, inventoryCoins, bankCoins, bankKnown);
        }
    }

    void recordReceived(long observedAccount, long gp) {
        if (gp <= 0L) return;
        synchronized (this) {
            bind(observedAccount);
            sessionGp = saturatingAdd(sessionGp, gp);
            lifetimeGp = saturatingAdd(lifetimeGp, gp);
            dirty = true;
        }
        persist(true);
    }

    /**
     * A verified reading with no trade open. A bank balance is only replaced when the bank was actually
     * readable; an unloaded bank keeps the last known balance instead of looking like a withdrawal.
     */
    void observeHoldings(long observedAccount, long inventoryCoins, long bankCoins, boolean bankKnown) {
        synchronized (this) {
            bind(observedAccount);
            long inventory = Math.max(0L, inventoryCoins);
            long bank = bankKnown ? Math.max(0L, bankCoins) : bankGp;
            if (inventory != inventoryGp || bank != bankGp) {
                inventoryGp = inventory;
                bankGp = bank;
                dirty = true;
            }
        }
        persist(false);
    }

    /** Writes anything still waiting, ignoring the save interval. */
    void flush() {
        persist(true);
    }

    synchronized Snapshot snapshot() {
        long elapsed = Math.max(0L, clock.getAsLong() - sessionStart);
        long held = inventoryGp < 0L ? -1L : saturatingAdd(inventoryGp, bankGp);
        return new Snapshot(sessionGp, lifetimeGp, held, elapsed);
    }

    /** Totals belong to one account; a different account on the same profile starts its own. */
    private void bind(long observed) {
        // 0 and -1 both mean the client has not reported an account yet; neither one is an account.
        if (observed == 0L || observed == UNKNOWN_ACCOUNT || observed == account) return;
        if (account != 0L) {
            inventoryGp = -1L;
            bankGp = 0L;
            lifetimeGp = 0L;
            sessionGp = 0L;
            sessionStart = clock.getAsLong();
        }
        account = observed;
        dirty = true;
    }

    /** Store writes run outside the ledger lock so a slow config listener cannot stall rendering. */
    private void persist(boolean force) {
        synchronized (writeLock) {
            Store target;
            State state;
            synchronized (this) {
                if (store == null || !dirty) return;
                long now = clock.getAsLong();
                if (!force && everSaved && now >= lastSaveAt && now - lastSaveAt < SAVE_INTERVAL_MS) return;
                target = store;
                state = new State(account, inventoryGp, bankGp, lifetimeGp);
                dirty = false;
                everSaved = true;
                lastSaveAt = now;
            }
            try {
                target.save(state);
            } catch (RuntimeException unavailable) {
                // Display-only: a failed write is retried by the next change.
                synchronized (this) {
                    dirty = true;
                }
            }
        }
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }
}
