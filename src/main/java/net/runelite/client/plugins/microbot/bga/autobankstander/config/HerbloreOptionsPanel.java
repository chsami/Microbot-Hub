package net.runelite.client.plugins.microbot.autobankstander.config;

import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class HerbloreOptionsPanel extends JPanel {
    
    private final ConfigData configData;
    
    // Clean herbs options
    private JComboBox<CleanHerbMode> cleanHerbModeSelector;
    
    // Unfinished potions options
    private JComboBox<UnfinishedPotionMode> unfinishedPotionModeSelector;
    
    // Finished potions options
    private JComboBox<HerblorePotion> finishedPotionSelector;
    private JCheckBox useAmuletCheckbox;
    
    // Dynamic content panel
    private CardLayout cardLayout;
    private JPanel dynamicContent;
    
    public HerbloreOptionsPanel(ConfigData configData) {
        this.configData = configData;
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        // clean herbs components
        cleanHerbModeSelector = new JComboBox<>(CleanHerbMode.values());
        cleanHerbModeSelector.setSelectedItem(configData.getCleanHerbMode());
        
        // unfinished potions components
        unfinishedPotionModeSelector = new JComboBox<>(UnfinishedPotionMode.values());
        unfinishedPotionModeSelector.setSelectedItem(configData.getUnfinishedPotionMode());
        
        // finished potions components
        finishedPotionSelector = new JComboBox<>(HerblorePotion.values());
        finishedPotionSelector.setSelectedItem(configData.getFinishedPotion());
        useAmuletCheckbox = new JCheckBox("Use Amulet of Chemistry", configData.isUseAmuletOfChemistry());
        
        // dynamic content with card layout
        cardLayout = new CardLayout();
        dynamicContent = new JPanel(cardLayout);
        
        // create panels for each herblore method
        dynamicContent.add(createCleanHerbsPanel(), "CLEAN_HERBS");
        dynamicContent.add(createUnfinishedPotionsPanel(), "UNFINISHED_POTIONS");
        dynamicContent.add(createFinishedPotionsPanel(), "FINISHED_POTIONS");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        
        add(dynamicContent, BorderLayout.CENTER);
    }
    
    private JPanel createCleanHerbsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Clean Herbs Options"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // herb type selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Herb Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(cleanHerbModeSelector, gbc);
        
        // description
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel description = new JLabel("<html><i>Select 'Any and all' to clean the highest level herbs available,<br>" +
                                       "or choose a specific herb type to clean only that herb.</i></html>");
        description.setForeground(Color.DARK_GRAY);
        panel.add(description, gbc);
        
        // add spacing
        gbc.gridy = 2; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private JPanel createUnfinishedPotionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Unfinished Potions Options"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // potion type selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Potion Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(unfinishedPotionModeSelector, gbc);
        
        // description
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel description = new JLabel("<html><i>Select 'Any and all' to make the highest level unfinished potions available,<br>" +
                                       "or choose a specific type. Requires clean herbs and vials of water.</i></html>");
        description.setForeground(Color.DARK_GRAY);
        panel.add(description, gbc);
        
        // add spacing
        gbc.gridy = 2; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private JPanel createFinishedPotionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Finished Potions Options"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // potion selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Potion:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(finishedPotionSelector, gbc);
        
        // amulet option
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(useAmuletCheckbox, gbc);
        
        // description
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel description = new JLabel("<html><i>Select the specific potion to make. Requires unfinished potions and secondary ingredients.<br>" +
                                       "Amulet of Chemistry provides a chance for 4-dose potions instead of 3-dose.</i></html>");
        description.setForeground(Color.DARK_GRAY);
        panel.add(description, gbc);
        
        // add spacing
        gbc.gridy = 3; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    public void updateFromConfigData() {
        // show the appropriate options based on selected herblore mode
        Mode mode = configData.getHerbloreMode();
        if (mode != null) {
            cardLayout.show(dynamicContent, mode.name());
            
            // update specific options based on mode
            switch (mode) {
                case CLEAN_HERBS:
                    cleanHerbModeSelector.setSelectedItem(configData.getCleanHerbMode());
                    break;
                case UNFINISHED_POTIONS:
                    unfinishedPotionModeSelector.setSelectedItem(configData.getUnfinishedPotionMode());
                    break;
                case FINISHED_POTIONS:
                    finishedPotionSelector.setSelectedItem(configData.getFinishedPotion());
                    useAmuletCheckbox.setSelected(configData.isUseAmuletOfChemistry());
                    break;
            }
        }
    }
    
    public void saveToConfigData() {
        // save the appropriate options based on selected herblore mode
        Mode mode = configData.getHerbloreMode();
        if (mode != null) {
            switch (mode) {
                case CLEAN_HERBS:
                    CleanHerbMode selectedCleanMode = (CleanHerbMode) cleanHerbModeSelector.getSelectedItem();
                    if (selectedCleanMode != null) {
                        configData.setCleanHerbMode(selectedCleanMode);
                    }
                    break;
                case UNFINISHED_POTIONS:
                    UnfinishedPotionMode selectedUnfinishedMode = (UnfinishedPotionMode) unfinishedPotionModeSelector.getSelectedItem();
                    if (selectedUnfinishedMode != null) {
                        configData.setUnfinishedPotionMode(selectedUnfinishedMode);
                    }
                    break;
                case FINISHED_POTIONS:
                    HerblorePotion selectedPotion = (HerblorePotion) finishedPotionSelector.getSelectedItem();
                    if (selectedPotion != null) {
                        configData.setFinishedPotion(selectedPotion);
                    }
                    configData.setUseAmuletOfChemistry(useAmuletCheckbox.isSelected());
                    break;
            }
        }
    }
}