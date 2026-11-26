package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.function.BooleanSupplier;

final class CanoeTrip
{
    static final int MAX_STEPS = 5;

    private CanoeTrip() { }

    static boolean travel(BooleanSupplier landed, BooleanSupplier atStation, BooleanSupplier allowed,
                          BooleanSupplier step, BooleanSupplier awaitLanding)
    {
        for (int attempt = 0; attempt < MAX_STEPS; attempt++)
        {
            if (landed.getAsBoolean()) return true;
            if (!atStation.getAsBoolean()) return awaitLanding.getAsBoolean();
            if (!allowed.getAsBoolean()) return false;
            if (!step.getAsBoolean()) return landed.getAsBoolean();
        }
        return landed.getAsBoolean() || awaitLanding.getAsBoolean();
    }
}
