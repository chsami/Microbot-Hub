package net.runelite.client.plugins.microbot.sulphurnaguafigther;

import net.runelite.api.Actor;
import net.runelite.api.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SulphurNaguaInteractingTest {

    @Test
    void missingTargetIsAvailable() {
        assertTrue(SulphurNaguaScript.isMissingOrDead(null));
    }

    @Test
    void deadTargetIsAvailable() {
        Actor target = mock(Actor.class);
        when(target.isDead()).thenReturn(true);

        assertTrue(SulphurNaguaScript.isMissingOrDead(target));
    }

    @Test
    void liveTargetIsNotAvailable() {
        Actor target = mock(Actor.class);

        assertFalse(SulphurNaguaScript.isMissingOrDead(target));
    }

    @Test
    void npcWhoseTargetDropsAfterFirstReadIsNotInteractingWithUs() {
        Player local = mock(Player.class);
        Actor npc = mock(Actor.class);
        when(npc.getInteracting()).thenReturn(local, (Actor) null);

        assertTrue(SulphurNaguaScript.isInteractingWith(npc, local));
        assertFalse(SulphurNaguaScript.isInteractingWith(npc, local));
        verify(npc, times(2)).getInteracting();
    }

    @Test
    void npcInteractingWithSomeoneElseIsNotInteractingWithUs() {
        Player local = mock(Player.class);
        Player other = mock(Player.class);
        Actor npc = mock(Actor.class);
        when(npc.getInteracting()).thenReturn(other);

        assertFalse(SulphurNaguaScript.isInteractingWith(npc, local));
    }

    @Test
    void npcWhoseTargetDropsMidCheckIsReadOnce() {
        Player local = mock(Player.class);
        Player other = mock(Player.class);
        Actor npc = mock(Actor.class);
        when(npc.getInteracting()).thenReturn(other, (Actor) null);

        assertFalse(SulphurNaguaScript.isFreeOrInteractingWith(npc, local));
        verify(npc, times(1)).getInteracting();
    }

    @Test
    void freeNpcOrNpcOnUsIsTargetable() {
        Player local = mock(Player.class);
        Actor freeNpc = mock(Actor.class);
        Actor npcOnUs = mock(Actor.class);
        when(npcOnUs.getInteracting()).thenReturn(local);

        assertTrue(SulphurNaguaScript.isFreeOrInteractingWith(freeNpc, local));
        assertTrue(SulphurNaguaScript.isFreeOrInteractingWith(npcOnUs, local));
    }
}
