package net.runelite.client.plugins.microbot.orbcharger;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig;
import net.runelite.client.plugins.microbot.orbcharger.OrbChargerOverlay;
import net.runelite.client.plugins.microbot.orbcharger.enums.OrbChargerState;
import net.runelite.client.plugins.microbot.orbcharger.enums.Teleport;
import net.runelite.client.plugins.microbot.orbcharger.scripts.AirOrbScript;
import net.runelite.client.plugins.microbot.orbcharger.scripts.PlayerDetectionScript;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@PluginDescriptor(
        name = PluginDescriptor.GMason + "Orb Charger",
        description = "Microbot Orb Charger Plugin",
        tags = {"magic", "microbot", "money", "money making"},
        version = OrbChargerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class OrbChargerPlugin extends Plugin {
    public final static String version = "1.1.0";
    
    @Inject
    private net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig config;
    @Provides
    net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.class);
    }
    
    @Getter
    private boolean useEnergyPotions;
    @Getter
    private boolean useStaminaPotions;
    @Getter
    private Rs2Food rs2Food;
    @Getter
    private int eatAtPercent;
    @Getter
    private Teleport teleport;
    
    @Getter
    @Setter
    public List<Player> dangerousPlayers = new ArrayList<>();

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private OrbChargerOverlay orbOverlay;
    
    @Inject
    private AirOrbScript airOrbScript;
    @Inject
    private PlayerDetectionScript playerDetectionScript;
    
    @Override
    protected void startUp() throws AWTException {
        useEnergyPotions = config.useEnergyPotions();
        useStaminaPotions = config.useStaminaPotions();
        rs2Food = config.food();
        eatAtPercent = config.eatAtPercent();
        teleport = config.teleport();
        if (overlayManager != null) {
            overlayManager.add(orbOverlay);
        }
        airOrbScript.run();
        playerDetectionScript.run();
        airOrbScript.handleWalk();
    }
    
    @Override
    protected void shutDown() {
        overlayManager.remove(orbOverlay);
        airOrbScript.shutdown();
        playerDetectionScript.shutdown();
    }
    
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.configGroup)) return;
        
        if (event.getKey().equals(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.useEnergyPotions)){
            useEnergyPotions = config.useEnergyPotions();
        }
        
        if (event.getKey().equals(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.useStaminaPotions)){
            useStaminaPotions = config.useStaminaPotions();
        }
        if (event.getKey().equals(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.food)){
            rs2Food = config.food();
        }
        if (event.getKey().equals(net.runelite.client.plugins.microbot.orbcharger.OrbChargerConfig.eatAtPercent)) {
            eatAtPercent = config.eatAtPercent();
        }
        if (event.getKey().equals(OrbChargerConfig.teleport)) {
            teleport = config.teleport();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().equalsIgnoreCase("oh dear, you are dead!")) {
            airOrbScript.hasDied = true; 
            airOrbScript.state = OrbChargerState.WALKING;
        }
    }
}
