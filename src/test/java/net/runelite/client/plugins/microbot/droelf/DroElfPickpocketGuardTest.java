package net.runelite.client.plugins.microbot.droelf;

import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DroElfPickpocketGuardTest
{
    @Test void clicksWhileRunningWithLockedElf()
    {
        assertTrue(DroElfScript.shouldClickLockedElf(true, mock(Rs2NpcModel.class)));
    }

    @Test void skipsClickWhenElfWasClearedDuringThePreClickPause()
    {
        assertFalse(DroElfScript.shouldClickLockedElf(true, null));
    }

    @Test void skipsClickWhenScriptStoppedDuringThePreClickPause()
    {
        assertFalse(DroElfScript.shouldClickLockedElf(false, mock(Rs2NpcModel.class)));
        assertFalse(DroElfScript.shouldClickLockedElf(false, null));
    }
}
