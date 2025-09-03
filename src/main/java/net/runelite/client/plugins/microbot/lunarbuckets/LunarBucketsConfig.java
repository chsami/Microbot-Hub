package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("lunarbuckets")
@ConfigInformation(
		"▪ Automatically casts Humidify to fill buckets.<br /><br />" +
			"▪ Ensure your bank contains a steam staff, astral runes and empty buckets.<br /><br />" +
			"▪ Ensure you are on Lunar Spellbook"
)
public interface LunarBucketsConfig extends Config {
}
