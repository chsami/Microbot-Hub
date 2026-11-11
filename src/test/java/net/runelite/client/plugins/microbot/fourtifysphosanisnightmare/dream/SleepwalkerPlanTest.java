package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import java.util.List;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SleepwalkerPlanTest
{
    private static final WorldArea BODY = new WorldArea(3870, 9949, 5, 5, 3);
    /** Four sleepwalkers as they rose in her third phase: two by one corner, one by each of two others. */
    private static final List<WorldPoint> RISEN = List.of(at(3863, 9957), at(3866, 9960), at(3878, 9960), at(3881, 9945));
    private static final int[] WALK_IN = {4, 4, 4, 4};
    private static final WorldPoint WAIT = at(3871, 9950);
    private static final int STAKE_REACH = 6;
    private static final int RAPID_TICKS = 2;

    private static WorldPoint at(int x, int y)
    {
        return new WorldPoint(x, y, 3);
    }

    @Test
    public void aSleepwalkerIsTakenInOnTheTickItIsNextToHer()
    {
        assertEquals(10, SleepwalkerPlan.taken(at(3863, 9957), 4, BODY), "four ticks standing, then six steps");
        assertEquals(at(3864, 9956), SleepwalkerPlan.position(at(3863, 9957), 4, 4, BODY));
        assertEquals(at(3863, 9957), SleepwalkerPlan.position(at(3863, 9957), 4, 3, BODY));
    }

    @Test
    public void eachPhaseRisesInItsOwnLayouts()
    {
        assertEquals(28, SleepwalkerPlan.layouts(1, BODY).size(), "two of the eight tiles");
        assertEquals(56, SleepwalkerPlan.layouts(2, BODY).size(), "three of the eight");
        assertEquals(16, SleepwalkerPlan.layouts(3, BODY).size(), "one by each pillar");
        assertEquals(0, SleepwalkerPlan.layouts(4, BODY).size(), "none in her final phase");
    }

    @Test
    public void theThrowingStyleThatLetsFewerThroughDependsOnTheWeaponsReach()
    {
        WorldPoint wait = at(BODY.getX() + 1, BODY.getY() + 3);
        assertEquals(11, SleepwalkerPlan.reaching(wait, BODY, 1, 3, 3, 4, 2), "a dragon knife on rapid in her first phase");
        assertEquals(1, SleepwalkerPlan.reaching(wait, BODY, 1, 3, 3, 6, 3), "and on longrange");
        assertEquals(32, SleepwalkerPlan.reaching(wait, BODY, 3, 5, 6, 4, 2), "a dragon knife on rapid in her third phase");
        assertEquals(16, SleepwalkerPlan.reaching(wait, BODY, 3, 5, 6, 6, 3), "and on longrange");
        assertEquals(0, SleepwalkerPlan.reaching(wait, BODY, 3, 5, 6, 6, 2), "a stake on rapid in her third phase");
        assertEquals(16, SleepwalkerPlan.reaching(wait, BODY, 3, 5, 6, 8, 3), "and on longrange");
    }

    @Test
    public void throwingAtAllFourLetsOneReachHer()
    {
        for (int[] order : orders())
        {
            assertTrue(reached(order, false) >= 1, "the fourth throw lands as she takes it in");
        }
    }

    @Test
    public void aMeleeHitOnTheLastLetsNoneReachHer()
    {
        boolean clear = false;
        for (int[] order : orders())
        {
            clear |= reached(order, true) == 0;
        }
        assertTrue(clear, "a melee hit lands sooner than a throw");
    }

    @Test
    public void theLastSleepwalkerIsHitInMeleeWhenThePlayerCanReachIt()
    {
        SleepwalkerPlan.Step near = SleepwalkerPlan.next(at(3866, 9954), List.of(at(3864, 9956)), new int[] {1}, new int[] {1}, BODY, 1, STAKE_REACH, RAPID_TICKS);
        assertTrue(near.isMelee());
        SleepwalkerPlan.Step behind = SleepwalkerPlan.next(at(3858, 9954), List.of(at(3865, 9954)), new int[] {1}, new int[] {1}, BODY, 1, STAKE_REACH, RAPID_TICKS);
        assertFalse(behind.isMelee(), "it walks away from the player, who reaches its side only after she takes it in");
    }

    @Test
    public void theNextStepComesFromAnOrderThatLetsNoneReachHer()
    {
        SleepwalkerPlan.Step step = SleepwalkerPlan.next(WAIT, RISEN, WALK_IN, WALK_IN, BODY, 1, STAKE_REACH, RAPID_TICKS);
        boolean clear = false;
        for (int[] order : orders())
        {
            clear |= order[0] == step.getIndex() && reached(order, true) == 0;
        }
        assertTrue(clear);
        assertFalse(step.isMelee(), "only the last one is hit in melee");
    }

    @Test
    public void aSleepwalkerTheFirstThrowLeavesOutOfClickRangeIsGoneForFirst()
    {
        // Three as they rose in her second phase, the player waiting one tile off her centre one way and two the other.
        WorldPoint wait = at(3873, 9953);
        List<WorldPoint> risen = List.of(at(3863, 9957), at(3866, 9942), at(3878, 9960));
        int[] walkIn = {3, 3, 3};
        assertTrue(SleepwalkerPlan.play(wait, risen, walkIn, BODY, 1, STAKE_REACH, RAPID_TICKS, new int[] {2, 1, 0}, true) / 160 >= 1,
            "running in to throw at the north-east one leaves the south one 13 tiles away, too far to click");
        SleepwalkerPlan.Step step = SleepwalkerPlan.next(wait, risen, walkIn, walkIn, BODY, 1, STAKE_REACH, RAPID_TICKS);
        assertEquals(1, step.getIndex());
        assertEquals(0, SleepwalkerPlan.play(wait, risen, walkIn, BODY, 1, STAKE_REACH, RAPID_TICKS, new int[] {1, 2, 0}, true) / 160);
    }

    @Test
    public void inHerThirdPhaseTheOrderHoldsWhetherTheyStandSixTicksOrSeven()
    {
        // One by each pillar, two ticks after they rose, the player waiting one tile off her centre both ways.
        WorldPoint wait = at(3873, 9952);
        List<WorldPoint> risen = List.of(at(3863, 9945), at(3863, 9957), at(3881, 9945), at(3878, 9960));
        int[] six = {4, 4, 4, 4};
        int[] seven = {5, 5, 5, 5};
        int early = SleepwalkerPlan.next(wait, risen, new int[] {2, 2, 2, 2}, new int[] {2, 2, 2, 2}, BODY, 1, STAKE_REACH, RAPID_TICKS).getIndex();
        assertFalse(clearsFrom(wait, risen, early, six) || clearsFrom(wait, risen, early, seven),
            "planned for her earlier phases' four ticks, one reaches her either way");
        int late = SleepwalkerPlan.next(wait, risen, seven, seven, BODY, 1, STAKE_REACH, RAPID_TICKS).getIndex();
        assertFalse(clearsFrom(wait, risen, late, six), "planned for seven ticks alone, one reaches her if they walk at six");
        int step = SleepwalkerPlan.next(wait, risen, six, seven, BODY, 1, STAKE_REACH, RAPID_TICKS).getIndex();
        assertTrue(clearsFrom(wait, risen, step, six) && clearsFrom(wait, risen, step, seven));
    }

    @Test
    public void inHerThirdPhaseAnOrderPlannedForSixTicksAloneCanFailAtSeven()
    {
        WorldPoint wait = at(3873, 9952);
        List<WorldPoint> risen = List.of(at(3863, 9945), at(3863, 9957), at(3878, 9942), at(3878, 9960));
        int[] six = {4, 4, 4, 4};
        int[] seven = {5, 5, 5, 5};
        int early = SleepwalkerPlan.next(wait, risen, six, six, BODY, 1, STAKE_REACH, RAPID_TICKS).getIndex();
        assertFalse(clearsFrom(wait, risen, early, seven), "planned for six ticks alone, one reaches her if they walk at seven");
        int step = SleepwalkerPlan.next(wait, risen, six, seven, BODY, 1, STAKE_REACH, RAPID_TICKS).getIndex();
        assertTrue(clearsFrom(wait, risen, step, seven));
    }

    /** Some order starting with the sleepwalker lets none reach her. */
    private static boolean clearsFrom(WorldPoint wait, List<WorldPoint> risen, int first, int[] walkIn)
    {
        for (int[] order : orders())
        {
            for (boolean melee : new boolean[] {true, false})
            {
                if (order[0] == first && SleepwalkerPlan.play(wait, risen, walkIn, BODY, 1, STAKE_REACH, RAPID_TICKS, order, melee) / 160 == 0)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static int reached(int[] order, boolean meleeLast)
    {
        return (int) (SleepwalkerPlan.play(WAIT, RISEN, WALK_IN, BODY, 1, STAKE_REACH, RAPID_TICKS, order, meleeLast) / 160);
    }

    private static List<int[]> orders()
    {
        java.util.ArrayList<int[]> result = new java.util.ArrayList<>();
        for (int a = 0; a < 4; a++)
        {
            for (int b = 0; b < 4; b++)
            {
                for (int c = 0; c < 4; c++)
                {
                    int d = 6 - a - b - c;
                    if (a != b && a != c && b != c && d >= 0 && d < 4 && d != a && d != b && d != c)
                    {
                        result.add(new int[] {a, b, c, d});
                    }
                }
            }
        }
        return result;
    }
}
