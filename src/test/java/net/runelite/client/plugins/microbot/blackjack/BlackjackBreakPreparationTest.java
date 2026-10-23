package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackBreakPreparationTest
{
    @Test
    void releasedHandoffWaitsAndResumesWineRunOnlyAfterBreakEnds() throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        try (BreakPreparation.Handle handle = BreakPreparation.register("blackjack-test"))
        {
            set(script, "breakPreparation", handle);
            set(script, "state", BlackjackState.WAITING_FOR_BREAK);
            set(script, "breakResumeState", BlackjackState.RESTOCKING_WINE);
            setPhase(script, "RELEASED");
            BreakPreparation.shouldDeferBreak();
            handle.ready();
            assertEquals(true, invoke(script, "handleBreakPreparation"));
            assertEquals(BlackjackState.WAITING_FOR_BREAK, script.getState());
            BreakPreparation.finishBreak();
            assertEquals(true, invoke(script, "handleBreakPreparation"));
            assertEquals(BlackjackState.RESTOCKING_WINE, script.getState());
            assertEquals(false, invoke(script, "handleBreakPreparation"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void cancellationDuringStairResetStopsInsteadOfResumingHalfway() throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        try (BreakPreparation.Handle handle = BreakPreparation.register("blackjack-test"))
        {
            set(script, "breakPreparation", handle);
            setPhase(script, "UPSTAIRS");
            BreakPreparation.finishBreak();
            invoke(script, "handleBreakPreparation");
            assertEquals(BlackjackState.ERROR, script.getState());
            assertFalse(BreakPreparation.hasParticipants());
        }
        finally
        {
            dispose(script);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPhase(BlackjackScript script, String phase) throws Exception
    {
        Field field = BlackjackScript.class.getDeclaredField("breakPhase");
        field.setAccessible(true);
        field.set(script, Enum.valueOf((Class) field.getType(), phase));
    }

    private static void set(BlackjackScript script, String name, Object value) throws Exception
    {
        Field field = BlackjackScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }

    private static Object invoke(BlackjackScript script, String name) throws Exception
    {
        Method method = BlackjackScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(script);
    }

    private static void dispose(BlackjackScript script) throws Exception
    {
        BreakPreparation.finishBreak();
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(script)).shutdownNow();
    }
}
