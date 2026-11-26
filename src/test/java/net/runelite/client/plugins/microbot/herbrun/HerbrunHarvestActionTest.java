package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HerbrunHarvestActionTest {

    private static Predicate<String> patchWith(String... actions) {
        return wanted -> Arrays.stream(actions)
                .filter(Objects::nonNull)
                .anyMatch(action -> action.toLowerCase().contains(wanted.toLowerCase()));
    }

    @Test
    void allotmentPatchOfferingHarvestIsHarvested() {
        assertEquals("Harvest", HerbrunScript.harvestAction(patchWith("Harvest", "Inspect", null, "Guide")));
    }

    @Test
    void flowerPatchOfferingPickIsPicked() {
        assertEquals("Pick", HerbrunScript.harvestAction(patchWith("Pick", "Inspect", null, "Guide")));
    }

    @Test
    void grownHerbPatchOfferingPickIsHarvestableEvenWhenVarbitReadsWeeds() {
        assertEquals("Harvestable", HerbrunScript.herbPatchState(patchWith("Pick", "Inspect", null, "Guide"), () -> 0));
    }

    @Test
    void herbPatchOfferingRakeIsWeedsEvenWhenVarbitReadsGrown() {
        assertEquals("Weeds", HerbrunScript.herbPatchState(patchWith("Rake", "Inspect", null, "Guide"), () -> 36));
    }

    @Test
    void deadHerbPatchOfferingClearIsDead() {
        assertEquals("Dead", HerbrunScript.herbPatchState(patchWith("Clear", "Inspect", null, "Guide"), () -> 0));
    }

    @Test
    void diseasedHerbPatchOfferingCureIsDiseased() {
        assertEquals("Diseased", HerbrunScript.herbPatchState(patchWith("Cure", "Inspect", null, "Guide"), () -> 0));
    }

    @Test
    void herbPatchWithoutDecidingActionFallsBackToVarbit() {
        assertEquals("Empty", HerbrunScript.herbPatchState(patchWith("Inspect", null, "Guide"), () -> 3));
        assertEquals("Growing", HerbrunScript.herbPatchState(patchWith("Inspect", null, "Guide"), () -> 32));
    }

    @Test
    void patchVarbitIsReadFromTheClientNotTheStaleCache() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(client.getVarbitValue(4774)).thenReturn(36);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>) i.getArgument(0)).call()));
        try (var micro = mockStatic(Microbot.class)) {
            micro.when(Microbot::getClient).thenReturn(client);
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(() -> Microbot.getVarbitValue(4774)).thenReturn(0);
            assertEquals(36, HerbrunScript.liveVarbitValue(4774));
        }
    }

    @Test
    void patchVarbitReadThatFailsIsNotTreatedAsWeeds() {
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
        try (var micro = mockStatic(Microbot.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            int value = HerbrunScript.liveVarbitValue(4774);
            assertNotEquals("Weeds", HerbrunScript.herbPatchState(patchWith("Inspect", null, "Guide"), () -> value));
        }
    }
}
