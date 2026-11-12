package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.Skill;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Experience gained per skill since the plugin started, and the per-hour rates for the overlay. Runtime counts from
 * {@link #reset}, so breaks stay in the rate and the numbers are honest. The start experience is taken the first time
 * the player is logged in ({@link #startIfNeeded}), which also covers a plugin started while logged out.
 */
public final class XpTracker {
    /** The skills a Scurrius kill trains, in overlay order. */
    public static final Skill[] TRACKED = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS};

    private final Map<Skill, Integer> startXp = new EnumMap<>(Skill.class);
    private volatile long startTimeMs;
    private volatile boolean started;

    /** Begin a new session: the clock starts now, the start experience is taken at the first chance. */
    public synchronized void reset(long nowMs) {
        startXp.clear();
        started = false;
        startTimeMs = nowMs;
    }

    /** Takes the start experience once, when the player is logged in. */
    public synchronized void startIfNeeded(boolean loggedIn, ToIntFunction<Skill> currentXp) {
        if (started || !loggedIn) {
            return;
        }
        for (Skill skill : TRACKED) {
            startXp.put(skill, currentXp.applyAsInt(skill));
        }
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    /** Experience gained in a skill; 0 before the start experience is known and never negative. */
    public synchronized long gained(Skill skill, int currentXp) {
        Integer start = startXp.get(skill);
        return start == null ? 0 : Math.max(0, (long) currentXp - start);
    }

    public long elapsedMs(long nowMs) {
        return Math.max(0, nowMs - startTimeMs);
    }

    /** A per-hour rate from an amount over the elapsed time; 0 while no time has passed. */
    public static long perHour(long amount, long elapsedMs) {
        return elapsedMs <= 0 ? 0 : amount * 3_600_000L / elapsedMs;
    }

    /** 1234567 -> "1,234,567" (fixed locale so the overlay never changes style). */
    public static String format(long value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    /** h:mm:ss */
    public static String formatTime(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    /** "12,345 xp (45,600/h)" */
    public static String line(long gained, long elapsedMs) {
        return format(gained) + " xp (" + format(perHour(gained, elapsedMs)) + "/h)";
    }
}
