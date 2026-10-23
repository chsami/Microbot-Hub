package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackBanditTargetsTest
{
    @Test
    void bothBanditLevelsHaveExactMatchingAndRequirements()
    {
        assertEquals(45, BlackjackTarget.BANDIT_41.requiredThievingLevel());
        assertEquals(55, BlackjackTarget.BANDIT_56.requiredThievingLevel());
        assertEquals(65, BlackjackTarget.MENAPHITE_THUG.requiredThievingLevel());
        for (BlackjackTarget target : new BlackjackTarget[]{BlackjackTarget.BANDIT_41, BlackjackTarget.BANDIT_56})
        {
            int level = target == BlackjackTarget.BANDIT_41 ? 41 : 56;
            assertTrue(target.matches("Bandit", level));
            assertTrue(target.matches("bandit", level));
            assertFalse(target.matches("Bandit", level == 41 ? 56 : 41));
            assertFalse(target.matches("Bandit", 57));
            assertFalse(target.matches("Menaphite Thug", level));
            assertFalse(target.matches(null, level));
        }
    }

    @Test
    void autoThresholdsRemainUnchanged()
    {
        assertEquals(BlackjackTarget.BANDIT_41, BlackjackTarget.forLevel(45));
        assertEquals(BlackjackTarget.BANDIT_41, BlackjackTarget.forLevel(54));
        assertEquals(BlackjackTarget.BANDIT_56, BlackjackTarget.forLevel(55));
        assertEquals(BlackjackTarget.BANDIT_56, BlackjackTarget.forLevel(69));
        assertEquals(BlackjackTarget.MENAPHITE_THUG, BlackjackTarget.forLevel(70));
    }

    @Test
    void autoDoesNotChangeHouseOrTargetDuringSession() throws Exception
    {
        for (int initialLevel : new int[]{54, 69, 70})
        {
            BlackjackScript script = script(BlackjackTarget.AUTO);
            try
            {
                // An unpopulated login skill cache must not pin the target.
                script.selectTargetForLevel(0);
                BlackjackTarget expected = BlackjackTarget.forLevel(initialLevel);
                assertEquals(expected, script.selectTargetForLevel(initialLevel));
                assertEquals(expected, script.selectTargetForLevel(99));
                assertEquals(expected, script.selectTargetForLevel(0));
            }
            finally { dispose(script); }
        }
    }

    @Test
    void banditHouseAndRoutesAreSeparateFromSouthernTent() throws Exception
    {
        for (BlackjackTarget target : new BlackjackTarget[]{BlackjackTarget.BANDIT_41, BlackjackTarget.BANDIT_56})
        {
            BlackjackScript script = script(target);
            try
            {
                assertEquals(target, script.selectTargetForLevel(99));
                Method inside = BlackjackScript.class.getDeclaredMethod("isInsideActiveHouse", WorldPoint.class);
                inside.setAccessible(true);
                assertEquals(true, inside.invoke(script, new WorldPoint(3357, 2991, 0)));
                assertEquals(true, inside.invoke(script, new WorldPoint(3360, 2995, 0)));
                assertEquals(false, inside.invoke(script, new WorldPoint(3350, 2954, 0)));
                assertEquals(false, inside.invoke(script, new WorldPoint(3358, 2993, 1)));
                WorldPoint insideDoor = point(script, "activeWineDoorInsideTile");
                WorldPoint outsideDoor = point(script, "activeWineDoorOutsideTile");
                assertEquals(new WorldPoint(3360, 2993, 0), insideDoor);
                assertEquals(new WorldPoint(3362, 2993, 0), outsideDoor);
                assertEquals(true, inside.invoke(script, insideDoor));
                assertEquals(false, inside.invoke(script, outsideDoor));
                assertEquals(new WorldPoint(3359, 2995, 0), point(script, "activeCombatStagingTile"));
                assertEquals(new WorldPoint(3360, 2993, 0), point(script, "activeCombatSafeTile"));
            }
            finally { dispose(script); }
        }
    }

    @Test
    void banditEntranceAcceptsConfirmedCurtainAndLegacyDoorOnlyAtItsLocation()
    {
        WorldPoint door = new WorldPoint(3361, 2993, 0);
        assertTrue(BlackjackScript.isBanditWineDoor("Door", door, new String[]{"Open", null}, "Open"));
        assertTrue(BlackjackScript.isBanditWineDoor("Door", door, new String[]{"Close"}, "Close"));
        assertFalse(BlackjackScript.isBanditWineDoor("Door", door, new String[]{"Close"}, "Open"));
        assertTrue(BlackjackScript.isBanditWineDoor("Curtain", door, new String[]{"Open"}, "Open"));
        assertTrue(BlackjackScript.isBanditWineDoor("Curtain", door, new String[]{"Close"}, "Close"));
        assertFalse(BlackjackScript.isBanditWineDoor("Curtain", door, new String[]{"Close"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Curtain", new WorldPoint(3350, 2957, 0), new String[]{"Open"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Crate", door, new String[]{"Open"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Door", new WorldPoint(3350, 2957, 0), new String[]{"Open"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Door", new WorldPoint(3361, 2993, 1), new String[]{"Open"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Door", null, new String[]{"Open"}, "Open"));
        assertFalse(BlackjackScript.isBanditWineDoor("Door", door, null, "Open"));
    }

    private static BlackjackScript script(BlackjackTarget target) throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        Field field = BlackjackScript.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(script, new BlackjackConfig() { @Override public BlackjackTarget target() { return target; } });
        return script;
    }

    private static WorldPoint point(BlackjackScript script, String name) throws Exception
    {
        Method method = BlackjackScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (WorldPoint) method.invoke(script);
    }

    private static void dispose(BlackjackScript script) throws Exception
    {
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(script)).shutdownNow();
    }
}
