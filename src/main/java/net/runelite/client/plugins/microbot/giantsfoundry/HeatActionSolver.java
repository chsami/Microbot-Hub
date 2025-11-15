package net.runelite.client.plugins.microbot.giantsfoundry;


import lombok.Value;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;

public class HeatActionSolver
{

    public static final int[] DX_1 = new int[]{
            7,
            8,
            9,
            11,
            13,
            15,
            17,
            19,
            21,
            24,
            27, // -- dunk/quench starts here
            30,
            33,
            37,
            41,
            45,
            49,
            53,
            57,
            62,
            67,
            72,
            77,
            83,
            89,
            95,
            91, // last one will always overshoot 1000
    };


    // index is stage, ordinal order
    public static final int[] TOOL_TICK_CYCLE = new int[] {
            5,
            2,
            2
    };

    public static final int MAX_INDEX = DX_1.length;
    public static final int FAST_INDEX = 10;

    @Value(staticConstructor = "of")
    public static class SolveResult
    {
        int index;
        int dx0;
        int dx1;
        int dx2;
    }

    private static SolveResult heatingSolve(int start, int goal, boolean overshoot, int max, boolean isFast)
    {
        return relativeSolve(goal - start, overshoot, max - start, isFast, -1);
    }

    private static SolveResult coolingSolve(int start, int goal, boolean overshoot, int min, boolean isFast)
    {
        return relativeSolve(start - goal, overshoot, start - min, isFast, 1);
    }

    private static SolveResult relativeSolve(int goal, boolean overshoot, int max, boolean isFast, int decayValue)
    {

        int index = isFast ? FAST_INDEX : 0;
        int dx0 = 0;

        boolean decay = false;

        while (true) {

            if (index > MAX_INDEX)
            {
                break;
            }

            if (!overshoot && dx0 + DX_1[index] > goal)
            {
                break;
            }
            else if (overshoot && dx0 >= goal)
            {
                break;
            }

            if (dx0 + DX_1[index] >= max)
            {
                break;
            }

            if (decay)
            {
                dx0 -= decayValue;
            }


            dx0 += DX_1[index];
            ++index;
            decay = !decay;
        }

        if (isFast)
        {
            index -= FAST_INDEX;
        }

        return SolveResult.of(index, dx0, DX_1[index], -1);
    }


    @Value(staticConstructor = "of")
    public static class DurationResult
    {
        int duration;
        boolean goalInRange;
        boolean overshooting;
        int predictedHeat;
    }

    public static DurationResult solve(
            Stage stage,
            int[] range,
            int actionLeftInStage,
            int start,
            boolean isFast,
            boolean isActionHeating,
            int paddingTicks,
            boolean isRunning)
    {

        final boolean isStageHeating = stage.isHeating();
        // adding tool cycle ticks because the first cycle at a tool is almost always nulled
        // (unless manually reaching the tile, then clicking the tool)
        final int toolDelay = TOOL_TICK_CYCLE[stage.ordinal()];
        final int travelTicks = solveTravelTicks(isRunning, stage, isActionHeating) + toolDelay;
        final int travelDecay = (int) Math.ceil((double) travelTicks / 2);

        final int paddingDecay = (int) Math.ceil((double) paddingTicks / 2);

        // adding 2.4s/8ticks worth of padding so preform doesn't decay out of range
        // average distance from lava+waterfall around 8 ticks
        // preform decays 1 heat every 2 ticks
        final int min = Math.max(0, Math.min(1000, range[0] + paddingDecay + travelDecay));
        final int max = Math.max(0, Math.min(1000, range[1] + paddingDecay + travelDecay));

        final int actionsLeft_DeltaHeat = actionLeftInStage * stage.getHeatChange();

        int estimatedDuration = 0;

        final boolean goalInRange;
        boolean overshoot = false;

        SolveResult result = null;

        // case actions are all cooling, heating is mirrored version

        // goal: in-range // stage: heating
        // overshoot goal
        //  <----------|stop|<---------------- heat
        // ------|min|----------goal-------|max|
        //                      stage ---------------->

        // goal: out-range // stage: heating
        // undershoot min
        // ...----------|stop|<--------------------- heat
        // -goal---|min|---------------------|max|
        //                      stage ----------------->

        // goal: in-range // stage: cooling
        // undershoot goal
        //   <-------------------------|stop|<--------------- heat
        // ------|min|----------goal-------|max|
        //    <---------------- stage

        // goal: out-range // stage: cooling
        // overshoot max
        //    <--------------------|stop|<--------------- heat
        // --------|min|---------------------|max|----goal
        //    <---------------- stage

        if (isActionHeating)
        {
            int goal = min - actionsLeft_DeltaHeat;
            goalInRange = goal >= min && goal <= max;

            if (isStageHeating)
            {

                if (start <= max)
                {
                    overshoot = !goalInRange;

                    if (!goalInRange)
                    {
                        goal = min;
                    }

                    result = heatingSolve(start, goal, overshoot, max, isFast);

                    estimatedDuration = result.index;
                }
            }
            else // cooling stage
            {
                // actionsLeft_DeltaHeat is negative here
                if (start <= max)
                {
                    overshoot = goalInRange;

                    if (!goalInRange)
                    {
                        goal = max;
                    }

                    result = heatingSolve(start, goal, overshoot, max, isFast);

                    estimatedDuration = result.index;
                }
            }
        }
        else // cooling action
        {
            int goal = max - actionsLeft_DeltaHeat;
            goalInRange = goal >= min && goal <= max;

            if (isStageHeating)
            {
                if (start >= min)
                {
                    overshoot = goalInRange;

                    if (!goalInRange)
                    {
                        goal = min;
                    }

                    result = coolingSolve(start, goal, overshoot, min, isFast);

                    estimatedDuration = result.index;
                }
            }
            else // cooling stage cooling action
            {
                if (start >= min)
                {
                    overshoot = !goalInRange;
                    if (!goalInRange)
                    {
                        goal = max;
                    }

                    result = coolingSolve(start, goal, overshoot, min, isFast);

                    estimatedDuration = result.index;
                }
            }
        }

        int dx0 = result == null ? 0 : result.dx0;
        if (!isActionHeating)
        {
            dx0 *= -1;
        }


        return DurationResult.of(estimatedDuration, goalInRange, overshoot, start + dx0);
    }

    private static int solveTravelTicks(boolean isRunning, Stage stage, boolean isLava)
    {
        final int distance;
        if (isLava)
        {
            distance = stage.getDistanceToLava();
        }
        else
        {
            distance = stage.getDistanceToWaterfall();
        }

        if (isRunning)
        {
            // for odd distances, like 7
            // 7 / 2 = 3.5
            // rounded to 4
            return (int) Math.ceil((double) distance / 2);
        }
        else
        {
            return distance;
        }
    }
}