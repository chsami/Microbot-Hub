package net.runelite.client.plugins.microbot.fourtifystormenteddemons.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.NativeConfigTestSupport;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.config.TimingConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonRoom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TormentedDemonsConfigInitializationTest
{
    @Test
    public void loadingDefaultsPersistsTheNativeSettingsValues() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            assertEquals("fourtifyTormentedDemons", TormentedDemonsConfig.GROUP);
            assertNull(fixture.manager.getConfiguration(TormentedDemonsConfig.GROUP, "eatAt"));
            assertEquals(55, fixture.config.eatAt());
            assertNull(fixture.manager.getConfiguration(TormentedDemonsConfig.GROUP, "eatAt"));

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals("true", fixture.value("showOverlay"));
            assertEquals("PLINTH_CHAMBER", fixture.value("room"));
            assertEquals("false", fixture.value("offensivePrayer"));
            assertEquals("11936", fixture.value("foodId"));
            assertEquals("12", fixture.value("foodCount"));
            assertEquals("55", fixture.value("eatAt"));
            assertEquals("4", fixture.value("restoreCount"));
            assertEquals("20", fixture.value("drinkAt"));
            assertEquals("5", fixture.value("scrollCount"));
            assertEquals("5000", fixture.value("lootMinimum"));
            assertEquals("Tormented synapse, Burning claw, Guthixian temple teleport", fixture.value("lootAlways"));
            assertEquals("25", fixture.value("emergencyAt"));
            assertEquals("0", fixture.value("tickDelayMin"));
            assertEquals("1", fixture.value("tickDelayMax"));
            assertEquals("1", fixture.value("tickDelayTarget"));
            assertEquals("0", fixture.value("tickDelayDeviation"));
            assertEquals("false", fixture.value("tickDelayWeightedDistribution"));
            assertEquals(DemonRoom.PLINTH_CHAMBER, roomComboBox(fixture).getSelectedItem());
        }
    }

    @Test
    public void loadingDefaultsPreservesSavedValues() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.save("room", "WEST_NORTH");
            fixture.save("offensivePrayer", "true");
            fixture.save("magicGear", "4675,12002");
            fixture.save("foodId", "391");
            fixture.save("foodCount", "20");
            fixture.save("eatAt", "70");
            fixture.save("restoreCount", "6");
            fixture.save("drinkAt", "35");
            fixture.save("scrollCount", "9");
            fixture.save("lootMinimum", "100000");
            fixture.save("lootAlways", "Rune bar");
            fixture.save("emergencyAt", "40");
            fixture.save("showOverlay", "false");
            fixture.save("tickDelayMin", "0");
            fixture.save("tickDelayMax", "8");
            fixture.save("tickDelayTarget", "4");
            fixture.save("tickDelayDeviation", "2");
            fixture.save("tickDelayWeightedDistribution", "true");

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals(DemonRoom.WEST_NORTH, fixture.config.room());
            assertTrue(fixture.config.offensivePrayer());
            assertEquals("4675,12002", fixture.config.magicGear());
            assertEquals(391, fixture.config.foodId());
            assertEquals(20, fixture.config.foodCount());
            assertEquals(70, fixture.config.eatAt());
            assertEquals(6, fixture.config.restoreCount());
            assertEquals(35, fixture.config.drinkAt());
            assertEquals(9, fixture.config.scrollCount());
            assertEquals(100000, fixture.config.lootMinimum());
            assertEquals("Rune bar", fixture.config.lootAlways());
            assertEquals(40, fixture.config.emergencyAt());
            assertFalse(fixture.config.showOverlay());
            assertEquals(0, fixture.config.tickDelayMin());
            assertEquals(8, fixture.config.tickDelayMax());
            assertEquals(4, fixture.config.tickDelayTarget());
            assertEquals(2, fixture.config.tickDelayDeviation());
            assertTrue(fixture.config.tickDelayWeightedDistribution());
            assertEquals("40", fixture.value("emergencyAt"));
            assertEquals("WEST_NORTH", fixture.value("room"));
            assertEquals(DemonRoom.WEST_NORTH, roomComboBox(fixture).getSelectedItem());
        }
    }

    @Test
    public void timingControlsDelegateToTheSharedDefaults()
    {
        TormentedDemonsConfig config = new TormentedDemonsConfig() { };
        TimingConfig shared = new TimingConfig() { };
        assertEquals(0, config.tickDelayMin());
        assertEquals(1, config.tickDelayMax());
        assertEquals(1, config.tickDelayTarget());
        assertEquals(0, config.tickDelayDeviation());
        assertFalse(config.tickDelayWeightedDistribution());
        assertEquals(shared.tickDelayMin(), config.tickDelayMin());
        assertEquals(shared.tickDelayMax(), config.tickDelayMax());
        assertEquals(shared.tickDelayTarget(), config.tickDelayTarget());
        assertEquals(shared.tickDelayDeviation(), config.tickDelayDeviation());
        assertEquals(shared.tickDelayWeightedDistribution(), config.tickDelayWeightedDistribution());
    }

    @Test
    public void thresholdDefaultsAreTheDocumentedPercentages()
    {
        TormentedDemonsConfig config = new TormentedDemonsConfig() { };
        assertEquals(55, config.eatAt());
        assertEquals(20, config.drinkAt());
        assertEquals(25, config.emergencyAt());
        assertEquals(DemonRoom.PLINTH_CHAMBER, config.room());
        assertFalse(config.offensivePrayer());
        assertEquals("", config.magicGear());
        assertEquals("", config.rangedGear());
        assertEquals("", config.meleeGear());
        assertEquals(11936, config.foodId());
    }

    @Test
    public void timingControlsKeepTheStandardKeysSectionsOrderAndRanges() throws Exception
    {
        String[] keys = {"tickDelayMin", "tickDelayMax", "tickDelayTarget", "tickDelayDeviation", "tickDelayWeightedDistribution"};
        String[] names = {"Minimum delay (ticks)", "Maximum delay (ticks)", "Target delay (ticks)", "Delay variation (ticks)", "Weighted distribution"};
        for (int index = 0; index < keys.length; index++)
        {
            Method method = TormentedDemonsConfig.class.getMethod(keys[index]);
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertEquals(keys[index], item.keyName());
            assertEquals(names[index], item.name());
            assertEquals(index, item.position());
            assertEquals(TormentedDemonsConfig.TIMING, item.section());
            if (index < 4)
            {
                Range range = method.getAnnotation(Range.class);
                assertEquals(0, range.min());
                assertEquals(10, range.max());
            }
        }
    }

    @Test
    public void sectionsAreOrderedWithTimingCollapsedAndGeneralAndEquipmentOpen() throws Exception
    {
        ConfigSection timing = (ConfigSection) annotation("TIMING");
        ConfigSection general = (ConfigSection) annotation("GENERAL");
        assertEquals(80, timing.position());
        assertTrue(timing.closedByDefault());
        assertEquals(20, general.position());
        assertFalse(general.closedByDefault());
        assertFalse(((ConfigSection) annotation("EQUIPMENT")).closedByDefault(), "the required equipment IDs are shown");
        ConfigItem overlay = TormentedDemonsConfig.class.getMethod("showOverlay").getAnnotation(ConfigItem.class);
        assertEquals("", overlay.section());
        ConfigItem emergency = TormentedDemonsConfig.class.getMethod("emergencyAt").getAnnotation(ConfigItem.class);
        assertEquals(TormentedDemonsConfig.SAFETY, emergency.section());
        assertEquals("At or below HP (%)", emergency.name());
    }

    private static Object annotation(String field) throws Exception
    {
        return TormentedDemonsConfig.class.getField(field).getAnnotation(ConfigSection.class);
    }

    private static JComboBox<?> roomComboBox(Fixture fixture) throws Exception
    {
        AtomicReference<JComboBox<?>> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                Class<?> panelClass = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
                Constructor<?> constructor = panelClass.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                Object[] arguments = new Object[constructor.getParameterCount()];
                Class<?>[] types = constructor.getParameterTypes();
                for (int index = 0; index < types.length; index++)
                {
                    if (types[index] == ConfigManager.class)
                    {
                        arguments[index] = fixture.manager;
                    }
                }
                Object panel = constructor.newInstance(arguments);
                ConfigDescriptor descriptor = fixture.manager.getConfigDescriptor(fixture.config);
                ConfigItemDescriptor item = descriptor.getItems().stream()
                    .filter(candidate -> "room".equals(candidate.getItem().keyName())).findFirst().orElseThrow();
                Method createComboBox = panelClass.getDeclaredMethod("createComboBox", ConfigDescriptor.class, ConfigItemDescriptor.class);
                createComboBox.setAccessible(true);
                result.set((JComboBox<?>) createComboBox.invoke(panel, descriptor, item));
            }
            catch (Exception error)
            {
                failure.set(error);
            }
        });
        if (failure.get() != null)
        {
            throw failure.get();
        }
        return result.get();
    }

    private static final class Fixture implements AutoCloseable
    {
        private final Path directory = Files.createTempDirectory("tormented-demons-config-");
        private final ConfigManager manager;
        private final TormentedDemonsConfig config;

        private Fixture() throws Exception
        {
            manager = NativeConfigTestSupport.create(directory.resolve("settings.properties").toFile());
            config = manager.getConfig(TormentedDemonsConfig.class);
        }

        private String value(String key)
        {
            return manager.getConfiguration(TormentedDemonsConfig.GROUP, key);
        }

        private void save(String key, String value)
        {
            manager.setConfiguration(TormentedDemonsConfig.GROUP, key, value);
        }

        @Override
        public void close() throws Exception
        {
            Files.delete(directory);
        }
    }
}
