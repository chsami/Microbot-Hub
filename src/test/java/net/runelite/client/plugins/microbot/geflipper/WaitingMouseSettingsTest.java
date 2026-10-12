package net.runelite.client.plugins.microbot.geflipper;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.ui.ColorScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual SDK settings panel and descriptor, with synthetic rows and no client/profile. */
class WaitingMouseSettingsTest {
    @Test
    void nativeRowGetsOneSliderAndPreservesSearchResetAndPreferences() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            JPopupMenu popup = fixture.row.label.getComponentPopupMenu();
            WaitingMouseSettings settings = fixture.settings();
            assertSame(fixture.row.spinner, fixture.row.spinner.getParent().getComponent(1));
            assertTrue(sliders(fixture.root).isEmpty());
            settings.start();
            JSlider slider = slider(fixture.root);
            assertEquals(0, slider.getMinimum());
            assertEquals(100, slider.getMaximum());
            assertEquals(30, slider.getValue());
            assertFalse(slider.getPaintLabels());
            assertFalse(slider.getPaintTicks());
            assertNull(slider.getLabelTable());
            assertFalse(fixture.row.label.getText().contains("%"));
            assertTrue(slider.getToolTipText().startsWith("Randomization: 30%."));
            assertNull(((BorderLayout) fixture.row.panel.getLayout()).getLayoutComponent(BorderLayout.EAST));
            assertFalse(slider.isEnabled());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, slider.getForeground());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, fixture.row.label.getForeground());
            assertSame(fixture.row.panel, fixture.row.label.getParent());
            assertSame(popup, fixture.row.label.getComponentPopupMenu());
            assertTrue(fixture.itemIndex().containsKey(fixture.row.panel));
            assertNull(fixture.row.spinner.getParent());
            settings.refresh();
            assertTrue(fixture.saved.isEmpty());
            assertEquals(0, fixture.row.nativeChanges.get());
            settings.close();
            assertSame(fixture.row.panel, fixture.row.spinner.getParent());
            assertTrue(sliders(fixture.root).isEmpty());
        });
    }

    @Test
    void dragCommitsFinalPercentageOnceAndKeyboardChangesKeepExactValue() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            JSlider slider = slider(fixture.root);
            slider.setValueIsAdjusting(true);
            slider.setValue(45);
            slider.setValue(100);
            assertTrue(slider.getToolTipText().startsWith("Randomization: 100%."));
            assertTrue(fixture.saved.isEmpty());
            slider.setValueIsAdjusting(false);
            assertEquals(Collections.singletonList(100), fixture.saved);
            slider.setValueIsAdjusting(true);
            slider.setValueIsAdjusting(false);
            assertEquals(Collections.singletonList(100), fixture.saved);
            slider.setValue(37);
            slider.setValue(0);
            assertEquals(Arrays.asList(100, 37, 0), fixture.saved);
            settings.close();
            assertEquals(0, fixture.row.spinner.getValue());
            assertEquals(0, ((JSpinner.DefaultEditor) fixture.row.spinner.getEditor()).getTextField().getValue());
            assertEquals(0, fixture.row.nativeChanges.get(), "Restoration must not trigger native preference saves");
        });
    }

    @Test
    void arrowAndPageKeysUseOnePercentAndDraggingKeepsEveryInteger() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            try (WaitingMouseSettings settings = fixture.settings()) {
                settings.start();
                JSlider slider = slider(fixture.root);
                for (int key : new int[]{KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_PAGE_UP}) {
                    int before = slider.getValue();
                    pressKey(slider, key);
                    assertEquals(before + 1, slider.getValue(), "Arrow/page increments must be exactly 1%");
                    assertEquals(slider.getValue(), fixture.config.frequency);
                }
                for (int key : new int[]{KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_DOWN}) {
                    int before = slider.getValue();
                    pressKey(slider, key);
                    assertEquals(before - 1, slider.getValue(), "Arrow/page decrements must be exactly 1%");
                    assertEquals(slider.getValue(), fixture.config.frequency);
                }
                for (int value = 0; value <= 100; value++) {
                    int saves = fixture.saved.size();
                    slider.setValueIsAdjusting(true);
                    slider.setValue(value);
                    assertEquals(value, slider.getValue(), "Every integer must remain selectable during a drag");
                    assertEquals(saves, fixture.saved.size(), "A drag must wait for release before saving");
                    slider.setValueIsAdjusting(false);
                    assertEquals(value, fixture.config.frequency);
                    assertTrue(slider.getToolTipText().startsWith("Randomization: " + value + "%."));
                }
                pressKey(slider, KeyEvent.VK_RIGHT);
                pressKey(slider, KeyEvent.VK_PAGE_UP);
                assertEquals(100, slider.getValue());
                slider.setValue(0);
                pressKey(slider, KeyEvent.VK_LEFT);
                pressKey(slider, KeyEvent.VK_PAGE_DOWN);
                assertEquals(0, slider.getValue());
            }
        });
    }

    @Test
    void restartingTheAdapterRetainsTheSavedIntegerWithoutWritingOnAttachment() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            JSlider previous;
            try (WaitingMouseSettings settings = fixture.settings()) {
                settings.start();
                previous = slider(fixture.root);
                previous.setValue(43);
                assertEquals(Collections.singletonList(43), fixture.saved);
            }
            try (WaitingMouseSettings settings = fixture.settings()) {
                settings.start();
                JSlider replacement = slider(fixture.root);
                assertNotSame(previous, replacement);
                assertEquals(43, replacement.getValue());
                settings.refresh();
                assertEquals(Collections.singletonList(43), fixture.saved, "Attachment/refresh must not reset or save config");
                previous.setValue(0);
                assertEquals(43, fixture.config.frequency, "The stopped adapter cannot overwrite persisted config");
                pressKey(replacement, KeyEvent.VK_RIGHT);
                assertEquals(44, fixture.config.frequency);
                assertEquals(Arrays.asList(43, 44), fixture.saved);
            }
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void suppliedAuthorityDrivesPresetAndFatigueValuesWithoutSavingOrUsingTheRawPreference() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            int[] effective = {25};
            boolean[] editable = {true};
            String[] description = {"AFK preset."};
            try (WaitingMouseSettings settings = fixture.effectiveSettings(
                () -> effective[0], () -> editable[0], () -> description[0], fixture.saved::add)) {
                settings.start();
                JSlider slider = slider(fixture.root);
                assertEquals(25, slider.getValue());
                assertTrue(slider.getToolTipText().contains("AFK preset."));
                effective[0] = 75;
                description[0] = "Attentive Human preset.";
                settings.refreshValue();
                assertEquals(75, slider.getValue());
                effective[0] = 42;
                editable[0] = false;
                description[0] = "Day fatigue at 09:00. Read-only while the automatic preset is selected.";
                settings.refresh();
                assertEquals(42, slider.getValue());
                assertFalse(slider.isEnabled());
                assertFalse(fixture.row.label.isEnabled());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, slider.getForeground());
                assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, fixture.row.label.getForeground());
                assertTrue(slider.getToolTipText().contains("Read-only"));
                assertEquals(slider.getToolTipText(), slider.getAccessibleContext().getAccessibleDescription());
                slider.setValue(90);
                assertEquals(42, slider.getValue());
                fixture.config.enabled = false;
                editable[0] = true;
                settings.refreshValue();
                assertFalse(slider.isEnabled(), "The master setting takes precedence over editability");
                assertTrue(fixture.saved.isEmpty());
                assertEquals(30, fixture.config.frequency);
            }
            assertEquals(30, fixture.row.spinner.getValue(), "The restored native control represents its stored manual preference");
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void passiveRefreshDefersDuringDraggingButChangedAuthorityRejectsAStaleRelease() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        int[] effective = {30};
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            settings[0] = fixture[0].effectiveSettings(() -> effective[0], () -> true,
                () -> "Custom randomization.", value -> {
                    fixture[0].saved.add(value);
                    effective[0] = value;
                });
            settings[0].start();
        });
        try {
            onEdt(() -> {
                JSlider slider = slider(fixture[0].root);
                slider.setValueIsAdjusting(true);
                slider.setValue(61);
                effective[0] = 70;
                settings[0].refreshValue();
                assertTrue(slider.getValueIsAdjusting());
                assertEquals(61, slider.getValue());
                assertTrue(fixture[0].saved.isEmpty());
                slider.setValueIsAdjusting(false);
                assertTrue(fixture[0].saved.isEmpty());
                assertEquals(70, effective[0]);
                assertEquals(70, slider.getValue());
                slider.setValueIsAdjusting(true);
                slider.setValue(73);
                SwingUtilities.invokeLater(settings[0]::refreshValue);
                slider.setValueIsAdjusting(false);
                assertEquals(Collections.singletonList(73), fixture[0].saved);
            });
            flushEvents();
            onEdt(() -> {
                assertEquals(73, slider(fixture[0].root).getValue(), "The queued refresh reads the latest authority after release");
                assertEquals(Collections.singletonList(73), fixture[0].saved);
                assertEquals(0, fixture[0].row.nativeChanges.get());
            });
        } finally {
            onEdt(settings[0]::close);
        }
    }

    @Test
    void explicitRefreshCancelsPresetDragsAndNonDragChangesRejectAStaleAuthority() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            int[] effective = {25};
            try (WaitingMouseSettings settings = fixture.effectiveSettings(
                () -> effective[0], () -> true, () -> "AFK preset.", fixture.saved::add)) {
                settings.start();
                JSlider slider = slider(fixture.root);
                slider.setValueIsAdjusting(true);
                slider.setValue(90);
                settings.refresh();
                assertFalse(slider.getValueIsAdjusting());
                assertEquals(25, slider.getValue());
                assertTrue(fixture.saved.isEmpty());
                effective[0] = 50;
                pressKey(slider, KeyEvent.VK_RIGHT);
                assertEquals(50, slider.getValue(), "A stale keyboard/model event cannot overwrite newer authority");
                assertTrue(fixture.saved.isEmpty());
            }
        });
    }

    @Test
    void fixedPresetRejectsPreEventProfileChangesEvenWhenEffectiveFrequencyStaysTheSame() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            fixture.config.frequency = 77;
            try (WaitingMouseSettings settings = fixture.effectiveSettings(
                () -> 25, () -> true, () -> "AFK preset.", fixture.saved::add)) {
                settings.start();
                JSlider slider = slider(fixture.root);
                slider.setValueIsAdjusting(true);
                slider.setValue(90);
                fixture.config.frequency = 63;
                // ProfileChanged has not reached the adapter yet; the raw preference already differs.
                slider.setValueIsAdjusting(false);
                assertEquals(25, slider.getValue());
                assertTrue(fixture.saved.isEmpty());
            }
            assertEquals(63, fixture.row.spinner.getValue());
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void disablingCancelsDragDimsLabelsAndRejectsChangesWithoutLosingPercentage() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            JSlider slider = slider(fixture.root);
            slider.setValueIsAdjusting(true);
            slider.setValue(90);
            fixture.config.enabled = false;
            settings.refresh();
            assertEquals(30, slider.getValue());
            assertFalse(slider.getValueIsAdjusting());
            assertFalse(slider.isEnabled());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, slider.getForeground());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, fixture.row.label.getForeground());
            assertNull(((BorderLayout) fixture.row.panel.getLayout()).getLayoutComponent(BorderLayout.EAST));
            assertFalse(slider.getPaintLabels());
            assertFalse(slider.getPaintTicks());
            slider.setValue(5);
            slider.setValueIsAdjusting(false);
            assertEquals(30, slider.getValue());
            assertTrue(fixture.saved.isEmpty());
            fixture.config.enabled = true;
            settings.refresh();
            assertTrue(slider.isEnabled());
            assertEquals(ColorScheme.BRAND_ORANGE, slider.getForeground());
            assertEquals(30, slider.getValue());
            settings.close();
        });
    }

    @Test
    void externalConfigChangeCannotBeOverwrittenByAStaleDrag() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            JSlider slider = slider(fixture.root);
            slider.setValueIsAdjusting(true);
            slider.setValue(90);
            fixture.config.frequency = 55;
            // The new profile/config value is checked even before its refresh event arrives.
            slider.setValueIsAdjusting(false);
            assertEquals(55, slider.getValue());
            assertTrue(fixture.saved.isEmpty());
            fixture.config.frequency = 140;
            settings.refresh();
            assertEquals(100, slider.getValue());
            fixture.config.frequency = -1;
            settings.refresh();
            assertEquals(0, slider.getValue());
            assertTrue(fixture.saved.isEmpty());
            settings.close();
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void profileNotificationCancelsADragEvenWhenTheNewFrequencyMatches() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            settings[0] = fixture[0].settings();
            field(FlipperPlugin.class, "waitingMouseSettings").set(fixture[0].owner, settings[0]);
            settings[0].start();
            JSlider slider = slider(fixture[0].root);
            slider.setValueIsAdjusting(true);
            slider.setValue(90);
        });
        try {
            // The profile event itself carries no value used by GEFlipper.
            fixture[0].owner.onProfileChanged(null);
            onEdt(() -> {
                JSlider slider = slider(fixture[0].root);
                assertFalse(slider.getValueIsAdjusting());
                assertEquals(30, slider.getValue());
                slider.setValueIsAdjusting(false);
                assertTrue(fixture[0].saved.isEmpty());
            });
        } finally {
            onEdt(() -> {
                field(FlipperPlugin.class, "waitingMouseSettings").set(fixture[0].owner, null);
                settings[0].close();
            });
        }
    }

    @Test
    void rebuiltRowsCannotSaveFromTheirOldSliderAndTheNewRowWorks() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            JSlider oldSlider = slider(fixture.root);
            Row original = fixture.row;
            oldSlider.setValueIsAdjusting(true);
            oldSlider.setValue(90);
            fixture.main.removeAll();
            fixture.row = new Row();
            fixture.main.add(fixture.row.panel);
            oldSlider.setValueIsAdjusting(false);
            assertTrue(fixture.saved.isEmpty());
            assertFalse(oldSlider.isEnabled());
            settings.refresh();
            JSlider newSlider = slider(fixture.root);
            assertNotSame(oldSlider, newSlider);
            oldSlider.setValue(0);
            assertTrue(fixture.saved.isEmpty());
            newSlider.setValue(65);
            assertEquals(Collections.singletonList(65), fixture.saved);
            settings.close();
            assertEquals(0, original.nativeChanges.get());
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void anotherPluginOrConfigGroupWithTheSameLabelIsNeverModified() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.setDescriptor(new FlipperPlugin(), FlipperConfig.class.getAnnotation(ConfigGroup.class));
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            assertTrue(sliders(fixture.root).isEmpty());
            assertSame(fixture.row.panel, fixture.row.spinner.getParent());
            fixture.setDescriptor(fixture.owner, OtherConfig.class.getAnnotation(ConfigGroup.class));
            settings.refresh();
            assertTrue(sliders(fixture.root).isEmpty());
            assertSame(fixture.row.panel, fixture.row.spinner.getParent());
            fixture.setDescriptor(fixture.owner, FlipperConfig.class.getAnnotation(ConfigGroup.class));
            settings.refresh();
            assertEquals(1, sliders(fixture.root).size());
            settings.close();
            assertTrue(fixture.saved.isEmpty());
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void unsupportedRowRetainsTheNativeIntegerControl() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.row.spinner.setModel(new SpinnerNumberModel(30, 0, 1000, 1));
            fixture.row.nativeChanges.set(0);
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            assertTrue(sliders(fixture.root).isEmpty());
            assertSame(fixture.row.panel, fixture.row.spinner.getParent());
            settings.close();
            assertEquals(0, fixture.row.nativeChanges.get());
        });
    }

    @Test
    void hiddenOrDisposedControlsCannotSaveAndCloseRemovesObserversAndRestoresListeners() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.config.enabled = true;
            int rootContainerListeners = fixture.root.getContainerListeners().length;
            int rootComponentListeners = fixture.root.getComponentListeners().length;
            int rootHierarchyListeners = fixture.root.getHierarchyListeners().length;
            int panelContainerListeners = fixture.panel.getContainerListeners().length;
            WaitingMouseSettings settings = fixture.settings();
            settings.start();
            assertEquals(rootContainerListeners + 1, fixture.root.getContainerListeners().length);
            assertEquals(rootHierarchyListeners + 1, fixture.root.getHierarchyListeners().length);
            JSlider slider = slider(fixture.root);
            slider.setValueIsAdjusting(true);
            slider.setValue(90);
            fixture.root.setVisible(false);
            slider.setValueIsAdjusting(false);
            assertTrue(fixture.saved.isEmpty());
            fixture.root.setVisible(true);
            settings.refresh();
            slider = slider(fixture.root);
            fixture.config.frequency = 55;
            settings.close();
            settings.close();
            settings.start();
            settings.refresh();
            slider.setValue(0);
            assertTrue(fixture.saved.isEmpty());
            assertEquals(rootContainerListeners, fixture.root.getContainerListeners().length);
            assertEquals(rootComponentListeners, fixture.root.getComponentListeners().length);
            assertEquals(rootHierarchyListeners, fixture.root.getHierarchyListeners().length);
            assertEquals(panelContainerListeners, fixture.panel.getContainerListeners().length);
            assertTrue(((java.util.Set<?>) field(WaitingMouseSettings.class, "observed").get(settings)).isEmpty());
            assertNull(field(WaitingMouseSettings.class, "settingsRoot").get(settings));
            assertNull(field(WaitingMouseSettings.class, "owner").get(settings));
            assertNull(field(WaitingMouseSettings.class, "config").get(settings));
            assertNull(field(WaitingMouseSettings.class, "frequencyChanged").get(settings));
            assertNull(field(WaitingMouseSettings.class, "frequencyValue").get(settings));
            assertNull(field(WaitingMouseSettings.class, "sliderEditable").get(settings));
            assertNull(field(WaitingMouseSettings.class, "frequencyDescription").get(settings));
            assertEquals(55, fixture.row.spinner.getValue());
            assertEquals(55, ((JSpinner.DefaultEditor) fixture.row.spinner.getEditor()).getTextField().getValue());
            assertEquals(0, fixture.row.nativeChanges.get());
            fixture.row.spinner.setValue(56);
            assertEquals(1, fixture.row.nativeChanges.get(), "Native listeners are restored for later user edits");
        });
    }

    @Test
    void openingAndRebuildingSettingsAutomaticallyAttachesWithoutPolling() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        JSlider[] previous = new JSlider[1];
        Row[] original = new Row[1];
        int[] originalListenerCount = new int[1];
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            fixture[0].root.remove(fixture[0].panel);
            settings[0] = fixture[0].settings();
            settings[0].start();
            assertTrue(sliders(fixture[0].root).isEmpty());
            fixture[0].root.add(fixture[0].panel, BorderLayout.CENTER);
        });
        flushEvents();
        try {
            onEdt(() -> {
                previous[0] = slider(fixture[0].root);
                previous[0].setValueIsAdjusting(true);
                previous[0].setValue(90);
                original[0] = fixture[0].row;
                originalListenerCount[0] = original[0].panel.getContainerListeners().length;
                fixture[0].main.removeAll();
                fixture[0].row = new Row();
                fixture[0].main.add(fixture[0].row.panel);
                assertEquals(originalListenerCount[0] - 1, original[0].panel.getContainerListeners().length,
                    "Removed rows must release their observers immediately");
            });
            flushEvents();
            onEdt(() -> {
                JSlider replacement = slider(fixture[0].root);
                assertNotSame(previous[0], replacement);
                assertFalse(previous[0].isEnabled());
                previous[0].setValueIsAdjusting(false);
                previous[0].setValue(0);
                assertTrue(fixture[0].saved.isEmpty());
                replacement.setValue(65);
                assertEquals(Collections.singletonList(65), fixture[0].saved);
                assertEquals(0, original[0].nativeChanges.get());
            });
        } finally {
            onEdt(settings[0]::close);
        }
    }

    @Test
    void hidingAndShowingRowsAutomaticallyDetachesAndRestoresWithoutCancellingOtherDrags() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        JSlider[] previous = new JSlider[1];
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            settings[0] = fixture[0].settings();
            settings[0].start();
            previous[0] = slider(fixture[0].root);
            previous[0].setValueIsAdjusting(true);
            previous[0].setValue(90);
            fixture[0].main.add(new JLabel("Unrelated row"));
        });
        flushEvents();
        try {
            onEdt(() -> {
                assertTrue(previous[0].getValueIsAdjusting(), "Unrelated UI changes must preserve a drag");
                assertEquals(90, previous[0].getValue());
                fixture[0].row.panel.setVisible(false);
            });
            flushEvents();
            onEdt(() -> {
                assertFalse(previous[0].isEnabled());
                assertTrue(sliders(fixture[0].root).isEmpty());
                assertSame(fixture[0].row.panel, fixture[0].row.spinner.getParent());
                previous[0].setValueIsAdjusting(false);
                assertTrue(fixture[0].saved.isEmpty());
                fixture[0].row.panel.setVisible(true);
            });
            flushEvents();
            onEdt(() -> {
                JSlider replacement = slider(fixture[0].root);
                assertNotSame(previous[0], replacement);
                assertEquals(30, replacement.getValue());
                assertTrue(fixture[0].saved.isEmpty());
                assertEquals(0, fixture[0].row.nativeChanges.get());
            });
        } finally {
            onEdt(settings[0]::close);
        }
    }

    @Test
    void closingWithAQueuedRebuildNeverReattachesOrKeepsComponentReferences() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            settings[0] = fixture[0].settings();
            settings[0].start();
            fixture[0].main.removeAll();
            fixture[0].row = new Row();
            fixture[0].main.add(fixture[0].row.panel);
            assertTrue((Boolean) field(WaitingMouseSettings.class, "scanQueued").get(settings[0]));
            settings[0].close();
        });
        flushEvents();
        onEdt(() -> {
            assertTrue(sliders(fixture[0].root).isEmpty());
            assertSame(fixture[0].row.panel, fixture[0].row.spinner.getParent());
            assertTrue(((java.util.Set<?>) field(WaitingMouseSettings.class, "observed").get(settings[0])).isEmpty());
            assertFalse((Boolean) field(WaitingMouseSettings.class, "scanQueued").get(settings[0]));
            assertNull(field(WaitingMouseSettings.class, "settingsRoot").get(settings[0]));
            assertTrue(fixture[0].saved.isEmpty());
            assertEquals(0, fixture[0].row.nativeChanges.get());
        });
    }

    @Test
    void hidingAnAncestorRejectsDragReleaseBeforeTheQueuedScan() throws Exception {
        Fixture[] fixture = new Fixture[1];
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        JSlider[] previous = new JSlider[1];
        onEdt(() -> {
            fixture[0] = new Fixture();
            fixture[0].config.enabled = true;
            settings[0] = fixture[0].settings();
            settings[0].start();
            previous[0] = slider(fixture[0].root);
            previous[0].setValueIsAdjusting(true);
            previous[0].setValue(90);
            fixture[0].root.showing(false);
            assertTrue(fixture[0].root.isVisible(), "Ancestor hiding does not change this panel's visible flag");
            assertFalse(fixture[0].root.isShowing());
            // A queued hierarchy scan has not run yet; this stale event must already be rejected.
            previous[0].setValueIsAdjusting(false);
            previous[0].setValue(0);
            assertTrue(fixture[0].saved.isEmpty());
            assertFalse(previous[0].isEnabled());
        });
        flushEvents();
        try {
            onEdt(() -> {
                assertTrue(sliders(fixture[0].root).isEmpty());
                assertSame(fixture[0].row.panel, fixture[0].row.spinner.getParent());
                assertEquals(30, fixture[0].row.spinner.getValue());
                assertEquals(0, fixture[0].row.nativeChanges.get());
                fixture[0].root.showing(true);
            });
            flushEvents();
            onEdt(() -> {
                JSlider replacement = slider(fixture[0].root);
                assertNotSame(previous[0], replacement);
                assertEquals(30, replacement.getValue());
                assertTrue(replacement.isEnabled());
                assertTrue(fixture[0].saved.isEmpty());
            });
        } finally {
            onEdt(settings[0]::close);
        }
    }

    @Test
    void lifecycleRequiresTheEdt() throws Exception {
        assertThrows(IllegalStateException.class, () -> new WaitingMouseSettings(
            new FlipperPlugin(), new MutableConfig(), new JPanel(), ignored -> {}));
        WaitingMouseSettings[] settings = new WaitingMouseSettings[1];
        onEdt(() -> settings[0] = new Fixture().settings());
        assertThrows(IllegalStateException.class, settings[0]::start);
        assertThrows(IllegalStateException.class, settings[0]::refresh);
        assertThrows(IllegalStateException.class, settings[0]::refreshValue);
        assertThrows(IllegalStateException.class, settings[0]::close);
        onEdt(settings[0]::close);
    }

    @Test
    void finishClickIsDirectOnceOnlyAndRestoresNativeListenersOnClose() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            AtomicInteger requests = new AtomicInteger();
            JButton button = fixture.finishRow.button;
            java.awt.event.ActionListener[] nativeListeners = button.getActionListeners();
            Color original = button.getForeground();
            WaitingMouseSettings settings = fixture.finishSettings(requests::incrementAndGet);
            settings.start();
            assertTrue(button.isEnabled());
            button.doClick(0);
            assertEquals(1, requests.get());
            assertEquals(0, fixture.finishRow.nativeChanges.get(), "Explicit finish does not store a command preference");
            assertFalse(button.isEnabled());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, button.getForeground());
            button.doClick(0);
            settings.refresh();
            assertEquals(1, requests.get());
            assertFalse(button.isEnabled(), "A refresh cannot re-arm an accepted finish request");
            settings.close();
            assertTrue(button.isEnabled());
            assertEquals(original, button.getForeground());
            assertArrayEquals(nativeListeners, button.getActionListeners());
            assertNull(field(WaitingMouseSettings.class, "finishAction").get(settings));
            assertNull(field(WaitingMouseSettings.class, "finishAvailable").get(settings));
            assertNull(field(WaitingMouseSettings.class, "finishBinding").get(settings));
            button.doClick(0);
            assertEquals(1, requests.get(), "Closing restores SDK behavior without retaining a finish callback");
            assertEquals(1, fixture.finishRow.nativeChanges.get());
        });
    }

    @Test
    void finishRejectsUnavailableHiddenAndUnownedControlsBeforeQueuedScans() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            AtomicInteger requests = new AtomicInteger();
            fixture.finishAvailable = false;
            WaitingMouseSettings settings = fixture.finishSettings(requests::incrementAndGet);
            settings.start();
            JButton button = fixture.finishRow.button;
            assertFalse(button.isEnabled());
            assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, button.getForeground());
            button.doClick(0);
            assertEquals(0, requests.get());
            fixture.finishAvailable = true;
            settings.refresh();
            assertTrue(button.isEnabled());
            fixture.root.showing(false);
            button.doClick(0);
            assertEquals(0, requests.get());
            assertEquals(0, fixture.finishRow.nativeChanges.get());
            fixture.root.showing(true);
            settings.refresh();
            fixture.setDescriptor(new FlipperPlugin(), FlipperConfig.class.getAnnotation(ConfigGroup.class));
            button.doClick(0);
            assertEquals(0, requests.get());
            assertEquals(0, fixture.finishRow.nativeChanges.get());
            settings.close();
        });
    }

    @Test
    void detachedFinishButtonCannotRequestAndNewRowPreservesAcceptedState() throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture();
            AtomicInteger requests = new AtomicInteger();
            WaitingMouseSettings settings = fixture.finishSettings(requests::incrementAndGet);
            settings.start();
            JButton stale = fixture.finishRow.button;
            fixture.main.remove(fixture.finishRow.panel);
            fixture.finishRow = new FinishRow();
            fixture.main.add(fixture.finishRow.panel);
            stale.doClick(0);
            assertEquals(0, requests.get());
            settings.refresh();
            fixture.finishRow.button.doClick(0);
            assertEquals(1, requests.get());
            fixture.main.remove(fixture.finishRow.panel);
            fixture.finishRow = new FinishRow();
            fixture.main.add(fixture.finishRow.panel);
            settings.refresh();
            assertFalse(fixture.finishRow.button.isEnabled());
            fixture.finishRow.button.doClick(0);
            assertEquals(1, requests.get(), "Rebuilding the panel does not repeat a finish request");
            settings.close();
        });
    }

    private static void onEdt(CheckedRunnable task) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { task.run(); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static void flushEvents() throws Exception {
        // Component visibility events and the resulting coalesced scan use consecutive EDT turns.
        for (int i = 0; i < 3; i++) onEdt(() -> {});
    }

    private interface CheckedRunnable { void run() throws Exception; }

    private static void pressKey(JSlider slider, int key) {
        Object binding = slider.getInputMap().get(KeyStroke.getKeyStroke(key, 0));
        assertNotNull(binding, "Use the slider's real keyboard binding");
        Action action = slider.getActionMap().get(binding);
        assertNotNull(action);
        action.actionPerformed(new ActionEvent(slider, ActionEvent.ACTION_PERFORMED, binding.toString()));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static JSlider slider(Container root) {
        List<JSlider> sliders = sliders(root);
        assertEquals(1, sliders.size());
        return sliders.get(0);
    }

    private static List<JSlider> sliders(Container root) {
        List<JSlider> found = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof JSlider) found.add((JSlider) component);
            if (component instanceof Container) found.addAll(sliders((Container) component));
        }
        return found;
    }

    private static final class Fixture {
        private final FlipperPlugin owner = new FlipperPlugin();
        private final MutableConfig config = new MutableConfig();
        private final ShowingPanel root = new ShowingPanel();
        private final JPanel panel;
        private final JPanel main;
        private final List<Integer> saved = new ArrayList<>();
        private Row row = new Row();
        private FinishRow finishRow = new FinishRow();
        private boolean finishAvailable = true;

        private Fixture() throws Exception {
            Class<?> type = Class.forName("net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel");
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            panel = (JPanel) constructor.newInstance(new Object[constructor.getParameterCount()]);
            main = (JPanel) field(type, "mainPanel").get(panel);
            main.removeAll();
            main.add(row.panel);
            main.add(finishRow.panel);
            root.add(panel, BorderLayout.CENTER);
            itemIndex().put(row.panel, row.label.getText().toLowerCase(java.util.Locale.ROOT));
            setDescriptor(owner, FlipperConfig.class.getAnnotation(ConfigGroup.class));
        }

        private WaitingMouseSettings settings() {
            return new WaitingMouseSettings(owner, config, root, frequency -> {
                saved.add(frequency);
                config.frequency = frequency;
            });
        }

        private WaitingMouseSettings finishSettings(Runnable action) {
            return new WaitingMouseSettings(owner, config, root, frequency -> {
                saved.add(frequency);
                config.frequency = frequency;
            }, action, () -> finishAvailable);
        }

        private WaitingMouseSettings effectiveSettings(IntSupplier frequency, BooleanSupplier editable,
                                                        Supplier<String> description, IntConsumer changed) {
            return new WaitingMouseSettings(owner, config, root, changed, null, null,
                frequency, editable, description);
        }

        private void setDescriptor(FlipperPlugin plugin, ConfigGroup group) throws Exception {
            ConfigItem item = FlipperConfig.class.getMethod("waitingMouseChance").getAnnotation(ConfigItem.class);
            Range range = FlipperConfig.class.getMethod("waitingMouseChance").getAnnotation(Range.class);
            ConfigDescriptor descriptor = new ConfigDescriptor(group, Collections.emptyList(),
                Arrays.asList(new ConfigItemDescriptor(item, int.class, range, null, null),
                    new ConfigItemDescriptor(FlipperConfig.class.getMethod("finish").getAnnotation(ConfigItem.class),
                        ConfigButton.class, null, null, null)), null);
            field(panel.getClass(), "pluginConfig").set(panel,
                new MicrobotPluginConfigurationDescriptor("Flipper", "", new String[0], plugin, config,
                    descriptor, Collections.emptyList()));
        }

        @SuppressWarnings("unchecked") private Map<JPanel, String> itemIndex() throws Exception {
            return (Map<JPanel, String>) field(panel.getClass(), "itemIndex").get(panel);
        }
    }

    private static final class ShowingPanel extends JPanel {
        private boolean showing = true;

        private ShowingPanel() { super(new BorderLayout()); }

        @Override public boolean isShowing() { return showing && isVisible(); }

        private void showing(boolean value) {
            showing = value;
            dispatchEvent(new HierarchyEvent(this, HierarchyEvent.HIERARCHY_CHANGED,
                this, getParent(), HierarchyEvent.SHOWING_CHANGED));
        }
    }

    private static final class Row {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel label;
        private final JSpinner spinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        private final AtomicInteger nativeChanges = new AtomicInteger();

        private Row() {
            try {
                label = new JLabel(FlipperConfig.class.getMethod("waitingMouseChance").getAnnotation(ConfigItem.class).name());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            label.setForeground(ColorScheme.TEXT_COLOR);
            JPopupMenu popup = new JPopupMenu();
            popup.add(new JMenuItem("Reset"));
            label.setComponentPopupMenu(popup);
            spinner.addChangeListener(event -> nativeChanges.incrementAndGet());
            panel.add(label, BorderLayout.CENTER);
            panel.add(spinner, BorderLayout.EAST);
        }
    }

    private static final class FinishRow {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JButton button;
        private final AtomicInteger nativeChanges = new AtomicInteger();

        private FinishRow() { this("End / Finish"); }

        private FinishRow(String name) {
            button = new JButton(name);
            button.addActionListener(event -> nativeChanges.incrementAndGet());
            panel.add(button, BorderLayout.CENTER);
        }
    }

    private static final class MutableConfig implements FlipperConfig {
        private boolean enabled;
        private int frequency = 30;
        @Override public boolean waitingMouseOffScreen() { return enabled; }
        @Override public int waitingMouseChance() { return frequency; }
    }

    @ConfigGroup("Other plugin") private interface OtherConfig extends FlipperConfig {}
}
