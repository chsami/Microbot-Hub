package net.runelite.client.plugins.microbot.anonymous;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
	name = PluginConstants.BOLADO + "Anonymous Mode",
	authors = { "Bolado" },
	version = AnonymousPlugin.version,
	minClientVersion = "1.9.6",
	description = "Hide your in-game identity.",
	tags = {"chat", "mask", "hide", "anonymous"},
	iconUrl = "https://chsami.github.io/Microbot-Hub/AnonymousPlugin/assets/icon.png",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)

@Slf4j
public class AnonymousPlugin extends Plugin {

	static final String version = "1.0.2";

	@Inject
	private Client client;

	@Inject
	private AnonymousConfig config;

	@Provides
	AnonymousConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(AnonymousConfig.class);
	}

    @Inject
    AnonymousScript anonymousScript;


    @Override
	protected void startUp() throws Exception {
		this.client = Microbot.getClient();

        Microbot.enableAutoRunOn = false;

        anonymousScript.run(config, client);
	}

	@Override
	protected void shutDown() throws Exception {
		if (anonymousScript != null && anonymousScript.isRunning()) {
			anonymousScript.shutdown();
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		if (anonymousScript != null && anonymousScript.isRunning()) anonymousScript.onBeforeRender(client, config);
	}
}
