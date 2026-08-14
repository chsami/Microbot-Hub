package net.runelite.client.plugins.microbot.zulrahslayer.rotationutils;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.zulrahslayer.constants.ZulrahType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure rotation-identification logic (no client). {@link RotationType#matching} narrows a candidate
 * set by the Zulrah form observed at each stage; feeding a rotation's own form sequence must converge
 * on exactly that rotation, and invalid inputs must yield an empty set rather than a wrong guess.
 */
class RotationTypeTest {

    /** A fake Zulrah NPC reporting the id of the given form (that is all {@code matching} reads). */
    private static NPC npcOf(ZulrahType type) {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(type.getNpcId());
        return npc;
    }

    @Test
    void allRotationsReturnsIndependentMutableCopies() {
        List<RotationType> first = RotationType.allRotations();
        List<RotationType> second = RotationType.allRotations();

        assertEquals(4, first.size(), "there are four Zulrah rotations");
        assertNotSame(first, second, "each call must return a fresh list so narrowing can't corrupt the source");

        first.clear();
        assertEquals(4, RotationType.allRotations().size(), "mutating a returned list must not affect later calls");
    }

    @Test
    void narrowingByObservedFormsConvergesOnTheCorrectRotation() {
        // Replays the plugin's narrowing: intersect the candidate set with the form seen at each stage.
        RotationType target = RotationType.ROT_A;
        List<RotationType> candidates = RotationType.allRotations();

        int lockedAtStage = -1;
        for (int stage = 0; stage < target.getZulrahPhases().size(); stage++) {
            ZulrahType observedForm = target.getZulrahPhases().get(stage).getZulrahNpc().getType();
            candidates = RotationType.matching(candidates, npcOf(observedForm), stage);

            assertTrue(candidates.contains(target),
                    "the true rotation must never be narrowed away by its own forms (stage " + stage + ")");
            if (candidates.size() == 1) {
                lockedAtStage = stage;
                break;
            }
        }

        assertEquals(List.of(target), candidates, "narrowing should converge on exactly the true rotation");
        assertTrue(lockedAtStage >= 0 && lockedAtStage <= 4,
                "Rotation A becomes unique by stage 4; locked at stage " + lockedAtStage);
    }

    @Test
    void eachRotationIsUniquelyIdentifiableFromItsOwnFormSequence() {
        for (RotationType target : RotationType.values()) {
            List<RotationType> candidates = RotationType.allRotations();
            for (int stage = 0; stage < target.getZulrahPhases().size() && candidates.size() > 1; stage++) {
                ZulrahType observedForm = target.getZulrahPhases().get(stage).getZulrahNpc().getType();
                candidates = RotationType.matching(candidates, npcOf(observedForm), stage);
            }
            assertEquals(List.of(target), candidates,
                    target.getRotationName() + " should be uniquely identifiable from its form sequence");
        }
    }

    @Test
    void matchingReturnsEmptyForInvalidStageOrUnknownNpc() {
        assertTrue(RotationType.matching(RotationType.allRotations(), npcOf(ZulrahType.RANGE), -1).isEmpty(),
                "a negative stage cannot match any rotation");

        NPC notZulrah = mock(NPC.class);
        when(notZulrah.getId()).thenReturn(1); // not one of 2042/2043/2044
        assertTrue(RotationType.matching(RotationType.allRotations(), notZulrah, 0).isEmpty(),
                "an unrecognised NPC id must not match a rotation");
    }

    @Test
    void narrowingIsMonotonicIntersectionNotAFreshScan() {
        // Start from a single-candidate base; a matching stage keeps it, so the set never re-expands.
        List<RotationType> base = List.of(RotationType.ROT_A);
        ZulrahType stage0Form = RotationType.ROT_A.getZulrahPhases().get(0).getZulrahNpc().getType();

        List<RotationType> narrowed = RotationType.matching(base, npcOf(stage0Form), 0);

        assertEquals(List.of(RotationType.ROT_A), narrowed, "intersecting a single candidate cannot add others back");
        assertSame(RotationType.ROT_A, narrowed.get(0));
    }
}
