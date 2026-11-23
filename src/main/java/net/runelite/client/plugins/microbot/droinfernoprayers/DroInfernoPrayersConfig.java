package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DroInfernoPrayersConfig.GROUP)
public interface DroInfernoPrayersConfig extends Config
{
	String GROUP = "droinfernoprayers";

	@ConfigItem(
		keyName = "holdWithPairs",
		name = "Hold with same-tick pairs",
		description = "When the same overhead is needed on back-to-back ticks, keep it up with an off/on pair early in each tick so it does not drain. Off: the overhead is simply left on.",
		position = 1
	)
	default boolean holdWithPairs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the overhead being prayed and the next attack it answers.",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}
}
