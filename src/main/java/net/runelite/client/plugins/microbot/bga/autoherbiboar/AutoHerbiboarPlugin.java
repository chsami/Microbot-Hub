package net.runelite.client.plugins.microbot.autoherbiboar;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.herbiboars.HerbiboarPlugin;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
    name = PluginConstants.BGA + "Auto Herbiboar",
    description = "Automatically hunts herbiboars...",
    tags = {"skilling", "hunter"},
    author = "bga",
    version = AutoHerbiboarPlugin.version,
    minClientVersion = "1.9.8",
    iconUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/icon.png",
    cardUrl = "https://chsami.github.io/Microbot-Hub/AutoHerbiboarPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class AutoHerbiboarPlugin extends Plugin {
    static final String version = "1.1.0";
    @Inject
    private AutoHerbiboarConfig config;
    @Provides
    AutoHerbiboarConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(AutoHerbiboarConfig.class); }
    @Inject
    private PluginManager pluginManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoHerbiboarOverlay overlay;
    private HerbiboarPlugin herbiboarPlugin;
    @Inject
    private AutoHerbiboarScript script;
    @Override
    protected void startUp() throws AWTException {
        herbiboarPlugin = pluginManager.getPlugins().stream().filter(HerbiboarPlugin.class::isInstance).map(HerbiboarPlugin.class::cast).findFirst().orElse(null);
        if (herbiboarPlugin != null && !pluginManager.isPluginEnabled(herbiboarPlugin)) pluginManager.setPluginEnabled(herbiboarPlugin, true);
        if (herbiboarPlugin != null && !pluginManager.getActivePlugins().contains(herbiboarPlugin)) try { pluginManager.startPlugin(herbiboarPlugin); } catch (Exception ignored) {}
        script.setHerbiboarPlugin(herbiboarPlugin);
        overlayManager.add(overlay);
        script.run(config);
    }
    @Override
    protected void shutDown() { 
        overlayManager.remove(overlay);
        script.shutdown(); 
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            String message = chatMessage.getMessage();
            if (message.equals("The creature has successfully confused you with its tracks, leading you round in circles.") || 
                message.equals("You'll need to start again.")) {
                script.handleConfusionMessage();
            } else if (message.equals("Nothing seems to be out of place here.")) {
                script.handleDeadEndTunnel();
            }
        }
    }
}
