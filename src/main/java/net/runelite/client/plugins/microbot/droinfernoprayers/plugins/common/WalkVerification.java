package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputDiagnostics;

final class WalkVerification
{
    static final int MAX_CORRECTIONS = 2;

    static final long CLICK_TIMEOUT_NANOS = 400_000_000L;

    static final int SETTLE_CYCLES = 4;
    static final long SETTLE_TIMEOUT_NANOS = 150_000_000L;
    static final long POLL_NANOS = 5_000_000L;

    enum Result
    {
        CONFIRMED, CORRECTED, MISSED, UNOBSERVED, NOT_SUBMITTED, CANCELLED;

        boolean accepted() { return this == CONFIRMED || this == CORRECTED || this == UNOBSERVED; }
    }

    static final class Click
    {
        private volatile int cycle = -1;
        void built(int cycle) { if (this.cycle < 0) this.cycle = cycle; }
        int cycle() { return cycle; }
    }

    static final class Observation
    {
        final int cycle;
        final WorldPoint destination, position;
        Observation(int cycle, WorldPoint destination, WorldPoint position)
        {
            this.cycle = cycle;
            this.destination = destination;
            this.position = position;
        }
    }

    interface Probe
    {
        WorldPoint target(int x, int y, int plane);

        boolean click(int x, int y, int plane, Click click);

        Observation observe();
    }

    private final Probe probe;
    private final BooleanSupplier allowed;
    private final LongSupplier clock;
    private final LongConsumer pause;

    WalkVerification(Probe probe, BooleanSupplier allowed, LongSupplier clock, LongConsumer pause)
    {
        this.probe = probe;
        this.allowed = allowed;
        this.clock = clock;
        this.pause = pause;
    }

    Result walk(int x, int y, int plane)
    {
        WorldPoint target = probe.target(x, y, plane);
        if (target == null) return Result.NOT_SUBMITTED;
        for (int attempt = 0; attempt <= MAX_CORRECTIONS; attempt++)
        {
            if (!allowed.getAsBoolean()) return Result.CANCELLED;
            Click click = new Click();
            if (!probe.click(x, y, plane, click)) return attempt == 0 ? Result.NOT_SUBMITTED : Result.MISSED;
            if (!await(() -> click.cycle() >= 0, CLICK_TIMEOUT_NANOS)) return finish(Result.UNOBSERVED);
            Observation seen = settle(click);
            if (seen == null) return finish(Result.UNOBSERVED);
            if (reached(target, seen))
            {
                ArenaInputDiagnostics.event("WALK_DESTINATION", attempt == 0 ? "CONFIRMED" : "CORRECTED");
                return attempt == 0 ? Result.CONFIRMED : Result.CORRECTED;
            }
            ArenaInputDiagnostics.event("WALK_DESTINATION", "MISSED " + offset(target, seen.destination));
        }
        return Result.MISSED;
    }

    private Observation settle(Click click)
    {
        long deadline = clock.getAsLong() + SETTLE_TIMEOUT_NANOS;
        while (allowed.getAsBoolean())
        {
            Observation seen = probe.observe();
            if (seen != null && seen.cycle > click.cycle())
            {
                if (seen.cycle >= click.cycle() + SETTLE_CYCLES) return seen;
            }

            if (clock.getAsLong() >= deadline) return null;
            pause.accept(POLL_NANOS);
        }
        return null;
    }

    private boolean await(BooleanSupplier done, long timeoutNanos)
    {
        long deadline = clock.getAsLong() + timeoutNanos;
        while (allowed.getAsBoolean())
        {
            if (done.getAsBoolean()) return true;
            if (clock.getAsLong() >= deadline) return false;
            pause.accept(POLL_NANOS);
        }
        return false;
    }

    private Result finish(Result unobserved)
    {
        if (!allowed.getAsBoolean()) return Result.CANCELLED;
        ArenaInputDiagnostics.event("WALK_DESTINATION", "UNOBSERVED");
        return unobserved;
    }

    static boolean reached(WorldPoint target, Observation seen)
    {
        return target.equals(seen.destination) || seen.destination == null && target.equals(seen.position);
    }

    static String offset(WorldPoint target, WorldPoint destination)
    {
        if (destination == null) return "NO_DESTINATION";
        return "dx=" + (destination.getX() - target.getX()) + " dy=" + (destination.getY() - target.getY());
    }
}
