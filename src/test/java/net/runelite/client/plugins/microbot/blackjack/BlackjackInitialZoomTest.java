package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackInitialZoomTest
{
    @Test
    void liveZoomIsNotReadBeforeLogin()
    {
        assertEquals(-1, BlackjackScript.initialTargetZoom(0, false, () ->
        {
            throw new NullPointerException("varc map is null before login");
        }));
    }

    @Test
    void liveZoomIsCapturedWhenLoggedIn()
    {
        assertEquals(512, BlackjackScript.initialTargetZoom(0, true, () -> 512));
    }

    @Test
    void configuredZoomNeverReadsLiveZoom()
    {
        assertEquals(736, BlackjackScript.initialTargetZoom(736, false, () ->
        {
            throw new NullPointerException("varc map is null before login");
        }));
        assertEquals(736, BlackjackScript.initialTargetZoom(736, true, () -> 512));
    }

    @Test
    void hiddenTargetZoomToleratesUncapturedBaseline()
    {
        int fromBaseline = BlackjackScript.zoomForHiddenTarget(BlackjackScript.zoomForHiddenTarget(-1));
        assertEquals(200, fromBaseline);
        assertTrue(Math.max(BlackjackScript.zoomForHiddenTarget(600), fromBaseline) >= 200);
    }
}
