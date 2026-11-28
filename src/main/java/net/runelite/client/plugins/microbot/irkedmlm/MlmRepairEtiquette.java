package net.runelite.client.plugins.microbot.irkedmlm;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * One definition of "somebody else is already fixing the waterwheel", and one clock for how long we
 * are prepared to leave them to it.
 *
 * <h3>Why this exists</h3>
 * The proximity test used to be written out twice — once in the orchestrator's pre-deposit gate and
 * once inside {@code RepairSession} — with their own radii and their own override flags, so the two
 * could and did disagree.
 *
 * <p>Worse, the "they've had long enough" timeout was measured from <b>when the strut broke</b>,
 * which is not the same thing at all. The bot notices the break, walks down, fetches a hammer, walks
 * to the wheel — and by the time it arrives the timer has already expired, so it decided the
 * bystander was idle and repaired straight over them every single time. The clock here starts when
 * <em>we begin yielding</em>, which is the thing the timeout is actually about.
 *
 * <h3>On fixed numbers</h3>
 * Neither the radius nor the patience is a constant. A bot that always treats exactly 5 tiles as
 * "near" and always waits exactly 25.000s is describing itself. The radius is rolled once per login
 * so it stays stable within a session (a radius that flickered tick to tick would make the bot
 * oscillate between yielding and barging, which is worse than a fixed one), and the patience is
 * re-rolled for each yield, because how long you're willing to stand around genuinely does vary.
 */
public final class MlmRepairEtiquette {

    /** Tile radius around the wheel that counts as "at it"; rolled once per login. */
    private static final int RADIUS_MIN = 4;
    private static final int RADIUS_MAX = 8;

    /** How long we leave a bystander to it before taking the job back. Re-rolled per yield. */
    private static final int PATIENCE_MIN_MS = 18_000;
    private static final int PATIENCE_MAX_MS = 48_000;

    private int radius = 0;
    private long yieldStartedMs = 0L;
    private long patienceMs = 0L;

    /** Re-rolls the per-login radius and clears any yield in progress. Call from the script's run(). */
    public void reset() {
        radius = Rs2Random.between(RADIUS_MIN, RADIUS_MAX + 1);
        yieldStartedMs = 0L;
        patienceMs = 0L;
    }

    private int radius() {
        if (radius <= 0) {
            radius = Rs2Random.between(RADIUS_MIN, RADIUS_MAX + 1);
        }
        return radius;
    }

    /** Other players (never the local one) currently stood at the waterwheel. */
    public int playersAtWheel(String localPlayerName) {
        WorldPoint wheel = IrkedMLMMapConstants.WATERWHEEL_AREA;
        int r = radius();
        return Microbot.getRs2PlayerCache().query()
                .where(p -> {
                    String name = p.getName();
                    if (name == null || name.isEmpty()) {
                        return false;
                    }
                    // An empty local name (not yet fetched) must not make us count ourselves as a
                    // bystander and yield to our own reflection.
                    if (localPlayerName != null && !localPlayerName.isEmpty()
                            && name.equalsIgnoreCase(localPlayerName)) {
                        return false;
                    }
                    WorldPoint loc = p.getWorldLocation();
                    return loc != null && loc.distanceTo(wheel) <= r;
                })
                .count();
    }

    /**
     * Starts (or continues) yielding to a bystander. The patience window is rolled on the first call
     * of an episode and left alone afterwards, so the countdown is stable once it has begun.
     */
    public void noteYielding() {
        if (yieldStartedMs == 0L) {
            yieldStartedMs = System.currentTimeMillis();
            patienceMs = Rs2Random.between(PATIENCE_MIN_MS, PATIENCE_MAX_MS + 1);
        }
    }

    /** Ends the current yield episode — the wheel was fixed, or nobody is there any more. */
    public void clearYield() {
        yieldStartedMs = 0L;
        patienceMs = 0L;
    }

    /** True once we have yielded longer than this episode's patience — the bystander clearly isn't fixing it. */
    public boolean yieldExpired() {
        return yieldStartedMs > 0L && System.currentTimeMillis() - yieldStartedMs >= patienceMs;
    }

    /** Seconds spent yielding in the current episode, for logging. */
    public long yieldedSeconds() {
        return yieldStartedMs > 0L ? (System.currentTimeMillis() - yieldStartedMs) / 1000L : 0L;
    }

    /**
     * The whole decision: should we leave the wheel alone right now?
     *
     * @param waitForOthers the user's "Wait for Others to Repair" setting
     * @param localPlayerName our own name, so we never yield to ourselves
     */
    public boolean shouldYield(boolean waitForOthers, String localPlayerName) {
        if (!waitForOthers) {
            clearYield();
            return false;
        }
        if (playersAtWheel(localPlayerName) == 0) {
            clearYield();
            return false;
        }
        noteYielding();
        if (yieldExpired()) {
            return false; // stood there long enough without fixing it — take the job
        }
        return true;
    }
}
