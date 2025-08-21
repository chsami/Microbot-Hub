package net.runelite.client.plugins.microbot.autobankstander;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.autobankstander.config.AutoBankStanderConfigPanel;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

@PluginDescriptor(
    name = PluginConstants.BGA + "Auto Bank Stander",
    description = "AIO bank standing plugin for various processing activities",
    tags = {"magic", "skilling", "processing"},
    authors = {"bga"},
    version = AutoBankStanderPlugin.version,
    minClientVersion = "1.9.8",
    iconUrl = "https://chsami.github.io/Microbot-Hub/AutoBankStanderPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/AutoBankStanderPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoBankStanderPlugin extends Plugin {
    static final String version = "1.0.0";
    
    @Inject
    private AutoBankStanderConfig config;
    
    @Inject
    private AutoBankStanderScript script;
    
    @Inject
    private ConfigManager configManager;
    
    private ConfigData currentConfigData;

    @Provides
    AutoBankStanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBankStanderConfig.class); // give the config to dependency injection system
    }

    @Override
    protected void startUp() throws AWTException {
        // load configuration data from storage
        loadConfigurationData();
        
        // only start script if configuration is valid
        if (currentConfigData != null && currentConfigData.isValid()) {
            script.run(currentConfigData); // start running the main script with our config data
        } else {
            log.info("No valid configuration found. Please open the configuration panel to set up the plugin.");
        }
    }

    @Override
    protected void shutDown() {
        script.shutdown(); // tell the script to stop running
    }
    
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        log.info("Config changed event: group={}, key={}", event.getGroup(), event.getKey());
        
        if (!event.getGroup().equals("AutoBankStander")) {
            log.info("Not our config group, ignoring");
            return;
        }
        
        if (event.getKey().equals("openConfiguration")) {
            log.info("Open configuration triggered, value: {}", config.openConfiguration());
            if (config.openConfiguration()) {
                // reset the boolean immediately to prevent repeated triggering
                configManager.setConfiguration("AutoBankStander", "openConfiguration", false);
                
                // open the configuration panel
                log.info("Opening configuration panel...");
                openConfigurationPanel();
            }
        }
    }
    
    private void loadConfigurationData() {
        String configDataJson = config.configurationData();
        if (configDataJson != null && !configDataJson.isEmpty()) {
            currentConfigData = ConfigData.fromJson(configDataJson);
            log.info("Loaded configuration: {}", currentConfigData);
        } else {
            currentConfigData = new ConfigData(); // default configuration
            log.info("No saved configuration found, using defaults");
        }
    }
    
    private void openConfigurationPanel() {
        SwingUtilities.invokeLater(() -> {
            try {
                // create the configuration panel
                AutoBankStanderConfigPanel configPanel = new AutoBankStanderConfigPanel(
                    currentConfigData,
                    this::saveConfiguration,
                    this::cancelConfiguration
                );
                
                // create and show the frame
                JFrame frame = new JFrame("Auto Bank Stander Configuration");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.add(configPanel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setResizable(false);
                frame.setVisible(true);
                
                log.info("Configuration panel opened");
            } catch (Exception e) {
                log.error("Error opening configuration panel: {}", e.getMessage(), e);
                JOptionPane.showMessageDialog(null, 
                    "Error opening configuration panel: " + e.getMessage(), 
                    "Configuration Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    private void saveConfiguration(ConfigData newConfigData) {
        try {
            this.currentConfigData = newConfigData;
            
            // save to hidden config items for persistence
            configManager.setConfiguration("AutoBankStander", "configurationData", newConfigData.toJson());
            configManager.setConfiguration("AutoBankStander", "isConfigured", true);
            
            log.info("Configuration saved: {}", newConfigData);
            
            // restart script with new configuration if it's running
            if (script.isRunning()) {
                script.shutdown();
                try {
                    Thread.sleep(1000); // give it a moment to shut down
                } catch (InterruptedException ignored) {}
                script.run(currentConfigData);
                log.info("Script restarted with new configuration");
            }
            
        } catch (Exception e) {
            log.error("Error saving configuration: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(null, 
                "Error saving configuration: " + e.getMessage(), 
                "Save Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void cancelConfiguration() {
        log.info("Configuration cancelled");
    }
}