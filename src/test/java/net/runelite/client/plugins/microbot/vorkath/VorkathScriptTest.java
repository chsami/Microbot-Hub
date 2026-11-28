package net.runelite.client.plugins.microbot.vorkath;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
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

    private static final WorldPoint VORKATH = new WorldPoint(2269, 4062, 0);

    @Test
    void wooxWalkFromTheLaneGoesToTheFarEndInsteadOfStandingStill() {
        VorkathScript script = new VorkathScript(null);

        assertEquals(new WorldPoint(2274, 4054, 0), script.wooxWalkTarget(VORKATH, new WorldPoint(2270, 4054, 0), null));
        assertEquals(new WorldPoint(2270, 4054, 0), script.wooxWalkTarget(VORKATH, new WorldPoint(2274, 4055, 0), null));
    }

    @Test
    void wooxWalkKeepsTheCurrentTargetWhileWalkingTheLane() {
        VorkathScript script = new VorkathScript(null);
        WorldPoint west = new WorldPoint(2270, 4054, 0);

        assertEquals(west, script.wooxWalkTarget(VORKATH, new WorldPoint(2272, 4054, 0), west));
    }

    @Test
    void wooxWalkNeverCrossesAcidInTheLane() {
        VorkathScript script = new VorkathScript(null);
        script.getAcidPools().add(new WorldPoint(2273, 4054, 0));

        assertEquals(new WorldPoint(2270, 4054, 0), script.wooxWalkTarget(VORKATH, new WorldPoint(2272, 4054, 0), new WorldPoint(2274, 4054, 0)));
    }

    @Test
    void standingOnAcidInTheLaneWalksToASafeTile() {
        VorkathScript script = new VorkathScript(null);
        script.getAcidPools().add(new WorldPoint(2272, 4054, 0));
        script.getAcidPools().add(new WorldPoint(2271, 4055, 0));

        assertEquals(new WorldPoint(2273, 4054, 0), script.wooxWalkTarget(VORKATH, new WorldPoint(2272, 4054, 0), null));
    }

    @Test
    void safeTileIsReachedWithoutWalkingThroughAcid() {
        VorkathScript script = new VorkathScript(null);
        script.getAcidPools().add(new WorldPoint(2272, 4056, 0));

        assertFalse(script.isPathSafe(new WorldPoint(2272, 4058, 0), new WorldPoint(2272, 4054, 0)));
        assertTrue(script.isPathSafe(new WorldPoint(2272, 4058, 0), new WorldPoint(2270, 4054, 0)));
        assertEquals(new WorldPoint(2271, 4054, 0), script.findSafeTile(VORKATH, new WorldPoint(2272, 4058, 0)));
    }

    @Test
    void deathAwayFromVorkathKeepsTheCurrentState() {
        assertEquals(State.BANKING, VorkathScript.stateAfterDeath(State.BANKING));
        assertEquals(State.DEAD_WALK, VorkathScript.stateAfterDeath(State.DEAD_WALK));
        assertEquals(State.TELEPORT_AWAY, VorkathScript.stateAfterDeath(State.TELEPORT_AWAY));
    }
}
