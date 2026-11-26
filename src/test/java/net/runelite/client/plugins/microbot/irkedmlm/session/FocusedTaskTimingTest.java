package net.runelite.client.plugins.microbot.irkedmlm.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality;

/**
 * Pins the split between AFK mining timing and focused-chore timing.
 *
 * <p>The behaviour being locked in: mining is the AFK half of MLM, but a hopper trip, sack emptying
 * or a wheel repair is a short errand a real player runs with full attention. Both stay randomised —
 * a fixed cadence is its own tell — but the chore profile must be materially quicker and must not
 * carry the multi-second distraction tail the AFK profile has.
 */
class FocusedTaskTimingTest {

    private static final int SAMPLES = 4000;
    private static final long BASE_MS = 600L;

    @Test
    void focusedChoresAreQuickerOnAverageThanTheAfkProfile() {
        double afk = mean(new Probe(true, false));
        double focused = mean(new Probe(true, true));
        assertTrue(focused < afk * 0.75,
                "focused chores should be clearly snappier: focused=" + (long) focused + "ms afk=" + (long) afk + "ms");
    }

    @Test
    void focusedChoresDropTheLongDistractionTail() {
        long afkMax = max(new Probe(true, false));
        long focusedMax = max(new Probe(true, true));
        // The AFK profile can stretch a 600ms beat past 1.5s; a focused one must not.
        assertTrue(focusedMax < afkMax,
                "focused tail must be shallower than AFK: focused=" + focusedMax + "ms afk=" + afkMax + "ms");
        assertTrue(focusedMax <= BASE_MS,
                "focused 600ms beat stretched to " + focusedMax + "ms — the chore tail is back");
    }

    @Test
    void focusedDelaysStayRandomisedAndAboveTheSpamFloor() {
        Probe p = new Probe(true, true);
        long min = Long.MAX_VALUE;
        long maxSeen = 0;
        java.util.Set<Long> distinct = new java.util.HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            long d = p.delay(BASE_MS);
            min = Math.min(min, d);
            maxSeen = Math.max(maxSeen, d);
            distinct.add(d);
        }
        assertTrue(min > 0, "a focused delay collapsed to " + min + "ms — that is click-spam, not snappy");
        assertTrue(distinct.size() > 50, "focused timing became a fixed cadence (" + distinct.size() + " distinct values)");
        assertTrue(maxSeen > min, "focused timing produced no variation at all");
    }

    @Test
    void humanLikeOffIsUnaffectedByTheFocusSplit() {
        // With the human layer off both profiles use the same tight band, so the split must be a no-op.
        long fastMax = max(new Probe(false, true));
        assertTrue(fastMax <= Math.round(BASE_MS * 1.11),
                "human-like OFF must stay on the tight band, saw " + fastMax + "ms");
    }

    /**
     * The double-climb regression, walked through as a real timeline.
     *
     * <p>Two earlier fixes used a fixed dead time after the click and both failed live, because the
     * second click does not happen at the <em>start</em> of the climb — it happens at the <b>end</b>.
     * The player finishes walking to the ladder and is, for a tick or two, neither moving nor
     * animating while still on the old floor. Any fixed window has expired by then. The guard has to
     * be keyed on the outcome, which is what these assertions pin.
     */
    @Test
    void aClimbIsNeverReClickedWhileItIsStillInFlight() {
        final long click = 1_000_000L;
        final long quiet = 2_400L;

        // Just clicked: the server has not reacted, the player is still. Must not re-click.
        assertTrue(Session.climbStillPending(click, click, click + 200, quiet, false, false),
                "re-clicked before the server even reacted");

        // Walking to the ladder. However long this takes, it stays pending.
        assertTrue(Session.climbStillPending(click, click + 3_000, click + 3_000, quiet, false, true),
                "re-clicked mid-walk");
        assertTrue(Session.climbStillPending(click, click + 9_000, click + 9_000, quiet, false, true),
                "a long walk to a distant ladder timed out mid-stride");

        // THE BUG: arrived at the ladder after a 4s walk. Not moving, not yet animating, floor
        // unchanged, and far past any fixed dead time. Progress was last seen on arrival.
        assertTrue(Session.climbStillPending(click, click + 4_000, click + 4_300, quiet, false, false),
                "re-clicked on arrival — this is the exact live failure");

        // Climb animation running.
        assertTrue(Session.climbStillPending(click, click + 4_600, click + 4_600, quiet, false, true),
                "re-clicked during the climb animation");

        // Landed on the target floor — guard releases immediately, no waiting around.
        assertTrue(!Session.climbStillPending(click, click + 5_000, click + 5_100, quiet, true, false),
                "guard stayed shut after the climb landed");
    }

    @Test
    void aGenuinelyMissedClickIsRetriedAfterTheQuietWindow() {
        final long click = 1_000_000L;
        final long quiet = 2_400L;
        // Clicked, nothing ever happened: no walk, no animation, still on the old floor.
        assertTrue(Session.climbStillPending(click, click, click + 2_000, quiet, false, false),
                "gave up before the quiet window elapsed");
        assertTrue(!Session.climbStillPending(click, click, click + 2_500, quiet, false, false),
                "a missed click was never retried — the bot would be stuck on the wrong floor");
    }

    @Test
    void noClimbRequestedMeansNothingIsPending() {
        assertTrue(!Session.climbStillPending(0L, 0L, 1_000_000L, 2_400L, false, false),
                "a fresh session must be free to click immediately");
    }

    // ── Harness ─────────────────────────────────────────────────────────────

    private static double mean(Probe p) {
        double total = 0;
        for (int i = 0; i < SAMPLES; i++) {
            total += p.delay(BASE_MS);
        }
        return total / SAMPLES;
    }

    private static long max(Probe p) {
        long m = 0;
        for (int i = 0; i < SAMPLES; i++) {
            m = Math.max(m, p.delay(BASE_MS));
        }
        return m;
    }

    /** Minimal concrete Session that exposes the package-private delay maths. */
    private static final class Probe extends Session {
        private final boolean human;
        private final boolean focused;

        Probe(boolean human, boolean focused) {
            super(new IrkedMLMConfig() {
            });
            this.human = human;
            this.focused = focused;
            // Neutral personality = speed 1.0, so the measured difference is the focus split alone.
            updatePersonality(SessionPersonality.neutral());
        }

        long delay(long baseMs) {
            return tickDelayMs(baseMs, baseMs);
        }

        @Override
        protected boolean isHumanLikeEnabled() {
            return human;
        }

        @Override
        protected boolean isFocusedTask() {
            return focused;
        }

        @Override
        protected void tickInternal() {
            // not exercised
        }
    }
}
