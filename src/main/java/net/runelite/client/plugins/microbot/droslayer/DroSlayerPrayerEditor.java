package net.runelite.client.plugins.microbot.droslayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerProfileManager;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Per-task prayers and optional bracelet selections for all Slayer masters. */
final class DroSlayerPrayerEditor {
    private static JDialog dialog;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static String displayTaskName(String key) {
        if ("chaos druids".equalsIgnoreCase(key)) return "chaos druid";
        if (DroSlayerProfileManager.ELDER_CHAOS_DRUID_PROFILE.equalsIgnoreCase(key)) return "chaos druid elder";
        return key;
    }

    static void open(java.util.function.BiConsumer<java.util.Map<String, DroSlayerPrayer>, java.util.Map<String, DroSlayerOffensivePrayer>> onSave) {
        if (dialog != null && dialog.isDisplayable()) { dialog.toFront(); return; }
        DroSlayerProfileManager manager = new DroSlayerProfileManager();
        manager.loadProfiles();
        JComboBox<String> tasks = new JComboBox<>(manager.getAllProfiles().keySet().stream()
                .sorted(java.util.Comparator.comparing(DroSlayerPrayerEditor::displayTaskName, String.CASE_INSENSITIVE_ORDER)).toArray(String[]::new));
        tasks.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, displayTaskName((String) value),
                        index, selected, focused);
            }
        });
        JComboBox<DroSlayerPrayer> prayers = new JComboBox<>(new DroSlayerPrayer[] {
                DroSlayerPrayer.NONE, DroSlayerPrayer.PROTECT_MELEE,
                DroSlayerPrayer.PROTECT_MAGIC, DroSlayerPrayer.PROTECT_RANGED});
        JComboBox<DroSlayerOffensivePrayer> offensive = new JComboBox<>(DroSlayerOffensivePrayer.values());
        JCheckBox slaughter = new JCheckBox(DroSlayerBracelets.SLAUGHTER);
        JCheckBox expeditious = new JCheckBox(DroSlayerBracelets.EXPEDITIOUS);
        JCheckBox wildSlaughter = new JCheckBox(DroSlayerBracelets.SLAUGHTER);
        JCheckBox wildExpeditious = new JCheckBox(DroSlayerBracelets.EXPEDITIOUS);
        mutuallyExclusive(slaughter, expeditious);
        mutuallyExclusive(wildSlaughter, wildExpeditious);
        Runnable load = () -> {
            var profile = manager.getAllProfiles().get(tasks.getSelectedItem());
            DroSlayerPrayer prayer = profile == null ? DroSlayerPrayer.NONE : profile.getParsedPrayer();
            offensive.setSelectedItem(profile == null ? DroSlayerOffensivePrayer.AUTOMATIC : profile.getParsedOffensivePrayer());
            prayers.setSelectedItem(prayer == DroSlayerPrayer.TASK_PROFILE ? DroSlayerPrayer.NONE : prayer);
            String normalBracelet = DroSlayerBracelets.requestedBracelet(profile, false);
            String wildernessBracelet = DroSlayerBracelets.requestedBracelet(profile, true);
            slaughter.setSelected(DroSlayerBracelets.SLAUGHTER.equals(normalBracelet));
            expeditious.setSelected(DroSlayerBracelets.EXPEDITIOUS.equals(normalBracelet));
            wildSlaughter.setSelected(DroSlayerBracelets.SLAUGHTER.equals(wildernessBracelet));
            wildExpeditious.setSelected(DroSlayerBracelets.EXPEDITIOUS.equals(wildernessBracelet));
        };
        tasks.addActionListener(e -> load.run());
        load.run();
        JPanel fields = new JPanel(new GridLayout(5, 2, 8, 8));
        fields.add(new JLabel("Task profile")); fields.add(tasks);
        fields.add(new JLabel("Protection prayer")); fields.add(prayers);
        fields.add(new JLabel("Offensive prayer")); fields.add(offensive);
        fields.add(new JLabel("Other Slayer masters")); fields.add(braceletChoices(slaughter, expeditious));
        fields.add(new JLabel("Wilderness (Krystilia)")); fields.add(braceletChoices(wildSlaughter, wildExpeditious));
        offensive.setToolTipText("None disables offensive prayers for this monster. Automatic uses your weapon. Requires Use Offensive Prayers enabled.");
        JLabel status = new JLabel("Bracelets are optional. Leave both unchecked to keep your inventory setup gloves.");
        JButton save = new JButton("Save task settings");
        save.addActionListener(e -> {
            try {
                String task = (String) tasks.getSelectedItem();
                DroSlayerPrayer prayer = (DroSlayerPrayer) prayers.getSelectedItem();
                if (task == null || prayer == null) return;
                DroSlayerTaskProfileJson selection = new DroSlayerTaskProfileJson();
                selection.setSlaughterBracelet(slaughter.isSelected());
                selection.setExpeditiousBracelet(expeditious.isSelected());
                selection.setWildernessSlaughterBracelet(wildSlaughter.isSelected());
                selection.setWildernessExpeditiousBracelet(wildExpeditious.isSelected());
                saveTaskSettings(manager.getProfilePath(), task, prayer.name(),
                        ((DroSlayerOffensivePrayer) offensive.getSelectedItem()).name(), selection);
                manager.loadProfiles();
                java.util.Map<String, DroSlayerPrayer> updated = new java.util.HashMap<>();
                manager.getAllProfiles().forEach((name, profile) -> updated.put(
                        name.trim().toLowerCase(java.util.Locale.ROOT), profile.getParsedPrayer()));
                java.util.Map<String, DroSlayerOffensivePrayer> updatedOffensive = new java.util.HashMap<>();
                manager.getAllProfiles().forEach((name, profile) -> updatedOffensive.put(
                        name.trim().toLowerCase(java.util.Locale.ROOT), profile.getParsedOffensivePrayer()));
                onSave.accept(updated, updatedOffensive);
                status.setText("Saved and applied for " + task + ".");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Could not save task settings: " + ex.getMessage(),
                        "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields, BorderLayout.NORTH);
        content.add(status, BorderLayout.CENTER);
        content.add(save, BorderLayout.SOUTH);
        dialog = new JDialog((java.awt.Frame) null, "Dro Slayer — Monster config", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    static void mutuallyExclusive(JCheckBox first, JCheckBox second) {
        first.addActionListener(e -> { if (first.isSelected()) second.setSelected(false); });
        second.addActionListener(e -> { if (second.isSelected()) first.setSelected(false); });
    }

    private static JPanel braceletChoices(JCheckBox first, JCheckBox second) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(first); row.add(second);
        return row;
    }

    /** Patch only edited fields in the latest JSON, including unknown fields from other editors. */
    static void saveTaskSettings(Path path, String task, String prayer, String offensive,
            DroSlayerTaskProfileJson bracelets) throws IOException {
        JsonObject latest;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            latest = GSON.fromJson(reader, JsonObject.class);
        }
        if (latest == null || !latest.has(task) || !latest.get(task).isJsonObject())
            throw new IOException("Task profile is no longer available; reopen the editor.");
        JsonObject selected = latest.getAsJsonObject(task);
        selected.addProperty("prayer", prayer);
        selected.addProperty("offensivePrayer", offensive);
        selected.addProperty("slaughterBracelet", bracelets.isSlaughterBracelet());
        selected.addProperty("expeditiousBracelet", bracelets.isExpeditiousBracelet());
        selected.addProperty("wildernessSlaughterBracelet", bracelets.isWildernessSlaughterBracelet());
        selected.addProperty("wildernessExpeditiousBracelet", bracelets.isWildernessExpeditiousBracelet());
        Path temporary = Files.createTempFile(path.getParent(), "slayer-task-settings-", ".json");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(latest, writer);
            }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
