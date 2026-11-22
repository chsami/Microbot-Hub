package net.runelite.client.plugins.microbot.herbihunter;

import java.util.Locale;
import java.util.Random;

/** Drawn once per session so no two sessions share them: fixed rules repeated over hours read as one signature. */
final class HerbiboarHabits
{
    final int turnComfortDeg;
    final int turnMaxDeg;
    final double pitchSoonChance;
    final double pitchIgnoreChance;
    final double blockerRecall;
    final double impatience;
    /** Furthest a spot is clicked from: never near where the game stops drawing them. */
    final int clickRangeTiles;
    final int behindDeg;
    final double patience;
    final double sackFirst;

    private HerbiboarHabits(int comfort, int max, double soon, double ignore, double recall, double impatience, int range,
                            int behind, double patience, double sackFirst)
    {
        this.sackFirst = sackFirst;
        this.behindDeg = behind;
        this.patience = patience;
        this.clickRangeTiles = range;
        this.turnComfortDeg = comfort;
        this.turnMaxDeg = max;
        this.pitchSoonChance = soon;
        this.pitchIgnoreChance = ignore;
        this.blockerRecall = recall;
        this.impatience = impatience;
    }

    static HerbiboarHabits draw(long seed)
    {
        Random r = new Random(seed);
        int comfort = 18 + r.nextInt(18);
        return new HerbiboarHabits(comfort, comfort + 15 + r.nextInt(26),
            0.50 + r.nextDouble() * 0.35,
            0.05 + r.nextDouble() * 0.10,
            0.60 + r.nextDouble() * 0.30,
            0.02 + r.nextDouble() * 0.04,
            26 + r.nextInt(6),
            100 + r.nextInt(46),
            r.nextDouble() < 0.2 ? Double.POSITIVE_INFINITY : 1.2 + r.nextDouble() * 6.0,
            0.15 + r.nextDouble() * 0.70);
    }

    boolean emptiesSackFirst(Random r)
    {
        return r.nextDouble() < sackFirst;
    }

    double annoyance(double now, int degreesOff, Random r)
    {
        if (Math.abs(degreesOff) >= behindDeg) return now + 0.15 + r.nextDouble() * 0.45;
        // In front is the norm: a small fade, or spots behind would never add up to a turn.
        return Math.max(0, now - (0.02 + r.nextDouble() * 0.06));
    }

    double breakingPoint(Random r)
    {
        return patience * (0.5 + r.nextDouble() * 1.2);
    }

    /** getAngleTo is camera minus target; a part turn leaves under {@code maxLeftDeg} so the spot ends up in view. */
    static int turnAroundTarget(int cameraAngle, int degreesOff, int maxLeftDeg, Random r)
    {
        int jitter = r.nextInt(61) - 30;
        double share = 1.0;
        if (r.nextDouble() < 0.35) {
            // Least share that keeps |off * (1 - share)| + |jitter| within maxLeftDeg.
            double least = Math.max(0.55, 1.0 - Math.max(0, maxLeftDeg - Math.abs(jitter)) / (double) Math.max(1, Math.abs(degreesOff)));
            share = least >= 0.85 ? least : least + r.nextDouble() * (0.85 - least);
        }
        return Math.floorMod(Math.round(cameraAngle - (float) (degreesOff * share)) + jitter, 360);
    }

    boolean turnsFor(int degreesOff, Random r)
    {
        int d = Math.abs(degreesOff);
        if (d <= turnComfortDeg) return true;
        if (d >= turnMaxDeg) return false;
        return r.nextDouble() < (double) (turnMaxDeg - d) / (turnMaxDeg - turnComfortDeg);
    }

    long pitchFixDelayMs(Random r)
    {
        double d = r.nextDouble();
        if (d < pitchIgnoreChance) return 300_000 + (long) (r.nextDouble() * 600_000);
        if (d < pitchIgnoreChance + pitchSoonChance) return 3_000 + (long) (r.nextDouble() * 27_000);
        return 60_000 + (long) (r.nextDouble() * 180_000);
    }

    boolean recallsBlocker(Random r)
    {
        return r.nextDouble() < blockerRecall;
    }

    boolean impatientPress(Random r)
    {
        return r.nextDouble() < impatience;
    }

    String describe()
    {
        return String.format(Locale.ROOT, "turns %d-%d deg, clicks from %d tiles, flat camera fixed soon %.0f%% / left %.0f%%,"
                + " blocker recall %.0f%%, impatient %.1f%%, behind past %d deg, turn-round patience %s", turnComfortDeg,
            turnMaxDeg, clickRangeTiles, pitchSoonChance * 100, pitchIgnoreChance * 100, blockerRecall * 100, impatience * 100,
            behindDeg, Double.isInfinite(patience) ? "never" : String.format(Locale.ROOT, "%.2f", patience))
            + String.format(Locale.ROOT, ", sack first %.0f%%", sackFirst * 100);
    }
}
