package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.Objects;

/** One exit action at a time, with observed floor changes and bounded retries. */
final class SpellbookHouseExit {
    static final class ExitPending extends IllegalStateException {
        ExitPending(String message) { super(message); }
    }
    interface Port {
        boolean inside();
        boolean busy();
        int floor();
        String exitKey();
        boolean click();
    }
    private long started = -1, nextAction;
    private int floor = -1, attempts;
    private String key;

    void reset() { started = -1; nextAction = 0; floor = -1; attempts = 0; key = null; }
    void resumeAfter(long millis){if(started>=0)started+=Math.max(0,millis);nextAction+=Math.max(0,millis);}

    boolean step(Port port, long now) {
        if (!port.inside()) { reset(); return true; }
        if (started < 0) started = now;
        if (now - started > 60_000)
            throw new ExitPending("House exit was not confirmed within 60 seconds; preparation stopped");
        if (port.busy() || now < nextAction) return false;
        int observedFloor = port.floor();
        String exit = port.exitKey();
        if (exit == null)
            throw new ExitPending("Cannot leave this house floor: no loaded exit portal or stairs");
        if (floor != observedFloor || !Objects.equals(key, exit)) {
            floor = observedFloor; key = exit; attempts = 0;
        }
        if (attempts >= 3)
            throw new ExitPending("House exit did not respond after 3 attempts; preparation stopped");
        // InputHeld propagates without recording an action that was never submitted.
        port.click();
        attempts++;
        nextAction = now + (exit.startsWith("controls:")&&!exit.equals("controls:Leave")?900:5_000);
        return false;
    }
}
