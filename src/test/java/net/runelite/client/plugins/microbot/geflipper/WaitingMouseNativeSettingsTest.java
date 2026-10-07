package net.runelite.client.plugins.microbot.geflipper;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseListener;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.ui.ColorScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the SDK's real config proxy, descriptor, init, row builders, search and per-item Reset. */
class WaitingMouseNativeSettingsTest {
    private static final String GROUP = "Flipper Config";
    private static final String FREQUENCY = GROUP + ".waitingMouseChance";
    private static final String ENABLED = GROUP + ".waitingMouseOffScreen";
    @TempDir Path temporary;

    @Test
    void sdkGeneratedIntegerRowGetsAPlainSliderWithoutWritingPreferences() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                JPanel row = fixture.row("waitingMouseChance");
                JLabel label = fixture.label(row);
                JPopupMenu resetMenu = fixture.resetMenu(label);
                JSpinner nativeSpinner = (JSpinner) fixture.east(row);
                SpinnerNumberModel model = (SpinnerNumberModel) nativeSpinner.getModel();
                assertEquals(0, model.getMinimum());
                assertEquals(100, model.getMaximum());
                assertEquals(47, model.getValue());
                assertEquals("Randomization", label.getText());
                assertNotNull(resetMenu);
                assertTrue(fixture.index().containsKey(row));
                Map<String, String> before = fixture.preferences();

                fixture.settings.start();
                JSlider slider = fixture.slider();
                assertEquals(0, slider.getMinimum());
                assertEquals(100, slider.getMaximum());
                assertEquals(47, slider.getValue());
                assertFalse(slider.getPaintLabels());
                assertFalse(slider.getPaintTicks());
                assertNull(slider.getLabelTable());
                assertNull(fixture.east(row), "The native percentage/number control must be removed");
                assertSame(label, fixture.label(row));
                assertSame(resetMenu, fixture.resetMenu(label));
                assertTrue(fixture.index().containsKey(row), "Native search keeps the actual SDK row");
                fixture.settings.refresh();
                assertEquals(before, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "Attaching or refreshing UI must not write config");

                fixture.settings.close();
                assertSame(nativeSpinner, fixture.east(row));
                assertEquals(47, nativeSpinner.getValue());
                assertTrue(fixture.sliders().isEmpty());
                assertEquals(before, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "Restoring native listeners must not save anything");
            }
        });
    }

    @Test
    void realCheckboxDimsAndRestoresSliderAndOnlyUserChangesSaveOwnedKeys() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JSlider[] slider = new JSlider[1];
        JCheckBox[] checkbox = new JCheckBox[1];
        Map<String, String> expected = new HashMap<>();
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].settings.start();
                slider[0] = fixture[0].slider();
                checkbox[0] = (JCheckBox) fixture[0].east(fixture[0].row("waitingMouseOffScreen"));
                expected.putAll(fixture[0].preferences());
                assertTrue(checkbox[0].isSelected());
                assertTrue(slider[0].isEnabled());
                slider[0].setValueIsAdjusting(true);
                slider[0].setValue(90);
                checkbox[0].doClick();
                expected.put(ENABLED, "false");
            });
            flushEdt();
            onEdt(() -> {
                assertEquals(expected, fixture[0].preferences());
                assertFalse(slider[0].isEnabled());
                assertFalse(slider[0].getValueIsAdjusting());
                assertEquals(47, slider[0].getValue(), "Disabling must discard the uncommitted drag");
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, slider[0].getForeground());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR,
                    fixture[0].label(fixture[0].row("waitingMouseChance")).getForeground());
                slider[0].setValue(5);
                assertEquals(expected, fixture[0].preferences(), "Disabled UI cannot change the retained value");
                checkbox[0].doClick();
                expected.put(ENABLED, "true");
            });
            flushEdt();
            onEdt(() -> {
                assertTrue(slider[0].isEnabled());
                assertEquals(47, slider[0].getValue());
                assertEquals(ColorScheme.BRAND_ORANGE, slider[0].getForeground());
                assertEquals(expected, fixture[0].preferences());
                slider[0].setValueIsAdjusting(true);
                slider[0].setValue(65);
                slider[0].setValue(69);
                assertEquals(expected, fixture[0].preferences(), "Intermediate drag positions must not save");
                slider[0].setValueIsAdjusting(false);
                expected.put(FREQUENCY, "69");
                assertEquals(expected, fixture[0].preferences());
                slider[0].setValue(0);
                expected.put(FREQUENCY, "0");
                assertEquals(expected, fixture[0].preferences());
                assertEquals(2, fixture[0].patches().size());
                assertEquals("true", fixture[0].patches().get(ENABLED));
                assertEquals("0", fixture[0].patches().get(FREQUENCY));

                fixture[0].settings.close();
                JSpinner restored = (JSpinner) fixture[0].east(fixture[0].row("waitingMouseChance"));
                assertEquals(0, restored.getValue());
                assertEquals(expected, fixture[0].preferences());
                restored.setValue(12);
                expected.put(FREQUENCY, "12");
                assertEquals(expected, fixture[0].preferences(), "The SDK's original save listener is restored");
            });
        } finally {
            onEdt(() -> { if (fixture[0] != null) fixture[0].close(); });
        }
    }

    @Test
    void nativeSearchHidesAndRestoresRowsWithoutSavingAStaleDrag() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JSlider[] oldSlider = new JSlider[1];
        Map<String, String> expected = new HashMap<>();
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].settings.start();
                expected.putAll(fixture[0].preferences());
                oldSlider[0] = fixture[0].slider();
                oldSlider[0].setValueIsAdjusting(true);
                oldSlider[0].setValue(90);
                fixture[0].search().setText("Verbose");
            });
            flushEdt();
            onEdt(() -> {
                assertFalse(fixture[0].row("waitingMouseChance").isVisible());
                oldSlider[0].setValueIsAdjusting(false);
                oldSlider[0].setValue(0);
                assertEquals(expected, fixture[0].preferences());
                assertFalse(oldSlider[0].isEnabled());
                fixture[0].search().setText("Randomization");
            });
            flushEdt();
            onEdt(() -> {
                JPanel row = fixture[0].row("waitingMouseChance");
                assertTrue(row.isVisible());
                JSlider restored = fixture[0].slider();
                assertNotSame(oldSlider[0], restored);
                assertEquals(47, restored.getValue());
                assertEquals(expected, fixture[0].preferences());
                restored.setValue(63);
                expected.put(FREQUENCY, "63");
                assertEquals(expected, fixture[0].preferences());
            });
        } finally {
            onEdt(() -> { if (fixture[0] != null) fixture[0].close(); });
        }
    }

    @Test
    void genuineRowResetRebuildsNativeRowsAndInvalidatesTheDetachedSlider() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JSlider[] oldSlider = new JSlider[1];
        JPanel[] oldRow = new JPanel[1];
        Map<String, String> expected = new HashMap<>();
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].settings.start();
                expected.putAll(fixture[0].preferences());
                oldSlider[0] = fixture[0].slider();
                oldRow[0] = fixture[0].row("waitingMouseChance");
                oldSlider[0].setValueIsAdjusting(true);
                oldSlider[0].setValue(90);
                JPopupMenu popup = fixture[0].resetMenu(fixture[0].label(oldRow[0]));
                JMenuItem reset = null;
                for (Component item : popup.getComponents()) {
                    if (item instanceof JMenuItem && "Reset".equals(((JMenuItem) item).getText())) {
                        reset = (JMenuItem) item;
                    }
                }
                assertNotNull(reset, "Use the SDK-created Reset action, not a test substitute");
                reset.doClick();
                expected.put(FREQUENCY, "30");
            });
            flushEdt();
            onEdt(() -> {
                assertNotSame(oldRow[0], fixture[0].row("waitingMouseChance"));
                assertFalse(SwingUtilities.isDescendingFrom(oldRow[0], fixture[0].panel));
                assertEquals(expected, fixture[0].preferences(), "Reset must preserve unrelated/shared settings");
                JSlider current = fixture[0].slider();
                assertNotSame(oldSlider[0], current);
                assertEquals(30, current.getValue());
                oldSlider[0].setValueIsAdjusting(false);
                oldSlider[0].setValue(100);
                assertEquals(expected, fixture[0].preferences());
                current.setValue(61);
                expected.put(FREQUENCY, "61");
                assertEquals(expected, fixture[0].preferences());
            });
        } finally {
            onEdt(() -> { if (fixture[0] != null) fixture[0].close(); });
        }
    }

    @Test
    void nativeFinishButtonCallsOnceWithoutPersistingACommandAndRestoresSdkListener() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary, true)) {
                assertNull(fixture.config.finish(), "A command has no persisted default");
                JButton button = fixture.finishButton();
                assertEquals("End / Finish", button.getText());
                java.awt.event.ActionListener[] nativeListeners = button.getActionListeners();
                assertEquals(1, nativeListeners.length, "Use the genuine SDK-created button");
                Map<String, String> before = fixture.preferences();
                fixture.settings.start();
                button.doClick(0);
                assertEquals(1, fixture.finishRequests.get());
                assertFalse(button.isEnabled());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, button.getForeground());
                button.doClick(0);
                fixture.settings.refresh();
                assertEquals(1, fixture.finishRequests.get());
                assertEquals(before, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "Explicit finishing must not store a command UUID");
                fixture.settings.close();
                assertArrayEquals(nativeListeners, button.getActionListeners());
                button.doClick(0);
                assertEquals(1, fixture.finishRequests.get(), "Stopped bindings cannot finish trades");
                assertNotNull(fixture.preferences().get(GROUP + ".finish"), "Native SDK behavior is restored");
            }
        });
    }

    @Test
    void profileDefaultAndNativeResetNeverRequestFinishingAndRebuiltButtonsAreGuarded() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JButton[] stale = new JButton[1];
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary, true);
                fixture[0].settings.start();
                stale[0] = fixture[0].finishButton();
                fixture[0].manager.setConfiguration(GROUP, "finish", "synthetic-profile-command");
                fixture[0].eventBus.post(new ProfileChanged());
                fixture[0].manager.setDefaultConfiguration(fixture[0].config, true);
                JPopupMenu popup = fixture[0].resetMenu(fixture[0].label(fixture[0].row("waitingMouseChance")));
                JMenuItem reset = null;
                for (Component item : popup.getComponents()) {
                    if (item instanceof JMenuItem && "Reset".equals(((JMenuItem) item).getText())) {
                        reset = (JMenuItem) item;
                    }
                }
                assertNotNull(reset);
                reset.doClick(0);
                stale[0].doClick(0);
                assertEquals(0, fixture[0].finishRequests.get(), "Config replay/reset and a detached row cannot command finishing");
            });
            flushEdt();
            onEdt(() -> {
                JButton current = fixture[0].finishButton();
                assertNotSame(stale[0], current);
                assertEquals(0, fixture[0].finishRequests.get());
                assertFalse(fixture[0].preferences().containsKey(GROUP + ".finish"), "Reset retains no stored command");
                fixture[0].patches().clear();
                current.doClick(0);
                assertEquals(1, fixture[0].finishRequests.get());
                assertTrue(fixture[0].patches().isEmpty());
            });
        } finally {
            onEdt(() -> { if (fixture[0] != null) fixture[0].close(); });
        }
    }

    @Test
    void nativeSearchHidingRejectsFinishBeforeScanAndOwnershipRejectsReplayedClick() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JButton[] button = new JButton[1];
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary, true);
                fixture[0].settings.start();
                button[0] = fixture[0].finishButton();
                fixture[0].search().setText("Verbose");
                button[0].doClick(0);
                assertEquals(0, fixture[0].finishRequests.get());
                assertFalse(fixture[0].preferences().containsKey(GROUP + ".finish"));
                fixture[0].search().setText("End / Finish");
            });
            flushEdt();
            onEdt(() -> {
                assertSame(button[0], fixture[0].finishButton());
                assertTrue(button[0].isEnabled());
                field(fixture[0].panel.getClass(), "pluginConfig").set(fixture[0].panel,
                    new MicrobotPluginConfigurationDescriptor("Other", "Synthetic", new String[0],
                        new FlipperPlugin(), fixture[0].config, fixture[0].manager.getConfigDescriptor(fixture[0].config),
                        Collections.emptyList()));
                button[0].doClick(0);
                assertEquals(0, fixture[0].finishRequests.get(), "An identical label belonging to another instance is not a command");
                assertFalse(fixture[0].preferences().containsKey(GROUP + ".finish"));
            });
        } finally {
            onEdt(() -> { if (fixture[0] != null) fixture[0].close(); });
        }
    }

    private static void onEdt(CheckedRunnable task) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { task.run(); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static void flushEdt() throws Exception {
        // Container events and their coalesced scans can enqueue one further UI update.
        onEdt(() -> {});
        onEdt(() -> {});
    }

    private interface CheckedRunnable { void run() throws Exception; }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class ShowingRoot extends JPanel {
        private ShowingRoot() { super(new BorderLayout()); }
        @Override public boolean isShowing() { return isVisible(); }
    }

    private static final class Fixture implements AutoCloseable {
        private final EventBus eventBus = new EventBus();
        private final FlipperPlugin owner = new FlipperPlugin();
        private final ShowingRoot root = new ShowingRoot();
        private final Field profileName = field(ConfigManager.class, "configProfileName");
        private final Object previousProfileName = profileName.get(null);
        private final ConfigManager manager;
        private final FlipperConfig config;
        private final JPanel panel;
        private final Object data;
        private final Map<String, String> properties;
        private final WaitingMouseSettings settings;
        private final AtomicInteger finishRequests = new AtomicInteger();
        private boolean finishAvailable = true;

        private Fixture(Path temporary) throws Exception {
            this(temporary, false);
        }

        @SuppressWarnings("unchecked")
        private Fixture(Path temporary, boolean bindFinish) throws Exception {
            Constructor<?> managerConstructor = ConfigManager.class.getDeclaredConstructors()[0];
            managerConstructor.setAccessible(true);
            manager = (ConfigManager) managerConstructor.newInstance(
                null, inertScheduler(), eventBus, null, null, null, null, null);
            // Supply the SDK serializer directly; this fixture has no live Microbot injector.
            ((Map) field(ConfigManager.class, "serializers").get(manager)).put(
                net.runelite.client.config.ConfigButtonSerializer.class,
                new net.runelite.client.config.ConfigButtonSerializer());
            Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
            Constructor<?> dataConstructor = dataType.getDeclaredConstructor(File.class);
            dataConstructor.setAccessible(true);
            data = dataConstructor.newInstance(temporary.resolve("synthetic.properties").toFile());
            field(ConfigManager.class, "configProfile").set(manager, data);
            properties = (Map<String, String>) field(dataType, "properties").get(data);
            properties.put(GROUP + ".slotActionMode", "MENU_OPTION");
            properties.put(GROUP + ".selectionMethod", "HOTKEY");
            properties.put(GROUP + ".guide", "Synthetic test guide");
            properties.put(GROUP + ".showOverlay", "true");
            properties.put(GROUP + ".verboseLogging", "false");
            properties.put(ENABLED, "true");
            properties.put(FREQUENCY, "47");
            properties.put("runelite.flipperplugin", "true");
            properties.put("flippingcopilot.slotActionSwap", "false");
            properties.put("microbot.enableAutoRunOn", "false");
            properties.put("microbot.useStaminaPotsIfNeeded", "false");
            config = manager.getConfig(FlipperConfig.class);

            Constructor<?> pluginManagerConstructor = PluginManager.class.getDeclaredConstructors()[0];
            pluginManagerConstructor.setAccessible(true);
            Object[] pluginArguments = new Object[pluginManagerConstructor.getParameterCount()];
            Class<?>[] parameterTypes = pluginManagerConstructor.getParameterTypes();
            for (int index = 0; index < parameterTypes.length; index++) {
                if (parameterTypes[index] == boolean.class) pluginArguments[index] = false;
                if (parameterTypes[index] == EventBus.class) pluginArguments[index] = eventBus;
                if (parameterTypes[index] == ConfigManager.class) pluginArguments[index] = manager;
            }
            PluginManager pluginManager = (PluginManager) pluginManagerConstructor.newInstance(pluginArguments);
            Class<?> panelType = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel");
            Constructor<?> panelConstructor = panelType.getDeclaredConstructors()[0];
            panelConstructor.setAccessible(true);
            panel = (JPanel) panelConstructor.newInstance(null, manager, pluginManager, null, null, null);
            root.add(panel, BorderLayout.CENTER);
            Method init = panelType.getDeclaredMethod("init", MicrobotPluginConfigurationDescriptor.class);
            init.setAccessible(true);
            init.invoke(panel, new MicrobotPluginConfigurationDescriptor("Flipper", "Synthetic test", new String[0],
                owner, config, manager.getConfigDescriptor(config), Collections.emptyList()));
            settings = bindFinish ? new WaitingMouseSettings(owner, config, root,
                value -> manager.setConfiguration(GROUP, "waitingMouseChance", value),
                finishRequests::incrementAndGet, () -> finishAvailable)
                : new WaitingMouseSettings(owner, config, root,
                    value -> manager.setConfiguration(GROUP, "waitingMouseChance", value));
            field(FlipperPlugin.class, "config").set(owner, config);
            field(FlipperPlugin.class, "configManager").set(owner, manager);
            field(FlipperPlugin.class, "waitingMouseSettings").set(owner, settings);
            eventBus.register(owner);
        }

        private JPanel row(String methodName) throws Exception {
            String name = FlipperConfig.class.getMethod(methodName).getAnnotation(ConfigItem.class).name();
            for (JPanel row : index().keySet()) {
                Component center = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                if ((center instanceof JLabel && name.equals(((JLabel) center).getText()))
                    || (center instanceof JButton && name.equals(((JButton) center).getText()))) return row;
            }
            throw new AssertionError("SDK did not create config row: " + name);
        }

        private JLabel label(JPanel row) {
            return (JLabel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        }

        private Component east(JPanel row) {
            return ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
        }

        private JButton finishButton() throws Exception {
            return (JButton) ((BorderLayout) row("finish").getLayout()).getLayoutComponent(BorderLayout.CENTER);
        }

        private JPopupMenu resetMenu(JLabel label) throws Exception {
            // The SDK captures the menu in its label MouseListener instead of setComponentPopupMenu.
            // Read that existing menu to exercise its genuine action without showing desktop UI.
            for (MouseListener listener : label.getMouseListeners()) {
                for (Field captured : listener.getClass().getDeclaredFields()) {
                    if (JPopupMenu.class.isAssignableFrom(captured.getType())) {
                        captured.setAccessible(true);
                        Object value = captured.get(listener);
                        if (value instanceof JPopupMenu) return (JPopupMenu) value;
                    }
                }
            }
            throw new AssertionError("SDK label has no captured settings popup menu");
        }

        private Map<String, String> preferences() { return new HashMap<>(properties); }

        @SuppressWarnings("unchecked") private Map<String, String> patches() throws Exception {
            return (Map<String, String>) field(data.getClass(), "patchChanges").get(data);
        }

        @SuppressWarnings("unchecked") private Map<JPanel, String> index() throws Exception {
            return (Map<JPanel, String>) field(panel.getClass(), "itemIndex").get(panel);
        }

        private JTextField search() throws Exception {
            return (JTextField) field(panel.getClass(), "searchField").get(panel);
        }

        private JSlider slider() {
            List<JSlider> found = sliders();
            assertEquals(1, found.size());
            return found.get(0);
        }

        private List<JSlider> sliders() {
            List<JSlider> found = new ArrayList<>();
            findSliders(root, found);
            return found;
        }

        @Override public void close() throws Exception {
            eventBus.unregister(owner);
            field(FlipperPlugin.class, "waitingMouseSettings").set(owner, null);
            settings.close();
            profileName.set(null, previousProfileName);
        }
    }

    private static void findSliders(Container root, List<JSlider> found) {
        for (Component component : root.getComponents()) {
            if (component instanceof JSlider) found.add((JSlider) component);
            if (component instanceof Container) findSliders((Container) component, found);
        }
    }

    private static ScheduledExecutorService inertScheduler() {
        return (ScheduledExecutorService) Proxy.newProxyInstance(ScheduledExecutorService.class.getClassLoader(),
            new Class<?>[]{ScheduledExecutorService.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("schedule") || method.getName().equals("submit")) {
                    return Proxy.newProxyInstance(ScheduledFuture.class.getClassLoader(),
                        new Class<?>[]{ScheduledFuture.class}, (future, futureMethod, futureArgs) -> {
                            if (futureMethod.getReturnType() == boolean.class) return false;
                            if (futureMethod.getName().equals("getDelay")) return 0L;
                            if (futureMethod.getName().equals("compareTo")) return 0;
                            return null;
                        });
                }
                if (method.getReturnType() == boolean.class) return false;
                if (method.getName().equals("shutdownNow")) return Collections.emptyList();
                return null;
            });
    }
}
