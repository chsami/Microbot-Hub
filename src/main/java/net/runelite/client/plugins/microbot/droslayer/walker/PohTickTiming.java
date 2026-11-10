package net.runelite.client.plugins.microbot.droslayer.walker;

/** Nonblocking POH pauses for client-thread navigation state machines. */
public final class PohTickTiming {
    private boolean inside;
    private long readyAt;

    public boolean hold(boolean inHouse, long now) {
        if (!inHouse) { inside=false; readyAt=0; return false; }
        if (!inside) {
            inside=true;
            readyAt=now+MaxPohSupport.pauseMillis(true);
        }
        return now<readyAt;
    }

    public void reset() { inside=false; readyAt=0; }

    public void action(long now) {
        if (inside) readyAt=now+MaxPohSupport.pauseMillis(false);
    }
}
