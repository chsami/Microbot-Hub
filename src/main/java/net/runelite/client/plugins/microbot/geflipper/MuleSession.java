package net.runelite.client.plugins.microbot.geflipper;

import java.util.Random;

/**
 * Coordinates the Flipper (sender) and Mule (receiver) state machines.
 *
 * Design rules (keep the pattern human-like and quiet):
 * - Only the Flipper initiates requests; the Mule reciprocates a fresh authorized in-game invitation once.
 * - The Mule stays logged out and only logs in while a Flipper holds a lease; it logs out again afterwards.
 * - The Flipper sells down to the trigger amount first (cancel buys, sell-only) before travelling.
 */
final class MuleSession {
    enum Phase {
        IDLE, WAITING_FOR_MULE, PREPARING_CASH, HOPPING_TO_MULE, WALKING_TO_MULE,
        OPENING_TRADE, OFFERING_COINS, CONFIRMING_TRADE, RETURNING
    }

    static final long ELIGIBLE_DEBOUNCE_MS = 5_000L;
    static final long PROBE_RETRY_MS = 60_000L;
    static final long PHASE_TIMEOUT_MS = 90_000L;
    static final long WAIT_FOR_MULE_TIMEOUT_MS = 4 * 60_000L;
    static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    static final long LIQUIDATE_TIMEOUT_MS = 45 * 60_000L;
    static final long COOLDOWN_AFTER_SUCCESS_MS = 10 * 60_000L;
    static final long COOLDOWN_AFTER_ABORT_MS = 8 * 60_000L;
    static final long COOLDOWN_MULE_OFFLINE_MS = 5 * 60_000L;
    static final long TRADE_RETRY_BASE_MS = 15_000L;
    static final int MAX_TRADE_REQUESTS = 3;
    static final int MAX_HOP_ATTEMPTS = 5;
    static final long HOP_RETRY_BASE_MS = 8_000L;
    static final long WALK_RETRY_BASE_MS = 4_000L;
    static final long LOGIN_RETRY_BASE_MS = 25_000L;
    static final long MULE_UNSEEN_TIMEOUT_MS = 40_000L;
    /** How long a low working-capital reading must last before a transfer is cancelled. */
    static final long CAPITAL_RECHECK_MS = 10_000L;
    static final long MULE_LOGOUT_DELAY_BASE_MS = 12_000L;
    static final long TRADE_REQUEST_MAX_AGE_MS = 10_000L;
    static final long RECEIPT_SETTLE_MS = 5_000L;

    interface Port {
        Snapshot read();
        boolean allowed();
        void prepareBoundary();
        void closeInterfaces();
        void withdrawBankCoins(int needed);
        void walkTo(int x, int y, int plane);
        boolean hopToWorld(int world);
        boolean playerNear(String rsn, int tiles);
        boolean tradePlayer(String rsn);
        boolean offerCoins(int amount, int inventoryCoins);
        boolean acceptTradeMain();
        boolean acceptTradeConfirm();
        boolean declineTrade();
        void logout();
        void login(int world);
        int profileWorld();

        MuleNetwork.MuleReply requestMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp);
        MuleNetwork.MuleReply heartbeatMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp);
        void releaseMuleLease(String host, int port, String muleRsn, String flipperRsn, boolean completed);

        boolean ensureServer(int port);
        void updateServerState(boolean ready, String muleRsn, int world, int x, int y, int plane);
        MuleNetwork.Lease activeServerLease();
        void clearServerLease(MuleNetwork.Lease expected);
        void stopServer();
        void reset();
    }

    static final class SenderSettings {
        final boolean enabled;
        /** Start a transfer once working capital reaches this. Below the keep amount it means "same as keep". */
        final int thresholdGp;
        /** Working capital kept for flipping; a transfer sends only what is above it. */
        final int reserveGp;
        /** What the transfer before an OSRS update keeps: -1 = the keep amount above, 0 = send all loose GP. */
        final int updateReserveGp;
        final String muleRsn;
        final String host;
        final int port;
        final int fixedWorld;
        final MulePolicy.TileSpec location;

        SenderSettings(boolean enabled, int thresholdGp, int reserveGp, String muleRsn,
                       String host, int port, int fixedWorld, MulePolicy.TileSpec location) {
            this(enabled, thresholdGp, reserveGp, -1, muleRsn, host, port, fixedWorld, location);
        }

        SenderSettings(boolean enabled, int thresholdGp, int reserveGp, int updateReserveGp, String muleRsn,
                       String host, int port, int fixedWorld, MulePolicy.TileSpec location) {
            this.enabled = enabled;
            this.thresholdGp = thresholdGp;
            this.reserveGp = reserveGp;
            this.updateReserveGp = updateReserveGp < 0 ? -1 : updateReserveGp;
            this.muleRsn = muleRsn == null ? "" : muleRsn.trim();
            this.host = host == null ? "auto" : host;
            this.port = port;
            this.fixedWorld = Math.max(0, fixedWorld);
            this.location = location == null ? MulePolicy.TileSpec.auto() : location;
        }
    }

    static final class ReceiverSettings {
        final int port;
        final int fixedWorld;
        final MulePolicy.TileSpec location;
        final String ownRsn;

        ReceiverSettings(int port, int fixedWorld, MulePolicy.TileSpec location, String ownRsn) {
            this.port = port;
            this.fixedWorld = Math.max(0, fixedWorld);
            this.location = location == null ? MulePolicy.TileSpec.auto() : location;
            this.ownRsn = ownRsn == null ? "" : ownRsn.trim();
        }
    }

    static final class Snapshot {
        static final long UNKNOWN_CONFIGURATION_PROFILE_ID = Long.MIN_VALUE;
        final long configurationProfileId;
        final boolean loggedIn;
        final long account;
        final String playerName;
        final int currentWorld;
        final int tileX;
        final int tileY;
        final int plane;
        final boolean atGrandExchange;
        final boolean exchangeOpen;
        final boolean offerOpen;
        final boolean bankOpen;
        final int inventoryCoins;
        final int knownBankCoins;
        final long wealthGp;
        final boolean capitalVerified;
        final boolean capitalComplete;
        final boolean hasActiveBuys;
        final boolean tradeMainOpen;
        final boolean tradeConfirmOpen;
        final String tradePartnerName;
        final int ourOfferedCoins;
        final boolean ourOfferedOtherItems;
        final int otherOfferedCoins;

        Snapshot(boolean loggedIn, long account, String playerName, int currentWorld,
                 int tileX, int tileY, int plane, boolean atGrandExchange,
                 boolean exchangeOpen, boolean offerOpen, boolean bankOpen,
                 int inventoryCoins, int knownBankCoins, long wealthGp, boolean hasActiveBuys,
                 boolean tradeMainOpen, boolean tradeConfirmOpen, String tradePartnerName,
                 int ourOfferedCoins, boolean ourOfferedOtherItems, int otherOfferedCoins) {
            this(loggedIn, account, playerName, currentWorld, tileX, tileY, plane, atGrandExchange,
                exchangeOpen, offerOpen, bankOpen, inventoryCoins, knownBankCoins, wealthGp, hasActiveBuys,
                tradeMainOpen, tradeConfirmOpen, tradePartnerName, ourOfferedCoins, ourOfferedOtherItems,
                otherOfferedCoins, true);
        }

        Snapshot(boolean loggedIn, long account, String playerName, int currentWorld,
                 int tileX, int tileY, int plane, boolean atGrandExchange,
                 boolean exchangeOpen, boolean offerOpen, boolean bankOpen,
                 int inventoryCoins, int knownBankCoins, long wealthGp, boolean hasActiveBuys,
                 boolean tradeMainOpen, boolean tradeConfirmOpen, String tradePartnerName,
                 int ourOfferedCoins, boolean ourOfferedOtherItems, int otherOfferedCoins, boolean capitalVerified) {
            this(loggedIn, account, playerName, currentWorld, tileX, tileY, plane, atGrandExchange,
                exchangeOpen, offerOpen, bankOpen, inventoryCoins, knownBankCoins, wealthGp, hasActiveBuys,
                tradeMainOpen, tradeConfirmOpen, tradePartnerName, ourOfferedCoins, ourOfferedOtherItems,
                otherOfferedCoins, capitalVerified, capitalVerified);
        }

        Snapshot(boolean loggedIn, long account, String playerName, int currentWorld,
                 int tileX, int tileY, int plane, boolean atGrandExchange,
                 boolean exchangeOpen, boolean offerOpen, boolean bankOpen,
                 int inventoryCoins, int knownBankCoins, long wealthGp, boolean hasActiveBuys,
                 boolean tradeMainOpen, boolean tradeConfirmOpen, String tradePartnerName,
                 int ourOfferedCoins, boolean ourOfferedOtherItems, int otherOfferedCoins,
                 boolean capitalVerified, boolean capitalComplete) {
            this(loggedIn, account, playerName, currentWorld, tileX, tileY, plane, atGrandExchange,
                exchangeOpen, offerOpen, bankOpen, inventoryCoins, knownBankCoins, wealthGp, hasActiveBuys,
                tradeMainOpen, tradeConfirmOpen, tradePartnerName, ourOfferedCoins, ourOfferedOtherItems,
                otherOfferedCoins, capitalVerified, capitalComplete, UNKNOWN_CONFIGURATION_PROFILE_ID);
        }

        Snapshot(boolean loggedIn, long account, String playerName, int currentWorld,
                 int tileX, int tileY, int plane, boolean atGrandExchange,
                 boolean exchangeOpen, boolean offerOpen, boolean bankOpen,
                 int inventoryCoins, int knownBankCoins, long wealthGp, boolean hasActiveBuys,
                 boolean tradeMainOpen, boolean tradeConfirmOpen, String tradePartnerName,
                 int ourOfferedCoins, boolean ourOfferedOtherItems, int otherOfferedCoins,
                 boolean capitalVerified, boolean capitalComplete, long configurationProfileId) {
            this.configurationProfileId = configurationProfileId;
            this.loggedIn = loggedIn;
            this.account = account;
            this.playerName = playerName == null ? "" : playerName;
            this.currentWorld = currentWorld;
            this.tileX = tileX;
            this.tileY = tileY;
            this.plane = plane;
            this.atGrandExchange = atGrandExchange;
            this.exchangeOpen = exchangeOpen;
            this.offerOpen = offerOpen;
            this.bankOpen = bankOpen;
            this.inventoryCoins = Math.max(0, inventoryCoins);
            this.knownBankCoins = Math.max(0, knownBankCoins);
            this.wealthGp = Math.max(0, wealthGp);
            this.capitalVerified = capitalVerified && wealthGp >= 0;
            this.capitalComplete = this.capitalVerified && capitalComplete;
            this.hasActiveBuys = hasActiveBuys;
            this.tradeMainOpen = tradeMainOpen;
            this.tradeConfirmOpen = tradeConfirmOpen;
            this.tradePartnerName = tradePartnerName == null ? "" : tradePartnerName;
            this.ourOfferedCoins = Math.max(0, ourOfferedCoins);
            this.ourOfferedOtherItems = ourOfferedOtherItems;
            this.otherOfferedCoins = Math.max(0, otherOfferedCoins);
        }

        static Snapshot loggedOut() {
            return new Snapshot(false, 0, "", 0, 0, 0, 0, false, false, false, false,
                0, 0, 0, false, false, false, "", 0, false, 0, false);
        }

        boolean usable() {
            return loggedIn && account != 0 && !playerName.isEmpty() && currentWorld > 0;
        }

        long looseCoins() {
            return (long) inventoryCoins + (long) knownBankCoins;
        }

        /** Runtime wealth excludes the trade offer; add its GP once while that trade is visibly open. */
        long workingCapitalGp() {
            int offered = tradeMainOpen || tradeConfirmOpen ? ourOfferedCoins : 0;
            return wealthGp > Long.MAX_VALUE - offered ? Long.MAX_VALUE : wealthGp + offered;
        }

        int distanceTo(int x, int y, int planeValue) {
            if (plane != planeValue) return Integer.MAX_VALUE;
            return Math.max(Math.abs(tileX - x), Math.abs(tileY - y));
        }
    }

    private final Port port;
    private final Random random;

    // Shared / sender state
    private volatile Phase phase = Phase.IDLE;
    private volatile boolean liquidating;
    private volatile int liquidationTargetGp;
    private volatile String status = "";
    private volatile long totalTransferredGp;
    private long boundAccount;
    private long liquidateSince;
    private long eligibleSince = -1;
    private long lastProbeMillis = -1;
    private long nextAttemptAt;
    private long lastHeartbeatMillis = -1;
    private long phaseSince;
    private String targetMuleRsn = "";
    private String lockedConfiguredHost = "";
    private String lockedLeaseHost = "";
    /** The Flipper's name the lease was booked under, so a later run can still release it. */
    private String lockedFlipperRsn = "";
    /** A transfer cut short by a stop, restart or profile change; the next run frees the Mule and heads home. */
    private Interrupted interrupted;

    private static final class Interrupted {
        final long account;
        final String flipperRsn, muleRsn, leaseHost;
        final int leasePort, awayWorld, homeWorld, homeX, homeY, homePlane;

        Interrupted(long account, String flipperRsn, String muleRsn, String leaseHost, int leasePort, int awayWorld,
                    int homeWorld, int homeX, int homeY, int homePlane) {
            this.account = account;
            this.flipperRsn = flipperRsn;
            this.muleRsn = muleRsn;
            this.leaseHost = leaseHost;
            this.leasePort = leasePort;
            this.awayWorld = awayWorld;
            this.homeWorld = homeWorld;
            this.homeX = homeX;
            this.homeY = homeY;
            this.homePlane = homePlane;
        }
    }
    private int lockedPort;
    private int lockedFixedWorld;
    private int targetMuleWorld;
    private int meetX, meetY, meetPlane;
    private int returnWorld;
    private int returnX, returnY, returnPlane;
    private int lockedTransferAmount;
    private int lockedReserveGp;
    private int coinsBeforeTrade = -1;
    private int hopAttempts;
    private long nextHopAt;
    private long nextWalkAt;
    private int tradeRequests;
    private long nextTradeAt;
    private long muleUnseenSince = -1;
    private long capitalShortSince = -1;
    private int capitalShortWorld;
    /** One transfer down to the keep amount before an OSRS update; it skips the trigger, cooldowns and sell-down. */
    private boolean updateTransferPending;
    /** "Mule now": this many GP to send, 0 = none. It frees up GP first and may go below the keep amount. */
    private volatile int manualTransferGp;
    /** What set the running transfer's floor, so a settings change only cancels the transfers it belongs to. */
    private Source lockedSource = Source.AUTO;
    private long updateDeadline;
    /** Pre-trade steps of the running transfer stop after this (0 = no deadline). */
    private long lockedDeadline;
    // Display only.
    private volatile long lastCapitalGp = -1;
    private volatile long lastTransferGp;
    private volatile long lastTransferAt = -1;

    // Receiver state
    private int rememberedWorld;
    private int rememberedX, rememberedY, rememberedPlane;
    private String rememberedRsn = "";
    private long idleLogoutAt = -1;
    private long nextLoginAt;
    private long nextReceiverHopAt;
    private long nextReceiverWalkAt;
    private int receiverBaselineCoins = -1;
    private boolean receiverWasInTrade;
    private long receiverTradeAccount;
    private String receiverTradePartner = "";
    private boolean receiverConfirmationSent;
    private long receiverReceiptWaitingSince = -1;
    private volatile TradeRequest pendingTradeRequest;

    private static final class TradeRequest {
        final String requester;
        final long account;
        final long receivedAt;

        TradeRequest(String requester, long account, long receivedAt) {
            this.requester = requester;
            this.account = account;
            this.receivedAt = receivedAt;
        }
    }

    MuleSession(Port port) {
        this(port, new Random());
    }

    MuleSession(Port port, Random random) {
        this.port = port;
        this.random = random;
    }

    boolean active() { return phase != Phase.IDLE; }
    Phase phase() { return phase; }
    String status() { return status; }
    private volatile String cancelReason = "";
    /** Why the last transfer was cancelled, returned once; empty when there is nothing new. */
    String takeCancelReason() {
        String reason = cancelReason;
        cancelReason = "";
        return reason;
    }
    private volatile String capitalStatus = "Estimate unavailable";
    String capitalStatus() { return capitalStatus; }
    long totalTransferredGp() { return totalTransferredGp; }
    /** The last verified working-capital estimate, or -1. Display only. */
    long lastCapitalGp() { return lastCapitalGp; }
    long lastTransferGp() { return lastTransferGp; }
    /** When the last transfer completed on the caller's clock, or -1. */
    long lastTransferAt() { return lastTransferAt; }

    /**
     * Asks for one transfer down to the keep amount before an OSRS update. It ignores the trigger, cooldowns and the
     * sell-down: only GP that is already liquid is sent. Pre-trade steps stop at {@code deadlineMillis}.
     */
    void requestTransferBeforeUpdate(long deadlineMillis) {
        updateTransferPending = true;
        updateDeadline = deadlineMillis;
    }

    /** True until the update transfer has been tried and, if it started, has finished its return journey. */
    boolean transferBeforeUpdateBusy() {
        return updateTransferPending || (lockedDeadline > 0 && phase != Phase.IDLE);
    }

    /** Drops an update transfer that never got a usable game reading; a started transfer keeps its own deadline. */
    void cancelTransferBeforeUpdate() {
        updateTransferPending = false;
    }

    /** Where a transfer came from: automatic muling, the transfer before an OSRS update, or "Mule now". */
    enum Source { AUTO, UPDATE, MANUAL }

    /**
     * "Mule now": sends {@code amount} GP as soon as possible. It frees up GP first if needed (the caller cancels buys
     * and sells down while {@link #liquidationRequested()} is true), ignores the trigger and the keep amount, and
     * gives up with a status message if the GP cannot be freed or the Mule doesn't take it.
     */
    void requestManualTransfer(int amount) {
        if (amount < MulePolicy.MIN_TRANSFER_GP) return;
        manualTransferGp = amount;
        status = "Mule now: preparing " + gp(amount) + ".";
    }

    /** True from the "Mule now" press until its transfer has started or given up. */
    boolean manualTransferPending() {
        return manualTransferGp > 0;
    }

    /** Display-only taps on receiver accounting. They are told what was verified and decide nothing. */
    interface ReceiverObserver {
        /** One confirmed incoming trade of {@code gp} coins. */
        void received(long account, long gp);

        /** A verified idle reading: no trade is open, so the coin counts are not mid-offer. */
        void holdings(long account, long inventoryCoins, long bankCoins, boolean bankKnown);

        default void receivedForProfile(long profileId, long account, long gp) { received(account, gp); }

        default void holdingsForProfile(long profileId, long account, long inventoryCoins, long bankCoins, boolean bankKnown) {
            holdings(account, inventoryCoins, bankCoins, bankKnown);
        }
    }

    private volatile ReceiverObserver receiverObserver;

    void observeReceiver(ReceiverObserver observer) { receiverObserver = observer; }

    private void notifyReceived(Snapshot view, int gained) {
        ReceiverObserver observer = receiverObserver;
        if (observer == null) return;
        try {
            observer.receivedForProfile(view.configurationProfileId, view.account, gained);
        } catch (RuntimeException ignored) {
            // A display problem must never change a trade outcome.
        }
    }

    private void notifyHoldings(Snapshot view) {
        ReceiverObserver observer = receiverObserver;
        if (observer == null) return;
        try {
            observer.holdingsForProfile(view.configurationProfileId, view.account, view.inventoryCoins, view.knownBankCoins,
                view.bankOpen || view.knownBankCoins > 0);
        } catch (RuntimeException ignored) {
            // A display problem must never change a trade outcome.
        }
    }

    /** True while the Flipper should cancel buys and sell down to the trigger amount. */
    boolean liquidationRequested() { return liquidating && phase == Phase.IDLE; }
    int liquidationTargetGp() { return liquidationRequested() ? liquidationTargetGp : 0; }

    /** The sell-down finished but the trigger amount could not be reached; resume normal flipping. */
    void liquidationStalled(long nowMillis) {
        if (!liquidating) return;
        liquidating = false;
        liquidationTargetGp = 0;
        nextAttemptAt = nowMillis + COOLDOWN_AFTER_ABORT_MS;
        status = "";
        if (manualTransferGp > 0) {
            manualTransferGp = 0;
            status = "Mule now: everything is listed, but not enough GP is free yet. Press Mule now again once more "
                + "has sold, or send less.";
        }
    }

    void cancelLiquidation() {
        liquidating = false;
        liquidationTargetGp = 0;
        manualTransferGp = 0;
    }

    boolean receiverHoldingLogout() {
        return port.activeServerLease() == null;
    }

    /** Called only for an actual incoming TRADEREQ event, never for ordinary chat text. */
    void recordTradeRequest(String requester, long account, long now) {
        String normalized = MulePolicy.normalizeRsn(requester);
        MuleNetwork.Lease lease = port.activeServerLease();
        if (account == 0 || normalized.isEmpty() || normalized.length() > 12
            || lease == null || !MulePolicy.sameRsn(normalized, lease.traderRsn)) return;
        synchronized (this) {
            pendingTradeRequest = new TradeRequest(normalized, account, now);
        }
    }

    private synchronized TradeRequest takeTradeRequest(Snapshot view, MuleNetwork.Lease lease, long now) {
        TradeRequest request = pendingTradeRequest;
        pendingTradeRequest = null;
        if (request == null || lease == null || request.account != view.account
            || now < request.receivedAt || now - request.receivedAt > TRADE_REQUEST_MAX_AGE_MS
            || !MulePolicy.sameRsn(request.requester, lease.traderRsn)) return null;
        return request;
    }

    private synchronized void clearTradeRequest() {
        pendingTradeRequest = null;
    }

    /** Closes receiver-only resources without discarding a sender's pending return journey. */
    void stopReceiver() {
        port.stopServer();
        clearReceiverState();
    }

    private void clearReceiverState() {
        clearTradeRequest();
        clearReceiverTradeState();
        rememberedWorld = rememberedX = rememberedY = rememberedPlane = 0;
        rememberedRsn = "";
        idleLogoutAt = -1;
        nextLoginAt = nextReceiverHopAt = nextReceiverWalkAt = 0;
    }

    private void clearReceiverTradeState() {
        receiverBaselineCoins = -1;
        receiverWasInTrade = false;
        receiverTradeAccount = 0;
        receiverTradePartner = "";
        receiverConfirmationSent = false;
        receiverReceiptWaitingSince = -1;
    }

    /** Stops accepting leases, then waits for a verified closed trade before Receiver Finish stops the plugin. */
    boolean finishReceiver(long now) {
        port.stopServer();
        clearTradeRequest();
        Snapshot view = port.read();
        if (view == null) return false;
        if (!view.loggedIn) {
            clearReceiverState();
            return true;
        }
        if (!view.usable()) return false;
        if (recordReceiverReceipt(view, null, now)) return false;
        if (view.tradeMainOpen || view.tradeConfirmOpen || view.ourOfferedCoins > 0 || view.ourOfferedOtherItems) {
            if (port.allowed()) port.declineTrade();
            return false;
        }
        if (!port.allowed()) return false;
        clearReceiverState();
        return true;
    }

    private boolean recordReceiverReceipt(Snapshot view, MuleNetwork.Lease lease, long now) {
        if (!view.usable()) {
            if (!view.loggedIn) {
                clearReceiverTradeState();
                clearTradeRequest();
            }
            return false;
        }
        if (receiverTradeAccount != 0 && receiverTradeAccount != view.account) {
            clearReceiverTradeState();
        }
        if (!receiverWasInTrade || view.tradeMainOpen || view.tradeConfirmOpen) return false;
        boolean received = receiverConfirmationSent && receiverBaselineCoins >= 0
            && view.inventoryCoins > receiverBaselineCoins;
        if (received) {
            int gained = view.inventoryCoins - receiverBaselineCoins;
            totalTransferredGp += gained;
            notifyReceived(view, gained);
            // COMPLETE_TRADE may already have cleared the old lease. Do not clear a different sender's new lease.
            if (lease != null && MulePolicy.sameRsn(lease.traderRsn, receiverTradePartner)) port.clearServerLease(lease);
            idleLogoutAt = now + jitter(MULE_LOGOUT_DELAY_BASE_MS);
            status = "Mule: received " + gp(gained) + "; logging out soon.";
        } else if (receiverConfirmationSent) {
            if (receiverReceiptWaitingSince < 0) receiverReceiptWaitingSince = now;
            if (now >= receiverReceiptWaitingSince && now - receiverReceiptWaitingSince < RECEIPT_SETTLE_MS) {
                status = "Mule: waiting for the confirmed trade's inventory update.";
                return true;
            }
        }
        receiverWasInTrade = false;
        receiverBaselineCoins = view.inventoryCoins;
        receiverTradeAccount = 0;
        receiverTradePartner = "";
        receiverConfirmationSent = false;
        receiverReceiptWaitingSince = -1;
        return received;
    }

    private long jitter(long base) {
        return base + (long) (random.nextDouble() * (base / 2.0));
    }

    /**
     * Runs the Mule role. Works while logged out: the mule stays offline and only logs in
     * while a Flipper holds a lease, then logs out again. It never initiates a trade.
     */
    void tickReceiver(ReceiverSettings s, long now) {
        if (phase != Phase.IDLE) {
            clearTransientSenderState();
        }
        liquidating = false;
        liquidationTargetGp = 0;
        int portNum = MulePolicy.clampPort(s.port);
        if (!port.ensureServer(portNum)) {
            status = "Mule: port " + portNum + " is already in use.";
            return;
        }
        Snapshot view = port.read();
        if (view == null) {
            // A timed-out client-thread read is unknown, not proof of logout or receipt cancellation.
            status = "Mule: waiting for verified game state.";
            return;
        }
        boolean in = view.usable();

        if (in) {
            rememberedRsn = view.playerName;
            rememberedWorld = view.currentWorld;
            if (!view.tradeMainOpen && !view.tradeConfirmOpen) notifyHoldings(view);
        }
        MuleNetwork.Lease lease = port.activeServerLease();
        // Account for a just-finished trade even if the sender's completion packet removed its lease first.
        if (recordReceiverReceipt(view, lease, now)) return;
        if (in && lease == null && !view.tradeMainOpen && !view.tradeConfirmOpen) {
            // Remember where we stand so later logins and Flippers meet at this tile.
            rememberedX = view.tileX;
            rememberedY = view.tileY;
            rememberedPlane = view.plane;
        } else if (in && s.location.auto && rememberedX <= 0 && view.atGrandExchange
            && !view.tradeMainOpen && !view.tradeConfirmOpen) {
            // First login for a lease after starting logged out: no tile is remembered yet. Standing anywhere at the
            // GE more than 6 tiles from its centre used to keep the Mule "not ready" until the Flipper gave up.
            rememberedX = view.tileX;
            rememberedY = view.tileY;
            rememberedPlane = view.plane;
        }

        int world = s.fixedWorld > 0 ? s.fixedWorld : rememberedWorld;
        if (world <= 0) world = Math.max(0, port.profileWorld());
        int tileX, tileY, tilePlane;
        if (s.location.auto) {
            boolean known = rememberedX > 0 && rememberedY > 0;
            tileX = known ? rememberedX : MulePolicy.GE_CENTER_X;
            tileY = known ? rememberedY : MulePolicy.GE_CENTER_Y;
            tilePlane = known ? rememberedPlane : MulePolicy.GE_CENTER_PLANE;
        } else {
            tileX = s.location.x;
            tileY = s.location.y;
            tilePlane = s.location.plane;
        }
        String rsn = !rememberedRsn.isEmpty() ? rememberedRsn : s.ownRsn;
        boolean atTile = in && sameTile(view, tileX, tileY, tilePlane, s.location.auto ? 6 : 2);
        boolean ready = in && view.currentWorld == world && atTile && !view.bankOpen && !view.exchangeOpen;
        port.updateServerState(ready, rsn, world, tileX, tileY, tilePlane);

        if (lease == null) {
            clearTradeRequest();
            if (!in) {
                idleLogoutAt = -1;
                status = world > 0 ? "Mule: standby (logged out, W" + world + ", port " + portNum + ")"
                    : "Mule: standby. Set a Mule world or log in once so it can remember one.";
                return;
            }
            if (!port.allowed()) return;
            if (view.tradeMainOpen || view.tradeConfirmOpen) {
                port.declineTrade();
                status = "Mule: declined unexpected trade.";
                return;
            }
            if (view.exchangeOpen || view.bankOpen) {
                port.closeInterfaces();
                return;
            }
            if (idleLogoutAt < 0) idleLogoutAt = now + jitter(MULE_LOGOUT_DELAY_BASE_MS);
            if (now >= idleLogoutAt) {
                status = "Mule: logging out until a Flipper needs it.";
                port.logout();
            } else {
                status = "Mule: no Flipper waiting; logging out shortly.";
            }
            return;
        }

        idleLogoutAt = -1;
        if (!in) {
            if (world <= 0) {
                status = "Mule: cannot log in; set a Mule world in settings.";
                return;
            }
            if (now >= nextLoginAt) {
                nextLoginAt = now + jitter(LOGIN_RETRY_BASE_MS);
                status = "Mule: logging in to W" + world + " for " + lease.traderRsn + ".";
                port.login(world);
            } else {
                status = "Mule: waiting to log in for " + lease.traderRsn + ".";
            }
            return;
        }
        if (!port.allowed()) return;

        boolean inTrade = view.tradeMainOpen || view.tradeConfirmOpen;
        if (!inTrade) {
            receiverBaselineCoins = view.inventoryCoins;
            if (view.exchangeOpen || view.bankOpen) {
                port.closeInterfaces();
                return;
            }
            if (view.currentWorld != world) {
                if (now >= nextReceiverHopAt) {
                    nextReceiverHopAt = now + jitter(HOP_RETRY_BASE_MS);
                    status = "Mule: hopping to W" + world + ".";
                    port.hopToWorld(world);
                }
                return;
            }
            if (!s.location.auto && !atTile) {
                if (now >= nextReceiverWalkAt) {
                    nextReceiverWalkAt = now + jitter(WALK_RETRY_BASE_MS);
                    status = "Mule: walking to the meeting tile.";
                    port.walkTo(tileX, tileY, tilePlane);
                }
                return;
            }
            TradeRequest request = takeTradeRequest(view, port.activeServerLease(), now);
            if (request != null && port.playerNear(request.requester, 10)) {
                // The reciprocal Trade action accepts the observed invitation; it never initiates an unsolicited trade.
                status = "Mule: accepting trade request from " + lease.traderRsn + ".";
                port.tradePlayer(request.requester);
                return;
            }
            status = "Mule: ready for " + lease.traderRsn + " ("
                + gp(lease.amountGp) + "). Waiting for their trade request.";
            return;
        }

        clearTradeRequest();
        if (!MulePolicy.sameRsn(view.tradePartnerName, lease.traderRsn)
            || view.ourOfferedCoins > 0 || view.ourOfferedOtherItems) {
            port.declineTrade();
            status = "Mule: declined mismatched trade.";
            return;
        }
        if (!receiverWasInTrade) {
            receiverBaselineCoins = view.inventoryCoins;
            receiverTradeAccount = view.account;
            receiverTradePartner = view.tradePartnerName;
            receiverConfirmationSent = false;
            receiverReceiptWaitingSince = -1;
            receiverWasInTrade = true;
        }
        if (view.tradeMainOpen) {
            int required = Math.max(MulePolicy.MIN_TRANSFER_GP, lease.amountGp);
            if (view.otherOfferedCoins >= required) {
                status = "Mule: accepting first trade screen from " + lease.traderRsn + ".";
                port.acceptTradeMain();
            } else {
                status = "Mule: waiting for " + lease.traderRsn + " to offer coins.";
            }
            return;
        }
        if (view.otherOfferedCoins >= MulePolicy.MIN_TRANSFER_GP) {
            status = "Mule: confirming trade from " + lease.traderRsn + ".";
            if (port.acceptTradeConfirm()) receiverConfirmationSent = true;
        } else {
            port.declineTrade();
        }
    }
    private static boolean sameTile(Snapshot v, int x, int y, int plane, int tolerance) {
        return v.distanceTo(x, y, plane) <= tolerance;
    }

    /**
     * Runs the Flipper role. Returns true when a mule transfer owns the current tick.
     * While {@link #liquidationRequested()} is true the caller should cancel buys and sell down.
     */
    boolean tickSender(SenderSettings s, boolean finishing, long now) {
        stopReceiver();
        if (interrupted != null && phase == Phase.IDLE && resumeInterruptedTransfer(now)) return true;
        boolean cancelled = !s.enabled || finishing || s.muleRsn.isEmpty();
        if (cancelled) {
            liquidating = false;
            liquidationTargetGp = 0;
            if (phase == Phase.IDLE) {
                eligibleSince = -1;
                status = s.enabled && s.muleRsn.isEmpty() ? "Mule: enter the Mule's RSN in settings." : "";
                // A pending update transfer that cannot run counts as tried, so the update Finish is never held.
                updateTransferPending = false;
                manualTransferGp = 0;
                return false;
            }
        }

        Snapshot view = port.read();
        if (view == null || !view.usable()) {
            eligibleSince = -1;
            capitalShortSince = -1;
            if (phase == Phase.IDLE) {
                liquidating = false;
                liquidationTargetGp = 0;
                status = "Mule: waiting for verified working-capital data.";
            }
            return phase != Phase.IDLE;
        }
        if (boundAccount != 0 && view.account != boundAccount) {
            // A return journey belongs to the previous account; never move the replacement account for it.
            clearTransientSenderState();
            boundAccount = view.account;
            return false;
        }
        boundAccount = view.account;

        capitalStatus = !view.capitalVerified ? "Awaiting capital data"
            : view.capitalComplete ? "Guide-price estimate" : "Partial estimate";
        lastCapitalGp = view.capitalVerified ? view.workingCapitalGp() : -1;

        // Book an already committed transfer before a changed setting or an expired phase can cancel it.
        if (recordCompletedSenderTrade(s, view, now)) return true;
        MulePolicy.HostPort configured = MulePolicy.parseHostAndPort(s.host, s.port);
        if (!cancelled && phase != Phase.IDLE && phase != Phase.RETURNING
            && (!MulePolicy.sameRsn(s.muleRsn, targetMuleRsn)
                || !normalizedHost(configured.host).equalsIgnoreCase(lockedConfiguredHost)
                || configured.port != lockedPort || s.fixedWorld != lockedFixedWorld)) {
            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: recipient or connection changed; cancelled the pending transfer.");
            return phase == Phase.RETURNING && tickSenderActive(s, view, now);
        }
        if (!cancelled && phase != Phase.IDLE && phase != Phase.RETURNING
            && lockedSource != Source.MANUAL && floorSetting(s, lockedSource) != lockedReserveGp) {
            // The working-cash floor belongs to the user. Never submit an old amount under a new floor.
            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: working-capital limit changed; cancelled the pending transfer.");
            return phase == Phase.RETURNING && tickSenderActive(s, view, now);
        }
        long offeredCoins = view.tradeMainOpen || view.tradeConfirmOpen ? view.ourOfferedCoins : 0;
        long floor = (long) lockedReserveGp - lockedKeepTolerance();
        boolean coinsShort = view.looseCoins() + offeredCoins < lockedTransferAmount;
        // Only a full, verified total can show that sending would break the keep amount. After a world hop the bank
        // and the GE collection box are unknown until the game sends them again, so the total reads partial and low;
        // that is no reason to cancel. The free coins are always read directly and must always cover the transfer.
        boolean belowKeep = view.capitalComplete && view.workingCapitalGp() - lockedTransferAmount < floor;
        boolean capitalShort = !cancelled && phase != Phase.IDLE && phase != Phase.RETURNING
            && (coinsShort || belowKeep);
        if (!capitalShort) {
            capitalShortSince = -1;
        } else {
            // Preserve total working capital, including invested items/offers; never offer more GP than remains liquid.
            // Straight after a world hop the game shows every GE slot as empty until the server resends them, so a
            // single low reading is not proof. Take no trade step while it is low; cancel only if it stays low.
            if (capitalShortSince < 0 || view.currentWorld != capitalShortWorld) {
                capitalShortSince = now;
                capitalShortWorld = view.currentWorld;
            }
            if (now - capitalShortSince < CAPITAL_RECHECK_MS) {
                status = "Mule: re-checking working capital before trading.";
                return true;
            }
            capitalShortSince = -1;
            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, coinsShort
                ? "Mule: cancelled the transfer: only " + gp(view.looseCoins() + offeredCoins) + " free coins to send "
                    + gp(lockedTransferAmount) + "."
                : "Mule: cancelled the transfer: sending " + gp(lockedTransferAmount) + " would leave less than "
                    + gp(lockedReserveGp) + " (your total is now " + gp(view.workingCapitalGp()) + ").");
            return phase == Phase.RETURNING && tickSenderActive(s, view, now);
        }

        if (cancelled && phase != Phase.RETURNING) abort(s, view, now, 0, "");
        if (phase == Phase.RETURNING) return tickSenderActive(s, view, now);
        if (cancelled) return false;

        if (phase == Phase.IDLE) return tickSenderIdle(s, view, now);
        return tickSenderActive(s, view, now);
    }

    /** The setting a transfer's floor comes from: the keep amount, or for an update transfer its own keep amount. */
    private static int floorSetting(SenderSettings s, Source source) {
        return source == Source.UPDATE && s.updateReserveGp >= 0 ? s.updateReserveGp : s.reserveGp;
    }

    private boolean tickSenderIdle(SenderSettings s, Snapshot view, long now) {
        if (updateTransferPending) return startTransferBeforeUpdate(s, view, now);
        if (manualTransferGp > 0) return startManualTransfer(s, view, now);
        if (now < nextAttemptAt) {
            liquidating = false;
            liquidationTargetGp = 0;
            eligibleSince = -1;
            if (!status.startsWith("Mule: ")) status = "";
            return false;
        }
        if (!view.capitalVerified) {
            liquidating = false;
            liquidationTargetGp = 0;
            eligibleSince = -1;
            status = "Mule: waiting for verified working-capital data.";
            return false;
        }
        if (s.thresholdGp < 0 || s.reserveGp <= 0) {
            liquidating = false;
            liquidationTargetGp = 0;
            eligibleSince = -1;
            status = "Mule: check the 'Keep for flipping' and 'Mule when over' amounts in settings.";
            return false;
        }
        // The trigger only starts a transfer. Once the sell-down has begun it carries on down to the keep amount,
        // so a small dip below the trigger cannot stop it halfway with buys already cancelled.
        int trigger = MulePolicy.autoTriggerGp(Math.max(s.thresholdGp, s.reserveGp), s.reserveGp);
        if (!liquidating && view.workingCapitalGp() < trigger) {
            liquidationTargetGp = 0;
            eligibleSince = -1;
            status = "";
            return false;
        }
        int required = MulePolicy.requiredPortfolioTransferAmount(view.workingCapitalGp(), s.reserveGp);
        if (required < MulePolicy.MIN_TRANSFER_GP) {
            liquidating = false;
            liquidationTargetGp = 0;
            eligibleSince = -1;
            status = "";
            return false;
        }
        if (MulePolicy.needsPortfolioLiquidity(view.workingCapitalGp(), view.looseCoins(), s.reserveGp)) {
            eligibleSince = -1;
            liquidationTargetGp = required;
            if (!liquidating) {
                liquidating = true;
                liquidateSince = now;
            }
            if (now - liquidateSince > LIQUIDATE_TIMEOUT_MS) {
                liquidating = false;
                liquidationTargetGp = 0;
                nextAttemptAt = now + COOLDOWN_AFTER_ABORT_MS;
                status = "Mule: excess capital did not become liquid in time; resuming normal flipping.";
            } else {
                status = "Mule: freeing up " + gp(required) + " to send; keeping " + gp(s.reserveGp)
                    + " for flipping.";
            }
            return false;
        }

        int amount = MulePolicy.calculatePortfolioTransferAmount(view.workingCapitalGp(), view.looseCoins(), s.reserveGp);
        boolean wasLiquidating = liquidating;
        if (!wasLiquidating) {
            if (eligibleSince < 0) eligibleSince = now;
            if (now - eligibleSince < ELIGIBLE_DEBOUNCE_MS) return false;
        }
        liquidating = false;
        liquidationTargetGp = 0;
        if (lastProbeMillis >= 0 && now - lastProbeMillis < PROBE_RETRY_MS) return false;
        lastProbeMillis = now;
        return requestTransfer(s, view, now, amount, 0, Source.AUTO, s.reserveGp);
    }

    private boolean startTransferBeforeUpdate(SenderSettings s, Snapshot view, long now) {
        updateTransferPending = false;
        liquidating = false;
        liquidationTargetGp = 0;
        eligibleSince = -1;
        if (updateDeadline > 0 && now >= updateDeadline) {
            status = "Mule: the OSRS update is too close; skipped the transfer before it.";
            return false;
        }
        if (!view.capitalVerified) {
            status = "Mule: no verified working-capital estimate; skipped the transfer before the update.";
            return false;
        }
        int keep = floorSetting(s, Source.UPDATE);
        int amount = keep == 0 ? (int) Math.min(Integer.MAX_VALUE, view.looseCoins())
            : MulePolicy.calculatePortfolioTransferAmount(view.workingCapitalGp(), view.looseCoins(), keep);
        if (amount < MulePolicy.MIN_TRANSFER_GP) {
            status = keep == 0 ? "Mule: no loose GP to send before the update."
                : "Mule: nothing above " + gp(keep)
                + " to send before the update.";
            return false;
        }
        return requestTransfer(s, view, now, amount, updateDeadline > 0 ? updateDeadline : Long.MAX_VALUE,
            Source.UPDATE, keep);
    }

    /** "Mule now": free up the amount if needed, then send it, whatever the trigger and keep amount say. */
    private boolean startManualTransfer(SenderSettings s, Snapshot view, long now) {
        int amount = manualTransferGp;
        eligibleSince = -1;
        if (!view.capitalVerified) {
            status = "Mule now: waiting for verified working-capital data.";
            return false;
        }
        if (amount > view.workingCapitalGp()) {
            endManualTransfer("Mule now: " + gp(amount) + " is more than "
                + "your total (" + gp(view.workingCapitalGp())
                + "). Enter a smaller amount.");
            return false;
        }
        if (view.looseCoins() < amount) {
            liquidationTargetGp = amount;
            if (!liquidating) {
                liquidating = true;
                liquidateSince = now;
            }
            if (now - liquidateSince > LIQUIDATE_TIMEOUT_MS) {
                endManualTransfer("Mule now: the GP did not become free in time; resuming normal flipping.");
            } else {
                status = "Mule now: freeing up " + gp(amount) + " ("
                    + gp(view.looseCoins()) + " free so far).";
            }
            return false;
        }
        liquidating = false;
        liquidationTargetGp = 0;
        manualTransferGp = 0;
        // The floor keeps the rest of the total as it is now, so a misread or a price drop can't send more.
        int floor = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, view.workingCapitalGp() - amount));
        if (requestTransfer(s, view, now, amount, 0, Source.MANUAL, floor)) return true;
        String reason = status.startsWith("Mule: ") ? status.substring("Mule: ".length()) : status;
        status = "Mule now: " + reason.replace("; retrying later.", ". Press Mule now to try again.");
        return false;
    }

    private void endManualTransfer(String message) {
        manualTransferGp = 0;
        liquidating = false;
        liquidationTargetGp = 0;
        status = message;
    }

    /** Asks the Mule for a lease and, when granted, locks this transfer's amount, floor and return point. */
    private boolean requestTransfer(SenderSettings s, Snapshot view, long now, int amount, long deadline,
                                    Source source, int floorGp) {
        MulePolicy.HostPort hp = MulePolicy.parseHostAndPort(s.host, s.port);
        MuleNetwork.MuleReply reply = port.requestMuleLease(hp.host, hp.port, s.muleRsn, view.playerName, amount);
        if (!reply.ok) {
            // Mule offline, wrong name, or busy: keep flipping and try again much later.
            liquidating = false;
            nextAttemptAt = now + COOLDOWN_MULE_OFFLINE_MS;
            status = "Mule: " + (reply.message.isEmpty() ? "no reply" : reply.message) + "; retrying later.";
            return false;
        }
        int world = s.fixedWorld > 0 ? s.fixedWorld : reply.world;
        if (world <= 0) {
            liquidating = false;
            nextAttemptAt = now + COOLDOWN_MULE_OFFLINE_MS;
            port.releaseMuleLease(hp.host, hp.port, s.muleRsn, view.playerName, false);
            status = "Mule: has no known world yet. Set a Mule world or log the Mule in once.";
            return false;
        }
        targetMuleRsn = s.muleRsn;
        lockedConfiguredHost = normalizedHost(hp.host);
        lockedLeaseHost = reply.resolvedHost.isEmpty() ? hp.host : reply.resolvedHost;
        lockedFlipperRsn = view.playerName;
        lockedPort = hp.port;
        lockedFixedWorld = s.fixedWorld;
        targetMuleWorld = world;
        applyMeetingTile(s, reply);
        returnWorld = view.currentWorld;
        returnX = view.tileX;
        returnY = view.tileY;
        returnPlane = view.plane;
        lockedTransferAmount = amount;
        lockedReserveGp = floorGp;
        lockedSource = source;
        lockedDeadline = deadline;
        coinsBeforeTrade = -1;
        hopAttempts = 0;
        tradeRequests = 0;
        nextTradeAt = 0;
        nextHopAt = 0;
        nextWalkAt = 0;
        muleUnseenSince = -1;
        lastHeartbeatMillis = now;
        liquidating = false;
        liquidationTargetGp = 0;
        enter(reply.ready ? Phase.PREPARING_CASH : Phase.WAITING_FOR_MULE, now);
        String reason = deadline > 0 ? " before the OSRS update" : source == Source.MANUAL ? " (Mule now)" : "";
        status = reply.ready ? "Mule: preparing " + gp(amount) + reason + "."
            : "Mule: waiting for " + targetMuleRsn + " to log in" + reason + ".";
        return true;
    }

    private void applyMeetingTile(SenderSettings s, MuleNetwork.MuleReply reply) {
        if (s.location.auto) {
            meetX = reply.tileX;
            meetY = reply.tileY;
            meetPlane = reply.plane;
        } else {
            meetX = s.location.x;
            meetY = s.location.y;
            meetPlane = s.location.plane;
        }
    }

    private static String normalizedHost(String host) {
        return host == null || host.trim().isEmpty() || "auto".equalsIgnoreCase(host.trim()) ? "auto" : host.trim();
    }

    private void enter(Phase next, long now) {
        phase = next;
        phaseSince = now;
    }

    private boolean tickSenderActive(SenderSettings s, Snapshot view, long now) {
        if (phase == Phase.RETURNING) {
            if (!port.allowed()) return true;
            return tickSenderReturning(view, now);
        }
        boolean beforeTrade = phase == Phase.WAITING_FOR_MULE || phase == Phase.PREPARING_CASH
            || phase == Phase.HOPPING_TO_MULE || phase == Phase.WALKING_TO_MULE || phase == Phase.OPENING_TRADE;
        boolean tradeOpen = view.tradeMainOpen || view.tradeConfirmOpen;
        if (lockedDeadline > 0 && now >= lockedDeadline && beforeTrade && !tradeOpen) {
            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: the OSRS update is too close; cancelled the transfer.");
            return phase != Phase.IDLE;
        }
        if (phase != Phase.RETURNING && now - lastHeartbeatMillis >= HEARTBEAT_INTERVAL_MS) {
            lastHeartbeatMillis = now;
            MuleNetwork.MuleReply beat = port.heartbeatMuleLease(lockedLeaseHost, lockedPort, targetMuleRsn,
                view.playerName, lockedTransferAmount);
            if (phase != Phase.WAITING_FOR_MULE && !beat.ok && beat.responding && beforeTrade && !tradeOpen) {
                // The Mule answered that it no longer holds this transfer (it restarted or the lease expired). It logs
                // out without a lease, so walking up and sending trade requests would only run into the time-outs.
                abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: " + targetMuleRsn + " no longer holds this transfer ("
                    + (beat.message.isEmpty() ? "no lease" : beat.message) + "); cancelled.");
                return phase != Phase.IDLE;
            }
            if (phase == Phase.WAITING_FOR_MULE) {
                if (!beat.ok) {
                    abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: lost the lease while waiting.");
                    return phase != Phase.IDLE;
                }
                if (s.fixedWorld == 0 && beat.world > 0) targetMuleWorld = beat.world;
                if (s.location.auto && beat.tileX > 0) {
                    meetX = beat.tileX;
                    meetY = beat.tileY;
                    meetPlane = beat.plane;
                }
                if (beat.ready) {
                    enter(Phase.PREPARING_CASH, now);
                    status = "Mule: " + targetMuleRsn + " is online; preparing cash.";
                    return true;
                }
            }
        }

        long timeout = phase == Phase.WAITING_FOR_MULE ? WAIT_FOR_MULE_TIMEOUT_MS : PHASE_TIMEOUT_MS;
        if (now - phaseSince > timeout) {
            if (view.tradeMainOpen || view.tradeConfirmOpen) {
                if (port.allowed()) port.declineTrade();
            }
            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: timed out in " + phase + ".");
            return phase != Phase.IDLE;
        }
        if (phase == Phase.WAITING_FOR_MULE) {
            status = "Mule: waiting for " + targetMuleRsn + " to log in.";
            return true;
        }
        if (!port.allowed()) return true;

        switch (phase) {
            case PREPARING_CASH:
                if (view.offerOpen) {
                    status = "Mule: leaving GE offer setup before transfer.";
                    port.prepareBoundary();
                    return true;
                }
                if (view.inventoryCoins < lockedTransferAmount) {
                    if (view.looseCoins() < lockedTransferAmount) {
                        abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: cash dropped below the amount; cancelled.");
                        return phase != Phase.IDLE;
                    }
                    status = "Mule: withdrawing coins from bank.";
                    port.withdrawBankCoins(lockedTransferAmount - view.inventoryCoins);
                    return true;
                }
                if (view.exchangeOpen || view.bankOpen) {
                    status = "Mule: closing interface before world hop.";
                    port.closeInterfaces();
                    return true;
                }
                enter(Phase.HOPPING_TO_MULE, now);
                status = "Mule: hopping to W" + targetMuleWorld + " to meet " + targetMuleRsn + ".";
                return true;

            case HOPPING_TO_MULE:
                if (view.currentWorld != targetMuleWorld) {
                    if (now >= nextHopAt) {
                        if (++hopAttempts > MAX_HOP_ATTEMPTS) {
                            abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: could not hop to W" + targetMuleWorld + ".");
                            return phase != Phase.IDLE;
                        }
                        nextHopAt = now + jitter(HOP_RETRY_BASE_MS);
                        status = "Mule: hopping to W" + targetMuleWorld + ".";
                        port.hopToWorld(targetMuleWorld);
                    }
                    return true;
                }
                enter(Phase.WALKING_TO_MULE, now);
                return true;

            case WALKING_TO_MULE:
                if (sameTile(view, meetX, meetY, meetPlane, 2) || port.playerNear(targetMuleRsn, 3)) {
                    enter(Phase.OPENING_TRADE, now);
                    return true;
                }
                if (now >= nextWalkAt) {
                    nextWalkAt = now + jitter(WALK_RETRY_BASE_MS);
                    status = "Mule: walking to " + targetMuleRsn + ".";
                    port.walkTo(meetX, meetY, meetPlane);
                }
                return true;

            case OPENING_TRADE:
                if (view.exchangeOpen || view.bankOpen) {
                    port.closeInterfaces();
                    return true;
                }
                if (view.tradeMainOpen) {
                    if (!MulePolicy.sameRsn(view.tradePartnerName, targetMuleRsn)) {
                        port.declineTrade();
                        return true;
                    }
                    coinsBeforeTrade = view.inventoryCoins + view.ourOfferedCoins;
                    enter(Phase.OFFERING_COINS, now);
                    status = "Mule: offering " + gp(lockedTransferAmount) + " to " + targetMuleRsn + ".";
                    return true;
                }
                if (!port.playerNear(targetMuleRsn, 10)) {
                    if (muleUnseenSince < 0) muleUnseenSince = now;
                    if (now - muleUnseenSince > MULE_UNSEEN_TIMEOUT_MS) {
                        abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: " + targetMuleRsn + " is not visible nearby.");
                        return phase != Phase.IDLE;
                    }
                    status = "Mule: looking for " + targetMuleRsn + ".";
                    if (now >= nextWalkAt) {
                        nextWalkAt = now + jitter(WALK_RETRY_BASE_MS);
                        port.walkTo(meetX, meetY, meetPlane);
                    }
                    return true;
                }
                muleUnseenSince = -1;
                if (now < nextTradeAt) {
                    status = "Mule: waiting for " + targetMuleRsn + " to accept (" + tradeRequests + "/" + MAX_TRADE_REQUESTS + ").";
                    return true;
                }
                if (tradeRequests >= MAX_TRADE_REQUESTS) {
                    abort(s, view, now, COOLDOWN_AFTER_ABORT_MS, "Mule: trade window never opened.");
                    return phase != Phase.IDLE;
                }
                tradeRequests++;
                nextTradeAt = now + jitter(TRADE_RETRY_BASE_MS);
                status = "Mule: sent trade request " + tradeRequests + "/" + MAX_TRADE_REQUESTS + " to " + targetMuleRsn + ".";
                port.tradePlayer(targetMuleRsn);
                return true;

            case OFFERING_COINS:
                if (view.tradeConfirmOpen) {
                    if (MulePolicy.sameRsn(view.tradePartnerName, targetMuleRsn)
                        && view.ourOfferedCoins == lockedTransferAmount && !view.ourOfferedOtherItems) {
                        enter(Phase.CONFIRMING_TRADE, now);
                        status = "Mule: confirming trade with " + targetMuleRsn + ".";
                    } else {
                        port.declineTrade();
                    }
                    return true;
                }
                if (!view.tradeMainOpen) {
                    enter(Phase.OPENING_TRADE, now);
                    return true;
                }
                if (!MulePolicy.sameRsn(view.tradePartnerName, targetMuleRsn) || view.ourOfferedOtherItems) {
                    port.declineTrade();
                    return true;
                }
                if (view.ourOfferedCoins == 0) {
                    status = "Mule: offering " + gp(lockedTransferAmount) + ".";
                    port.offerCoins(lockedTransferAmount, view.inventoryCoins);
                    return true;
                }
                if (view.ourOfferedCoins != lockedTransferAmount) {
                    port.declineTrade();
                    return true;
                }
                status = "Mule: accepting first trade screen.";
                port.acceptTradeMain();
                return true;

            case CONFIRMING_TRADE:
                if (!view.tradeConfirmOpen && !view.tradeMainOpen) {
                    enter(Phase.OPENING_TRADE, now);
                    return true;
                }
                if (view.tradeConfirmOpen) {
                    if (!MulePolicy.sameRsn(view.tradePartnerName, targetMuleRsn)
                        || view.ourOfferedCoins != lockedTransferAmount || view.ourOfferedOtherItems) {
                        port.declineTrade();
                        return true;
                    }
                    status = "Mule: confirming final trade screen.";
                    port.acceptTradeConfirm();
                }
                return true;

            default:
                return false;
        }
    }

    private boolean recordCompletedSenderTrade(SenderSettings s, Snapshot view, long now) {
        if (phase != Phase.CONFIRMING_TRADE || view.tradeConfirmOpen || view.tradeMainOpen
            || coinsBeforeTrade < lockedTransferAmount
            || view.inventoryCoins > coinsBeforeTrade - lockedTransferAmount) return false;
        totalTransferredGp += lockedTransferAmount;
        lastTransferGp = lockedTransferAmount;
        lastTransferAt = now;
        port.releaseMuleLease(lockedLeaseHost, lockedPort, targetMuleRsn, view.playerName, true);
        nextAttemptAt = now + COOLDOWN_AFTER_SUCCESS_MS;
        status = "Mule: transfer complete; returning.";
        hopAttempts = 0;
        nextHopAt = nextWalkAt = 0;
        enter(Phase.RETURNING, now);
        return true;
    }

    private boolean tickSenderReturning(Snapshot view, long now) {
        // Decline/close an existing trade before walking or hopping, including same-world cancellations.
        if (view.exchangeOpen || view.bankOpen || view.tradeMainOpen || view.tradeConfirmOpen) {
            port.closeInterfaces();
            return true;
        }
        if (now - phaseSince > PHASE_TIMEOUT_MS) {
            clearTransientSenderState();
            status = "Mule: return timed out. Check the world and location manually.";
            return false;
        }
        if (returnWorld > 0 && view.currentWorld != returnWorld) {
            if (now >= nextHopAt) {
                if (++hopAttempts > MAX_HOP_ATTEMPTS + 3) {
                    clearTransientSenderState();
                    return false;
                }
                nextHopAt = now + jitter(HOP_RETRY_BASE_MS);
                status = "Mule: hopping back to W" + returnWorld + ".";
                port.hopToWorld(returnWorld);
            }
            return true;
        }
        if (view.distanceTo(returnX, returnY, returnPlane) > 3 && returnX > 0) {
            if (now >= nextWalkAt) {
                nextWalkAt = now + jitter(WALK_RETRY_BASE_MS);
                status = "Mule: walking back.";
                port.walkTo(returnX, returnY, returnPlane);
            }
            return true;
        }
        clearTransientSenderState();
        return false;
    }

    /**
     * GE Flipper was stopped or restarted while away for a transfer. Free the Mule, and if this account is still on
     * the Mule's world, go back to the world and spot the transfer started from before flipping again. Waits while
     * logged out; drops the plan if another account logs in or the player has moved to some other world.
     */
    private boolean resumeInterruptedTransfer(long now) {
        Interrupted pending = interrupted;
        Snapshot view = port.read();
        if (view == null || !view.usable()) return false;
        interrupted = null;
        if (!pending.muleRsn.isEmpty() && !pending.flipperRsn.isEmpty()) {
            port.releaseMuleLease(pending.leaseHost, pending.leasePort, pending.muleRsn, pending.flipperRsn, false);
        }
        if (view.account != pending.account || view.currentWorld != pending.awayWorld
            || pending.homeWorld == pending.awayWorld) {
            return false;
        }
        boundAccount = view.account;
        returnWorld = pending.homeWorld;
        returnX = pending.homeX;
        returnY = pending.homeY;
        returnPlane = pending.homePlane;
        hopAttempts = 0;
        nextHopAt = 0;
        nextWalkAt = 0;
        enter(Phase.RETURNING, now);
        status = "Mule: going back to W" + returnWorld + " after the transfer was interrupted.";
        cancelReason = "Mule: GE Flipper stopped during a transfer; going back to W" + returnWorld + " first.";
        return true;
    }

    /** Releases the lease and, if we left our world, heads back before resuming flipping. */
    private void abort(SenderSettings s, Snapshot view, long now, long cooldownMs, String reason) {
        String flipper = view == null ? "" : view.playerName;
        if (!flipper.isEmpty() && !targetMuleRsn.isEmpty()) {
            port.releaseMuleLease(lockedLeaseHost, lockedPort, targetMuleRsn, flipper, false);
        }
        if (cooldownMs > 0) nextAttemptAt = now + cooldownMs;
        if (!reason.isEmpty()) cancelReason = reason;
        if (view != null && view.usable() && returnWorld > 0
            && (view.currentWorld != returnWorld || view.distanceTo(returnX, returnY, returnPlane) > 3
                || view.tradeMainOpen || view.tradeConfirmOpen)) {
            hopAttempts = 0;
            nextHopAt = 0;
            nextWalkAt = 0;
            enter(Phase.RETURNING, now);
            status = reason.isEmpty() ? "Mule: returning to W" + returnWorld + "." : reason + " Returning.";
            return;
        }
        clearTransientSenderState();
        if (!reason.isEmpty()) status = reason;
    }

    private void clearTransientSenderState() {
        phase = Phase.IDLE;
        eligibleSince = -1;
        phaseSince = 0;
        lastHeartbeatMillis = -1;
        targetMuleRsn = "";
        lockedConfiguredHost = lockedLeaseHost = lockedFlipperRsn = "";
        lockedPort = lockedFixedWorld = 0;
        targetMuleWorld = 0;
        returnWorld = 0;
        returnX = returnY = returnPlane = 0;
        lockedTransferAmount = 0;
        lockedReserveGp = 0;
        lockedDeadline = 0;
        coinsBeforeTrade = -1;
        hopAttempts = 0;
        tradeRequests = 0;
        muleUnseenSince = -1;
        capitalShortSince = -1;
        status = "";
        port.reset();
    }

    void reset() {
        stopReceiver();
        if (phase != Phase.IDLE && returnWorld > 0 && boundAccount != 0) {
            interrupted = new Interrupted(boundAccount, lockedFlipperRsn, targetMuleRsn, lockedLeaseHost, lockedPort,
                targetMuleWorld, returnWorld, returnX, returnY, returnPlane);
        }
        clearTransientSenderState();
        updateTransferPending = false;
        updateDeadline = 0;
        lastCapitalGp = -1;
        liquidating = false;
        liquidationTargetGp = 0;
        boundAccount = 0;
        capitalStatus = "Estimate unavailable";
        lastProbeMillis = -1;
        nextAttemptAt = 0;
    }

    /**
     * How far below the keep amount a locked transfer may leave the total. Sell offers keep filling during the trade
     * and the GE takes 2% tax on each sale, so the total drifts down a little; that must not cancel the transfer.
     */
    private long lockedKeepTolerance() {
        return Math.max(MulePolicy.transferTolerance(lockedReserveGp), lockedTransferAmount / 50L);
    }

    /** A GP amount for mule messages, without the "+" that profit figures carry. */
    static String gp(long amount) {
        return FlipperOverlay.formatProfit(amount).replace("+", "");
    }
}
