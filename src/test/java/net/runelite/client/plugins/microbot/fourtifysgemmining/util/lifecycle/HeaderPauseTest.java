package net.runelite.client.plugins.microbot.fourtifysgemmining.util.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeaderPauseTest
{
    private static boolean paused;

    public static boolean isPaused()
    {
        return paused;
    }

    @Test
    public void aClientWithoutTheHeaderPauseButtonIsNeverPaused()
    {
        assertNull(SessionLifecycle.pauseCheck("net.runelite.client.plugins.microbot.NoSuchPauseController"));
        assertFalse(SessionLifecycle.headerPaused(null));
    }

    @Test
    public void theHeaderPauseButtonPausesMining()
    {
        java.lang.reflect.Method check = SessionLifecycle.pauseCheck(HeaderPauseTest.class.getName());
        assertNotNull(check);
        paused = true;
        assertTrue(SessionLifecycle.headerPaused(check));
        paused = false;
        assertFalse(SessionLifecycle.headerPaused(check));
    }
}
