package net.runelite.client.plugins.microbot.herbihunter;

import java.util.Arrays;

/**
 * Splits the delay between the game revealing a step and our next action into its phases, for the [HB-T] log line.
 * Pure. Synchronized: a step is opened on the client thread and marked and closed on the script thread.
 */
final class HerbiboarStepTimer
{
    private static final String[] PHASES = {"seen", "react", "anim", "upkeep", "pass", "aim"};

    private final long[] spent = new long[PHASES.length];
    private final boolean[] marked = new boolean[PHASES.length];
    private String what;
    private long landedAt;
    private long last;
    private long lastTotalMs;
    private String lastWhat;

    /** Opens a step; replaces any step still open. */
    synchronized void landed(long nowMs, String what)
    {
        this.what = what;
        landedAt = nowMs;
        last = nowMs;
        Arrays.fill(spent, 0);
        Arrays.fill(marked, false);
    }

    /** Records the end of a phase; a phase already recorded for this step is ignored. */
    synchronized void mark(String phase, long nowMs)
    {
        if (what == null) return;
        for (int i = 0; i < PHASES.length; i++)
        {
            if (!PHASES[i].equals(phase) || marked[i]) continue;
            marked[i] = true;
            spent[i] = nowMs - last;
            last = nowMs;
            return;
        }
    }

    /** Closes the step and returns its log line, or null when none is open. */
    synchronized String finish(String kind, long nowMs)
    {
        if (what == null) return null;
        StringBuilder sb = new StringBuilder("[HB-T] ").append(what).append(" -> ").append(kind)
            .append(" total=").append(nowMs - landedAt);
        for (int i = 0; i < PHASES.length; i++) sb.append(' ').append(PHASES[i]).append('=').append(spent[i]);
        sb.append(" press=").append(nowMs - last);
        lastTotalMs = nowMs - landedAt;
        lastWhat = what;
        what = null;
        return sb.toString();
    }

    /** The step {@link #finish} last closed, for the per-catch summary. */
    synchronized long lastTotalMs()
    {
        return lastTotalMs;
    }

    synchronized String lastWhat()
    {
        return lastWhat;
    }

    synchronized void reset()
    {
        what = null;
    }
}
