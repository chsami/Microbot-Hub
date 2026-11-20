package net.runelite.client.plugins.microbot.vorkath;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VorkathScriptTest {

    @Test
    void crumbleUndeadIsCastOnTheLegacyNpcModelOfTheSpawn() {
        NPC npc = mock(NPC.class);
        Rs2NpcModel spawn = mock(Rs2NpcModel.class);
        when(spawn.getNpc()).thenReturn(npc);

        try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class)) {
            magic.when(() -> Rs2Magic.castOn(eq(MagicAction.CRUMBLE_UNDEAD), argThat((Actor actor) ->
                    actor instanceof net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel
                            && ((net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel) actor).getRuneliteNpc() == npc)))
                    .thenReturn(true);

            assertTrue(VorkathScript.castCrumbleUndead(spawn));
        }
    }

    @Test
    void crumbleUndeadIsNotCastWithoutAnNpc() {
        try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class)) {
            assertFalse(VorkathScript.castCrumbleUndead(null));
            assertFalse(VorkathScript.castCrumbleUndead(mock(Rs2NpcModel.class)));
            magic.verifyNoInteractions();
        }
    }

    @Test
    void deathDuringTheFightGoesToDeadWalk() {
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.PREPARE_FIGHT));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.FIGHT_VORKATH));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.ZOMBIE_SPAWN));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.ACID));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.LOOT_ITEMS));
    }

    @Test
    void deathAwayFromVorkathKeepsTheCurrentState() {
        assertEquals(State.BANKING, VorkathScript.stateAfterDeath(State.BANKING));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.DEAD_WALK));
        assertEquals(State.TELEPORT_AWAY, VorkathScript.stateAfterDeath(State.TELEPORT_AWAY));
    }
}
