package net.runelite.client.plugins.microbot.geflipper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MouseSpeedVariationTest {
    @Test
    void disabledReturnsTheSourceWithoutSamplingOrCallingIt() {
        RecordingSource source = new RecordingSource(1_000);
        RandomSource random = new RandomSource(80);

        assertSame(source, MouseSpeedVariation.forMovement(source, false, random));
        assertEquals(0, random.calls);
        assertTrue(source.distances.isEmpty());
        assertSame(source, MouseSpeedVariation.forMovement(source, false, null));
    }

    @Test
    void endpointPercentagesScaleDurationInversely() {
        RecordingSource source = new RecordingSource(1_000);
        RandomSource slower = new RandomSource(80);
        RandomSource faster = new RandomSource(120);

        assertEquals(1_250L, MouseSpeedVariation.forMovement(source, true, slower).getFlowWithTime(100).y);
        assertEquals(833L, MouseSpeedVariation.forMovement(source, true, faster).getFlowWithTime(100).y);
        assertEquals(1, slower.calls);
        assertEquals(1, faster.calls);
    }

    @Test
    void oneMovementSamplesOnceAndPreservesItsFactorAcrossSegments() {
        RecordingSource source = new RecordingSource(1_000, 1_600, 2_400);
        RandomSource random = new RandomSource(80, 120);
        SpeedManager movement = MouseSpeedVariation.forMovement(source, true, random);

        assertEquals(1_250L, movement.getFlowWithTime(31.25).y);
        assertEquals(2_000L, movement.getFlowWithTime(12).y);
        assertEquals(3_000L, movement.getFlowWithTime(4.5).y);
        assertEquals(1, random.calls, "Segments cannot resample the speed during a movement");
        assertEquals(Arrays.asList(31.25, 12.0, 4.5), source.distances);
    }

    @Test
    void decoratorPreservesFlowIdentityAndDoesNotChangeTheSourceBaseline() {
        RecordingSource source = new RecordingSource(1_000);
        Pair<Flow, Long> baseline = source.responses.get(0);
        SpeedManager movement = MouseSpeedVariation.forMovement(source, true, new RandomSource(120));

        Pair<Flow, Long> varied = movement.getFlowWithTime(87.5);

        assertNotSame(baseline, varied);
        assertSame(baseline.x, varied.x);
        assertEquals(1_000L, baseline.y);
        assertSame(baseline, source.responses.get(0));
        assertEquals(Arrays.asList(87.5), source.distances);
    }

    @Test
    void outOfRangeRandomValuesAreClampedToTheSupportedPercentages() {
        RecordingSource source = new RecordingSource(1_000);

        assertEquals(1_250L, MouseSpeedVariation.forMovement(source, true,
            new RandomSource(Integer.MIN_VALUE)).getFlowWithTime(10).y);
        assertEquals(833L, MouseSpeedVariation.forMovement(source, true,
            new RandomSource(Integer.MAX_VALUE)).getFlowWithTime(10).y);
    }

    @Test
    void positiveDurationsStayBoundedIncludingLongOverflowCandidates() {
        for (int percent : new int[]{80, 120}) {
            RecordingSource source = new RecordingSource(1, 10_000, Long.MAX_VALUE);
            SpeedManager movement = MouseSpeedVariation.forMovement(source, true, new RandomSource(percent));

            assertEquals(1L, movement.getFlowWithTime(1).y);
            long large = movement.getFlowWithTime(10).y;
            assertTrue(large >= 1 && large <= 10_000);
            assertEquals(10_000L, movement.getFlowWithTime(100).y,
                "Valid large input durations must saturate rather than overflow");
        }
    }

    @Test
    void invalidSourcesAndResponsesFailWithGenericMessages() {
        assertFailure("Mouse speed source is unavailable.", () ->
            MouseSpeedVariation.forMovement(null, false, null));
        assertFailure("Mouse speed random source is unavailable.", () ->
            MouseSpeedVariation.forMovement(new RecordingSource(1_000), true, null));
        assertResponseFailure(null, "Mouse speed source returned no movement.");
        assertResponseFailure(new Pair<>(null, 1_000L), "Mouse speed source returned no flow.");
        Flow flow = new Flow(new double[]{1, 1});
        for (Long duration : Arrays.asList(null, 0L, -1L, Long.MIN_VALUE)) {
            assertResponseFailure(new Pair<>(flow, duration), "Mouse speed source returned an invalid duration.");
        }
    }

    @Test
    void consecutiveMovementsHaveIndependentSamples() {
        RecordingSource source = new RecordingSource(1_000);
        RandomSource random = new RandomSource(80, 120);
        SpeedManager first = MouseSpeedVariation.forMovement(source, true, random);
        SpeedManager second = MouseSpeedVariation.forMovement(source, true, random);

        assertNotSame(first, second);
        assertEquals(1_250L, first.getFlowWithTime(20).y);
        assertEquals(833L, second.getFlowWithTime(20).y);
        assertEquals(1_250L, first.getFlowWithTime(20).y);
        assertEquals(2, random.calls);
    }

    private static void assertResponseFailure(Pair<Flow, Long> response, String message) {
        SpeedManager movement = MouseSpeedVariation.forMovement(distance -> response, true, new RandomSource(100));
        assertFailure(message, () -> movement.getFlowWithTime(10));
    }

    private static void assertFailure(String message, Runnable operation) {
        assertEquals(message, assertThrows(IllegalStateException.class, operation::run).getMessage());
    }

    private static final class RecordingSource implements SpeedManager {
        private final List<Pair<Flow, Long>> responses = new ArrayList<>();
        private final List<Double> distances = new ArrayList<>();

        private RecordingSource(long... durations) {
            Flow flow = new Flow(new double[]{1, 1});
            for (long duration : durations) responses.add(new Pair<>(flow, duration));
        }

        @Override
        public Pair<Flow, Long> getFlowWithTime(double distance) {
            int index = Math.min(distances.size(), responses.size() - 1);
            distances.add(distance);
            return responses.get(index);
        }
    }

    private static final class RandomSource implements WaitingMouse.RandomInt {
        private final int[] values;
        private int calls;

        private RandomSource(int... values) {
            this.values = values;
        }

        @Override
        public int inclusive(int minimum, int maximum) {
            assertEquals(80, minimum);
            assertEquals(120, maximum);
            return values[Math.min(calls++, values.length - 1)];
        }
    }
}
