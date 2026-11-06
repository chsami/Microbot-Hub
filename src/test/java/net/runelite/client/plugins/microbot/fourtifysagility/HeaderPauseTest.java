package net.runelite.client.plugins.microbot.fourtifysagility;

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
        assertNull(FourtifysAgilityPlugin.pauseCheck("net.runelite.client.plugins.microbot.NoSuchPauseController"));
        assertFalse(FourtifysAgilityPlugin.headerPaused(null));
    }

    @Test
    public void theHeaderPauseButtonPausesTheLap()
    {
        java.lang.reflect.Method check = FourtifysAgilityPlugin.pauseCheck(HeaderPauseTest.class.getName());
        assertNotNull(check);
        paused = true;
        assertTrue(FourtifysAgilityPlugin.headerPaused(check));
        paused = false;
        assertFalse(FourtifysAgilityPlugin.headerPaused(check));
    }
}
