package net.runelite.client.plugins.microbot.minnowsfishing;

import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Auto Minnows",
        description = "Microbot minnows plugin",
        tags = {"minnows", "microbot"},
        authors = {"See1Duck"},
        version = MinnowsPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/MinnowsPlugin/assets/card.jpg",
        iconUrl = "https://chsami.github.io/Microbot-Hub/MinnowsPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class MinnowsPlugin extends Plugin {
    public static final String version = "1.0.5";
    @Inject
    MinnowsScript minnowsScript;
    @Inject
    private MinnowsConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MinnowsOverlay minnowsOverlay;

    @Provides
    MinnowsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MinnowsConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(minnowsOverlay);
        }
        minnowsScript.run();
    }

    protected void shutDown() {
        overlayManager.remove(minnowsOverlay);
        minnowsScript.shutdown();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        minnowsScript.onGameTick();

    }

}