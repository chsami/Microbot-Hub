package net.runelite.client.plugins.microbot.geflipper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.ui.ColorScheme;

/** Binds this plugin's native settings controls without changing client UI or preferences. */
final class WaitingMouseSettings implements AutoCloseable {
    private static final String PANEL_CLASS = "net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel";
    private static final String GROUP = "Flipper Config";
    private static final String FREQUENCY_KEY = "waitingMouseChance";
    private static final String FINISH_KEY = "finish";
    private FlipperPlugin owner;
    private FlipperConfig config;
    private JPanel settingsRoot;
    private IntConsumer frequencyChanged;
    private Runnable finishAction;
    private BooleanSupplier finishAvailable;
    private final Set<Container> observed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ContainerAdapter treeChanges = new ContainerAdapter() {
        @Override public void componentAdded(ContainerEvent event) {
            observe(event.getChild());
            queueScan();
        }
        @Override public void componentRemoved(ContainerEvent event) {
            unobserve(event.getChild());
            queueScan();
        }
    };
    private final ComponentAdapter visibilityChanges = new ComponentAdapter() {
        @Override public void componentShown(ComponentEvent event) { queueScan(); }
        @Override public void componentHidden(ComponentEvent event) { queueScan(); }
    };
    private final HierarchyListener rootChanges = event -> {
        long relevant = HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED
            | HierarchyEvent.PARENT_CHANGED;
        if ((event.getChangeFlags() & relevant) != 0) queueScan();
    };
    private Binding binding;
    private FinishBinding finishBinding;
    private boolean finishRequested;
    private boolean started;
    private boolean closed;
    private boolean scanQueued;

    WaitingMouseSettings(FlipperPlugin owner, FlipperConfig config, JPanel settingsRoot,
                         IntConsumer frequencyChanged) {
        this(owner, config, settingsRoot, frequencyChanged, null, null);
    }

    WaitingMouseSettings(FlipperPlugin owner, FlipperConfig config, JPanel settingsRoot,
                         IntConsumer frequencyChanged, Runnable finishAction, BooleanSupplier finishAvailable) {
        requireEdt();
        this.owner = Objects.requireNonNull(owner);
        this.config = Objects.requireNonNull(config);
        this.settingsRoot = Objects.requireNonNull(settingsRoot);
        this.frequencyChanged = Objects.requireNonNull(frequencyChanged);
        if ((finishAction == null) != (finishAvailable == null)) {
            throw new IllegalArgumentException("Finish action and availability must be supplied together");
        }
        this.finishAction = finishAction;
        this.finishAvailable = finishAvailable;
    }

    void start() {
        requireEdt();
        if (closed || started) return;
        started = true;
        settingsRoot.addHierarchyListener(rootChanges);
        observe(settingsRoot);
        scan(false);
    }

    /** Configuration/profile events cancel any pending drag; UI tree events never do. */
    void refresh() {
        requireEdt();
        if (!closed && started) scan(true);
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        scanQueued = false;
        settingsRoot.removeHierarchyListener(rootChanges);
        unobserve(settingsRoot);
        detach();
        detachFinish();
        settingsRoot = null;
        owner = null;
        config = null;
        frequencyChanged = null;
        finishAction = null;
        finishAvailable = null;
    }

    private void observe(Component component) {
        if (closed || !(component instanceof Container)) return;
        Container container = (Container) component;
        if (!observed.add(container)) return;
        container.addContainerListener(treeChanges);
        container.addComponentListener(visibilityChanges);
        for (Component child : container.getComponents()) observe(child);
    }

    private void unobserve(Component component) {
        if (!(component instanceof Container)) return;
        Container container = (Container) component;
        if (!observed.remove(container)) return;
        container.removeContainerListener(treeChanges);
        container.removeComponentListener(visibilityChanges);
        for (Component child : container.getComponents()) unobserve(child);
    }

    private void queueScan() {
        if (closed || !started || scanQueued) return;
        scanQueued = true;
        // The native panel may still be rebuilding, so inspect it after the current EDT task.
        SwingUtilities.invokeLater(() -> {
            scanQueued = false;
            if (!closed && started) scan(false);
        });
    }

    private void scan(boolean forceRefresh) {
        requireEdt();
        if (closed || !started) return;
        if (!settingsRoot.isShowing()) {
            detach();
            detachFinish();
            return;
        }
        JPanel panel = findOwnedPanel(settingsRoot);
        String itemName = frequencyName(descriptor(panel));
        JPanel row = panel == null || itemName == null ? null : findRow(panel, itemName);
        if (binding != null && (binding.panel != panel || binding.row != row || !binding.attached())) {
            detach();
        }
        if (binding == null && row != null) {
            BorderLayout layout = (BorderLayout) row.getLayout();
            Component label = layout.getLayoutComponent(BorderLayout.CENTER);
            Component spinner = layout.getLayoutComponent(BorderLayout.EAST);
            if (label instanceof JLabel && spinner instanceof JSpinner
                && frequencySpinner((JSpinner) spinner)
                && layout.getLayoutComponent(BorderLayout.SOUTH) == null) {
                binding = new Binding(panel, row, (JLabel) label, (JSpinner) spinner);
                binding.attach();
            }
        }
        if (binding != null) {
            int frequency = clamp(config.waitingMouseChance());
            boolean enabled = config.waitingMouseOffScreen();
            if (forceRefresh || frequency != binding.savedFrequency || enabled != binding.enabled) {
                binding.refresh(frequency, enabled);
            }
        }
        if (finishAction != null) bindFinish(panel);
    }

    private void bindFinish(JPanel panel) {
        String itemName = itemName(descriptor(panel), ConfigButton.class, FINISH_KEY);
        JPanel row = panel == null || itemName == null ? null : findRow(panel, itemName, true);
        if (finishBinding != null && (finishBinding.panel != panel || finishBinding.row != row
            || !finishBinding.attached())) detachFinish();
        if (finishBinding == null && row != null) {
            BorderLayout layout = (BorderLayout) row.getLayout();
            Component center = layout.getLayoutComponent(BorderLayout.CENTER);
            if (center instanceof JButton && layout.getLayoutComponent(BorderLayout.EAST) == null
                && layout.getLayoutComponent(BorderLayout.SOUTH) == null) {
                finishBinding = new FinishBinding(panel, row, (JButton) center);
                finishBinding.attach();
            }
        }
        if (finishBinding != null) finishBinding.refresh();
    }

    private void detach() {
        if (binding == null) return;
        Binding previous = binding;
        binding = null;
        previous.detach();
    }

    private void detachFinish() {
        if (finishBinding == null) return;
        FinishBinding previous = finishBinding;
        finishBinding = null;
        previous.detach();
    }

    private JPanel findOwnedPanel(Component component) {
        if (component == null || !component.isVisible()) return null;
        if (PANEL_CLASS.equals(component.getClass().getName())) {
            return owned(descriptor(component)) ? (JPanel) component : null;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JPanel found = findOwnedPanel(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean owned(MicrobotPluginConfigurationDescriptor descriptor) {
        ConfigDescriptor details = descriptor == null ? null : descriptor.getConfigDescriptor();
        return descriptor != null && descriptor.getPlugin() == owner && details != null
            && details.getGroup() != null && GROUP.equals(details.getGroup().value());
    }

    private MicrobotPluginConfigurationDescriptor descriptor(Component panel) {
        if (panel == null || !PANEL_CLASS.equals(panel.getClass().getName())) return null;
        try {
            // The native panel is package-private. Read only its descriptor to prove row ownership.
            Field field = panel.getClass().getDeclaredField("pluginConfig");
            field.setAccessible(true);
            Object value = field.get(panel);
            return value instanceof MicrobotPluginConfigurationDescriptor
                ? (MicrobotPluginConfigurationDescriptor) value : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // An unsupported client layout retains its normal integer control.
            return null;
        }
    }

    private String frequencyName(MicrobotPluginConfigurationDescriptor descriptor) {
        return itemName(descriptor, int.class, FREQUENCY_KEY);
    }

    private String itemName(MicrobotPluginConfigurationDescriptor descriptor, Class<?> type, String key) {
        if (!owned(descriptor) || descriptor.getConfigDescriptor().getItems() == null) return null;
        for (ConfigItemDescriptor item : descriptor.getConfigDescriptor().getItems()) {
            if (item != null && item.getItem() != null && item.getType() == type
                && key.equals(item.getItem().keyName()) && !item.getItem().hidden()) {
                return item.getItem().name();
            }
        }
        return null;
    }

    private static JPanel findRow(Container container, String itemName) {
        return findRow(container, itemName, false);
    }

    private static JPanel findRow(Container container, String itemName, boolean button) {
        if (!container.isVisible()) return null;
        if (container instanceof JPanel && container.getLayout() instanceof BorderLayout) {
            Component center = ((BorderLayout) container.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if ((!button && center instanceof JLabel && itemName.equals(((JLabel) center).getText()))
                || (button && center instanceof JButton && itemName.equals(((JButton) center).getText()))) {
                return (JPanel) container;
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                JPanel found = findRow((Container) child, itemName, button);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean frequencySpinner(JSpinner spinner) {
        if (!(spinner.getModel() instanceof SpinnerNumberModel)) return false;
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        return model.getMinimum() instanceof Number && model.getMaximum() instanceof Number
            && ((Number) model.getMinimum()).intValue() == 0
            && ((Number) model.getMaximum()).intValue() == 100;
    }

    private boolean visibleWithinRoot(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
            if (current == settingsRoot) return true;
        }
        return false;
    }

    private final class FinishBinding {
        private final JPanel panel;
        private final JPanel row;
        private final JButton button;
        private final ActionListener[] originalListeners;
        private final Color originalColor;
        private final boolean originalEnabled;
        private final ActionListener listener;
        private boolean active = true;

        private FinishBinding(JPanel panel, JPanel row, JButton button) {
            this.panel = panel;
            this.row = row;
            this.button = button;
            listener = event -> {
                if (event.getSource() == this.button) clicked();
            };
            originalListeners = button.getActionListeners();
            originalColor = button.getForeground();
            originalEnabled = button.isEnabled();
        }

        private void attach() {
            // Native buttons store a command UUID as a preference. Direct clicks need no stored command,
            // and profile/default ConfigChanged events must never start finishing trades.
            for (ActionListener action : originalListeners) button.removeActionListener(action);
            button.addActionListener(listener);
            refresh();
        }

        private boolean attached() {
            String name = itemName(descriptor(panel), ConfigButton.class, FINISH_KEY);
            return active && button.getParent() == row && row.getLayout() instanceof BorderLayout
                && ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER) == button
                && ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST) == null
                && ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.SOUTH) == null
                && SwingUtilities.isDescendingFrom(row, panel) && settingsRoot.isShowing()
                && visibleWithinRoot(button) && name != null && name.equals(button.getText());
        }

        private void refresh() {
            boolean available = !finishRequested && finishAvailable.getAsBoolean();
            button.setEnabled(available);
            button.setForeground(available ? originalColor : ColorScheme.MEDIUM_GRAY_COLOR);
        }

        private void clicked() {
            if (closed || !active || finishBinding != this) return;
            if (!attached()) {
                detachFinish();
                return;
            }
            if (finishRequested || !button.isEnabled() || !finishAvailable.getAsBoolean()) {
                refresh();
                return;
            }
            finishRequested = true;
            refresh();
            try {
                finishAction.run();
            } catch (RuntimeException failure) {
                finishRequested = false;
                refresh();
                throw failure;
            }
        }

        private void detach() {
            active = false;
            button.removeActionListener(listener);
            for (int i = originalListeners.length - 1; i >= 0; i--) button.addActionListener(originalListeners[i]);
            button.setEnabled(originalEnabled);
            button.setForeground(originalColor);
        }
    }

    private final class Binding {
        private final JPanel panel;
        private final JPanel row;
        private final JLabel label;
        private final JSpinner originalSpinner;
        private final Color originalLabelColor;
        private final boolean originalLabelEnabled;
        private final JSlider slider = new JSlider(0, 100);
        private final ChangeListener listener;
        private boolean refreshing;
        private boolean active = true;
        private int savedFrequency;
        private boolean enabled;

        private Binding(JPanel panel, JPanel row, JLabel label, JSpinner spinner) {
            this.panel = panel;
            this.row = row;
            this.label = label;
            originalSpinner = spinner;
            originalLabelColor = label.getForeground();
            originalLabelEnabled = label.isEnabled();
            slider.setOpaque(false);
            slider.setPaintTicks(false);
            slider.setPaintLabels(false);
            slider.getAccessibleContext().setAccessibleName(label.getText());
            slider.setToolTipText("Randomizes chance and waiting delays together. Move right for sooner, more frequent movement; fully left disables it.");
            listener = event -> changed();
            slider.addChangeListener(listener);
        }

        private void attach() {
            row.remove(originalSpinner);
            row.add(slider, BorderLayout.SOUTH);
            refresh(clamp(config.waitingMouseChance()), config.waitingMouseOffScreen());
            row.revalidate();
            row.repaint();
        }

        private boolean attached() {
            return active && slider.getParent() == row
                && label.getParent() == row && SwingUtilities.isDescendingFrom(row, panel)
                && settingsRoot.isShowing() && visibleWithinRoot(row) && owned(descriptor(panel));
        }

        private void refresh(int frequency, boolean isEnabled) {
            refreshing = true;
            try {
                slider.setValueIsAdjusting(false);
                slider.setValue(frequency);
                savedFrequency = frequency;
                enabled = isEnabled;
                slider.setEnabled(isEnabled);
                slider.setForeground(isEnabled ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
                label.setEnabled(isEnabled);
                label.setForeground(isEnabled ? originalLabelColor : ColorScheme.MEDIUM_GRAY_COLOR);
            } finally {
                refreshing = false;
            }
        }

        private void changed() {
            if (closed || !active || refreshing || binding != this) return;
            if (!attached() || !panel.isVisible() || !row.isVisible()) {
                WaitingMouseSettings.this.detach();
                return;
            }
            int current = clamp(config.waitingMouseChance());
            boolean isEnabled = config.waitingMouseOffScreen();
            if (!isEnabled || !slider.isEnabled() || current != savedFrequency) {
                refresh(current, isEnabled);
                return;
            }
            if (!slider.getValueIsAdjusting() && slider.getValue() != savedFrequency) {
                savedFrequency = slider.getValue();
                frequencyChanged.accept(savedFrequency);
            }
        }

        private void detach() {
            active = false;
            slider.removeChangeListener(listener);
            slider.setValueIsAdjusting(false);
            slider.setEnabled(false);
            if (slider.getParent() == row) row.remove(slider);
            if (label.getParent() == row) {
                label.setForeground(originalLabelColor);
                label.setEnabled(originalLabelEnabled);
            }
            if (row.getLayout() instanceof BorderLayout && label.getParent() == row
                && ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST) == null
                && originalSpinner.getParent() == null) {
                // Refresh only this detached UI model. Native listeners would otherwise save a setting.
                ChangeListener[] listeners = originalSpinner.getChangeListeners();
                for (ChangeListener change : listeners) originalSpinner.removeChangeListener(change);
                try {
                    int frequency = clamp(config.waitingMouseChance());
                    originalSpinner.setValue(frequency);
                    if (originalSpinner.getEditor() instanceof JSpinner.DefaultEditor) {
                        ((JSpinner.DefaultEditor) originalSpinner.getEditor()).getTextField().setValue(frequency);
                    }
                } finally {
                    for (int i = listeners.length - 1; i >= 0; i--) originalSpinner.addChangeListener(listeners[i]);
                }
                row.add(originalSpinner, BorderLayout.EAST);
            }
            row.revalidate();
            row.repaint();
        }
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Waiting mouse settings must be updated on the EDT");
        }
    }
}
