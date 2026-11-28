package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.accountstarter.logic.RewardSpace;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import org.junit.jupiter.api.Test;

class RewardSpaceTest {
    @Test
    void claimMakesSpaceFirst() {
        assertEquals(7, RewardSpace.slotsNeeded(List.of(ApTask.STRENGTH_5), Collections.emptyMap()));
    }

    @Test
    void whatALaterTaskUsesStaysOutOfTheBank() {
        assertEquals(Set.of(), RewardSpace.toBank(List.of(ApTask.RANGED_10), Set.of(843, 882)));
        assertEquals(Set.of(329, 113), RewardSpace.toBank(List.of(ApTask.STRENGTH_20), Set.of(843)));
    }

    @Test
    void itemIdsListEveryRewardOfTheClaimedTasks() {
        assertEquals(Set.of(329, 113, 1299), RewardSpace.itemIds(List.of(ApTask.STRENGTH_20, ApTask.ATTACK_20)));
    }

    @Test
    void heldStackNeedsNoSlot() {
        assertEquals(2, RewardSpace.slotsNeeded(List.of(ApTask.MAGIC_5), Map.of(558, 12)));
    }

    @Test
    void stacksAcrossTasksShareOneSlot() {
        assertEquals(3, RewardSpace.slotsNeeded(List.of(ApTask.RANGED_5, ApTask.RANGED_10), Collections.emptyMap()));
    }
}
