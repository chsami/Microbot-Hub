package net.runelite.client.plugins.microbot.herbrun;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
