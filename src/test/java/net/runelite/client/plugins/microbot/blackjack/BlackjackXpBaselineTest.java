package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackXpBaselineTest
{
    @Test
    void loginSnapshotCannotBecomeZeroBaseline() throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        try
        {
            assertEquals(0, script.getXpGained());
            assertFalse(script.initializeXpBaseline(0));
            assertFalse(script.initializeXpBaseline(-1));
            script.onStatChanged(Skill.THIEVING, 2_000_000);
            assertEquals(-1, read(script, "startXp"));
            assertEquals(0, script.getXpGained());
            assertTrue(script.initializeXpBaseline(2_000_000));
            assertEquals(2_000_000, read(script, "startXp"));
            assertEquals(2_000_000, read(script, "lastObservedThievingXp"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void LaterSnapshotsDoNotResetSessionOrStatTracking() throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        try
        {
            script.initializeXpBaseline(2_000_000);
            script.onStatChanged(Skill.THIEVING, 2_000_010);
            assertTrue(script.initializeXpBaseline(2_100_000));
            assertEquals(2_000_000, read(script, "startXp"));
            assertEquals(2_000_010, read(script, "lastObservedThievingXp"));
        }
        finally
        {
            dispose(script);
        }
    }

    private static Object read(BlackjackScript script, String name) throws Exception
    {
        Field field = BlackjackScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private static void dispose(BlackjackScript script) throws Exception
    {
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(script)).shutdownNow();
    }
}
