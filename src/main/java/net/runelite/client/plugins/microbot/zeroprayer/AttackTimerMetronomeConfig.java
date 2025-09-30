package net.runelite.client.plugins.microbot.zeroprayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("zprayerhelper")
public interface AttackTimerMetronomeConfig extends Config
{
	@Getter
	@RequiredArgsConstructor
	enum PrayerMode {
		NONE("None"),           // New option
		LAZY("Lazy Flick"),
		NORMAL("Normal");

		private final String description;
	}


	@ConfigSection(
			name = "Prayer Settings",
			description = "Settings",
			position = 1
	)
	String TickNumberSettings = "Attack Cooldown Tick Settings";

	@ConfigItem(
			position = 1,
			keyName = "enableLazyFlicking",
			name = "Enable Offensive Prayers",
			description = "Toggle the lazy flicking of offensive prayers based on attack style",
			section = TickNumberSettings

	)
	default PrayerMode enableLazyFlicking()
	{
		return PrayerMode.LAZY;
	}

	@ConfigItem(
			position = 2,
			keyName = "showTick",
			name = "Show Attack Cooldown Ticks",
			description = "Shows number of ticks until next attack",
			section = TickNumberSettings
	)
	default boolean showTick()
	{
		return true;
	}



}
