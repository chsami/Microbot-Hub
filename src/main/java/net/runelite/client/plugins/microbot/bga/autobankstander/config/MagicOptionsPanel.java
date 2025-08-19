package net.runelite.client.plugins.microbot.autobankstander.config;

import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class MagicOptionsPanel extends JPanel {
    
    private final ConfigData configData;
    
    // Enchanting options
    private JComboBox<BoltType> boltTypeSelector;
    private JCheckBox useStaffCheckbox;
    
    // Placeholder components for other magic methods
    private JLabel lunarPlaceholder;
    private JLabel alchingPlaceholder;
    private JLabel superheatingPlaceholder;
    
    // Dynamic content panel
    private CardLayout cardLayout;
    private JPanel dynamicContent;
    
    public MagicOptionsPanel(ConfigData configData) {
        this.configData = configData;
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        // enchanting components
        boltTypeSelector = new JComboBox<>(BoltType.values());
        boltTypeSelector.setSelectedItem(configData.getBoltType());
        useStaffCheckbox = new JCheckBox("Use elemental staff when available", true);
        
        // placeholder components for other methods
        lunarPlaceholder = new JLabel("Lunar spells options will be implemented here");
        alchingPlaceholder = new JLabel("Alchemy options will be implemented here");
        superheatingPlaceholder = new JLabel("Superheating options will be implemented here");
        
        // dynamic content with card layout
        cardLayout = new CardLayout();
        dynamicContent = new JPanel(cardLayout);
        
        // create panels for each magic method
        dynamicContent.add(createEnchantingPanel(), "ENCHANTING");
        dynamicContent.add(createPlaceholderPanel(lunarPlaceholder), "LUNARS");
        dynamicContent.add(createPlaceholderPanel(alchingPlaceholder), "ALCHING");
        dynamicContent.add(createPlaceholderPanel(superheatingPlaceholder), "SUPERHEATING");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        
        add(dynamicContent, BorderLayout.CENTER);
    }
    
    private JPanel createEnchantingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Bolt Enchanting Options"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // bolt type selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Bolt Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(boltTypeSelector, gbc);
        
        // staff usage option
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(useStaffCheckbox, gbc);
        
        // add some spacing
        gbc.gridy = 2; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private JPanel createPlaceholderPanel(JLabel placeholder) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Coming Soon"));
        
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        placeholder.setForeground(Color.GRAY);
        panel.add(placeholder, BorderLayout.CENTER);
        
        return panel;
    }
    
    public void updateFromConfigData() {
        // show the appropriate options based on selected magic method
        MagicMethod method = configData.getMagicMethod();
        if (method != null) {
            cardLayout.show(dynamicContent, method.name());
            
            // update enchanting options if applicable
            if (method == MagicMethod.ENCHANTING) {
                boltTypeSelector.setSelectedItem(configData.getBoltType());
            }
        }
    }
    
    public void saveToConfigData() {
        // save enchanting options
        if (configData.getMagicMethod() == MagicMethod.ENCHANTING) {
            BoltType selectedBolt = (BoltType) boltTypeSelector.getSelectedItem();
            if (selectedBolt != null) {
                configData.setBoltType(selectedBolt);
            }
        }
        
        // TODO: Save other magic method options when implemented
    }
}