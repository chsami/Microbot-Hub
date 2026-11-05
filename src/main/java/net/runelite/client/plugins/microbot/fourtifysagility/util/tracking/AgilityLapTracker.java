package net.runelite.client.plugins.microbot.fourtifysagility.util.tracking;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;

/** Counts completed laps that both started and ended during this session. */
public final class AgilityLapTracker
{
    public static final int START_LAP_SCRIPT = 5173;
    public static final int END_LAP_SCRIPT = 5174;

    private final Map<AgilityCourse, Integer> completed = new EnumMap<>(AgilityCourse.class);
    private AgilityCourse activeCourse;
    private AgilityCourse startedCourse;

    public void reset()
    {
        completed.clear();
        activeCourse = null;
        startedCourse = null;
    }

    /** Discard a partial lap after changing courses or losing game state. */
    public void suspend()
    {
        activeCourse = null;
        startedCourse = null;
    }

    public void observeCourse(AgilityCourse course)
    {
        if (activeCourse != course)
        {
            activeCourse = course;
            startedCourse = null;
        }
    }

    public void script(int scriptId, AgilityCourse course)
    {
        observeCourse(course);
        if (course != AgilityCourse.GNOME && course != AgilityCourse.APE_ATOLL)
        {
            return;
        }
        if (scriptId == START_LAP_SCRIPT)
        {
            startedCourse = course;
        }
        else if (scriptId == END_LAP_SCRIPT && startedCourse == course)
        {
            completed.merge(course, 1, Integer::sum);
            startedCourse = null;
        }
    }

    public int getCompleted(AgilityCourse course)
    {
        return completed.getOrDefault(course, 0);
    }
}
