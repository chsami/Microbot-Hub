package net.runelite.client.plugins.microbot.autobankstander;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

@PluginDescriptor(
    name = PluginConstants.DEFAULT_PREFIX + "Bank Stander",
    description = "AIO bank standing plugin for various processing activities",
    tags = {"magic", "skilling", "processing"},
    authors = {"Unknown"},
    version = AutoBankStanderPlugin.version,
    minClientVersion = "1.9.8",
    iconUrl = "https://chsami.github.io/Microbot-Hub/AutoBankStanderPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/AutoBankStanderPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoBankStanderPlugin extends Plugin {
    static final String version = "1.0.1";
    
    @Inject
    private AutoBankStanderConfig config;
    
    @Getter
    @Inject
    private AutoBankStanderScript script;
    
    @Inject
    private ConfigManager configManager;
    
    @Inject
    private ClientToolbar clientToolbar;
    
    @Inject
    private SkillIconManager skillIconManager;
    
    private AutoBankStanderPanel panel;
    private NavigationButton navButton;
    private ConfigData currentConfigData;

    @Provides
    AutoBankStanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBankStanderConfig.class); // give the config to dependency injection system
    }

    @Override
    protected void startUp() throws AWTException {
        // set plugin reference in script for callbacks
        script.setPlugin(this);
        
        // create and add panel to sidebar
        addPanel();
        
        // build configuration data directly from config values
        buildConfigurationData();
        
        // plugin starts in stopped state - script only runs when user clicks start
        log.info("Plugin started in stopped state. Use panel to start script.");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        removePanel();
        log.info("Plugin stopped");
    }
    
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("AutoBankStander")) {
            return;
        }
        
        log.info("Configuration changed: {}", event.getKey());
        
        // rebuild config data from new values
        buildConfigurationData();
        
        // only restart script if it's currently running and user initiated the change
        // panel-driven changes should not auto-restart the script
        if (script.isRunning()) {
            log.info("Script is running - configuration updated. Restart manually if needed.");
            // note: we don't auto-restart to give user control over when script runs
        }
    }
    
    private void buildConfigurationData() {
        try {
            currentConfigData = new ConfigData();
            
            // set skill and methods from config
            currentConfigData.setSkill(config.skill());
            currentConfigData.setMagicMethod(config.magicMethod());
            currentConfigData.setHerbloreMode(config.herbloreMode());
            
            // set method-specific configurations
            currentConfigData.setBoltType(config.boltType());
            currentConfigData.setCleanHerbMode(config.cleanHerbMode());
            currentConfigData.setUnfinishedPotionMode(config.unfinishedPotionMode());
            currentConfigData.setFinishedPotion(config.finishedPotion());
            currentConfigData.setUseAmuletOfChemistry(config.useAmuletOfChemistry());
            
            // note: fletching configurations are set by panel since config doesn't store them
            // they remain at their defaults until panel updates them
            
            log.info("Built configuration from config values: {}", currentConfigData);
            
        } catch (Exception e) {
            log.error("Error building configuration data: {}", e.getMessage(), e);
            currentConfigData = new ConfigData(); // fallback to defaults
        }
    }
    
    private void addPanel() {
        if (panel == null) {
            panel = new AutoBankStanderPanel(this, skillIconManager);
            
            // ensure panel starts in stopped state
            panel.ensureStoppedState();
            
            // load the crafting icon from resources
            final BufferedImage icon = ImageUtil.loadImageResource(AutoBankStanderPlugin.class, 
                "assets/icon.png");
            
            // create navigation button
            navButton = NavigationButton.builder()
                .tooltip("Auto Bank Stander")
                .icon(icon)
                .priority(8)
                .panel(panel)
                .build();
            
            clientToolbar.addNavigation(navButton);
            log.info("Panel added to sidebar");
        }
    }
    
    private void removePanel() {
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
            panel = null;
            log.info("Panel removed from sidebar");
        }
    }
    
    // helper method for panel to run script with new configuration
    public void runScriptWithConfig(ConfigData configData) {
        log.info("=== RUNNING SCRIPT FROM PANEL ===");
        log.info("Config skill: {}", configData.getSkill());
        log.info("Config fletching mode: {}", configData.getFletchingMode());
        log.info("Config bow type: {}", configData.getBowType());
        log.info("Full config: {}", configData);
        
        // ensure script is fully stopped before starting
        if (script.isRunning()) {
            log.info("Script already running, stopping first");
            script.shutdown();
        }
        
        this.currentConfigData = configData;
        script.run(configData);
        log.info("=== SCRIPT START COMMAND SENT ===");
    }
    
    // helper method for panel to get current configuration
    public ConfigData getCurrentConfig() {
        return currentConfigData != null ? new ConfigData(currentConfigData) : new ConfigData();
    }
    
    // helper method for panel to update its state based on script status
    public void updatePanelState() {
        if (panel != null) {
            if (script.isRunning()) {
                panel.updateStatus("Running");
            } else {
                panel.ensureStoppedState();
            }
        }
    }
}