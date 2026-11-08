package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config;

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
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.NativeConfigTestSupport;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.config.TimingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhosanisNightmareConfigInitializationTest
{
    private static final String MELEE = "24419,29801,21295,24420,24421,24417,22322,22947,22981,31097,28307";
    private static final String MAGIC = "21018,12002,21791,21021,21024,27275,31106,28313";
    private static final String ALWAYS = "Slepey tablet, Parasitic egg, Jar of dreams, Little nightmare, Nightmare staff, "
        + "Inquisitor's great helm, Inquisitor's hauberk, Inquisitor's plateskirt, Inquisitor's mace, Eldritch orb, "
        + "Harmonised orb, Volatile orb";

    @Test
    public void loadingDefaultsPersistsTheNativeSettingsValues() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            assertEquals("fourtifyPhosanisNightmare", PhosanisNightmareConfig.GROUP);
            assertNull(fixture.manager.getConfiguration(PhosanisNightmareConfig.GROUP, "eatAt"));
            assertEquals(70, fixture.config.eatAt());
            assertNull(fixture.manager.getConfiguration(PhosanisNightmareConfig.GROUP, "eatAt"));

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals("true", fixture.value("showOverlay"));
            assertEquals("true", fixture.value("offensivePrayers"));
            assertEquals("false", fixture.value("ghostlyThrall"));
            assertEquals("false", fixture.value("deathCharge"));
            assertEquals("true", fixture.value("redemption"));
            assertEquals(MELEE, fixture.value("meleeGear"));
            assertEquals(MAGIC, fixture.value("magicGear"));
            assertEquals("34027", fixture.value("huskWeapon"));
            assertEquals("34027", fixture.value("parasiteWeapon"));
            assertEquals("33716", fixture.value("sleepwalkerWeapon"));
            assertEquals("33631", fixture.value("specWeapon"));
            assertEquals("FINAL_PHASE", fixture.value("specTiming"));
            assertEquals("13441", fixture.value("foodId"));
            assertEquals("8", fixture.value("foodCount"));
            assertEquals("70", fixture.value("eatAt"));
            assertEquals("23685", fixture.value("combatPotionId"));
            assertEquals("16", fixture.value("restoreDoses"));
            assertEquals("30", fixture.value("drinkAt"));
            assertEquals("SANFEW_SERUM", fixture.value("parasiteCure"));
            assertEquals("8", fixture.value("cureDoses"));
            assertEquals("4", fixture.value("nextKillFood"));
            assertEquals("4", fixture.value("nextKillRestores"));
            assertEquals("4", fixture.value("nextKillCures"));
            assertEquals("5000", fixture.value("lootMinimum"));
            assertEquals(ALWAYS, fixture.value("lootAlways"));
            assertEquals("0", fixture.value("tickDelayMin"));
            assertEquals("1", fixture.value("tickDelayMax"));
            assertEquals("1", fixture.value("tickDelayTarget"));
            assertEquals("0", fixture.value("tickDelayDeviation"));
            assertEquals("false", fixture.value("tickDelayWeightedDistribution"));
            assertEquals(SpecTiming.FINAL_PHASE, comboBox(fixture, "specTiming").getSelectedItem());
        }
    }

    @Test
    public void loadingDefaultsPreservesSavedValues() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.save("specTiming", "ANY_PHASE");
            fixture.save("offensivePrayers", "false");
            fixture.save("magicGear", "4675,12002");
            fixture.save("foodId", "391");
            fixture.save("eatAt", "60");
            fixture.save("parasiteCure", "RELICYMS_BALM");
            fixture.save("lootAlways", "Rune bar");
            fixture.save("showOverlay", "false");
            fixture.save("tickDelayMax", "8");

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals(SpecTiming.ANY_PHASE, fixture.config.specTiming());
            assertFalse(fixture.config.offensivePrayers());
            assertEquals("4675,12002", fixture.config.magicGear());
            assertEquals(391, fixture.config.foodId());
            assertEquals(60, fixture.config.eatAt());
            assertEquals(ParasiteCure.RELICYMS_BALM, fixture.config.parasiteCure());
            assertEquals("Rune bar", fixture.config.lootAlways());
            assertFalse(fixture.config.showOverlay());
            assertEquals(8, fixture.config.tickDelayMax());
            assertEquals("ANY_PHASE", fixture.value("specTiming"));
            assertEquals(SpecTiming.ANY_PHASE, comboBox(fixture, "specTiming").getSelectedItem());
        }
    }

    @Test
    public void choiceLabelsAreShortSentences()
    {
        assertEquals("Final phase", SpecTiming.FINAL_PHASE.toString());
        assertEquals("Any phase", SpecTiming.ANY_PHASE.toString());
        assertEquals("Sanfew serum", ParasiteCure.SANFEW_SERUM.toString());
        assertEquals("Relicym's balm", ParasiteCure.RELICYMS_BALM.toString());
    }

    @Test
    public void timingControlsDelegateToTheSharedDefaults()
    {
        PhosanisNightmareConfig config = new PhosanisNightmareConfig() { };
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
    public void timingControlsKeepTheStandardKeysSectionsOrderAndRanges() throws Exception
    {
        String[] keys = {"tickDelayMin", "tickDelayMax", "tickDelayTarget", "tickDelayDeviation", "tickDelayWeightedDistribution"};
        String[] names = {"Minimum delay (ticks)", "Maximum delay (ticks)", "Target delay (ticks)", "Delay variation (ticks)", "Weighted distribution"};
        for (int index = 0; index < keys.length; index++)
        {
            Method method = PhosanisNightmareConfig.class.getMethod(keys[index]);
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            assertEquals(keys[index], item.keyName());
            assertEquals(names[index], item.name());
            assertEquals(index, item.position());
            assertEquals(PhosanisNightmareConfig.TIMING, item.section());
            if (index < 4)
            {
                Range range = method.getAnnotation(Range.class);
                assertEquals(0, range.min());
                assertEquals(10, range.max());
            }
        }
    }

    @Test
    public void sectionsAreOrderedWithTimingCollapsedAndGeneralOpen() throws Exception
    {
        ConfigSection timing = (ConfigSection) annotation("TIMING");
        ConfigSection general = (ConfigSection) annotation("GENERAL");
        assertEquals(80, timing.position());
        assertTrue(timing.closedByDefault());
        assertEquals(20, general.position());
        assertFalse(general.closedByDefault());
        ConfigItem overlay = PhosanisNightmareConfig.class.getMethod("showOverlay").getAnnotation(ConfigItem.class);
        assertEquals("", overlay.section());
        ConfigItem redemption = PhosanisNightmareConfig.class.getMethod("redemption").getAnnotation(ConfigItem.class);
        assertEquals(PhosanisNightmareConfig.GENERAL, redemption.section());
        assertEquals("Redemption", redemption.name());
    }

    private static Object annotation(String field) throws Exception
    {
        return PhosanisNightmareConfig.class.getField(field).getAnnotation(ConfigSection.class);
    }

    private static JComboBox<?> comboBox(Fixture fixture, String key) throws Exception
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
                    .filter(candidate -> key.equals(candidate.getItem().keyName())).findFirst().orElseThrow();
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
        private final Path directory = Files.createTempDirectory("phosanis-nightmare-config-");
        private final ConfigManager manager;
        private final PhosanisNightmareConfig config;

        private Fixture() throws Exception
        {
            manager = NativeConfigTestSupport.create(directory.resolve("settings.properties").toFile());
            config = manager.getConfig(PhosanisNightmareConfig.class);
        }

        private String value(String key)
        {
            return manager.getConfiguration(PhosanisNightmareConfig.GROUP, key);
        }

        private void save(String key, String value)
        {
            manager.setConfiguration(PhosanisNightmareConfig.GROUP, key, value);
        }

        @Override
        public void close() throws Exception
        {
            Files.delete(directory);
        }
    }
}
