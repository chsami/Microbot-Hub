package net.runelite.client.plugins.microbot.leftclickcast;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("leftclickcast")
public interface LeftClickCastConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enabled",
		description = "Replace the left-click Attack option on NPCs with Cast Spell",
		position = 0
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spell",
		name = "Spell",
		description = "The spell cast when left-clicking an attackable NPC",
		position = 1
	)
	default PertTargetSpell spell()
	{
		return PertTargetSpell.FIRE_STRIKE;
	}

	@ConfigItem(
		keyName = "requireMagicWeapon",
		name = "Require magic weapon",
		description = "When enabled, the Cast entry is only inserted while a staff, bladed staff, powered staff, or powered wand is equipped. Disable to cast regardless of equipped weapon.",
		position = 2
	)
	default boolean requireMagicWeapon()
	{
		return true;
	}
}
