package net.runelite.client.plugins.microbot.accountstarter;

import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Progress;
import net.runelite.client.plugins.microbot.accountstarter.logic.ProgressReader;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressReaderTest {

    private static Progress read(Map<Integer, Integer> varbits) {
        return ProgressReader.read(id -> varbits.getOrDefault(id, 0));
    }

    @Test
    void freshAccountHasNothingDone() {
        Progress p = read(Map.of(9340, 1));
        for (ApTask t : ApTask.values()) {
            assertEquals(TaskState.NOT_DONE, p.of(t));
        }
        assertTrue(p.isParticipating());
        assertFalse(p.isAltPaths());
        assertFalse(p.isIronman());
    }

    @Test
    void doneButNotClaimed() {
        assertEquals(TaskState.DONE_UNCLAIMED, read(Map.of(10174, 1)).of(ApTask.ATTACK_5));
    }

    @Test
    void claimedWinsOverDone() {
        Map<Integer, Integer> v = new HashMap<>();
        v.put(10174, 1);
        v.put(10175, 1);
        assertEquals(TaskState.CLAIMED, read(v).of(ApTask.ATTACK_5));
    }

    @Test
    void altPathSelectorDetected() {
        assertTrue(read(Map.of(10518, 1)).isAltPaths());
    }

    @Test
    void ownedSumsAllPlaces() {
        AccountSnapshot s = AccountSnapshot.builder()
                .inventory(Map.of(882, 2))
                .bank(Map.of(882, 3))
                .build();
        assertEquals(5, s.owned(882));
    }
}
