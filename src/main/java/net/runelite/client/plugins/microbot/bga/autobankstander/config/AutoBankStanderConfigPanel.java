package net.runelite.client.plugins.microbot.autobankstander.config;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

@Slf4j
public class AutoBankStanderConfigPanel extends JPanel {
    
    private final ConfigData configData;
    private final Consumer<ConfigData> onSave;
    private final Runnable onCancel;
    
    // Main selection components
    private JComboBox<SkillType> skillSelector;
    private JComboBox<MagicMethod> magicMethodSelector;
    private JComboBox<Mode> herbloreMethodSelector;
    
    // Dynamic content area
    private JPanel dynamicOptionsPanel;
    private CardLayout cardLayout;
    
    // Skill-specific panels
    private MagicOptionsPanel magicOptionsPanel;
    private HerbloreOptionsPanel herbloreOptionsPanel;
    
    public AutoBankStanderConfigPanel(ConfigData initialConfig, Consumer<ConfigData> onSave, Runnable onCancel) {
        this.configData = new ConfigData(initialConfig); // create a copy to work with
        this.onSave = onSave;
        this.onCancel = onCancel;
        
        initializeComponents();
        setupLayout();
        updateFromConfigData();
        setupEventHandlers();
    }
    
    private void initializeComponents() {
        // main skill selector
        skillSelector = new JComboBox<>(SkillType.values());
        skillSelector.setSelectedItem(configData.getSkill());
        
        // magic method selector
        magicMethodSelector = new JComboBox<>(MagicMethod.values());
        magicMethodSelector.setSelectedItem(configData.getMagicMethod());
        
        // herblore method selector  
        herbloreMethodSelector = new JComboBox<>(Mode.values());
        herbloreMethodSelector.setSelectedItem(configData.getHerbloreMode());
        
        // dynamic options panel with card layout
        cardLayout = new CardLayout();
        dynamicOptionsPanel = new JPanel(cardLayout);
        
        // create skill-specific option panels
        magicOptionsPanel = new MagicOptionsPanel(configData);
        herbloreOptionsPanel = new HerbloreOptionsPanel(configData);
        
        // add panels to card layout
        dynamicOptionsPanel.add(magicOptionsPanel, "MAGIC");
        dynamicOptionsPanel.add(herbloreOptionsPanel, "HERBLORE");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // top panel for main selections
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // center panel for skill-specific options
        JPanel centerPanel = createCenterPanel();
        add(centerPanel, BorderLayout.CENTER);
        
        // bottom panel for action buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("General Settings"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // skill selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Skill:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(skillSelector, gbc);
        
        // method selection (changes based on skill)
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Method:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // create a panel that switches between magic and herblore method selectors
        JPanel methodPanel = new JPanel(new CardLayout());
        methodPanel.add(magicMethodSelector, "MAGIC");
        methodPanel.add(herbloreMethodSelector, "HERBLORE");
        panel.add(methodPanel, gbc);
        
        return panel;
    }
    
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Method Options"));
        panel.add(dynamicOptionsPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton saveButton = new JButton("Save & Close");
        saveButton.addActionListener(e -> handleSave());
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> handleCancel());
        
        panel.add(cancelButton);
        panel.add(saveButton);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        // skill selector changes which method selector and options are shown
        skillSelector.addActionListener(e -> {
            SkillType selectedSkill = (SkillType) skillSelector.getSelectedItem();
            if (selectedSkill != null) {
                configData.setSkill(selectedSkill);
                updateMethodSelector();
                updateDynamicOptions();
            }
        });
        
        // magic method selector updates config and shows relevant options
        magicMethodSelector.addActionListener(e -> {
            MagicMethod selectedMethod = (MagicMethod) magicMethodSelector.getSelectedItem();
            if (selectedMethod != null) {
                configData.setMagicMethod(selectedMethod);
                magicOptionsPanel.updateFromConfigData();
            }
        });
        
        // herblore method selector updates config and shows relevant options
        herbloreMethodSelector.addActionListener(e -> {
            Mode selectedMode = (Mode) herbloreMethodSelector.getSelectedItem();
            if (selectedMode != null) {
                configData.setHerbloreMode(selectedMode);
                herbloreOptionsPanel.updateFromConfigData();
            }
        });
    }
    
    private void updateFromConfigData() {
        skillSelector.setSelectedItem(configData.getSkill());
        updateMethodSelector();
        updateDynamicOptions();
    }
    
    private void updateMethodSelector() {
        // get the method panel and show the appropriate selector
        Component[] components = ((JPanel) ((JPanel) getComponent(0)).getComponent(1)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                JPanel methodPanel = (JPanel) comp;
                if (methodPanel.getLayout() instanceof CardLayout) {
                    CardLayout layout = (CardLayout) methodPanel.getLayout();
                    layout.show(methodPanel, configData.getSkill().name());
                }
            }
        }
    }
    
    private void updateDynamicOptions() {
        cardLayout.show(dynamicOptionsPanel, configData.getSkill().name());
        
        // update the specific options panel
        switch (configData.getSkill()) {
            case MAGIC:
                magicOptionsPanel.updateFromConfigData();
                break;
            case HERBLORE:
                herbloreOptionsPanel.updateFromConfigData();
                break;
        }
    }
    
    private void handleSave() {
        // collect all data from the sub-panels
        magicOptionsPanel.saveToConfigData();
        herbloreOptionsPanel.saveToConfigData();
        
        // validate configuration
        if (!configData.isValid()) {
            JOptionPane.showMessageDialog(this, 
                "Invalid configuration. Please check your settings.", 
                "Configuration Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        log.info("Saving configuration: {}", configData);
        onSave.accept(configData);
        
        // close the window
        SwingUtilities.getWindowAncestor(this).dispose();
    }
    
    private void handleCancel() {
        onCancel.run();
        SwingUtilities.getWindowAncestor(this).dispose();
    }
}