package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.EnumSet;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CourseDataTest
{
    @Test
    public void courseBoundsIncludeStartAndExcludeEnd()
    {
        WorldArea area = CourseObstacle.SEERS_ZERO.extendedWorldArea;

        assertTrue(area.contains(new WorldPoint(2688, 3456, 0)));
        assertTrue(area.contains(new WorldPoint(2751, 3518, 0)));
        assertFalse(area.contains(new WorldPoint(2752, 3518, 0)));
        assertFalse(area.contains(new WorldPoint(2751, 3519, 0)));
        assertFalse(area.contains(new WorldPoint(2688, 3456, 1)));
    }

    @Test
    public void draynorCourseNeedsNoAgilityLevel()
    {
        for (CourseObstacle obstacle : CourseObstacle.values())
        {
            if (obstacle.area == AgilityCourse.DRAYNOR)
            {
                assertEquals(1, obstacle.level, obstacle.name());
            }
        }
    }

    @Test
    public void everySelectableCourseHasValidObstacleData()
    {
        EnumSet<AgilityCourse> covered = EnumSet.noneOf(AgilityCourse.class);
        assertEquals(149, CourseObstacle.values().length);
        assertEquals(16, AgilityCourse.values().length);

        for (CourseObstacle obstacle : CourseObstacle.values())
        {
            covered.add(obstacle.area);
            assertTrue(obstacle.id > 0, obstacle.name());
            assertTrue(obstacle.extendedWorldArea.getWidth() > 0, obstacle.name());
            assertTrue(obstacle.extendedWorldArea.getHeight() > 0, obstacle.name());
            assertTrue(obstacle.level >= 1 && obstacle.level <= 99, obstacle.name());
        }

        assertEquals(EnumSet.allOf(AgilityCourse.class), covered);
    }

    @Test
    public void prifddinasSecondRopeFallCanSelectRecoveryLadder()
    {
        CourseObstacle recovery = CourseObstacle.PRIFDDINAS_EIGHT_RECOVERY;
        assertTrue(recovery.extendedWorldArea.contains(new WorldPoint(2250, 3395, 0)));
        assertEquals(36232, recovery.id);
        assertEquals(ObstacleObjectType.GAME, recovery.type);
        assertFalse(recovery.extendedWorldArea.contains(new WorldPoint(2250, 3395, 2)));
        assertTrue(CourseObstacle.PRIFDDINAS_EIGHT.extendedWorldArea.contains(new WorldPoint(3270, 6144, 0)));
    }

    @Test
    public void wyrmForkKeepsBothRoutesAtTheSameDecisionArea()
    {
        CourseObstacle advanced = CourseObstacle.COLOSSAL_WYRM_THREE;
        CourseObstacle basic = CourseObstacle.COLOSSAL_WYRM_SEVEN;

        assertEquals(advanced.extendedWorldArea.getX(), basic.extendedWorldArea.getX());
        assertEquals(advanced.extendedWorldArea.getY(), basic.extendedWorldArea.getY());
        assertEquals(advanced.extendedWorldArea.getPlane(), basic.extendedWorldArea.getPlane());
        assertEquals(advanced.extendedWorldArea.getWidth(), basic.extendedWorldArea.getWidth());
        assertEquals(advanced.extendedWorldArea.getHeight(), basic.extendedWorldArea.getHeight());
        assertEquals(62, advanced.level);
        assertEquals(50, basic.level);
        assertEquals(ObstacleObjectType.WALL, advanced.type);
        assertEquals(ObstacleObjectType.GROUND, basic.type);
        assertEquals(55191, advanced.id);
        assertEquals(55184, basic.id);
    }

    @Test
    public void pyramidUpperFloorsKeepTheirSeparateMapCoordinates()
    {
        WorldArea lower = CourseObstacle.PYRAMID_SIXTEEN.extendedWorldArea;
        WorldArea upper = CourseObstacle.PYRAMID_TWENTY_ONE.extendedWorldArea;

        assertTrue(lower.contains(new WorldPoint(3365, 2848, 3)));
        assertTrue(upper.contains(new WorldPoint(3048, 4699, 2)));
        assertFalse(upper.contains(new WorldPoint(3365, 2848, 3)));
        assertEquals(Integer.valueOf(3372), PyramidBlocks.PYRAMID_BLOCK_START_COORDINATES.get(5788));
        assertEquals(Integer.valueOf(2845), PyramidBlocks.PYRAMID_BLOCK_START_COORDINATES.get(5787));
    }
}
