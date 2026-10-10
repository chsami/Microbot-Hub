package net.runelite.client.plugins.microbot.microhunter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ClientTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.hunter.HunterPlugin;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.microhunter.scripts.AutoChinScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "AutoHunter",
        description = "Microbot AutoHunter plugin",
        tags = {"hunter", "microbot"},
        version = AutoHunterPlugin.version,
        minClientVersion = "2.6.30",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
@PluginDependency(HunterPlugin.class)
public class AutoHunterPlugin extends Plugin {
    public static final String version = "1.5.0";
    @Inject
    private AutoHunterConfig config;

    @Provides
    AutoHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoHunterConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoHunterOverlay autoHunterOverlay;

    @Inject
    AutoChinScript autoChinScript;

    AutoChinScript getAutoChinScript() {
        return autoChinScript;
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoHunterOverlay);
        }
        autoChinScript.run(config);
    }

    protected void shutDown() {
        if (autoChinScript != null) {
            autoChinScript.shutdown();
        }
        if (overlayManager != null && autoHunterOverlay != null) {
            overlayManager.remove(autoHunterOverlay);
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (autoChinScript != null) autoChinScript.onClientTick();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (autoChinScript != null) {
            autoChinScript.onNpcSpawned(event.getNpc());
        }
    }

}
