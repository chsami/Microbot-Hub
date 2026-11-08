package net.runelite.client.plugins.microbot.geflipper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.ui.ColorScheme;

/**
 * How GE Flipper's own settings panel looks, whether GE Flipper is on or off. Its sections stay folded until opened
 * (Microbot's panel reopens every section each time it redraws), and settings that are off, or do nothing with the
 * current choices, get grey text. Only text colours and section folding change: never a value, control or listener.
 * While GE Flipper runs, {@link WaitingMouseSettings} styles the rows it binds itself ({@link #BOUND_KEYS}).
 */
final class SettingsLook implements AutoCloseable {
    static final Color DIM = ColorScheme.MEDIUM_GRAY_COLOR;
    /** A component's own text colour, kept while it is greyed here. */
    static final String OWN_COLOR = "geflipper.ownForeground";
    /** Rows WaitingMouseSettings styles itself while GE Flipper runs. */
    static final Set<String> BOUND_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "finish", "waitingMouseChance", "waitingMouseTimeSource", "waitingMouseTime")));
    private static final String PANEL_CLASS = "net.runelite.client.plugins.microbot.ui.MicrobotConfigPanel";
    private static final String GROUP = FlipperConfig.GROUP;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingsLook.class);

    private final Plugin owner;
    private final FlipperConfig config;
    private final JPanel root;
    private final EventBus eventBus;
    private final BooleanSupplier running;
    private final Events events = new Events(this);
    private final Set<Container> observed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ContainerAdapter treeChanges = new ContainerAdapter() {
        @Override public void componentAdded(ContainerEvent event) { observe(event.getChild()); queuePass(); }
        @Override public void componentRemoved(ContainerEvent event) { unobserve(event.getChild()); queuePass(); }
    };
    private final ComponentAdapter visibilityChanges = new ComponentAdapter() {
        @Override public void componentShown(ComponentEvent event) { queuePass(); }
        @Override public void componentHidden(ComponentEvent event) { queuePass(); }
    };
    private final HierarchyListener rootChanges = event -> {
        if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) queuePass();
    };
    private boolean bindingsActive;
    private boolean started;
    private boolean closed;
    private boolean passQueued;

    SettingsLook(Plugin owner, FlipperConfig config, JPanel root, EventBus eventBus, BooleanSupplier running) {
        this.owner = owner;
        this.config = config;
        this.root = root;
        this.eventBus = eventBus;
        this.running = running;
    }

    /** Call on the Swing thread. */
    void start() {
        if (started || closed) return;
        started = true;
        root.addHierarchyListener(rootChanges);
        observe(root);
        if (eventBus != null) eventBus.register(events);
        pass();
    }

    /**
     * GE Flipper started (true) or stopped (false). On start, the rows WaitingMouseSettings binds get their own
     * colours back first, so it records the right ones; then they are left to it until GE Flipper stops.
     */
    void setBindingsActive(boolean active) {
        if (closed) return;
        bindingsActive = active;
        if (active) {
            JPanel panel = ownedPanel(root, owner);
            if (panel != null) forEachRow(panel, (key, row, label, value) -> {
                if (BOUND_KEYS.contains(key)) {
                    style(label, true);
                    for (JComponent text : texts(value)) style(text, true);
                }
            });
        }
        queuePass();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (eventBus != null) {
            try {
                eventBus.unregister(events);
            } catch (RuntimeException alreadyGone) {
                // Nothing to undo.
            }
        }
        root.removeHierarchyListener(rootChanges);
        for (Container container : new ArrayList<>(observed)) unobserve(container);
        JPanel panel = ownedPanel(root, owner);
        if (panel != null) forEachRow(panel, (key, row, label, value) -> {
            style(label, true);
            for (JComponent text : texts(value)) style(text, true);
        });
    }

    /** Whether a setting does anything with the current choices. Off, or depending on something off, is grey. */
    static boolean isActive(FlipperConfig c, String key, boolean running) {
        switch (key) {
            case "finish": return running;
            case "showOverlay": return c.showOverlay();
            // A Mule never finishes for an update: it stays ready for the Flipper's last transfer.
            case "autoFinishOnSystemUpdate":
                return c.autoFinishOnSystemUpdate() && c.muleMode() != FlipperConfig.MuleMode.MULE_RECEIVER;
            case "waitingMouseOffScreen":
            case "waitingMousePreset":
            case "waitingMouseChance": return c.waitingMouseOffScreen();
            case "waitingMouseTimeSource": return fatigueOn(c);
            case "waitingMouseTime":
                return fatigueOn(c) && c.waitingMouseTimeSource() == FlipperConfig.TimeOfDaySource.CUSTOM_TIME;
            case "randomizeMouseSpeed": return c.randomizeMouseSpeed();
            case "blockManualMouseInput": return c.blockManualMouseInput();
            case "secondScreenAfkMode":
            case "secondScreenAfkMinutes": return c.secondScreenAfkMode() != FlipperConfig.SecondScreenAfkMode.OFF;
            case "muleMode":
            case "muleWorld":
            case "mulePort": return c.muleMode() != FlipperConfig.MuleMode.OFF;
            case "muleRsn":
            case "muleThresholdGp":
            case "muleTriggerGp": return c.muleMode() == FlipperConfig.MuleMode.FLIPPER_SENDER;
            case "muleBeforeUpdate": return c.muleMode() == FlipperConfig.MuleMode.FLIPPER_SENDER && c.muleBeforeUpdate();
            case "verboseLogging": return c.verboseLogging();
            default: return true; // Always in use: suggestion input, left-click swap and the help texts.
        }
    }

    private static boolean fatigueOn(FlipperConfig c) {
        return c.waitingMouseOffScreen() && c.randomizeMouseSpeed()
            && c.waitingMousePreset() == FlipperConfig.RandomizationPreset.DAY_FATIGUE;
    }

    /** Greys a component's text, keeping its own colour to put back later. */
    static void style(JComponent component, boolean active) {
        if (component == null) return;
        Object own = component.getClientProperty(OWN_COLOR);
        if (active) {
            if (own instanceof Color) {
                component.setForeground((Color) own);
                component.putClientProperty(OWN_COLOR, null);
            }
            return;
        }
        if (!(own instanceof Color) && component.getForeground() != null) {
            component.putClientProperty(OWN_COLOR, component.getForeground());
        }
        if (!DIM.equals(component.getForeground())) component.setForeground(DIM);
    }

    /** A component's own text colour, even while it is greyed here. */
    static Color ownForeground(Component component) {
        Object own = component instanceof JComponent ? ((JComponent) component).getClientProperty(OWN_COLOR) : null;
        return own instanceof Color ? (Color) own : component.getForeground();
    }

    private void queuePass() {
        if (closed || !started || passQueued) return;
        passQueued = true;
        // The native panel may still be rebuilding; look at it once the current Swing task is done.
        SwingUtilities.invokeLater(() -> {
            passQueued = false;
            pass();
        });
    }

    private void pass() {
        if (closed || !started) return;
        try {
            JPanel panel = ownedPanel(root, owner);
            if (panel == null) return;
            fold(panel);
            boolean isRunning = running != null && running.getAsBoolean();
            forEachRow(panel, (key, row, label, value) -> {
                boolean active = (bindingsActive && BOUND_KEYS.contains(key)) || isActive(config, key, isRunning);
                style(label, active);
                for (JComponent text : texts(value)) style(text, active);
            });
        } catch (RuntimeException unavailable) {
            // A client whose settings panel looks different keeps its plain look.
            log.debug("GE Flipper settings look skipped: {}", unavailable.toString());
        }
    }

    /**
     * Microbot's panel re-runs its search filter after every rebuild, and with an empty search that opens every
     * section, so "folded by default" never sticks. Put this plugin's sections back to the state the panel itself
     * remembers (folded until opened) by clicking the panel's own toggle. A search in progress is left alone.
     */
    private static void fold(JPanel panel) {
        if (searching(panel)) return;
        Object boxes = nativeField(panel.getClass(), panel, "sectionPanelByDesc");
        Object contents = nativeField(panel.getClass(), panel, "sectionContentByKey");
        Object remembered = nativeField(panel.getClass(), null, "sectionExpandStates");
        if (!(boxes instanceof Map) || !(contents instanceof Map) || !(remembered instanceof Map)) return;
        for (Map.Entry<?, ?> entry : new ArrayList<>(((Map<?, ?>) boxes).entrySet())) {
            if (!(entry.getKey() instanceof ConfigSectionDescriptor) || !(entry.getValue() instanceof JPanel)) continue;
            ConfigSectionDescriptor section = (ConfigSectionDescriptor) entry.getKey();
            JPanel box = (JPanel) entry.getValue();
            Object body = ((Map<?, ?>) contents).get(section.getKey());
            if (section.getSection() == null || !(body instanceof Component) || box.getComponentCount() < 2
                || box.getComponent(1) != body || !(box.getComponent(0) instanceof JPanel)) continue;
            JPanel header = (JPanel) box.getComponent(0);
            Component toggle = header.getLayout() instanceof BorderLayout
                ? ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.WEST) : null;
            Object saved = ((Map<?, ?>) remembered).get(section);
            boolean open = saved instanceof Boolean ? (Boolean) saved : !section.getSection().closedByDefault();
            if (((Component) body).isVisible() != open && toggle instanceof JButton) ((JButton) toggle).doClick(0);
        }
    }

    private static boolean searching(JPanel panel) {
        Object search = nativeField(panel.getClass(), panel, "searchField");
        if (!(search instanceof JTextComponent)) return true;
        String text = ((JTextComponent) search).getText();
        return text != null && !text.trim().isEmpty();
    }

    private interface RowVisitor { void visit(String key, JPanel row, JComponent label, Component value); }

    /** Every row of this plugin's panel, folded or not, with its setting key, name label (or button) and control. */
    private static void forEachRow(JPanel panel, RowVisitor visitor) {
        MicrobotPluginConfigurationDescriptor descriptor = descriptor(panel);
        Object index = nativeField(panel.getClass(), panel, "itemIndex");
        if (descriptor == null || descriptor.getConfigDescriptor() == null
            || descriptor.getConfigDescriptor().getItems() == null || !(index instanceof Map)) return;
        Map<String, String> keyByName = new HashMap<>();
        for (ConfigItemDescriptor item : descriptor.getConfigDescriptor().getItems()) {
            if (item != null && item.getItem() != null && !item.getItem().hidden()) {
                keyByName.put(item.getItem().name(), item.getItem().keyName());
            }
        }
        for (Object entry : new ArrayList<>(((Map<?, ?>) index).keySet())) {
            if (!(entry instanceof JPanel) || !(((JPanel) entry).getLayout() instanceof BorderLayout)) continue;
            JPanel row = (JPanel) entry;
            BorderLayout layout = (BorderLayout) row.getLayout();
            Component center = layout.getLayoutComponent(BorderLayout.CENTER);
            String name = center instanceof JLabel ? ((JLabel) center).getText()
                : center instanceof JButton ? ((JButton) center).getText() : null;
            String key = name == null ? null : keyByName.get(name);
            if (key == null) continue;
            Component value = layout.getLayoutComponent(BorderLayout.EAST);
            if (value == null) value = layout.getLayoutComponent(BorderLayout.SOUTH);
            visitor.visit(key, row, (JComponent) center, value);
        }
    }

    /** The parts of a control that show its value as text. */
    private static java.util.List<JComponent> texts(Component value) {
        java.util.List<JComponent> texts = new ArrayList<>();
        if (value instanceof JComboBox || value instanceof JTextComponent) texts.add((JComponent) value);
        if (value instanceof JSpinner && ((JSpinner) value).getEditor() instanceof JSpinner.DefaultEditor) {
            texts.add(((JSpinner.DefaultEditor) ((JSpinner) value).getEditor()).getTextField());
        }
        return texts;
    }

    // ---- Finding this plugin's own native panel (shared with WaitingMouseSettings) ----

    static JPanel ownedPanel(Component component, Plugin owner) {
        if (component == null || !component.isVisible()) return null;
        if (PANEL_CLASS.equals(component.getClass().getName())) {
            return owned(descriptor(component), owner) ? (JPanel) component : null;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JPanel found = ownedPanel(child, owner);
                if (found != null) return found;
            }
        }
        return null;
    }

    static boolean owned(MicrobotPluginConfigurationDescriptor descriptor, Plugin owner) {
        ConfigDescriptor details = descriptor == null ? null : descriptor.getConfigDescriptor();
        return descriptor != null && owner != null && descriptor.getPlugin() == owner && details != null
            && details.getGroup() != null && GROUP.equals(details.getGroup().value());
    }

    static MicrobotPluginConfigurationDescriptor descriptor(Component panel) {
        if (panel == null || !PANEL_CLASS.equals(panel.getClass().getName())) return null;
        // The native panel is package-private. Read only its descriptor to prove row ownership.
        Object value = nativeField(panel.getClass(), panel, "pluginConfig");
        return value instanceof MicrobotPluginConfigurationDescriptor ? (MicrobotPluginConfigurationDescriptor) value : null;
    }

    /** Reads one of the native panel's fields; an unsupported client layout simply returns null. */
    static Object nativeField(Class<?> type, Object owner, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
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

    /** Setting changes while GE Flipper is off reach only this; Microbot's panel does not redraw for them. */
    static final class Events {
        private final SettingsLook look;

        Events(SettingsLook look) { this.look = look; }

        @Subscribe
        public void onConfigChanged(ConfigChanged event) {
            if (GROUP.equals(event.getGroup())) later(look::queuePass);
        }

        @Subscribe
        public void onProfileChanged(ProfileChanged event) {
            later(look::queuePass);
        }

        @Subscribe
        public void onPluginChanged(PluginChanged event) {
            if (event.getPlugin() == look.owner && !event.isLoaded()) later(look::close);
            else later(look::queuePass);
        }

        private static void later(Runnable task) {
            if (SwingUtilities.isEventDispatchThread()) task.run();
            else SwingUtilities.invokeLater(task);
        }
    }
}
