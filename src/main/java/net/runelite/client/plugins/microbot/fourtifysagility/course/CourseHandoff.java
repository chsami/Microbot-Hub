package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.function.Predicate;
import net.runelite.api.coords.WorldPoint;

/** Keeps an unfinished rooftop lap separate from the selected destination. */
public final class CourseHandoff
{
    private AgilityCourse finishing;
    private boolean finalObstacleSeen;

    public void reset()
    {
        finishing = null;
        finalObstacleSeen = false;
    }

    public void update(WorldPoint position, AgilityCourse selected)
    {
        if (position == null)
        {
            return;
        }
        if (finishing == selected)
        {
            reset();
        }
        if (finishing == null)
        {
            for (CourseObstacle obstacle : CourseObstacle.values())
            {
                if (obstacle.area != selected && lastObstacle(obstacle.area) != null
                    && obstacle.extendedWorldArea.getPlane() > 0
                    && obstacle.extendedWorldArea.contains(position))
                {
                    finishing = obstacle.area;
                    finalObstacleSeen = false;
                    break;
                }
            }
        }
        if (finishing == null)
        {
            return;
        }
        CourseObstacle last = lastObstacle(finishing);
        if (last.extendedWorldArea.contains(position))
        {
            finalObstacleSeen = true;
        }
        else if (position.getPlane() == 0)
        {
            // Earlier falls restart the old lap. Only the final descent hands it over.
            if (finalObstacleSeen && firstObstacle(finishing).extendedWorldArea.contains(position))
            {
                finishing = null;
            }
            finalObstacleSeen = false;
        }
        else
        {
            for (CourseObstacle obstacle : CourseObstacle.values())
            {
                if (obstacle.area == finishing && obstacle.extendedWorldArea.contains(position))
                {
                    finalObstacleSeen = false;
                    break;
                }
            }
        }
    }

    public AgilityCourse activeCourse(AgilityCourse selected)
    {
        return finishing == null ? selected : finishing;
    }

    public boolean isFinishing()
    {
        return finishing != null;
    }

    public static boolean isOnCourse(AgilityCourse course, WorldPoint position)
    {
        for (CourseObstacle obstacle : CourseObstacle.values())
        {
            if (obstacle.area == course && obstacle.extendedWorldArea.contains(position))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The course's first obstacle when the player is in none of the course's areas but that obstacle is within
     * interaction range; null otherwise. A player near the start but outside the areas still begins the lap.
     */
    public static CourseObstacle startObstacle(AgilityCourse course, WorldPoint position,
            Predicate<CourseObstacle> inRange)
    {
        if (position == null || isOnCourse(course, position))
        {
            return null;
        }
        CourseObstacle first = firstObstacle(course);
        return first.extendedWorldArea.getPlane() == position.getPlane() && inRange.test(first) ? first : null;
    }

    /** The obstacle a lap of the course starts with. */
    public static CourseObstacle firstObstacle(AgilityCourse course)
    {
        for (CourseObstacle obstacle : CourseObstacle.values())
        {
            if (obstacle.area == course)
            {
                return obstacle;
            }
        }
        throw new IllegalArgumentException("No obstacles for " + course);
    }

    private static CourseObstacle lastObstacle(AgilityCourse course)
    {
        switch (course)
        {
            case DRAYNOR:
                return CourseObstacle.DRAYNOR_SIX;
            case AL_KHARID:
                return CourseObstacle.ALKHARID_SEVEN;
            case VARROCK:
                return CourseObstacle.VARROCK_EIGHT;
            case CANIFIS:
                return CourseObstacle.CANIFIS_SEVEN;
            case FALADOR:
                return CourseObstacle.FALADOR_THIRTEEN;
            case SEERS_VILLAGE:
                return CourseObstacle.SEERS_FIVE;
            case POLLNIVNEACH:
                return CourseObstacle.POLLNIVNEACH_EIGHT;
            case RELLEKKA:
                return CourseObstacle.RELLEKKA_SEVEN;
            case ARDOUGNE:
                return CourseObstacle.ARDOUGNE_SEVEN;
            default:
                return null;
        }
    }
}
