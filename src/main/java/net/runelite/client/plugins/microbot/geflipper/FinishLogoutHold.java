package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;

/**
 * After Finish has listed everything GE Flipper makes no more input and waits for the game to log the idle account
 * out, then switches itself off. Meanwhile it keeps the client's own terminal-logout hold armed, so Break Handler
 * and AutoLogin don't log back in. That hold ends by itself when the player logs in or another script starts.
 */
final class FinishLogoutHold {
    /** The client drops a pending request after 15 seconds, so it is renewed well before that. */
    static final long RENEW_MS = 5_000L;
    /** The game normally logs out an idle player after about 5 minutes. */
    static final long GIVE_UP_MS = 10 * 60_000L;

    interface Port {
        boolean loggedOut();
        void post(Object event);
        /** Called once: true after the logout, false when the game kept the account logged in. */
        void finished(boolean loggedOut);
    }

    private final Port port;
    private long startedAt;
    private boolean active;

    FinishLogoutHold(Port port) {
        this.port = port;
    }

    synchronized void start(long nowMillis) {
        startedAt = nowMillis;
        active = true;
        port.post(new AutoLoginSuppressionRequest());
    }

    /** Runs every {@link #RENEW_MS}. Returns true once the hold is over. */
    boolean tick(long nowMillis) {
        boolean loggedOut;
        synchronized (this) {
            if (!active) return true;
            loggedOut = port.loggedOut();
            if (loggedOut) {
                // Keep the account out after the logout as well, until the player logs in again.
                port.post(AutoLoginSuppressionRequest.loggedOut());
            } else if (nowMillis - startedAt < GIVE_UP_MS) {
                port.post(AutoLoginSuppressionRequest.renewal());
                port.post(new AutoLoginSuppressionRequest());
                return false;
            }
            active = false;
        }
        port.finished(loggedOut);
        return true;
    }

    synchronized void cancel() {
        active = false;
    }

    synchronized boolean active() {
        return active;
    }
}
