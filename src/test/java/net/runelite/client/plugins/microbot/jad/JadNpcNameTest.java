package net.runelite.client.plugins.microbot.jad;

import net.runelite.api.Actor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JadNpcNameTest {

    @Test
    void nameThatDropsAfterFirstReadIsReadOnce() {
        Actor npc = mock(Actor.class);
        when(npc.getName()).thenReturn("TzTok-Jad", (String) null);

        assertTrue(JadScript.nameContains(npc, "jad"));
        assertFalse(JadScript.nameContains(npc, "jad"));
        verify(npc, times(2)).getName();
    }

    @Test
    void nullNameDoesNotMatch() {
        Actor npc = mock(Actor.class);

        assertFalse(JadScript.nameContains(npc, "jad"));
        verify(npc, times(1)).getName();
    }

    @Test
    void matchIgnoresCase() {
        Actor healer = mock(Actor.class);
        when(healer.getName()).thenReturn("Yt-HurKot");

        assertTrue(JadScript.nameContains(healer, "hurkot"));
        assertFalse(JadScript.nameContains(healer, "jad"));
    }
}
