package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TormentedDemonScriptTest {

    @Test
    void clearedInteractionIsNotTheTarget() {
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        when(target.getIndex()).thenReturn(7);

        assertFalse(TormentedDemonScript.isInteractingWith(null, target));
    }

    @Test
    void nulledTargetDoesNotThrow() {
        NPC interacting = mock(NPC.class);
        when(interacting.getIndex()).thenReturn(7);

        assertFalse(TormentedDemonScript.isInteractingWith(interacting, null));
    }

    @Test
    void nonNpcInteractionIsNotTheTarget() {
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        when(target.getIndex()).thenReturn(7);

        assertFalse(TormentedDemonScript.isInteractingWith(mock(Player.class), target));
    }

    @Test
    void matchingIndexIsTheTarget() {
        NPC interacting = mock(NPC.class);
        when(interacting.getIndex()).thenReturn(7);
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        when(target.getIndex()).thenReturn(7);

        assertTrue(TormentedDemonScript.isInteractingWith(interacting, target));
    }

    @Test
    void differentIndexIsNotTheTarget() {
        NPC interacting = mock(NPC.class);
        when(interacting.getIndex()).thenReturn(8);
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        when(target.getIndex()).thenReturn(7);

        assertFalse(TormentedDemonScript.isInteractingWith(interacting, target));
    }
}
