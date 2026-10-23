package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SouthernTentPreparationTest
{
    @Test
    void newLureAttemptClearsOldCrossingWithoutResettingOverallDeadline() throws Exception
    {
        BlackjackScript script = preparedScript(BlackjackScript.SouthernTentPhase.MOVING_TO_CURTAIN);
        try
        {
            long preparationStarted = (long) get(script, "stateEnteredAt");
            set(script, "southernCrossingStartedAt", System.currentTimeMillis() - 28_000);
            set(script, "lureAttempts", 2);
            set(script, "pendingSouthernCurtainAction", "Open");
            Method transition = BlackjackScript.class.getDeclaredMethod("setSouthernTentPhase",
                    BlackjackScript.SouthernTentPhase.class, String.class);
            transition.setAccessible(true);
            transition.invoke(script, BlackjackScript.SouthernTentPhase.APPROACHING_TARGET, "Retry Lure");
            assertEquals(0L, get(script, "southernCrossingStartedAt"));
            assertNull(get(script, "pendingSouthernCurtainAction"));
            assertEquals(preparationStarted, get(script, "stateEnteredAt"));
            assertEquals(2, get(script, "lureAttempts"));

            long before = System.currentTimeMillis();
            transition.invoke(script, BlackjackScript.SouthernTentPhase.MOVING_TO_CURTAIN, "Fresh crossing");
            long started = (long) get(script, "southernCrossingStartedAt");
            assertTrue(started >= before);
            transition.invoke(script, BlackjackScript.SouthernTentPhase.OPENING_CURTAIN, "Open");
            assertEquals(started, get(script, "southernCrossingStartedAt"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void frontFacingCameraConvertsActorConventionAndWrapsYaw()
    {
        assertEquals(0, BlackjackScript.frontFacingCameraYaw(0));
        assertEquals(1536, BlackjackScript.frontFacingCameraYaw(512));
        assertEquals(1024, BlackjackScript.frontFacingCameraYaw(1024));
        assertEquals(512, BlackjackScript.frontFacingCameraYaw(1536));
        assertEquals(1, BlackjackScript.frontFacingCameraYaw(2047));
        assertEquals(1792, BlackjackScript.frontFacingCameraYaw(256));
        assertEquals(1280, BlackjackScript.frontFacingCameraYaw(768));
        assertEquals(768, BlackjackScript.frontFacingCameraYaw(1280));
        assertEquals(256, BlackjackScript.frontFacingCameraYaw(1792));
    }

    @Test
    void continuousFeedbackRecoveryIsBoundedAndRespectsRealFeedback()
    {
        assertFalse(BlackjackScript.continuousFeedbackTimedOut(20_000, 0, 0));
        assertFalse(BlackjackScript.continuousFeedbackTimedOut(20_000, 12_001, 0));
        assertTrue(BlackjackScript.continuousFeedbackTimedOut(20_000, 12_000, 0));
        assertFalse(BlackjackScript.continuousFeedbackTimedOut(20_000, 12_000, 16_001));
        assertTrue(BlackjackScript.continuousFeedbackTimedOut(20_000, 12_000, 16_000));
    }

    @Test
    void runRestorationDoesNotInterruptActiveLure() throws Exception
    {
        BlackjackScript script = preparedScript(BlackjackScript.SouthernTentPhase.LEADING_THROUGH_CURTAIN);
        try
        {
            set(script, "autoRunBeforeLure", Boolean.TRUE);
            invoke(script, "ensureRunAfterPreparation");
            assertEquals(0L, get(script, "lastRunRestoreAttemptAt"));
            assertEquals(Boolean.TRUE, get(script, "autoRunBeforeLure"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void expiredPubResetStopsInsteadOfReassessingOnWrongFloor() throws Exception
    {
        for (BlackjackScript.SouthernTentPhase phase : new BlackjackScript.SouthernTentPhase[]{
                BlackjackScript.SouthernTentPhase.CLIMBING_PUB_STAIRS,
                BlackjackScript.SouthernTentPhase.WAITING_UPSTAIRS,
                BlackjackScript.SouthernTentPhase.DESCENDING_PUB_STAIRS})
        {
            BlackjackScript script = preparedScript(phase);
            try
            {
                set(script, "southernTentPhaseEnteredAt", System.currentTimeMillis() - 21_000);
                invoke(script, "prepareSouthernTent");
                assertEquals(BlackjackState.ERROR, script.getState());
            }
            finally
            {
                dispose(script);
            }
        }
    }

    @Test
    void expiredCurtainDispatchAllowsBoundedRetry() throws Exception
    {
        BlackjackScript script = preparedScript(BlackjackScript.SouthernTentPhase.OPENING_CURTAIN);
        try
        {
            set(script, "pendingSouthernCurtain", new net.runelite.api.coords.WorldPoint(3350, 2957, 0));
            set(script, "pendingSouthernCurtainAction", "Open");
            set(script, "pendingSouthernCurtainAt", System.currentTimeMillis() - 3_100);
            Method pending = BlackjackScript.class.getDeclaredMethod("southernCurtainActionPending");
            pending.setAccessible(true);
            assertEquals(false, pending.invoke(script));
            assertNull(get(script, "pendingSouthernCurtain"));
            assertNull(get(script, "pendingSouthernCurtainAction"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void crossingDeadlineSurvivesRepeatedPhaseChanges() throws Exception
    {
        BlackjackScript script = preparedScript(BlackjackScript.SouthernTentPhase.OPENING_CURTAIN);
        try
        {
            set(script, "southernTentPhaseEnteredAt", System.currentTimeMillis());
            set(script, "southernCrossingStartedAt", System.currentTimeMillis() - 31_000);
            invoke(script, "prepareSouthernTent");
            assertEquals(BlackjackState.ERROR, script.getState());
            assertTrue(script.getStopReason().contains("crossing did not complete"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void expiredCurtainPhasesRecoverBeforeAnyClientInteraction() throws Exception
    {
        for (BlackjackScript.SouthernTentPhase phase : new BlackjackScript.SouthernTentPhase[]{
                BlackjackScript.SouthernTentPhase.OPENING_CURTAIN,
                BlackjackScript.SouthernTentPhase.CLOSING_CURTAIN,
                BlackjackScript.SouthernTentPhase.POSITIONING_TO_CLOSE})
        {
            BlackjackScript script = preparedScript(phase);
            try
            {
                invoke(script, "prepareSouthernTent");
                assertEquals(BlackjackScript.SouthernTentPhase.ASSESSING, get(script, "southernTentPhase"));
                assertTrue(script.getNextAction().contains("timed out"));
                assertEquals(BlackjackState.PREPARING_SOUTHERN_TENT, script.getState());
            }
            finally
            {
                dispose(script);
            }
        }
    }

    @Test
    void releaseDeadlineStopsBeforeAttemptingReentry() throws Exception
    {
        BlackjackScript script = preparedScript(BlackjackScript.SouthernTentPhase.WAITING_FOR_RELEASE);
        try
        {
            invoke(script, "prepareSouthernTent");
            assertEquals(BlackjackState.ERROR, script.getState());
            assertTrue(script.getStopReason().contains("did not stop following"));
        }
        finally
        {
            dispose(script);
        }
    }

    @Test
    void autoRunRestoresOriginalPreferenceAfterReassessmentOrFailure() throws Exception
    {
        boolean original = Microbot.enableAutoRunOn;
        try
        {
            for (boolean enabled : new boolean[]{false, true})
            {
                for (BlackjackScript.SouthernTentPhase phase : new BlackjackScript.SouthernTentPhase[]{
                        BlackjackScript.SouthernTentPhase.CLOSING_CURTAIN,
                        BlackjackScript.SouthernTentPhase.WAITING_FOR_RELEASE})
                {
                    BlackjackScript script = preparedScript(phase);
                    try
                    {
                        Microbot.enableAutoRunOn = enabled;
                        invoke(script, "suspendAutoRunForLure");
                        invoke(script, "suspendAutoRunForLure");
                        assertFalse(Microbot.enableAutoRunOn);
                        invoke(script, "prepareSouthernTent");
                        assertEquals(enabled, Microbot.enableAutoRunOn);
                        assertNull(get(script, "autoRunBeforeLure"));
                    }
                    finally
                    {
                        dispose(script);
                    }
                }
            }
        }
        finally
        {
            Microbot.enableAutoRunOn = original;
        }
    }

    private static BlackjackScript preparedScript(BlackjackScript.SouthernTentPhase phase) throws Exception
    {
        BlackjackScript script = new BlackjackScript();
        set(script, "config", new BlackjackConfig()
        {
            @Override
            public BlackjackTarget target()
            {
                return BlackjackTarget.MENAPHITE_THUG;
            }
        });
        set(script, "state", BlackjackState.PREPARING_SOUTHERN_TENT);
        set(script, "stateEnteredAt", System.currentTimeMillis());
        set(script, "southernTentPhase", phase);
        set(script, "southernTentPhaseEnteredAt", System.currentTimeMillis() - 13_000);
        return script;
    }

    private static Object get(BlackjackScript script, String name) throws Exception
    {
        Field field = BlackjackScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private static void set(BlackjackScript script, String name, Object value) throws Exception
    {
        Field field = BlackjackScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }

    private static void invoke(BlackjackScript script, String name) throws Exception
    {
        Method method = BlackjackScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(script);
    }

    private static void dispose(BlackjackScript script) throws Exception
    {
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(script)).shutdownNow();
    }
}
