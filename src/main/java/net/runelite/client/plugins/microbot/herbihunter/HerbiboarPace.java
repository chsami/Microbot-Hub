package net.runelite.client.plugins.microbot.herbihunter;

import java.util.Locale;
import java.util.Random;

/** Pure: randomness only from the seed and the Random passed in, so it can be checked. */
final class HerbiboarPace
{
    static final double FATIGUE_CAP = 1.35;

    private final boolean relaxed;
    private final long startMs;
    final double reactionMedian;
    final double attention;
    final double preHoverChance;
    final double lapseChance;
    final double fatiguePerHour;
    final double xpGlanceChance;
    final double skillTabChance;
    final double afkChance;
    /** Per stop at the bank or the G.E., and only when the user has micro breaks on. */
    final double microBreakChance;
    /** Focused only: how often a trail that goes behind the camera is followed with a turn instead of a walk. */
    final double followTurnChance;
    final double driftChance;

    enum AfterCatch { NONE, XP_GLANCE, SKILL_TAB }

    private HerbiboarPace(boolean relaxed, long startMs, double median, double attention, double preHover,
                          double lapse, double fatigue, double xpGlance, double skillTab, double afk, double drift,
                          double microBreak, double followTurn)
    {
        this.microBreakChance = microBreak;
        this.followTurnChance = followTurn;
        this.xpGlanceChance = xpGlance;
        this.skillTabChance = skillTab;
        this.afkChance = afk;
        this.driftChance = drift;
        this.relaxed = relaxed;
        this.startMs = startMs;
        this.reactionMedian = median;
        this.attention = attention;
        this.preHoverChance = preHover;
        this.lapseChance = lapse;
        this.fatiguePerHour = fatigue;
    }

    static HerbiboarPace focused()
    {
        return new HerbiboarPace(false, 0, 280, 1.0, 1.0, 0.0, 0.0, 0, 0, 0, 0, 0.02, 0.75);
    }

    static HerbiboarPace focused(long seed)
    {
        Random r = new Random(seed);
        return new HerbiboarPace(false, 0, 250 + r.nextDouble() * 60, 1.0, 1.0, 0.0, 0.0, 0, 0, 0.05 + r.nextDouble() * 0.15, 0,
            // Drawn last, so every earlier draw stays what it was for a given seed.
            0.01 + r.nextDouble() * 0.03,
            0.55 + r.nextDouble() * 0.35);
    }

    static HerbiboarPace relaxed(long seed, long sessionStartMs)
    {
        Random r = new Random(seed);
        return new HerbiboarPace(true, sessionStartMs,
            320 + r.nextDouble() * 140,
            0.6 + r.nextDouble() * 0.35,
            0.35 + r.nextDouble() * 0.40,
            0.02 + r.nextDouble() * 0.04,
            0.03 + r.nextDouble() * 0.05,
            0.12 + r.nextDouble() * 0.08,
            0.03 + r.nextDouble() * 0.03,
            0.30 + r.nextDouble() * 0.20,
            // Camera drift per search, kept rare: more looks restless.
            0.015 + r.nextDouble() * 0.045,
            0.04 + r.nextDouble() * 0.06,
            // Relaxed keeps its lazy camera.
            0);
    }

    boolean isRelaxed()
    {
        return relaxed;
    }

    /** Log-normal: right-skewed like a person's reaction time. */
    int reactionMs(long nowMs, Random r)
    {
        if (!relaxed)
        {
            return (int) Math.max(150, Math.round(reactionMedian * Math.exp(0.25 * r.nextGaussian())));
        }
        double ms = reactionMedian * Math.exp(0.25 * r.nextGaussian()) * fatigue(nowMs);
        return (int) Math.max(160, Math.round(ms));
    }

    double fatigue(long nowMs)
    {
        if (!relaxed)
        {
            return 1.0;
        }
        double hours = Math.max(0, nowMs - startMs) / 3_600_000.0;
        return Math.min(FATIGUE_CAP, 1.0 + fatiguePerHour * hours);
    }

    long lapseMs(Random r)
    {
        if (!relaxed || r.nextDouble() >= lapseChance)
        {
            return 0;
        }
        return 1_500 + (long) (r.nextDouble() * 4_500);
    }

    AfterCatch afterCatch(Random r)
    {
        double d = r.nextDouble();
        if (d < xpGlanceChance)
        {
            return AfterCatch.XP_GLANCE;
        }
        return d < xpGlanceChance + skillTabChance ? AfterCatch.SKILL_TAB : AfterCatch.NONE;
    }

    static final long AFK_MAX_MS = 15_000;

    /** Never above 15 s: the stuck watchdog fires at 60 s. */
    long afkAfterBankMs(Random r)
    {
        if (r.nextDouble() >= afkChance)
        {
            return 0;
        }
        return Math.min(AFK_MAX_MS, 2_000 + (long) (r.nextDouble() * 13_000));
    }

    boolean driftCameraDuringSearch(Random r)
    {
        return relaxed && r.nextDouble() < driftChance;
    }

    boolean preHover(Random r)
    {
        return !relaxed || r.nextDouble() < preHoverChance;
    }

    double preHoverChance()
    {
        return preHoverChance;
    }

    String describe()
    {
        if (!relaxed)
        {
            return String.format(Locale.ROOT, "Focused: reaction ~%.0f ms, step away after a bank %.0f%%", reactionMedian,
                afkChance * 100);
        }
        return String.format(Locale.ROOT, "Relaxed: reaction ~%.0f ms, attention %.2f, fatigue +%.1f%%/h,"
                + " pre-hover %.0f%%, lapse %.1f%%", reactionMedian, attention, fatiguePerHour * 100,
            preHoverChance * 100, lapseChance * 100);
    }
}
