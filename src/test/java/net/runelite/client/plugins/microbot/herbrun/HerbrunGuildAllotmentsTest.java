package net.runelite.client.plugins.microbot.herbrun;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void farmingGuildCountsTowardsAllotmentAndFlowerSeeds() {
        HerbrunConfig config = mock(HerbrunConfig.class);
        when(config.enableGuild()).thenReturn(true);
        HerbrunScript script = new HerbrunScript(mock(HerbrunPlugin.class), config);

        assertEquals(List.of("Farming Guild"), script.enabledAllotmentFlowerLocations());
    }

    @Test
    void seedLocationsMatchFarmedRegions() {
        HerbrunConfig config = mock(HerbrunConfig.class);
        when(config.enableArdougne()).thenReturn(true);
        when(config.enableCatherby()).thenReturn(true);
        when(config.enableFalador()).thenReturn(true);
        when(config.enableGuild()).thenReturn(true);
        when(config.enableHosidius()).thenReturn(true);
        when(config.enableMorytania()).thenReturn(true);
        when(config.enableVarlamore()).thenReturn(true);
        HerbrunScript script = new HerbrunScript(mock(HerbrunPlugin.class), config);

        assertEquals(HerbrunScript.ALLOTMENT_FLOWER_REGIONS.size(), script.enabledAllotmentFlowerLocations().size());
    }

    @Test
    void allotmentShortfallExplainsSeedCount() {
        List<String> locations = List.of("Ardougne", "Catherby", "Falador", "Farming Guild", "Morytania");

        assertEquals("Not enough Sweetcorn seed in the bank: need 30 (3 per patch, 2 patches at each of 5 locations: "
                        + "Ardougne, Catherby, Falador, Farming Guild, Morytania). Bank more seeds, turn off a location "
                        + "in the Location toggles section, or turn on Allow Partial Runs.",
                HerbrunScript.allotmentSeedShortfall("Sweetcorn seed", locations, false));
    }

    @Test
    void flowerShortfallExplainsSeedCount() {
        List<String> locations = List.of("Ardougne", "Catherby", "Falador", "Farming Guild", "Morytania");

        assertEquals("Not enough Limpwurt seed in the bank: need 5 (1 per location: "
                        + "Ardougne, Catherby, Falador, Farming Guild, Morytania). Bank more seeds or turn off a location "
                        + "in the Location toggles section.",
                HerbrunScript.flowerSeedShortfall("Limpwurt seed", locations, true));
    }
}
