package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanoeTripTest
{
    @Test void intermediateStepsKeepTheTripGoingUntilThePaddleLands()
    {
        List<String> stages = List.of("Chop-down", "Shape-Canoe", "Float Canoe", "Paddle Canoe");
        List<String> done = new ArrayList<>();
        AtomicBoolean landed = new AtomicBoolean();
        AtomicInteger landingWaits = new AtomicInteger();
        assertTrue(CanoeTrip.travel(landed::get, () -> !landed.get(), () -> true,
            () -> {
                done.add(stages.get(done.size()));
                if (done.size() == stages.size()) landed.set(true);
                return true;
            },
            () -> { landingWaits.incrementAndGet(); return false; }));
        assertEquals(stages, done);
        assertEquals(0, landingWaits.get());
    }

    @Test void leavingTheStationWaitsForTheLandingInsteadOfAnotherStep()
    {
        AtomicBoolean away = new AtomicBoolean();
        AtomicInteger steps = new AtomicInteger();
        assertTrue(CanoeTrip.travel(() -> false, () -> !away.get(), () -> true,
            () -> { if (steps.incrementAndGet() == 4) away.set(true); return true; },
            () -> true));
        assertEquals(4, steps.get());
    }

    @Test void aFailedStepEndsTheTripWithoutFurtherClicks()
    {
        AtomicInteger steps = new AtomicInteger();
        assertFalse(CanoeTrip.travel(() -> false, () -> true, () -> true,
            () -> steps.incrementAndGet() < 2, () -> { fail("No landing wait after a failed step"); return true; }));
        assertEquals(2, steps.get());
    }

    @Test void heldInputOrAStuckStationIsBounded()
    {
        assertFalse(CanoeTrip.travel(() -> false, () -> true, () -> false,
            () -> { fail("Held input clicked"); return true; }, () -> true));
        AtomicInteger steps = new AtomicInteger();
        assertFalse(CanoeTrip.travel(() -> false, () -> true, () -> true,
            () -> { steps.incrementAndGet(); return true; }, () -> false));
        assertEquals(CanoeTrip.MAX_STEPS, steps.get());
    }
}
