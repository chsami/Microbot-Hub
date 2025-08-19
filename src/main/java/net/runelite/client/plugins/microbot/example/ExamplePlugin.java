package net.runelite.client.plugins.microbot.example;

import com.google.inject.Provides;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Example",
	description = "Microbot Example Plugin",
	tags = {"example"},
	authors = { "Mocrosoft" },
	version = ExamplePlugin.version,
	minClientVersion = "1.9.8",
	disable = true,
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ExamplePlugin extends Plugin {

	static final String version = "1.1.1";

    @Inject
    private ExampleConfig config;
    @Provides
    ExampleConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ExampleOverlay exampleOverlay;

    @Inject
    private ExampleScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
            exampleOverlay.myButton.hookMouseListener();
        }
        exampleScript.run();
    }

    protected void shutDown() {
        exampleScript.shutdown();
        overlayManager.remove(exampleOverlay);
        exampleOverlay.myButton.unhookMouseListener();
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        log.info(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));
    }
}
