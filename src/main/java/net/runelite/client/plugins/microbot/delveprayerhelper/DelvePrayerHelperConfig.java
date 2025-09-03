package net.runelite.client.plugins.microbot.delveprayerhelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(DelvePrayerHelperConfig.configGroup)
public interface DelvePrayerHelperConfig extends Config {
	String configGroup = "delve-prayer-helper";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "offensivePrayer",
		name = "Offensive Prayer",
		description = "Enable to use offensive ranged prayer",
		position = 0,
		section = generalSection
	)
	default boolean offensivePrayer()
	{
		return true;
	}
    @ConfigItem(
            keyName = "noOffensivePrayerInShieldPhase",
            name = "No Offensive Prayer in Shield Phase",
            description = "Enable to toggle the offensive prayer in the shield phase for saving prayer points",
            position = 1,
            section = generalSection
    )
    default boolean noOffensivePrayerInShieldPhase()
    {
        return true;
    }
}
