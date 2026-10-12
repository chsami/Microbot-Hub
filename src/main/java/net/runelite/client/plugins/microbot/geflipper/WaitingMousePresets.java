package net.runelite.client.plugins.microbot.geflipper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** In-memory randomization policy. The master switch and all preference writes belong to the caller. */
final class WaitingMousePresets {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final Clock clock;
    private final WaitingMouse.RandomInt random;
    private final LongSupplier nanoTime;
    private Instant anchor;
    private LocalTime startTime;
    private LocalTime virtualTime;
    private FlipperConfig.TimeOfDaySource source;
    private FlipperConfig.RandomizationPreset preset;
    private String configuredTime;
    private String error;
    private int drift;
    private int effective;
    private long nextDriftSeconds;
    private long lastAdjustmentSeconds;
    private long dayElapsed;
    private long localAnchorNanos;
    private boolean sessionInitialized;
    private int sessionOffset;
    private FlipperConfig.RandomizationPreset sessionPreset;
    private Instant sessionAnchor;
    private int sessionDrift;
    private int sessionEffective;
    private int fatigueStepSeconds;
    private long sessionElapsed;
    private long sessionNextDrift;
    private long sessionLastAdjustment;

    WaitingMousePresets() {
        this(liveSystemClock(), (minimum, maximum) ->
            ThreadLocalRandom.current().nextInt(minimum, maximum + 1));
    }

    WaitingMousePresets(Clock clock, WaitingMouse.RandomInt random) {
        this(clock, random, System::nanoTime);
    }

    WaitingMousePresets(Clock clock, WaitingMouse.RandomInt random, LongSupplier nanoTime) {
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    synchronized int frequency(FlipperConfig config) {
        return evaluate(Objects.requireNonNull(config));
    }

    synchronized void reset() {
        clearDay();
        preset = null;
        error = null;
        clearSessionClock();
        sessionInitialized = false;
        sessionOffset = 0;
    }

    synchronized String description(FlipperConfig config) {
        int frequency = evaluate(Objects.requireNonNull(config));
        if (error != null) return error;
        switch (preset) {
            case AFK: return sessionDescription("AFK", frequency);
            case SEMI_AFK: return sessionDescription("Semi-AFK", frequency);
            case ATTENTIVE_HUMAN: return sessionDescription("Attentive Human", frequency);
            case DAY_FATIGUE:
                if (anchor == null)
                    return "Time-of-day clock disabled; using saved manual randomization: " + frequency + ".";
                if (source == FlipperConfig.TimeOfDaySource.LOCAL_TIME)
                    return "Day Fatigue: computer local time " + TIME.format(virtualTime)
                        + "; randomization " + frequency + ".";
                return "Day Fatigue: " + phaseName(source) + " start " + TIME.format(startTime)
                    + "; virtual time " + TIME.format(virtualTime) + "; randomization " + frequency + ".";
            default: return "Custom randomization: " + frequency + ".";
        }
    }

    private int evaluate(FlipperConfig config) {
        preset = config.waitingMousePreset();
        error = null;
        if (preset != FlipperConfig.RandomizationPreset.DAY_FATIGUE) {
            clearDay();
            if (preset == null) {
                clearSessionClock();
                error = "Choose a randomization preset.";
                return 0;
            }
            switch (preset) {
                case AFK: return sessionFrequency(config, 25);
                case SEMI_AFK: return sessionFrequency(config, 50);
                case ATTENTIVE_HUMAN: return sessionFrequency(config, 75);
                default:
                    clearSessionClock();
                    return clamp(config.waitingMouseChance(), 0, 100);
            }
        }

        clearSessionClock();
        if (!config.randomizeMouseSpeed()) {
            clearDay();
            return clamp(config.waitingMouseChance(), 0, 100);
        }

        FlipperConfig.TimeOfDaySource selectedSource = config.waitingMouseTimeSource();
        boolean local = selectedSource == FlipperConfig.TimeOfDaySource.LOCAL_TIME;
        // Local time owns no saved starting time, including any invalid hidden custom value.
        String selectedTime = local ? null : config.waitingMouseTime();
        LocalTime selectedStart = local ? null : startingTime(selectedSource, selectedTime);
        if (!local && selectedStart == null) {
            clearDay();
            error = selectedSource == null ? "Choose a starting phase." : "Enter time as HH:mm";
            return 0;
        }
        Instant now = clock.instant();
        if (local) selectedStart = LocalTime.ofInstant(now, clock.getZone());
        long nowNanos = local ? nanoTime.getAsLong() : 0;
        if (anchor == null || selectedSource != source || !Objects.equals(selectedTime, configuredTime)) {
            anchor = now;
            startTime = virtualTime = selectedStart;
            source = selectedSource;
            configuredTime = selectedTime;
            drift = randomDrift();
            nextDriftSeconds = randomInterval();
            dayElapsed = lastAdjustmentSeconds = 0;
            localAnchorNanos = nowNanos;
            effective = target(virtualTime, drift);
            return effective;
        }

        long elapsed = local
            ? Math.max(dayElapsed, TimeUnit.NANOSECONDS.toSeconds(Math.max(0, nowNanos - localAnchorNanos)))
            : Math.max(0, Duration.between(anchor, now).getSeconds());
        virtualTime = local ? selectedStart : startTime.plusSeconds(elapsed % (24 * 60 * 60));
        // Local wall time selects the target; monotonic elapsed time gates drift and
        // slider changes so clock/zone corrections cannot freeze or accelerate them.
        dayElapsed = elapsed;
        if (elapsed >= nextDriftSeconds) {
            drift = randomDrift();
            nextDriftSeconds = elapsed + randomInterval();
        }
        int target = target(virtualTime, drift);
        if (target != effective && elapsed - lastAdjustmentSeconds >= 30) {
            effective += Integer.compare(target, effective);
            lastAdjustmentSeconds = elapsed;
        }
        return effective;
    }

    private int sessionFrequency(FlipperConfig config, int baseline) {
        if (!sessionInitialized) {
            sessionOffset = clamp(random.inclusive(-3, 3), -3, 3);
            sessionInitialized = true;
        }
        boolean changedPreset = preset != sessionPreset;
        if (!config.randomizeMouseSpeed()) {
            clearSessionClock();
            sessionPreset = preset;
            sessionEffective = baseline + sessionOffset;
            return sessionEffective;
        }
        Instant now = clock.instant();
        if (sessionAnchor == null) {
            sessionAnchor = now;
            sessionDrift = clamp(random.inclusive(-2, 2), -2, 2);
            fatigueStepSeconds = clamp(random.inclusive(900, 1800), 900, 1800);
            sessionNextDrift = randomInterval();
            sessionEffective = sessionTarget(baseline);
            sessionPreset = preset;
            return sessionEffective;
        }
        // Clock rollback cannot reduce accumulated fatigue or trigger extra random draws.
        sessionElapsed = Math.max(sessionElapsed, Math.max(0, Duration.between(sessionAnchor, now).getSeconds()));
        if (sessionElapsed >= sessionNextDrift) {
            sessionDrift = clamp(random.inclusive(-2, 2), -2, 2);
            sessionNextDrift = sessionElapsed + randomInterval();
        }
        int target = sessionTarget(baseline);
        if (changedPreset) {
            sessionEffective = target;
            sessionLastAdjustment = sessionElapsed;
        } else if (target != sessionEffective && sessionElapsed - sessionLastAdjustment >= 30) {
            sessionEffective += Integer.compare(target, sessionEffective);
            sessionLastAdjustment = sessionElapsed;
        }
        sessionPreset = preset;
        return sessionEffective;
    }

    private int sessionTarget(int baseline) {
        int fatigue = (int) Math.min(6L, sessionElapsed / fatigueStepSeconds);
        return clamp(baseline + sessionOffset + sessionDrift - fatigue, baseline - 10, baseline + 10);
    }

    private String sessionDescription(String name, int frequency) {
        return name + " randomization: " + frequency + ". Session variation"
            + (sessionAnchor == null ? "; fatigue disabled." : "; gradual fatigue active.");
    }

    private void clearSessionClock() {
        sessionAnchor = null;
        sessionPreset = null;
        sessionDrift = sessionEffective = fatigueStepSeconds = 0;
        sessionElapsed = sessionNextDrift = sessionLastAdjustment = 0;
    }

    private void clearDay() {
        anchor = null;
        startTime = virtualTime = null;
        source = null;
        configuredTime = null;
        effective = drift = 0;
        localAnchorNanos = dayElapsed = nextDriftSeconds = lastAdjustmentSeconds = 0;
    }

    private static Clock liveSystemClock() {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
            @Override public Clock withZone(ZoneId zone) { return Clock.system(zone); }
            @Override public Instant instant() { return Instant.now(); }
        };
    }

    private int randomDrift() { return clamp(random.inclusive(-5, 5), -5, 5); }
    private int randomInterval() { return clamp(random.inclusive(120, 300), 120, 300); }

    private static int target(LocalTime time, int drift) {
        double hour = time.toSecondOfDay() / 3600.0;
        int baseline = (int) Math.round(50 + 25 * Math.cos((hour - 10) / 24 * 2 * Math.PI));
        return clamp(baseline + drift, 20, 80);
    }

    private static LocalTime startingTime(FlipperConfig.TimeOfDaySource source, String custom) {
        if (source == null) return null;
        switch (source) {
            case MORNING: return LocalTime.of(9, 0);
            case MID_DAY: return LocalTime.of(13, 0);
            case NIGHT: return LocalTime.of(21, 0);
            default:
                if (custom == null || !custom.matches("[0-2][0-9]:[0-5][0-9]")) return null;
                int hour = Integer.parseInt(custom.substring(0, 2));
                return hour < 24 ? LocalTime.of(hour, Integer.parseInt(custom.substring(3))) : null;
        }
    }

    private static String phaseName(FlipperConfig.TimeOfDaySource source) {
        switch (source) {
            case MORNING: return "Morning";
            case MID_DAY: return "Mid-day";
            case NIGHT: return "Night";
            default: return "Custom time";
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
