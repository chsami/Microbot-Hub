package net.runelite.client.plugins.microbot.butterflycatcher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.MORAY + "Butterfly Catcher",
	description = "Catches Butterflies for hunter training",
	tags = {"butterfly", "catcher","hunter"},
	authors = { "Moray" },
	version = ButterflyCatcherPlugin.version,
	minClientVersion = "1.9.8",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ButterflyCatcherPlugin extends Plugin {

	static final String version = "1.1.2";

    @Inject
    private ButterflyCatcherScript butterflyCatcherScript;



    @Override
    protected void startUp() throws AWTException {
        butterflyCatcherScript.run();
    }

    protected void shutDown() {
        butterflyCatcherScript.shutdown();
    }
}
