package net.runelite.client.plugins.microbot.blackjack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackZoomRestoreTest
{
    @Test
    void queuedWriteIsNotConfirmation()
    {
        BlackjackZoomRestore restore = new BlackjackZoomRestore();
        assertFalse(restore.confirm(200, 736, 36, 0));
        assertFalse(restore.confirm(736, 736, 36, 1000));
        assertFalse(restore.confirm(736, 736, 36, 3999));
        assertTrue(restore.confirm(736, 736, 36, 4000));
    }

    @Test
    void competingZoomAndNewTravelRestartConfirmation()
    {
        BlackjackZoomRestore restore = new BlackjackZoomRestore();
        assertFalse(restore.confirm(736, 736, 36, 0));
        assertFalse(restore.confirm(200, 736, 36, 2000));
        assertFalse(restore.confirm(730, 736, 36, 3000));
        assertFalse(restore.confirm(740, 736, 36, 5000));
        assertTrue(restore.confirm(736, 736, 36, 6000));
        restore.reset();
        assertFalse(restore.confirm(736, 736, 36, 10000));
    }
}
