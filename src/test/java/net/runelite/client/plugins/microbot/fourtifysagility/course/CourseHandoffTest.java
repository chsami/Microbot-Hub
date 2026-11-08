package net.runelite.client.plugins.microbot.fourtifysagility.course;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CourseHandoffTest
{
    /** Three tiles south of the Wilderness pipe: between the course's start and pipe areas. */
    private static final WorldPoint BY_THE_PIPE = new WorldPoint(3004, 3934, 0);

    @Test
    public void startsFromBesideTheFirstObstacleWhenItIsInRangeOutsideTheAreas()
    {
        assertFalse(CourseHandoff.isOnCourse(AgilityCourse.WILDERNESS, BY_THE_PIPE));
        assertEquals(CourseObstacle.WILDERNESS_ONE,
            CourseHandoff.startObstacle(AgilityCourse.WILDERNESS, BY_THE_PIPE, obstacle -> true));
    }

    @Test
    public void doesNotStartWhenTheFirstObstacleIsOutOfRangeOrOnAnotherPlane()
    {
        assertNull(CourseHandoff.startObstacle(AgilityCourse.WILDERNESS, BY_THE_PIPE, obstacle -> false));
        assertNull(CourseHandoff.startObstacle(AgilityCourse.WILDERNESS, new WorldPoint(3004, 3934, 1), obstacle -> true));
    }

    @Test
    public void inACourseAreaTheAreaChoosesTheObstacleNotTheStart()
    {
        WorldPoint onTheLog = new WorldPoint(2474, 3436, 0);
        assertTrue(CourseHandoff.isOnCourse(AgilityCourse.GNOME, onTheLog));
        assertNull(CourseHandoff.startObstacle(AgilityCourse.GNOME, onTheLog, obstacle -> true));
    }

    @Test
    public void resumesVarrockBeforeTravellingToSelectedCanifis()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        assertEquals(AgilityCourse.VARROCK, handoff.activeCourse(AgilityCourse.CANIFIS));
        assertTrue(handoff.isFinishing());
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        assertTrue(handoff.isFinishing());
        handoff.update(new WorldPoint(3237, 3417, 0), AgilityCourse.CANIFIS);
        assertFalse(handoff.isFinishing());
        assertEquals(AgilityCourse.CANIFIS, handoff.activeCourse(AgilityCourse.CANIFIS));
    }

    @Test
    public void fallingBeforeTheEndKeepsVarrockUntilAFinishedLap()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3205, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3205, 3415, 0), AgilityCourse.CANIFIS);
        assertTrue(handoff.isFinishing());
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        assertEquals(AgilityCourse.VARROCK, handoff.activeCourse(AgilityCourse.CANIFIS));
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3237, 3417, 0), AgilityCourse.CANIFIS);
        assertFalse(handoff.isFinishing());
        assertEquals(AgilityCourse.CANIFIS, handoff.activeCourse(AgilityCourse.CANIFIS));
    }

    @Test
    public void startingAtTheFinalObstacleStillFinishes()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3237, 3417, 0), AgilityCourse.CANIFIS);
        assertFalse(handoff.isFinishing());
        assertEquals(AgilityCourse.CANIFIS, handoff.activeCourse(AgilityCourse.CANIFIS));
    }

    @Test
    public void unknownTilesAndMissingPositionsDoNotDiscardTheOldLap()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3211, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(null, AgilityCourse.CANIFIS);
        assertEquals(AgilityCourse.VARROCK, handoff.activeCourse(AgilityCourse.CANIFIS));
    }

    @Test
    public void selectedCourseCanChangeWithoutAbandoningTheCurrentLap()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.FALADOR);
        assertEquals(AgilityCourse.VARROCK, handoff.activeCourse(AgilityCourse.FALADOR));
        handoff.update(new WorldPoint(3237, 3417, 0), AgilityCourse.FALADOR);
        assertEquals(AgilityCourse.FALADOR, handoff.activeCourse(AgilityCourse.FALADOR));
        assertFalse(handoff.isFinishing());
    }

    @Test
    public void selectingTheCurrentCourseCancelsTheHandoff()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.VARROCK);
        assertFalse(handoff.isFinishing());
    }

    @Test
    public void groundAndUnrelatedRoofsDoNotStartAHandoff()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3221, 3414, 0), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3100, 3500, 3), AgilityCourse.CANIFIS);
        assertFalse(handoff.isFinishing());
    }

    @Test
    public void stopResetDiscardsTheFinishingLap()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        handoff.reset();
        assertFalse(handoff.isFinishing());
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3237, 3417, 0), AgilityCourse.CANIFIS);
        handoff.reset();
        assertFalse(handoff.isFinishing());
    }

    @Test
    public void reenteringAnEarlierSectionInvalidatesAFinalSectionObservation()
    {
        CourseHandoff handoff = new CourseHandoff();
        handoff.update(new WorldPoint(3237, 3412, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3215, 3415, 3), AgilityCourse.CANIFIS);
        handoff.update(new WorldPoint(3205, 3415, 0), AgilityCourse.CANIFIS);
        assertTrue(handoff.isFinishing());
    }
}
