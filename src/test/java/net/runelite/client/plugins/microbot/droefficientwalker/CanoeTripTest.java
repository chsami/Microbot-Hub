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
        assertEquals(CanoeTrip.Outcome.LANDED, CanoeTrip.travel(landed::get, () -> !landed.get(), () -> true,
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
        assertEquals(CanoeTrip.Outcome.LANDED, CanoeTrip.travel(() -> false, () -> !away.get(), () -> true,
            () -> { if (steps.incrementAndGet() == 4) away.set(true); return true; },
            () -> true));
        assertEquals(4, steps.get());
    }

    @Test void aSlowStageIsRepeatedFromTheCurrentStageInsteadOfFailingTheTrip()
    {
        List<Boolean> results = List.of(false, true, false, true, true, true);
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        AtomicBoolean landed = new AtomicBoolean();
        assertEquals(CanoeTrip.Outcome.LANDED, CanoeTrip.travel(landed::get, () -> true, () -> true,
            () -> {
                boolean ok = results.get(steps.getAndIncrement());
                if (ok && finished.incrementAndGet() == 4) landed.set(true);
                return ok;
            },
            () -> { fail("No landing wait while the canoe is still at the station"); return true; }));
        assertEquals(results.size(), steps.get());
    }

    @Test void aStageThatKeepsFailingIsRetriedABoundedNumberOfTimes()
    {
        AtomicInteger steps = new AtomicInteger();
        assertEquals(CanoeTrip.Outcome.FAILED, CanoeTrip.travel(() -> false, () -> true, () -> true,
            () -> { steps.incrementAndGet(); return false; }, () -> false));
        assertEquals(CanoeTrip.MAX_STEPS, steps.get());
    }

    @Test void pausingOrTakingControlInterruptsWithoutFailing()
    {
        assertEquals(CanoeTrip.Outcome.INTERRUPTED, CanoeTrip.travel(() -> false, () -> true, () -> false,
            () -> { fail("Held input clicked"); return true; }, () -> true));
        AtomicBoolean human = new AtomicBoolean();
        AtomicInteger steps = new AtomicInteger();
        assertEquals(CanoeTrip.Outcome.INTERRUPTED, CanoeTrip.travel(() -> false, () -> true, () -> !human.get(),
            () -> { if (steps.incrementAndGet() == 2) human.set(true); return false; },
            () -> { fail("No landing wait after an interruption"); return true; }));
        assertEquals(2, steps.get());
    }

    @Test void aStuckStationIsBounded()
    {
        AtomicInteger steps = new AtomicInteger();
        assertEquals(CanoeTrip.Outcome.FAILED, CanoeTrip.travel(() -> false, () -> true, () -> true,
            () -> { steps.incrementAndGet(); return true; }, () -> false));
        assertEquals(CanoeTrip.MAX_STEPS, steps.get());
    }
}
