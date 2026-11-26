package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.Random;
import lombok.Getter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Per-login behavioural identity for the MLM bot.
 *
 * <h3>Why this exists</h3>
 * Believable behaviour is <em>consistent</em>, not merely random. A behavioural classifier keys on
 * two things: how stable a player is <em>within</em> a session, and how much players differ
 * <em>between</em> sessions. Re-rolling every decision from a global RNG (e.g. "70% park the mouse
 * off-screen" independently each bout) produces the opposite — a run with no identity and two logins
 * that are statistically identical.
 *
 * <p>This class is rolled <b>once</b> per {@code run()} (login) from a per-session seed, producing a
 * fixed trait vector. Every probability/timing knob below is a pure function of those traits, so the
 * numbers stay constant for the whole session and differ from login to login. Call sites read the
 * named accessors instead of hard-coding literals, which also makes this the single home for
 * behavioural tuning.
 *
 * <p>The randomness the bot exhibits still comes from {@link Rs2Random} at the moment of action — but
 * it is now <em>gated by a stable character</em> rather than floating free. An impatient, mouse-confident
 * player stays that way all session; a relaxed AFK-heavy player keeps parking the cursor off-screen.
 */
public final class SessionPersonality {

    // Traits. speed multiplies delays (0.80 fast .. 1.30 slow); the rest are 0..1 propensities.
    @Getter private final double speed;
    @Getter private final double patience;
    @Getter private final double mouseConfidence;
    @Getter private final double afkTendency;
    @Getter private final double hesitation;
    @Getter private final double checkHabit;
    @Getter private final long   seed;

    private SessionPersonality(double speed, double patience, double mouseConfidence,
                               double afkTendency, double hesitation, double checkHabit, long seed) {
        this.speed           = speed;
        this.patience        = patience;
        this.mouseConfidence = mouseConfidence;
        this.afkTendency     = afkTendency;
        this.hesitation      = hesitation;
        this.checkHabit      = checkHabit;
        this.seed            = seed;
    }

    /** Rolls a fresh personality from a time-based seed (call once per login). */
    public static SessionPersonality roll() {
        return roll(System.nanoTime());
    }

    /** Seeded roll — deterministic for tests. */
    public static SessionPersonality roll(long seed) {
        Random r = new Random(seed);
        return new SessionPersonality(
                0.80 + r.nextDouble() * 0.50, // speed 0.80..1.30
                triangular(r),                // patience
                triangular(r),                // mouseConfidence
                triangular(r),                // afkTendency
                triangular(r),                // hesitation
                triangular(r),                // checkHabit
                seed);
    }

    /**
     * Neutral, average player — all propensities 0.5, no speed bias. Used as the safe default before a
     * personality is rolled, and to reproduce the plugin's original hard-coded constants exactly.
     */
    public static SessionPersonality neutral() {
        return new SessionPersonality(1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0L);
    }

    /** Average of two uniforms → biased toward the middle: most players are average, few are extreme. */
    private static double triangular(Random r) {
        return (r.nextDouble() + r.nextDouble()) / 2.0;
    }

    // ------------------------------------------------------------------
    // Named behavioural knobs (base value ± per-session trait variance)
    // ------------------------------------------------------------------

    // Mining-bout mouse behaviour (MiningSession)
    public int offScreenParkChance() { return pct(70, afkTendency, 45, 88); }
    public int earlyHoverChance()    { return pct(10, hesitation,   4, 18); }
    public int lateHoverChance()     { return pct(55, hesitation,  40, 72); }
    public int misclickChance()      { return pct(5, 1.0 - mouseConfidence, 1, 12); }

    // Ambient inventory-glance while mining (MiningSession). Correlated: only fires while the cursor is on
    // the canvas — an AFK player parked off-screen isn't glancing at their inventory.
    public int inventoryGlanceChance() { return pct(8, checkHabit,  3, 16); }

    // Timing character (Session#jitter, Script gear-up)
    public int    slowOutlierChance() { return pct(12, 1.0 - patience, 6, 20); }
    public int    gearUpPauseChance() { return pct(25, 1.0 - patience, 12, 40); }
    public double jitterLow()  { return 0.65 + (mouseConfidence - 0.5) * 0.12; } // confident = tighter band
    public double jitterHigh() { return 1.35 - (mouseConfidence - 0.5) * 0.12; }

    // Spec / strut decisions (Script, RepairSession)
    public int specHesitationChance() { return pct(15, hesitation, 6, 26); }
    public int oneStrutSkipChance()   { return pct(90, patience,  82, 96); }

    /**
     * How long (ms) to keep re-attempting a single broken strut before giving up on the job.
     * Repairing is a Smithing-gated skill roll (~12% success/click at level 1, ~28% at 99), needing
     * on average 4-8 clicks — a fixed short attempt cap gives up mid-job most of the time, which then
     * re-triggers a full hammer refetch next cycle. Patient players keep at it longer; impatient ones
     * bail sooner — so this varies per login rather than being one more fixed, guessable number.
     */
    public long repairPersistenceMs() { return Math.round(45_000 + patience * 45_000); } // 45s..90s

    /** Scales a base delay by this player's overall reaction speed. */
    public long reaction(long baseMs) {
        return Math.max(0L, Math.round(baseMs * speed));
    }

    /** Rolls a live decision against a personality-biased percent chance. */
    public boolean roll(int chancePercent) {
        return Rs2Random.between(0, 100) < chancePercent;
    }

    /**
     * Maps a trait in [0,1] to a multiplier in [0.6, 1.4] around {@code base}, clamped to [min, max].
     * trait=0.5 (average player) returns {@code base}, preserving the original hard-coded value.
     */
    private static int pct(int base, double trait, int min, int max) {
        int v = (int) Math.round(base * (0.6 + 0.8 * clamp01(trait)));
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp01(double d) {
        return d < 0 ? 0 : (d > 1 ? 1 : d);
    }

    @Override
    public String toString() {
        return String.format(
                "SessionPersonality[speed=%.2f patience=%.2f mouse=%.2f afk=%.2f hesitation=%.2f check=%.2f]",
                speed, patience, mouseConfidence, afkTendency, hesitation, checkHabit);
    }
}
