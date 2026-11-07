package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Pure tick-timing for Scurrius's protection prayers. No client access, so it is unit tested.
 *
 * <p>How the hit time is known: Scurrius's attack animation tells the style (melee, range, magic) but not when the hit
 * lands. The delay between the animation being first seen and the hitsplat on the player is learned per style from the
 * hits actually taken ({@link #onHit}). A delay is trusted only after two equal observations in a row for that style
 * ({@link #CONFIRMATIONS}); until all three styles are trusted the prayer stays on all the time (always-on behaviour).
 *
 * <p>Tick convention: every method takes the client tick count of the GameTick being processed. A click made while
 * handling tick {@code t} is applied by the server in tick {@code t + 1}. So for a hit landing at tick {@code L}
 * the prayer must be requested on by tick {@code L - 1}. The right style is requested on the very tick the attack is
 * first seen ({@code A}, where {@code A <= L - 1}), in every mode, and {@link #decide} keeps it on through tick {@code L}
 * (lazy flick). The request to switch off is made once the hit has landed (the hitsplat clears the pending attack), or
 * one tick after {@code L} if no hitsplat was seen.
 *
 * <p>The style comes from either the attack animation or the projectile aimed at the player; both are signals for the
 * same attack when they arrive within {@link #SAME_ATTACK_TICKS} of each other, and a later signal with another style
 * corrects the earlier one (the projectile is the authority).
 *
 * <p>Lazy flick is deliberately not perfect (all random, rolled once per attack, never changing the protection): the
 * prayer is switched on at {@code L - 1} (65%), at {@code L - 2} (25%) or on the animation tick (10%), never later than
 * {@code L - 1}; it stays on for a random hold of ticks after the hit; and now and then (10%) it is not flicked at all
 * for an attack: it goes on at once and stays on until the next attack. A new attack seen while the prayer is still
 * on just switches the style, so there is never an off-then-on. Turning it on is left to the caller to delay inside
 * the tick ({@link #onDelayMs}), as is turning it off ({@link #offDelayMs}).
 *
 * <p>Safety: an unprotected hit, an unexpected hit, rats attacking, or an unknown timing keep the prayer on.
 */
public final class PrayerFlickTiming {
    public enum Style { MELEE, RANGE, MAGIC }

    /** What to do this tick: which protection style should be active (null = none) and whether to pulse it. */
    public static final class Decision {
        public final Style style;
        /** One-tick flick: switch the prayer off and on again within this tick. */
        public final boolean pulse;
        /** Why, for the log: "early", "held", "kept on" or empty. */
        public final String tag;

        Decision(Style style, boolean pulse) {
            this(style, pulse, "");
        }

        Decision(Style style, boolean pulse, String tag) {
            this.style = style;
            this.pulse = pulse;
            this.tag = tag;
        }
    }

    public static final int MIN_DELAY = 1;
    public static final int MAX_DELAY = 8;
    public static final int CONFIRMATIONS = 2;
    /** Ticks the prayer is held on (no flicking) after a hit that was not blocked. */
    public static final int UNPROTECTED_HOLD_TICKS = 10;
    private static final int PENDING_EXPIRY_TICKS = MAX_DELAY + 4;
    /** Signals (animation, projectile) closer than this belong to one attack: Scurrius attacks at most every 4-5 ticks. */
    public static final int SAME_ATTACK_TICKS = 3;

    /** When, relative to the landing tick L, the prayer is switched on for one attack. Never later than L - 1. */
    enum OnPlan { NORMAL, EARLY, IMMEDIATE }

    private static final class Pending {
        final int startTick;
        final Style style;
        final OnPlan plan;
        /** Not flicked at all: on at once, and kept on until the next attack. */
        final boolean keepOn;
        boolean holdStarted;

        Pending(int startTick, Style style, OnPlan plan, boolean keepOn) {
            this.startTick = startTick;
            this.style = style;
            this.plan = plan;
            this.keepOn = keepOn;
        }

        /** The tick the prayer must be requested on from: at the latest landing - 1. */
        int onTick(int landing) {
            switch (plan) {
                case IMMEDIATE:
                    return startTick;
                case EARLY:
                    return Math.max(startTick, landing - 2);
                default:
                    return Math.max(startTick, landing - 1);
            }
        }
    }

    public static final int KEEP_ON_PERCENT = 10;
    public static final int IMMEDIATE_PERCENT = 10;
    public static final int EARLY_PERCENT = 25;
    public static final int MAX_ON_DELAY_MS = 120;
    public static final int MIN_OFF_DELAY_MS = 30;
    public static final int MAX_OFF_DELAY_MS = 150;
    private static final int FOREVER = Integer.MAX_VALUE;

    private final Deque<Pending> pending = new ArrayDeque<>();
    private final Map<Style, Integer> lastDelay = new EnumMap<>(Style.class);
    private final Map<Style, Integer> confirmedDelay = new EnumMap<>(Style.class);
    private final Map<Style, Integer> streak = new EnumMap<>(Style.class);
    private Style lastStyle;
    private int lastSignalTick = Integer.MIN_VALUE;
    private int holdUntil = Integer.MIN_VALUE;
    private final Random random;
    private boolean variation = false;
    private int holdMin;
    private int holdMax;
    /** After a hit the prayer stays on while tick < flickHoldEnd (lazy flick), in the style of the last attack. */
    private int flickHoldEnd = Integer.MIN_VALUE;
    private Style flickHoldStyle;
    private int lastHoldTicks;

    public PrayerFlickTiming() {
        this(new Random());
    }

    /** A source of randomness that tests can seed. */
    public PrayerFlickTiming(Random random) {
        this.random = random;
    }

    /** Lazy flick: extra ticks the prayer stays on after a hit, random per attack between the two (0 or more). */
    public void setLazyHold(int min, int max) {
        holdMin = Math.max(0, Math.min(min, max));
        holdMax = Math.max(0, Math.max(min, max));
    }

    /** Lazy flick: vary when the prayer goes on and sometimes do not flick at all. Off = always on at L - 1. */
    public void setLazyVariation(boolean variation) {
        this.variation = variation;
    }

    /** The hold, in ticks, rolled for the last attack that landed; for the log line. */
    public int lastHoldTicks() {
        return lastHoldTicks;
    }

    /** Milliseconds into the tick to wait before turning the prayer on (not before a style switch). */
    public static int onDelayMs(Random random) {
        return random.nextInt(MAX_ON_DELAY_MS + 1);
    }

    /** Milliseconds into the tick to wait before turning the prayer off. */
    public static int offDelayMs(Random random) {
        return MIN_OFF_DELAY_MS + random.nextInt(MAX_OFF_DELAY_MS - MIN_OFF_DELAY_MS + 1);
    }

    private Pending newAttack(int tick, Style style) {
        if (!variation) {
            return new Pending(tick, style, OnPlan.NORMAL, false);
        }
        boolean keepOn = random.nextInt(100) < KEEP_ON_PERCENT;
        int roll = random.nextInt(100);
        OnPlan plan = roll < IMMEDIATE_PERCENT ? OnPlan.IMMEDIATE
                : roll < IMMEDIATE_PERCENT + EARLY_PERCENT ? OnPlan.EARLY : OnPlan.NORMAL;
        return new Pending(tick, style, keepOn ? OnPlan.IMMEDIATE : plan, keepOn);
    }

    private void startHold(int offTick, Style style, boolean keepOn) {
        lastHoldTicks = holdMin + (holdMax > holdMin ? random.nextInt(holdMax - holdMin + 1) : 0);
        flickHoldStyle = style;
        flickHoldEnd = keepOn ? FOREVER : offTick + lastHoldTicks;
    }

    /** Scurrius started an attack of this style (first tick its attack animation was seen). */
    public void onAttackStarted(int tick, Style style) {
        pending.addLast(newAttack(tick, style));
        lastStyle = style;
        lastSignalTick = tick;
    }

    /**
     * An attack animation or a projectile aimed at the player showed this style. A new attack unless it follows another
     * signal within {@link #SAME_ATTACK_TICKS}; then it is the same attack, and if the style differs it replaces it.
     */
    public void onStyleSignal(int tick, Style style) {
        Pending last = pending.peekLast();
        if (last != null && tick - lastSignalTick >= 0 && tick - lastSignalTick < SAME_ATTACK_TICKS) {
            if (last.style != style) {
                pending.removeLast();
                pending.addLast(new Pending(last.startTick, style, last.plan, last.keepOn));
                lastStyle = style;
            }
            return;
        }
        onAttackStarted(tick, style);
    }

    /**
     * A hitsplat landed on the player.
     *
     * @param damage    the hit amount (0 for a block)
     * @param activeNow the protection style active when the hit landed, or null
     * @param ambiguous true when the hit may come from something other than Scurrius (rats, falling rocks), so it
     *                  must not be used for learning and does not count as an unprotected Scurrius hit
     */
    public void onHit(int tick, int damage, Style activeNow, boolean ambiguous) {
        expire(tick);
        if (ambiguous) {
            return;
        }
        Pending attack = pending.pollFirst();
        if (attack == null) {
            if (damage > 0) {
                holdUntil = tick + UNPROTECTED_HOLD_TICKS; // a hit nobody predicted
            }
            return;
        }
        if (damage > 0 && activeNow != attack.style) {
            forget(attack.style);
            holdUntil = tick + UNPROTECTED_HOLD_TICKS;
            return;
        }
        startHold(tick, attack.style, attack.keepOn);
        learn(attack.style, tick - attack.startTick);
    }

    private void learn(Style style, int delay) {
        if (delay < MIN_DELAY || delay > MAX_DELAY) {
            forget(style);
            return;
        }
        Integer previous = lastDelay.get(style);
        int count = previous != null && previous == delay ? streak.getOrDefault(style, 1) + 1 : 1;
        lastDelay.put(style, delay);
        streak.put(style, count);
        if (count >= CONFIRMATIONS) {
            confirmedDelay.put(style, delay);
        } else {
            confirmedDelay.remove(style);
        }
    }

    private void forget(Style style) {
        lastDelay.remove(style);
        confirmedDelay.remove(style);
        streak.remove(style);
    }

    private void expire(int tick) {
        while (!pending.isEmpty() && tick - pending.peekFirst().startTick > PENDING_EXPIRY_TICKS) {
            pending.removeFirst();
        }
    }

    /** The confirmed animation-to-hit delay for a style, or 0 while it is not trusted yet. */
    public int confirmedDelay(Style style) {
        return confirmedDelay.getOrDefault(style, 0);
    }

    /** True once the hit delay of every style is trusted. */
    public boolean isLearned() {
        for (Style style : Style.values()) {
            if (!confirmedDelay.containsKey(style)) {
                return false;
            }
        }
        return true;
    }

    /** Forget pending attacks (new fight, state change). Learned delays are kept: they do not change between kills. */
    public void clearPending() {
        pending.clear();
        lastSignalTick = Integer.MIN_VALUE;
        holdUntil = Integer.MIN_VALUE;
        flickHoldEnd = Integer.MIN_VALUE;
    }

    /** Forget everything, including learned delays. */
    public void reset() {
        clearPending();
        lastStyle = null;
        lastDelay.clear();
        confirmedDelay.clear();
        streak.clear();
    }

    /**
     * The prayer wanted for this tick.
     *
     * @param ratsAttacking true while giant rats are attacking the player: the prayer is then held on, not flicked
     */
    public Decision decide(int tick, PrayerMode mode, boolean ratsAttacking) {
        expire(tick);
        Style steady = lastStyle != null ? lastStyle : Style.MELEE;
        if (mode == PrayerMode.ALWAYS_ON || ratsAttacking || tick < holdUntil) {
            return new Decision(steady, false);
        }
        if (mode == PrayerMode.ONE_TICK_FLICK) {
            // The prayer protects the whole time, so hit timing is not needed, only the style.
            return new Decision(steady, lastStyle != null);
        }
        // LAZY_FLICK
        if (!isLearned()) {
            return new Decision(steady, false);
        }
        // An attack whose hit was never seen: the hold starts the tick after it should have landed.
        for (Pending attack : pending) {
            int landing = attack.startTick + confirmedDelay.get(attack.style);
            if (tick > landing && !attack.holdStarted) {
                attack.holdStarted = true;
                startHold(landing + 1, attack.style, attack.keepOn);
            }
        }
        boolean prayerStillOn = tick < flickHoldEnd;
        Pending upcoming = null;
        for (Pending attack : pending) {
            int landing = attack.startTick + confirmedDelay.get(attack.style);
            if (tick > landing) {
                continue;
            }
            if (tick >= attack.onTick(landing)) {
                return new Decision(attack.style, false, attack.keepOn ? "kept on" : attack.plan == OnPlan.NORMAL ? "" : "early");
            }
            if (upcoming == null) {
                upcoming = attack;
            }
        }
        if (prayerStillOn) {
            // Still on from the last hit: a new attack only switches the style, never off-then-on.
            return new Decision(upcoming != null ? upcoming.style : flickHoldStyle, false, "held");
        }
        return new Decision(null, false);
    }
}
