package net.runelite.client.plugins.microbot.fourtifysbarrows.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.fourtifysbarrows.NativeConfigTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BarrowsConfigInitializationTest
{
    @Test
    public void loadingDefaultsPersistsTheNativeSettingsValues() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "activityName"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "activityName"));
            fixture.manager.setDefaultConfiguration(fixture.config, false);
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "activityName"));
            assertEquals("385", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "foodId"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "magicGear"), "Blank keeps the worn equipment");
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "rangedGear"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "meleeGear"));
            assertEquals("880", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "rewardTarget"));
            assertEquals("BARROWS_TABLET", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "travelMethod"));
            assertEquals("RING_OF_DUELING", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "bankTeleport"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "bankPlane"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "bankTravelMethod"));
            assertEquals("true", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "showOverlay"));
            assertEquals("0", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "tickDelayMin"));
            assertEquals("1", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "tickDelayMax"));
            assertEquals("1", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "tickDelayTarget"));
            assertEquals("0", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "tickDelayDeviation"));
            assertEquals("false", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "tickDelayWeightedDistribution"));
            assertEquals("RING_OF_DUELING", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "emergencyTeleport"));
            assertNull(fixture.manager.getConfiguration(BarrowsConfig.GROUP, "emergencyTeleportId"));
            assertEquals("25", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "emergencyAt"));
        }
    }

    @Test
    public void removedBankSettingsAreIgnoredWhileSavedOutboundTravelRemains() throws Exception
    {
        for (String saved : new String[] {"WALK", "RING_OF_DUELING", "RING_OF_DUELING_FEROX", "RING_OF_WEALTH", "GAMES_NECKLACE", "AMULET_OF_GLORY"})
        {
            try (Fixture fixture = new Fixture())
            {
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "bankTravelMethod", saved);
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "bankX", "3510");
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "bankY", "3480");
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "bankPlane", "3");
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "travelMethod", "MORYTANIA_LEGS_4");
                fixture.manager.setDefaultConfiguration(fixture.config, false);
                assertEquals(TravelMethod.MORYTANIA_LEGS_4, fixture.config.travelMethod());
                assertEquals(BankTeleport.RING_OF_DUELING, fixture.config.bankTeleport());
                assertEquals(saved, fixture.manager.getConfiguration(BarrowsConfig.GROUP, "bankTravelMethod"));
            }
        }
    }

    @Test
    public void bankTravelDropdownHasOnlyTheRequestedOptionAndPrecedesBarrowsTravel() throws Exception
    {
        List<ConfigSection> sections = Arrays.stream(BarrowsConfig.class.getDeclaredFields())
            .map(field -> field.getAnnotation(ConfigSection.class))
            .filter(section -> section != null)
            .sorted(Comparator.comparingInt(ConfigSection::position))
            .collect(Collectors.toList());
        List<String> names = sections.stream().map(ConfigSection::name).collect(Collectors.toList());
        int bank = names.indexOf("Travel to Bank");
        assertTrue(bank >= 0);
        assertEquals("Travel to Barrows", names.get(bank + 1));
        ConfigItem bankItem = BarrowsConfig.class.getMethod("bankTeleport").getAnnotation(ConfigItem.class);
        ConfigItem outboundItem = BarrowsConfig.class.getMethod("travelMethod").getAnnotation(ConfigItem.class);
        assertEquals(BarrowsConfig.BANK_TRAVEL, bankItem.section());
        assertEquals("bankTeleport", bankItem.keyName());
        assertEquals(BarrowsConfig.TRAVEL, outboundItem.section());
        assertEquals("travelMethod", outboundItem.keyName());
        assertEquals(BankTeleport.class, BarrowsConfig.class.getMethod("bankTeleport").getReturnType());
        assertEquals(1, BankTeleport.values().length);
        assertEquals("Ring of Dueling", BankTeleport.RING_OF_DUELING.toString());
        ConfigItem supplies = BarrowsConfig.class.getMethod("restockItems").getAnnotation(ConfigItem.class);
        assertEquals("Additional supplies/ammunition", supplies.name());
        assertEquals("restockItems", supplies.keyName());
    }

    @Test
    public void renamedOutboundLabelsRetainEverySavedEnumChoice() throws Exception
    {
        assertEquals("Shades of Mort'ton", TravelMethod.SHADES_MINIGAME.toString());
        assertEquals("travel", BarrowsConfig.TRAVEL);
        for (TravelMethod method : TravelMethod.values())
        {
            try (Fixture fixture = new Fixture())
            {
                fixture.manager.setConfiguration(BarrowsConfig.GROUP, "travelMethod", method.name());
                fixture.manager.setDefaultConfiguration(fixture.config, false);
                assertEquals(method, fixture.config.travelMethod());
                assertEquals(method.name(), fixture.manager.getConfiguration(BarrowsConfig.GROUP, "travelMethod"));
                assertEquals(BankTeleport.RING_OF_DUELING, fixture.config.bankTeleport());
            }
        }
    }

    @Test
    public void emergencyControlsUseRequestedLabelsWithTheirOriginalKeysAndOrder() throws Exception
    {
        ConfigSection section = BarrowsConfig.class.getField("SAFETY").getAnnotation(ConfigSection.class);
        assertEquals("Emergency teleport", section.name());
        assertEquals("safety", BarrowsConfig.SAFETY);
        assertEquals(60, section.position());
        ConfigItem item = BarrowsConfig.class.getMethod("emergencyTeleport").getAnnotation(ConfigItem.class);
        assertEquals("Teleport", item.name());
        assertEquals("emergencyTeleport", item.keyName());
        assertEquals(EmergencyTeleport.class, BarrowsConfig.class.getMethod("emergencyTeleport").getReturnType());
        assertArrayEquals(new EmergencyTeleport[] {EmergencyTeleport.RING_OF_DUELING}, EmergencyTeleport.values());
        assertEquals(BarrowsConfig.SAFETY, item.section());
        assertEquals(0, item.position());
        ConfigItem threshold = BarrowsConfig.class.getMethod("emergencyAt").getAnnotation(ConfigItem.class);
        assertEquals("At or below HP (%)", threshold.name());
        assertEquals("emergencyAt", threshold.keyName());
        assertEquals(BarrowsConfig.SAFETY, threshold.section());
        assertEquals(1, threshold.position());
    }

    @Test
    public void loadingDefaultsPreservesSavedSettings() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "foodId", "391");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "rewardTarget", "800");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "travelMethod", "MORYTANIA_LEGS_4");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "tickDelayMin", "2");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "tickDelayWeightedDistribution", "true");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "restockItems", "556:1000,882:200");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "emergencyTeleport", "RING_OF_DUELING");
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "emergencyAt", "17");
            fixture.manager.setDefaultConfiguration(fixture.config, false);
            assertEquals(391, fixture.config.foodId());
            assertEquals(800, fixture.config.rewardTarget());
            assertEquals(TravelMethod.MORYTANIA_LEGS_4, fixture.config.travelMethod());
            assertEquals(2, fixture.config.tickDelayMin());
            assertTrue(fixture.config.tickDelayWeightedDistribution());
            assertEquals("556:1000,882:200", fixture.config.restockItems());
            assertEquals(EmergencyTeleport.RING_OF_DUELING, fixture.config.emergencyTeleport());
            assertEquals(17, fixture.config.emergencyAt());
        }
    }

    @Test
    public void loadingDefaultsRetainsThePreviouslySavedRewardTarget() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.manager.setConfiguration(BarrowsConfig.GROUP, "rewardTarget", "756");
            fixture.manager.setDefaultConfiguration(fixture.config, false);
            assertEquals(756, fixture.config.rewardTarget());
            assertEquals("756", fixture.manager.getConfiguration(BarrowsConfig.GROUP, "rewardTarget"));
        }
    }

    private static final class Fixture implements AutoCloseable
    {
        private final Path directory = Files.createTempDirectory("fourtifys-barrows-config-");
        private final ConfigManager manager;
        private final BarrowsConfig config;

        private Fixture() throws Exception
        {
            manager = NativeConfigTestSupport.create(directory.resolve("settings.properties").toFile());
            config = manager.getConfig(BarrowsConfig.class);
        }

        @Override
        public void close() throws Exception
        {
            Files.delete(directory);
        }
    }
}
