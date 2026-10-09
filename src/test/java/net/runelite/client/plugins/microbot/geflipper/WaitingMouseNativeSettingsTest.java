package net.runelite.client.plugins.microbot.geflipper;

import com.formdev.flatlaf.ui.FlatSliderUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.text.JTextComponent;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the SDK's real config proxy, descriptor, init, row builders, search and per-item Reset. */
class WaitingMouseNativeSettingsTest {
    private static final String GROUP = "Flipper Config";
    private static final String FREQUENCY = GROUP + ".waitingMouseChance";
    private static final String ENABLED = GROUP + ".waitingMouseOffScreen";
    private static final String MOUSE_SPEED = GROUP + ".randomizeMouseSpeed";
    private static final String TIME_SOURCE = GROUP + ".waitingMouseTimeSource";
    private static final String TIME = GROUP + ".waitingMouseTime";
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
    void nativeThemeTrackPageAndArrowMovementsUseOnePercentWithoutChangingGlobalDefaults() throws Exception {
        onEdt(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                UIManager.setLookAndFeel(new RuneLiteLAF());
                Object trackDefault = UIManager.get("Slider.scrollOnTrackClick");
                assertFalse(UIManager.getBoolean("Slider.scrollOnTrackClick"), "Exercise FlatLaf's normal jump-to-track default");
                try (Fixture fixture = new Fixture(temporary)) {
                    fixture.settings.start();
                    JSlider slider = fixture.slider();
                    assertTrue(slider.getUI() instanceof FlatSliderUI, "Keep the client's native slider painting");
                    Map<String, String> expected = fixture.preferences();
                    clickTrack(slider, 90);
                    assertEquals(48, slider.getValue());
                    expected.put(FREQUENCY, "48");
                    assertEquals(expected, fixture.preferences());
                    clickTrack(slider, 5);
                    assertEquals(47, slider.getValue());
                    for (int key : new int[]{KeyEvent.VK_RIGHT, KeyEvent.VK_PAGE_UP}) {
                        int before = slider.getValue();
                        pressKey(slider, key);
                        assertEquals(before + 1, slider.getValue());
                    }
                    for (int key : new int[]{KeyEvent.VK_LEFT, KeyEvent.VK_PAGE_DOWN}) {
                        int before = slider.getValue();
                        pressKey(slider, key);
                        assertEquals(before - 1, slider.getValue());
                    }
                    expected.put(FREQUENCY, "47");
                    assertEquals(expected, fixture.preferences());
                    slider.updateUI();
                    clickTrack(slider, 90);
                    assertEquals(48, slider.getValue(), "Reinstalling the native UI must preserve 1% track steps");
                    assertSame(trackDefault, UIManager.get("Slider.scrollOnTrackClick"));
                    assertEquals(Collections.singleton(FREQUENCY), fixture.patches().keySet());
                }
            } finally {
                UIManager.setLookAndFeel(previous);
            }
        });
    }

    @Test
    void aNewPluginAndAdapterRestoreTheSavedIntegerWithoutResettingAnyConfiguration() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.settings.start();
                JSlider previous = fixture.slider();
                JPanel previousPanel = fixture.panel;
                previous.setValue(43);
                Map<String, String> expected = fixture.preferences();
                fixture.patches().clear();
                fixture.reattachPlugin();
                JSlider current = fixture.slider();
                assertNotSame(previous, current);
                assertNotSame(previousPanel, fixture.panel, "The SDK initializes each native settings panel only once");
                assertFalse(SwingUtilities.isDescendingFrom(previousPanel, fixture.root));
                assertEquals(43, current.getValue());
                assertEquals(43, fixture.config.waitingMouseChance());
                fixture.settings.refresh();
                previous.setValue(0);
                assertEquals(expected, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "A new plugin/adapter must not rewrite saved or shared preferences");
                pressKey(current, KeyEvent.VK_RIGHT);
                expected.put(FREQUENCY, "44");
                assertEquals(expected, fixture.preferences());
                assertEquals(Collections.singletonMap(FREQUENCY, "44"), fixture.patches());
            }
        });
    }

    @Test
    void nativePresetMenuShowsEffectiveValuesAndOnlyAnExplicitSliderEditSavesCustomFrequency() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                Map<String, String> expected = fixture.preferences();
                fixture.settings.start();
                assertEquals(47, fixture.slider().getValue(), "Custom retains the persisted manual value on attachment");
                assertEquals(expected, fixture.preferences());
                assertTrue(fixture.patches().isEmpty());
                JComboBox<?> preset = fixture.choice("waitingMousePreset");
                FlipperConfig.RandomizationPreset[] choices = {
                    FlipperConfig.RandomizationPreset.AFK,
                    FlipperConfig.RandomizationPreset.SEMI_AFK,
                    FlipperConfig.RandomizationPreset.ATTENTIVE_HUMAN
                };
                for (int index = 0; index < choices.length; index++) {
                    preset.setSelectedItem(choices[index]);
                    fixture.settings.refresh();
                    expected.put(GROUP + ".waitingMousePreset", choices[index].name());
                    assertEquals(fixture.presets.frequency(fixture.config), fixture.slider().getValue());
                    assertTrue(fixture.slider().isEnabled());
                    assertEquals(47, fixture.config.waitingMouseChance(), "Preset selection does not overwrite the manual value");
                    assertEquals(expected, fixture.preferences(), "The native preset menu changes only its owned key");
                }
                fixture.patches().clear();
                int edited = fixture.slider().getValue() + 1;
                pressKey(fixture.slider(), KeyEvent.VK_RIGHT);
                expected.put(GROUP + ".waitingMousePreset", "CUSTOM");
                expected.put(FREQUENCY, Integer.toString(edited));
                assertEquals(edited, fixture.slider().getValue());
                assertEquals(expected, fixture.preferences());
                Map<String, String> changes = new HashMap<>();
                changes.put(GROUP + ".waitingMousePreset", "CUSTOM");
                changes.put(FREQUENCY, Integer.toString(edited));
                assertEquals(changes, fixture.patches());
            }
        });
    }

    @Test
    void nativeMouseSpeedEnableSelectsFatigueAndRemembersOnlyItsOwnedPreferences() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JCheckBox[] previous = new JCheckBox[1];
        Map<String, String> expected = new HashMap<>();
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].usePresetSettings();
                fixture[0].settings.start();
                fixture[0].choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.AFK);
                fixture[0].settings.refresh();
                expected.putAll(fixture[0].preferences());
                fixture[0].patches().clear();
                JPanel row = fixture[0].row("randomizeMouseSpeed");
                assertEquals("Randomize mouse speed", fixture[0].label(row).getText());
                previous[0] = (JCheckBox) fixture[0].east(row);
                assertFalse(previous[0].isSelected(), "The SDK renders the opt-in checkbox off by default");
                assertFalse(fixture[0].config.randomizeMouseSpeed());
                assertEquals(47, fixture[0].config.waitingMouseChance());
                assertEquals(fixture[0].presets.frequency(fixture[0].config), fixture[0].slider().getValue());

                previous[0].doClick(0);
                expected.put(MOUSE_SPEED, "true");
                expected.put(GROUP + ".waitingMousePreset", "DAY_FATIGUE");
                assertTrue(fixture[0].config.randomizeMouseSpeed());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture[0].config.waitingMousePreset());
                assertEquals(expected, fixture[0].preferences(), "Explicit enable changes only speed and its owned fatigue preset");
                Map<String, String> enabledChanges = new HashMap<>();
                enabledChanges.put(MOUSE_SPEED, "true");
                enabledChanges.put(GROUP + ".waitingMousePreset", "DAY_FATIGUE");
                assertEquals(enabledChanges, fixture[0].patches());
                fixture[0].patches().clear();
                fixture[0].eventBus.register(fixture[0].panel);
                fixture[0].eventBus.post(new ProfileChanged());
            });
            flushEdt();
            onEdt(() -> {
                JCheckBox current = (JCheckBox) fixture[0].east(fixture[0].row("randomizeMouseSpeed"));
                assertNotSame(previous[0], current, "Exercise the SDK's actual row rebuild");
                assertTrue(current.isSelected(), "Rerendering remembers the user's opt-in choice");
                assertEquals(expected, fixture[0].preferences());
                assertTrue(fixture[0].patches().isEmpty(), "Rendering and profile replay cannot save settings");
                JPopupMenu popup = fixture[0].resetMenu(fixture[0].label(fixture[0].row("randomizeMouseSpeed")));
                JMenuItem reset = null;
                for (Component item : popup.getComponents()) {
                    if (item instanceof JMenuItem && "Reset".equals(((JMenuItem) item).getText())) reset = (JMenuItem) item;
                }
                assertNotNull(reset, "Use the SDK-created per-item Reset action");
                reset.doClick(0);
                expected.put(MOUSE_SPEED, "false");
            });
            flushEdt();
            onEdt(() -> {
                assertFalse(((JCheckBox) fixture[0].east(fixture[0].row("randomizeMouseSpeed"))).isSelected());
                assertFalse(fixture[0].config.randomizeMouseSpeed());
                assertEquals(expected, fixture[0].preferences(), "Reset preserves manual frequency, preset, master and shared preferences");
                assertEquals(Collections.singletonMap(MOUSE_SPEED, "false"), fixture[0].patches());
                assertEquals(47, fixture[0].config.waitingMouseChance());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture[0].config.waitingMousePreset());
                assertEquals(47, fixture[0].slider().getValue());
                fixture[0].settings.refresh();
                assertEquals(expected, fixture[0].preferences());
            });
        } finally {
            onEdt(() -> {
                if (fixture[0] != null) {
                    fixture[0].eventBus.unregister(fixture[0].panel);
                    fixture[0].close();
                }
            });
        }
    }

    @Test
    void onlyAnOwnedNewSpeedEnableActionSelectsFatigueAndCleanupRestoresNativeListeners() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                JCheckBox checkbox = fixture.mouseSpeed();
                ActionListener[] nativeActions = checkbox.getActionListeners();
                java.awt.event.ItemListener[] nativeItems = checkbox.getItemListeners();
                fixture.settings.start();
                ActionListener owned = java.util.Arrays.stream(checkbox.getActionListeners())
                    .filter(listener -> java.util.Arrays.stream(nativeActions).noneMatch(original -> original == listener))
                    .findFirst().orElseThrow(AssertionError::new);

                fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", true);
                checkbox.setSelected(true);
                fixture.eventBus.post(new ProfileChanged());
                Map<String, String> replayed = fixture.preferences();
                fixture.patches().clear();
                owned.actionPerformed(new ActionEvent(checkbox, ActionEvent.ACTION_PERFORMED, "replay"));
                assertEquals(FlipperConfig.RandomizationPreset.CUSTOM, fixture.config.waitingMousePreset());
                assertEquals(replayed, fixture.preferences(), "Programmatic selection/profile replay cannot choose fatigue");
                assertTrue(fixture.patches().isEmpty());

                fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", false);
                checkbox.setSelected(false);
                fixture.patches().clear();
                checkbox.doClick(0);
                assertTrue(fixture.config.randomizeMouseSpeed());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture.config.waitingMousePreset());
                Map<String, String> enabled = fixture.preferences();
                fixture.patches().clear();
                owned.actionPerformed(new ActionEvent(checkbox, ActionEvent.ACTION_PERFORMED, "duplicate"));
                assertEquals(enabled, fixture.preferences(), "An already enabled checkbox does not replay its command");
                assertTrue(fixture.patches().isEmpty());

                checkbox.doClick(0);
                assertFalse(fixture.config.randomizeMouseSpeed());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture.config.waitingMousePreset());
                assertEquals(Collections.singletonMap(MOUSE_SPEED, "false"), fixture.patches());
                fixture.manager.setConfiguration(GROUP, "waitingMousePreset", FlipperConfig.RandomizationPreset.SEMI_AFK);
                fixture.row("randomizeMouseSpeed").setVisible(false);
                fixture.patches().clear();
                checkbox.doClick(0);
                assertEquals(FlipperConfig.RandomizationPreset.SEMI_AFK, fixture.config.waitingMousePreset(),
                    "An immediately hidden native checkbox cannot authorize a new preset");

                fixture.settings.close();
                assertArrayEquals(nativeActions, checkbox.getActionListeners());
                assertArrayEquals(nativeItems, checkbox.getItemListeners());
                Map<String, String> closed = fixture.preferences();
                fixture.patches().clear();
                owned.actionPerformed(new ActionEvent(checkbox, ActionEvent.ACTION_PERFORMED, "closed"));
                assertEquals(closed, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "Disposed callbacks cannot save a preset");
            }
        });
    }

    @Test
    void nativeLocalClockChoiceHidesCustomInputWithoutOverwritingItOrChangingOtherPreferences() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                assertEquals(FlipperConfig.TimeOfDaySource.MORNING,
                    new FlipperConfig() {}.waitingMouseTimeSource(), "Existing defaults are retained");
                fixture.usePresetSettings();
                fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", true);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.enterTime("19:20");
                fixture.settings.start();
                JPanel customRow = fixture.row("waitingMouseTime");
                JTextComponent customInput = fixture.time();
                assertTrue(customRow.isVisible());
                Map<String, String> expected = fixture.preferences();
                fixture.patches().clear();

                JComboBox<?> source = fixture.choice("waitingMouseTimeSource");
                assertTrue(java.util.stream.IntStream.range(0, source.getItemCount())
                    .anyMatch(index -> source.getItemAt(index) == FlipperConfig.TimeOfDaySource.LOCAL_TIME));
                source.setSelectedItem(FlipperConfig.TimeOfDaySource.LOCAL_TIME);
                expected.put(TIME_SOURCE, "LOCAL_TIME");
                assertEquals(expected, fixture.preferences());
                assertEquals(Collections.singletonMap(TIME_SOURCE, "LOCAL_TIME"), fixture.patches());
                fixture.settings.refresh();
                assertFalse(customRow.isVisible());
                fixture.patches().clear();
                customInput.setText("not a time");
                loseFocus(customInput);
                fixture.settings.refreshValue();
                fixture.eventBus.post(new ProfileChanged());
                fixture.settings.refresh();
                assertEquals(expected, fixture.preferences(), "Local clock and profile replay retain saved manual input");
                assertTrue(fixture.patches().isEmpty());

                source.setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                expected.put(TIME_SOURCE, "CUSTOM_TIME");
                fixture.settings.refresh();
                assertTrue(customRow.isVisible());
                assertSame(customInput, fixture.time());
                assertEquals("19:20", fixture.time().getText());
                assertEquals(expected, fixture.preferences());
                fixture.settings.close();
                assertEquals(expected, fixture.preferences());
            }
        });
    }

    @Test
    void hiddenCustomTimeSurvivesRepeatedScansAndRespectsNativeSearchAndClose() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", true);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.enterTime("19:20");
                JPanel row = fixture.row("waitingMouseTime");
                JTextComponent nativeTime = fixture.time();
                FocusListener[] nativeListeners = nativeTime.getFocusListeners();
                Map<JPanel, String> nativeIndex = fixture.index();
                fixture.settings.start();
                assertTrue(row.isVisible());

                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.MORNING);
                Object hiddenBinding = field(WaitingMouseSettings.class, "customTimeBinding").get(fixture.settings);
                assertNotNull(hiddenBinding);
                Map<String, String> expected = fixture.preferences();
                fixture.patches().clear();
                for (int pass = 0; pass < 40; pass++) {
                    fixture.settings.refresh();
                    fixture.settings.refreshValue();
                    assertFalse(row.isVisible());
                    assertSame(hiddenBinding, field(WaitingMouseSettings.class, "customTimeBinding").get(fixture.settings),
                        "A hidden owned row remains bound instead of cycling detach/attach");
                }
                nativeTime.setText("23:59");
                loseFocus(nativeTime);
                assertEquals(expected, fixture.preferences());
                assertTrue(fixture.patches().isEmpty());

                fixture.search().setText("verbose");
                fixture.manager.setConfiguration(GROUP, "waitingMouseTimeSource", FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.settings.refresh();
                assertFalse(row.isVisible(), "Eligible custom time still obeys the native search filter");
                fixture.patches().clear();
                nativeTime.setText("22:05");
                loseFocus(nativeTime);
                assertEquals("19:20", fixture.config.waitingMouseTime());
                assertTrue(fixture.patches().isEmpty(), "Search-hidden inputs cannot save stale text");

                fixture.search().setText("");
                fixture.settings.refresh();
                assertTrue(row.isVisible());
                assertSame(nativeTime, fixture.time());
                assertEquals("19:20", fixture.time().getText());
                fixture.search().setText("custom start");
                fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", false);
                fixture.settings.refresh();
                assertFalse(row.isVisible());
                fixture.patches().clear();
                fixture.settings.close();
                assertTrue(row.isVisible(), "Closing restores the SDK's current matching search result");
                assertSame(nativeTime, fixture.time());
                assertArrayEquals(nativeListeners, nativeTime.getFocusListeners());
                assertSame(nativeIndex, fixture.index(), "The native row index is never replaced");
                assertTrue(nativeIndex.containsKey(row));
                assertTrue(fixture.patches().isEmpty());
            }
        });
    }

    @Test
    void nativeTimeMenuAndTypedClockDriveReadonlyFatigueWithoutSavingEffectiveValues() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                fixture.settings.start();
                Map<String, String> expected = fixture.preferences();
                fixture.mouseSpeed().doClick(0);
                expected.put(MOUSE_SPEED, "true");
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                expected.put(GROUP + ".waitingMousePreset", "DAY_FATIGUE");
                fixture.settings.refresh();
                assertTrue(fixture.choice("waitingMouseTimeSource").isEnabled());
                assertBlankTime(fixture);
                JSlider slider = fixture.slider();
                int morning = slider.getValue();
                assertEquals(fixture.presets.frequency(fixture.config), morning);
                assertFalse(slider.isEnabled());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, slider.getForeground());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, fixture.label(fixture.row("waitingMouseChance")).getForeground());
                assertTrue(slider.getToolTipText().contains("09:00"));
                assertTrue(slider.getToolTipText().contains("This preset controls the slider; select Custom to edit it."));
                fixture.clock.advanceSeconds(8 * 60 * 60);
                fixture.settings.refreshValue();
                assertNotEquals(morning, slider.getValue(), "The displayed frequency follows the advancing policy clock");
                assertEquals(fixture.presets.frequency(fixture.config), slider.getValue());
                assertEquals(expected, fixture.preferences(), "Runtime movement of the slider must not persist an effective value");
                slider.setValue(5);
                assertEquals(fixture.presets.frequency(fixture.config), slider.getValue());
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.NIGHT);
                expected.put(GROUP + ".waitingMouseTimeSource", "NIGHT");
                fixture.settings.refresh();
                assertBlankTime(fixture);
                assertTrue(slider.getToolTipText().contains("Night start 21:00"));
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                expected.put(GROUP + ".waitingMouseTimeSource", "CUSTOM_TIME");
                assertTrue(fixture.time().isEnabled());
                assertTrue(fixture.time().isEditable());
                assertEquals("09:00", fixture.time().getText());
                fixture.enterTime("04:15");
                expected.put(TIME, "04:15");
                fixture.settings.refresh();
                assertTrue(slider.getToolTipText().contains("Custom time start 04:15"));
                assertEquals(fixture.presets.frequency(fixture.config), slider.getValue());
                fixture.enterTime("24:30");
                expected.put(TIME, "24:30");
                fixture.settings.refresh();
                assertEquals(0, slider.getValue());
                assertTrue(slider.getToolTipText().contains("Enter time as HH:mm"));
                fixture.enterTime("14:30");
                expected.put(TIME, "14:30");
                fixture.settings.refresh();
                assertTrue(slider.getToolTipText().contains("Custom time start 14:30"));
                assertEquals(expected, fixture.preferences());
                assertEquals(47, fixture.config.waitingMouseChance());
                assertFalse(fixture.patches().containsKey(FREQUENCY));
                fixture.settings.close();
                assertEquals(47, ((JSpinner) fixture.east(fixture.row("waitingMouseChance"))).getValue());
                assertEquals(expected, fixture.preferences(), "Restoring the native spinner cannot save the effective value");
            }
        });
    }

    @Test
    void fatigueClockEligibilityBlanksOnlyItsOwnedControlsWithoutSavingSettings() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.enterTime("18:37");
                fixture.settings.start();
                assertEquals("Fatigue clock", fixture.label(fixture.row("waitingMouseTimeSource")).getText());
                assertEquals("Custom start time (HH:mm)", fixture.label(fixture.row("waitingMouseTime")).getText());
                for (FlipperConfig.RandomizationPreset preset : FlipperConfig.RandomizationPreset.values()) {
                    for (boolean master : new boolean[]{false, true}) {
                        for (boolean speed : new boolean[]{false, true}) {
                            fixture.manager.setConfiguration(GROUP, "waitingMousePreset", preset);
                            fixture.manager.setConfiguration(GROUP, "waitingMouseOffScreen", master);
                            fixture.manager.setConfiguration(GROUP, "randomizeMouseSpeed", speed);
                            Map<String, String> expected = fixture.preferences();
                            fixture.patches().clear();
                            fixture.settings.refresh();
                            fixture.settings.refreshValue();
                            boolean active = preset == FlipperConfig.RandomizationPreset.DAY_FATIGUE && master && speed;
                            assertEquals(active, fixture.choice("waitingMouseTimeSource").isEnabled(),
                                preset + ", master=" + master + ", speed=" + speed);
                            if (active) {
                                assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME,
                                    fixture.choice("waitingMouseTimeSource").getSelectedItem());
                                assertTrue(fixture.time().isEnabled());
                                assertTrue(fixture.time().isEditable());
                                assertEquals("18:37", fixture.time().getText());
                                assertTrue(fixture.label(fixture.row("waitingMouseTimeSource")).isEnabled());
                                assertTrue(fixture.label(fixture.row("waitingMouseTime")).isEnabled());
                            } else {
                                assertBlankClock(fixture);
                            }
                            assertEquals(master && (preset != FlipperConfig.RandomizationPreset.DAY_FATIGUE || !speed),
                                fixture.slider().isEnabled(), "Only an active fatigue preset takes control of the slider");
                            assertEquals("18:37", fixture.config.waitingMouseTime());
                            assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME, fixture.config.waitingMouseTimeSource());
                            assertEquals(47, fixture.config.waitingMouseChance());
                            assertEquals(expected, fixture.preferences(), "Eligibility must not change any saved or shared preference");
                            assertTrue(fixture.patches().isEmpty(), "Refreshing and blanking controls must not save config");
                        }
                    }
                }
            }
        });
    }

    @Test
    void clockTogglesRestoreTheSameNativeControlsAndRememberTimeWithoutChangingTheSelectedPreset() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                fixture.mouseSpeed().doClick(0);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.enterTime("18:37");
                JComboBox<?> nativeClock = fixture.choice("waitingMouseTimeSource");
                JTextComponent nativeTime = fixture.time();
                java.awt.event.ItemListener[] nativeClockListeners = nativeClock.getItemListeners();
                FocusListener[] nativeTimeListeners = nativeTime.getFocusListeners();
                Map<String, String> expected = fixture.preferences();
                fixture.patches().clear();
                fixture.settings.start();
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"));
                assertSame(nativeTime, fixture.time());
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                assertTrue(fixture.patches().isEmpty(), "Attaching active controls cannot write config");

                fixture.mouseSpeed().doClick(0);
                expected.put(MOUSE_SPEED, "false");
                assertBlankClock(fixture);
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                assertEquals(47, fixture.slider().getValue(), "Turning speed off uses the saved manual randomization");
                assertTrue(fixture.slider().isEnabled());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture.config.waitingMousePreset());
                assertEquals(expected, fixture.preferences(), "Turning the feature off changes only the explicit checkbox");
                fixture.mouseSpeed().doClick(0);
                expected.put(MOUSE_SPEED, "true");
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"));
                assertSame(nativeTime, fixture.time());
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                assertEquals("18:37", nativeTime.getText());
                assertFalse(fixture.slider().isEnabled());

                JCheckBox master = (JCheckBox) fixture.east(fixture.row("waitingMouseOffScreen"));
                master.doClick(0);
                expected.put(ENABLED, "false");
                assertBlankClock(fixture);
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                master.doClick(0);
                expected.put(ENABLED, "true");
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"));
                assertSame(nativeTime, fixture.time());
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.SEMI_AFK);
                expected.put(GROUP + ".waitingMousePreset", "SEMI_AFK");
                assertBlankClock(fixture);
                assertNativeUiListenersRetained(nativeClock, nativeTime, nativeClockListeners, nativeTimeListeners);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                expected.put(GROUP + ".waitingMousePreset", "DAY_FATIGUE");
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"));
                assertSame(nativeTime, fixture.time());
                assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME, nativeClock.getSelectedItem());
                assertEquals("18:37", nativeTime.getText());
                assertEquals(expected, fixture.preferences());

                fixture.patches().clear();
                fixture.settings.refresh();
                fixture.settings.refreshValue();
                fixture.settings.close();
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"), "Closing restores the SDK's native east layout");
                assertSame(nativeTime, fixture.time(), "Closing restores the SDK's native south layout");
                assertArrayEquals(nativeClockListeners, nativeClock.getItemListeners());
                assertArrayEquals(nativeTimeListeners, nativeTime.getFocusListeners());
                assertEquals("18:37", nativeTime.getText());
                assertEquals(expected, fixture.preferences());
                assertTrue(fixture.patches().isEmpty(), "Refresh and native restoration must not save effective or blank values");
            }
        });
    }

    @Test
    void pendingTextAndStaleNativeEventsCannotSaveWhenTheClockOrTypedTimeIsDisabled() throws Exception {
        onEdt(() -> {
            try (Fixture fixture = new Fixture(temporary)) {
                fixture.usePresetSettings();
                fixture.mouseSpeed().doClick(0);
                fixture.choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                fixture.choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture.enterTime("19:20");
                fixture.settings.start();
                JComboBox<?> nativeClock = fixture.choice("waitingMouseTimeSource");
                JTextComponent nativeTime = fixture.time();
                nativeTime.setText("23:59");
                Map<String, String> expected = fixture.preferences();
                fixture.patches().clear();
                fixture.mouseSpeed().doClick(0);
                expected.put(MOUSE_SPEED, "false");
                // Fire the events immediately, before any queued native panel scan can run.
                loseFocus(nativeTime);
                nativeClock.setSelectedItem(FlipperConfig.TimeOfDaySource.NIGHT);
                assertBlankClock(fixture);
                assertEquals(expected, fixture.preferences());
                assertEquals(Collections.singletonMap(MOUSE_SPEED, "false"), fixture.patches());
                assertEquals("19:20", fixture.config.waitingMouseTime());
                assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME, fixture.config.waitingMouseTimeSource());

                fixture.mouseSpeed().doClick(0);
                expected.put(MOUSE_SPEED, "true");
                assertSame(nativeClock, fixture.choice("waitingMouseTimeSource"));
                assertSame(nativeTime, fixture.time());
                assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME, nativeClock.getSelectedItem());
                assertEquals("19:20", nativeTime.getText(), "Unsaved disabled input must not replace remembered time");
                nativeClock.setSelectedItem(FlipperConfig.TimeOfDaySource.MORNING);
                expected.put(TIME_SOURCE, "MORNING");
                assertBlankTime(fixture);
                fixture.patches().clear();
                nativeTime.setText("22:05");
                loseFocus(nativeTime);
                loseFocus(fixture.time());
                fixture.settings.refresh();
                assertBlankTime(fixture);
                assertEquals(expected, fixture.preferences(), "A stale Type a time field cannot save under Morning");
                assertTrue(fixture.patches().isEmpty());
                assertEquals("19:20", fixture.config.waitingMouseTime());
            }
        });
    }

    @Test
    void profileReplayAndNativeResetRebuildClockRowsWithCurrentEligibilityAndNoReplayWrites() throws Exception {
        Fixture[] fixture = new Fixture[1];
        JComboBox<?>[] oldClock = new JComboBox<?>[1];
        JTextComponent[] oldTime = new JTextComponent[1];
        Map<String, String> expected = new HashMap<>();
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].usePresetSettings();
                fixture[0].mouseSpeed().doClick(0);
                fixture[0].choice("waitingMousePreset").setSelectedItem(FlipperConfig.RandomizationPreset.DAY_FATIGUE);
                fixture[0].choice("waitingMouseTimeSource").setSelectedItem(FlipperConfig.TimeOfDaySource.CUSTOM_TIME);
                fixture[0].enterTime("18:37");
                fixture[0].settings.start();
                oldClock[0] = fixture[0].choice("waitingMouseTimeSource");
                oldTime[0] = fixture[0].time();
                expected.putAll(fixture[0].preferences());
                fixture[0].patches().clear();
                fixture[0].eventBus.register(fixture[0].panel);
                fixture[0].eventBus.post(new ProfileChanged());
            });
            flushEdt();
            onEdt(() -> {
                assertNotSame(oldClock[0], fixture[0].choice("waitingMouseTimeSource"));
                assertNotSame(oldTime[0], fixture[0].time());
                assertTrue(fixture[0].choice("waitingMouseTimeSource").isEnabled());
                assertEquals(FlipperConfig.TimeOfDaySource.CUSTOM_TIME,
                    fixture[0].choice("waitingMouseTimeSource").getSelectedItem());
                assertTrue(fixture[0].time().isEnabled());
                assertEquals("18:37", fixture[0].time().getText());
                assertEquals(expected, fixture[0].preferences());
                assertTrue(fixture[0].patches().isEmpty(), "Profile replay and replacement native rows must not write preferences");
                oldClock[0] = fixture[0].choice("waitingMouseTimeSource");
                fixture[0].reset("waitingMouseTimeSource").doClick(0);
                expected.put(TIME_SOURCE, "MORNING");
            });
            flushEdt();
            onEdt(() -> {
                assertNotSame(oldClock[0], fixture[0].choice("waitingMouseTimeSource"));
                assertTrue(fixture[0].choice("waitingMouseTimeSource").isEnabled());
                assertEquals(FlipperConfig.TimeOfDaySource.MORNING,
                    fixture[0].choice("waitingMouseTimeSource").getSelectedItem());
                assertBlankTime(fixture[0]);
                assertEquals("18:37", fixture[0].config.waitingMouseTime());
                assertEquals(expected, fixture[0].preferences());
                assertEquals(Collections.singletonMap(TIME_SOURCE, "MORNING"), fixture[0].patches());
                fixture[0].patches().clear();
                fixture[0].reset("randomizeMouseSpeed").doClick(0);
                expected.put(MOUSE_SPEED, "false");
            });
            flushEdt();
            onEdt(() -> {
                assertBlankClock(fixture[0]);
                assertEquals(47, fixture[0].slider().getValue());
                assertTrue(fixture[0].slider().isEnabled());
                assertEquals(FlipperConfig.RandomizationPreset.DAY_FATIGUE, fixture[0].config.waitingMousePreset());
                assertEquals("18:37", fixture[0].config.waitingMouseTime());
                assertEquals(expected, fixture[0].preferences());
                assertEquals(Collections.singletonMap(MOUSE_SPEED, "false"), fixture[0].patches());
            });
        } finally {
            onEdt(() -> {
                if (fixture[0] != null) {
                    fixture[0].eventBus.unregister(fixture[0].panel);
                    fixture[0].close();
                }
            });
        }
    }

    @Test
    void nativeProfileReplayRebuildsThePresetRowsWithoutSavingOrReapplyingTheManualValue() throws Exception {
        Fixture[] fixture = new Fixture[1];
        try {
            onEdt(() -> {
                fixture[0] = new Fixture(temporary);
                fixture[0].manager.setConfiguration(GROUP, "waitingMousePreset", FlipperConfig.RandomizationPreset.AFK);
                fixture[0].usePresetSettings();
                fixture[0].settings.start();
                fixture[0].patches().clear();
                fixture[0].eventBus.register(fixture[0].panel);
                fixture[0].eventBus.post(new ProfileChanged());
            });
            flushEdt();
            onEdt(() -> {
                assertEquals(fixture[0].presets.frequency(fixture[0].config), fixture[0].slider().getValue());
                assertEquals(47, fixture[0].config.waitingMouseChance());
                assertEquals(FlipperConfig.RandomizationPreset.AFK,
                    fixture[0].choice("waitingMousePreset").getSelectedItem());
                assertTrue(fixture[0].patches().isEmpty(), "Native rebuild, attachment and profile replay must not write preferences");
            });
        } finally {
            onEdt(() -> {
                if (fixture[0] != null) {
                    fixture[0].eventBus.unregister(fixture[0].panel);
                    fixture[0].close();
                }
            });
        }
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

    private static void loseFocus(JTextComponent text) {
        FocusEvent event = new FocusEvent(text, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : text.getFocusListeners()) listener.focusLost(event);
    }

    private static void assertNativeUiListenersRetained(JComboBox<?> nativeClock, JTextComponent nativeTime,
                                                        java.awt.event.ItemListener[] clockListeners,
                                                        FocusListener[] timeListeners) {
        String persistencePrefix = "net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel$";
        int retainedClockListeners = 0;
        for (java.awt.event.ItemListener listener : clockListeners) {
            if (!listener.getClass().getName().startsWith(persistencePrefix)) {
                assertTrue(java.util.Arrays.stream(nativeClock.getItemListeners()).anyMatch(current -> current == listener),
                    "Native combo UI listeners remain attached while only config persistence is gated");
                retainedClockListeners++;
            }
        }
        int retainedTimeListeners = 0;
        for (FocusListener listener : timeListeners) {
            if (!listener.getClass().getName().startsWith(persistencePrefix)) {
                assertTrue(java.util.Arrays.stream(nativeTime.getFocusListeners()).anyMatch(current -> current == listener),
                    "Native text caret and focus UI listeners remain attached while config persistence is gated");
                retainedTimeListeners++;
            }
        }
        assertTrue(retainedClockListeners > 0, "Exercise actual native combo UI listeners");
        assertTrue(retainedTimeListeners > 0, "Exercise actual native text UI listeners");
    }

    private static void assertBlankTime(Fixture fixture) throws Exception {
        assertFalse(fixture.row("waitingMouseTime").isVisible(), "Inactive custom time has no settings row");
        JTextComponent text = fixture.time();
        assertFalse(text.isEnabled());
        assertFalse(text.isEditable());
        assertEquals("", text.getText(), "Inactive input displays no clock value");
        JLabel label = fixture.label(fixture.row("waitingMouseTime"));
        assertFalse(label.isEnabled());
        assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, label.getForeground());
    }

    private static void assertBlankClock(Fixture fixture) throws Exception {
        JComboBox<?> clock = fixture.choice("waitingMouseTimeSource");
        assertFalse(clock.isEnabled());
        assertNull(clock.getSelectedItem());
        assertEquals(0, clock.getItemCount(), "An inactive clock dropdown displays no saved period");
        JLabel label = fixture.label(fixture.row("waitingMouseTimeSource"));
        assertFalse(label.isEnabled());
        assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, label.getForeground());
        assertBlankTime(fixture);
    }

    private static void pressKey(JSlider slider, int key) {
        Object binding = slider.getInputMap().get(KeyStroke.getKeyStroke(key, 0));
        assertNotNull(binding);
        Action action = slider.getActionMap().get(binding);
        assertNotNull(action);
        action.actionPerformed(new ActionEvent(slider, ActionEvent.ACTION_PERFORMED, binding.toString()));
    }

    private static void clickTrack(JSlider slider, int targetValue) {
        slider.setSize(250, 40);
        BufferedImage canvas = new BufferedImage(250, 40, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics graphics = canvas.getGraphics();
        try { slider.paint(graphics); } finally { graphics.dispose(); }
        BasicSliderUI ui = (BasicSliderUI) slider.getUI();
        int x = 0;
        while (x < slider.getWidth() && ui.valueForXPosition(x) < targetValue) x++;
        slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_PRESSED, 0,
            InputEvent.BUTTON1_DOWN_MASK, x, 20, 1, false, MouseEvent.BUTTON1));
        slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_RELEASED, 0,
            0, x, 20, 1, false, MouseEvent.BUTTON1));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class ShowingRoot extends JPanel {
        private ShowingRoot() { super(new BorderLayout()); }
        @Override public boolean isShowing() { return isVisible(); }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-09T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
        private void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
    }

    private static final class Fixture implements AutoCloseable {
        private final EventBus eventBus = new EventBus();
        private FlipperPlugin owner = new FlipperPlugin();
        private final ShowingRoot root = new ShowingRoot();
        private final Field profileName = field(ConfigManager.class, "configProfileName");
        private final Object previousProfileName = profileName.get(null);
        private final ConfigManager manager;
        private final PluginManager pluginManager;
        private final FlipperConfig config;
        private JPanel panel;
        private final Object data;
        private final Map<String, String> properties;
        private WaitingMouseSettings settings;
        private final AtomicInteger finishRequests = new AtomicInteger();
        private final MutableClock clock = new MutableClock();
        private final WaitingMousePresets presets = new WaitingMousePresets(clock,
            (minimum, maximum) -> minimum <= 0 && maximum >= 0 ? 0 : minimum);
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
            properties.put(MOUSE_SPEED, "false");
            properties.put(GROUP + ".waitingMousePreset", "CUSTOM");
            properties.put(GROUP + ".waitingMouseTimeSource", "MORNING");
            properties.put(GROUP + ".waitingMouseTime", "09:00");
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
            pluginManager = (PluginManager) pluginManagerConstructor.newInstance(pluginArguments);
            createPanel();
            settings = new WaitingMouseSettings(owner, config, root,
                value -> manager.setConfiguration(GROUP, "waitingMouseChance", value),
                bindFinish ? finishRequests::incrementAndGet : null, bindFinish ? () -> finishAvailable : null);
            registerOwner();
        }

        private void createPanel() throws Exception {
            Class<?> panelType = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel");
            Constructor<?> panelConstructor = panelType.getDeclaredConstructors()[0];
            panelConstructor.setAccessible(true);
            panel = (JPanel) panelConstructor.newInstance(null, manager, pluginManager, null, null, null);
            root.add(panel, BorderLayout.CENTER);
            initializePanel();
        }

        private void initializePanel() throws Exception {
            Method init = panel.getClass().getDeclaredMethod("init", MicrobotPluginConfigurationDescriptor.class);
            init.setAccessible(true);
            init.invoke(panel, new MicrobotPluginConfigurationDescriptor("Flipper", "Synthetic test", new String[0],
                owner, config, manager.getConfigDescriptor(config), Collections.emptyList()));
        }

        private void registerOwner() throws Exception {
            field(FlipperPlugin.class, "config").set(owner, config);
            field(FlipperPlugin.class, "configManager").set(owner, manager);
            field(FlipperPlugin.class, "waitingMouseSettings").set(owner, settings);
            eventBus.register(owner);
        }

        private void usePresetSettings() throws Exception {
            settings.close();
            FlipperScript script = new FlipperScript();
            field(Script.class, "scheduledExecutorService").set(script, inertScheduler());
            field(FlipperScript.class, "waitingMousePresets").set(script, presets);
            settings = new WaitingMouseSettings(owner, config, root, owner::saveWaitingMouseChance,
                null, null, () -> script.waitingMouseFrequency(config),
                () -> config.waitingMousePreset() != FlipperConfig.RandomizationPreset.DAY_FATIGUE
                    || !config.randomizeMouseSpeed(),
                () -> script.waitingMouseDescription(config));
            field(FlipperPlugin.class, "waitingMouseSettings").set(owner, settings);
        }

        private JComboBox<?> choice(String methodName) throws Exception {
            return (JComboBox<?>) east(row(methodName));
        }

        private void enterTime(String value) throws Exception {
            JTextComponent text = time();
            text.setText(value);
            loseFocus(text);
        }

        private JTextComponent time() throws Exception {
            JPanel row = row("waitingMouseTime");
            return (JTextComponent) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        }

        private JCheckBox mouseSpeed() throws Exception {
            return (JCheckBox) east(row("randomizeMouseSpeed"));
        }

        private JMenuItem reset(String methodName) throws Exception {
            JPopupMenu popup = resetMenu(label(row(methodName)));
            for (Component item : popup.getComponents()) {
                if (item instanceof JMenuItem && "Reset".equals(((JMenuItem) item).getText())) {
                    return (JMenuItem) item;
                }
            }
            throw new AssertionError("SDK label has no per-item Reset action");
        }

        private void reattachPlugin() throws Exception {
            eventBus.unregister(owner);
            field(FlipperPlugin.class, "waitingMouseSettings").set(owner, null);
            settings.close();
            root.remove(panel);
            owner = new FlipperPlugin();
            // MicrobotConfigPanel has no deinit operation; its init contract requires a fresh panel.
            createPanel();
            settings = new WaitingMouseSettings(owner, config, root,
                value -> manager.setConfiguration(GROUP, "waitingMouseChance", value));
            registerOwner();
            settings.start();
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
            return configButton("finish");
        }

        private JButton configButton(String methodName) throws Exception {
            return (JButton) ((BorderLayout) row(methodName).getLayout()).getLayoutComponent(BorderLayout.CENTER);
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
