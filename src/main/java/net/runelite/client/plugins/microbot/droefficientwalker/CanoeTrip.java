package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.function.BooleanSupplier;

final class CanoeTrip
{
    static final int MAX_STEPS = 8;

    enum Outcome
    {
        LANDED,
        INTERRUPTED,
        FAILED
    }

    private CanoeTrip() { }

    static Outcome travel(BooleanSupplier landed, BooleanSupplier atStation, BooleanSupplier allowed,
                          BooleanSupplier step, BooleanSupplier awaitLanding)
    {
        for (int attempt = 0; attempt < MAX_STEPS; attempt++)
        {
            if (landed.getAsBoolean()) return Outcome.LANDED;
            if (!atStation.getAsBoolean()) return awaitLanding.getAsBoolean() ? Outcome.LANDED : Outcome.FAILED;
            if (!allowed.getAsBoolean()) return Outcome.INTERRUPTED;
            step.getAsBoolean();
        }
        if (landed.getAsBoolean()) return Outcome.LANDED;
        if (!allowed.getAsBoolean()) return Outcome.INTERRUPTED;
        return awaitLanding.getAsBoolean() ? Outcome.LANDED : Outcome.FAILED;
    }
}
