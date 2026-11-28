package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.api.gameval.ObjectID;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HerbrunGuildAllotmentsTest {

    @Test
    void farmingGuildAllotmentsAndFlowerAreFarmed() {
        assertTrue(HerbrunScript.ALLOTMENT_FLOWER_REGIONS.contains("Farming Guild"));
    }

    @Test
    void farmingGuildAllotmentObjectsAreRecognised() {
        assertTrue(Arrays.stream(HerbrunScript.ALLOTMENT_PATCH_IDS).anyMatch(id -> id == ObjectID.FARMING_VEG_PATCH_12));
        assertTrue(Arrays.stream(HerbrunScript.ALLOTMENT_PATCH_IDS).anyMatch(id -> id == ObjectID.FARMING_VEG_PATCH_13));
    }

    @Test
    void farmingGuildFlowerObjectIsRecognised() {
        assertTrue(Arrays.stream(HerbrunScript.FLOWER_PATCH_IDS).anyMatch(id -> id == ObjectID.FARMING_FLOWER_PATCH_6));
    }

    @Test
    void farmingGuildCountsTowardsAllotmentAndFlowerSeeds() {
        HerbrunConfig config = mock(HerbrunConfig.class);
        when(config.enableGuild()).thenReturn(true);
        HerbrunScript script = new HerbrunScript(mock(HerbrunPlugin.class), config);

        assertEquals(1, script.countEnabledAllotmentFlowerLocations());
    }
}
