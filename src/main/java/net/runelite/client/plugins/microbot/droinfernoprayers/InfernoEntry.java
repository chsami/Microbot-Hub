package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.function.LongSupplier;

/** One local entrance action at a time; only the instance boundary proves arrival. */
final class InfernoEntry {
    enum Result { WAITING, ARRIVED, STOPPED }
    interface Port {
        boolean inside();
        boolean allowed();
        boolean jump();
    }
    private final Port port;
    private final LongSupplier clock;
    private boolean started;
    private long startedAt, nextAttempt;
    private int attempts;
    private Result result=Result.WAITING;
    private String status="Looking for Jump-in The Inferno";
    InfernoEntry(Port port, LongSupplier clock) { this.port=port;this.clock=clock; }
    String status() { return status; }
    void resumeAfter(long millis) { if(millis>0){if(started)startedAt+=millis;nextAttempt+=millis;} }
    Result tick() {
        if (result!=Result.WAITING) return result;
        if (port.inside()) { status="Inferno instance arrival confirmed";return result=Result.ARRIVED; }
        if (!port.allowed()) return result;
        long now=clock.getAsLong();
        if (!started) { started=true;startedAt=now; }
        if (now-startedAt>=30_000L) {
            status="Inferno entry not confirmed; check access or any entry dialogue and restart";
            return result=Result.STOPPED;
        }
        if (attempts<3 && now>=nextAttempt) {
            nextAttempt=now+6_000L;
            if (port.jump()) {
                attempts++;status="Jump-in requested; waiting for confirmed Inferno instance";
            } else status="Jump-in The Inferno unavailable; check access and camera";
        }
        return result;
    }
}
