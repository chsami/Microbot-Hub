package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScurriusScriptTest {

    private final WorldArea fightRoom = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);

    @Test
    void thresholdStaysInRangeWhenMinExceedsMax() {
        for (int i = 0; i < 500; i++) {
            int threshold = ScurriusScript.randomThreshold(60, 40);
            assertTrue(threshold >= 40 && threshold <= 60);
        }
    }

    @Test
    void thresholdStaysInRangeWhenOrdered() {
        for (int i = 0; i < 500; i++) {
            int threshold = ScurriusScript.randomThreshold(40, 60);
            assertTrue(threshold >= 40 && threshold <= 60);
        }
    }

    @Test
    void equalBoundsReturnThatValue() {
        assertEquals(50, ScurriusScript.randomThreshold(50, 50));
    }

    @Test
    void nullLocationIsNotInFightRoom() {
        assertFalse(ScurriusScript.isInArea(fightRoom, null));
    }

    @Test
    void locationInsideFightRoomIsDetected() {
        assertTrue(ScurriusScript.isInArea(fightRoom, new WorldPoint(3295, 9865, 0)));
        assertFalse(ScurriusScript.isInArea(fightRoom, new WorldPoint(3279, 9869, 0)));
    }

    @Test
    void giantRatNameIsReadOnceSoALaterNullCannotThrow() {
        Rs2NpcModel rat = mock(Rs2NpcModel.class);
        NPC npc = mock(NPC.class);
        when(rat.getName()).thenReturn("Giant rat", (String) null);
        when(rat.getNpc()).thenReturn(npc);
        when(npc.isDead()).thenReturn(false);

        assertTrue(ScurriusScript.isLiveGiantRat(rat));
        verify(rat).getName();
    }

    @Test
    void nullNameIsNotAGiantRat() {
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getName()).thenReturn(null);

        assertFalse(ScurriusScript.isLiveGiantRat(npc));
    }

    @Test
    void deadOrOtherNpcsAreNotLiveGiantRats() {
        Rs2NpcModel deadRat = mock(Rs2NpcModel.class);
        NPC deadNpc = mock(NPC.class);
        when(deadRat.getName()).thenReturn("Giant rat");
        when(deadRat.getNpc()).thenReturn(deadNpc);
        when(deadNpc.isDead()).thenReturn(true);
        assertFalse(ScurriusScript.isLiveGiantRat(deadRat));

        Rs2NpcModel scurrius = mock(Rs2NpcModel.class);
        when(scurrius.getName()).thenReturn("Scurrius");
        assertFalse(ScurriusScript.isLiveGiantRat(scurrius));
    }
}
