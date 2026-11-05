package net.runelite.client.plugins.microbot.fourtifysagility.util.tracking;

import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AgilityLapTrackerTest
{
    @Test
    public void countsOnlyAStartedAndConfirmedCompletedLap()
    {
        AgilityLapTracker tracker = new AgilityLapTracker();
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.GNOME);
        assertEquals(0, tracker.getCompleted(AgilityCourse.GNOME));
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.GNOME);
        tracker.script(9999, AgilityCourse.GNOME);
        assertEquals(0, tracker.getCompleted(AgilityCourse.GNOME));
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.GNOME);
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.GNOME);
        assertEquals(1, tracker.getCompleted(AgilityCourse.GNOME));
    }

    @Test
    public void courseChangeAndSuspensionDiscardPartialLaps()
    {
        AgilityLapTracker tracker = new AgilityLapTracker();
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.GNOME);
        tracker.observeCourse(AgilityCourse.APE_ATOLL);
        tracker.observeCourse(AgilityCourse.GNOME);
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.GNOME);
        assertEquals(0, tracker.getCompleted(AgilityCourse.GNOME));
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.APE_ATOLL);
        tracker.suspend();
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.APE_ATOLL);
        assertEquals(0, tracker.getCompleted(AgilityCourse.APE_ATOLL));
    }

    @Test
    public void repeatedStartsDoNotCountFallsAndSessionResetClearsTotals()
    {
        AgilityLapTracker tracker = new AgilityLapTracker();
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.APE_ATOLL);
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.APE_ATOLL);
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.APE_ATOLL);
        assertEquals(1, tracker.getCompleted(AgilityCourse.APE_ATOLL));
        tracker.script(AgilityLapTracker.START_LAP_SCRIPT, AgilityCourse.WILDERNESS);
        tracker.script(AgilityLapTracker.END_LAP_SCRIPT, AgilityCourse.WILDERNESS);
        assertEquals(0, tracker.getCompleted(AgilityCourse.WILDERNESS));
        tracker.reset();
        assertEquals(0, tracker.getCompleted(AgilityCourse.APE_ATOLL));
    }
}
