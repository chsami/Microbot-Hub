package net.runelite.client.plugins.microbot.herbihunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plugin's pure decisions: trail, stalls, breaks, pace, config and panel. */
class HerbiboarDecisionCheckTest
{
    @Test
    void everyDecisionCheckPasses()
    {
        assertEquals(0, HerbiboarDecisionCheck.run(), "failed checks are listed as FAIL lines above");
    }
}
