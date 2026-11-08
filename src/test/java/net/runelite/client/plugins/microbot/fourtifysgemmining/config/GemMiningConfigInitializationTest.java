package net.runelite.client.plugins.microbot.fourtifysgemmining.config;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GemMiningConfigInitializationTest
{
    @Test
    public void defaultsArePersistedForTheNativeEnumControl() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            assertEquals("fourtifyGemMining", GemMiningConfig.GROUP);
            assertNull(fixture.manager.getConfiguration(GemMiningConfig.GROUP, "mine"));
            assertEquals(GemMine.SURFACE, fixture.config.mine());
            assertNull(fixture.manager.getConfiguration(GemMiningConfig.GROUP, "mine"));

            // Without a saved value, Microbot 2.6.43's settings panel fails on the enum control and 2.6.52 builds it.
            // Either way the plugin saves its defaults, so the control has a value on every supported client.
            try
            {
                nativeMineComboBox(fixture);
            }
            catch (InvocationTargetException error)
            {
                assertTrue(error.getCause() instanceof NullPointerException);
                assertEquals("Name is null", error.getCause().getMessage());
            }

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals("SURFACE", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "mine"));
            assertEquals("0", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayMin"));
            assertEquals("1", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayMax"));
            assertEquals("1", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayTarget"));
            assertEquals("0", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayDeviation"));
            assertEquals("false", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayWeightedDistribution"));
            assertEquals("true", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "showOverlay"));
            assertEquals(GemMine.SURFACE, nativeMineComboBox(fixture).getSelectedItem());
        }
    }

    @Test
    public void initializingDefaultsPreservesSavedMineAndTimingSettings() throws Exception
    {
        try (Fixture fixture = new Fixture())
        {
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "mine", "UNDERGROUND");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "tickDelayMin", "1");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "tickDelayMax", "8");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "tickDelayTarget", "4");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "tickDelayDeviation", "2");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "tickDelayWeightedDistribution", "true");
            fixture.manager.setConfiguration(GemMiningConfig.GROUP, "showOverlay", "false");

            fixture.manager.setDefaultConfiguration(fixture.config, false);

            assertEquals("UNDERGROUND", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "mine"));
            assertEquals("1", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayMin"));
            assertEquals("8", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayMax"));
            assertEquals("4", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayTarget"));
            assertEquals("2", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayDeviation"));
            assertEquals("true", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "tickDelayWeightedDistribution"));
            assertEquals("false", fixture.manager.getConfiguration(GemMiningConfig.GROUP, "showOverlay"));
            assertEquals(GemMine.UNDERGROUND, fixture.config.mine());
            assertEquals(1, fixture.config.tickDelayMin());
            assertEquals(8, fixture.config.tickDelayMax());
            assertEquals(4, fixture.config.tickDelayTarget());
            assertEquals(2, fixture.config.tickDelayDeviation());
            assertTrue(fixture.config.tickDelayWeightedDistribution());
            assertEquals(GemMine.UNDERGROUND, nativeMineComboBox(fixture).getSelectedItem());
        }
    }

    private static JComboBox<?> nativeMineComboBox(Fixture fixture) throws Exception
    {
        AtomicReference<JComboBox<?>> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                Class<?> panelClass = Class.forName("net.runelite.client.plugins.config.ConfigPanel");
                Constructor<?> constructor = panelClass.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                Object[] arguments = new Object[constructor.getParameterCount()];
                Class<?>[] types = constructor.getParameterTypes();
                for (int i = 0; i < types.length; i++)
                {
                    if (types[i] == ConfigManager.class)
                    {
                        arguments[i] = fixture.manager;
                    }
                }
                Object panel = constructor.newInstance(arguments);
                ConfigDescriptor descriptor = fixture.manager.getConfigDescriptor(fixture.config);
                ConfigItemDescriptor item = descriptor.getItems().stream()
                    .filter(candidate -> "mine".equals(candidate.getItem().keyName())).findFirst().orElseThrow();
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
        private final Path directory = Files.createTempDirectory("gem-mining-config-test-");
        private final ConfigManager manager;
        private final GemMiningConfig config;

        private Fixture() throws Exception
        {
            ScheduledExecutorService scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
                ScheduledExecutorService.class.getClassLoader(), new Class<?>[] {ScheduledExecutorService.class},
                (proxy, method, arguments) -> null);
            Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++)
            {
                if (types[i] == ScheduledExecutorService.class)
                {
                    arguments[i] = scheduler;
                }
                else if (types[i] == EventBus.class)
                {
                    arguments[i] = new EventBus();
                }
                else if (types[i] == Gson.class)
                {
                    arguments[i] = new Gson();
                }
            }
            manager = (ConfigManager) constructor.newInstance(arguments);
            Class<?> dataClass = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataClass.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            Object data = dataConstructor.newInstance(directory.resolve("settings.properties").toFile());
            Field configProfile = ConfigManager.class.getDeclaredField("configProfile");
            configProfile.setAccessible(true);
            configProfile.set(manager, data);
            config = manager.getConfig(GemMiningConfig.class);
        }

        @Override
        public void close() throws Exception
        {
            Files.delete(directory);
        }
    }
}
