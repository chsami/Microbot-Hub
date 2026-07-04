package net.runelite.client.plugins.microbot.agility.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum AgilityPotionOption
{
	NONE("None", -1),
	STAMINA_4("Stamina potion(4)", ItemID._4DOSESTAMINA),
	STAMINA_3("Stamina potion(3)", ItemID._3DOSESTAMINA),
	STAMINA_2("Stamina potion(2)", ItemID._2DOSESTAMINA),
	STAMINA_1("Stamina potion(1)", ItemID._1DOSESTAMINA);

	private final String displayName;
	private final int itemId;

	AgilityPotionOption(String displayName, int itemId)
	{
		this.displayName = displayName;
		this.itemId = itemId;
	}

	public boolean isNone()
	{
		return this == NONE;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
