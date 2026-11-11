package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.Random;

public final class SessionPersonality {
    private final double speed;
    private final double mouseConfidence;
    private final double afkTendency;
    private final double hesitation;
    private final double checkHabit;

    private SessionPersonality(double speed, double mouseConfidence, double afkTendency,
                               double hesitation, double checkHabit) {
        this.speed = speed;
        this.mouseConfidence = mouseConfidence;
        this.afkTendency = afkTendency;
        this.hesitation = hesitation;
        this.checkHabit = checkHabit;
    }

    public static SessionPersonality roll() {
        Random random = new Random(System.nanoTime());
        return new SessionPersonality(0.80 + random.nextDouble() * 0.50,
                triangular(random), triangular(random), triangular(random), triangular(random));
    }

    public static SessionPersonality neutral() {
        return new SessionPersonality(1.0, 0.5, 0.5, 0.5, 0.5);
    }

    private static double triangular(Random random) {
        return (random.nextDouble() + random.nextDouble()) / 2.0;
    }

    public int offScreenParkChance() { return percent(70, afkTendency, 45, 88); }
    public int earlyHoverChance() { return percent(10, hesitation, 4, 18); }
    public int lateHoverChance() { return percent(55, hesitation, 40, 72); }
    public int inventoryGlanceChance() { return percent(8, checkHabit, 3, 16); }
    public int misclickChance() { return percent(5, 1.0 - mouseConfidence, 1, 12); }
    public long reaction(long baseMs) { return Math.max(0L, Math.round(baseMs * speed)); }
    public boolean roll(int chance) { return Rs2Random.between(0, 100) < chance; }

    private static int percent(int base, double trait, int min, int max) {
        int value = (int) Math.round(base * (0.6 + 0.8 * Math.max(0, Math.min(1, trait))));
        return Math.max(min, Math.min(max, value));
    }
}
