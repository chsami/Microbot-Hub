package net.runelite.client.plugins.microbot.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ExampleConfig.configGroup)
public interface ExampleConfig extends Config {
	String configGroup = "micro-example";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "exampleSetting",
		name = "Example Setting",
		description = "Enable this option to use the example feature.",
		position = 0,
		section = generalSection
	)
	default boolean exampleSetting()
	{
		return false;
	}
}
