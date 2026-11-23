package net.runelite.client.plugins.microbot.anvilsmither;

import java.util.Random;

/**
 * How the player of this session behaves, drawn once per run so two sessions are not the same
 * machine. Everything here is pure, so it can be checked without a client.
 */
final class AnvilHabits {
    static final int REACT_FLOOR_MS = 120;
    static final long LONG_LOAD_MS = 40_000;

    /** Speed of reactions this session: under 1 is brisk, over 1 is slower. */
    final double pace;
    /** Out of 100 loads, how many the player is watching when the last bar is used. */
    final int attentivePct;
    /** Out of 100 loads, how many the player has drifted right off (the rest looked away briefly). */
    final int afkPct;
    /** Out of 100 loads, how many see the cursor do something while the bars are hammered. */
    final int idleMovePct;
    /** Of those, how many leave the game window rather than rest over the bank. */
    final int offScreenPct;
    /** Out of 100 trips, how many use the next booth or anvil along. */
    final int otherSpotPct;
    /** Chance of a short break after a load, when the Break Handler allows them. */
    final double microBreakChance;

    private AnvilHabits(Random r) {
        pace = 0.85 + r.nextDouble() * 0.40;
        attentivePct = 45 + r.nextInt(26);
        afkPct = 3 + r.nextInt(10);
        // Seen live: at 25-50% moving and 20-60% of that off the window, the rest over the bank
        // was about one load in five and read as gone. Resting on the booth is the habit.
        idleMovePct = 70 + r.nextInt(23);
        offScreenPct = 8 + r.nextInt(18);
        otherSpotPct = 3 + r.nextInt(8);
        microBreakChance = 0.01 + r.nextDouble() * 0.03;
    }

    static AnvilHabits draw(Random r) {
        return new AnvilHabits(r);
    }

    /** Pause before a click the player was expecting to make. Right-skewed, never instant. */
    long reactionMs(Random r, int medianMs) {
        double sample = medianMs * pace * Math.exp(0.30 * r.nextGaussian());
        return Math.max(REACT_FLOOR_MS, Math.min(Math.round(sample), medianMs * 5L));
    }

    /**
     * How long after the last bar the player acts. A full load is a minute of nothing to do, so most
     * of the time they are back within a second or two, sometimes several seconds, now and then
     * longer. A short load (5 platebodies is 15 seconds) leaves no time to drift off, so the lapses
     * shrink with the load: seen live as a 4 second stall after every 15 second load.
     */
    long loadEndDelayMs(Random r, boolean cursorLeftTheWindow, long loadMs) {
        double idle = Math.max(0, Math.min(1.0, loadMs / 60_000.0));
        int roll = r.nextInt(100);
        boolean lapsed = r.nextDouble() < idle;
        // Nobody walks away from a 15 second load: seen live as a 24 second stall on one. The long
        // absence needs a load long enough to leave, and a cursor off the window is back sooner.
        boolean longLoad = loadMs >= LONG_LOAD_MS;
        if (longLoad && lapsed && roll < afkPct) return logNormal(r, 8_000, 45_000);
        if (cursorLeftTheWindow) return longLoad ? logNormal(r, 1_500, 9_000) : logNormal(r, 900, 3_500);
        if (lapsed && roll >= afkPct + attentivePct) return longLoad ? logNormal(r, 1_500, 9_000) : logNormal(r, 900, 3_500);
        return logNormal(r, 300, 1_100);   // watching: 450-1,400 read slow live
    }

    /** About 95% of draws fall between min and max, with the long side towards max. */
    static long logNormal(Random r, int min, int max) {
        double mu = (Math.log(min) + Math.log(max)) / 2.0;
        double sigma = (Math.log(max) - Math.log(min)) / 3.92;
        return Math.max(min, Math.min(max, Math.round(Math.exp(mu + sigma * r.nextGaussian()))));
    }

    String describe() {
        return String.format("pace %.2f, watching %d%%%% of loads, away %d%%%%, cursor moves on %d%%%% (off-window %d%%%%), "
                        + "other booth or anvil %d%%%%, break chance %.1f%%%%",
                pace, attentivePct, afkPct, idleMovePct, offScreenPct, otherSpotPct, microBreakChance * 100);
    }
}
